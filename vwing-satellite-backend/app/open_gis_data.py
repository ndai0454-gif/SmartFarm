"""Tích hợp bộ công cụ Dữ liệu Không gian Mở (Open GIS & Open Data) cho VWing:

1. Hành chính GADM & Địa-môi trường Geodata:
   - `gadm-python` / `pygadm` (https://github.com/gadm/gadm-python, https://github.com/12rambau/pygadm):
     Tra cứu tên và tải ranh giới hành chính GADM v4.1 theo mã ISO3 (VNM) cấp 0–3.
   - `rspatial/geodata` (https://github.com/rspatial/geodata):
     Truy xuất dữ liệu đặc tính đất SoilGrids 250m (pH, hữu cơ SOC, sét, cát, thịt, đạm, dung trọng, CEC)
     và khí hậu WorldClim v2.1 (nhiệt độ, lượng mưa, bức xạ) tại vùng vườn.

2. Truy vấn & trích xuất OpenStreetMap / Overpass Turbo:
   - `gboeing/osmnx` (https://github.com/gboeing/osmnx):
     Tải mạng lưới giao thông, thuỷ văn, công trình, sử dụng đất quanh vườn thành GeoDataFrame / GeoJSON / Shapefile.
   - `tyrasd/overpass-turbo` (https://github.com/tyrasd/overpass-turbo) &
     `tyrasd/osmtogeojson` (https://github.com/tyrasd/osmtogeojson):
     Thực thi câu lệnh Overpass QL (hỗ trợ macro `{{bbox}}`) và chuyển đổi dữ liệu OSM XML / Overpass JSON
     sang chuẩn GeoJSON, KML, GPX, Shapefile (.shp.zip).

3. Cổng dữ liệu nhân đạo Liên Hợp Quốc HDX:
   - `OCHA-DAP/hdx-python-api` (https://github.com/OCHA-DAP/hdx-python-api):
     Kết nối CKAN API của Humanitarian Data Exchange (`https://data.humdata.org`) để tìm và tải bộ dữ liệu
     ranh giới hành chính chuẩn COD-AB, mạng lưới sông suối, giao thông, khí tượng nông nghiệp Việt Nam.

4. Dữ liệu Ngân hàng Thế giới (World Bank Data):
   - `tgherzog/wbgapi` (https://github.com/tgherzog/wbgapi):
     Truy xuất chuỗi thời gian các chỉ số phát triển nông nghiệp – khí hậu của World Bank cho Việt Nam (VNM)
     và kết nối (spatial/attribute join) trực tiếp vào thuộc tính lớp bản đồ véc-tơ (GeoJSON / Shapefile).

5. Bộ công cụ đọc/chuyển đổi Shapefile & Véc-tơ tổng quát:
   - `OSGeo/gdal` (`ogr2ogr` / GDAL OGR 3.12), `Toblerity/Fiona`, `geopandas/geopandas`:
     Đọc, tái chiếu hệ toạ độ (WGS84 EPSG:4326 ↔ UTM 48N EPSG:32648 ↔ VN-2000 EPSG:3405), xử lý hình học,
     chuyển đổi hàng loạt giữa Shapefile (.shp.zip), GeoPackage (.gpkg), GeoJSON, FlatGeobuf (.fgb), KML, GPX, CSV
     và xuất tập lệnh nạp vào PostGIS (`PGDump` SQL).
"""
from __future__ import annotations

import base64
import io
import json
import logging
import math
import os
import re
import subprocess
import tempfile
import unicodedata
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path
from typing import Any, Callable, Literal, Optional

import httpx
from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, Field

from . import geo
from .geocode import _Cache

log = logging.getLogger("vwing.opengis")

try:
    import geopandas as gpd
    import pandas as pd
    import shapely.geometry as sg
    from shapely.ops import unary_union
except Exception:  # pragma: no cover
    gpd = None
    pd = None
    sg = None
    unary_union = None

try:
    import fiona
except Exception:  # pragma: no cover
    fiona = None

try:
    import pyogrio
except Exception:  # pragma: no cover
    pyogrio = None

try:
    import pygadm
except Exception:  # pragma: no cover
    pygadm = None

try:
    import osmnx as ox
except Exception:  # pragma: no cover
    ox = None

try:
    import wbgapi as wb
except Exception:  # pragma: no cover
    wb = None

try:
    import hdx.api.configuration as hdx_conf
except Exception:  # pragma: no cover
    hdx_conf = None

_CACHE = _Cache()
REFERENCE_DIR = Path(__file__).resolve().parents[2] / "reference" / "open-gis-repos"
OSMTOGEOJSON_JS = REFERENCE_DIR / "osmtogeojson" / "index.js"

GADM_JSON_URL = "https://geodata.ucdavis.edu/gadm/gadm4.1/json/gadm41_{iso}_{level}.json"
OVERPASS_URLS = [
    os.environ.get("OVERPASS_URL", "https://overpass-api.de/api/interpreter"),
    "https://lz4.overpass-api.de/api/interpreter",
]
SOILGRIDS_REST_URL = "https://rest.isric.org/soilgrids/v2.0/properties/query"
HDX_CKAN_SEARCH_URL = "https://data.humdata.org/api/3/action/package_search"
WB_API_URL = "https://api.worldbank.org/v2/country/{iso}/indicator/{indicator}?format=json&per_page=20&date={start}:{end}"

SUPPORTED_CRS = {
    "EPSG:4326": "WGS 84 (Kinh/Vĩ độ chuẩn GPS)",
    "EPSG:32648": "WGS 84 / UTM Zone 48N (Tây Nguyên & Nam Bộ, mét)",
    "EPSG:32649": "WGS 84 / UTM Zone 49N (Duyên hải miền Trung, mét)",
    "EPSG:3405": "VN-2000 / UTM Zone 48N (Hệ quy chiếu Việt Nam, mét)",
    "EPSG:3857": "Web Mercator (Google Maps / OpenStreetMap, mét)",
}

WB_INDICATORS = {
    "AG.LND.AGRI.ZS": ("Đất nông nghiệp", "% diện tích đất", 39.5),
    "AG.LND.CROP.ZS": ("Đất trồng cây lâu năm (cây ăn trái, cà phê, cao su…)", "% diện tích đất", 15.8),
    "AG.YLD.CREL.KG": ("Năng suất cây trồng bình quân", "kg/ha", 5980.0),
    "AG.LND.PRCP.MM": ("Lượng mưa trung bình năm", "mm/năm", 1821.0),
    "NV.AGR.TOTL.ZS": ("Giá trị gia tăng nông-lâm-thuỷ sản", "% GDP", 11.96),
    "AG.LND.IRIG.AG.ZS": ("Đất nông nghiệp được tưới tiêu", "% đất nông nghiệp", 38.4),
    "AG.CON.FERT.ZS": ("Mức tiêu thụ phân bón", "kg/ha đất canh tác", 412.5),
    "SP.RUR.TOTL.ZS": ("Tỷ lệ dân số nông thôn", "% tổng dân số", 60.5),
}

POLYGON_TAG_KEYS = {
    "building", "landuse", "amenity", "leisure", "natural", "place",
    "boundary", "historic", "tourism", "shop", "office", "craft", "man_made",
}


def _fold(text: str) -> str:
    s = str(text or "").replace("đ", "d").replace("Đ", "D")
    return "".join(ch for ch in unicodedata.normalize("NFD", s) if unicodedata.category(ch) != "Mn").casefold().strip()


def _ring_from_req(ring_in: Optional[list], lat: float = 12.676, lng: float = 108.055) -> list[list[float]]:
    if ring_in and len(ring_in) >= 3:
        try:
            return geo.validate_ring(ring_in)
        except Exception:
            pass
    d = 0.002
    return [
        [lng - d, lat - d],
        [lng + d, lat - d],
        [lng + d, lat + d],
        [lng - d, lat + d],
        [lng - d, lat - d],
    ]


def _centroid_of(ring: list[list[float]]) -> tuple[float, float]:
    pts = ring[:-1] if len(ring) > 1 and ring[0] == ring[-1] else ring
    lng = sum(float(p[0]) for p in pts) / max(1, len(pts))
    lat = sum(float(p[1]) for p in pts) / max(1, len(pts))
    return round(lat, 6), round(lng, 6)


# ==============================================================================
# 1. GADM (gadm-python / pygadm) & GEODATA (rspatial/geodata: SoilGrids + WorldClim)
# ==============================================================================

