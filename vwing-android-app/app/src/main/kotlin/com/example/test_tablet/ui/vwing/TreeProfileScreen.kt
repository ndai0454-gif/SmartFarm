package com.example.test_tablet.ui.vwing

import androidx.compose.material.icons.filled.Park
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.CheckStatus
import com.example.test_tablet.data.vwing.Condition
import com.example.test_tablet.data.vwing.Dates
import com.example.test_tablet.data.vwing.EventType
import com.example.test_tablet.data.vwing.Photo
import com.example.test_tablet.data.vwing.checkStatus
import com.example.test_tablet.data.vwing.dataGaps
import com.example.test_tablet.data.vwing.effectiveCondition
import com.example.test_tablet.data.vwing.eventsFor
import com.example.test_tablet.data.vwing.issuesFor
import com.example.test_tablet.data.vwing.treeById
import com.example.test_tablet.data.vwing.zoneDesc
import com.example.test_tablet.data.vwing.zoneNames

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TreeProfileScreen(c: VCtx, id: String) {
    val d = c.d
    val t = d.treeById(id)
    if (t == null) {
        Text("Không tìm thấy cây.", color = VC.text2); VButton("← Quay lại", { c.nav.pop() }, primary = false); return
    }
    var typeF by rememberSaveable { mutableStateOf("Tất cả") }
    var rangeF by rememberSaveable { mutableStateOf("Tất cả thời gian") }
    var showEdit by remember { mutableStateOf(false) }
    var showReplace by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showIssue by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    val cond = d.effectiveCondition(t)
    val events = d.eventsFor(t.id)
    val today = Dates.today()

    Row(Modifier.padding(bottom = 10.dp)) { VButton("← Quay lại", { c.nav.pop() }, primary = false) }
    PageHeader(Icons.Filled.Park, "HỒ SƠ CÂY", "Cây ${t.code}", "${t.zone}${d.zoneDesc(t.zone).let { if (it.isNotBlank()) " · $it" else "" }} · ID cố định ${t.id.take(8)}") {
        ConditionTag(cond)
        if (!t.active) Tag("Không còn quản lý", VC.red)
        SyncTag(t.sync)
    }

    if (t.replacedById != null) Notice("Cây này đã được thay bằng ${d.treeById(t.replacedById)?.code ?: "cây khác"}. Lịch sử cũ được giữ lại và không chuyển sang cây mới.", VC.amber, Modifier.padding(bottom = 10.dp)) {
        TextButton({ c.nav.push("tree|${t.replacedById}") }) { Text("Xem →", color = VC.lime) }
    }
    if (t.replacesId != null) Notice("Cây thay thế cho ${d.treeById(t.replacesId)?.code ?: "cây cũ"}; không kế thừa lịch sử của cây cũ.", VC.teal, Modifier.padding(bottom = 10.dp)) {
        TextButton({ c.nav.push("tree|${t.replacesId}") }) { Text("Xem →", color = VC.lime) }
    }

    FlowRow(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VButton("Ghi hoạt động", { c.nav.push("log|${t.id}") }, icon = Icons.Filled.Add, enabled = t.active)
        VButton("Báo vấn đề", { showIssue = true }, primary = false, enabled = t.active)
        VButton("Sửa hồ sơ", { showEdit = true }, primary = false, enabled = t.active)
        VButton("Thay thế cây", { showReplace = true }, primary = false, enabled = t.active)
        VButton("Mã QR", { showQr = true }, primary = false)
        if (com.example.test_tablet.data.vwing.Access.canDelete) VButton("Xoá cây", { showDelete = true }, primary = false, danger = true)
        VButton("Xem trên bản đồ", { c.selectTree(t.id); c.nav.top("map") }, primary = false)
    }

    if (showQr) QrDialog(c, t) { showQr = false }
    if (showDelete) DeleteConfirmDialog(c, "trees", t.id, "Xoá cây ${t.code}?", { showDelete = false }) { c.selectTree(null); c.nav.pop() }

    val facts: @Composable (Modifier) -> Unit = { m ->
        VCard(m) {
            Eyebrow("THÔNG TIN")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniFact("Trái ghi nhận", t.fruits?.let { "$it trái" } ?: "Chưa đếm", Modifier.weight(1f))
                MiniFact("Tình trạng đã nhập", t.condition.label, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniFact("Giống / năm trồng", listOf(t.variety.ifBlank { null }, t.plantedYear?.toString()).filterNotNull().joinToString(" · ").ifEmpty { "Chưa ghi" }, Modifier.weight(1f))
                MiniFact("Kiểm tra", when (val s = d.checkStatus(t)) {
                    CheckStatus.Never -> "Chưa từng quan sát"; is CheckStatus.Overdue -> "Quá hạn ${s.days} ngày"; is CheckStatus.Ok -> "${s.days} ngày trước"
                }, Modifier.weight(1f))
            }
            Text("Vị trí: " + (t.pos?.let { "%.6f, %.6f · ${t.posSource.label}".format(it.lat, it.lng) } ?: "chưa có"), color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
            val gaps = d.dataGaps(t)
            Text("Chất lượng dữ liệu: " + if (gaps.isEmpty()) "đầy đủ" else gaps.joinToString(" · "), color = if (gaps.isEmpty()) VC.mint else VC.amber, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            Text("Tình trạng hiển thị = mức nặng hơn giữa đánh giá thủ công và vấn đề đang mở. Làm xong việc không tự xác nhận cây đã hồi phục.", color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
    val issues: @Composable (Modifier) -> Unit = { m ->
        VCard(m) {
            Eyebrow("VẤN ĐỀ")
            val all = d.issuesFor(t.id)
            if (all.isEmpty()) EmptyNote("Chưa ghi nhận vấn đề nào cho cây này.")
            all.forEach { i ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { c.nav.push("issue|${i.id}") }.padding(vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${i.code} · ${i.symptom}", color = VC.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("${Dates.fmt(i.createdAt)}" + (i.dueAt?.let { " · hạn ${Dates.fmt(it)}" } ?: ""), color = VC.muted, fontSize = 13.sp)
                    }
                    Tag(i.status.label, if (i.status.isOpen) VC.amber else VC.mint)
                }
                Divider2()
            }
        }
    }
    if (c.wide) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { facts(Modifier.weight(1f)); issues(Modifier.weight(1f)) }
    else Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { facts(Modifier); issues(Modifier) }

    // So sánh ảnh trước – sau
    val photos = events.flatMap { e -> e.photos.map { it to e } }.sortedBy { (p, e) -> p.takenAt ?: e.occurredAt }
    if (photos.size >= 2) {
        Spacer(Modifier.height(14.dp))
        VCard(Modifier.fillMaxWidth()) {
            Eyebrow("SO SÁNH ẢNH TRƯỚC – SAU")
            val labels = photos.map { (p, e) -> "${Dates.fmt(p.takenAt ?: e.occurredAt)} · ${e.activity}" }
            var bi by remember { mutableStateOf(0) }
            var ai by remember { mutableStateOf(photos.lastIndex) }
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        VPicker("Trước", labels, labels[bi], { bi = labels.indexOf(it) })
                        PhotoBlock(photos[bi].first, photos[bi].second.occurredAt)
                    }
                    Column(Modifier.weight(1f)) {
                        VPicker("Sau", labels, labels[ai], { ai = labels.indexOf(it) })
                        PhotoBlock(photos[ai].first, photos[ai].second.occurredAt)
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("DÒNG THỜI GIAN")
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipRow(listOf("Tất cả") + EventType.values().map { it.label }, typeF, { it }, { typeF = it })
            ChipRow(listOf("30 ngày", "90 ngày", "Tất cả thời gian"), rangeF, { it }, { rangeF = it })
        }
        val shown = events.filter { e ->
            (typeF == "Tất cả" || e.type.label == typeF) && (rangeF == "Tất cả thời gian" || Dates.daysBetween(e.occurredAt, today) <= (if (rangeF == "30 ngày") 30 else 90))
        }
        Spacer(Modifier.height(6.dp))
        if (shown.isEmpty()) EmptyNote("Không có sự kiện nào trong bộ lọc này.")
        shown.forEach { EventRow(c, it) }
        if (t.audit.isNotEmpty()) {
            SectionLabel("Lịch sử chỉnh sửa hồ sơ")
            t.audit.reversed().forEach { Text("${Dates.fmtTime(it.at)} · ${it.by}: ${it.summary}", color = VC.text2, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp)) }
        }
    }

    if (showEdit) EditTreeDialog(c, t.id) { showEdit = false }
    if (showReplace) ReplaceTreeDialog(c, t.id) { showReplace = false }
    if (showIssue) NewIssueDialog(c, listOf(t.id), null, null) { showIssue = false }
}

@Composable
private fun PhotoBlock(p: Photo, fallbackDate: Long) {
    Column(Modifier.padding(top = 6.dp)) {
        PhotoThumb(p.path, Modifier.fillMaxWidth(), size = 150.dp)
        Text("Chụp: " + (p.takenAt?.let { Dates.fmt(it) } ?: "không rõ (ghi chép ${Dates.fmt(fallbackDate)})"), color = VC.muted, fontSize = 13.sp)
        Text("Tải lên: ${Dates.fmt(p.uploadedAt)}", color = VC.muted, fontSize = 13.sp)
    }
}

@Composable
fun EditTreeDialog(c: VCtx, id: String, onDismiss: () -> Unit) {
    val d = c.d
    val t = remember { d.treeById(id)!! }   // giữ phiên bản lúc mở để phát hiện thay đổi đồng thời
    var code by remember { mutableStateOf(t.code) }
    var zone by remember { mutableStateOf(t.zone) }
    var newZone by remember { mutableStateOf("") }
    var variety by remember { mutableStateOf(t.variety) }
    var year by remember { mutableStateOf(t.plantedYear?.toString() ?: "") }
    var fruits by remember { mutableStateOf(t.fruits?.toString() ?: "") }
    var cond by remember { mutableStateOf(t.condition) }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = VC.card,
        title = { Text("Sửa hồ sơ cây", color = VC.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VField(code, { code = it }, "Mã cây")
                Text("Đổi mã không làm mất lịch sử: hồ sơ gắn với ID cố định.", color = VC.muted, fontSize = 13.sp)
                VPicker("Khu vực", d.zoneNames(), zone, { zone = it; newZone = "" })
                VField(newZone, { newZone = it }, "Hoặc khu mới")
                VField(variety, { variety = it }, "Giống")
                VField(year, { year = it.filter(Char::isDigit).take(4) }, "Năm trồng")
                VField(fruits, { fruits = it.filter(Char::isDigit) }, "Số trái (để trống = chưa đếm)")
                VPicker("Tình trạng (đánh giá thủ công)", Condition.values().map { it.label }, cond.label, { l -> cond = Condition.values().first { it.label == l } })
                err?.let { Text(it, color = VC.red, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            VButton("Lưu", {
                val y = year.toIntOrNull()
                if (year.isNotEmpty() && (y == null || y < 1900 || y > 2100)) err = "Năm trồng không hợp lệ."
                else {
                    val z = newZone.trim().ifEmpty { zone }
                    val changes = buildList {
                        if (code.trim() != t.code) add("mã ${t.code} → ${code.trim()}")
                        if (z != t.zone) add("khu ${t.zone} → $z")
                        if (fruits.toIntOrNull() != t.fruits) add("số trái")
                        if (cond != t.condition) add("tình trạng ${t.condition.label} → ${cond.label}")
                        if (variety.trim() != t.variety || y != t.plantedYear) add("giống/năm trồng")
                    }
                    val r = c.store.editTree(t.id, t.version, "Sửa: " + changes.joinToString(", ").ifEmpty { "không đổi" }) {
                        it.copy(code = code.trim(), zone = z, variety = variety.trim(), plantedYear = y, fruits = fruits.toIntOrNull(), condition = cond)
                    }
                    if (r.ok) { c.toast("Đã lưu hồ sơ cây."); onDismiss() } else err = r.message
                }
            })
        },
        dismissButton = { TextButton(onDismiss) { Text("Đóng", color = VC.text2) } },
    )
}

@Composable
fun ReplaceTreeDialog(c: VCtx, id: String, onDismiss: () -> Unit) {
    val old = c.d.treeById(id)!!
    var code by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = VC.card,
        title = { Text("Thay thế cây ${old.code}", color = VC.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cây mới nhận ID mới và liên kết với ${old.code}. Lịch sử của ${old.code} được giữ nguyên và không chuyển sang cây mới.", color = VC.text2, fontSize = 14.sp)
                VField(code, { code = it }, "Mã cây mới *")
                VField(reason, { reason = it }, "Lý do (cây chết, trồng dặm…)")
                err?.let { Text(it, color = VC.red, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            VButton("Thay thế", {
                val r = c.store.replaceTree(old.id, code, reason)
                if (r.ok) {
                    c.toast("Đã tạo cây thay thế ${code.trim()}.")
                    c.store.data.trees.firstOrNull { it.replacesId == old.id }?.let { c.selectTree(it.id); c.nav.stack[c.nav.stack.lastIndex] = "tree|${it.id}" }
                    onDismiss()
                } else err = r.message
            })
        },
        dismissButton = { TextButton(onDismiss) { Text("Đóng", color = VC.text2) } },
    )
}
