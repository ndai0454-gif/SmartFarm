import numpy as np
import pytest
from fastapi.testclient import TestClient

from app import config, geo, indices as ix
from app.main import create_app

TOKEN = "test-token-123456"
RING = [[107.8000, 11.5500], [107.8010, 11.5500], [107.8010, 11.5510], [107.8000, 11.5510], [107.8000, 11.5500]]
H = {"Authorization": f"Bearer {TOKEN}"}


def settings(**kw):
    base = dict(token=TOKEN, mode="mock", ee_project="", max_area_ha=500, max_composite_days=93, max_series_days=400, public_base_url="http://testserver")
    base.update(kw)
    return config.Settings(**base)


@pytest.fixture()
def client():
    return TestClient(create_app(settings()))


# ---------- công thức: bản NumPy (spyndex / awesome-spectral-indices) phải khớp công thức đã công bố ----------

def test_ndvi_matches_published_formula():
    n = np.array([0.5, 0.3, 0.0]); r = np.array([0.1, 0.3, 0.2])
    got = ix.compute_numpy("NDVI", {"N": n, "R": r})
    assert np.allclose(got, (n - r) / (n + r))


def test_evi_uses_reflectance_not_dn():
    # EVI có hằng số cộng nên DN (×10000) cho kết quả sai: đây là lý do phải nhân 0,0001 trước khi tính
    n, r, b = np.array([0.5]), np.array([0.1]), np.array([0.05])
    ref = ix.compute_numpy("EVI", {"N": n, "R": r, "B": b})
    assert np.allclose(ref, 2.5 * (n - r) / (n + 6 * r - 7.5 * b + 1))
    dn = ix.compute_numpy("EVI", {"N": n / ix.S2_SCALE, "R": r / ix.S2_SCALE, "B": b / ix.S2_SCALE})
    assert not np.allclose(ref, dn, atol=0.05)


def test_ndmi_is_nir_swir():
    n, s = np.array([0.5]), np.array([0.2])
    assert np.allclose(ix.compute_numpy("NDMI", {"N": n, "S1": s}), (n - s) / (n + s))


def test_required_bands_and_sentinel2_map():
    assert set(ix.required_bands("NDVI")) == {"N", "R"}
    assert set(ix.required_bands("EVI")) == {"N", "R", "B"}
    assert set(ix.required_bands("NDMI")) == {"N", "S1"}
    for idx in ix.INDICES:
        assert set(ix.required_bands(idx)) <= set(ix.S2_BAND_MAP.values())   # Sentinel-2 có đủ băng cho cả ba chỉ số


def test_cloud_classes_masked():
    assert {0, 1, 3, 8, 9, 10, 11} == set(ix.SCL_MASKED) and 4 not in ix.SCL_MASKED and 5 not in ix.SCL_MASKED


# ---------- ranh giới ----------

def test_geo_validation():
    assert len(geo.validate_ring(RING)) == 5
    assert len(geo.validate_ring(RING[:-1])) == 5                       # tự khép kín
    with pytest.raises(geo.GeoError):
        geo.validate_ring([[11.55, 107.8], [11.55, 107.801], [11.551, 107.801], [11.551, 107.8]])   # đảo vĩ/kinh
    with pytest.raises(geo.GeoError):
        geo.validate_ring([[0, 0], [1, 1], [1, 0], [0, 1], [0, 0]])      # bướm nơ tự cắt
    with pytest.raises(geo.GeoError):
        geo.validate_ring([[200, 0], [201, 0], [201, 1]])
    assert geo.area_ha(geo.validate_ring(RING)) == pytest.approx(1.21, abs=0.02)   # 109 m × 110,6 m


# ---------- API ----------

def body(**kw):
    b = {"region": {"ring": RING}, "index": "NDVI", "start": "2026-08-01", "end": "2026-09-01"}
    b.update(kw)
    return b


def test_auth_required(client):
    assert client.post("/v1/satellite/index", json=body()).status_code == 401
    assert client.post("/v1/satellite/index", json=body(), headers={"Authorization": "Bearer sai-sai-sai-sai"}).status_code == 401
    assert client.get("/v1/health").json()["mode"] == "mock"


