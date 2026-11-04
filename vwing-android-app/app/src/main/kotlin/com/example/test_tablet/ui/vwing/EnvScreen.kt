package com.example.test_tablet.ui.vwing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.Calibration
import com.example.test_tablet.data.vwing.Dates
import com.example.test_tablet.data.vwing.Env
import com.example.test_tablet.data.vwing.FeatureKind
import com.example.test_tablet.data.vwing.GardenData
import com.example.test_tablet.data.vwing.GeoPoint
import com.example.test_tablet.data.vwing.LabValue
import com.example.test_tablet.data.vwing.Measure
import com.example.test_tablet.data.vwing.MeasureKind
import com.example.test_tablet.data.vwing.MeasureOrigin
import com.example.test_tablet.data.vwing.newId
import com.example.test_tablet.data.vwing.treeByCode
import com.example.test_tablet.data.vwing.treeById
import com.example.test_tablet.data.vwing.zoneNames

private fun scopeText(d: GardenData, m: Measure): String = when {
    m.treeIds.isNotEmpty() -> m.treeIds.mapNotNull { d.treeById(it)?.code }.joinToString(", ")
    m.scopeZone != null -> m.scopeZone
    else -> "Toàn vườn"
}

private fun valueText(m: Measure): String =
    if (m.kind == MeasureKind.SOIL_LAB) m.lab.joinToString(" · ") { "${it.name} ${fmtNum(it.value, 2)}${if (it.unit.isNotBlank()) " ${it.unit}" else ""}" }
    else (m.value?.let { fmtNum(it, 1) + " " + m.unit } ?: "—")

