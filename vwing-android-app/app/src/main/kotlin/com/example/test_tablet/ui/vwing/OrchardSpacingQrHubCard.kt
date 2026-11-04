package com.example.test_tablet.ui.vwing

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.*
import java.util.Locale
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Quy chuẩn khoảng cách trồng theo loại vườn:
 * - Sầu riêng (DURIAN): 8m * 8m = 64 m²/cây (~156,25 cây/ha)
 * - Cà phê (COFFEE):   3m * 3m = 9 m²/cây  (~1.111,11 cây/ha)
 */
enum class OrchardCropSpacingRule(
    val id: String,
    val titleVi: String,
    val shortVi: String,
    val icon: String,
    val spacingM: Int,
    val unitAreaM2: Int,
    val codePrefix: String,
    val crownDiameterM: Double,
    val accentColor: Color,
) {
    DURIAN(
        id = "DURIAN",
        titleVi = "Vườn Sầu riêng (8m × 8m = 64 m²/cây)",
        shortVi = "Sầu riêng 8m×8m",
        icon = "🌳",
        spacingM = 8,
        unitAreaM2 = 64,
        codePrefix = "SR",
        crownDiameterM = 5.5,
        accentColor = Color(0xFF3DDC84),
    ),
    COFFEE(
        id = "COFFEE",
        titleVi = "Vườn Cà phê (3m × 3m = 9 m²/cây)",
        shortVi = "Cà phê 3m×3m",
        icon = "☕",
        spacingM = 3,
        unitAreaM2 = 9,
        codePrefix = "CF",
        crownDiameterM = 2.4,
        accentColor = Color(0xFFFFB74D),
    );

    val densityPerHa: Double get() = 10_000.0 / unitAreaM2.toDouble()
}

/** Hồ sơ 1 cây được tính toán & gắn nhãn từ thuật toán diện tích khoanh vùng (8m×8m hoặc 3m×3m). */
data class SpacingLabeledTree(
    val treeNumber: Int,
    val code: String,
    val rule: OrchardCropSpacingRule,
    val rowNumber: Int,
    val colNumber: Int,
    val pos: GeoPoint,
    val regionName: String,
    val areaM2: Double,
    val totalTreesInRegion: Int,
    val qrPayload: String,
) {
    val zoneLabel: String get() = "Hàng $rowNumber · $regionName"
    val fullLabelVi: String get() = "$code · Hàng $rowNumber - Cây $colNumber (${rule.spacingM}m×${rule.spacingM}m)"

    fun toSatTreePoint(): SatTreePoint = SatTreePoint(
        code = code,
        status = "healthy",
        ndvi = 0.68,
        ndre = 0.36,
        isPeak = true,
        pos = pos,
        treeNumber = treeNumber,
        rowNumber = rowNumber,
        statusVi = "Đã gắn nhãn & QR (${rule.spacingM}m×${rule.spacingM}m)",
        healthScore = 90,
        confidencePct = 99,
        crownDiameterM = rule.crownDiameterM,
        detectionSource = "Thuật toán Diện tích Khoanh vùng ÷ (${rule.spacingM}m × ${rule.spacingM}m = ${rule.unitAreaM2} m²/cây)",
        evi = 0.48,
        ndmi = 0.24,
        diagnosisVi = "Nhãn $code (Hàng $rowNumber · Cột $colNumber) · Khoảng cách ${rule.spacingM}m×${rule.spacingM}m (${rule.unitAreaM2} m²/cây) · Mã QR: $qrPayload",
    )
}

/** Kết quả tính số lượng cây, gắn nhãn và tạo mã QR cho toàn bộ vùng đã khoanh. */
data class OrchardSpacingGridReport(
    val regionName: String,
    val rule: OrchardCropSpacingRule,
    val points: List<GeoPoint>,
    val areaHa: Double,
    val areaM2: Double,
    val perimeterM: Double,
    val formulaTreeCount: Int,
    val rowsCount: Int,
    val firstLabel: String,
    val lastLabel: String,
    val formulaVi: String,
    val trees: List<SpacingLabeledTree>,
)

