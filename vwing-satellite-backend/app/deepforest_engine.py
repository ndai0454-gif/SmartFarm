"""Genuine DeepForest tree inference API. RGB cannot supply NDVI/NDRE or plant health."""

from __future__ import annotations

import io
import math
from collections.abc import Callable
from itertools import pairwise

import numpy as np
from fastapi import APIRouter, Depends, HTTPException, Request
from PIL import Image, ImageDraw
from pydantic import BaseModel, Field

from . import deepforest_finetune as ft
from . import deepforest_runtime as dr
from . import geo, mock_service, vietnam_deepforest_sources as vds
from .schemas import Region

PREBUILT_MODELS = {
    dr.MODEL_ID: {
        "model_id": dr.MODEL_ID,
        "name": "DeepForest NEON Tree Crown (Prebuilt)",
        "architecture": "RetinaNet / PyTorch Lightning",
        "TargetClass": "Tree",
        "label_vi": "Tán cây (NEON pretrained)",
        "default_patch_size": 400,
        "default_patch_overlap": 0.15,
        "default_iou_threshold": 0.15,
        "default_score_threshold": 0.30,
        "pretrained_dataset": "NEON aerial RGB tree crowns; upstream pretrained weights",
        "recommended_for_vi": "Cần kiểm định địa phương; chưa có mô hình vườn Việt Nam đã huấn luyện.",
    },
    "weecology/deepforest-bird": {
        "model_id": "weecology/deepforest-bird",
        "name": "DeepForest Bird Detector (Prebuilt)",
        "architecture": "RetinaNet / PyTorch Lightning",
        "TargetClass": "Bird",
        "label_vi": "Chim",
        "default_patch_size": 400,
        "default_patch_overlap": 0.15,
        "default_iou_threshold": 0.15,
        "default_score_threshold": 0.30,
        "pretrained_dataset": "Weecology aerial bird imagery; upstream pretrained weights",
        "recommended_for_vi": "Ảnh drone độ phân giải rất cao (chim vài chục pixel); chưa kiểm định với chim ở vườn Việt Nam.",
    },
    "weecology/deepforest-livestock": {
        "model_id": "weecology/deepforest-livestock",
        "name": "DeepForest Livestock Detector (Prebuilt)",
        "architecture": "RetinaNet / PyTorch Lightning",
        "TargetClass": "Livestock",
        "label_vi": "Gia súc",
        "default_patch_size": 400,
        "default_patch_overlap": 0.15,
        "default_iou_threshold": 0.15,
        "default_score_threshold": 0.30,
        "pretrained_dataset": "Weecology aerial livestock imagery; upstream pretrained weights",
        "recommended_for_vi": "Ảnh drone nhìn thẳng xuống; chưa kiểm định với vật nuôi ở Việt Nam.",
    },
}


def model_catalog() -> dict:
    """Prebuilt models plus every finished local fine-tune checkpoint."""
    catalog = dict(PREBUILT_MODELS)
    for meta in ft.list_checkpoints():
        base = PREBUILT_MODELS.get(meta.get("base_model_id"), {})
        label = meta.get("label") or base.get("TargetClass", "Tree")
        catalog[meta["checkpoint_id"]] = {
            **base,
            "model_id": meta["checkpoint_id"],
            "name": f"Fine-tune {meta.get('crop_type', '')} ({meta['checkpoint_id']})",
            "TargetClass": label,
            "label_vi": f"{base.get('label_vi', label)} (fine-tune cục bộ)",
            "base_model_id": meta.get("base_model_id"),
            "final_map50": meta.get("final_map50"),
            "validation_annotations_count": meta.get("validation_annotations_count"),
            "pretrained_dataset": "Fine-tune từ nhãn người dùng; mAP chỉ đo trên vùng kiểm định của đúng ảnh đó.",
            "recommended_for_vi": "Chỉ dùng cho vùng/ảnh tương tự ảnh đã huấn luyện; chưa kiểm định ngoài thực địa.",
        }
    return catalog


