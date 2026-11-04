package com.example.test_tablet.data.vwing

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Công cụ GIS chạy trên máy chủ vệ tinh bằng engine RustGIS (vwing-satellite-backend/app/gis_tools.py): nhập KMZ/GPX/Shapefile(zip)/
 * GeoPackage/CSV, xuất KML/GPX/CSV/WKT, phân tích ranh vườn và thống kê raster theo ranh. Cần mạng và cấu hình máy chủ vệ tinh.
 */
class GisException(message: String) : Exception(message)

data class GisLayer(val name: String, val type: String?, val features: Int, val geojson: String)
data class GisImport(val layers: List<GisLayer>, val warnings: List<String>)
data class GisExport(val text: String, val filename: String, val mime: String, val bytes: ByteArray? = null)
data class GisBuffer(val meters: Double, val ring: List<GeoPoint>, val areaHa: Double)
data class GisBoundary(
    val valid: Boolean, val issues: List<String>, val areaHa: Double, val perimeterM: Double, val vertices: Int,
    val buffers: List<GisBuffer>, val simplified: List<GeoPoint>?,
)
data class GisZonal(val label: String, val mean: Double?, val pixels: Int, val areaHa: Double, val coveragePct: Double?, val crs: String?, val bands: Int)

object GisClient {
    const val MAX_BYTES = 15 * 1024 * 1024
    /** Định dạng nhập mà máy chủ đọc được (KML/GeoJSON nhỏ vẫn đọc ngay trên máy). */
    val IMPORT_EXT = setOf("kml", "kmz", "gpx", "zip", "geojson", "json", "gpkg", "csv", "osm", "fgb")
    val EXPORT = listOf(
        "shp" to "Shapefile (.shp.zip)",
        "gpkg" to "GeoPackage (.gpkg)",
        "kml" to "KML (Google Earth)",
        "gpx" to "GPX (GPS)",
        "csv" to "CSV (bảng tính)",
        "wkt" to "WKT",
        "geojson" to "GeoJSON",
        "postgis" to "PostGIS (.sql)",
    )
    val RASTER_EXT = setOf("tif", "tiff", "asc")