object OrchardSpacingGridEngine {
    /**
     * Tính chính xác số lượng cây trong vùng đã khoanh theo diện tích:
     * - Sầu riêng (8m * 8m): Số cây = floor(Diện_tích_m2 / (8 * 8)) = floor(Diện_tích_m2 / 64)
     * - Cà phê   (3m * 3m): Số cây = floor(Diện_tích_m2 / (3 * 3)) = floor(Diện_tích_m2 / 9)
     * Sau đó định vị lưới tọa độ (lat, lng) bên trong đa giác vùng khoanh, gán nhãn tuần tự và tạo mã QR cho từng cây.
     */
    fun compute(
        points: List<GeoPoint>,
        rule: OrchardCropSpacingRule,
        regionName: String,
        maxLatticePoints: Int = 2500,
    ): OrchardSpacingGridReport {
        val cleanPts = Geo.open(points)
        if (cleanPts.size < 3) {
            return OrchardSpacingGridReport(
                regionName = regionName,
                rule = rule,
                points = cleanPts,
                areaHa = 0.0,
                areaM2 = 0.0,
                perimeterM = 0.0,
                formulaTreeCount = 0,
                rowsCount = 0,
                firstLabel = "${rule.codePrefix}-001",
                lastLabel = "${rule.codePrefix}-001",
                formulaVi = "Chưa có vùng khoanh hợp lệ (cần tối thiểu 3 đỉnh).",
                trees = emptyList(),
            )
        }

        val areaHa = Geo.polygonAreaHa(cleanPts)
        val areaM2 = areaHa * 10_000.0
        val centerLat = cleanPts.map { it.lat }.average()
        val mPerDegLat = 110_574.0
        val mPerDegLng = max(10_000.0, 111_320.0 * cos(Math.toRadians(centerLat)))
        val perimeterM = cleanPts.indices.sumOf { i ->
            val a = cleanPts[i]
            val b = cleanPts[(i + 1) % cleanPts.size]
            hypot((b.lat - a.lat) * mPerDegLat, (b.lng - a.lng) * mPerDegLng)
        }
        val unitM2 = rule.unitAreaM2.toDouble()

        // Công thức chính xác theo yêu cầu:
        // N = floor(Diện tích khu vực (m²) / (spacing * spacing))
        val exactTreeCount = max(1, floor(areaM2 / unitM2).toInt())

        val minLat = cleanPts.minOf { it.lat }
        val maxLat = cleanPts.maxOf { it.lat }
        val minLng = cleanPts.minOf { it.lng }
        val maxLng = cleanPts.maxOf { it.lng }

        val targetPts = min(exactTreeCount, max(1, maxLatticePoints))
        val baseStepM = sqrt(areaM2 / targetPts.toDouble())

        data class RawGridPt(val lat: Double, val lng: Double, val rRaw: Int, val cRaw: Int)
        val candidates = mutableListOf<RawGridPt>()

        for (scale in listOf(0.98, 0.92, 0.85, 0.78, 0.70, 0.60)) {
            val stepM = max(1.1, baseStepM * scale)
            val dLat = stepM / mPerDegLat
            val dLng = stepM / mPerDegLng
            candidates.clear()
            var lat = maxLat - dLat * 0.45
            var rIdx = 0
            while (lat > minLat) {
                rIdx++
                var lng = minLng + dLng * 0.45
                var cIdx = 0
                while (lng < maxLng) {
                    val gp = GeoPoint(lat, lng)
                    if (Geo.pointInPolygon(gp, cleanPts)) {
                        cIdx++
                        candidates.add(RawGridPt(lat, lng, rIdx, cIdx))
                    }
                    lng += dLng
                }
                lat -= dLat
            }
            if (candidates.size >= targetPts) break
        }

        if (candidates.isEmpty()) {
            candidates.add(RawGridPt(centerLat, (minLng + maxLng) * 0.5, 1, 1))
        }

        val picked = if (candidates.size > targetPts) {
            List(targetPts) { i ->
                val idx = ((i.toDouble() * (candidates.size - 1)) / max(1, targetPts - 1)).roundToInt()
                    .coerceIn(0, candidates.size - 1)
                candidates[idx]
            }
        } else {
            candidates.toList()
        }

        val uniqueRows = picked.map { it.rRaw }.distinct().sorted()
        val rowMap = uniqueRows.withIndex().associate { (idx, rOld) -> rOld to (idx + 1) }
        val rowColCounter = mutableMapOf<Int, Int>()
        val digits = if (exactTreeCount >= 1000) 4 else 3

        val trees = picked.mapIndexed { idx, raw ->
            val treeNum = idx + 1
            val rNum = rowMap[raw.rRaw] ?: 1
            val cNum = (rowColCounter[rNum] ?: 0) + 1
            rowColCounter[rNum] = cNum
            val code = "${rule.codePrefix}-${treeNum.toString().padStart(digits, '0')}"
            val qrPayload = Qr.spacingPayloadFor(
                code = code,
                cropType = rule.id,
                spacingM = rule.spacingM,
                row = rNum,
                col = cNum,
                lat = raw.lat,
                lng = raw.lng,
                areaM2 = areaM2,
                totalTrees = exactTreeCount,
                treeId = code,
            )
            SpacingLabeledTree(
                treeNumber = treeNum,
                code = code,
                rule = rule,
                rowNumber = rNum,
                colNumber = cNum,
                pos = GeoPoint(raw.lat, raw.lng),
                regionName = regionName,
                areaM2 = areaM2,
                totalTreesInRegion = exactTreeCount,
                qrPayload = qrPayload,
            )
        }

        val firstLbl = trees.firstOrNull()?.code ?: "${rule.codePrefix}-${1.toString().padStart(digits, '0')}"
        val lastLbl = "${rule.codePrefix}-${exactTreeCount.toString().padStart(digits, '0')}"
        val formulaStr = String.format(
            Locale.US,
            "Số cây = ⌊Diện tích (%.1f m²) ÷ (%dm × %dm = %d m²/cây)⌋ = %,d cây",
            areaM2,
            rule.spacingM,
            rule.spacingM,
            rule.unitAreaM2,
            exactTreeCount,
        )

        return OrchardSpacingGridReport(
            regionName = regionName,
            rule = rule,
            points = cleanPts,
            areaHa = areaHa,
            areaM2 = areaM2,
            perimeterM = perimeterM,
            formulaTreeCount = exactTreeCount,
            rowsCount = uniqueRows.size,
            firstLabel = firstLbl,
            lastLabel = lastLbl,
            formulaVi = formulaStr,
            trees = trees,
        )
    }

