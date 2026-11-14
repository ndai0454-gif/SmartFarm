"""Xử lý thật trên Google Earth Engine: Sentinel-2 (NDVI/EVI/NDMI qua eemont) và Sentinel-1 (nước mặt qua HYDRAFloods).

Cần Cloud project đã đăng ký Earth Engine và thông tin xác thực phía máy chủ (Application Default Credentials hoặc
`earthengine authenticate`). Mô-đun này CHƯA được chạy thử với tài khoản thật trong dự án này: hãy đối chiếu kết quả với
Code Editor trước khi dùng (xem README, mục "Kiểm tra bằng Code Editor").
"""
from datetime import date, timedelta

import ee
import eemont  # noqa: F401  (đăng ký các phương thức scaleAndOffset/spectralIndices lên ee.ImageCollection)

from . import indices as ix
from .geo import area_ha, bounds as geo_bounds
from .schemas import Bounds, IndexResult, Legend, SeriesPoint, SeriesResult, WaterResult

S2 = "COPERNICUS/S2_SR_HARMONIZED"
MIN_COVERAGE = 0.10        # cảnh có dưới 10% pixel hợp lệ trong vườn không được đưa vào chuỗi


class NoScenes(Exception):
    pass


def init(project: str) -> None:
    try:
        ee.Initialize(project=project)
    except Exception:
        import google.auth
        creds, _ = google.auth.default(scopes=["https://www.googleapis.com/auth/earthengine"])
        ee.Initialize(credentials=creds, project=project)


def _geom(ring):
    return ee.Geometry.Polygon([ring], proj="EPSG:4326", geodesic=False)


def _scl_mask(img):
    scl = img.select("SCL")
    clear = ee.Image.constant(1)
    for c in ix.SCL_MASKED:
        clear = clear.And(scl.neq(c))
    return img.updateMask(clear)


def _collection(geom, start: date, end: date, max_cloud: float):
    return (ee.ImageCollection(S2).filterBounds(geom)
            .filterDate(start.isoformat(), end.isoformat())
            .filter(ee.Filter.lt("CLOUDY_PIXEL_PERCENTAGE", max_cloud)))


def _with_index(col, index: str):
    # eemont: scaleAndOffset đưa DN về phản xạ (×0,0001) trước khi spectralIndices tính chỉ số (EVI cần đúng thang đo)
    return col.map(_scl_mask).scaleAndOffset().spectralIndices(index)


def _legend(d: ix.IndexDef) -> Legend:
    return Legend(min=d.vmin, max=d.vmax, palette=list(d.palette), label=d.title)


def index_result(ring, index: str, start: date, end: date, max_cloud: float) -> IndexResult:
    d = ix.INDICES[index]
    geom = _geom(ring)
    col = _collection(geom, start, end, max_cloud)
    n = col.size().getInfo()
    if n == 0:
        raise NoScenes("Không có cảnh Sentinel-2 nào trong khoảng ngày và ngưỡng mây đã chọn.")
    comp = _with_index(col, index).select(index).median().clip(geom)
    reducer = ee.Reducer.mean().combine(ee.Reducer.percentile([5, 95]), sharedInputs=True)
    stats = comp.reduceRegion(reducer=reducer, geometry=geom, scale=10, maxPixels=100_000_000).getInfo() or {}
    cov = comp.mask().reduceRegion(reducer=ee.Reducer.mean(), geometry=geom, scale=10, maxPixels=100_000_000).getInfo() or {}
    coverage = cov.get(index)
    mean = stats.get(f"{index}_mean")
    warnings = []
    if mean is None or coverage is None or coverage == 0:
        raise NoScenes("Không còn điểm ảnh hợp lệ sau khi loại mây.")
    if coverage < 0.5:
        warnings.append(f"Chỉ {coverage * 100:.0f}% diện tích vườn có điểm ảnh hợp lệ; kết quả kém tin cậy.")
    if n < 3:
        warnings.append("Ít cảnh trong khoảng ngày; trung vị có thể bị lệch bởi một ngày duy nhất.")
    vis = comp.visualize(min=d.vmin, max=d.vmax, palette=list(d.palette))
    url = vis.getThumbURL({"region": geom, "dimensions": 768, "format": "png", "crs": "EPSG:4326"})
    return IndexResult(
        index=index, dataset=S2, source="Sentinel-2 SR Harmonized (Copernicus), qua Google Earth Engine", simulated=False,
        start=start, end_exclusive=end, scene_count=int(n),
        mean=mean, p5=stats.get(f"{index}_p5"), p95=stats.get(f"{index}_p95"), valid_coverage_pct=round(coverage * 100, 1),
        nominal_pixel_size_m=10, method="Trung vị các cảnh sau khi loại mây bằng SCL; chỉ số tính bằng eemont trên phản xạ đã nhân 0,0001",
        area_ha=round(area_ha(ring), 2), bounds=Bounds(**geo_bounds(ring)), image_url=url, legend=_legend(d),
        warnings=warnings, caution=d.caution,
    )


