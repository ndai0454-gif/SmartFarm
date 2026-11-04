"""Công cụ GIS dùng engine RustGIS (https://github.com/opengeos/RustGIS, Apache-2.0, gói `rustgis` trên PyPI).

Chạy trên máy chủ để app không phải mang theo engine nặng: nhập KML/KMZ/GPX/Shapefile(zip)/GeoPackage/CSV/GeoJSON,
xuất ra KML/GPX/CSV/WKT/GeoJSON, phân tích ranh vườn (diện tích, chu vi, kiểm tra hình, vùng đệm) và chạy một số công cụ
xử lý không gian trong danh sách trắng (buffer, clip, erase, dissolve, simplify, convexHull, featureToPoint, countPoints).
Mỗi yêu cầu dùng một phiên RustGIS riêng, không giữ trạng thái giữa các lần gọi.
"""
import base64
import binascii
import json
import math
import re
from typing import Any, Callable, Optional

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

from . import geo

try:                                                     # engine là phụ thuộc tuỳ chọn: thiếu thì các đường dẫn này trả 503
    import rustgis
except Exception:                                        # pragma: no cover
    rustgis = None

MAX_UPLOAD_BYTES = 15 * 1024 * 1024
MAX_FEATURES = 20_000
MAX_VERTICES = 500_000
IMPORT_EXT = {"geojson", "json", "kml", "kmz", "gpx", "zip", "gpkg", "csv", "tsv", "fgb", "parquet", "osm"}
EXPORT = {                                               # định dạng → (mime, đuôi tệp)
    "geojson": ("application/geo+json", "geojson"), "kml": ("application/vnd.google-earth.kml+xml", "kml"),
    "gpx": ("application/gpx+xml", "gpx"), "csv": ("text/csv", "csv"), "wkt": ("text/plain", "wkt"),
    "shp": ("application/zip", "shp.zip"), "gpkg": ("application/geopackage+sqlite3", "gpkg"), "postgis": ("application/sql", "sql"),
}
TOOLS = {                                                # công cụ → tên các tham số là LỚP (còn lại là tham số thường)
    "buffer": ("input",), "clip": ("input", "clip"), "erase": ("input", "erase"), "dissolve": ("input",),
    "simplify": ("input",), "convexHull": ("input",), "featureToPoint": ("input",), "countPoints": ("polygons", "points"),
    "calculateGeometry": ("input",), "selectByLocation": ("input", "select"),
    "selectByAttributes": ("input",), "summaryStatistics": ("input",), "xyTableToPoint": ("input",),
}
NUMERIC = {"buffer": ("distance", 5000.0), "simplify": ("tolerance", 100.0)}       # tham số số bắt buộc và mức trần
RASTER_FUNCTIONS = {"slope", "aspect", "hillshade", "ndvi", "bandMath", "threshold", "remap"}
TOPOLOGY_RULES = {"mustNotOverlap", "mustNotHaveGaps", "mustNotSelfIntersect", "mustNotHaveDangles", "mustBeCoveredBy"}
NAME_RE = re.compile(r"^[A-Za-z0-9_\- ]{1,40}$")
WHERE_RE = re.compile(r"^[A-Za-z0-9_\s=!<>()'\".,+\-*/%]+$")


def available() -> bool:
    return rustgis is not None


def _need():
    if rustgis is None:
        raise HTTPException(503, "Engine RustGIS chưa được cài trên máy chủ (pip install rustgis).")


class GisError(ValueError):
    pass


def _session():
    _need()
    return rustgis.Session()


def _text(result: Any) -> str:
    if isinstance(result, dict) and "text" in result:
        return result["text"]
    raise GisError("Engine không trả về dữ liệu.")


def _vertex_count(geom: Optional[dict]) -> int:
    if not geom:
        return 0
    c = geom.get("coordinates")
    if geom.get("type") == "GeometryCollection":
        return sum(_vertex_count(g) for g in geom.get("geometries", []))

    def walk(x):
        if isinstance(x, (list, tuple)) and x and isinstance(x[0], (int, float)):
            return 1
        return sum(walk(i) for i in x) if isinstance(x, (list, tuple)) else 0
    return walk(c)


