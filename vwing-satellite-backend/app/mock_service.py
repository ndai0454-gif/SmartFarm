"""Dữ liệu MÔ PHỎNG xác định (không phải ảnh vệ tinh thật) để thử ứng dụng khi chưa có tài khoản Earth Engine.

Mọi kết quả đều mang simulated=true và nguồn ghi rõ "MÔ PHỎNG". Không dùng để ra quyết định.
"""
import hashlib
import random
import struct
import zlib
from datetime import date, timedelta

from . import indices as ix
from .geo import area_ha, bounds as geo_bounds
from .schemas import Bounds, IndexResult, Legend, SeriesPoint, SeriesResult, WaterResult

SIM = "MÔ PHỎNG — không phải ảnh vệ tinh thật"
_IMAGES: dict[str, bytes] = {}


def _rng(*parts) -> random.Random:
    h = hashlib.sha256("|".join(map(str, parts)).encode()).hexdigest()
    return random.Random(int(h[:16], 16))


def _hex(c: str):
    return tuple(int(c[i:i + 2], 16) for i in (0, 2, 4))


def _ramp(t: float, palette) -> tuple:
    cols = [_hex(p) for p in palette]
    t = max(0.0, min(1.0, t)) * (len(cols) - 1)
    i = min(int(t), len(cols) - 2)
    f = t - i
    return tuple(int(cols[i][k] + (cols[i + 1][k] - cols[i][k]) * f) for k in range(3))


def _png(w: int, h: int, pixel) -> bytes:
    raw = b"".join(b"\x00" + b"".join(bytes(pixel(x, y)) for x in range(w)) for y in range(h))
    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


def get_image(image_id: str):
    return _IMAGES.get(image_id)


def _store(png: bytes, base_url: str = "") -> str:
    """Trả đường dẫn TƯƠNG ĐỐI: ứng dụng ghép với địa chỉ máy chủ mà nó đang dùng (tránh sai host khi truy cập qua LAN/emulator)."""
    image_id = hashlib.sha256(png).hexdigest()[:20]
    _IMAGES[image_id] = png
    return f"/v1/mock/image/{image_id}.png"


def _base_level(index: str, r: random.Random) -> float:
    return {"NDVI": 0.62, "EVI": 0.42, "NDMI": 0.18}[index] + r.uniform(-0.05, 0.05)


def index_result(ring, index, start, end, max_cloud, base_url) -> IndexResult:
    d = ix.INDICES[index]
    r = _rng("idx", ring, index, start, end)
    level = _base_level(index, r)
    spread = 0.12
    n = max(1, (end - start).days // 5 - r.randint(0, 3))
    cov = round(r.uniform(72, 98), 1)

    def pixel(x, y):
        v = level + spread * (((x * 7 + y * 13) % 17) / 17 - 0.5) + 0.04 * ((x // 8 + y // 8) % 3 - 1)
        return (*_ramp((v - d.vmin) / (d.vmax - d.vmin), d.palette), 255)

    url = _store(_png(96, 96, pixel), base_url)
    return IndexResult(
        index=index, dataset="MÔ PHỎNG", source=SIM, simulated=True, start=start, end_exclusive=end, scene_count=n,
        mean=round(level, 3), p5=round(level - spread, 3), p95=round(level + spread, 3), valid_coverage_pct=cov,
        nominal_pixel_size_m=10, method="Dữ liệu sinh ngẫu nhiên xác định theo ranh giới và ngày; không đọc ảnh vệ tinh",
        area_ha=round(area_ha(ring), 2), bounds=Bounds(**geo_bounds(ring)), image_url=url,
        legend=Legend(min=d.vmin, max=d.vmax, palette=list(d.palette), label=d.title),
        warnings=["Đây là dữ liệu MÔ PHỎNG để thử giao diện, không phải ảnh vệ tinh thật."], caution=d.caution,
    )


def series_result(ring, index, start, end, max_cloud) -> SeriesResult:
    d = ix.INDICES[index]
    r = _rng("ser", ring, index, start, end)
    level = _base_level(index, r)
    pts, skipped, day, k = [], 0, start, 0
    while day < end:
        day += timedelta(days=5)
        if day >= end:
            break
        k += 1
        cov = r.uniform(0, 100)
        if cov < 10:
            skipped += 1
            continue
        season = 0.08 * __import__("math").sin(k / 5.0)
        pts.append(SeriesPoint(date=day, mean=round(level + season + r.uniform(-0.03, 0.03), 3), valid_coverage_pct=round(cov, 1)))
    return SeriesResult(
        index=index, dataset="MÔ PHỎNG", source=SIM, simulated=True, start=start, end_exclusive=end, points=pts,
        skipped_scenes=skipped, nominal_pixel_size_m=10, method="Chuỗi sinh ngẫu nhiên xác định; không đọc ảnh vệ tinh",
        warnings=["Đây là dữ liệu MÔ PHỎNG để thử giao diện."] + ([f"{skipped} ngày bị bỏ vì thiếu điểm ảnh hợp lệ."] if skipped else []),
        caution=d.caution,
    )


def water_change(ring, b0, b1, e0, e1, base_url) -> WaterResult:
    r = _rng("wat", ring, b0, b1, e0, e1)
    area = area_ha(ring)
    perm = round(area * r.uniform(0.0, 0.03), 3); new = round(area * r.uniform(0.0, 0.2), 3); rec = round(area * r.uniform(0.0, 0.05), 3)

    def pixel(x, y):
        v = (x * 3 + y * 5) % 23
        if v < 3: return (30, 120, 184, 255)
        if v < 4: return (138, 154, 143, 255)
        return (0, 0, 0, 0)

    return WaterResult(
        dataset="MÔ PHỎNG", source=SIM, simulated=True, baseline_scenes=r.randint(2, 5), event_scenes=r.randint(2, 5),
        new_water_ha=new, permanent_water_ha=perm, receded_ha=rec, area_ha=round(area, 2), bounds=Bounds(**geo_bounds(ring)),
        image_url=_store(_png(96, 96, pixel), base_url), method="Dữ liệu sinh ngẫu nhiên xác định; không đọc ảnh radar",
        warnings=["Đây là dữ liệu MÔ PHỎNG để thử giao diện.", "Nước mặt mới xuất hiện không tự chứng minh thiệt hại cây trồng; cần kiểm tra thực địa."],
        caution="Kết quả là gợi ý để đi kiểm tra, không phải kết luận ngập.",
    )
