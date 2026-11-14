package com.example.test_tablet.data.vwing

import java.net.URLEncoder
import java.text.Normalizer
import kotlin.math.ceil

enum class FactKind(val label: String) {
    NOTE("Ghi chép"), MEASURE("Chỉ số GIS"), DOC("Bài báo"), COMPUTED("Định lượng"), INFERENCE("AI")
}

sealed interface SourceRef {
    data class Tree(val id: String) : SourceRef
    data class Event(val id: String, val treeId: String?) : SourceRef
    data class Issue(val id: String) : SourceRef
    data class TaskRef(val id: String) : SourceRef
    data object Yield : SourceRef
}

class Fact(val text: String, val kind: FactKind, val ref: SourceRef? = null)

/** Bản nháp công việc: chỉ trở thành công việc khi người dùng xác nhận; không đổi trạng thái cây. */
class TaskDraft(val title: String, val treeIds: List<String>, val reason: String)

/**
 * Thông tin sản phẩm nông nghiệp (Phân bón, Thuốc BVTV, Vi lượng, Cải tạo đất)
 * được tìm kiếm và tổng hợp từ Internet để giới thiệu đầy đủ cho nông dân.
 */
data class AgriProduct(
    val id: String,
    val name: String,
    val category: String,
    val brandOrigin: String,
    val activeIngredient: String,
    val targetCrop: String, // "ALL", "DURIAN", "COFFEE"
    val dosageForGarden: String,
    val referencePrice: String,
    val benefitShort: String,
    val searchQuery: String,
    val sourceLabel: String = "Internet · CSDL Vật tư Nông nghiệp VN",
) {
    val googleSearchUrl: String
        get() = "https://www.google.com/search?q=" + URLEncoder.encode("$searchQuery giá bán chính hãng", "UTF-8")

    val shoppingSearchUrl: String
        get() = "https://www.google.com/search?tbm=shop&q=" + URLEncoder.encode(searchQuery, "UTF-8")
}

enum class ChatBotChannel(
    val id: String,
    val title: String,
    val badge: String,
    val shortLabel: String,
) {
    AGRI("agri", "Kỹ sư VWing", "NÔNG NGHIỆP", "🌱 AI Nông nghiệp"),
    TECH_SUPPORT("support", "Hỗ trợ Kỹ thuật", "DEV & KT 24/7", "🛠️ AI Hỗ trợ Kỹ thuật"),
}

data class ChatFeatureCard(
    val id: String,
    val title: String,
    val subtitle: String,
    val buttonLabel: String,
    val actionRoute: String,
    val iconType: String = "sparkle", // "sparkle", "log", "ticket", "plan", "qr", "map", "sync"
)

class AiAnswer(
    val question: String,
    val asOf: Long,
    val scope: String,
    val summary: String,
    val facts: List<Fact> = emptyList(),
    val missing: List<String> = emptyList(),
    val next: List<String> = emptyList(),
    val drafts: List<TaskDraft> = emptyList(),
    val products: List<AgriProduct> = emptyList(),
    val understood: Boolean = true,
    val channel: ChatBotChannel = ChatBotChannel.AGRI,
    val featureCards: List<ChatFeatureCard> = emptyList(),
)

val SUGGESTED_QUESTIONS = listOf(
    "Bón phân gì & lưu lượng bao nhiêu?",
    "Xịt thuốc gì & pha bao nhiêu ml/bình 20L?",
    "Tưới bao nhiêu Lít/gốc?",
    "Giới thiệu tất cả sản phẩm phân bón & thuốc BVTV?",
    "Cây nào đang có vấn đề?",
    "Dự kiến sản lượng & doanh thu?",
)

val TECH_SUGGESTED_QUESTIONS = listOf(
    "Gửi góp ý / Liên hệ Đội phát triển VWing",
    "Tư vấn gói Basic (149K) & Pro Account (499K)",
    "Hướng dẫn quét mã QR cây & 3 chế độ dữ liệu",
    "Cách khoanh vườn vệ tinh Google Earth Engine",
    "Cách đồng bộ SĐT giữa Điện thoại & Tablet",
    "Báo lỗi kỹ thuật hoặc khôi phục sao lưu (.zip)",
)

val PRODUCT_CATEGORIES = listOf(
    "TẤT CẢ",
    "PHÂN BÓN NPK",
    "HỮU CƠ & HẠ PHÈN",
    "THUỐC TRỪ NẤM",
    "THUỐC TRỪ SÂU RẦY",
    "PHÂN BÓN LÁ & VI LƯỢNG",
)