def check_featurecollection(gj: Any) -> dict:
    """Kiểm tra đầu vào GeoJSON do app gửi: đúng kiểu, không quá lớn, toạ độ hợp lệ."""
    if isinstance(gj, dict) and gj.get("type") == "Feature":
        gj = {"type": "FeatureCollection", "features": [gj]}
    if not isinstance(gj, dict) or gj.get("type") != "FeatureCollection" or not isinstance(gj.get("features"), list):
        raise GisError("Cần một GeoJSON FeatureCollection.")
    feats = gj["features"]
    if len(feats) > MAX_FEATURES:
        raise GisError(f"Quá nhiều đối tượng (tối đa {MAX_FEATURES}).")
    total = 0
    for f in feats:
        if not isinstance(f, dict) or f.get("type") != "Feature":
            raise GisError("Mỗi phần tử phải là một Feature.")
        total += _vertex_count(f.get("geometry"))
        if total > MAX_VERTICES:
            raise GisError("Quá nhiều đỉnh để xử lý.")
    return gj


def _geojson_of(s, layer_name: str) -> dict:
    """Đọc toàn bộ đối tượng của lớp sang GeoJSON bằng API gốc của RustGIS."""
    try:
        s.run("layer.loadAll", layer=layer_name)
    except Exception:
        pass
    try:
        return s.layer(layer_name).to_geojson()
    except Exception:
        return json.loads(_text(s.run("data.convert", layer=layer_name, format="geojson")))


# ---------- nhập / xuất ----------

def import_file(filename: str, data: bytes) -> dict:
    ext = filename.rsplit(".", 1)[-1].lower() if "." in filename else ""
    if ext not in IMPORT_EXT:
        raise GisError("Định dạng chưa hỗ trợ. Dùng: " + ", ".join(sorted(IMPORT_EXT)) + ".")
    if not re.fullmatch(r"[A-Za-z0-9._\- ]{1,80}", filename):
        raise GisError("Tên tệp chỉ gồm chữ, số, dấu chấm, gạch ngang, khoảng trắng.")
    s = _session()
    if ext == "osm":
        try:
            from . import open_gis_data
            gj = check_featurecollection(open_gis_data.osm_to_geojson(data))
            return {"layers": [{"name": filename.rsplit(".", 1)[0], "type": "OSM", "features": len(gj["features"]), "geojson": gj}], "warnings": []}
        except Exception as e:
            raise GisError(f"Kh?ng ??c ???c t?p OSM {filename}: {str(e)[:160]}")
    before = {l["id"] for l in s.layers()}
    try:
        s.run("layer.addDataBytes", fileName=filename, data=base64.b64encode(data).decode())
    except Exception as e:                               # engine báo lỗi đọc tệp bằng thông điệp tiếng Anh; không đưa chi tiết nội bộ
        raise GisError(f"Không đọc được tệp {filename}: {str(e)[:160]}")
    out, warnings = [], []
    for msg, is_err in s.notices():
        if is_err:
            warnings.append(msg)
    for l in s.layers():
        if l["id"] in before:
            continue
        try:
            gj = check_featurecollection(_geojson_of(s, l["name"]))
        except GisError as e:
            warnings.append(f"Lớp {l['name']}: {e}")
            continue
        out.append({"name": l["name"], "type": l.get("type"), "features": len(gj["features"]), "geojson": gj})
    if not out and not warnings:
        warnings.append("Tệp không có lớp đối tượng nào (có thể chỉ là bảng hoặc ảnh raster).")
    return {"layers": out, "warnings": warnings}


def export_geojson(gj: dict, fmt: str, name: str = "VWing") -> dict:
    if fmt not in EXPORT:
        raise GisError("Định dạng xuất không hỗ trợ.")
    gj = check_featurecollection(gj)
    if fmt in ("shp", "gpkg", "postgis"):
        try:
            from . import open_gis_data
            conv = open_gis_data.convert_geojson_to_format(gj, fmt, "EPSG:4326", name)
            return {"text": conv.get("text") or conv.get("data_base64") or "", "data_base64": conv.get("data_base64"), "mime": conv["mime"], "filename": conv["filename"]}
        except Exception as e:
            raise GisError(f"Kh?ng xu?t ???c {fmt}: {str(e)[:160]}")
    s = _session()
    s.add_geojson(gj, name="Layer")
    mime, ext = EXPORT[fmt]
    kwargs = {"layer": "Layer", "format": fmt}
    text = _text(s.run("data.convert", **kwargs))
    safe = re.sub(r"[^A-Za-z0-9_\-]", "_", name)[:40] or "VWing"
    return {"text": text, "mime": mime, "filename": f"{safe}.{ext}"}


# ---------- ranh vườn ----------

