"""Bounded access to public market listings; unavailable prices stay unavailable."""
from concurrent.futures import ThreadPoolExecutor
from copy import deepcopy
from datetime import date, datetime, timezone
from html.parser import HTMLParser
import math
import re
import threading
import time
import unicodedata
from urllib.parse import urljoin, urlsplit

import httpx
from fastapi import APIRouter, Depends

MAX_BODY = 2 * 1024 * 1024
TTL_SECONDS = 15 * 60
CATALOG = [
    dict(id="giacaphe", name="Giacaphe.com", url="https://giacaphe.com/gia-ca-phe-noi-dia/", category="prices", commodities=["coffee"], note="Giá tham khảo; giá nội địa và hợp đồng kỳ hạn có đơn vị riêng."),
    dict(id="giatieu", name="Giatieu.com", url="https://giatieu.com/", category="prices", commodities=["pepper"], note="Đã sửa tên miền giaitieu thành giatieu.com; lấy bảng giá công khai có ngày và đơn vị."),
    dict(id="giacaosu", name="Giacaosu.com", url="https://giacaosu.com/", category="prices", commodities=["rubber"], note="Tên miền hiện là trang rao bán, không phải bảng giá cao su. Chưa có dữ liệu từ nguồn này."),
    dict(id="vfa", name="VFA · địa chỉ được cung cấp", url="https://vietnamrice.org.vn/", category="prices", commodities=["rice"], note="Địa chỉ vietnamrice.org.vn hiện không phân giải được; chưa có bảng giá xác minh từ địa chỉ này."),
    dict(id="congthuong", name="Báo Công Thương", url="https://congthuong.vn/thi-truong/nong-san", category="news", commodities=["durian", "rice", "coffee", "pepper", "rubber", "fertilizer"], note="Chỉ tổng hợp tiêu đề và liên kết từ danh sách công khai; không suy giá từ bài viết."),
    dict(id="agromonitor", name="AgroMonitor", url="https://agromonitor.vn/", category="analysis", commodities=["all"], note="Mở nguồn; một số dữ liệu cần tài khoản hoặc gói truy cập."),
    dict(id="nongnghiep", name="Báo Nông nghiệp và Môi trường", url="https://nongnghiepmoitruong.vn/kinh-te-thi-truong/", category="news", commodities=["all"], note="Tên miền mới của nongnghiep.vn; chỉ tiêu đề và liên kết công khai."),
    dict(id="investing", name="Investing.com", url="https://vn.investing.com/commodities/", category="international", commodities=["coffee", "rubber", "cocoa", "sugar", "cotton"], note="Mở nguồn để xem giá quốc tế; không vượt kiểm soát truy cập hoặc CAPTCHA."),
    dict(id="tradingeconomics", name="Trading Economics", url="https://tradingeconomics.com/commodities", category="international", commodities=["coffee", "rubber", "rice", "cocoa", "sugar", "cotton"], note="Mở nguồn; dữ liệu/API có điều kiện cấp phép và kiểm soát truy cập."),
    dict(id="chocaphe", name="Chợ Cà Phê", url="https://chocaphe.vn/gia-ca-phe-dak-lak.cfx", category="prices", commodities=["coffee"], note="Giá trung bình Đắk Lắk từ dòng mới nhất có ngày, đơn vị trên bảng công khai; các nguồn có thể khác nhau."),
    dict(id="banggianongsan", name="Bảng Giá Nông Sản", url="https://banggianongsan.com/", category="prices", commodities=["coffee", "pepper", "rubber", "durian", "cashew", "chilli", "avocado", "cocoa", "other"], note="Giá tham khảo theo ngày từng bảng, vùng và phẩm cấp; khoảng giá tách thành mức thấp nhất/cao nhất, không lấy trung bình."),
    dict(id="chogia", name="Chợ Giá", url="https://chogia.vn/gia-ca-phe/", category="prices", commodities=["coffee"], note="Chỉ đọc bảng công khai có ngày và đơn vị rõ ràng; không dùng mức dự báo hoặc tự quy đổi giá kỳ hạn thành giá thu mua."),
]
FETCH_URLS = {
    "giacaphe": CATALOG[0]["url"],
    "giatieu": "https://giatieu.com/gia-tieu-hom-nay/",
    "congthuong": CATALOG[4]["url"],
    "nongnghiep": CATALOG[6]["url"],
    "chocaphe": CATALOG[9]["url"],
    "banggianongsan": CATALOG[10]["url"],
    "chogia": CATALOG[11]["url"],
}
ALLOWED_HOSTS = frozenset(urlsplit(url).hostname for url in FETCH_URLS.values())


