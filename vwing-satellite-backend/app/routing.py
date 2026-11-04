"""Phân tích theo MẠNG LƯỚI ĐƯỜNG: vùng phục vụ / đường đồng thời gian (isochrone) và ma trận điểm đi–điểm đến.

Dùng Valhalla (https://github.com/valhalla/valhalla, MIT) trên dữ liệu đường OpenStreetMap. Máy chủ này đứng giữa app và Valhalla để
(1) đổi nơi đặt bằng biến VALHALLA_URL (tự dựng máy chủ riêng khi dùng nhiều), (2) nhớ kết quả và giới hạn tốc độ vì máy chủ công cộng
chỉ dành cho dùng vừa phải, (3) chuẩn hoá kết quả.

LƯU Ý QUYỀN RIÊNG TƯ: mặc định (https://valhalla1.openstreetmap.de) toạ độ điểm xuất phát/đích được gửi tới máy chủ của cộng đồng OpenStreetMap.
Muốn giữ nội bộ, tự chạy Valhalla và đặt VALHALLA_URL. Máy chủ công cộng giới hạn mỗi vùng tối đa 60 phút di chuyển.
Thời gian là ước tính theo dữ liệu đường OSM và tốc độ mặc định của từng loại phương tiện, không tính kẹt xe, mưa lũ hay đường đất chưa vào bản đồ.
"""
import os
from typing import Callable, Literal, Optional

import httpx
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

from . import geo
from .geocode import _Cache, _Limiter

VALHALLA_URL = os.environ.get("VALHALLA_URL", "https://valhalla1.openstreetmap.de").rstrip("/")
ATTRIBUTION = "© OpenStreetMap contributors · Valhalla"
PROFILES = {"pedestrian": "Đi bộ", "bicycle": "Xe đạp", "motor_scooter": "Xe máy", "auto": "Ô tô"}
MAX_MINUTES = 60
MAX_CONTOURS = 4
MAX_ORIGINS = 5
MAX_MATRIX = 10
RATE_PER_MIN = 30
TIMEOUT = 25.0

Post = Callable[[str, dict], dict]
Profile = Literal["pedestrian", "bicycle", "motor_scooter", "auto"]


class Point(BaseModel):
    lat: float = Field(ge=-85, le=85)
    lng: float = Field(ge=-180, le=180)
    name: str = Field(default="", max_length=80)


class IsochroneRequest(BaseModel):
    origins: list[Point] = Field(min_length=1, max_length=MAX_ORIGINS)
    profile: Profile = "auto"
    minutes: list[int] = Field(min_length=1, max_length=MAX_CONTOURS)


class MatrixRequest(BaseModel):
    sources: list[Point] = Field(min_length=1, max_length=MAX_MATRIX)
    targets: list[Point] = Field(min_length=1, max_length=MAX_MATRIX)
    profile: Profile = "auto"


def http_post(path: str, body: dict) -> dict:
    try:
        r = httpx.post(VALHALLA_URL + path, json=body, timeout=TIMEOUT, headers={"User-Agent": "VWing/1.0 (routing proxy)"})
    except httpx.HTTPError:
        raise HTTPException(502, "Không kết nối được dịch vụ đường đi (Valhalla).")
    if r.status_code == 429:
        raise HTTPException(503, "Dịch vụ đường đi đang quá tải. Thử lại sau ít phút.")
    if r.status_code == 400:
        try:
            msg = str(r.json().get("error", ""))
        except ValueError:
            msg = ""
        if "Exceeded max" in msg:
            raise HTTPException(422, "Vượt giới hạn của máy chủ đường đi (thời gian hoặc số điểm). Giảm số phút hoặc số điểm.")
        raise HTTPException(422, "Không tìm được đường đi cho điểm này (điểm có thể nằm xa đường hoặc ngoài vùng dữ liệu).")
    if r.status_code != 200:
        raise HTTPException(502, "Dịch vụ đường đi trả lỗi.")
    try:
        return r.json()
    except ValueError:
        raise HTTPException(502, "Dịch vụ đường đi trả dữ liệu không đọc được.")


def _loc(p: Point) -> dict:
    return {"lat": round(p.lat, 5), "lon": round(p.lng, 5)}


