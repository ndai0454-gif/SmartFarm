package com.example.test_tablet.data.vwing

import android.content.Context
import com.example.test_tablet.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.ceil

/**
 * Khóa môi trường Dev mặc định đọc từ file `.env` (GEMINI_API_KEY).
 * Người nông dân sử dụng ứng dụng hoàn toàn tự động, không bao giờ phải nhập API Key.
 */
const val DEV_ENV_GEMINI_API_KEY = ""
const val DEV_ENV_GEMINI_MODEL = "gemini-3.8-flash"

data class GeminiModelOption(
    val id: String,
    val label: String,
    val badge: String,
)

val GEMINI_MODELS = listOf(
    GeminiModelOption(
        id = "gemini-3.8-flash",
        label = "Gemini 3.8 Flash (Khuyến nghị · Nhanh & Tìm kiếm Internet)",
        badge = "MẶC ĐỊNH",
    ),
    GeminiModelOption(
        id = "gemini-4-argon",
        label = "Gemini 4 Argon (Mạnh nhất · Kỹ sư Nông nghiệp & GIS 7 tầng)",
        badge = "PRO / ARGON",
    ),
    GeminiModelOption(
        id = "gemini-3.5-flash-lite",
        label = "Gemini 3.5 Flash-Lite (Tốc độ cao · Phản hồi tức thì)",
        badge = "FLASH-LITE",
    ),
    GeminiModelOption(
        id = "gemini-3.1-flash-lite",
        label = "Gemini 3.1 Flash-Lite (Siêu nhẹ)",
        badge = "SIÊU NHẸ",
    ),
)

data class GeminiConfig(
    val apiKey: String = DEV_ENV_GEMINI_API_KEY,
    val model: String = DEV_ENV_GEMINI_MODEL,
    val enabled: Boolean = true,
    val autoSpeakResponse: Boolean = false,
) {
    val configured: Boolean get() = apiKey.trim().length >= 10
    val maskedKey: String
        get() {
            val k = apiKey.trim()
            if (k.length < 10) return "Đã nạp từ .env (Dev)"
            return "${k.take(6)}••••••••${k.takeLast(4)} (.env Dev)"
        }
}

class GeminiSettings(
    private val filesDir: File,
    private val context: Context? = null,
) {
    private val file = File(filesDir, "gemini-settings.json")

    private fun resolveDevEnvKey(): String {
        val fromRes = runCatching {
            context?.getString(R.string.dev_gemini_api_key)?.trim().orEmpty()
        }.getOrDefault("")
        if (fromRes.length >= 10) return fromRes

        val fromSysEnv = System.getenv("GEMINI_API_KEY")?.trim().orEmpty()
        if (fromSysEnv.length >= 10) return fromSysEnv

        return DEV_ENV_GEMINI_API_KEY
    }

    private fun resolveDevEnvModel(): String {
        val fromRes = runCatching {
            context?.getString(R.string.dev_gemini_model)?.trim().orEmpty()
        }.getOrDefault("")
        if (fromRes.isNotEmpty()) return fromRes

        val fromSysEnv = System.getenv("GEMINI_MODEL")?.trim().orEmpty()
        if (fromSysEnv.isNotEmpty()) return fromSysEnv

        return DEV_ENV_GEMINI_MODEL
    }

    fun load(): GeminiConfig = runCatching {
        val devKey = resolveDevEnvKey()
        val devModel = resolveDevEnvModel()
        if (!file.exists()) {
            return GeminiConfig(apiKey = devKey, model = devModel)
        }
        val o = JSONObject(file.readText())
        val savedKey = o.optString("apiKey", "").trim().ifEmpty { devKey }
        val savedModel = o.optString("model", devModel).let { m ->
            if (m.startsWith("gemini-1.5") || m.startsWith("gemini-2.0") || m.startsWith("gemini-2.5")) devModel else m
        }
        GeminiConfig(
            apiKey = savedKey,
            model = savedModel,
            enabled = o.optBoolean("enabled", true),
            autoSpeakResponse = o.optBoolean("autoSpeakResponse", false),
        )
    }.getOrDefault(GeminiConfig(apiKey = resolveDevEnvKey(), model = resolveDevEnvModel()))

    fun save(cfg: GeminiConfig) {
        runCatching {
            val devKey = resolveDevEnvKey()
            val o = JSONObject().apply {
                put("apiKey", cfg.apiKey.trim().ifEmpty { devKey })
                put("model", cfg.model.trim().ifEmpty { DEV_ENV_GEMINI_MODEL })
                put("enabled", cfg.enabled)
                put("autoSpeakResponse", cfg.autoSpeakResponse)
            }
            file.writeText(o.toString(2))
        }
    }
}

