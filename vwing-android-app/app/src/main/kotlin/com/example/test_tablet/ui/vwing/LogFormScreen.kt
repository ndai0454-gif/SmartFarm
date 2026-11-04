package com.example.test_tablet.ui.vwing

import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.Condition
import com.example.test_tablet.data.vwing.Dates
import com.example.test_tablet.data.vwing.Event
import com.example.test_tablet.data.vwing.EventType
import com.example.test_tablet.data.vwing.EvidenceSource
import com.example.test_tablet.data.vwing.IssueStatus
import com.example.test_tablet.data.vwing.Photo
import com.example.test_tablet.data.vwing.Priority
import com.example.test_tablet.data.vwing.activeTrees
import com.example.test_tablet.data.vwing.newId
import com.example.test_tablet.data.vwing.treeById
import com.example.test_tablet.data.vwing.zoneNames

private val ACTIVITIES = listOf("Kiểm tra", "Tưới nước", "Bón phân", "Phun thuốc", "Cắt tỉa", "Thu hoạch")
private val ACTIVITY_HINT = mapOf(
    "Kiểm tra" to "Quan sát nhanh: chỉ cần ghi chú/ảnh/tình trạng. Không cần nhập vật tư hay liều lượng.",
    "Tưới nước" to "Ghi lượng hoặc thời gian tưới nếu biết; vật tư là tùy chọn.",
    "Bón phân" to "Cần ghi vật tư (loại phân). Liều lượng nếu biết.",
    "Phun thuốc" to "Cần ghi vật tư (loại thuốc). Liều lượng nếu biết.",
    "Cắt tỉa" to "Ghi chú phần đã cắt tỉa. Không cần vật tư.",
    "Thu hoạch" to "Cần ghi khối lượng thu hoạch (kg).",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogFormScreen(c: VCtx, arg: String) {
    val d = c.d
    val parts = arg.split('|')
    val preTree = parts.getOrNull(0)?.ifBlank { null }
    val preIssue = parts.getOrNull(1)?.ifBlank { null }

    val draftId = rememberSaveable { newId() }   // bấm lưu hai lần không tạo hai bản ghi
    var zoneMode by rememberSaveable { mutableStateOf(false) }
    var codes by rememberSaveable { mutableStateOf(preTree?.let { d.treeById(it)?.code } ?: c.selectedTree?.let { d.treeById(it)?.code } ?: "") }
    var zone by rememberSaveable { mutableStateOf(d.zoneNames().firstOrNull() ?: "") }
    var activity by rememberSaveable { mutableStateOf("Kiểm tra") }
    var date by rememberSaveable { mutableStateOf(Dates.fmt(Dates.now())) }
    var product by rememberSaveable { mutableStateOf("") }
    var dosage by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var kg by rememberSaveable { mutableStateOf("") }
    var fruits by rememberSaveable { mutableStateOf("") }
    var author by rememberSaveable { mutableStateOf(d.garden.operatorName) }
    var condLabel by rememberSaveable { mutableStateOf("Không đổi") }
    var createIssue by rememberSaveable { mutableStateOf(false) }
    var issueId by rememberSaveable { mutableStateOf(preIssue ?: "") }
    var source by rememberSaveable { mutableStateOf(EvidenceSource.OWNER_NOTE.name) }
    val photos = remember { mutableStateListOf<Photo>() }
    var err by remember { mutableStateOf<String?>(null) }
    val pick = rememberPhotoPicker(c) { photos.addAll(it) }
    val shoot = rememberCameraCapture(c) { photos.addAll(it) }
    val speak = rememberVoiceInput(c) { text -> note = if (note.isBlank()) text else note.trimEnd() + " " + text; err = null }
    var scanning by remember { mutableStateOf(false) }
    if (scanning) QrScannerDialog({ scanning = false }) { raw ->
        scanning = false
        val r = resolveScan(d, raw)
        if (r is com.example.test_tablet.data.vwing.Qr.Scan.Found) {
            if (!r.tree.active) c.toast("Cây ${r.tree.code} không còn được quản lý.")
            else {
                zoneMode = false; err = null
                val cur = codes.split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }
                if (cur.none { it.equals(r.tree.code, true) }) codes = (cur + r.tree.code).joinToString(", ")
                c.toast("Đã thêm cây ${r.tree.code}.")
            }
        } else c.toast(scanMessage(r) ?: "")
    }

    val (foundTrees, missing) = if (zoneMode) emptyList<com.example.test_tablet.data.vwing.Tree>() to emptyList() else parseCodes(d, codes)
    val zoneTrees = d.activeTrees.filter { it.zone == zone }
    val targetTrees = if (zoneMode) zoneTrees else foundTrees
    val cond = Condition.values().firstOrNull { it.label == condLabel }

    PageHeader(Icons.Filled.EditNote, "NHẬT KÝ CANH TÁC", "Ghi lại một lần chăm sóc", "Lưu thời gian, đối tượng, bằng chứng và (khi cần) vật tư để theo dõi cây theo thời gian. Dữ liệu lưu ngay trên thiết bị.")

    VCard(Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Eyebrow("ÁP DỤNG CHO")
            ChipRow(listOf(false, true), zoneMode, { if (it) "Cả khu vực" else "Từng cây" }, { zoneMode = it; err = null }, icon = { if (it) Icons.Filled.GridView else Icons.Filled.Park })
            if (zoneMode) {
                VPicker("Khu vực", d.zoneNames(), zone, { zone = it })
                Text("${zoneTrees.size} cây hiện có trong khu sẽ được ghi nhận tại thời điểm này. Cây thêm vào khu sau này sẽ không tự có bản ghi này.", color = VC.muted, fontSize = 13.sp)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    VField(codes, { codes = it; err = null }, "Mã cây (nhiều cây cách nhau bằng dấu phẩy) *", Modifier.weight(1f), placeholder = "A-04, A-05")
                    VButton("Quét QR", { scanning = true }, primary = false)
                }
                if (missing.isNotEmpty()) Text("Chưa có hồ sơ cho: ${missing.joinToString(", ")}. Thêm cây ở màn Bản đồ vườn hoặc tạo qua \"Ghi nhận vấn đề\".", color = VC.amber, fontSize = 14.sp)
                else if (foundTrees.isNotEmpty()) Text("${foundTrees.size} cây: " + foundTrees.joinToString(", ") { it.code } + if (foundTrees.size > 1) " · một bản ghi duy nhất cho các cây này." else "", color = VC.mint, fontSize = 14.sp)
            }

            Eyebrow("HOẠT ĐỘNG")
            ChipRow(ACTIVITIES, activity, { it }, { activity = it; err = null }, icon = { a ->
                when (a) {
                    "Kiểm tra" -> Icons.Filled.Visibility
                    "Tưới nước" -> Icons.Filled.WaterDrop
                    "Bón phân" -> Icons.Filled.Grass
                    "Phun thuốc" -> Icons.Filled.Science
                    "Cắt tỉa" -> Icons.Filled.ContentCut
                    "Thu hoạch" -> Icons.Filled.Agriculture
                    else -> null
                }
            })
            Text(ACTIVITY_HINT.getValue(activity), color = VC.text2, fontSize = 14.sp)

            VField(date, { date = it; err = null }, "Ngày thực hiện (dd/MM/yyyy) *")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChipRow(listOf("Hôm nay", "Hôm qua"), "", { it }, { date = Dates.fmt(if (it == "Hôm nay") Dates.now() else Dates.daysAgo(1)) })
            }

            when (activity) {
                "Kiểm tra" -> {
                    VPicker("Kết quả quan sát (tình trạng cây)", listOf("Không đổi") + Condition.values().filter { it != Condition.UNASSESSED }.map { it.label }, condLabel, { condLabel = it })
                    if (targetTrees.size == 1) VField(fruits, { fruits = it.filter(Char::isDigit) }, "Số trái đếm được (không bắt buộc)")
                }
                "Tưới nước" -> { VField(dosage, { dosage = it }, "Lượng tưới / thời gian (nếu biết)"); VField(product, { product = it }, "Nguồn nước (tùy chọn)") }
                "Bón phân", "Phun thuốc" -> { VField(product, { product = it; err = null }, "Sản phẩm / vật tư *"); VField(dosage, { dosage = it }, "Liều lượng (nếu biết)") }
                "Thu hoạch" -> VField(kg, { kg = it; err = null }, "Khối lượng thu hoạch (kg) *", number = true)
            }

            VField(note, { note = it; err = null }, "Ghi chú", singleLine = false, minLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VButton("Nói để ghi chú", speak, primary = false)
                Text("Văn bản nhận dạng được thêm vào ô ghi chú; bạn xem và sửa trước khi lưu.", color = VC.muted, fontSize = 13.sp)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VButton("Thêm ảnh", pick, primary = false, icon = Icons.Filled.Add)
                VButton("Chụp ảnh", shoot, primary = false)
                Text("${photos.size} ảnh", color = VC.text2, fontSize = 14.sp)
            }
            if (photos.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { photos.take(5).forEach { PhotoThumb(it.path, size = 64.dp) } }

            VPicker("Nguồn bằng chứng", EvidenceSource.values().map { it.label }, EvidenceSource.valueOf(source).label, { l -> source = EvidenceSource.values().first { it.label == l }.name })

            if (!zoneMode && targetTrees.size == 1) {
                val open = d.issues.filter { it.status.isOpen && targetTrees[0].id in it.treeIds }
                if (open.isNotEmpty()) {
                    val labels = listOf("Không gắn") + open.map { "${it.code} · ${it.symptom.take(40)}" }
                    VPicker("Gắn với vấn đề đang mở", labels, labels.getOrNull(open.indexOfFirst { it.id == issueId } + 1) ?: labels[0],
                        { l -> issueId = if (l == labels[0]) "" else open[labels.indexOf(l) - 1].id })
                }
            }
            if (activity == "Kiểm tra" && (cond == Condition.WATCH || cond == Condition.NEEDS_CHECK)) {
                Row(Modifier.clickable { createIssue = !createIssue }, verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = createIssue, onCheckedChange = null); Text("  Tạo hồ sơ vấn đề từ quan sát này", color = VC.text, fontSize = 15.sp)
                }
            }
            VField(author, { author = it }, "Người thực hiện")
            err?.let { Text(it, color = VC.red, fontSize = 14.sp) }
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VButton("Hủy", { c.nav.pop() }, primary = false)
                VButton("Lưu ghi chép", {
                    val dt = Dates.parse(date)
                    val k = if (kg.isBlank()) null else parseNum(kg)
                    err = when {
                        !zoneMode && missing.isNotEmpty() -> "Có mã cây chưa tồn tại: ${missing.joinToString(", ")}."
                        targetTrees.isEmpty() -> if (zoneMode) "Khu này chưa có cây nào." else "Nhập ít nhất một mã cây."
                        dt == null -> "Ngày không hợp lệ (dd/MM/yyyy)."
                        dt > Dates.today() -> "Ngày thực hiện không được ở tương lai."
                        (activity == "Bón phân" || activity == "Phun thuốc") && product.isBlank() -> "$activity cần ghi sản phẩm / vật tư."
                        activity == "Thu hoạch" && (k == null || k <= 0) -> "Nhập khối lượng thu hoạch lớn hơn 0 (kg)."
                        activity == "Kiểm tra" && note.isBlank() && photos.isEmpty() && cond == null -> "Ghi chú, ảnh hoặc tình trạng: cần ít nhất một thông tin quan sát."
                        createIssue && note.isBlank() -> "Nhập ghi chú mô tả biểu hiện để tạo vấn đề."
                        else -> null
                    }
                    if (err == null) {
                        val ev = Event(
                            id = draftId, treeIds = targetTrees.map { it.id }, scopeZone = if (zoneMode) zone else null,
                            type = when (activity) { "Kiểm tra" -> EventType.OBSERVATION; "Thu hoạch" -> EventType.HARVEST; else -> EventType.CARE },
                            activity = activity, occurredAt = dt!! + 12 * 3_600_000L, author = author.ifBlank { d.garden.operatorName }, note = note.trim(),
                            product = product.trim().ifEmpty { null }, dosage = dosage.trim().ifEmpty { null }, amountKg = k,
                            photos = photos.toList(), source = EvidenceSource.valueOf(source),
                            issueId = issueId.ifEmpty { null }, conditionSet = if (activity == "Kiểm tra") cond else null,
                        )
                        val r = c.store.addEvent(ev, fruits.toIntOrNull())
                        if (!r.ok) err = r.message
                        else {
                            if (createIssue) c.store.addIssue(ev.treeIds, ev.scopeZone, note, if (cond == Condition.NEEDS_CHECK) Priority.HIGH else Priority.MEDIUM,
                                author, Dates.daysAgo(-3), ev.id)
                            c.toast("Đã lưu ghi chép${if (createIssue) " và tạo vấn đề" else ""}.")
                            c.nav.pop()
                        }
                    }
                })
            }

        }
    }
}