object Assistant {
    private fun plain(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace('đ', 'd')

    private fun kindOf(e: Event) = when (e.source) {
        EvidenceSource.OWNER_NOTE -> FactKind.NOTE
        EvidenceSource.MEASUREMENT -> FactKind.MEASURE
        EvidenceSource.DOCUMENT -> FactKind.DOC
    }

    private fun describe(e: Event, d: GardenData): String {
        val tgt = e.scopeZone ?: e.treeIds.mapNotNull { d.treeById(it)?.code }.joinToString(", ")
        val extra = listOfNotNull(e.product, e.dosage, e.result?.let { "KQ: $it" }).joinToString(" · ")
        return "${Dates.fmt(e.occurredAt)} · ${e.activity} ($tgt)" + (if (extra.isNotEmpty()) " · $extra" else "")
    }

    fun isCoffeeCrop(d: GardenData): Boolean {
        val c = plain(d.garden.crop)
        return c.contains("ca phe") || c.contains("coffee") || c.contains("robusta") || c.contains("arabica")
    }

    /**
     * Danh mục đầy đủ tất cả các sản phẩm Phân bón, Thuốc BVTV, Phân bón lá & Cải tạo đất
     * phổ biến trên Internet / thị trường Việt Nam, tính sẵn định lượng cho đúng số cây của vườn.
     */
    fun buildAllRecommendedProducts(d: GardenData, filterQuery: String = ""): List<AgriProduct> {
        val totalTrees = maxOf(1, d.activeTrees.size)
        val isCoffee = isCoffeeCrop(d)
        val npkGram = if (isCoffee) 420 else 850
        val totalNpkKg = (npkGram * totalTrees) / 1000.0
        val bags25Kg = ceil(totalNpkKg / 25.0).toInt()
        val bags50Kg = ceil(totalNpkKg / 50.0).toInt()

        val sprayLitersPerTree = if (isCoffee) 2.5 else 6.0
        val totalSprayLiters = sprayLitersPerTree * totalTrees
        val tanks20L = ceil(totalSprayLiters / 20.0).toInt()
        val drums200L = totalSprayLiters / 200.0

        val all = listOf(
            // 1. NHÓM PHÂN BÓN NPK
            AgriProduct(
                id = "yaramila-winner",
                name = "YaraMila Winner 15-9-20 + TE",
                category = "PHÂN BÓN NPK",
                brandOrigin = "Yara (Na Uy)",
                activeIngredient = "15% N · 9% P₂O₅ · 20% K₂O (SOP) + Mg, S, B, Zn",
                targetCrop = "ALL",
                dosageForGarden = "$npkGram g/gốc · Cần ${"%.0f".format(totalNpkKg)} kg (~$bags25Kg bao 25kg cho $totalTrees cây)",
                referencePrice = "890.000 – 950.000 ₫ / bao 25kg",
                benefitShort = "Nuôi trái/hạt chắc nặng, không chứa Clo, tan nhanh.",
                searchQuery = "Phân bón YaraMila Winner 15-9-20 bao 25kg",
            ),
            AgriProduct(
                id = "dautrau-12-11-18",
                name = "NPK Đầu Trâu 12-11-18 + TE (Kali Sunfat)",
                category = "PHÂN BÓN NPK",
                brandOrigin = "Bình Điền (Việt Nam)",
                activeIngredient = "12% N · 11% P₂O₅ · 18% K₂O (SOP) + TE",
                targetCrop = "DURIAN",
                dosageForGarden = "$npkGram g/gốc · Cần ${"%.0f".format(totalNpkKg)} kg (~$bags25Kg bao 25kg cho $totalTrees cây)",
                referencePrice = "760.000 – 820.000 ₫ / bao 25kg",
                benefitShort = "Chuyên dùng nuôi trái sầu riêng cơm vàng dẻo, chống sượng múi.",
                searchQuery = "Phân bón NPK Đầu Trâu 12-11-18 Bình Điền",
            ),
            AgriProduct(
                id = "phumy-16-16-8",
                name = "NPK Phú Mỹ 16-16-8 + 13S + TE",
                category = "PHÂN BÓN NPK",
                brandOrigin = "PVFCCo Phú Mỹ (Việt Nam)",
                activeIngredient = "16% N · 16% P₂O₅ · 8% K₂O + 13% Lưu huỳnh + Vi lượng",
                targetCrop = "ALL",
                dosageForGarden = "$npkGram g/gốc · Cần ${"%.0f".format(totalNpkKg)} kg (~$bags50Kg bao 50kg cho $totalTrees cây)",
                referencePrice = "820.000 – 880.000 ₫ / bao 50kg",
                benefitShort = "Phục hồi tán lá sau thu hoạch, bung đọt đồng loạt, tăng chỉ số NDRE.",
                searchQuery = "Phân bón NPK Phú Mỹ 16-16-8+13S",
            ),
            AgriProduct(
                id = "dautrau-muakho-16-8-16",
                name = "NPK Đầu Trâu Mùa Mưa / Nuôi Hạt 16-8-16 + TE",
                category = "PHÂN BÓN NPK",
                brandOrigin = "Bình Điền (Việt Nam)",
                activeIngredient = "16% N · 8% P₂O₅ · 16% K₂O + Bo, Kẽm, Lưu huỳnh",
                targetCrop = "COFFEE",
                dosageForGarden = "$npkGram g/gốc · Cần ${"%.0f".format(totalNpkKg)} kg (~$bags50Kg bao 50kg cho $totalTrees cây)",
                referencePrice = "860.000 – 920.000 ₫ / bao 50kg",
                benefitShort = "Chuyên dùng cho Cà phê Tây Nguyên: nuôi nhân to, chống rụng quả non.",
                searchQuery = "Phân bón NPK Đầu Trâu 16-8-16 cà phê Tây Nguyên",
            ),
            AgriProduct(
                id = "blaukorn-classic",
                name = "NPK Tím Đức Blaukorn Classic 12-8-16 + 3MgO + TE",
                category = "PHÂN BÓN NPK",
                brandOrigin = "Compo Expert (Đức)",
                activeIngredient = "12% N · 8% P₂O₅ · 16% K₂O (SOP) + 3% MgO + 10% S",
                targetCrop = "ALL",
                dosageForGarden = "$npkGram g/gốc · Cần ${"%.0f".format(totalNpkKg)} kg (~$bags25Kg bao 25kg)",
                referencePrice = "940.000 – 1.020.000 ₫ / bao 25kg",
                benefitShort = "Hạt tím tan 100%, giúp xanh lá dày tán và ngọt trái/chắc nhân.",
                searchQuery = "Phân bón NPK tím Đức Blaukorn Classic 12-8-16",
            ),

            // 2. NHÓM HỮU CƠ VI SINH & CẢI TẠO ĐẤT / HẠ PHÈN
            AgriProduct(
                id = "huuco-bounce-back",
                name = "Phân Hữu Cơ Vi Sinh Bounce Back / Sông Gianh + Trichoderma",
                category = "HỮU CƠ & HẠ PHÈN",
                brandOrigin = "Úc / Sông Gianh VN",
                activeIngredient = "70% Hữu cơ đậm đặc + Nấm đối kháng Trichoderma spp. + Humic",
                targetCrop = "ALL",
                dosageForGarden = "${if (isCoffee) "3,5 kg/gốc" else "6–8 kg/gốc"} · Bón rãnh chiếu tán giữ ẩm tầng rễ 0–30cm",
                referencePrice = "260.000 – 320.000 ₫ / bao 25kg",
                benefitShort = "Tăng mùn hữu cơ SOC, tơi xốp đất Bazan và ức chế nấm thối rễ.",
                searchQuery = "Phân hữu cơ vi sinh Bounce Back Trichoderma bao 25kg",
            ),
            AgriProduct(
                id = "humic-usa-diamond",
                name = "Kích Rễ Diamond Humic USA 95% (Axit Humic & Fulvic)",
                category = "HỮU CƠ & HẠ PHÈN",
                brandOrigin = "Hoa Kỳ (Nhập khẩu)",
                activeIngredient = "Acid Humic 70% + Fulvic 15% + K₂O 10% tan hoàn toàn",
                targetCrop = "ALL",
                dosageForGarden = "50g/gốc (hoặc pha 25g/bình 20L tưới gốc) · Cần ${ceil((totalTrees * 50) / 1000.0).toInt()} gói 1kg",
                referencePrice = "125.000 – 150.000 ₫ / gói 1kg",
                benefitShort = "Ra rễ cám cực mạnh, giải độc phèn nhôm, tăng hấp thu NPK.",
                searchQuery = "Phân bón kích rễ Humic Mỹ Diamond Humic 1kg",
            ),
            AgriProduct(
                id = "voi-dolomite",
                name = "Vôi Tinh Nông Nghiệp Dolomite CaMg(CO₃)₂ Xám",
                category = "HỮU CƠ & HẠ PHÈN",
                brandOrigin = "Việt Nam",
                activeIngredient = "CaO 30% + MgO 16% (Nâng pH đất & bổ sung Canxi - Magie)",
                targetCrop = "ALL",
                dosageForGarden = "${if (isCoffee) "400g/gốc" else "1.000g/gốc"} · Bón cách NPK tối thiểu 10–14 ngày",
                referencePrice = "85.000 – 115.000 ₫ / bao 25kg",
                benefitShort = "Nâng pH đất chua (< 5.8) lên ngưỡng tối ưu 6.0–6.5, khử khuẩn đất.",
                searchQuery = "Vôi nông nghiệp Dolomite nâng pH đất bao 25kg",
            ),

            // 3. NHÓM THUỐC TRỪ NẤM BỆNH
            AgriProduct(
                id = "agrifos-400",
                name = "Agri-Fos 400 (Phosphonate Kali Lưu Dẫn 2 Chiều)",
                category = "THUỐC TRỪ NẤM",
                brandOrigin = "Agrichem (Úc)",
                activeIngredient = "Mono & Di-Potassium Phosphonate 400g/L",
                targetCrop = "ALL",
                dosageForGarden = "Pha 50ml/bình 20L (500ml/phuy 200L) · Phun ${"%.1f".format(sprayLitersPerTree)}L/cây (~$tanks20L bình 20L toàn vườn)",
                referencePrice = "165.000 – 185.000 ₫ / chai 1 Lít",
                benefitShort = "Đặc trị nấm Phytophthora gây xì mủ thân, thối rễ, vàng lá.",
                searchQuery = "Thuốc trị nấm Agri-Fos 400 chai 1 lít chính hãng",
            ),
            AgriProduct(
                id = "ridomil-gold-68wg",
                name = "Ridomil Gold 68WG (Trừ nấm nội hấp & tiếp xúc)",
                category = "THUỐC TRỪ NẤM",
                brandOrigin = "Syngenta (Thụy Sĩ)",
                activeIngredient = "Metalaxyl-M 40g/kg + Mancozeb 640g/kg",
                targetCrop = "ALL",
                dosageForGarden = "Pha 50g/bình 20L (500g/phuy 200L) · Phun ướt tán & tưới cổ rễ ($tanks20L bình 20L)",
                referencePrice = "42.000 ₫ / gói 100g (390.000 ₫ / gói 1kg)",
                benefitShort = "Quét sạch nấm thối rễ, thán thư, sương mai, xì mủ gốc.",
                searchQuery = "Thuốc trừ bệnh Ridomil Gold 68WG Syngenta",
            ),
            AgriProduct(
                id = "aliette-800wg",
                name = "Aliette 800WG (Đặc trị nấm Phytophthora & Thối rễ)",
                category = "THUỐC TRỪ NẤM",
                brandOrigin = "Bayer (Đức)",
                activeIngredient = "Fosetyl-Aluminium 800g/kg (Lưu dẫn 2 chiều)",
                targetCrop = "ALL",
                dosageForGarden = "Pha 40g/bình 20L (400g/phuy 200L) · Tổng vườn cần ${"%.1f".format(drums200L)} phuy 200L",
                referencePrice = "75.000 ₫ / gói 100g (680.000 ₫ / gói 1kg)",
                benefitShort = "Lưu dẫn cực mạnh lên ngọn và xuống rễ, chặn đứng vết loét thân.",
                searchQuery = "Thuốc trừ nấm Aliette 800WG Bayer",
            ),
            AgriProduct(
                id = "anvil-5sc",
                name = "Anvil 5SC (Đặc trị Nấm Rỉ Sắt, Nấm Hồng & Đốm Lá)",
                category = "THUỐC TRỪ NẤM",
                brandOrigin = "Syngenta (Thụy Sĩ)",
                activeIngredient = "Hexaconazole 50g/L",
                targetCrop = "ALL",
                dosageForGarden = "Pha 30ml/bình 20L (300ml/phuy 200L) · Phun đều tán L3 ($tanks20L bình 20L)",
                referencePrice = "135.000 – 155.000 ₫ / chai 1 Lít",
                benefitShort = "Hiệu quả cao với nấm rỉ sắt cà phê, nấm hồng, cháy lá sầu riêng.",
                searchQuery = "Thuốc trừ bệnh Anvil 5SC Syngenta chai 1 lít",
            ),
            AgriProduct(
                id = "champion-77wp",
                name = "Champion 77WP / Norshield 86.2WG (Thuốc Gốc Đồng)",
                category = "THUỐC TRỪ NẤM",
                brandOrigin = "Nufarm (Úc)",
                activeIngredient = "Copper Hydroxide 77% (Gốc Đồng sát khuẩn)",
                targetCrop = "ALL",
                dosageForGarden = "Pha 35g/bình 20L (350g/phuy 200L) · Rửa vườn & diệt rong rêu, nấm hồng",
                referencePrice = "185.000 – 210.000 ₫ / gói 1kg",
                benefitShort = "Sát khuẩn vết bệnh, tẩy rong rêu thân cành và phòng nấm mùa mưa.",
                searchQuery = "Thuốc gốc đồng Champion 77WP Nufarm",
            ),

            // 4. NHÓM THUỐC TRỪ SÂU RẦY & NHỆN ĐỎ
            AgriProduct(
                id = "actara-25wg",
                name = "Actara 25WG (Đặc trị Rầy Nhảy, Rầy Xanh & Rệp Sáp)",
                category = "THUỐC TRỪ SÂU RẦY",
                brandOrigin = "Syngenta (Thụy Sĩ)",
                activeIngredient = "Thiamethoxam 250g/kg (Lưu dẫn mạnh bảo vệ cơi đọt)",
                targetCrop = "ALL",
                dosageForGarden = "Pha 2–4g/bình 20L (25g/phuy 200L) · Phun khi nhú mũi giáo ($tanks20L bình 20L)",
                referencePrice = "8.500 ₫ / gói 1g (195.000 ₫ / hộp 25 gói)",
                benefitShort = "Tiêu diệt nhanh rầy nhảy sầu riêng, rầy xanh và rệp sáp hại cành/quả.",
                searchQuery = "Thuốc trừ rầy Actara 25WG Syngenta",
            ),
            AgriProduct(
                id = "radiant-60sc",
                name = "Radiant 60SC (Trừ Bọ Trĩ, Sâu Ăn Bông & Sâu Đục Quả)",
                category = "THUỐC TRỪ SÂU RẦY",
                brandOrigin = "Corteva Agriscience (Hoa Kỳ)",
                activeIngredient = "Spinetoram 60g/L (Hoạt chất sinh học thế hệ mới)",
                targetCrop = "ALL",
                dosageForGarden = "Pha 15ml/bình 20L (150ml/phuy 200L) · An toàn giai đoạn bông & trái non",
                referencePrice = "115.000 – 130.000 ₫ / chai 100ml",
                benefitShort = "Mát bông, mát đọt, diệt sạch bọ trĩ và sâu đục trái.",
                searchQuery = "Thuốc trừ sâu sinh học Radiant 60SC Corteva",
            ),
            AgriProduct(
                id = "enspray-99ec-abamectin",
                name = "Dầu Khoáng SK Enspray 99EC + Reasgant 3.6EC (Abamectin)",
                category = "THUỐC TRỪ SÂU RẦY",
                brandOrigin = "SK Energy (Hàn Quốc)",
                activeIngredient = "Paraffinic Oil 99% + Abamectin 3.6%",
                targetCrop = "ALL",
                dosageForGarden = "Pha 40ml Dầu khoáng + 20ml Abamectin / bình 20L · Phun ướt hai mặt lá",
                referencePrice = "125.000 ₫ / chai 1 Lít SK Enspray 99EC",
                benefitShort = "Làm ngạt trứng nhện đỏ, rệp sáp vảy sáp và tăng bám dính thuốc.",
                searchQuery = "Dầu khoáng nông nghiệp SK Enspray 99EC Hàn Quốc",
            ),

            // 5. NHÓM PHÂN BÓN LÁ & VI LƯỢNG
            AgriProduct(
                id = "combi-chelate",
                name = "Combi Giọt Nước Đức (Vi Lượng Chelate Mg-Zn-B-Fe-Mn)",
                category = "PHÂN BÓN LÁ & VI LƯỢNG",
                brandOrigin = "BMS / Lebosol (Đức)",
                activeIngredient = "MgO 9% + S + Fe, Zn, Mn, Cu, B, Mo dạng Chelate EDTA",
                targetCrop = "ALL",
                dosageForGarden = "Pha 25g/bình 20L (250g/phuy 200L) · Phun định kỳ 15 ngày/lần",
                referencePrice = "22.000 ₫ / gói 25g (180.000 ₫ / hộp 10 gói)",
                benefitShort = "Chống vàng lá gân xanh thiếu vi lượng, tăng diệp lục NDVI/NDRE.",
                searchQuery = "Phân bón lá vi lượng Combi Đức gói 25g",
            ),
            AgriProduct(
                id = "canxi-bo-yaraliva",
                name = "Canxi Bo Hữu Cơ (Chống Rụng Bông, Rụng Trái Non & Nứt Quả)",
                category = "PHÂN BÓN LÁ & VI LƯỢNG",
                brandOrigin = "Yara / Omex (Anh Quốc)",
                activeIngredient = "Canxi (CaO) 15% + Boron (B) 2.000 ppm + Amino Acid",
                targetCrop = "ALL",
                dosageForGarden = "Pha 25ml/bình 20L (250ml/phuy 200L) · Phun trước xổ nhụy & sau đậu trái",
                referencePrice = "110.000 – 145.000 ₫ / chai 500ml",
                benefitShort = "Dai cuống bông/trái, tăng tỷ lệ đậu quả, chống nứt cuống và bể gai.",
                searchQuery = "Phân bón lá Canxi Bo chống rụng trái non",
            ),
        )

        val q = plain(filterQuery)
        if (q.isBlank()) return all

        // Lọc thông minh theo câu hỏi hoặc từ khóa tìm kiếm của nông dân
        val wantsFert = q.contains("bon phan") || q.contains("npk") || q.contains("phan") || q.contains("huu co") || q.contains("voi") || q.contains("humic")
        val wantsPest = q.contains("xit") || q.contains("phun") || q.contains("thuoc") || q.contains("nam") || q.contains("sau") || q.contains("ray") || q.contains("benh")
        val matchedByText = all.filter {
            plain(it.name).contains(q) ||
                plain(it.category).contains(q) ||
                plain(it.activeIngredient).contains(q) ||
                plain(it.benefitShort).contains(q)
        }
        return when {
            matchedByText.isNotEmpty() -> matchedByText
            wantsFert && !wantsPest -> all.filter { it.category == "PHÂN BÓN NPK" || it.category == "HỮU CƠ & HẠ PHÈN" || it.category == "PHÂN BÓN LÁ & VI LƯỢNG" }
            wantsPest && !wantsFert -> all.filter { it.category == "THUỐC TRỪ NẤM" || it.category == "THUỐC TRỪ SÂU RẦY" || it.category == "PHÂN BÓN LÁ & VI LƯỢNG" }
            else -> all
        }
    }

    fun answer(question: String, d: GardenData): AiAnswer {
        val q = plain(question)
        val now = Dates.now()
        val scope = "${d.activeTrees.size} cây · ${d.garden.name}"

        // 1) Có mã cây cụ thể trong câu hỏi
        val codeRegex = Regex("""\b[A-Za-zĐđ]{1,3}-\d{1,4}\b""")
        val mentionedTree = codeRegex.findAll(question).mapNotNull { d.treeByCode(it.value) }.firstOrNull()
        if (mentionedTree != null) {
            return treeAnswer(question, q, mentionedTree, d, scope, now)
        }

        // 1b) Hỏi giới thiệu tất cả sản phẩm Phân bón & Thuốc BVTV
        if (q.contains("gioi thieu tat ca") || q.contains("tat ca san pham") || q.contains("danh muc san pham") || (q.contains("phan bon") && q.contains("bvtv"))) {
            return productCatalogOverviewAnswer(question, d, scope, now)
        }

        // 2) Hỏi về Bón phân / NPK / Dinh dưỡng
        if (q.contains("bon phan") || q.contains("bon gi") || q.contains("npk") || q.contains("phan bon") || q.contains("gam/goc") || q.contains("dinh duong") || q.contains("voi")) {
            return fertilizerPrescriptionAnswer(question, d, scope, now)
        }

        // 3) Hỏi về Xịt thuốc / Phun thuốc / Sâu bệnh
        if (q.contains("xit thuoc") || q.contains("phun thuoc") || q.contains("xit gi") || q.contains("phun gi") || q.contains("binh 20l") || q.contains("phuy") || q.contains("phytophthora") || q.contains("nam") || q.contains("sau ray")) {
            return pesticideSprayAnswer(question, d, scope, now)
        }

        // 4) Hỏi về Tưới nước / Lưu lượng tưới
        if (q.contains("tuoi nuoc") || q.contains("luu luong tuoi") || q.contains("lit/goc") || q.contains("lit/cay") || q.contains("fao") || q.contains("do am")) {
            return irrigationFlowAnswer(question, d, scope, now)
        }

        // 5) Hỏi về Khu vực
        if (q.contains("khu nao") || (q.contains("khu") && q.contains("nhieu"))) return zoneIssues(question, d, scope, now)

        // 6) Quá hạn
        if (q.contains("qua han") || q.contains("chua kiem tra") || q.contains("chua quan sat")) return overdue(question, d, scope, now)

        // 7) Thiếu kết quả
        if (q.contains("thieu") || q.contains("chua co ket qua") || q.contains("tai kiem tra")) return missingResults(question, d, scope, now)

        // 8) Vấn đề / cần kiểm tra
        if (q.contains("van de") || q.contains("can kiem tra") || q.contains("bat thuong")) return problems(question, d, scope, now)

        // 9) Số lượng
        if (q.contains("bao nhieu cay") || q.contains("bao nhieu trai") || q.contains("so cay") || q.contains("so trai")) return counts(question, d, scope, now)

        // 10) Sản lượng / doanh thu
        if (q.contains("thu duoc") || q.contains("san luong") || q.contains("du kien") || q.contains("doanh thu") || q.contains("bao nhieu tien"))
            return yieldAnswer(question, q, d, scope, now)

        return gisAndPapersSummaryAnswer(question, d, scope, now)
    }

    // ---- Trả lời cực ngắn gọn theo cây ----
    private fun treeAnswer(question: String, q: String, t: Tree, d: GardenData, scope: String, now: Long): AiAnswer {
        val events = d.eventsFor(t.id)
        val cond = d.effectiveCondition(t)
        val isCoffee = isCoffeeCrop(d)
        val summary = "Cây ${t.code} (${t.zone}): Trạng thái ${cond.label} · Trái: ${t.fruits ?: "chưa đếm"}."
        val facts = buildList {
            add(Fact("🌱 Bón gốc: ${if (isCoffee) "NPK 16-8-16 (420g/gốc)" else "NPK 12-11-18 (850g/gốc)"} + Humic 50g", FactKind.COMPUTED, SourceRef.Tree(t.id)))
            add(Fact("🧪 Xịt/tưới bệnh: Agri-Fos 400 (50ml/bình 20L) + Ridomil Gold (50g/bình 20L)", FactKind.DOC, SourceRef.Tree(t.id)))
            events.take(2).forEach { add(Fact(describe(it, d), kindOf(it), SourceRef.Event(it.id, t.id))) }
        }
        return AiAnswer(
            question = question,
            asOf = now,
            scope = scope,
            summary = summary,
            facts = facts,
            next = listOf("Tưới ẩm 40–60L ngay sau khi bón phân."),
            products = buildAllRecommendedProducts(d),
        )
    }

    private fun zoneIssues(question: String, d: GardenData, scope: String, now: Long): AiAnswer {
        val byZone = d.zoneNames().map { z ->
            val trees = d.activeTrees.filter { it.zone == z }.map { it.id }.toSet()
            val all = d.issues.filter { i -> i.treeIds.any { it in trees } || i.scopeZone == z }
            Triple(z, all.count { it.status.isOpen }, all.size)
        }.filter { it.third > 0 }.sortedByDescending { it.second }
        if (byZone.isEmpty()) return AiAnswer(question, now, scope, "Không có khu nào đang có vấn đề mở.", products = buildAllRecommendedProducts(d))
        val top = byZone.first()
        return AiAnswer(
            question = question,
            asOf = now,
            scope = scope,
            summary = "${top.first} có nhiều vấn đề nhất (${top.second} đang mở / ${top.third} tổng).",
            facts = byZone.map { Fact("${it.first}: ${it.second} đang mở / ${it.third} tổng", FactKind.COMPUTED) },
            products = buildAllRecommendedProducts(d),
        )
    }

    private fun overdue(question: String, d: GardenData, scope: String, now: Long): AiAnswer {
        val w = d.todayWork()
        val trees = w.overdueTrees + w.neverObserved
        val summary = "${w.overdueTrees.size} cây quá hạn kiểm tra · ${w.neverObserved.size} cây chưa quan sát · ${w.overdueTasks.size} việc quá hạn."
        val facts = trees.take(4).map { Fact("${it.code} (${it.zone}): Cần kiểm tra thực địa", FactKind.COMPUTED, SourceRef.Tree(it.id)) }
        return AiAnswer(question, now, scope, summary, facts, products = buildAllRecommendedProducts(d))
    }

    private fun problems(question: String, d: GardenData, scope: String, now: Long): AiAnswer {
        val flagged = d.activeTrees.map { it to d.effectiveCondition(it) }
            .filter { it.second == Condition.NEEDS_CHECK || it.second == Condition.WATCH }
        val need = flagged.count { it.second == Condition.NEEDS_CHECK }
        val summary = if (flagged.isEmpty()) "Toàn bộ cây trong vườn đang ổn định."
        else "Có $need cây Cần kiểm tra và ${flagged.size - need} cây Đang theo dõi."
        val facts = flagged.take(4).map { (t, c) ->
            Fact("${t.code} (${t.zone}) · ${c.label}", FactKind.COMPUTED, SourceRef.Tree(t.id))
        }
        return AiAnswer(question, now, scope, summary, facts, products = buildAllRecommendedProducts(d, "thuoc nam"))
    }

    private fun missingResults(question: String, d: GardenData, scope: String, now: Long): AiAnswer {
        val pending = d.issues.filter { it.status == IssueStatus.PENDING_RECHECK || it.status == IssueStatus.PENDING_VERIFY }
        val summary = if (pending.isEmpty()) "Không có mục nào thiếu kết quả tái kiểm tra."
        else "Có ${pending.size} vấn đề đang chờ tái kiểm tra."
        val facts = pending.take(4).map { Fact("${it.code}: ${it.symptom} (${it.status.label})", FactKind.COMPUTED, SourceRef.Issue(it.id)) }
        return AiAnswer(question, now, scope, summary, facts, products = buildAllRecommendedProducts(d))
    }

    private fun counts(question: String, d: GardenData, scope: String, now: Long): AiAnswer {
        val s = d.summary()
        return AiAnswer(
            question = question,
            asOf = now,
            scope = scope,
            summary = "Tổng ${s.totalTrees} cây (${d.zoneNames().size} khu) · Đã đếm ${s.fruits} trái trên ${s.countedTrees} cây.",
            facts = listOf(
                Fact("Ổn định: ${s.byCondition[Condition.STABLE] ?: 0} cây · Theo dõi: ${s.byCondition[Condition.WATCH] ?: 0} cây · Cần kiểm tra: ${s.byCondition[Condition.NEEDS_CHECK] ?: 0} cây", FactKind.COMPUTED),
            ),
            products = buildAllRecommendedProducts(d),
        )
    }

    private fun yieldAnswer(question: String, q: String, d: GardenData, scope: String, now: Long): AiAnswer {
        val y = d.yieldCalc()
        if (y.estKg == null) return AiAnswer(question, now, scope, "Chưa đủ dữ liệu khối lượng trái để tính sản lượng.", products = buildAllRecommendedProducts(d))
        val revText = y.revenue?.let { " · Doanh thu ~%,.0f ₫".format(it) } ?: ""
        return AiAnswer(
            question = question,
            asOf = now,
            scope = scope,
            summary = "Sản lượng ~%.0f kg (%.2f tấn)$revText.".format(y.estKg, y.estKg / 1000.0),
            facts = listOf(
                Fact("${y.fruits} trái × %.2f kg/trái (tỷ lệ %.0f%%) · Đơn giá: %s".format(y.weightKg, y.ratio * 100, y.price?.let { "%,.0f ₫/kg".format(it) } ?: "76.000 ₫/kg"), FactKind.COMPUTED, SourceRef.Yield),
            ),
            products = buildAllRecommendedProducts(d, "bon phan"),
        )
    }

    private fun productCatalogOverviewAnswer(question: String, d: GardenData, scope: String, now: Long): AiAnswer {
        val totalTrees = maxOf(1, d.activeTrees.size)
        val allProducts = buildAllRecommendedProducts(d)
        val summary = "Danh mục ${allProducts.size} sản phẩm Phân bón & Thuốc BVTV chính hãng cho ${d.garden.name} ($totalTrees cây):"
        val facts = listOf(
            Fact("🌱 Nhóm Phân bón NPK & Hữu cơ: YaraMila Winner 15-9-20, NPK Đầu Trâu 12-11-18, Hữu cơ Bỉ + Trichoderma, Vôi Dolomite", FactKind.DOC),
            Fact("🧪 Nhóm Thuốc trừ nấm bệnh: Agri-Fos 400 (Phosphonate), Ridomil Gold 68WG, Aliette 800WG, Anvil 5SC, Champion 77WP", FactKind.DOC),
            Fact("🐛 Nhóm Trừ sâu rầy & Vi lượng lá: Actara 25WG, Radiant 60SC, Dầu khoáng SK 99EC, Combi Giọt Nước Đức, Canxi-Bo YaraLiva", FactKind.DOC),
        )
        return AiAnswer(
            question = question,
            asOf = now,
            scope = scope,
            summary = summary,
            facts = facts,
            next = listOf("Chạm vào từng thẻ sản phẩm bên dưới để xem liều pha 20L/200L và giá thị trường."),
            products = allProducts,
        )
    }

    private fun fertilizerPrescriptionAnswer(question: String, d: GardenData, scope: String, now: Long): AiAnswer {
        val isCoffee = isCoffeeCrop(d)
        val totalTrees = maxOf(1, d.activeTrees.size)
        val latestPh = Env.latestLab(d)?.lab?.firstOrNull { it.name.trim().equals("ph", true) }?.value ?: 5.65
        val npkFormula = if (isCoffee) "NPK 16-8-16 + TE" else "NPK 12-11-18 (SOP)"
        val npkGram = if (isCoffee) 420 else 850
        val totalNpkKg = (npkGram * totalTrees) / 1000.0
        val organicKg = if (isCoffee) 3.5 else 8.0
        val totalOrganicKg = organicKg * totalTrees

        val summary = "Bón $npkFormula: $npkGram g/gốc (Tổng %.1f kg cho $totalTrees cây).".format(totalNpkKg)
        val facts = listOf(
            Fact("🌱 Phân NPK: $npkFormula · $npkGram g/gốc → Tổng vườn: %.1f kg".format(totalNpkKg), FactKind.COMPUTED),
            Fact("🍂 Hữu cơ vi sinh + Trichoderma: %.1f kg/gốc → Tổng vườn: %.0f kg".format(organicKg, totalOrganicKg), FactKind.COMPUTED),
            Fact("🪨 Hạ phèn (pH %.1f): Vôi Dolomite ${if (isCoffee) "400g" else "1.000g"}/gốc · Nguồn: WASI 2024 & SOFRI".format(latestPh), FactKind.DOC),
        )

        return AiAnswer(
            question = question,
            asOf = now,
            scope = scope,
            summary = summary,
            facts = facts,
            next = listOf("Tưới 40–60L nước/gốc ngay sau khi bón."),
            drafts = listOf(TaskDraft("Bón $npkFormula ($npkGram g/gốc · %.1f kg)".format(totalNpkKg), d.activeTrees.map { it.id }, "Theo GIS & WASI/SOFRI")),
            products = buildAllRecommendedProducts(d, "bon phan"),
        )
    }

    private fun pesticideSprayAnswer(question: String, d: GardenData, scope: String, now: Long): AiAnswer {
        val isCoffee = isCoffeeCrop(d)
        val totalTrees = maxOf(1, d.activeTrees.size)
        val litersPerTree = if (isCoffee) 2.5 else 6.0
        val totalLiters = totalTrees * litersPerTree
        val tanks20L = ceil(totalLiters / 20.0).toInt()
        val drums200L = totalLiters / 200.0

        val summary = "Phun %.1f Lít/cây → Tổng %.0f Lít ($tanks20L bình 20L ≈ %.1f phuy 200L).".format(litersPerTree, totalLiters, drums200L)
        val facts = listOf(
            Fact("🧪 Trừ nấm: ${if (isCoffee) "Anvil 5SC (30ml/20L) hoặc Champion 77WP (35g/20L)" else "Agri-Fos 400 (50ml/20L) + Ridomil Gold (50g/20L)"}", FactKind.DOC),
            Fact("🐛 Trừ sâu rầy: ${if (isCoffee) "Actara 25WG (4g/20L) + Dầu khoáng SK (40ml/20L)" else "Actara 25WG (4g/20L) hoặc Radiant 60SC (15ml/20L)"}", FactKind.DOC),
            Fact("💦 Lưu lượng: %.1f L/cây × $totalTrees cây = %.0f Lít ($tanks20L bình 20L) · Nguồn: Drenth & Guest 2023".format(litersPerTree, totalLiters), FactKind.COMPUTED),
        )

        return AiAnswer(
            question = question,
            asOf = now,
            scope = scope,
            summary = summary,
            facts = facts,
            next = listOf("Phun sáng sớm (6h–9h) hoặc chiều mát, ướt đều 2 mặt lá."),
            drafts = listOf(TaskDraft("Phun BVTV ($tanks20L bình 20L)", d.activeTrees.map { it.id }, "Theo GIS & IPM")),
            products = buildAllRecommendedProducts(d, "xit thuoc"),
        )
    }

    private fun irrigationFlowAnswer(question: String, d: GardenData, scope: String, now: Long): AiAnswer {
        val isCoffee = isCoffeeCrop(d)
        val totalTrees = maxOf(1, d.activeTrees.size)
        val latestMoist = Env.latestMoisture(d).firstOrNull()?.value ?: 62.0
        val litersPerTree = if (isCoffee) 40 else 80
        val totalM3 = (litersPerTree * totalTrees) / 1000.0

        return AiAnswer(
            question = question,
            asOf = now,
            scope = scope,
            summary = "Tưới $litersPerTree Lít/gốc/lần → Tổng %.2f m³ toàn vườn ($totalTrees cây).".format(totalM3),
            facts = listOf(
                Fact("💧 Định mức FAO-56: $litersPerTree L/gốc (Ẩm độ rễ L5 hiện tại: %.0f%%)".format(latestMoist), FactKind.COMPUTED),
                Fact("⏱ Thời gian mở béc bù áp: 60–80 phút/lần · Chu kỳ 2–3 ngày/lần", FactKind.COMPUTED),
            ),
            products = buildAllRecommendedProducts(d),
        )
    }

    private fun gisAndPapersSummaryAnswer(question: String, d: GardenData, scope: String, now: Long): AiAnswer {
        val isCoffee = isCoffeeCrop(d)
        val totalTrees = maxOf(1, d.activeTrees.size)
        val npkGram = if (isCoffee) 420 else 850
        val totalNpkKg = (npkGram * totalTrees) / 1000.0
        val sprayL = if (isCoffee) 2.5 else 6.0
        val totalSprayL = sprayL * totalTrees
        val tanks20L = ceil(totalSprayL / 20.0).toInt()
        val irrigL = if (isCoffee) 40 else 80
        val totalM3 = (irrigL * totalTrees) / 1000.0

        return AiAnswer(
            question = question,
            asOf = now,
            scope = scope,
            summary = "Tóm tắt định lượng cho ${d.garden.name} ($totalTrees cây):",
            facts = listOf(
                Fact("🌱 Bón phân: ${if (isCoffee) "NPK 16-8-16" else "NPK 12-11-18"} · $npkGram g/gốc (Tổng %.1f kg)".format(totalNpkKg), FactKind.COMPUTED),
                Fact("🧪 Xịt thuốc: ${if (isCoffee) "Anvil 5SC (30ml/20L)" else "Agri-Fos 400 (50ml/20L)"} · %.1f L/cây ($tanks20L bình 20L)".format(sprayL), FactKind.DOC),
                Fact("💧 Tưới nước: $irrigL Lít/gốc/lần (Tổng %.2f m³ toàn vườn)".format(totalM3), FactKind.COMPUTED),
            ),
            next = listOf("Chọn sản phẩm bên dưới để xem chi tiết & tra cứu giá trực tuyến trên Internet."),
            products = buildAllRecommendedProducts(d, question),
        )
    }

    /**
     * AI Chatbot Hỗ trợ Kỹ thuật & Liên hệ Đội ngũ Phát triển 24/7:
     * Thay thế hoàn toàn việc người dùng phải gọi SĐT hoặc gửi Email thủ công.
     */
    fun answerTechSupport(question: String, d: GardenData): AiAnswer {
        val q = plain(question)
        val now = Dates.now()
        val ticketCode = "VW-${(kotlin.math.abs(question.hashCode()) % 8999) + 1000}"
        val scope = "VWing Tech Support AI · Hỗ trợ Kỹ thuật & Liên hệ Nhà phát triển 24/7"

        // 1) Hỏi về Gói Basic / Pro Account / Giá cước / Feature Flags
        if (q.contains("basic") || q.contains("pro") || q.contains("149") || q.contains("499") || q.contains("goi") || q.contains("gia") || q.contains("nang cap") || q.contains("feature")) {
            return AiAnswer(
                question = question,
                asOf = now,
                scope = scope,
                summary = "Hai gói tài khoản VWing linh hoạt theo Tháng hoặc Năm (tiết kiệm 17%):",
                facts = listOf(
                    Fact("🌱 Basic Account: 149.000 đ/tháng hoặc 1.499.000 đ/năm (tiết kiệm 289.000 đ) · Giao diện Trắng sáng chống chói, quản lý 100 cây QR, dự báo thời tiết 6 ngày.", FactKind.DOC),
                    Fact("✦ Pro Account: 499.000 đ/tháng hoặc 4.999.000 đ/năm (tiết kiệm 989.000 đ) · Mở khóa giao diện Aurora Liquid-Glass, Vệ tinh GEE 7 chỉ số, Drone VRA & AI 24/7 không giới hạn.", FactKind.DOC),
                    Fact("🎛️ Bảng Feature Flags cho phép bạn bật/tắt tức thì từng mô-đun chức năng ngay trong ứng dụng.", FactKind.COMPUTED),
                ),
                channel = ChatBotChannel.TECH_SUPPORT,
                featureCards = listOf(
                    ChatFeatureCard(
                        id = "open-plan-dialog",
                        title = "Bảng Gói Basic & Pro / Feature Flags",
                        subtitle = "Chuyển đổi tức thì Basic (149K) ⇄ Pro (499K) & bật/tắt 8 mô-đun",
                        buttonLabel = "Mở bảng",
                        actionRoute = "action:plan_dialog",
                        iconType = "plan",
                    ),
                    ChatFeatureCard(
                        id = "send-dev-ticket-$ticketCode",
                        title = "Phiếu hỗ trợ nâng cấp #$ticketCode",
                        subtitle = "Gửi yêu cầu kích hoạt doanh nghiệp / HTX trực tiếp tới Đội Phát Triển",
                        buttonLabel = "Gửi phiếu",
                        actionRoute = "action:submit_ticket|$ticketCode|$question",
                        iconType = "ticket",
                    ),
                ),
            )
        }

        // 2) Hỏi về Quét mã QR cây & 3 chế độ dữ liệu
        if (q.contains("qr") || q.contains("quet") || q.contains("tem") || q.contains("truy xuat")) {
            return AiAnswer(
                question = question,
                asOf = now,
                scope = scope,
                summary = "Trình quét QR Laser AI tự động giải mã 3 chế độ dữ liệu:",
                facts = listOf(
                    Fact("1️⃣ Chế độ Nội bộ (`vwing://tree/<id>`): Mở ngay 6 nhóm dữ liệu (Định danh, Sinh trưởng, Sâu bệnh, Nhật ký, Ảnh thực tế, Tọa độ GPS).", FactKind.DOC),
                    Fact("2️⃣ Mã QR Lưới Khoảng Cách (8m×8m): Tự động gán Hàng/Cột, tính diện tích 64m²/cây và mật độ cây/ha.", FactKind.COMPUTED),
                    Fact("3️⃣ Chế độ Người mua (Truy xuất nguồn gốc): Hiển thị thẻ ✓ VWing Verified minh bạch giống, vùng trồng và nhật ký chăm sóc.", FactKind.DOC),
                ),
                channel = ChatBotChannel.TECH_SUPPORT,
                featureCards = listOf(
                    ChatFeatureCard(
                        id = "open-qr-screen",
                        title = "Trình Quét Mã QR & Xuất Tờ QR Vườn",
                        subtitle = "Mở camera quét mã QR thân cây hoặc xuất file PDF nhãn QR cả vườn",
                        buttonLabel = "Mở ngay",
                        actionRoute = "qr",
                        iconType = "qr",
                    ),
                ),
            )
        }

        // 3) Hỏi về Bản đồ Khoanh vườn / Vệ tinh Google Earth Engine (GEE)
        if (q.contains("ban do") || q.contains("khoanh vuon") || q.contains("gee") || q.contains("ve tinh") || q.contains("ndvi") || q.contains("gps")) {
            return AiAnswer(
                question = question,
                asOf = now,
                scope = scope,
                summary = "Hướng dẫn Khoanh vườn & Phân tích 7 chỉ số Vệ tinh Google Earth Engine:",
                facts = listOf(
                    Fact("📐 Chạm tối thiểu 3–4 đỉnh trên bản đồ vệ tinh để khoanh ranh vườn (${d.garden.areaHa ?: 2.91} ha) hoặc chọn nhanh vườn mẫu Tây Nguyên.", FactKind.DOC),
                    Fact("🛰️ Hệ thống tự động trích xuất 7 chỉ số GEE LIVE: Độ phủ tán, Đếm số cây tự động, NDVI, NDRE, Ẩm độ đất, Nhiệt độ bề mặt & Cao độ.", FactKind.COMPUTED),
                    Fact("✅ Giao diện HUD đáy đã được căn chỉnh không che khuất nút Zoom [+][-] của Google Maps.", FactKind.DOC),
                ),
                channel = ChatBotChannel.TECH_SUPPORT,
                featureCards = listOf(
                    ChatFeatureCard(
                        id = "open-map-gee",
                        title = "Bản Đồ Khoanh Vườn & Vệ Tinh GEE",
                        subtitle = "Mở bản đồ khoanh đa giác, đếm tán cây tự động & xem 7 chỉ số GEE",
                        buttonLabel = "Mở bản đồ",
                        actionRoute = "map",
                        iconType = "map",
                    ),
                ),
            )
        }

        // 4) Hỏi về Đồng bộ SĐT / Điện thoại & Tablet / Sao lưu .zip
        if (q.contains("dong bo") || q.contains("sdt") || q.contains("so dien thoai") || q.contains("tablet") || q.contains("sao luu") || q.contains("zip") || q.contains("khoi phuc")) {
            return AiAnswer(
                question = question,
                asOf = now,
                scope = scope,
                summary = "Cơ chế đồng bộ theo Số điện thoại & Sao lưu an toàn đa thiết bị:",
                facts = listOf(
                    Fact("📱⇄💻 Chỉ cần đăng nhập cùng một Số điện thoại trên Điện thoại (1080×2400) và Tablet (2560×1600), sổ vườn và gói tài khoản sẽ tự đồng bộ qua Cloud.", FactKind.DOC),
                    Fact("💾 Lưu trữ Local-First: Mọi ghi chép đều lưu tức thì trên máy ngay cả khi mất sóng tại rẫy, và tự đồng bộ khi có mạng.", FactKind.COMPUTED),
                    Fact("📦 Bạn cũng có thể xuất/nhập bản sao lưu toàn diện (.zip) tại mục Cài đặt vườn.", FactKind.DOC),
                ),
                channel = ChatBotChannel.TECH_SUPPORT,
                featureCards = listOf(
                    ChatFeatureCard(
                        id = "open-phone-auth",
                        title = "Đồng Bộ Tài Khoản Theo Số Điện Thoại",
                        subtitle = "Liên kết hoặc chuyển đổi SĐT nhà nông giữa Điện thoại & Tablet",
                        buttonLabel = "Mở SĐT",
                        actionRoute = "action:phone_auth",
                        iconType = "sync",
                    ),
                    ChatFeatureCard(
                        id = "open-setup-backup",
                        title = "Cài Đặt Vườn & Sao Lưu (.zip)",
                        subtitle = "Quản lý dữ liệu ngoại tuyến, sao lưu và hợp nhất không mất bản ghi",
                        buttonLabel = "Mở cài đặt",
                        actionRoute = "setup",
                        iconType = "log",
                    ),
                ),
            )
        }

        // 5) Mặc định: Liên hệ Đội ngũ Phát triển / Báo lỗi / Góp ý tính năng (Thay thế SĐT & Email)
        return AiAnswer(
            question = question,
            asOf = now,
            scope = scope,
            summary = "Đã tiếp nhận thông tin cho Đội ngũ Phát triển VWing (Mã phiếu #$ticketCode):",
            facts = listOf(
                Fact("⚡ Thay vì phải gọi SĐT hay gửi Email chờ đợi, AI Hỗ trợ Kỹ thuật đã tự động đóng gói nội dung của bạn kèm trạng thái vườn (${d.garden.name} · ${d.activeTrees.size} cây).", FactKind.COMPUTED),
                Fact("🛠️ Đội ngũ Kỹ sư Phát triển VWing ưu tiên xử lý phản hồi trực tiếp từ Chatbot trong vòng 15 phút làm việc.", FactKind.DOC),
                Fact("📌 Bạn có thể bấm nút \"Gửi phiếu\" bên dưới để xác nhận lưu và đẩy phiếu #$ticketCode lên hệ thống Dev.", FactKind.INFERENCE),
            ),
            channel = ChatBotChannel.TECH_SUPPORT,
            featureCards = listOf(
                ChatFeatureCard(
                    id = "submit-dev-ticket-$ticketCode",
                    title = "Phiếu Liên Hệ Đội Phát Triển #$ticketCode",
                    subtitle = "Nội dung: \"${question.take(48)}${if (question.length > 48) "…" else ""}\"",
                    buttonLabel = "Gửi phiếu",
                    actionRoute = "action:submit_ticket|$ticketCode|$question",
                    iconType = "ticket",
                ),
                ChatFeatureCard(
                    id = "open-plan-from-support",
                    title = "Kiểm Tra Gói Tài Khoản & Tính Năng",
                    subtitle = "Xem trạng thái Basic / Pro Account và bảng điều khiển Feature Flags",
                    buttonLabel = "Mở bảng",
                    actionRoute = "action:plan_dialog",
                    iconType = "plan",
                ),
            ),
        )
    }

    fun buildAgriFeatureCards(a: AiAnswer, d: GardenData): List<ChatFeatureCard> {
        if (a.featureCards.isNotEmpty()) return a.featureCards
        val cards = mutableListOf<ChatFeatureCard>()
        val q = plain(a.question)
        if (q.contains("van de") || q.contains("kiem tra") || q.contains("cay")) {
            val firstProblemTree = d.activeTrees.firstOrNull {
                val c = d.effectiveCondition(it)
                c == Condition.NEEDS_CHECK || c == Condition.WATCH
            } ?: d.activeTrees.firstOrNull()
            if (firstProblemTree != null) {
                cards += ChatFeatureCard(
                    id = "open-tree-${firstProblemTree.id}",
                    title = "Hồ sơ Cây ${firstProblemTree.code} (${firstProblemTree.zone})",
                    subtitle = "Kiểm tra lịch sử chăm sóc, số trái (${firstProblemTree.fruits ?: 46} trái) & cập nhật ảnh",
                    buttonLabel = "Mở hồ sơ",
                    actionRoute = "tree|${firstProblemTree.id}",
                    iconType = "sparkle",
                )
            }
        }
        cards += ChatFeatureCard(
            id = "open-garden-log",
            title = "Sổ Nhật Ký & Ghi Hoạt Động Vườn",
            subtitle = "Ghi lại lịch bón phân, phun thuốc hoặc tưới nước vào sổ theo SĐT",
            buttonLabel = "Mở sổ",
            actionRoute = "log",
            iconType = "log",
        )
        return cards
    }
}
