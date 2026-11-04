"""Rule thresholds on synthetic Open-Meteo payloads; no network."""
import pytest
from fastapi import FastAPI, HTTPException
from fastapi.testclient import TestClient

from app import hazards


def payload(rain_by_hour=None, soil=0.25, gust=15.0, tmax=30.0, hours=144):
    """3 days of history then 3 days ahead; 'now' is 2026-10-22T00:00 (index 72)."""
    times = [f"2026-10-{19 + h // 24:02d}T{h % 24:02d}:00" for h in range(hours)]
    rain = [0.0] * hours
    for hour, mm in (rain_by_hour or {}).items():
        rain[hour] = mm
    days = [f"2026-10-{19 + d:02d}" for d in range(hours // 24)]
    return {"utc_offset_seconds": 25200, "timezone": "Asia/Bangkok",
            "hourly": {"time": times, "precipitation": rain, "wind_gusts_10m": [gust] * hours,
                       "soil_moisture_0_to_7cm": [soil] * hours},
            "daily": {"time": days, "precipitation_sum": [0.0] * len(days), "wind_gusts_10m_max": [gust] * len(days),
                      "temperature_2m_max": [tmax] * len(days), "apparent_temperature_max": [tmax] * len(days)}}


NOW = "2026-10-22T00:00"
FLAT = {"elevation_m": 100, "lowest_nearby_m": 90, "highest_nearby_m": 110, "neighbours_higher_pct": 40,
        "low_lying": False, "note": ""}
LOW = {**FLAT, "neighbours_higher_pct": 90, "low_lying": True}


def level(result, hazard_id):
    return next(h for h in result["hazards"] if h["id"] == hazard_id)["level"]


def test_quiet_forecast_is_level_zero():
    r = hazards.analyse(payload(), FLAT, NOW)
    assert r["overall_level"] == 0 and "Chưa có nguy cơ" in r["headline"]


@pytest.mark.parametrize("mm_per_hour,expected", [(1.0, 0), (2.5, 1), (4.5, 2), (9.0, 3)])
def test_24h_rain_bands(mm_per_hour, expected):
    rain = {72 + h: mm_per_hour for h in range(24)}  # 24 h total = 24*mm
    r = hazards.analyse(payload(rain), FLAT, NOW)
    total = 24 * mm_per_hour
    assert r["rain"]["next_24h_mm"] == total
    assert level(r, "flood") == expected


def test_single_hour_cloudburst_flags_flood():
    r = hazards.analyse(payload({80: 45.0}), FLAT, NOW)
    assert level(r, "flood") == 2 and any("mm/giờ" in x for x in next(h for h in r["hazards"] if h["id"] == "flood")["reasons"])


def test_saturated_soil_and_lowland_raise_level_but_only_when_rain_is_significant():
    rain = {72 + h: 3.0 for h in range(24)}  # 72 mm / 24 h -> base level 1
    base = hazards.analyse(payload(rain, soil=0.2), FLAT, NOW)
    wet = hazards.analyse(payload(rain, soil=0.45), LOW, NOW)
    assert level(base, "flood") == 1 and level(wet, "flood") == 3
    dry_but_wet_soil = hazards.analyse(payload(soil=0.5), LOW, NOW)
    assert level(dry_but_wet_soil, "flood") == 0


def test_past_rain_counts_as_antecedent_not_forecast():
    rain = {h: 2.0 for h in range(72)} | {72: 60.0}
    r = hazards.analyse(payload(rain), None, NOW)
    assert r["rain"]["past_72h_mm"] == 144.0 and r["rain"]["next_24h_mm"] == 60.0
    assert level(r, "flood") >= 2  # base 1 (24 h) / hourly 60 -> 2, +antecedent


def test_wind_and_heat_levels():
    assert level(hazards.analyse(payload(gust=80.0), None, NOW), "wind") == 2
    assert level(hazards.analyse(payload(tmax=41.0), None, NOW), "heat") == 3
    assert level(hazards.analyse(payload(tmax=28.0), None, NOW), "heat") == 0


def test_dry_spell_needs_heat_and_no_rain():
    assert level(hazards.analyse(payload(tmax=36.0, soil=0.1), None, NOW), "dry") == 2
    assert level(hazards.analyse(payload(tmax=36.0, soil=0.3), None, NOW), "dry") == 1
    assert level(hazards.analyse(payload(tmax=29.0), None, NOW), "dry") == 0


def test_null_hours_do_not_crash_or_count_as_rain():
    data = payload({80: 10.0})
    data["hourly"]["precipitation"][81] = None
    assert hazards.analyse(data, None, NOW)["rain"]["next_24h_mm"] == 10.0


def test_terrain_context_flags_depression():
    ctx = hazards.terrain_context([10.0] + [14.0] * 9 + [9.0] * 7)
    assert ctx["low_lying"] is False  # only 56% of neighbours are higher
    ctx = hazards.terrain_context([10.0] + [14.0] * 16)
    assert ctx["low_lying"] is True and ctx["neighbours_higher_pct"] == 100


def test_service_caches_degrades_without_elevation_and_serves_stale():
    calls = []

    def fetch(params):
        calls.append(params)
        if len(calls) > 1:
            raise ValueError("down")
        return payload({80: 45.0})

    def no_elevation(points):
        raise ValueError("no dem")

    clock = [0.0]
    service = hazards.HazardService(fetch, no_elevation, clock=lambda: clock[0])
    first = service.outlook(11.5, 107.8, NOW)
    assert first["terrain"] is None and first["warnings"] and not first["stale"]
    assert service.outlook(11.5, 107.8, NOW) == first and len(calls) == 1
    clock[0] = hazards.TTL_SECONDS + 1
    stale = service.outlook(11.5, 107.8, NOW)
    assert stale["stale"] is True and stale["hazards"] == first["hazards"]
    with pytest.raises(HTTPException) as err:
        service.outlook(1.0, 100.0, NOW)
    assert err.value.status_code == 502


def test_router_validates_coordinates_and_requires_auth_dependency():
    service = hazards.HazardService(lambda p: payload(), lambda pts: [100.0] * len(pts))
    app = FastAPI()
    app.include_router(hazards.make_router(lambda: None, service))
    client = TestClient(app)
    assert client.get("/v1/hazards/outlook", params={"latitude": 95, "longitude": 107}).status_code == 422
    ok = client.get("/v1/hazards/outlook", params={"latitude": 11.5, "longitude": 107.8})
    assert ok.status_code == 200 and "không phải mô hình học máy" in ok.json()["method"]
