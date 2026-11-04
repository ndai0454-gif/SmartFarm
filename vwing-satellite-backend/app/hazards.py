"""Natural-hazard outlook (3 days) for a farm location from Open-Meteo data.

Rule-based thresholds, not a trained model: the rain/wind/heat levels follow the
common Vietnamese warning bands (mm per 24 h, gust speed, daily maximum
temperature) and are raised one level when soil is already saturated or the
site sits in a local depression. Nothing here is calibrated against past
floods; the response says so in `method`.
"""
from collections import OrderedDict
from copy import deepcopy
from datetime import datetime, timedelta, timezone
import json
import math
import threading
import time

import httpx
from fastapi import APIRouter, Depends, HTTPException, Query

from . import weather

ELEVATION_API = "https://api.open-meteo.com/v1/elevation"
TTL_SECONDS = 900
MAX_LOCATIONS = 128
MAX_BODY = 512 * 1024
LEVELS = ["Bình thường", "Chú ý", "Cảnh báo cao", "Nguy hiểm"]
# (yellow, orange, red) lower bounds
RAIN_24H = (50, 100, 200)
RAIN_48H = (80, 160, 300)
RAIN_72H = (100, 200, 350)
RAIN_HOUR = (20, 40, 70)
GUST_KMH = (50, 75, 100)
TMAX_C = (35, 37, 40)
SATURATED_SOIL = 0.40  # m3/m3 in the 0-7 cm layer
WET_ANTECEDENT_MM = 100
METHOD = ("Quy tắc theo ngưỡng cảnh báo, không phải mô hình học máy đã huấn luyện từ các đợt lũ. "
          "Chưa đối chiếu với mực nước, trạm đo mưa hay ảnh ngập thực tế.")


def hazard_params(latitude: float, longitude: float) -> dict:
    return dict(latitude=latitude, longitude=longitude, timezone="auto", past_days=3, forecast_days=3,
                hourly="precipitation,wind_gusts_10m,soil_moisture_0_to_7cm",
                daily="precipitation_sum,wind_gusts_10m_max,temperature_2m_max,apparent_temperature_max",
                wind_speed_unit="kmh", temperature_unit="celsius")


def http_elevations(points: list[tuple[float, float]]) -> list[float]:
    params = {"latitude": ",".join(f"{p[0]:.5f}" for p in points),
              "longitude": ",".join(f"{p[1]:.5f}" for p in points)}
    with httpx.Client(follow_redirects=False, timeout=8, trust_env=False,
                      headers={"User-Agent": "VWing-Hazards/1.0"}) as client:
        response = client.get(ELEVATION_API, params=params)
        if response.status_code != 200 or len(response.content) > MAX_BODY:
            raise ValueError("Elevation upstream unavailable")
        values = json.loads(response.content)["elevation"]
    if len(values) != len(points) or not all(isinstance(v, (int, float)) and math.isfinite(v) for v in values):
        raise ValueError("Invalid elevation response")
    return [float(v) for v in values]


def neighbour_points(lat: float, lon: float, radii_m=(1000, 3000)) -> list[tuple[float, float]]:
    cos_lat = max(0.1, math.cos(math.radians(lat)))
    points = [(lat, lon)]
    for radius in radii_m:
        for k in range(8):
            angle = math.radians(45 * k)
            points.append((lat + radius * math.cos(angle) / 110_574.0,
                           lon + radius * math.sin(angle) / (111_320.0 * cos_lat)))
    return [(max(-90.0, min(90.0, a)), ((b + 180) % 360) - 180) for a, b in points]


def terrain_context(elevations: list[float]) -> dict:
    centre, around = elevations[0], elevations[1:]
    higher = sum(1 for v in around if v > centre + 1.0)
    return {"elevation_m": round(centre, 1), "lowest_nearby_m": round(min(around), 1),
            "highest_nearby_m": round(max(around), 1), "neighbours_higher_pct": round(100 * higher / len(around)),
            "low_lying": higher / len(around) >= 0.7 and centre - min(around) <= 3.0,
            "note": "Độ cao DEM ~90 m của Open-Meteo; 16 điểm quanh vườn trong bán kính 1–3 km, chưa xét dòng chảy hay công trình thoát nước."}


def _level(value, bands) -> int:
    return sum(1 for edge in bands if value is not None and value >= edge)


def _sum(values) -> float:
    return round(sum(v for v in values if v is not None), 1)


def _clean(values, minimum=0.0):
    out = []
    for v in values:
        ok = isinstance(v, (int, float)) and not isinstance(v, bool) and math.isfinite(v) and v >= minimum
        out.append(float(v) if ok else None)
    return out


