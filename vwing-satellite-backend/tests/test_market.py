"""Public source fixtures test provenance, not prediction or trading accuracy."""
from datetime import datetime, timezone
import threading

from fastapi import FastAPI, Header, HTTPException
from fastapi.testclient import TestClient
import httpx
import pytest

from app import market

COFFEE = """<h1>Giá cà phê hôm nay ngày 22/10/2026</h1>
<div class="gia--trung-binh"><span class="_trung-binh-gia">91,400<span>đ/kg</span></span>
<span class="price_change" data-price="-400">-400</span></div>
<section id="robusta-london"><h2>Giá cà phê Robusta London</h2>
<p class="update-time">Cập nhật: 21/10/2026</p>
<table><tr><th>Kỳ hạn</th><th>Giá khớp</th></tr>
<tr><td>11/26</td><td>3,352</td><td><span data-price="-58">-58</span><span>-1.70%</span></td></tr>
</table><p>Đơn vị tính: USD/tấn</p></section>
<section id="arabica-newyork"><p class="update-time">Cập nhật: 22/10/2026</p>
<table><tr><td>12/26</td><td>316.15</td><td>+4.85 1.56%</td></tr></table>
<p>Đơn vị tính: USD cent/lb; 1lb ~= 0.45kg</p></section>
<script>Giá cà phê hôm nay ngày 01/01/2099 123,456 đ/kg</script>
<p>Ví dụ lịch sử 31/12/2024: 999,999 đ/kg</p>"""
PEPPER = """<article><div class="page-gia-inner"><h1>Giá tiêu hôm nay ngày 22/10/2026</h1>
<div class="giatieu-body"><table class="giatieu-table"><tr><th>Khu vực</th><th>Giá trung bình</th><th>Thay đổi</th></tr>
<tr><td>Gia Lai</td><td>137.000</td><td>0</td></tr>
<tr><td>Đắk Lắk</td><td>140,000</td><td>+1,500</td></tr>
<tr><td>Không hợp lệ</td><td>Liên hệ</td><td>-</td></tr></table></div>
<p>Đơn vị tính: VNĐ/kg</p></div></article>"""
BANGGIANONGSAN = """<h1>Bảng giá nông sản 22/10/2026</h1>
<p>Giá sầu riêng cập nhật ngày 22/10/2026
<table><tr><th>Nông sản (tên + đơn vị tính)</th><th>Giá cũ</th><th>Giá mới</th><th>Thay đổi</th></tr>
<tr><td>Sầu Thái loại A – ĐBSCL (đồng/kg)</td><td>82.000 – 90.000</td><td>86.000 – 90.000</td><td>▲ +4.000 ~ 0 đ</td></tr></table>
<p>Giá hồ tiêu cập nhật ngày 21/10/2026
<table><tr><th>Nông sản</th><th>Giá cũ</th><th>Giá mới</th><th>Thay đổi</th></tr>
<tr><td>Tiêu đen Indonesia (USD/tấn)</td><td>6.750</td><td>6.769</td><td>▲ +19 USD (+0,3%)</td></tr></table>
<p>Giá cao su cập nhật ngày 20/10/2026
<table><tr><th>Nông sản</th><th>Giá cũ</th><th>Giá mới</th><th>Thay đổi</th></tr>
<tr><td>Cao Su Mang Yang - Mủ Nước Loại 1 (đồng/TSC/kg)</td><td>463</td><td>463</td><td>Không đổi</td></tr></table>"""
CHOCAPHE = """<h1>Giá cà phê Đắk Lắk hôm nay 23/10/2026</h1>
<h2>Bảng lịch sử giá cà phê Đắk Lắk 5 ngày gần nhất</h2>
<table><tr><th>Ngày</th><th>Giá trung bình (₫/kg)</th><th>Thay đổi</th><th>Xu hướng</th></tr>
<tr><td>22/10/2026</td><td>91.400</td><td>-400</td><td>Giảm nhẹ</td></tr>
<tr><td>21/10/2026</td><td>91.800</td><td>-2.000</td><td>Giảm mạnh</td></tr></table>"""
CHOGIA = """<h1>Giá cà phê hôm nay 22/10/2026 – Tổng hợp giá cà phê Tây Nguyên mới nhất</h1>
<h2>Bảng giá cà phê hôm nay 22/10/2026</h2>
<table><tr><th>Địa phương</th><th>Giá trung bình</th><th>Chênh lệch</th></tr>
<tr><td>Đắk Lắk</td><td>91.400</td><td>-400</td></tr>
<tr><td>Hồ tiêu</td><td>140.500</td><td>+500</td></tr>
<tr><td>Tỷ giá USD/VND</td><td>25.620</td><td>-40</td></tr></table>
<p>Dự báo 92.656 đ/kg; ví dụ lịch sử 999.000 đ/kg</p>
<h2>Giá cà phê thế giới theo kỳ hạn</h2>
<h3>Sàn Robusta London theo kỳ hạn</h3>
<table><tr><th>Kỳ hạn</th><th>Giá (USD/tấn)</th><th>Thay đổi</th></tr>
<tr><td>11/26</td><td>3.352</td><td>-58</td></tr></table>
<h3>Sàn Arabica New York theo kỳ hạn</h3>
<table><tr><th>Kỳ hạn</th><th>Giá (cent/lb)</th><th>Thay đổi</th></tr>
<tr><td>12/26</td><td>316,15</td><td>+4,85</td></tr></table>"""


