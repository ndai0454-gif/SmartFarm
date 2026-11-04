import numpy as np
import pytest
from fastapi.testclient import TestClient

from app.main import create_app
from app.region_workflow import NODATA, paired_change
from tests.test_backend import H, RING, settings


def body(**updates):
    result = {"region": {"ring": RING}, "baseline_start": "2026-08-01",
              "baseline_end": "2026-09-01", "comparison_start": "2026-09-01",
              "comparison_end": "2026-10-01", "max_cloud_pct": 80, "threshold": 0.1}
    result.update(updates)
    return result


@pytest.fixture
def client():
    with TestClient(create_app(settings())) as instance:
        yield instance


def test_common_mask_does_not_subtract_independent_means():
    # Two different missing locations: independent means suggest +0.3, paired mean +0.1.
    b = np.array([.1, .9, np.nan])
    c = np.array([.2, np.nan, .8])
    result, delta = paired_change(b, c, np.ones(3, bool), .01, .1)
    assert result["mean_delta"] == pytest.approx(.1)
    assert result["common_valid_area_ha"] == .01
    assert result["common_valid_coverage_pct"] == pytest.approx(33.33)
    assert np.isnan(delta[1:]).all()
    assert result["mean_delta"] != pytest.approx(np.nanmean(c) - np.nanmean(b))


def test_classification_uses_actual_common_cell_areas_and_threshold_boundaries():
    b = np.zeros(6)
    c = np.array([.25, -.25, .125, -.125, 0, 1])
    roi = np.array([True, True, True, True, True, False])
    result, _ = paired_change(b, c, roi, [.01, .02, .03, .04, .05, .06], .125)
    assert result["increase_ha"] == .01
    assert result["decrease_ha"] == .02
    assert result["stable_ha"] == .12
    assert result["common_valid_area_ha"] == .15
    assert result["common_valid_coverage_pct"] == 100
    assert sum(result[k] for k in ("increase_ha", "decrease_ha", "stable_ha")) == pytest.approx(.15)


def test_missing_and_nonfinite_pixels_never_turn_into_zero_change():
    result, delta = paired_change([NODATA, .4, np.inf], [.5, NODATA, .7], [1, 1, 1], .01, .1)
    assert result["mean_delta"] is None
    assert result["increase_ha"] is result["decrease_ha"] is result["stable_ha"] is None
    assert result["common_valid_coverage_pct"] == 0 and result["common_valid_area_ha"] is None
    assert np.isnan(delta).all()


def test_workflow_requires_authentication(client):
    assert client.post("/v1/satellite/region-workflow", json=body()).status_code == 401
    assert client.post("/v1/satellite/region-workflow", json=body(), headers={"Authorization": "Bearer wrong"}).status_code == 401


@pytest.mark.parametrize("updates", [
    {"baseline_end": "2026-08-01"}, {"comparison_end": "2026-08-01"},
    {"comparison_start": "2026-08-31"}, {"baseline_start": "2026-01-01"},
    {"comparison_end": "2027-01-01"}, {"threshold": 0}, {"threshold": 1.1},
    {"max_cloud_pct": -1}, {"max_cloud_pct": 101},
    {"region": {"ring": [[107, 11, 0], [108, 11, 0], [108, 12, 0]]}},
    {"region": {"ring": [[107, 11], [108, 12], [108, 11], [107, 12]]}},
])
def test_workflow_rejects_invalid_periods_parameters_and_regions(client, updates):
    assert client.post("/v1/satellite/region-workflow", json=body(**updates), headers=H).status_code == 422


def test_full_mock_report_is_explicit_consistent_and_reproducible(client):
    response = client.post("/v1/satellite/region-workflow", json=body(), headers=H)
    assert response.status_code == 200, response.text
    j = response.json()
    assert j == client.post("/v1/satellite/region-workflow", json=body(), headers=H).json()
    assert j["simulated"] is True and "MÔ PHỎNG" in j["source"]
    for key in ("baseline", "comparison"):
        p = j[key]
        assert p["scene_count"] == len(p["scene_ids"])
        assert {s["index"]: s["nominal_pixel_size_m"] for s in p["indices"]} == {"NDVI": 10, "EVI": 10, "NDMI": 20}
        assert 0 < p["valid_coverage_pct"] <= 100
    c = j["change"]
    assert c["common_valid_coverage_pct"] <= min(j["baseline"]["valid_coverage_pct"], j["comparison"]["valid_coverage_pct"])
    assert sum(c[k] for k in ("increase_ha", "stable_ha", "decrease_ha")) == pytest.approx(c["common_valid_area_ha"], abs=2e-6)
    assert c["nominal_pixel_size_m"] == 10 and c["threshold"] == .1 and c["legend"]["min"] == -1
    image = client.get(c["image_url"], headers=H)
    assert image.status_code == 200 and image.content.startswith(b"\x89PNG")
    assert client.get(c["image_url"]).status_code == 401
    assert "LiDAR" in j["caution"] and "MÔ PHỎNG" in " ".join(j["warnings"])


def test_cloud_threshold_participates_in_cache_key(client, monkeypatch):
    from app import region_workflow
    old = region_workflow.mock_result
    called = []
    def tracked(ring, req):
        called.append(req.max_cloud_pct)
        return old(ring, req)
    monkeypatch.setattr(region_workflow, "mock_result", tracked)
    for cloud in (80, 80, 70):
        assert client.post("/v1/satellite/region-workflow", json=body(max_cloud_pct=cloud), headers=H).status_code == 200
    assert called == [80, 70]


def test_live_errors_never_fall_back_to_mock(monkeypatch):
    from app import ee_service, region_workflow
    monkeypatch.setattr(ee_service, "init", lambda _: None)
    def fail(*_):
        raise RuntimeError("private credential detail must not leak")
    monkeypatch.setattr(region_workflow, "live_result", fail)
    with TestClient(create_app(settings(mode="live", ee_project="test"))) as client:
        r = client.post("/v1/satellite/region-workflow", json=body(), headers=H)
        assert r.status_code == 502
        assert "private credential" not in r.text and "simulated" not in r.text


def test_no_scenes_is_404_not_successful_empty_analysis(monkeypatch):
    from app import ee_service, region_workflow
    monkeypatch.setattr(ee_service, "init", lambda _: None)
    def fail(*_):
        raise ee_service.NoScenes("Không có cảnh.")
    monkeypatch.setattr(region_workflow, "live_result", fail)
    with TestClient(create_app(settings(mode="live", ee_project="test"))) as client:
        assert client.post("/v1/satellite/region-workflow", json=body(), headers=H).status_code == 404
