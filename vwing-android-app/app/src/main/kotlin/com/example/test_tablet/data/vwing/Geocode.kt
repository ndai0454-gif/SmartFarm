package com.example.test_tablet.data.vwing

import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * Tìm địa điểm / tra ngược toạ độ bằng Photon (dữ liệu OpenStreetMap) qua máy chủ vệ tinh (vwing-satellite-backend/app/geocode.py).
 * Dữ liệu: © người đóng góp OpenStreetMap — nơi nào hiện kết quả phải ghi nguồn ([ATTRIBUTION]).
 */
data class GeoPlace(val name: String, val label: String, val admin: String, val lat: Double, val lng: Double, val kind: String, val bbox: GeoBounds?)

object GeocodeClient {
    const val ATTRIBUTION = "© người đóng góp OpenStreetMap · Photon"

    private fun get(cfg: SatelliteConfig, path: String): JSONObject {
        SyncClient.urlProblem(cfg.url)?.let { throw GisException(it) }
        if (!cfg.configured) throw GisException("Chưa kết nối máy chủ vệ tinh (mục Vệ tinh → Kết nối).")
        val r = try { Api.call(cfg.url, cfg.token, "GET", path) } catch (e: IOException) { throw GisException("Không kết nối được máy chủ: ${e.message ?: e.javaClass.simpleName}") }
        if (r.code in 200..299) return r.json ?: throw GisException("Phản hồi không đọc được.")
        val detail = r.json?.opt("detail") as? String
        throw GisException(when (r.code) {
            401 -> "Sai mã truy cập máy chủ vệ tinh."
            429 -> "Tìm quá nhanh, đợi một chút."
            else -> detail ?: "Máy chủ trả lỗi ${r.code}."
        })
    }

    private fun placeOf(o: JSONObject): GeoPlace {
        val b = o.optJSONObject("bbox")
        return GeoPlace(o.optString("name"), o.optString("label"), o.optString("admin"), o.getDouble("lat"), o.getDouble("lng"), o.optString("kind"),
            b?.let { GeoBounds(it.getDouble("south"), it.getDouble("west"), it.getDouble("north"), it.getDouble("east")) })
    }

    fun search(cfg: SatelliteConfig, query: String, near: GeoPoint? = null, limit: Int = 6): List<GeoPlace> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        var path = "/v1/geo/search?q=" + URLEncoder.encode(q.take(100), "UTF-8") + "&limit=$limit"
        if (near != null) path += "&lat=${near.lat}&lng=${near.lng}"
        val a = get(cfg, path).optJSONArray("results") ?: return emptyList()
        return (0 until a.length()).map { placeOf(a.getJSONObject(it)) }
    }

    fun reverse(cfg: SatelliteConfig, p: GeoPoint): GeoPlace? {
        val o = get(cfg, "/v1/geo/reverse?lat=${p.lat}&lng=${p.lng}")
        return o.optJSONObject("result")?.let(::placeOf)
    }
}