def listing(host):
    return ''.join(f'<h3><a href="https://{host}/gia-nong-san-{n}.html">Bản tin giá nông sản cập nhật số {n}</a></h3>' for n in range(10, 20))


def fixture_fetch(url):
    if "giacaphe" in url:
        return COFFEE
    if "giatieu" in url:
        return PEPPER
    if "banggianongsan" in url:
        return BANGGIANONGSAN
    if "chocaphe" in url:
        return CHOCAPHE
    if "chogia" in url:
        return CHOGIA
    return listing(httpx.URL(url).host)


def test_parse_quotes_keep_explicit_dates_units_and_changes():
    rows = market.parse_prices("giacaphe", COFFEE)
    assert len(rows) == 3
    assert [(q["price"], q["unit"], q["change"], q["as_of"], q["kind"]) for q in rows] == [
        (91400, "VND/kg", -400, "2026-10-22", "domestic"),
        (3352, "USD/tấn", -58, "2026-10-21", "futures"),
        (316.15, "US cent/lb", 4.85, "2026-10-22", "futures")]
    assert all(q["source_url"] == market.FETCH_URLS["giacaphe"] for q in rows)
    pepper = market.parse_prices("giatieu", PEPPER)
    assert [(q["market"], q["price"], q["change"]) for q in pepper] == [("Gia Lai", 137000, 0), ("Đắk Lắk", 140000, 1500)]


@pytest.mark.parametrize("value,expected", [("91,400", 91400), ("137.500", 137500),
    ("316.15", 316.15), ("316,15", 316.15), ("−1.500", -1500),
    ("1.234,50", 1234.5), ("+4.85", 4.85), ("-", None), ("secret", None), ("1.2.3", None), ("9" * 400, None)])
def test_locale_and_signed_numbers(value, expected):
    assert market.parse_number(value) == expected


@pytest.mark.parametrize("html", ["<h1>Giá cà phê hôm nay</h1>91,400 đ/kg", "",
    COFFEE.replace("22/10/2026", "32/10/2026").replace("21/10/2026", "32/10/2026"),
    '<h1>Giá cà phê hôm nay ngày 22/10/2026</h1><p>Ví dụ 999,999 đ/kg</p>'])
def test_no_quotes_from_missing_invalid_dates_or_prose(html):
    assert market.parse_prices("giacaphe", html) == []


def test_missing_units_and_js_only_prices_are_not_inferred():
    assert market.parse_prices("giatieu", PEPPER.replace("VNĐ/kg", "")) == []
    assert market.parse_prices("giacaphe", COFFEE.replace("91,400", "").split('<section')[0]) == []


def test_public_news_only_same_host_article_titles_no_body_or_navigation():
    html = listing("congthuong.vn") + '<h3><a href="https://evil.test/secret-99.html">A malicious article with a long title</a></h3>'
    news = market.parse_news("congthuong", html)
    assert len(news) == 8
    assert all(set(item) == {"title", "url", "source_id", "published_at"} for item in news)
    assert all(item["published_at"] is None and item["url"].startswith("https://congthuong.vn/") for item in news)


def test_cache_refresh_failure_keeps_provenance_and_marks_stale():
    tick, broken, calls = [100.0], [False], []
    def fetch(url):
        calls.append(url)
        if broken[0]:
            raise RuntimeError("secret upstream credential")
        return fixture_fetch(url)
    service = market.MarketService(fetch, clock=lambda: tick[0], utcnow=lambda: datetime(2026, 10, 22, 12, tzinfo=timezone.utc))
    first = service.overview()
    assert not first["stale"] and len(first["sources"]) == 12 and len(first["quotes"]) == 12
    assert len(first["news"]) == 8 and {n["source_id"] for n in first["news"]} == {"congthuong", "nongnghiep"}
    first["quotes"][0]["price"] = 0  # Returned snapshots cannot corrupt cache.
    assert service.overview()["quotes"][0]["price"] == 91400 and len(calls) == 7
    tick[0] += 901
    broken[0] = True
    stale = service.overview()
    assert stale["stale"] and len(calls) == 14
    assert all(q["stale"] and q["as_of"] in ("2026-10-20", "2026-10-21", "2026-10-22") for q in stale["quotes"])
    assert all(s["status"] == "unavailable" and s["stale"] and s["fetched_at"] for s in stale["sources"] if s["id"] in market.FETCH_URLS)
    assert "secret" not in str(stale)