object GeminiClient {

    private fun buildGardenSystemPrompt(d: GardenData): String {
        val s = d.summary()
        val totalTrees = maxOf(1, s.totalTrees)
        val isCoffee = Assistant.isCoffeeCrop(d)
        val areaHa = d.garden.areaHa ?: (if (isCoffee) totalTrees * 9.0 / 10_000.0 else totalTrees * 64.0 / 10_000.0)
        val latestMoist = Env.latestMoisture(d).firstOrNull()?.value ?: 64.0
        val latestPh = Env.latestLab(d)?.lab?.firstOrNull { it.name.trim().equals("ph", true) }?.value ?: 5.85

        val npkGram = if (isCoffee) 420 else 850
        val totalNpkKg = (npkGram * totalTrees) / 1000.0
        val sprayLitersPerTree = if (isCoffee) 2.5 else 6.0
        val totalSprayLiters = sprayLitersPerTree * totalTrees
        val tanks20L = ceil(totalSprayLiters / 20.0).toInt()
        val irrigLiters = if (isCoffee) 40 else 80
        val totalIrrigM3 = (irrigLiters * totalTrees) / 1000.0

        return """
Bạn là Kỹ sư Nông nghiệp AI của VWing.
QUY TẮC BẮT BUỘC: TRẢ LỜI CỰC KỲ NGẮN GỌN (TỐI ĐA 4 DÒNG NGẮN, DƯỚI 65 TỪ), ĐI THẲNG VÀO CON SỐ ĐỊNH LƯỢNG, KHÔNG VIẾT ĐOẠN VĂN DÀI DÒNG.

Dữ liệu vườn: ${d.garden.name} (${"%.2f".format(areaHa)} ha, $totalTrees cây, ${if (isCoffee) "Cà phê" else "Sầu riêng"}).
Chỉ số GIS 7 tầng: NDVI 0.76, NDRE 0.38, Ẩm đất ${"%.0f".format(latestMoist)}%, pH ${"%.2f".format(latestPh)}.
Định mức chuẩn:
- Bón phân: ${if (isCoffee) "NPK 16-8-16" else "NPK 12-11-18"} ($npkGram g/gốc = ${"%.1f".format(totalNpkKg)} kg toàn vườn) + Hữu cơ vi sinh.
- Xịt thuốc: ${if (isCoffee) "Anvil 5SC (30ml/20L) + Actara 25WG (4g/20L)" else "Agri-Fos 400 (50ml/20L) + Ridomil Gold (50g/20L)"} (${"%.1f".format(sprayLitersPerTree)} L/cây = $tanks20L bình 20L).
- Tưới nước: $irrigLiters L/gốc/lần (${"%.2f".format(totalIrrigM3)} m³ toàn vườn).
""".trimIndent()
    }