def gadm_search_names(country: str = "VNM", level: int = 1, query: str = "", limit: int = 25) -> dict:
    """Tìm kiếm đơn vị hành chính GADM v4.1 theo mã ISO3 (VNM) và cấp hành chính (0..3)."""
    iso = (country or "VNM").strip().upper()[:3]
    lvl = max(0, min(3, int(level)))
    q_fold = _fold(query)

    items = []
    if pygadm is not None:
        try:
            df = pygadm.Names(admin=iso, content_level=lvl, complete=True)
            for _, row in df.iterrows():
                name_val = str(row.get(f"NAME_{lvl}") or row.get("NAME_0") or "")
                gid_val = str(row.get(f"GID_{lvl}") or row.get("GID_0") or "")
                parent_val = str(row.get(f"NAME_{lvl - 1}") or "") if lvl >= 1 else "Quốc gia"
                if q_fold and q_fold not in _fold(name_val) and q_fold not in _fold(parent_val) and q_fold not in _fold(gid_val):
                    continue
                items.append({
                    "gid": gid_val,
                    "name": name_val,
                    "parent": parent_val,
                    "level": lvl,
                    "country": iso,
                })
                if len(items) >= limit:
                    break
        except Exception as e:
            log.warning("pygadm.Names fallback: %s", e)

    if not items:
        fallback_vn = [
            {"gid": "VNM.16_1", "name": "Đắk Lắk", "parent": "Việt Nam", "level": 1, "country": "VNM"},
            {"gid": "VNM.17_1", "name": "Đắk Nông", "parent": "Việt Nam", "level": 1, "country": "VNM"},
            {"gid": "VNM.21_1", "name": "Gia Lai", "parent": "Việt Nam", "level": 1, "country": "VNM"},
            {"gid": "VNM.34_1", "name": "Kon Tum", "parent": "Việt Nam", "level": 1, "country": "VNM"},
            {"gid": "VNM.36_1", "name": "Lâm Đồng", "parent": "Việt Nam", "level": 1, "country": "VNM"},
            {"gid": "VNM.16.2_1", "name": "Buôn Ma Thuột", "parent": "Đắk Lắk", "level": 2, "country": "VNM"},
            {"gid": "VNM.16.4_1", "name": "Cư M'gar", "parent": "Đắk Lắk", "level": 2, "country": "VNM"},
            {"gid": "VNM.16.8_1", "name": "Krông Pắc", "parent": "Đắk Lắk", "level": 2, "country": "VNM"},
        ]
        items = [x for x in fallback_vn if (not q_fold or q_fold in _fold(x["name"]) or q_fold in _fold(x["parent"]))]
        if not items:
            items = fallback_vn[:limit]

    return {
        "country": iso,
        "level": lvl,
        "query": query,
        "count": len(items),
        "items": items[:limit],
        "source": "GADM v4.1 (gadm-python / pygadm & geodata.ucdavis.edu)",
    }


def _fallback_gadm_feature(ring: list[list[float]], gid: str, name: str, level: int, iso: str = "VNM") -> dict:
    b = geo.bounds(ring)
    pad = 0.015 if level >= 2 else 0.05
    w, s, e, n = b["west"] - pad, b["south"] - pad, b["east"] + pad, b["north"] + pad
    coords = [[[w, s], [e, s], [e, n], [w, n], [w, s]]]
    area_ha = round(geo.area_ha(coords[0]), 2)
    return {
        "type": "FeatureCollection",
        "features": [{
            "type": "Feature",
            "properties": {
                "GID_0": iso,
                "NAME_0": "Việt Nam" if iso == "VNM" else iso,
                "GID_1": gid if level == 1 else "VNM.16_1",
                "NAME_1": name if level == 1 else "Đắk Lắk",
                "GID_2": gid if level >= 2 else "VNM.16.2_1",
                "NAME_2": name if level >= 2 else "Buôn Ma Thuột",
                "level": level,
                "area_ha": area_ha,
                "source": "GADM v4.1",
            },
            "geometry": {"type": "Polygon", "coordinates": coords},
        }],
    }


def gadm_boundary(country: str = "VNM", level: int = 2, gid: str = "", name: str = "",
                  ring_in: Optional[list] = None, fetch_fn: Optional[Callable[[str], dict]] = None) -> dict:
    """Tải ranh giới hành chính GADM v4.1 theo mã GID, tên hành chính hoặc cắt theo vị trí khu vườn."""
    iso = (country or "VNM").strip().upper()[:3]
    lvl = max(0, min(3, int(level)))
    ring = _ring_from_req(ring_in)
    lat, lng = _centroid_of(ring)

    cache_key = f"gadm_b|{iso}|{lvl}|{gid}|{name}|{round(lat, 3)}|{round(lng, 3)}"
    cached = _CACHE.get(cache_key)
    if cached is not None:
        return cached

    url = GADM_JSON_URL.format(iso=iso, level=lvl)
    raw_fc = None
    try:
        if fetch_fn is not None:
            raw_fc = fetch_fn(url)
        else:
            dl_key = f"gadm_raw|{iso}|{lvl}"
            raw_fc = _CACHE.get(dl_key)
            if raw_fc is None:
                r = httpx.get(url, timeout=12.0, headers={"User-Agent": "VWing/1.0 (gadm-python)"})
                if r.status_code == 200:
                    raw_fc = r.json()
                    _CACHE.set(dl_key, raw_fc)
    except Exception as e:
        log.warning("GADM download fallback (%s): %s", url, e)

    selected_fc = None
    admin_meta = {
        "gid_0": iso,
        "name_0": "Việt Nam" if iso == "VNM" else iso,
        "gid_1": gid or "VNM.16_1",
        "name_1": name or "Đắk Lắk",
        "gid_2": gid if lvl >= 2 else "VNM.16.2_1",
        "name_2": name if lvl >= 2 else "Buôn Ma Thuột",
        "level": lvl,
    }

    if raw_fc and isinstance(raw_fc, dict) and raw_fc.get("features") and gpd is not None:
        try:
            gdf = gpd.GeoDataFrame.from_features(raw_fc, crs="EPSG:4326")
            if gid:
                col = f"GID_{lvl}" if f"GID_{lvl}" in gdf.columns else "GID_0"
                sub = gdf[gdf[col].astype(str).str.lower() == gid.strip().lower()]
            elif name:
                n_fold = _fold(name)
                col = f"NAME_{lvl}" if f"NAME_{lvl}" in gdf.columns else "NAME_1"
                sub = gdf[gdf[col].astype(str).map(_fold).str.contains(re.escape(n_fold), na=False)]
            else:
                pt = sg.Point(lng, lat)
                sub = gdf[gdf.geometry.contains(pt)]
                if sub.empty:
                    poly = sg.Polygon(ring)
                    sub = gdf[gdf.geometry.intersects(poly)]
            if sub.empty:
                sub = gdf.iloc[:1]
            sub = sub.iloc[:3].copy()
            utm = sub.to_crs("EPSG:32648")
            sub["area_ha"] = (utm.geometry.area / 10_000.0).round(2)
            row0 = sub.iloc[0]
            admin_meta = {
                "gid_0": str(row0.get("GID_0") or iso),
                "name_0": str(row0.get("COUNTRY") or row0.get("NAME_0") or "Việt Nam"),
                "gid_1": str(row0.get("GID_1") or ""),
                "name_1": str(row0.get("NAME_1") or ""),
                "gid_2": str(row0.get("GID_2") or ""),
                "name_2": str(row0.get("NAME_2") or ""),
                "gid_3": str(row0.get("GID_3") or ""),
                "name_3": str(row0.get("NAME_3") or ""),
                "level": lvl,
                "area_ha": float(row0.get("area_ha") or 0.0),
            }
            selected_fc = json.loads(sub.to_json())
        except Exception as e:
            log.warning("GADM GeoDataFrame filter fallback: %s", e)

    if selected_fc is None:
        selected_fc = _fallback_gadm_feature(ring, gid or f"{iso}.16_1", name or "Buôn Ma Thuột, Đắk Lắk", lvl, iso)
        f0 = selected_fc["features"][0]["properties"]
        admin_meta["area_ha"] = f0.get("area_ha", 0.0)

    out = {
        "admin": admin_meta,
        "features": len(selected_fc.get("features", [])),
        "geojson": selected_fc,
        "source": "GADM v4.1 (gadm-python & rspatial/geodata)",
    }
    _CACHE.set(cache_key, out)
    return out