class DeepForestAnnotationBox(BaseModel):
    box_id: str = Field(default="ANN-001")
    west: float
    south: float
    east: float
    north: float
    label: str = Field(default="Tree")
    user_verified: bool = Field(default=True)


class DeepForestPredictRequest(BaseModel):
    image_id: str | None = None
    region: Region
    model_id: str = Field(default="weecology/deepforest-tree")
    crop_type: str = Field(default="durian")
    patch_size: int = Field(default=400, ge=128, le=1024)
    patch_overlap: float = Field(default=0.15, ge=0.05, le=0.50)
    iou_threshold: float = Field(default=0.15, ge=0.05, le=0.60)
    score_threshold: float = Field(default=0.30, ge=0.10, le=0.90)
    user_annotations: list[DeepForestAnnotationBox] = Field(default_factory=list)


class DeepForestFineTuneRequest(BaseModel):
    region: Region
    image_id: str | None = None
    base_model_id: str = Field(default="weecology/deepforest-tree")
    crop_type: str = Field(default="durian")
    epochs: int = Field(default=6, ge=1, le=30)
    learning_rate: float = Field(default=0.0005, ge=1e-5, le=0.05)
    patch_size: int = Field(default=400, ge=128, le=1024)
    patch_overlap: float = Field(default=0.15, ge=0.05, le=0.50)
    annotations: list[DeepForestAnnotationBox] = Field(default_factory=list)


class DeepForestCrownBox(BaseModel):
    box_id: str
    code: str
    label: str
    label_vi: str
    score: float
    confidence_pct: int
    xmin_px: int
    ymin_px: int
    xmax_px: int
    ymax_px: int
    west: float
    south: float
    east: float
    north: float
    center_lon: float
    center_lat: float
    crown_width_m: float
    crown_height_m: float
    crown_diameter_m: float
    crown_area_m2: float
    tile_id: str
    row_number: int
    status: str
    status_vi: str
    health_score: int | None = None
    ndvi: float | None = None
    ndre: float | None = None
    is_user_annotated: bool = False


class DeepForestTileWindow(BaseModel):
    tile_id: str
    row_idx: int
    col_idx: int
    x0_px: int
    y0_px: int
    x1_px: int
    y1_px: int
    west: float
    south: float
    east: float
    north: float
    raw_candidates: int | None = None
    kept_after_nms: int | None = None


class DeepForestPredictResponse(BaseModel):
    source: dict = Field(default_factory=dict)
    warnings: list[str] = Field(default_factory=list)
    simulated: bool = False
    health_assessment_available: bool = False
    unknown_health_count: int = 0
    detections_outside_roi: int = 0
    counts_note: str = "DeepForest predict_tile trả kết quả đã NMS; không công bố số ứng viên thô hoặc số box bị loại."
    repo_url: str = "https://github.com/weecology/DeepForest.git"
    engine_runtime: str
    model_id: str
    model_name: str
    architecture: str
    target_class: str
    label_vi: str
    area_ha: float
    bounds: dict
    raster_width_px: int
    raster_height_px: int
    spatial_resolution_m: float
    patch_size_px: int
    patch_overlap: float
    iou_threshold: float
    score_threshold: float
    total_tiles_processed: int
    raw_boxes_before_nms: int | None = None
    merged_boxes_after_nms: int
    nms_suppressed_count: int | None = None
    mean_confidence_pct: float
    mean_crown_diameter_m: float
    canopy_cover_pct: float
    healthy_count: int | None = None
    moderate_count: int | None = None
    stressed_count: int | None = None
    tiles: list[DeepForestTileWindow]
    boxes: list[DeepForestCrownBox]
    overlay_url: str | None = None
    finetuned_checkpoint_id: str | None = None
    summary_vi: str