private fun ageText(m: Measure): String = Env.ageDays(m).let { if (it <= 0) "hôm nay" else "$it ngày trước" }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EnvScreen(c: VCtx) {
    val d = c.d
    var showAdd by remember { mutableStateOf(false) }
    var showSpot by remember { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("Tất cả") }
    var detail by remember { mutableStateOf<String?>(null) }

    PageHeader(
        Icons.Filled.WaterDrop,
        "NƯỚC, ĐẤT & THỜI TIẾT", "Số đo có nguồn và tuổi dữ liệu",
        "Mưa đã đo tách khỏi dự báo; mm mưa và lít nước tưới không cộng với nhau; thiếu dữ liệu không bao giờ thành 0. Chưa tự tính lượng tưới.",
    ) {
        VButton("Ghi số đo", { showAdd = true })
        VButton("Ghi chú vị trí đọng nước / khô hạn", { showSpot = true }, primary = false)
    }

    val r7 = Env.rain(d, 7); val r30 = Env.rain(d, 30)
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Kpi("Mưa đã đo · 7 ngày", r7.totalMm?.let { fmtNum(it, 1) } ?: "—", if (r7.totalMm != null) "mm" else "", if (r7.records == 0) "Chưa có số đo" else "${r7.coveredDays}/7 ngày có số đo · ${r7.missingDays} ngày thiếu", Modifier.weight(1f), warn = r7.records > 0 && r7.missingDays > 0)
        Kpi("Mưa đã đo · 30 ngày", r30.totalMm?.let { fmtNum(it, 1) } ?: "—", if (r30.totalMm != null) "mm" else "", if (r30.records == 0) "Chưa có số đo" else "${r30.coveredDays}/30 ngày có số đo", Modifier.weight(1f), warn = r30.records > 0 && r30.missingDays > 0)
    }
    Spacer(Modifier.height(10.dp))
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Kpi("Dự báo mưa · 7 ngày tới", r7.forecastMm?.let { fmtNum(it, 1) } ?: "—", if (r7.forecastMm != null) "mm" else "", "Dự báo, chưa phải số đo", Modifier.weight(1f))
        Kpi("Nước tưới · 30 ngày", Env.irrigationLitres(d, 30)?.let { fmtInt(it.toInt()) } ?: "—", if (Env.irrigationLitres(d, 30) != null) "lít" else "", "Đơn vị lít, không cộng với mm mưa", Modifier.weight(1f))
    }
    Env.reminders(d).forEach { Notice("Nhắc kiểm tra: $it.", VC.amber, Modifier.padding(top = 10.dp)) }
    Spacer(Modifier.height(14.dp))

    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("ĐỘ ẨM ĐẤT GẦN NHẤT")
        val moist = Env.latestMoisture(d)
        if (moist.isEmpty()) EmptyNote("Chưa có số đo độ ẩm đất.")
        moist.forEach { m ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(scopeText(d, m), color = VC.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(valueText(m), color = VC.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Tag(ageText(m), if (Env.isStale(m)) VC.amber else VC.text2)
                Tag(m.origin.label, VC.cyan)
            }
            Divider2()
        }
    }
    Spacer(Modifier.height(14.dp))

    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("MẪU ĐẤT GẦN NHẤT")
        val lab = Env.latestLab(d)
        if (lab == null) EmptyNote("Chưa có kết quả phân tích mẫu đất.")
        else {
            Text(valueText(lab), color = VC.text, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
            Text("${lab.labName} · ${Dates.fmt(lab.measuredAt)} (${ageText(lab)}) · ${scopeText(d, lab)}", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            lab.point?.let { Text("Vị trí lấy mẫu: ${"%.6f, %.6f".format(it.lat, it.lng)}", color = VC.mint, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp)) }
            if (Env.isStale(lab)) Tag("Kết quả đã hơn 1 năm", VC.amber, Modifier.padding(top = 6.dp))
        }
    }
    Spacer(Modifier.height(14.dp))

    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("LỊCH SỬ SỐ ĐO")
        ChipRow(listOf("Tất cả") + MeasureKind.values().map { it.label }, filter, { it }, { filter = it }, Modifier.padding(vertical = 8.dp))
        val list = d.measures.filter { filter == "Tất cả" || it.kind.label == filter }.sortedByDescending { it.measuredAt }
        if (list.isEmpty()) EmptyNote("Không có số đo.")
        list.take(60).forEach { m ->
            Column(Modifier.fillMaxWidth().clickable { detail = m.id }.padding(vertical = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Dates.fmt(m.measuredAt), color = VC.mint, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Tag(m.kind.label, VC.teal)
                    Text(valueText(m), color = VC.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (m.origin == MeasureOrigin.FORECAST) Tag("Dự báo", VC.amber) else Tag(m.origin.label, VC.cyan)
                }
                Text("${scopeText(d, m)}${if (m.periodDays > 1) " · bao phủ ${m.periodDays} ngày" else ""}${if (m.note.isNotBlank()) " · ${m.note}" else ""}", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Divider2()
        }
    }
    Spacer(Modifier.height(14.dp))

    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("GHI CHÚ ĐỌNG NƯỚC / KHÔ HẠN TRÊN BẢN ĐỒ")
        val spots = d.features.filter { it.kind == FeatureKind.WATERLOG || it.kind == FeatureKind.DRY }
        if (spots.isEmpty()) EmptyNote("Chưa có ghi chú. Các điểm này hiện trên bản đồ vườn.")
        spots.sortedByDescending { it.importedAt }.forEach { f ->
            Column(Modifier.padding(vertical = 6.dp)) {
                Text(f.name, color = VC.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(f.points.firstOrNull()?.let { "%.6f, %.6f".format(it.lat, it.lng) } + if (f.note.isNotBlank()) " · ${f.note}" else "", color = VC.text2, fontSize = 14.sp)
            }
        }
    }

    if (showAdd) AddMeasureDialog(c) { showAdd = false }
    if (showSpot) SpotDialog(c) { showSpot = false }
    detail?.let { id -> d.measures.firstOrNull { it.id == id }?.let { m ->
        AlertDialog(
            onDismissRequest = { detail = null }, containerColor = VC.card,
            title = { Text("${m.kind.label} · ${Dates.fmt(m.measuredAt)}", color = VC.text) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(valueText(m), color = VC.text, fontWeight = FontWeight.Bold)
                    Text("Nguồn: ${m.origin.label}" + if (m.calibration != Calibration.NA) " · hiệu chuẩn: ${m.calibration.label}" else "", color = VC.text2, fontSize = 14.sp)
                    Text("Phạm vi: ${scopeText(d, m)} · bao phủ ${m.periodDays} ngày · cách đây ${ageText(m)}", color = VC.text2, fontSize = 14.sp)
                    if (m.labName.isNotBlank()) Text("Phòng thí nghiệm: ${m.labName}", color = VC.text2, fontSize = 14.sp)
                    m.point?.let { Text("Vị trí: ${"%.6f, %.6f".format(it.lat, it.lng)}", color = VC.text2, fontSize = 14.sp) }
                    if (m.note.isNotBlank()) Text(m.note, color = VC.text2, fontSize = 14.sp)
                    Text("Ghi bởi ${m.author} · phiên bản ${m.version}", color = VC.muted, fontSize = 13.sp)
                    SyncTag(m.sync)
                }
            },
            confirmButton = { TextButton({ detail = null }) { Text("Đóng", color = VC.text2) } },
        )
    } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddMeasureDialog(c: VCtx, onDismiss: () -> Unit) {
    val d = c.d
    var kind by remember { mutableStateOf(MeasureKind.RAIN) }
    var origin by remember { mutableStateOf(MeasureOrigin.MEASURED) }
    var value by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(Dates.fmt(Dates.now())) }
    var period by remember { mutableStateOf("1") }
    var scopeMode by remember { mutableStateOf("Toàn vườn") }
    var zone by remember { mutableStateOf(d.zoneNames().firstOrNull() ?: "") }
    var codes by remember { mutableStateOf("") }
    var calib by remember { mutableStateOf(Calibration.UNKNOWN) }
    var lat by remember { mutableStateOf("") }; var lng by remember { mutableStateOf("") }
    var labName by remember { mutableStateOf("") }
    var labText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val locate = rememberLocationFetcher(c) { la, lo, acc -> lat = "%.6f".format(la); lng = "%.6f".format(lo); acc?.let { c.toast("Sai số thiết bị báo ±${it.toInt()} m.") } }

    val origins = when (kind) {
        MeasureKind.RAIN -> listOf(MeasureOrigin.MEASURED, MeasureOrigin.SENSOR, MeasureOrigin.FORECAST)
        MeasureKind.SOIL_LAB -> listOf(MeasureOrigin.LAB)
        else -> listOf(MeasureOrigin.MEASURED, MeasureOrigin.SENSOR)
    }
    if (origin !in origins) origin = origins.first()

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = VC.card,
        title = { Text("Ghi số đo", color = VC.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipRow(MeasureKind.values().toList(), kind, { it.label }, { kind = it; err = null })
                ChipRow(origins, origin, { it.label }, { origin = it; err = null })
                if (kind == MeasureKind.SOIL_LAB) {
                    VField(labName, { labName = it; err = null }, "Phòng thí nghiệm / đơn vị phân tích *")
                    VField(labText, { labText = it; err = null }, "Chỉ tiêu (mỗi dòng: tên; giá trị; đơn vị) *", singleLine = false, minLines = 3, placeholder = "pH; 5,8;\nHữu cơ; 3,1; %\nĐạm tổng; 0,18; %")
                } else {
                    VField(value, { value = it; err = null }, "Giá trị (${kind.unit}) *", number = true)
                }
                VField(date, { date = it; err = null }, if (origin == MeasureOrigin.FORECAST) "Ngày dự báo (dd/MM/yyyy)" else "Ngày đo (dd/MM/yyyy) *")
                if (kind == MeasureKind.RAIN) VField(period, { period = it.filter(Char::isDigit); err = null }, "Số ngày số đo bao phủ (mưa tích lũy)", number = true)
                if (origin == MeasureOrigin.SENSOR) VPicker("Tình trạng hiệu chuẩn cảm biến *", listOf(Calibration.CALIBRATED, Calibration.UNCALIBRATED, Calibration.UNKNOWN).map { it.label }, calib.label, { l -> calib = Calibration.values().first { it.label == l } })
                if (kind != MeasureKind.SOIL_LAB) {
                    ChipRow(listOf("Toàn vườn", "Theo khu", "Theo cây"), scopeMode, { it }, { scopeMode = it; err = null })
                    if (scopeMode == "Theo khu") VPicker("Khu", d.zoneNames(), zone, { zone = it })
                    if (scopeMode == "Theo cây") VField(codes, { codes = it; err = null }, "Mã cây (cách nhau bằng dấu phẩy)", placeholder = "A-04, A-05")
                    if (kind == MeasureKind.RAIN && scopeMode != "Toàn vườn") Text("Số đo theo khu/cây không được cộng vào tổng mưa toàn vườn.", color = VC.muted, fontSize = 13.sp)
                }
                Text(if (kind == MeasureKind.SOIL_LAB) "Vị trí lấy mẫu *" else "Vị trí đo / đặt cảm biến (không bắt buộc)", color = VC.text2, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    VField(lat, { lat = it; err = null }, "Vĩ độ", Modifier.weight(1f), number = true)
                    VField(lng, { lng = it; err = null }, "Kinh độ", Modifier.weight(1f), number = true)
                    VButton("GPS", locate, primary = false)
                }
                VField(note, { note = it }, "Ghi chú", singleLine = false, minLines = 2)
                err?.let { Text(it, color = VC.red, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            VButton("Lưu số đo", {
                val dt = Dates.parse(date)
                val point = if (lat.isBlank() && lng.isBlank()) null else {
                    val la = parseNum(lat); val lo = parseNum(lng)
                    if (la == null || lo == null) { err = "Tọa độ không hợp lệ."; return@VButton }
                    GeoPoint(la, lo)
                }
                val v = if (kind == MeasureKind.SOIL_LAB) null else parseNum(value)
                if (kind != MeasureKind.SOIL_LAB && v == null) { err = "Nhập giá trị là số (không để trống)."; return@VButton }
                val lab = if (kind != MeasureKind.SOIL_LAB) emptyList() else labText.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
                    val p = line.split(';').map { it.trim() }
                    val x = p.getOrNull(1)?.let(::parseNum)
                    if (p.size < 2 || p[0].isBlank() || x == null) { err = "Chỉ tiêu \"$line\" sai dạng: tên; giá trị; đơn vị."; return@VButton }
                    LabValue(p[0], x, p.getOrElse(2) { "" })
                }
                val trees = if (scopeMode == "Theo cây" && kind != MeasureKind.SOIL_LAB) {
                    val found = codes.split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }.map { it to d.treeByCode(it) }
                    found.firstOrNull { it.second == null }?.let { err = "Không tìm thấy cây \"${it.first}\"."; return@VButton }
                    found.mapNotNull { it.second?.id }
                } else emptyList()
                if (dt == null) { err = "Ngày không hợp lệ."; return@VButton }
                val m = Measure(
                    newId(), kind, origin, v, if (kind == MeasureKind.SOIL_LAB) "" else kind.unit, dt + 12 * 3_600_000L,
                    periodDays = if (kind == MeasureKind.RAIN) (period.toIntOrNull() ?: 1) else 1,
                    scopeZone = if (scopeMode == "Theo khu" && kind != MeasureKind.SOIL_LAB) zone else null, treeIds = trees, point = point,
                    calibration = if (origin == MeasureOrigin.SENSOR) calib else Calibration.NA, labName = labName.trim(), lab = lab,
                    note = note.trim(), author = d.garden.operatorName,
                )
                val r = c.store.addMeasure(m)
                if (r.ok) { c.toast("Đã ghi số đo."); onDismiss() } else err = r.message
            })
        },
        dismissButton = { TextButton(onDismiss) { Text("Hủy", color = VC.text2) } },
    )
}

@Composable
private fun SpotDialog(c: VCtx, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf(FeatureKind.WATERLOG) }
    var lat by remember { mutableStateOf("") }; var lng by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val locate = rememberLocationFetcher(c) { la, lo, _ -> lat = "%.6f".format(la); lng = "%.6f".format(lo) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = VC.card,
        title = { Text("Ghi chú vị trí", color = VC.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipRow(listOf(FeatureKind.WATERLOG, FeatureKind.DRY), kind, { it.label }, { kind = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    VField(lat, { lat = it; err = null }, "Vĩ độ", Modifier.weight(1f), number = true)
                    VField(lng, { lng = it; err = null }, "Kinh độ", Modifier.weight(1f), number = true)
                    VButton("GPS", locate, primary = false)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    VField(code, { code = it }, "Hoặc lấy vị trí của cây", Modifier.weight(1f), placeholder = "A-04")
                    VButton("Dùng", {
                        val p = c.d.treeByCode(code)?.pos
                        if (p == null) c.toast("Cây chưa có vị trí hoặc không tồn tại.") else { lat = "%.6f".format(p.lat); lng = "%.6f".format(p.lng) }
                    }, primary = false, enabled = code.isNotBlank())
                }
                VField(note, { note = it }, "Ghi chú (ví dụ: đọng sau mưa lớn)", singleLine = false, minLines = 2)
                err?.let { Text(it, color = VC.red, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            VButton("Lưu", {
                val la = parseNum(lat); val lo = parseNum(lng)
                if (la == null || lo == null) { err = "Nhập tọa độ hoặc dùng GPS/vị trí cây."; return@VButton }
                val r = c.store.addSpotNote(kind, GeoPoint(la, lo), note)
                if (r.ok) { c.toast("Đã ghi chú. Xem trên bản đồ vườn."); onDismiss() } else err = r.message
            })
        },
        dismissButton = { TextButton(onDismiss) { Text("Hủy", color = VC.text2) } },
    )
}