def perimeter_m(ring: list) -> float:
    t = 0.0
    for a, b in zip(ring, ring[1:]):
        p1, p2 = math.radians(a[1]), math.radians(b[1])
        dl = math.radians(b[0] - a[0]); dp = p2 - p1
        h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
        t += 2 * geo.EARTH_R * math.asin(min(1.0, math.sqrt(h)))
    return t


def _ring_gj(ring: list, name: str = "Ranh") -> dict:
    return {"type": "FeatureCollection", "features": [{"type": "Feature", "properties": {"name": name}, "geometry": {"type": "Polygon", "coordinates": [ring]}}]}


def boundary_analyze(ring_in: list, buffers_m: list[float], simplify_m: Optional[float]) -> dict:
    issues, ring = [], None
    try:
        ring = geo.validate_ring(ring_in)
    except geo.GeoError as e:
        issues.append(str(e))
        try:
            ring = geo._close([[float(p[0]), float(p[1])] for p in ring_in])
        except Exception:
            raise GisError("Ranh giới phải là danh sách [kinh độ, vĩ độ].")
    area = geo.area_ha(ring)
    perim = perimeter_m(ring)
    res: dict = {"valid": not issues, "issues": issues, "area_ha": round(area, 4), "perimeter_m": round(perim, 1),
                 "vertices": len(ring) - 1, "bounds": geo.bounds(ring), "buffers": {}}
    if area < 0.01:
        res["issues"].append("Vùng quá nhỏ (dưới 0,01 ha)."); res["valid"] = False
    if res["issues"]:                                    # hình lỗi thì không chạy tiếp các phép xử lý hình học
        return res

    # Dùng chung một phiên RustGIS cho toàn bộ phân tích ranh giới
    s = _session()
    s.add_geojson(_ring_gj(ring), name="Ranh")
    try:
        s.run("gp.run", tool="calculateGeometry", params={"input": "Ranh"})
        r_gj = _geojson_of(s, "Ranh")
        props = (r_gj.get("features") or [{}])[0].get("properties") or {}
        if "centroid_lon" in props and "centroid_lat" in props:
            res["centroid"] = [round(float(props["centroid_lon"]), 6), round(float(props["centroid_lat"]), 6)]
        else:
            pts = ring[:-1]
            res["centroid"] = [round(sum(p[0] for p in pts) / len(pts), 6), round(sum(p[1] for p in pts) / len(pts), 6)]
    except Exception:
        pts = ring[:-1]
        res["centroid"] = [round(sum(p[0] for p in pts) / len(pts), 6), round(sum(p[1] for p in pts) / len(pts), 6)]

    try:
        rh = s.run("gp.run", tool="convexHull", params={"input": "Ranh", "output": "Hull"})
        hname = next((l["name"] for l in s.layers() if l["id"] == rh.get("layer")), "Hull")
        h_gj = _geojson_of(s, hname)
        h_geom = h_gj["features"][0]["geometry"] if h_gj.get("features") else None
        if h_geom and h_geom.get("type") == "Polygon":
            hull_ha = geo.area_ha(h_geom["coordinates"][0])
            res["convex_hull_area_ha"] = round(hull_ha, 4)
            if hull_ha > 0:
                res["compactness"] = round(min(1.0, area / hull_ha), 4)
    except Exception:
        pass

    for idx, m in enumerate(buffers_m):
        out_name = f"Buf_{idx}"
        r = s.run("gp.run", tool="buffer", params={"input": "Ranh", "distance": m, "units": "Meters", "output": out_name})
        lname = next((l["name"] for l in s.layers() if l["id"] == r.get("layer")), out_name)
        gj = _geojson_of(s, lname)
        geom = gj["features"][0]["geometry"] if gj["features"] else None
        if geom and geom["type"] == "Polygon":
            res["buffers"][str(m)] = {"ring": geom["coordinates"][0], "area_ha": round(geo.area_ha(geom["coordinates"][0]), 4)}

    if simplify_m:
        # RustGIS simplify nhận tolerance theo km; đổi từ mét sang km (simplify_m / 1000.0)
        tol_km = simplify_m / 1000.0
        r = s.run("gp.run", tool="simplify", params={"input": "Ranh", "tolerance": tol_km, "output": "Simp"})
        lname = next((l["name"] for l in s.layers() if l["id"] == r.get("layer")), "Simp")
        gj = _geojson_of(s, lname)
        geom = gj["features"][0]["geometry"] if gj["features"] else None
        if geom and geom["type"] == "Polygon":
            ring2 = geom["coordinates"][0]
            res["simplified"] = {"ring": ring2, "vertices": len(ring2) - 1, "area_ha": round(geo.area_ha(ring2), 4)}
    return res


