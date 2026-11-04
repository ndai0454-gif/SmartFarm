"""Kiểm thử toàn diện cho mô-đun Dữ liệu Không gian Mở (Open GIS & Open Data):
- GADM (`gadm-python` / `pygadm`) & Geodata (`rspatial/geodata`: SoilGrids 250m + WorldClim v2.1)
- OpenStreetMap / Overpass Turbo / `osmtogeojson` / `osmnx`
- Humanitarian Data Exchange HDX (`OCHA-DAP/hdx-python-api`)
- World Bank Open Data (`tgherzog/wbgapi`) + Spatial Join GeoDataFrame
- GDAL/OGR + Fiona + GeoPandas: đọc/chuyển đổi Shapefile (.shp.zip), GeoPackage (.gpkg), PostGIS SQL, tái chiếu CRS
"""
import base64
import io
import zipfile

from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app

TOKEN = "test-secret-token-123456"
HEADERS = {"Authorization": f"Bearer {TOKEN}"}
SAMPLE_RING = [
    [108.0540, 12.6750],
    [108.0562, 12.6750],
    [108.0562, 12.6768],
    [108.0540, 12.6768],
    [108.0540, 12.6750],
]


def _client() -> TestClient:
    st = Settings(token=TOKEN, mode="mock", ee_project=None, max_area_ha=5000.0,
                  max_composite_days=93, max_series_days=366, public_base_url="http://testserver")
    return TestClient(create_app(st))


def test_open_gis_status():
    c = _client()
    r = c.get("/v1/open-gis/status", headers=HEADERS)
    assert r.status_code == 200
    body = r.json()
    assert body["geodata_soil_worldclim"] is True
    assert "shp" in body["export_formats"]
    assert "gpkg" in body["export_formats"]
    assert "postgis" in body["export_formats"]
    assert "EPSG:32648" in body["supported_crs"]


def test_gadm_names_and_boundary():
    c = _client()
    # 1. Tìm kiếm đơn vị hành chính Việt Nam cấp 1 (Tỉnh)
    r = c.get("/v1/open-gis/gadm/names", params={"country": "VNM", "level": 1, "q": "Đắk Lắk"}, headers=HEADERS)
    assert r.status_code == 200
    data = r.json()
    assert data["country"] == "VNM"
    assert data["count"] >= 1
    assert any("Lắk" in item["name"] or "Lak" in item["name"] for item in data["items"])

    # 2. Tải ranh giới hành chính GADM quanh vườn
    r2 = c.post("/v1/open-gis/gadm/boundary", json={"country": "VNM", "level": 2, "ring": SAMPLE_RING}, headers=HEADERS)
    assert r2.status_code == 200
    b2 = r2.json()
    assert b2["features"] >= 1
    assert b2["geojson"]["type"] == "FeatureCollection"
    assert "admin" in b2


def test_geodata_soil_and_worldclim():
    c = _client()
    r = c.post("/v1/open-gis/geodata/env", json={"ring": SAMPLE_RING}, headers=HEADERS)
    assert r.status_code == 200
    body = r.json()
    assert "soil_grids" in body
    assert "worldclim" in body
    assert "phh2o" in body["soil_grids"]
    assert "soc" in body["soil_grids"]
    assert "clay" in body["soil_grids"]
    assert body["worldclim"]["annual_precip_mm"] > 500
    assert len(body["recommendations"]) >= 2


def test_osm_extract_overpass_and_convert():
    c = _client()
    # 1. Trích xuất mạng lưới đường, thuỷ văn, công trình quanh vườn
    r = c.post("/v1/open-gis/osm/extract", json={"ring": SAMPLE_RING, "buffer_m": 400}, headers=HEADERS)
    assert r.status_code == 200
    body = r.json()
    assert body["features"] >= 1
    assert body["geojson"]["type"] == "FeatureCollection"
    assert "counts" in body

    # 2. Chuyển đổi tệp OSM XML sang GeoJSON và Shapefile (.shp.zip) bằng osmtogeojson + GeoPandas
    sample_osm_xml = """<?xml version="1.0" encoding="UTF-8"?>
    <osm version="0.6">
      <node id="101" lat="12.6752" lon="108.0542">
        <tag k="name" v="Cây sầu riêng đầu nguồn"/>
        <tag k="natural" v="tree"/>
      </node>
      <node id="102" lat="12.6755" lon="108.0545"/>
      <node id="103" lat="12.6760" lon="108.0550"/>
      <way id="201">
        <nd ref="102"/>
        <nd ref="103"/>
        <tag k="highway" v="track"/>
        <tag k="name" v="Đường trục chính Khu A"/>
      </way>
    </osm>"""
    r_conv = c.post("/v1/open-gis/osm/convert", json={
        "osm_content": sample_osm_xml,
        "export_format": "shp",
        "name": "vuon_osm",
    }, headers=HEADERS)
    assert r_conv.status_code == 200
    conv_data = r_conv.json()
    assert conv_data["filename"] == "vuon_osm.shp.zip"
    assert len(conv_data["geojson"]["features"]) == 2

    # 3. Nhập trực tiếp tệp .osm qua /v1/gis/import
    r_imp = c.post("/v1/gis/import", json={
        "filename": "map.osm",
        "data_base64": base64.b64encode(sample_osm_xml.encode("utf-8")).decode("ascii"),
    }, headers=HEADERS)
    assert r_imp.status_code == 200
    assert r_imp.json()["layers"][0]["features"] == 2