def test_inaccessible_and_malformed_sources_do_not_fabricate_prices():
    def failed(_):
        raise httpx.ConnectError("unavailable")
    for fetcher in (failed, lambda _: '<html>CAPTCHA login required</html>'):
        result = market.MarketService(fetcher).overview()
        assert not result["stale"] and result["partial"] and result["quotes"] == [] and result["news"] == []
        assert all(s["status"] == ("unavailable" if s["id"] in ("giacaosu", "vfa") else "link_only") for s in result["sources"] if s["id"] not in market.FETCH_URLS)


def test_missing_source_does_not_mark_available_fresh_quotes_stale():
    def fetch(url):
        if "giatieu" in url:
            raise httpx.ConnectError("unavailable")
        return fixture_fetch(url)
    result = market.MarketService(fetch).overview()
    assert result["partial"] and not result["stale"]
    assert len(result["quotes"]) == 10 and all(not q["stale"] for q in result["quotes"])
    assert next(s for s in result["sources"] if s["id"] == "giatieu")["status"] == "unavailable"


def test_average_change_does_not_use_price_data_attribute():
    html = COFFEE.replace('class="_trung-binh-gia"', 'class="_trung-binh-gia" data-price="91400"')
    assert market.parse_prices("giacaphe", html)[0]["change"] == -400
    html = html.replace('<span class="price_change" data-price="-400">-400</span>', '')
    assert market.parse_prices("giacaphe", html)[0]["change"] is None


def test_provider_fetches_run_concurrently_and_ttl_cannot_exceed_15_minutes():
    barrier = threading.Barrier(7, timeout=2)
    def fetch(url):
        barrier.wait()
        return fixture_fetch(url)
    service = market.MarketService(fetch, ttl_seconds=10000)
    assert service.ttl_seconds == 900
    result = service.overview()
    assert not result["stale"] and not result["partial"]
    assert len([s for s in result["sources"] if s["status"] == "public"]) == 7


def test_auth_is_required_before_any_provider_fetch():
    calls = []
    def auth(authorization: str = Header(default="")):
        if authorization != "Bearer test":
            raise HTTPException(401)
    service = market.MarketService(lambda url: calls.append(url) or fixture_fetch(url))
    app = FastAPI()
    app.include_router(market.make_router(auth, service))
    with TestClient(app) as client:
        assert client.get("/v1/market/overview").status_code == 401
        assert calls == []
        response = client.get("/v1/market/overview", headers={"Authorization": "Bearer test"})
        assert response.status_code == 200 and len(response.json()["sources"]) == 12


@pytest.mark.parametrize("target", ["http://giacaphe.com/", "https://127.0.0.1/", "https://giacaphe.com.evil.test/", "https://giacaphe.com@evil.test/", "https://giacaphe.com:8443/", "file:///etc/passwd"])
def test_disallowed_source_urls(target):
    with pytest.raises(ValueError):
        market._safe_url(target)


def test_fetch_validates_redirect_before_following_and_bounds_body(monkeypatch):
    calls = []
    def handler(request):
        calls.append(str(request.url))
        return httpx.Response(302, headers={"location": "https://127.0.0.1/private"})
    original = httpx.Client
    monkeypatch.setattr(market.httpx, "Client", lambda **kwargs: original(transport=httpx.MockTransport(handler), **kwargs))
    with pytest.raises(ValueError, match="not allowed"):
        market.fetch_public(market.FETCH_URLS["giacaphe"])
    assert len(calls) == 1
    def large(request):
        return httpx.Response(200, headers={"content-type": "text/html"}, content=b'x' * (market.MAX_BODY + 1))
    monkeypatch.setattr(market.httpx, "Client", lambda **kwargs: original(transport=httpx.MockTransport(large), **kwargs))
    with pytest.raises(ValueError, match="too large|limit"):
        market.fetch_public(market.FETCH_URLS["giacaphe"])


