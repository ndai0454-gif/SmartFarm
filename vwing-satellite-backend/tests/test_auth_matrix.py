"""Ma trận xác thực: MỌI đường /v1/* (trừ /v1/health) phải đòi mã truy cập. Quét tự động theo danh sách route thật của ứng dụng,
nên thêm đường mới mà quên khoá sẽ làm test này đỏ ngay. Dùng pytest-fastapi-deps để thay riêng khâu xác thực khi cần xem phần còn lại."""
import pytest
from fastapi.testclient import TestClient

from app import config
from app.main import create_app

TOKEN = "test-token-123456"
PUBLIC = {("GET", "/v1/health")}


def make():
    st = config.Settings(token=TOKEN, mode="mock", ee_project="", max_area_ha=500, max_composite_days=93, max_series_days=400, public_base_url="http://t")
    return create_app(st, geocode_fetch=lambda path, params: {"features": []})


APP = make()


def routes():
    out = []
    for path, item in APP.openapi()["paths"].items():
        for method in item:
            if method.upper() in ("GET", "POST", "PUT", "DELETE", "PATCH"):
                out.append((method.upper(), path))
    return sorted(out)


ROUTES = [r for r in routes() if r not in PUBLIC]


def concrete(path: str) -> str:
    """Điền giá trị giả vào tham số đường dẫn như {job_id}."""
    import re
    return re.sub(r"\{[^}]+\}", "x", path)


def test_route_inventory_is_not_empty_and_covers_every_feature_area():
    paths = {p for _, p in ROUTES}
    for area in ("/v1/satellite/index", "/v1/gis/import", "/v1/gis/boundary", "/v1/geo/search", "/v1/geo/reverse", "/v1/satellite/super-resolution"):
        assert area in paths, f"thiếu {area} trong danh sách route"
    assert len(ROUTES) >= 12


@pytest.mark.parametrize("method,path", ROUTES, ids=[f"{m} {p}" for m, p in ROUTES])
def test_requires_token(method, path):
    c = TestClient(APP)
    url = concrete(path)
    assert c.request(method, url).status_code == 401, "không có mã phải bị từ chối"
    assert c.request(method, url, headers={"Authorization": "Bearer sai-ma-truy-cap"}).status_code == 401
    assert c.request(method, url, headers={"Authorization": "Basic " + TOKEN}).status_code == 401, "sai kiểu xác thực phải bị từ chối"
    assert c.request(method, url, headers={"Authorization": "Bearer " + TOKEN[:-1]}).status_code == 401, "thiếu một ký tự cũng phải bị từ chối"


def test_health_is_public_and_does_not_leak_secrets():
    r = TestClient(APP).get("/v1/health")
    assert r.status_code == 200 and set(r.json()) == {"ok", "mode"}
    assert TOKEN not in r.text


def test_with_auth_overridden_endpoints_still_validate_their_input(fastapi_dep):
    """Thay riêng khâu xác thực (pytest-fastapi-deps) để chứng minh dữ liệu sai bị chặn ở lớp nghiệp vụ chứ không chỉ nhờ mã truy cập."""
    # FastAPI mới bọc router con trong _IncludedRouter; khâu xác thực nằm ở phụ thuộc cấp router của nó
    inc = next(r for r in APP.routes if type(r).__name__ == "_IncludedRouter" and any(getattr(x, "path", "") == "/v1/gis/boundary" for x in r.original_router.routes))
    auth = inc.original_router.dependencies[0].dependency
    with fastapi_dep(APP).override({auth: lambda: None}):
        c = TestClient(APP)
        assert c.post("/v1/gis/boundary", json={"ring": "khong-phai-danh-sach"}).status_code == 422
        two = c.post("/v1/gis/boundary", json={"ring": [[108, 12], [108.001, 12.001]], "buffers_m": [10]})
        assert two.status_code == 200 and two.json()["valid"] is False and two.json()["buffers"] == {}      # ranh hỏng: báo lỗi hình học trong nội dung, không chạy vùng đệm
        assert c.get("/v1/geo/search", params={"q": "a"}).status_code == 422                  # quá ngắn
        assert c.get("/v1/geo/reverse", params={"lat": 999, "lng": 0}).status_code == 422
    assert c.post("/v1/gis/boundary", json={"ring": []}).status_code == 401                     # thoát khỏi override là bị khoá lại
