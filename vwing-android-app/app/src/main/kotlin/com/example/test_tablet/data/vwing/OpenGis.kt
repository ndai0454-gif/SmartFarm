package com.example.test_tablet.data.vwing

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * Khách kết nối mô-đun Dữ liệu Không gian Mở (`/v1/open-gis/...`) trên máy chủ vệ tinh:
 * 1. GADM (`gadm-python` / `pygadm`) & Geodata (`rspatial/geodata`: SoilGrids 250m + WorldClim v2.1)
 * 2. OpenStreetMap / Overpass Turbo / `osmtogeojson` / `osmnx`
 * 3. Humanitarian Data Exchange HDX (`OCHA-DAP/hdx-python-api`)
 * 4. World Bank Open Data (`tgherzog/wbgapi`) + Spatial Join GeoDataFrame
 * 5. GDAL/OGR + Fiona + GeoPandas: đọc & chuyển đổi Shapefile (.shp.zip), GeoPackage (.gpkg), PostGIS SQL, tái chiếu CRS.
 */
data class GadmItem(
    val gid: String,
    val name: String,
    val parent: String,
    val level: Int,
    val country: String,
)

data class GadmBoundaryResult(
    val gid0: String,
    val name0: String,
    val gid1: String,
    val name1: String,
    val gid2: String,
    val name2: String,
    val level: Int,
    val areaHa: Double,
    val features: Int,
    val geojson: String,
    val source: String,
)

data class SoilDepthProperty(
    val key: String,
    val label: String,
    val unit: String,
    val d0to5: Double,
    val d5to15: Double,
    val d15to30: Double,
)

data class GeodataEnvResult(
    val wrbClass: String,
    val soilProperties: List<SoilDepthProperty>,
    val tavgC: Double,
    val tminC: Double,
    val tmaxC: Double,
    val annualPrecipMm: Double,
    val wetSeasonPrecipMm: Double,
    val drySeasonPrecipMm: Double,
    val solarRadMjM2Day: Double,
    val elevationM: Double,
    val recommendations: List<String>,
    val source: String,
)

data class OsmExtractResult(
    val features: Int,
    val roadsCount: Int,
    val waterCount: Int,
    val buildingsCount: Int,
    val landuseCount: Int,
    val roadLengthKm: Double,
    val waterwayLengthKm: Double,
    val overpassQl: String,
    val simulated: Boolean,
    val geojson: String,
    val source: String,
)

data class HdxResource(val name: String, val format: String, val url: String)

data class HdxDataset(
    val id: String,
    val name: String,
    val title: String,
    val organization: String,
    val datasetDate: String,
    val formats: List<String>,
    val resources: List<HdxResource>,
)

data class HdxFetchResult(
    val datasetId: String,
    val title: String,
    val features: Int,
    val geojson: String,
    val source: String,
)

data class WbIndicator(
    val code: String,
    val label: String,
    val unit: String,
    val latestYear: Int,
    val latestValue: Double,
)

data class WbJoinResult(
    val country: String,
    val indicators: List<WbIndicator>,
    val joinedGeojson: String?,
    val source: String,
)

data class VectorConvertResult(
    val filename: String,
    val mime: String,
    val format: String,
    val crs: String,
    val features: Int,
    val sizeBytes: Int,
    val text: String?,
    val bytes: ByteArray,
)

object OpenGisClient {
    val CRS_OPTIONS = listOf(
        "EPSG:4326" to "WGS 84 (EPSG:4326 · GPS chuẩn)",
        "EPSG:32648" to "UTM Zone 48N (EPSG:32648 · Tây Nguyên/Nam Bộ, mét)",
        "EPSG:3405" to "VN-2000 / UTM 48N (EPSG:3405 · Việt Nam, mét)",
        "EPSG:3857" to "Web Mercator (EPSG:3857 · Bản đồ Web)",
    )

