"""Kiểm tra nhanh chế độ Earth Engine thật. Chạy sau khi đã xác thực:

    $env:EE_PROJECT = "id-project-cua-ban"
    .\\.venv\\Scripts\\python.exe check_live.py

Mỗi bước in OK/LỖI riêng để biết hỏng ở đâu: xác thực, quyền, bộ ảnh Sentinel-2, xử lý eemont, Sentinel-1 / HYDRAFloods.
Lấy kết quả NDVI in ra so với Code Editor (cùng polygon, cùng ngày) trước khi tin dùng.
"""
import os
import sys
from datetime import date

import ee

PROJECT = os.environ.get("EE_PROJECT", "")
RING = [[107.8000, 11.5500], [107.8010, 11.5500], [107.8010, 11.5510], [107.8000, 11.5510], [107.8000, 11.5500]]  # polygon giả gần Bảo Lộc


def step(name, fn):
    try:
        out = fn()
        print(f"[OK]   {name}" + (f": {out}" if out is not None else ""))
        return out
    except Exception as e:  # noqa: BLE001 - in nguyên nhân để người dùng xử lý
        print(f"[LỖI]  {name}: {type(e).__name__}: {str(e)[:300]}")
        return None


if not PROJECT:
    sys.exit("Chưa đặt EE_PROJECT (id Cloud project đã đăng ký Earth Engine).")

if step("Xác thực + khởi tạo Earth Engine", lambda: ee.Initialize(project=PROJECT) or "đã khởi tạo") is None:
    sys.exit("Dừng: chạy `.\\.venv\\Scripts\\earthengine.exe authenticate` rồi thử lại, và kiểm tra project đã bật Earth Engine API.")

from app import ee_service  # noqa: E402

geom = ee.Geometry.Polygon([RING])
step("Đọc bộ ảnh Sentinel-2", lambda: ee.ImageCollection(ee_service.S2).filterBounds(geom).filterDate("2026-08-01", "2026-09-01").size().getInfo())
r = step("NDVI qua eemont (ảnh ghép 01/08–01/09/2026)", lambda: ee_service.index_result(RING, "NDVI", date(2026, 8, 1), date(2026, 9, 1), 80))
if r:
    print(f"       trung bình={r.mean:.3f}  p5={r.p5:.3f}  p95={r.p95:.3f}  phủ hợp lệ={r.valid_coverage_pct}%  cảnh={r.scene_count}")
    print(f"       ảnh: {r.image_url}")
step("Chuỗi thời gian NDVI (03–09/2026)", lambda: len(ee_service.series_result(RING, "NDVI", date(2026, 3, 1), date(2026, 9, 1), 80).points))
step("EVI", lambda: round(ee_service.index_result(RING, "EVI", date(2026, 8, 1), date(2026, 9, 1), 80).mean, 3))
step("NDMI", lambda: round(ee_service.index_result(RING, "NDMI", date(2026, 8, 1), date(2026, 9, 1), 80).mean, 3))
w = step("Nước mặt Sentinel-1 + HYDRAFloods", lambda: ee_service.water_change(RING, date(2026, 8, 1), date(2026, 8, 20), date(2026, 9, 1), date(2026, 9, 10)))
if w:
    print(f"       nước mới={w.new_water_ha} ha  thường xuyên={w.permanent_water_ha} ha  đã rút={w.receded_ha} ha")