    /** Mã hoá base64; trên JVM kiểm thử (android.util.Base64 là bản giả trả null) dùng java.util.Base64. */
    @android.annotation.SuppressLint("NewApi")
    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP) ?: java.util.Base64.getEncoder().encodeToString(bytes)

    fun extOf(name: String) = name.substringAfterLast('.', "").lowercase()

    /** Tên tệp mà máy chủ chấp nhận (chữ, số, . _ - và khoảng trắng). */
    fun safeName(name: String): String = name.replace(Regex("[^A-Za-z0-9._\\- ]"), "_").take(80).ifBlank { "tep" }

    internal fun call(cfg: SatelliteConfig, path: String, body: JSONObject): JSONObject {
        SyncClient.urlProblem(cfg.url)?.let { throw GisException(it) }
        if (!cfg.configured) throw GisException("Chưa nhập địa chỉ và mã truy cập máy chủ vệ tinh (mục Vệ tinh → Kết nối).")
        val payload = body.toString().toByteArray()
        val headers = mapOf("Content-Type" to "application/json")
        val r = try {
            var lastErr: IOException? = null
            var resp: ApiResp? = null
            for (base in SatelliteClient.candidateUrls(cfg.url)) {
                try {
                    resp = Api.call(base, cfg.token, "POST", path, payload, headers)
                    break
                } catch (e: IOException) {
                    lastErr = e
                }
            }
            resp ?: throw (lastErr ?: IOException("Không kết nối được máy chủ."))
        } catch (e: IOException) { throw GisException("Không kết nối được máy chủ: ${e.message ?: e.javaClass.simpleName}") }
        if (r.code in 200..299) return r.json ?: throw GisException("Phản hồi của máy chủ không đọc được.")
        val detail = r.json?.opt("detail")
        val msg = when {
            r.code == 401 -> "Sai mã truy cập máy chủ vệ tinh."
            r.code == 503 -> (detail as? String) ?: "Máy chủ chưa cài engine RustGIS."
            detail is String -> detail
            detail is JSONArray -> "Dữ liệu gửi lên không hợp lệ."
            else -> "Máy chủ trả lỗi ${r.code}."
        }
        throw GisException(msg)
    }

    fun importFile(cfg: SatelliteConfig, filename: String, bytes: ByteArray): GisImport {
        if (bytes.size > MAX_BYTES) throw GisException("Tệp lớn hơn 15 MB.")
        if (extOf(filename) !in IMPORT_EXT) throw GisException("Định dạng chưa hỗ trợ (dùng: ${IMPORT_EXT.sorted().joinToString(", ")}).")
        val o = call(cfg, "/v1/gis/import", JSONObject().put("filename", safeName(filename)).put("data_base64", b64(bytes)))
        val a = o.optJSONArray("layers") ?: JSONArray()
        val w = o.optJSONArray("warnings") ?: JSONArray()
        return GisImport(
            (0 until a.length()).map { a.getJSONObject(it).let { l -> GisLayer(l.optString("name"), l.optString("type").ifBlank { null }, l.optInt("features"), l.getJSONObject("geojson").toString()) } },
            (0 until w.length()).map { w.getString(it) },
        )
    }

    /** Gộp các lớp thành một FeatureCollection để đưa vào luồng nhập sẵn có (Geo.parse). */
    fun mergeLayers(layers: List<GisLayer>): String {
        val feats = JSONArray()
        layers.forEach { l ->
            val fc = JSONObject(l.geojson).optJSONArray("features") ?: return@forEach
            for (i in 0 until fc.length()) feats.put(fc.getJSONObject(i))
        }
        return JSONObject().put("type", "FeatureCollection").put("features", feats).toString()
    }

    fun export(cfg: SatelliteConfig, geojson: String, format: String, name: String): GisExport {
        val o = call(cfg, "/v1/gis/export", JSONObject().put("geojson", JSONObject(geojson)).put("format", format).put("name", name))
        val b64 = o.optString("data_base64", "")
        val raw = if (b64.isNotBlank()) {
            runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull() ?: java.util.Base64.getDecoder().decode(b64)
        } else null
        return GisExport(o.optString("text", ""), o.getString("filename"), o.optString("mime", "text/plain"), raw)
    }

    private fun ringOf(a: JSONArray) = (0 until a.length()).map { a.getJSONArray(it).let { p -> GeoPoint(p.getDouble(1), p.getDouble(0)) } }
    private fun ringJson(ring: List<GeoPoint>) = JSONArray().also { a -> Geo.open(ring).let { it + it.first() }.forEach { a.put(JSONArray().put(it.lng).put(it.lat)) } }

    fun boundary(cfg: SatelliteConfig, ring: List<GeoPoint>, buffersM: List<Double>, simplifyM: Double?): GisBoundary {
        val b = JSONObject().put("ring", ringJson(ring)).put("buffers_m", JSONArray(buffersM))
        if (simplifyM != null) b.put("simplify_m", simplifyM)
        val o = call(cfg, "/v1/gis/boundary", b)
        val iss = o.optJSONArray("issues") ?: JSONArray()
        val bufs = o.optJSONObject("buffers")?.let { bo -> bo.keys().asSequence().map { k -> bo.getJSONObject(k).let { v -> GisBuffer(k.toDouble(), ringOf(v.getJSONArray("ring")), v.getDouble("area_ha")) } }.sortedBy { it.meters }.toList() } ?: emptyList()
        return GisBoundary(o.optBoolean("valid"), (0 until iss.length()).map { iss.getString(it) }, o.optDouble("area_ha"), o.optDouble("perimeter_m"), o.optInt("vertices"),
            bufs, o.optJSONObject("simplified")?.let { ringOf(it.getJSONArray("ring")) })
    }

    /** GeoJSON của các vùng (đa giác) để gửi cho công cụ xử lý không gian; toạ độ [kinh độ, vĩ độ], vòng khép kín. */
    fun polygonsGeoJson(features: List<GeoFeature>): String {
        val arr = JSONArray()
        features.filter { Geo.open(it.points).size >= 3 }.forEach { f ->
            arr.put(JSONObject().put("type", "Feature").put("properties", JSONObject().put("name", f.name))
                .put("geometry", JSONObject().put("type", "Polygon").put("coordinates", JSONArray().put(ringJson(f.points)))))
        }
        return JSONObject().put("type", "FeatureCollection").put("features", arr).toString()
    }

    /** Phép toán giữa hai vùng. clip = phần chung, erase = A trừ B, dissolve = gộp cả hai. Trả các đa giác kết quả. */
    enum class Overlay(val label: String, val tool: String) {
        CLIP("Phần chung (cắt A theo B)", "clip"), ERASE("A trừ B (khoét)", "erase"), MERGE("Gộp A và B", "dissolve")
    }

    fun overlay(cfg: SatelliteConfig, op: Overlay, a: GeoFeature, b: GeoFeature): List<List<GeoPoint>> {
        val inputs = JSONObject()
        val params = JSONObject()
        when (op) {
            Overlay.CLIP -> { inputs.put("A", JSONObject(polygonsGeoJson(listOf(a)))).put("B", JSONObject(polygonsGeoJson(listOf(b)))); params.put("input", "A").put("clip", "B") }
            Overlay.ERASE -> { inputs.put("A", JSONObject(polygonsGeoJson(listOf(a)))).put("B", JSONObject(polygonsGeoJson(listOf(b)))); params.put("input", "A").put("erase", "B") }
            Overlay.MERGE -> { inputs.put("Both", JSONObject(polygonsGeoJson(listOf(a, b)))); params.put("input", "Both") }
        }
        val o = call(cfg, "/v1/gis/tool", JSONObject().put("tool", op.tool).put("inputs", inputs).put("params", params))
        val parsed = Geo.parseGeoJson(o.getJSONObject("geojson").toString())
        return parsed.features.filter { it.geom == Geom.POLYGON && Geo.open(it.points).size >= 3 }.map { Geo.open(it.points) }
    }

    fun zonal(cfg: SatelliteConfig, filename: String, bytes: ByteArray, ring: List<GeoPoint>, index: String?): GisZonal {
        if (bytes.size > MAX_BYTES) throw GisException("Tệp lớn hơn 15 MB.")
        if (extOf(filename) !in RASTER_EXT) throw GisException("Chỉ nhận raster .tif, .tiff hoặc .asc.")
        val b = JSONObject().put("filename", safeName(filename)).put("data_base64", b64(bytes)).put("ring", ringJson(ring))
        if (index != null) b.put("index", index)
        val o = call(cfg, "/v1/gis/raster/zonal", b)
        val r = o.optJSONObject("raster")
        return GisZonal(o.optString("label"), if (o.isNull("mean")) null else o.getDouble("mean"), o.optInt("pixels"), o.optDouble("area_ha"),
            if (o.has("coverage_pct")) o.getDouble("coverage_pct") else null, r?.optString("crs"), r?.optInt("bands") ?: 0)
    }
}