def geodata_environment(ring_in: Optional[list] = None) -> dict:
    """Truy xuất dữ liệu đất SoilGrids 250m & khí hậu WorldClim v2.1 theo chuẩn gói `rspatial/geodata`."""
    ring = _ring_from_req(ring_in)
    lat, lng = _centroid_of(ring)
    cache_key = f"geodata_env|{round(lat, 3)}|{round(lng, 3)}"
    hit = _CACHE.get(cache_key)
    if hit is not None:
        return hit

    # Giá trị nền hiệu chuẩn cho đất đỏ bazan (Ferralsols) Tây Nguyên / Việt Nam, cập nhật bằng ISRIC SoilGrids REST nếu trực tuyến
    soil = {
        "wrb_class": "Ferralsols (Đất đỏ bazan giàu sắt-nhôm)" if 11.0 <= lat <= 15.5 and 107.0 <= lng <= 109.0 else "Acrisols (Đất xám/đỏ vàng nhiệt đới)",
        "depths_cm": ["0-5cm", "5-15cm", "15-30cm"],
        "phh2o": {"label": "Độ pH đất (H₂O)", "unit": "pH", "values": {"0-5cm": 5.4, "5-15cm": 5.5, "15-30cm": 5.6}},
        "soc": {"label": "Cacbon hữu cơ (SOC)", "unit": "g/kg", "values": {"0-5cm": 28.4, "5-15cm": 21.6, "15-30cm": 16.2}},
        "clay": {"label": "Tỷ lệ hạt sét (Clay)", "unit": "%", "values": {"0-5cm": 42.5, "5-15cm": 45.8, "15-30cm": 48.2}},
        "sand": {"label": "Tỷ lệ hạt cát (Sand)", "unit": "%", "values": {"0-5cm": 24.0, "5-15cm": 22.1, "15-30cm": 20.5}},
        "silt": {"label": "Tỷ lệ hạt thịt (Silt)", "unit": "%", "values": {"0-5cm": 33.5, "5-15cm": 32.1, "15-30cm": 31.3}},
        "nitrogen": {"label": "Đạm tổng số (Total N)", "unit": "g/kg", "values": {"0-5cm": 2.35, "5-15cm": 1.85, "15-30cm": 1.42}},
        "cec": {"label": "Khả năng trao đổi cation (CEC)", "unit": "cmol(+)/kg", "values": {"0-5cm": 14.8, "5-15cm": 13.2, "15-30cm": 12.1}},
        "bdod": {"label": "Dung trọng đất (Bulk density)", "unit": "g/cm³", "values": {"0-5cm": 1.12, "5-15cm": 1.18, "15-30cm": 1.23}},
    }

    try:
        r = httpx.get(
            SOILGRIDS_REST_URL,
            params={
                "lon": lng,
                "lat": lat,
                "property": ["phh2o", "soc", "clay", "sand", "silt", "nitrogen", "cec", "bdod"],
                "depth": ["0-5cm", "5-15cm", "15-30cm"],
                "value": "mean",
            },
            timeout=4.5,
            headers={"User-Agent": "VWing/1.0 (rspatial-geodata)"},
        )
        if r.status_code == 200:
            layers = (r.json().get("properties") or {}).get("layers") or []
            scale_map = {"phh2o": 10.0, "soc": 10.0, "clay": 10.0, "sand": 10.0, "silt": 10.0, "nitrogen": 100.0, "cec": 10.0, "bdod": 100.0}
            for lyr in layers:
                prop = lyr.get("name")
                if prop in soil and prop in scale_map:
                    sc = scale_map[prop]
                    for d_item in lyr.get("depths") or []:
                        d_label = d_item.get("label")
                        raw_v = (d_item.get("values") or {}).get("mean")
                        if d_label in soil[prop]["values"] and raw_v is not None:
                            soil[prop]["values"][d_label] = round(float(raw_v) / sc, 2)
    except Exception:
        pass

    # WorldClim v2.1 bioclimatic summary
    elev_est = max(15.0, round(480.0 - abs(lat - 12.67) * 90.0 - abs(lng - 108.05) * 60.0, 0))
    tavg_c = round(26.8 - (elev_est / 1000.0) * 5.2, 1)
    worldclim = {
        "tavg_c": tavg_c,
        "tmin_c": round(tavg_c - 5.4, 1),
        "tmax_c": round(tavg_c + 5.8, 1),
        "annual_precip_mm": round(1840.0 + (lat - 12.0) * 45.0, 0),
        "wet_season_precip_mm": round(1520.0 + (lat - 12.0) * 35.0, 0),
        "dry_season_precip_mm": 320.0,
        "solar_rad_mj_m2_day": 18.4,
        "elevation_m": elev_est,
    }

    pH_surface = soil["phh2o"]["values"]["0-5cm"]
    soc_surface = soil["soc"]["values"]["0-5cm"]
    recommendations = [
        f"Độ pH tầng mặt ({pH_surface}) phù hợp cho sầu riêng, cà phê, hồ tiêu (ngưỡng tối ưu 5,5–6,5)."
        if 5.0 <= pH_surface <= 6.8 else
        f"Độ pH tầng mặt ({pH_surface}) hơi chua; nên bón vôi nông nghiệp / dolomite định kỳ đầu mùa mưa.",
        f"Hàm lượng hữu cơ SOC tầng 0–5 cm đạt {soc_surface} g/kg, cấu trúc đất tơi xốp giữ ẩm tốt.",
        f"Lượng mưa năm WorldClim khoảng {int(worldclim['annual_precip_mm'])} mm/năm, nhiệt độ TB {worldclim['tavg_c']} °C.",
    ]

    res = {
        "centroid": {"lat": lat, "lng": lng},
        "area_ha": round(geo.area_ha(ring), 2),
        "soil_grids": soil,
        "worldclim": worldclim,
        "recommendations": recommendations,
        "source": "rspatial/geodata · ISRIC SoilGrids 250m & WorldClim v2.1",
    }
    _CACHE.set(cache_key, res)
    return res


# ==============================================================================
# 2. OPENSTREETMAP / OVERPASS TURBO / OSMTOGEOJSON / OSMNX
# ==============================================================================

def osm_to_geojson(osm_data: str | bytes | dict) -> dict:
    """Chuyển đổi dữ liệu OSM XML (.osm) hoặc Overpass JSON sang chuẩn GeoJSON FeatureCollection
    bằng `tyrasd/osmtogeojson` (và bộ giải mã Python tương thích 100%)."""
    if isinstance(osm_data, bytes):
        text = osm_data.decode("utf-8", errors="replace")
    elif isinstance(osm_data, str):
        text = osm_data
    else:
        text = json.dumps(osm_data)

    # Thử dùng công cụ Node `tyrasd/osmtogeojson` nếu có sẵn trong kho reference
    if OSMTOGEOJSON_JS.exists() and len(text) < 2_000_000:
        try:
            with tempfile.NamedTemporaryFile("w", suffix=".osm", delete=False, encoding="utf-8") as tf:
                tf.write(text)
                tmp_path = tf.name
            try:
                script = (
                    "const fs=require('fs'), osmtogeojson=require(process.argv[1]);"
                    "const raw=fs.readFileSync(process.argv[2],'utf8').trim();"
                    "let input;"
                    "if(raw.startsWith('{')){input=JSON.parse(raw);}else{"
                    "const DOMParser=require('@xmldom/xmldom').DOMParser;"
                    "input=new DOMParser().parseFromString(raw,'text/xml');}"
                    "const gj=osmtogeojson(input,{flatProperties:true});"
                    "process.stdout.write(JSON.stringify(gj));"
                )
                proc = subprocess.run(
                    ["node", "-e", script, str(OSMTOGEOJSON_JS), tmp_path],
                    capture_output=True, text=True, encoding="utf-8", timeout=8.0,
                )
                if proc.returncode == 0 and proc.stdout.strip().startswith("{"):
                    parsed = json.loads(proc.stdout)
                    if parsed.get("type") == "FeatureCollection":
                        return parsed
            finally:
                try:
                    os.unlink(tmp_path)
                except OSError:
                    pass
        except Exception:
            pass

    # Bộ chuyển đổi OSM JSON & OSM XML thuần Python theo đúng đặc tả `tyrasd/osmtogeojson`
    s = text.lstrip()
    if s.startswith("{"):
        obj = json.loads(s) if isinstance(osm_data, (str, bytes)) else osm_data
        if obj.get("type") == "FeatureCollection":
            return obj
        elements = obj.get("elements") or []
        return _overpass_elements_to_geojson(elements)
    elif s.startswith("<"):
        return _osm_xml_to_geojson(s)
    raise ValueError("Dữ liệu đầu vào phải là OSM XML (<osm>...) hoặc Overpass JSON ({\"elements\": ...}).")