def test_source_quote_count_is_bounded():
    repeated = PEPPER.replace('<tr><td>Gia Lai</td><td>137.000</td><td>0</td></tr>', '<tr><td>Gia Lai</td><td>137.000</td><td>0</td></tr>' * 30)
    assert len(market.parse_prices("giatieu", repeated)) == 15


def test_catalog_has_twelve_requested_sources_and_fixed_dedicated_urls():
    assert len(market.CATALOG) == 12
    assert {s["id"] for s in market.CATALOG} >= {"chocaphe", "banggianongsan", "chogia"}
    assert market.FETCH_URLS["chocaphe"] == "https://chocaphe.vn/gia-ca-phe-dak-lak.cfx"
    assert market.FETCH_URLS["chogia"] == "https://chogia.vn/gia-ca-phe/"
    result = market.MarketService(fixture_fetch).overview()
    assert all(s["status"] == "public" for s in result["sources"] if s["id"] in ("chocaphe", "banggianongsan", "chogia"))


def test_banggianongsan_dates_units_ranges_and_non_futures_export_price():
    quotes = market.parse_prices("banggianongsan", BANGGIANONGSAN)
    assert [(q["commodity"], q["price"], q["unit"], q["as_of"], q["kind"], q["change"]) for q in quotes] == [
        ("durian", 86000, "VND/kg", "2026-10-22", "domestic", None),
        ("durian", 90000, "VND/kg", "2026-10-22", "domestic", None),
        ("pepper", 6769, "USD/tấn", "2026-10-21", "international", 19),
        ("rubber", 463, "VND/TSC/kg", "2026-10-20", "domestic", 0)]
    assert quotes[0]["label"].endswith("Thấp nhất") and quotes[1]["label"].endswith("Cao nhất")
    assert all(q["source_url"] == "https://banggianongsan.com/" for q in quotes)
    assert len({q["id"] for q in quotes}) == len(quotes)
    assert 88000 not in [q["price"] for q in quotes]  # No invented midpoint.


@pytest.mark.parametrize("replacement", ["Giá sầu riêng", "Giá sầu riêng cập nhật ngày 32/10/2026", "Bài báo ví dụ lịch sử ngày 22/10/2026"])
def test_banggianongsan_never_inherits_global_or_other_table_dates(replacement):
    html = BANGGIANONGSAN.replace("Giá sầu riêng cập nhật ngày 22/10/2026", replacement)
    quotes = market.parse_prices("banggianongsan", html)
    assert [q["commodity"] for q in quotes] == ["pepper", "rubber"]
    assert [q["as_of"] for q in quotes] == ["2026-10-21", "2026-10-20"]


def test_banggianongsan_table_date_does_not_use_an_earlier_date_in_context():
    html = BANGGIANONGSAN.replace("Giá sầu riêng cập nhật", "Ví dụ lịch sử 01/01/2020. Giá sầu riêng cập nhật")
    assert market.parse_prices("banggianongsan", html)[0]["as_of"] == "2026-10-22"


@pytest.mark.parametrize("bad_cell", ["Liên hệ", "86.000 – 90.000 (giá dự báo)", "90.000 – 86.000", "-86.000", "86.000 hoặc 90.000", "86.000 USD/tấn"])
def test_banggianongsan_rejects_non_numeric_ambiguous_and_conflicting_units(bad_cell):
    html = BANGGIANONGSAN.replace("86.000 – 90.000", bad_cell)
    assert all(q["commodity"] != "durian" for q in market.parse_prices("banggianongsan", html))
    html = BANGGIANONGSAN.replace("(đồng/kg)", "")
    assert all(q["commodity"] != "durian" for q in market.parse_prices("banggianongsan", html))


