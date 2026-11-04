"""Authenticated model forecasts from Open-Meteo with bounded local caching."""
from collections import OrderedDict
from copy import deepcopy
from datetime import date, datetime, timezone
import json
import math
import os
import threading
import time

import httpx
from fastapi import APIRouter, Depends, HTTPException, Query

PUBLIC_API = "https://api.open-meteo.com/v1/forecast"
CUSTOMER_API = "https://customer-api.open-meteo.com/v1/forecast"
MAX_BODY = 1024 * 1024
TTL_SECONDS = 600
MAX_LOCATIONS = 128
CURRENT_UNITS = {"time": "iso8601", "temperature_2m": "°C", "relative_humidity_2m": "%",
                 "apparent_temperature": "°C", "is_day": "", "precipitation": "mm",
                 "weather_code": "wmo code", "cloud_cover": "%", "wind_speed_10m": "km/h",
                 "wind_direction_10m": "°"}
HOURLY_UNITS = {"time": "iso8601", "temperature_2m": "°C", "precipitation_probability": "%",
                "weather_code": "wmo code", "wind_speed_10m": "km/h"}
DAILY_UNITS = {"time": "iso8601", "weather_code": "wmo code", "temperature_2m_max": "°C",
               "temperature_2m_min": "°C", "precipitation_probability_max": "%",
               "sunrise": "iso8601", "sunset": "iso8601", "uv_index_max": ""}
ERROR_MESSAGE = "Không lấy được dự báo thời tiết. Vui lòng thử lại sau."


def http_forecast(params: dict) -> dict:
    """Only fixed HTTPS upstreams; commercial credentials never enter responses."""
    key = os.environ.get("OPEN_METEO_API_KEY", "").strip()
    url = CUSTOMER_API if key else PUBLIC_API
    request_params = dict(params)
    if key:
        request_params["apikey"] = key
    deadline = time.monotonic() + 8
    with httpx.Client(follow_redirects=False, timeout=8, trust_env=False,
                      headers={"User-Agent": "VWing-Weather/1.0"}) as client:
        with client.stream("GET", url, params=request_params) as response:
            # Redirects are rejected, including redirects to another public host.
            if response.status_code != 200:
                raise ValueError("Weather upstream unavailable")
            if "application/json" not in response.headers.get("content-type", "").lower():
                raise ValueError("Weather response is not JSON")
            if int(response.headers.get("content-length", "0")) > MAX_BODY:
                raise ValueError("Weather response exceeds body limit")
            body = bytearray()
            for chunk in response.iter_bytes():
                body.extend(chunk)
                if len(body) > MAX_BODY or time.monotonic() >= deadline:
                    raise ValueError("Weather response body or deadline limit")
    return json.loads(body)


def _number(value, *, low=None, high=None, nullable=True):
    if value is None and nullable:
        return
    if (isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value)
            or (low is not None and value < low) or (high is not None and value > high)):
        raise ValueError("Invalid weather number")


def _timestamp(value, *, day=False, nullable=False):
    if value is None and nullable:
        return
    if not isinstance(value, str) or not value:
        raise ValueError("Invalid weather timestamp")
    if day:
        date.fromisoformat(value)
    else:
        if "T" not in value:
            raise ValueError("Missing weather time")
        datetime.fromisoformat(value)


def _value(field, value):
    if field in ("sunrise", "sunset"):
        _timestamp(value, nullable=True)
    elif field in ("relative_humidity_2m", "cloud_cover", "precipitation_probability", "precipitation_probability_max"):
        _number(value, low=0, high=100)
    elif field == "wind_direction_10m":
        _number(value, low=0, high=360)
    elif field == "is_day":
        _number(value, low=0, high=1)
        if value is not None and value not in (0, 1):
            raise ValueError("Invalid weather day flag")
    elif field == "weather_code":
        _number(value, low=0, high=99)
        if value is not None and value != int(value):
            raise ValueError("Invalid weather code")
    elif field in ("precipitation", "wind_speed_10m", "uv_index_max"):
        _number(value, low=0)
    else:
        _number(value)