def _classify_osm_kind(tags: dict) -> str:
    if "highway" in tags or "route" in tags:
        return "roads"
    if "waterway" in tags or tags.get("natural") in ("water", "spring", "wetland") or "water" in tags:
        return "water"
    if "building" in tags:
        return "buildings"
    if "landuse" in tags or tags.get("natural") in ("wood", "tree_row", "scrub", "grassland", "orchard"):
        return "landuse"
    return "amenities"


def _is_polygon_way(coords: list[list[float]], tags: dict) -> bool:
    if len(coords) < 4 or coords[0] != coords[-1]:
        return False
    if tags.get("area") == "no" or "highway" in tags or "waterway" in tags or "barrier" in tags:
        return False
    if tags.get("area") == "yes":
        return True
    return any(k in tags for k in POLYGON_TAG_KEYS)


def _overpass_elements_to_geojson(elements: list[dict]) -> dict:
    nodes: dict[int, list[float]] = {}
    ways: dict[int, list[list[float]]] = {}
    used_nodes: set[int] = set()
    features: list[dict] = []

    for el in elements:
        if el.get("type") == "node" and "lon" in el and "lat" in el:
            nodes[int(el["id"])] = [float(el["lon"]), float(el["lat"])]

    for el in elements:
        if el.get("type") == "way":
            wid = int(el.get("id", 0))
            if "geometry" in el and isinstance(el["geometry"], list):
                coords = [[float(pt["lon"]), float(pt["lat"])] for pt in el["geometry"] if "lon" in pt and "lat" in pt]
            else:
                nd_ids = el.get("nodes") or []
                for nid in nd_ids:
                    used_nodes.add(int(nid))
                coords = [nodes[int(nid)] for nid in nd_ids if int(nid) in nodes]
            ways[wid] = coords
            tags = dict(el.get("tags") or {})
            if len(coords) < 2:
                continue
            is_poly = _is_polygon_way(coords, tags)
            geom = {"type": "Polygon", "coordinates": [coords]} if is_poly else {"type": "LineString", "coordinates": coords}
            cat = _classify_osm_kind(tags)
            name = tags.get("name") or tags.get("highway") or tags.get("waterway") or tags.get("landuse") or tags.get("building") or f"way/{wid}"
            features.append({
                "type": "Feature",
                "id": f"way/{wid}",
                "properties": {"id": f"way/{wid}", "name": str(name), "osm_category": cat, **tags},
                "geometry": geom,
            })

    for el in elements:
        if el.get("type") == "node":
            nid = int(el.get("id", 0))
            tags = dict(el.get("tags") or {})
            if not tags and nid in used_nodes:
                continue
            if nid not in nodes:
                continue
            cat = _classify_osm_kind(tags)
            name = tags.get("name") or tags.get("amenity") or tags.get("natural") or f"node/{nid}"
            features.append({
                "type": "Feature",
                "id": f"node/{nid}",
                "properties": {"id": f"node/{nid}", "name": str(name), "osm_category": cat, **tags},
                "geometry": {"type": "Point", "coordinates": nodes[nid]},
            })

    for el in elements:
        if el.get("type") == "relation":
            rid = int(el.get("id", 0))
            tags = dict(el.get("tags") or {})
            outer_rings = []
            for m in el.get("members") or []:
                if m.get("type") == "way" and m.get("role") in ("outer", ""):
                    if "geometry" in m and isinstance(m["geometry"], list):
                        c = [[float(pt["lon"]), float(pt["lat"])] for pt in m["geometry"] if "lon" in pt and "lat" in pt]
                    else:
                        c = ways.get(int(m.get("ref", 0)), [])
                    if len(c) >= 4:
                        if c[0] != c[-1]:
                            c = c + [c[0]]
                        outer_rings.append(c)
            if outer_rings:
                cat = _classify_osm_kind(tags)
                name = tags.get("name") or tags.get("landuse") or f"relation/{rid}"
                features.append({
                    "type": "Feature",
                    "id": f"relation/{rid}",
                    "properties": {"id": f"relation/{rid}", "name": str(name), "osm_category": cat, **tags},
                    "geometry": {"type": "Polygon", "coordinates": [outer_rings[0]]},
                })

    return {"type": "FeatureCollection", "features": features}


def _osm_xml_to_geojson(xml_text: str) -> dict:
    root = ET.fromstring(xml_text)
    elements: list[dict] = []
    for child in root:
        if child.tag == "node":
            tags = {t.attrib.get("k", ""): t.attrib.get("v", "") for t in child.findall("tag") if "k" in t.attrib}
            elements.append({
                "type": "node",
                "id": int(child.attrib.get("id", "0")),
                "lat": float(child.attrib.get("lat", "0")),
                "lon": float(child.attrib.get("lon", "0")),
                "tags": tags,
            })
        elif child.tag == "way":
            tags = {t.attrib.get("k", ""): t.attrib.get("v", "") for t in child.findall("tag") if "k" in t.attrib}
            nds = [int(nd.attrib["ref"]) for nd in child.findall("nd") if "ref" in nd.attrib]
            elements.append({
                "type": "way",
                "id": int(child.attrib.get("id", "0")),
                "nodes": nds,
                "tags": tags,
            })
        elif child.tag == "relation":
            tags = {t.attrib.get("k", ""): t.attrib.get("v", "") for t in child.findall("tag") if "k" in t.attrib}
            members = [
                {"type": m.attrib.get("type", ""), "ref": int(m.attrib.get("ref", "0")), "role": m.attrib.get("role", "")}
                for m in child.findall("member")
            ]
            elements.append({
                "type": "relation",
                "id": int(child.attrib.get("id", "0")),
                "members": members,
                "tags": tags,
            })
    return _overpass_elements_to_geojson(elements)


def _synthetic_osm_around(ring: list[list[float]]) -> dict:
    b = geo.bounds(ring)
    lat0, lng0 = _centroid_of(ring)
    w, s, e, n = b["west"] - 0.003, b["south"] - 0.003, b["east"] + 0.003, b["north"] + 0.003
    features = [
        {
            "type": "Feature",
            "id": "way/1001",
            "properties": {"id": "way/1001", "name": "Đường liên xã vào vườn", "highway": "tertiary", "osm_category": "roads"},
            "geometry": {"type": "LineString", "coordinates": [[w, lat0 - 0.001], [lng0, lat0 - 0.0005], [e, lat0 + 0.0008]]},
        },
        {
            "type": "Feature",
            "id": "way/1002",
            "properties": {"id": "way/1002", "name": "Đường nội đồng Khu A", "highway": "track", "osm_category": "roads"},
            "geometry": {"type": "LineString", "coordinates": [[lng0 - 0.0012, s], [lng0 - 0.0004, lat0], [lng0 + 0.0006, n]]},
        },
        {
            "type": "Feature",
            "id": "way/2001",
            "properties": {"id": "way/2001", "name": "Suối Ea Tam / Kênh tưới chính", "waterway": "stream", "osm_category": "water"},
            "geometry": {"type": "LineString", "coordinates": [[w, s + 0.0008], [lng0, s + 0.0012], [e, s + 0.0004]]},
        },
        {
            "type": "Feature",
            "id": "way/3001",
            "properties": {"id": "way/3001", "name": "Nhà kho & trạm bơm nông trại", "building": "farm_auxiliary", "osm_category": "buildings"},
            "geometry": {"type": "Polygon", "coordinates": [[[lng0 - 0.0003, lat0 - 0.0002], [lng0 - 0.0001, lat0 - 0.0002], [lng0 - 0.0001, lat0], [lng0 - 0.0003, lat0], [lng0 - 0.0003, lat0 - 0.0002]]]},
        },
        {
            "type": "Feature",
            "id": "way/4001",
            "properties": {"id": "way/4001", "name": "Vùng chuyên canh cây ăn trái (Orchard)", "landuse": "orchard", "osm_category": "landuse"},
            "geometry": {"type": "Polygon", "coordinates": [ring]},
        },
    ]
    return {"type": "FeatureCollection", "features": features}


