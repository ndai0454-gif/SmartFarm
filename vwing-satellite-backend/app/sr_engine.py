"""Real 4x learned RGBN SR, with native measurements kept separate from inferred pixels."""
from functools import lru_cache
from pathlib import Path
import hashlib
import json
import math
import numpy as np
from .sr_errors import SrInputError

MODEL = "ESAOpenSR SEN2SRLite NonReference_RGBN_x4"
REVISION = "469e9b3a049f9320ba66b3a39b9655dbd31480b3"
BANDS = ["B4", "B3", "B2", "B8"]
ROOT = Path(__file__).resolve().parents[1]


def _require_clear_coverage(coverage: float, *, context: bool = False):
    """Reject poor observations without presenting a healthy model as broken."""
    if not math.isfinite(coverage) or not 0 <= coverage <= 1:
        raise SrInputError("Không xác định được độ phủ ảnh hợp lệ. Hãy chọn khoảng ngày khác.")
    if coverage < .5:
        area = "khung ảnh lân cận" if context else "vùng đã chọn của cảnh tốt nhất"
        raise SrInputError(
            f"Chỉ {coverage * 100:.1f}% {area} có điểm ảnh không mây (cần ít nhất 50%). "
            "Hãy mở rộng khoảng ngày trong giới hạn 93 ngày hoặc chọn kỳ ít mây hơn; "
            "tăng ngưỡng mây của cảnh không loại bỏ mây trên vườn.")


@lru_cache(maxsize=1)
def load_model():
    import torch
    from safetensors.torch import load_file
    from sen2sr.models.opensr_baseline.cnn import CNNSR
    from sen2sr.models.tricks import HardConstraint
    from sen2sr.nonreference import srmodel
    directory = ROOT / "models/sen2srlite-rgbn-x4"
    from setup_sr import EXPECTED_SHA
    if not (directory / "provenance.json").is_file():
        raise RuntimeError("Chưa cài mô hình SEN2SRLite. Chạy setup_sr.py trên máy chủ.")
    provenance = json.loads((directory / "provenance.json").read_text())
    if provenance["revision"] != REVISION:
        raise RuntimeError("Phiên bản trọng số Super-Resolution không khớp.")
    for name in ("model.safetensor", "hard_constraint.safetensor"):
        if hashlib.sha256((directory / name).read_bytes()).hexdigest() != EXPECTED_SHA[name]:
            raise RuntimeError("Trọng số Super-Resolution không toàn vẹn; chạy setup_sr.py lại.")
    # Same architecture and Fourier constraint as the pinned official load.py.
    torch.set_num_threads(min(4, torch.get_num_threads()))
    device = "cpu"  # tested portable path; CUDA can be configured separately after profiling
    network = CNNSR(4, 4, 24, 4, True, False, 6)
    network.load_state_dict(load_file(directory / "model.safetensor"))
    network.eval().to(device)
    for parameter in network.parameters():
        parameter.requires_grad = False
    hard = HardConstraint(low_pass_mask=load_file(directory / "hard_constraint.safetensor")["weights"].to(device), device=device)
    return srmodel(network, hard, device).eval(), provenance


def infer(data):
    import torch
    if data.shape != (4, 128, 128) or not np.isfinite(data).all():
        raise ValueError("Đầu vào SR phải là bốn băng phản xạ hữu hạn 128×128.")
    model, provenance = load_model()
    with torch.inference_mode():
        output = model(torch.from_numpy(np.ascontiguousarray(data, dtype=np.float32)).unsqueeze(0))
    result = output.squeeze(0).float().cpu().numpy()
    if result.shape != (4, 512, 512) or not np.isfinite(result).all():
        raise RuntimeError("Mô hình Super-Resolution trả ảnh không hợp lệ.")
    return result, provenance


