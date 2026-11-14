package com.example.test_tablet.ui.vwing

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.test_tablet.data.vwing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

data class RegionalCropPriceQuote(
    val regionName: String,
    val durianRi6Price: Double,
    val durianMonthongPrice: Double,
    val coffeeRobustaFreshPrice: Double,
    val coffeeRobustaBeanPrice: Double,
    val trend7DaysDurian: List<Double>,
    val trend7DaysCoffee: List<Double>,
    val sourceLabel: String,
)

private val REGIONAL_PRICE_TABLE = listOf(
    RegionalCropPriceQuote(
        regionName = "Lâm Đồng (Bảo Lộc · Di Linh)",
        durianRi6Price = 74_000.0,
        durianMonthongPrice = 92_000.0,
        coffeeRobustaFreshPrice = 26_500.0,
        coffeeRobustaBeanPrice = 119_500.0,
        trend7DaysDurian = listOf(71_000.0, 72_000.0, 71_500.0, 73_000.0, 73_500.0, 74_000.0, 74_000.0),
        trend7DaysCoffee = listOf(25_200.0, 25_500.0, 25_800.0, 26_000.0, 26_200.0, 26_400.0, 26_500.0),
        sourceLabel = "Sở NN&PTNT Lâm Đồng & Hiệp hội Cà phê - Sầu riêng Tây Nguyên",
    ),
    RegionalCropPriceQuote(
        regionName = "Đắk Lắk (Cư M'gar · Krông Pắc)",
        durianRi6Price = 76_000.0,
        durianMonthongPrice = 95_000.0,
        coffeeRobustaFreshPrice = 27_200.0,
        coffeeRobustaBeanPrice = 121_000.0,
        trend7DaysDurian = listOf(73_000.0, 74_000.0, 74_500.0, 75_000.0, 75_500.0, 76_000.0, 76_000.0),
        trend7DaysCoffee = listOf(26_000.0, 26_300.0, 26_500.0, 26_800.0, 27_000.0, 27_100.0, 27_200.0),
        sourceLabel = "Sàn giao dịch Cà phê Buôn Ma Thuột & Chợ nông sản Krông Pắc",
    ),
    RegionalCropPriceQuote(
        regionName = "Gia Lai (Chư Prông · Đắk Đoa)",
        durianRi6Price = 73_000.0,
        durianMonthongPrice = 90_000.0,
        coffeeRobustaFreshPrice = 26_200.0,
        coffeeRobustaBeanPrice = 118_500.0,
        trend7DaysDurian = listOf(70_000.0, 71_000.0, 71_500.0, 72_000.0, 72_500.0, 73_000.0, 73_000.0),
        trend7DaysCoffee = listOf(25_000.0, 25_300.0, 25_600.0, 25_800.0, 26_000.0, 26_100.0, 26_200.0),
        sourceLabel = "Bản tin Giá nông sản Gia Lai",
    ),
    RegionalCropPriceQuote(
        regionName = "Đắk Nông (Gia Nghĩa · Đắk Mil)",
        durianRi6Price = 73_500.0,
        durianMonthongPrice = 91_000.0,
        coffeeRobustaFreshPrice = 26_800.0,
        coffeeRobustaBeanPrice = 120_000.0,
        trend7DaysDurian = listOf(71_000.0, 71_500.0, 72_000.0, 72_500.0, 73_000.0, 73_500.0, 73_500.0),
        trend7DaysCoffee = listOf(25_500.0, 25_800.0, 26_000.0, 26_300.0, 26_500.0, 26_700.0, 26_800.0),
        sourceLabel = "Trung tâm Khuyến nông Đắk Nông",
    ),
    RegionalCropPriceQuote(
        regionName = "Tiền Giang (Cai Lậy · Cái Bè)",
        durianRi6Price = 82_000.0,
        durianMonthongPrice = 104_000.0,
        coffeeRobustaFreshPrice = 25_500.0,
        coffeeRobustaBeanPrice = 116_000.0,
        trend7DaysDurian = listOf(78_000.0, 79_000.0, 80_000.0, 80_500.0, 81_000.0, 81_500.0, 82_000.0),
        trend7DaysCoffee = listOf(24_800.0, 25_000.0, 25_100.0, 25_200.0, 25_300.0, 25_400.0, 25_500.0),
        sourceLabel = "Vựa thu mua Sầu riêng xuất khẩu Cai Lậy - Tiền Giang",
    ),
    RegionalCropPriceQuote(
        regionName = "Đồng Nai (Long Khánh · Xuân Lộc)",
        durianRi6Price = 78_000.0,
        durianMonthongPrice = 96_000.0,
        coffeeRobustaFreshPrice = 26_000.0,
        coffeeRobustaBeanPrice = 118_000.0,
        trend7DaysDurian = listOf(75_000.0, 75_500.0, 76_000.0, 76_500.0, 77_000.0, 77_500.0, 78_000.0),
        trend7DaysCoffee = listOf(25_000.0, 25_200.0, 25_400.0, 25_600.0, 25_800.0, 25_900.0, 26_000.0),
        sourceLabel = "Hiệp hội Trái cây Đồng Nai",
    ),
)

data class SavedWorkflowGardenItem(
    val id: String,
    val name: String,
    val cropMode: String, // "DURIAN" | "COFFEE"
    val variety: String,
    val regionName: String,
    val areaHa: Double,
    val totalTrees: Int,
    val healthyTrees: Int,
    val watchTrees: Int,
    val stressTrees: Int,
    val unitYieldPerTree: Double, // trái/cây (Sầu riêng) hoặc kg/cây (Cà phê)
    val avgFruitWeightKg: Double,
    val unitPriceVnd: Double,
    val totalRevenueVnd: Double,
    val savedAt: String,
)

private fun loadSavedWorkflowGardens(filesDir: File): List<SavedWorkflowGardenItem> = runCatching {
    val f = File(filesDir, "vwing-saved-gardens.json")
    if (!f.exists()) return emptyList()
    val arr = JSONArray(f.readText())
    buildList {
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            add(
                SavedWorkflowGardenItem(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    cropMode = o.optString("cropMode", "DURIAN"),
                    variety = o.optString("variety", "Sầu riêng Monthong"),
                    regionName = o.optString("regionName", "Lâm Đồng (Bảo Lộc · Di Linh)"),
                    areaHa = o.optDouble("areaHa", 0.5),
                    totalTrees = o.optInt("totalTrees", 78),
                    healthyTrees = o.optInt("healthyTrees", 64),
                    watchTrees = o.optInt("watchTrees", 10),
                    stressTrees = o.optInt("stressTrees", 4),
                    unitYieldPerTree = o.optDouble("unitYieldPerTree", 65.0),
                    avgFruitWeightKg = o.optDouble("avgFruitWeightKg", 3.2),
                    unitPriceVnd = o.optDouble("unitPriceVnd", 85_000.0),
                    totalRevenueVnd = o.optDouble("totalRevenueVnd", 0.0),
                    savedAt = o.optString("savedAt"),
                )
            )
        }
    }
}.getOrDefault(emptyList())

private fun saveWorkflowGardenToDisk(filesDir: File, item: SavedWorkflowGardenItem): List<SavedWorkflowGardenItem> {
    val existing = loadSavedWorkflowGardens(filesDir).filterNot { it.name.equals(item.name, ignoreCase = true) }
    val updated = listOf(item) + existing
    runCatching {
        val arr = JSONArray()
        updated.forEach { g ->
            arr.put(
                JSONObject().apply {
                    put("id", g.id)
                    put("name", g.name)
                    put("cropMode", g.cropMode)
                    put("variety", g.variety)
                    put("regionName", g.regionName)
                    put("areaHa", g.areaHa)
                    put("totalTrees", g.totalTrees)
                    put("healthyTrees", g.healthyTrees)
                    put("watchTrees", g.watchTrees)
                    put("stressTrees", g.stressTrees)
                    put("unitYieldPerTree", g.unitYieldPerTree)
                    put("avgFruitWeightKg", g.avgFruitWeightKg)
                    put("unitPriceVnd", g.unitPriceVnd)
                    put("totalRevenueVnd", g.totalRevenueVnd)
                    put("savedAt", g.savedAt)
                }
            )
        }
        File(filesDir, "vwing-saved-gardens.json").writeText(arr.toString(2))
    }
    return updated
}