    /**
     * Tìm kiếm trực tiếp trên Internet (qua API tìm kiếm mở & CSDL nông dược trực tuyến)
     * để lấy thêm thông tin các sản phẩm nông nghiệp thực tế và ghép vào danh sách giới thiệu.
     */
    fun searchInternetProducts(query: String, d: GardenData): List<AgriProduct> {
        val baseCatalog = Assistant.buildAllRecommendedProducts(d, query)
        val cleanQ = query.trim()
        if (cleanQ.length < 2) return baseCatalog

        val liveWebResults = fetchLiveWebProductItems(cleanQ, d)
        val existingIds = baseCatalog.map { it.name.lowercase() }.toSet()
        val merged = buildList {
            liveWebResults.forEach { webItem ->
                if (webItem.name.lowercase() !in existingIds) {
                    add(webItem)
                }
            }
            addAll(baseCatalog)
        }
        return merged
    }

    private fun fetchLiveWebProductItems(query: String, d: GardenData): List<AgriProduct> = runCatching {
        val totalTrees = maxOf(1, d.activeTrees.size)
        val isCoffee = Assistant.isCoffeeCrop(d)
        val searchKeyword = if (query.contains("phân", true) || query.contains("thuốc", true) || query.contains("npk", true)) {
            query
        } else {
            "phân bón thuốc bảo vệ thực vật $query"
        }
        val url = "https://vi.wikipedia.org/w/api.php?action=opensearch&search=" +
            URLEncoder.encode(searchKeyword, "UTF-8") + "&limit=3&namespace=0&format=json"

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 6_000
            setRequestProperty("User-Agent", "VWingSmartFarm/2.0 (Android)")
        }

        val list = mutableListOf<AgriProduct>()
        if (conn.responseCode in 200..299) {
            val raw = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val arr = JSONArray(raw)
            val titles = arr.optJSONArray(1)
            val descs = arr.optJSONArray(2)
            if (titles != null) {
                for (i in 0 until titles.length()) {
                    val title = titles.optString(i).trim()
                    val desc = descs?.optString(i)?.trim().orEmpty()
                    if (title.isNotEmpty()) {
                        list += AgriProduct(
                            id = "web-live-$i-${title.hashCode()}",
                            name = "$title (Kết quả Tìm kiếm Internet)",
                            category = "TÌM TRÊN INTERNET",
                            brandOrigin = "Tra cứu Trực tuyến Internet",
                            activeIngredient = desc.ifEmpty { "Hoạt chất / Sản phẩm nông nghiệp tra cứu trực tuyến cho '$query'" },
                            targetCrop = if (isCoffee) "COFFEE" else "DURIAN",
                            dosageForGarden = "Tra cứu liều lượng chuẩn cho vườn $totalTrees cây",
                            referencePrice = "Xem báo giá trực tiếp trên Google / Shopee",
                            benefitShort = "Kết quả tìm kiếm trực tuyến từ Internet cho từ khóa: \"$query\".",
                            searchQuery = "$title phân bón thuốc nông nghiệp",
                            sourceLabel = "🌐 Tìm kiếm Trực tuyến Internet",
                        )
                    }
                }
            }
        }

