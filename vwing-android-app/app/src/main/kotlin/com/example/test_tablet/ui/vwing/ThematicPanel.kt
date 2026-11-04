package com.example.test_tablet.ui.vwing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ThemeKind(val label: String, val blurb: String) {
    KDE("Mật độ vấn đề", "Làm mịn các điểm có vấn đề thành một bề mặt liên tục (Kernel quartic) để thấy nơi vấn đề dồn lại."),
    EMERGING("Điểm nóng mới nổi", "Chia thời gian thành các mốc, tìm cụm vấn đề bất thường (Gi*) và xem cụm đó mới hình thành, kéo dài hay đang giảm (Mann-Kendall)."),
    BIVARIATE("Hai biến", "Tô màu theo hai thông tin cùng lúc để thấy chỗ nào chúng đi cùng nhau, ví dụ nhiều quả nhưng cũng nhiều vấn đề."),
    SUITABILITY("Cơ hội & ràng buộc", "Chồng các lớp có trọng số để gợi ý chỗ còn trống, ít vấn đề, đủ xa mép ranh để trồng thêm; vùng vi phạm ràng buộc bị loại."),
}

internal class ThemeOutput(
    val bounds: GeoBounds, val bitmap: ImageBitmap, val label: String,
    val verdict: Verdict, val headline: String, val sub: String,
    val legend: List<Pair<Color, String>>, val bivariate: Boolean, val details: List<String>,
)