    /** Vẽ nhãn in (Mã QR + Nhãn cây + Khoảng cách + Hàng/Cột + Tọa độ GPS) cho 1 cây từ thuật toán khoảng cách. */
    fun renderSpacingTreeLabelBitmap(t: SpacingLabeledTree): Bitmap {
        val w = 440
        val h = 560
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = AndroidCanvas(bmp)
        cv.drawColor(0xFFFFFFFF.toInt())
        drawSingleSpacingLabel(cv, t, 0f, 0f, w.toFloat(), h.toFloat())
        return bmp
    }

    /** Vẽ tờ nhãn QR hàng loạt (4 cột) cho danh sách cây đã gán nhãn trong vùng khoanh để in dán lên cây. */
    fun renderSpacingTreeSheetBitmap(trees: List<SpacingLabeledTree>, maxTreesOnSheet: Int = 60): Bitmap {
        val sub = trees.take(maxTreesOnSheet.coerceAtLeast(1))
        val cols = 4
        val cw = 440
        val ch = 560
        val rows = ((sub.size + cols - 1) / cols).coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(cols * cw, rows * ch, Bitmap.Config.ARGB_8888)
        val cv = AndroidCanvas(bmp)
        cv.drawColor(0xFFFFFFFF.toInt())
        sub.forEachIndexed { i, t ->
            val left = (i % cols) * cw.toFloat()
            val top = (i / cols) * ch.toFloat()
            drawSingleSpacingLabel(cv, t, left, top, cw.toFloat(), ch.toFloat())
        }
        return bmp
    }

