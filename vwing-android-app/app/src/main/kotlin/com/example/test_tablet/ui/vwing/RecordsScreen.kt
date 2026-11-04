package com.example.test_tablet.ui.vwing

import androidx.compose.material.icons.filled.History
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.Dates
import com.example.test_tablet.data.vwing.Event
import com.example.test_tablet.data.vwing.EventType
import com.example.test_tablet.data.vwing.GardenData
import com.example.test_tablet.data.vwing.treeById
import com.example.test_tablet.data.vwing.zoneNames

fun targetLabel(d: GardenData, e: Event): String {
    val codes = e.treeIds.mapNotNull { d.treeById(it)?.code }
    return when {
        e.scopeZone != null -> "${e.scopeZone} · ${e.treeIds.size} cây"
        codes.size > 3 -> codes.take(3).joinToString(", ") + " +${codes.size - 3}"
        else -> codes.joinToString(", ")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordsScreen(c: VCtx) {
    val d = c.d
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("Tất cả") }
    var range by rememberSaveable { mutableStateOf("Tất cả thời gian") }
    var zone by rememberSaveable { mutableStateOf("Tất cả khu") }

    PageHeader(Icons.Filled.History, "NHẬT KÝ CANH TÁC", "Mọi lần chăm sóc đều được ghi nhớ.", "Tra cứu hoạt động theo cây hoặc khu vực, từ lúc thực hiện đến vật tư và bằng chứng.") {
        VButton("Thêm ghi chép", { c.nav.push("log") }, icon = Icons.Filled.Add)
    }

    VCard(Modifier.fillMaxWidth()) {
        VField(query, { query = it }, "Tìm hoạt động, cây, sản phẩm, người thực hiện…")
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipRow(listOf("Tất cả") + EventType.values().map { it.label }, type, { it }, { type = it })
            ChipRow(listOf("30 ngày", "90 ngày", "Tất cả thời gian"), range, { it }, { range = it })
            Row(Modifier.horizontalScroll(rememberScrollState())) { ChipRow(listOf("Tất cả khu") + d.zoneNames(), zone, { it }, { zone = it }) }
        }
    }
    val q = query.trim().lowercase()
    val today = Dates.today()
    val trees = d.trees.associateBy { it.id }
    val list = d.events.sortedByDescending { it.occurredAt }.filter { e ->
        (type == "Tất cả" || e.type.label == type) &&
            (range == "Tất cả thời gian" || Dates.daysBetween(e.occurredAt, today) <= (if (range == "30 ngày") 30 else 90)) &&
            (zone == "Tất cả khu" || e.scopeZone == zone || e.treeIds.any { trees[it]?.zone == zone }) &&
            (q.isEmpty() || (e.activity + " " + e.note + " " + (e.product ?: "") + " " + e.author + " " + targetLabel(d, e) + " " +
                e.treeIds.mapNotNull { trees[it]?.code }.joinToString(" ")).lowercase().contains(q))
    }
    Text("${list.size} / ${d.events.size} ghi chép", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 10.dp))
    VCard(Modifier.fillMaxWidth()) {
        if (list.isEmpty()) EmptyNote("Không tìm thấy ghi chép phù hợp.")
        list.forEach { EventRow(c, it) }
    }
}

@Composable
fun EventRow(c: VCtx, e: Event, compact: Boolean = false) {
    val d = c.d
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(Dates.fmt(e.occurredAt), color = VC.mint, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Tag(e.activity, if (e.type == EventType.OBSERVATION || e.type == EventType.RECHECK) VC.amber else VC.mint)
            Text(targetLabel(d, e), color = VC.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
            if (e.photos.isNotEmpty()) Tag("${e.photos.size} ảnh", VC.cyan)
        }
        val detail = listOfNotNull(e.product, e.dosage, e.amountKg?.let { fmtKg(it) }, e.result).joinToString(" · ")
        if (detail.isNotEmpty()) Text(detail, color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 3.dp))
        if (e.note.isNotBlank()) Text(e.note, color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
        if (!compact) Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(e.author, color = VC.muted, fontSize = 13.sp)
            SyncTag(e.sync)
            if (e.audit.isNotEmpty()) Tag("Đã sửa ${e.audit.size} lần", VC.text2)
        }
    }
    Divider2()
    if (open) EventDialog(c, e.id) { open = false }
}

