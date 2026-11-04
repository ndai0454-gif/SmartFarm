package com.example.test_tablet.data.vwing

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

/**
 * Máy khách cho API của web app.mapmycrop.com (đảo ngược từ JS công khai của trang, đọc 2026-10-29):
 * POST /auth/login (form) -> apikey; mọi GET gắn ?api_key=...; danh sách farm: farm/v1/;
 * dự báo bệnh: advisory/forecast_disease_advisory?farm_id=...
 *
 * Dùng tài khoản MapMyCrop CỦA CHÍNH người dùng (nhập trong app). Mô hình dự báo nằm ở máy chủ của họ
 * nên app chỉ hiển thị; không tự suy ra rủi ro bệnh. Mật khẩu không được lưu, chỉ lưu apikey.
 * Tên trường của /auth/login và farm/v1/ chưa kiểm chứng với tài khoản thật nên bộ đọc rất dung sai.
 */
data class MmcFarm(val id: String, val name: String)
data class MmcFarmPage(val farms: List<MmcFarm>, val hasNext: Boolean, val nextCursorCreatedAt: String?, val nextCursorId: String?)

/** Mức rủi ro của một bệnh trong một ngày; [percent] null = máy chủ không có dữ liệu ngày đó. */
data class RiskCell(val percent: Double?, val level: String?, val colorHex: String?)
data class DiseaseRow(val disease: String, val cells: Map<String, RiskCell>)
data class RiskLegend(val level: String, val colorHex: String)
data class DiseaseForecast(val dates: List<String>, val rows: List<DiseaseRow>, val legend: List<RiskLegend>)

class MmcAuthException(message: String) : Exception(message)

object MapMyCropClient {
    const val BASE = "https://api.mapmycrop.com"

    /** Web ẩn 2 ngày cuối của chuỗi (`slice(0,-2)`) vì dữ liệu cuối chuỗi chưa đủ; giữ cùng hành vi. */
    const val TAIL_DAYS_HIDDEN = 2

    private fun unwrap(text: String): JSONObject {
        val root = JSONObject(text)
        return root.optJSONObject("data")?.takeIf { root.has("data") && !root.has("daily_risk_analysis") && !root.has("farms") && !root.has("apikey") } ?: root
    }

    internal fun parseApiKey(text: String): String {
        val o = JSONObject(text)
        val candidates = listOf(o, o.optJSONObject("data"))
        for (c in candidates) {
            if (c == null) continue
            for (k in listOf("apikey", "api_key", "apiKey")) {
                val v = c.optString(k, "")
                if (v.isNotBlank() && v != "null") return v
            }
        }
        throw MmcAuthException("Đăng nhập không trả về khóa API.")
    }

    internal fun parseFarms(text: String): MmcFarmPage {
        val o = unwrap(text)
        val arr = o.optJSONArray("farms") ?: JSONArray()
        val farms = (0 until arr.length()).mapNotNull { i ->
            val f = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = f.opt("id")?.toString()?.takeIf { it.isNotBlank() && it != "null" } ?: return@mapNotNull null
            val name = listOf("name", "farm_name", "title").firstNotNullOfOrNull { k -> f.optString(k, "").takeIf { it.isNotBlank() && it != "null" } } ?: "Farm $id"
            MmcFarm(id, name)
        }
        val p = o.optJSONObject("pagination")
        val next = p?.opt("next_cursor")
        val cursor = (next as? JSONObject)
        return MmcFarmPage(
            farms,
            p?.optBoolean("has_next", false) ?: false,
            cursor?.optString("created_at", "")?.takeIf { it.isNotBlank() },
            cursor?.opt("id")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
        )
    }