class DeepForestEpochMetric(BaseModel):
    epoch: int
    box_regression_loss: float | None = None
    focal_classification_loss: float | None = None
    total_loss: float | None = None
    val_map50: float | None = None
    val_recall: float | None = None


class DeepForestFineTuneResponse(BaseModel):
    checkpoint_id: str
    base_model_id: str
    crop_type: str
    label: str
    framework: str = "PyTorch / PyTorch Lightning RetinaNet (weecology/DeepForest)"
    training_annotations_count: int
    validation_annotations_count: int
    dropped_annotations_count: int
    augmented_patches_count: int
    validation_patches_count: int
    epochs_completed: int
    learning_rate: float
    initial_map50: float | None = None
    final_map50: float | None = None
    initial_recall: float | None = None
    final_recall: float | None = None
    epoch_history: list[DeepForestEpochMetric]
    split: dict
    created_at: str


class DeepForestFineTuneJob(BaseModel):
    job_id: str
    state: str
    stage: str
    error: str | None = None
    result: DeepForestFineTuneResponse | None = None


def compute_box_iou(
    box_a: tuple[float, float, float, float], box_b: tuple[float, float, float, float]
) -> float:
    """Tính Intersection over Union (IoU) giữa 2 bounding box [xmin, ymin, xmax, ymax]."""
    xa1, ya1, xa2, ya2 = box_a
    xb1, yb1, xb2, yb2 = box_b
    inter_x1 = max(xa1, xb1)
    inter_y1 = max(ya1, yb1)
    inter_x2 = min(xa2, xb2)
    inter_y2 = min(ya2, yb2)
    inter_w = max(0.0, inter_x2 - inter_x1)
    inter_h = max(0.0, inter_y2 - inter_y1)
    inter_area = inter_w * inter_h
    if inter_area <= 0.0:
        return 0.0
    area_a = max(1e-6, (xa2 - xa1) * (ya2 - ya1))
    area_b = max(1e-6, (xb2 - xb1) * (yb2 - yb1))
    return float(inter_area / (area_a + area_b - inter_area))


def torchvision_style_nms(
    boxes: list[dict], iou_threshold: float = 0.15
) -> tuple[list[dict], int]:
    """
    Gộp các bounding box chồng lấn giữa các mảnh cắt (Sliding Window Tiles) bằng thuật toán
    Non-Maximum Suppression (NMS) chuẩn `torchvision.ops.nms` của DeepForest.
    Ưu tiên giữ các box do người dùng gán nhãn (`is_user_annotated=True`) hoặc có `score` cao nhất.
    """
    if not boxes:
        return [], 0
    sorted_boxes = sorted(
        boxes,
        key=lambda b: (
            1 if b.get("is_user_annotated") else 0,
            float(b.get("score", 0.0)),
        ),
        reverse=True,
    )
    kept: list[dict] = []
    suppressed = 0
    for cand in sorted_boxes:
        ca = (
            float(cand["xmin"]),
            float(cand["ymin"]),
            float(cand["xmax"]),
            float(cand["ymax"]),
        )
        cx_a = 0.5 * (ca[0] + ca[2])
        cy_a = 0.5 * (ca[1] + ca[3])
        rad_a = 0.25 * ((ca[2] - ca[0]) + (ca[3] - ca[1]))
        overlap = False
        for k in kept:
            cb = (
                float(k["xmin"]),
                float(k["ymin"]),
                float(k["xmax"]),
                float(k["ymax"]),
            )
            iou = compute_box_iou(ca, cb)
            if iou >= iou_threshold:
                overlap = True
                break
            # Kiểm tra thêm khoảng cách tâm nếu một box nằm lọt trong tán lớn
            cx_b = 0.5 * (cb[0] + cb[2])
            cy_b = 0.5 * (cb[1] + cb[3])
            rad_b = 0.25 * ((cb[2] - cb[0]) + (cb[3] - cb[1]))
            dist_c = math.hypot(cx_a - cx_b, cy_a - cy_b)
            if dist_c < min(rad_a, rad_b) * 0.82:
                overlap = True
                break
        if overlap:
            suppressed += 1
        else:
            kept.append(cand)
    return kept, suppressed


