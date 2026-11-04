import base64
import json

import pytest
from fastapi.testclient import TestClient

from app import config, gis_tools
from app.main import create_app

TOKEN = "test-token-123456"
H = {"Authorization": f"Bearer {TOKEN}"}
RING = [[108.0, 12.0], [108.001, 12.0], [108.001, 12.001], [108.0, 12.001], [108.0, 12.0]]
pytestmark = pytest.mark.skipif(not gis_tools.available(), reason="chưa cài rustgis")


@pytest.fixture()
def client():
    st = config.Settings(token=TOKEN, mode="mock", ee_project="", max_area_ha=500, max_composite_days=93, max_series_days=400, public_base_url="http://testserver")
    return TestClient(create_app(st))


def fc(ring=RING, name="Ranh", **props):
    return {"type": "FeatureCollection", "features": [{"type": "Feature", "properties": {"name": name, **props}, "geometry": {"type": "Polygon", "coordinates": [ring]}}]}


def test_requires_token(client):
    assert client.get("/v1/gis/status").status_code == 401
    assert client.get("/v1/gis/status", headers=H).json()["engine"] == "rustgis"


def test_import_kml_gpx_and_reject_bad_files(client):
    kml = ('<?xml version="1.0"?><kml xmlns="http://www.opengis.net/kml/2.2"><Document><Placemark><name>Vuon</name><Polygon><outerBoundaryIs><LinearRing><coordinates>'
           '108,12,0 108.001,12,0 108.001,12.001,0 108,12.001,0 108,12,0</coordinates></LinearRing></outerBoundaryIs></Polygon></Placemark></Document></kml>')
    r = client.post("/v1/gis/import", headers=H, json={"filename": "vuon.kml", "data_base64": base64.b64encode(kml.encode()).decode()})
    assert r.status_code == 200, r.text
    lay = r.json()["layers"][0]
    assert lay["features"] == 1 and lay["geojson"]["features"][0]["geometry"]["type"] == "Polygon"
    gpx = ('<?xml version="1.0"?><gpx version="1.1" creator="t" xmlns="http://www.topografix.com/GPX/1/1"><wpt lat="12.0005" lon="108.0005"><name>Cay A-01</name></wpt></gpx>')
    r = client.post("/v1/gis/import", headers=H, json={"filename": "cay.gpx", "data_base64": base64.b64encode(gpx.encode()).decode()})
    assert r.status_code == 200 and r.json()["layers"][0]["features"] == 1
    assert client.post("/v1/gis/import", headers=H, json={"filename": "x.exe", "data_base64": "AAAA"}).status_code == 422
    assert client.post("/v1/gis/import", headers=H, json={"filename": "../x.kml", "data_base64": "AAAA"}).status_code == 422
    assert client.post("/v1/gis/import", headers=H, json={"filename": "a.kml", "data_base64": "***"}).status_code == 422
    r = client.post("/v1/gis/import", headers=H, json={"filename": "hong.kml", "data_base64": base64.b64encode(b"khong phai kml").decode()})
    assert r.status_code in (200, 422)            # không được lỗi 500
    if r.status_code == 200:
        assert r.json()["layers"] == []


def test_export_formats(client):
    for fmt, needle in [("kml", "<kml"), ("geojson", "FeatureCollection"), ("wkt", "POLYGON")]:
        r = client.post("/v1/gis/export", headers=H, json={"geojson": fc(), "format": fmt, "name": "Vuon 1"})
        assert r.status_code == 200, (fmt, r.text)
        assert needle in r.json()["text"] and r.json()["filename"] == f"Vuon_1.{fmt}"
    assert client.post("/v1/gis/export", headers=H, json={"geojson": fc(), "format": "exe"}).status_code == 422
    assert client.post("/v1/gis/export", headers=H, json={"geojson": {"type": "x"}, "format": "kml"}).status_code == 422


def test_boundary_analysis(client):
    dense = [[108.0, 12.0], [108.0005, 12.0], [108.001, 12.0], [108.001, 12.001], [108.0, 12.001], [108.0, 12.0]]
    r = client.post("/v1/gis/boundary", headers=H, json={"ring": dense, "buffers_m": [10], "simplify_m": 2.0}).json()
    assert r["valid"] and abs(r["area_ha"] - 1.2) < 0.15 and 400 < r["perimeter_m"] < 450
    assert r["simplified"]["vertices"] == 4 and len(r["centroid"]) == 2 and r["compactness"] > 0.7
    b = r["buffers"]["10.0"]
    assert b["area_ha"] > r["area_ha"]                                   # vùng đệm 10 m phải rộng hơn ranh
    bow = [[108, 12], [108.001, 12.001], [108.001, 12], [108, 12.001], [108, 12]]   # nút thắt
    bad = client.post("/v1/gis/boundary", headers=H, json={"ring": bow, "buffers_m": [10]}).json()
    assert not bad["valid"] and bad["buffers"] == {} and "tự cắt" in bad["issues"][0]
    assert client.post("/v1/gis/boundary", headers=H, json={"ring": RING, "buffers_m": [99999]}).status_code == 422
    assert client.post("/v1/gis/boundary", headers=H, json={"ring": "xx"}).status_code in (422,)


