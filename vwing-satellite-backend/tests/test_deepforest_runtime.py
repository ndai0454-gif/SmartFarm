"""Contract tests use stub predictions; they never claim model accuracy."""

import io
from types import SimpleNamespace

import numpy as np
import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient
from PIL import Image

from app import deepforest_engine as de
from app import deepforest_runtime as dr

RING = [[107, 11], [107.001, 11], [107.001, 11.001], [107, 11.001], [107, 11]]
BOUNDS = {"west": 107, "south": 11, "east": 107.001, "north": 11.001}


def png(mode="RGB", size=(100, 100)):
    buffer = io.BytesIO()
    Image.new(mode, size, (30, 120, 50) if mode == "RGB" else 128).save(
        buffer, format="PNG"
    )
    return buffer.getvalue()


@pytest.fixture()
def client(tmp_path, monkeypatch):
    monkeypatch.setattr(dr, "UPLOAD_DIR", tmp_path)
    monkeypatch.setattr(dr, "CHECKPOINT_DIR", tmp_path / "checkpoints")
    app = FastAPI()
    app.include_router(de.make_router(lambda: None, lambda: "mock", None))
    return TestClient(app)


def upload(client, **kwargs):
    return client.post(
        "/v1/deepforest/images",
        content=png(),
        headers={"Content-Type": "image/png"},
        **kwargs,
    )


def predict(client, image_id=None, **kwargs):
    payload = {"region": {"ring": RING}, **kwargs}
    if image_id:
        payload["image_id"] = image_id
    return client.post("/v1/deepforest/predict", json=payload)


def test_uploaded_real_records_georeference_and_unknown_health(client, monkeypatch):
    saved = upload(client, params=BOUNDS)
    assert saved.status_code == 200
    assert saved.json()["width"] == 100
    record = {
        "xmin": 10,
        "ymin": 20,
        "xmax": 30,
        "ymax": 40,
        "score": 0.614,
        "label": "Tree",
    }
    monkeypatch.setattr(dr.runtime, "predict", lambda *a, **kw: [record])
    result = predict(client, saved.json()["image_id"])
    assert result.status_code == 200, result.text
    body = result.json()
    box = body["boxes"][0]
    assert box["west"] == pytest.approx(107.0001)
    assert box["north"] == pytest.approx(11.0008)
    assert box["south"] == pytest.approx(11.0006)
    assert box["score"] == 0.614
    assert box["ndvi"] is None and box["ndre"] is None and box["health_score"] is None
    assert box["status"] == "unknown"
    assert body["healthy_count"] is None
    assert body["raw_boxes_before_nms"] is None and body["nms_suppressed_count"] is None
    assert body["source"]["source"] == "user_uploaded_rgb"
    assert body["simulated"] is False and body["unknown_health_count"] == 1


def test_zero_detections_are_not_filled_with_synthetic_trees(client, monkeypatch):
    image_id = upload(client).json()["image_id"]
    monkeypatch.setattr(dr.runtime, "predict", lambda *a, **kw: [])
    body = predict(client, image_id).json()
    assert body["boxes"] == []
    assert body["merged_boxes_after_nms"] == 0 and body["mean_confidence_pct"] == 0
    assert body["source"]["georeference"] == "assumed_from_prediction_region"


def test_no_source_image_fails_instead_of_fabricating(client, monkeypatch):
    def no_image(*args):
        raise dr.RuntimeUnavailable("No RGB image available")

    monkeypatch.setattr(dr, "fetch_esri_rgb", no_image)
    assert predict(client).status_code == 503


def test_model_unavailable_is_503(client, monkeypatch):
    image_id = upload(client).json()["image_id"]

    def unavailable(*a, **kw):
        raise dr.RuntimeUnavailable("Weights unavailable")

    monkeypatch.setattr(dr.runtime, "predict", unavailable)
    response = predict(client, image_id)
    assert (
        response.status_code == 503
        and response.json()["detail"] == "Weights unavailable"
    )


