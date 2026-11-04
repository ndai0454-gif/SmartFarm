"""Kiểm tra ranh giới vườn do ứng dụng gửi lên. GeoJSON: kinh độ trước, vĩ độ sau."""
import math

EARTH_R = 6_371_008.8


class GeoError(ValueError):
    pass


def _close(ring):
    return ring if ring[0] == ring[-1] else ring + [ring[0]]


def _cross(a, b, c, d) -> bool:
    def ccw(p, q, r):
        return (r[1] - p[1]) * (q[0] - p[0]) > (q[1] - p[1]) * (r[0] - p[0])
    return ccw(a, c, d) != ccw(b, c, d) and ccw(a, b, c) != ccw(a, b, d)


def validate_ring(ring: list) -> list:
    """Trả về vòng khép kín hợp lệ hoặc ném GeoError với thông báo rõ ràng."""
    try:
        pts = [[float(p[0]), float(p[1])] for p in ring]
    except Exception:
        raise GeoError("Ranh giới phải là danh sách [kinh độ, vĩ độ].")
    pts = _close(pts)
    if len(pts) < 4:
        raise GeoError("Ranh giới cần ít nhất 3 đỉnh.")
    for lon, lat in pts:
        if not (-180 <= lon <= 180 and -90 <= lat <= 90):
            raise GeoError("Tọa độ ngoài phạm vi. Hãy kiểm tra thứ tự: kinh độ trước, vĩ độ sau.")
    # nhiều vườn Việt Nam có lat ~ 8..24, lng ~ 102..110: cảnh báo đảo thứ tự rõ ràng
    if all(abs(p[0]) < 30 and 90 < abs(p[1]) <= 180 for p in pts):
        raise GeoError("Có vẻ đã đảo thứ tự: GeoJSON là [kinh độ, vĩ độ].")
    n = len(pts) - 1
    for i in range(n):
        for j in range(i + 2, n):
            if i == 0 and j == n - 1:
                continue
            if _cross(pts[i], pts[i + 1], pts[j], pts[j + 1]):
                raise GeoError("Ranh giới tự cắt nhau.")
    return pts


def area_ha(ring: list) -> float:
    """Diện tích (ha) bằng phép chiếu phẳng cục bộ quanh tâm; đủ chính xác cho vườn."""
    lat0 = sum(p[1] for p in ring) / len(ring)
    k = math.cos(math.radians(lat0))
    xy = [(math.radians(p[0]) * EARTH_R * k, math.radians(p[1]) * EARTH_R) for p in ring]
    a = 0.0
    for (x1, y1), (x2, y2) in zip(xy, xy[1:]):
        a += x1 * y2 - x2 * y1
    return abs(a) / 2 / 10_000


def bounds(ring: list) -> dict:
    lons = [p[0] for p in ring]; lats = [p[1] for p in ring]
    return {"south": min(lats), "west": min(lons), "north": max(lats), "east": max(lons)}
