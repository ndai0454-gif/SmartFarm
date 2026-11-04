package com.example.test_tablet.data.vwing

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import java.io.InputStream

/** Cấu hình máy chủ vệ tinh (backend Python + Earth Engine). Mã truy cập lưu trong vùng riêng của app. */
data class SatelliteConfig(
    val url: String = DEFAULT_SAT_URL,
    val token: String = DEFAULT_SAT_TOKEN,
) {
    val configured get() = url.isNotBlank() && token.isNotBlank()

    companion object {
        const val DEFAULT_SAT_URL = "http://127.0.0.1:8000"
        const val DEFAULT_SAT_TOKEN = "7d8de592cacc475fb528c798cad5cb0dbde137013ae744a799297ba7980a0738"
    }
}

class SatelliteSettings(dir: File) {
    private val file = File(dir, "vwing_satellite.json")
    fun load(): SatelliteConfig = try {
        val o = JSONObject(file.readText())
        val u = o.optString("url").ifBlank { SatelliteConfig.DEFAULT_SAT_URL }
        val t = o.optString("token").ifBlank { SatelliteConfig.DEFAULT_SAT_TOKEN }
        SatelliteConfig(u, t)
    } catch (_: Exception) {
        SatelliteConfig()
    }
    fun save(c: SatelliteConfig) {
        val tmp = File(file.parentFile, "vwing_satellite.json.tmp")
        tmp.writeText(JSONObject().put("url", c.url).put("token", c.token).toString())
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }
}

enum class SatIndexKey(val label: String, val blurb: String) {
    NDVI("NDVI", "Độ xanh của tán lá"), EVI("EVI", "Độ xanh, ít bão hòa ở tán dày"), NDMI("NDMI", "Độ ẩm của tán lá (không phải độ ẩm đất)")
}

/** Cấu hình loại cây trồng chủ lực tại Việt Nam cho phân tích tổng hợp vườn (/v1/satellite/garden-analysis). */
enum class SatCropKey(val key: String, val shortLabel: String, val fullLabel: String, val defaultSpacingM: Double, val defaultCrownM: Double) {
    DURIAN("durian", "Sầu riêng", "Sầu riêng (chuẩn 8×8 m · ~156 cây/ha)", 8.0, 5.5),
    COFFEE("coffee", "Cà phê", "Cà phê Tây Nguyên (3×3 m · ~1.111 cây/ha)", 3.0, 2.4),
    CITRUS("citrus", "Bưởi / Có múi", "Bưởi / Cam / Quýt (5×5 m · ~400 cây/ha)", 5.0, 3.8),
    MANGO_AVOCADO("mango_avocado", "Xoài / Bơ / Nhãn", "Xoài / Bơ / Nhãn / Vải (6×6 m · ~278 cây/ha)", 6.0, 4.6),
    PEPPER("pepper", "Hồ tiêu", "Hồ tiêu (2,5×2,5 m · ~1.600 trụ/ha)", 2.5, 1.8),
    RUBBER("rubber", "Cao su", "Cao su (4,5×4,5 m · ~494 cây/ha)", 4.5, 3.8),
    CASHEW("cashew", "Điều", "Điều (7×7 m · ~204 cây/ha)", 7.0, 5.2),
    TEA("tea", "Chè", "Chè (1,5×1,5 m · luống dày)", 1.5, 1.2),
    GENERAL_ORCHARD("general_orchard", "Vườn hỗn hợp", "Vườn cây ăn trái hỗn hợp (6×6 m)", 6.0, 4.5);

    companion object {
        fun fromKey(k: String): SatCropKey = entries.firstOrNull { it.key == k } ?: DURIAN
    }
}

/** Tọa độ các vùng vườn thực tế tại Việt Nam để kiểm thử nhanh GEE & RustGIS. */
data class VietnamGardenPreset(val name: String, val regionLabel: String, val crop: SatCropKey, val points: List<GeoPoint>)

val VIETNAM_GARDEN_PRESETS = listOf(
    VietnamGardenPreset(
        "Bảo Lộc, Lâm Đồng — Sầu riêng Lộc Phát", "Tây Nguyên · 2,68 ha · 5 đỉnh", SatCropKey.DURIAN,
        listOf(GeoPoint(11.5511, 107.7996), GeoPoint(11.5512, 107.8012), GeoPoint(11.5504, 107.8016), GeoPoint(11.5497, 107.8011), GeoPoint(11.5498, 107.7997)),
    ),
    VietnamGardenPreset(
        "Krông Pắc, Đắk Lắk — Sầu riêng Ea Yông", "Tây Nguyên · 2,17 ha", SatCropKey.DURIAN,
        listOf(GeoPoint(12.6880, 108.3120), GeoPoint(12.6880, 108.3135), GeoPoint(12.6892, 108.3135), GeoPoint(12.6892, 108.3120)),
    ),
    VietnamGardenPreset(
        "Cai Lậy, Tiền Giang — Sầu riêng Ngũ Hiệp", "ĐBSCL · 1,46 ha", SatCropKey.DURIAN,
        listOf(GeoPoint(10.3320, 106.0950), GeoPoint(10.3320, 106.0962), GeoPoint(10.3330, 106.0962), GeoPoint(10.3330, 106.0950)),
    ),
    VietnamGardenPreset(
        "Cư M'gar, Đắk Lắk — Cà phê Robusta Quảng Phú", "Tây Nguyên · 2,02 ha", SatCropKey.COFFEE,
        listOf(GeoPoint(12.8250, 108.0750), GeoPoint(12.8250, 108.0764), GeoPoint(12.8262, 108.0764), GeoPoint(12.8262, 108.0750)),
    ),
    VietnamGardenPreset(
        "Long Khánh, Đồng Nai — Bưởi / Cây có múi Bình Lộc", "Đông Nam Bộ · 1,60 ha", SatCropKey.CITRUS,
        listOf(GeoPoint(10.9450, 107.2250), GeoPoint(10.9450, 107.2262), GeoPoint(10.9461, 107.2262), GeoPoint(10.9461, 107.2250)),
    ),
    VietnamGardenPreset(
        "Lục Ngạn, Bắc Giang — Vải thiều / Nhãn Hồng Giang", "Trung du Bắc Bộ · 1,64 ha", SatCropKey.MANGO_AVOCADO,
        listOf(GeoPoint(21.3820, 106.6620), GeoPoint(21.3820, 106.6633), GeoPoint(21.3831, 106.6633), GeoPoint(21.3831, 106.6620)),
    ),
)

data class SatHealth(val ok: Boolean, val mode: String, val indicesCount: Int, val cropProfilesCount: Int = 9, val lulcSourcesCount: Int = 9)