def _safe_url(url: str, hosts=ALLOWED_HOSTS) -> str:
    parsed = urlsplit(url)
    if (parsed.scheme != "https" or parsed.hostname not in hosts or parsed.username
            or parsed.password or parsed.port not in (None, 443)):
        raise ValueError("Market source URL is not allowed")
    return url


def fetch_public(url: str) -> str:
    """No cookies/login/browser emulation; validate every redirect before fetching."""
    deadline = time.monotonic() + 8
    with httpx.Client(follow_redirects=False, trust_env=False,
                      headers={"User-Agent": "VWing-Market/1.0 (public listings)"}) as client:
        for _ in range(4):
            _safe_url(url)
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise TimeoutError("Market fetch deadline")
            with client.stream("GET", url, timeout=min(8, remaining)) as response:
                if response.is_redirect:
                    url = _safe_url(urljoin(url, response.headers.get("location", "")))
                    continue
                response.raise_for_status()
                content_type = response.headers.get("content-type", "").lower()
                if "text/html" not in content_type:
                    raise ValueError("Expected public HTML listing")
                if int(response.headers.get("content-length", "0")) > MAX_BODY:
                    raise ValueError("Market body too large")
                body = bytearray()
                for chunk in response.iter_bytes():
                    body.extend(chunk)
                    if len(body) > MAX_BODY or time.monotonic() >= deadline:
                        raise ValueError("Market body or deadline limit")
                return bytes(body).decode("utf-8", errors="replace")
    raise ValueError("Too many market redirects")


class _Node:
    def __init__(self, tag="root", attrs=(), parent=None):
        self.tag, self.attrs, self.parent, self.children = tag, dict(attrs), parent, []

    def nodes(self, tag=None, cls=None, ident=None):
        for child in self.children:
            if isinstance(child, _Node):
                if ((tag is None or child.tag == tag)
                        and (cls is None or cls in child.attrs.get("class", "").split())
                        and (ident is None or child.attrs.get("id") == ident)):
                    yield child
                yield from child.nodes(tag, cls, ident)

    def text(self):
        if self.tag in ("script", "style", "noscript"):
            return ""
        return " ".join(" ".join(c.text() if isinstance(c, _Node) else c
                                  for c in self.children).split())


class _Document(HTMLParser):
    def __init__(self, html):
        super().__init__(convert_charrefs=True)
        self.root = self.current = _Node()
        self.feed(unicodedata.normalize("NFC", html))

    def handle_starttag(self, tag, attrs):
        node = _Node(tag, attrs, self.current)
        self.current.children.append(node)
        if tag not in ("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"):
            self.current = node

    def handle_endtag(self, tag):
        node = self.current
        while node.parent is not None:
            if node.tag == tag:
                self.current = node.parent
                return
            node = node.parent

    def handle_data(self, value):
        self.current.children.append(value)


def parse_number(value: str) -> float | None:
    value = value.strip().replace("−", "-").replace("\u00a0", "")
    if not re.fullmatch(r"[+-]?\d+(?:[.,]\d+)*", value):
        return None
    if "." in value and "," in value:
        decimal, grouping = (".", ",") if value.rfind(".") > value.rfind(",") else (",", ".")
        value = value.replace(grouping, "").replace(decimal, ".")
    elif "." in value or "," in value:
        separator = "." if "." in value else ","
        parts = value.lstrip("+-").split(separator)
        if all(len(part) == 3 for part in parts[1:]):
            value = value.replace(separator, "")
        elif len(parts) == 2:
            value = value.replace(separator, ".")
        else:
            return None
    number = float(value)
    return number if math.isfinite(number) else None


def _date(text):
    match = re.search(r"\b(\d{1,2})/(\d{1,2})/(\d{4})\b", text)
    if not match:
        return None
    try:
        return date(int(match[3]), int(match[2]), int(match[1])).isoformat()
    except ValueError:
        return None


