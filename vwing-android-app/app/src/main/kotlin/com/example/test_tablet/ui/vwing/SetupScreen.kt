package com.example.test_tablet.ui.vwing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.Access
import com.example.test_tablet.data.vwing.BulkImport
import com.example.test_tablet.data.vwing.BulkPreview
import com.example.test_tablet.data.vwing.PlanTier
import com.example.test_tablet.data.vwing.RowStatus
import com.example.test_tablet.data.vwing.VFeature
import com.example.test_tablet.data.vwing.VWingStore
import com.example.test_tablet.data.vwing.unsyncedCount
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(c: VCtx) {
    val d = c.d
    val g = d.garden
    val context = LocalContext.current
    var name by remember(g) { mutableStateOf(g.name) }
    var location by remember(g) { mutableStateOf(g.location) }
    var crop by remember(g) { mutableStateOf(g.crop) }
    var area by remember(g) { mutableStateOf(g.areaHa?.toString()?.removeSuffix(".0") ?: "") }
    var cycle by remember(g) { mutableStateOf(g.harvestCycle) }
    var interval by remember(g) { mutableStateOf(g.checkIntervalDays.toString()) }
    var operator by remember(g) { mutableStateOf(g.operatorName) }
    var err by remember { mutableStateOf<String?>(null) }
    var zName by remember { mutableStateOf("") }
    var zDesc by remember { mutableStateOf("") }
    var showBulk by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<String?>(null) }
    var delZone by remember { mutableStateOf<String?>(null) }
    var showPlace by remember { mutableStateOf(false) }
    var locBusy by remember { mutableStateOf(false) }
    var locNote by remember { mutableStateOf<String?>(null) }
    val locScope = androidx.compose.runtime.rememberCoroutineScope()
    val ensureLoc = rememberEnsureLocationPermission(c)
    val gpsFix = rememberLocationFetcher(c) { la, lo, _ ->
        locBusy = true; locNote = null
        val cfg = com.example.test_tablet.data.vwing.SatelliteSettings(context.filesDir).load()
        locScope.launch {
            val r = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.example.test_tablet.data.vwing.GeocodeClient.reverse(cfg, com.example.test_tablet.data.vwing.GeoPoint(la, lo)) } }
            locBusy = false
            r.onSuccess { pl ->
                if (pl == null || pl.admin.isBlank()) locNote = "Không tra được địa chỉ tại vị trí này."
                else { location = pl.admin; locNote = "Đã điền: ${pl.admin}. Bấm \"Lưu thông tin vườn\" để giữ lại." }
            }.onFailure { locNote = "Không tra được địa chỉ: ${it.message}" }
        }
    }
    fun fillFromGps() = gpsFix()

    val exportQr = rememberQrSheetExport(c)
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { c.store.exportBackup(it) }
            c.toast("Đã sao lưu dữ liệu và ảnh.")
        }.onFailure { c.toast("Không sao lưu được: ${it.message}") }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val res = context.contentResolver.openInputStream(uri)?.use { c.store.importBackup(it) }
            report = res?.fold({ "Đã hợp nhất bản sao lưu: $it" }, { "Không đọc được bản sao lưu: ${it.message}" }) ?: "Không mở được tệp."
        }
    }

    PageHeader(Icons.Filled.Settings, "THIẾT LẬP", "Thiết lập vườn", "Đăng nhập SĐT + OTP Firebase, định vị địa chỉ trên Map, thông tin vườn và sao lưu.")

    FarmerAccountMapCard(c = c)
    Spacer(Modifier.height(16.dp))

    // Thiết lập chia theo mục đích: mỗi nhóm một dòng tiêu đề, mở một nhóm khi cần (mặc định chỉ mở "Vườn của tôi").
    var openGroups by remember { mutableStateOf(setOf("vuon")) }
    val gardenCard: @Composable () -> Unit = {
            VCard(Modifier.fillMaxWidth()) {
                Eyebrow("THÔNG TIN VƯỜN")
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VField(name, { name = it }, "Tên khu vườn")
                    VField(location, { location = it }, "Địa điểm")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        VButton(if (locBusy) "Đang tra địa chỉ…" else "Điền theo vị trí của tôi", { ensureLoc { fillFromGps() } }, primary = false, enabled = !locBusy)
                        VButton("Tìm địa điểm…", { showPlace = true }, primary = false)
                    }
                    if (location.isBlank() && locNote == null) Text("Địa chỉ lấy từ OpenStreetMap (Photon) qua máy chủ vệ tinh.", color = VC.muted, fontSize = 13.sp)
                    locNote?.let { Text(it, color = if (it.startsWith("Không")) VC.red else VC.mint, fontSize = 14.sp) }
                    VField(crop, { crop = it }, "Loại cây trồng")
                    VField(area, { area = it; err = null }, "Diện tích (ha)", number = true)
                    VField(cycle, { cycle = it }, "Chu kỳ thu hoạch")
                    VField(interval, { interval = it.filter(Char::isDigit); err = null }, "Hạn kiểm tra định kỳ (ngày)")
                    VField(operator, { operator = it }, "Tên người dùng (ghi trên bản ghi)")
                    err?.let { Text(it, color = VC.red, fontSize = 14.sp) }
                    if (!Access.canEditGarden) Text("Vai trò ${Access.label(Access.role)} chỉ xem thông tin vườn; chủ vườn hoặc quản lý mới sửa được.", color = VC.amber, fontSize = 13.sp)
                    VButton("Lưu thông tin vườn", {
                        val a = if (area.isBlank()) null else parseNum(area)
                        val iv = interval.toIntOrNull()
                        when {
                            name.isBlank() -> err = "Nhập tên vườn."
                            area.isNotBlank() && (a == null || a <= 0) -> err = "Diện tích phải lớn hơn 0."
                            iv == null || iv < 1 || iv > 365 -> err = "Hạn kiểm tra từ 1 đến 365 ngày."
                            operator.isBlank() -> err = "Nhập tên người dùng."
                            else -> { c.store.updateGarden(g.copy(name = name.trim(), location = location.trim(), crop = crop.trim(), areaHa = a,
                                harvestCycle = cycle.trim(), checkIntervalDays = iv, operatorName = operator.trim())); c.toast("Đã lưu thông tin vườn.") }
                        }
                    }, enabled = Access.canEditGarden)
                }
            }
    }
    val zonesCard: @Composable () -> Unit = {
            VCard(Modifier.fillMaxWidth()) {
                Eyebrow("KHU VỰC")
                if (d.zones.isEmpty()) EmptyNote("Chưa có khu nào.")
                d.zones.forEach { z ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(z.name + if (z.desc.isNotBlank()) " · ${z.desc}" else "", color = VC.text, fontSize = 15.sp, modifier = Modifier.padding(vertical = 4.dp).weight(1f))
                        if (Access.canDelete) VButton("Xoá", { delZone = z.name }, primary = false, danger = true)
                    }
                }
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VField(zName, { zName = it }, "Tên khu mới"); VField(zDesc, { zDesc = it }, "Mô tả (gần lối vào, đất dốc…)")
                    VButton("Thêm khu", { val r = c.store.addZone(zName, zDesc); if (r.ok) { zName = ""; zDesc = "" } else c.toast(r.message ?: "Lỗi") }, primary = false, enabled = zName.isNotBlank())
                }
            }
    }
    val backupCard: @Composable () -> Unit = {
            VCard(Modifier.fillMaxWidth()) {
                Eyebrow("LƯU TRỮ & SAO LƯU")
                Text("Mọi ghi chép, ảnh và hồ sơ được lưu ngay trên thiết bị, kể cả khi mất mạng. ${d.unsyncedCount()} bản ghi chưa đồng bộ lên máy chủ (hiện \"Đã lưu tại máy\"). Hãy cấu hình máy chủ bên dưới hoặc sao lưu định kỳ.",
                    color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VButton("Sao lưu (.zip)", { export.launch("vwing-sao-luu.zip") })
                    VButton("Khôi phục / hợp nhất…", { restore.launch(arrayOf("*/*")) }, primary = false)
                    VButton(
                        if (c.isFeatureEnabled(VFeature.BULK_IMPORT_EXPORT)) "Nhập cây từ bảng tính…" else "Nhập cây từ bảng tính… (PRO)",
                        {
                            if (c.isFeatureEnabled(VFeature.BULK_IMPORT_EXPORT)) showBulk = true
                            else c.requestFeature(VFeature.BULK_IMPORT_EXPORT)
                        },
                        primary = false,
                    )
                    VButton("Xuất nhãn QR tất cả cây", exportQr, primary = false)
                }
                Text("Hợp nhất không ghi đè âm thầm: bản ghi trùng ID khác nội dung được đưa vào mục Xung đột bên dưới. Gửi lại cùng một bản sao lưu không tạo bản ghi trùng.", color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                report?.let { Text(it, color = VC.mint, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
    }
    val conflictsCard: @Composable () -> Unit = {
            if (d.conflicts.isNotEmpty()) VCard(Modifier.fillMaxWidth(), borderColor = VC.amber.copy(alpha = .6f)) {
                Eyebrow("XUNG ĐỘT CẦN GIẢI QUYẾT (${d.conflicts.size})", VC.amber)
                d.conflicts.forEach { cf ->
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text("${typeLabel(cf.entityType)}: ${cf.label}", color = VC.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        diffLines(cf.localJson, cf.remoteJson).forEach { Text(it, color = VC.text2, fontSize = 13.sp) }
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VButton("Giữ bản trên máy", { c.store.resolveConflict(cf.id, false) }, primary = false)
                            VButton("Lấy bản vừa nhập", { c.store.resolveConflict(cf.id, true) }, primary = false)
                        }
                    }
                    Divider2()
                }
            }
    }
    val displayCard: @Composable () -> Unit = {
            VCard(Modifier.fillMaxWidth()) {
                Eyebrow("CHẾ ĐỘ HIỂN THỊ & GÓI DỊCH VỤ")
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Chế độ đơn giản (Nông dân)", color = VC.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Ẩn số liệu kỹ thuật (độ phủ mây, kích thước pixel, số cảnh ảnh…) ở các màn GIS/vệ tinh, chỉ hiện kết luận rõ ràng dễ hiểu.",
                            color = VC.text2, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                    androidx.compose.material3.Switch(
                        checked = c.simpleMode,
                        onCheckedChange = { next ->
                            if (!next && !c.isFeatureEnabled(VFeature.ENGINEER_MODE)) {
                                c.requestFeature(VFeature.ENGINEER_MODE)
                            } else {
                                c.setSimpleMode(next)
                            }
                        },
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val isEnt = c.uiSettings.planTier == PlanTier.ENTERPRISE
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (isEnt) "Đang dùng: ✦ Gói Enterprise (Trang trại / HTX)" else "Đang dùng: 🌱 Gói Free (Nông dân cá thể)",
                            color = VC.text,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                        Text(
                            "Bấm để chuyển gói Free ⇄ Enterprise hoặc gạt On/Off từng chức năng (Feature Flags).",
                            color = VC.text2,
                            fontSize = 13.sp,
                        )
                    }
                    VButton("Quản lý Gói & On/Off", { c.requestFeature(null) }, primary = false)
                }
            }
    }
    val advancedCard: @Composable () -> Unit = {
            VCard(Modifier.fillMaxWidth()) {
                Eyebrow("NÂNG CAO")
                Text("Nhật ký bay drone DJI và công cụ khác trong cùng dự án.", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VButton("Nhật ký bay drone", { c.nav.push("drone") }, primary = false)
                    VButton("FarmInsights", c.openFarmer, primary = false); VButton("FlightOps", c.openOps, primary = false)
                }
            }
    }
    val sampleCard: @Composable () -> Unit = {
            if (d.isSample) VCard(Modifier.fillMaxWidth()) {
                Eyebrow("DỮ LIỆU MINH HỌA")
                Text("Đang dùng dữ liệu mẫu. Xóa để bắt đầu vườn của bạn (nên sao lưu trước nếu muốn giữ lại).", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
                VButton("Xóa dữ liệu mẫu", { c.store.clearSample(); c.toast("Đã xóa dữ liệu mẫu.") }, danger = true)
            }
    }

    val group: @Composable (String, String, String, @Composable ColumnScope.() -> Unit) -> Unit = { key, title, hint, body ->
        val isOpen = key in openGroups
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 68.dp).clip(LeafShape).background(if (isOpen) VC.rail else VC.card)
                    .border(1.dp, if (isOpen) VC.rail else VC.border, LeafShape)
                    .bounceClick { openGroups = if (isOpen) openGroups - key else openGroups + key }.padding(horizontal = 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = if (isOpen) VC.onRail else VC.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text(hint, color = if (isOpen) VC.onRailMuted else VC.text2, fontSize = 15.sp)
                }
                Text(if (isOpen) "Thu gọn" else "Mở", color = if (isOpen) VC.onRail else VC.lime, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            if (isOpen) Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = body)
        }
    }

    if (d.conflicts.isNotEmpty()) conflictsCard()     // xung đột cần quyết định nên luôn nằm trên cùng
    val colA: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            group("vuon", "Vườn của tôi", "Thông tin vườn, khu vực, gói dịch vụ & hiển thị") { displayCard(); gardenCard(); zonesCard() }
            group("data", "Dữ liệu và sao lưu", "Sao lưu, khôi phục, nhập bảng tính, nhật ký bay") { backupCard(); advancedCard(); if (d.isSample) sampleCard() }
        }
    }
    val colB: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            group(
                "acct",
                if (c.isFeatureEnabled(VFeature.CLOUD_TEAM)) "Tài khoản và nhóm" else "Tài khoản và nhóm (PRO)",
                "Đăng nhập, đồng bộ đám mây, phân quyền nhân công & trang công khai",
            ) {
                if (c.isFeatureEnabled(VFeature.CLOUD_TEAM)) {
                    SyncCard(c); MembersCard(c); PublicPageCard(c)
                } else {
                    VCard(Modifier.fillMaxWidth()) {
                        Eyebrow("ĐỒNG BỘ ĐÁM MÂY & LÀM VIỆC NHÓM (GÓI ENTERPRISE)", VC.amber)
                        Text(
                            "Ở Gói Free, toàn bộ dữ liệu vườn được lưu an toàn 100% trên máy tính bảng của bạn và có thể sao lưu ra tệp .zip. Nâng cấp hoặc bật tính năng Đồng bộ nhóm để nhiều máy cùng cập nhật và phân quyền Chủ vườn / Kỹ sư / Nhân công.",
                            color = VC.text2,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                        VButton("✦ Mở khóa Đồng bộ Đám mây & Nhóm", {
                            c.requestFeature(VFeature.CLOUD_TEAM)
                        })
                    }
                }
            }
            group("tools", "Công cụ bản đồ", "Chọn vùng vườn, đi bộ ghi ranh, GIS") { LocationCard(c); GisToolsCard(c) }
        }
    }
    if (c.wide) Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) { colA(Modifier.weight(1f)); colB(Modifier.weight(1f)) }
    else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { colA(Modifier); colB(Modifier) }

    if (showBulk) BulkDialog(c) { showBulk = false }
    if (showPlace) PlaceSearchDialog(null, { pl -> location = pl.admin.ifBlank { pl.label }; locNote = "Đã điền: $location. Bấm \"Lưu thông tin vườn\" để giữ lại." }, { showPlace = false })
    delZone?.let { z -> DeleteConfirmDialog(c, "zones", z, "Xoá khu \"$z\"?", { delZone = null }) }
}