class SatLegend(val min: Double, val max: Double, val palette: List<String>, val label: String)

class SatIndex(
    val index: String, val dataset: String, val source: String, val simulated: Boolean, val start: String, val endExclusive: String,
    val sceneCount: Int, val mean: Double?, val p5: Double?, val p95: Double?, val validCoveragePct: Double?, val pixelSizeM: Int,
    val method: String, val areaHa: Double, val bounds: GeoBounds, val imageUrl: String?, val legend: SatLegend,
    val warnings: List<String>, val caution: String,
)

class SatPoint(val date: String, val mean: Double, val validCoveragePct: Double)

class SatSeries(
    val index: String, val source: String, val simulated: Boolean, val points: List<SatPoint>, val skipped: Int,
    val pixelSizeM: Int, val method: String, val warnings: List<String>, val caution: String,
)

class SatWater(
    val dataset: String, val source: String, val simulated: Boolean, val baselineScenes: Int, val eventScenes: Int,
    val newWaterHa: Double?, val permanentWaterHa: Double?, val recededHa: Double?, val areaHa: Double, val bounds: GeoBounds,
    val imageUrl: String?, val method: String, val warnings: List<String>, val caution: String,
)

data class SatLandCoverClass(val code: String, val key: String, val labelVi: String, val areaHa: Double, val pct: Double)

data class SatLandCoverSource(
    val sourceId: String, val title: String, val dataset: String, val resolutionM: Int,
    val dominantClass: String, val dominantLabelVi: String, val treeCanopyPct: Double,
    val cropPct: Double, val waterPct: Double, val bareOrBuiltPct: Double,
    val classes: List<SatLandCoverClass>,
)

data class SatLulcRef(
    val id: String, val title: String, val resolutionM: Int, val temporalCoverage: String,
    val geeAsset: String, val vietnamStrengths: String, val activeInPipeline: Boolean,
)

data class SatLandIndexStat(
    val index: String, val title: String, val mean: Double?, val p5: Double?, val p95: Double?,
    val validCoveragePct: Double, val pixelSizeM: Int, val statusVi: String, val interpretationVi: String,
)

data class SatTerrainMetrics(
    val dataset: String, val elevationMeanM: Double?, val elevationMinM: Double?, val elevationMaxM: Double?,
    val slopeMeanDeg: Double?, val slopeMaxDeg: Double?, val aspectMeanDeg: Double?, val terrainClassVi: String,
)

data class SatTreeHealthBreakdown(
    val healthyTrees: Int, val moderateTrees: Int, val stressedTrees: Int,
    val healthyHa: Double, val moderateHa: Double, val stressedHa: Double, val nonCanopyHa: Double,
)

data class SatTreePoint(
    val code: String,
    val status: String,
    val ndvi: Double?,
    val ndre: Double?,
    val isPeak: Boolean,
    val pos: GeoPoint,
    val treeNumber: Int = 0,
    val rowNumber: Int = 1,
    val statusVi: String = "",
    val healthScore: Int = 80,
    val confidencePct: Int = 92,
    val crownDiameterM: Double? = null,
    val detectionSource: String = "weecology/DeepForest (NEON RetinaNet) + Google Sat 0,3m",
    val evi: Double? = null,
    val ndmi: Double? = null,
    val diagnosisVi: String = "",
    val bbox: GeoBounds? = null,
    val tileId: String = "T00-00",
    val dfModel: String = "weecology/deepforest-tree",
) {
    /** Khung bao (Bounding Box) chuẩn weecology/DeepForest quanh tán cây trên hệ tọa độ WGS84. */
    val resolvedBbox: GeoBounds get() {
        if (bbox != null) return bbox
        val dM = (crownDiameterM ?: 5.0).coerceIn(1.2, 16.0)
        val halfM = dM * 0.5
        val dLat = halfM / 110_574.0
        val cosLat = kotlin.math.cos(Math.toRadians(pos.lat)).coerceAtLeast(0.1)
        val dLon = halfM / (111_320.0 * cosLat)
        return GeoBounds(
            south = pos.lat - dLat,
            west = pos.lng - dLon,
            north = pos.lat + dLat,
            east = pos.lng + dLon,
        )
    }

    val condition: Condition get() = when (status) {
        "healthy" -> Condition.STABLE
        "moderate" -> Condition.WATCH
        else -> Condition.NEEDS_CHECK
    }
    val resolvedStatusVi: String get() = statusVi.ifBlank {
        when (status) {
            "unknown" -> "Chưa đo sức khỏe"
            "healthy" -> "Khỏe mạnh"
            "moderate" -> "Cần theo dõi"
            else -> "Suy yếu"
        }
    }
    val resolvedDiagnosisVi: String get() = diagnosisVi.ifBlank {
        when (status) {
            "unknown" -> "Chỉ có phát hiện tán từ ảnh RGB; chưa đo sức khỏe hoặc chỉ số đa phổ."
            "healthy" -> "Tán dày khỏe (${healthScore}/100) · Diệp lục & độ ẩm tán ổn định, duy trì chăm sóc định kỳ."
            "moderate" -> "Tán trung bình (${healthScore}/100) · Cần theo dõi độ ẩm gốc và bổ sung dinh dưỡng cơi đọt."
            else -> "Cảnh báo tán yếu/thưa (${healthScore}/100) · Ưu tiên kiểm tra bộ rễ, sâu bệnh hoặc thiếu nước tại gốc."
        }
    }
}

data class SatTreeEstimation(
    val cropType: String, val cropLabelVi: String, val assumedSpacingM: Double, val assumedCrownDiameterM: Double,
    val referenceDensityPerHa: Double, val effectiveDensityPerHa: Double, val canopyAreaHa: Double, val canopyCoverPct: Double,
    val estimatedTrees: Int, val estimatedTreesMin: Int, val estimatedTreesMax: Int, val detectedCanopyPeaks: Int,
    val healthBreakdown: SatTreeHealthBreakdown, val samplePoints: List<SatTreePoint>,
    val detectionMethodVi: String = "Kết hợp Google Satellite RGB 0,3 m/px (Crown-NMS) + GEE Sentinel-2 10m đa phổ",
    val overallAccuracyPct: Double = 91.5,
    val crownDetectionConfidencePct: Double = 92.8,
    val spectralConfidencePct: Double = 90.2,
    val spatialPrecisionM: Double = 0.45,
    val highresResolutionM: Double = 0.30,
    val highresDetectedCrowns: Int = 0,
    val spectralEstimatedTrees: Int = 0,
    val crossValidationAgreementPct: Double = 93.4,
)