# ---------- công cụ xử lý không gian ----------

def run_tool(tool: str, inputs: dict, params: dict) -> dict:
    if tool not in TOOLS:
        raise GisError("Công cụ không có trong danh sách cho phép: " + ", ".join(sorted(TOOLS)) + ".")
    layer_params = TOOLS[tool]
    s = _session()
    names = {}
    for k, gj in inputs.items():
        if not NAME_RE.match(k):
            raise GisError("Tên lớp chỉ gồm chữ, số, gạch, khoảng trắng (tối đa 40 ký tự).")
        s.add_geojson(check_featurecollection(gj), name=k)
        names[k] = True
    p = {}
    for k, v in params.items():
        if k == "output":                                # tên lớp kết quả do máy chủ đặt
            continue
        if k == "where":
            if tool not in ("selectByAttributes", "exportFeatures"):
                continue
            if not isinstance(v, str) or not (1 <= len(v) <= 200) or not WHERE_RE.match(v):
                raise GisError("Biểu thức lọc where không hợp lệ.")
            p[k] = v
            continue
        if k in layer_params:
            if v not in names:
                raise GisError(f"Tham số {k} phải là tên một lớp trong inputs.")
        elif not isinstance(v, (int, float, str, bool)) or (isinstance(v, str) and len(v) > 40):
            raise GisError(f"Tham số {k} không hợp lệ.")
        p[k] = v
    for k in layer_params:
        if k not in p:
            raise GisError(f"Thiếu tham số {k}.")
    if tool in NUMERIC:
        key, cap = NUMERIC[tool]
        v = p.get(key)
        if isinstance(v, bool) or not isinstance(v, (int, float)) or not (0 < v <= cap):
            raise GisError(f"Tham số {key} bắt buộc, trong khoảng (0, {cap:g}].")
    if tool == "buffer":
        p["units"] = "Meters"
    elif tool == "simplify":
        # Hỗ trợ truyền mét (ví dụ 2..100 m) hoặc km (< 0.5 km), đổi sang km cho RustGIS
        tol = float(p["tolerance"])
        p["tolerance"] = tol / 1000.0 if tol >= 0.5 else tol
    elif tool == "selectByLocation" and "distance_m" in p:
        dm = p.pop("distance_m")
        if isinstance(dm, bool) or not isinstance(dm, (int, float)) or not (0 <= dm <= 50_000):
            raise GisError("distance_m phải trong khoảng [0, 50000].")
        p["distance"] = float(dm) / 1000.0

    before = {l["id"] for l in s.layers()}
    try:
        if tool == "calculateGeometry":
            s.run("gp.run", tool="calculateGeometry", params={"input": p["input"]})
            gj = check_featurecollection(_geojson_of(s, p["input"]))
            return {"tool": tool, "features": len(gj["features"]), "geojson": gj}
        if tool == "selectByAttributes":
            if "where" not in p:
                raise GisError("Thiếu tham số where.")
            r = s.run("gp.run", tool="exportFeatures", params={"input": p["input"], "where": p["where"], "output": "Result"})
        elif tool == "selectByLocation":
            s.run("gp.run", tool="selectByLocation", params=p)
            r = s.run("select.layerFromSelection", layer=p["input"])
        elif tool == "summaryStatistics":
            if "field" not in p:
                raise GisError("Thiếu tham số field.")
            r = s.run("gp.run", tool="summaryStatistics", params=p)
            prof = s.run("data.profile", layer=p["input"])
            field_stat = next((f for f in prof.get("fields", []) if f.get("field") == p["field"]), None)
            gj = check_featurecollection(_geojson_of(s, p["input"]))
            return {"tool": tool, "features": len(gj["features"]), "geojson": gj, "statistics": field_stat, "table": r.get("table") if isinstance(r, dict) else None}
        else:
            r = s.run("gp.run", tool=tool, params={**p, "output": "Result"})
    except GisError:
        raise
    except Exception as e:
        raise GisError(f"Công cụ {tool} lỗi: {str(e)[:160]}")

    new = [l for l in s.layers() if l["id"] not in before]
    target = next((l for l in new if isinstance(r, dict) and l["id"] == r.get("layer")), new[0] if new else None)
    if target is None:
        raise GisError("Công cụ không tạo ra lớp kết quả.")
    gj = check_featurecollection(_geojson_of(s, target["name"]))
    return {"tool": tool, "features": len(gj["features"]), "geojson": gj}


