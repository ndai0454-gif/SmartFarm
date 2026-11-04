"""Tìm địa điểm và tra ngược toạ độ → địa chỉ, dùng Photon (https://github.com/komoot/photon, Apache-2.0) trên dữ liệu OpenStreetMap.

Ứng dụng không gọi thẳng Photon: máy chủ này đứng giữa để (1) đổi nơi đặt Photon bằng biến PHOTON_URL mà không phải cập nhật app,
(2) nhớ kết quả (bộ nhớ đệm) và giới hạn tốc độ để không vượt mức dùng hợp lý của máy chủ demo công cộng, (3) chuẩn hoá kết quả.
Mặc định dùng máy chủ demo https://photon.komoot.io (không đảm bảo hoạt động, bị giới hạn nếu dùng nhiều). Dùng nhiều thì tự dựng Photon
(java -jar photon-*.jar serve, cổng 2322) rồi đặt PHOTON_URL=http://127.0.0.1:2322. Dữ liệu: © người đóng góp OpenStreetMap (ODbL) — app phải ghi nguồn.
"""
import os
import threading
import unicodedata
import time
from collections import OrderedDict, deque
from typing import Callable, Optional

import httpx
from fastapi import APIRouter, Depends, HTTPException, Query

DEMO_URL = "https://photon.komoot.io"
PHOTON_URL = os.environ.get("PHOTON_URL", DEMO_URL).rstrip("/")
# Photon riêng lỗi/tắt thì tạm dùng máy chủ demo công cộng (chỉ khi PHOTON_URL không phải chính nó). Đặt PHOTON_FALLBACK_URL="" để tắt.
PHOTON_FALLBACK_URL = os.environ.get("PHOTON_FALLBACK_URL", DEMO_URL if PHOTON_URL != DEMO_URL else "").rstrip("/")
VN_BBOX = "102.0,8.0,110.0,24.0"                      # minLon,minLat,maxLon,maxLat: ưu tiên kết quả trong Việt Nam
CACHE_TTL = 6 * 3600
CACHE_MAX = 2000
RATE_PER_MIN = 40                                     # theo mã truy cập, đủ cho gõ-tới-đâu-tìm-tới-đó có debounce
TIMEOUT = 6.0


class _Cache:
    def __init__(self):
        self._d: "OrderedDict[str, tuple[float, object]]" = OrderedDict()
        self._lock = threading.Lock()

    def get(self, k: str):
        with self._lock:
            hit = self._d.get(k)
            if hit and hit[0] > time.time():
                self._d.move_to_end(k)
                return hit[1]
            self._d.pop(k, None)
        return None

    def set(self, k: str, v: object):
        with self._lock:
            self._d[k] = (time.time() + CACHE_TTL, v)
            while len(self._d) > CACHE_MAX:
                self._d.popitem(last=False)


class _Limiter:
    def __init__(self, per_min: int):
        self.per_min = per_min
        self._hits: dict[str, deque] = {}
        self._lock = threading.Lock()

    def allow(self, who: str) -> bool:
        now = time.time()
        with self._lock:
            q = self._hits.setdefault(who, deque())
            while q and now - q[0] > 60:
                q.popleft()
            if len(q) >= self.per_min:
                return False
            q.append(now)
            if len(self._hits) > 2000:
                self._hits.clear()
        return True


def _fold(v: str) -> str:
    """So khớp không phân biệt dấu/hoa thường: OSM hay có cả "Buôn Ma Thuột" lẫn "Buon Ma Thuot" trong cùng một địa chỉ."""
    return "".join(ch for ch in unicodedata.normalize("NFD", str(v).replace("đ", "d").replace("Đ", "D")) if unicodedata.category(ch) != "Mn").casefold().strip()


def _join(p: dict, keys) -> list:
    out, seen = [], set()
    for k in keys:
        v = p.get(k)
        if v and _fold(v) not in seen:
            seen.add(_fold(v)); out.append(str(v))
    return out


def _label(p: dict) -> str:
    """Nhãn ngắn dễ đọc: tên · đường/phường/huyện/tỉnh (bỏ phần trùng)."""
    parts = _join(p, ("name", "street", "locality", "district", "city", "state"))
    if parts and p.get("housenumber") and p.get("street") and not p.get("name"):
        parts[0] = f'{p["housenumber"]} {parts[0]}'
    return ", ".join(parts) or "Không tên"


