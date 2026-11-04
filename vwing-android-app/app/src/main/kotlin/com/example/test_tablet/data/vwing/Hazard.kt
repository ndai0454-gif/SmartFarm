package com.example.test_tablet.data.vwing

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale

data class HazardItem(val id: String, val label: String, val level: Int, val levelLabel: String, val riskIndex: Double,
                      val reasons: List<String>, val advice: List<String>)
data class HazardRain(val next24: Double, val next48: Double, val next72: Double, val past72: Double,
                      val maxHourly: Double, val soilMoisture: Double?)
data class HazardOutlook(val fetchedAt: String, val stale: Boolean, val asOf: String, val overallLevel: Int,
                         val headline: String, val hazards: List<HazardItem>, val rain: HazardRain,
                         val lowLying: Boolean?, val elevationM: Double?, val method: String, val warnings: List<String>)

/** 3-day rule-based hazard outlook from the backend (/v1/hazards/outlook); levels 0 (normal) .. 3 (danger). */
object HazardClient {
    private fun strings(o: JSONObject, key: String): List<String> =
        o.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()

    fun parse(text: String): HazardOutlook {
        val root = JSONObject(text)
        val items = root.getJSONArray("hazards").let { rows -> (0 until minOf(rows.length(), 12)).map { i ->
            val o = rows.getJSONObject(i)
            val level = o.getInt("level"); require(level in 0..3)
            val risk = o.getDouble("risk_index"); require(risk.isFinite() && risk in 0.0..1.0)
            HazardItem(o.getString("id"), o.getString("label_vi"), level, o.getString("level_vi"), risk,
                strings(o, "reasons"), strings(o, "advice"))
        } }
        require(items.isNotEmpty())
        val r = root.getJSONObject("rain")
        fun num(key: String) = r.getDouble(key).also { require(it.isFinite() && it >= 0) }
        val rain = HazardRain(num("next_24h_mm"), num("next_48h_mm"), num("next_72h_mm"), num("past_72h_mm"),
            num("max_hourly_mm_24h"), if (r.isNull("soil_moisture_0_7cm")) null else r.getDouble("soil_moisture_0_7cm"))
        val terrain = root.optJSONObject("terrain")
        val overall = root.getInt("overall_level"); require(overall in 0..3 && overall == items.maxOf { it.level })
        return HazardOutlook(root.getString("fetched_at"), root.optBoolean("stale"), root.getString("as_of"), overall,
            root.getString("headline"), items, rain, terrain?.optBoolean("low_lying"),
            terrain?.let { if (it.isNull("elevation_m")) null else it.getDouble("elevation_m") },
            root.optString("method"), strings(root, "warnings"))
    }

    private fun cacheFile(dir: File, p: GeoPoint): File {
        require(p.lat.isFinite() && p.lng.isFinite() && p.lat in -90.0..90.0 && p.lng in -180.0..180.0)
        return File(dir, "vwing_hazard_${String.format(Locale.US, "%.3f_%.3f", p.lat, p.lng)}.json")
    }

    fun loadCache(dir: File, p: GeoPoint): HazardOutlook? = runCatching {
        val f = cacheFile(dir, p); require(f.length() in 1..1_048_576)
        parse(f.readText()).copy(stale = true)
    }.getOrNull()

    fun refresh(cfg: SatelliteConfig, dir: File, p: GeoPoint): Result<HazardOutlook> = runCatching {
        val file = cacheFile(dir, p)
        SyncClient.urlProblem(cfg.url)?.let { throw IOException(it) }
        require(cfg.configured) { "Chưa kết nối máy chủ trong Thiết lập → Vệ tinh & GIS." }
        var last: Exception? = null
        for (base in SatelliteClient.candidateUrls(cfg.url)) {
            try {
                val path = "/v1/hazards/outlook?latitude=${p.lat}&longitude=${p.lng}"
                val conn = URI(base.trimEnd('/') + path).toURL().openConnection() as HttpURLConnection
                val body = try {
                    conn.connectTimeout = 8_000; conn.readTimeout = 25_000; conn.instanceFollowRedirects = false
                    conn.setRequestProperty("Authorization", "Bearer ${cfg.token}")
                    if (conn.responseCode != 200) throw IOException(when (conn.responseCode) {
                        401 -> "Mã truy cập máy chủ không đúng. Kiểm tra Thiết lập."
                        404 -> "Máy chủ chưa có cảnh báo thiên tai. Cần cập nhật backend."
                        in 300..399 -> "Đã dừng chuyển hướng máy chủ để bảo vệ mã truy cập."
                        else -> "Chưa tính được cảnh báo thiên tai (HTTP ${conn.responseCode})."
                    })
                    conn.inputStream.use { it.readBytes().also { b -> if (b.size > 1_048_576) throw IOException("Dữ liệu cảnh báo quá lớn.") }.toString(Charsets.UTF_8) }
                } finally { conn.disconnect() }
                val outlook = parse(body)
                val tmp = File(file.path + ".tmp"); tmp.writeText(body); check(tmp.renameTo(file))
                return@runCatching outlook
            } catch (e: java.net.ConnectException) { last = e }
            catch (e: java.net.SocketTimeoutException) { last = e }
            catch (e: java.net.UnknownHostException) { last = e }
        }
        throw IOException("Không kết nối được máy chủ. Kiểm tra Wi-Fi.", last)
    }
}