def generate_sliding_windows(
    width: int,
    height: int,
    patch_size: int = 400,
    patch_overlap: float = 0.15,
) -> list[tuple[int, int, int, int, int, int]]:
    """
    Tạo danh sách các cửa sổ trượt (Sliding Windows) tương đương `deepforest.preprocess.split_raster`:
    Trả về list `(row_idx, col_idx, x0, y0, x1, y1)`.
    """
    ps = max(64, min(patch_size, max(width, height)))
    step = max(32, round(ps * (1.0 - max(0.05, min(0.50, patch_overlap)))))

    xs = list(range(0, max(1, width - ps + 1), step))
    if not xs or xs[-1] + ps < width:
        xs.append(max(0, width - ps))
    xs = sorted(set(xs))

    ys = list(range(0, max(1, height - ps + 1), step))
    if not ys or ys[-1] + ps < height:
        ys.append(max(0, height - ps))
    ys = sorted(set(ys))

    windows: list[tuple[int, int, int, int, int, int]] = []
    for r_i, y0 in enumerate(ys):
        for c_i, x0 in enumerate(xs):
            x1 = min(width, x0 + ps)
            y1 = min(height, y0 + ps)
            windows.append((r_i, c_i, x0, y0, x1, y1))
    return windows


def enrich_crowns_with_deepforest_bboxes(
    crowns,
    box,
    hr_w,
    hr_h,
    default_crown_d_m,
    patch_size=400,
    patch_overlap=0.15,
    model_id=dr.MODEL_ID,
):
    """Legacy garden heuristic boxes; this function does not run DeepForest."""
    lat_c = (box["south"] + box["north"]) / 2
    lon_m = max(1.0, 111_320 * math.cos(math.radians(lat_c)))
    for crown in crowns:
        lon, lat = float(crown["lon"]), float(crown["lat"])
        diameter = float(crown.get("crown_diameter_m", default_crown_d_m))
        dx, dy = diameter / (2 * lon_m), diameter / (2 * 110_574)
        crown["bbox_geo"] = [lon - dx, lat - dy, lon + dx, lat + dy]
        crown["bbox_px"] = [
            max(0, int((lon - dx - box["west"]) / (box["east"] - box["west"]) * hr_w)),
            max(
                0, int((box["north"] - lat - dy) / (box["north"] - box["south"]) * hr_h)
            ),
            min(
                hr_w, int((lon + dx - box["west"]) / (box["east"] - box["west"]) * hr_w)
            ),
            min(
                hr_h,
                int((box["north"] - lat + dy) / (box["north"] - box["south"]) * hr_h),
            ),
        ]
        crown.update(
            crown_width_m=diameter,
            crown_height_m=diameter,
            tile_id="HEURISTIC",
            df_score=None,
            df_label="Tree",
            df_model="rgb_heuristic_not_deepforest",
            bbox_method="estimated_from_heuristic_center_and_diameter",
        )
    return crowns


def _point_in_ring(lon, lat, ring):
    inside = False
    for (x1, y1), (x2, y2) in pairwise(ring):
        cross = (lon - x1) * (y2 - y1) - (lat - y1) * (x2 - x1)
        if (
            abs(cross) < 1e-14
            and min(x1, x2) <= lon <= max(x1, x2)
            and min(y1, y2) <= lat <= max(y1, y2)
        ):
            return True
        if (y1 > lat) != (y2 > lat) and lon < (x2 - x1) * (lat - y1) / (y2 - y1) + x1:
            inside = not inside
    return inside


def _pixel_geo(x, y, width, height, bounds):
    return (
        bounds["west"] + x / width * (bounds["east"] - bounds["west"]),
        bounds["north"] - y / height * (bounds["north"] - bounds["south"]),
    )