internal fun argb(a: Int, hex: Number) = (a shl 24) or (hex.toInt() and 0xFFFFFF)
internal fun toBitmap(cols: Int, rows: Int, px: IntArray): ImageBitmap =
    android.graphics.Bitmap.createBitmap(px, cols, rows, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
internal fun colorOf(argb: Int) = Color(argb)

/** Bảng màu hai biến 3×3 (Joshua Stevens): hàng = biến B thấp→cao, cột = biến A thấp→cao. */
private val BIVAR = arrayOf(
    longArrayOf(0xE8E8E8, 0xACE4E4, 0x5AC8C8),
    longArrayOf(0xDFB0D6, 0xA5ADD3, 0x5698B9),
    longArrayOf(0xBE64AC, 0x8C62AA, 0x3B4994),
)
private val EMERGING_COLOR = mapOf(
    EmergingClass.NEW to 0xE31A1C, EmergingClass.CONSECUTIVE to 0xFF7F00, EmergingClass.INTENSIFYING to 0x99000D,
    EmergingClass.PERSISTENT to 0xB15928, EmergingClass.DIMINISHING to 0xFDBF6F, EmergingClass.HISTORICAL to 0x6A8CAF,
    EmergingClass.SPORADIC to 0xFFD92F,
)
private val SUIT_COLOR = longArrayOf(0x6B6B6B, 0xD73027, 0xFEE08B, 0x1A9850)
private val SUIT_LABEL = arrayOf("Ràng buộc (loại)", "Thấp", "Vừa", "Cao")

internal fun ha(v: Double) = "${fmtNum(v, 2)} ha"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThematicPanel(c: VCtx, region: GeoFeature?, problem: String?, onOverlay: (MapOverlay?) -> Unit) {
    var kind by rememberSaveable { mutableStateOf(ThemeKind.KDE) }
    var bandwidth by rememberSaveable { mutableStateOf(20) }
    var bins by rememberSaveable { mutableStateOf(6) }
    var varA by rememberSaveable { mutableStateOf(BivarVar.FRUITS) }
    var varB by rememberSaveable { mutableStateOf(BivarVar.ISSUES) }
    var wSpace by rememberSaveable { mutableStateOf(.4f) }
    var wHealth by rememberSaveable { mutableStateOf(.4f) }
    var wInterior by rememberSaveable { mutableStateOf(.2f) }
    var spacing by rememberSaveable { mutableStateOf(4f) }
    var edge by rememberSaveable { mutableStateOf(3f) }
    var issueRadius by rememberSaveable { mutableStateOf(8f) }
    var alpha by remember { mutableFloatStateOf(.75f) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val signature = listOf(region?.id, region?.points, region?.version, kind)
    var out by remember(signature) { mutableStateOf<ThemeOutput?>(null) }
    val currentOverlay by rememberUpdatedState(onOverlay)
    val currentSignature by rememberUpdatedState(signature)
    val scope = rememberCoroutineScope()

    LaunchedEffect(out, alpha) { currentOverlay(out?.let { MapOverlay(it.bounds, it.bitmap, alpha, it.label) }) }
    DisposableEffect(Unit) { onDispose { currentOverlay(null) } }

    Text(kind.blurb, color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
    ChipRow(ThemeKind.entries.toList(), kind, { it.label }, { kind = it; error = null }, Modifier.padding(top = 8.dp))

    when (kind) {
        ThemeKind.KDE, ThemeKind.EMERGING -> {
            if (kind == ThemeKind.KDE) {
                Text("Bán kính ảnh hưởng của mỗi điểm", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                ChipRow(listOf(10, 20, 40), bandwidth, { "$it m" }, { bandwidth = it })
            } else {
                Text("Số mốc thời gian", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                ChipRow(listOf(4, 6, 8), bins, { "$it mốc" }, { bins = it })
            }
        }
        ThemeKind.BIVARIATE -> {
            val names = BivarVar.entries.map { it.label }
            VPicker("Biến A (ngang)", names, varA.label, { l -> varA = BivarVar.entries.first { it.label == l } }, Modifier.padding(top = 8.dp))
            VPicker("Biến B (dọc)", names, varB.label, { l -> varB = BivarVar.entries.first { it.label == l } }, Modifier.padding(top = 8.dp))
            Text("Bán kính làm trơn", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            ChipRow(listOf(10, 20, 40), bandwidth, { "$it m" }, { bandwidth = it })
        }
        ThemeKind.SUITABILITY -> {
            Column(Modifier.padding(top = 8.dp)) {
                Text("Trọng số tiêu chí (tự chuẩn hoá)", color = VC.text2, fontSize = 14.sp)
                Text("${ThematicMaps.Criterion.SPACE.label}: ${fmtNum(wSpace * 100.0, 0)}", color = VC.muted, fontSize = 13.sp)
                Slider(wSpace, { wSpace = it }, valueRange = 0f..1f)
                Text("${ThematicMaps.Criterion.HEALTH.label}: ${fmtNum(wHealth * 100.0, 0)}", color = VC.muted, fontSize = 13.sp)
                Slider(wHealth, { wHealth = it }, valueRange = 0f..1f)
                Text("${ThematicMaps.Criterion.INTERIOR.label}: ${fmtNum(wInterior * 100.0, 0)}", color = VC.muted, fontSize = 13.sp)
                Slider(wInterior, { wInterior = it }, valueRange = 0f..1f)
                Text("Ràng buộc cứng (loại hẳn)", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                Text("Cách cây hiện có tối thiểu ${fmtNum(spacing.toDouble(), 0)} m", color = VC.muted, fontSize = 13.sp)
                Slider(spacing, { spacing = it }, valueRange = 1f..10f)
                Text("Cách mép ranh tối thiểu ${fmtNum(edge.toDouble(), 0)} m", color = VC.muted, fontSize = 13.sp)
                Slider(edge, { edge = it }, valueRange = 0f..15f)
                Text("Cách cây đang có vấn đề tối thiểu ${fmtNum(issueRadius.toDouble(), 0)} m", color = VC.muted, fontSize = 13.sp)
                Slider(issueRadius, { issueRadius = it }, valueRange = 0f..30f)
            }
        }
    }

    VButton(if (busy) "Đang tính…" else "Tạo bản đồ", {
        val reg = region ?: return@VButton
        val snapshot = signature; val data = c.d; val k = kind
        val bw = bandwidth.toDouble(); val nb = bins; val a = varA; val b = varB
        val weights = mapOf(ThematicMaps.Criterion.SPACE to wSpace.toDouble(), ThematicMaps.Criterion.HEALTH to wHealth.toDouble(), ThematicMaps.Criterion.INTERIOR to wInterior.toDouble())
        val sp = spacing.toDouble(); val ed = edge.toDouble(); val ir = issueRadius.toDouble()
        busy = true; error = null; out = null
        scope.launch {
            try {
                val r = withContext(Dispatchers.Default) { compute(k, data, reg.points.toList(), bw, nb, a, b, weights, sp, ed, ir) }
                if (snapshot == currentSignature) out = r
            } catch (e: CancellationException) { throw e }
            catch (e: IllegalStateException) { error = e.message }
            catch (e: Exception) { error = "Không tạo được bản đồ: ${e.message ?: e.javaClass.simpleName}" }
            finally { busy = false }
        }
    }, enabled = !busy && problem == null && region != null, modifier = Modifier.padding(top = 10.dp))

    error?.let { Notice(it, VC.amber, Modifier.padding(top = 10.dp)) }
    out?.let { o -> ThemeOutputCard(c, o, alpha, { alpha = it }) }
}

/** Khối kết quả dùng chung cho các bản đồ chuyên đề: kết luận, độ đậm lớp, chú giải và chi tiết kỹ thuật. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThemeOutputCard(c: VCtx, o: ThemeOutput, alpha: Float, onAlpha: (Float) -> Unit) {
    Column {
        Spacer(Modifier.height(10.dp))
        VerdictBanner(o.verdict, o.headline, o.sub)
        Text("Độ đậm lớp bản đồ", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
        Slider(alpha, onAlpha, valueRange = .1f..1f)
        if (o.bivariate) BivariateLegend(o.legend, Modifier.padding(top = 4.dp))
        else FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            o.legend.forEach { (col, name) ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).background(col)); Spacer(Modifier.width(6.dp)); Text(name, color = VC.text2, fontSize = 13.sp)
                }
            }
        }
        TechnicalDetails(c.simpleMode, Modifier.padding(top = 4.dp)) {
            o.details.forEach { Text("• $it", color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp)) }
        }
    }
}

@Composable
private fun BivariateLegend(colors: List<Pair<Color, String>>, modifier: Modifier = Modifier) {
    Column(modifier) {
        // hàng trên cùng là B cao
        for (row in 2 downTo 0) Row {
            for (col in 0..2) Box(Modifier.size(26.dp).background(colors[row * 3 + col].first))
        }
        Text(colors.getOrNull(9)?.second ?: "", color = VC.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Chạy ngoài luồng giao diện. Ném IllegalStateException(thông điệp tiếng Việt) khi thiếu dữ liệu. */
private fun compute(kind: ThemeKind, d: GardenData, ring: List<GeoPoint>, bw: Double, bins: Int, a: BivarVar, b: BivarVar,
                    weights: Map<ThematicMaps.Criterion, Double>, spacing: Double, edge: Double, issueR: Double): ThemeOutput {
    val g = ThematicMaps.grid(ring) ?: error("Ranh giới quá nhỏ hoặc không hợp lệ để tạo lưới phân tích.")
    val (incidents, skipped) = ThematicMaps.incidentsOf(d, ring)
    val trees = ThematicMaps.treesIn(d, ring)
    val gridNote = "Lưới ${g.cols}×${g.rows} ô, mỗi ô ≈ ${fmtNum(g.cellWm, 1)}×${fmtNum(g.cellHm, 1)} m."
    val skipNote = if (skipped > 0) "Bỏ qua $skipped vấn đề không xác định được vị trí (cây chưa có toạ độ, ngoài ranh hoặc chỉ gán theo khu)." else null
    val label = kind.label

    when (kind) {
        ThemeKind.KDE -> {
            if (incidents.isEmpty()) error("Chưa có vấn đề nào gắn với cây có toạ độ trong vùng này.")
            val k = ThematicMaps.kde(g, incidents.map { it.p to it.w }, bw)
            val px = IntArray(g.size) { i ->
                val t = if (!g.inside[i] || k.max <= 0) 0.0 else k.density[i] / k.max
                if (t < .03) 0 else {
                    val r = 255; val gr = (235 - 200 * t).toInt().coerceIn(30, 235); val bl = (60 - 40 * t).toInt().coerceIn(20, 60)
                    argb((90 + 150 * t).toInt(), ((r shl 16) or (gr shl 8) or bl).toLong())
                }
            }
            val hotShare = (0 until g.size).count { g.inside[it] && k.max > 0 && k.density[it] >= .5 * k.max } * g.cellHa
            val legend = listOf(colorOf(argb(255, 0xFFEB3C)) to "Thưa", colorOf(argb(255, 0xFF6428)) to "Vừa", colorOf(argb(255, 0xFF1E14)) to "Dày nhất")
            return ThemeOutput(g.bounds, toBitmap(g.cols, g.rows, px), "Mật độ vấn đề · ${fmtNum(bw, 0)} m",
                Verdict.WARN, "Vấn đề tập trung nhiều nhất trên ${ha(hotShare)}",
                "${k.points} điểm ghi nhận, cao nhất ${fmtNum(k.max, 1)} điểm/ha (đã nhân trọng số ưu tiên).", legend, false,
                listOfNotNull(gridNote, "Kernel quartic, bán kính ${fmtNum(bw, 0)} m; vấn đề ưu tiên Cao tính 2, Vừa 1, Thấp 0,5.",
                    "Mật độ phụ thuộc bán kính; đổi bán kính để kiểm tra cụm có bền không. Đây là nơi vấn đề được GHI NHẬN nhiều, chưa chắc là nơi bệnh nặng nhất.", skipNote))
        }
        ThemeKind.EMERGING -> {
            if (incidents.size < ThematicMaps.MIN_INCIDENTS_EMERGING) error("Cần ít nhất ${ThematicMaps.MIN_INCIDENTS_EMERGING} điểm vấn đề có vị trí và thời gian (hiện có ${incidents.size}).")
            val coarse = ThematicMaps.grid(ring, maxCells = 1_600, minCellM = 5.0) ?: error("Ranh giới quá nhỏ để chia ô phân tích.")
            val r = ThematicMaps.emerging(coarse, incidents, bins)
                ?: error("Các vấn đề xảy ra cùng một thời điểm hoặc quá ít để chia mốc thời gian. Cần dữ liệu trải theo thời gian.")
            val px = IntArray(coarse.size) { i -> val col = EMERGING_COLOR[r.classes[i]]; if (!coarse.inside[i] || col == null) 0 else argb(200, col) }
            val newHa = r.areaHa[EmergingClass.NEW] ?: 0.0
            val growing = (r.areaHa[EmergingClass.INTENSIFYING] ?: 0.0) + (r.areaHa[EmergingClass.CONSECUTIVE] ?: 0.0) + (r.areaHa[EmergingClass.PERSISTENT] ?: 0.0)
            val activeHa = EmergingClass.entries.filter { it.hot }.sumOf { r.areaHa[it] ?: 0.0 }
            val verdict = if (newHa + growing > 0) Verdict.BAD else if (activeHa > 0) Verdict.WARN else Verdict.GOOD
            val headline = when {
                newHa + growing > 0 -> "Có cụm vấn đề mới hoặc đang kéo dài trên ${ha(newHa + growing)}"
                activeHa > 0 -> "Có điểm nóng chập chờn/giảm dần trên ${ha(activeHa)}"
                else -> "Không thấy cụm vấn đề bất thường theo thời gian"
            }
            val legend = EMERGING_COLOR.map { (cl, col) -> colorOf(argb(255, col)) to "${cl.label} (${ha(r.areaHa[cl] ?: 0.0)})" }
            return ThemeOutput(coarse.bounds, toBitmap(coarse.cols, coarse.rows, px), "Điểm nóng mới nổi · $bins mốc",
                verdict, headline, "${incidents.size} điểm vấn đề chia thành $bins mốc thời gian (${r.binLabels.first()} → nay).", legend, false,
                listOfNotNull("Lưới ${coarse.cols}×${coarse.rows} ô (≈ ${fmtNum(coarse.cellWm, 0)} m/ô), Gi* với láng giềng 2 ô, nóng khi z ≥ 1,96; xu hướng bằng Mann-Kendall.",
                    "Rút gọn từ Emerging Hot Spot Analysis của Esri: chỉ 7 loại điểm nóng, KHÔNG xét điểm lạnh, không hiệu chỉnh kiểm định nhiều lần, không hiệu chỉnh đồng hạng.",
                    "Vườn nhỏ và ít điểm: một cụm có thể do vài lần ghi chép; dùng như gợi ý đi kiểm tra, không phải kết luận dịch bệnh.", skipNote))
        }
        ThemeKind.BIVARIATE -> {
            if (a == b) error("Hãy chọn hai biến khác nhau.")
            for (v in listOf(a, b)) {
                val n = when (v) { BivarVar.ISSUES -> incidents.size; BivarVar.TREES -> trees.size; BivarVar.FRUITS -> trees.count { it.fruits != null } }
                if (n == 0) error("Chưa có dữ liệu cho \"${v.label}\" trong vùng này.")
            }
            val sa = ThematicMaps.variableSurface(g, a, incidents, trees, bw)
            val sb = ThematicMaps.variableSurface(g, b, incidents, trees, bw)
            val r = ThematicMaps.bivariate(g, sa, sb)
            val px = IntArray(g.size) { i -> if (r.cls[i] < 0) 0 else argb(215, BIVAR[r.cls[i] / 3][r.cls[i] % 3]) }
            val hh = r.areaHa[8]; val hl = r.areaHa[2]; val lh = r.areaHa[6]
            val legend = (0..8).map { colorOf(argb(255, BIVAR[it / 3][it % 3])) to "" } +
                listOf(Color.Transparent to "Ngang: ${a.label} thấp → cao · Dọc: ${b.label} thấp → cao (chia 3 nhóm bằng nhau theo từng biến)")
            return ThemeOutput(g.bounds, toBitmap(g.cols, g.rows, px), "${a.label} × ${b.label}",
                Verdict.UNKNOWN, "Cả hai cùng cao trên ${ha(hh)}",
                "${a.label} cao & ${b.label} thấp: ${ha(hl)} · ${a.label} thấp & ${b.label} cao: ${ha(lh)}.", legend, true,
                listOfNotNull(gridNote, "Mỗi biến là bề mặt Kernel bán kính ${fmtNum(bw, 0)} m, chia thấp/vừa/cao theo tam phân vị của các ô trong vùng.",
                    "Tam phân vị là TƯƠNG ĐỐI trong vườn này: \"cao\" nghĩa là cao hơn phần còn lại của vườn, không phải vượt một ngưỡng chuẩn. Tương quan không chứng minh nguyên nhân.", skipNote))
        }
        ThemeKind.SUITABILITY -> {
            if (trees.isEmpty()) error("Cần cây có toạ độ trong vùng để xác định chỗ trống.")
            if (weights.values.sum() <= 0) error("Hãy đặt ít nhất một trọng số lớn hơn 0.")
            val issuePts = incidents.map { it.p }.distinct()
            val s = ThematicMaps.suitability(g, ring, trees, issuePts, weights, spacing, edge, issueR, bw.coerceAtLeast(20.0))
            val px = IntArray(g.size) { i -> val cl = s.cls[i]; if (cl < 0) 0 else argb(if (cl == 0) 150 else 205, SUIT_COLOR[cl]) }
            val high = s.areaHa[3]; val med = s.areaHa[2]
            val legend = (0..3).map { colorOf(argb(255, SUIT_COLOR[it])) to "${SUIT_LABEL[it]} (${ha(s.areaHa[it])})" }
            return ThemeOutput(g.bounds, toBitmap(g.cols, g.rows, px), "Cơ hội trồng thêm",
                if (high > 0) Verdict.GOOD else if (med > 0) Verdict.WARN else Verdict.BAD,
                if (high > 0) "Còn ${ha(high)} chỗ phù hợp cao để trồng thêm" else if (med > 0) "Chỉ có chỗ phù hợp vừa (${ha(med)})" else "Không còn chỗ phù hợp với các ràng buộc đã đặt",
                "Bị loại bởi ràng buộc: ${ha(s.areaHa[0])} trên tổng ${ha(g.insideCount * g.cellHa)}.", legend, false,
                listOfNotNull(gridNote, "Điểm = Σ trọng số × tiêu chí (0–1); Thấp < 0,4, Vừa < 0,7, Cao ≥ 0,7. Ràng buộc: cách cây ≥ ${fmtNum(spacing, 0)} m, cách ranh ≥ ${fmtNum(edge, 0)} m, cách cây có vấn đề ≥ ${fmtNum(issueR, 0)} m.",
                    "Chỉ dùng dữ liệu đã ghi trong app (vị trí cây, vấn đề, ranh). KHÔNG xét độ dốc, đất, nước, ngập hay quyền sử dụng đất. Trọng số do bạn đặt, không phải AHP đã hiệu chuẩn; kiểm tra thực địa trước khi trồng.", skipNote))
        }
    }
}
