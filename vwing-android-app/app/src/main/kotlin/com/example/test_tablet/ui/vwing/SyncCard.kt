package com.example.test_tablet.ui.vwing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.Access
import com.example.test_tablet.data.vwing.Account
import com.example.test_tablet.data.vwing.AccountException
import com.example.test_tablet.data.vwing.Dates
import com.example.test_tablet.data.vwing.GardenRef
import com.example.test_tablet.data.vwing.InviteLink
import com.example.test_tablet.data.vwing.SyncClient
import com.example.test_tablet.data.vwing.SyncConfig
import com.example.test_tablet.data.vwing.SyncResult
import com.example.test_tablet.data.vwing.SyncSettings
import com.example.test_tablet.data.vwing.unsyncedCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun describe(r: SyncResult): String = when (r) {
    is SyncResult.Success -> buildString {
        append("Đã đồng bộ. Kéo về: thêm ${r.pulled.added}, cập nhật ${r.pulled.updated}")
        if (r.pulled.deleted > 0) append(", xoá ${r.pulled.deleted}")
        append("; ảnh lên ${r.photosUp}, ảnh về ${r.photosDown}.")
        if (r.rejected.isNotEmpty()) append(" Máy chủ TỪ CHỐI một số thay đổi: ${r.rejected.joinToString("; ")}")
    }
    is SyncResult.Blocked -> "Có ${r.conflicts} xung đột cần chọn bản giữ lại (mục Xung đột bên dưới) trước khi gửi lên máy chủ."
    is SyncResult.Failure -> r.message
}

/** Đồng bộ một lượt trên luồng nền và ghi lại kết quả (kể cả vai trò) vào cấu hình. */
suspend fun runSync(c: VCtx, settings: SyncSettings): Pair<SyncConfig, SyncResult> = withContext(Dispatchers.IO) {
    val cfg = settings.load()
    val r = SyncClient.run(c.store, cfg)
    val role = (r as? SyncResult.Success)?.role ?: cfg.role
    val next = cfg.copy(lastSyncAt = if (r is SyncResult.Success) System.currentTimeMillis() else cfg.lastSyncAt, lastMessage = describe(r), role = role)
    settings.save(next)
    Access.role = if (next.configured) next.role else ""
    next to r
}

