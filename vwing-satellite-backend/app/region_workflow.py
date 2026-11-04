"""Selected-region multi-index report; actual change uses paired, aligned valid pixels.

This deliberately uses native Sentinel-2 reflectance, never super-resolution output.
All ends are exclusive. SCL filtering and index formulas reuse the existing service.
"""
from datetime import date
from io import BytesIO
import math
from urllib.request import urlopen

import numpy as np

from . import geo, indices as ix, mock_service
from .schemas import (Bounds, Legend, RegionWorkflowResult, WorkflowChange,
                      WorkflowIndexStats, WorkflowPeriod)

PALETTE = ["b2182b", "f7f7f7", "1a9850"]
NODATA = -9999.0
CAUTION = ("Biến động chỉ số không xác định bệnh, thiếu dinh dưỡng hay thiệt hại. "
           "Mùa vụ, thời tiết, góc chụp và mây còn sót đều có thể gây thay đổi; cần kiểm tra thực địa. "
           "Ảnh 10–20 m không đủ kết luận từng cây; đây không phải dữ liệu LiDAR hoặc độ cao.")


def paired_change(baseline, comparison, roi, pixel_ha, threshold):
    """No-data is excluded from BOTH periods; classification thresholds are symmetric.

    Areas use per-cell physical hectares. Empty common support yields null change,
    never a fabricated zero. This is used by the real EE download path and mock.
    """
    b, c = np.asarray(baseline, dtype=float), np.asarray(comparison, dtype=float)
    r = np.asarray(roi, dtype=bool)
    a = np.broadcast_to(np.asarray(pixel_ha, dtype=float), b.shape)
    if b.shape != c.shape or b.shape != r.shape:
        raise ValueError("Paired rasters must have the same aligned shape.")
    if not 0.01 <= threshold <= 1 or np.any(~np.isfinite(a)) or np.any(a <= 0):
        raise ValueError("Invalid threshold or cell area.")
    valid = r & np.isfinite(b) & np.isfinite(c) & (b != NODATA) & (c != NODATA)
    delta = np.where(valid, c - b, np.nan)
    total = float(a[r].sum())
    common = float(a[valid].sum())
    out = {"mean_delta": None, "common_valid_coverage_pct": round(100 * common / total, 2) if total else 0,
           "common_valid_area_ha": round(common, 6) if valid.any() else None, "increase_ha": None,
           "stable_ha": None, "decrease_ha": None}
    if valid.any():
        out.update(mean_delta=round(float(delta[valid].mean()), 6),
                   increase_ha=round(float(a[valid & (delta > threshold)].sum()), 6),
                   stable_ha=round(float(a[valid & (np.abs(delta) <= threshold)].sum()), 6),
                   decrease_ha=round(float(a[valid & (delta < -threshold)].sum()), 6))
    return out, delta


def _stats(index, values, roi, pixel_ha):
    v = np.asarray(values, dtype=float)
    a = np.broadcast_to(np.asarray(pixel_ha, dtype=float), v.shape)
    valid = np.asarray(roi, dtype=bool) & np.isfinite(v) & (v != NODATA)
    total = float(a[np.asarray(roi, dtype=bool)].sum())
    vals = v[valid]
    return WorkflowIndexStats(index=index,
        mean=round(float(vals.mean()), 6) if vals.size else None,
        p5=round(float(np.percentile(vals, 5)), 6) if vals.size else None,
        p95=round(float(np.percentile(vals, 95)), 6) if vals.size else None,
        valid_coverage_pct=round(100 * float(a[valid].sum()) / total, 2) if total else 0,
        nominal_pixel_size_m=20 if index == "NDMI" else 10)


def _warnings(baseline, comparison, change, area):
    warnings = []
    for label, period in (("Kỳ nền", baseline), ("Kỳ so sánh", comparison)):
        if period.scene_count < 3:
            warnings.append(f"{label}: chỉ {period.scene_count} cảnh; trung vị có thể chịu ảnh hưởng một ngày duy nhất.")
        if period.valid_coverage_pct < 50:
            warnings.append(f"{label}: dưới 50% vùng có NDVI hợp lệ sau loại mây/thiếu dữ liệu.")
        if any(s.mean is None for s in period.indices):
            warnings.append(f"{label}: một số chỉ số không có điểm ảnh hợp lệ; giá trị được để trống.")
    if change["mean_delta"] is None:
        warnings.append("Không có điểm ảnh hợp lệ chung giữa hai kỳ; không thể kết luận biến động.")
    elif change["common_valid_coverage_pct"] < 50:
        warnings.append("Dưới 50% vùng hợp lệ ở cả hai kỳ; biến động chỉ đại diện phần có dữ liệu chung.")
    if area < 1:
        warnings.append("Vùng dưới 1 ha có ít điểm ảnh Sentinel-2; ranh giới và pixel trộn có thể ảnh hưởng mạnh thống kê.")
    warnings.append("Ngưỡng mây lọc theo toàn cảnh; độ phủ hợp lệ tính trong vùng sau mặt nạ SCL, không phải phần trăm mây đo trực tiếp.")
    warnings.append("Diện tích phân lớp cộng diện tích ô raster có tâm nằm trong vùng; sai số lượng tử ở biên có thể làm tổng khác diện tích ranh giới vector, nhất là vùng nhỏ.")
    return warnings