# ---------- kiểm tra topology & hồ sơ dữ liệu ----------

def topology_validate(ring_in: Optional[list], zones_gj: Optional[dict], trees_gj: Optional[dict]) -> dict:
    """Kiểm tra toàn vẹn không gian của vườn bằng engine topology của RustGIS."""
    s = _session()
    issues = []
    ring = None
    if ring_in is not None:
        try:
            ring = geo.validate_ring(ring_in)
            s.add_geojson(_ring_gj(ring, "Ranh"), name="Ranh")
        except geo.GeoError as e:
            issues.append({"rule": "ValidBoundary", "layer": "Ranh", "message": str(e)})

    has_zones = False
    if zones_gj is not None:
        z_fc = check_featurecollection(zones_gj)
        if z_fc["features"]:
            s.add_geojson(z_fc, name="Khu")
            has_zones = True
            s.run("topology.rules", add={"rule": "mustNotOverlap", "layer": "Khu"})
            s.run("topology.rules", add={"rule": "mustNotSelfIntersect", "layer": "Khu"})

    has_trees = False
    if trees_gj is not None:
        t_fc = check_featurecollection(trees_gj)
        if t_fc["features"]:
            s.add_geojson(t_fc, name="Cay")
            has_trees = True
            if ring is not None:
                s.run("topology.rules", add={"rule": "mustBeCoveredBy", "layer": "Cay", "by": "Ranh"})

    topo = s.run("topology.validate") if (has_zones or (has_trees and ring is not None)) else {"errors": 0, "first": []}
    for err in topo.get("first", []):
        issues.append(err)

    outside_zone_ha = 0.0
    if has_zones and ring is not None:
        try:
            before = {l["id"] for l in s.layers()}
            r = s.run("gp.run", tool="erase", params={"input": "Khu", "erase": "Ranh", "output": "KhuNgoaiRanh"})
            new = [l for l in s.layers() if l["id"] not in before]
            target = next((l for l in new if isinstance(r, dict) and l["id"] == r.get("layer")), new[0] if new else None)
            if target:
                out_gj = _geojson_of(s, target["name"])
                for f in out_gj.get("features", []):
                    g = f.get("geometry") or {}
                    if g.get("type") == "Polygon" and g.get("coordinates"):
                        outside_zone_ha += geo.area_ha(g["coordinates"][0])
                if outside_zone_ha > 0.0001:
                    issues.append({"rule": "ZoneInsideBoundary", "layer": "Khu", "outside_area_ha": round(outside_zone_ha, 4),
                                   "message": f"Có {outside_zone_ha:.4f} ha khu vực nằm tràn ra ngoài ranh vườn."})
        except Exception:
            pass

    zone_tree_counts = []
    if has_zones and has_trees:
        try:
            before = {l["id"] for l in s.layers()}
            r = s.run("gp.run", tool="countPoints", params={"polygons": "Khu", "points": "Cay", "output": "DemCay"})
            new = [l for l in s.layers() if l["id"] not in before]
            target = next((l for l in new if isinstance(r, dict) and l["id"] == r.get("layer")), new[0] if new else None)
            if target:
                c_gj = _geojson_of(s, target["name"])
                for f in c_gj.get("features", []):
                    zone_tree_counts.append(f.get("properties") or {})
        except Exception:
            pass

    return {
        "valid": len(issues) == 0,
        "error_count": len(issues),
        "issues": issues,
        "outside_zone_ha": round(outside_zone_ha, 4),
        "zone_tree_counts": zone_tree_counts,
    }


def profile_geojson(gj: dict) -> dict:
    """Thống kê cấu trúc bảng thuộc tính và hình học bằng data.profile + calculateGeometry của RustGIS."""
    fc = check_featurecollection(gj)
    if not fc["features"]:
        return {"features": 0, "fields": [], "geojson": fc}
    s = _session()
    s.add_geojson(fc, name="Layer")
    try:
        s.run("gp.run", tool="calculateGeometry", params={"input": "Layer"})
    except Exception:
        pass
    prof = s.run("data.profile", layer="Layer")
    enriched = _geojson_of(s, "Layer")
    return {"features": prof.get("features", len(fc["features"])), "fields": prof.get("fields", []), "geojson": enriched}


# ---------- raster: thống kê, địa hình, nội suy IDW ----------

RASTER_EXT = {"tif", "tiff", "asc"}
INDEXES = {"ndvi", "ndwi", "savi", "nbr", "ndbi", "evi"}