def _window_metadata(rgb, patch_size, patch_overlap, bounds):
    height, width = rgb.shape[:2]
    # Use precisely the upstream window algorithm when installed. The fallback
    # is display metadata only and never creates detection candidates.
    try:
        from deepforest import preprocess

        windows = preprocess.compute_windows(
            np.moveaxis(rgb, -1, 0), patch_size, patch_overlap
        )
        output = []
        for window in windows:
            x, y, w, h = window.getRect()
            output.append((int(x), int(y), int(x + w), int(y + h)))
    except (ImportError, ModuleNotFoundError):
        output = [
            (x0, y0, x1, y1)
            for _, _, x0, y0, x1, y1 in generate_sliding_windows(
                width, height, patch_size, patch_overlap
            )
        ]
    xs, ys = sorted({x[0] for x in output}), sorted({x[1] for x in output})
    tiles = []
    for x0, y0, x1, y1 in output:
        west, north = _pixel_geo(x0, y0, width, height, bounds)
        east, south = _pixel_geo(x1, y1, width, height, bounds)
        r, c = ys.index(y0), xs.index(x0)
        tiles.append(
            DeepForestTileWindow(
                tile_id=f"T{r:02d}-{c:02d}",
                row_idx=r,
                col_idx=c,
                x0_px=x0,
                y0_px=y0,
                x1_px=x1,
                y1_px=y1,
                west=west,
                south=south,
                east=east,
                north=north,
            )
        )
    return tiles


def _render_overlay(rgb, boxes):
    image = Image.fromarray(rgb).convert("RGB")
    draw = ImageDraw.Draw(image)
    for b in boxes:
        draw.rectangle(
            (b.xmin_px, b.ymin_px, b.xmax_px, b.ymax_px),
            outline=(255, 215, 64),
            width=2,
        )
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    return buffer.getvalue()


