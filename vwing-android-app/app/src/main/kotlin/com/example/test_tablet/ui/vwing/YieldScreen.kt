package com.example.test_tablet.ui.vwing

import androidx.compose.material.icons.filled.Insights
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.Dates
import com.example.test_tablet.data.vwing.EventType
import com.example.test_tablet.data.vwing.yieldCalc
import kotlin.math.abs

@Composable
fun YieldScreen(c: VCtx) {
    val d = c.d
    val g = d.garden
    val y = d.yieldCalc()
    var showSale by remember { mutableStateOf(false) }

    PageHeader(Icons.Filled.Insights, "DỰ BÁO VỤ MÙA", "Ước tính để lập kế hoạch tốt hơn.", "Tách bạch số trái quan sát, sản lượng ước tính, thực thu và tiền đã bán. Mọi con số truy được về giả định.") {
        VButton("Ghi lần thu hoạch", { c.nav.push("log") }, primary = false, icon = Icons.Filled.Add)
        VButton("Xem giá thị trường", { c.nav.push("market") }, primary = false)
        VButton("Ghi lần bán", { showSale = true }, primary = false, icon = Icons.Filled.Add, enabled = com.example.test_tablet.data.vwing.Access.canWriteSales)
    }

    val left: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // 1) Phương pháp
            VCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Eyebrow("1 · SẢN LƯỢNG ƯỚC TÍNH"); Tag("ƯỚC TÍNH", VC.amber) }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Step("SỐ TRÁI ĐÃ ĐẾM", fmtInt(y.fruits), "${y.countedTrees}/${y.totalTrees} cây", Modifier.weight(1f))
                    Text("×", color = VC.muted, fontSize = 20.sp)
                    Step("KG/TRÁI", y.weightKg?.let { fmtNum(it) } ?: "—", "giả định", Modifier.weight(1f))
                    Text("×", color = VC.muted, fontSize = 20.sp)
                    Step("THƯƠNG PHẨM", "${fmtNum(y.ratio * 100, 0)}%", "giả định", Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Text(y.estKg?.let { "= ${fmtKg(it)} (≈ ${fmtNum(it / 1000.0)} tấn)" } ?: "Chưa đủ dữ liệu: nhập khối lượng trung bình mỗi trái ở cột giả định.",
                    color = if (y.estKg != null) VC.lime else VC.amber, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                if (y.totalTrees - y.countedTrees > 0)
                    Text("${y.totalTrees - y.countedTrees} cây chưa đếm trái nên chưa nằm trong tổng. Cây chưa đếm không được tính là 0 trái.", color = VC.amber, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            }
            // 2) Doanh thu & kịch bản
            VCard(Modifier.fillMaxWidth()) {
                Eyebrow("2 · DOANH THU THAM KHẢO")
                if (y.revenue != null) {
                    Text(fmtVnd(y.revenue), color = VC.lime, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 6.dp))
                    Text("${fmtKg(y.estKg!!)} × ${fmtVnd(y.price!!)}/kg", color = VC.text2, fontSize = 14.sp)
                } else Text("Chưa đủ giả định (khối lượng/trái và giá).", color = VC.amber, fontSize = 15.sp, modifier = Modifier.padding(vertical = 8.dp))
                val age = g.priceDate?.let { Dates.daysBetween(it, Dates.today()) }
                Text("Giá: ${g.priceSource.ifBlank { "chưa ghi nguồn" }}" + (if (g.priceRegion.isNotBlank()) " · ${g.priceRegion}" else "") + (if (g.priceGrade.isNotBlank()) " · ${g.priceGrade}" else "") +
                    " · " + (g.priceDate?.let { "cập nhật ${Dates.fmt(it)} ($age ngày trước)" } ?: "chưa ghi ngày"),
                    color = if (age == null || age > 30) VC.amber else VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                Text("Kết quả chưa trừ chi phí, hao hụt, phân loại chất lượng hay biến động giá; doanh thu không phải lợi nhuận.", color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                SectionLabel("Kịch bản (±${fmtNum(g.spreadPct, 0)}% khối lượng/trái và giá)")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    y.scenarios.forEach { s ->
                        Column(Modifier.weight(1f).background(VC.elevated, androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).padding(10.dp)) {
                            Text(s.label.uppercase(), color = VC.text2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(s.kg?.let { fmtKg(it) } ?: "—", color = VC.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(s.revenue?.let { fmtVnd(it) } ?: "—", color = VC.mint, fontSize = 14.sp)
                        }
                    }
                }
                Text("Biên độ do bạn đặt ở cột giả định; không phải dự báo thống kê.", color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
            // 3) Thực thu
            VCard(Modifier.fillMaxWidth()) {
                Eyebrow("3 · THỰC THU SO VỚI ƯỚC TÍNH")
                val harvests = d.events.filter { it.type == EventType.HARVEST }.sortedByDescending { it.occurredAt }
                Text(if (y.actualKg > 0) "Đã thu ${fmtKg(y.actualKg)}" else "Chưa có ghi nhận thu hoạch", color = VC.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                if (y.varianceKg != null && y.estKg != null && y.estKg > 0) {
                    val pct = y.varianceKg / y.estKg * 100
                    Text("Chênh lệch ${if (y.varianceKg >= 0) "+" else "−"}${fmtKg(abs(y.varianceKg))} (${if (pct >= 0) "+" else "−"}${fmtNum(abs(pct), 1)}%) so với ước tính ${fmtKg(y.estKg)}.", color = VC.amber, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
                    Text("Truy về giả định: khối lượng ${y.weightKg?.let { fmtNum(it) } ?: "—"} kg/trái · thương phẩm ${fmtNum(y.ratio * 100, 0)}% · ${y.countedTrees}/${y.totalTrees} cây đã đếm trái.", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                } else if (y.estKg == null) Text("Cần ước tính để so sánh.", color = VC.muted, fontSize = 14.sp)
                harvests.take(5).forEach { e -> Text("${Dates.fmt(e.occurredAt)} · ${targetLabel(d, e)} · ${e.amountKg?.let { fmtKg(it) } ?: "chưa ghi kg"}", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 3.dp)) }
            }
            // 4) Đã bán
            VCard(Modifier.fillMaxWidth()) {
                Eyebrow("4 · TIỀN ĐÃ BÁN")
                Text(if (d.sales.isEmpty()) "Chưa có lần bán nào" else "${fmtVnd(y.soldAmount)} · ${fmtKg(y.soldKg)}", color = VC.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                d.sales.sortedByDescending { it.date }.forEach { s ->
                    Text("${Dates.fmt(s.date)} · ${fmtKg(s.kg)} × ${fmtVnd(s.pricePerKg)}/kg = ${fmtVnd(s.kg * s.pricePerKg)}" + (if (s.note.isNotBlank()) " · ${s.note}" else ""), color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Text("Doanh thu bán được, chưa trừ chi phí.", color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
    val right: @Composable (Modifier) -> Unit = { m -> AssumptionsCard(c, m) }
    if (c.wide) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { left(Modifier.weight(1.5f)); right(Modifier.weight(1f)) }
    else Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { left(Modifier); right(Modifier) }

    if (showSale) SaleDialog(c) { showSale = false }
}

@Composable
private fun Step(title: String, value: String, sub: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = VC.muted, fontSize = 13.sp, letterSpacing = .5.sp, fontWeight = FontWeight.Bold)
        Text(value, color = VC.mint, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(sub, color = VC.text2, fontSize = 13.sp)
    }
}

@Composable
private fun AssumptionsCard(c: VCtx, modifier: Modifier) {
    val g = c.d.garden
    var weight by remember(g) { mutableStateOf(g.avgWeightKg?.toString() ?: "") }
    var ratio by remember(g) { mutableStateOf((g.commercialRatio * 100).toString().removeSuffix(".0")) }
    var spread by remember(g) { mutableStateOf(g.spreadPct.toString().removeSuffix(".0")) }
    var price by remember(g) { mutableStateOf(g.pricePerKg?.toString()?.removeSuffix(".0") ?: "") }
    var source by remember(g) { mutableStateOf(g.priceSource) }
    var region by remember(g) { mutableStateOf(g.priceRegion) }
    var grade by remember(g) { mutableStateOf(g.priceGrade) }
    var date by remember(g) { mutableStateOf(g.priceDate?.let { Dates.fmt(it) } ?: "") }
    var err by remember { mutableStateOf<String?>(null) }

    VCard(modifier) {
        Eyebrow("DỮ LIỆU ĐẦU VÀO")
        Text("Giả định của vườn", color = VC.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VField(weight, { weight = it; err = null }, "Khối lượng trung bình (kg/trái)", number = true)
            VField(ratio, { ratio = it; err = null }, "Tỷ lệ thương phẩm (%)", number = true)
            VField(spread, { spread = it; err = null }, "Biên độ kịch bản (±%)", number = true)
            VField(price, { price = it; err = null }, "Giá thu mua tham khảo (₫/kg)", number = true)
            VField(source, { source = it }, "Nguồn giá")
            VField(region, { region = it }, "Khu vực giá")
            VField(grade, { grade = it }, "Loại hàng")
            VField(date, { date = it; err = null }, "Ngày cập nhật giá (dd/MM/yyyy)")
            err?.let { Text(it, color = VC.red, fontSize = 14.sp) }
            VButton("Lưu giả định", {
                val w = if (weight.isBlank()) null else parseNum(weight)
                val r = parseNum(ratio); val sp = parseNum(spread)
                val p = if (price.isBlank()) null else parseNum(price)
                val dt = if (date.isBlank()) null else Dates.parse(date)
                when {
                    weight.isNotBlank() && (w == null || w <= 0) -> err = "Khối lượng/trái phải lớn hơn 0."
                    r == null || r < 0 || r > 100 -> err = "Tỷ lệ thương phẩm từ 0 đến 100%."
                    sp == null || sp < 0 || sp > 90 -> err = "Biên độ từ 0 đến 90%."
                    price.isNotBlank() && (p == null || p < 0) -> err = "Giá không hợp lệ."
                    date.isNotBlank() && dt == null -> err = "Ngày không hợp lệ (dd/MM/yyyy)."
                    else -> { c.store.updateGarden(g.copy(avgWeightKg = w, commercialRatio = r / 100.0, spreadPct = sp, pricePerKg = p,
                        priceSource = source.trim(), priceRegion = region.trim(), priceGrade = grade.trim(), priceDate = dt)); c.toast("Đã lưu giả định. Các con số được tính lại.") }
                }
            }, Modifier.fillMaxWidth())
            Text("Mở Giá thị trường để đối chiếu nguồn công khai. Giá của vườn chỉ thay đổi khi bạn xác nhận chọn giá hoặc lưu ở đây.", color = VC.muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SaleDialog(c: VCtx, onDismiss: () -> Unit) {
    var date by remember { mutableStateOf(Dates.fmt(Dates.now())) }
    var kg by remember { mutableStateOf("") }
    var price by remember { mutableStateOf(c.d.garden.pricePerKg?.toString()?.removeSuffix(".0") ?: "") }
    var note by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = VC.card,
        title = { Text("Ghi lần bán", color = VC.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VField(date, { date = it }, "Ngày bán (dd/MM/yyyy)")
                VField(kg, { kg = it }, "Khối lượng bán (kg) *", number = true)
                VField(price, { price = it }, "Giá bán thực tế (₫/kg) *", number = true)
                VField(note, { note = it }, "Ghi chú (người mua…)")
                err?.let { Text(it, color = VC.red, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            VButton("Lưu", {
                val dt = Dates.parse(date); val k = parseNum(kg); val p = parseNum(price)
                if (dt == null) err = "Ngày không hợp lệ." else if (k == null || p == null) err = "Nhập khối lượng và giá."
                else { val r = c.store.addSale(dt, k, p, note); if (r.ok) { c.toast("Đã ghi lần bán."); onDismiss() } else err = r.message }
            })
        },
        dismissButton = { TextButton(onDismiss) { Text("Đóng", color = VC.text2) } },
    )
}