def raster_zonal(filename: str, data: bytes, ring_in: list, index: Optional[str], bands: dict,
                 function: Optional[str] = None, function_params: Optional[dict] = None) -> dict:
    """Giá trị trung bình và thống kê của raster (hoặc chỉ số / hàm địa hình) bên trong ranh vườn."""
    ext = filename.rsplit(".", 1)[-1].lower() if "." in filename else ""
    if ext not in RASTER_EXT or not re.fullmatch(r"[A-Za-z0-9._\- ]{1,80}", filename):
        raise GisError("Chỉ nhận raster .tif, .tiff hoặc .asc với tên tệp đơn giản.")
    try:
        ring = geo.validate_ring(ring_in)
    except geo.GeoError as e:
        raise GisError(str(e))
    if index is not None and index not in INDEXES:
        raise GisError("Chỉ số hỗ trợ: " + ", ".join(sorted(INDEXES)) + ".")
    if function is not None and function not in RASTER_FUNCTIONS:
        raise GisError("Hàm raster hỗ trợ: " + ", ".join(sorted(RASTER_FUNCTIONS)) + ".")
    s = _session()
    try:
        s.run("layer.addDataBytes", fileName=filename, data=base64.b64encode(data).decode())
    except Exception as e:
        raise GisError(f"Không đọc được raster: {str(e)[:160]}")
    rast = next((l for l in s.layers() if l.get("type") == "Raster"), None)
    if rast is None:
        raise GisError("Tệp không chứa ảnh raster.")
    info = s.run("raster.info", layer=rast["name"])
    ext_box = info.get("extent") or {}
    b = geo.bounds(ring)
    if not (b["west"] < ext_box.get("east", -999) and b["east"] > ext_box.get("west", 999) and b["south"] < ext_box.get("north", -999) and b["north"] > ext_box.get("south", 999)):
        raise GisError("Ranh vườn nằm ngoài phạm vi của ảnh raster.")
    target, label = rast["name"], "Band 1"
    if index:
        kw = {k: int(v) for k, v in bands.items() if k in ("red", "green", "blue", "nir", "swir") and isinstance(v, int) and 1 <= v <= 32}
        before = {l["id"] for l in s.layers()}
        try:
            s.run("raster.index", layer=rast["name"], index=index, name="Index", **kw)
        except Exception as e:
            raise GisError(f"Không tính được {index}: {str(e)[:160]}")
        new = [l for l in s.layers() if l["id"] not in before and l.get("type") == "Raster"]
        if not new:
            raise GisError("Không tạo được lớp chỉ số.")
        target, label = new[0]["name"], index.upper()
    elif function:
        fp = dict(function_params or {})
        allowed_fp = {k: v for k, v in fp.items() if k in ("band", "expression", "ranges", "value", "keep", "nir", "red", "azimuth", "altitude", "zFactor")}
        before = {l["id"] for l in s.layers()}
        try:
            s.run("raster.function", layer=rast["name"], function=function, name="FuncOut", **allowed_fp)
        except Exception as e:
            raise GisError(f"Không chạy được hàm raster {function}: {str(e)[:160]}")
        new = [l for l in s.layers() if l["id"] not in before and l.get("type") == "Raster"]
        if not new:
            raise GisError("Không tạo được lớp kết quả hàm raster.")
        target, label = new[0]["name"], function.upper()

    s.add_geojson(_ring_gj(ring, "Zone"), name="Zone")
    z = s.run("raster.spectral", layer=target, zone="Zone")
    pixels = int(z.get("pixels", 0))
    if pixels == 0:
        raise GisError("Không có điểm ảnh hợp lệ nào nằm trong ranh vườn (ranh quá nhỏ so với độ phân giải hoặc toàn NoData).")
    vals = z.get("values") or []
    mean = vals[0] if vals else None

    # Lấy thêm thống kê min/max/stdDev từ lớp cắt theo khung ranh giới
    band_stats = {}
    try:
        s.run("raster.process", layer=target, operation="clip", extent=[b["west"], b["south"], b["east"], b["north"]], name="Clipped")
        c_info = s.run("raster.info", layer="Clipped")
        c_bands = c_info.get("bands") or []
        if c_bands:
            b0 = c_bands[0]
            band_stats = {"min": b0.get("min"), "max": b0.get("max"), "std_dev": b0.get("stdDev")}
    except Exception:
        t_info = s.run("raster.info", layer=target)
        t_bands = t_info.get("bands") or []
        if t_bands:
            b0 = t_bands[0]
            band_stats = {"min": b0.get("min"), "max": b0.get("max"), "std_dev": b0.get("stdDev")}

    res = {"label": label, "function": function, "mean": mean, "pixels": pixels, "means": dict(zip(z.get("bands", []), vals)),
           **band_stats,
           "area_ha": round(geo.area_ha(ring), 4), "raster": {"crs": info.get("crs"), "width": info.get("width"), "height": info.get("height"),
                                                          "bands": len(info.get("bands", [])), "extent": ext_box, "no_data": info.get("noData")}}
    cs = info.get("cellSize")
    if info.get("crs") == "EPSG:4326" and cs:
        lat = (b["south"] + b["north"]) / 2
        cell_ha = (cs[0] * 111_320 * math.cos(math.radians(lat))) * (cs[1] * 110_574) / 10_000
        res["coverage_pct"] = round(min(100.0, pixels * cell_ha / res["area_ha"] * 100), 1)
    return res


