package com.example.test_tablet.ui.vwing

import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Checklist
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import com.example.test_tablet.data.vwing.Access
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.CheckStatus
import com.example.test_tablet.data.vwing.Condition
import com.example.test_tablet.data.vwing.Dates
import com.example.test_tablet.data.vwing.GardenData
import com.example.test_tablet.data.vwing.Issue
import com.example.test_tablet.data.vwing.IssueStatus
import com.example.test_tablet.data.vwing.Photo
import com.example.test_tablet.data.vwing.Priority
import com.example.test_tablet.data.vwing.activeTrees
import com.example.test_tablet.data.vwing.checkStatus
import com.example.test_tablet.data.vwing.effectiveCondition
import com.example.test_tablet.data.vwing.isOverdue
import com.example.test_tablet.data.vwing.summary
import com.example.test_tablet.data.vwing.treeByCode
import com.example.test_tablet.data.vwing.treeById
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Chọn ảnh từ thư viện, sao chép vào bộ nhớ app và lấy ngày chụp từ EXIF (nếu có). */
@Composable
fun rememberPhotoPicker(c: VCtx, onPicked: (List<Photo>) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            val ps = withContext(Dispatchers.IO) { uris.mapNotNull { c.store.importPhoto(it) } }
            if (ps.size < uris.size) c.toast("Không đọc được ${uris.size - ps.size} ảnh.")
            onPicked(ps)
        }
    }
    return { launcher.launch("image/*") }
}

fun priorityColor(p: Priority) = when (p) { Priority.HIGH -> VC.red; Priority.MEDIUM -> VC.amber; Priority.LOW -> VC.text2 }

fun dueText(due: Long?, overdue: Boolean) = due?.let { (if (overdue) "quá hạn " else "hạn ") + Dates.fmt(it) } ?: "chưa đặt hạn"

fun treeCodes(d: GardenData, ids: List<String>, zone: String?): String {
    val codes = ids.mapNotNull { d.treeById(it)?.code }
    return (zone?.let { "$it · " } ?: "") + if (codes.size > 4) codes.take(4).joinToString(", ") + " +${codes.size - 4}" else codes.joinToString(", ")
}