def test_hdx_search_and_fetch():
    c = _client()
    r = c.get("/v1/open-gis/hdx/search", params={"q": "waterways", "country": "vnm"}, headers=HEADERS)
    assert r.status_code == 200
    body = r.json()
    assert body["count"] >= 1
    assert len(body["datasets"]) >= 1

    r2 = c.post("/v1/open-gis/hdx/fetch", json={"dataset_id": "hotosm_vnm_waterways", "ring": SAMPLE_RING}, headers=HEADERS)
    assert r2.status_code == 200
    b2 = r2.json()
    assert b2["features"] >= 1
    assert b2["geojson"]["type"] == "FeatureCollection"


def test_worldbank_indicators_and_vector_join():
    c = _client()
    r = c.post("/v1/open-gis/worldbank/join", json={
        "country": "VNM",
        "start_year": 2019,
        "end_year": 2024,
        "ring": SAMPLE_RING,
    }, headers=HEADERS)
    assert r.status_code == 200
    body = r.json()
    assert body["country"] == "VNM"
    assert len(body["indicators"]) >= 6
    joined = body["joined_geojson"]
    assert joined is not None
    props = joined["features"][0]["properties"]
    assert "wb_agri_land_pct" in props
    assert "wb_perm_crop_pct" in props


def test_shapefile_geopackage_postgis_roundtrip():
    c = _client()
    sample_gj = {
        "type": "FeatureCollection",
        "features": [{
            "type": "Feature",
            "properties": {"name": "Khu A Sầu riêng", "crop": "Durian"},
            "geometry": {"type": "Polygon", "coordinates": [SAMPLE_RING]},
        }],
    }
    # 1. Xuất ra Shapefile (.shp.zip) ở hệ chiếu UTM 48N (EPSG:32648)
    r_shp = c.post("/v1/open-gis/vector/convert", json={
        "geojson": sample_gj,
        "format": "shp",
        "target_crs": "EPSG:32648",
        "name": "khu_a",
    }, headers=HEADERS)
    assert r_shp.status_code == 200
    shp_body = r_shp.json()
    assert shp_body["filename"] == "khu_a.shp.zip"
    raw_zip = base64.b64decode(shp_body["data_base64"])
    with zipfile.ZipFile(io.BytesIO(raw_zip)) as zf:
        names = zf.namelist()
        assert any(n.endswith(".shp") for n in names)
        assert any(n.endswith(".dbf") for n in names)
        assert any(n.endswith(".prj") for n in names)

    # 2. Đọc ngược lại tệp Shapefile (.zip) bằng GeoPandas/Fiona và kiểm tra diện tích
    r_read = c.post("/v1/open-gis/vector/read", json={
        "filename": "khu_a.zip",
        "data_base64": shp_body["data_base64"],
        "target_crs": "EPSG:4326",
    }, headers=HEADERS)
    assert r_read.status_code == 200
    read_body = r_read.json()
    assert read_body["features"] == 1
    assert read_body["total_area_ha"] > 4.0

    # 3. Xuất ra tập lệnh PostGIS SQL
    r_sql = c.post("/v1/open-gis/vector/convert", json={
        "geojson": sample_gj,
        "format": "postgis",
        "target_crs": "EPSG:4326",
        "name": "khu_a",
    }, headers=HEADERS)
    assert r_sql.status_code == 200
    assert "CREATE TABLE public.khu_a" in r_sql.json()["text"]
    assert "ST_GeomFromText" in r_sql.json()["text"]
