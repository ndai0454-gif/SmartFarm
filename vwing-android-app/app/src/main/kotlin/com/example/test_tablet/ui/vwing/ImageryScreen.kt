package com.example.test_tablet.ui.vwing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.Dates
import com.example.test_tablet.data.vwing.DetStatus
import com.example.test_tablet.data.vwing.Detection
import com.example.test_tablet.data.vwing.Event
import com.example.test_tablet.data.vwing.EventType
import com.example.test_tablet.data.vwing.EvidenceSource
import com.example.test_tablet.data.vwing.FeatureKind
import com.example.test_tablet.data.vwing.GeoBounds
import com.example.test_tablet.data.vwing.GeoPoint
import com.example.test_tablet.data.vwing.ImageKind
import com.example.test_tablet.data.vwing.ImageSet
import com.example.test_tablet.data.vwing.Imagery
import com.example.test_tablet.data.vwing.PosSource
import com.example.test_tablet.data.vwing.SensorKind
import com.example.test_tablet.data.vwing.activeTrees
import com.example.test_tablet.data.vwing.effectiveCondition
import com.example.test_tablet.data.vwing.newId
import com.example.test_tablet.data.vwing.treeById
import com.example.test_tablet.data.vwing.treeByCode
import com.example.test_tablet.data.vwing.zoneNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val TABS = listOf(
    "images" to "Ảnh theo thời gian",
    "ndvi" to "Chỉ số thực vật (NDVI)",
    "drone_ms" to "Camera Đa phổ Drone & Phun VRA",
)