/** Tự đồng bộ một lần khi mở app nếu đã cấu hình máy chủ; lỗi mạng không làm phiền người dùng, chỉ ghi vào trạng thái. */
@Composable
fun AutoSyncOnOpen(c: VCtx) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val settings = SyncSettings(context.filesDir)
        val cfg = settings.load()
        Access.role = if (cfg.configured) cfg.role else ""
        if (cfg.configured && !c.d.isSample) runSync(c, settings)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncCard(c: VCtx) {
    val context = LocalContext.current
    val settings = remember { SyncSettings(context.filesDir) }
    val scope = rememberCoroutineScope()
    var cfg by remember { mutableStateOf(settings.load()) }
    var url by remember { mutableStateOf(cfg.url) }
    var phone by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(false) }
    var otpOn by remember { mutableStateOf(false) }
    var loginMode by remember { mutableStateOf("pw") }      // pw = mật khẩu, sms = mã SMS, reset = quên mật khẩu
    var otpCode by remember { mutableStateOf("") }
    var otpWait by remember { mutableStateOf(0) }
    var adminToken by remember { mutableStateOf("") }
    var showAdmin by remember { mutableStateOf(false) }
    var gardens by remember { mutableStateOf<List<GardenRef>>(emptyList()) }
    var newKey by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var scan by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun persist(next: SyncConfig) { cfg = next; settings.save(next); Access.role = if (next.configured) next.role else "" }

    /** Chạy việc mạng ở luồng nền; lỗi nghiệp vụ hiện ngay dưới form. */
    fun <T> io(work: () -> T, done: (T) -> Unit) {
        val problem = SyncClient.urlProblem(url)
        if (problem != null) { err = problem; return }
        busy = true; err = null
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { work() } }
            busy = false
            r.onSuccess(done).onFailure { err = (it as? AccountException)?.message ?: "Lỗi: ${it.message}" }
        }
    }

    fun refreshGardens(next: SyncConfig = cfg) = io({ Account.me(next) }) { list ->
        gardens = list
        val cur = list.firstOrNull { it.key == next.gardenKey }
        if (cur != null) persist(next.copy(role = cur.role))
        else if (next.gardenKey.isNotBlank() && next.role.isNotBlank()) persist(next.copy(role = ""))
    }

    fun select(g: GardenRef) {
        persist(cfg.copy(gardenKey = g.key, role = g.role, lastSyncAt = null, lastMessage = null))
        c.toast("Đã chọn vườn ${g.name} (${Access.label(g.role)}).")
    }

    fun signedIn(s: com.example.test_tablet.data.vwing.Session) {
        val next = cfg.copy(url = Account.normalizeUrl(url), token = s.token, userName = s.userName, phone = s.phone, role = "", gardenKey = "")
        persist(next); password = ""; refreshGardens(next)
    }

    LaunchedEffect(cfg.signedIn) { if (cfg.signedIn && gardens.isEmpty()) refreshGardens() }
    // Hỏi máy chủ có bật xác thực SMS không (đợi người dùng ngừng gõ địa chỉ)
    LaunchedEffect(url, cfg.signedIn) {
        if (cfg.signedIn || SyncClient.urlProblem(url) != null) { otpOn = false; return@LaunchedEffect }
        kotlinx.coroutines.delay(600)
        otpOn = withContext(Dispatchers.IO) { Account.otpEnabled(url) }
    }
    // đếm ngược nút "Gửi lại mã"
    LaunchedEffect(otpWait) { if (otpWait > 0) { kotlinx.coroutines.delay(1000); otpWait -= 1 } }

    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("TÀI KHOẢN & ĐỒNG BỘ")
        Text(
            "Mỗi người dùng một tài khoản; quyền được máy chủ kiểm tra theo vườn và vai trò. Khi hai nơi cùng sửa một bản ghi, app giữ cả hai và hỏi bạn chọn, không tự ghi đè.",
            color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VField(url, { url = it; err = null }, "Địa chỉ máy chủ", placeholder = "http://192.168.1.10:8790", enabled = !cfg.signedIn)
            if (url.trim().startsWith("http://")) Text("HTTP không mã hóa: chỉ dùng trong mạng nội bộ tin cậy.", color = VC.amber, fontSize = 13.sp)

            if (!cfg.signedIn) {
                ChipRow(listOf(false, true), register, { if (it) "Tạo tài khoản" else "Đăng nhập" }, { register = it; err = null; otpCode = "" })
                if (!register && otpOn) ChipRow(listOf("pw", "sms", "reset"), loginMode, { when (it) { "pw" -> "Mật khẩu"; "sms" -> "Mã SMS"; else -> "Quên mật khẩu" } }, { loginMode = it; err = null; otpCode = "" })
                VField(phone, { phone = it; err = null }, "Số điện thoại", placeholder = "0912345678")
                if (register) VField(name, { name = it }, "Họ tên (hiện trong nhật ký)")
                val needPw = register || !otpOn || loginMode != "sms"
                if (needPw) VField(password, { password = it }, (if (!register && otpOn && loginMode == "reset") "Mật khẩu mới" else "Mật khẩu") + if (register || loginMode == "reset") " (tối thiểu 8 ký tự)" else "", secret = true)
                val usesOtp = otpOn && (register || loginMode != "pw")
                val purpose = if (register) "register" else if (loginMode == "reset") "reset" else "login"
                if (usesOtp) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    VField(otpCode, { otpCode = it.filter(Char::isDigit).take(6); err = null }, "Mã SMS (6 số)", Modifier.weight(1f), number = true)
                    VButton(if (otpWait > 0) "Gửi lại (${otpWait}s)" else "Gửi mã SMS", {
                        io({ Account.requestOtp(url, phone, purpose) }) { w -> otpWait = w; c.toast("Đã gửi mã SMS tới $phone (nếu số này hợp lệ).") }
                    }, primary = false, enabled = !busy && otpWait == 0 && phone.isNotBlank())
                }
                VButton(if (busy) "Đang xử lý…" else if (register) "Tạo tài khoản" else if (loginMode == "reset" && otpOn) "Đặt lại mật khẩu" else "Đăng nhập", {
                    io({
                        when {
                            register -> Account.register(url, phone, name, password, if (otpOn) otpCode else null)
                            otpOn && loginMode == "sms" -> Account.loginOtp(url, phone, otpCode)
                            otpOn && loginMode == "reset" -> Account.resetPassword(url, phone, otpCode, password)
                            else -> Account.login(url, phone, password)
                        }
                    }, ::signedIn)
                }, enabled = !busy && url.isNotBlank() && phone.isNotBlank() && (!needPw || password.isNotBlank()) && (!register || name.isNotBlank()) && (!usesOtp || otpCode.length == 6))
                Text(if (otpOn) "Máy chủ yêu cầu xác thực số điện thoại bằng mã SMS khi tạo tài khoản; có thể đăng nhập bằng mã SMS hoặc đặt lại mật khẩu." else "Máy chủ chưa bật mã SMS: đăng nhập bằng số điện thoại và mật khẩu.", color = VC.muted, fontSize = 13.sp)
                if (!showAdmin) VButton("Dùng mã quản trị hệ thống (nâng cao)", { showAdmin = true }, primary = false)
                else {
                    VField(adminToken, { adminToken = it }, "Mã quản trị (VWING_TOKEN)", secret = true)
                    VField(cfg.gardenKey, { persist(cfg.copy(gardenKey = it.trim())) }, "Mã vườn")
                    VButton("Lưu mã quản trị", {
                        val problem = SyncClient.urlProblem(url)
                        if (problem != null) err = problem
                        else if (!Regex("^[A-Za-z0-9_-]{3,64}$").matches(cfg.gardenKey)) err = "Mã vườn chỉ gồm chữ, số, - và _ (3–64 ký tự)."
                        else { persist(cfg.copy(url = Account.normalizeUrl(url), token = adminToken.trim(), role = "admin", userName = "Quản trị")); c.toast("Đã lưu.") }
                    }, primary = false, enabled = adminToken.length >= 12)
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(cfg.userName.ifBlank { "Quản trị" }, color = VC.text, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        if (cfg.phone.isNotBlank()) Text(cfg.phone, color = VC.text2, fontSize = 14.sp)
                    }
                    VButton("Đăng xuất", {
                        val old = cfg
                        scope.launch(Dispatchers.IO) { Account.logout(old) }
                        persist(old.copy(token = "", role = "", userName = "", phone = "", gardenKey = "", lastSyncAt = null, lastMessage = null)); gardens = emptyList()
                    }, primary = false)
                }
                if (cfg.role != "admin") {
                    SectionLabel("Vườn của bạn")
                    if (gardens.isEmpty()) Text("Bạn chưa thuộc vườn nào: tạo vườn mới hoặc nhập mã mời bên dưới.", color = VC.text2, fontSize = 14.sp)
                    ChipRow(gardens, gardens.firstOrNull { it.key == cfg.gardenKey }, { "${it?.name ?: ""} · ${Access.label(it?.role ?: "")}" }, { it?.let(::select) })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VField(newKey, { newKey = it; err = null }, "Mã vườn mới", Modifier.weight(1f), placeholder = "vuon-sau-rieng-01")
                        VField(newName, { newName = it }, "Tên vườn", Modifier.weight(1f))
                    }
                    VButton("Tạo vườn mới (bạn là chủ)", {
                        if (!Regex("^[A-Za-z0-9_-]{3,64}$").matches(newKey.trim())) err = "Mã vườn chỉ gồm chữ, số, - và _ (3–64 ký tự)."
                        else io({ Account.createGarden(cfg, newKey.trim(), newName.trim().ifBlank { newKey.trim() }) }) { g -> newKey = ""; newName = ""; gardens = gardens + g; select(g) }
                    }, primary = false, enabled = !busy && newKey.isNotBlank())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        VField(inviteCode, { inviteCode = it; err = null }, "Mã mời (10 ký tự)", Modifier.weight(1f))
                        VButton("Quét QR", { scan = true }, primary = false)
                    }
                    VButton("Tham gia bằng mã mời", {
                        val code = InviteLink.parse(inviteCode)?.first
                        if (code == null) err = "Mã mời gồm 10 ký tự chữ và số."
                        else io({ Account.acceptInvite(cfg, code) }) { g -> inviteCode = ""; gardens = gardens.filter { it.key != g.key } + g; select(g) }
                    }, primary = false, enabled = !busy && inviteCode.isNotBlank())
                }
            }
        }
        err?.let { Text(it, color = VC.red, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp)) }

        if (cfg.configured) {
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag("Vườn ${cfg.gardenKey}")
                if (cfg.role.isNotBlank()) Tag(Access.label(cfg.role), VC.lime)
            }
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VButton(if (busy) "Đang đồng bộ…" else "Đồng bộ ngay", {
                    busy = true; err = null
                    scope.launch {
                        val (next, r) = runSync(c, settings)
                        cfg = next; busy = false; c.toast(describe(r))
                    }
                }, enabled = !busy)
                if (cfg.role == "owner") VButton("Xoá vườn…", { confirmDelete = true }, primary = false, danger = true)
            }
            Text(
                "Chưa đồng bộ: ${c.d.unsyncedCount()} bản ghi" + (cfg.lastSyncAt?.let { " · lần cuối ${Dates.fmtTime(it)}" } ?: " · chưa đồng bộ lần nào"),
                color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
            )
            cfg.lastMessage?.let { Text(it, color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
        }
        Text(
            "Phiên đăng nhập lưu trong vùng riêng của app, không nằm trong tệp sao lưu. Đồng bộ gồm cây, nhật ký, vấn đề, công việc, bán hàng, bản đồ, ảnh, thông tin vườn và các mục đã xoá.",
            color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
        )
    }

    if (scan) QrScannerDialog({ scan = false }) { raw ->
        scan = false
        val p = InviteLink.parse(raw)
        if (p == null) c.toast("Đây không phải mã mời của VWing.")
        else { inviteCode = p.first; p.second?.let { if (url.isBlank()) url = it }; c.toast("Đã nhận mã mời. Bấm \"Tham gia bằng mã mời\".") }
    }
    if (confirmDelete) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = VC.card,
        title = { Text("Xoá vườn ${cfg.gardenKey}?", color = VC.text) },
        text = { Text("Xoá toàn bộ dữ liệu, ảnh, thành viên và lời mời của vườn này trên máy chủ. Không hoàn tác. Dữ liệu trên máy bạn vẫn còn (hãy sao lưu trước).", color = VC.text2, fontSize = 15.sp) },
        confirmButton = {
            VButton("Xoá vườn trên máy chủ", {
                confirmDelete = false
                val old = cfg
                io({ Account.deleteGarden(old) }) { persist(old.copy(gardenKey = "", role = "", lastSyncAt = null, lastMessage = null)); gardens = gardens.filter { it.key != old.gardenKey }; c.toast("Đã xoá vườn trên máy chủ.") }
            }, danger = true)
        },
        dismissButton = { androidx.compose.material3.TextButton({ confirmDelete = false }) { Text("Huỷ", color = VC.text2) } },
    )
}