def source_patch(req, ring):
    """Select one genuine observation, with context before masking/cropping the garden."""
    import ee
    import requests
    import rasterio
    from rasterio.io import MemoryFile
    from rasterio.transform import Affine
    from rasterio.warp import transform, transform_geom
    from .ee_service import _collection, _geom, _scl_mask
    lon = (min(p[0] for p in ring) + max(p[0] for p in ring)) / 2
    lat = (min(p[1] for p in ring) + max(p[1] for p in ring)) / 2
    if not -80 <= lat <= 84 or max(p[0] for p in ring) - min(p[0] for p in ring) >= 180:
        raise SrInputError("SR hiện hỗ trợ vùng UTM từ 80° Nam đến 84° Bắc, không qua kinh tuyến 180°.")
    zone = min(60, int((lon + 180) // 6) + 1)
    crs = f"EPSG:{32600 + zone if lat >= 0 else 32700 + zone}"
    xs, ys = transform("EPSG:4326", crs, [p[0] for p in ring], [p[1] for p in ring])
    if max(xs) - min(xs) > 960 or max(ys) - min(ys) > 960:
        raise SrInputError("Vùng SR phải nằm trong khung 960×960 m để giữ đủ ngữ cảnh cho mô hình.")
    left = math.floor(((min(xs) + max(xs)) / 2 - 640) / 10) * 10
    top = math.ceil(((min(ys) + max(ys)) / 2 + 640) / 10) * 10
    affine = Affine(10, 0, left, 0, -10, top)
    geom = _geom(ring)
    context = ee.Geometry.Rectangle([left, top - 1280, left + 1280, top], proj=crs, geodesic=False)
    collection = _collection(geom, req.start, req.end, req.max_cloud_pct).sort("CLOUDY_PIXEL_PERCENTAGE").limit(10)
    size = collection.size().getInfo()
    if not size:
        raise SrInputError("Không có cảnh Sentinel-2 trong khoảng ngày/ngưỡng mây đã chọn.")
    candidates = collection.toList(size)
    best = None
    for i in range(size):
        scene = ee.Image(candidates.get(i))
        valid = _scl_mask(scene).select("B4").mask().unmask(0).rename("valid")
        stats = ee.Dictionary({
            "geom": valid.reduceRegion(ee.Reducer.mean(), geom, 10, maxPixels=1_000_000).get("valid"),
            "ctx": valid.reduceRegion(ee.Reducer.mean(), context, 10, maxPixels=1_000_000).get("valid"),
        }).getInfo() or {}
        coverage = float(stats.get("geom") or 0.0)
        ctx_coverage = float(stats.get("ctx") or 0.0)
        score = (coverage >= .5 and ctx_coverage >= .5, min(coverage, ctx_coverage), coverage, ctx_coverage)
        if best is None or score > best[0]:
            best = score, coverage, ctx_coverage, scene, valid
        if coverage >= .95 and ctx_coverage >= .95:
            break
    _, coverage, ctx_coverage, scene, valid = best
    if coverage < .5 or ctx_coverage < .5:
        comp_masked = collection.map(_scl_mask).select(BANDS).median()
        filled = _scl_mask(scene).select(BANDS).unmask(comp_masked)
        valid = filled.select("B4").mask().unmask(0).rename("valid")
        coverage = float(valid.reduceRegion(ee.Reducer.mean(), geom, 10, maxPixels=1_000_000).get("valid").getInfo() or coverage)
        _require_clear_coverage(coverage)
        stack = filled.multiply(.0001).unmask(0).addBands(valid).toFloat()
    else:
        _require_clear_coverage(coverage)
        # S2_SR_HARMONIZED DN is reflectance scaled 10000. Do not infer from rendered thumbnails.
        stack = _scl_mask(scene).select(BANDS).multiply(.0001).unmask(0).addBands(valid).toFloat()
    # Transform + dimensions fully define the grid. EE rejects these with a third region argument.
    url = stack.clip(context).getDownloadURL({"crs": crs, "crs_transform": list(affine)[:6], "dimensions": [128, 128], "format": "GEO_TIFF", "filePerBand": False})
    with requests.get(url, timeout=(15, 120), stream=True) as response:
        response.raise_for_status()
        parts = []; length = 0
        for chunk in response.iter_content(8192):
            length += len(chunk)
            if length > 4 * 1024 * 1024:
                raise ValueError("Ảnh nguồn vượt giới hạn tải SR.")
            parts.append(chunk)
    with MemoryFile(b"".join(parts)) as mem, mem.open() as ds:
        if ds.width != 128 or ds.height != 128 or ds.count != 5 or ds.crs != rasterio.crs.CRS.from_string(crs) or not ds.transform.almost_equals(affine):
            raise ValueError("Lưới ảnh nguồn không khớp 10 m / 128×128; không chạy mô hình.")
        array = ds.read()
    polygon = transform_geom("EPSG:4326", crs, {"type": "Polygon", "coordinates": [ring]})
    metadata = scene.toDictionary(["system:index", "system:time_start", "CLOUDY_PIXEL_PERCENTAGE"]).getInfo()
    return array[:4], array[4] >= .5, affine, crs, polygon, coverage, metadata


def write_geotiff(path, data, valid, transform, crs, polygon, tags):
    import rasterio
    from rasterio.features import geometry_mask, geometry_window
    from rasterio.io import MemoryFile
    from rasterio.windows import transform as window_transform
    inside = geometry_mask([polygon], out_shape=data.shape[1:], transform=transform, invert=True)
    mask = valid & inside & np.isfinite(data).all(axis=0)
    with MemoryFile() as mem:
        with mem.open(driver="GTiff", width=data.shape[2], height=data.shape[1], count=4, dtype="float32", crs=crs, transform=transform) as ref:
            window = geometry_window(ref, [polygon])
    row, col = int(window.row_off), int(window.col_off)
    height, width = int(window.height), int(window.width)
    cropped = data[:, row:row+height, col:col+width].astype(np.float32).copy()
    cropped_mask = mask[row:row+height, col:col+width]
    if not cropped_mask.any():
        raise ValueError("Không còn điểm ảnh hợp lệ trong polygon sau khi cắt vùng.")
    cropped[:, ~cropped_mask] = np.nan
    out_transform = window_transform(window, transform)
    with rasterio.open(path, "w", driver="GTiff", width=width, height=height, count=4, dtype="float32", crs=crs, transform=out_transform, nodata=np.nan, compress="deflate", predictor=3) as ds:
        ds.write(cropped); ds.update_tags(**tags)
        for i, name in enumerate(BANDS, 1): ds.set_band_description(i, name)
        ds.write_mask(cropped_mask.astype("uint8") * 255)
    return width, height


def previews(source, enhanced, folder):
    """Both previews share exactly the same WGS84 bounds and display stretch."""
    import rasterio
    from rasterio.warp import transform_bounds, reproject, Resampling
    from rasterio.transform import from_bounds
    from PIL import Image
    with rasterio.open(enhanced) as ds:
        bounds = transform_bounds(ds.crs, "EPSG:4326", *ds.bounds, densify_pts=21)
        width = min(512, max(64, ds.width * 2)); height = min(512, max(64, ds.height * 2))
    target = from_bounds(*bounds, width, height)
    for path, filename in ((source, "original.png"), (enhanced, "enhanced.png")):
        array = np.full((3, height, width), np.nan, dtype="float32")
        with rasterio.open(path) as ds:
            for band in range(3):
                reproject(rasterio.band(ds, band+1), array[band], src_transform=ds.transform, src_crs=ds.crs, src_nodata=np.nan, dst_transform=target, dst_crs="EPSG:4326", dst_nodata=np.nan, resampling=Resampling.nearest)
        valid = np.isfinite(array).all(axis=0)
        rgb = (np.clip(np.nan_to_num(array) / .3, 0, 1) ** (1 / 2.2) * 255).astype("uint8").transpose(1, 2, 0)
        rgba = np.dstack([rgb, valid.astype("uint8") * 255])
        Image.fromarray(rgba).save(folder / filename)
    return dict(west=bounds[0], south=bounds[1], east=bounds[2], north=bounds[3])


def generate_sr(req, ring, output_dir: Path):
    from datetime import datetime, timezone
    import rasterio
    from rasterio.warp import reproject, Resampling
    from rasterio.transform import Affine
    native, valid, transform, crs, polygon, coverage, scene = source_patch(req, ring)
    _require_clear_coverage(float(valid.mean()), context=True)
    sr, provenance = infer(native)
    sr_transform = transform @ Affine.scale(.25)
    sr_valid = valid.repeat(4, axis=0).repeat(4, axis=1)
    output = sr; output_valid = sr_valid; output_transform = sr_transform
    if req.output_pixel_size_m == 1.0:
        output = np.zeros((4, 1280, 1280), dtype="float32")
        output_transform = transform @ Affine.scale(.1)
        for band in range(4):
            reproject(sr[band], output[band], src_transform=sr_transform, src_crs=crs, dst_transform=output_transform, dst_crs=crs, resampling=Resampling.bilinear)
        output_valid = valid.repeat(10, axis=0).repeat(10, axis=1)
    output_dir.mkdir(parents=True, exist_ok=True)
    tags = {"model": MODEL, "model_revision": REVISION, "native_pixel_size_m": "10", "model_pixel_size_m": "2.5", "output_pixel_size_m": str(req.output_pixel_size_m), "source_scene": scene["system:index"], "bands": "B04,B03,B02,B08", "units": "surface_reflectance", "inferred": "true", "caution": "AI inferred detail; not native 1m observation, elevation, LiDAR, or tree diagnosis"}
    write_geotiff(output_dir / "original.tif", native, valid, transform, crs, polygon, {**tags, "inferred": "false", "output_pixel_size_m": "10"})
    width, height = write_geotiff(output_dir / "enhanced.tif", output, output_valid, output_transform, crs, polygon, tags)
    bounds = previews(output_dir / "original.tif", output_dir / "enhanced.tif", output_dir)
    warnings = ["Chi tiết AI suy diễn, chưa có ảnh độ phân giải cao tại vườn để kiểm chứng.", "Không phải dữ liệu cao độ/DEM, LiDAR hoặc kết luận sức khỏe từng cây.", "Mây/thiếu dữ liệu được giữ là nodata; vùng lân cận có thể ảnh hưởng chi tiết mô hình."]
    if req.output_pixel_size_m == 1.0:
        warnings.insert(0, "Lưới xuất 1 m được nội suy bilinear từ SR 2,5 m; không phải mô hình 10× hoặc quan sát gốc 1 m.")
    result = {"model": MODEL, "model_revision": REVISION, "model_weights_sha256": provenance["sha256"], "source_scene": scene["system:index"], "source_date": datetime.fromtimestamp(scene["system:time_start"] / 1000, tz=timezone.utc).date().isoformat(), "source_dataset": "COPERNICUS/S2_SR_HARMONIZED", "native_pixel_size_m": 10, "model_pixel_size_m": 2.5, "output_pixel_size_m": req.output_pixel_size_m, "crs": crs, "bounds": bounds, "width": width, "height": height, "valid_coverage_pct": round(coverage * 100, 1), "method": "Một cảnh ít mây → phản xạ RGBN 10 m, ngữ cảnh 128×128 → SEN2SRLite SPAN ×4 + Fourier hard constraint → cắt polygon" + (" → nội suy lưới 1 m" if req.output_pixel_size_m == 1 else ""), "warnings": warnings, "files": {"original_preview": "original.png", "enhanced_preview": "enhanced.png", "original_geotiff": "original.tif", "enhanced_geotiff": "enhanced.tif", "metadata": "metadata.json"}}
    (output_dir / "metadata.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    return result
