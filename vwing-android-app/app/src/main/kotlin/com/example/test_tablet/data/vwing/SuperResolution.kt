package com.example.test_tablet.data.vwing

import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL

enum class SrJobState(val wire: String) {
    QUEUED("queued"), RUNNING("running"), SUCCEEDED("succeeded"), FAILED("failed");

    val terminal get() = this == SUCCEEDED || this == FAILED
    fun permits(next: SrJobState): Boolean = when (this) {
        QUEUED -> true
        RUNNING -> next != QUEUED
        SUCCEEDED, FAILED -> next == this
    }
}

data class SrResult(
    val model: String, val modelRevision: String, val sourceScene: String, val sourceDate: String,
    val nativePixelSizeM: Double, val modelPixelSizeM: Double, val outputPixelSizeM: Double,
    val crs: String, val bounds: GeoBounds, val width: Int, val height: Int,
    val validCoveragePct: Double, val method: String, val warnings: List<String>, val files: Map<String, String>,
)

data class SrJobSnapshot(val jobId: String, val state: SrJobState, val result: SrResult?, val error: String?) {
    fun accepts(next: SrJobSnapshot) = jobId == next.jobId && state.permits(next.state)
}

/** Only real backend jobs. A 1 m export is resampled from the 2.5 m model output. */
object SuperResolutionClient {
    private const val API = "/v1/satellite/super-resolution"
    private const val JSON_LIMIT = 1024 * 1024
    private const val IMAGE_LIMIT = 12 * 1024 * 1024
    private const val DOWNLOAD_LIMIT = 64 * 1024 * 1024
    private val jobPattern = Regex("[a-f0-9]{32}")
    private val artifactNames = mapOf(
        "original_preview" to "original.png", "enhanced_preview" to "enhanced.png",
        "original_geotiff" to "original.tif", "enhanced_geotiff" to "enhanced.tif", "metadata" to "metadata.json",
    )
    private val artifactPath = Regex("$API/[a-f0-9]{32}/files/(original\\.png|enhanced\\.png|original\\.tif|enhanced\\.tif|metadata\\.json)")

    private inline fun <T> safe(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) { throw e }
    catch (_: SocketTimeoutException) { Result.failure(IOException("Máy chủ vệ tinh phản hồi quá lâu. Hãy kiểm tra lại tác vụ.")) }
    catch (_: ConnectException) { Result.failure(IOException("Không kết nối được máy chủ vệ tinh. Kiểm tra địa chỉ và mạng.")) }
    catch (_: UnknownHostException) { Result.failure(IOException("Không tìm thấy địa chỉ máy chủ vệ tinh.")) }
    catch (e: IOException) { Result.failure(e) }
    catch (_: Exception) { Result.failure(IOException("Phản hồi Super-Resolution không hợp lệ hoặc không đọc được.")) }

    private fun checkConfig(cfg: SatelliteConfig) {
        SyncClient.urlProblem(cfg.url)?.let { throw IOException(it) }
        if (!cfg.configured) throw IOException("Chưa kết nối máy chủ vệ tinh.")
    }

    /** No external host, userinfo, redirect, query or unrecognized artifact path. */
    internal fun artifactTarget(cfg: SatelliteConfig, path: String): URL {
        checkConfig(cfg)
        val (url, sameOrigin) = SatelliteClient.imageTarget(cfg, path)
        val uri = url.toURI()
        if (!sameOrigin || uri.rawQuery != null || uri.rawFragment != null || !artifactPath.matches(uri.rawPath)) {
            throw IOException("Tệp Super-Resolution phải thuộc đúng máy chủ vệ tinh đã kết nối.")
        }
        return url
    }