def _unit(text):
    for pattern, unit in [(r"(?:VNĐ|VND|đồng|đ|₫)\s*/\s*TSC\s*/\s*kg", "VND/TSC/kg"),
                          (r"(?:VNĐ|VND|đồng|đ|₫)\s*/\s*kg", "VND/kg"),
                          (r"USD\s*/\s*tấn", "USD/tấn"),
                          (r"US(?:D)?\s+cent\s*/\s*(?:lb|pound)", "US cent/lb"),
                          (r"\bcent\s*/\s*lb", "cent/lb")]:
        if re.search(pattern, text, re.I):
            return unit
    return None


def _change(cell):
    tagged = next((node for node in cell.nodes() if "data-price" in node.attrs), None)
    if tagged is not None:
        return parse_number(tagged.attrs["data-price"])
    match = re.match(r"([+−-]?\d[\d.,]*)", cell.text())
    return parse_number(match[1]) if match else None


def _quote(source_id, commodity, label, market, price, unit, change, as_of, kind, key):
    return dict(id=f"{source_id}:{key}", commodity=commodity, label=label, market=market,
                price=price, unit=unit, change=change, source_id=source_id,
                source_url=FETCH_URLS[source_id], as_of=as_of,
                as_of_label=f"Ngày nguồn: {as_of}", kind=kind, stale=False)


_PRICE_UNIT = r"(?:(?:VNĐ|VND|đồng|đ|₫)\s*/\s*(?:TSC\s*/\s*)?kg|USD\s*/\s*tấn|(?:US(?:D)?\s+)?cent\s*/\s*(?:lb|pound))"


def _price_bounds(text):
    """Only complete numeric cells, optionally with their explicit unit suffix."""
    match = re.fullmatch(rf"\s*(\d[\d.,]*)(?:\s*[–—~\-]\s*(\d[\d.,]*))?\s*(?:{_PRICE_UNIT})?\s*", text, re.I)
    if not match:
        return []
    low = parse_number(match[1])
    high = parse_number(match[2]) if match[2] else low
    if low is None or high is None or low <= 0 or high < low:
        return []
    return [low] if low == high else [low, high]


def _listed_change(text):
    text = text.strip().lstrip("▲▼➖ ")
    if text.casefold() == "không đổi":
        return 0
    match = re.fullmatch(r"([+−-]?\d[\d.,]*)(?:\s*(?:đồng|VNĐ|VND|đ|₫|USD))?(?:\s*\([^()]*%\))?", text, re.I)
    return parse_number(match[1]) if match else None


def _table_prefix(table):
    """Nearest preceding context without crossing another table's content."""
    node = table
    for _ in range(4):
        parent = node.parent
        if parent is None:
            break
        parts = []
        for sibling in reversed(parent.children[:parent.children.index(node)]):
            if isinstance(sibling, _Node):
                if sibling.tag == "table" or next(sibling.nodes("table"), None) is not None:
                    break
                parts.append(sibling.text())
            else:
                parts.append(sibling)
        prefix = " ".join(reversed(parts)).strip()
        if prefix:
            return prefix
        node = parent
    return ""


def _commodity(text):
    text = text.casefold()
    for key, words in [("coffee", ("cà phê", "robusta", "arabica")),
                       ("pepper", ("hồ tiêu", "tiêu đen", "tiêu trắng")),
                       ("durian", ("sầu", "ri6")), ("cashew", ("điều",)),
                       ("chilli", ("ớt",)), ("rubber", ("cao su", "mủ ")),
                       ("avocado", ("bơ",)), ("cocoa", ("ca cao", "cacao")),
                       ("rice", ("lúa", "gạo")), ("fertilizer", ("phân bón",)),
                       ("sugar", ("mía", "đường")), ("cotton", ("bông",))]:
        if any(word in text for word in words):
            return key
    return "other"