private fun setLabel(s: ImageSet) = "${s.name} · ${Dates.fmt(s.capturedAt)}"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageryScreen(c: VCtx, tabArg: String) {
    var tab by rememberSaveable(tabArg) {
        mutableStateOf(
            when (tabArg) {
                "ndvi" -> "ndvi"
                "drone_ms" -> "drone_ms"
                else -> "images"
            }
        )
    }
    ChipRow(TABS.map { it.first }, tab, { k -> TABS.first { it.first == k }.second }, { tab = it }, Modifier.padding(bottom = 12.dp))
    when (tab) {
        "ndvi" -> NdviTab(c)
        "drone_ms" -> DroneFlightScreen(c)
        else -> ImagesTab(c)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImagesTab(c: VCtx) {
    val d = c.d
    var showAdd by remember { mutableStateOf(false) }
    var showCompare by remember { mutableStateOf(false) }
    PageHeader(
        Icons.Filled.Layers, "ẢNH THEO THỜI GIAN", "Ảnh thực địa và ảnh drone",
        "Nhập ảnh kèm ngày chụp, nguồn, cảm biến và khung địa lý. Xem hai thời điểm, ghi quan sát và tạo việc kiểm tra. App không tự chẩn đoán bệnh từ ảnh.",
    ) {
        VButton("Nhập ảnh", { showAdd = true })
        VButton("So sánh hai ngày", { showCompare = true }, primary = false, enabled = d.imagery.size >= 2)
    }
    val sorted = d.imagery.sortedByDescending { it.capturedAt }
    VCard(Modifier.fillMaxWidth()) {
        if (sorted.isEmpty()) EmptyNote("Chưa có ảnh nào. Bấm \"Nhập ảnh\" để thêm ảnh thực địa hoặc ảnh trực giao từ drone.")
        sorted.forEach { s ->
            val pending = s.detections.count { it.status == DetStatus.PENDING }
            Row(Modifier.fillMaxWidth().clickable { c.nav.push("imageset|${s.id}") }.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PhotoThumb(File(c.store.photoDir, s.file).absolutePath, size = 64.dp)
                Column(Modifier.weight(1f)) {
                    Text(s.name, color = VC.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("${Dates.fmt(s.capturedAt)} · ${s.kind.label} · ${s.source}", color = VC.text2, fontSize = 14.sp)
                    FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Tag(s.sensor.label, VC.cyan)
                        if (s.georeferenced) Tag("Đã định vị", VC.mint) else Tag("Chưa định vị", VC.amber)
                        Imagery.pixelSizeM(s)?.let { Tag("≈ ${fmtNum(it * 100, 0)} cm/pixel", VC.text2) }
                        if (pending > 0) Tag("$pending điểm chờ xác nhận", VC.amber)
                        SyncTag(s.sync)
                    }
                }
            }
            Divider2()
        }
    }
    if (showAdd) AddImageDialog(c) { showAdd = false }
    if (showCompare) CompareDialog(c) { showCompare = false }
}

@Composable
private fun CompareDialog(c: VCtx, onDismiss: () -> Unit) {
    val sets = c.d.imagery.sortedBy { it.capturedAt }
    var a by remember { mutableStateOf(sets.first().id) }
    var b by remember { mutableStateOf(sets.last().id) }
    var split by remember { mutableFloatStateOf(.5f) }
    val sa = sets.firstOrNull { it.id == a }; val sb = sets.firstOrNull { it.id == b }
    val ba = sa?.let { rememberBitmap(File(c.store.photoDir, it.file).absolutePath, 2048) }
    val bb = sb?.let { rememberBitmap(File(c.store.photoDir, it.file).absolutePath, 2048) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = VC.card, modifier = Modifier.fillMaxWidth(.96f),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("So sánh hai thời điểm", color = VC.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VPicker("Ảnh 1 (bên trái)", sets.map(::setLabel), sa?.let(::setLabel) ?: "", { l -> a = sets.first { setLabel(it) == l }.id }, Modifier.weight(1f))
                    VPicker("Ảnh 2 (bên phải)", sets.map(::setLabel), sb?.let(::setLabel) ?: "", { l -> b = sets.first { setLabel(it) == l }.id }, Modifier.weight(1f))
                }
                if (sa != null && sb != null) {
                    if (sa.id == sb.id) Notice("Chọn hai ảnh khác nhau.", VC.amber)
                    else if (Imagery.comparable(sa, sb)) Notice("Hai ảnh đã định vị và chồng khung nhau: được đặt đúng vị trí địa lý.", VC.teal)
                    else Notice("Ít nhất một ảnh chưa định vị hoặc hai khung không chồng nhau: ảnh chỉ được căng vừa khung để so sánh bằng mắt, KHÔNG đảm bảo trùng vị trí.", VC.amber)
                }
                if (ba != null && bb != null && sa != null && sb != null) {
                    ImageViewer(ViewerImage(ba, sa.bounds, Dates.fmt(sa.capturedAt)), Modifier.fillMaxWidth().height(420.dp), secondary = ViewerImage(bb, sb.bounds, Dates.fmt(sb.capturedAt)), split = split)
                    Slider(split, { split = it }, valueRange = 0f..1f)
                    Text("Kéo thanh trượt để chia khung. Chụm hai ngón để phóng to.", color = VC.muted, fontSize = 13.sp)
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Đóng", color = VC.text2) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddImageDialog(c: VCtx, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<Triple<String, Int, Int>?>(null) }
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(ImageKind.FIELD) }
    var date by remember { mutableStateOf(Dates.fmt(Dates.now())) }
    var source by remember { mutableStateOf("") }
    var sensor by remember { mutableStateOf(SensorKind.RGB) }
    var model by remember { mutableStateOf("") }
    var gsd by remember { mutableStateOf("") }
    var quality by remember { mutableStateOf("") }
    var geo by remember { mutableStateOf(false) }
    var south by remember { mutableStateOf("") }; var west by remember { mutableStateOf("") }
    var north by remember { mutableStateOf("") }; var east by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val r = withContext(Dispatchers.IO) { c.store.importImageFile(uri) }
            if (r == null) c.toast("Không đọc được ảnh.") else { picked?.let { File(c.store.photoDir, it.first).delete() }; picked = r }
        }
    }
    fun boundaryBox() {
        val pts = c.d.features.firstOrNull { it.kind == FeatureKind.BOUNDARY }?.points
        if (pts.isNullOrEmpty()) { c.toast("Vườn chưa có ranh để lấy khung."); return }
        south = "%.6f".format(pts.minOf { it.lat }); north = "%.6f".format(pts.maxOf { it.lat }); west = "%.6f".format(pts.minOf { it.lng }); east = "%.6f".format(pts.maxOf { it.lng }); geo = true
    }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = VC.card,
        title = { Text("Nhập ảnh", color = VC.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VButton(if (picked == null) "Chọn tệp ảnh…" else "Đổi ảnh", { pick.launch(arrayOf("image/*")) }, primary = picked == null)
                    picked?.let { Text("${it.second}×${it.third} px", color = VC.mint, fontSize = 14.sp) }
                }
                VField(name, { name = it; err = null }, "Tên bộ ảnh *", placeholder = "Bay drone 05/10")
                VPicker("Loại ảnh", ImageKind.values().map { it.label }, kind.label, { l -> kind = ImageKind.values().first { it.label == l }
                    if (kind == ImageKind.BAND_RED || kind == ImageKind.BAND_NIR) sensor = SensorKind.MULTISPECTRAL })
                VField(date, { date = it; err = null }, "Ngày chụp (dd/MM/yyyy) *")
                VField(source, { source = it; err = null }, "Nguồn ảnh *", placeholder = "Đội bay / đối tác / người chụp")
                VPicker("Cảm biến", SensorKind.values().map { it.label }, sensor.label, { l -> sensor = SensorKind.values().first { it.label == l } })
                VField(model, { model = it }, "Thiết bị / model cảm biến (nếu biết)")
                VField(gsd, { gsd = it; err = null }, "Kích thước điểm ảnh, cm/pixel (nếu biết)", number = true)
                VField(quality, { quality = it }, "Chất lượng / ghi chú (che phủ mây, mờ, lệch…)")
                ChipRow(listOf(false, true), geo, { if (it) "Có khung địa lý" else "Chưa định vị" }, { geo = it; err = null })
                if (geo) {
                    Text("Ảnh hướng bắc, trục thẳng (đã chiếu trực giao). Nhập tọa độ độ thập phân.", color = VC.muted, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { VField(south, { south = it; err = null }, "Vĩ độ Nam", Modifier.weight(1f), number = true); VField(north, { north = it; err = null }, "Vĩ độ Bắc", Modifier.weight(1f), number = true) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { VField(west, { west = it; err = null }, "Kinh độ Tây", Modifier.weight(1f), number = true); VField(east, { east = it; err = null }, "Kinh độ Đông", Modifier.weight(1f), number = true) }
                    VButton("Lấy khung từ ranh vườn", ::boundaryBox, primary = false)
                }
                err?.let { Text(it, color = VC.red, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            VButton("Lưu ảnh", {
                val p = picked; val dt = Dates.parse(date)
                val bounds = if (!geo) null else {
                    val s = parseNum(south); val n = parseNum(north); val w = parseNum(west); val e = parseNum(east)
                    if (s == null || n == null || w == null || e == null) { err = "Nhập đủ 4 tọa độ của khung."; return@VButton }
                    GeoBounds(s, w, n, e)
                }
                val g = if (gsd.isBlank()) null else parseNum(gsd)
                when {
                    p == null -> err = "Chọn tệp ảnh."
                    dt == null -> err = "Ngày chụp không hợp lệ."
                    gsd.isNotBlank() && g == null -> err = "Kích thước điểm ảnh không hợp lệ."
                    else -> {
                        val set = ImageSet(newId(), name.trim(), kind, dt + 12 * 3_600_000L, source.trim(), sensor, model.trim(), p.first, p.second, p.third, bounds, g, quality.trim())
                        val r = c.store.addImageSet(set)
                        if (r.ok) { c.toast("Đã nhập ảnh."); onDismiss() } else err = r.message
                    }
                }
            })
        },
        dismissButton = { TextButton({ picked?.let { File(c.store.photoDir, it.first).delete() }; onDismiss() }) { Text("Hủy", color = VC.text2) } },
    )
}

// ---------- chi tiết bộ ảnh ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageSetScreen(c: VCtx, id: String) {
    val d = c.d
    val s = d.imagery.firstOrNull { it.id == id }
    if (s == null) { Text("Không tìm thấy bộ ảnh.", color = VC.text2); VButton("← Quay lại", { c.nav.pop() }, primary = false); return }
    val bmp = rememberBitmap(File(c.store.photoDir, s.file).absolutePath, 2048)
    var tapped by remember { mutableStateOf<Triple<Double, Double, GeoPoint?>?>(null) }
    var selDet by remember { mutableStateOf<String?>(null) }
    var showImport by remember { mutableStateOf(false) }
    var newTreeFor by remember { mutableStateOf<Detection?>(null) }
    val offset = remember(d, s) { Imagery.systematicOffsetM(d, s) }

    Row(Modifier.padding(bottom = 10.dp)) { VButton("← Quay lại", { c.nav.pop() }, primary = false) }
    PageHeader(Icons.Filled.Layers, "BỘ ẢNH", s.name, "${s.kind.label} · chụp ${Dates.fmt(s.capturedAt)} · nguồn: ${s.source}") { SyncTag(s.sync) }

    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("THÔNG TIN")
        Spacer(Modifier.height(8.dp))
        val rows = listOfNotNull(
            "Cảm biến" to (s.sensor.label + if (s.sensorModel.isNotBlank()) " · ${s.sensorModel}" else ""),
            "Kích thước ảnh" to "${s.widthPx}×${s.heightPx} px",
            "Điểm ảnh ngoài thực địa" to (Imagery.pixelSizeM(s)?.let { "≈ ${fmtNum(it * 100, 1)} cm/pixel" + if (s.gsdCm != null) " (khai báo)" else " (suy ra từ khung)" } ?: "Không rõ (ảnh chưa định vị)"),
            "Khung địa lý" to (s.bounds?.toString() ?: "Chưa định vị: không đặt lên bản đồ và không ghép được với cây"),
            s.quality.takeIf { it.isNotBlank() }?.let { "Chất lượng" to it },
            "Phiên bản" to "v${s.version} · ${s.audit.size} lần ghi/sửa",
        )
        rows.forEach { (k, v) -> Row(Modifier.padding(vertical = 3.dp)) { Text(k, color = VC.muted, fontSize = 14.sp, modifier = Modifier.weight(.38f)); Text(v, color = VC.text, fontSize = 14.sp, modifier = Modifier.weight(.62f)) } }
        if (s.sensor == SensorKind.RGB) Text("Ảnh RGB không chứa băng cận hồng ngoại nên không thể dùng để tính NDVI.", color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }
    Spacer(Modifier.height(14.dp))

    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("XEM ẢNH")
        Text(
            if (s.georeferenced) "Chạm vào ảnh để ghi quan sát/tạo việc kiểm tra tại điểm đó. Chấm trắng viền màu: cây theo hồ sơ; ô vuông: điểm ứng viên."
            else "Ảnh chưa định vị: chạm để ghi quan sát và chọn cây thủ công.",
            color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp),
        )
        if (bmp != null) {
            ImageViewer(
                ViewerImage(bmp, s.bounds, Dates.fmt(s.capturedAt)), Modifier.fillMaxWidth().height(440.dp),
                trees = if (s.georeferenced) d.activeTrees.mapNotNull { t -> t.pos?.let { ViewerTree(t.code, it, d.effectiveCondition(t)) } } else emptyList(),
                detections = s.detections, selectedDetectionId = selDet,
                onTap = { u, v, g -> tapped = Triple(u, v, g) },
            )
        } else EmptyNote("Không đọc được ảnh.")
    }
    Spacer(Modifier.height(14.dp))

    VCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("ĐIỂM ỨNG VIÊN TỪ CÔNG CỤ ĐẾM CÂY")
                Text("Chỉ là gợi ý. Chỉ khi bạn xác nhận mới ghép với cây; app không tự tạo hay ghi đè cây.", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
            VButton("Nhập điểm (GeoJSON)", { showImport = true }, primary = false, enabled = s.georeferenced)
        }
        if (!s.georeferenced) Text("Cần ảnh đã định vị để nhập điểm ứng viên.", color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        if (s.detectionModel.isNotBlank()) Text("Mô hình: ${s.detectionModel} ${s.detectionVersion}", color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        offset?.let {
            if (it > 1.5) Notice("Các điểm ứng viên lệch trung bình ≈ ${fmtNum(it, 1)} m so với vị trí cây trong hồ sơ (cùng chiều). Ảnh hoặc hồ sơ có thể bị dịch: kiểm tra tại vườn trước khi xác nhận hàng loạt.", VC.amber, Modifier.padding(top = 8.dp))
        }
        val counts = DetStatus.values().associateWith { st -> s.detections.count { it.status == st } }
        if (s.detections.isNotEmpty()) Text(counts.entries.filter { it.value > 0 }.joinToString(" · ") { "${it.key.label}: ${it.value}" }, color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        val pending = s.detections.filter { it.status == DetStatus.PENDING }
        pending.take(40).forEachIndexed { i, det ->
            Divider2()
            val m = Imagery.suggest(d, det)
            Column(Modifier.fillMaxWidth().clickable { selDet = det.id }.padding(vertical = 8.dp)) {
                Text("Điểm ${i + 1} · ${"%.6f, %.6f".format(det.point.lat, det.point.lng)}", color = VC.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    when (m) {
                        is Imagery.Match.Unique -> VButton("Ghép với ${m.tree.code} (${fmtNum(m.distM, 1)} m)", { val r = c.store.resolveDetection(s.id, det.id, DetStatus.CONFIRMED, m.tree.id); if (!r.ok) c.toast(r.message ?: "") })
                        is Imagery.Match.Ambiguous -> m.options.take(3).forEach { (t, dist) -> VButton("${t.code} (${fmtNum(dist, 1)} m)", { val r = c.store.resolveDetection(s.id, det.id, DetStatus.CONFIRMED, t.id); if (!r.ok) c.toast(r.message ?: "") }, primary = false) }
                        Imagery.Match.None -> VButton("Thêm thành cây mới…", { newTreeFor = det }, primary = false)
                    }
                    VButton("Bỏ qua", { c.store.resolveDetection(s.id, det.id, DetStatus.REJECTED, null) }, primary = false)
                }
                if (m is Imagery.Match.Ambiguous) Text("Có nhiều cây ở khoảng cách gần nhau: chọn đúng cây.", color = VC.amber, fontSize = 13.sp)
            }
        }
        if (pending.size > 40) Text("… còn ${pending.size - 40} điểm khác.", color = VC.muted, fontSize = 13.sp)
    }

    tapped?.let { t -> TapDialog(c, s, t.third) { tapped = null } }
    if (showImport) ImportDetectionsDialog(c, s) { showImport = false }
    newTreeFor?.let { det -> NewTreeFromDetectionDialog(c, s, det) { newTreeFor = null } }
}

@Composable
private fun TapDialog(c: VCtx, s: ImageSet, geo: GeoPoint?, onDismiss: () -> Unit) {
    val d = c.d
    val near = geo?.let { Imagery.nearestTree(d, it) }
    var code by remember { mutableStateOf(near?.first?.code ?: "") }
    var note by remember { mutableStateOf("") }
    var makeTask by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = VC.card,
        title = { Text("Ghi quan sát từ ảnh", color = VC.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    (geo?.let { "Vị trí: ${"%.6f, %.6f".format(it.lat, it.lng)}" } ?: "Ảnh chưa định vị: không có tọa độ.") +
                        (near?.let { " · cây gần nhất ${it.first.code} (${fmtNum(it.second, 1)} m)" } ?: if (geo != null) " · không có cây trong 8 m" else ""),
                    color = VC.text2, fontSize = 14.sp,
                )
                VField(code, { code = it; err = null }, "Mã cây liên quan *", placeholder = "A-04")
                VField(note, { note = it; err = null }, "Quan sát (bạn thấy gì trên ảnh) *", singleLine = false, minLines = 2)
                Row(Modifier.clickable { makeTask = !makeTask }, verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = makeTask, onCheckedChange = null); Text("  Tạo công việc kiểm tra tại vườn", color = VC.text, fontSize = 15.sp)
                }
                Text("Quan sát được ghi là \"Tài liệu\" (ảnh ${setLabel(s)}); chỉ là gợi ý cần kiểm tra thực địa, không phải kết luận bệnh.", color = VC.muted, fontSize = 13.sp)
                err?.let { Text(it, color = VC.red, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            VButton("Lưu", {
                val t = d.treeByCode(code)
                when {
                    t == null -> err = "Không tìm thấy cây \"${code.trim()}\"."
                    note.isBlank() -> err = "Nhập điều bạn quan sát được."
                    else -> {
                        val where = geo?.let { " tại ${"%.6f, %.6f".format(it.lat, it.lng)}" } ?: ""
                        val text = note.trim() + "\n[Từ ảnh ${setLabel(s)}$where]"
                        val r = c.store.addEvent(Event(newId(), listOf(t.id), null, EventType.OBSERVATION, "Quan sát từ ảnh", Dates.now(), author = d.garden.operatorName, note = text, source = EvidenceSource.DOCUMENT))
                        if (!r.ok) { err = r.message; return@VButton }
                        if (makeTask) c.store.addTask("Kiểm tra ${t.code} theo ảnh ${setLabel(s)}: ${note.trim().take(80)}", listOf(t.id), null, "", Dates.now() + 3 * 86_400_000L)
                        c.toast("Đã ghi quan sát" + if (makeTask) " và tạo việc kiểm tra." else "."); onDismiss()
                    }
                }
            })
        },
        dismissButton = { TextButton(onDismiss) { Text("Hủy", color = VC.text2) } },
    )
}

@Composable
private fun ImportDetectionsDialog(c: VCtx, s: ImageSet, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var model by remember { mutableStateOf(s.detectionModel) }
    var version by remember { mutableStateOf(s.detectionVersion) }
    var report by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("Không đọc được tệp.")
            val (pts, notes) = Imagery.parsePoints(text)
            val inFrame = pts.filter { s.bounds?.contains(it) == true }
            val out = pts.size - inFrame.size
            val added = c.store.importDetections(s.id, inFrame, model, version).getOrThrow()
            report = "Đọc ${pts.size} điểm: thêm $added mới, ${inFrame.size - added} trùng điểm đã có" + (if (out > 0) ", $out điểm nằm ngoài khung ảnh bị bỏ" else "") + "." +
                (if (notes.isNotEmpty()) " " + notes.joinToString(" ") else "")
        }.onFailure { report = "Lỗi: ${it.message}" }
    }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = VC.card,
        title = { Text("Nhập điểm ứng viên", color = VC.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Chọn tệp GeoJSON chứa các điểm (Point) do công cụ đếm cây xuất ra. Điểm gần điểm đã có (≤ 0,3 m) được bỏ qua nên nhập lại không nhân đôi.", color = VC.text2, fontSize = 14.sp)
                VField(model, { model = it }, "Tên mô hình / công cụ", placeholder = "Ví dụ: TreeCounter")
                VField(version, { version = it }, "Phiên bản")
                report?.let { Text(it, color = VC.mint, fontSize = 14.sp) }
            }
        },
        confirmButton = { VButton("Chọn tệp…", { picker.launch(arrayOf("*/*")) }) },
        dismissButton = { TextButton(onDismiss) { Text("Đóng", color = VC.text2) } },
    )
}

@Composable
private fun NewTreeFromDetectionDialog(c: VCtx, s: ImageSet, det: Detection, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var zone by remember { mutableStateOf(c.d.zoneNames().firstOrNull() ?: "") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = VC.card,
        title = { Text("Thêm thành cây mới", color = VC.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Điểm này không gần cây nào trong hồ sơ. Cây mới có vị trí ước lượng từ ảnh, chưa xác minh tại vườn.", color = VC.text2, fontSize = 14.sp)
                VField(code, { code = it; err = null }, "Mã cây mới *", placeholder = "A-25")
                VField(zone, { zone = it; err = null }, "Khu vực *")
                err?.let { Text(it, color = VC.red, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            VButton("Thêm cây", {
                val r = c.store.addTree(code, zone, pos = det.point, posSource = PosSource.ESTIMATED)
                if (!r.ok) { err = r.message; return@VButton }
                val t = c.d.treeByCode(code)!!
                c.store.resolveDetection(s.id, det.id, DetStatus.NEW_TREE, t.id)
                c.toast("Đã thêm cây ${t.code}."); onDismiss()
            })
        },
        dismissButton = { TextButton(onDismiss) { Text("Hủy", color = VC.text2) } },
    )
}