def _admin(p: dict) -> str:
    """Địa chỉ hành chính (không tên riêng): xã/phường, huyện, tỉnh — dùng điền ô "Địa điểm" của vườn."""
    return ", ".join(_join(p, ("locality", "district", "city", "state")))


def normalize(feature: dict) -> Optional[dict]:
    try:
        lon, lat = feature["geometry"]["coordinates"][:2]
        p = feature.get("properties") or {}
        lon, lat = float(lon), float(lat)
    except Exception:
        return None
    if not (-90 <= lat <= 90 and -180 <= lon <= 180):
        return None
    ext = p.get("extent")
    bbox = None
    if isinstance(ext, list) and len(ext) == 4:
        try:
            w, n, e, s = (float(x) for x in ext)               # Photon: [minLon, maxLat, maxLon, minLat]
            bbox = {"west": min(w, e), "north": max(n, s), "east": max(w, e), "south": min(n, s)}
        except Exception:
            bbox = None
    return {"name": p.get("name") or "", "label": _label(p), "admin": _admin(p), "lat": lat, "lng": lon,
            "kind": p.get("osm_value") or p.get("type") or "", "country": p.get("countrycode") or "", "bbox": bbox}


Fetch = Callable[[str, dict], dict]


def _get(base: str, path: str, params: dict) -> dict:
    r = httpx.get(base + path, params=params, timeout=TIMEOUT, headers={"User-Agent": "VWing/1.0 (geocode proxy)"})
    if r.status_code == 429:
        raise HTTPException(503, "Dịch vụ tìm địa điểm đang quá tải. Thử lại sau ít phút.")
    if r.status_code != 200:
        raise httpx.HTTPError(f"HTTP {r.status_code}")
    try:
        return r.json()
    except ValueError:
        raise httpx.HTTPError("không đọc được JSON")


def http_fetch(path: str, params: dict) -> dict:
    last = None
    for base in [b for b in (PHOTON_URL, PHOTON_FALLBACK_URL) if b]:
        try:
            return _get(base, path, params)
        except httpx.HTTPError as e:                  # lỗi mạng/máy chủ → thử nguồn dự phòng; 429 đã ném HTTPException riêng
            last = e
    raise HTTPException(502, "Không kết nối được dịch vụ tìm địa điểm (Photon).")


def make_router(auth: Callable, fetch: Fetch = http_fetch) -> APIRouter:
    r = APIRouter(prefix="/v1/geo", dependencies=[Depends(auth)])
    cache, limiter = _Cache(), _Limiter(RATE_PER_MIN)

    def guard(who: str):
        if not limiter.allow(who):
            raise HTTPException(429, "Tìm quá nhanh. Đợi một chút rồi thử lại.")

    @r.get("/search")
    def search(q: str = Query(min_length=2, max_length=100), lat: Optional[float] = Query(None, ge=-90, le=90),
               lng: Optional[float] = Query(None, ge=-180, le=180), limit: int = Query(6, ge=1, le=10), worldwide: bool = False):
        guard("search")
        q = " ".join(q.split())
        if len(q) < 2:
            raise HTTPException(422, "Nhập ít nhất 2 ký tự.")
        params = {"q": q, "limit": limit, "lang": "default"}
        if lat is not None and lng is not None:           # thiên về kết quả gần vị trí người dùng
            params["lat"], params["lon"] = round(lat, 2), round(lng, 2)
        if not worldwide:
            params["bbox"] = VN_BBOX
        key = "s|" + repr(sorted(params.items()))
        hit = cache.get(key)
        if hit is None:
            data = fetch("/api", params)
            hit = [x for x in (normalize(f) for f in data.get("features", [])) if x]
            cache.set(key, hit)
        return {"results": hit, "attribution": "© OpenStreetMap contributors · Photon"}

    @r.get("/reverse")
    def reverse(lat: float = Query(ge=-90, le=90), lng: float = Query(ge=-180, le=180)):
        guard("reverse")
        params = {"lat": round(lat, 5), "lon": round(lng, 5), "lang": "default", "limit": 1}
        key = "r|" + repr(sorted(params.items()))
        hit = cache.get(key)
        if hit is None:
            data = fetch("/reverse", params)
            res = [x for x in (normalize(f) for f in data.get("features", [])) if x]
            hit = res[0] if res else {}
            cache.set(key, hit)
        return {"result": hit or None, "attribution": "© OpenStreetMap contributors · Photon"}

    return r
