"""Real, lazy DeepForest inference and validated RGB image ingestion.

Importing this module never imports torch or downloads model weights. No mock
images, detections or spectral indices are produced here.
"""

from __future__ import annotations

import importlib.util
import io
import json
import math
import os
import re
import threading
import urllib.parse
import urllib.request
import uuid
import warnings
from pathlib import Path

import numpy as np
from PIL import Image, UnidentifiedImageError

MAX_IMAGE_BYTES = 32 * 1024 * 1024
MAX_IMAGE_PIXELS = 25_000_000
UPLOAD_DIR = (
    Path(__file__).resolve().parents[1] / "artifacts" / "deepforest" / "uploads"
)
MODEL_ID = "weecology/deepforest-tree"
ESRI_EXPORT = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/export"


class RuntimeUnavailable(RuntimeError):
    pass


class ImageInputError(ValueError):
    pass


def validate_bounds(bounds: dict) -> dict:
    try:
        b = {k: float(bounds[k]) for k in ("west", "south", "east", "north")}
    except (KeyError, TypeError, ValueError):
        raise ImageInputError("Cần đủ west, south, east, north hợp lệ.") from None
    if (
        not all(math.isfinite(v) for v in b.values())
        or not -180 <= b["west"] < b["east"] <= 180
        or not -90 <= b["south"] < b["north"] <= 90
    ):
        raise ImageInputError(
            "Khung tọa độ không hợp lệ hoặc vượt kinh/vĩ độ cho phép."
        )
    return b


def decode_rgb(data: bytes) -> np.ndarray:
    if not data or len(data) > MAX_IMAGE_BYTES:
        raise ImageInputError("Ảnh rỗng hoặc vượt giới hạn 32 MiB.")
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            with Image.open(io.BytesIO(data)) as image:
                if image.format not in {"PNG", "JPEG", "TIFF"}:
                    raise ImageInputError("Chỉ chấp nhận ảnh PNG, JPEG hoặc TIFF RGB.")
                if image.width * image.height > MAX_IMAGE_PIXELS:
                    raise ImageInputError("Ảnh vượt giới hạn 25 triệu pixel.")
                if image.width < 16 or image.height < 16:
                    raise ImageInputError("Ảnh phải có ít nhất 16 pixel mỗi chiều.")
                if image.mode not in {"RGB", "RGBA"}:
                    raise ImageInputError(
                        "Cần ảnh màu RGB 8-bit; không tự chuyển ảnh đa phổ hoặc thang xám."
                    )
                if image.mode == "RGBA" and image.getextrema()[3][0] != 255:
                    raise ImageInputError(
                        "Ảnh có pixel trong suốt/no-data; cần ảnh RGB đầy đủ."
                    )
                return np.asarray(image.convert("RGB")).copy()
    except ImageInputError:
        raise
    except (
        UnidentifiedImageError,
        OSError,
        ValueError,
        Image.DecompressionBombError,
        Image.DecompressionBombWarning,
    ):
        raise ImageInputError("Không giải mã được ảnh RGB hợp lệ.") from None


def save_upload(data: bytes, bounds: dict | None = None) -> dict:
    rgb = decode_rgb(data)
    bounds = validate_bounds(bounds) if bounds is not None else None
    image_id = uuid.uuid4().hex
    UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
    # Preserve the bounded original: recompressing a large JPEG to PNG could
    # exceed the upload byte limit and make the saved image unreadable later.
    (UPLOAD_DIR / f"{image_id}.image").write_bytes(data)
    meta = {
        "image_id": image_id,
        "width": int(rgb.shape[1]),
        "height": int(rgb.shape[0]),
        "bounds": bounds,
        "source": "user_uploaded_rgb",
        "capture_date": None,
        "georeference": "user_declared_bounds"
        if bounds
        else "requires_prediction_region_bounds",
        "warnings": [
            "Ảnh tải lên không được tự xác minh ngày chụp hoặc tọa độ; chỉ hỗ trợ ảnh north-up, WGS84 theo khung khai báo."
        ],
    }
    (UPLOAD_DIR / f"{image_id}.json").write_text(
        json.dumps(meta, ensure_ascii=False), encoding="utf-8"
    )
    return meta