def _result(ring, baseline, comparison, change, threshold, image_url, simulated):
    area = geo.area_ha(ring)
    return RegionWorkflowResult(dataset="MÔ PHỎNG" if simulated else "COPERNICUS/S2_SR_HARMONIZED",
        source=mock_service.SIM if simulated else "Sentinel-2 SR Harmonized (Copernicus), qua Google Earth Engine",
        simulated=simulated, area_ha=round(area, 4), bounds=Bounds(**geo.bounds(ring)),
        baseline=baseline, comparison=comparison,
        change=WorkflowChange(**change, threshold=threshold, image_url=image_url,
            legend=Legend(min=-1, max=1, palette=PALETTE, label="ΔNDVI (kỳ so sánh − kỳ nền)")),
        method=("MÔ PHỎNG xác định để thử giao diện; không đọc ảnh vệ tinh. " if simulated else "") +
            "Mặt nạ SCL loại lớp 0,1,3,8,9,10,11; phản xạ ×0,0001; chỉ số eemont/spyndex; "
            "trung vị chỉ số từng kỳ. NDVI/EVI lấy mẫu 10 m; NDMI 20 m theo B11 (NIR lấy mẫu lân cận gần nhất). "
            "Hai ảnh NDVI cùng lưới UTM 10 m; Δ = so sánh − nền tại từng pixel hợp lệ chung. "
            "Tăng khi Δ > ngưỡng, giảm khi Δ < −ngưỡng, ổn định khi |Δ| ≤ ngưỡng. "
            "Diện tích cộng diện tích pixel; độ phủ là tỷ lệ diện tích pixel trong vùng. Không dùng ảnh AI tăng độ phân giải.",
        warnings=(["Đây là dữ liệu MÔ PHỎNG; không dùng để ra quyết định."] if simulated else []) +
                 _warnings(baseline, comparison, change, area), caution=CAUTION)