def test_roi_excludes_model_boxes_outside_polygon(client, monkeypatch):
    image_id = upload(client, params=BOUNDS).json()["image_id"]
    records = [
        {"xmin": 5, "ymin": 5, "xmax": 15, "ymax": 15, "score": 0.8, "label": "Tree"},
        {"xmin": 80, "ymin": 80, "xmax": 90, "ymax": 90, "score": 0.7, "label": "Tree"},
    ]
    monkeypatch.setattr(dr.runtime, "predict", lambda *a, **kw: records)
    triangle = [[107, 11.001], [107.001, 11.001], [107, 11], [107, 11.001]]
    body = predict(client, image_id, region={"ring": triangle}).json()
    assert body["merged_boxes_after_nms"] == 1 and body["detections_outside_roi"] == 1


def test_manual_labels_do_not_become_detections_or_training_metrics(
    client, monkeypatch
):
    image_id = upload(client).json()["image_id"]
    monkeypatch.setattr(dr.runtime, "predict", lambda *a, **kw: [])
    ann = {**BOUNDS, "label": "Tree", "user_verified": True}
    body = predict(client, image_id, user_annotations=[ann]).json()
    assert body["boxes"] == [] and body["finetuned_checkpoint_id"] is None


def test_finetune_rejects_missing_image_and_too_few_labels(client, monkeypatch):
    ann = {**BOUNDS, "label": "Tree", "user_verified": True}
    no_image = client.post(
        "/v1/deepforest/finetune", json={"region": {"ring": RING}, "annotations": [ann]}
    )
    assert no_image.status_code == 422
    image_id = upload(client, params=BOUNDS).json()["image_id"]
    started = []
    monkeypatch.setattr(de.ft, "start_job", lambda *a: started.append(a))
    few = client.post(
        "/v1/deepforest/finetune",
        json={"region": {"ring": RING}, "image_id": image_id, "annotations": [ann]},
    )
    assert few.status_code == 422 and not started
    assert client.get("/v1/deepforest/finetune/nope").status_code == 404


def test_split_holds_out_rightmost_boxes_without_overlap():
    boxes = [(10 + 40 * i, 10, 40 + 40 * i, 40) for i in range(10)]
    cut, train, val = de.ft.split_train_validation(boxes, 500)
    assert len(val) >= de.ft.MIN_VAL_BOXES and len(train) >= de.ft.MIN_TRAIN_BOXES
    assert all(b[2] <= cut for b in train) and all(b[0] >= 0 for b in val)
    with pytest.raises(de.ft.FineTuneInputError):
        de.ft.split_train_validation(boxes[:5], 500)


def test_pixel_conversion_flips_latitude_and_drops_tiny_boxes():
    a = lambda **k: SimpleNamespace(**k)  # noqa: E731
    bounds = {"west": 0.0, "east": 1.0, "south": 0.0, "north": 1.0}
    boxes = de.ft._geo_to_pixel_boxes(
        [
            a(west=0.1, east=0.3, south=0.7, north=0.9),
            a(west=0.1, east=0.1001, south=0.7, north=0.9),
        ],
        bounds,
        100,
        200,
    )
    assert boxes == [pytest.approx((10, 20, 30, 60))]


def test_catalog_lists_tree_bird_livestock_and_no_invented_model(client):
    assert [m["model_id"] for m in client.get("/v1/deepforest/models").json()] == [
        dr.MODEL_ID,
        "weecology/deepforest-bird",
        "weecology/deepforest-livestock",
    ]
    assert predict(client, model_id="vwing/deepforest-vn-orchard").status_code == 422
    assert predict(client, model_id="ft-000000000000").status_code == 422


def test_predict_passes_selected_model_to_runtime(client, monkeypatch):
    image_id = upload(client, params=BOUNDS).json()["image_id"]
    seen = {}

    def fake(*a, **kw):
        seen.update(kw)
        return []

    monkeypatch.setattr(dr.runtime, "predict", fake)
    body = predict(client, image_id, model_id="weecology/deepforest-bird").json()
    assert seen["model_id"] == "weecology/deepforest-bird"
    assert body["target_class"] == "Bird"