    /** Chuyển `daily_risk_analysis` (ngày → danh sách bệnh) thành bảng bệnh × ngày như trang web. */
    fun parseForecast(text: String): DiseaseForecast {
        val days = unwrap(text).optJSONArray("daily_risk_analysis") ?: JSONArray()
        val dates = ArrayList<String>(); val titles = LinkedHashSet<String>()
        val byDate = HashMap<String, Map<String, RiskCell>>()
        val legend = LinkedHashMap<String, String>()
        for (i in 0 until days.length()) {
            val day = days.optJSONObject(i) ?: continue
            val date = day.optString("date", "").takeIf { it.isNotBlank() } ?: continue
            val adv = day.optJSONArray("advisories") ?: JSONArray()
            val cells = LinkedHashMap<String, RiskCell>()
            for (j in 0 until adv.length()) {
                val a = adv.optJSONObject(j) ?: continue
                val title = a.optString("advisory_title", "").takeIf { it.isNotBlank() } ?: continue
                val pct = if (a.isNull("risk_percentage")) null else a.optDouble("risk_percentage", Double.NaN).takeIf { it.isFinite() }
                val level = a.optString("risk_level", "").takeIf { it.isNotBlank() }
                val hex = a.optString("hex", "").takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) }
                titles += title; cells[title] = RiskCell(pct, level, hex)
                if (level != null && hex != null) legend.putIfAbsent(level, hex)
            }
            dates += date; byDate[date] = cells
        }
        val rows = titles.map { t -> DiseaseRow(t, dates.associateWith { d -> byDate[d]?.get(t) ?: RiskCell(null, null, null) }) }
        val order = listOf("high", "medium", "low")
        val sorted = legend.map { RiskLegend(it.key, it.value) }.sortedBy { order.indexOf(it.level.lowercase()).let { i -> if (i < 0) 3 else i } }
        return DiseaseForecast(dates, rows, sorted)
    }

    /** Tóm tắt cho người dùng: số bệnh ở mức cao trong các ngày đang hiển thị, và ngày sớm nhất. */
    fun highRiskSummary(f: DiseaseForecast): Pair<Int, String?> {
        val shown = f.dates.dropLast(TAIL_DAYS_HIDDEN)
        val hits = f.rows.mapNotNull { r -> shown.firstOrNull { d -> r.cells[d]?.level.equals("high", true) } }
        return hits.size to hits.minOrNull()
    }

    /** Web coi 406 là hết phiên; máy chủ còn trả 404 + USER_NOT_FOUND khi khóa API không còn hợp lệ (đã thử với khóa giả). */
    internal fun isSessionLost(code: Int, body: String) =
        code == 401 || code == 403 || code == 406 || (code == 404 && body.contains("USER_NOT_FOUND"))

    private fun form(vararg kv: Pair<String, String>) =
        kv.joinToString("&") { URLEncoder.encode(it.first, "UTF-8") + "=" + URLEncoder.encode(it.second, "UTF-8") }.toByteArray()

    private fun query(apiKey: String, params: Map<String, String>) =
        (params + ("api_key" to apiKey)).entries.joinToString("&", "?") { URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8") }

    private fun get(apiKey: String, path: String, params: Map<String, String> = emptyMap()): String {
        val r = Api.call(BASE, null, "GET", "/" + path + query(apiKey, params), headers = mapOf("Accept" to "application/json"))
        val text = String(r.body, Charsets.UTF_8)
        if (isSessionLost(r.code, text)) throw MmcAuthException("Phiên MapMyCrop hết hạn, hãy đăng nhập lại.")
        if (r.code !in 200..299) throw java.io.IOException("MapMyCrop trả mã ${r.code}.")
        return text
    }

    /** [username] là email, hoặc mã vùng + số điện thoại (ví dụ +84901234567) như ô đăng nhập của web. */
    fun login(username: String, password: String): String {
        val r = Api.call(BASE, null, "POST", "/auth/login", form("username" to username.trim(), "password" to password),
            mapOf("Content-Type" to "application/x-www-form-urlencoded", "Accept" to "application/json"))
        if (r.code in 400..403) throw MmcAuthException("Sai tài khoản hoặc mật khẩu MapMyCrop.")
        if (r.code !in 200..299) throw java.io.IOException("MapMyCrop trả mã ${r.code}.")
        return parseApiKey(String(r.body, Charsets.UTF_8))
    }

    fun farms(apiKey: String, search: String = "", cursorCreatedAt: String? = null, cursorId: String? = null): MmcFarmPage {
        val p = linkedMapOf("page_size" to "20")
        if (search.isNotBlank()) p["search"] = search.trim()
        if (cursorCreatedAt != null) p["cursor_created_at"] = cursorCreatedAt
        if (cursorId != null) p["cursor_id"] = cursorId
        return parseFarms(get(apiKey, "farm/v1/", p))
    }

    fun forecastRaw(apiKey: String, farmId: String): String = get(apiKey, "advisory/forecast_disease_advisory", mapOf("farm_id" to farmId))
}

/** Phiên đăng nhập MapMyCrop và bản dự báo gần nhất, lưu trong thư mục riêng của app (không lưu mật khẩu). */
class MmcStore(private val dir: File) {
    private val session = File(dir, "vwing_mmc_session.json")
    private fun cache(farmId: String) = File(dir, "vwing_mmc_forecast_${farmId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64)}.json")

    fun apiKey(): String? = try { JSONObject(session.readText()).optString("apiKey").takeIf { it.isNotBlank() } } catch (_: Exception) { null }
    fun saveApiKey(key: String) { session.writeText(JSONObject().put("apiKey", key).toString()) }
    fun signOut() { session.delete() }

    fun saveForecast(farmId: String, raw: String) { cache(farmId).writeText(JSONObject().put("fetchedAt", System.currentTimeMillis()).put("raw", raw).toString()) }
    fun loadForecast(farmId: String): Pair<Long, String>? = try {
        val o = JSONObject(cache(farmId).readText()); o.getLong("fetchedAt") to o.getString("raw")
    } catch (_: Exception) { null }
}
