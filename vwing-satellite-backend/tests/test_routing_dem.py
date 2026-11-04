import math

import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient

from app import config, dem, routing
from app.main import create_app

TOKEN = "test-token-123456"
H = {"Authorization": f"Bearer {TOKEN}"}
RING = [[108.0380, 12.6800], [108.0400, 12.6800], [108.0400, 12.6820], [108.0380, 12.6820], [108.0380, 12.6800]]


def square(lng, lat, d):
    return [[lng - d, lat - d], [lng + d, lat - d], [lng + d, lat + d], [lng - d, lat + d], [lng - d, lat - d]]


def iso_response(lng, lat):
    """Giống Valhalla: các đường đồng mức xếp từ lớn đến nhỏ, mỗi cái một Polygon."""
    return {"type": "FeatureCollection", "features": [
        {"type": "Feature", "properties": {"contour": 15.0, "metric": "time"}, "geometry": {"type": "Polygon", "coordinates": [square(lng, lat, 0.03)]}},
        {"type": "Feature", "properties": {"contour": 5.0, "metric": "time"}, "geometry": {"type": "Polygon", "coordinates": [square(lng, lat, 0.01)]}},
        {"type": "Feature", "properties": {"contour": 10.0, "metric": "time"}, "geometry": {"type": "MultiPolygon", "coordinates": [
            [square(lng, lat, 0.001)], [square(lng, lat, 0.02)]]}},
    ]}


def make(post=None, mode="mock"):
    calls = []

    def fake(path, body):
        calls.append((path, body))
        if post == "boom":
            raise HTTPException(502, "x")
        if path == "/isochrone":
            loc = body["locations"][0]
            return iso_response(loc["lon"], loc["lat"])
        return {"sources_to_targets": [[
            {"from_index": 0, "to_index": 0, "time": 600, "distance": 4.2},
            {"from_index": 0, "to_index": 1, "time": None, "distance": None},
        ]]}
    st = config.Settings(token=TOKEN, mode=mode, ee_project="", max_area_ha=500, max_composite_days=93, max_series_days=400, public_base_url="http://t")
    return TestClient(create_app(st, routing_post=fake)), calls


# ---------- định tuyến ----------

def test_routing_requires_token_and_validates_input():
    c, calls = make()
    body = {"origins": [{"lat": 12.68, "lng": 108.04}], "profile": "auto", "minutes": [5, 10]}
    assert c.post("/v1/routing/isochrone", json=body).status_code == 401
    bad = [
        {**body, "minutes": [0]}, {**body, "minutes": [61]}, {**body, "minutes": []}, {**body, "minutes": [1, 2, 3, 4, 5]},
        {**body, "profile": "tank"}, {**body, "origins": []}, {**body, "origins": [{"lat": 12.68, "lng": 108.04}] * 6},
        {**body, "origins": [{"lat": 95, "lng": 108}]},
    ]
    for b in bad:
        assert c.post("/v1/routing/isochrone", json=b, headers=H).status_code == 422, b
    assert calls == []                                                          # đầu vào xấu không bao giờ tới dịch vụ ngoài


def test_isochrone_returns_sorted_rings_with_area_and_caches():
    c, calls = make()
    body = {"origins": [{"lat": 12.68, "lng": 108.04, "name": "Vườn"}], "profile": "motor_scooter", "minutes": [15, 5, 10, 5]}
    r = c.post("/v1/routing/isochrone", json=body, headers=H).json()
    cs = r["origins"][0]["contours"]
    assert [x["minutes"] for x in cs] == [5, 10, 15]
    assert cs[0]["ring"][0] == cs[0]["ring"][-1] and cs[0]["area_ha"] < cs[1]["area_ha"] < cs[2]["area_ha"]
    assert r["origins"][0]["name"] == "Vườn" and "OpenStreetMap" in r["attribution"] and "kẹt xe" in r["caution"]
    sent = calls[0][1]
    assert sent["costing"] == "motor_scooter" and sent["contours"] == [{"time": 5}, {"time": 10}, {"time": 15}] and sent["polygons"] is True
    c.post("/v1/routing/isochrone", json=body, headers=H)
    assert len(calls) == 1                                                     # cùng truy vấn → bộ nhớ đệm


def test_multipolygon_keeps_the_largest_ring_only():
    c, _ = make()
    r = c.post("/v1/routing/isochrone", json={"origins": [{"lat": 12.68, "lng": 108.04}], "minutes": [10]}, headers=H).json()
    ten = r["origins"][0]["contours"][0]
    assert ten["area_ha"] > 500                                                 # vòng 0,02° (~ 4,9 km²), không phải vòng nhỏ 0,001°


