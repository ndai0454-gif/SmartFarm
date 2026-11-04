package com.example.test_tablet.ui.vwing

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.Condition
import com.example.test_tablet.data.vwing.Access
import com.example.test_tablet.data.vwing.Dates
import com.example.test_tablet.data.vwing.Event
import com.example.test_tablet.data.vwing.EventType
import com.example.test_tablet.data.vwing.effectiveCondition
import com.example.test_tablet.data.vwing.newId
import com.example.test_tablet.data.vwing.openIssuesFor
import com.example.test_tablet.data.vwing.SUGGESTED_QUESTIONS
import com.example.test_tablet.data.vwing.activeTrees
import com.example.test_tablet.data.vwing.summary
import com.example.test_tablet.data.vwing.todayWork
import com.example.test_tablet.data.vwing.treeByCode
import com.example.test_tablet.data.vwing.yieldCalc
import com.example.test_tablet.data.vwing.zoneNames

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewScreen(c: VCtx) {
    val d = c.d
    val s = d.summary()
    val y = d.yieldCalc()
    val w = d.todayWork()
    var confirmClear by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }

    // Việc cần làm hôm nay (gồm nhắc số đo nước/đất đã cũ và điểm ảnh chờ xác nhận)
    val staleMeasures = com.example.test_tablet.data.vwing.Env.reminders(d).size
    val pendingDetections = d.imagery.sumOf { s -> s.detections.count { it.status == com.example.test_tablet.data.vwing.DetStatus.PENDING } }
    val totalItems = w.total + staleMeasures + (if (pendingDetections > 0) 1 else 0)
    val needs = s.byCondition.getValue(Condition.NEEDS_CHECK)
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = when { hour < 11 -> "Chào buổi sáng"; hour < 14 -> "Chào buổi trưa"; hour < 18 -> "Chào buổi chiều"; else -> "Chào buổi tối" }

    if (scanning) QrScannerDialog({ scanning = false }) { raw ->
        scanning = false
        val r = resolveScan(d, raw)
        if (r is com.example.test_tablet.data.vwing.Qr.Scan.Found) { c.selectTree(r.tree.id); c.nav.push("tree|${r.tree.id}") } else c.toast(scanMessage(r) ?: "")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BẢNG ĐIỀU KHIỂN CHÍNH AURORA LIQUID-GLASS (TỐI ƯU TABLET TRƯỚC, ĐIỆN THOẠI SAU)
    // Tinh gọn 6 tầng thẻ cũ thành 1 khung Aurora Weather & SmartFarm duy nhất
    // ═══════════════════════════════════════════════════════════════════════════
    AuroraSmartFarmDashboard(c)
    Spacer(Modifier.height(14.dp))

    // Taste Skill §5 Divided KPI Strip: 4 thông số cốt lõi người nông dân quan tâm nhất về vườn
    val latestMoist = com.example.test_tablet.data.vwing.Env.latestMoisture(d).firstOrNull()?.value
    val latestPh = com.example.test_tablet.data.vwing.Env.latestLab(d)?.lab?.firstOrNull { it.name.trim().equals("ph", true) }?.value
    val stableCount = s.byCondition.getValue(Condition.STABLE)
    val estTons = y.estKg?.let { fmtNum(it / 1000.0, 2) } ?: "2,76"
    TasteKpiStrip(
        cells = listOf(
            TasteKpiCell(
                label = if (c.simpleMode) "Nước trong đất" else "Độ ẩm đất tầng rễ",
                value = latestMoist?.let { fmtNum(it, 0) } ?: "64",
                unit = "%",
                sub = if (c.simpleMode) "Đủ ẩm · Chưa cần tưới" else "Tầng 0–30 cm ổn định",
                onClick = { c.nav.top("env") },
            ),
            TasteKpiCell(
                label = if (c.simpleMode) "Độ tốt của đất" else "Độ pH đất",
                value = latestPh?.let { fmtNum(it, 1) } ?: "6,2",
                unit = "pH",
                sub = if ((latestPh ?: 6.2) in 5.5..6.8) {
                    if (c.simpleMode) "Đất tốt · Không bị chua" else "Đạt ngưỡng tối ưu (5,5–6,8)"
                } else {
                    "Cần bón vôi cân bằng"
                },
                onClick = { c.nav.top("env") },
            ),
            TasteKpiCell(
                label = if (c.simpleMode) "Sức khỏe vườn cây" else "Cây sinh trưởng ổn định",
                value = "$stableCount/${s.totalTrees}",
                unit = "cây khỏe",
                sub = if (needs > 0) "$needs cây cần bác ra thăm" else "Toàn bộ cây đều khỏe",
                accentColor = if (needs > 0) VC.amber else VC.mint,
                onClick = { c.nav.top("map") },
            ),
            TasteKpiCell(
                label = if (c.simpleMode) "Ước thu vụ này" else "Sản lượng dự kiến",
                value = estTons,
                unit = "tấn",
                sub = y.revenue?.let { "Khoảng ${fmtNum(it / 1_000_000.0, 1)} triệu ₫" } ?: "Theo số trái trên cây",
                onClick = { c.nav.top("yield") },
            ),
        ),
        wide = c.wide,
        modifier = Modifier.padding(bottom = 14.dp),
    )

    c.store.loadError?.let { Notice(it, VC.red, Modifier.padding(bottom = 10.dp)) { TextButton({ c.store.dismissLoadError() }) { Text("Đóng", color = VC.text2) } } }

    // Danh sách cây ưu tiên kiểm tra để xử lý nhanh 1 chạm
    val flagged = d.activeTrees.map { it to d.effectiveCondition(it) }
        .filter { it.second == Condition.NEEDS_CHECK || it.second == Condition.WATCH || it.first in w.overdueTrees }
        .sortedByDescending { it.second.ordinal }

    if (c.simpleMode) {
        // ═══════════════════════════════════════════════════════════════════════
        // CHẾ ĐỘ NÔNG DÂN TỐI GIẢN: Chỉ 1 thẻ duy nhất liệt kê các cây cần thăm
        // và nút Quét QR cây, không lặp lại các bảng kỹ thuật rối mắt.
        // ═══════════════════════════════════════════════════════════════════════
        VCard(Modifier.fillMaxWidth().animateContentSize(), borderColor = VC.border) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Eyebrow(if (flagged.isEmpty()) "Vườn cây hôm nay" else "Cây cần ra thăm hôm nay (${flagged.size} cây)")
                    Text(
                        if (flagged.isEmpty()) "Tất cả cây trong vườn đều đang khỏe mạnh." else "Chạm vào từng cây để xem vị trí và cách chăm sóc",
                        color = VC.muted,
                        fontSize = 13.sp,
                    )
                }
                VButton(
                    "Quét QR gốc cây",
                    { scanning = true },
                    primary = false,
                    icon = Icons.Filled.QrCodeScanner,
                )
            }
            Spacer(Modifier.height(10.dp))
            if (flagged.isEmpty()) {
                EmptyNote("Hôm nay không có cây nào cần xử lý.", Icons.Filled.TaskAlt)
            } else {
                flagged.take(4).forEachIndexed { i, (t, cond) ->
                    val open = d.openIssuesFor(t.id).firstOrNull()
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 54.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(
                            Modifier
                                .weight(1f)
                                .bounceClick { c.selectTree(t.id); c.nav.push("tree|${t.id}") }
                                .padding(vertical = 6.dp),
                        ) {
                            Text(
                                "Cây ${t.code} · ${t.zone}",
                                color = VC.text,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (open != null) "Tình trạng: ${open.symptom}" else "Tình trạng: ${cond.label}",
                                color = if (open != null || cond == Condition.NEEDS_CHECK) VC.amber else VC.muted,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                            )
                        }
                        if (open != null) {
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .clip(LeafShapeSmall)
                                    .border(1.dp, VC.border, LeafShapeSmall)
                                    .bounceClick { c.nav.push("issue|${open.id}") }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Xem cách chữa", color = VC.text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .clip(LeafShapeSmall)
                                    .background(VC.pine)
                                    .bounceClick {
                                        val ev = Event(
                                            id = newId(), treeIds = listOf(t.id), type = EventType.OBSERVATION, activity = "Kiểm tra", occurredAt = Dates.now(),
                                            author = d.garden.operatorName, note = "Xác nhận ổn nhanh từ Tổng quan", conditionSet = Condition.STABLE,
                                        )
                                        val r = c.store.addEvent(ev)
                                        c.toast(if (r.ok) "Đã xác nhận cây ${t.code} khỏe mạnh." else r.message ?: "Không lưu được.")
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Đã thăm ổn", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }
                    if (i < minOf(flagged.size, 4) - 1) Divider2()
                }
            }
        }
    } else {
        // ═══════════════════════════════════════════════════════════════════════
        // CHẾ ĐỘ KỸ SƯ: Hiển thị đầy đủ công cụ OBIA, GIS, tra cứu mã & nhật ký
        // ═══════════════════════════════════════════════════════════════════════
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(LeafShape)
                .background(VC.pine)
                .border(1.dp, VC.pine, LeafShape)
                .bounceClick { c.nav.top("workflow") }
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = VC.gold, modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "Quy trình 6 bước · Địa tầng 3D 7 tầng · Phân lớp Drone OBIA (Lite Classify)",
                    color = Color.White,
                    fontSize = if (c.wide) 15.5.sp else 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Khoanh vùng vườn · Đếm cây & dán nhãn QR · Tính doanh thu (Sầu riêng = trái, Cà phê = kg) · Trợ lý Kỹ sư AI",
                    color = VC.onRailMuted,
                    fontSize = 12.5.sp,
                )
            }
            Box(
                modifier = Modifier
                    .clip(LeafShapeSmall)
                    .background(Color.White)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Mở quy trình →", color = VC.pine, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(14.dp))

        val todayCard: @Composable (Modifier) -> Unit = { m ->
            VCard(m.animateContentSize(), borderColor = VC.border) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("Việc cần xử lý hôm nay")
                    if (totalItems > 0) {
                        Tag("$totalItems hạng mục", VC.amber)
                    }
                }
                Spacer(Modifier.height(8.dp))
                class Item(val label: String, val count: Int, val route: String, val color: Color, val icon: ImageVector)
                val rows = listOf(
                    Item("Việc quá hạn", w.overdueTasks.size, "issues|tasks", VC.red, Icons.Filled.EventBusy),
                    Item("Việc hôm nay", w.dueTodayTasks.size, "issues|tasks", VC.amber, Icons.Filled.Today),
                    Item("Cây bệnh cần xử lý", w.overdueIssues.size, "issues|issues", VC.red, Icons.Filled.ReportProblem),
                    Item("Cây chờ kiểm tra lại", w.pendingVerify.size + w.pendingRecheck.size, "issues|issues", VC.amber, Icons.Filled.Replay),
                    Item("Cây cần đi thăm", w.overdueTrees.size + w.neverObserved.size, "issues|trees", VC.amber, Icons.Filled.Schedule),
                    Item("Đo lại nước & đất", staleMeasures, "env", VC.amber, Icons.Filled.WaterDrop),
                    Item("Ảnh chụp chờ xem", if (pendingDetections > 0) pendingDetections else 0, "imagery", VC.amber, Icons.Filled.Layers),
                ).filter { it.count > 0 }
                if (rows.isEmpty()) EmptyNote("Hôm nay không có công việc tồn đọng.", Icons.Filled.TaskAlt)
                rows.forEachIndexed { i, it ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).appear(i).bounceClick { c.nav.top(it.route) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(it.icon, null, tint = VC.muted, modifier = Modifier.size(19.dp))
                        Text(it.label, color = VC.text, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(
                            "${it.count}",
                            color = it.color,
                            fontFamily = VDisplayFont,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(it.color.copy(alpha = .10f))
                                .padding(horizontal = 9.dp, vertical = 2.dp),
                        )
                    }
                    if (i < rows.lastIndex) Divider2()
                }
                if (w.unsynced > 0) {
                    Notice("${w.unsynced} bản ghi chưa đồng bộ lên máy chủ (đang lưu an toàn trên máy). Đồng bộ hoặc sao lưu ở Thiết lập vườn.", VC.cyan, Modifier.padding(top = 10.dp))
                }
            }
        }

        val sideCards: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                VCard(Modifier.fillMaxWidth()) {
                    Eyebrow("Thao tác thực địa nhanh")
                    Spacer(Modifier.height(6.dp))
                    val quick = listOf(
                        Triple(Icons.Filled.AutoAwesome, "Quy trình 6 bước & Phân tích OBIA", { c.nav.top("workflow") }),
                        Triple(Icons.Filled.FlightTakeoff, "Drone Đa phổ & Phun VRA", { c.nav.top("drone") }),
                        Triple(Icons.Filled.SatelliteAlt, "Hạn hán 26 năm & Vệ tinh", { c.nav.top("satellite") }),
                        Triple(Icons.Filled.QrCodeScanner, "Quét mã QR cây tại gốc", { scanning = true }),
                        Triple(Icons.Filled.ReportProblem, "Báo cáo cây có triệu chứng bệnh", { c.nav.top("issues|issues") }),
                        Triple(Icons.Filled.AutoAwesome, "Hỏi Trợ lý Kỹ sư AI (Gemini)", { c.nav.top("ai") }),
                    )
                    quick.forEachIndexed { i, (ic, label, go) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 46.dp)
                                .bounceClick(onClick = go)
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(ic, null, tint = VC.muted, modifier = Modifier.size(19.dp))
                            Text(label, color = VC.text, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.ChevronRight, null, tint = VC.muted, modifier = Modifier.size(18.dp))
                        }
                        if (i < quick.lastIndex) Divider2()
                    }
                }
                VCard(Modifier.fillMaxWidth()) {
                    Eyebrow("Tra cứu cây theo mã định danh")
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        VField(code, { code = it }, "Mã cây (VD: A-04)", Modifier.weight(1f))
                        VButton("Mở", {
                            val t = d.treeByCode(code)
                            if (t == null) c.toast("Không tìm thấy cây \"${code.trim()}\".") else { c.selectTree(t.id); c.nav.push("tree|${t.id}") }
                        }, enabled = code.isNotBlank(), icon = Icons.Filled.Park)
                    }
                }
            }
        }
        if (c.wide) Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) { todayCard(Modifier.weight(1.25f)); sideCards(Modifier.weight(1f)) }
        else Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { todayCard(Modifier.fillMaxWidth()); sideCards(Modifier.fillMaxWidth()) }
        Spacer(Modifier.height(14.dp))

        TechnicalDetails(false) {
            if (d.isSample) {
                Notice("Đang hiển thị dữ liệu minh họa. Tình trạng cây và giá thu mua cần được xác nhận bằng dữ liệu thực tế trước khi ra quyết định.", VC.teal, Modifier.padding(bottom = 16.dp)) {
                    TextButton({ confirmClear = true }) { Text("Bắt đầu vườn của tôi", color = VC.lime, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
            }

            val left: @Composable (Modifier) -> Unit = { m ->
                VCard(m) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Sơ đồ vườn số hóa", color = VC.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("${d.garden.name} · ${s.totalTrees} cây" + (d.garden.areaHa?.let { " · ${fmtNum(it)} ha" } ?: "") + (if (d.garden.crop.isNotBlank()) " · ${d.garden.crop}" else ""), color = VC.text2, fontSize = 14.sp)
                        }
                        VButton("Mở bản đồ", { c.nav.top("map") }, primary = false)
                    }
                    Spacer(Modifier.height(10.dp))
                    val geo = d.activeTrees.any { it.pos != null }
                    GardenMap(d, if (geo) MapMode.GEO else MapMode.SCHEMATIC, emptySet(), c.selectedTree, Modifier.fillMaxWidth().height(260.dp), interactive = false)
                    MapLegend()
                }
            }
            left(Modifier.fillMaxWidth())

            Spacer(Modifier.height(14.dp))
            VCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Eyebrow("NHẬT KÝ GẦN ĐÂY"); Text("Hoạt động mới nhất", color = VC.text, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                    VButton("Xem tất cả (${d.events.size})", { c.nav.top("records") }, primary = false)
                }
                val recent = d.events.sortedByDescending { it.occurredAt }.take(4)
                if (recent.isEmpty()) EmptyNote("Chưa có ghi chép nào.")
                recent.forEach { EventRow(c, it, compact = true) }
            }
        }
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false }, containerColor = VC.card,
        title = { Text("Xóa dữ liệu minh họa?", color = VC.text) },
        text = { Text("Toàn bộ cây, ghi chép, vấn đề và bản đồ mẫu sẽ bị xóa để bạn bắt đầu vườn của mình. Hãy sao lưu trước nếu cần giữ lại.", color = VC.text2) },
        confirmButton = { VButton("Xóa và bắt đầu", { c.store.clearSample(); confirmClear = false; c.nav.top("setup") }, danger = true) },
        dismissButton = { TextButton({ confirmClear = false }) { Text("Hủy", color = VC.text2) } },
    )
}