@Composable
fun EventDialog(c: VCtx, id: String, onDismiss: () -> Unit) {
    val d = c.d
    val e = d.events.firstOrNull { it.id == id } ?: run { onDismiss(); return }
    var editing by remember { mutableStateOf(false) }
    var note by remember(e.version) { mutableStateOf(e.note) }
    var product by remember(e.version) { mutableStateOf(e.product ?: "") }
    var dosage by remember(e.version) { mutableStateOf(e.dosage ?: "") }
    var date by remember(e.version) { mutableStateOf(Dates.fmt(e.occurredAt)) }
    var kg by remember(e.version) { mutableStateOf(e.amountKg?.toString() ?: "") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = VC.card,
        title = { Text("${e.activity} · ${Dates.fmt(e.occurredAt)}", color = VC.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(targetLabel(d, e), color = VC.mint, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Tag(e.type.label); Tag(e.source.label, VC.cyan); SyncTag(e.sync) }
                Text("Ghi bởi ${e.author} lúc ${Dates.fmtTime(e.recordedAt)} · phiên bản ${e.version}", color = VC.muted, fontSize = 13.sp)
                e.confirmedBy?.let { Text("Người xác nhận: $it", color = VC.text2, fontSize = 14.sp) }
                if (!editing) {
                    listOfNotNull(e.product?.let { "Vật tư: $it" }, e.dosage?.let { "Liều lượng: $it" }, e.amountKg?.let { "Khối lượng: ${fmtKg(it)}" }, e.result?.let { "Kết quả: $it" })
                        .forEach { Text(it, color = VC.text, fontSize = 15.sp) }
                    if (e.note.isNotBlank()) Text(e.note, color = VC.text2, fontSize = 15.sp)
                    if (e.photos.isNotEmpty()) {
                        SectionLabel("Ảnh (${e.photos.size})")
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            e.photos.forEach { p ->
                                Column {
                                    PhotoThumb(p.path, size = 110.dp)
                                    Text("Chụp: " + (p.takenAt?.let { Dates.fmt(it) } ?: "không rõ"), color = VC.muted, fontSize = 13.sp)
                                    Text("Tải lên: ${Dates.fmt(p.uploadedAt)}", color = VC.muted, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    e.treeIds.mapNotNull { d.treeById(it) }.take(6).let { ts ->
                        if (ts.isNotEmpty() && e.treeIds.size <= 6) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ts.forEach { t -> Text(t.code, color = VC.lime, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.heightIn(min = 48.dp).clickable { c.selectTree(t.id); c.nav.push("tree|${t.id}"); onDismiss() }.padding(horizontal = 6.dp, vertical = 12.dp)) }
                        }
                    }
                    if (e.audit.isNotEmpty()) {
                        SectionLabel("Lịch sử chỉnh sửa")
                        e.audit.forEach { Text("${Dates.fmtTime(it.at)} · ${it.by}: ${it.summary}", color = VC.text2, fontSize = 13.sp) }
                    }
                } else {
                    VField(date, { date = it; err = null }, "Ngày thực hiện (dd/MM/yyyy)")
                    if (e.type != EventType.OBSERVATION && e.type != EventType.RECHECK && e.type != EventType.HARVEST) {
                        VField(product, { product = it }, "Sản phẩm / vật vật tư"); VField(dosage, { dosage = it }, "Liều lượng")
                    }
                    if (e.type == EventType.HARVEST) VField(kg, { kg = it }, "Khối lượng thu hoạch (kg)", number = true)
                    VField(note, { note = it }, "Ghi chú", singleLine = false, minLines = 2)
                    Text("Chỉnh sửa được lưu kèm dấu vết; bản gốc không bị mất.", color = VC.muted, fontSize = 13.sp)
                    err?.let { Text(it, color = VC.red, fontSize = 14.sp) }
                }
            }
        },
        confirmButton = {
            if (editing) VButton("Lưu thay đổi", {
                val dt = Dates.parse(date)
                val k = if (kg.isBlank()) null else parseNum(kg)
                when {
                    dt == null -> err = "Ngày không hợp lệ."
                    e.type == EventType.HARVEST && kg.isNotBlank() && (k == null || k < 0) -> err = "Khối lượng không hợp lệ."
                    else -> {
                        val r = c.store.editEvent(e.id, e.version) {
                            it.copy(note = note.trim(), product = product.trim().ifEmpty { null }, dosage = dosage.trim().ifEmpty { null },
                                occurredAt = if (Dates.startOf(it.occurredAt) == dt) it.occurredAt else dt + 12 * 3_600_000L, amountKg = k)
                        }
                        if (r.ok) { editing = false; c.toast("Đã lưu thay đổi.") } else err = r.message
                    }
                }
            }) else VButton("Chỉnh sửa", { editing = true }, primary = false)
        },
        dismissButton = { TextButton(onDismiss) { Text("Đóng", color = VC.text2) } },
    )
}