def test_upload_rejects_bad_payload_type_grayscale_partial_bounds_and_large_bytes(
    client, monkeypatch
):
    assert (
        client.post(
            "/v1/deepforest/images",
            content=b"invalid",
            headers={"Content-Type": "image/png"},
        ).status_code
        == 422
    )
    assert (
        client.post(
            "/v1/deepforest/images",
            content=png(),
            headers={"Content-Type": "text/plain"},
        ).status_code
        == 415
    )
    assert (
        client.post(
            "/v1/deepforest/images",
            content=png("L"),
            headers={"Content-Type": "image/png"},
        ).status_code
        == 422
    )
    assert upload(client, params={"west": 107}).status_code == 422
    monkeypatch.setattr(dr, "MAX_IMAGE_BYTES", 10)
    assert upload(client).status_code == 413


def test_upload_pixel_limit_and_no_data_rejected(monkeypatch):
    monkeypatch.setattr(dr, "MAX_IMAGE_PIXELS", 50)
    with pytest.raises(dr.ImageInputError):
        dr.decode_rgb(png())
    monkeypatch.setattr(dr, "MAX_IMAGE_PIXELS", 25_000_000)
    image = Image.new("RGBA", (30, 30), (1, 2, 3, 0))
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    with pytest.raises(dr.ImageInputError):
        dr.decode_rgb(buffer.getvalue())


def test_image_id_cannot_reference_arbitrary_paths(client):
    assert predict(client, "../../private").status_code == 422
    assert predict(client, "a" * 32).status_code == 422


def test_image_extent_must_cover_roi(client, monkeypatch):
    image_id = upload(client, params={**BOUNDS, "east": 107.0005}).json()["image_id"]

    def should_not_run(*a, **kw):
        pytest.fail("Model must not run outside uploaded image bounds")

    monkeypatch.setattr(dr.runtime, "predict", should_not_run)
    assert predict(client, image_id).status_code == 422


def test_runtime_calls_actual_model_tile_method_with_unmodified_rgb():
    runtime = dr.DeepForestRuntime()
    calls = []

    class Frame:
        empty = False

        def to_dict(self, orient):
            return [
                {
                    "xmin": 1,
                    "ymin": 2,
                    "xmax": 10,
                    "ymax": 20,
                    "score": 0.7,
                    "label": "Tree",
                }
            ]

    def tile(**kwargs):
        calls.append(kwargs)
        return Frame()

    runtime._model = SimpleNamespace(
        config=SimpleNamespace(), model=SimpleNamespace(), predict_tile=tile
    )
    rgb = np.zeros((50, 50, 3), dtype=np.uint8)
    rgb[:, :, 0] = 200
    output = runtime.predict(
        rgb, patch_size=400, patch_overlap=0.2, iou_threshold=0.1, score_threshold=0.3
    )
    assert output[0]["score"] == 0.7
    assert calls[0]["image"] is rgb and calls[0]["patch_overlap"] == 0.2
    assert runtime._model.model.score_thresh == 0.3


def test_runtime_load_error_never_creates_model(monkeypatch):
    import builtins

    original = builtins.__import__

    def blocked(name, *args, **kwargs):
        if name == "deepforest":
            raise ImportError("dependency missing")
        return original(name, *args, **kwargs)

    monkeypatch.setattr(builtins, "__import__", blocked)
    runtime = dr.DeepForestRuntime()
    with pytest.raises(dr.RuntimeUnavailable):
        runtime.predict(
            np.zeros((30, 30, 3)),
            patch_size=400,
            patch_overlap=0.15,
            iou_threshold=0.15,
            score_threshold=0.3,
        )
    assert runtime._model is None
    assert runtime.status()["ready"] is False


def test_legacy_garden_boxes_do_not_claim_deepforest_scores():
    crowns = [{"lon": 107.0005, "lat": 11.0005, "confidence_pct": 99}]
    de.enrich_crowns_with_deepforest_bboxes(crowns, BOUNDS, 100, 100, 4)
    assert crowns[0]["df_model"] == "rgb_heuristic_not_deepforest"
    assert crowns[0]["df_score"] is None