def test_tools_clip_countpoints_and_whitelist(client):
    zone = fc([[108.0, 12.0], [108.0005, 12.0], [108.0005, 12.001], [108.0, 12.001], [108.0, 12.0]], name="KhuA")
    r = client.post("/v1/gis/tool", headers=H, json={"tool": "clip", "inputs": {"Ranh": fc(), "Khu": zone}, "params": {"input": "Ranh", "clip": "Khu"}})
    assert r.status_code == 200, r.text
    from app import geo
    ring = r.json()["geojson"]["features"][0]["geometry"]["coordinates"][0]
    assert 0.5 < geo.area_ha(ring) < 0.7                                  # nửa vườn
    pts = {"type": "FeatureCollection", "features": [{"type": "Feature", "properties": {"code": c, "health": h}, "geometry": {"type": "Point", "coordinates": xy}}
                                                     for c, h, xy in [("A1", 90, [108.0002, 12.0005]), ("A2", 85, [108.0003, 12.0002]), ("B1", 60, [108.0008, 12.0005])]]}
    r = client.post("/v1/gis/tool", headers=H, json={"tool": "countPoints", "inputs": {"Khu": zone, "Cay": pts}, "params": {"polygons": "Khu", "points": "Cay"}})
    assert r.status_code == 200, r.text
    props = r.json()["geojson"]["features"][0]["properties"]
    assert 2 in [v for v in props.values() if isinstance(v, (int, float))]   # 2 cây trong khu A

    # simplify (2.0 m tự động đổi sang km)
    dense_fc = fc([[108.0, 12.0], [108.0005, 12.0], [108.001, 12.0], [108.001, 12.001], [108.0, 12.001], [108.0, 12.0]])
    r = client.post("/v1/gis/tool", headers=H, json={"tool": "simplify", "inputs": {"Ranh": dense_fc}, "params": {"input": "Ranh", "tolerance": 2.0}})
    assert r.status_code == 200 and len(r.json()["geojson"]["features"][0]["geometry"]["coordinates"][0]) == 5

    # calculateGeometry, selectByAttributes, selectByLocation, summaryStatistics
    r = client.post("/v1/gis/tool", headers=H, json={"tool": "calculateGeometry", "inputs": {"Ranh": fc()}, "params": {"input": "Ranh"}})
    assert r.status_code == 200 and "area_km2" in r.json()["geojson"]["features"][0]["properties"]
    r = client.post("/v1/gis/tool", headers=H, json={"tool": "selectByAttributes", "inputs": {"Cay": pts}, "params": {"input": "Cay", "where": "health >= 80"}})
    assert r.status_code == 200 and r.json()["features"] == 2
    r = client.post("/v1/gis/tool", headers=H, json={"tool": "selectByLocation", "inputs": {"Cay": pts, "Khu": zone}, "params": {"input": "Cay", "select": "Khu"}})
    assert r.status_code == 200 and r.json()["features"] == 2
    r = client.post("/v1/gis/tool", headers=H, json={"tool": "summaryStatistics", "inputs": {"Cay": pts}, "params": {"input": "Cay", "field": "health"}})
    assert r.status_code == 200 and r.json().get("statistics", {}).get("field") == "health"

    assert client.post("/v1/gis/tool", headers=H, json={"tool": "os.system", "inputs": {}, "params": {}}).status_code == 422
    assert client.post("/v1/gis/tool", headers=H, json={"tool": "buffer", "inputs": {"../x": fc()}, "params": {"input": "../x", "distance": 5}}).status_code == 422
    assert client.post("/v1/gis/tool", headers=H, json={"tool": "buffer", "inputs": {"R": fc()}, "params": {"input": "R"}}).status_code == 422   # thiếu distance? engine quyết định


def asc(vals):
    n = len(vals)
    return ("ncols %d\nnrows %d\nxllcorner 108.0\nyllcorner 12.0\ncellsize 0.00025\nNODATA_value -9999\n" % (n, n)
            + "\n".join(" ".join(str(v) for v in row) for row in vals)).encode()


