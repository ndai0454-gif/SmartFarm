package com.example.test_tablet.data.vwing

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max

data class RegionWorkflowIndex(val index: String, val mean: Double?, val p5: Double?, val p95: Double?, val validCoveragePct: Double?, val nominalPixelSizeM: Int)
data class RegionWorkflowPeriod(val start: String, val endExclusive: String, val sceneCount: Int, val sceneIds: List<String>, val validCoveragePct: Double?, val indices: List<RegionWorkflowIndex>)
data class RegionWorkflowChange(val index: String, val threshold: Double, val meanDelta: Double?, val commonValidCoveragePct: Double?, val commonValidAreaHa: Double?, val increaseHa: Double?, val stableHa: Double?, val decreaseHa: Double?, val nominalPixelSizeM: Int, val imageUrl: String?, val legend: SatLegend)
data class RegionWorkflowResult(val dataset: String, val source: String, val simulated: Boolean, val areaHa: Double, val bounds: GeoBounds, val baseline: RegionWorkflowPeriod, val comparison: RegionWorkflowPeriod, val change: RegionWorkflowChange, val method: String, val warnings: List<String>, val caution: String, val rawJson: String)

/** Chỉ số luôn được tính trên ảnh nguồn; ảnh Super-Resolution không phải dữ liệu đo mới. */
object RegionWorkflowClient {
    private const val MAX_BYTES = 1024 * 1024
    private fun invalid(message: String): Nothing = throw IOException(message)
    private fun date(value: String): Date {
        require(value.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false; timeZone = TimeZone.getTimeZone("UTC") }
        return requireNotNull(format.parse(value)).also { require(format.format(it) == value) }
    }

    fun inputProblem(b0: String, b1: String, c0: String, c1: String, maxCloud: Double = 80.0, threshold: Double = 0.1): String? {
        if (!maxCloud.isFinite() || maxCloud !in 0.0..100.0) return "Ngưỡng mây phải từ 0 đến 100%."
        if (!threshold.isFinite() || threshold !in 0.01..1.0) return "Ngưỡng biến động phải từ 0,01 đến 1."
        val dates = try { listOf(b0, b1, c0, c1).map(::date) }
        catch (_: Exception) { return "Ngày phải hợp lệ theo định dạng YYYY-MM-DD." }
        val (a, b, c, d) = dates
        if (!a.before(b) || !c.before(d)) return "Ngày kết thúc phải sau ngày bắt đầu (không gồm ngày kết thúc)."
        if (b.time - a.time > 93L * 86400000 || d.time - c.time > 93L * 86400000) return "Mỗi kỳ phân tích tối đa 93 ngày."
        if (c.before(b)) return "Kỳ so sánh phải bắt đầu từ ngày kết thúc kỳ nền trở đi."
        return null
    }

    private fun ringProblem(ring: List<GeoPoint>): String? {
        val points = Geo.open(ring)
        if (points.size < 3 || points.size > 5000 || points.distinct().size < 3) return "Ranh giới cần từ 3 đến 5.000 đỉnh hợp lệ."
        if (points.any { !it.lat.isFinite() || !it.lng.isFinite() || it.lat !in -80.0..84.0 || it.lng !in -180.0..180.0 }) return "Tọa độ ranh giới nằm ngoài phạm vi được hỗ trợ."
        if (points.maxOf { it.lng } - points.minOf { it.lng } > 180) return "Chưa hỗ trợ ranh giới qua kinh tuyến đổi ngày."
        if (Geo.polygonAreaHa(points) <= 0.0) return "Ranh giới không có diện tích hợp lệ."
        return null
    }