def interpolate_idw(points_gj: dict, field: str, ring_in: list, power: float = 2.0, neighbors: int = 12, cell_size_deg: Optional[float] = None) -> dict:
    """Nội suy không gian IDW từ các điểm đo (độ ẩm đất, pH, năng suất cây...) trong ranh vườn bằng RustGIS."""
    ring = geo.validate_ring(ring_in)
    fc = check_featurecollection(points_gj)
    if len(fc["features"]) < 3:
        raise GisError("Cần ít nhất 3 điểm đo để nội suy IDW.")
    if not NAME_RE.match(field):
        raise GisError("Tên trường số liệu không hợp lệ.")
    b = geo.bounds(ring)
    span = max(b["east"] - b["west"], b["north"] - b["south"])
    cs = cell_size_deg or max(0.00005, min(0.002, span / 25.0))
    s = _session()
    s.add_geojson(fc, name="Points")
    s.add_geojson(_ring_gj(ring, "Zone"), name="Zone")
    try:
        s.run("analysis.interpolate", layer="Points", field=field, cellSize=cs, power=power, neighbors=neighbors, name="IDW")
    except Exception as e:
        raise GisError(f"Không nội suy được trường {field}: {str(e)[:160]}")
    info = s.run("raster.info", layer="IDW")
    z = s.run("raster.spectral", layer="IDW", zone="Zone")
    vals = z.get("values") or []
    b0 = (info.get("bands") or [{}])[0]
    return {
        "field": field,
        "mean": vals[0] if vals else b0.get("mean"),
        "min": b0.get("min"),
        "max": b0.get("max"),
        "std_dev": b0.get("stdDev"),
        "pixels": int(z.get("pixels", 0)) or int(b0.get("validPixels", 0)),
        "cell_size_deg": cs,
        "area_ha": round(geo.area_ha(ring), 4),
    }


def render_map_png(layers: dict[str, dict], styles: Optional[dict[str, dict]] = None, width: int = 800, height: int = 600, dpi: float = 96.0) -> bytes:
    """Kết xuất bản đồ các lớp GeoJSON ra ảnh PNG bằng bộ vẽ headless của RustGIS."""
    if not layers:
        raise GisError("Cần ít nhất một lớp để vẽ bản đồ.")
    s = _session()
    styles = styles or {}
    for name, gj in layers.items():
        if not NAME_RE.match(name):
            raise GisError("Tên lớp không hợp lệ.")
        s.add_geojson(check_featurecollection(gj), name=name)
        st = styles.get(name) or {}
        if "color" in st and isinstance(st["color"], str):
            s.run("layer.color", layer=name, color=st["color"])
        if "outline" in st and isinstance(st["outline"], str):
            s.run("layer.outline", layer=name, color=st["outline"], width=float(st.get("width", 2.0)))
    return s.to_png(width=width, height=height, dpi=dpi)


# ---------- đường dẫn HTTP ----------

class ImportRequest(BaseModel):
    filename: str = Field(max_length=100)
    data_base64: str = Field(max_length=int(MAX_UPLOAD_BYTES * 1.4))


class ExportRequest(BaseModel):
    geojson: dict
    format: str = "kml"
    name: str = "VWing"


class BoundaryRequest(BaseModel):
    ring: list
    buffers_m: list[float] = Field(default_factory=list, max_length=5)
    simplify_m: Optional[float] = Field(default=None, gt=0, le=100)


class ZonalRequest(BaseModel):
    filename: str = Field(max_length=100)
    data_base64: str = Field(max_length=int(MAX_UPLOAD_BYTES * 1.4))
    ring: list
    index: Optional[str] = None
    bands: dict = Field(default_factory=dict)
    function: Optional[str] = None
    function_params: dict = Field(default_factory=dict)


