"""Đối chiếu ĐỘC LẬP: tính NDVI bằng earthengine-api thuần (không eemont, không mã của backend) rồi so với backend.

    $env:EE_PROJECT = "id-project"
    .\\.venv\\Scripts\\python.exe crosscheck.py "lat,lng;lat,lng;lat,lng;..." 2026-09-13 2026-10-14

Đối số 1: các đỉnh "vĩ độ,lng" cách nhau bằng dấu chấm phẩy. Hai đối số sau: ngày bắt đầu và ngày kết thúc (không gồm).
Cách tính độc lập: S2_SR_HARMONIZED, loại mây bằng SCL, phản xạ = DN × 0,0001, NDVI = (B8 − B4)/(B8 + B4) trên từng cảnh, trung vị theo pixel, trung bình theo vùng ở 10 m.
Khác biệt nhỏ (< 0,005) là bình thường (khác cách làm tròn/bước tái lấy mẫu); lệch lớn nghĩa là có lỗi cần xem lại.
"""
import os
import sys
from datetime import date

import ee

from app import ee_service

if len(sys.argv) != 4:
    sys.exit(__doc__)
pts = [tuple(float(x) for x in p.split(",")) for p in sys.argv[1].split(";")]
ring = [[lng, lat] for lat, lng in pts]
start, end = date.fromisoformat(sys.argv[2]), date.fromisoformat(sys.argv[3])
ee.Initialize(project=os.environ["EE_PROJECT"])
geom = ee.Geometry.Polygon([ring + [ring[0]]], proj="EPSG:4326", geodesic=False)

# --- cách 1: độc lập, chỉ earthengine-api ---
col = (ee.ImageCollection("COPERNICUS/S2_SR_HARMONIZED").filterBounds(geom)
       .filterDate(start.isoformat(), end.isoformat()).filter(ee.Filter.lt("CLOUDY_PIXEL_PERCENTAGE", 80)))


def ndvi(img):
    scl = img.select("SCL")
    ok = scl.neq(0).And(scl.neq(1)).And(scl.neq(3)).And(scl.neq(8)).And(scl.neq(9)).And(scl.neq(10)).And(scl.neq(11))
    nir = img.select("B8").multiply(0.0001); red = img.select("B4").multiply(0.0001)
    return nir.subtract(red).divide(nir.add(red)).rename("NDVI").updateMask(ok)


n = col.size().getInfo()
print(f"Cảnh Sentinel-2 (mây < 80%): {n}")
dates = sorted(set(col.aggregate_array("system:time_start").map(lambda t: ee.Date(t).format("YYYY-MM-dd")).getInfo()))
print("Ngày có ảnh:", ", ".join(dates) if dates else "—")
if n == 0:
    sys.exit("Không có cảnh nào để đối chiếu.")
comp = col.map(ndvi).median().clip(geom)
mean = comp.reduceRegion(ee.Reducer.mean(), geom, 10, maxPixels=100_000_000).get("NDVI").getInfo()
cov = comp.mask().reduceRegion(ee.Reducer.mean(), geom, 10, maxPixels=100_000_000).get("NDVI").getInfo()
print(f"[ĐỘC LẬP]  NDVI trung bình = {mean}   phủ hợp lệ = {None if cov is None else round(cov * 100, 1)}%")

# --- cách 2: mã của backend (eemont) ---
try:
    r = ee_service.index_result(ring + [ring[0]], "NDVI", start, end, 80)
    print(f"[BACKEND]  NDVI trung bình = {r.mean}   phủ hợp lệ = {r.valid_coverage_pct}%   cảnh = {r.scene_count}")
    if mean is not None:
        d = abs(mean - r.mean)
        print(f"Chênh lệch tuyệt đối = {d:.5f}  →  {'KHỚP' if d < 0.005 else 'LỆCH: cần xem lại'}")
except ee_service.NoScenes as e:
    print("[BACKEND]  ", e, "(độc lập " + ("cũng không có điểm ảnh hợp lệ" if mean is None else "có giá trị: lệch hành vi!") + ")")