def load_upload(image_id: str, region_bounds: dict) -> tuple[np.ndarray, dict]:
    try:
        if uuid.UUID(image_id).hex != image_id:
            raise ValueError
    except (ValueError, AttributeError):
        raise ImageInputError("image_id không hợp lệ.") from None
    try:
        meta = json.loads((UPLOAD_DIR / f"{image_id}.json").read_text(encoding="utf-8"))
        rgb = decode_rgb((UPLOAD_DIR / f"{image_id}.image").read_bytes())
    except FileNotFoundError:
        raise ImageInputError(
            "Không tìm thấy ảnh đã tải lên; hãy tải ảnh trước."
        ) from None
    if meta.get("bounds") is None:
        meta["bounds"] = validate_bounds(region_bounds)
        meta["georeference"] = "assumed_from_prediction_region"
        meta["warnings"].append(
            "Chưa có georeference: giả định toàn bộ ảnh phủ chính xác khung vùng chọn, không xác minh được ngoài thực địa."
        )
    return rgb, meta


def _read_url(url: str, max_bytes: int = MAX_IMAGE_BYTES) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "VWing-DeepForest/1.0"})
    with urllib.request.urlopen(req, timeout=40) as response:
        data = response.read(max_bytes + 1)
    if len(data) > max_bytes:
        raise ImageInputError("Nguồn ảnh vượt giới hạn dung lượng.")
    return data


def _fetch_esri_export_rgb(bounds: dict) -> tuple[np.ndarray, dict]:
    """Fetch one complete export, retaining the service's actual output extent."""
    bounds = validate_bounds(bounds)
    lon_span, lat_span = (
        bounds["east"] - bounds["west"],
        bounds["north"] - bounds["south"],
    )
    # Match geographic aspect ratio because imageSR is EPSG:4326.
    scale = 1024 / max(lon_span, lat_span)
    width, height = max(16, round(lon_span * scale)), max(16, round(lat_span * scale))
    query = urllib.parse.urlencode(
        {
            "bbox": ",".join(
                str(bounds[k]) for k in ("west", "south", "east", "north")
            ),
            "bboxSR": 4326,
            "imageSR": 4326,
            "size": f"{width},{height}",
            "format": "png32",
            "transparent": "true",
            "f": "json",
        }
    )
    source_url = f"{ESRI_EXPORT}?{query}"
    try:
        payload = json.loads(_read_url(source_url, 1024 * 1024))
        extent = payload["extent"]
        actual = validate_bounds(
            {
                "west": extent["xmin"],
                "south": extent["ymin"],
                "east": extent["xmax"],
                "north": extent["ymax"],
            }
        )
        href = payload["href"]
        parsed = urllib.parse.urlparse(href)
        if parsed.scheme != "https" or parsed.hostname != "server.arcgisonline.com":
            raise ImageInputError("Nguồn export trả địa chỉ ảnh không hợp lệ.")
        rgb = decode_rgb(_read_url(href))
        if (
            int(payload["width"]) != rgb.shape[1]
            or int(payload["height"]) != rgb.shape[0]
        ):
            raise ImageInputError("Kích thước ảnh không khớp metadata export.")
        if np.max(rgb) - np.min(rgb) < 2:
            raise ImageInputError("Nguồn ảnh không có nội dung sử dụng được.")
    except Exception as exc:
        raise RuntimeUnavailable(
            "Không tải được ảnh RGB đầy đủ từ Esri World Imagery. Hãy tải ảnh drone/orthomosaic PNG, JPEG hoặc TIFF RGB qua /v1/deepforest/images."
        ) from exc
    return rgb, {
        "source": "Esri World Imagery export",
        "source_url": source_url,
        "attribution": "Esri, Maxar, Earthstar Geographics, and the GIS User Community",
        "capture_date": None,
        "bounds": actual,
        "georeference": "service_export_extent_epsg4326",
        "width": int(rgb.shape[1]),
        "height": int(rgb.shape[0]),
        "warnings": [
            "Ngày chụp và độ phân giải cảm biến gốc chưa xác định; khoảng cách pixel đầu ra không chứng minh độ phân giải gốc.",
            "Mô hình cây NEON chưa được kiểm định cho khu vực/vườn Việt Nam này.",
        ],
    }