private val ISSUE_TABS = listOf("issues" to "Vấn đề", "tasks" to "Công việc", "trees" to "Cây cần chú ý")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IssuesScreen(c: VCtx, tabArg: String) {
    val d = c.d
    var tab by rememberSaveable(tabArg) { mutableStateOf(tabArg) }
    var statusF by rememberSaveable { mutableStateOf("Đang mở") }
    var taskF by rememberSaveable { mutableStateOf("Chưa xong") }
    var showIssue by remember { mutableStateOf(false) }
    var showTask by remember { mutableStateOf(false) }
    val s = d.summary()
    val today = Dates.today()

    PageHeader(Icons.Filled.Checklist, "ƯU TIÊN THỰC ĐỊA", "Vấn đề & kiểm tra", "Từ phát hiện đến xác nhận kết quả. Làm xong việc chưa có nghĩa là cây đã hồi phục.") {
        VButton("Ghi nhận vấn đề", { showIssue = true }, icon = Icons.Filled.Add)
        VButton("Thêm công việc", { showTask = true }, primary = false)
    }

    FlowRow(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = if (c.wide) 4 else 2,
    ) {
        listOf(Condition.NEEDS_CHECK, Condition.WATCH, Condition.STABLE, Condition.UNASSESSED).forEach { cond ->
            VCard(Modifier.weight(1f), borderColor = conditionColor(cond).copy(alpha = .5f), padding = PaddingValues(12.dp), onClick = { tab = "trees" }) {
                Text(cond.label, color = conditionColor(cond), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("${s.byCondition[cond]} cây", color = VC.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
    ChipRow(ISSUE_TABS.map { it.first }, tab, { k -> ISSUE_TABS.first { it.first == k }.second }, { tab = it }, Modifier.padding(bottom = 12.dp))

    when (tab) {
        "tasks" -> {
            ChipRow(listOf("Chưa xong", "Quá hạn", "Đã xong"), taskF, { it }, { taskF = it }, Modifier.padding(bottom = 10.dp))
            val list = d.tasks.filter {
                when (taskF) { "Chưa xong" -> !it.done; "Quá hạn" -> it.isOverdue(today); else -> it.done }
            }.sortedWith(compareBy({ it.dueAt ?: Long.MAX_VALUE }))
            VCard(Modifier.fillMaxWidth()) {
                if (list.isEmpty()) EmptyNote("Không có công việc trong bộ lọc này.")
                list.forEach { t ->
                    val over = t.isOverdue(today)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        androidx.compose.material3.Checkbox(checked = t.done, onCheckedChange = { c.store.setTaskDone(t.id, it) })
                        Column(Modifier.weight(1f)) {
                            Text(t.title, color = VC.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(listOfNotNull(treeCodes(d, t.treeIds, t.scopeZone).ifBlank { null }, t.assignee.ifBlank { null }, dueText(t.dueAt, over)).joinToString(" · "),
                                color = if (over) VC.red else VC.text2, fontSize = 14.sp)
                            val me = myName(LocalContext.current, d.garden.operatorName)
                            if (t.open) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Tag("Đơn việc mở", VC.lime)
                                if (Access.canClaimJobs) VButton("Nhận việc", { val r = c.store.claimTask(t.id, me); c.toast(if (r.ok) "Đã nhận việc. Đồng bộ để giữ chỗ." else r.message ?: "Không nhận được.") }, primary = true)
                            }
                            else if (Access.canClaimJobs && !t.done && t.assignee.trim().equals(me.trim(), true) && t.assignee.isNotBlank())
                                VButton("Trả lại việc", { val r = c.store.releaseTask(t.id, me); c.toast(if (r.ok) "Đã trả lại đơn việc mở." else r.message ?: "Không trả được.") }, primary = false, modifier = Modifier.padding(top = 4.dp))
                            t.issueId?.let { id -> d.issues.firstOrNull { it.id == id }?.let { i ->
                                Text("${i.code} · mở vấn đề", color = VC.lime, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.heightIn(min = 48.dp).clickable { c.nav.push("issue|${i.id}") }.padding(vertical = 12.dp))
                            } }
                        }
                        if (t.fromAi) Tag("Từ gợi ý AI", VC.cyan)
                        if (t.done) Tag("Xong ${t.doneAt?.let { Dates.fmt(it) } ?: ""}", VC.mint)
                    }
                    Divider2()
                }
            }
        }
        "trees" -> {
            val rows = d.activeTrees.map { it to d.effectiveCondition(it) }.filter { (t, cnd) ->
                cnd == Condition.NEEDS_CHECK || cnd == Condition.WATCH || cnd == Condition.UNASSESSED || d.checkStatus(t) !is CheckStatus.Ok
            }.sortedWith(compareByDescending<Pair<com.example.test_tablet.data.vwing.Tree, Condition>> { it.second == Condition.NEEDS_CHECK }
                .thenByDescending { it.second == Condition.WATCH })
            VCard(Modifier.fillMaxWidth()) {
                Text("Gồm cây Cần kiểm tra, Theo dõi, Chưa đánh giá và cây quá hạn kiểm tra. Cây chưa quan sát không được coi là ổn định.", color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                if (rows.isEmpty()) EmptyNote("Không có cây nào cần chú ý.")
                rows.forEach { (t, cnd) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { c.selectTree(t.id); c.nav.push("tree|${t.id}") }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ConditionShape(cnd, 18.dp)
                        Column(Modifier.weight(1f)) {
                            Text("${t.code} · ${t.zone}", color = VC.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(when (val st = d.checkStatus(t)) {
                                CheckStatus.Never -> "Chưa từng quan sát"; is CheckStatus.Overdue -> "Quá hạn kiểm tra ${st.days} ngày"; is CheckStatus.Ok -> "Quan sát ${st.days} ngày trước"
                            }, color = VC.text2, fontSize = 14.sp)
                        }
                        ConditionTag(cnd)
                    }
                    Divider2()
                }
            }
        }
        else -> {
            ChipRow(listOf("Đang mở", "Tất cả") + IssueStatus.values().map { it.label }, statusF, { it }, { statusF = it }, Modifier.padding(bottom = 10.dp))
            val list = d.issues.filter {
                when (statusF) { "Đang mở" -> it.status.isOpen; "Tất cả" -> true; else -> it.status.label == statusF }
            }.sortedWith(compareByDescending<Issue> { it.isOverdue(today) }.thenByDescending { it.priority.ordinal }.thenBy { it.dueAt ?: Long.MAX_VALUE })
            VCard(Modifier.fillMaxWidth()) {
                if (list.isEmpty()) EmptyNote("Không có vấn đề nào trong bộ lọc này.")
                list.forEach { i ->
                    val over = i.isOverdue(today)
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { c.nav.push("issue|${i.id}") }.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("${i.code} · ${i.symptom}", color = VC.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(listOfNotNull(treeCodes(d, i.treeIds, i.scopeZone).ifBlank { null }, i.assignee.ifBlank { null }, dueText(i.dueAt, over)).joinToString(" · "),
                                color = if (over) VC.red else VC.text2, fontSize = 14.sp)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Tag("Ưu tiên ${i.priority.label}", priorityColor(i.priority)); Tag(i.status.label, if (i.status.isOpen) VC.amber else VC.mint)
                        }
                    }
                    Divider2()
                }
            }
        }
    }
    if (showIssue) NewIssueDialog(c, emptyList(), null, null) { showIssue = false }
    if (showTask) NewTaskDialog(c, emptyList(), null) { showTask = false }
}

// ---------- Chi tiết vấn đề ----------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IssueDetailScreen(c: VCtx, id: String) {
    val d = c.d
    val i = d.issues.firstOrNull { it.id == id }
    if (i == null) { Text("Không tìm thấy vấn đề.", color = VC.text2); VButton("← Quay lại", { c.nav.pop() }, primary = false); return }
    var showEdit by remember { mutableStateOf(false) }
    var showRecheck by remember { mutableStateOf(false) }
    var showTask by remember { mutableStateOf(false) }
    val today = Dates.today()
    val over = i.isOverdue(today)

    fun move(to: IssueStatus) { val r = c.store.setIssueStatus(i.id, i.version, to); if (!r.ok) c.toast(r.message ?: "Không thể chuyển trạng thái.") }

    // Tiêu đề + mô tả đã nằm trong PageHeader (hiển thị cả ở Chế độ Nông dân);
    // nút back của Header trên cùng là đủ, không lặp thêm nút "← Quay lại".
    PageHeader(Icons.Filled.ReportProblem, "VẤN ĐỀ ${i.code}", i.symptom, "${treeCodes(d, i.treeIds, i.scopeZone)} · ${i.assignee.ifBlank { "chưa giao" }} · ${dueText(i.dueAt, over)}") {
        Tag("Ưu tiên ${i.priority.label}", priorityColor(i.priority)); Tag(i.status.label, if (i.status.isOpen) VC.amber else VC.mint); SyncTag(i.sync)
    }

    // Tiến trình
    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("TIẾN TRÌNH")
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            IssueStatus.values().forEach { st -> Tag((if (st == i.status) "● " else "○ ") + st.label, if (st == i.status) VC.lime else VC.muted) }
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (i.status) {
                IssueStatus.NEW -> { VButton("Chờ xác minh", { move(IssueStatus.PENDING_VERIFY) }, primary = false); VButton("Bắt đầu xử lý", { move(IssueStatus.IN_PROGRESS) }) }
                IssueStatus.PENDING_VERIFY -> VButton("Bắt đầu xử lý", { move(IssueStatus.IN_PROGRESS) })
                IssueStatus.IN_PROGRESS -> { VButton("Đã xử lý – chờ tái kiểm tra", { move(IssueStatus.PENDING_RECHECK) }); VButton("Tái kiểm tra…", { showRecheck = true }, primary = false) }
                IssueStatus.PENDING_RECHECK -> { VButton("Tái kiểm tra…", { showRecheck = true }); VButton("Quay lại xử lý", { move(IssueStatus.IN_PROGRESS) }, primary = false) }
                IssueStatus.CONFIRMED -> VButton("Mở lại", { move(IssueStatus.PENDING_VERIFY) }, primary = false)
            }
            VButton("Ghi chăm sóc", { c.nav.push("log|${i.treeIds.firstOrNull() ?: ""}|${i.id}") }, primary = false, enabled = i.status.isOpen)
            VButton("Sửa thông tin", { showEdit = true }, primary = false)
        }
        if (i.status == IssueStatus.CONFIRMED) Text("Đã xác nhận bởi ${i.confirmedBy} ngày ${i.closedAt?.let { Dates.fmt(it) }}.", color = VC.mint, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        Text("Chỉ đóng được vấn đề sau khi tái kiểm tra đạt, có bằng chứng (ghi chú/ảnh) và người xác nhận.", color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
    }
    Spacer(Modifier.height(14.dp))

    val tasks = d.tasks.filter { it.issueId == i.id }
    VCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("CÔNG VIỆC XỬ LÝ")
            VButton("Thêm", { showTask = true }, primary = false, icon = Icons.Filled.Add)
        }
        if (tasks.isEmpty()) EmptyNote("Chưa có công việc nào.")
        tasks.forEach { t ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.Checkbox(checked = t.done, onCheckedChange = { c.store.setTaskDone(t.id, it) })
                Column(Modifier.weight(1f)) {
                    Text(t.title, color = VC.text, fontSize = 15.sp)
                    Text("${t.assignee.ifBlank { "chưa giao" }} · ${dueText(t.dueAt, t.isOverdue(today))}", color = if (t.isOverdue(today)) VC.red else VC.text2, fontSize = 13.sp)
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("BẰNG CHỨNG & LỊCH SỬ XỬ LÝ")
        val evs = d.events.filter { it.issueId == i.id || it.id == i.originEventId }.sortedByDescending { it.occurredAt }
        if (evs.isEmpty()) EmptyNote("Chưa có ghi chép nào gắn với vấn đề này.")
        evs.forEach { EventRow(c, it) }
        if (i.audit.isNotEmpty()) {
            SectionLabel("Lịch sử thay đổi")
            i.audit.reversed().forEach { Text("${Dates.fmtTime(it.at)} · ${it.by}: ${it.summary}", color = VC.text2, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp)) }
        }
    }

    if (showEdit) EditIssueDialog(c, i.id) { showEdit = false }
    if (showTask) NewTaskDialog(c, i.treeIds, i.id) { showTask = false }
    if (showRecheck) RecheckDialog(c, i.id) { showRecheck = false }
}

// ---------- Hộp thoại ----------

/** Phân tích danh sách mã cây "A-04, B-05"; trả (cây tìm thấy, mã không tồn tại). */
fun parseCodes(d: GardenData, text: String): Pair<List<com.example.test_tablet.data.vwing.Tree>, List<String>> {
    val parts = text.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
    val found = mutableListOf<com.example.test_tablet.data.vwing.Tree>(); val missing = mutableListOf<String>()
    parts.forEach { p -> d.treeByCode(p)?.let { if (found.none { f -> f.id == it.id }) found += it } ?: missing.add(p) }
    return found to missing
}

@Composable
fun NewIssueDialog(c: VCtx, treeIds: List<String>, scopeZone: String?, originEventId: String?, onDismiss: () -> Unit) {
    val d = c.d
    var codes by remember { mutableStateOf("") }
    var createMinimal by remember { mutableStateOf(false) }
    var minimalZone by remember { mutableStateOf("") }
    var symptom by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(Priority.MEDIUM) }
    var assignee by remember { mutableStateOf(d.garden.operatorName) }
    var due by remember { mutableStateOf(Dates.fmt(Dates.daysAgo(-3))) }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = VC.card,
        title = { Text("Ghi nhận vấn đề", color = VC.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (treeIds.isEmpty()) {
                    VField(codes, { codes = it; err = null }, "Mã cây (nhiều cây cách nhau bằng dấu phẩy) *", placeholder = "A-04, B-05")
                    val (_, missing) = parseCodes(d, codes)
                    if (missing.isNotEmpty()) {
                        Text("Chưa có hồ sơ cho: ${missing.joinToString(", ")}", color = VC.amber, fontSize = 14.sp)
                        Row(Modifier.clickable { createMinimal = !createMinimal }, verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(checked = createMinimal, onCheckedChange = null); Text("  Tạo hồ sơ tối thiểu cho các mã này", color = VC.text, fontSize = 15.sp)
                        }
                        if (createMinimal) VField(minimalZone, { minimalZone = it }, "Khu vực của các cây mới *")
                    }
                } else Text("Áp dụng cho: ${treeCodes(d, treeIds, scopeZone)}", color = VC.mint, fontSize = 15.sp)
                VField(symptom, { symptom = it; err = null }, "Biểu hiện quan sát *", singleLine = false, minLines = 2, placeholder = "Ví dụ: lá vàng ở tán dưới")
                VPicker("Mức ưu tiên", Priority.values().map { it.label }, priority.label, { l -> priority = Priority.values().first { it.label == l } })
                VField(assignee, { assignee = it }, "Người phụ trách")
                VField(due, { due = it; err = null }, "Hạn kiểm tra (dd/MM/yyyy, có thể để trống)")
                Text("Đây là ghi nhận để xác minh, không phải chẩn đoán bệnh.", color = VC.muted, fontSize = 13.sp)
                err?.let { Text(it, color = VC.red, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            VButton("Ghi nhận", {
                val dueAt = if (due.isBlank()) null else Dates.parse(due)
                if (due.isNotBlank() && dueAt == null) { err = "Hạn không hợp lệ (dd/MM/yyyy)."; return@VButton }
                var ids = treeIds
                if (treeIds.isEmpty()) {
                    val (found, missing) = parseCodes(d, codes)
                    if (found.isEmpty() && missing.isEmpty()) { err = "Nhập mã cây."; return@VButton }
                    if (missing.isNotEmpty()) {
                        if (!createMinimal) { err = "Có mã chưa tồn tại. Tích \"Tạo hồ sơ tối thiểu\" hoặc sửa mã."; return@VButton }
                        if (minimalZone.isBlank()) { err = "Nhập khu vực cho cây mới."; return@VButton }
                        for (m in missing) { val r = c.store.addTree(m, minimalZone); if (!r.ok) { err = r.message; return@VButton } }
                    }
                    ids = parseCodes(c.store.data, codes).first.map { it.id }
                }
                val r = c.store.addIssue(ids, scopeZone, symptom, priority, assignee, dueAt, originEventId)
                if (r.ok) { c.toast("Đã ghi nhận vấn đề."); onDismiss() } else err = r.message
            })
        },
        dismissButton = { TextButton(onDismiss) { Text("Đóng", color = VC.text2) } },
    )
}

@Composable
fun NewTaskDialog(c: VCtx, treeIds: List<String>, issueId: String?, onDismiss: () -> Unit) {
    val d = c.d
    var title by remember { mutableStateOf("") }
    var codes by remember { mutableStateOf("") }
    var assignee by remember { mutableStateOf(d.garden.operatorName) }
    var due by remember { mutableStateOf(Dates.fmt(Dates.daysAgo(-1))) }
    var openJob by remember { mutableStateOf(false) }
    var zone by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = VC.card,
        title = { Text("Thêm công việc", color = VC.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VField(title, { title = it; err = null }, "Tên công việc *")
                if (treeIds.isEmpty()) VField(codes, { codes = it }, "Mã cây (không bắt buộc)", placeholder = "A-04, B-05")
                else Text("Áp dụng cho: ${treeCodes(d, treeIds, null)}", color = VC.mint, fontSize = 15.sp)
                if (!openJob) VField(assignee, { assignee = it }, "Người thực hiện")
                if (Access.canDelete) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Đơn việc mở (để nhân viên/phi công tự nhận)", color = VC.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Chưa giao ai; ai có quyền thấy việc này đều nhận được, người đến trước được giữ.", color = VC.text2, fontSize = 13.sp)
                    }
                    androidx.compose.material3.Switch(openJob, { openJob = it })
                }
                if (openJob && d.zones.isNotEmpty()) VPicker("Khu vực của việc (phi công chỉ thấy việc trong khu được cấp)", listOf("Toàn vườn") + d.zones.map { it.name }, zone.ifEmpty { "Toàn vườn" }, { zone = if (it == "Toàn vườn") "" else it })
                VField(due, { due = it; err = null }, "Hạn (dd/MM/yyyy)")
                err?.let { Text(it, color = VC.red, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            VButton("Thêm", {
                val dueAt = if (due.isBlank()) null else Dates.parse(due)
                if (due.isNotBlank() && dueAt == null) { err = "Hạn không hợp lệ (dd/MM/yyyy)."; return@VButton }
                val (found, missing) = if (treeIds.isEmpty()) parseCodes(d, codes) else (treeIds.mapNotNull { d.treeById(it) } to emptyList())
                if (missing.isNotEmpty()) { err = "Không tìm thấy cây: ${missing.joinToString(", ")}"; return@VButton }
                val r = c.store.addTask(title, found.map { it.id }, zone.ifBlank { null }.takeIf { openJob }, if (openJob) "" else assignee, dueAt, issueId, open = openJob)
                if (r.ok) { c.toast("Đã thêm công việc."); onDismiss() } else err = r.message
            })
        },
        dismissButton = { TextButton(onDismiss) { Text("Đóng", color = VC.text2) } },
    )
}

@Composable
fun EditIssueDialog(c: VCtx, id: String, onDismiss: () -> Unit) {
    val i = remember { c.d.issues.first { it.id == id } }
    var symptom by remember { mutableStateOf(i.symptom) }
    var priority by remember { mutableStateOf(i.priority) }
    var assignee by remember { mutableStateOf(i.assignee) }
    var due by remember { mutableStateOf(i.dueAt?.let { Dates.fmt(it) } ?: "") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = VC.card,
        title = { Text("Sửa vấn đề ${i.code}", color = VC.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VField(symptom, { symptom = it }, "Biểu hiện", singleLine = false, minLines = 2)
                VPicker("Mức ưu tiên", Priority.values().map { it.label }, priority.label, { l -> priority = Priority.values().first { it.label == l } })
                VField(assignee, { assignee = it }, "Người phụ trách")
                VField(due, { due = it; err = null }, "Hạn kiểm tra (dd/MM/yyyy)")
                err?.let { Text(it, color = VC.red, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            VButton("Lưu", {
                val dueAt = if (due.isBlank()) null else Dates.parse(due)
                if (due.isNotBlank() && dueAt == null) err = "Hạn không hợp lệ."
                else if (symptom.isBlank()) err = "Nhập biểu hiện."
                else {
                    val r = c.store.editIssue(i.id, i.version, "Sửa thông tin vấn đề") { it.copy(symptom = symptom.trim(), priority = priority, assignee = assignee.trim(), dueAt = dueAt) }
                    if (r.ok) onDismiss() else err = r.message
                }
            })
        },
        dismissButton = { TextButton(onDismiss) { Text("Đóng", color = VC.text2) } },
    )
}

@Composable
fun RecheckDialog(c: VCtx, id: String, onDismiss: () -> Unit) {
    val i = remember { c.d.issues.first { it.id == id } }
    var passed by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(Dates.fmt(Dates.now())) }
    var confirmer by remember { mutableStateOf(c.d.garden.operatorName) }
    val photos = remember { mutableStateListOf<Photo>() }
    var err by remember { mutableStateOf<String?>(null) }
    val pick = rememberPhotoPicker(c) { photos.addAll(it) }
    val shoot = rememberCameraCapture(c) { photos.addAll(it) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = VC.card,
        title = { Text("Tái kiểm tra ${i.code}", color = VC.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipRow(listOf(true, false), passed, { if (it) "Đạt – đã hồi phục" else "Chưa đạt" }, { passed = it })
                VField(note, { note = it; err = null }, "Kết quả quan sát (bằng chứng) *", singleLine = false, minLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    VButton("Thêm ảnh", pick, primary = false, icon = Icons.Filled.Add)
                    VButton("Chụp ảnh", shoot, primary = false)
                    Text("${photos.size} ảnh", color = VC.text2, fontSize = 14.sp)
                }
                VField(date, { date = it; err = null }, "Ngày tái kiểm tra (dd/MM/yyyy)")
                VField(confirmer, { confirmer = it; err = null }, "Người xác nhận *")
                Text(if (passed) "Vấn đề sẽ được đóng và cây chuyển sang Ổn định." else "Vấn đề quay lại \"Đang xử lý\" và cây giữ mức Cần kiểm tra.", color = VC.muted, fontSize = 13.sp)
                err?.let { Text(it, color = VC.red, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            VButton("Ghi kết quả", {
                val dt = Dates.parse(date)
                if (dt == null) err = "Ngày không hợp lệ."
                else {
                    val r = c.store.recheckIssue(i.id, i.version, passed, note, photos.toList(), confirmer, dt + 12 * 3_600_000L)
                    if (r.ok) { c.toast(if (passed) "Đã xác nhận kết quả và đóng vấn đề." else "Đã ghi: chưa đạt, tiếp tục xử lý."); onDismiss() } else err = r.message
                }
            })
        },
        dismissButton = { TextButton(onDismiss) { Text("Đóng", color = VC.text2) } },
    )
}



/** Tên dùng khi nhận việc: tên tài khoản đã đăng nhập (máy chủ so khớp theo tên này), không thì tên người dùng trong Thiết lập. */
fun myName(context: android.content.Context, fallback: String): String =
    com.example.test_tablet.data.vwing.SyncSettings(context.filesDir).load().userName.ifBlank { fallback }
