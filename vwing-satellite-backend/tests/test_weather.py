from datetime import datetime, timezone
import math

from fastapi import FastAPI, Header, HTTPException
from fastapi.testclient import TestClient
import httpx
import pytest

from app import weather


def fixture():
    result = dict(latitude=10.8, longitude=106.7, timezone="Asia/Ho_Chi_Minh", utc_offset_seconds=25200)
    for section, units, times in [
        ("current", weather.CURRENT_UNITS, "2026-10-22T22:15"),
        ("hourly", weather.HOURLY_UNITS, ["2026-10-22T22:00", "2026-10-22T23:00"]),
        ("daily", weather.DAILY_UNITS, ["2026-10-22", "2026-10-23"]),
    ]:
        result[f"{section}_units"] = dict(units)
        data = {"time": times}
        for field in units:
            if field == "time":
                continue
            value = "2026-10-22T06:00" if field == "sunrise" else "2026-10-22T17:30" if field == "sunset" else 0 if field == "is_day" else 20
            data[field] = value if section == "current" else [value] * len(times)
        result[section] = data
    return result


def make_client(service):
    def auth(authorization: str = Header(default="")):
        if authorization != "Bearer test":
            raise HTTPException(401)
    app = FastAPI()
    app.include_router(weather.make_router(auth, service))
    return TestClient(app)


def test_auth_before_upstream_and_source_contract():
    calls = []
    service = weather.WeatherService(lambda p: calls.append(p) or fixture())
    with make_client(service) as client:
        url = "/v1/weather/forecast?latitude=10.7769&longitude=106.7009"
        assert client.get(url).status_code == 401
        assert calls == []
        response = client.get(url, headers={"Authorization": "Bearer test"})
    assert response.status_code == 200
    data = response.json()
    assert data["source"] == "Open-Meteo" and not data["stale"]
    assert data["timezone"] == "Asia/Ho_Chi_Minh" and data["utc_offset_seconds"] == 25200
    assert data["current"]["time"] == "2026-10-22T22:15"
    assert data["current_units"]["temperature_2m"] == "°C"
    assert data["hourly_units"]["wind_speed_10m"] == "km/h"
    assert data["license_url"].endswith("/by/4.0/")
    assert calls[0]["forecast_hours"] == 48 and calls[0]["forecast_days"] == 7
    assert calls[0]["timezone"] == "auto" and calls[0]["latitude"] == 10.777


@pytest.mark.parametrize("lat,lng", [("nan", "10"), ("inf", "10"), ("-inf", "10"), ("91", "10"), ("-91", "10"), ("10", "181"), ("10", "nan"), ("text", "10")])
def test_invalid_coordinates_rejected_without_fetch(lat, lng):
    calls = []
    with make_client(weather.WeatherService(lambda p: calls.append(p) or fixture())) as client:
        response = client.get(f"/v1/weather/forecast?latitude={lat}&longitude={lng}", headers={"Authorization": "Bearer test"})
    assert response.status_code == 422 and calls == []


def test_cache_rounding_refresh_stale_preserves_original_timestamps_and_units():
    tick, broken, calls = [100.0], [False], []
    now = [datetime(2026, 10, 22, 15, 20, tzinfo=timezone.utc)]
    def fetch(params):
        calls.append(params)
        if broken[0]:
            raise RuntimeError("secret upstream API key")
        return fixture()
    service = weather.WeatherService(fetch, clock=lambda: tick[0], utcnow=lambda: now[0])
    first = service.forecast(10.77691, 106.70091)
    first["current"]["temperature_2m"] = -999  # cache snapshots are isolated
    assert service.forecast(10.77692, 106.70092)["current"]["temperature_2m"] == 20 and len(calls) == 1
    tick[0] += 601
    broken[0] = True
    now[0] = datetime(2026, 10, 23, tzinfo=timezone.utc)
    stale = service.forecast(10.77691, 106.70091)
    assert stale["stale"] and stale["fetched_at"] == "2026-10-22T15:20:00Z"
    assert stale["current"]["time"] == "2026-10-22T22:15"
    assert stale["current_units"] == fixture()["current_units"] and "secret" not in str(stale)
    broken[0] = False
    fresh = service.forecast(10.77691, 106.70091)
    assert not fresh["stale"] and fresh["fetched_at"] == "2026-10-23T00:00:00Z"


def test_cache_is_bounded_and_recently_used_location_survives():
    calls = []
    service = weather.WeatherService(lambda p: calls.append(p) or fixture())
    for index in range(128):
        service.forecast(10, index)
    service.forecast(10, 0)
    service.forecast(10, 128)
    assert len(service._cache) == 128 and (10, 0) in service._cache and (10, 1) not in service._cache
    service.forecast(10, 0)
    assert len(calls) == 129