def analyse(raw: dict, terrain: dict | None, now_local: str | None = None) -> dict:
    """Pure function: Open-Meteo payload (+ optional terrain) -> hazard outlook."""
    hourly, daily = raw["hourly"], raw["daily"]
    times = hourly["time"]
    rain = _clean(hourly["precipitation"])
    gust = _clean(hourly["wind_gusts_10m"])
    soil = _clean(hourly.get("soil_moisture_0_to_7cm", []))
    if not (len(times) == len(rain) == len(gust)) or len(times) < 24:
        raise ValueError("Hourly arrays do not align")
    now = now_local or datetime.now(timezone.utc).astimezone(
        timezone(timedelta(seconds=int(raw.get("utc_offset_seconds", 0))))).strftime("%Y-%m-%dT%H:00")
    idx = next((i for i, t in enumerate(times) if t >= now), len(times) - 1)
    ahead = lambda hours: slice(idx, min(len(times), idx + hours))  # noqa: E731
    p24, p48, p72 = (_sum(rain[ahead(h)]) for h in (24, 48, 72))
    past72 = _sum(rain[max(0, idx - 72):idx])
    max_hour = max([v for v in rain[ahead(24)] if v is not None], default=0.0)
    gust_max = max([v for v in gust[ahead(72)] if v is not None], default=0.0)
    soil_now = next((v for v in reversed(soil[:idx + 1]) if v is not None), None)
    d_t = _clean(daily["temperature_2m_max"], minimum=-90)
    d_dates, d_rain, d_gust = daily["time"], _clean(daily["precipitation_sum"]), _clean(daily["wind_gusts_10m_max"])
    today = now[:10]
    future = [i for i, d in enumerate(d_dates) if d >= today]
    tmax = max([d_t[i] for i in future if d_t[i] is not None], default=None)

    hazards = []

    # --- flood / waterlogging
    base = max(_level(p24, RAIN_24H), _level(p48, RAIN_48H), _level(p72, RAIN_72H), _level(max_hour, RAIN_HOUR))
    flood, reasons = base, []
    if p24 >= RAIN_24H[0]:
        reasons.append(f"Mưa dự báo 24 giờ tới: {p24} mm (ngưỡng chú ý {RAIN_24H[0]} mm).")
    if p72 >= RAIN_72H[0]:
        reasons.append(f"Mưa tích lũy 72 giờ tới: {p72} mm.")
    if max_hour >= RAIN_HOUR[0]:
        reasons.append(f"Cường độ mưa tối đa {max_hour:.0f} mm/giờ: dễ ngập cục bộ, nước tràn rãnh.")
    if base >= 1:
        if soil_now is not None and soil_now >= SATURATED_SOIL:
            flood += 1
            reasons.append(f"Đất đã bão hòa nước (độ ẩm lớp mặt {soil_now:.2f} m³/m³) nên nước khó thấm.")
        if past72 >= WET_ANTECEDENT_MM:
            flood += 1
            reasons.append(f"3 ngày qua đã mưa {past72} mm, mưa mới dễ gây úng.")
        if terrain and terrain["low_lying"]:
            flood += 1
            reasons.append("Vườn nằm ở chỗ trũng so với vùng quanh (≥70% điểm lân cận cao hơn).")
    flood = min(3, flood)
    if not reasons:
        reasons.append(f"Mưa 24 giờ tới {p24} mm, 72 giờ {p72} mm: dưới ngưỡng cảnh báo.")
    hazards.append({
        "id": "flood", "label_vi": "Ngập úng / lũ cục bộ", "level": flood, "level_vi": LEVELS[flood],
        "risk_index": round(min(1.0, max(p24 / 200, p48 / 300, p72 / 350) * (1 + 0.15 * (flood - base if flood > base else 0))), 2),
        "reasons": reasons,
        "advice": ["Thông thoáng rãnh thoát nước, kiểm tra bờ bao.", "Dời phân bón, thuốc, máy móc lên cao.",
                   "Hoãn bón phân và phun thuốc trước đợt mưa."] if flood >= 1 else [],
    })

    # --- strong wind / storm gusts
    wl = _level(gust_max, GUST_KMH)
    hazards.append({
        "id": "wind", "label_vi": "Gió mạnh / giông lốc", "level": wl, "level_vi": LEVELS[wl],
        "risk_index": round(min(1.0, gust_max / 120), 2),
        "reasons": [f"Gió giật cao nhất 72 giờ tới: {gust_max:.0f} km/h."] + (
            ["Cây cao, giàn leo và cây đang mang quả dễ đổ, gãy cành."] if wl >= 1 else []),
        "advice": ["Chống đỡ cây, giàn; thu hoạch sớm quả đã chín.", "Cố định mái che và vật tư nhẹ."] if wl >= 1 else [],
    })

    # --- heat
    hl = _level(tmax, TMAX_C)
    hazards.append({
        "id": "heat", "label_vi": "Nắng nóng", "level": hl, "level_vi": LEVELS[hl],
        "risk_index": round(min(1.0, max(0.0, ((tmax or 25) - 25) / 17)), 2),
        "reasons": [f"Nhiệt độ cao nhất 3 ngày tới: {tmax:.0f}°C." if tmax is not None else "Chưa có nhiệt độ dự báo."],
        "advice": ["Tưới sáng sớm/chiều tối, phủ gốc giữ ẩm.", "Tránh làm việc ngoài trời lúc nắng gắt."] if hl >= 1 else [],
    })

    # --- short-term dryness (6-day window only)
    dry_rain = _sum(rain[max(0, idx - 72):min(len(times), idx + 72)])
    dry = 1 if dry_rain < 3 and (tmax or 0) >= 33 else 0
    if dry and soil_now is not None and soil_now <= 0.15:
        dry = 2
    hazards.append({
        "id": "dry", "label_vi": "Khô hạn ngắn hạn", "level": dry, "level_vi": LEVELS[dry],
        "risk_index": round(min(1.0, max(0.0, 1 - dry_rain / 20)) * (0.5 + 0.5 * (tmax or 0) / 40), 2) if dry else 0.0,
        "reasons": [f"Mưa 6 ngày (3 ngày qua + 3 ngày tới): {dry_rain} mm."
                    + (f" Độ ẩm đất mặt {soil_now:.2f} m³/m³." if soil_now is not None else "")],
        "advice": ["Lên lịch tưới, ưu tiên cây non và giai đoạn ra hoa/đậu quả."] if dry else [],
        "note": "Chỉ nhìn cửa sổ 6 ngày; khô hạn mùa vụ cần chuỗi dài hơn (xem mục Hạn hán).",
    })

    overall = max(h["level"] for h in hazards)
    worst = max(hazards, key=lambda h: (h["level"], h["risk_index"]))
    return {
        "overall_level": overall, "overall_vi": LEVELS[overall],
        "headline": (f"{worst['label_vi']}: {LEVELS[worst['level']].lower()}" if overall else "Chưa có nguy cơ thiên tai đáng chú ý trong 3 ngày tới."),
        "hazards": hazards,
        "rain": {"next_24h_mm": p24, "next_48h_mm": p48, "next_72h_mm": p72, "past_72h_mm": past72,
                 "max_hourly_mm_24h": round(max_hour, 1), "soil_moisture_0_7cm": soil_now},
        "daily": [{"date": d_dates[i], "rain_mm": d_rain[i], "gust_kmh": d_gust[i], "tmax_c": d_t[i]} for i in future],
        "terrain": terrain, "as_of": now,
    }