def series_result(ring, index: str, start: date, end: date, max_cloud: float) -> SeriesResult:
    d = ix.INDICES[index]
    geom = _geom(ring)
    col = _collection(geom, start, end, max_cloud)
    dates = ee.List(col.aggregate_array("system:time_start")).map(lambda t: ee.Date(t).format("YYYY-MM-dd")).distinct().sort()
    indexed = _with_index(col, index).select(index)

    def per_day(day):
        day = ee.String(day)
        t0 = ee.Date(day)
        img = indexed.filterDate(t0, t0.advance(1, "day")).mosaic()
        m = img.reduceRegion(ee.Reducer.mean(), geom, 10, maxPixels=100_000_000).get(index)
        c = img.mask().reduceRegion(ee.Reducer.mean(), geom, 10, maxPixels=100_000_000).get(index)
        return ee.Feature(None, {"d": day, "mean": m, "cov": c})

    feats = ee.FeatureCollection(dates.map(per_day)).getInfo().get("features", [])
    points, skipped = [], 0
    for f in feats:
        p = f["properties"]
        cov = p.get("cov") or 0.0
        if p.get("mean") is None or cov < MIN_COVERAGE:
            skipped += 1                # thiếu dữ liệu: bỏ khỏi chuỗi, không thay bằng 0
            continue
        points.append(SeriesPoint(date=date.fromisoformat(p["d"]), mean=p["mean"], valid_coverage_pct=round(cov * 100, 1)))
    if not points:
        raise NoScenes("Không có ngày nào đủ điểm ảnh hợp lệ trong khoảng đã chọn.")
    warnings = []
    if skipped:
        warnings.append(f"{skipped} ngày bị bỏ vì dưới {int(MIN_COVERAGE * 100)}% diện tích vườn có điểm ảnh hợp lệ (mây/thiếu ảnh).")
    return SeriesResult(
        index=index, dataset=S2, source="Sentinel-2 SR Harmonized (Copernicus), qua Google Earth Engine", simulated=False,
        start=start, end_exclusive=end, points=points, skipped_scenes=skipped, nominal_pixel_size_m=10,
        method="Trung bình theo vườn của ảnh ghép từng ngày sau khi loại mây bằng SCL", warnings=warnings, caution=d.caution,
    )


def water_change(ring, b0: date, b1: date, e0: date, e1: date) -> WaterResult:
    """Nước mặt từ Sentinel-1 (VV): so kỳ nền với kỳ sự kiện bằng HYDRAFloods edge_otsu. Kết quả để gợi ý kiểm tra thực địa."""
    import hydrafloods as hf   # nhập muộn: thư viện nặng, chỉ cần ở chế độ live

    geom = _geom(ring)
    ctx = geom.buffer(3000).bounds()          # ngữ cảnh rộng hơn vườn để ước lượng ngưỡng Otsu có đủ nước/không nước

    def period(s: date, e: date):
        ds = hf.Sentinel1(ctx, s.isoformat(), e.isoformat())
        col = ds.collection.select("VV")
        return col, col.median().clip(ctx)

    bcol, bimg = period(b0, b1)
    ecol, eimg = period(e0, e1)
    bn, en = int(bcol.size().getInfo()), int(ecol.size().getInfo())
    if bn == 0 or en == 0:
        raise NoScenes(f"Thiếu ảnh Sentinel-1: kỳ nền {bn} cảnh, kỳ sự kiện {en} cảnh.")

    def water(img):
        # nước mặt có phản xạ ngược VV thấp (dB): khởi tạo ngưỡng −16 dB, edge_otsu tinh chỉnh theo biên nước
        return hf.edge_otsu(img, band="VV", region=ctx, scale=30, initial_threshold=-16, thresh_no_data=-16, invert=False).unmask(0)

    wb, we = water(bimg), water(eimg)
    perm = wb.And(we); new = we.And(wb.Not()); rec = wb.And(we.Not())
    px = ee.Image.pixelArea().divide(10_000)

    def ha(mask):
        return px.updateMask(mask).reduceRegion(ee.Reducer.sum(), geom, 30, maxPixels=100_000_000).get("area")

    areas = ee.Dictionary({"perm": ha(perm), "new": ha(new), "rec": ha(rec)}).getInfo()
    klass = ee.Image(0).where(perm, 1).where(new, 2).where(rec, 3).selfMask().clip(geom)
    vis = klass.visualize(min=1, max=3, palette=["8a9a8f", "1e78b8", "e0a030"])
    url = vis.getThumbURL({"region": geom, "dimensions": 768, "format": "png", "crs": "EPSG:4326"})
    return WaterResult(
        dataset="COPERNICUS/S1_GRD", source="Sentinel-1 GRD (Copernicus), qua Google Earth Engine + HYDRAFloods", simulated=False,
        baseline_scenes=bn, event_scenes=en,
        new_water_ha=_r(areas.get("new")), permanent_water_ha=_r(areas.get("perm")), receded_ha=_r(areas.get("rec")),
        area_ha=round(area_ha(ring), 2), bounds=Bounds(**geo_bounds(ring)), image_url=url,
        method="Trung vị VV theo kỳ → HYDRAFloods edge_otsu (ngưỡng ban đầu −16 dB) → so sánh nước kỳ nền và kỳ sự kiện",
        warnings=[
            "Nước mặt mới xuất hiện không tự chứng minh thiệt hại cây trồng; cần kiểm tra thực địa.",
            "Radar có thể nhầm bóng địa hình, mặt đường nhẵn hoặc đất rất ướt với nước; chưa áp dụng lọc speckle/hiệu chỉnh địa hình (gee_s1_ard).",
        ],
        caution="Kết quả là gợi ý để đi kiểm tra, không phải kết luận ngập.",
    )


def _r(v):
    return None if v is None else round(float(v), 3)