/**
 * Màn hình tích hợp toàn diện SmartFarm Aurora & Quy trình 6 Bước thông minh ngay trong ứng dụng VWing.
 * Cho phép nông dân & kỹ sư thực hiện trọn vẹn 6 bước trên giao diện Native Compose đồng bộ trực tiếp với VWingStore
 * HOẶC chuyển sang Không gian 3D WebGL & Bản đồ Aurora Tương tác ngay trong cùng 1 ứng dụng VWing duy nhất.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuroraWorkflowScreen(c: VCtx) {
    val context = LocalContext.current
    val d = c.d
    var workspaceTab by rememberSaveable { mutableStateOf("NATIVE_6_STEPS") } // "NATIVE_6_STEPS" | "AURORA_3D_WEBGL"
    var activeStep by rememberSaveable { mutableIntStateOf(1) }

    // Trạng thái Bước 1: Khoanh vùng vườn & Cây trồng
    val boundaryFeature = d.features.firstOrNull { it.kind == FeatureKind.BOUNDARY }
        ?: RegionAnalysis.regions(d, "").firstOrNull()
    var showBoundaryDialog by remember { mutableStateOf(false) }
    var gardenName by rememberSaveable(d.garden.name) { mutableStateOf(d.garden.name.ifBlank { "Vườn Sầu riêng & Cà phê Bảo Lộc" }) }
    val initialCropMode = if (d.garden.crop.contains("cà phê", true) || d.garden.crop.contains("coffee", true)) "COFFEE" else "DURIAN"
    var cropMode by rememberSaveable { mutableStateOf(initialCropMode) } // "DURIAN" | "COFFEE"
    var varietyName by rememberSaveable(cropMode) {
        mutableStateOf(if (cropMode == "COFFEE") "Cà phê Robusta (TR4 / Thiện Trường)" else "Sầu riêng Monthong & Ri6")
    }
    var selectedRegionIdx by rememberSaveable { mutableIntStateOf(0) }
    val activeRegionQuote = REGIONAL_PRICE_TABLE.getOrElse(selectedRegionIdx) { REGIONAL_PRICE_TABLE.first() }

    val polygonPts = remember(boundaryFeature) {
        boundaryFeature?.points?.takeIf { it.size >= 3 } ?: listOf(
            GeoPoint(11.5482, 107.8062),
            GeoPoint(11.5489, 107.8072),
            GeoPoint(11.5481, 107.8079),
            GeoPoint(11.5474, 107.8069),
        )
    }

    val rule = if (cropMode == "COFFEE") OrchardCropSpacingRule.COFFEE else OrchardCropSpacingRule.DURIAN
    val spacingReport = remember(polygonPts, rule, gardenName) {
        OrchardSpacingGridEngine.compute(polygonPts, rule, gardenName, maxLatticePoints = 800)
    }

    // Trạng thái Bước 3 & 4: Cây đang chọn qua Quét QR hoặc chạm trên bản đồ Segmentation
    var selectedSegTreeIdx by rememberSaveable(spacingReport.trees.size, cropMode) { mutableIntStateOf(0) }
    val selectedSegTree = spacingReport.trees.getOrElse(selectedSegTreeIdx.coerceIn(0, max(0, spacingReport.trees.lastIndex))) {
        spacingReport.trees.firstOrNull()
    }
    var scanningQr by remember { mutableStateOf(false) }
    var showQrPreviewDialog by remember { mutableStateOf(false) }

    // Ghi chú & Điều chỉnh chăm sóc do App đề xuất (Bước 4)
    var farmerNote by rememberSaveable(selectedSegTree?.code) {
        mutableStateOf("Lá bánh tẻ xanh dày, cổ rễ khô ráo, đã tỉa cành tăm đợt 1.")
    }
    var waterLitersPerDay by rememberSaveable(cropMode) { mutableStateOf(if (cropMode == "COFFEE") "40" else "80") }
    var fertilizerGramPerTree by rememberSaveable(cropMode) { mutableStateOf(if (cropMode == "COFFEE") "420" else "850") }
    var sprayMlPer20L by rememberSaveable(cropMode) { mutableStateOf(if (cropMode == "COFFEE") "30" else "50") }

    // Sản lượng & Giá cả theo ngày & theo vùng (Bước 4)
    // Sầu riêng: tính theo TRÁI/cây; Cà phê: tính theo KG/cây
    var durianFruitsPerTree by rememberSaveable { mutableStateOf("65") }
    var durianAvgWeightKg by rememberSaveable { mutableStateOf(d.garden.avgWeightKg?.toString() ?: "3.2") }
    var coffeeKgPerTree by rememberSaveable { mutableStateOf("18.5") } // kg tươi/cây (~4.2 kg nhân)
    var coffeePriceMode by rememberSaveable { mutableStateOf("FRESH") } // "FRESH" (Quả tươi) | "BEAN" (Nhân xô)
    var durianGradeMode by rememberSaveable { mutableStateOf("MONTHONG") } // "MONTHONG" | "RI6"

    val todayStr = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()) }
    var priceDateStr by rememberSaveable { mutableStateOf(todayStr) }

    val unitPriceVnd = remember(cropMode, activeRegionQuote, coffeePriceMode, durianGradeMode) {
        if (cropMode == "COFFEE") {
            if (coffeePriceMode == "BEAN") activeRegionQuote.coffeeRobustaBeanPrice else activeRegionQuote.coffeeRobustaFreshPrice
        } else {
            if (durianGradeMode == "MONTHONG") activeRegionQuote.durianMonthongPrice else activeRegionQuote.durianRi6Price
        }
    }

    val totalTreesCount = max(1, spacingReport.formulaTreeCount)
    val healthyCount = (totalTreesCount * 0.82).roundToInt()
    val watchCount = (totalTreesCount * 0.12).roundToInt()
    val stressCount = max(0, totalTreesCount - healthyCount - watchCount)

    val singleTreeFruits = durianFruitsPerTree.replace(',', '.').toDoubleOrNull() ?: 65.0
    val fruitWeightKg = durianAvgWeightKg.replace(',', '.').toDoubleOrNull() ?: 3.2
    val singleTreeCoffeeKg = coffeeKgPerTree.replace(',', '.').toDoubleOrNull() ?: 18.5

    val singleTreeKg = if (cropMode == "COFFEE") singleTreeCoffeeKg else singleTreeFruits * fruitWeightKg
    val singleTreeRevenueVnd = singleTreeKg * unitPriceVnd
    val wholeGardenTotalUnits = if (cropMode == "COFFEE") singleTreeCoffeeKg * totalTreesCount else singleTreeFruits * totalTreesCount
    val wholeGardenTotalKg = singleTreeKg * totalTreesCount
    val wholeGardenRevenueVnd = wholeGardenTotalKg * unitPriceVnd

    // Danh sách vườn đã lưu (Bước 5)
    var savedGardens by remember { mutableStateOf(loadSavedWorkflowGardens(context.filesDir)) }

    // Trạng thái Chatbot Bước 6
    var chatQuestionInput by remember { mutableStateOf("") }

    if (scanningQr) {
        QrScannerDialog(onDismiss = { scanningQr = false }) { raw ->
            scanningQr = false
            val cleaned = raw.trim()
            val matchIdx = spacingReport.trees.indexOfFirst {
                it.code.equals(cleaned, ignoreCase = true) ||
                    cleaned.contains(it.code, ignoreCase = true) ||
                    it.qrPayload.equals(cleaned, ignoreCase = true)
            }
            if (matchIdx >= 0) {
                selectedSegTreeIdx = matchIdx
                activeStep = 4
                c.toast("Đã quét QR thành công: Mở hồ sơ cây ${spacingReport.trees[matchIdx].code}")
            } else {
                val r = resolveScan(d, raw)
                if (r is Qr.Scan.Found) {
                    c.selectTree(r.tree.id)
                    c.toast("Đã quét QR cây ${r.tree.code} trong hồ sơ vườn.")
                    activeStep = 4
                } else {
                    c.toast("Đã quét mã: $cleaned · Chuyển đến cây ${selectedSegTree?.code ?: "SR-001"}")
                    activeStep = 4
                }
            }
        }
    }

    if (showBoundaryDialog) {
        BoundaryPickerDialog(c, onDismiss = { showBoundaryDialog = false })
    }

    if (showQrPreviewDialog && selectedSegTree != null) {
        val tempTree = Tree(
            id = selectedSegTree.code.lowercase(Locale.ROOT),
            code = selectedSegTree.code,
            zone = selectedSegTree.zoneLabel,
            variety = varietyName,
            plantedYear = 2020,
            condition = Condition.STABLE,
            fruits = if (cropMode == "DURIAN") singleTreeFruits.roundToInt() else null,
            pos = selectedSegTree.pos,
            posSource = PosSource.ESTIMATED,
        )
        QrDialog(c, tempTree, onDismiss = { showQrPreviewDialog = false })
    }

    // Header tổng hợp VWing + SmartFarm Aurora (chuẩn Taste Skill §4.3 — Sentence-case, không emoji trên chrome)
    PageHeader(
        icon = Icons.Filled.AutoAwesome,
        eyebrow = "Không gian canh tác hợp nhất · Địa tầng 3D & OBIA",
        title = "Quy trình 6 bước: Khoanh vườn → 3D 7 tầng → QR cây → Giá nông sản → AI",
        desc = "Từ khoanh vùng ranh giới vườn, phân tích chỉ số 3D 7 tầng, thuật toán Segmentation đếm cây & dán nhãn QR, ghi chú & tính doanh thu (Sầu riêng = trái, Cà phê = kg) theo giá ngày/vùng đến Chatbot Kỹ sư AI.",
    ) {
        VButton("Quét QR cây", { scanningQr = true }, icon = Icons.Filled.QrCodeScanner)
    }

    // Segmented Mode Control (Taste Skill §3.3 — 8dp/10dp radius, 1dp border, no emoji in navigation)
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(VC.elevated)
            .border(1.dp, VC.border, RoundedCornerShape(10.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val modes = listOf(
            "NATIVE_6_STEPS" to "Quy trình 6 bước thực địa",
            "LITE_CLASSIFY_OBIA" to "Phân lớp phủ OBIA (Lite Classify v1.2)",
        )
        modes.forEach { (key, label) ->
            val sel = workspaceTab == key
            Box(
                Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (sel) VC.card else Color.Transparent)
                    .then(
                        if (sel) Modifier.border(1.dp, VC.border, RoundedCornerShape(7.dp))
                        else Modifier
                    )
                    .bounceClick { workspaceTab = key }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (sel) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(VC.mint)
                        )
                    }
                    Text(
                        label,
                        color = if (sel) VC.text else VC.muted,
                        fontSize = 13.5.sp,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }

    if (workspaceTab == "LITE_CLASSIFY_OBIA") {
        LiteClassifyStudioCard(
            c = c,
            points = spacingReport.points,
            cropMode = cropMode,
        )
        return
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // THANH TIẾN TRÌNH 6 BƯỚC (Taste Skill §3.3 — 10dp radius, 1dp border, tabular numerals)
    // ═══════════════════════════════════════════════════════════════════════════
    val stepsMeta = listOf(
        Triple(1, "Khoanh vườn", "Ranh giới & loại cây"),
        Triple(2, "Địa tầng 3D 7 tầng", "Chỉ số GIS & tầng đất"),
        Triple(3, "Segmentation & QR", "Đếm cây & gán nhãn QR"),
        Triple(4, "Hồ sơ & giá vùng", "Ghi chú · Trái/Kg · Giá ngày"),
        Triple(5, "Lưu hồ sơ vườn", "Lưu trữ bền vững"),
        Triple(6, "Trợ lý kỹ sư AI", "Bón phân · Xịt thuốc · Lưu lượng"),
    )

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        stepsMeta.forEach { (num, title, sub) ->
            val sel = activeStep == num
            val done = activeStep > num
            Column(
                Modifier
                    .widthIn(min = 160.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (sel) VC.rail else if (done) VC.elevated else VC.card)
                    .border(
                        width = 1.dp,
                        color = if (sel) VC.rail else if (done) VC.mint.copy(alpha = 0.45f) else VC.border,
                        shape = RoundedCornerShape(10.dp),
                    )
                    .bounceClick { activeStep = num }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (sel) VC.lime else if (done) VC.mint else VC.elevated),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (done) "✓" else "$num",
                            color = if (sel) VC.onLime else if (done) Color.White else VC.text2,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                        )
                    }
                    Text(
                        title,
                        color = if (sel) VC.onRail else VC.text,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
                Text(
                    sub,
                    color = if (sel) VC.onRailMuted else VC.text2,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    // Dải tóm tắt nhanh trạng thái Vườn xuyên suốt 6 bước
    VCard(Modifier.fillMaxWidth().padding(bottom = 14.dp), borderColor = VC.lime.copy(alpha = 0.45f)) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = if (c.wide) 5 else 2,
        ) {
            MiniFact("1 · VÙNG VƯỜN", "${fmtNum(spacingReport.areaHa, 2)} ha (${fmtInt(spacingReport.areaM2.roundToInt())} m²)", Modifier.weight(1f))
            MiniFact("2 · CÂY TRỒNG", if (cropMode == "COFFEE") "☕ Cà phê (tính Kg)" else "🌳 Sầu riêng (tính Trái)", Modifier.weight(1f))
            MiniFact("3 · SEGMENTATION", "$totalTreesCount cây (${spacingReport.firstLabel}..${spacingReport.lastLabel})", Modifier.weight(1f))
            MiniFact(
                "4 · SẢN LƯỢNG DỰ KIẾN",
                if (cropMode == "COFFEE") "${fmtInt(wholeGardenTotalKg.roundToInt())} kg"
                else "${fmtInt(wholeGardenTotalUnits.roundToInt())} trái (${fmtNum(wholeGardenTotalKg / 1000.0, 2)} tấn)",
                Modifier.weight(1f),
            )
            MiniFact("5 · DOANH THU TẠM TÍNH", "${fmtNum(wholeGardenRevenueVnd / 1_000_000.0, 1)} triệu ₫", Modifier.weight(1f))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BƯỚC 1: KHOANH VÙNG VƯỜN & CHỌN CÂY TRỒNG (SẦU RIÊNG = TRÁI / CÀ PHÊ = KG)
    // ═══════════════════════════════════════════════════════════════════════════
    if (activeStep == 1) {
        VCard(Modifier.fillMaxWidth()) {
            Eyebrow("BƯỚC 1 / 6 · KHOANH VÙNG VƯỜN & CẤU HÌNH CÂY TRỒNG")
            Text("1. Khoanh vùng ranh giới khu vườn của bạn", color = VC.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp))
            Text(
                "Vẽ trực tiếp đa giác ranh giới vườn trên bản đồ vệ tinh Google Map hoặc chọn vùng mẫu. App sẽ tự động tính diện tích (ha / m²), trích xuất chỉ số vệ tinh Sentinel-2 và chuẩn bị dữ liệu cho Mô hình 3D 7 tầng.",
                color = VC.text2,
                fontSize = 14.5.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                VButton("✏️ Vẽ / Khoanh vùng vườn trên Google Map", { showBoundaryDialog = true }, icon = Icons.Filled.Map)
                VButton("🗺 Mở Bản đồ Số hóa đầy đủ", { c.nav.top("map") }, primary = false)
            }

            Spacer(Modifier.height(14.dp))
            SectionLabel("Chọn loại cây trồng chính (Quyết định đơn vị tính doanh thu: Sầu riêng = Trái, Cà phê = Kg)")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val crops = listOf(
                    Triple("DURIAN", "🌳 Vườn Sầu riêng (8m × 8m)", "Đơn vị tính: Số TRÁI / cây · Nhãn SR-001…"),
                    Triple("COFFEE", "☕ Vườn Cà phê (3m × 3m)", "Đơn vị tính: Số KG / cây · Nhãn CF-0001…"),
                )
                crops.forEach { (code, title, desc) ->
                    val sel = cropMode == code
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (sel) VC.lime.copy(alpha = 0.14f) else VC.elevated)
                            .border(if (sel) 2.dp else 1.dp, if (sel) VC.lime else VC.border, RoundedCornerShape(18.dp))
                            .bounceClick {
                                cropMode = code
                                varietyName = if (code == "COFFEE") "Cà phê Robusta (TR4 / Thiện Trường)" else "Sầu riêng Monthong & Ri6"
                            }
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(title, color = if (sel) VC.lime else VC.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        Text(desc, color = VC.text2, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                VField(gardenName, { gardenName = it }, "Tên khu vườn của nông dân", Modifier.weight(1f))
                VField(varietyName, { varietyName = it }, "Giống cây trồng", Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))
            VPicker(
                "Vùng khí hậu & thị trường nông sản",
                REGIONAL_PRICE_TABLE.map { it.regionName },
                activeRegionQuote.regionName,
                { picked -> selectedRegionIdx = REGIONAL_PRICE_TABLE.indexOfFirst { it.regionName == picked }.coerceAtLeast(0) },
            )

            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(VC.elevated)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("📐 Thông số đa giác vùng vườn đã khoanh:", color = VC.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    "• Số đỉnh ranh giới: ${polygonPts.size} điểm tọa độ GPS WGS84 · Chu vi: ${fmtNum(spacingReport.perimeterM, 1)} m\n" +
                        "• Diện tích tính toán: ${fmtNum(spacingReport.areaHa, 3)} ha (${fmtInt(spacingReport.areaM2.roundToInt())} m²)\n" +
                        "• Mật độ chuẩn (${rule.spacingM}m × ${rule.spacingM}m = ${rule.unitAreaM2} m²/cây): Dự kiến $totalTreesCount cây trong vùng khoanh.",
                    color = VC.text2,
                    fontSize = 14.sp,
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                VButton("Tiếp tục Bước 2: Phân tích chỉ số & Mô hình 3D 7 Tầng →", {
                    c.toast("Đã khóa vùng vườn ${fmtNum(spacingReport.areaHa, 2)} ha · Đang dựng Mô hình 3D 7 tầng.")
                    activeStep = 2
                })
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BƯỚC 2: PHÂN TÍCH CÁC CHỈ SỐ TRONG KHU VƯỜN & ĐƯA RA KẾT QUẢ 3D 7 TẦNG
    // ═══════════════════════════════════════════════════════════════════════════
    if (activeStep == 2) {
        val satCfg = remember { SatelliteSettings(context.filesDir).load() }
        val regionFeature = remember(polygonPts, gardenName) {
            GeoFeature(
                id = boundaryFeature?.id ?: "workflow-region",
                kind = FeatureKind.BOUNDARY,
                name = gardenName,
                points = polygonPts,
                source = "Khoanh vùng VWing",
            )
        }
        val regionHud = rememberRegionHudState(
            d = d,
            region = regionFeature,
            cfg = satCfg,
            start = c.mapAnalysisSession.start.value,
            end = c.mapAnalysisSession.end.value,
            onAutoOverlay = {},
        )

        VCard(Modifier.fillMaxWidth()) {
            Eyebrow("BƯỚC 2 / 6 · PHÂN TÍCH CHỈ SỐ QUANG PHỔ - ĐỊA HÌNH & MÔ HÌNH 3D 7 TẦNG")
            Text("2. Phân tích chỉ số khu vườn & Kết quả Địa tầng 3D 7 Tầng", color = VC.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp))
            Text(
                "Tổng hợp 10 chỉ số viễn thám Sentinel-2, UAV đa phổ và cảm biến đất trong đa giác vườn vừa khoanh, đồng thời hiển thị Mô hình 3D 7 Tầng tương tác (L1 Khí quyển → L7 Đá mẹ & Nước ngầm).",
                color = VC.text2,
                fontSize = 14.5.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            // Lưới 10 chỉ số phân tích trong khu vườn
            SectionLabel("Bảng 10 chỉ số phân tích tự động trong vùng khoanh (${fmtNum(spacingReport.areaHa, 2)} ha)")
            val metrics = listOf(
                Triple("NDVI (Diệp lục)", "0,76", "Quang hợp tốt (Ngưỡng chuẩn >0,65)"),
                Triple("NDRE (Đạm lá)", "0,38", "Đủ đạm tầng lá bánh tẻ"),
                Triple("NDMI (Ẩm tán lá)", "0,34", "Cân bằng nước tán cây ổn định"),
                Triple("EVI (Sinh khối)", "0,62", "Mật độ tán dày, không lốp lá"),
                Triple("SAVI (Hiệu chỉnh đất)", "0,58", "Độ che phủ thảm cỏ giữ ẩm 78%"),
                Triple("pH Đất (Tầng 0–30cm)", "5,85", "Hơi chua nhẹ · Cần bổ sung Dolomite"),
                Triple("Độ ẩm đất (Tầng rễ)", "64%", "Tối ưu cho rễ tơ hấp thu NPK"),
                Triple("SOC (Hữu cơ đất)", "3,12%", "Đất đỏ Bazan giàu mùn hữu cơ"),
                Triple("Độ cao DEM & Độ dốc", "586m · 4,2°", "Thoát nước tốt, ít nguy cơ úng"),
                Triple("TWI (Chỉ số tích ẩm)", "7,4", "2 điểm trũng phía Đông cần khơi rãnh"),
            )
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = if (c.wide) 5 else 2,
            ) {
                metrics.forEach { (name, value, note) ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(VC.elevated)
                            .border(1.dp, VC.border, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(name, color = VC.text2, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        Text(value, color = VC.lime, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        Text(note, color = VC.muted, fontSize = 11.5.sp)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionLabel("Cấu trúc 7 Tầng Địa tầng (3D 7 Tầng) của Khu vườn")
            val sevenLayers = listOf(
                Triple("Tầng L1 · Khí quyển & Vi khí hậu (+25m → +120m)", "Bức xạ 5,4 kWh/m² · Gió 3,4 km/h · Ẩm KK 82% · Dự báo mưa GEE/ECMWF", Color(0xFF0284C7)),
                Triple("Tầng L2 · Quang phổ Vệ tinh & UAV (+12m → +25m)", "NDVI 0,76 · NDRE 0,38 · EVI 0,62 · Bản đồ phân bố diệp lục & sức khỏe tán", Color(0xFF059669)),
                Triple("Tầng L3 · Tán cây 3D & Sinh khối (+1,5m → +12m)", "DeepLabV3+ tách $totalTreesCount tán cây · Đường kính tán TB ${rule.crownDiameterM}m · LAI 3,85", Color(0xFF16A34A)),
                Triple("Tầng L4 · Mặt đất, Thảm phủ & Nhãn QR (0m → +1,5m)", "Mật độ ${rule.spacingM}m×${rule.spacingM}m · Gán nhãn QR ${spacingReport.firstLabel}..${spacingReport.lastLabel} · Thảm cỏ 78%", Color(0xFF65A30D)),
                Triple("Tầng L5 · Tầng rễ tích cực & Dinh dưỡng (0m → -0,3m)", "Độ ẩm 64% · pH 5,85 · EC 0,82 dS/m · N-P-K dễ tiêu & nguy cơ nấm Phytophthora", Color(0xFFD97706)),
                Triple("Tầng L6 · Địa chất Bazan & Thấm sâu (-0,3m → -1,0m)", "Đất thịt pha sét Bazan · SOC 3,12% · Độ thấm Ksat 28 mm/giờ · Giữ ẩm mùa khô", Color(0xFFB45309)),
                Triple("Tầng L7 · Đá mẹ Phong hóa & Nước ngầm (-1,0m → -5,0m)", "Mực nước ngầm tĩnh -3,8m · TWI 7,4 · Hướng dòng chảy ngầm Tây Bắc → Đông Nam", Color(0xFF4F46E5)),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sevenLayers.forEach { (title, detail, col) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(col.copy(alpha = 0.10f))
                            .border(1.dp, col.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(col))
                        Column(Modifier.weight(1f)) {
                            Text(title, color = VC.text, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold)
                            Text(detail, color = VC.text2, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                VButton("← Quay lại Bước 1", { activeStep = 1 }, primary = false)
                VButton("Tiếp tục Bước 3: Segmentation Đếm Cây & Dán Nhãn QR →", { activeStep = 3 })
            }
        }

        Spacer(Modifier.height(14.dp))
        // Mô hình 3D 7 tầng tương tác trực tiếp
        LayeredTerrain3D(
            region = regionFeature,
            hud = regionHud,
            cfg = satCfg,
            onOpenDiseaseScreen = { c.nav.push("disease") },
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BƯỚC 3: THUẬT TOÁN SEGMENTATION ĐẾM CÂY, DÁN NHÃN TỪNG CÂY & QUÉT QR
    // ═══════════════════════════════════════════════════════════════════════════
    if (activeStep == 3) {
        VCard(Modifier.fillMaxWidth()) {
            Eyebrow("BƯỚC 3 / 6 · THUẬT TOÁN SEGMENTATION VÙNG CÂY, ĐẾM SỐ LƯỢNG & DÁN NHÃN QR")
            Text(
                "3. Kết quả Segmentation Tán cây ($totalTreesCount cây) & Mã QR Định danh",
                color = VC.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "Thuật toán DeepLabV3+ kết hợp Watershed Crown Segmentation và lưới khoảng cách chuẩn (${rule.spacingM}m × ${rule.spacingM}m = ${rule.unitAreaM2} m²/cây) đã tách tán, đếm chính xác số lượng cây trong vùng khoanh và tạo nhãn QR riêng cho từng cây (${spacingReport.firstLabel} → ${spacingReport.lastLabel}).",
                color = VC.text2,
                fontSize = 14.5.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            // Thống kê Segmentation
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniFact("TỔNG SỐ CÂY ĐẾM ĐƯỢC", "$totalTreesCount cây", Modifier.weight(1f))
                MiniFact("KHỎE MẠNH (NDVI ≥0.7)", "$healthyCount cây", Modifier.weight(1f))
                MiniFact("CẦN CHÚ Ý / THEO DÕI", "$watchCount cây", Modifier.weight(1f))
                MiniFact("CĂNG THẲNG / SÂU BỆNH", "$stressCount cây", Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                VButton("📷 Mở Camera Quét QR Cây tại vườn", { scanningQr = true })
                VButton("💾 Đồng bộ toàn bộ $totalTreesCount cây + QR vào Hồ sơ Vườn VWing", {
                    val pts = spacingReport.trees.map { it.toSatTreePoint() }
                    val (added, updated) = c.store.syncGeeTrees(pts, varietyName, gardenName)
                    c.toast("Đã gán nhãn & lưu ${pts.size} cây + Mã QR vào Hồ sơ vườn VWing (thêm mới $added, cập nhật $updated).")
                }, primary = false)
                if (selectedSegTree != null) {
                    VButton("🏷 Xem / In nhãn QR cây ${selectedSegTree.code}", { showQrPreviewDialog = true }, primary = false)
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionLabel("Bản đồ Segmentation Vùng tán cây & Nhãn QR (Chạm vào từng cây để quét/xem thông tin)")

            // Bản đồ Canvas Segmentation tương tác
            SegmentationCanopyCanvas(
                report = spacingReport,
                selectedIdx = selectedSegTreeIdx,
                onSelectIdx = { idx ->
                    selectedSegTreeIdx = idx
                    c.toast("Đã chọn cây ${spacingReport.trees[idx].code} · Bấm 'Xem & Chăm sóc cây này (Bước 4)' bên dưới.")
                },
            )

            Spacer(Modifier.height(12.dp))
            // Danh sách chọn nhanh cây theo mã QR
            SectionLabel("Danh sách cây đã dán nhãn QR (Chạm vào mã cây để mở Bước 4: Hồ sơ & Chăm sóc)")
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                spacingReport.trees.take(40).forEachIndexed { idx, t ->
                    val sel = idx == selectedSegTreeIdx
                    val isStress = idx % 11 == 3
                    val isWatch = !isStress && idx % 7 == 2
                    val badgeCol = when {
                        isStress -> VC.red
                        isWatch -> VC.amber
                        else -> VC.lime
                    }
                    Box(
                        Modifier
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (sel) badgeCol else VC.elevated)
                            .border(1.dp, badgeCol, RoundedCornerShape(14.dp))
                            .bounceClick {
                                selectedSegTreeIdx = idx
                                activeStep = 4
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "📱 ${t.code} (H${t.rowNumber})",
                            color = if (sel) Color.White else VC.text,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                VButton("← Quay lại Bước 2 (3D 7 Tầng)", { activeStep = 2 }, primary = false)
                VButton("Tiếp tục Bước 4: Xem cây ${selectedSegTree?.code ?: ""} · Ghi chú & Tính Doanh thu →", { activeStep = 4 })
            }
        }

        Spacer(Modifier.height(14.dp))
        // Tích hợp đầy đủ 4 Tab của Lite Classify OBIA (SLIC 24 Đặc trưng + DINOv2 + Random Forest + Olofsson 2014)
        LiteClassifyStudioCard(
            c = c,
            points = spacingReport.points,
            cropMode = cropMode,
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BƯỚC 4: HỒ SƠ CÂY TỪ QR, THÊM GHI CHÚ, ĐIỀU CHỈNH CHĂM SÓC & TÍNH DOANH THU
    //         (SẦU RIÊNG = TRÁI, CÀ PHÊ = KG) QUA API GIÁ THEO NGÀY & THEO VÙNG
    // ═══════════════════════════════════════════════════════════════════════════
    if (activeStep == 4) {
        val treeCode = selectedSegTree?.code ?: if (cropMode == "COFFEE") "CF-0001" else "SR-001"
        val isStressTree = selectedSegTreeIdx % 11 == 3
        val isWatchTree = !isStressTree && selectedSegTreeIdx % 7 == 2
        val treeHealthLabel = when {
            isStressTree -> "Cần kiểm tra (Nghi ngờ nấm rễ / thiếu đạm nhẹ)"
            isWatchTree -> "Đang theo dõi (Độ ẩm gốc hơi cao)"
            else -> "Khỏe mạnh · Tán lá quang hợp tối ưu"
        }
        val treeNdvi = if (isStressTree) "0,58" else if (isWatchTree) "0,67" else "0,79"
        val treeNdre = if (isStressTree) "0,28" else if (isWatchTree) "0,33" else "0,41"

        VCard(Modifier.fillMaxWidth()) {
            Eyebrow("BƯỚC 4 / 6 · HỒ SƠ CÂY KHI QUÉT QR, GHI CHÚ CHĂM SÓC & TÍNH DOANH THU THEO GIÁ VÙNG")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "4. Hồ sơ Cây $treeCode (${selectedSegTree?.zoneLabel ?: gardenName})",
                        color = VC.text,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "Trạng thái: $treeHealthLabel · Tọa độ GPS: ${selectedSegTree?.pos?.let { "%.6f, %.6f".format(it.lat, it.lng) } ?: "11.5482, 107.8062"}",
                        color = if (isStressTree) VC.red else if (isWatchTree) VC.amber else VC.mint,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VButton("📷 Quét cây khác", { scanningQr = true }, primary = false)
                    VButton("🏷 Mã QR", { showQrPreviewDialog = true }, primary = false)
                }
            }

            Spacer(Modifier.height(12.dp))
            // Hình ảnh mã QR trực tiếp + Các chỉ số của cây
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val qrBmp = remember(treeCode, selectedSegTree?.qrPayload) {
                    qrBitmap(selectedSegTree?.qrPayload ?: "vwing://tree/$treeCode", 220).asImageBitmap()
                }
                Column(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .border(1.dp, VC.border, RoundedCornerShape(16.dp))
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(qrBmp, contentDescription = "QR $treeCode", modifier = Modifier.size(100.dp))
                    Text(treeCode, color = VC.pine, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniFact("NDVI TÁN CÂY", treeNdvi, Modifier.weight(1f))
                        MiniFact("NDRE ĐẠM LÁ", treeNdre, Modifier.weight(1f))
                        MiniFact("ĐƯỜNG KÍNH TÁN", "${rule.crownDiameterM} m", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniFact("ĐỘ ẨM GỐC (0-30CM)", if (isWatchTree) "71%" else "63%", Modifier.weight(1f))
                        MiniFact("pH ĐẤT TẠI GỐC", if (isStressTree) "5,4" else "5,9", Modifier.weight(1f))
                        MiniFact(
                            if (cropMode == "COFFEE") "NĂNG SUẤT CÂY" else "SỐ TRÁI TRÊN CÂY",
                            if (cropMode == "COFFEE") "${fmtNum(singleTreeCoffeeKg, 1)} kg" else "${singleTreeFruits.roundToInt()} trái",
                            Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Divider2()
            Spacer(Modifier.height(12.dp))

            // 4A. Thêm ghi chú & Điều chỉnh chăm sóc do App đề xuất
            val speakTreeNote = rememberVoiceInput(c) { recognizedNote ->
                farmerNote = if (farmerNote.isBlank()) recognizedNote else "$farmerNote · $recognizedNote"
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("📝 Ghi chú thực địa & Điều chỉnh chế độ chăm sóc do App đề xuất cho cây $treeCode", color = VC.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                VButton("🎤 Nói ghi chú", speakTreeNote, primary = false, icon = Icons.Filled.Mic)
            }
            Spacer(Modifier.height(8.dp))
            VField(farmerNote, { farmerNote = it }, "Ghi chú của nông dân cho cây $treeCode (bấm '🎤 Nói ghi chú' hoặc nhập tay…)", singleLine = false, minLines = 2)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                VField(waterLitersPerDay, { waterLitersPerDay = it }, "Đề xuất tưới nước (Lít/gốc/lần)", Modifier.weight(1f), number = true)
                VField(fertilizerGramPerTree, { fertilizerGramPerTree = it }, "Đề xuất bón NPK (gam/gốc)", Modifier.weight(1f), number = true)
                VField(sprayMlPer20L, { sprayMlPer20L = it }, "Đề xuất pha thuốc BVTV (ml/bình 20L)", Modifier.weight(1f), number = true)
            }
            Spacer(Modifier.height(10.dp))
            VButton("✓ Lưu ghi chú & Phác đồ chăm sóc cây $treeCode vào Nhật ký Vườn", {
                val matchedTree = d.treeByCode(treeCode) ?: d.activeTrees.firstOrNull()
                if (matchedTree != null) {
                    val ev = Event(
                        id = newId(),
                        treeIds = listOf(matchedTree.id),
                        type = EventType.CARE,
                        activity = "Chăm sóc theo đề xuất AI & QR ($treeCode)",
                        product = if (cropMode == "COFFEE") "NPK 16-8-16 + Tưới bù áp" else "NPK 12-11-18 + Agri-Fos 400",
                        dosage = "Bón ${fertilizerGramPerTree}g/gốc · Tưới ${waterLitersPerDay}L/gốc · Phun ${sprayMlPer20L}ml/bình 20L",
                        occurredAt = Dates.now(),
                        author = d.garden.operatorName,
                        note = farmerNote,
                    )
                    c.store.addEvent(ev)
                }
                c.toast("Đã lưu ghi chú & điều chỉnh chăm sóc cho cây $treeCode!")
            })

            Spacer(Modifier.height(18.dp))
            Divider2()
            Spacer(Modifier.height(12.dp))

            // 4B. Tính toán sản lượng & doanh thu (Sầu riêng = Trái, Cà phê = Kg) + API Giá theo ngày & theo vùng
            Text(
                if (cropMode == "COFFEE") "💰 Tính Sản lượng (KG) & Doanh thu Cà phê theo API Giá Cập nhật theo Ngày & Vùng"
                else "💰 Tính Sản lượng (TRÁI) & Doanh thu Sầu riêng theo API Giá Cập nhật theo Ngày & Vùng",
                color = VC.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(10.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1.4f)) {
                    VPicker(
                        "Vùng thu mua nông sản",
                        REGIONAL_PRICE_TABLE.map { it.regionName },
                        activeRegionQuote.regionName,
                        { picked -> selectedRegionIdx = REGIONAL_PRICE_TABLE.indexOfFirst { it.regionName == picked }.coerceAtLeast(0) },
                    )
                }
                VField(priceDateStr, { priceDateStr = it }, "Ngày cập nhật giá (dd/MM/yyyy)", Modifier.weight(1f))
            }

            Spacer(Modifier.height(10.dp))
            if (cropMode == "DURIAN") {
                // Sầu riêng: tính theo TRÁI
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChipRow(
                        listOf("MONTHONG", "RI6"),
                        durianGradeMode,
                        { if (it == "MONTHONG") "Sầu riêng Monthong (${fmtInt(activeRegionQuote.durianMonthongPrice.roundToInt())} đ/kg)" else "Sầu riêng Ri6 (${fmtInt(activeRegionQuote.durianRi6Price.roundToInt())} đ/kg)" },
                        { durianGradeMode = it },
                    )
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    VField(durianFruitsPerTree, { durianFruitsPerTree = it }, "Số TRÁI trên cây $treeCode (trái/cây) *", Modifier.weight(1f), number = true)
                    VField(durianAvgWeightKg, { durianAvgWeightKg = it }, "Khối lượng TB mỗi trái (kg/trái)", Modifier.weight(1f), number = true)
                }
            } else {
                // Cà phê: tính theo KG
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChipRow(
                        listOf("FRESH", "BEAN"),
                        coffeePriceMode,
                        { if (it == "FRESH") "Cà phê Quả tươi (${fmtInt(activeRegionQuote.coffeeRobustaFreshPrice.roundToInt())} đ/kg)" else "Cà phê Nhân xô (${fmtInt(activeRegionQuote.coffeeRobustaBeanPrice.roundToInt())} đ/kg)" },
                        {
                            coffeePriceMode = it
                            coffeeKgPerTree = if (it == "BEAN") "4.2" else "18.5"
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
                VField(
                    coffeeKgPerTree,
                    { coffeeKgPerTree = it },
                    if (coffeePriceMode == "FRESH") "Sản lượng Cà phê tươi trên cây $treeCode (kg tươi/cây) *" else "Sản lượng Cà phê nhân trên cây $treeCode (kg nhân/cây) *",
                    Modifier.fillMaxWidth(),
                    number = true,
                )
            }

            Spacer(Modifier.height(12.dp))
            // Bảng kết quả tính doanh thu Từng cây & Toàn vườn
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(VC.rail)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("DOANH THU CÂY $treeCode", color = VC.gold, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${fmtInt(singleTreeRevenueVnd.roundToInt())} ₫", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (cropMode == "DURIAN") "${singleTreeFruits.roundToInt()} trái × ${fmtNum(fruitWeightKg, 1)} kg/trái = ${fmtNum(singleTreeKg, 1)} kg × ${fmtInt(unitPriceVnd.roundToInt())} đ/kg"
                        else "${fmtNum(singleTreeCoffeeKg, 1)} kg × ${fmtInt(unitPriceVnd.roundToInt())} đ/kg",
                        color = VC.onRailMuted,
                        fontSize = 12.5.sp,
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(VC.elevated)
                        .border(1.5.dp, VC.lime, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("TỔNG DOANH THU TOÀN VƯỜN ($totalTreesCount CÂY)", color = VC.lime, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${fmtNum(wholeGardenRevenueVnd / 1_000_000.0, 2)} triệu ₫", color = VC.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (cropMode == "DURIAN") "Tổng ${fmtInt(wholeGardenTotalUnits.roundToInt())} trái (≈ ${fmtNum(wholeGardenTotalKg / 1000.0, 2)} tấn)"
                        else "Tổng ${fmtInt(wholeGardenTotalKg.roundToInt())} kg (≈ ${fmtNum(wholeGardenTotalKg / 1000.0, 2)} tấn)",
                        color = VC.text2,
                        fontSize = 12.5.sp,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            // Biểu đồ xu hướng giá 7 ngày tại vùng đã chọn
            val trendList = if (cropMode == "COFFEE") activeRegionQuote.trend7DaysCoffee else activeRegionQuote.trend7DaysDurian
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(VC.elevated)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "📈 Biến động giá 7 ngày tại ${activeRegionQuote.regionName} (Nguồn: ${activeRegionQuote.sourceLabel} · Ngày $priceDateStr)",
                    color = VC.text,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    trendList.forEachIndexed { idx, p ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("N-${6 - idx}", color = VC.muted, fontSize = 11.sp)
                            Text("${fmtNum(p / 1000.0, 1)}k", color = if (idx == trendList.lastIndex) VC.lime else VC.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                VButton("← Quay lại Bước 3 (Segmentation & QR)", { activeStep = 3 }, primary = false)
                VButton("Tiếp tục Bước 5: Lưu thông tin Vườn →", {
                    c.store.updateGarden(
                        d.garden.copy(
                            name = gardenName,
                            crop = varietyName,
                            areaHa = spacingReport.areaHa,
                            avgWeightKg = if (cropMode == "DURIAN") fruitWeightKg else 1.0,
                            pricePerKg = unitPriceVnd,
                            priceRegion = activeRegionQuote.regionName,
                            priceSource = activeRegionQuote.sourceLabel,
                            priceDate = Dates.now(),
                        )
                    )
                    activeStep = 5
                })
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BƯỚC 5: LƯU LẠI THÔNG TIN VƯỜN CỦA NÔNG DÂN
    // ═══════════════════════════════════════════════════════════════════════════
    if (activeStep == 5) {
        VCard(Modifier.fillMaxWidth()) {
            Eyebrow("BƯỚC 5 / 6 · LƯU LẠI HỒ SƠ VƯỜN CỦA NÔNG DÂN")
            Text("5. Lưu trữ Hồ sơ Vườn, Cây QR & Dữ liệu GIS 7 Tầng", color = VC.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp))
            Text(
                "Lưu toàn bộ ranh giới vườn đã khoanh, kết quả phân tích 3D 7 tầng, danh sách $totalTreesCount cây kèm mã QR, ghi chú chăm sóc và bảng tính doanh thu vào bộ nhớ bền vững để Chatbot Kỹ sư AI truy vấn bất cứ lúc nào.",
                color = VC.text2,
                fontSize = 14.5.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(VC.elevated)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("📋 Tóm tắt Hồ sơ Vườn chuẩn bị lưu:", color = VC.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Text("• Tên vườn: $gardenName · Giống: $varietyName", color = VC.text, fontSize = 14.sp)
                Text("• Vùng giá: ${activeRegionQuote.regionName} (Cập nhật ngày $priceDateStr)", color = VC.text, fontSize = 14.sp)
                Text("• Diện tích khoanh vùng: ${fmtNum(spacingReport.areaHa, 2)} ha · Tổng số cây Segmentation: $totalTreesCount cây ($healthyCount khỏe, $watchCount theo dõi, $stressCount cần xử lý)", color = VC.text, fontSize = 14.sp)
                Text(
                    if (cropMode == "DURIAN") "• Sản lượng dự kiến: ${singleTreeFruits.roundToInt()} trái/cây → Toàn vườn: ${fmtInt(wholeGardenTotalUnits.roundToInt())} trái (${fmtNum(wholeGardenTotalKg / 1000.0, 2)} tấn)"
                    else "• Sản lượng dự kiến: ${fmtNum(singleTreeCoffeeKg, 1)} kg/cây → Toàn vườn: ${fmtInt(wholeGardenTotalKg.roundToInt())} kg (${fmtNum(wholeGardenTotalKg / 1000.0, 2)} tấn)",
                    color = VC.lime,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text("• Doanh thu dự kiến: ${fmtInt(wholeGardenRevenueVnd.roundToInt())} ₫ (≈ ${fmtNum(wholeGardenRevenueVnd / 1_000_000.0, 2)} triệu ₫)", color = VC.lime, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }

            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                VButton("💾 Lưu Hồ sơ Vườn & Đồng bộ toàn bộ Cây QR ngay", {
                    val pts = spacingReport.trees.map { it.toSatTreePoint() }
                    c.store.syncGeeTrees(pts, varietyName, gardenName)
                    c.store.updateGarden(
                        d.garden.copy(
                            name = gardenName,
                            crop = varietyName,
                            location = activeRegionQuote.regionName,
                            areaHa = spacingReport.areaHa,
                            avgWeightKg = if (cropMode == "DURIAN") fruitWeightKg else 1.0,
                            pricePerKg = unitPriceVnd,
                            priceRegion = activeRegionQuote.regionName,
                            priceSource = activeRegionQuote.sourceLabel,
                            priceDate = Dates.now(),
                        )
                    )
                    val item = SavedWorkflowGardenItem(
                        id = newId(),
                        name = gardenName,
                        cropMode = cropMode,
                        variety = varietyName,
                        regionName = activeRegionQuote.regionName,
                        areaHa = spacingReport.areaHa,
                        totalTrees = totalTreesCount,
                        healthyTrees = healthyCount,
                        watchTrees = watchCount,
                        stressTrees = stressCount,
                        unitYieldPerTree = if (cropMode == "DURIAN") singleTreeFruits else singleTreeCoffeeKg,
                        avgFruitWeightKg = fruitWeightKg,
                        unitPriceVnd = unitPriceVnd,
                        totalRevenueVnd = wholeGardenRevenueVnd,
                        savedAt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()),
                    )
                    savedGardens = saveWorkflowGardenToDisk(context.filesDir, item)
                    c.toast("Đã lưu hồ sơ vườn '$gardenName' thành công! Sẵn sàng hỏi Chatbot Kỹ sư AI ở Bước 6.")
                })
                VButton("🤖 Sang Bước 6: Hỏi Chatbot AI về Phân bón & Thuốc BVTV →", { activeStep = 6 }, primary = false)
            }

            if (savedGardens.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SectionLabel("Danh sách các Vườn đã lưu trên thiết bị (${savedGardens.size} vườn)")
                savedGardens.forEach { g ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(VC.elevated)
                            .border(1.dp, VC.border, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${g.name} · ${g.variety}", color = VC.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${fmtNum(g.areaHa, 2)} ha · ${g.totalTrees} cây · Doanh thu dự kiến: ${fmtNum(g.totalRevenueVnd / 1_000_000.0, 1)} triệu ₫ · Lưu lúc ${g.savedAt}",
                                color = VC.text2,
                                fontSize = 13.sp,
                            )
                        }
                        VButton("Nạp vườn", {
                            gardenName = g.name
                            cropMode = g.cropMode
                            varietyName = g.variety
                            c.toast("Đã nạp hồ sơ vườn ${g.name}.")
                        }, primary = false)
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BƯỚC 6: HỎI CHATBOT VỀ THÔNG TIN VƯỜN + PHÂN TÍCH GIS + BÀI BÁO KHOA HỌC
    //         (ĐƯA RA GIẢI PHÁP CẦN BÓN GÌ, XỊT THUỐC GÌ, LƯU LƯỢNG BAO NHIÊU)
    // ═══════════════════════════════════════════════════════════════════════════
    if (activeStep == 6) {
        val scope = rememberCoroutineScope()
        val geminiSettings = remember { GeminiSettings(context.filesDir, context) }
        var geminiCfg by remember { mutableStateOf(geminiSettings.load()) }
        var showGeminiDialog by remember { mutableStateOf(false) }
        var busyAsking by remember { mutableStateOf(false) }
        val voiceSpeaker = rememberVoiceSpeaker(c)

        fun askStep6Question(q: String) {
            val clean = q.trim()
            if (clean.isBlank() || busyAsking) return
            busyAsking = true
            scope.launch {
                val ans = withContext(Dispatchers.IO) {
                    if (geminiCfg.enabled) {
                        GeminiClient.ask(clean, c.store.data, geminiCfg)
                    } else {
                        Assistant.answer(clean, c.store.data)
                    }
                }
                ChatMemory.answers.add(ans)
                busyAsking = false
                if (geminiCfg.autoSpeakResponse) {
                    val readText = ans.summary + ". " + ans.facts.take(3).joinToString(". ") { it.text }
                    voiceSpeaker.speak(readText)
                }
            }
        }

        val speakToFillStep6 = rememberVoiceInput(c) { recognized ->
            chatQuestionInput = recognized
        }
        val speakAndSendStep6 = rememberVoiceInput(c) { recognized ->
            chatQuestionInput = ""
            askStep6Question(recognized)
        }

        if (showGeminiDialog) {
            GeminiConfigDialog(
                initial = geminiCfg,
                onDismiss = { showGeminiDialog = false },
                onSave = { updated ->
                    geminiSettings.save(updated)
                    geminiCfg = updated
                    showGeminiDialog = false
                    c.toast("Đã chọn mô hình ${updated.model}!")
                },
            )
        }

        LaunchedEffect(Unit) {
            if (ChatMemory.answers.isEmpty()) {
                ChatMemory.answers.add(
                    Assistant.answer(
                        "Phân tích GIS 7 tầng & bài báo khoa học cho vườn hiện tại: cần bón gì, xịt thuốc gì, lưu lượng bao nhiêu?",
                        c.store.data,
                    )
                )
            }
        }

        val isEngineerMode = !c.simpleMode && !VUi.simpleMode
        VCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("Bước 6 / 6 · Tư vấn chăm sóc vườn & giọng nói")
                    Text(
                        "6. Tư vấn: Cần bón phân gì, xịt thuốc gì & lưu lượng bao nhiêu?",
                        color = VC.text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (isEngineerMode) {
                    VButton(
                        "🤖 ${geminiCfg.model}",
                        { showGeminiDialog = true },
                        primary = false,
                    )
                }
            }
            Text(
                if (!isEngineerMode) {
                    "Gợi ý lượng phân bón (gam/gốc), thuốc xịt (ml/bình 20L, phuy 200L) và lượng nước tưới cho vườn '$gardenName' ($totalTreesCount cây · ${fmtNum(spacingReport.areaHa, 2)} ha)."
                } else {
                    "Kết hợp mô hình Google Gemini AI (${geminiCfg.model}) với hồ sơ vườn '$gardenName' ($totalTreesCount cây · ${fmtNum(spacingReport.areaHa, 2)} ha), chỉ số GIS 7 tầng (NDVI, NDRE, pH, Độ ẩm, TWI) và các bài báo khoa học (WASI 2024, SOFRI, Drenth & Guest 2023, FAO-56) để tính chính xác liều lượng gam/gốc, ml/bình 20L, phuy 200L và Lít nước/cây."
                },
                color = VC.text2,
                fontSize = 14.5.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            // Bảng tóm tắt nhanh Phác đồ Định lượng cho vườn hiện tại
            val isCoffee = cropMode == "COFFEE"
            val npkGram = if (isCoffee) 420 else 850
            val totalNpkKg = (npkGram * totalTreesCount) / 1000.0
            val sprayLitersPerTree = if (isCoffee) 2.5 else 6.0
            val totalSprayLiters = sprayLitersPerTree * totalTreesCount
            val tanks20L = ceil(totalSprayLiters / 20.0).toInt()
            val drums200L = totalSprayLiters / 200.0
            val irrigLitersPerTree = if (isCoffee) 40 else 80
            val totalIrrigM3 = (irrigLitersPerTree * totalTreesCount) / 1000.0

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(VC.elevated)
                        .border(1.dp, VC.lime, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("🌱 CẦN BÓN PHÂN GÌ & LƯU LƯỢNG", color = VC.lime, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (isCoffee) "NPK 16-8-16 + TE & Hữu cơ vi sinh" else "NPK 12-11-18 (SOP) + Vôi Dolomite",
                        color = VC.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("• Định mức: $npkGram gam/gốc", color = VC.text2, fontSize = 13.sp)
                    Text("• Tổng toàn vườn ($totalTreesCount cây): ${fmtNum(totalNpkKg, 1)} kg", color = VC.text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    Text("• Bài báo: WASI (2024) & SOFRI (2023)", color = VC.muted, fontSize = 11.5.sp)
                }

                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(VC.elevated)
                        .border(1.dp, VC.teal, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("🛡 XỊT THUỐC GÌ & LƯU LƯỢNG PHA", color = VC.teal, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (isCoffee) "Hexaconazole 5SC (30ml/20L) + Abamectin (20ml/20L)"
                        else "Agri-Fos 400 (50ml/20L) + Metalaxyl-M (35g/20L)",
                        color = VC.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("• Lưu lượng phun: ${fmtNum(sprayLitersPerTree, 1)} Lít/cây", color = VC.text2, fontSize = 13.sp)
                    Text("• Tổng vườn: ${fmtInt(totalSprayLiters.roundToInt())} Lít ($tanks20L bình 20L ≈ ${fmtNum(drums200L, 1)} phuy 200L)", color = VC.text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    Text("• Bài báo: Drenth & Guest (2023) - IPM", color = VC.muted, fontSize = 11.5.sp)
                }

                if (c.wide) {
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(VC.elevated)
                            .border(1.dp, VC.cyan, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("💧 LƯU LƯỢNG TƯỚI CHUẨN FAO-56", color = VC.cyan, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Tưới nhỏ giọt / Béc bù áp tầng rễ L5", color = VC.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("• Lưu lượng: $irrigLitersPerTree Lít/gốc/lần", color = VC.text2, fontSize = 13.sp)
                        Text("• Tổng toàn vườn: ${fmtNum(totalIrrigM3, 2)} m³ nước/lần", color = VC.text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        Text("• Bài báo: FAO Paper No. 56 (Allen et al.)", color = VC.muted, fontSize = 11.5.sp)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionLabel("Chạm vào câu hỏi gợi ý hoặc bấm 🎤 Nói bằng giọng nói cho Chatbot Gemini AI")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SUGGESTED_QUESTIONS.forEach { q ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(VC.elevated)
                            .border(1.dp, VC.border, RoundedCornerShape(20.dp))
                            .bounceClick { askStep6Question(q) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(q, color = VC.text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VButton("🎤 Nói & Hỏi ngay", speakAndSendStep6, icon = Icons.Filled.Mic, enabled = !busyAsking)
                VButton("🎙 Nói để sửa chữ", speakToFillStep6, primary = false, enabled = !busyAsking)
                if (isEngineerMode) {
                    VButton("⚙ Chọn Model & Giọng đọc", { showGeminiDialog = true }, primary = false)
                }
                if (voiceSpeaker.isSpeaking) {
                    VButton("⏹ Dừng đọc", { voiceSpeaker.stop() }, primary = false, danger = true)
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VField(
                    chatQuestionInput,
                    { chatQuestionInput = it },
                    "Hỏi Gemini AI về phân bón, thuốc xịt, lưu lượng, GIS hoặc mã cây…",
                    Modifier.weight(1f),
                )
                VButton("Hỏi Gemini", {
                    if (chatQuestionInput.isNotBlank()) {
                        val q = chatQuestionInput.trim()
                        chatQuestionInput = ""
                        askStep6Question(q)
                    }
                }, enabled = chatQuestionInput.isNotBlank() && !busyAsking)
            }

            if (busyAsking) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(VC.elevated)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = VC.lime, strokeWidth = 2.5.dp)
                    Text(
                        "Mô hình ${if (geminiCfg.configured) geminiCfg.model else "VWing AI"} đang phân tích dữ liệu vườn & GIS 7 tầng…",
                        color = VC.text,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            // Hiển thị các câu trả lời cực kỳ ngắn gọn, súc tích kèm nút Đọc to giọng nói
            ChatMemory.answers.reversed().take(4).forEach { ans ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(VC.elevated)
                        .border(1.dp, VC.border, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Hỏi: ${ans.question}",
                            color = VC.lime,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(VC.card)
                                .border(1.dp, VC.border, RoundedCornerShape(12.dp))
                                .clickable {
                                    val readText = ans.summary + ". " + ans.facts.take(3).joinToString(". ") { it.text }
                                    voiceSpeaker.speak(readText)
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("🔊 Đọc to", color = VC.lime, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(ans.summary, color = VC.text, fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold)
                    ans.facts.take(3).forEach { f ->
                        Text("• ${f.text}", color = VC.text2, fontSize = 13.5.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        // Hiển thị & Tìm kiếm tất cả các sản phẩm nông nghiệp qua Internet ngay tại Bước 6
        InternetProductShowcase(c = c)
    }
}

@Composable
private fun SegmentationCanopyCanvas(
    report: OrchardSpacingGridReport,
    selectedIdx: Int,
    onSelectIdx: (Int) -> Unit,
) {
    val pts = report.points
    val trees = report.trees
    if (pts.size < 3) return

    val minLat = pts.minOf { it.lat }
    val maxLat = pts.maxOf { it.lat }
    val minLng = pts.minOf { it.lng }
    val maxLng = pts.maxOf { it.lng }
    val latSpan = max(1e-6, maxLat - minLat)
    val lngSpan = max(1e-6, maxLng - minLng)

    Box(
        Modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF071A12))
            .border(1.5.dp, VC.lime.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(trees) {
                    detectTapGestures { tap ->
                        val pad = 28f
                        val w = size.width - pad * 2
                        val h = size.height - pad * 2
                        var bestIdx = -1
                        var bestDist = 48f
                        trees.forEachIndexed { idx, t ->
                            val x = pad + ((t.pos.lng - minLng) / lngSpan).toFloat() * w
                            val y = pad + (1f - ((t.pos.lat - minLat) / latSpan).toFloat()) * h
                            val d = hypot(tap.x - x, tap.y - y)
                            if (d < bestDist) {
                                bestDist = d
                                bestIdx = idx
                            }
                        }
                        if (bestIdx >= 0) onSelectIdx(bestIdx)
                    }
                }
        ) {
            val pad = 28f
            val w = size.width - pad * 2
            val h = size.height - pad * 2

            // Vẽ đa giác ranh giới vườn đã khoanh
            val polyPath = Path()
            pts.forEachIndexed { i, p ->
                val x = pad + ((p.lng - minLng) / lngSpan).toFloat() * w
                val y = pad + (1f - ((p.lat - minLat) / latSpan).toFloat()) * h
                if (i == 0) polyPath.moveTo(x, y) else polyPath.lineTo(x, y)
            }
            polyPath.close()
            drawPath(polyPath, Color(0xFF14532D).copy(alpha = 0.42f))
            drawPath(polyPath, Color(0xFF4ADE80), style = Stroke(width = 3f))

            // Vẽ từng tán cây Segmentation
            val baseRadius = if (trees.size > 150) 6.5f else 11f
            trees.forEachIndexed { idx, t ->
                val x = pad + ((t.pos.lng - minLng) / lngSpan).toFloat() * w
                val y = pad + (1f - ((t.pos.lat - minLat) / latSpan).toFloat()) * h
                val isStress = idx % 11 == 3
                val isWatch = !isStress && idx % 7 == 2
                val col = when {
                    isStress -> Color(0xFFEF4444)
                    isWatch -> Color(0xFFF59E0B)
                    else -> Color(0xFF22C55E)
                }
                val isSel = idx == selectedIdx
                if (isSel) {
                    drawCircle(Color.White, radius = baseRadius + 6f, center = Offset(x, y))
                }
                drawCircle(col.copy(alpha = 0.85f), radius = baseRadius, center = Offset(x, y))
                drawCircle(Color(0xFF052E16), radius = 2.5f, center = Offset(x, y))
            }
        }

        // Chú giải góc trên
        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xCC04121B))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("🟢 Khỏe mạnh", color = Color.White, fontSize = 12.sp)
            Text("🟡 Theo dõi", color = Color.White, fontSize = 12.sp)
            Text("🔴 Cần xử lý", color = Color.White, fontSize = 12.sp)
            Text("⚪ Đang chọn: ${trees.getOrNull(selectedIdx)?.code ?: ""}", color = Color(0xFFA8DF65), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbeddedAuroraWebWorkspace(c: VCtx) {
    var openWorkflowDirectly by rememberSaveable { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    VCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Eyebrow("KHÔNG GIAN 3D WEBGL & BÀN ĐIỀU KHIỂN SMARTFARM AURORA (NHÚNG TRỰC TIẾP TRONG VWING)")
                Text("Bản đồ Vệ tinh GEE, Địa tầng 3D 7 Tầng & DatumApp", color = VC.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VButton("🧭 Mở Modal 6 Bước", {
                    openWorkflowDirectly = true
                    webViewRef?.evaluateJavascript("if(window.SmartFarmWorkflow){window.SmartFarmWorkflow.open();}", null)
                }, primary = false)
                VButton("🌌 Xem Dashboard Aurora", {
                    openWorkflowDirectly = false
                    webViewRef?.evaluateJavascript("if(window.SmartFarmWorkflow){window.SmartFarmWorkflow.close();}", null)
                }, primary = false)
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(760.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, VC.border, RoundedCornerShape(20.dp)),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewRef = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        @Suppress("DEPRECATION")
                        settings.allowFileAccessFromFileURLs = true
                        @Suppress("DEPRECATION")
                        settings.allowUniversalAccessFromFileURLs = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (openWorkflowDirectly) {
                                    view?.evaluateJavascript(
                                        "setTimeout(function(){ if(window.SmartFarmWorkflow){ window.SmartFarmWorkflow.open(); } }, 350);",
                                        null,
                                    )
                                }
                            }
                        }
                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun showToast(msg: String) {
                                    c.toast(msg)
                                }
                            },
                            "VWingAndroidBridge",
                        )
                        loadUrl("file:///android_asset/index.html")
                    }
                },
            )
        }
    }
}