class HazardService:
    def __init__(self, fetcher=None, elevation_fetcher=None, *, clock=time.monotonic):
        self.fetcher = fetcher or weather.http_forecast
        self.elevation_fetcher = elevation_fetcher or http_elevations
        self.clock = clock
        self._cache = OrderedDict()
        self._lock = threading.Lock()

    def outlook(self, latitude: float, longitude: float, now_local: str | None = None) -> dict:
        key = (round(latitude, 3), round(longitude, 3))
        with self._lock:
            cached = self._cache.get(key)
            if cached and self.clock() < cached[0]:
                self._cache.move_to_end(key)
                return deepcopy(cached[1])
            warnings = []
            try:
                raw = self.fetcher(hazard_params(*key))
                terrain = None
                try:
                    terrain = terrain_context(self.elevation_fetcher(neighbour_points(*key)))
                except Exception:
                    warnings.append("Không lấy được độ cao địa hình: chưa xét vùng trũng.")
                result = analyse(raw, terrain, now_local)
                result.update(
                    latitude=key[0], longitude=key[1], timezone=raw.get("timezone"), warnings=warnings, stale=False,
                    method=METHOD, source="Open-Meteo (mô hình Best Match; độ cao Copernicus DEM)",
                    source_url="https://open-meteo.com/", attribution="Weather data by Open-Meteo.com · CC BY 4.0",
                    fetched_at=datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"))
            except Exception:
                if cached:
                    stale = deepcopy(cached[1])
                    stale["stale"] = True
                    return stale
                raise HTTPException(502, "Không tính được cảnh báo thiên tai. Vui lòng thử lại sau.") from None
            self._cache[key] = (self.clock() + TTL_SECONDS, deepcopy(result))
            while len(self._cache) > MAX_LOCATIONS:
                self._cache.popitem(last=False)
            return result


def make_router(auth, service: HazardService | None = None):
    service = service or HazardService()
    router = APIRouter(prefix="/v1/hazards", tags=["hazards"], dependencies=[Depends(auth)])

    @router.get("/outlook")
    def outlook(latitude: float = Query(ge=-90, le=90, allow_inf_nan=False),
                longitude: float = Query(ge=-180, le=180, allow_inf_nan=False)):
        return service.outlook(latitude, longitude)

    return router