def test_isochrone_errors_are_not_swallowed():
    c, _ = make("boom")
    r = c.post("/v1/routing/isochrone", json={"origins": [{"lat": 12.68, "lng": 108.04}], "minutes": [5]}, headers=H)
    assert r.status_code == 502


def test_matrix_maps_unreachable_to_null():
    c, calls = make()
    body = {"sources": [{"lat": 12.68, "lng": 108.04}], "targets": [{"lat": 12.70, "lng": 108.06}, {"lat": 12.0, "lng": 108.0}], "profile": "auto"}
    r = c.post("/v1/routing/matrix", json=body, headers=H).json()
    assert r["minutes"] == [[10.0, None]] and r["km"] == [[4.2, None]]
    assert calls[0][0] == "/sources_to_targets" and calls[0][1]["costing"] == "auto"
    assert c.post("/v1/routing/matrix", json={**body, "targets": [{"lat": 1, "lng": 1}] * 11}, headers=H).status_code == 422


def test_http_post_translates_valhalla_errors(monkeypatch):
    class R:
        def __init__(self, code, js=None): self.status_code, self._js = code, js
        def json(self):
            if self._js is None: raise ValueError
            return self._js
    for code, js, want in [(400, {"error": "Exceeded max time: 60"}, 422), (400, {"error": "No suitable edges near location"}, 422), (429, None, 503), (500, None, 502), (200, None, 502)]:
        monkeypatch.setattr(routing.httpx, "post", lambda *a, _r=R(code, js), **k: _r)
        with pytest.raises(HTTPException) as e:
            routing.http_post("/isochrone", {})
        assert e.value.status_code == want, (code, e.value.detail)


# ---------- DEM ----------

def test_grid_is_aligned_to_srtm_cell_centres_and_covers_region_plus_buffer():
    g = dem.grid_for(RING, 500)
    assert g["west"] < 108.0380 and g["east"] > 108.0400 and g["south"] < 12.6800 and g["north"] > 12.6820
    assert g["cols"] == round((g["east"] - g["west"]) / dem.STEP) and g["rows"] == round((g["north"] - g["south"]) / dem.STEP)
    for edge in (g["west"], g["south"]):             # mép ô nằm ở (k+0,5)·STEP nên tâm ô là bội số của STEP, trùng tâm ô SRTM
        assert abs((edge / dem.STEP - 0.5) - round(edge / dem.STEP - 0.5)) < 1e-6
    # vùng đệm 500 m ≈ 16 ô mỗi phía quanh vùng ~ 6×7 ô
    assert 38 <= g["cols"] <= 46 and 38 <= g["rows"] <= 46


def test_grid_limits():
    with pytest.raises(dem.DemError):
        dem.grid_for(RING, 5000)
    with pytest.raises(dem.DemError):
        dem.grid_for([[108.0, 70.0], [108.01, 70.0], [108.01, 70.01], [108.0, 70.01], [108.0, 70.0]], 100)
    big = [[108.0, 12.0], [108.3, 12.0], [108.3, 12.3], [108.0, 12.3], [108.0, 12.0]]
    with pytest.raises(dem.DemError):
        dem.grid_for(big, 3000)


def test_mock_dem_is_deterministic_labelled_simulated_and_has_a_valley():
    g = dem.grid_for(RING, 500)
    a, b = dem.mock_dem(g), dem.mock_dem(g)
    assert a == b and a["simulated"] is True and "MÔ PHỎNG" in a["source"]
    assert len(a["values"]) == a["rows"] * a["cols"] == g["rows"] * g["cols"]
    assert a["valid_pct"] == 100.0 and a["min"] < a["max"]
    assert a["bounds"]["west"] == g["west"] and 28 <= a["cell_m"] <= 32


def test_dem_endpoint_mock_and_validation():
    c, _ = make()
    body = {"region": {"ring": RING}, "buffer_m": 500}
    assert c.post("/v1/terrain/dem", json=body).status_code == 401
    r = c.post("/v1/terrain/dem", json=body, headers=H)
    assert r.status_code == 200 and r.json()["simulated"] is True and r.json()["rows"] > 3
    assert c.post("/v1/terrain/dem", json={**body, "buffer_m": 99999}, headers=H).status_code == 422
    assert c.post("/v1/terrain/dem", json={"region": {"ring": [[0, 0], [1, 1]]}}, headers=H).status_code == 422
    tiny = [[108.0, 12.0], [108.0000001, 12.0], [108.0000001, 12.0000001], [108.0, 12.0]]
    assert c.post("/v1/terrain/dem", json={"region": {"ring": tiny}}, headers=H).status_code == 422