def test_raster_zonal_stats(client):
    grid = [[0.2, 0.3, 0.4, 0.5], [0.3, 0.4, 0.5, 0.6], [0.4, 0.5, 0.6, 0.7], [0.5, 0.6, 0.7, 0.8]]
    half = [[108.0, 12.0], [108.0005, 12.0], [108.0005, 12.0005], [108.0, 12.0005], [108.0, 12.0]]
    r = client.post("/v1/gis/raster/zonal", headers=H, json={"filename": "ndvi.asc", "data_base64": base64.b64encode(asc(grid)).decode(), "ring": half})
    assert r.status_code == 200, r.text
    j = r.json()
    assert j["pixels"] == 4 and abs(j["mean"] - 0.5) < 1e-6 and j["raster"]["crs"] == "EPSG:4326"
    assert j["min"] is not None and j["max"] is not None and j["min"] <= j["mean"] <= j["max"]
    assert 90 <= j["coverage_pct"] <= 110
    r_slope = client.post("/v1/gis/raster/zonal", headers=H, json={"filename": "dem.asc", "data_base64": base64.b64encode(asc(grid)).decode(), "ring": half, "function": "slope"})
    assert r_slope.status_code == 200 and r_slope.json()["function"] == "slope"
    far = [[109.0, 13.0], [109.001, 13.0], [109.001, 13.001], [109.0, 13.001], [109.0, 13.0]]
    r = client.post("/v1/gis/raster/zonal", headers=H, json={"filename": "ndvi.asc", "data_base64": base64.b64encode(asc(grid)).decode(), "ring": far})
    assert r.status_code == 422 and "ngoài phạm vi" in r.json()["detail"]
    assert client.post("/v1/gis/raster/zonal", headers=H, json={"filename": "a.png", "data_base64": "AAAA", "ring": half}).status_code == 422
    assert client.post("/v1/gis/raster/zonal", headers=H, json={"filename": "x.asc", "data_base64": base64.b64encode(b"rac").decode(), "ring": half}).status_code == 422
    assert client.post("/v1/gis/raster/zonal", headers=H, json={"filename": "ndvi.asc", "data_base64": base64.b64encode(asc(grid)).decode(), "ring": half, "index": "xx"}).status_code == 422


def test_topology_profile_interpolate_and_render(client):
    zones = {
        "type": "FeatureCollection",
        "features": [
            {"type": "Feature", "properties": {"name": "Khu A"}, "geometry": {"type": "Polygon", "coordinates": [[[108.0, 12.0], [108.0006, 12.0], [108.0006, 12.001], [108.0, 12.001], [108.0, 12.0]]]}},
            {"type": "Feature", "properties": {"name": "Khu B"}, "geometry": {"type": "Polygon", "coordinates": [[[108.0004, 12.0], [108.0012, 12.0], [108.0012, 12.001], [108.0004, 12.001], [108.0004, 12.0]]]}},
        ],
    }
    trees = {
        "type": "FeatureCollection",
        "features": [
            {"type": "Feature", "properties": {"code": "T1", "moisture": 65.0}, "geometry": {"type": "Point", "coordinates": [108.0002, 12.0002]}},
            {"type": "Feature", "properties": {"code": "T2", "moisture": 72.0}, "geometry": {"type": "Point", "coordinates": [108.0008, 12.0002]}},
            {"type": "Feature", "properties": {"code": "T3", "moisture": 58.0}, "geometry": {"type": "Point", "coordinates": [108.0002, 12.0008]}},
            {"type": "Feature", "properties": {"code": "T4", "moisture": 80.0}, "geometry": {"type": "Point", "coordinates": [108.0008, 12.0008]}},
            {"type": "Feature", "properties": {"code": "OUT", "moisture": 50.0}, "geometry": {"type": "Point", "coordinates": [108.002, 12.002]}},
        ],
    }
    top = client.post("/v1/gis/topology", headers=H, json={"boundary": fc(), "zones": zones, "trees": trees})
    assert top.status_code == 200, top.text
    tj = top.json()
    assert not tj["valid"] and tj["outside_zone_ha"] > 0 and tj["error_count"] >= 2 and len(tj["zone_tree_counts"]) == 2

    prof = client.post("/v1/gis/profile", headers=H, json={"geojson": zones})
    assert prof.status_code == 200, prof.text
    assert prof.json()["features"] == 2 and any(f.get("field") == "area_km2" for f in prof.json()["fields"])

    idw = client.post("/v1/gis/interpolate", headers=H, json={"points": trees, "field": "moisture", "ring": RING})
    assert idw.status_code == 200, idw.text
    ij = idw.json()
    assert 50.0 <= ij["mean"] <= 80.0 and ij["pixels"] > 0

    png = client.post("/v1/gis/render", headers=H, json={"layers": {"Ranh": fc(), "Cay": trees}, "styles": {"Ranh": {"color": "#2E7D32", "outline": "#1B5E20"}, "Cay": {"color": "#FF9800"}}})
    assert png.status_code == 200 and png.headers["content-type"] == "image/png" and png.content.startswith(b"\x89PNG")