    fun analyze(cfg: SatelliteConfig, ring: List<GeoPoint>, b0: String, b1: String, c0: String, c1: String, maxCloud: Double = 80.0, threshold: Double = 0.1): Result<RegionWorkflowResult> = runCatching {
        inputProblem(b0, b1, c0, c1, maxCloud, threshold)?.let { invalid(it) }
        ringProblem(ring)?.let { invalid(it) }
        if (!cfg.configured) invalid("Chưa cấu hình máy chủ vệ tinh và mã truy cập.")
        SyncClient.urlProblem(cfg.url)?.let { invalid(it) }
        val uri = try { URI(cfg.url.trim()) } catch (_: Exception) { invalid("Địa chỉ máy chủ không hợp lệ.") }
        if (uri.host == null || uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null) invalid("Địa chỉ máy chủ không hợp lệ.")
        val body = JSONObject().put("region", JSONObject().put("ring", JSONArray().also { a -> ring.forEach { a.put(JSONArray().put(it.lng).put(it.lat)) } }))
            .put("baseline_start", b0).put("baseline_end", b1).put("comparison_start", c0).put("comparison_end", c1)
            .put("max_cloud_pct", maxCloud).put("threshold", threshold).toString().toByteArray(Charsets.UTF_8)
        var lastErr: IOException? = null
        var result: RegionWorkflowResult? = null
        for (base in SatelliteClient.candidateUrls(cfg.url)) {
            val connection = URI(base.trim().trimEnd('/') + "/v1/satellite/region-workflow").toURL().openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 8000; connection.readTimeout = 180000; connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", "Bearer ${cfg.token}")
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.doOutput = true; connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
                val code = connection.responseCode
                if (code in 300..399) invalid("Máy chủ chuyển hướng; hãy kiểm tra địa chỉ cấu hình.")
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val bytes = stream?.use { input ->
                    val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                    while (true) { val n = input.read(buffer); if (n < 0) break; if (output.size() + n > MAX_BYTES) invalid("Phản hồi phân tích vượt giới hạn 1 MB."); output.write(buffer, 0, n) }
                    output.toByteArray()
                } ?: ByteArray(0)
                if (code != 200) invalid(when (code) {
                    401, 403 -> "Mã truy cập máy chủ vệ tinh không hợp lệ hoặc không có quyền."
                    404 -> "Không tìm thấy dữ liệu hoặc máy chủ chưa hỗ trợ quy trình vùng."
                    422 -> "Yêu cầu phân tích không hợp lệ; kiểm tra ranh giới, ngày và ngưỡng."
                    429 -> "Máy chủ đang bận; vui lòng thử lại sau."
                    502, 503, 504 -> "Nguồn vệ tinh tạm thời chưa sẵn sàng; vui lòng thử lại sau."
                    else -> "Máy chủ trả lỗi $code khi phân tích vùng."
                })
                result = try { parse(JSONObject(bytes.toString(Charsets.UTF_8))) } catch (e: Exception) { invalid("Phản hồi phân tích không hợp lệ: ${e.message ?: "thiếu dữ liệu"}") }
                break
            } catch (e: java.net.ConnectException) {
                lastErr = e
            } catch (e: java.net.SocketTimeoutException) {
                lastErr = e
            } catch (e: java.net.UnknownHostException) {
                lastErr = e
            } finally { connection.disconnect() }
        }
        result ?: throw (lastErr ?: IOException("Không kết nối được máy chủ vệ tinh."))
    }

    private fun JSONObject.number(key: String, nullable: Boolean = false): Double? {
        if (!has(key)) invalid("Thiếu trường $key.")
        if (isNull(key)) { if (nullable) return null; invalid("Trường $key không được rỗng.") }
        val raw = get(key)
        if (raw !is Number || !raw.toDouble().isFinite()) invalid("Trường $key phải là số hữu hạn.")
        return raw.toDouble()
    }
    private fun JSONObject.text(key: String): String = (get(key) as? String)?.takeIf { it.isNotBlank() } ?: invalid("Trường $key phải là văn bản.")
    private fun JSONObject.count(key: String): Int { val n = number(key)!!; if (n < 0 || n > Int.MAX_VALUE || n % 1.0 != 0.0) invalid("Trường $key phải là số nguyên không âm."); return n.toInt() }
    private fun JSONObject.percent(key: String): Double? = number(key, true)?.also { if (it !in 0.0..100.0) invalid("Trường $key phải từ 0 đến 100.") }
    private fun strings(array: JSONArray): List<String> = (0 until array.length()).map { (array.get(it) as? String) ?: invalid("Danh sách văn bản không hợp lệ.") }

    private fun period(o: JSONObject): RegionWorkflowPeriod {
        val start = o.text("start"); val end = o.text("end_exclusive")
        try { if (!date(start).before(date(end))) invalid("Kỳ ảnh không hợp lệ.") } catch (_: Exception) { invalid("Ngày kỳ ảnh không hợp lệ.") }
        val count = o.count("scene_count"); val ids = strings(o.getJSONArray("scene_ids"))
        if (ids.size != count || ids.distinct().size != ids.size) invalid("Danh sách cảnh ảnh không khớp số cảnh.")
        val a = o.getJSONArray("indices")
        val indices = (0 until a.length()).map { i -> a.getJSONObject(i).let { j ->
            val index = j.text("index"); val pixel = j.count("nominal_pixel_size_m")
            if (index !in listOf("NDVI", "EVI", "NDMI") || pixel != if (index == "NDMI") 20 else 10) invalid("Chỉ số hoặc độ phân giải nguồn không hợp lệ.")
            val mean = j.number("mean", true); val p5 = j.number("p5", true); val p95 = j.number("p95", true)
            if (listOf(mean, p5, p95).any { it == null } && listOf(mean, p5, p95).any { it != null }) invalid("Thống kê chỉ số không đầy đủ.")
            if (p5 != null && p95 != null && p5 > p95) invalid("Phân vị chỉ số không hợp lệ.")
            RegionWorkflowIndex(index, mean, p5, p95, j.percent("valid_coverage_pct"), pixel)
        } }
        if (indices.map { it.index }.toSet() != setOf("NDVI", "EVI", "NDMI") || indices.size != 3) invalid("Cần đủ ba chỉ số NDVI, EVI và NDMI.")
        return RegionWorkflowPeriod(start, end, count, ids, o.percent("valid_coverage_pct"), indices)
    }

    fun parse(o: JSONObject): RegionWorkflowResult {
        val area = o.number("area_ha")!!; if (area <= 0) invalid("Diện tích vùng không hợp lệ.")
        val b = o.getJSONObject("bounds")
        val bounds = GeoBounds(b.number("south")!!, b.number("west")!!, b.number("north")!!, b.number("east")!!)
        if (bounds.south !in -90.0..90.0 || bounds.north !in -90.0..90.0 || bounds.west !in -180.0..180.0 || bounds.east !in -180.0..180.0 || bounds.south >= bounds.north || bounds.west >= bounds.east) invalid("Khung tọa độ không hợp lệ.")
        val baseline = period(o.getJSONObject("baseline")); val comparison = period(o.getJSONObject("comparison"))
        val c = o.getJSONObject("change"); val threshold = c.number("threshold")!!
        inputProblem(baseline.start, baseline.endExclusive, comparison.start, comparison.endExclusive, 80.0, threshold)?.let { invalid(it) }
        val changeIndex = c.text("index"); val pixel = c.count("nominal_pixel_size_m")
        if (changeIndex != "NDVI" || pixel != 10) invalid("Biến động phải dùng NDVI nguồn 10 m.")
        val delta = c.number("mean_delta", true); val coverage = c.percent("common_valid_coverage_pct")
        val common = c.number("common_valid_area_ha", true); val increase = c.number("increase_ha", true)
        val stable = c.number("stable_ha", true); val decrease = c.number("decrease_ha", true)
        // Diện tích raster đếm theo pixel có thể vượt diện tích hình học ở ranh giới vùng nhỏ.
        if (listOf(common, increase, stable, decrease).any { it != null && it < 0 }) invalid("Diện tích biến động không hợp lệ.")
        if (listOf(delta, common, increase, stable, decrease).any { it == null } && listOf(delta, common, increase, stable, decrease).any { it != null }) invalid("Thống kê biến động không đầy đủ.")
        if (common != null && increase != null && stable != null && decrease != null && kotlin.math.abs(increase + stable + decrease - common) > max(0.0003, common * 0.01)) invalid("Tổng diện tích biến động không khớp vùng hợp lệ chung.")
        if (!c.has("image_url")) invalid("Thiếu trường image_url.")
        val url = if (c.isNull("image_url")) null else c.text("image_url")
        if (delta == null && (coverage != 0.0 || url != null)) invalid("Vùng không có dữ liệu chung phải có độ phủ 0 và không có ảnh biến động.")
        val l = c.getJSONObject("legend"); val legend = SatLegend(l.number("min")!!, l.number("max")!!, strings(l.getJSONArray("palette")), l.text("label"))
        if (legend.min >= legend.max || legend.palette.size < 2 || legend.palette.any { !it.matches(Regex("[0-9a-fA-F]{6}")) }) invalid("Chú giải biến động không hợp lệ.")
        val simulated = o.get("simulated") as? Boolean ?: invalid("Thiếu nhãn dữ liệu mô phỏng.")
        return RegionWorkflowResult(o.text("dataset"), o.text("source"), simulated, area, bounds, baseline, comparison,
            RegionWorkflowChange(changeIndex, threshold, delta, coverage, common, increase, stable, decrease, pixel, url, legend),
            o.text("method"), strings(o.getJSONArray("warnings")), o.text("caution"), o.toString())
    }
}