    val VECTOR_FORMATS = listOf(
        "shp" to "ESRI Shapefile (.shp.zip)",
        "gpkg" to "OGC GeoPackage (.gpkg)",
        "geojson" to "GeoJSON (.geojson)",
        "fgb" to "FlatGeobuf (.fgb)",
        "kml" to "Google Earth KML (.kml)",
        "gpx" to "GPS Exchange (.gpx)",
        "csv" to "CSV + WKT (.csv)",
        "postgis" to "PostGIS SQL Dump (.sql)",
    )

    @android.annotation.SuppressLint("NewApi")
    private fun decodeB64(b64: String): ByteArray =
        runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull()
            ?: java.util.Base64.getDecoder().decode(b64)

    private fun ringJson(ring: List<GeoPoint>): JSONArray = JSONArray().also { arr ->
        val pts = Geo.open(ring)
        if (pts.isNotEmpty()) {
            (pts + pts.first()).forEach { p -> arr.put(JSONArray().put(p.lng).put(p.lat)) }
        }
    }

    private fun request(cfg: SatelliteConfig, method: String, path: String, body: JSONObject? = null): JSONObject {
        SyncClient.urlProblem(cfg.url)?.let { throw GisException(it) }
        if (!cfg.configured) throw GisException("Chưa nhập địa chỉ và mã truy cập máy chủ vệ tinh.")
        val payload = body?.toString()?.toByteArray()
        val headers = if (payload != null) mapOf("Content-Type" to "application/json") else emptyMap()
        val r = try {
            var lastErr: IOException? = null
            var resp: ApiResp? = null
            for (base in SatelliteClient.candidateUrls(cfg.url)) {
                try {
                    resp = Api.call(base, cfg.token, method, path, payload, headers)
                    break
                } catch (e: IOException) {
                    lastErr = e
                }
            }
            resp ?: throw (lastErr ?: IOException("Không kết nối được máy chủ."))
        } catch (e: IOException) {
            throw GisException("Không kết nối được máy chủ: ${e.message ?: e.javaClass.simpleName}")
        }
        if (r.code in 200..299) return r.json ?: throw GisException("Phản hồi của máy chủ không đọc được.")
        val detail = r.json?.opt("detail")
        throw GisException((detail as? String) ?: "Máy chủ trả lỗi ${r.code}.")
    }

    fun searchGadm(cfg: SatelliteConfig, country: String = "VNM", level: Int = 1, query: String = ""): List<GadmItem> {
        val qEnc = URLEncoder.encode(query, "UTF-8")
        val o = request(cfg, "GET", "/v1/open-gis/gadm/names?country=$country&level=$level&q=$qEnc&limit=25")
        val arr = o.optJSONArray("items") ?: JSONArray()
        return (0 until arr.length()).map { i ->
            val it = arr.getJSONObject(i)
            GadmItem(
                gid = it.optString("gid"),
                name = it.optString("name"),
                parent = it.optString("parent"),
                level = it.optInt("level", level),
                country = it.optString("country", country),
            )
        }
    }

    fun fetchGadmBoundary(
        cfg: SatelliteConfig,
        country: String = "VNM",
        level: Int = 2,
        gid: String = "",
        name: String = "",
        ring: List<GeoPoint>? = null,
    ): GadmBoundaryResult {
        val body = JSONObject().put("country", country).put("level", level).put("gid", gid).put("name", name)
        if (!ring.isNullOrEmpty()) body.put("ring", ringJson(ring))
        val o = request(cfg, "POST", "/v1/open-gis/gadm/boundary", body)
        val adm = o.optJSONObject("admin") ?: JSONObject()
        return GadmBoundaryResult(
            gid0 = adm.optString("gid_0", country),
            name0 = adm.optString("name_0", "Việt Nam"),
            gid1 = adm.optString("gid_1"),
            name1 = adm.optString("name_1"),
            gid2 = adm.optString("gid_2"),
            name2 = adm.optString("name_2"),
            level = adm.optInt("level", level),
            areaHa = adm.optDouble("area_ha", 0.0),
            features = o.optInt("features", 1),
            geojson = (o.optJSONObject("geojson") ?: JSONObject()).toString(),
            source = o.optString("source", "GADM v4.1"),
        )
    }