def _is_esri_no_data_tile(rgb: np.ndarray) -> bool:
    """Recognize Esri's gray 204 'Map data not yet available' placeholder.

    This is deliberately provider-specific and only used on downloaded map
    tiles. A genuine image yielding zero tree predictions remains valid.
    """
    near_background = np.all(np.abs(rgb.astype(np.int16) - 204) <= 2, axis=2)
    chromatic = np.ptp(rgb.astype(np.int16), axis=2) > 15
    return bool(np.mean(near_background) > 0.80 and np.mean(chromatic) < 0.01)


def fetch_esri_rgb(bounds: dict) -> tuple[np.ndarray, dict]:
    failures = []
    for zoom in (19, 18, 17, 16):
        try:
            rgb, meta = _fetch_esri_tiles_at_zoom(bounds, zoom)
            if failures:
                meta["warnings"].append(
                    f"Ảnh zoom cao không khả dụng đầy đủ; đã dùng toàn bộ mosaic Esri ở zoom {meta['tile_zoom']}."
                )
            return rgb, meta
        except RuntimeUnavailable as exc:
            failures.append(exc)
    raise RuntimeUnavailable(
        "Esri không cung cấp đầy đủ ảnh thật ở zoom 19–16 cho vùng chọn; ảnh no-data bị từ chối. Hãy tải ảnh drone/orthomosaic RGB qua /v1/deepforest/images."
    ) from failures[-1]


