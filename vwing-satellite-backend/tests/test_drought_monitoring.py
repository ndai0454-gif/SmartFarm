from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app


def _client() -> TestClient:
    st = Settings(
        token="test-token",
        mode="mock",
        ee_project="",
        public_base_url="http://testserver",
        max_area_ha=5000.0,
        max_composite_days=180,
        max_series_days=366,
    )
    return TestClient(create_app(st))


def _sample_ring_vn():
    return [
        [106.350, 10.280],
        [106.356, 10.280],
        [106.356, 10.286],
        [106.350, 10.286],
        [106.350, 10.280],
    ]


def _sample_ring_basra():
    return [
        [47.780, 30.500],
        [47.790, 30.500],
        [47.790, 30.510],
        [47.780, 30.510],
        [47.780, 30.500],
    ]


def test_drought_monitoring_26_years_vn_and_basra():
    client = _client()
    resp = client.post(
        "/v1/drought-monitoring/analyze",
        headers={"Authorization": "Bearer test-token"},
        json={
            "region": {"ring": _sample_ring_vn()},
            "study_mode": "LOCAL_AND_BASRA",
            "focus_year": 2025,
            "vhi_alpha": 0.5,
            "overlay_metric": "VHI",
            "buffer_m": 300.0,
        },
    )
    assert resp.status_code == 200, resp.text
    data = resp.json()

    # 1. Six core indices
    codes = [item["code"] for item in data["six_core_indices"]]
    assert codes == ["NDVI", "VCI", "LST", "TCI", "VHI", "RAINFALL_CHIRPS"]

    # 2. Four reference years (2000, 2010, 2020, 2025)
    ref_years = [item["year"] for item in data["reference_years_comparison"]]
    assert ref_years == [2000, 2010, 2020, 2025]
    for ref in data["reference_years_comparison"]:
        assert 0.0 <= ref["vci_mean_pct"] <= 100.0
        assert 0.0 <= ref["tci_mean_pct"] <= 100.0
        assert 0.0 <= ref["vhi_mean_pct"] <= 100.0
        assert ref["lst_spatial_zones"]["bare_vs_water_gradient_c"] > 0.0

    # 3. 26-year annual time series (2000 -> 2025)
    series = data["annual_time_series_2000_2025"]
    assert len(series) == 26
    assert series[0]["year"] == 2000
    assert series[-1]["year"] == 2025

    # Wettest year 2018 & driest year 2021
    dec = data["decoupling_and_lag_analysis"]
    assert dec["wettest_year"]["year"] == 2018
    assert dec["driest_year"]["year"] == 2021
    assert dec["pearson_r_rain_ndvi_lag_1y"] > dec["pearson_r_rain_ndvi_same_year"]
    assert dec["pearson_r_lst_ndvi"] < 0.0
    assert len(dec["driving_factors"]) == 5

    # 4. NDVI Change Map 2000-2025
    ndvi_chg = data["ndvi_change_2000_2025"]
    assert len(ndvi_chg["change_classes"]) == 5
    total_pct = sum(c["area_pct"] for c in ndvi_chg["change_classes"])
    assert 99.5 <= total_pct <= 100.5

    # 5. Basra, Iraq Reference Benchmark
    basra = data["basra_iraq_reference_study"]
    assert basra["wettest_year_2018"]["year"] == 2018
    assert basra["driest_year_2021"]["year"] == 2021
    assert basra["wettest_year_2018"]["rainfall_mm"] > 240.0
    assert basra["driest_year_2021"]["rainfall_mm"] < 60.0

    # 6. Verify PNG overlay image is stored and retrievable
    overlay_url = data["active_overlay_url"]
    assert overlay_url.startswith("/v1/mock/image/")
    img_resp = client.get(overlay_url, headers={"Authorization": "Bearer test-token"})
    assert img_resp.status_code == 200
    assert img_resp.headers["content-type"] == "image/png"


def test_drought_monitoring_basra_polygon_and_delta_ndvi_overlay():
    client = _client()
    resp = client.post(
        "/v1/drought-monitoring/analyze",
        headers={"Authorization": "Bearer test-token"},
        json={
            "region": {"ring": _sample_ring_basra()},
            "study_mode": "BASRA_IRAQ_REFERENCE",
            "focus_year": 2021,
            "vhi_alpha": 0.5,
            "overlay_metric": "DELTA_NDVI",
        },
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["fao_gaul_boundary"]["adm1_code"] == 1542
    assert "Basra" in data["fao_gaul_boundary"]["adm1_name"]
    assert data["focus_year"] == 2021
    assert data["focus_year_snapshot"]["rainfall_mm"] == 54.2
    assert data["overlay_metric"] == "DELTA_NDVI"