def run_deepforest_predict_tile(
    ring: list, req: DeepForestPredictRequest
) -> DeepForestPredictResponse:
    catalog = model_catalog()
    if req.model_id not in catalog or not dr.is_known_model(req.model_id):
        raise HTTPException(
            422,
            "Mô hình không có trong danh sách /v1/deepforest/models; không tự thay bằng mô hình khác.",
        )
    meta = catalog[req.model_id]
    region_bounds = geo.bounds(ring)
    try:
        dr.validate_bounds(region_bounds)
        rgb, source = (
            dr.load_upload(req.image_id, region_bounds)
            if req.image_id
            else dr.fetch_esri_rgb(region_bounds)
        )
        bounds = dr.validate_bounds(source["bounds"])
        height, width = rgb.shape[:2]
        # Reject a polygon extending beyond an uploaded image's declared extent.
        if any(
            not bounds["west"] - 1e-9 <= lon <= bounds["east"] + 1e-9
            or not bounds["south"] - 1e-9 <= lat <= bounds["north"] + 1e-9
            for lon, lat in ring
        ):
            raise dr.ImageInputError("Vùng chọn nằm ngoài khung tọa độ của ảnh RGB.")
        records = dr.runtime.predict(
            rgb,
            patch_size=req.patch_size,
            patch_overlap=req.patch_overlap,
            iou_threshold=req.iou_threshold,
            score_threshold=req.score_threshold,
            model_id=req.model_id,
        )
        tiles = _window_metadata(rgb, req.patch_size, req.patch_overlap, bounds)
    except dr.ImageInputError as exc:
        raise HTTPException(422, str(exc)) from exc
    except dr.RuntimeUnavailable as exc:
        raise HTTPException(503, str(exc)) from exc
    warnings_out = list(source.get("warnings", []))
    warnings_out += [
        "RGB không có băng NIR/red-edge: NDVI, NDRE và sức khỏe cây chưa được đo.",
        "Kích thước tán và độ phủ là ước lượng từ bounding box, không phải phân đoạn tán cây.",
    ]
    if req.user_annotations:
        warnings_out.append(
            "Nhãn thủ công không được tính là dự đoán hoặc dùng tăng độ tin cậy; muốn dùng nhãn để huấn luyện hãy gọi /v1/deepforest/finetune."
        )
    lat_c = (bounds["south"] + bounds["north"]) / 2
    m_lon, m_lat = max(1.0, 111_320 * math.cos(math.radians(lat_c))), 110_574
    pixel_w = (bounds["east"] - bounds["west"]) * m_lon / width
    pixel_h = (bounds["north"] - bounds["south"]) * m_lat / height
    boxes = []
    excluded = 0
    for record in records:
        try:
            x0, y0, x1, y1, score = [
                float(record[k]) for k in ("xmin", "ymin", "xmax", "ymax", "score")
            ]
        except (KeyError, TypeError, ValueError):
            raise HTTPException(
                503, "Mô hình trả dữ liệu bounding box không hợp lệ."
            ) from None
        if (
            not all(math.isfinite(v) for v in (x0, y0, x1, y1, score))
            or not 0 <= score <= 1
        ):
            raise HTTPException(503, "Mô hình trả tọa độ hoặc độ tin cậy không hợp lệ.")
        if score < req.score_threshold:
            continue
        x0, x1 = max(0, x0), min(width, x1)
        y0, y1 = max(0, y0), min(height, y1)
        if x1 <= x0 or y1 <= y0:
            continue
        center_lon, center_lat = _pixel_geo(
            (x0 + x1) / 2, (y0 + y1) / 2, width, height, bounds
        )
        if not _point_in_ring(center_lon, center_lat, ring):
            excluded += 1
            continue
        west, north = _pixel_geo(x0, y0, width, height, bounds)
        east, south = _pixel_geo(x1, y1, width, height, bounds)
        cw, ch = (x1 - x0) * pixel_w, (y1 - y0) * pixel_h
        idx = len(boxes) + 1
        tile = next(
            (
                t
                for t in tiles
                if t.x0_px <= (x0 + x1) / 2 <= t.x1_px
                and t.y0_px <= (y0 + y1) / 2 <= t.y1_px
            ),
            None,
        )
        boxes.append(
            DeepForestCrownBox(
                box_id=f"DF-{idx:03d}",
                code=f"DF-{idx:03d}",
                label=str(record.get("label", meta["TargetClass"])),
                label_vi=meta["label_vi"],
                score=score,
                confidence_pct=round(score * 100),
                xmin_px=math.floor(x0),
                ymin_px=math.floor(y0),
                xmax_px=math.ceil(x1),
                ymax_px=math.ceil(y1),
                west=west,
                south=south,
                east=east,
                north=north,
                center_lon=center_lon,
                center_lat=center_lat,
                crown_width_m=round(cw, 3),
                crown_height_m=round(ch, 3),
                crown_diameter_m=round((cw + ch) / 2, 3),
                crown_area_m2=round(math.pi * cw * ch / 4, 3),
                tile_id=tile.tile_id if tile else "MERGED",
                row_number=0,
                status="unknown",
                status_vi="Chưa có dữ liệu sức khỏe",
            )
        )
    area = geo.area_ha(ring)
    if area <= 0:
        raise HTTPException(422, "Vùng chọn phải có diện tích dương.")
    return DeepForestPredictResponse(
        engine_runtime="weecology/DeepForest predict_tile (real CPU inference)",
        model_id=req.model_id,
        model_name=meta["name"],
        architecture=meta["architecture"],
        target_class=meta["TargetClass"],
        label_vi=meta["label_vi"],
        area_ha=round(area, 4),
        bounds=bounds,
        raster_width_px=width,
        raster_height_px=height,
        spatial_resolution_m=round((pixel_w + pixel_h) / 2, 3),
        patch_size_px=req.patch_size,
        patch_overlap=req.patch_overlap,
        iou_threshold=req.iou_threshold,
        score_threshold=req.score_threshold,
        total_tiles_processed=len(tiles),
        merged_boxes_after_nms=len(boxes),
        mean_confidence_pct=round(sum(b.score * 100 for b in boxes) / len(boxes), 2)
        if boxes
        else 0,
        mean_crown_diameter_m=round(
            sum(b.crown_diameter_m for b in boxes) / len(boxes), 3
        )
        if boxes
        else 0,
        canopy_cover_pct=round(
            min(100, sum(b.crown_area_m2 for b in boxes) / (area * 10000) * 100), 2
        ),
        tiles=tiles,
        boxes=boxes,
        overlay_url=mock_service._store(_render_overlay(rgb, boxes)),
        finetuned_checkpoint_id=req.model_id if dr.checkpoint_file(req.model_id) else None,
        source=source,
        warnings=warnings_out,
        unknown_health_count=len(boxes),
        detections_outside_roi=excluded,
        summary_vi=f"DeepForest thật đã quét ảnh RGB {width}×{height} px và trả {len(boxes)} đối tượng «{meta["label_vi"]}» có tâm trong vùng chọn sau NMS. Kết quả chưa được kiểm định ngoài thực địa; không suy ra sức khỏe từ RGB.",
    )


