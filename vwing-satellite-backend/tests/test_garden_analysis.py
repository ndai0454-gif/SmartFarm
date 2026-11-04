import pytest
from fastapi.testclient import TestClient

from app import config
from app.main import create_app

TOKEN = "test-token-123456"
H = {"Authorization": f"Bearer {TOKEN}"}
RING = [[107.8000, 11.5500], [107.8010, 11.5500], [107.8010, 11.5510], [107.8000, 11.5510], [107.8000, 11.5500]]


@pytest.fixture()
def client():
    st = config.Settings(
        token=TOKEN,
        mode="mock",
        ee_project="",
        max_area_ha=500,
        max_composite_days=93,
        max_series_days=400,
        public_base_url="http://testserver",
    )
    return TestClient(create_app(st))


def test_land_cover_sources_and_crop_profiles(client):
    r_src = client.get("/v1/satellite/land-cover/sources", headers=H)
    assert r_src.status_code == 200
    sources = r_src.json()
    ids = {s["id"] for s in sources}
    assert {
        "dynamic_world",
        "esa_worldcover",
        "esri_10m_lulc",
        "copernicus_cgls_lc100",
        "modis_mcd12q1",
        "jaxa_vietnam_lulc",
        "servir_mekong_rlcms",
        "usgs_gfsad30",
        "vnforest_formis",
    } <= ids

    r_crops = client.get("/v1/satellite/crop-profiles", headers=H)
    assert r_crops.status_code == 200
    crop_keys = {c["key"] for c in r_crops.json()}
    assert {"durian", "coffee", "pepper", "citrus", "mango_avocado", "rubber", "cashew", "tea"} <= crop_keys


def test_garden_analysis_workflow_tree_count_and_land_metrics(client):
    zones = {
        "type": "FeatureCollection",
        "features": [
            {
                "type": "Feature",
                "properties": {"name": "Khu A"},
                "geometry": {
                    "type": "Polygon",
                    "coordinates": [[[107.8000, 11.5500], [107.8005, 11.5500], [107.8005, 11.5510], [107.8000, 11.5510], [107.8000, 11.5500]]],
                },
            },
            {
                "type": "Feature",
                "properties": {"name": "Khu B"},
                "geometry": {
                    "type": "Polygon",
                    "coordinates": [[[107.8005, 11.5500], [107.8010, 11.5500], [107.8010, 11.5510], [107.8005, 11.5510], [107.8005, 11.5500]]],
                },
            },
        ],
    }
    payload = {
        "region": {"ring": RING},
        "start": "2026-08-01",
        "end": "2026-09-01",
        "crop_type": "durian",
        "zones": zones,
    }
    r = client.post("/v1/satellite/garden-analysis", headers=H, json=payload)
    assert r.status_code == 200, r.text
    j = r.json()

    # 1. Geometry & Terrain
    assert j["area_ha"] == pytest.approx(1.21, abs=0.05)
    assert j["perimeter_m"] > 400
    assert j["geometry"]["vertices"] == 4
    assert j["terrain"]["elevation_mean_m"] is not None
    assert j["terrain"]["slope_mean_deg"] > 0

    # 2. Multi-source Vietnam LULC
    lc = j["land_cover"]
    assert {"dynamic_world", "esa_worldcover", "jaxa_vietnam_lulc", "copernicus_cgls_lc100"} <= set(lc.keys())
    assert lc["dynamic_world"]["tree_canopy_pct"] > 0
    assert len(j["vietnam_lulc_reference"]) == 9

    # 3. 7 Land & Canopy Indices
    idx_names = [x["index"] for x in j["indices"]]
    assert idx_names == ["NDVI", "EVI", "NDRE", "NDMI", "SAVI", "NDWI", "BSI"]
    for item in j["indices"]:
        assert item["mean"] is not None and item["p5"] <= item["mean"] <= item["p95"]
        assert item["status_vi"] and item["interpretation_vi"]

    # 4. Tree count & canopy segmentation
    te = j["tree_estimation"]
    hb = te["health_breakdown"]
    assert te["crop_type"] == "durian"
    assert te["estimated_trees"] > 0
    assert te["estimated_trees_min"] <= te["estimated_trees"] <= te["estimated_trees_max"]
    assert hb["healthy_trees"] + hb["moderate_trees"] + hb["stressed_trees"] == te["estimated_trees"]
    assert 0 < te["canopy_area_ha"] <= j["area_ha"]
    assert len(te["sample_tree_points_geojson"]["features"]) > 0
    assert len(te["zone_tree_counts"]) == 2

    # 5. Coffee (3x3m) should yield significantly more trees than Durian (8x8m) on the same polygon
    r_coffee = client.post("/v1/satellite/garden-analysis", headers=H, json={**payload, "crop_type": "coffee"})
    assert r_coffee.status_code == 200
    assert r_coffee.json()["tree_estimation"]["estimated_trees"] > te["estimated_trees"] * 3

    # 6. Overlay PNG image
    img_path = j["image_url"].replace("http://testserver", "")
    img = client.get(img_path, headers=H)
    assert img.status_code == 200 and img.content.startswith(b"\x89PNG\r\n\x1a\n")


