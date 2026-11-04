package com.example.test_tablet.ui.vwing

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.Access
import com.example.test_tablet.data.vwing.Account
import com.example.test_tablet.data.vwing.AccountException
import com.example.test_tablet.data.vwing.AuditRow
import com.example.test_tablet.data.vwing.Dates
import com.example.test_tablet.data.vwing.InviteLink
import com.example.test_tablet.data.vwing.InviteRow
import com.example.test_tablet.data.vwing.MemberRow
import com.example.test_tablet.data.vwing.PublicConfig
import com.example.test_tablet.data.vwing.SyncConfig
import com.example.test_tablet.data.vwing.SyncSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Chạy việc mạng ở luồng nền và đưa lỗi nghiệp vụ lên [onError]. */
@Composable
private fun rememberNet(onError: (String) -> Unit, setBusy: (Boolean) -> Unit): (() -> Any?, (Any?) -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    return { work, done ->
        setBusy(true)
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { work() } }
            setBusy(false)
            r.onSuccess { done(it) }.onFailure { onError((it as? AccountException)?.message ?: "Lỗi: ${it.message}") }
        }
    }
}

private fun fmtExpiry(ms: Long?) = ms?.let { "đến ${Dates.fmt(it)}" } ?: "không hết hạn"

/** Thành viên, lời mời và nhật ký kiểm toán. Chỉ chủ vườn / quản lý (quyền thật do máy chủ kiểm tra). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MembersCard(c: VCtx) {
    val context = LocalContext.current
    val settings = remember { SyncSettings(context.filesDir) }
    val cfg: SyncConfig = remember(Access.role) { settings.load() }
    if (!cfg.configured || !Access.canManageMembers) return

    var members by remember { mutableStateOf<List<MemberRow>>(emptyList()) }
    var invites by remember { mutableStateOf<List<InviteRow>>(emptyList()) }
    var audit by remember { mutableStateOf<List<AuditRow>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var role by remember { mutableStateOf("worker") }
    var zones by remember { mutableStateOf(setOf<String>()) }
    var days by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var fresh by remember { mutableStateOf<InviteRow?>(null) }
    var revoke by remember { mutableStateOf<MemberRow?>(null) }
    var edit by remember { mutableStateOf<MemberRow?>(null) }
    var transfer by remember { mutableStateOf<MemberRow?>(null) }
    val net = rememberNet({ err = it }, { busy = it })

    val roles = if (Access.role == "manager") Access.ROLES.filter { it != "manager" } else Access.ROLES
    fun reload() {
        err = null
        net({ Account.members(cfg) to Account.invites(cfg) }) { r -> @Suppress("UNCHECKED_CAST") (r as Pair<List<MemberRow>, List<InviteRow>>).let { members = it.first; invites = it.second } }
    }
    LaunchedEffect(cfg.gardenKey) { reload() }

    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("THÀNH VIÊN & PHÂN QUYỀN")
        Text(
            "Mời người cùng làm vườn bằng mã QR hoặc số điện thoại. Mỗi người có vai trò, có thể giới hạn theo khu và theo thời hạn; thu hồi có hiệu lực ngay ở máy chủ.",
            color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp),
        )
        if (members.isEmpty()) Text(if (busy) "Đang tải…" else "Chưa có thành viên.", color = VC.muted, fontSize = 14.sp)
        members.forEach { m ->
            Column(Modifier.padding(vertical = 6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(m.name, color = VC.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Tag(Access.label(m.role), if (m.role == "owner") VC.lime else VC.mint)
                    if (m.expired) Tag("Đã hết hạn", VC.red)
                }
                Text(
                    m.phone + " · " + (if (m.zones.isEmpty()) "toàn vườn" else "khu " + m.zones.joinToString(", ")) + " · " + fmtExpiry(m.expiresAt),
                    color = VC.text2, fontSize = 13.sp,
                )
                if (m.role != "owner" && (Access.isOwner || m.role != "manager")) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VButton("Đổi quyền", { edit = m }, primary = false)
                    VButton("Thu hồi", { revoke = m }, primary = false, danger = true)
                    if (Access.isOwner && Access.role == "owner" && !m.expired) VButton("Chuyển quyền chủ", { transfer = m }, primary = false, danger = true)
                }
            }
            Divider2()
        }

        SectionLabel("Mời thành viên")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipRow(roles, role, { Access.label(it) }, { role = it })
            Text(Access.describe(role), color = VC.text2, fontSize = 13.sp)
            if (c.d.zones.isNotEmpty()) {
                Text("Giới hạn theo khu (bỏ trống = toàn vườn)", color = VC.text2, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    c.d.zones.forEach { z ->
                        val on = z.name in zones
                        ChipRow(listOf(on), on, { z.name }, { zones = if (on) zones - z.name else zones + z.name })
                    }
                }
            }
            VField(days, { days = it.filter(Char::isDigit) }, "Thời hạn thành viên (ngày)" + if (role == "pilot") " — bắt buộc" else " — để trống nếu không giới hạn")
            VField(phone, { phone = it }, "Chỉ dành cho số điện thoại (tuỳ chọn)", placeholder = "0912345678")
            VButton("Tạo lời mời", {
                val d = days.toIntOrNull()
                if (role == "pilot" && d == null) err = "Phi công thuê ngoài cần số ngày hiệu lực."
                else net({ Account.invite(cfg, role, zones.toList(), d, phone.trim()) }) { r -> fresh = r as InviteRow; days = ""; phone = ""; reload() }
            }, enabled = !busy)
        }
        err?.let { Text(it, color = VC.red, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp)) }

        fresh?.let { inv ->
            val payload = InviteLink.payload(cfg.url, inv.code ?: "")
            Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(remember(payload) { qrBitmap(payload, 360).asImageBitmap() }, "Mã QR mời", Modifier.size(220.dp).background(Color.White))
                Text("Mã mời: ${inv.code}", color = VC.text, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 6.dp))
                Text("Dùng một lần, hết hạn ${Dates.fmtTime(inv.expiresAt)}. Người nhận quét QR trong mục Tài khoản, hoặc gõ mã.", color = VC.text2, fontSize = 13.sp)
            }
        }
        if (invites.isNotEmpty()) {
            SectionLabel("Lời mời đang chờ")
            invites.forEach { i ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${Access.label(i.role)}${i.phone?.let { " · $it" } ?: ""} · hết hạn ${Dates.fmt(i.expiresAt)}", color = VC.text2, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    VButton("Thu hồi", { net({ Account.revokeInvite(cfg, i.id) }) { reload() } }, primary = false)
                }
            }
        }

        if (Access.isOwner) {
            SectionLabel("Nhật ký kiểm toán")
            if (audit == null) VButton("Xem nhật ký kiểm toán", { net({ Account.audit(cfg, 100) }) { r -> @Suppress("UNCHECKED_CAST") run { audit = r as List<AuditRow> } } }, primary = false, enabled = !busy)
            else {
                if (audit!!.isEmpty()) Text("Chưa có hoạt động.", color = VC.muted, fontSize = 14.sp)
                audit!!.take(60).forEach { a ->
                    Text("${Dates.fmtTime(a.at)} · ${a.by}: ${a.detail}", color = if (a.detail.contains("TỪ CHỐI")) VC.amber else VC.text2, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }

    revoke?.let { m ->
        AlertDialog(
            onDismissRequest = { revoke = null }, containerColor = VC.card,
            title = { Text("Thu hồi quyền của ${m.name}?", color = VC.text) },
            text = { Text("${m.name} sẽ không còn truy cập vườn này nữa, ngay lập tức. Bản ghi họ đã tạo vẫn được giữ.", color = VC.text2, fontSize = 15.sp) },
            confirmButton = { VButton("Thu hồi", { revoke = null; net({ Account.revokeMember(cfg, m.userId) }) { reload(); c.toast("Đã thu hồi.") } }, danger = true) },
            dismissButton = { TextButton({ revoke = null }) { Text("Huỷ", color = VC.text2) } },
        )
    }
    transfer?.let { m ->
        var pw by remember(m) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { transfer = null }, containerColor = VC.card,
            title = { Text("Chuyển quyền chủ vườn cho ${m.name}?", color = VC.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${m.name} sẽ là chủ vườn (toàn quyền, kể cả xoá vườn). Bạn trở thành Quản lý và không tự lấy lại được quyền chủ. Nhập mật khẩu để xác nhận.", color = VC.text2, fontSize = 14.sp)
                    VField(pw, { pw = it }, "Mật khẩu của bạn", secret = true)
                }
            },
            confirmButton = {
                VButton("Chuyển quyền", {
                    transfer = null
                    net({ Account.transferOwner(cfg, m.userId, pw) }) { Access.role = "manager"; settings.save(settings.load().copy(role = "manager")); c.toast("Đã chuyển quyền chủ cho ${m.name}."); reload() }
                }, danger = true, enabled = pw.isNotEmpty())
            },
            dismissButton = { TextButton({ transfer = null }) { Text("Huỷ", color = VC.text2) } },
        )
    }
    edit?.let { m ->
        var r by remember(m) { mutableStateOf(m.role) }
        var z by remember(m) { mutableStateOf(m.zones.toSet()) }
        var d by remember(m) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { edit = null }, containerColor = VC.card,
            title = { Text("Đổi quyền của ${m.name}", color = VC.text) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipRow(roles, r, { Access.label(it) }, { r = it })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        c.d.zones.forEach { zone ->
                            val on = zone.name in z
                            ChipRow(listOf(on), on, { zone.name }, { z = if (on) z - zone.name else z + zone.name })
                        }
                    }
                    VField(d, { d = it.filter(Char::isDigit) }, "Gia hạn thêm từ hôm nay (ngày) — trống = giữ nguyên")
                }
            },
            confirmButton = {
                VButton("Lưu", {
                    edit = null
                    val exp = d.toIntOrNull()?.let { System.currentTimeMillis() + it * 86_400_000L }
                    net({ Account.updateMember(cfg, m.userId, role = r, zones = z.toList(), expiresAt = exp) }) { reload(); c.toast("Đã cập nhật quyền.") }
                })
            },
            dismissButton = { TextButton({ edit = null }) { Text("Huỷ", color = VC.text2) } },
        )
    }
}

/** Chủ vườn chọn dữ liệu nào hiện ra cho người mua quét QR (trang truy xuất công khai). */
@Composable
fun PublicPageCard(c: VCtx) {
    val context = LocalContext.current
    val settings = remember { SyncSettings(context.filesDir) }
    val cfg: SyncConfig = remember(Access.role) { settings.load() }
    if (!cfg.configured || !Access.isOwner) return

    var pc by remember { mutableStateOf<PublicConfig?>(null) }
    var enabled by remember { mutableStateOf(false) }
    var fields by remember { mutableStateOf(setOf<String>()) }
    var story by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val net = rememberNet({ err = it }, { busy = it })
    LaunchedEffect(cfg.gardenKey) {
        net({ Account.publicConfig(cfg) }) { r -> (r as PublicConfig).let { pc = it; enabled = it.enabled; fields = it.fields; story = it.story } }
    }

    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("TRANG CÔNG KHAI CHO NGƯỜI MUA")
        Text(
            "Người mua quét QR trên sản phẩm sẽ xem được lịch sử của cây — chỉ những trường bạn chọn dưới đây. Tên người ghi, vấn đề, công việc và doanh thu không bao giờ công khai.",
            color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (enabled) "Đang công khai" else "Đang tắt", color = VC.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Switch(enabled, { enabled = it })
        }
        pc?.allFields?.forEach { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(f.label, color = VC.text2, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(f.id in fields, { fields = if (it) fields + f.id else fields - f.id }, enabled = enabled)
            }
        }
        VField(story, { story = it.take(600) }, "Lời giới thiệu (tuỳ chọn)", singleLine = false, minLines = 2, modifier = Modifier.padding(top = 8.dp))
        err?.let { Text(it, color = VC.red, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp)) }
        VButton("Lưu cấu hình công khai", {
            err = null
            net({ Account.savePublicConfig(cfg, enabled, fields, story) }) { c.toast(if (enabled) "Đã bật trang công khai." else "Đã tắt trang công khai.") }
        }, modifier = Modifier.padding(top = 8.dp), enabled = !busy && pc != null)
        Text("Mã QR công khai của từng cây có ở hồ sơ cây → Mã QR → \"QR cho người mua\". Máy chủ cần truy cập được từ Internet (HTTPS) để người mua ngoài mạng nội bộ quét được.", color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

private val TYPE_LABEL = mapOf(
    "trees" to "cây", "events" to "ghi chép", "issues" to "vấn đề", "tasks" to "công việc", "sales" to "lần bán", "features" to "lớp bản đồ",
    "imagery" to "bộ ảnh", "measures" to "số đo", "indices" to "bản đồ chỉ số", "droneFlights" to "nhật ký bay", "zones" to "khu",
)

/** Hộp xác nhận xoá, nêu rõ những gì sẽ bị xoá kèm theo. */
@Composable
fun DeleteConfirmDialog(c: VCtx, type: String, id: String, title: String, onDismiss: () -> Unit, onDone: () -> Unit = {}) {
    val counts = remember(type, id) { c.store.deletePreview(type, id) }
    val summary = counts.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.value} ${TYPE_LABEL[it.key] ?: it.key}" }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = VC.card,
        title = { Text(title, color = VC.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Sẽ xoá: ${summary.ifBlank { "không có gì" }}.", color = VC.text, fontSize = 15.sp)
                Text("Ghi chép/vấn đề chỉ liên quan tới mục này bị xoá cùng; bản ghi còn liên quan tới mục khác chỉ bị bỏ liên kết. Việc xoá được đồng bộ sang mọi thiết bị và không hoàn tác.", color = VC.text2, fontSize = 14.sp)
            }
        },
        confirmButton = {
            VButton("Xoá", {
                val r = c.store.deleteEntity(type, id)
                if (r.ok) { c.toast("Đã xoá (${counts.values.sum()} bản ghi)."); onDone() } else c.toast(r.message ?: "Không xoá được.")
                onDismiss()
            }, danger = true)
        },
        dismissButton = { TextButton(onDismiss) { Text("Huỷ", color = VC.text2) } },
    )
}
