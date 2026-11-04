package com.example.test_tablet.data.vwing

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class MarketSource(val id: String, val name: String, val url: String, val category: String,
                        val commodities: List<String>, val status: String, val note: String, val fetchedAt: String?, val stale: Boolean)
data class MarketQuote(val id: String, val commodity: String, val label: String, val market: String,
                       val price: Double, val unit: String, val change: Double?, val sourceId: String,
                       val sourceUrl: String, val asOf: String?, val asOfLabel: String, val kind: String, val stale: Boolean)
data class MarketNews(val title: String, val url: String, val sourceId: String, val publishedAt: String?)
data class MarketOverview(val fetchedAt: String, val stale: Boolean, val sources: List<MarketSource>,
                          val quotes: List<MarketQuote>, val news: List<MarketNews>)

object MarketClient {
    fun sourceDirectory(): MarketOverview {
        fun source(id: String, name: String, url: String, category: String, crops: List<String>) =
            MarketSource(id, name, url, category, crops, "link_only", "Chưa tải trạng thái nguồn. Mở website để xem trực tiếp.", null, false)
        return MarketOverview("", false, listOf(
            source("giacaphe", "Giacaphe.com", "https://giacaphe.com/gia-ca-phe-noi-dia/", "Giá trong nước", listOf("coffee")),
            source("giatieu", "Giatieu.com", "https://giatieu.com/", "Giá trong nước", listOf("pepper")),
            source("giacaosu", "Giacaosu.com", "https://giacaosu.com/", "Giá trong nước", listOf("rubber")),
            source("vfa", "VFA · Hiệp hội Lương thực Việt Nam", "https://vietnamrice.org.vn/", "Lúa gạo", listOf("rice")),
            source("congthuong", "Báo Công Thương", "https://congthuong.vn/thi-truong/nong-san", "Tin thị trường", listOf("all")),
            source("agromonitor", "AgroMonitor", "https://agromonitor.vn/", "Phân tích thị trường", listOf("all")),
            source("nongnghiep", "Báo Nông nghiệp và Môi trường", "https://nongnghiepmoitruong.vn/kinh-te-thi-truong/", "Tin thị trường", listOf("all")),
            source("investing", "Investing.com", "https://vn.investing.com/commodities/", "Giá quốc tế", listOf("coffee", "rubber", "cocoa", "sugar", "cotton")),
            source("tradingeconomics", "Trading Economics", "https://tradingeconomics.com/commodities", "Giá quốc tế", listOf("coffee", "rubber", "rice", "cocoa", "sugar", "cotton")),
            source("chocaphe", "Chocaphe.vn", "https://chocaphe.vn/", "Cà phê", listOf("coffee")),
            source("banggianongsan", "Banggianongsan.com", "https://banggianongsan.com/", "Nông sản trong nước", listOf("all")),
            source("chogia", "Chogia.vn", "https://chogia.vn/", "Tin giá thị trường", listOf("all"))
        ), emptyList(), emptyList())
    }
    private val domains = setOf("giacaphe.com", "giatieu.com", "giaitieu.com", "giacaosu.com", "vietnamrice.org.vn",
        "congthuong.vn", "agromonitor.vn", "nongnghiep.vn", "nongnghiepmoitruong.vn", "investing.com", "tradingeconomics.com",
        "chocaphe.vn", "banggianongsan.com", "chogia.vn")