def _largest_ring(geometry: dict) -> Optional[list]:
    """Vòng ngoài lớn nhất của Polygon/MultiPolygon (bỏ lỗ thủng: vùng phục vụ coi như liền khối)."""
    t, c = geometry.get("type"), geometry.get("coordinates")
    rings = [c[0]] if t == "Polygon" and c else [poly[0] for poly in c if poly] if t == "MultiPolygon" and c else []
    rings = [[[float(p[0]), float(p[1])] for p in r] for r in rings if r and len(r) >= 4]
    return max(rings, key=geo.area_ha) if rings else None


def parse_isochrone(data: dict, minutes: list) -> list:
    out = {}
    for f in data.get("features", []):
        p = f.get("properties") or {}
        ring = _largest_ring(f.get("geometry") or {})
        try:
            m = int(round(float(p.get("contour"))))
        except (TypeError, ValueError):
            continue
        if ring and m in minutes:
            out[m] = {"minutes": m, "ring": ring, "area_ha": round(geo.area_ha(ring), 2)}
    return [out[m] for m in sorted(out)]


def parse_matrix(data: dict, n_src: int, n_tgt: int) -> dict:
    mins = [[None] * n_tgt for _ in range(n_src)]
    kms = [[None] * n_tgt for _ in range(n_src)]
    for row in data.get("sources_to_targets", []):
        for cell in row:
            i, j = cell.get("from_index"), cell.get("to_index")
            t, d = cell.get("time"), cell.get("distance")
            if isinstance(i, int) and isinstance(j, int) and 0 <= i < n_src and 0 <= j < n_tgt and t is not None and d is not None:
                mins[i][j] = round(float(t) / 60.0, 1)
                kms[i][j] = round(float(d), 2)
    return {"minutes": mins, "km": kms}


def make_router(auth: Callable, post: Post = http_post) -> APIRouter:
    r = APIRouter(prefix="/v1/routing", dependencies=[Depends(auth)])
    cache, limiter = _Cache(), _Limiter(RATE_PER_MIN)

    def guard():
        if not limiter.allow("routing"):
            raise HTTPException(429, "Gửi quá nhanh. Đợi một chút rồi thử lại.")

    @r.get("/profiles")
    def profiles():
        return {"profiles": [{"key": k, "label": v} for k, v in PROFILES.items()], "max_minutes": MAX_MINUTES, "attribution": ATTRIBUTION}

    @r.post("/isochrone")
    def isochrone(req: IsochroneRequest):
        mins = sorted(set(req.minutes))
        if mins[0] < 1 or mins[-1] > MAX_MINUTES:
            raise HTTPException(422, f"Số phút phải từ 1 đến {MAX_MINUTES}.")
        guard()
        result = []
        for o in req.origins:
            key = ("iso", req.profile, tuple(mins), round(o.lat, 5), round(o.lng, 5))
            hit = cache.get(key)
            if hit is None:
                body = {"locations": [_loc(o)], "costing": req.profile, "contours": [{"time": m} for m in mins],
                        "polygons": True, "denoise": 0.5, "generalize": 30}
                hit = parse_isochrone(post("/isochrone", body), mins)
                if not hit:
                    raise HTTPException(422, "Không tạo được vùng phục vụ quanh điểm này (không có đường lân cận).")
                cache.set(key, hit)
            result.append({"name": o.name, "lat": o.lat, "lng": o.lng, "contours": hit})
        return {"profile": req.profile, "source": "Valhalla/OpenStreetMap", "origins": result, "attribution": ATTRIBUTION,
                "caution": "Thời gian ước tính theo dữ liệu đường OSM và tốc độ mặc định; không tính kẹt xe, thời tiết hay đường chưa có trên bản đồ."}

    @r.post("/matrix")
    def matrix(req: MatrixRequest):
        guard()
        key = ("mx", req.profile, tuple((round(p.lat, 5), round(p.lng, 5)) for p in req.sources), tuple((round(p.lat, 5), round(p.lng, 5)) for p in req.targets))
        hit = cache.get(key)
        if hit is None:
            body = {"sources": [_loc(p) for p in req.sources], "targets": [_loc(p) for p in req.targets], "costing": req.profile}
            hit = parse_matrix(post("/sources_to_targets", body), len(req.sources), len(req.targets))
            cache.set(key, hit)
        return {"profile": req.profile, "source": "Valhalla/OpenStreetMap", **hit, "attribution": ATTRIBUTION,
                "caution": "Chỉ là thời gian/quãng đường theo mạng đường OSM; null nghĩa là không có đường nối hai điểm."}

    return r