def _fetch_esri_tiles_at_zoom(
    bounds: dict, requested_zoom: int
) -> tuple[np.ndarray, dict]:
    """Mosaic Esri-only tiles, requiring every tile, then warp to north-up WGS84.

    World Imagery commonly disables /export. Tile pixels are Web Mercator;
    assigning a linear latitude transform without reprojection would shift boxes.
    """
    from concurrent.futures import ThreadPoolExecutor

    bounds = validate_bounds(bounds)
    if bounds["south"] < -85.05 or bounds["north"] > 85.05:
        raise ImageInputError("Nguồn Web Mercator không hỗ trợ vùng cực này.")

    def tile_range(zoom):
        n = 2**zoom

        def tx(lon):
            return (lon + 180) / 360 * n

        def ty(lat):
            return (1 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2 * n

        x0, x1 = math.floor(tx(bounds["west"])), math.ceil(tx(bounds["east"])) - 1
        y0, y1 = math.floor(ty(bounds["north"])), math.ceil(ty(bounds["south"])) - 1
        return x0, max(x0, x1), y0, max(y0, y1)

    zoom = requested_zoom
    x0, x1, y0, y1 = tile_range(zoom)
    while (x1 - x0 + 1) * (y1 - y0 + 1) > 36 and zoom > 2:
        zoom -= 1
        x0, x1, y0, y1 = tile_range(zoom)
    urls = [
        f"https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{zoom}/{y}/{x}"
        for y in range(y0, y1 + 1)
        for x in range(x0, x1 + 1)
    ]
    coords = [(x, y) for y in range(y0, y1 + 1) for x in range(x0, x1 + 1)]
    try:

        def download(url):
            image = decode_rgb(_read_url(url))
            if image.shape != (256, 256, 3):
                raise ImageInputError("Tile ảnh vệ tinh không đủ 256×256 RGB.")
            if _is_esri_no_data_tile(image):
                raise ImageInputError(
                    "Esri trả tile no-data 'Map data not yet available'."
                )
            return image

        with ThreadPoolExecutor(max_workers=8) as executor:
            images = list(executor.map(download, urls))
        canvas = np.zeros(((y1 - y0 + 1) * 256, (x1 - x0 + 1) * 256, 3), dtype=np.uint8)
        for (x, y), image in zip(coords, images):
            canvas[
                (y - y0) * 256 : (y - y0 + 1) * 256, (x - x0) * 256 : (x - x0 + 1) * 256
            ] = image
        import rasterio.warp
        from rasterio.transform import from_bounds

        half_world = 20037508.342789244
        tile_span = 2 * half_world / (2**zoom)
        mercator_extent = (
            -half_world + x0 * tile_span,
            half_world - (y1 + 1) * tile_span,
            -half_world + (x1 + 1) * tile_span,
            half_world - y0 * tile_span,
        )
        lat_c = (bounds["north"] + bounds["south"]) / 2
        output_m = 156543.033928 * math.cos(math.radians(lat_c)) / (2**zoom)
        physical_w = (
            (bounds["east"] - bounds["west"]) * 111320 * math.cos(math.radians(lat_c))
        )
        physical_h = (bounds["north"] - bounds["south"]) * 110574
        ratio = max(1, max(physical_w, physical_h) / (1536 * output_m))
        width, height = (
            max(16, round(physical_w / (output_m * ratio))),
            max(16, round(physical_h / (output_m * ratio))),
        )
        rgb = np.zeros((height, width, 3), dtype=np.uint8)
        src_transform = from_bounds(*mercator_extent, canvas.shape[1], canvas.shape[0])
        dst_transform = from_bounds(
            bounds["west"],
            bounds["south"],
            bounds["east"],
            bounds["north"],
            width,
            height,
        )
        for band in range(3):
            rasterio.warp.reproject(
                source=canvas[:, :, band],
                destination=rgb[:, :, band],
                src_transform=src_transform,
                src_crs="EPSG:3857",
                dst_transform=dst_transform,
                dst_crs="EPSG:4326",
                resampling=rasterio.warp.Resampling.bilinear,
            )
        if np.max(rgb) - np.min(rgb) < 2:
            raise ImageInputError("Nguồn ảnh không có nội dung sử dụng được.")
    except Exception as exc:
        raise RuntimeUnavailable(
            "Không tải/ghép được đầy đủ ảnh Esri World Imagery thật. Không tạo ảnh thay thế; hãy tải ảnh RGB qua /v1/deepforest/images."
        ) from exc
    return rgb, {
        "source": "Esri World Imagery tiles",
        "source_url": "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer",
        "tile_urls": urls,
        "tile_zoom": zoom,
        "tiles_downloaded": len(images),
        "attribution": "Esri, Maxar, Earthstar Geographics, and the GIS User Community",
        "capture_date": None,
        "bounds": bounds,
        "georeference": "esri_web_mercator_tiles_reprojected_epsg4326",
        "width": width,
        "height": height,
        "warnings": [
            "Ngày chụp và độ phân giải cảm biến gốc chưa xác định; khoảng cách pixel đầu ra không chứng minh độ phân giải gốc.",
            "Mô hình cây NEON chưa được kiểm định cho khu vực/vườn Việt Nam này.",
        ],
    }


CHECKPOINT_DIR = (
    Path(__file__).resolve().parents[1] / "artifacts" / "deepforest" / "checkpoints"
)
HUB_MODEL_IDS = (
    MODEL_ID,
    "weecology/deepforest-bird",
    "weecology/deepforest-livestock",
)
_CHECKPOINT_ID = re.compile(r"^ft-[0-9a-f]{12}$")
CPU_ARGS = {"accelerator": "cpu", "devices": 1, "workers": 0, "batch_size": 1}


def checkpoint_file(model_id: str) -> Path | None:
    """Path of a locally fine-tuned checkpoint, or None for an id that is not one."""
    if not _CHECKPOINT_ID.match(model_id or ""):
        return None
    return CHECKPOINT_DIR / model_id / "model.ckpt"


def is_known_model(model_id: str) -> bool:
    if model_id in HUB_MODEL_IDS:
        return True
    path = checkpoint_file(model_id)
    return bool(path and path.is_file())


class DeepForestRuntime:
    def __init__(self):
        self._lock = threading.RLock()
        self._models: dict = {}
        self._last_error = None
        self._last_error_type = None

    @property
    def _model(self):
        return self._models.get(MODEL_ID)

    @_model.setter
    def _model(self, value):
        if value is None:
            self._models.pop(MODEL_ID, None)
        else:
            self._models[MODEL_ID] = value

    def status(self) -> dict:
        dependencies = {
            name: importlib.util.find_spec(name) is not None
            for name in ("torch", "deepforest")
        }
        return {
            "dependencies": dependencies,
            "model_loaded": bool(self._models),
            "loaded_models": sorted(self._models),
            "ready": MODEL_ID in self._models,
            "state": "ready"
            if self._models
            else ("unavailable" if self._last_error else "not_loaded"),
            "model_id": MODEL_ID,
            "device": "cpu",
            "last_error": self._last_error,
            "last_error_type": self._last_error_type,
            "capabilities": {
                "real_inference": True,
                "sliding_window_nms": True,
                "rgb_health_indices": False,
                "fine_tuning_api": True,
                "additional_species_models": True,
            },
            "note": "Mô hình được nạp khi dự đoán đầu tiên; cần gói DeepForest và trọng số tải được từ Hugging Face hoặc cache cục bộ.",
        }

    @staticmethod
    def build_model(model_id: str):
        """Fresh, uncached model instance (used as the starting point of fine-tuning)."""
        from deepforest import main

        checkpoint = checkpoint_file(model_id)
        if checkpoint is not None:
            return main.deepforest.load_from_checkpoint(
                str(checkpoint), map_location="cpu", config_args=dict(CPU_ARGS)
            )
        if model_id not in HUB_MODEL_IDS:
            raise ValueError(model_id)
        return main.deepforest(
            config_args={
                **CPU_ARGS,
                "model": {
                    "name": model_id,
                    "revision": os.environ.get("VWING_DEEPFOREST_REVISION", "main"),
                },
            }
        )

    def _load_model(self, model_id: str = MODEL_ID):
        model = self._models.get(model_id)
        if model is not None:
            return model
        try:
            model = self.build_model(model_id)
            model.model.eval()
            self._models[model_id] = model
            self._last_error = self._last_error_type = None
            return model
        except Exception as exc:
            self._last_error_type = type(exc).__name__
            self._last_error = "Không nạp được mô hình DeepForest thật; kiểm tra dependency và cache/kết nối Hugging Face trên máy chủ."
            raise RuntimeUnavailable(self._last_error) from exc

    def predict(
        self,
        rgb: np.ndarray,
        *,
        patch_size: int,
        patch_overlap: float,
        iou_threshold: float,
        score_threshold: float,
        model_id: str = MODEL_ID,
    ) -> list[dict]:
        with self._lock:
            model = self._load_model(model_id)
            try:
                model.config.score_thresh = score_threshold
                model.model.score_thresh = score_threshold
                # Current repository's PredictionDataset consumes RGB directly.
                frame = model.predict_tile(
                    image=rgb,
                    patch_size=patch_size,
                    patch_overlap=patch_overlap,
                    iou_threshold=iou_threshold,
                )
                if frame is None or frame.empty:
                    return []
                return frame.to_dict(orient="records")
            except Exception as exc:
                raise RuntimeUnavailable(
                    "DeepForest không hoàn thành suy luận thật; kiểm tra log máy chủ và dữ liệu RGB."
                ) from exc


runtime = DeepForestRuntime()