def start_deepforest_finetune(req: DeepForestFineTuneRequest) -> dict:
    if not req.image_id:
        raise HTTPException(
            422,
            "Fine-tune cần ảnh RGB đã tải lên (image_id từ /v1/deepforest/images) kèm nhãn trên chính ảnh đó.",
        )
    if not dr.is_known_model(req.base_model_id):
        raise HTTPException(422, "base_model_id không có trong danh sách mô hình.")
    try:
        ring = geo.validate_ring(req.region.ring)
        rgb, source = dr.load_upload(req.image_id, geo.bounds(ring))
        bounds = dr.validate_bounds(source["bounds"])
        # Fail fast on unusable labels instead of after the job has been queued.
        height, width = rgb.shape[:2]
        boxes = ft._geo_to_pixel_boxes(req.annotations, bounds, width, height)
        ft.split_train_validation(boxes, width)
    except (geo.GeoError, dr.ImageInputError, ft.FineTuneInputError) as exc:
        raise HTTPException(422, str(exc)) from exc
    return ft.start_job(req, rgb, bounds)


def make_router(auth: Callable, get_mode: Callable[[], str], cache) -> APIRouter:
    router = APIRouter(prefix="/v1/deepforest", tags=["deepforest"])

    @router.get("/models", dependencies=[Depends(auth)])
    def models():
        return list(model_catalog().values())

    @router.get("/status", dependencies=[Depends(auth)])
    def status():
        return dr.runtime.status()

    @router.post("/images", dependencies=[Depends(auth)])
    async def upload_image(
        request: Request,
        west: float | None = None,
        south: float | None = None,
        east: float | None = None,
        north: float | None = None,
    ):
        if request.headers.get("content-type", "").split(";")[
            0
        ].strip().lower() not in {
            "application/octet-stream",
            "image/png",
            "image/jpeg",
            "image/tiff",
        }:
            raise HTTPException(
                415,
                "Gửi raw PNG/JPEG/TIFF bằng Content-Type image/png, image/jpeg, image/tiff hoặc application/octet-stream.",
            )
        data = bytearray()
        async for chunk in request.stream():
            data.extend(chunk)
            if len(data) > dr.MAX_IMAGE_BYTES:
                raise HTTPException(413, "Ảnh vượt giới hạn 32 MiB.")
        coordinates = [west, south, east, north]
        bounds = (
            dict(zip(("west", "south", "east", "north"), coordinates))
            if any(v is not None for v in coordinates)
            else None
        )
        try:
            return dr.save_upload(bytes(data), bounds)
        except dr.ImageInputError as exc:
            raise HTTPException(422, str(exc)) from exc

    @router.post(
        "/predict",
        response_model=DeepForestPredictResponse,
        dependencies=[Depends(auth)],
    )
    def predict(req: DeepForestPredictRequest):
        try:
            ring = geo.validate_ring(req.region.ring)
        except geo.GeoError as exc:
            raise HTTPException(422, str(exc)) from exc
        return run_deepforest_predict_tile(ring, req)

    @router.post(
        "/finetune",
        response_model=DeepForestFineTuneJob,
        status_code=202,
        dependencies=[Depends(auth)],
    )
    def finetune(req: DeepForestFineTuneRequest):
        return start_deepforest_finetune(req)

    @router.get(
        "/finetune/{job_id}",
        response_model=DeepForestFineTuneJob,
        dependencies=[Depends(auth)],
    )
    def finetune_status(job_id: str):
        job = ft.get_job(job_id)
        if job is None:
            raise HTTPException(404, "Không tìm thấy tác vụ fine-tune.")
        return job

    @router.get("/vietnam-sources", dependencies=[Depends(auth)])
    def vietnam_sources():
        """Danh mục nguồn ảnh vệ tinh/UAV độ phân giải cao (<0.5m-1m/px), bộ dữ liệu
        lâm nghiệp - nông nghiệp Việt Nam và quy trình 3 bước tạo dataset DeepForest.
        """
        return vds.get_vietnam_deepforest_catalog()

    @router.get("/oam/search", dependencies=[Depends(auth)])
    def oam_search(
        region_preset: str = "vietnam_all",
        west: float | None = None,
        south: float | None = None,
        east: float | None = None,
        north: float | None = None,
        limit: int = 12,
        max_gsd_m: float = 0.50,
    ):
        """Tìm kiếm ảnh Orthomosaic Drone/UAV mở trên OpenAerialMap tại Việt Nam."""
        coords = [west, south, east, north]
        if any(v is not None for v in coords) and not all(v is not None for v in coords):
            raise HTTPException(
                422,
                "Phải truyền đủ cả 4 tọa độ west, south, east, north hoặc để trống dùng region_preset.",
            )
        bbox = (
            [float(west), float(south), float(east), float(north)]
            if all(v is not None for v in coords)
            else None
        )
        try:
            return vds.search_openaerialmap_vietnam(
                bbox=bbox,
                region_preset=region_preset,
                limit=limit,
                max_gsd_m=max_gsd_m,
            )
        except ValueError as exc:
            raise HTTPException(422, str(exc)) from exc

    @router.post("/dataset/xml-to-csv", dependencies=[Depends(auth)])
    def dataset_xml_to_csv(req: PascalVocToCsvRequest):
        """Bước 3: Chuyển đổi nhãn PASCAL VOC (.xml từ LabelImg/Roboflow) sang chuẩn CSV
        của DeepForest (`image_path, xmin, ymin, xmax, ymax, label`).
        """
        try:
            docs = [
                {"filename": d.filename, "xml_content": d.xml_content}
                for d in req.xml_documents
            ]
            return vds.convert_xml_strings_to_deepforest_csv(
                xml_documents=docs,
                val_ratio=req.val_ratio,
                normalize_label_to_tree=req.normalize_label_to_tree,
            )
        except ValueError as exc:
            raise HTTPException(422, str(exc)) from exc

    return router


class PascalVocXmlDocument(BaseModel):
    filename: str = Field(default="vietnam_garden_01.png")
    xml_content: str = Field(..., min_length=10)


class PascalVocToCsvRequest(BaseModel):
    xml_documents: list[PascalVocXmlDocument] = Field(..., min_length=1)
    val_ratio: float = Field(default=0.2, ge=0.0, le=0.5)
    normalize_label_to_tree: bool = Field(default=False)