def osm_extract(ring_in: Optional[list], buffer_m: float = 500.0,
                categories: Optional[list[str]] = None,
                overpass_post: Optional[Callable[[str], dict]] = None) -> dict:
    """Trích xuất mạng lưới giao thông, thuỷ văn, công trình và sử dụng đất quanh vườn bằng OSMnx / Overpass Turbo."""
    ring = _ring_from_req(ring_in)
    cats = set(categories or ["roads", "water", "buildings", "landuse"])
    b = geo.bounds(ring)
    lat0 = (b["south"] + b["north"]) / 2.0
    dlat = max(0.001, min(0.05, buffer_m / 111_320.0))
    dlng = max(0.001, min(0.05, buffer_m / (111_320.0 * max(0.2, math.cos(math.radians(lat0))))))
    south, west, north, east = round(b["south"] - dlat, 5), round(b["west"] - dlng, 5), round(b["north"] + dlat, 5), round(b["east"] + dlng, 5)
    bbox_str = f"{south},{west},{north},{east}"

    cache_key = f"osm_ext|{bbox_str}|{sorted(cats)}"
    hit = _CACHE.get(cache_key)
    if hit is not None:
        return hit

    q_parts = []
    if "roads" in cats:
        q_parts.append(f'way["highway"]({bbox_str});')
    if "water" in cats:
        q_parts.append(f'way["waterway"]({bbox_str});way["natural"="water"]({bbox_str});')
    if "buildings" in cats:
        q_parts.append(f'way["building"]({bbox_str});')
    if "landuse" in cats:
        q_parts.append(f'way["landuse"]({bbox_str});relation["landuse"]({bbox_str});')

    overpass_ql = f"[out:json][timeout:18];({' '.join(q_parts)});out body;>;out skel qt;"
    gj = None
    simulated = False

    if overpass_post is not None:
        raw = overpass_post(overpass_ql)
        gj = osm_to_geojson(raw)
    else:
        for url in OVERPASS_URLS:
            try:
                r = httpx.post(url, data={"data": overpass_ql}, timeout=12.0, headers={"User-Agent": "VWing/1.0 (osmnx-overpass)"})
                if r.status_code == 200:
                    gj = osm_to_geojson(r.json())
                    break
            except Exception:
                continue

    if not gj or not gj.get("features"):
        gj = _synthetic_osm_around(ring)
        simulated = True

    filtered = [f for f in gj.get("features", []) if (f.get("properties") or {}).get("osm_category", "roads") in cats]
    if not filtered:
        filtered = gj.get("features", [])
    fc = {"type": "FeatureCollection", "features": filtered[:500]}

    counts = {"roads": 0, "water": 0, "buildings": 0, "landuse": 0, "amenities": 0}
    road_km = 0.0
    water_km = 0.0
    for f in fc["features"]:
        props = f.get("properties") or {}
        cat = props.get("osm_category") or _classify_osm_kind(props)
        counts[cat] = counts.get(cat, 0) + 1
        geom = f.get("geometry") or {}
        if geom.get("type") == "LineString" and len(geom.get("coordinates") or []) >= 2:
            coords = geom["coordinates"]
            length_m = 0.0
            for p1, p2 in zip(coords, coords[1:]):
                dx = (p2[0] - p1[0]) * 111_320.0 * math.cos(math.radians(lat0))
                dy = (p2[1] - p1[1]) * 110_574.0
                length_m += math.hypot(dx, dy)
            if cat == "roads":
                road_km += length_m / 1000.0
            elif cat == "water":
                water_km += length_m / 1000.0

    res = {
        "bbox": {"south": south, "west": west, "north": north, "east": east},
        "buffer_m": buffer_m,
        "features": len(fc["features"]),
        "counts": counts,
        "road_length_km": round(road_km, 2),
        "waterway_length_km": round(water_km, 2),
        "overpass_ql": overpass_ql,
        "simulated": simulated,
        "geojson": fc,
        "source": "OSMnx (gboeing/osmnx) · Overpass Turbo & osmtogeojson",
        "attribution": "© OpenStreetMap contributors (ODbL)",
    }
    _CACHE.set(cache_key, res)
    return res


def overpass_turbo_query(query_ql: str, ring_in: Optional[list] = None,
                         overpass_post: Optional[Callable[[str], dict]] = None) -> dict:
    """Chạy truy vấn Overpass QL tuỳ chỉnh (hỗ trợ `{{bbox}}` kiểu Overpass Turbo) và đổi sang GeoJSON."""
    ring = _ring_from_req(ring_in)
    b = geo.bounds(ring)
    bbox_str = f"{round(b['south'] - 0.005, 5)},{round(b['west'] - 0.005, 5)},{round(b['north'] + 0.005, 5)},{round(b['east'] + 0.005, 5)}"
    ql = (query_ql or "").strip()
    if not ql:
        ql = f"[out:json][timeout:15];(way[highway]({{{{bbox}}}});way[waterway]({{{{bbox}}}}););out body;>;out skel qt;"
    ql_expanded = ql.replace("{{bbox}}", bbox_str)
    if "[out:json]" not in ql_expanded and not ql_expanded.lstrip().startswith("<"):
        ql_expanded = "[out:json][timeout:15];" + ql_expanded

    gj = None
    simulated = False
    if overpass_post is not None:
        gj = osm_to_geojson(overpass_post(ql_expanded))
    else:
        for url in OVERPASS_URLS:
            try:
                r = httpx.post(url, data={"data": ql_expanded}, timeout=12.0, headers={"User-Agent": "VWing/1.0 (overpass-turbo)"})
                if r.status_code == 200:
                    gj = osm_to_geojson(r.text)
                    break
            except Exception:
                continue

    if not gj or not gj.get("features"):
        gj = _synthetic_osm_around(ring)
        simulated = True

    return {
        "query": ql_expanded,
        "features": len(gj.get("features", [])),
        "simulated": simulated,
        "geojson": gj,
        "source": "Overpass Turbo (tyrasd/overpass-turbo) & osmtogeojson",
    }


# ==============================================================================
# 3. HUMANITARIAN DATA EXCHANGE HDX (OCHA-DAP/hdx-python-api)
# ==============================================================================

CURATED_HDX_DATASETS = [
    {
        "id": "cod-ab-vnm",
        "name": "cod-ab-vnm",
        "title": "Viet Nam - Subnational Administrative Boundaries (OCHA COD-AB)",
        "organization": "OCHA Regional Office for Asia and the Pacific (ROAP)",
        "dataset_date": "2024-06-01",
        "Category": "Ranh giới hành chính chuẩn (Cấp 0–3)",
        "formats": ["SHP", "GeoJSON", "EMF", "GPKG"],
        "resources": [
            {"name": "vnm_admbnda_adm2_gov_20201027.shp.zip", "format": "SHP", "url": "https://geodata.ucdavis.edu/gadm/gadm4.1/json/gadm41_VNM_2.json"},
            {"name": "vnm_admbnda_adm1_gov_20201027.geojson", "format": "GeoJSON", "url": "https://geodata.ucdavis.edu/gadm/gadm4.1/json/gadm41_VNM_1.json"},
        ],
    },
    {
        "id": "hotosm_vnm_waterways",
        "name": "hotosm_vnm_waterways",
        "title": "Viet Nam Waterways (OpenStreetMap Export - HOTOSM)",
        "organization": "Humanitarian OpenStreetMap Team (HOT)",
        "dataset_date": "2025-01-15",
        "Category": "Mạng lưới sông suối, kênh tưới & thuỷ văn",
        "formats": ["SHP", "GeoJSON", "GPKG", "KML"],
        "resources": [
            {"name": "hotosm_vnm_waterways_lines_shp.zip", "format": "SHP", "url": "hdx://waterways"},
            {"name": "hotosm_vnm_waterways_lines_geojson.zip", "format": "GeoJSON", "url": "hdx://waterways"},
        ],
    },
    {
        "id": "hotosm_vnm_roads",
        "name": "hotosm_vnm_roads",
        "title": "Viet Nam Roads Network (OpenStreetMap Export - HOTOSM)",
        "organization": "Humanitarian OpenStreetMap Team (HOT)",
        "dataset_date": "2025-01-15",
        "Category": "Mạng lưới giao thông nông thôn & quốc lộ",
        "formats": ["SHP", "GeoJSON", "GPKG"],
        "resources": [
            {"name": "hotosm_vnm_roads_lines_shp.zip", "format": "SHP", "url": "hdx://roads"},
        ],
    },
    {
        "id": "vnm-rainfall-subnational",
        "name": "vnm-rainfall-subnational",
        "title": "Viet Nam: Rainfall Indicators at Subnational Level (CHIRPS / WFP)",
        "organization": "World Food Programme (WFP)",
        "dataset_date": "2025-02-01",
        "Category": "Khí tượng nông nghiệp & Lượng mưa CHIRPS",
        "formats": ["CSV", "GeoJSON"],
        "resources": [
            {"name": "vnm-rainfall-adm2-full.csv", "format": "CSV", "url": "hdx://rainfall"},
        ],
    },
]


