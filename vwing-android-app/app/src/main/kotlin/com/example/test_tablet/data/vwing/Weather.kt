package com.example.test_tablet.data.vwing

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale

data class WeatherNow(val time: String, val temperature: Double, val feels: Double, val humidity: Double,
                      val wind: Double, val precipitation: Double, val code: Int, val isDay: Boolean)
data class WeatherHour(val time: String, val temperature: Double, val rainChance: Double?, val code: Int)
data class WeatherDay(val date: String, val low: Double, val high: Double, val rainChance: Double?,
                      val code: Int, val sunrise: String?, val sunset: String?, val uv: Double?)
data class FarmWeather(val fetchedAt: String, val stale: Boolean, val timezone: String,
                       val now: WeatherNow, val hours: List<WeatherHour>, val days: List<WeatherDay>)

object WeatherClient {
    fun condition(code: Int): String = when (code) {
        0 -> "Trời quang"; 1 -> "Ít mây"; 2 -> "Mây rải rác"; 3 -> "Nhiều mây"
        45, 48 -> "Sương mù"; 51, 53, 55, 56, 57 -> "Mưa phùn"
        61, 63, 65, 66, 67 -> "Có mưa"; 71, 73, 75, 77, 85, 86 -> "Có tuyết"
        80, 81, 82 -> "Mưa rào"; 95, 96, 99 -> "Dông"; else -> "Chưa xác định"
    }
    fun symbol(code: Int, isDay: Boolean = true): String = when (code) {
        0 -> if (isDay) "☀" else "☾"; 1, 2 -> if (isDay) "⛅" else "☁"
        3 -> "☁"; 45, 48 -> "≋"; 95, 96, 99 -> "⛈"; in 51..67, in 80..82 -> "🌧"
        in 71..77, 85, 86 -> "❄"; else -> "◌"
    }
    private fun number(o: JSONObject, field: String): Double {
        val value = o.getDouble(field); require(value.isFinite()) { "Dữ liệu thời tiết không hợp lệ." }; return value
    }
    private fun arrayNumber(o: JSONObject, field: String, index: Int): Double? {
        val a = o.optJSONArray(field) ?: return null
        return if (a.isNull(index)) null else a.optDouble(index, Double.NaN).takeIf { it.isFinite() }
    }
    fun parse(text: String): FarmWeather {
        val root = JSONObject(text); val cur = root.getJSONObject("current")
        val units = root.getJSONObject("current_units")
        require(units.getString("temperature_2m") == "°C" && units.getString("wind_speed_10m") == "km/h")
        val now = WeatherNow(cur.getString("time"), number(cur, "temperature_2m"), number(cur, "apparent_temperature"),
            number(cur, "relative_humidity_2m"), number(cur, "wind_speed_10m"), number(cur, "precipitation"),
            cur.getInt("weather_code"), cur.getInt("is_day") == 1)
        require(now.time.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}")))
        require(now.humidity in 0.0..100.0 && now.wind >= 0 && now.precipitation >= 0)
        val hourly = root.getJSONObject("hourly"); val ht = hourly.getJSONArray("time")
        val hours = (0 until minOf(ht.length(), 192)).mapNotNull { i ->
            val temp = arrayNumber(hourly, "temperature_2m", i) ?: return@mapNotNull null
            val code = arrayNumber(hourly, "weather_code", i) ?: return@mapNotNull null
            WeatherHour(ht.getString(i), temp, arrayNumber(hourly, "precipitation_probability", i), code.toInt())
        }
        val daily = root.getJSONObject("daily"); val dt = daily.getJSONArray("time")
        val days = (0 until minOf(dt.length(), 7)).mapNotNull { i ->
            val low = arrayNumber(daily, "temperature_2m_min", i) ?: return@mapNotNull null
            val high = arrayNumber(daily, "temperature_2m_max", i) ?: return@mapNotNull null
            val code = arrayNumber(daily, "weather_code", i) ?: return@mapNotNull null
            require(low <= high)
            WeatherDay(dt.getString(i), low, high, arrayNumber(daily, "precipitation_probability_max", i), code.toInt(),
                daily.optJSONArray("sunrise")?.optString(i)?.takeIf { it.isNotBlank() && it != "null" },
                daily.optJSONArray("sunset")?.optString(i)?.takeIf { it.isNotBlank() && it != "null" }, arrayNumber(daily, "uv_index_max", i))
        }
        require(days.isNotEmpty() && hours.isNotEmpty())
        return FarmWeather(root.getString("fetched_at"), root.optBoolean("stale"), root.getString("timezone"), now, hours, days)
    }
    fun gardenPoint(data: GardenData): GeoPoint? {
        val points = data.features.firstOrNull { it.kind == FeatureKind.BOUNDARY && it.points.size >= 3 }?.points
            ?: data.activeTrees.mapNotNull { it.pos }.takeIf { it.isNotEmpty() } ?: return null
        return Geo.centroid(Geo.open(points))?.takeIf { it.lat.isFinite() && it.lng.isFinite() && it.lat in -90.0..90.0 && it.lng in -180.0..180.0 }
    }
    private fun cacheFile(dir: File, point: GeoPoint): File {
        require(point.lat.isFinite() && point.lng.isFinite() && point.lat in -90.0..90.0 && point.lng in -180.0..180.0)
        return File(dir, "vwing_weather_${String.format(Locale.US, "%.3f_%.3f", point.lat, point.lng)}.json")
    }
    fun loadCache(dir: File, point: GeoPoint): FarmWeather? = runCatching {
        val f = cacheFile(dir, point); require(f.length() in 1..1_048_576)
        parse(f.readText()).copy(stale = true)
    }.getOrNull()
    fun refresh(cfg: SatelliteConfig, dir: File, point: GeoPoint): Result<FarmWeather> = runCatching {
        val file = cacheFile(dir, point)
        SyncClient.urlProblem(cfg.url)?.let { throw IOException(it) }
        require(cfg.configured) { "Chưa kết nối máy chủ trong Thiết lập → Vệ tinh & GIS." }
        var last: Exception? = null
        for (base in SatelliteClient.candidateUrls(cfg.url)) {
            try {
                val path = "/v1/weather/forecast?latitude=${point.lat}&longitude=${point.lng}"
                val conn = URI(base.trimEnd('/') + path).toURL().openConnection() as HttpURLConnection
                val body = try {
                    conn.connectTimeout = 8_000; conn.readTimeout = 20_000; conn.instanceFollowRedirects = false
                    conn.setRequestProperty("Authorization", "Bearer ${cfg.token}")
                    if (conn.responseCode != 200) throw IOException(when (conn.responseCode) {
                        401 -> "Mã truy cập máy chủ không đúng. Kiểm tra Thiết lập."
                        404 -> "Máy chủ chưa có thời tiết. Cần cập nhật backend."
                        in 300..399 -> "Đã dừng chuyển hướng máy chủ để bảo vệ mã truy cập."
                        else -> "Chưa lấy được thời tiết (HTTP ${conn.responseCode})."
                    })
                    conn.inputStream.use { input ->
                        val out = java.io.ByteArrayOutputStream(); val chunk = ByteArray(8192)
                        while (true) { val n = input.read(chunk); if (n < 0) break
                            if (out.size() + n > 1_048_576) throw IOException("Dữ liệu thời tiết quá lớn.")
                            out.write(chunk, 0, n)
                        }; out.toString("UTF-8")
                    }
                } finally { conn.disconnect() }
                val report = parse(body)
                val tmp = File(file.path + ".tmp"); tmp.writeText(body); check(tmp.renameTo(file))
                return@runCatching report
            } catch (e: java.net.ConnectException) { last = e }
            catch (e: java.net.SocketTimeoutException) { last = e }
            catch (e: java.net.UnknownHostException) { last = e }
        }
        throw IOException("Không kết nối được máy chủ thời tiết. Kiểm tra Wi-Fi.", last)
    }
}
