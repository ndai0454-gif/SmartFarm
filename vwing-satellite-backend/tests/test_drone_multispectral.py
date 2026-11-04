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


def _sample_ring():
    return [
        [106.350, 10.280],
        [106.356, 10.280],
        [106.356, 10.286],
        [106.350, 10.286],
        [106.350, 10.280],
    ]


def test_drone_multispectral_workflow_and_vra_prescription():
    client = _client()
    resp = client.post(
        "/v1/drone-multispectral/analyze",
        headers={"Authorization": "Bearer test-token"},
        json={
            "region": {"ring": _sample_ring()},
            "survey_drone_id": "DJI_MAVIC_3_MULTISPECTRAL",
            "sprayer_id": "DJI_AGRAS_T50",
            "crop_guide": "FRUIT_ORCHARD",
            "flight_altitude_m": 50.0,
            "overlay_layer": "VRA_PRESCRIPTION",
        },
    )
    assert resp.status_code == 200, resp.text
    data = resp.json()

    # Step 1: Survey & RTK
    s1 = data["step_1_survey_and_rtk"]
    assert s1["survey_drone"]["drone_id"] == "DJI_MAVIC_3_MULTISPECTRAL"
    assert s1["gsd_cm_per_pixel"] > 0.0
    assert "RTK FIXED" in s1["rtk_fix_status"]

    # Step 2: Mapping & 5 Vegetation Indices (NDVI, GNDVI, NDRE, EVI, VARI) + Stand Count
    s2 = data["step_2_mapping_and_indices"]
    codes = [item["code"] for item in s2["five_vegetation_indices"]]
    assert codes == ["NDVI", "GNDVI", "NDRE", "EVI", "VARI"]
    assert s2["stand_count_and_yield"]["detected_stand_count"] > 0
    assert 70.0 <= s2["stand_count_and_yield"]["emergence_survival_pct"] <= 100.0
    assert len(s2["early_anomaly_detection"]) == 3

    # Step 3: Variable-Rate Spraying (VRA) & 15%-40% Chemical Savings
    s3 = data["step_3_variable_rate_spraying"]
    assert s3["sprayer_hardware"]["sprayer_id"] == "DJI_AGRAS_T50"
    assert 15.0 <= s3["chemical_savings_pct"] <= 40.0
    assert s3["saved_spray_volume_l"] > 0.0
    assert len(s3["prescription_zones"]) == 5
    assert s3["prescription_zones"][0]["spray_rate_l_ha"] == 0.0  # Zone 0 shutoff

    # Step 4: Agricultural Insurance Evidence
    s4 = data["step_4_agricultural_insurance"]
    assert s4["certificate_id"].startswith("UAV-INS-")
    assert s4["verified_damaged_pct"] > 0.0

    # Verify overlay PNG
    img_resp = client.get(data["image_url"], headers={"Authorization": "Bearer test-token"})
    assert img_resp.status_code == 200
    assert img_resp.headers["content-type"] == "image/png"


def test_drone_multispectral_with_uploaded_camera_frames():
    client = _client()
    resp = client.post(
        "/v1/drone-multispectral/analyze",
        headers={"Authorization": "Bearer test-token"},
        json={
            "region": {"ring": _sample_ring()},
            "survey_drone_id": "WINGTRAONE_GEN2_REDEDGE_P",
            "sprayer_id": "XAG_P100_PRO",
            "crop_guide": "CORN_MAIZE",
            "flight_altitude_m": 65.0,
            "overlay_layer": "NDRE",
            "uploaded_frames": [
                {
                    "file_name": "DJI_M3M_0042_MS.JPG",
                    "width_px": 5280,
                    "height_px": 3956,
                    "mean_r": 0.068,
                    "mean_g": 0.145,
                    "mean_b": 0.051,
                    "mean_re": 0.258,
                    "mean_nir": 0.512,
                }
            ],
        },
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["uploaded_frames_count"] == 1
    assert "DJI_M3M_0042_MS.JPG" in data["ingest_summary_vi"]
    assert data["step_3_variable_rate_spraying"]["sprayer_hardware"]["sprayer_id"] == "XAG_P100_PRO"