def test_index_result_is_labelled_simulated_and_has_provenance(client):
    r = client.post("/v1/satellite/index", json=body(), headers=H)
    assert r.status_code == 200
    j = r.json()
    assert j["simulated"] is True and "MÔ PHỎNG" in j["source"]
    assert j["nominal_pixel_size_m"] == 10 and j["scene_count"] >= 1
    assert 0 < j["valid_coverage_pct"] <= 100 and -1 <= j["mean"] <= 1
    assert j["bounds"]["west"] == pytest.approx(107.8) and j["bounds"]["north"] == pytest.approx(11.551)
    assert j["caution"] and j["warnings"]
    img = client.get(j["image_url"].replace("http://testserver", ""), headers=H)
    assert img.status_code == 200 and img.content[:8] == b"\x89PNG\r\n\x1a\n"
    assert client.get(j["image_url"].replace("http://testserver", "")).status_code == 401   # ảnh cũng cần mã truy cập


def test_same_request_is_deterministic_and_cached(client):
    a = client.post("/v1/satellite/index", json=body(), headers=H).json()
    b = client.post("/v1/satellite/index", json=body(), headers=H).json()
    assert a == b


@pytest.mark.parametrize("idx", ["NDVI", "EVI", "NDMI"])
def test_all_three_indices(client, idx):
    j = client.post("/v1/satellite/index", json=body(index=idx), headers=H).json()
    assert j["index"] == idx and j["legend"]["min"] < j["legend"]["max"] and len(j["legend"]["palette"]) >= 3


def test_unknown_index_rejected(client):
    assert client.post("/v1/satellite/index", json=body(index="LST"), headers=H).status_code == 422


def test_validation_errors(client):
    assert client.post("/v1/satellite/index", json=body(end="2026-07-01"), headers=H).status_code == 422          # ngược ngày
    assert client.post("/v1/satellite/index", json=body(start="2026-01-01", end="2026-09-01"), headers=H).status_code == 422   # quá 93 ngày
    assert client.post("/v1/satellite/index", json=body(region={"ring": [[11.55, 107.8], [11.55, 107.801], [11.551, 107.801]]}), headers=H).status_code == 422
    big = [[107.0, 11.0], [107.2, 11.0], [107.2, 11.2], [107.0, 11.2]]
    r = client.post("/v1/satellite/index", json=body(region={"ring": big}), headers=H)
    assert r.status_code == 422 and "giới hạn" in r.json()["detail"]


def test_timeseries_never_turns_missing_into_zero(client):
    r = client.post("/v1/satellite/timeseries", json=body(start="2026-03-01", end="2026-09-01"), headers=H)
    assert r.status_code == 200
    j = r.json()
    assert j["simulated"] is True and j["points"]
    assert all(p["mean"] is not None and p["valid_coverage_pct"] >= 10 for p in j["points"])   # ngày thiếu ảnh bị bỏ, không thành 0
    dates = [p["date"] for p in j["points"]]
    assert dates == sorted(dates)


def test_water_change(client):
    b = {"region": {"ring": RING}, "baseline_start": "2026-08-01", "baseline_end": "2026-08-20", "event_start": "2026-09-01", "event_end": "2026-09-10"}
    r = client.post("/v1/satellite/water-change", json=b, headers=H)
    assert r.status_code == 200
    j = r.json()
    assert j["simulated"] is True and j["new_water_ha"] >= 0 and "kiểm tra thực địa" in " ".join(j["warnings"])
    bad = dict(b, event_start="2026-08-10")
    assert client.post("/v1/satellite/water-change", json=bad, headers=H).status_code == 422


def test_live_mode_requires_project():
    with pytest.raises(RuntimeError):
        import os
        os.environ["VWING_SAT_TOKEN"] = TOKEN; os.environ["VWING_EE_MODE"] = "live"; os.environ.pop("EE_PROJECT", None)
        config.load()


def test_formulas_match_awesome_spectral_indices_catalog():
    """Đối chiếu công thức spyndex với bản danh mục gốc (repo awesome-spectral-indices) nếu có trong thư mục reference."""
    import json, os, spyndex
    path = os.path.join(os.path.dirname(__file__), "..", "..", "reference", "awesome-spectral-indices", "output", "spectral-indices-dict.json")
    if not os.path.exists(path):
        pytest.skip("Không có bản sao awesome-spectral-indices")
    cat = json.load(open(path, encoding="utf-8"))["SpectralIndices"]
    for k in ("NDVI", "EVI", "NDMI"):
        assert cat[k]["formula"].replace(" ", "") == spyndex.indices[k].formula.replace(" ", ""), k
        assert set(cat[k]["bands"]) == set(spyndex.indices[k].bands)