    fun fetchGeodataEnv(cfg: SatelliteConfig, ring: List<GeoPoint>?): GeodataEnvResult {
        val body = JSONObject()
        if (!ring.isNullOrEmpty()) body.put("ring", ringJson(ring))
        val o = request(cfg, "POST", "/v1/open-gis/geodata/env", body)
        val sg = o.optJSONObject("soil_grids") ?: JSONObject()
        val wc = o.optJSONObject("worldclim") ?: JSONObject()
        val keys = listOf("phh2o", "soc", "clay", "sand", "silt", "nitrogen", "cec", "bdod")
        val props = keys.mapNotNull { k ->
            val p = sg.optJSONObject(k) ?: return@mapNotNull null
            val vals = p.optJSONObject("values") ?: JSONObject()
            SoilDepthProperty(
                key = k,
                label = p.optString("label", k),
                unit = p.optString("unit"),
                d0to5 = vals.optDouble("0-5cm", 0.0),
                d5to15 = vals.optDouble("5-15cm", 0.0),
                d15to30 = vals.optDouble("15-30cm", 0.0),
            )
        }
        val recs = o.optJSONArray("recommendations") ?: JSONArray()
        return GeodataEnvResult(
            wrbClass = sg.optString("wrb_class", "Ferralsols"),
            soilProperties = props,
            tavgC = wc.optDouble("tavg_c", 24.5),
            tminC = wc.optDouble("tmin_c", 19.0),
            tmaxC = wc.optDouble("tmax_c", 30.2),
            annualPrecipMm = wc.optDouble("annual_precip_mm", 1850.0),
            wetSeasonPrecipMm = wc.optDouble("wet_season_precip_mm", 1520.0),
            drySeasonPrecipMm = wc.optDouble("dry_season_precip_mm", 330.0),
            solarRadMjM2Day = wc.optDouble("solar_rad_mj_m2_day", 18.4),
            elevationM = wc.optDouble("elevation_m", 480.0),
            recommendations = (0 until recs.length()).map { recs.getString(it) },
            source = o.optString("source", "rspatial/geodata · SoilGrids & WorldClim"),
        )
    }

    fun extractOsm(cfg: SatelliteConfig, ring: List<GeoPoint>?, bufferM: Double = 500.0): OsmExtractResult {
        val body = JSONObject().put("buffer_m", bufferM)
        if (!ring.isNullOrEmpty()) body.put("ring", ringJson(ring))
        val o = request(cfg, "POST", "/v1/open-gis/osm/extract", body)
        val counts = o.optJSONObject("counts") ?: JSONObject()
        return OsmExtractResult(
            features = o.optInt("features", 0),
            roadsCount = counts.optInt("roads", 0),
            waterCount = counts.optInt("water", 0),
            buildingsCount = counts.optInt("buildings", 0),
            landuseCount = counts.optInt("landuse", 0),
            roadLengthKm = o.optDouble("road_length_km", 0.0),
            waterwayLengthKm = o.optDouble("waterway_length_km", 0.0),
            overpassQl = o.optString("overpass_ql"),
            simulated = o.optBoolean("simulated", false),
            geojson = (o.optJSONObject("geojson") ?: JSONObject()).toString(),
            source = o.optString("source", "OSMnx & Overpass Turbo"),
        )
    }

    fun overpassQuery(cfg: SatelliteConfig, query: String, ring: List<GeoPoint>?): OsmExtractResult {
        val body = JSONObject().put("query", query)
        if (!ring.isNullOrEmpty()) body.put("ring", ringJson(ring))
        val o = request(cfg, "POST", "/v1/open-gis/osm/overpass", body)
        val gj = o.optJSONObject("geojson") ?: JSONObject()
        val feats = gj.optJSONArray("features")?.length() ?: 0
        return OsmExtractResult(
            features = feats,
            roadsCount = 0,
            waterCount = 0,
            buildingsCount = 0,
            landuseCount = 0,
            roadLengthKm = 0.0,
            waterwayLengthKm = 0.0,
            overpassQl = o.optString("query", query),
            simulated = o.optBoolean("simulated", false),
            geojson = gj.toString(),
            source = o.optString("source", "Overpass Turbo & osmtogeojson"),
        )
    }