def _parse_banggianongsan(root):
    quotes = []
    for table_index, table in enumerate(root.nodes("table")):
        # This page's malformed nested paragraphs are intentionally not flattened:
        # the page date and dates on preceding/following tables are not this date.
        matches = list(re.finditer(r"Giá\s+[^.!?\n]{1,100}?\s+cập nhật ngày\s+\d{1,2}/\d{1,2}/\d{4}", _table_prefix(table), re.I))
        if not matches:
            continue
        heading = matches[-1].group()
        as_of = _date(heading)
        rows = list(table.nodes("tr"))
        if not as_of or not rows:
            continue
        headers = [c.text() for c in rows[0].nodes() if c.tag in ("th", "td")]
        lower_headers = [h.casefold() for h in headers]
        if not headers:
            continue
        if "giá mới" in lower_headers and "giá cũ" in lower_headers:
            price_columns = [lower_headers.index("giá mới")]
            change_column = next((i for i, h in enumerate(lower_headers) if h in ("thay đổi", "chênh lệch")), None)
        elif _commodity(heading) in ("avocado", "chilli"):
            price_columns = [i for i, h in enumerate(headers) if i and _unit(h)]
            change_column = None
        else:
            continue
        table_quotes = []
        region = "Việt Nam"
        for row_index, row in enumerate(rows[1:]):
            cells = list(row.nodes("td"))
            if len(cells) == 1 and _commodity(heading) == "chilli":
                region = cells[0].text()
                continue
            if len(cells) != len(headers) or not cells[0].text():
                continue
            label = cells[0].text()
            for column in price_columns:
                units = {_unit(text) for text in (label, cells[column].text(), headers[column])} - {None}
                unit = next(iter(units)) if len(units) == 1 else None
                values = _price_bounds(cells[column].text()) if unit else []
                if not values or len(table_quotes) + len(values) > 12:
                    continue
                commodity = _commodity(heading)
                kind = "domestic" if unit.startswith("VND/") else "futures" if re.search(r"kỳ hạn", label, re.I) else "international"
                market = region if kind == "domestic" else "Hợp đồng kỳ hạn" if kind == "futures" else "Quốc tế / xuất khẩu"
                price_label = label if len(price_columns) == 1 else f"{label} · {headers[column]}"
                change = _listed_change(cells[change_column].text()) if change_column is not None else None
                for bound_index, price in enumerate(values):
                    bound = (" · Thấp nhất" if bound_index == 0 else " · Cao nhất") if len(values) == 2 else ""
                    table_quotes.append(_quote("banggianongsan", commodity, price_label + bound, market,
                                               price, unit, change if len(values) == 1 or change == 0 else None,
                                               as_of, kind, f"{table_index}:{row_index}:{column}:{bound_index}"))
        quotes.extend(table_quotes)
        if len(quotes) >= 120:
            break
    return quotes[:120]


def _parse_chocaphe(root):
    if not any("Giá cà phê Đắk Lắk" in n.text() for n in root.nodes("h1")):
        return []
    candidates = []
    for table in root.nodes("table"):
        rows = list(table.nodes("tr"))
        if not rows:
            continue
        headers = [c.text() for c in rows[0].nodes("th")]
        if len(headers) < 3 or headers[0] != "Ngày" or "Giá trung bình" not in headers[1] or _unit(headers[1]) != "VND/kg":
            continue
        for row in rows[1:]:
            cells = list(row.nodes("td"))
            if len(cells) != len(headers):
                continue
            as_of, price = _date(cells[0].text()), parse_number(cells[1].text())
            if as_of and price and price > 0:
                candidates.append(_quote("chocaphe", "coffee", "Cà phê Robusta nhân xô · trung bình Đắk Lắk",
                                          "Đắk Lắk", price, "VND/kg", _change(cells[2]), as_of, "domestic", "daklak"))
    # The public table also contains history. Only its most recent dated row is
    # a current quote, and its date is not replaced with the page heading date.
    return [max(candidates, key=lambda q: q["as_of"])] if candidates else []