def test_banggianongsan_handles_header_units_multiple_markets_and_commodity_keys():
    html = """<h1>Bảng giá 22/10/2026</h1>
    <p>Giá ớt cập nhật ngày 21/10/2026<table>
    <tr><th>Vùng / Loại ớt</th><th>Giá (đ/kg)</th></tr>
    <tr><td colspan="2">Miền Tây</td></tr><tr><td>Ớt chỉ thiên hàng xuất khẩu</td><td>33.000 – 41.000</td></tr></table>
    <p>Giá bơ cập nhật ngày 22/10/2026<table>
    <tr><th>Nông sản</th><th>Giá bán lẻ (đ/kg)</th><th>Giá sỉ (đ/kg)</th><th>Giá tại vườn (đ/kg)</th></tr>
    <tr><td>Bơ Sáp</td><td>18.000–35.000</td><td>9.000–18.000</td><td>4.000–12.000</td></tr></table>
    <p>Giá hạt điều cập nhật ngày 22/10/2026<table>
    <tr><th>Nông sản</th><th>Giá cũ</th><th>Giá mới</th><th>Thay đổi</th></tr>
    <tr><td>Điều tươi (vnđ/kg)</td><td>25.000–30.000 vnđ/kg</td><td>25.000–30.000 vnđ/kg</td><td>Không đổi</td></tr></table>"""
    quotes = market.parse_prices("banggianongsan", html)
    assert len(quotes) == 10
    assert [q["commodity"] for q in quotes] == ["chilli"] * 2 + ["avocado"] * 6 + ["cashew"] * 2
    assert all(q["market"] == "Miền Tây" for q in quotes[:2])
    assert {q["label"].split(" · ")[1] for q in quotes[2:8]} == {"Giá bán lẻ (đ/kg)", "Giá sỉ (đ/kg)", "Giá tại vườn (đ/kg)"}
    assert [q["price"] for q in quotes[2:8]] == [18000, 35000, 9000, 18000, 4000, 12000]
    assert all(q["unit"] == "VND/kg" for q in quotes)


def test_banggianongsan_keeps_futures_currency_and_does_not_lose_small_tables():
    table = """<table><tr><th>Nông sản</th><th>Giá cũ</th><th>Giá mới</th><th>Thay đổi</th></tr>
    <tr><td>Robusta kỳ hạn tháng 11/2026 (USD/tấn)</td><td>3.410</td><td>3.352</td><td>▼ -58 USD (-1,7%)</td></tr>
    <tr><td>Arabica kỳ hạn tháng 12/2026 (US cent/pound)</td><td>311,30</td><td>316,15</td><td>▲ +4,85</td></tr></table>"""
    quotes = market.parse_prices("banggianongsan", "<p>Giá cà phê cập nhật ngày 22/10/2026" + table)
    assert [(q["price"], q["unit"], q["kind"], q["change"]) for q in quotes] == [(3352, "USD/tấn", "futures", -58), (316.15, "US cent/lb", "futures", 4.85)]
    many = "<p>Giá cà phê cập nhật ngày 22/10/2026" + table.replace("</table>", "")
    many += '<tr><td>Cà phê (đ/kg)</td><td>92.000</td><td>91.000</td><td>-1.000</td></tr>' * 30 + "</table>"
    many += "<p>Giá ca cao cập nhật ngày 21/10/2026" + table.replace("Robusta kỳ hạn tháng 11/2026", "Hạt cacao").replace("Arabica kỳ hạn tháng 12/2026", "Cacao tươi")
    bounded = market.parse_prices("banggianongsan", many)
    assert len(bounded) == 14 and any(q["commodity"] == "cocoa" for q in bounded)
    assert len(market.parse_prices("banggianongsan", many * 15)) <= 120


def test_chocaphe_only_latest_row_date_not_page_date_or_old_history():
    quotes = market.parse_prices("chocaphe", CHOCAPHE)
    assert len(quotes) == 1
    assert (quotes[0]["market"], quotes[0]["price"], quotes[0]["as_of"], quotes[0]["change"]) == ("Đắk Lắk", 91400, "2026-10-22", -400)
    assert market.parse_prices("chocaphe", CHOCAPHE.replace("₫/kg", "")) == []
    assert market.parse_prices("chocaphe", CHOCAPHE.replace("Đắk Lắk", "Lâm Đồng")) == []
    assert market.parse_prices("chocaphe", CHOCAPHE.replace("22/10/2026", "32/10/2026"))[0]["as_of"] == "2026-10-21"


def test_chogia_uses_only_explicit_table_units_not_forecast_prose_or_fx_rows():
    quotes = market.parse_prices("chogia", CHOGIA)
    assert [(q["price"], q["unit"], q["change"], q["kind"]) for q in quotes] == [(3352, "USD/tấn", -58, "futures"), (316.15, "cent/lb", 4.85, "futures")]
    assert all(q["as_of"] == "2026-10-22" for q in quotes)
    with_unit = CHOGIA.replace("<th>Giá trung bình</th>", "<th>Giá trung bình (VNĐ/kg)</th>")
    domestic = market.parse_prices("chogia", with_unit)[0]
    assert (domestic["market"], domestic["price"], domestic["unit"], domestic["kind"]) == ("Đắk Lắk", 91400, "VND/kg", "domestic")
    assert len(market.parse_prices("chogia", with_unit)) == 3
    assert market.parse_prices("chogia", CHOGIA.replace("22/10/2026", "32/10/2026")) == []
    assert market.parse_prices("chogia", CHOGIA.replace("USD/tấn", "").replace("cent/lb", "")) == []