def test_gee_garden_clarity_endpoint_returns_layers_and_pngs(client):
    r = client.post(
        "/v1/satellite/gee-garden-clarity",
        headers=H,
        json={"region": {"ring": RING}, "crop_type": "durian", "buffer_m": 45.0},
    )
    assert r.status_code == 200
    j = r.json()
    assert j["area_ha"] == pytest.approx(1.21, abs=0.05)
    assert j["estimated_trees"] > 0
    assert j["canopy_cover_pct"] > 0
    assert len(j["layers"]) == 4
    layer_ids = [x["layer_id"] for x in j["layers"]]
    assert layer_ids == [
        "GEE_CLARITY_TRUE_COLOR",
        "GEE_FALSE_COLOR_NIR",
        "GEE_CANOPY_CROWN_HD",
        "GEE_MOISTURE_WATER",
    ]
    for lyr in j["layers"]:
        img_path = lyr["image_url"].replace("http://testserver", "")
        img = client.get(img_path, headers=H)
        assert img.status_code == 200 and img.content.startswith(b"\x89PNG\r\n\x1a\n")


def test_ag_research_9papers_catalog_and_suite(client):
    r_cat = client.get("/v1/satellite/ag-research-catalog", headers=H)
    assert r_cat.status_code == 200
    cat = r_cat.json()
    assert len(cat) == 8
    item_nums = [c["item_number"] for c in cat]
    assert item_nums == ["1", "2", "3–4", "5", "6", "7", "8", "9"]

    r_suite = client.post(
        "/v1/satellite/ag-research-suite",
        headers=H,
        json={"region": {"ring": RING}, "crop_type": "durian"},
    )
    assert r_suite.status_code == 200, r_suite.text
    s = r_suite.json()
    assert s["area_ha"] == pytest.approx(1.21, abs=0.05)
    assert s["estimated_trees"] > 0
    assert s["paper1_ai_agriculture_book"]["target_2030_yield_benchmark_pct"] == 70.0
    assert s["paper2_tls_uav_biomass"]["tls_3d_crown_volume_m3_per_tree"] > 0
    assert s["paper2_tls_uav_biomass"]["aboveground_biomass_kg_per_tree"] > 0
    assert "3 và mục 4" in s["paper3_4_dual_attention_nowcasting"]["duplicate_import_notice_vi"]
    assert s["paper5_planosol_topsoil_sand"]["topsoil_sand_pct"] > 0
    assert s["paper6_jataed_precision_ag"]["doi"] == "10.62671/jataed.v2i2.88"
    assert s["paper7_msta_yolo_landslide"]["landslide_susceptibility_score"] > 0
    assert s["paper8_hrnet_ca_deeplab_segmentation"]["boundary_f1_score"] > 0.8
    assert len(s["paper9_pgis_free_gis_sources"]["sources"]) == 5


