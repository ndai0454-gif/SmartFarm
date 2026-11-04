package com.example.test_tablet.ui.vwing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.Dates
import com.example.test_tablet.data.vwing.GardenData
import com.example.test_tablet.data.vwing.ImageKind
import com.example.test_tablet.data.vwing.ImageSet
import com.example.test_tablet.data.vwing.IndexSource
import com.example.test_tablet.data.vwing.IndexStat
import com.example.test_tablet.data.vwing.Ndvi
import com.example.test_tablet.data.vwing.NdviIO
import com.example.test_tablet.data.vwing.Preprocess
import com.example.test_tablet.data.vwing.SensorKind
import com.example.test_tablet.data.vwing.VegIndex
import com.example.test_tablet.data.vwing.activeTrees
import com.example.test_tablet.data.vwing.effectiveCondition
import com.example.test_tablet.data.vwing.newId
import com.example.test_tablet.data.vwing.treeByCode
import com.example.test_tablet.data.vwing.treeById
import com.example.test_tablet.data.vwing.zoneNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val DISCLAIMER = "NDVI chỉ phản ánh độ xanh/phản xạ của tán lá theo dữ liệu đã nhập. Không dùng để kết luận bệnh hay thiếu dinh dưỡng; luôn kiểm tra tại vườn trước khi xử lý."

fun scopeLabel(d: GardenData, scope: String): String = when {
    scope == "garden" -> "Toàn vườn"
    scope.startsWith("zone:") -> scope.removePrefix("zone:")
    scope.startsWith("tree:") -> "Cây " + (d.treeById(scope.removePrefix("tree:"))?.code ?: "?")
    else -> scope
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NdviTab(c: VCtx) {
    val d = c.d
    var showCompute by remember { mutableStateOf(false) }
    var showProvided by remember { mutableStateOf(false) }
    PageHeader(
        Icons.Filled.Eco,
        "CHỈ SỐ THỰC VẬT", "NDVI theo thời gian",
        "Chỉ nhận dữ liệu có băng Đỏ (Red) và cận hồng ngoại (NIR) từ cảm biến đa phổ. Mỗi bản ghi nêu rõ ngày, độ phân giải, tiền xử lý và vùng thiếu dữ liệu.",
    ) {
        VButton("Tính từ băng Red + NIR", { showCompute = true }, enabled = d.imagery.any { it.kind == ImageKind.BAND_RED } && d.imagery.any { it.kind == ImageKind.BAND_NIR })
        VButton("Nhập giá trị từ nhà cung cấp", { showProvided = true }, primary = false)
    }
    Notice(DISCLAIMER, VC.amber, Modifier.padding(bottom = 12.dp))
    if (d.imagery.none { it.kind == ImageKind.BAND_RED } || d.imagery.none { it.kind == ImageKind.BAND_NIR })
        Text("Để tính NDVI trên máy: ở tab Ảnh, nhập một băng Red và một băng NIR (cảm biến đa phổ, cùng kích thước, cùng khung địa lý, cùng ngày bay).", color = VC.muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))

    VCard(Modifier.fillMaxWidth()) {
        val list = d.indices.sortedByDescending { it.date }
        if (list.isEmpty()) EmptyNote("Chưa có bản ghi NDVI nào.")
        list.forEach { v ->
            Column(Modifier.fillMaxWidth().clickable { c.nav.push("ndvi|${v.id}") }.padding(vertical = 10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Dates.fmt(v.date), color = VC.mint, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(v.name, color = VC.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    SyncTag(v.sync)
                }
                FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Tag(v.source.label, VC.cyan)
                    Tag(v.preprocess.label, if (v.preprocess == Preprocess.REFLECTANCE) VC.mint else VC.amber)
                    Tag(v.resolutionM?.let { "${fmtNum(it, 2)} m/pixel" } ?: "Không rõ độ phân giải", if (Ndvi.resolutionWarning(v) == null) VC.text2 else VC.amber)
                    v.stats.firstOrNull { it.scope == "garden" }?.let { Tag("Toàn vườn ${fmtNum(it.mean, 2)}", VC.text2) }
                }
            }
            Divider2()
        }
    }
    if (showCompute) ComputeDialog(c) { showCompute = false }
    if (showProvided) ProvidedDialog(c) { showProvided = false }
}