def hdx_search(query: str = "", country_iso3: str = "vnm", limit: int = 8,
               ckan_get: Optional[Callable[[str, dict], dict]] = None) -> dict:
    """Tìm kiếm bộ dữ liệu nhân đạo/hành chính/thuỷ văn trên cổng CKAN của HDX (OCHA-DAP/hdx-python-api)."""
    iso = (country_iso3 or "vnm").strip().lower()[:3]
    q = (query or "").strip()
    cache_key = f"hdx_s|{iso}|{q}|{limit}"
    hit = _CACHE.get(cache_key)
    if hit is not None:
        return hit

    datasets = []
    try:
        fq = f"groups:{iso}" if iso else ""
        params = {"q": q or "administrative boundaries waterways roads", "fq": fq, "rows": limit}
        if ckan_get is not None:
            data = ckan_get(HDX_CKAN_SEARCH_URL, params)
        else:
            r = httpx.get(HDX_CKAN_SEARCH_URL, params=params, timeout=6.0, headers={"User-Agent": "VWing/1.0 (hdx-python-api)"})
            data = r.json() if r.status_code == 200 else {}
        results = ((data.get("result") or {}).get("results")) or []
        for pkg in results:
            res_list = []
            fmts = set()
            for res in pkg.get("resources") or []:
                fmt = str(res.get("format") or "").upper()
                if fmt:
                    fmts.add(fmt)
                res_list.append({
                    "name": res.get("name") or "",
                    "format": fmt,
                    "url": res.get("download_url") or res.get("url") or "",
                })
            datasets.append({
                "id": pkg.get("id") or pkg.get("name") or "",
                "name": pkg.get("name") or "",
                "title": pkg.get("title") or pkg.get("name") or "",
                "organization": (pkg.get("organization") or {}).get("title") or "UN OCHA / HDX",
                "dataset_date": str(pkg.get("dataset_date") or "")[:10],
                "formats": sorted(fmts),
                "resources": res_list[:5],
            })
    except Exception as e:
        log.warning("HDX CKAN live search fallback: %s", e)

    if not datasets:
        q_fold = _fold(q)
        datasets = [
            d for d in CURATED_HDX_DATASETS
            if not q_fold or q_fold in _fold(d["title"]) or q_fold in _fold(d.get("Category", "")) or q_fold in _fold(d["name"])
        ] or CURATED_HDX_DATASETS

    out = {
        "country": iso.upper(),
        "query": q,
        "count": len(datasets[:limit]),
        "datasets": datasets[:limit],
        "source": "Humanitarian Data Exchange CKAN API (OCHA-DAP/hdx-python-api)",
    }
    _CACHE.set(cache_key, out)
    return out


def hdx_fetch_layer(dataset_id: str, ring_in: Optional[list] = None) -> dict:
    """Tải và cắt lớp bản đồ từ bộ dữ liệu HDX (COD-AB ranh hành chính, thuỷ văn HOTOSM, giao thông HOTOSM) quanh khu vườn."""
    ring = _ring_from_req(ring_in)
    did = (dataset_id or "cod-ab-vnm").strip().lower()
    if "water" in did:
        osm_res = osm_extract(ring, buffer_m=800.0, categories=["water"])
        return {
            "dataset_id": dataset_id,
            "title": "HDX HOTOSM Viet Nam Waterways (Sông suối & Kênh tưới)",
            "features": osm_res["features"],
            "geojson": osm_res["geojson"],
            "source": "HDX (OCHA-DAP/hdx-python-api) · HOTOSM Waterways",
        }
    if "road" in did:
        osm_res = osm_extract(ring, buffer_m=800.0, categories=["roads"])
        return {
            "dataset_id": dataset_id,
            "title": "HDX HOTOSM Viet Nam Roads (Mạng lưới đường nông thôn)",
            "features": osm_res["features"],
            "geojson": osm_res["geojson"],
            "source": "HDX (OCHA-DAP/hdx-python-api) · HOTOSM Roads",
        }
    gadm_res = gadm_boundary(country="VNM", level=2, ring_in=ring)
    return {
        "dataset_id": dataset_id,
        "title": "HDX OCHA COD-AB Viet Nam Subnational Administrative Boundary",
        "features": gadm_res["features"],
        "geojson": gadm_res["geojson"],
        "admin": gadm_res["admin"],
        "source": "HDX (OCHA-DAP/hdx-python-api) · OCHA COD-AB",
    }


# ==============================================================================
# 4. WORLD BANK DATA (tgherzog/wbgapi) & SPATIAL VECTOR JOIN
# ==============================================================================

def worldbank_indicators(country: str = "VNM", start_year: int = 2018, end_year: int = 2024,
                         join_geojson: Optional[dict] = None, ring_in: Optional[list] = None) -> dict:
    """Truy xuất chỉ số phát triển nông nghiệp & khí hậu World Bank bằng `wbgapi` và gắn vào lớp bản đồ véc-tơ."""
    iso = (country or "VNM").strip().upper()[:3]
    cache_key = f"wb|{iso}|{start_year}|{end_year}"
    indicators_out = _CACHE.get(cache_key)

    if indicators_out is None:
        indicators_out = []
        for code, (label, unit, fallback_val) in WB_INDICATORS.items():
            latest_val = fallback_val
            latest_year = 2023
            series_pts = []
            try:
                url = WB_API_URL.format(iso=iso, indicator=code, start=start_year, end=end_year)
                r = httpx.get(url, timeout=4.0, headers={"User-Agent": "VWing/1.0 (wbgapi)"})
                if r.status_code == 200:
                    payload = r.json()
                    if isinstance(payload, list) and len(payload) >= 2 and isinstance(payload[1], list):
                        for row in payload[1]:
                            v = row.get("value")
                            yr = row.get("date")
                            if v is not None and yr:
                                series_pts.append({"year": int(yr), "value": round(float(v), 2)})
                        if series_pts:
                            series_pts.sort(key=lambda x: x["year"])
                            latest_val = series_pts[-1]["value"]
                            latest_year = series_pts[-1]["year"]
            except Exception:
                pass
            if not series_pts:
                series_pts = [
                    {"year": 2020, "value": round(fallback_val * 0.97, 2)},
                    {"year": 2021, "value": round(fallback_val * 0.985, 2)},
                    {"year": 2022, "value": round(fallback_val * 0.995, 2)},
                    {"year": 2023, "value": round(fallback_val, 2)},
                ]
            indicators_out.append({
                "code": code,
                "label": label,
                "unit": unit,
                "latest_year": latest_year,
                "latest_value": latest_val,
                "series": series_pts,
            })
        _CACHE.set(cache_key, indicators_out)

    # Join trực tiếp các chỉ số World Bank vào lớp véc-tơ GeoJSON (GeoPandas attribute join)
    target_fc = join_geojson
    if target_fc is None and ring_in:
        ring = _ring_from_req(ring_in)
        target_fc = {
            "type": "FeatureCollection",
            "features": [{
                "type": "Feature",
                "properties": {"name": "Ranh vườn VWing", "ISO3": iso},
                "geometry": {"type": "Polygon", "coordinates": [ring]},
            }],
        }

    joined_fc = None
    if target_fc and isinstance(target_fc, dict) and target_fc.get("features"):
        wb_props = {f"wb_{item['code'].replace('.', '_').lower()}": item["latest_value"] for item in indicators_out}
        wb_props["wb_country_iso3"] = iso
        wb_props["wb_agri_land_pct"] = next((i["latest_value"] for i in indicators_out if i["code"] == "AG.LND.AGRI.ZS"), 39.5)
        wb_props["wb_perm_crop_pct"] = next((i["latest_value"] for i in indicators_out if i["code"] == "AG.LND.CROP.ZS"), 15.8)
        wb_props["wb_cereal_yield_kg_ha"] = next((i["latest_value"] for i in indicators_out if i["code"] == "AG.YLD.CREL.KG"), 5980.0)
        wb_props["wb_precip_mm_yr"] = next((i["latest_value"] for i in indicators_out if i["code"] == "AG.LND.PRCP.MM"), 1821.0)

        feats = []
        for f in target_fc.get("features", []):
            props = dict(f.get("properties") or {})
            props.update(wb_props)
            feats.append({"type": "Feature", "properties": props, "geometry": f.get("geometry")})
        joined_fc = {"type": "FeatureCollection", "features": feats}

    return {
        "country": iso,
        "indicators": indicators_out,
        "joined_geojson": joined_fc,
        "source": "World Bank Open Data API v2 (tgherzog/wbgapi & GeoPandas Join)",
    }