/** Báo cáo độc lập, có thể chỉnh sửa HTML; không tải ảnh từ mạng hoặc đưa mã truy cập vào tệp. */
object RegionWorkflowReport {
    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
    private fun n(value: Double?, decimals: Int = 3) = value?.let { String.format(Locale.US, "%.${decimals}f", it) } ?: "Chưa có dữ liệu"
    private fun coordinates(ring: List<GeoPoint>) = JSONArray().also { a -> ring.forEach { a.put(JSONArray().put(it.lng).put(it.lat)) } }
    fun json(result: RegionWorkflowResult, regionName: String, ring: List<GeoPoint>): String {
        fun putNumber(o: JSONObject, key: String, value: Double?) { o.put(key, value ?: JSONObject.NULL) }
        fun period(p: RegionWorkflowPeriod) = JSONObject().put("start", p.start).put("end_exclusive", p.endExclusive).put("scene_count", p.sceneCount).put("scene_ids", JSONArray(p.sceneIds))
            .also { putNumber(it, "valid_coverage_pct", p.validCoveragePct) }.put("indices", JSONArray().also { a -> p.indices.forEach { i ->
                val o = JSONObject().put("index", i.index).put("nominal_pixel_size_m", i.nominalPixelSizeM)
                putNumber(o, "mean", i.mean); putNumber(o, "p5", i.p5); putNumber(o, "p95", i.p95); putNumber(o, "valid_coverage_pct", i.validCoveragePct); a.put(o)
            } })
        val c = result.change
        val change = JSONObject().put("index", c.index).put("threshold", c.threshold).put("nominal_pixel_size_m", c.nominalPixelSizeM).put("image_url", JSONObject.NULL)
            .put("legend", JSONObject().put("min", c.legend.min).put("max", c.legend.max).put("palette", JSONArray(c.legend.palette)).put("label", c.legend.label))
        for ((key, value) in listOf("mean_delta" to c.meanDelta, "common_valid_coverage_pct" to c.commonValidCoveragePct, "common_valid_area_ha" to c.commonValidAreaHa, "increase_ha" to c.increaseHa, "stable_ha" to c.stableHa, "decrease_ha" to c.decreaseHa)) putNumber(change, key, value)
        val analysis = JSONObject().put("dataset", result.dataset).put("source", result.source).put("simulated", result.simulated).put("area_ha", result.areaHa)
            .put("bounds", JSONObject().put("south", result.bounds.south).put("west", result.bounds.west).put("north", result.bounds.north).put("east", result.bounds.east))
            .put("baseline", period(result.baseline)).put("comparison", period(result.comparison)).put("change", change).put("method", result.method).put("warnings", JSONArray(result.warnings)).put("caution", result.caution)
        return JSONObject().put("report_version", 1).put("region_name", regionName).put("region", JSONObject().put("ring", coordinates(ring)))
            .put("analysis", analysis).put("note", "URL ảnh tạm thời đã bỏ. Không chứa mã truy cập. Chỉ số tính từ ảnh nguồn, không từ Super-Resolution.").toString(2)
    }
    private fun boundary(ring: List<GeoPoint>): String {
        val p = Geo.open(ring).filter { it.lat.isFinite() && it.lng.isFinite() }
        if (p.size < 3) return "<p>Chưa có ranh giới để minh họa.</p>"
        val origin = p.first(); val xy = p.map { Geo.toMeters(it, origin) }
        val x0 = xy.minOf { it.first }; val x1 = xy.maxOf { it.first }; val y0 = xy.minOf { it.second }; val y1 = xy.maxOf { it.second }
        val scale = 280 / max(max(x1 - x0, y1 - y0), 0.001)
        val points = xy.joinToString(" ") { "${n(20 + (it.first - x0) * scale)},${n(300 - (it.second - y0) * scale)}" }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 320 340\" role=\"img\" aria-label=\"Ranh giới vùng phân tích\"><polygon points=\"$points\" fill=\"#dceede\" stroke=\"#28783a\" stroke-width=\"2\"/><text x=\"20\" y=\"330\" font-size=\"11\">Bắc ↑ · Ranh giới minh họa, không có nền ảnh</text></svg>"
    }
    private fun period(p: RegionWorkflowPeriod, title: String): String = buildString {
        append("<h2>${escape(title)}</h2><p>${escape(p.start)} → ${escape(p.endExclusive)} (không gồm ngày cuối) · ${p.sceneCount} cảnh · độ phủ hợp lệ ${n(p.validCoveragePct, 1)}%</p>")
        append("<table><thead><tr><th>Chỉ số</th><th>Trung bình</th><th>P5</th><th>P95</th><th>Độ phủ %</th><th>Pixel nguồn</th></tr></thead><tbody>")
        p.indices.forEach { append("<tr><td>${escape(it.index)}</td><td>${n(it.mean)}</td><td>${n(it.p5)}</td><td>${n(it.p95)}</td><td>${n(it.validCoveragePct, 1)}</td><td>${it.nominalPixelSizeM} m</td></tr>") }
        append("</tbody></table><p>Cảnh nguồn: ${p.sceneIds.joinToString(", ") { escape(it) }.ifBlank { "Chưa có cảnh" }}</p>")
    }
    fun html(result: RegionWorkflowResult, regionName: String, ring: List<GeoPoint>): String = buildString {
        append("<!doctype html><html lang=\"vi\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Báo cáo vùng — ${escape(regionName)}</title><style>body{font:16px system-ui,sans-serif;max-width:960px;margin:32px auto;padding:0 20px;color:#183320;line-height:1.5}table{border-collapse:collapse;width:100%;font-size:14px}td,th{padding:9px;border:1px solid #ccd9ce;text-align:left}th{background:#eef5ec}svg{width:280px;max-width:100%}.notice{background:#fff3d4;padding:12px;border-left:4px solid #b78000}h2{margin-top:28px}small{color:#526358}</style></head><body>")
        append("<h1>Báo cáo phân tích vùng</h1><p><strong>${escape(regionName)}</strong> · ${n(result.areaHa, 4)} ha</p>")
        if (result.simulated) append("<p class=\"notice\"><strong>DỮ LIỆU MÔ PHỎNG — không dùng để ra quyết định canh tác.</strong></p>")
        append(boundary(ring)); append("<p>Nguồn: ${escape(result.source)}<br>Tập dữ liệu: ${escape(result.dataset)}</p>")
        append(period(result.baseline, "Kỳ nền")); append(period(result.comparison, "Kỳ so sánh"))
        val c = result.change
        append("<h2>Biến động NDVI trên vùng hợp lệ chung</h2><p>ΔNDVI = kỳ so sánh − kỳ nền. Ngưỡng ±${n(c.threshold)}; pixel nguồn ${c.nominalPixelSizeM} m.</p>")
        append("<table><tr><th>Độ phủ chung</th><th>Diện tích chung</th><th>Δ trung bình</th><th>Tăng</th><th>Ổn định</th><th>Giảm</th></tr><tr><td>${n(c.commonValidCoveragePct, 1)}%</td><td>${n(c.commonValidAreaHa, 4)} ha</td><td>${n(c.meanDelta)}</td><td>${n(c.increaseHa, 4)} ha</td><td>${n(c.stableHa, 4)} ha</td><td>${n(c.decreaseHa, 4)} ha</td></tr></table>")
        append("<p>Phần bị mây, thiếu dữ liệu hoặc chỉ hợp lệ ở một kỳ không được phân loại là tăng, giảm hay ổn định.</p>")
        append("<h2>Phương pháp và độ phân giải</h2><p>${escape(result.method)}</p><p>Sentinel-2: B02 (xanh dương), B04 (đỏ), B08 (cận hồng ngoại): 10 m; B11 (hồng ngoại sóng ngắn): 20 m. NDVI dùng B08/B04; EVI dùng B08/B04/B02; NDMI dùng B08/B11 với độ phân giải nguồn 20 m. Pixel được lấy mẫu lại không tạo thêm quan sát.</p>")
        append("<p class=\"notice\">Vùng nhỏ hoặc hẹp có ít pixel độc lập và dễ bị trộn với vùng lân cận. Ảnh AI và lưới xuất 1 m không chứng minh độ chính xác 1 m. Các chỉ số không chẩn đoán bệnh từng cây, không đo trực tiếp độ ẩm đất, chiều cao cây hoặc dữ liệu LiDAR.</p>")
        append("<h2>Lưu ý</h2><ul>"); result.warnings.forEach { append("<li>${escape(it)}</li>") }; append("</ul><p>${escape(result.caution)}</p><small>Báo cáo HTML có thể chỉnh sửa. Ranh giới SVG được nhúng; không có ảnh hoặc URL mạng tạm thời, không có mã truy cập.</small></body></html>")
    }
}