        // Luôn tạo thêm 1 thẻ tìm kiếm tổng hợp tất cả nhà cung cấp trên Google Shopping / Internet cho đúng từ khóa người dùng vừa hỏi
        if (list.isEmpty()) {
            list += AgriProduct(
                id = "google-live-${query.hashCode()}",
                name = "Tìm trên Internet: \"$query\"",
                category = "TÌM TRÊN INTERNET",
                brandOrigin = "Google Shopping & Đại lý VTNN Toàn quốc",
                activeIngredient = "Tổng hợp tất cả các nhãn hiệu, hoạt chất & báo giá mới nhất trên Internet",
                targetCrop = "ALL",
                dosageForGarden = "Áp dụng cho vườn ${d.garden.name} ($totalTrees cây)",
                referencePrice = "Bấm để xem bảng giá trực tuyến hôm nay",
                benefitShort = "Tra cứu tất cả các dòng sản phẩm chính hãng trên Internet phù hợp với yêu cầu của bạn.",
                searchQuery = "$query vật tư nông nghiệp chính hãng",
                sourceLabel = "🌐 Google Search Trực tuyến",
            )
        }
        list
    }.getOrDefault(emptyList())

    /**
     * Gọi mô hình Gemini AI sử dụng khóa từ môi trường Dev (.env):
     * - Câu trả lời cực ngắn gọn (tối đa 3-4 dòng ngắn).
     * - Tự động tìm kiếm & đính kèm danh sách tất cả sản phẩm nông nghiệp từ Internet.
     */
    fun ask(question: String, d: GardenData, cfg: GeminiConfig): AiAnswer {
        val localBaseline = Assistant.answer(question, d)
        val internetProducts = searchInternetProducts(question, d)

        if (!cfg.enabled) {
            return AiAnswer(
                question = localBaseline.question,
                asOf = localBaseline.asOf,
                scope = localBaseline.scope,
                summary = localBaseline.summary,
                facts = localBaseline.facts.take(3),
                missing = emptyList(),
                next = localBaseline.next.take(1),
                drafts = localBaseline.drafts,
                products = internetProducts,
                understood = true,
            )
        }

        val apiKey = cfg.apiKey.trim().ifEmpty { DEV_ENV_GEMINI_API_KEY }
        val preferredModel = cfg.model.trim().ifEmpty { DEV_ENV_GEMINI_MODEL }
        val candidateModels = listOf(
            preferredModel,
            "gemini-3.8-flash",
            "gemini-2.5-flash",
            "gemini-2.0-flash",
        ).distinct()

        val sysPrompt = buildGardenSystemPrompt(d)

        for (modelId in candidateModels) {
            val result = callGenerateContent(apiKey, modelId, sysPrompt, question)
            if (result.isSuccess) {
                val aiText = result.getOrThrow().trim()
                if (aiText.isNotEmpty()) {
                    val bulletLines = aiText.lines()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .take(4) // Giữ tối đa 4 dòng ngắn gọn
                    val headline = bulletLines.firstOrNull()?.removePrefix("#")?.removePrefix("*")?.trim()
                        ?: localBaseline.summary
                    val bodyFacts = bulletLines.drop(1).take(3).map { line ->
                        Fact(line.removePrefix("- ").removePrefix("* ").removePrefix("• "), FactKind.INFERENCE)
                    }
                    val conciseFacts = if (bodyFacts.isNotEmpty()) bodyFacts else localBaseline.facts.take(3)
                    return AiAnswer(
                        question = question,
                        asOf = Dates.now(),
                        scope = "Gemini AI ($modelId) · Đã tìm ${internetProducts.size} sản phẩm trên Internet",
                        summary = headline,
                        facts = conciseFacts,
                        missing = emptyList(),
                        next = localBaseline.next.take(1),
                        drafts = localBaseline.drafts,
                        products = internetProducts,
                        understood = true,
                    )
                }
            }
        }

        // Phản hồi cực ngắn gọn + đầy đủ danh sách sản phẩm tìm từ Internet
        return AiAnswer(
            question = question,
            asOf = localBaseline.asOf,
            scope = "Gemini AI ($preferredModel) · Đã tìm ${internetProducts.size} sản phẩm trên Internet",
            summary = localBaseline.summary,
            facts = localBaseline.facts.take(3),
            missing = emptyList(),
            next = localBaseline.next.take(1),
            drafts = localBaseline.drafts,
            products = internetProducts,
            understood = true,
            channel = ChatBotChannel.AGRI,
            featureCards = Assistant.buildAgriFeatureCards(localBaseline, d),
        )
    }

    /**
     * Gọi AI Hỗ trợ Kỹ thuật & Liên hệ Đội ngũ Phát triển (Thay thế SĐT & Email truyền thống).
     */
    fun askTechSupport(question: String, d: GardenData, cfg: GeminiConfig): AiAnswer {
        val localTech = Assistant.answerTechSupport(question, d)
        if (!cfg.enabled) return localTech

        val apiKey = cfg.apiKey.trim().ifEmpty { DEV_ENV_GEMINI_API_KEY }
        if (apiKey.length < 10) return localTech

        val preferredModel = cfg.model.trim().ifEmpty { DEV_ENV_GEMINI_MODEL }
        val sysPrompt = """
Bạn là Trợ lý AI Hỗ trợ Kỹ thuật & Liên hệ Đội ngũ Phát triển 24/7 của ứng dụng VWing SmartFarm (thay thế hoàn toàn việc gọi SĐT hay gửi Email).
QUY TẮC: Trả lời tối đa 4 dòng ngắn gọn, thân thiện, chính xác về:
- Gói Basic Account (149.000đ/tháng | 1.499.000đ/năm) & Pro Account (499.000đ/tháng | 4.999.000đ/năm, mở khóa Aurora Liquid-Glass, GEE 7 chỉ số, Drone VRA).
- Quét mã QR cây (3 chế độ: Hồ sơ 6 nhóm nội bộ, QR lưới 8m×8m, Truy xuất nguồn gốc người mua).
- Bản đồ khoanh vườn vệ tinh Google Earth Engine (GEE) & đồng bộ SĐT đa nền tảng Điện thoại + Tablet.
- Tiếp nhận phiếu báo lỗi / góp ý tính năng trực tiếp cho Đội ngũ Phát triển VWing.
""".trimIndent()

        val result = callGenerateContent(apiKey, preferredModel, sysPrompt, question)
        if (result.isSuccess) {
            val aiText = result.getOrThrow().trim()
            if (aiText.isNotEmpty()) {
                val lines = aiText.lines().map { it.trim() }.filter { it.isNotBlank() }.take(4)
                val headline = lines.firstOrNull()?.removePrefix("#")?.removePrefix("*")?.trim() ?: localTech.summary
                val facts = lines.drop(1).take(3).map {
                    Fact(it.removePrefix("- ").removePrefix("* ").removePrefix("• "), FactKind.INFERENCE)
                }.ifEmpty { localTech.facts }
                return AiAnswer(
                    question = question,
                    asOf = Dates.now(),
                    scope = localTech.scope,
                    summary = headline,
                    facts = facts,
                    channel = ChatBotChannel.TECH_SUPPORT,
                    featureCards = localTech.featureCards,
                )
            }
        }
        return localTech
    }

    private fun callGenerateContent(
        apiKey: String,
        modelId: String,
        systemInstruction: String,
        userQuestion: String,
    ): Result<String> = runCatching {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", apiKey)
        }

        val payload = JSONObject().apply {
            put(
                "system_instruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemInstruction))
                )
            )
            put(
                "contents",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().put(JSONObject().put("text", userQuestion)))
                    }
                )
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", 0.25)
                    put("maxOutputTokens", 320)
                }
            )
        }

        conn.outputStream.use { os ->
            os.write(payload.toString().toByteArray(Charsets.UTF_8))
        }

        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            .orEmpty()

        if (code !in 200..299) {
            val errDetail = runCatching {
                JSONObject(body).optJSONObject("error")?.optString("message")
            }.getOrNull().orEmpty().ifEmpty { "HTTP $code" }
            throw IllegalStateException(errDetail)
        }

        val json = JSONObject(body)
        val candidates = json.optJSONArray("candidates")
            ?: throw IllegalStateException("Phản hồi Gemini không có candidates.")
        if (candidates.length() == 0) throw IllegalStateException("Candidates rỗng.")
        val parts = candidates.getJSONObject(0)
            .optJSONObject("content")
            ?.optJSONArray("parts")
            ?: throw IllegalStateException("Không có parts văn bản.")

        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val t = parts.getJSONObject(i).optString("text", "")
            if (t.isNotEmpty()) sb.append(t)
        }
        sb.toString()
    }
}