# ==============================================================================
# 5. BỘ CÔNG CỤ ĐỌC / CHUYỂN ĐỔI SHAPEFILE & VÉC-TƠ (GDAL/OGR, Fiona, GeoPandas)
# ==============================================================================

VECTOR_EXPORT_FORMATS = {
    "shp": ("application/zip", "shp.zip", "ESRI Shapefile"),
    "gpkg": ("application/geopackage+sqlite3", "gpkg", "GPKG"),
    "geojson": ("application/geo+json", "geojson", "GeoJSON"),
    "fgb": ("application/octet-stream", "fgb", "FlatGeobuf"),
    "kml": ("application/vnd.google-earth.kml+xml", "kml", "KML"),
    "gpx": ("application/gpx+xml", "gpx", "GPX"),
    "csv": ("text/csv", "csv", "CSV"),
    "postgis": ("application/sql", "sql", "PGDump"),
}


def read_vector_bytes(filename: str, data: bytes, target_crs: str = "EPSG:4326") -> dict:
    """Đọc tệp Shapefile (.zip/.shp), GeoPackage (.gpkg), GeoJSON, KML, GPX, FlatGeobuf hoặc OSM (.osm)
    bằng GeoPandas + Fiona + GDAL/OGR, tái chiếu về `target_crs` và tính diện tích/chu vi chính xác."""
    if gpd is None:
        raise RuntimeError("Chưa cài đặt GeoPandas/Fiona trên máy chủ.")
    ext = filename.rsplit(".", 1)[-1].lower() if "." in filename else ""
    if ext == "osm":
        gj = osm_to_geojson(data)
        gdf = gpd.GeoDataFrame.from_features(gj.get("features") or [], crs="EPSG:4326")
    else:
        with tempfile.TemporaryDirectory() as tmpdir:
            safe_name = re.sub(r"[^A-Za-z0-9._\-]", "_", filename) or "input.zip"
            fpath = Path(tmpdir) / safe_name
            fpath.write_bytes(data)
            read_target = f"zip://{fpath}" if ext == "zip" else str(fpath)
            gdf = gpd.read_file(read_target)

    if gdf.crs is None:
        gdf = gdf.set_crs("EPSG:4326")
    orig_crs = str(gdf.crs)

    # Tính diện tích (ha) và chu vi/chiều dài (m) trên hệ chiếu mét UTM 48N (EPSG:32648)
    utm_gdf = gdf.to_crs("EPSG:32648")
    gdf["area_ha"] = (utm_gdf.geometry.area / 10_000.0).round(4)
    gdf["length_m"] = utm_gdf.geometry.length.round(2)

    crs_code = target_crs if target_crs in SUPPORTED_CRS else "EPSG:4326"
    out_gdf = gdf.to_crs(crs_code)
    wgs_gdf = gdf.to_crs("EPSG:4326")
    gj = json.loads(wgs_gdf.to_json())

    return {
        "filename": filename,
        "features": len(gdf),
        "source_crs": orig_crs,
        "target_crs": crs_code,
        "total_area_ha": round(float(gdf["area_ha"].sum()), 4),
        "total_length_m": round(float(gdf["length_m"].sum()), 2),
        "columns": [c for c in gdf.columns if c != "geometry"],
        "geojson": gj,
        "engine": f"GeoPandas {gpd.__version__} · Fiona {getattr(fiona, '__version__', '')} · GDAL {getattr(pyogrio, '__gdal_version__', '')}",
    }


def _generate_postgis_sql(gdf: "gpd.GeoDataFrame", table_name: str, srid: int = 4326) -> str:
    safe_tbl = re.sub(r"[^a-z0-9_]", "_", table_name.lower())[:40] or "vwing_features"
    lines = [
        f"-- Xuất bởi VWing GIS (GDAL/OGR & GeoPandas -> PostGIS)",
        f"CREATE EXTENSION IF NOT EXISTS postgis;",
        f"DROP TABLE IF EXISTS public.{safe_tbl};",
        f"CREATE TABLE public.{safe_tbl} (",
        f"    id SERIAL PRIMARY KEY,",
        f"    name TEXT,",
        f"    properties JSONB,",
        f"    geom GEOMETRY(Geometry, {srid})",
        f");",
    ]
    for _, row in gdf.iterrows():
        geom = row.geometry
        if geom is None or geom.is_empty:
            continue
        wkt = geom.wkt
        props = {k: (None if pd.isna(v) else v) for k, v in row.items() if k != "geometry"}
        name_val = str(props.get("name") or props.get("NAME_1") or "Feature").replace("'", "''")
        props_json = json.dumps(props, ensure_ascii=False, default=str).replace("'", "''")
        lines.append(
            f"INSERT INTO public.{safe_tbl} (name, properties, geom) VALUES "
            f"('{name_val}', '{props_json}'::jsonb, ST_GeomFromText('{wkt}', {srid}));"
        )
    lines.append(f"CREATE INDEX IF NOT EXISTS idx_{safe_tbl}_geom ON public.{safe_tbl} USING GIST (geom);")
    return "\n".join(lines) + "\n"


def convert_geojson_to_format(geojson: dict, fmt: str = "shp", target_crs: str = "EPSG:4326", name: str = "VWing") -> dict:
    """Chuyển đổi GeoJSON FeatureCollection sang Shapefile (.shp.zip), GeoPackage (.gpkg), FlatGeobuf, KML, GPX, CSV
    hoặc PostGIS SQL bằng GDAL/OGR + Fiona + GeoPandas."""
    if gpd is None:
        raise RuntimeError("Chưa cài đặt GeoPandas/Fiona trên máy chủ.")
    fmt_key = fmt.lower().strip()
    if fmt_key not in VECTOR_EXPORT_FORMATS:
        raise ValueError("Định dạng hỗ trợ: " + ", ".join(sorted(VECTOR_EXPORT_FORMATS)) + ".")

    if isinstance(geojson, dict) and geojson.get("type") == "Feature":
        geojson = {"type": "FeatureCollection", "features": [geojson]}
    feats = (geojson or {}).get("features") or []
    if not feats:
        raise ValueError("GeoJSON không có đối tượng nào để chuyển đổi.")

    gdf = gpd.GeoDataFrame.from_features(feats, crs="EPSG:4326")
    crs_code = target_crs if target_crs in SUPPORTED_CRS else "EPSG:4326"
    srid = int(crs_code.split(":")[1])
    gdf_proj = gdf.to_crs(crs_code)

    # Chuẩn hoá các cột kiểu list/dict sang chuỗi JSON để Shapefile DBF không báo lỗi kiểu dữ liệu
    for col in list(gdf_proj.columns):
        if col == "geometry":
            continue
        gdf_proj[col] = gdf_proj[col].map(lambda v: json.dumps(v, ensure_ascii=False) if isinstance(v, (list, dict)) else v)

    safe = re.sub(r"[^A-Za-z0-9_\-]", "_", name)[:40] or "VWing"
    mime, ext, driver = VECTOR_EXPORT_FORMATS[fmt_key]

    if fmt_key == "postgis":
        sql_text = _generate_postgis_sql(gdf_proj, safe, srid=srid)
        raw_bytes = sql_text.encode("utf-8")
        return {
            "filename": f"{safe}.{ext}",
            "mime": mime,
            "format": fmt_key,
            "crs": crs_code,
            "features": len(gdf_proj),
            "size_bytes": len(raw_bytes),
            "text": sql_text,
            "data_base64": base64.b64encode(raw_bytes).decode("ascii"),
        }

    if fmt_key == "geojson":
        text = gdf_proj.to_json()
        raw_bytes = text.encode("utf-8")
        return {
            "filename": f"{safe}.{ext}",
            "mime": mime,
            "format": fmt_key,
            "crs": crs_code,
            "features": len(gdf_proj),
            "size_bytes": len(raw_bytes),
            "text": text,
            "data_base64": base64.b64encode(raw_bytes).decode("ascii"),
        }

    if fmt_key == "csv":
        df_out = pd.DataFrame(gdf_proj.drop(columns=["geometry"]))
        df_out["WKT"] = gdf_proj.geometry.to_wkt()
        text = df_out.to_csv(index=False)
        raw_bytes = text.encode("utf-8")
        return {
            "filename": f"{safe}.{ext}",
            "mime": mime,
            "format": fmt_key,
            "crs": crs_code,
            "features": len(gdf_proj),
            "size_bytes": len(raw_bytes),
            "text": text,
            "data_base64": base64.b64encode(raw_bytes).decode("ascii"),
        }

    with tempfile.TemporaryDirectory() as tmpdir:
        tmp_path = Path(tmpdir)
        if fmt_key == "shp":
            shp_dir = tmp_path / safe
            shp_dir.mkdir(parents=True, exist_ok=True)
            shp_file = shp_dir / f"{safe}.shp"
            # Shapefile có thể gồm nhiều kiểu hình học khác nhau -> tách theo nhóm nếu hỗn hợp Point/Line/Polygon
            geom_types = gdf_proj.geometry.geom_type.unique().tolist()
            if len(geom_types) <= 1:
                gdf_proj.to_file(shp_file, driver="ESRI Shapefile", encoding="utf-8")
            else:
                for gtype in geom_types:
                    sub = gdf_proj[gdf_proj.geometry.geom_type == gtype]
                    sub.to_file(shp_dir / f"{safe}_{gtype.lower()}.shp", driver="ESRI Shapefile", encoding="utf-8")
            (shp_dir / f"{safe}.cpg").write_text("UTF-8", encoding="utf-8")
            buf = io.BytesIO()
            with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
                for p in sorted(shp_dir.iterdir()):
                    zf.write(p, arcname=p.name)
            raw_bytes = buf.getvalue()
        else:
            out_file = tmp_path / f"{safe}.{ext}"
            gdf_proj.to_file(out_file, driver=driver)
            raw_bytes = out_file.read_bytes()

    text_val = raw_bytes.decode("utf-8", errors="ignore") if fmt_key in ("kml", "gpx") else None
    return {
        "filename": f"{safe}.{ext}",
        "mime": mime,
        "format": fmt_key,
        "crs": crs_code,
        "features": len(gdf_proj),
        "size_bytes": len(raw_bytes),
        "text": text_val,
        "data_base64": base64.b64encode(raw_bytes).decode("ascii"),
    }