def test_null_weather_values_are_preserved_not_replaced_by_zero():
    data = fixture()
    data["current"]["precipitation"] = None
    data["hourly"]["precipitation_probability"][0] = None
    data["daily"]["sunrise"][0] = None  # valid at polar locations
    result = weather.normalize_forecast(data)
    assert result["current"]["precipitation"] is None
    assert result["hourly"]["precipitation_probability"][0] is None
    assert result["daily"]["sunrise"][0] is None


@pytest.mark.parametrize("mutation", [
    lambda d: d.pop("current"),
    lambda d: d["current"].pop("temperature_2m"),
    lambda d: d["current"].update(temperature_2m=math.nan),
    lambda d: d["current"].update(temperature_2m=math.inf),
    lambda d: d["current"].update(relative_humidity_2m=101),
    lambda d: d["current_units"].update(temperature_2m="°F"),
    lambda d: d["hourly"]["temperature_2m"].append(30),
    lambda d: d["hourly"].update(time=["bad-date", "later"]),
    lambda d: d["daily"].update(time=[]),
    lambda d: d.update(timezone=""),
    lambda d: d.update(utc_offset_seconds=math.inf),
])
def test_malformed_upstream_returns_curated_502_without_fabrication(mutation):
    data = fixture()
    mutation(data)
    with make_client(weather.WeatherService(lambda p: data)) as client:
        response = client.get("/v1/weather/forecast?latitude=10&longitude=106", headers={"Authorization": "Bearer test"})
    assert response.status_code == 502 and response.json() == {"detail": weather.ERROR_MESSAGE}


def test_unexpected_exception_never_leaks_key_or_url():
    def failed(_):
        raise RuntimeError("https://customer-api.open-meteo.com/?apikey=super-secret")
    with pytest.raises(HTTPException) as caught:
        weather.WeatherService(failed).forecast(10, 106)
    assert caught.value.status_code == 502 and caught.value.detail == weather.ERROR_MESSAGE


def test_no_stale_data_from_another_location():
    state = [False]
    def fetch(_):
        if state[0]:
            raise ValueError("failed")
        return fixture()
    service = weather.WeatherService(fetch)
    service.forecast(10, 106)
    state[0] = True
    with pytest.raises(HTTPException) as caught:
        service.forecast(20, 106)
    assert caught.value.status_code == 502


@pytest.mark.parametrize("commercial", [False, True])
def test_fixed_public_or_customer_endpoint_and_no_redirects(monkeypatch, commercial):
    key = "test-key-DO-NOT-EXPOSE"
    if commercial:
        monkeypatch.setenv("OPEN_METEO_API_KEY", key)
    else:
        monkeypatch.delenv("OPEN_METEO_API_KEY", raising=False)
    calls, options = [], []
    def handler(request):
        calls.append(request)
        return httpx.Response(302, headers={"location": "https://127.0.0.1/private"})
    original = httpx.Client
    def client(**kwargs):
        options.append(kwargs)
        return original(transport=httpx.MockTransport(handler), **kwargs)
    monkeypatch.setattr(weather.httpx, "Client", client)
    with pytest.raises(ValueError, match="unavailable"):
        weather.http_forecast({"latitude": 10, "longitude": 106})
    assert len(calls) == 1 and calls[0].url.host == ("customer-api.open-meteo.com" if commercial else "api.open-meteo.com")
    assert calls[0].url.params.get("apikey") == (key if commercial else None)
    assert options[0]["timeout"] <= 8 and options[0]["follow_redirects"] is False


def test_streaming_body_limit_and_json_type(monkeypatch):
    original = httpx.Client
    def oversized(request):
        return httpx.Response(200, headers={"content-type": "application/json"}, content=b'x' * (weather.MAX_BODY + 1))
    monkeypatch.setattr(weather.httpx, "Client", lambda **kwargs: original(transport=httpx.MockTransport(oversized), **kwargs))
    with pytest.raises(ValueError, match="limit"):
        weather.http_forecast({"latitude": 10, "longitude": 106})
    def invalid_type(request):
        return httpx.Response(200, headers={"content-type": "text/html"}, content=b'<html>login</html>')
    monkeypatch.setattr(weather.httpx, "Client", lambda **kwargs: original(transport=httpx.MockTransport(invalid_type), **kwargs))
    with pytest.raises(ValueError, match="not JSON"):
        weather.http_forecast({"latitude": 10, "longitude": 106})