def test_esri_keeps_returned_export_extent_and_provenance(monkeypatch):
    import json

    extent = {"xmin": 106.9999, "ymin": 10.9999, "xmax": 107.0011, "ymax": 11.0011}
    response = {
        "extent": extent,
        "href": "https://server.arcgisonline.com/test.png",
        "width": 100,
        "height": 100,
    }
    calls = []

    def read(url, *args):
        calls.append(url)
        return json.dumps(response).encode() if len(calls) == 1 else png()

    monkeypatch.setattr(dr, "_read_url", read)
    # A flat image is rejected: ensure the fixture has real pixel variation.
    real = np.full((100, 100, 3), (30, 120, 50), dtype=np.uint8)
    real[:10] = 220
    buffer = io.BytesIO()
    Image.fromarray(real).save(buffer, format="PNG")

    def read_varied(url, *args):
        calls.append(url)
        return json.dumps(response).encode() if len(calls) == 1 else buffer.getvalue()

    monkeypatch.setattr(dr, "_read_url", read_varied)
    rgb, meta = dr._fetch_esri_export_rgb(BOUNDS)
    assert meta["bounds"]["west"] == 106.9999
    assert meta["capture_date"] is None and "Esri" in meta["source"]
    assert "bboxSR=4326" in calls[0] and rgb.shape == (100, 100, 3)


def test_esri_missing_any_tile_fails_instead_of_using_partial_mosaic(monkeypatch):
    def unavailable(*args):
        raise OSError("tile failed")

    monkeypatch.setattr(dr, "_read_url", unavailable)
    with pytest.raises(dr.RuntimeUnavailable):
        dr.fetch_esri_rgb(BOUNDS)


def test_esri_complete_tiles_reproject_to_exact_requested_bounds(monkeypatch):
    pytest.importorskip("rasterio")
    image = Image.new("RGB", (256, 256), (10, 140, 20))
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    calls = []

    def download(url, *args):
        calls.append(url)
        return buffer.getvalue()

    monkeypatch.setattr(dr, "_read_url", download)
    rgb, meta = dr.fetch_esri_rgb(BOUNDS)
    assert meta["bounds"] == BOUNDS
    assert "reprojected_epsg4326" in meta["georeference"]
    assert meta["tiles_downloaded"] == len(calls) and len(calls) > 0
    assert all("World_Imagery/MapServer/tile/" in url for url in calls)
    assert np.all(rgb[:, :, 1] == 140)


def test_esri_placeholder_tile_detected_without_rejecting_green_image():
    placeholder = np.full((256, 256, 3), 204, dtype=np.uint8)
    placeholder[100:108, 60:160] = 255
    assert dr._is_esri_no_data_tile(placeholder)
    forest = np.full((256, 256, 3), (30, 120, 50), dtype=np.uint8)
    assert not dr._is_esri_no_data_tile(forest)


def test_esri_placeholder_falls_back_to_consistent_lower_zoom(monkeypatch):
    pytest.importorskip("rasterio")
    placeholder = Image.new("RGB", (256, 256), (204, 204, 204))
    buffer = io.BytesIO()
    placeholder.save(buffer, format="PNG")
    bad_data = buffer.getvalue()
    image = Image.new("RGB", (256, 256), (10, 140, 20))
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    good_data = buffer.getvalue()

    def download(url, *args):
        return bad_data if "/tile/19/" in url else good_data

    monkeypatch.setattr(dr, "_read_url", download)
    rgb, meta = dr.fetch_esri_rgb(BOUNDS)
    assert meta["tile_zoom"] == 18
    assert all("/tile/18/" in url for url in meta["tile_urls"])
    assert np.all(rgb[:, :, 1] == 140)
    assert any("zoom 18" in w for w in meta["warnings"])


def test_all_esri_placeholder_tiles_fail_without_running_detection(monkeypatch):
    image = Image.new("RGB", (256, 256), (204, 204, 204))
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    monkeypatch.setattr(dr, "_read_url", lambda *a: buffer.getvalue())
    with pytest.raises(dr.RuntimeUnavailable, match="no-data"):
        dr.fetch_esri_rgb(BOUNDS)