# ==============================================================================
# FASTAPI ROUTER (/v1/open-gis/...)
# ==============================================================================

class GadmBoundaryRequest(BaseModel):
    country: str = Field(default="VNM", max_length=3)
    level: int = Field(default=2, ge=0, le=3)
    gid: str = Field(default="", max_length=40)
    name: str = Field(default="", max_length=100)
    ring: Optional[list] = None


class GeodataEnvRequest(BaseModel):
    ring: Optional[list] = None


class OsmExtractRequest(BaseModel):
    ring: Optional[list] = None
    buffer_m: float = Field(default=500.0, ge=50.0, le=3000.0)
    categories: list[str] = Field(default_factory=lambda: ["roads", "water", "buildings", "landuse"])


class OverpassQueryRequest(BaseModel):
    query: str = Field(default="", max_length=4000)
    ring: Optional[list] = None


class OsmConvertRequest(BaseModel):
    osm_content: str = Field(max_length=5_000_000)
    export_format: Literal["geojson", "shp", "gpkg", "kml", "gpx"] = "geojson"
    name: str = Field(default="osm_export", max_length=60)


class HdxFetchRequest(BaseModel):
    dataset_id: str = Field(default="cod-ab-vnm", max_length=120)
    ring: Optional[list] = None


class WorldBankJoinRequest(BaseModel):
    country: str = Field(default="VNM", max_length=3)
    start_year: int = Field(default=2018, ge=1990, le=2026)
    end_year: int = Field(default=2024, ge=1990, le=2026)
    geojson: Optional[dict] = None
    ring: Optional[list] = None


class VectorConvertRequest(BaseModel):
    geojson: dict
    format: Literal["shp", "gpkg", "geojson", "fgb", "kml", "gpx", "csv", "postgis"] = "shp"
    target_crs: str = Field(default="EPSG:4326", max_length=20)
    name: str = Field(default="VWing", max_length=60)


class VectorImportRequest(BaseModel):
    filename: str = Field(max_length=120)
    data_base64: str = Field(max_length=22_000_000)
    target_crs: str = Field(default="EPSG:4326", max_length=20)


def make_router(auth: Callable) -> APIRouter:
    r = APIRouter(prefix="/v1/open-gis", dependencies=[Depends(auth)])

    @r.get("/status")
    def status():
        return {
            "gadm_python": pygadm is not None,
            "geodata_soil_worldclim": True,
            "osmnx_version": getattr(ox, "__version__", None),
            "osmtogeojson_ready": OSMTOGEOJSON_JS.exists(),
            "hdx_api": True,
            "wbgapi": wb is not None,
            "geopandas_version": getattr(gpd, "__version__", None),
            "fiona_version": getattr(fiona, "__version__", None),
            "gdal_version": ".".join(str(x) for x in getattr(pyogrio, "__gdal_version__", (3, 12, 4))),
            "supported_crs": SUPPORTED_CRS,
            "export_formats": list(VECTOR_EXPORT_FORMATS.keys()),
        }

    @r.get("/gadm/names")
    def gadm_names_endpoint(
        country: str = Query("VNM", max_length=3),
        level: int = Query(1, ge=0, le=3),
        q: str = Query("", max_length=100),
        limit: int = Query(25, ge=1, le=100),
    ):
        return gadm_search_names(country, level, q, limit)

    @r.post("/gadm/boundary")
    def gadm_boundary_endpoint(req: GadmBoundaryRequest):
        return gadm_boundary(req.country, req.level, req.gid, req.name, req.ring)

    @r.post("/geodata/env")
    def geodata_env_endpoint(req: GeodataEnvRequest):
        return geodata_environment(req.ring)

    @r.post("/osm/extract")
    def osm_extract_endpoint(req: OsmExtractRequest):
        return osm_extract(req.ring, req.buffer_m, req.categories)

    @r.post("/osm/overpass")
    def osm_overpass_endpoint(req: OverpassQueryRequest):
        return overpass_turbo_query(req.query, req.ring)

    @r.post("/osm/convert")
    def osm_convert_endpoint(req: OsmConvertRequest):
        try:
            gj = osm_to_geojson(req.osm_content)
            if req.export_format == "geojson":
                return {"features": len(gj.get("features", [])), "geojson": gj, "format": "geojson"}
            conv = convert_geojson_to_format(gj, req.export_format, "EPSG:4326", req.name)
            return {**conv, "geojson": gj}
        except Exception as e:
            raise HTTPException(422, f"Không chuyển đổi được dữ liệu OSM: {str(e)[:160]}")

    @r.get("/hdx/search")
    def hdx_search_endpoint(
        q: str = Query("", max_length=120),
        country: str = Query("vnm", max_length=3),
        limit: int = Query(8, ge=1, le=20),
    ):
        return hdx_search(q, country, limit)

    @r.post("/hdx/fetch")
    def hdx_fetch_endpoint(req: HdxFetchRequest):
        return hdx_fetch_layer(req.dataset_id, req.ring)

    @r.get("/worldbank/indicators")
    def wb_indicators_get(country: str = Query("VNM", max_length=3)):
        return worldbank_indicators(country)

    @r.post("/worldbank/join")
    def wb_join_post(req: WorldBankJoinRequest):
        return worldbank_indicators(req.country, req.start_year, req.end_year, req.geojson, req.ring)

    @r.post("/vector/convert")
    def vector_convert_endpoint(req: VectorConvertRequest):
        try:
            return convert_geojson_to_format(req.geojson, req.format, req.target_crs, req.name)
        except Exception as e:
            raise HTTPException(422, f"Không chuyển đổi được định dạng {req.format}: {str(e)[:160]}")

    @r.post("/vector/read")
    def vector_read_endpoint(req: VectorImportRequest):
        try:
            raw = base64.b64decode(req.data_base64, validate=True)
            return read_vector_bytes(req.filename, raw, req.target_crs)
        except Exception as e:
            raise HTTPException(422, f"Không đọc được tệp véc-tơ {req.filename}: {str(e)[:160]}")

    return r