    private fun drawSingleSpacingLabel(
        cv: AndroidCanvas,
        t: SpacingLabeledTree,
        left: Float,
        top: Float,
        w: Float,
        h: Float,
    ) {
        val border = Paint().apply {
            style = Paint.Style.STROKE
            color = 0xFF2E7D32.toInt()
            strokeWidth = 3f
        }
        cv.drawRect(left + 6f, top + 6f, left + w - 6f, top + h - 6f, border)
        val qrSize = 330
        val qrBmp = qrBitmap(t.qrPayload, qrSize)
        val qrLeft = left + (w - qrSize) / 2f
        val qrTop = top + 16f
        cv.drawBitmap(qrBmp, qrLeft, qrTop, null)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0B2918.toInt()
            textSize = 36f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1B5E20.toInt()
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF455A64.toInt()
            textSize = 19f
            textAlign = Paint.Align.CENTER
        }
        cv.drawText(t.code, left + w / 2f, qrTop + qrSize + 40f, titlePaint)
        cv.drawText(
            "${t.rule.shortVi} (${t.rule.unitAreaM2} m²/cây) · Hàng ${t.rowNumber} - Cây ${t.colNumber}",
            left + w / 2f,
            qrTop + qrSize + 72f,
            subPaint,
        )
        cv.drawText(
            String.format(Locale.US, "GPS: %.6f, %.6f · %s", t.pos.lat, t.pos.lng, t.regionName.take(22)),
            left + w / 2f,
            qrTop + qrSize + 102f,
            metaPaint,
        )
    }
}

