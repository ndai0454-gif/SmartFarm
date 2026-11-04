package com.example.test_tablet.ui.vwing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.Access
import com.example.test_tablet.data.vwing.AccountException
import com.example.test_tablet.data.vwing.BoundaryWalk
import com.example.test_tablet.data.vwing.Dates
import com.example.test_tablet.data.vwing.FeatureKind
import com.example.test_tablet.data.vwing.GeoFeature
import com.example.test_tablet.data.vwing.GeoPoint
import com.example.test_tablet.data.vwing.LivePerson
import com.example.test_tablet.data.vwing.LiveShare
import com.example.test_tablet.data.vwing.SyncConfig
import com.example.test_tablet.data.vwing.SyncSettings
import com.example.test_tablet.data.vwing.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun fmtAcc(a: Double?) = a?.let { "±${"%.0f".format(it)} m" } ?: "không rõ sai số"

/** Đi bộ quanh vườn để khoanh ranh, và chia sẻ vị trí trực tiếp (do chính người đi bật, có hẹn giờ). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocationCard(c: VCtx) {
    val context = LocalContext.current
    val settings = remember { SyncSettings(context.filesDir) }
    val cfg: SyncConfig = remember(Access.role) { settings.load() }
    val scope = rememberCoroutineScope()
    val ensure = rememberEnsureLocationPermission(c)
    var walk by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var minutes by remember { mutableStateOf(60) }
    var until by remember { mutableStateOf<Long?>(null) }
    var people by remember { mutableStateOf<List<LivePerson>>(emptyList()) }
    var err by remember { mutableStateOf<String?>(null) }
    var lastSent by remember { mutableStateOf<Long?>(null) }
    var lastAcc by remember { mutableStateOf<Float?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val sharing = until?.let { it > now } == true
    val canShare = cfg.configured && cfg.role != "admin"

    fun <T> net(work: () -> T, done: (T) -> Unit = {}) = scope.launch {
        val r = runCatching { withContext(Dispatchers.IO) { work() } }
        r.onSuccess { err = null; done(it) }.onFailure { err = (it as? AccountException)?.message ?: "Lỗi: ${it.message}" }
    }

    // đồng hồ + làm mới danh sách người đang chia sẻ (chỉ chủ/quản lý thấy người khác)
    LaunchedEffect(canShare, sharing) {
        while (canShare) {
            now = System.currentTimeMillis()
            if (sharing || Access.canManageMembers) runCatching { withContext(Dispatchers.IO) { LiveShare.get(cfg) } }.onSuccess { people = it.people; if (it.sharingUntil == null) until = null }
            delay(5_000)
        }
    }
    LocationUpdates(active = sharing, gpsOnly = false, intervalMs = 5_000) { loc ->
        lastAcc = if (loc.hasAccuracy()) loc.accuracy else null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { LiveShare.post(cfg, loc.latitude, loc.longitude, lastAcc) } }
                .onSuccess { lastSent = System.currentTimeMillis(); err = null }
                .onFailure { err = (it as? AccountException)?.message ?: "Không gửi được vị trí: ${it.message}"; if ((it as? AccountException)?.code == 409) until = null }
        }
    }

    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("VỊ TRÍ & RANH VƯỜN")
        Text(
            "Vị trí chỉ được gửi khi chính người cầm máy bấm bật, có hẹn giờ và tắt được bất cứ lúc nào. Không thể lấy vị trí của một người chỉ từ số điện thoại.",
            color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp),
        )
        SectionLabel("Chọn vùng vườn trên bản đồ")
        Text("Bản đồ vệ tinh mở đúng chỗ bạn đang đứng. Chạm lên bản đồ để đánh dấu các góc vườn, hoặc đứng ở góc vườn và bấm \"Thêm đỉnh tại đây\".", color = VC.text2, fontSize = 14.sp)
        VButton("Chọn vùng vườn trên bản đồ", { picker = true }, modifier = Modifier.padding(top = 8.dp), enabled = Access.canEditGarden)
        SectionLabel("Hoặc khoanh ranh bằng cách đi bộ")
        Text("Cầm máy đi chậm dọc bờ vườn một vòng rồi quay lại điểm xuất phát. App bỏ điểm GPS kém, nối thành đa giác và tính diện tích kèm sai số.", color = VC.text2, fontSize = 14.sp)
        VButton("Bắt đầu đi bộ ghi ranh", { ensure { walk = true } }, modifier = Modifier.padding(top = 8.dp), enabled = Access.canEditGarden)
        if (!Access.canEditGarden) Text("Vai trò ${Access.label(Access.role)} không được đổi ranh vườn.", color = VC.amber, fontSize = 13.sp)

        if (canShare) {
            SectionLabel("Chia sẻ vị trí trực tiếp")
            Text("Chủ vườn và quản lý của vườn ${cfg.gardenKey} sẽ thấy vị trí của bạn trong thời gian đã chọn. App cần mở và màn hình sáng để gửi đều.", color = VC.text2, fontSize = 14.sp)
            if (!sharing) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipRow(listOf(15, 60, 480), minutes, { if (it < 60) "$it phút" else "${it / 60} giờ" }, { minutes = it })
                    VButton("Bật chia sẻ vị trí", {
                        ensure { net({ LiveShare.start(cfg, minutes) }) { until = it; c.toast("Đang chia sẻ vị trí.") } }
                    })
                }
            } else {
                Text("Đang chia sẻ đến ${Dates.fmtTime(until!!)}" + (lastSent?.let { " · gửi lần cuối ${Dates.fmtTime(it)} (${fmtAcc(lastAcc?.toDouble())})" } ?: " · đang chờ tín hiệu GPS…"),
                    color = VC.mint, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                VButton("Tắt chia sẻ ngay", { net({ LiveShare.stop(cfg) }) { until = null; people = emptyList(); c.toast("Đã tắt chia sẻ.") } }, primary = false, danger = true, modifier = Modifier.padding(top = 6.dp))
            }
            if (Access.canManageMembers) {
                SectionLabel("Đang chia sẻ với bạn")
                if (people.isEmpty()) Text("Chưa ai đang chia sẻ vị trí.", color = VC.muted, fontSize = 14.sp)
                people.forEach { p ->
                    val ageS = ((now - p.at) / 1000).coerceAtLeast(0)
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text("${p.name} · ${Access.label(p.role)}", color = VC.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("%.6f, %.6f · %s · cập nhật %d giây trước · đến %s".format(p.lat, p.lng, fmtAcc(p.accM), ageS, Dates.fmtTime(p.until)), color = VC.text2, fontSize = 13.sp)
                    }
                }
            }
        } else if (!cfg.configured) {
            Text("Đăng nhập và chọn vườn ở mục Tài khoản để chia sẻ vị trí với chủ vườn.", color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        err?.let { Text(it, color = VC.red, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp)) }
    }
    if (walk) WalkBoundaryDialog(c) { walk = false }
    if (picker) BoundaryPickerDialog(c, { picker = false })
}

/** Ghi các điểm GPS khi đi dọc bờ vườn, xem hình, rồi lưu thành ranh vườn. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WalkBoundaryDialog(c: VCtx, onDismiss: () -> Unit) {
    val all = remember { mutableStateListOf<BoundaryWalk.Fix>() }
    var recording by remember { mutableStateOf(true) }
    var keptCount by remember { mutableStateOf(0) }
    var lastKept by remember { mutableStateOf<GeoPoint?>(null) }
    var lastAcc by remember { mutableStateOf<Float?>(null) }
    var result by remember { mutableStateOf<BoundaryWalk.Result?>(null) }
    val view = LocalView.current
    DisposableEffect(recording) { view.keepScreenOn = recording; onDispose { view.keepScreenOn = false } }

    LocationUpdates(active = recording, gpsOnly = true, intervalMs = 1_000) { loc ->
        val fix = BoundaryWalk.Fix(GeoPoint(loc.latitude, loc.longitude), if (loc.hasAccuracy()) loc.accuracy else null, System.currentTimeMillis())
        lastAcc = fix.accM
        if (all.size < 5000) all += fix
        if (BoundaryWalk.accept(lastKept, fix)) { keptCount++; lastKept = fix.p }
    }

    val res = result
    AlertDialog(
        onDismissRequest = {}, containerColor = VC.card,
        title = { Text(if (res == null) "Đang ghi ranh vườn" else "Kết quả khoanh ranh", color = VC.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (res == null) {
                    Text("Đi chậm dọc bờ vườn, giữ máy thoáng. Khi đã đi hết một vòng về gần điểm xuất phát, bấm \"Dừng và xem\".", color = VC.text2, fontSize = 14.sp)
                    Text("Điểm đã ghi: $keptCount · bỏ: ${all.size - keptCount}", color = VC.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    val acc = lastAcc
                    Text(
                        when {
                            acc == null -> "Đang chờ tín hiệu GPS…"
                            acc > BoundaryWalk.MAX_ACC_M -> "Sai số hiện tại ±${"%.0f".format(acc)} m — quá lớn, điểm này bị bỏ. Ra chỗ thoáng hơn."
                            else -> "Sai số GPS hiện tại ±${"%.0f".format(acc)} m"
                        }, color = if (acc != null && acc <= BoundaryWalk.MAX_ACC_M) VC.mint else VC.amber, fontSize = 14.sp,
                    )
                    lastKept?.let { Preview(all.filter { f -> BoundaryWalk.accept(null, f) }.map { it.p }, closed = false) }
                } else {
                    if (res.error != null) Text(res.error, color = VC.red, fontSize = 15.sp)
                    else {
                        Preview(res.polygon, closed = true)
                        Text("Diện tích khoảng ${"%.2f".format(res.areaHa)} ha" + (res.areaErrHa?.let { " (± ${"%.2f".format(it)} ha)" } ?: ""), color = VC.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("${res.polygon.size} đỉnh từ ${res.kept} điểm GPS · sai số trung bình ${res.meanAccM?.let { "%.1f".format(it) } ?: "?"} m · hụt khép vòng ${res.closeGapM.toInt()} m", color = VC.text2, fontSize = 14.sp)
                        Text("Đây là số đo bằng GPS điện thoại, không phải đo đạc trắc địa. Muốn sát hơn cần đầu thu RTK hoặc đối chiếu bản đồ vệ tinh.", color = VC.muted, fontSize = 13.sp)
                    }
                    res.warnings.forEach { Text("• $it", color = VC.amber, fontSize = 14.sp) }
                }
            }
        },
        confirmButton = {
            if (res == null) VButton("Dừng và xem", { recording = false; result = BoundaryWalk.build(all.toList()) }, enabled = keptCount >= 1)
            else if (res.ok) VButton("Lưu làm ranh vườn", {
                val f = GeoFeature(newId(), FeatureKind.BOUNDARY, "Ranh vườn", res.polygon, note = "Sai số GPS trung bình ${res.meanAccM?.let { "%.1f".format(it) } ?: "?"} m",
                    source = "Đi bộ GPS ${Dates.fmt(System.currentTimeMillis())}")
                c.store.applyGeoImport(listOf(f), emptyList(), "đi bộ GPS")
                c.toast("Đã lưu ranh vườn ${"%.2f".format(res.areaHa)} ha (thay ranh cũ nếu có)."); onDismiss()
            }) else VButton("Ghi lại", { all.clear(); keptCount = 0; lastKept = null; result = null; recording = true })
        },
        dismissButton = {
            Row {
                if (res != null && res.ok) TextButton({ all.clear(); keptCount = 0; lastKept = null; result = null; recording = true }) { Text("Ghi lại", color = VC.text2) }
                TextButton(onDismiss) { Text("Huỷ", color = VC.text2) }
            }
        },
    )
}

@Composable
private fun Preview(pts: List<GeoPoint>, closed: Boolean) {
    if (pts.size < 2) return
    val line = VC.lime; val fill = VC.lime.copy(alpha = .15f)
    Canvas(Modifier.fillMaxWidth().height(180.dp).background(VC.elevated)) {
        val o = pts[0]
        val m = pts.map { com.example.test_tablet.data.vwing.Geo.toMeters(it, o) }
        val minX = m.minOf { it.first }; val maxX = m.maxOf { it.first }; val minY = m.minOf { it.second }; val maxY = m.maxOf { it.second }
        val w = (maxX - minX).coerceAtLeast(1.0); val h = (maxY - minY).coerceAtLeast(1.0)
        val s = minOf((size.width - 24f) / w, (size.height - 24f) / h).toFloat()
        fun at(p: Pair<Double, Double>) = Offset(12f + ((p.first - minX) * s).toFloat(), size.height - 12f - ((p.second - minY) * s).toFloat())
        val path = Path().apply { m.forEachIndexed { i, p -> val q = at(p); if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) }; if (closed) close() }
        if (closed) drawPath(path, fill)
        drawPath(path, line, style = Stroke(width = 3f))
        drawCircle(line, 6f, at(m.last()))
    }
}