class ToolRequest(BaseModel):
    tool: str
    inputs: dict
    params: dict = Field(default_factory=dict)


class TopologyRequest(BaseModel):
    ring: Optional[list] = None
    boundary: Optional[dict] = None
    zones: Optional[dict] = None
    trees: Optional[dict] = None


class ProfileRequest(BaseModel):
    geojson: dict


class InterpolateRequest(BaseModel):
    points: dict
    field: str = Field(max_length=40)
    ring: list
    power: float = Field(default=2.0, gt=0, le=10)
    neighbors: int = Field(default=12, ge=3, le=64)
    cell_size_deg: Optional[float] = Field(default=None, gt=0, le=0.05)


class RenderRequest(BaseModel):
    layers: dict[str, dict]
    styles: dict[str, dict] = Field(default_factory=dict)
    width: int = Field(default=800, ge=128, le=2048)
    height: int = Field(default=600, ge=128, le=2048)
    dpi: float = Field(default=96.0, ge=72.0, le=300.0)


def make_router(auth: Callable) -> APIRouter:
    from fastapi.responses import Response
    r = APIRouter(prefix="/v1/gis", dependencies=[Depends(auth)])

    def guard(fn, *a):
        _need()
        try:
            return fn(*a)
        except (GisError, geo.GeoError) as e:
            raise HTTPException(422, str(e))

    @r.get("/status")
    def status():
        return {"engine": "rustgis" if available() else None, "version": getattr(rustgis, "__version__", None),
                "import": sorted(IMPORT_EXT), "export": sorted(EXPORT), "tools": sorted(TOOLS),
                "raster_indices": sorted(INDEXES), "raster_functions": sorted(RASTER_FUNCTIONS),
                "topology_rules": sorted(TOPOLOGY_RULES)}

    @r.post("/import")
    def import_endpoint(req: ImportRequest):
        try:
            raw = base64.b64decode(req.data_base64, validate=True)
        except (binascii.Error, ValueError):
            raise HTTPException(422, "data_base64 không hợp lệ.")
        if len(raw) > MAX_UPLOAD_BYTES:
            raise HTTPException(413, "Tệp lớn hơn 15 MB.")
        return guard(import_file, req.filename, raw)

    @r.post("/export")
    def export_endpoint(req: ExportRequest):
        return guard(export_geojson, req.geojson, req.format, req.name)

    @r.post("/boundary")
    def boundary_endpoint(req: BoundaryRequest):
        for m in req.buffers_m:
            if not (0 < m <= 5000):
                raise HTTPException(422, "Vùng đệm từ 1 đến 5000 m.")
        return guard(boundary_analyze, req.ring, req.buffers_m, req.simplify_m)

    @r.post("/raster/zonal")
    def zonal_endpoint(req: ZonalRequest):
        try:
            raw = base64.b64decode(req.data_base64, validate=True)
        except (binascii.Error, ValueError):
            raise HTTPException(422, "data_base64 không hợp lệ.")
        if len(raw) > MAX_UPLOAD_BYTES:
            raise HTTPException(413, "Tệp lớn hơn 15 MB.")
        return guard(raster_zonal, req.filename, raw, req.ring, req.index, req.bands, req.function, req.function_params)

    @r.post("/tool")
    def tool_endpoint(req: ToolRequest):
        return guard(run_tool, req.tool, req.inputs, req.params)

    @r.post("/topology")
    def topology_endpoint(req: TopologyRequest):
        ring = req.ring
        if ring is None and req.boundary is not None:
            bfc = check_featurecollection(req.boundary)
            if bfc["features"]:
                g = bfc["features"][0].get("geometry") or {}
                if g.get("type") == "Polygon" and g.get("coordinates"):
                    ring = g["coordinates"][0]
        return guard(topology_validate, ring, req.zones, req.trees)

    @r.post("/profile")
    def profile_endpoint(req: ProfileRequest):
        return guard(profile_geojson, req.geojson)

    @r.post("/interpolate")
    def interpolate_endpoint(req: InterpolateRequest):
        return guard(interpolate_idw, req.points, req.field, req.ring, req.power, req.neighbors, req.cell_size_deg)

    @r.post("/render")
    def render_endpoint(req: RenderRequest):
        png = guard(render_map_png, req.layers, req.styles, req.width, req.height, req.dpi)
        return Response(png, media_type="image/png", headers={"Cache-Control": "no-store"})

    return r