    /** Website URLs are separate from the authenticated backend: never send its token to a publisher. */
    fun safeSourceUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && uri.userInfo == null && (uri.port == -1 || uri.port == 443) &&
            domains.any { uri.host?.lowercase(Locale.ROOT)?.let { host -> host == it || host.endsWith(".$it") } == true }
    }.getOrDefault(false)

    private fun optional(o: JSONObject, name: String): String? =
        if (o.isNull(name)) null else o.optString(name).trim().takeIf { it.isNotEmpty() }

    fun parse(text: String): MarketOverview {
        val root = JSONObject(text)
        val sources = root.getJSONArray("sources").let { rows -> (0 until minOf(rows.length(), 20)).map { i ->
            val o = rows.getJSONObject(i)
            require(safeSourceUrl(o.getString("url"))) { "Nguồn thị trường không hợp lệ." }
            MarketSource(o.getString("id"), o.getString("name"), o.getString("url"), o.optString("category"),
                o.optJSONArray("commodities")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                o.getString("status"), o.optString("note"), optional(o, "fetched_at"), o.optBoolean("stale"))
        } }
        require(sources.map { it.id }.distinct().size == sources.size)
        val quotes = root.getJSONArray("quotes").let { rows -> (0 until minOf(rows.length(), 200)).mapNotNull { i ->
            runCatching {
                val o = rows.getJSONObject(i)
                val price = o.getDouble("price")
                val change = if (o.isNull("change")) null else o.getDouble("change")
                val source = sources.firstOrNull { it.id == o.getString("source_id") } ?: error("Unknown source")
                require(price.isFinite() && price > 0 && (change == null || change.isFinite()))
                require(safeSourceUrl(o.getString("source_url")))
                require(URI(o.getString("source_url")).host == URI(source.url).host)
                val unit = o.getString("unit").trim(); require(unit.isNotEmpty())
                val kind = o.getString("kind"); require(kind in setOf("domestic", "futures", "international"))
                MarketQuote(o.getString("id"), o.getString("commodity"), o.getString("label"), o.getString("market"),
                    price, unit, change, source.id, o.getString("source_url"), optional(o, "as_of"),
                    o.optString("as_of_label", "Nguồn chưa ghi ngày giá"), kind, o.optBoolean("stale"))
            }.getOrNull()
        } }
        val news = root.optJSONArray("news")?.let { rows -> (0 until minOf(rows.length(), 30)).mapNotNull { i ->
            val o = rows.optJSONObject(i) ?: return@mapNotNull null
            val source = sources.firstOrNull { it.id == o.optString("source_id") } ?: return@mapNotNull null
            val url = o.optString("url")
            if (!safeSourceUrl(url) || URI(url).host != URI(source.url).host) null
            else MarketNews(o.optString("title"), url, source.id, optional(o, "published_at"))
        } } ?: emptyList()
        return MarketOverview(root.getString("fetched_at"), root.optBoolean("stale"), sources, quotes, news)
    }

    fun loadCache(dir: File): MarketOverview? = runCatching {
        val file = File(dir, "vwing_market_cache.json")
        require(file.length() in 1..1_048_576)
        parse(file.readText()).copy(stale = true)
    }.getOrNull()

    fun refresh(cfg: SatelliteConfig, dir: File): Result<MarketOverview> = runCatching {
        SyncClient.urlProblem(cfg.url)?.let { throw IOException(it) }
        if (!cfg.configured) throw IOException("Hãy kết nối máy chủ trong Thiết lập → Vệ tinh & GIS.")
        var last: Exception? = null
        for (base in SatelliteClient.candidateUrls(cfg.url)) {
            try {
                val conn = URI(base.trimEnd('/') + "/v1/market/overview").toURL().openConnection() as HttpURLConnection
                val body = try {
                    conn.connectTimeout = 8_000; conn.readTimeout = 45_000
                    conn.instanceFollowRedirects = false
                    conn.setRequestProperty("Authorization", "Bearer ${cfg.token}")
                    when (conn.responseCode) {
                        200 -> Unit
                        401 -> throw IOException("Mã truy cập máy chủ không đúng. Kiểm tra Thiết lập → Vệ tinh & GIS.")
                        404 -> throw IOException("Máy chủ chưa có bảng giá. Cần cập nhật backend VWing.")
                        in 300..399 -> throw IOException("Máy chủ chuyển hướng; đã dừng để bảo vệ mã truy cập.")
                        else -> throw IOException("Chưa lấy được bảng giá (HTTP ${conn.responseCode}).")
                    }
                    conn.inputStream.use { input ->
                        val out = java.io.ByteArrayOutputStream(); val chunk = ByteArray(8192)
                        while (true) { val n = input.read(chunk); if (n < 0) break
                            if (out.size() + n > 1_048_576) throw IOException("Bảng giá quá lớn.")
                            out.write(chunk, 0, n)
                        }
                        out.toString("UTF-8")
                    }
                } finally { conn.disconnect() }
                val data = parse(body)
                val tmp = File(dir, "vwing_market_cache.json.tmp"); tmp.writeText(body)
                check(tmp.renameTo(File(dir, "vwing_market_cache.json"))) { "Không lưu được bảng giá ngoại tuyến." }
                return@runCatching data
            } catch (e: java.net.ConnectException) { last = e }
            catch (e: java.net.SocketTimeoutException) { last = e }
            catch (e: java.net.UnknownHostException) { last = e }
        }
        throw IOException("Không kết nối được máy chủ bảng giá. Kiểm tra Wi-Fi và địa chỉ máy chủ.", last)
    }

    fun oldPrice(asOf: String?, now: Long = System.currentTimeMillis()): Boolean {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false; timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh") }
        val day = asOf?.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }?.let { runCatching { parser.parse(it)?.time }.getOrNull() } ?: return true
        return now - day > 3 * 86_400_000L || day > now + 86_400_000L
    }
}