def _parse_chogia(root):
    title = next((n.text() for n in root.nodes("h1") if "giá cà phê hôm nay" in n.text().casefold()), "")
    as_of = _date(title)
    if not as_of:
        return []
    quotes = []
    for table_index, table in enumerate(root.nodes("table")):
        rows = list(table.nodes("tr"))
        if not rows:
            continue
        headers = [c.text() for c in rows[0].nodes("th")]
        if len(headers) != 3:
            continue
        unit = _unit(headers[1])
        if headers[0] == "Kỳ hạn" and unit in ("USD/tấn", "US cent/lb", "cent/lb"):
            market = "Robusta London" if unit == "USD/tấn" else "Arabica New York"
            for row_index, row in enumerate(rows[1:]):
                cells = list(row.nodes("td"))
                if len(cells) != 3 or not re.fullmatch(r"\d{2}/\d{2}", cells[0].text()):
                    continue
                price = parse_number(cells[1].text())
                if price and price > 0:
                    quotes.append(_quote("chogia", "coffee", f"{market} {cells[0].text()}", market,
                                         price, unit, _change(cells[2]), as_of, "futures", f"{table_index}:{row_index}"))
        elif headers[0] == "Địa phương" and unit == "VND/kg":
            # Do not borrow a unit from an unrelated chart, forecast or FX row.
            for row_index, row in enumerate(rows[1:]):
                cells = list(row.nodes("td"))
                if len(cells) != 3 or cells[0].text() not in ("Đắk Lắk", "Lâm Đồng", "Gia Lai", "Đắk Nông"):
                    continue
                price = parse_number(cells[1].text())
                if price and price > 0:
                    quotes.append(_quote("chogia", "coffee", "Cà phê nhân xô", cells[0].text(),
                                         price, unit, _change(cells[2]), as_of, "domestic", f"{table_index}:{row_index}"))
    return quotes[:15]


def parse_prices(source_id: str, html: str) -> list[dict]:
    root = _Document(html).root
    if source_id == "banggianongsan":
        return _parse_banggianongsan(root)
    if source_id == "chocaphe":
        return _parse_chocaphe(root)
    if source_id == "chogia":
        return _parse_chogia(root)
    title = next((node.text() for node in root.nodes("h1")
                  if ("Giá cà phê hôm nay" if source_id == "giacaphe" else "Giá tiêu hôm nay") in node.text()), "")
    as_of = _date(title)
    if not as_of:
        return []
    quotes = []
    if source_id == "giacaphe":
        average = next(root.nodes(cls="gia--trung-binh"), None)
        if average:
            price_node = next(average.nodes(cls="_trung-binh-gia"), None)
            if price_node:
                match = re.match(r"([\d.,]+)", price_node.text())
                price = parse_number(match[1]) if match else None
                unit = _unit(price_node.text())
                if price and unit:
                    change_node = next(average.nodes(cls="price_change"), None)
                    quotes.append(_quote(source_id, "coffee", "Cà phê nội địa trung bình", "Việt Nam", price, unit,
                                         _change(change_node) if change_node else None, as_of, "domestic", "average"))
        for section_id, label in [("robusta-london", "Robusta London"), ("arabica-newyork", "Arabica New York")]:
            section = next(root.nodes(ident=section_id), None)
            if section is None:
                continue
            updated = next(section.nodes(cls="update-time"), None)
            section_date = _date(updated.text()) if updated else None
            unit = _unit(section.text())
            if not section_date or not unit:
                continue
            for row in section.nodes("tr"):
                cells = list(row.nodes("td"))
                if len(cells) < 3 or not re.fullmatch(r"\d{2}/\d{2}", cells[0].text()):
                    continue
                price = parse_number(cells[1].text())
                if price and price > 0:
                    term = cells[0].text()
                    quotes.append(_quote(source_id, "coffee", f"{label} {term}", label, price, unit,
                                         _change(cells[2]), section_date, "futures", f"{section_id}:{term}"))
    elif source_id == "giatieu":
        # Only the dedicated quote table, not prices mentioned in older articles.
        for table in root.nodes("table"):
            if not all(word in table.text() for word in ("Khu vực", "Giá trung bình", "Thay đổi")):
                continue
            container = table.parent
            while container.parent and container.tag != "article" and "page-gia-inner" not in container.attrs.get("class", "").split():
                container = container.parent
            # The dedicated source places the explicit unit immediately below
            # the table, inside this container; never infer it from another page.
            unit = _unit(container.text())
            if unit != "VND/kg":
                continue
            for index, row in enumerate(table.nodes("tr")):
                cells = list(row.nodes("td"))
                if len(cells) != 3:
                    continue
                market, price = cells[0].text(), parse_number(cells[1].text())
                if market and price and price > 0:
                    quotes.append(_quote(source_id, "pepper", "Hồ tiêu", market, price, unit,
                                         _change(cells[2]), as_of, "domestic", str(index)))
    return quotes[:15]