@Composable
private fun ComputeDialog(c: VCtx, onDismiss: () -> Unit) {
    val reds = c.d.imagery.filter { it.kind == ImageKind.BAND_RED }.sortedByDescending { it.capturedAt }
    val nirs = c.d.imagery.filter { it.kind == ImageKind.BAND_NIR }.sortedByDescending { it.capturedAt }
    fun lab(s: ImageSet) = "${s.name} · ${Dates.fmt(s.capturedAt)}"
    var red by remember { mutableStateOf(reds.first().id) }
    var nir by remember { mutableStateOf(nirs.first().id) }
    var pre by remember { mutableStateOf(Preprocess.UNKNOWN) }
    var canopy by remember { mutableStateOf("3") }
    var provider by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val sr = reds.firstOrNull { it.id == red }; val sn = nirs.firstOrNull { it.id == nir }
    val check = if (sr != null && sn != null) Ndvi.checkBands(sr, sn) else "Chọn băng."
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() }, containerColor = VC.card,
        title = { Text("Tính NDVI từ băng Red + NIR", color = VC.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VPicker("Băng Đỏ (Red)", reds.map(::lab), sr?.let(::lab) ?: "", { l -> red = reds.first { lab(it) == l }.id })
                VPicker("Băng cận hồng ngoại (NIR)", nirs.map(::lab), sn?.let(::lab) ?: "", { l -> nir = nirs.first { lab(it) == l }.id })
                check?.let { Notice(it, VC.red) } ?: Notice("Hai băng khớp nhau (cùng cảm biến đa phổ, kích thước, khung và ngày).", VC.teal)
                VPicker("Tiền xử lý của hai băng", Preprocess.values().map { it.label }, pre.label, { l -> pre = Preprocess.values().first { it.label == l } })
                Text("Giá trị thô (DN) hoặc chưa rõ: kết quả chỉ so sánh tương đối trong cùng một ngày bay.", color = VC.muted, fontSize = 13.sp)
                VField(canopy, { canopy = it; err = null }, "Bán kính tán cây (m) để lấy giá trị theo từng cây", number = true)
                VField(provider, { provider = it }, "Nguồn dữ liệu (để trống = nguồn của băng Red)")
                Text("Ảnh lớn được thu nhỏ để tính (tối đa 1600 px cạnh dài). Giá trị theo cây chỉ có khi độ phân giải ≤ bán kính tán.", color = VC.muted, fontSize = 13.sp)
                err?.let { Text(it, color = VC.red, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            VButton(if (busy) "Đang tính…" else "Tính NDVI", {
                val r = parseNum(canopy)
                if (r == null || r <= 0) { err = "Bán kính tán phải là số lớn hơn 0."; return@VButton }
                busy = true; err = null
                scope.launch {
                    val res = withContext(Dispatchers.IO) { NdviIO.computeFromBands(c.store, sr!!, sn!!, pre, r, provider) }
                    busy = false
                    res.onSuccess { v ->
                        val o = c.store.addVegIndex(v)
                        if (o.ok) { c.toast("Đã tính NDVI."); onDismiss(); c.nav.push("ndvi|${v.id}") } else err = o.message
                    }.onFailure { err = it.message }
                }
            }, enabled = check == null && !busy)
        },
        dismissButton = { TextButton({ if (!busy) onDismiss() }) { Text("Hủy", color = VC.text2) } },
    )
}

@Composable
private fun ProvidedDialog(c: VCtx, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(Dates.fmt(Dates.now())) }
    var provider by remember { mutableStateOf("") }
    var sensor by remember { mutableStateOf(SensorKind.UNKNOWN) }
    var pre by remember { mutableStateOf(Preprocess.UNKNOWN) }
    var res by remember { mutableStateOf("") }
    var canopy by remember { mutableStateOf("3") }
    var csv by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = VC.card,
        title = { Text("Nhập NDVI từ nhà cung cấp", color = VC.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Chỉ nhận khi bạn xác nhận dữ liệu gốc có băng Red và NIR (cảm biến đa phổ). Ảnh RGB không thể thành NDVI.", color = VC.text2, fontSize = 14.sp)
                VField(name, { name = it; err = null }, "Tên bản ghi *", placeholder = "NDVI bay 05/10")
                VField(date, { date = it; err = null }, "Ngày dữ liệu (dd/MM/yyyy) *")
                VField(provider, { provider = it; err = null }, "Nguồn / đơn vị cung cấp *")
                VPicker("Cảm biến gốc", SensorKind.values().map { it.label }, sensor.label, { l -> sensor = SensorKind.values().first { it.label == l }; err = null })
                VPicker("Tiền xử lý", Preprocess.values().map { it.label }, pre.label, { l -> pre = Preprocess.values().first { it.label == l } })
                VField(res, { res = it; err = null }, "Độ phân giải (m/pixel) – cần để nhận giá trị theo cây", number = true)
                VField(canopy, { canopy = it; err = null }, "Bán kính tán cây (m)", number = true)
                VField(csv, { csv = it; err = null }, "Giá trị (mỗi dòng: phạm vi; trung bình; nhỏ nhất; lớn nhất; số điểm ảnh)", singleLine = false, minLines = 4,
                    placeholder = "Toàn vườn; 0,58; 0,12; 0,86; 4200\nKhu A; 0,61; 0,30; 0,85; 700\nA-04; 0,47; 0,35; 0,55; 14")
                Text("Phạm vi: \"Toàn vườn\", tên khu, hoặc mã cây. Giá trị theo cây chỉ nhận khi độ phân giải ≤ bán kính tán.", color = VC.muted, fontSize = 13.sp)
                err?.let { Text(it, color = VC.red, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            VButton("Lưu", {
                val dt = Dates.parse(date)
                val resM = if (res.isBlank()) null else parseNum(res)
                val rad = parseNum(canopy)
                if (dt == null) { err = "Ngày không hợp lệ."; return@VButton }
                if (res.isNotBlank() && resM == null) { err = "Độ phân giải không hợp lệ."; return@VButton }
                if (rad == null) { err = "Bán kính tán không hợp lệ."; return@VButton }
                val stats = mutableListOf<IndexStat>()
                csv.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEachIndexed { i, line ->
                    val p = line.split(';', '\t').map { it.trim() }
                    val v = p.drop(1).map(::parseNum)
                    if (p.size != 5 || v.any { it == null }) { err = "Dòng ${i + 1}: cần đúng 5 cột phạm vi; trung bình; nhỏ nhất; lớn nhất; số điểm ảnh."; return@VButton }
                    val scopeKey = parseScope(c.d, p[0]) ?: run { err = "Dòng ${i + 1}: không nhận ra phạm vi \"${p[0]}\"."; return@VButton }
                    stats += IndexStat(scopeKey, v[0]!!, v[1]!!, v[2]!!, v[3]!!.toInt(), 100.0)
                }
                if (stats.isEmpty()) { err = "Nhập ít nhất một dòng giá trị."; return@VButton }
                val rec = VegIndex(newId(), name.trim(), IndexSource.PROVIDED, dt + 12 * 3_600_000L, provider.trim(), sensor, pre, resM, null, canopyRadiusM = rad, stats = stats,
                    notes = listOf("Giá trị do nhà cung cấp, chưa được app kiểm tra lại từ ảnh gốc.") + if (pre != Preprocess.REFLECTANCE) listOf("Chưa phải phản xạ đã hiệu chỉnh: không so sánh với ngày khác.") else emptyList())
                val r = c.store.addVegIndex(rec)
                if (r.ok) { c.toast("Đã lưu NDVI."); onDismiss() } else err = r.message
            })
        },
        dismissButton = { TextButton(onDismiss) { Text("Hủy", color = VC.text2) } },
    )
}

private fun parseScope(d: GardenData, raw: String): String? {
    val t = raw.trim()
    if (t.equals("toàn vườn", true) || t.equals("garden", true)) return "garden"
    d.treeByCode(t)?.let { return "tree:${it.id}" }
    d.zoneNames().firstOrNull { it.equals(t, true) }?.let { return "zone:$it" }
    return null
}

// ---------- chi tiết ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NdviDetailScreen(c: VCtx, id: String) {
    val d = c.d
    val v = d.indices.firstOrNull { it.id == id }
    if (v == null) { Text("Không tìm thấy bản ghi.", color = VC.text2); VButton("← Quay lại", { c.nav.pop() }, primary = false); return }
    val grid by produceState<Ndvi.Grid?>(null, v.gridFile) { value = v.gridFile?.let { f -> withContext(Dispatchers.IO) { NdviIO.loadGrid(File(c.store.photoDir, f)) } } }
    val heat = remember(grid) { grid?.let { NdviIO.heatBitmap(it).asImageBitmap() } }
    var scope by remember { mutableStateOf("garden") }

    Row(Modifier.padding(bottom = 10.dp)) { VButton("← Quay lại", { c.nav.pop() }, primary = false) }
    PageHeader(Icons.Filled.Eco, "CHỈ SỐ THỰC VẬT", v.name, "${v.source.label} · ${Dates.fmt(v.date)} · nguồn: ${v.provider}") { SyncTag(v.sync) }
    Notice(DISCLAIMER, VC.amber, Modifier.padding(bottom = 12.dp))

    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("THÔNG TIN DỮ LIỆU")
        Spacer(Modifier.height(8.dp))
        listOfNotNull(
            "Cảm biến" to v.sensor.label,
            "Tiền xử lý" to v.preprocess.label,
            "Độ phân giải" to (v.resolutionM?.let { "${fmtNum(it, 2)} m/pixel" + if (v.gridW > 0) " (lưới ${v.gridW}×${v.gridH})" else "" } ?: "Không rõ"),
            "Bán kính tán dùng" to "${fmtNum(v.canopyRadiusM, 1)} m",
            v.bounds?.let { "Khung địa lý" to it.toString() },
        ).forEach { (k, x) -> Row(Modifier.padding(vertical = 3.dp)) { Text(k, color = VC.muted, fontSize = 14.sp, modifier = Modifier.weight(.38f)); Text(x, color = VC.text, fontSize = 14.sp, modifier = Modifier.weight(.62f)) } }
        Ndvi.resolutionWarning(v)?.let { Notice(it, VC.amber, Modifier.padding(top = 8.dp)) }
        v.notes.forEach { Text("• $it", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
    }
    Spacer(Modifier.height(14.dp))

    if (heat != null && v.bounds != null) {
        VCard(Modifier.fillMaxWidth()) {
            Eyebrow("BẢN ĐỒ NDVI · ${Dates.fmt(v.date)} · ${v.resolutionM?.let { "${fmtNum(it, 2)} m/pixel" } ?: "?"}")
            Spacer(Modifier.height(8.dp))
            ImageViewer(
                ViewerImage(heat, v.bounds, Dates.fmt(v.date)), Modifier.fillMaxWidth().height(380.dp),
                trees = d.activeTrees.mapNotNull { t -> t.pos?.let { ViewerTree(t.code, it, d.effectiveCondition(t)) } },
            )
            NdviLegend()
        }
        Spacer(Modifier.height(14.dp))
    } else if (v.source == IndexSource.COMPUTED) {
        VCard(Modifier.fillMaxWidth()) { Eyebrow("BẢN ĐỒ NDVI"); Text(if (grid == null) "Đang đọc lưới…" else "Dữ liệu chưa có khung địa lý nên không phủ lên bản đồ.", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(14.dp))
    }

    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("GIÁ TRỊ THEO PHẠM VI")
        Spacer(Modifier.height(6.dp))
        if (v.stats.isEmpty()) EmptyNote("Không có giá trị nào.")
        val ordered = v.stats.sortedWith(compareBy({ when { it.scope == "garden" -> 0; it.scope.startsWith("zone:") -> 1; else -> 2 } }, { scopeLabel(d, it.scope) }))
        ordered.forEach { s ->
            Row(Modifier.fillMaxWidth().clickable { scope = s.scope }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(scopeLabel(d, s.scope), color = if (scope == s.scope) VC.lime else VC.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(.32f))
                Text("TB ${fmtNum(s.mean, 2)}", color = VC.text, fontSize = 15.sp, modifier = Modifier.weight(.2f))
                Text("${fmtNum(s.min, 2)} … ${fmtNum(s.max, 2)}", color = VC.text2, fontSize = 14.sp, modifier = Modifier.weight(.28f))
                Text("${fmtInt(s.n)} điểm · ${fmtNum(s.validPct, 0)}% hợp lệ", color = VC.muted, fontSize = 13.sp, modifier = Modifier.weight(.3f))
            }
            Divider2()
        }
    }
    Spacer(Modifier.height(14.dp))

    // chuỗi thời gian của phạm vi đang chọn
    val series = remember(d.indices, scope) { Ndvi.series(d, scope) }
    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("THEO THỜI GIAN · ${scopeLabel(d, scope).uppercase()}")
        if (series.size < 2) Text("Cần ít nhất hai bản ghi có phạm vi này để xem biến thiên. Chọn phạm vi khác ở bảng trên.", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        else {
            SeriesChart(series.map { it.index.date to it.stat.mean }, Modifier.fillMaxWidth().height(140.dp).padding(top = 8.dp))
            series.zipWithNext().forEach { (a, b) ->
                val dl = Ndvi.delta(a, b)
                Text(
                    "${Dates.fmt(a.index.date)} → ${Dates.fmt(b.index.date)}: " + (dl?.let { (if (it >= 0) "+" else "") + fmtNum(it, 2) } ?: "không so sánh (có dữ liệu chưa hiệu chỉnh phản xạ)"),
                    color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text("Chỉ so sánh khi cả hai ngày là phản xạ đã hiệu chỉnh. Độ phân giải mỗi ngày có thể khác nhau.", color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun NdviLegend() {
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(-0.5 to "< 0", 0.1 to "0–0,2", 0.3 to "0,2–0,4", 0.5 to "0,4–0,6", 0.8 to "> 0,6").forEach { (v, t) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(12.dp).background(Color(NdviIO.colorOf(v)))); Text(t, color = VC.text2, fontSize = 13.sp)
            }
        }
        Text("Không tô màu = thiếu dữ liệu", color = VC.muted, fontSize = 13.sp)
    }
}

@Composable
fun SeriesChart(points: List<Pair<Long, Double>>, modifier: Modifier) {
    Canvas(modifier) {
        val lo = minOf(points.minOf { it.second }, 0.0) - 0.05; val hi = maxOf(points.maxOf { it.second }, 0.0) + 0.05
        val t0 = points.first().first; val t1 = points.last().first.coerceAtLeast(t0 + 1)
        fun pt(p: Pair<Long, Double>) = Offset(16f + (size.width - 32f) * (p.first - t0).toFloat() / (t1 - t0), size.height - 12f - (size.height - 24f) * ((p.second - lo) / (hi - lo)).toFloat())
        drawLine(Color(0x33000000), Offset(0f, pt(t0 to 0.0).y), Offset(size.width, pt(t0 to 0.0).y), strokeWidth = 1f)
        points.zipWithNext().forEach { (a, b) -> drawLine(Color(0xFF3F7A2E), pt(a), pt(b), strokeWidth = 3f) }
        points.forEach { drawCircle(Color(0xFF3F7A2E), 6f, pt(it)); drawCircle(Color.White, 3f, pt(it)) }
    }
}