/**
 * THẺ THUẬT TOÁN ĐẾM CÂY THEO DIỆN TÍCH VÙNG KHOANH (SẦU RIÊNG 8m×8m · CÀ PHÊ 3m×3m),
 * GÁN NHÃN TỰ ĐỘNG & TẠO MÃ QR CHO TỪNG CÂY TRÊN BẢN ĐỒ VƯỜN.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrchardSpacingQrHubCard(
    region: GeoFeature?,
    hud: RegionHudState?,
    onSyncTreesToStore: (List<SatTreePoint>, String, String) -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pts = region?.points?.takeIf { it.size >= 3 }
        ?: hud?.points?.takeIf { it.size >= 3 }
        ?: listOf(
            GeoPoint(11.5511, 107.7996),
            GeoPoint(11.5512, 107.8012),
            GeoPoint(11.5504, 107.8016),
            GeoPoint(11.5497, 107.8011),
            GeoPoint(11.5498, 107.7997),
        )
    val regionName = region?.name ?: hud?.regionName ?: "Vùng khoanh vườn"

    var selectedRuleId by rememberSaveable { mutableStateOf(OrchardCropSpacingRule.DURIAN.id) }
    val selectedRule = remember(selectedRuleId) {
        OrchardCropSpacingRule.entries.firstOrNull { it.id == selectedRuleId } ?: OrchardCropSpacingRule.DURIAN
    }

    val report = remember(pts, selectedRule, regionName) {
        OrchardSpacingGridEngine.compute(
            points = pts,
            rule = selectedRule,
            regionName = regionName,
            maxLatticePoints = 2500,
        )
    }

    var selectedTreeCode by remember(report.rule, report.regionName) {
        mutableStateOf(report.trees.firstOrNull()?.code)
    }
    val selectedTree = remember(report, selectedTreeCode) {
        report.trees.firstOrNull { it.code == selectedTreeCode } ?: report.trees.firstOrNull()
    }
    var selectedRowFilter by remember(report.rule, report.regionName) { mutableStateOf(0) } // 0 = Tất cả hàng
    var showAllCards by remember(report.rule, report.regionName) { mutableStateOf(false) }
    var dialogTree by remember { mutableStateOf<SpacingLabeledTree?>(null) }

    // Xuất tờ nhãn QR hàng loạt (.PNG)
    var pendingSheetBmp by remember { mutableStateOf<Bitmap?>(null) }
    val saveSheetLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val b = pendingSheetBmp
        pendingSheetBmp = null
        if (uri != null && b != null) {
            val ok = saveBitmap(context, uri, b)
            onToast(if (ok) "Đã xuất tờ nhãn QR (${report.rule.shortVi}) thành công!" else "Không lưu được tệp ảnh QR.")
        }
    }

    val filteredTrees = remember(report.trees, selectedRowFilter) {
        if (selectedRowFilter == 0) report.trees
        else report.trees.filter { it.rowNumber == selectedRowFilter }
    }
    val displayedTrees = if (showAllCards) filteredTrees.take(120) else filteredTrees.take(16)
    val availableRows = remember(report.trees) { report.trees.map { it.rowNumber }.distinct().sorted() }

    VCard(modifier.fillMaxWidth()) {
        // 1. Tiêu đề & Nút chuyển nhanh Sầu riêng (8m×8m) / Cà phê (3m×3m)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Eyebrow("📐 THUẬT TOÁN ĐẾM CÂY THEO DIỆN TÍCH KHOANH VÙNG · GÁN NHÃN & MÃ QR TỪNG CÂY")
                Text(
                    "Vùng khoanh: $regionName · Diện tích ${fmtNum(report.areaM2, 1)} m² (${fmtNum(report.areaHa, 3)} ha)",
                    color = VC.text,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    "Chọn loại vườn để tính tự động số lượng cây theo khoảng cách trồng chuẩn (Sầu riêng 8m×8m = 64 m²/cây · Cà phê 3m×3m = 9 m²/cây), gán nhãn tọa độ và tạo Mã QR riêng cho từng cây.",
                    color = VC.text2,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // 2. Hai nút lớn chọn công thức: Sầu riêng (8m * 8m) vs Cà phê (3m * 3m)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OrchardCropSpacingRule.entries.forEach { rule ->
                val isSelected = rule == selectedRule
                val previewCount = max(1, floor(report.areaM2 / rule.unitAreaM2.toDouble()).toInt())
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) rule.accentColor.copy(alpha = 0.20f) else VC.elevated)
                        .border(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) rule.accentColor else VC.border,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { selectedRuleId = rule.id }
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${rule.icon} ${rule.titleVi}",
                            color = if (isSelected) rule.accentColor else VC.text,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        if (isSelected) {
                            Text(
                                "ĐANG CHỌN",
                                color = Color(0xFF07150E),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(rule.accentColor)
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        "Công thức: ⌊${fmtNum(report.areaM2, 0)} m² ÷ (${rule.spacingM}m × ${rule.spacingM}m = ${rule.unitAreaM2} m²)⌋",
                        color = VC.text2,
                        fontSize = 12.sp,
                    )
                    Text(
                        "= $previewCount cây (${rule.codePrefix}-001 → ${rule.codePrefix}-${previewCount.toString().padStart(if (previewCount >= 1000) 4 else 3, '0')})",
                        color = rule.accentColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // 3. Khung Công Thức Toán Học & 4 Chỉ Số Đầu Ra
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0A1D14))
                .border(1.2.dp, selectedRule.accentColor.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "🧮 CÔNG THỨC TÍNH SỐ LƯỢNG CÂY TRÊN VÙNG ĐÃ KHOANH (${selectedRule.titleVi.uppercase()})",
                color = selectedRule.accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                report.formulaVi,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    Triple("📐 Diện tích vùng khoanh", "${fmtNum(report.areaM2, 1)} m²", "${fmtNum(report.areaHa, 4)} ha · Chu vi ${fmtNum(report.perimeterM, 0)} m"),
                    Triple("📏 Khoảng cách mỗi cây", "${selectedRule.spacingM}m × ${selectedRule.spacingM}m", "Diện tích chiếm chỗ: ${selectedRule.unitAreaM2} m²/cây"),
                    Triple("🌳 Tổng số cây tính được", "${report.formulaTreeCount} cây", "Mật độ chuẩn: ${fmtNum(selectedRule.densityPerHa, 0)} cây/ha"),
                    Triple("🏷 Nhãn & Mã QR đã gán", "${report.firstLabel} → ${report.lastLabel}", "${report.trees.size} nhãn tọa độ · ${report.rowsCount} hàng cây"),
                ).forEach { (lbl, mainVal, subTxt) ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color(0xFF122B1E))
                            .padding(8.dp),
                    ) {
                        Text(lbl, color = VC.text2, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(mainVal, color = selectedRule.accentColor, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 2.dp))
                        Text(subTxt, color = Color(0xFFD0E4D6), fontSize = 10.5.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // 4. Bản đồ Lưới Cây Đã Gán Nhãn Trong Vùng Khoanh + Thẻ Mã QR Trực Tiếp Của Cây Đang Chọn
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Cột trái: Sơ đồ Lưới Tọa Độ Cây 8m×8m / 3m×3m cắt theo Đa Giác Vùng Khoanh
            Column(
                Modifier
                    .weight(1.35f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF081710))
                    .border(1.dp, VC.border, RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "🗺 SƠ ĐỒ LƯỚI CÂY ${selectedRule.spacingM}m×${selectedRule.spacingM}m TRONG VÙNG KHOANH",
                        color = VC.mint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "Chạm vào điểm cây để xem Nhãn & Mã QR",
                        color = VC.text2,
                        fontSize = 11.sp,
                    )
                }

                OrchardSpacingLatticeCanvas(
                    report = report,
                    selectedTreeCode = selectedTree?.code,
                    onSelectTree = { t ->
                        selectedTreeCode = t.code
                        hud?.selectedGeeTreeCode = t.code
                        hud?.showGeeTreeLabels = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF05110B)),
                )

                // Nút đồng bộ lên Bản đồ chính & Hồ sơ vườn + Nút xuất tờ nhãn QR
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(selectedRule.accentColor)
                            .clickable {
                                val satPts = report.trees.map { it.toSatTreePoint() }
                                onSyncTreesToStore(satPts, selectedRule.titleVi, regionName)
                                hud?.showGeeTreeLabels = true
                                hud?.selectedGeeTreeCode = selectedTree?.code
                            }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "🏷 Gán Nhãn & Lưu ${report.trees.size} Cây + QR Vào Hồ Sơ Vườn",
                            color = Color(0xFF06150D),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }

                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF132E20))
                            .border(1.2.dp, VC.mint, RoundedCornerShape(10.dp))
                            .clickable {
                                pendingSheetBmp = OrchardSpacingGridEngine.renderSpacingTreeSheetBitmap(report.trees, maxTreesOnSheet = 60)
                                saveSheetLauncher.launch("vwing-qr-${selectedRule.id.lowercase()}-${report.formulaTreeCount}cay.png")
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "🖨 Xuất Tờ Nhãn QR (.PNG)",
                            color = VC.mint,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }

            // Cột phải: Mã QR Cá Thể Hóa Của Cây Đang Chọn
            selectedTree?.let { st ->
                val qrBmp = remember(st.qrPayload) { qrBitmap(st.qrPayload, 260) }
                Column(
                    Modifier
                        .weight(0.95f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0D2218))
                        .border(1.5.dp, selectedRule.accentColor, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "📱 MÃ QR CỦA CÂY ${st.code}",
                        color = selectedRule.accentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Box(
                        Modifier
                            .size(164.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .padding(6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = qrBmp.asImageBitmap(),
                            contentDescription = "Mã QR cây ${st.code}",
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Text(
                        st.fullLabelVi,
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        String.format(Locale.US, "📍 GPS: %.6f, %.6f", st.pos.lat, st.pos.lng),
                        color = VC.cyan,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Ô dinh dưỡng: ${st.rule.spacingM}m × ${st.rule.spacingM}m = ${st.rule.unitAreaM2} m²/cây",
                        color = VC.text2,
                        fontSize = 11.5.sp,
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(VC.elevated)
                            .border(1.dp, selectedRule.accentColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .clickable { dialogTree = st }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "🔍 Phóng to & Tải Nhãn In QR Cây ${st.code}",
                            color = selectedRule.accentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // 5. Bộ lọc theo Hàng cây & Danh sách Nhãn + Mã QR từng cây
        if (availableRows.size > 1) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Lọc theo hàng:", color = VC.text2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                (listOf(0) + availableRows).forEach { rNum ->
                    val active = selectedRowFilter == rNum
                    val label = if (rNum == 0) "Tất cả (${report.trees.size} cây / ${availableRows.size} hàng)" else "Hàng $rNum"
                    Text(
                        label,
                        color = if (active) Color(0xFF06150D) else VC.text2,
                        fontSize = 11.5.sp,
                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (active) selectedRule.accentColor else VC.elevated)
                            .clickable { selectedRowFilter = rNum }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // Danh sách thẻ cây kèm Mã QR trực tiếp (2 cột)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            displayedTrees.chunked(2).forEach { pair ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pair.forEach { t ->
                        val isSel = selectedTree?.code == t.code
                        val thumbQr = remember(t.qrPayload) { qrBitmap(t.qrPayload, 130) }
                        Row(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) selectedRule.accentColor.copy(alpha = 0.16f) else VC.elevated)
                                .border(
                                    if (isSel) 1.8.dp else 1.dp,
                                    if (isSel) selectedRule.accentColor else VC.border,
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable {
                                    selectedTreeCode = t.code
                                    hud?.selectedGeeTreeCode = t.code
                                    hud?.showGeeTreeLabels = true
                                }
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Thumbnail QR Code của từng cây
                            Box(
                                Modifier
                                    .size(58.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White)
                                    .clickable { dialogTree = t }
                                    .padding(3.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    bitmap = thumbQr.asImageBitmap(),
                                    contentDescription = "QR ${t.code}",
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "${t.rule.icon} ${t.code}",
                                        color = selectedRule.accentColor,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                    )
                                    Text(
                                        "Hàng ${t.rowNumber} · Cây ${t.colNumber}",
                                        color = VC.cyan,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Text(
                                    "Khoảng cách: ${t.rule.spacingM}m × ${t.rule.spacingM}m (${t.rule.unitAreaM2} m²/cây)",
                                    color = VC.text,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        String.format(Locale.US, "📍 %.5f, %.5f", t.pos.lat, t.pos.lng),
                                        color = VC.text2,
                                        fontSize = 10.5.sp,
                                    )
                                    Text(
                                        "In QR ↗",
                                        color = VC.mint,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.clickable { dialogTree = t },
                                    )
                                }
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        if (filteredTrees.size > 16) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text(
                    if (showAllCards) "▲ Thu gọn danh sách nhãn & QR (đang hiện ${displayedTrees.size}/${filteredTrees.size} cây)"
                    else "▼ Xem thêm danh sách nhãn & Mã QR (${filteredTrees.size} cây trong vùng khoanh)",
                    color = selectedRule.accentColor,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(VC.elevated)
                        .border(1.dp, selectedRule.accentColor.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        .clickable { showAllCards = !showAllCards }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                )
            }
        }
    }

    dialogTree?.let { dt ->
        SpacingTreeQrDialog(
            tree = dt,
            onDismiss = { dialogTree = null },
            onToast = onToast,
        )
    }
}

@Composable
private fun OrchardSpacingLatticeCanvas(
    report: OrchardSpacingGridReport,
    selectedTreeCode: String?,
    onSelectTree: (SpacingLabeledTree) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val pts = report.points
    if (pts.size < 3) return

    val minLat = pts.minOf { it.lat }
    val maxLat = pts.maxOf { it.lat }
    val minLng = pts.minOf { it.lng }
    val maxLng = pts.maxOf { it.lng }
    val latSpan = max(1e-6, maxLat - minLat)
    val lngSpan = max(1e-6, maxLng - minLng)

    Canvas(
        modifier = modifier.pointerInput(report, pts) {
            detectTapGestures { tap ->
                val pad = 20f
                val w = size.width - pad * 2f
                val h = size.height - pad * 2f
                if (w <= 10f || h <= 10f) return@detectTapGestures
                var best: SpacingLabeledTree? = null
                var bestDist = 36f
                for (t in report.trees) {
                    val x = pad + ((t.pos.lng - minLng) / lngSpan).toFloat() * w
                    val y = pad + ((maxLat - t.pos.lat) / latSpan).toFloat() * h
                    val d = hypot(tap.x - x, tap.y - y)
                    if (d < bestDist) {
                        bestDist = d
                        best = t
                    }
                }
                if (best != null) onSelectTree(best)
            }
        },
    ) {
        val pad = 20f
        val w = (size.width - pad * 2f).coerceAtLeast(10f)
        val h = (size.height - pad * 2f).coerceAtLeast(10f)

        fun toScreen(p: GeoPoint): Offset {
            val x = pad + ((p.lng - minLng) / lngSpan).toFloat() * w
            val y = pad + ((maxLat - p.lat) / latSpan).toFloat() * h
            return Offset(x, y)
        }

        // 1. Vẽ đa giác vùng đã khoanh
        val polyPath = Path().apply {
            pts.forEachIndexed { idx, gp ->
                val o = toScreen(gp)
                if (idx == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
            }
            close()
        }
        drawPath(polyPath, color = report.rule.accentColor.copy(alpha = 0.12f))
        drawPath(polyPath, color = report.rule.accentColor, style = Stroke(width = 2.5f))

        // 2. Vẽ các điểm cây trong lưới 8m×8m hoặc 3m×3m
        val total = report.trees.size
        val dotRadius = when {
            total > 800 -> 2.6f
            total > 300 -> 3.6f
            else -> 5.2f
        }
        val showCodeBadgeStride = when {
            total <= 35 -> 1
            total <= 120 -> 5
            total <= 500 -> 20
            else -> 60
        }

        report.trees.forEachIndexed { idx, t ->
            val center = toScreen(t.pos)
            val isSel = t.code == selectedTreeCode
            if (isSel) {
                drawCircle(
                    color = Color(0xFFFFEA00),
                    radius = dotRadius + 5f,
                    center = center,
                    style = Stroke(width = 2.5f),
                )
            }
            drawCircle(
                color = if (isSel) Color(0xFFFFEA00) else report.rule.accentColor,
                radius = if (isSel) dotRadius + 2.2f else dotRadius,
                center = center,
            )
            if (isSel || idx % showCodeBadgeStride == 0) {
                val tl = measurer.measure(
                    t.code,
                    style = TextStyle(
                        color = if (isSel) Color(0xFFFFEA00) else Color.White,
                        fontSize = if (isSel) 10.5.sp else 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                )
                val tx = (center.x - tl.size.width / 2f).coerceIn(2f, size.width - tl.size.width - 2f)
                val ty = (center.y - dotRadius - tl.size.height - 2f).coerceIn(2f, size.height - tl.size.height - 2f)
                drawText(tl, topLeft = Offset(tx, ty))
            }
        }
    }
}

/** Hộp thoại xem chi tiết & lưu nhãn in Mã QR cho 1 cây bất kỳ từ thuật toán khoảng cách 8m×8m / 3m×3m. */
@Composable
fun SpacingTreeQrDialog(
    tree: SpacingLabeledTree,
    onDismiss: () -> Unit,
    onToast: (String) -> Unit,
) {
    val context = LocalContext.current
    val labelBmp = remember(tree.qrPayload) { OrchardSpacingGridEngine.renderSpacingTreeLabelBitmap(tree) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) {
            val ok = saveBitmap(context, uri, labelBmp)
            onToast(if (ok) "Đã lưu nhãn QR cây ${tree.code}." else "Không lưu được ảnh nhãn QR.")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VC.card,
        title = {
            Text(
                "${tree.rule.icon} Mã QR & Nhãn Cây · ${tree.code}",
                color = VC.text,
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Image(
                    bitmap = labelBmp.asImageBitmap(),
                    contentDescription = "Nhãn QR cây ${tree.code}",
                    modifier = Modifier
                        .size(width = 250.dp, height = 318.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White),
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(VC.elevated)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("• Mã nhãn cây: ${tree.code}", color = tree.rule.accentColor, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
                    Text("• Loại vườn & khoảng cách: ${tree.rule.titleVi}", color = VC.text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    Text("• Vị trí hàng/cột: Hàng ${tree.rowNumber} · Cây thứ ${tree.colNumber} (${tree.regionName})", color = VC.cyan, fontSize = 12.5.sp)
                    Text(String.format(Locale.US, "• Tọa độ GPS: %.6f, %.6f", tree.pos.lat, tree.pos.lng), color = VC.text2, fontSize = 12.sp)
                    Text("• Nội dung QR: ${tree.qrPayload}", color = VC.muted, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            VButton("💾 Lưu ảnh nhãn QR (.PNG)", {
                saveLauncher.launch("vwing-qr-${tree.code}.png")
            })
        },
        dismissButton = {
            TextButton(onDismiss) { Text("Đóng", color = VC.text2) }
        },
    )
}