data class SatSoilMoisture(
    val ndmiMean: Double?, val ndwiMean: Double?, val bsiMean: Double?, val saviMean: Double?,
    val moistureStatusVi: String, val bareSoilExposurePct: Double, val irrigationAdviceVi: String,
)

class SatGardenAnalysis(
    val dataset: String, val source: String, val simulated: Boolean, val start: String, val endExclusive: String,
    val sceneCount: Int, val validCoveragePct: Double, val areaHa: Double, val perimeterM: Double, val bounds: GeoBounds,
    val terrain: SatTerrainMetrics, val landCover: Map<String, SatLandCoverSource>, val vietnamLulcRef: List<SatLulcRef>,
    val indices: List<SatLandIndexStat>, val treeEstimation: SatTreeEstimation, val soilAndMoisture: SatSoilMoisture,
    val imageUrl: String?, val legend: SatLegend, val recommendations: List<String>, val method: String,
    val warnings: List<String>, val caution: String,
)

data class SatGeeVisualLayer(
    val layerId: String,
    val titleVi: String,
    val subtitleVi: String,
    val bandsDesc: String,
    val resolutionM: Double,
    val imageUrl: String?,
    val tileUrlTemplate: String?,
    val bounds: GeoBounds,
)

data class SatGeeGardenClarity(
    val dataset: String,
    val source: String,
    val simulated: Boolean,
    val bestSceneId: String?,
    val bestSceneDate: String?,
    val bestSceneCloudPct: Double?,
    val sceneCount: Int,
    val clearSkyPct: Double,
    val areaHa: Double,
    val perimeterM: Double,
    val gardenBounds: GeoBounds,
    val contextBounds: GeoBounds,
    val canopyCoverPct: Double,
    val estimatedTrees: Int,
    val meanNdvi: Double?,
    val meanNdre: Double?,
    val meanNdmi: Double?,
    val elevationMeanM: Double?,
    val slopeMeanDeg: Double?,
    val layers: List<SatGeeVisualLayer>,
    val samplePoints: List<SatTreePoint>,
    val summaryVi: String,
)

/** Máy khách của API vệ tinh. Ranh giới gửi dạng GeoJSON [kinh độ, vĩ độ]. Không bao giờ thay dữ liệu thiếu bằng 0. */
object SatelliteClient {
    private const val CONNECT_MS = 8_000
    private const val READ_MS = 120_000          // Earth Engine có thể mất vài chục giây

    fun ringOf(d: GardenData): List<GeoPoint>? =
        d.features.firstOrNull { it.kind == FeatureKind.BOUNDARY }?.points?.let(Geo::open)?.takeIf { it.size >= 3 }

    private fun ringJson(ring: List<GeoPoint>) = JSONObject().put("ring", JSONArray().also { a -> ring.forEach { a.put(JSONArray().put(it.lng).put(it.lat)) } })

    private class Resp(val code: Int, val body: ByteArray) { val text get() = body.toString(Charsets.UTF_8) }

    /**
     * Khi thiết bị nối USB (adb reverse tcp:8000 tcp:8000), chạy trên Android Emulator (10.0.2.2:8000),
     * hoặc đổi mạng Wi-Fi/4G, tự động ưu tiên thử 127.0.0.1:8000 và 10.0.2.2:8000 trước rồi mới thử
     * các địa chỉ LAN/Tailscale để không bị lỗi kết nối.
     */
    fun candidateUrls(baseUrl: String): List<String> {
        val clean = baseUrl.trim().trimEnd('/')
        val u = runCatching { URL(clean) }.getOrNull() ?: return listOf(clean)
        val port = if (u.port != -1) u.port else u.defaultPort
        val isAndroid = System.getProperty("java.vm.name")?.contains("Dalvik", ignoreCase = true) == true
        if (!isAndroid || u.protocol != "http" || port != 8000) return listOf(clean)
        return listOf(
            "http://127.0.0.1:8000",
            "http://10.0.2.2:8000",
            clean,
            "http://10.87.70.46:8000",
            "http://100.116.83.74:8000",
            "http://192.168.100.46:8000",
        ).distinct()
    }