def parse_news(source_id: str, html: str) -> list[dict]:
    root = _Document(html).root
    base = FETCH_URLS[source_id]
    host = urlsplit(base).hostname
    result, seen = [], set()
    for node in root.nodes():
        if node.tag not in ("h2", "h3"):
            continue
        for anchor in node.nodes("a"):
            title = anchor.text()
            url = urljoin(base, anchor.attrs.get("href", ""))
            try:
                _safe_url(url, {host})
            except ValueError:
                continue
            # Both publishers use numeric article IDs, unlike category/navigation links.
            if len(title) < 20 or not re.search(r"(?:-\d+\.html|-d\d+\.html)$", urlsplit(url).path) or url in seen:
                continue
            seen.add(url)
            result.append(dict(title=title[:300], url=url, source_id=source_id, published_at=None))
            if len(result) == 8:
                return result
    return result


class MarketService:
    def __init__(self, fetcher=None, *, clock=time.monotonic, utcnow=None, ttl_seconds=TTL_SECONDS):
        self.fetcher = fetcher or fetch_public
        self.clock = clock
        self.utcnow = utcnow or (lambda: datetime.now(timezone.utc))
        self.ttl_seconds = min(max(ttl_seconds, 0), TTL_SECONDS)
        self._lock = threading.Lock()
        self._cached = {}
        self._snapshot = None
        self._expires = 0

    def _fetch(self, source_id):
        html = self.fetcher(FETCH_URLS[source_id])
        if not isinstance(html, str) or len(html.encode("utf-8")) > MAX_BODY:
            raise ValueError("Invalid market body")
        if source_id in ("giacaphe", "giatieu", "banggianongsan", "chocaphe", "chogia"):
            rows = parse_prices(source_id, html)
            if not rows:
                raise ValueError("No verifiable public quote table")
            return rows, []
        news = parse_news(source_id, html)
        if not news:
            raise ValueError("No public headline listing")
        return [], news

    def overview(self):
        with self._lock:
            if self._snapshot is not None and self.clock() < self._expires:
                return deepcopy(self._snapshot)
            now = self.utcnow().astimezone(timezone.utc).isoformat().replace("+00:00", "Z")
            sources, quotes, news = [], [], []
            stale = partial = False
            with ThreadPoolExecutor(max_workers=len(FETCH_URLS), thread_name_prefix="vwing-market") as executor:
                pending = {source_id: executor.submit(self._fetch, source_id) for source_id in FETCH_URLS}
                for item in CATALOG:
                    source = dict(deepcopy(item), status="unavailable" if item["id"] in ("giacaosu", "vfa") else "link_only", fetched_at=None, stale=False)
                    if item["id"] in pending:
                        try:
                            rows, headlines = pending[item["id"]].result()
                            self._cached[item["id"]] = (deepcopy(rows), deepcopy(headlines), now)
                            source.update(status="public", fetched_at=now)
                        except Exception:
                            # Never expose transport details, credentials or arbitrary HTML.
                            rows, headlines, fetched_at = deepcopy(self._cached.get(item["id"], ([], [], None)))
                            source.update(status="unavailable", fetched_at=fetched_at, stale=bool(fetched_at))
                            source["note"] += " Hiện không đọc được dữ liệu; dữ liệu lưu trước đó (nếu có) đã cũ."
                            partial = True
                            stale = stale or bool(fetched_at)
                            for row in rows:
                                row["stale"] = True
                        quotes.extend(rows)
                        news.extend(headlines[:4])
                    sources.append(source)
            self._snapshot = dict(fetched_at=now, stale=stale, partial=partial, sources=sources, quotes=quotes, news=news[:8])
            self._expires = self.clock() + self.ttl_seconds
            return deepcopy(self._snapshot)


def make_router(auth, service: MarketService | None = None):
    service = service or MarketService()
    router = APIRouter(prefix="/v1/market", tags=["market"], dependencies=[Depends(auth)])

    @router.get("/overview")
    def overview():
        return service.overview()

    return router