def mock_result(ring, req):
    # Each cell corresponds to a mock aligned spatial location, not independent means.
    r = mock_service._rng("region-workflow", ring, req.model_dump(mode="json"))
    roi = np.ones((24, 24), dtype=bool)
    roi[:2, :3] = False
    b = np.array([r.uniform(0.3, 0.8) for _ in range(roi.size)]).reshape(roi.shape)
    c = b + np.array([r.uniform(-0.25, 0.25) for _ in range(roi.size)]).reshape(roi.shape)
    b[1::7, 1::6] = np.nan
    c[2::7, 3::6] = np.nan
    cell_ha = geo.area_ha(ring) / roi.sum()
    change, delta = paired_change(b, c, roi, cell_ha, req.threshold)

    def period(v, start, end):
        stats = [_stats(i, v if i == "NDVI" else (v * .7 if i == "EVI" else v - .4), roi, cell_ha)
                 for i in ix.INDICES]
        n = max(1, (end - start).days // 5)
        return WorkflowPeriod(start=start, end_exclusive=end, scene_count=n,
            scene_ids=[f"SIMULATED-{start.isoformat()}-{k}" for k in range(n)],
            valid_coverage_pct=stats[0].valid_coverage_pct, indices=stats)

    def pixel(x, y):
        value = delta[y, x]
        return (*mock_service._ramp((value + 1) / 2, PALETTE), 255) if np.isfinite(value) else (0, 0, 0, 0)
    image_url = mock_service._store(mock_service._png(24, 24, pixel))
    return _result(ring, period(b, req.baseline_start, req.baseline_end),
                   period(c, req.comparison_start, req.comparison_end), change, req.threshold, image_url, True)


def live_result(ring, req):
    import ee
    from . import ee_service as es

    geom = es._geom(ring)
    box = geo.bounds(ring)
    lon, lat = (box["west"] + box["east"]) / 2, (box["south"] + box["north"]) / 2
    if not -80 <= lat <= 84 or box["east"] - box["west"] > 180:
        raise ValueError("Quy trình cần vùng trong phạm vi UTM và không vượt kinh tuyến 180°.")
    zone = min(60, max(1, int((lon + 180) // 6) + 1))
    crs = f"EPSG:{32600 + zone if lat >= 0 else 32700 + zone}"
    projection = ee.Projection(crs)
    coords = geom.bounds(maxError=1, proj=projection).coordinates().getInfo()[0]
    x0, y0 = math.floor(min(p[0] for p in coords) / 10) * 10, math.floor(min(p[1] for p in coords) / 10) * 10
    x1, y1 = math.ceil(max(p[0] for p in coords) / 10) * 10, math.ceil(max(p[1] for p in coords) / 10) * 10
    width, height = int((x1 - x0) / 10), int((y1 - y0) / 10)
    if width * height > 1_000_000:
        raise ValueError("Khung bao vùng quá lớn hoặc quá dài; hãy chọn vùng gọn hơn.")
    transform = [10, 0, x0, 0, -10, y1]

    def period(start, end):
        col = es._collection(geom, start, end, req.max_cloud_pct)
        ids = col.aggregate_array("system:index").getInfo()
        if not ids:
            raise es.NoScenes(f"Không có cảnh Sentinel-2 kỳ {start} đến {end} theo ngưỡng mây đã chọn.")
        images = {index: es._with_index(col, index).select(index).median().clip(geom)
                  for index in ix.INDICES}
        # NDVI and EVI are actually sampled with the exact change grid below.
        return ids, images

    bids, bimages = period(req.baseline_start, req.baseline_end)
    cids, cimages = period(req.comparison_start, req.comparison_end)
    bundle = ee.Image.cat([
        bimages["NDVI"].rename("baseline"), cimages["NDVI"].rename("comparison"),
        bimages["EVI"].rename("baseline_evi"), cimages["EVI"].rename("comparison_evi")
    ]).unmask(NODATA)
    roi_img = ee.Image.constant(1).clip(geom).unmask(0).rename("roi")
    bundle = bundle.addBands(roi_img).addBands(ee.Image.pixelArea().divide(10000).rename("pixel_ha"))
    url = bundle.getDownloadURL({"crs": crs, "crs_transform": transform,
        "dimensions": [width, height], "format": "NPY"})
    with urlopen(url, timeout=90) as response:
        data = response.read(48 * 1024 * 1024 + 1)
    if len(data) > 48 * 1024 * 1024:
        raise ValueError("Dữ liệu vượt giới hạn tải.")
    arr = np.load(BytesIO(data), allow_pickle=False)
    roi, areas = arr["roi"] > 0, arr["pixel_ha"]
    change, _ = paired_change(arr["baseline"], arr["comparison"], roi, areas, req.threshold)

    def ndmi_stats(img):
        # NDMI's B11 information is 20 m. Do not falsely report extra detail at 10 m.
        img = img.reproject(crs=crs, scale=20)
        stats = img.reduceRegion(ee.Reducer.mean().combine(ee.Reducer.percentile([5, 95]), sharedInputs=True),
                                geom, 20, crs=crs, maxPixels=1_000_000).getInfo() or {}
        coverage = img.mask().unmask(0).reduceRegion(ee.Reducer.mean(), geom, 20,
                                                   crs=crs, maxPixels=1_000_000).getInfo() or {}
        return WorkflowIndexStats(index="NDMI", mean=stats.get("NDMI_mean"), p5=stats.get("NDMI_p5"),
            p95=stats.get("NDMI_p95"), valid_coverage_pct=round(float(coverage.get("NDMI") or 0) * 100, 2),
            nominal_pixel_size_m=20)

    def report(start, end, ids, prefix, images):
        stats = [_stats("NDVI", arr[prefix], roi, areas), _stats("EVI", arr[prefix + "_evi"], roi, areas),
                 ndmi_stats(images["NDMI"])]
        return WorkflowPeriod(start=start, end_exclusive=end, scene_count=len(ids), scene_ids=sorted(ids),
                              valid_coverage_pct=stats[0].valid_coverage_pct, indices=stats)

    image_url = None
    if change["mean_delta"] is not None:
        b = bimages["NDVI"].reproject(crs=crs, crsTransform=transform)
        c = cimages["NDVI"].reproject(crs=crs, crsTransform=transform)
        delta = c.subtract(b).updateMask(b.mask().And(c.mask())).clip(geom)
        image_url = delta.visualize(min=-1, max=1, palette=PALETTE).getThumbURL({
            "region": ee.Geometry.Rectangle([box["west"], box["south"], box["east"], box["north"]],
                                            proj="EPSG:4326", geodesic=False),
            "dimensions": 768, "format": "png", "crs": "EPSG:4326"})
    return _result(ring, report(req.baseline_start, req.baseline_end, bids, "baseline", bimages),
        report(req.comparison_start, req.comparison_end, cids, "comparison", cimages),
        change, req.threshold, image_url, False)