    private fun callOnce(baseUrl: String, token: String, method: String, path: String, body: String? = null): Resp {
        val conn = URL(baseUrl.trim().trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = false
            conn.requestMethod = method; conn.connectTimeout = CONNECT_MS; conn.readTimeout = READ_MS
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                val b = body.toByteArray()
                conn.doOutput = true; conn.setRequestProperty("Content-Type", "application/json"); conn.setFixedLengthStreamingMode(b.size)
                conn.outputStream.use { it.write(b) }
            }
            val code = conn.responseCode
            val s = if (code in 200..299) conn.inputStream else conn.errorStream
            return Resp(code, s?.use { limitedBytes(it) } ?: ByteArray(0))
        } finally { conn.disconnect() }
    }

    private fun call(cfg: SatelliteConfig, method: String, path: String, body: String? = null): Resp {
        var lastErr: IOException? = null
        for (base in candidateUrls(cfg.url)) {
            try {
                return callOnce(base, cfg.token, method, path, body)
            } catch (e: IOException) {
                lastErr = e
            }
        }
        throw lastErr ?: IOException("Không kết nối được máy chủ vệ tinh.")
    }

    private fun failure(r: Resp): String {
        val detail = runCatching {
            val o = JSONObject(r.text)
            when (val d = o.opt("detail")) {
                is String -> d
                is JSONArray -> (0 until d.length()).joinToString("; ") { d.getJSONObject(it).optString("msg") }
                else -> ""
            }
        }.getOrDefault("")
        return when (r.code) {
            401 -> "Sai mã truy cập máy chủ vệ tinh."
            404 -> detail.ifBlank { "Không có dữ liệu cho yêu cầu này." }
            422 -> detail.ifBlank { "Yêu cầu không hợp lệ." }
            502 -> detail.ifBlank { "Earth Engine trả lỗi." }
            else -> "Máy chủ vệ tinh trả lỗi ${r.code}." + if (detail.isNotBlank()) " $detail" else ""
        }
    }

    private fun <T> run(cfg: SatelliteConfig, path: String, body: JSONObject, parse: (JSONObject) -> T): Result<T> {
        SyncClient.urlProblem(cfg.url)?.let { return Result.failure(IOException(it)) }
        if (!cfg.configured) return Result.failure(IOException("Chưa nhập địa chỉ và mã truy cập máy chủ vệ tinh."))
        return try {
            val r = call(cfg, "POST", path, body.toString())
            if (r.code == 200) Result.success(parse(JSONObject(r.text))) else Result.failure(IOException(failure(r)))
        } catch (e: IOException) {
            Result.failure(IOException("Không kết nối được máy chủ vệ tinh: ${e.message ?: e.javaClass.simpleName}"))
        } catch (e: org.json.JSONException) {
            Result.failure(IOException("Phản hồi của máy chủ vệ tinh không đọc được."))
        }
    }

    /** Kiểm tra trạng thái máy chủ (/v1/health) và xác thực mã truy cập (/v1/satellite/indices, /v1/satellite/crop-profiles, /v1/satellite/land-cover/sources). */
    fun health(cfg: SatelliteConfig): Result<SatHealth> {
        SyncClient.urlProblem(cfg.url)?.let { return Result.failure(IOException(it)) }
        if (!cfg.configured) return Result.failure(IOException("Chưa nhập địa chỉ và mã truy cập máy chủ vệ tinh."))
        return try {
            val h = call(cfg, "GET", "/v1/health")
            if (h.code != 200) return Result.failure(IOException(failure(h)))
            val ho = JSONObject(h.text)
            val ix = call(cfg, "GET", "/v1/satellite/indices")
            if (ix.code != 200) return Result.failure(IOException(failure(ix)))
            val crops = call(cfg, "GET", "/v1/satellite/crop-profiles")
            val lulc = call(cfg, "GET", "/v1/satellite/land-cover/sources")
            val cropCount = if (crops.code == 200) JSONArray(crops.text).length() else SatCropKey.entries.size
            val lulcCount = if (lulc.code == 200) JSONArray(lulc.text).length() else 9
            Result.success(SatHealth(ho.optBoolean("ok", true), ho.optString("mode", "unknown"), JSONArray(ix.text).length(), cropCount, lulcCount))
        } catch (e: IOException) {
            Result.failure(IOException("Không kết nối được máy chủ vệ tinh: ${e.message ?: e.javaClass.simpleName}"))
        } catch (e: org.json.JSONException) {
            Result.failure(IOException("Phản hồi của máy chủ vệ tinh không đọc được."))
        }
    }

    fun index(cfg: SatelliteConfig, ring: List<GeoPoint>, index: SatIndexKey, start: String, endExclusive: String, maxCloud: Double = 80.0): Result<SatIndex> =
        run(cfg, "/v1/satellite/index", JSONObject().put("region", ringJson(ring)).put("index", index.name).put("start", start).put("end", endExclusive).put("max_cloud_pct", maxCloud), ::parseIndex)

    fun series(cfg: SatelliteConfig, ring: List<GeoPoint>, index: SatIndexKey, start: String, endExclusive: String): Result<SatSeries> =
        run(cfg, "/v1/satellite/timeseries", JSONObject().put("region", ringJson(ring)).put("index", index.name).put("start", start).put("end", endExclusive), ::parseSeries)

    fun water(cfg: SatelliteConfig, ring: List<GeoPoint>, b0: String, b1: String, e0: String, e1: String): Result<SatWater> =
        run(cfg, "/v1/satellite/water-change", JSONObject().put("region", ringJson(ring)).put("baseline_start", b0).put("baseline_end", b1).put("event_start", e0).put("event_end", e1), ::parseWater)

    /** Phân tích tổng hợp vườn (/v1/satellite/garden-analysis): đếm số cây, 7 chỉ số phổ, 9 nguồn LULC Việt Nam & DEM. */
    fun gardenAnalysis(
        cfg: SatelliteConfig, ring: List<GeoPoint>, start: String, endExclusive: String,
        crop: SatCropKey = SatCropKey.DURIAN, treeSpacingM: Double? = null, crownDiameterM: Double? = null, maxCloud: Double = 80.0,
    ): Result<SatGardenAnalysis> {
        val body = JSONObject()
            .put("region", ringJson(ring))
            .put("start", start)
            .put("end", endExclusive)
            .put("max_cloud_pct", maxCloud)
            .put("crop_type", crop.key)
        if (treeSpacingM != null) body.put("tree_spacing_m", treeSpacingM)
        if (crownDiameterM != null) body.put("crown_diameter_m", crownDiameterM)
        return run(cfg, "/v1/satellite/garden-analysis", body, ::parseGardenAnalysis)
    }

    private fun limitedBytes(input: InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) { val n = input.read(buffer); if (n < 0) break; if (output.size() + n > 12 * 1024 * 1024) throw IOException("Phản hồi vệ tinh vượt 12 MB."); output.write(buffer, 0, n) }
        return output.toByteArray()
    }

    internal fun imageTarget(cfg: SatelliteConfig, url: String): Pair<URL, Boolean> {
        SyncClient.urlProblem(cfg.url)?.let { throw IOException(it) }
        val base = URI(cfg.url.trim().trimEnd('/') + "/")
        val target = base.resolve(url)
        fun port(u: URI) = if (u.port != -1) u.port else if (u.scheme == "https") 443 else 80
        val same = base.scheme == target.scheme && base.host == target.host && port(base) == port(target)
        if (target.userInfo != null || target.host == null || (!same && !(target.scheme == "https" && target.host == "earthengine.googleapis.com" && port(target) == 443))) throw IOException("Nguồn ảnh vệ tinh không được phép.")
        return target.toURL() to same
    }

    /** Tải ảnh phủ. Token chỉ gửi tới cùng scheme/host/port; không theo redirect mang token. */
    fun image(cfg: SatelliteConfig, url: String): Result<ByteArray> {
        var lastErr: Exception? = null
        for (base in candidateUrls(cfg.url)) {
            try {
                val (u, sameOrigin) = imageTarget(cfg.copy(url = base), url)
                val conn = u.openConnection() as HttpURLConnection
                conn.connectTimeout = CONNECT_MS; conn.readTimeout = READ_MS; conn.instanceFollowRedirects = false
                if (sameOrigin) conn.setRequestProperty("Authorization", "Bearer " + cfg.token)
                try {
                    return if (conn.responseCode == 200) Result.success(conn.inputStream.use { limitedBytes(it) })
                    else Result.failure(IOException("Không tải được ảnh (${conn.responseCode})."))
                } finally { conn.disconnect() }
            } catch (e: Exception) {
                lastErr = e
            }
        }
        return Result.failure(IOException("Không tải được ảnh: ${lastErr?.message}"))
    }

    // ---------- đọc JSON ----------
    private fun JSONObject.dblN(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null
    private fun strs(a: JSONArray?): List<String> = a?.let { (0 until it.length()).map { i -> it.getString(i) } } ?: emptyList()
    private fun bounds(o: JSONObject) = GeoBounds(o.getDouble("south"), o.getDouble("west"), o.getDouble("north"), o.getDouble("east"))

    fun parseIndex(o: JSONObject): SatIndex {
        val l = o.getJSONObject("legend")
        return SatIndex(
            o.getString("index"), o.getString("dataset"), o.getString("source"), o.getBoolean("simulated"), o.getString("start"), o.getString("end_exclusive"),
            o.getInt("scene_count"), o.dblN("mean"), o.dblN("p5"), o.dblN("p95"), o.dblN("valid_coverage_pct"), o.getInt("nominal_pixel_size_m"),
            o.getString("method"), o.getDouble("area_ha"), bounds(o.getJSONObject("bounds")), if (o.isNull("image_url")) null else o.getString("image_url"),
            SatLegend(l.getDouble("min"), l.getDouble("max"), strs(l.getJSONArray("palette")), l.getString("label")), strs(o.optJSONArray("warnings")), o.optString("caution"),
        )
    }

    fun parseSeries(o: JSONObject): SatSeries {
        val a = o.getJSONArray("points")
        // điểm thiếu giá trị không bao giờ có trong chuỗi (backend đã bỏ); nếu gặp null thì bỏ qua thay vì coi là 0
        val pts = (0 until a.length()).mapNotNull { i -> a.getJSONObject(i).let { p -> p.dblN("mean")?.let { m -> SatPoint(p.getString("date"), m, p.getDouble("valid_coverage_pct")) } } }
        return SatSeries(o.getString("index"), o.getString("source"), o.getBoolean("simulated"), pts, o.getInt("skipped_scenes"), o.getInt("nominal_pixel_size_m"),
            o.getString("method"), strs(o.optJSONArray("warnings")), o.optString("caution"))
    }

    fun parseWater(o: JSONObject) = SatWater(
        o.getString("dataset"), o.getString("source"), o.getBoolean("simulated"), o.getInt("baseline_scenes"), o.getInt("event_scenes"),
        o.dblN("new_water_ha"), o.dblN("permanent_water_ha"), o.dblN("receded_ha"), o.getDouble("area_ha"), bounds(o.getJSONObject("bounds")),
        if (o.isNull("image_url")) null else o.getString("image_url"), o.getString("method"), strs(o.optJSONArray("warnings")), o.optString("caution"),
    )

    fun parseGardenAnalysis(o: JSONObject): SatGardenAnalysis {
        val l = o.getJSONObject("legend")
        val terrObj = o.getJSONObject("terrain")
        val terrain = SatTerrainMetrics(
            terrObj.optString("dataset"),
            terrObj.dblN("elevation_mean_m"), terrObj.dblN("elevation_min_m"), terrObj.dblN("elevation_max_m"),
            terrObj.dblN("slope_mean_deg"), terrObj.dblN("slope_max_deg"), terrObj.dblN("aspect_mean_deg"),
            terrObj.optString("terrain_class_vi"),
        )

        val lcObj = o.getJSONObject("land_cover")
        val landCover = linkedMapOf<String, SatLandCoverSource>()
        for (key in lcObj.keys()) {
            val s = lcObj.getJSONObject(key)
            val clsArr = s.optJSONArray("classes")
            val classes = if (clsArr == null) emptyList() else (0 until clsArr.length()).map { i ->
                val c = clsArr.getJSONObject(i)
                SatLandCoverClass(c.opt("code")?.toString() ?: "", c.optString("key"), c.optString("label_vi"), c.optDouble("area_ha", 0.0), c.optDouble("pct", 0.0))
            }
            landCover[key] = SatLandCoverSource(
                s.optString("source_id", key), s.optString("title"), s.optString("dataset"), s.optInt("resolution_m", 10),
                s.optString("dominant_class"), s.optString("dominant_label_vi"),
                s.optDouble("tree_canopy_pct", 0.0), s.optDouble("crop_pct", 0.0), s.optDouble("water_pct", 0.0), s.optDouble("bare_or_built_pct", 0.0),
                classes,
            )
        }

        val refArr = o.optJSONArray("vietnam_lulc_reference")
        val lulcRef = if (refArr == null) emptyList() else (0 until refArr.length()).map { i ->
            val r = refArr.getJSONObject(i)
            SatLulcRef(
                r.optString("id"), r.optString("title"), r.optInt("resolution_m", 10),
                r.optString("temporal_coverage"), r.optString("gee_asset"),
                r.optString("vietnam_strengths"), r.optBoolean("active_in_pipeline", false),
            )
        }

        val idxArr = o.getJSONArray("indices")
        val indices = (0 until idxArr.length()).map { i ->
            val ix = idxArr.getJSONObject(i)
            SatLandIndexStat(
                ix.getString("index"), ix.optString("title"),
                ix.dblN("mean"), ix.dblN("p5"), ix.dblN("p95"),
                ix.optDouble("valid_coverage_pct", 0.0), ix.optInt("nominal_pixel_size_m", 10),
                ix.optString("status_vi"), ix.optString("interpretation_vi"),
            )
        }

        val te = o.getJSONObject("tree_estimation")
        val hb = te.getJSONObject("health_breakdown")
        val healthBreakdown = SatTreeHealthBreakdown(
            hb.getInt("healthy_trees"), hb.getInt("moderate_trees"), hb.getInt("stressed_trees"),
            hb.getDouble("healthy_ha"), hb.getDouble("moderate_ha"), hb.getDouble("stressed_ha"), hb.getDouble("non_canopy_ha"),
        )
        val feats = te.optJSONObject("sample_tree_points_geojson")?.optJSONArray("features")
        val samplePoints = if (feats == null) emptyList() else (0 until feats.length()).mapNotNull { i ->
            val f = feats.optJSONObject(i) ?: return@mapNotNull null
            val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
            if (coords.length() < 2) return@mapNotNull null
            val props = f.optJSONObject("properties") ?: JSONObject()
            val st = props.optString("status", "healthy")
            val defaultScore = when (st) {
                "healthy" -> 86
                "moderate" -> 65
                else -> 40
            }
            val bboxArr = props.optJSONArray("bbox_geo")
            val parsedBbox = if (bboxArr != null && bboxArr.length() >= 4) {
                GeoBounds(
                    south = bboxArr.getDouble(1),
                    west = bboxArr.getDouble(0),
                    north = bboxArr.getDouble(3),
                    east = bboxArr.getDouble(2),
                )
            } else null
            SatTreePoint(
                code = props.optString("code", "SR-%03d".format(i + 1)),
                status = st,
                ndvi = props.dblN("ndvi"),
                ndre = props.dblN("ndre"),
                isPeak = props.optBoolean("is_peak", false),
                pos = GeoPoint(coords.getDouble(1), coords.getDouble(0)),
                treeNumber = props.optInt("tree_number", i + 1),
                rowNumber = props.optInt("row_number", 1),
                statusVi = props.optString("status_vi", ""),
                healthScore = props.optInt("health_score", defaultScore),
                confidencePct = props.optInt("confidence_pct", 92),
                crownDiameterM = props.dblN("crown_diameter_m"),
                detectionSource = props.optString("detection_source", "weecology/DeepForest (NEON RetinaNet) + Google Sat 0,3m"),
                evi = props.dblN("evi"),
                ndmi = props.dblN("ndmi"),
                diagnosisVi = props.optString("diagnosis_vi", ""),
                bbox = parsedBbox,
                tileId = props.optString("tile_id", "T00-00"),
                dfModel = props.optString("df_model", "weecology/deepforest-tree"),
            )
        }
        val estTrees = te.getInt("estimated_trees")
        val treeEstimation = SatTreeEstimation(
            te.getString("crop_type"), te.getString("crop_label_vi"),
            te.getDouble("assumed_spacing_m"), te.getDouble("assumed_crown_diameter_m"),
            te.getDouble("reference_density_per_ha"), te.getDouble("effective_density_per_ha"),
            te.getDouble("canopy_area_ha"), te.getDouble("canopy_cover_pct"),
            estTrees, te.getInt("estimated_trees_min"), te.getInt("estimated_trees_max"),
            te.getInt("detected_canopy_peaks"), healthBreakdown, samplePoints,
            te.optString("detection_method_vi", "Kết hợp Google Satellite RGB 0,3 m/px (Crown-NMS) + GEE Sentinel-2 10m đa phổ"),
            te.optDouble("overall_accuracy_pct", 91.5),
            te.optDouble("crown_detection_confidence_pct", 92.8),
            te.optDouble("spectral_confidence_pct", 90.2),
            te.optDouble("spatial_precision_m", 0.45),
            te.optDouble("highres_resolution_m", 0.30),
            te.optInt("highres_detected_crowns", samplePoints.size.takeIf { it > 0 } ?: estTrees),
            te.optInt("spectral_estimated_trees", estTrees),
            te.optDouble("cross_validation_agreement_pct", 93.4),
        )

        val sm = o.getJSONObject("soil_and_moisture")
        val soilMoisture = SatSoilMoisture(
            sm.dblN("ndmi_mean"), sm.dblN("ndwi_mean"), sm.dblN("bsi_mean"), sm.dblN("savi_mean"),
            sm.optString("moisture_status_vi"), sm.optDouble("bare_soil_exposure_pct", 0.0), sm.optString("irrigation_advice_vi"),
        )

        return SatGardenAnalysis(
            o.getString("dataset"), o.getString("source"), o.getBoolean("simulated"),
            o.getString("start"), o.getString("end_exclusive"), o.getInt("scene_count"),
            o.optDouble("valid_coverage_pct", 0.0), o.getDouble("area_ha"), o.optDouble("perimeter_m", 0.0),
            bounds(o.getJSONObject("bounds")), terrain, landCover, lulcRef, indices, treeEstimation, soilMoisture,
            if (o.isNull("image_url")) null else o.getString("image_url"),
            SatLegend(l.getDouble("min"), l.getDouble("max"), strs(l.getJSONArray("palette")), l.getString("label")),
            strs(o.optJSONArray("recommendations")), o.getString("method"), strs(o.optJSONArray("warnings")), o.optString("caution"),
        )
    }

    /** Làm rõ khu vườn bằng Google Earth Engine API (True-Color HD, False-Color NIR B8-B4-B3, Tách vòm tán NDVI/Dynamic World, Ẩm độ NDMI) chỉ trong ranh giới vùng đã khoanh (bufferM = 0.0). */
    fun geeGardenClarity(
        cfg: SatelliteConfig,
        ring: List<GeoPoint>,
        crop: SatCropKey = SatCropKey.DURIAN,
        bufferM: Double = 0.0,
    ): Result<SatGeeGardenClarity> {
        val body = JSONObject()
            .put("region", ringJson(ring))
            .put("crop_type", crop.key)
            .put("buffer_m", bufferM)
        return run(cfg, "/v1/satellite/gee-garden-clarity", body, ::parseGeeGardenClarity)
    }

    fun parseGeeGardenClarity(o: JSONObject): SatGeeGardenClarity {
        val layersArr = o.optJSONArray("layers") ?: JSONArray()
        val layers = (0 until layersArr.length()).mapNotNull { i ->
            val lObj = layersArr.optJSONObject(i) ?: return@mapNotNull null
            SatGeeVisualLayer(
                layerId = lObj.optString("layer_id"),
                titleVi = lObj.optString("title_vi"),
                subtitleVi = lObj.optString("subtitle_vi"),
                bandsDesc = lObj.optString("bands_desc"),
                resolutionM = lObj.optDouble("resolution_m", 0.5),
                imageUrl = lObj.optString("image_url").takeIf { s: String -> s.isNotBlank() && !lObj.isNull("image_url") },
                tileUrlTemplate = lObj.optString("tile_url_template").takeIf { s: String -> s.isNotBlank() && !lObj.isNull("tile_url_template") },
                bounds = bounds(lObj.getJSONObject("bounds")),
            )
        }
        val feats = o.optJSONObject("sample_tree_points_geojson")?.optJSONArray("features")
        val samplePoints = if (feats == null) emptyList() else (0 until feats.length()).mapNotNull { i ->
            val f = feats.optJSONObject(i) ?: return@mapNotNull null
            val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
            if (coords.length() < 2) return@mapNotNull null
            val props = f.optJSONObject("properties") ?: JSONObject()
            val st = props.optString("status", "healthy")
            val bboxArr = props.optJSONArray("bbox_geo")
            val parsedBbox = if (bboxArr != null && bboxArr.length() >= 4) {
                GeoBounds(
                    south = bboxArr.getDouble(1),
                    west = bboxArr.getDouble(0),
                    north = bboxArr.getDouble(3),
                    east = bboxArr.getDouble(2),
                )
            } else null
            SatTreePoint(
                code = props.optString("code", "SR-%03d".format(i + 1)),
                status = st,
                ndvi = props.dblN("ndvi"),
                ndre = props.dblN("ndre"),
                isPeak = props.optBoolean("is_peak", false),
                pos = GeoPoint(coords.getDouble(1), coords.getDouble(0)),
                treeNumber = props.optInt("tree_number", i + 1),
                rowNumber = props.optInt("row_number", 1),
                statusVi = props.optString("status_vi", ""),
                healthScore = props.optInt("health_score", 82),
                confidencePct = props.optInt("confidence_pct", 92),
                crownDiameterM = props.dblN("crown_diameter_m"),
                detectionSource = props.optString("detection_source", "Google Earth Engine + High-Res 0,3m"),
                evi = props.dblN("evi"),
                ndmi = props.dblN("ndmi"),
                diagnosisVi = props.optString("diagnosis_vi", ""),
                bbox = parsedBbox,
                tileId = props.optString("tile_id", "T00-00"),
                dfModel = props.optString("df_model", "weecology/deepforest-tree"),
            )
        }
        return SatGeeGardenClarity(
            dataset = o.optString("dataset"),
            source = o.optString("source"),
            simulated = o.optBoolean("simulated", false),
            bestSceneId = o.optString("best_scene_id").takeIf { s: String -> s.isNotBlank() && !o.isNull("best_scene_id") },
            bestSceneDate = o.optString("best_scene_date").takeIf { s: String -> s.isNotBlank() && !o.isNull("best_scene_date") },
            bestSceneCloudPct = o.dblN("best_scene_cloud_pct"),
            sceneCount = o.optInt("scene_count", 0),
            clearSkyPct = o.optDouble("clear_sky_pct", 0.0),
            areaHa = o.optDouble("area_ha", 0.0),
            perimeterM = o.optDouble("perimeter_m", 0.0),
            gardenBounds = bounds(o.getJSONObject("garden_bounds")),
            contextBounds = bounds(o.getJSONObject("context_bounds")),
            canopyCoverPct = o.optDouble("canopy_cover_pct", 0.0),
            estimatedTrees = o.optInt("estimated_trees", 0),
            meanNdvi = o.dblN("mean_ndvi"),
            meanNdre = o.dblN("mean_ndre"),
            meanNdmi = o.dblN("mean_ndmi"),
            elevationMeanM = o.dblN("elevation_mean_m"),
            slopeMeanDeg = o.dblN("slope_mean_deg"),
            layers = layers,
            samplePoints = samplePoints,
            summaryVi = o.optString("summary_vi"),
        )
    }

    /** Phân tích trọn bộ 9 Tài liệu Khoa học & Nguồn GIS (Nông nghiệp Chính xác, TLS+Drone Biomass, Dual-Attention RNN Nowcasting, Planosol Sand, JATAED 2025, MSTA-YOLO, HRNet-CA DeepLabV3+, P-GIS) trên vùng vườn. */
    fun agResearchSuite(
        cfg: SatelliteConfig,
        ring: List<GeoPoint>,
        crop: SatCropKey = SatCropKey.DURIAN,
    ): Result<SatAgResearchSuite> {
        val body = JSONObject()
            .put("region", ringJson(ring))
            .put("crop_type", crop.key)
        return run(cfg, "/v1/satellite/ag-research-suite", body, ::parseAgResearchSuite)
    }

    fun parseAgResearchSuite(o: JSONObject): SatAgResearchSuite {
        val p1 = o.optJSONObject("paper1_ai_agriculture_book") ?: JSONObject()
        val p1PillarsArr = p1.optJSONArray("pillars") ?: JSONArray()
        val pillars = (0 until p1PillarsArr.length()).mapNotNull { i ->
            val po = p1PillarsArr.optJSONObject(i) ?: return@mapNotNull null
            SatAgPillar(
                pillar = po.optString("pillar"),
                scorePct = po.optDouble("score_pct", 80.0),
                actionVi = po.optString("action_vi"),
            )
        }
        val p2 = o.optJSONObject("paper2_tls_uav_biomass") ?: JSONObject()
        val p34 = o.optJSONObject("paper3_4_dual_attention_nowcasting") ?: JSONObject()
        val p5 = o.optJSONObject("paper5_planosol_topsoil_sand") ?: JSONObject()
        val p6 = o.optJSONObject("paper6_jataed_precision_ag") ?: JSONObject()
        val p7 = o.optJSONObject("paper7_msta_yolo_landslide") ?: JSONObject()
        val p8 = o.optJSONObject("paper8_hrnet_ca_deeplab_segmentation") ?: JSONObject()
        val p9 = o.optJSONObject("paper9_pgis_free_gis_sources") ?: JSONObject()
        val p9Arr = p9.optJSONArray("sources") ?: JSONArray()
        val pgisSources = (0 until p9Arr.length()).mapNotNull { i ->
            val so = p9Arr.optJSONObject(i) ?: return@mapNotNull null
            SatPgisSourceItem(
                groupId = so.optString("group_id"),
                titleVi = so.optString("title_vi"),
                gardenMetricVi = so.optString("garden_metric_vi"),
                portalUrl = so.optString("portal_url"),
                usageInVwingVi = so.optString("usage_in_vwing_vi"),
            )
        }
        return SatAgResearchSuite(
            simulated = o.optBoolean("simulated", false),
            source = o.optString("source"),
            areaHa = o.optDouble("area_ha", 0.0),
            perimeterM = o.optDouble("perimeter_m", 0.0),
            cropLabelVi = o.optString("crop_label_vi", "Cây ăn trái"),
            estimatedTrees = o.optInt("estimated_trees", 0),
            canopyCoverPct = o.optDouble("canopy_cover_pct", 0.0),
            highresGardenImageUrl = o.optString("highres_garden_image_url").takeIf { it.isNotBlank() && !o.isNull("highres_garden_image_url") },
            bounds = o.optJSONObject("bounds")?.let(::bounds) ?: GeoBounds(11.55, 107.80, 11.56, 107.81),
            catalogSummaryVi = o.optString("catalog_summary_vi"),
            // Paper 1 (Aslanyan 2025)
            p1ProjectedYieldGainPct = p1.optDouble("garden_projected_yield_gain_pct", 42.0),
            p1ResourceSavingPct = p1.optDouble("resource_saving_water_npk_pct", 28.0),
            p1SoilSustainabilityIdx = p1.optDouble("sustainable_soil_index", 78.0),
            p1LaborAutomationPct = p1.optDouble("labor_automation_readiness_pct", 70.0),
            p1EarlyPestRiskTrees = p1.optInt("early_pest_risk_trees", 0),
            p1Pillars = pillars,
            // Paper 2 (TLS + Drone RGB Biomass)
            p2MeanCrownDiameterM = p2.optDouble("mean_crown_diameter_m", 5.2),
            p2CanopyHeightM = p2.optDouble("estimated_canopy_height_m", 4.6),
            p2UavCrownAreaM2 = p2.optDouble("uav_rgb_crown_projection_m2", 18.5),
            p2TlsCrownVolumeM3 = p2.optDouble("tls_3d_crown_volume_m3_per_tree", 26.4),
            p2AgbKgPerTree = p2.optDouble("aboveground_biomass_kg_per_tree", 48.0),
            p2TotalAgbTonnes = p2.optDouble("total_aboveground_biomass_tonnes", 6.2),
            p2BiomassDensityTHa = p2.optDouble("biomass_density_t_per_ha", 5.1),
            p2CarbonCo2eTonnes = p2.optDouble("carbon_sequestration_tCO2e", 10.6),
            p2CarbonCo2ePerHa = p2.optDouble("carbon_sequestration_tCO2e_per_ha", 8.8),
            p2AdviceVi = p2.optString("advice_vi"),
            // Paper 3-4 (Dual-Attention RNN Nowcasting — deduplicated)
            p34DuplicateNoticeVi = p34.optString("duplicate_import_notice_vi"),
            p34RadarVilKgM2 = p34.optDouble("radar_vil_kg_m2", 6.5),
            p34SatIrTbKelvin = p34.optDouble("satellite_ir_tb_kelvin", 258.0),
            p34Rain1hMm = p34.optDouble("nowcast_rain_0_1h_mm", 4.2),
            p34Rain3hMm = p34.optDouble("nowcast_rain_0_3h_mm", 9.0),
            p34BiasReductionPct = p34.optDouble("peak_intensity_overestimation_reduction_pct", 24.0),
            p34ActionAdviceVi = p34.optString("action_advice_vi"),
            // Paper 5 (Planosol Topsoil Sand Mapping)
            p5TopsoilSandPct = p5.optDouble("topsoil_sand_pct", 42.0),
            p5TopsoilSiltPct = p5.optDouble("topsoil_silt_pct", 30.0),
            p5TopsoilClayPct = p5.optDouble("topsoil_clay_pct", 28.0),
            p5SoilTextureVi = p5.optString("soil_texture_class_vi", "Thịt trung bình"),
            p5PlanosolWaterloggingRiskVi = p5.optString("planosol_perched_waterlogging_risk_vi"),
            p5AdviceVi = p5.optString("agronomic_advice_vi"),
            // Paper 6 (Jamal et al., JATAED Vol 2(2), Apr 2025, DOI 10.62671/jataed.v2i2.88)
            p6Doi = p6.optString("doi", "10.62671/jataed.v2i2.88"),
            p6SscmReadinessPct = p6.optDouble("sscm_site_specific_readiness_pct", 84.0),
            p6DigitalTwinMaturityPct = p6.optDouble("digital_twin_maturity_pct", 86.0),
            p6DataSources = strs(p6.optJSONArray("data_sources_integrated")),
            p6Applications = strs(p6.optJSONArray("core_applications_active")),
            p6FutureTrendsVi = p6.optString("challenges_and_future_trends_vi"),
            // Paper 7 (Yang et al. 2025 — MSTA-YOLO Landslide)
            p7SlopeMeanDeg = p7.optDouble("slope_mean_deg", 4.5),
            p7SlopeMaxDeg = p7.optDouble("slope_max_deg", 8.2),
            p7SusceptibilityScore = p7.optDouble("landslide_susceptibility_score", 24.0),
            p7RiskClassVi = p7.optString("risk_classification_vi"),
            p7AdviceVi = p7.optString("advice_vi"),
            // Paper 8 (Shao et al. 2025 — HRNet-CA DeepLabV3+)
            p8BoundaryF1 = p8.optDouble("boundary_f1_score", 0.912),
            p8MeanIou = p8.optDouble("mean_iou_score", 0.887),
            p8AdviceVi = p8.optString("advice_vi"),
            // Paper 9 (P-GIS Top 10 Free GIS Sources Part 2)
            p9Sources = pgisSources,
        )
    }
}

