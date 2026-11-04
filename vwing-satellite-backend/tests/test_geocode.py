import pytest
from fastapi.testclient import TestClient

from app import config, geocode
from app.main import create_app

TOKEN = "test-token-123456"
H = {"Authorization": f"Bearer {TOKEN}"}
FEATURE = {"type": "Feature", "geometry": {"type": "Point", "coordinates": [108.038, 12.68]},
           "properties": {"name": "Buôn Ma Thuột", "district": "Buôn Ma Thuột", "state": "Đắk Lắk", "countrycode": "VN", "osm_value": "city",
                          "extent": [108.0, 12.75, 108.1, 12.62]}}
STREET = {"type": "Feature", "geometry": {"type": "Point", "coordinates": [108.0374, 12.6808]},
          "properties": {"name": "Hẻm 209 Quang Trung", "locality": "Tổ dân phố 6", "district": "Buôn Ma Thuột", "city": "Buôn Ma Thuột", "state": "Đắk Lắk", "osm_value": "residential"}}


def make(responses=None):
    calls = []

    def fake(path, params):
        calls.append((path, dict(params)))
        if responses == "boom":
            from fastapi import HTTPException
            raise HTTPException(502, "x")
        return {"features": responses if responses is not None else [FEATURE, {"type": "Feature", "geometry": {"type": "Point", "coordinates": ["x"]}}]}
    st = config.Settings(token=TOKEN, mode="mock", ee_project="", max_area_ha=500, max_composite_days=93, max_series_days=400, public_base_url="http://t")
    return TestClient(create_app(st, geocode_fetch=fake)), calls


def test_requires_token_and_validates_input():
    c, _ = make()
    assert c.get("/v1/geo/search?q=abc").status_code == 401
    assert c.get("/v1/geo/search?q=a", headers=H).status_code == 422
    assert c.get("/v1/geo/search?q=" + "x" * 101, headers=H).status_code == 422
    assert c.get("/v1/geo/search?q=abc&lat=999&lng=0", headers=H).status_code == 422
    assert c.get("/v1/geo/reverse?lat=1", headers=H).status_code == 422


def test_search_normalizes_biases_to_vietnam_and_caches():
    c, calls = make()
    r = c.get("/v1/geo/search?q=buon ma thuot&lat=12.7&lng=108.1", headers=H).json()
    assert len(r["results"]) == 1                                         # điểm toạ độ hỏng bị bỏ
    x = r["results"][0]
    assert x["label"] == "Buôn Ma Thuột, Đắk Lắk" and x["admin"] == "Buôn Ma Thuột, Đắk Lắk" and x["lat"] == 12.68 and x["lng"] == 108.038
    assert x["bbox"] == {"west": 108.0, "north": 12.75, "east": 108.1, "south": 12.62}
    assert calls[0][1]["bbox"] == geocode.VN_BBOX and calls[0][1]["lat"] == 12.7 and "OpenStreetMap" in r["attribution"]
    c.get("/v1/geo/search?q=buon  ma thuot&lat=12.7&lng=108.1", headers=H)          # cùng truy vấn (khoảng trắng thừa) → dùng bộ nhớ đệm
    assert len(calls) == 1
    c.get("/v1/geo/search?q=buon ma thuot&worldwide=true", headers=H)
    assert "bbox" not in calls[-1][1]


def test_reverse_gives_admin_address_for_garden_location():
    c, _ = make([STREET])
    r = c.get("/v1/geo/reverse?lat=12.68&lng=108.038", headers=H).json()["result"]
    assert r["admin"] == "Tổ dân phố 6, Buôn Ma Thuột, Đắk Lắk" and r["kind"] == "residential"
    empty, _ = make([])
    assert empty.get("/v1/geo/reverse?lat=0&lng=0", headers=H).json()["result"] is None


def test_upstream_failure_and_rate_limit():
    c, _ = make("boom")
    assert c.get("/v1/geo/search?q=abc", headers=H).status_code == 502
    c, _ = make()
    codes = [c.get(f"/v1/geo/search?q=abc{i}", headers=H).status_code for i in range(geocode.RATE_PER_MIN + 3)]
    assert codes[0] == 200 and codes[-1] == 429


def test_duplicate_names_differing_only_by_accents_are_merged():
    f = {"type": "Feature", "geometry": {"type": "Point", "coordinates": [108.04, 12.68]},
         "properties": {"locality": "Tổ dân phố 12", "district": "Buôn Ma Thuột", "city": "Buon Ma Thuot", "state": "Đắk Lắk"}}
    x = geocode.normalize(f)
    assert x["admin"] == "Tổ dân phố 12, Buôn Ma Thuột, Đắk Lắk"


def test_falls_back_to_public_demo_when_private_photon_is_down(monkeypatch):
    import httpx
    seen = []

    class R:
        def __init__(self, code, body=None): self.status_code, self._b = code, body
        def json(self): return self._b

    def fake_get(url, **kw):
        seen.append(url)
        if url.startswith("http://local"):
            raise httpx.ConnectError("down")
        return R(200, {"features": []})
    monkeypatch.setattr(geocode, "PHOTON_URL", "http://local:2322")
    monkeypatch.setattr(geocode, "PHOTON_FALLBACK_URL", "https://demo.example")
    monkeypatch.setattr(geocode.httpx, "get", fake_get)
    assert geocode.http_fetch("/api", {"q": "x"}) == {"features": []}
    assert seen == ["http://local:2322/api", "https://demo.example/api"]
    monkeypatch.setattr(geocode, "PHOTON_FALLBACK_URL", "")
    seen.clear()
    with pytest.raises(Exception) as e:
        geocode.http_fetch("/api", {"q": "x"})
    assert getattr(e.value, "status_code", None) == 502 and seen == ["http://local:2322/api"]