    internal fun copyLimited(input: InputStream, output: OutputStream, maxBytes: Int,
                             checkCanceled: () -> Unit = {}): Long {
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            checkCanceled()
            val size = input.read(buffer)
            if (size < 0) break
            if (total + size > maxBytes) throw IOException("Tệp Super-Resolution vượt giới hạn ${maxBytes / 1024 / 1024} MB.")
            output.write(buffer, 0, size)
            total += size
        }
        return total
    }

    private fun bytes(input: InputStream, limit: Int): ByteArray = ByteArrayOutputStream().use { output ->
        copyLimited(input, output, limit)
        output.toByteArray()
    }

    private fun connection(cfg: SatelliteConfig, url: URL) = (url.openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = false
        connectTimeout = 8_000
        readTimeout = 30_000
        setRequestProperty("Authorization", "Bearer ${cfg.token}")
    }

    private fun failure(conn: HttpURLConnection): IOException {
        val detail = runCatching {
            conn.errorStream?.use { JSONObject(bytes(it, JSON_LIMIT).toString(Charsets.UTF_8)).opt("detail") as? String }
        }.getOrNull()
        val message = when (conn.responseCode) {
            401 -> "Sai mã truy cập máy chủ vệ tinh."
            404 -> "Tác vụ hoặc tệp đã hết hạn. Hãy tạo lại ảnh."
            422 -> detail ?: "Vùng hoặc khoảng ngày không hợp lệ."
            429 -> "Máy chủ đang xử lý tác vụ khác. Hãy thử lại sau."
            503 -> "Super-Resolution cần backend live với mô hình AI đã cài đặt; không chạy trong chế độ mô phỏng."
            in 300..399 -> "Máy chủ chuyển hướng tải tệp; app đã dừng để bảo vệ mã truy cập."
            else -> "Không xử lý được Super-Resolution (HTTP ${conn.responseCode})."
        }
        return IOException(message)
    }

    private fun <T> withCandidates(cfg: SatelliteConfig, block: (SatelliteConfig) -> T): T {
        checkConfig(cfg)
        var lastErr: IOException? = null
        for (base in SatelliteClient.candidateUrls(cfg.url)) {
            try {
                return block(cfg.copy(url = base))
            } catch (e: ConnectException) {
                lastErr = e
            } catch (e: SocketTimeoutException) {
                lastErr = e
            } catch (e: UnknownHostException) {
                lastErr = e
            }
        }
        throw lastErr ?: ConnectException("Không kết nối được máy chủ vệ tinh.")
    }

    private fun api(cfg: SatelliteConfig, path: String, body: JSONObject? = null): SrJobSnapshot = withCandidates(cfg) { c ->
        val (target, sameOrigin) = SatelliteClient.imageTarget(c, path)
        if (!sameOrigin) throw IOException("Máy chủ Super-Resolution không hợp lệ.")
        val conn = connection(c, target)
        try {
            conn.requestMethod = if (body == null) "GET" else "POST"
            if (body != null) {
                val data = body.toString().toByteArray(Charsets.UTF_8)
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setFixedLengthStreamingMode(data.size)
                conn.outputStream.use { it.write(data) }
            }
            if (conn.responseCode != if (body == null) 200 else 202) throw failure(conn)
            parseJob(JSONObject(conn.inputStream.use { bytes(it, JSON_LIMIT) }.toString(Charsets.UTF_8)))
        } finally { conn.disconnect() }
    }

    fun submit(cfg: SatelliteConfig, ring: List<GeoPoint>, start: String, end: String,
               outputPixelSizeM: Double = 2.5, maxCloudPct: Double = 80.0): Result<SrJobSnapshot> = safe {
        if (outputPixelSizeM !in listOf(1.0, 2.5) || !maxCloudPct.isFinite() || maxCloudPct !in 0.0..100.0) {
            throw IOException("Độ phân giải hoặc ngưỡng mây không hợp lệ.")
        }
        val points = JSONArray().also { array -> ring.forEach { array.put(JSONArray().put(it.lng).put(it.lat)) } }
        api(cfg, API, JSONObject().put("region", JSONObject().put("ring", points))
            .put("start", start).put("end", end).put("max_cloud_pct", maxCloudPct).put("output_pixel_size_m", outputPixelSizeM))
    }

    fun status(cfg: SatelliteConfig, jobId: String): Result<SrJobSnapshot> = safe {
        if (!jobPattern.matches(jobId)) throw IOException("Mã tác vụ Super-Resolution không hợp lệ.")
        api(cfg, "$API/$jobId").also { if (it.jobId != jobId) throw IOException("Máy chủ trả sai mã tác vụ.") }
    }

    fun preview(cfg: SatelliteConfig, path: String): Result<ByteArray> = safe {
        withCandidates(cfg) { c ->
            val url = artifactTarget(c, path)
            if (!url.path.endsWith(".png")) throw IOException("Tệp xem trước phải là PNG.")
            val conn = connection(c, url)
            try {
                if (conn.responseCode != 200) throw failure(conn)
                conn.inputStream.use { bytes(it, IMAGE_LIMIT) }.also {
                    if (it.size < 8 || !it.copyOf(8).contentEquals(byteArrayOf(0x89.toByte(), 80, 78, 71, 13, 10, 26, 10))) {
                        throw IOException("Ảnh xem trước không phải PNG hợp lệ.")
                    }
                }
            } finally { conn.disconnect() }
        }
    }

    /** Streams directly to the SAF document; caller owns/cleans the output stream. */
    fun download(cfg: SatelliteConfig, path: String, output: OutputStream,
                 checkCanceled: () -> Unit = {}): Result<Long> = safe {
        withCandidates(cfg) { c ->
            val url = artifactTarget(c, path)
            val conn = connection(c, url)
            val limit = if (url.path.endsWith(".json")) JSON_LIMIT else DOWNLOAD_LIMIT
            try {
                checkCanceled()
                if (conn.responseCode != 200) throw failure(conn)
                if (conn.contentLengthLong > limit) throw IOException("Tệp Super-Resolution quá lớn.")
                conn.inputStream.use { copyLimited(it, output, limit, checkCanceled) }
            } finally { conn.disconnect() }
        }
    }

    fun parseJob(json: JSONObject): SrJobSnapshot {
        val id = json.getString("job_id")
        require(jobPattern.matches(id)) { "Invalid job ID" }
        val state = SrJobState.entries.firstOrNull { it.wire == json.getString("state") }
            ?: throw IOException("Trạng thái Super-Resolution chưa được hỗ trợ.")
        val result = if (json.isNull("result")) null else parseResult(json.getJSONObject("result"), id)
        val error = if (json.isNull("error")) null else json.getString("error")
        require((state == SrJobState.SUCCEEDED) == (result != null)) { "Job result does not match state" }
        require(state == SrJobState.FAILED || error == null) { "Unexpected job error" }
        return SrJobSnapshot(id, state, result, if (state == SrJobState.FAILED) error?.takeIf(String::isNotBlank) ?: "Mô hình chưa tạo được ảnh. Hãy thử lại." else null)
    }

    private fun parseResult(json: JSONObject, id: String): SrResult {
        val b = json.getJSONObject("bounds")
        val bounds = GeoBounds(b.getDouble("south"), b.getDouble("west"), b.getDouble("north"), b.getDouble("east"))
        require(bounds.valid) { "Invalid preview bounds" }
        val filesJson = json.getJSONObject("files")
        val files = artifactNames.mapValues { (key, filename) ->
            filesJson.getString(key).also { require(it == "$API/$id/files/$filename") { "Invalid artifact manifest" } }
        }
        val coverage = json.getDouble("valid_coverage_pct")
        val width = json.getInt("width")
        val height = json.getInt("height")
        val nativePixel = json.getDouble("native_pixel_size_m")
        val modelPixel = json.getDouble("model_pixel_size_m")
        val outputPixel = json.getDouble("output_pixel_size_m")
        require(coverage.isFinite() && coverage in 0.0..100.0 && width in 1..8192 && height in 1..8192)
        require(nativePixel == 10.0 && modelPixel == 2.5 && outputPixel in listOf(1.0, 2.5))
        val warnings = json.optJSONArray("warnings")?.let { array -> (0 until array.length()).map(array::getString) } ?: emptyList()
        return SrResult(json.getString("model"), json.getString("model_revision"), json.getString("source_scene"),
            json.getString("source_date"), nativePixel, modelPixel, outputPixel, json.getString("crs"), bounds,
            width, height, coverage, json.getString("method"), warnings, files)
    }
}