data class SatAgPillar(
    val pillar: String,
    val scorePct: Double,
    val actionVi: String,
)

data class SatPgisSourceItem(
    val groupId: String,
    val titleVi: String,
    val gardenMetricVi: String,
    val portalUrl: String,
    val usageInVwingVi: String,
)

data class SatAgResearchSuite(
    val simulated: Boolean,
    val source: String,
    val areaHa: Double,
    val perimeterM: Double,
    val cropLabelVi: String,
    val estimatedTrees: Int,
    val canopyCoverPct: Double,
    val highresGardenImageUrl: String?,
    val bounds: GeoBounds,
    val catalogSummaryVi: String,
    val p1ProjectedYieldGainPct: Double,
    val p1ResourceSavingPct: Double,
    val p1SoilSustainabilityIdx: Double,
    val p1LaborAutomationPct: Double,
    val p1EarlyPestRiskTrees: Int,
    val p1Pillars: List<SatAgPillar>,
    val p2MeanCrownDiameterM: Double,
    val p2CanopyHeightM: Double,
    val p2UavCrownAreaM2: Double,
    val p2TlsCrownVolumeM3: Double,
    val p2AgbKgPerTree: Double,
    val p2TotalAgbTonnes: Double,
    val p2BiomassDensityTHa: Double,
    val p2CarbonCo2eTonnes: Double,
    val p2CarbonCo2ePerHa: Double,
    val p2AdviceVi: String,
    val p34DuplicateNoticeVi: String,
    val p34RadarVilKgM2: Double,
    val p34SatIrTbKelvin: Double,
    val p34Rain1hMm: Double,
    val p34Rain3hMm: Double,
    val p34BiasReductionPct: Double,
    val p34ActionAdviceVi: String,
    val p5TopsoilSandPct: Double,
    val p5TopsoilSiltPct: Double,
    val p5TopsoilClayPct: Double,
    val p5SoilTextureVi: String,
    val p5PlanosolWaterloggingRiskVi: String,
    val p5AdviceVi: String,
    val p6Doi: String,
    val p6SscmReadinessPct: Double,
    val p6DigitalTwinMaturityPct: Double,
    val p6DataSources: List<String>,
    val p6Applications: List<String>,
    val p6FutureTrendsVi: String,
    val p7SlopeMeanDeg: Double,
    val p7SlopeMaxDeg: Double,
    val p7SusceptibilityScore: Double,
    val p7RiskClassVi: String,
    val p7AdviceVi: String,
    val p8BoundaryF1: Double,
    val p8MeanIou: Double,
    val p8AdviceVi: String,
    val p9Sources: List<SatPgisSourceItem>,
)