    fun searchHdx(cfg: SatelliteConfig, query: String = "", country: String = "vnm"): List<HdxDataset> {
        val qEnc = URLEncoder.encode(query, "UTF-8")
        val o = request(cfg, "GET", "/v1/open-gis/hdx/search?q=$qEnc&country=$country&limit=8")
        val arr = o.optJSONArray("datasets") ?: JSONArray()
        return (0 until arr.length()).map { i ->
            val d = arr.getJSONObject(i)
            val fmts = d.optJSONArray("formats") ?: JSONArray()
            val resArr = d.optJSONArray("resources") ?: JSONArray()
            HdxDataset(
                id = d.optString("id"),
                name = d.optString("name"),
                title = d.optString("title"),
                organization = d.optString("organization"),
                datasetDate = d.optString("dataset_date"),
                formats = (0 until fmts.length()).map { fmts.getString(it) },
                resources = (0 until resArr.length()).map { j ->
                    val r = resArr.getJSONObject(j)
                    HdxResource(r.optString("name"), r.optString("format"), r.optString("url"))
                },
            )
        }
    }

    fun fetchHdxLayer(cfg: SatelliteConfig, datasetId: String, ring: List<GeoPoint>?): HdxFetchResult {
        val body = JSONObject().put("dataset_id", datasetId)
        if (!ring.isNullOrEmpty()) body.put("ring", ringJson(ring))
        val o = request(cfg, "POST", "/v1/open-gis/hdx/fetch", body)
        return HdxFetchResult(
            datasetId = o.optString("dataset_id", datasetId),
            title = o.optString("title", datasetId),
            features = o.optInt("features", 0),
            geojson = (o.optJSONObject("geojson") ?: JSONObject()).toString(),
            source = o.optString("source", "HDX CKAN API"),
        )
    }

    fun fetchWorldBank(cfg: SatelliteConfig, country: String = "VNM", ring: List<GeoPoint>? = null): WbJoinResult {
        val body = JSONObject().put("country", country)
        if (!ring.isNullOrEmpty()) body.put("ring", ringJson(ring))
        val o = request(cfg, "POST", "/v1/open-gis/worldbank/join", body)
        val arr = o.optJSONArray("indicators") ?: JSONArray()
        val list = (0 until arr.length()).map { i ->
            val it = arr.getJSONObject(i)
            WbIndicator(
                code = it.optString("code"),
                label = it.optString("label"),
                unit = it.optString("unit"),
                latestYear = it.optInt("latest_year", 2023),
                latestValue = it.optDouble("latest_value", 0.0),
            )
        }
        return WbJoinResult(
            country = o.optString("country", country),
            indicators = list,
            joinedGeojson = o.optJSONObject("joined_geojson")?.toString(),
            source = o.optString("source", "World Bank wbgapi"),
        )
    }

    fun convertVector(
        cfg: SatelliteConfig,
        geojson: String,
        format: String,
        targetCrs: String = "EPSG:4326",
        name: String = "VWing",
    ): VectorConvertResult {
        val body = JSONObject()
            .put("geojson", JSONObject(geojson))
            .put("format", format)
            .put("target_crs", targetCrs)
            .put("name", name)
        val o = request(cfg, "POST", "/v1/open-gis/vector/convert", body)
        val b64 = o.optString("data_base64", "")
        val txt = if (o.isNull("text")) null else o.optString("text")
        val raw = if (b64.isNotBlank()) decodeB64(b64) else (txt?.toByteArray() ?: ByteArray(0))
        return VectorConvertResult(
            filename = o.optString("filename", "$name.$format"),
            mime = o.optString("mime", "application/octet-stream"),
            format = o.optString("format", format),
            crs = o.optString("crs", targetCrs),
            features = o.optInt("features", 0),
            sizeBytes = o.optInt("size_bytes", raw.size),
            text = txt,
            bytes = raw,
        )
    }
}