def normalize_forecast(payload: dict) -> dict:
    """Validate requested variables; keep source timestamps, nulls and units intact."""
    if not isinstance(payload, dict) or payload.get("error"):
        raise ValueError("Invalid forecast payload")
    _number(payload.get("latitude"), low=-90, high=90, nullable=False)
    _number(payload.get("longitude"), low=-180, high=180, nullable=False)
    offset = payload.get("utc_offset_seconds")
    _number(offset, low=-86400, high=86400, nullable=False)
    if offset != int(offset):
        raise ValueError("Invalid forecast UTC offset")
    zone = payload.get("timezone")
    if not isinstance(zone, str) or not zone or len(zone) > 100:
        raise ValueError("Missing forecast timezone")
    result = {key: payload[key] for key in ("latitude", "longitude", "timezone", "utc_offset_seconds")}
    for section, expected, limit in [("current", CURRENT_UNITS, None), ("hourly", HOURLY_UNITS, 48), ("daily", DAILY_UNITS, 7)]:
        data, units = payload.get(section), payload.get(f"{section}_units")
        if not isinstance(data, dict) or not isinstance(units, dict):
            raise ValueError("Missing forecast section")
        if any(units.get(key) != unit or key not in data for key, unit in expected.items()):
            raise ValueError("Missing forecast variable or unexpected unit")
        if section == "current":
            _timestamp(data["time"])
            for field in expected:
                if field != "time":
                    _value(field, data[field])
            result[section] = {key: data[key] for key in expected}
            if "interval" in data:
                _number(data["interval"], low=1, nullable=False)
                if units.get("interval") != "seconds":
                    raise ValueError("Unexpected forecast interval unit")
                result[section]["interval"] = data["interval"]
        else:
            times = data["time"]
            if not isinstance(times, list) or not 1 <= len(times) <= limit:
                raise ValueError("Invalid forecast array length")
            for value in times:
                _timestamp(value, day=section == "daily")
            if times != sorted(set(times)):
                raise ValueError("Forecast timestamps are not ordered")
            for field in expected:
                values = data[field]
                if not isinstance(values, list) or len(values) != len(times):
                    raise ValueError("Forecast arrays do not align")
                if field != "time":
                    for value in values:
                        _value(field, value)
            result[section] = {key: data[key] for key in expected}
        result[f"{section}_units"] = {key: units[key] for key in expected}
        if section == "current" and "interval" in data:
            result["current_units"]["interval"] = units["interval"]
    return deepcopy(result)


class WeatherService:
    def __init__(self, fetcher=None, *, clock=time.monotonic, utcnow=None):
        self.fetcher = fetcher or http_forecast
        self.clock = clock
        self.utcnow = utcnow or (lambda: datetime.now(timezone.utc))
        self._cache = OrderedDict()
        self._lock = threading.Lock()

    def forecast(self, latitude: float, longitude: float):
        try:
            _number(latitude, low=-90, high=90, nullable=False)
            _number(longitude, low=-180, high=180, nullable=False)
        except ValueError:
            raise HTTPException(422, "Tọa độ thời tiết không hợp lệ.") from None
        location = (round(latitude, 3), round(longitude, 3))
        # One lock also avoids duplicate upstream requests for the same location.
        with self._lock:
            cached = self._cache.get(location)
            if cached:
                self._cache.move_to_end(location)
                if self.clock() < cached[0]:
                    return deepcopy(cached[1])
            params = dict(latitude=location[0], longitude=location[1], timezone="auto",
                          current=",".join(key for key in CURRENT_UNITS if key != "time"),
                          hourly=",".join(key for key in HOURLY_UNITS if key != "time"),
                          daily=",".join(key for key in DAILY_UNITS if key != "time"),
                          forecast_hours=48, forecast_days=7, temperature_unit="celsius", wind_speed_unit="kmh")
            try:
                result = normalize_forecast(self.fetcher(params))
                result.update(source="Open-Meteo", source_url="https://open-meteo.com/",
                              attribution="Weather data by Open-Meteo.com · CC BY 4.0",
                              license_url="https://creativecommons.org/licenses/by/4.0/",
                              note="Dự báo từ mô hình thời tiết Best Match của Open-Meteo; không phải số đo tại trạm trong vườn.",
                              fetched_at=self.utcnow().astimezone(timezone.utc).isoformat().replace("+00:00", "Z"), stale=False)
            except Exception:
                if cached:
                    result = deepcopy(cached[1])
                    result["stale"] = True
                    return result
                raise HTTPException(502, ERROR_MESSAGE) from None
            self._cache[location] = (self.clock() + TTL_SECONDS, deepcopy(result))
            self._cache.move_to_end(location)
            while len(self._cache) > MAX_LOCATIONS:
                self._cache.popitem(last=False)
            return result


def make_router(auth, service: WeatherService | None = None):
    service = service or WeatherService()
    router = APIRouter(prefix="/v1/weather", tags=["weather"], dependencies=[Depends(auth)])

    @router.get("/forecast")
    def forecast(latitude: float = Query(ge=-90, le=90, allow_inf_nan=False),
                 longitude: float = Query(ge=-180, le=180, allow_inf_nan=False)):
        return service.forecast(latitude, longitude)

    return router