private fun typeLabel(t: String) = when (t) {
    "tree" -> "Cây"; "event" -> "Ghi chép"; "issue" -> "Vấn đề"; "task" -> "Công việc"; "sale" -> "Lần bán"; "feature" -> "Lớp bản đồ"; else -> t
}

private val SKIP_KEYS = setOf("version", "baseVersion", "updatedAt", "opId", "sync", "audit")

private fun diffLines(local: String, remote: String): List<String> = runCatching {
    val l = JSONObject(local); val r = JSONObject(remote)
    val keys = (l.keys().asSequence() + r.keys().asSequence()).toSet() - SKIP_KEYS
    keys.filter { l.opt(it).toString() != r.opt(it).toString() }.take(6).map {
        "• $it: máy \"${l.opt(it).toString().take(50)}\" ≠ nhập \"${r.opt(it).toString().take(50)}\""
    }
}.getOrDefault(emptyList())

@Composable
private fun BulkDialog(c: VCtx, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<BulkPreview?>(null) }
    fun load(t: String) { text = t; preview = if (t.isBlank()) null else BulkImport.parse(t, c.d) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { load(context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: "") }
    }
    val p = preview
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = VC.card,
        title = { Text("Nhập cây từ bảng tính", color = VC.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dán dữ liệu từ Excel/Google Sheets (TSV) hoặc CSV, hoặc chọn tệp .csv. Dòng đầu là tiêu đề: mã cây, khu, số trái, giống, năm trồng, vĩ độ, kinh độ. Chỉ cột mã cây là bắt buộc. Ô số trái để trống nghĩa là chưa đếm.", color = VC.text2, fontSize = 14.sp)
                VButton("Chọn tệp CSV…", { picker.launch(arrayOf("*/*")) }, primary = false)
                VField(text, { load(it) }, "Dán dữ liệu", singleLine = false, minLines = 4)
                p?.fatal?.let { Text("✕ $it", color = VC.red, fontSize = 14.sp) }
                if (p != null && p.fatal == null) {
                    SectionLabel("Xem trước · ${p.addable.size} cây sẽ được thêm / ${p.rows.size} dòng")
                    p.rows.take(60).forEach { r ->
                        val col = when (r.status) { RowStatus.OK -> VC.mint; RowStatus.ERROR -> VC.red; else -> VC.amber }
                        Text("Dòng ${r.line} · ${r.code.ifBlank { "(trống)" }} · ${r.zone} · ${r.status.label}${if (r.message.isNotBlank()) " — ${r.message}" else ""}", color = col, fontSize = 13.sp)
                    }
                    if (p.rows.size > 60) Text("… và ${p.rows.size - 60} dòng nữa", color = VC.muted, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            VButton("Thêm ${p?.addable?.size ?: 0} cây", {
                val rows = p!!.addable.map { VWingStore.TreeRow(it.code, it.zone, it.variety, it.year, it.fruits, it.pos) }
                val n = c.store.bulkAddTrees(rows)
                c.toast("Đã thêm $n cây."); onDismiss()
            }, enabled = (p?.addable?.size ?: 0) > 0)
        },
        dismissButton = { TextButton(onDismiss) { Text("Đóng", color = VC.text2) } },
    )
}
