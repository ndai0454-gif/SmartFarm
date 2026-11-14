package com.example.test_tablet.ui.vwing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.Access

enum class VPeriod(val label: String, val days: Int) { TODAY("Hôm nay", 1), WEEK("Tuần", 7), MONTH("Tháng", 30), SEASON("Vụ mùa", 180) }

enum class VUnit(val label: String) { VND("VND"), TON("TẤN"), TREE("CÂY") }

/** Trạng thái giao diện dùng chung giữa tiêu đề (bộ chọn kỳ, đơn vị, tìm nhanh) và màn Tổng quan. */
object VUi {
    var period by mutableStateOf(VPeriod.MONTH)
    var unit by mutableStateOf(VUnit.VND)
    var searchOpen by mutableStateOf(false)
    var simpleMode by mutableStateOf(true)
    var country by mutableStateOf(AppCountry.VN)
    var countryPickerOpen by mutableStateOf(false)

    /** Người không xem được doanh thu (nhân viên, phi công) không bao giờ thấy đơn vị VND. */
    fun effectiveUnit(): VUnit = if (unit == VUnit.VND && !Access.canSeeSales) VUnit.TON else unit

    fun cycleUnit() {
        val all = VUnit.values().filter { it != VUnit.VND || Access.canSeeSales }
        unit = all[(all.indexOf(effectiveUnit()) + 1) % all.size]
    }
}

@Composable
fun PeriodSwitcher(modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(VC.elevated)
            .border(1.dp, VC.border, RoundedCornerShape(8.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val periodLabels = AppStrings.shell(VUi.country).periodLabels
        VPeriod.values().forEach { p ->
            val sel = p == VUi.period
            Box(
                Modifier
                    .heightIn(min = 34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (sel) VC.card else Color.Transparent)
                    .then(if (sel) Modifier.border(1.dp, VC.border, RoundedCornerShape(6.dp)) else Modifier)
                    .bounceClick { VUi.period = p }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    periodLabels.getOrElse(p.ordinal) { p.label },
                    color = if (sel) VC.text else VC.muted,
                    fontSize = 13.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun UnitPill(modifier: Modifier = Modifier) {
    Row(
        modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(VC.card)
            .border(1.dp, VC.border, RoundedCornerShape(8.dp))
            .bounceClick { VUi.cycleUnit() }
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(VUi.effectiveUnit().label, color = VC.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Icon(Icons.Filled.KeyboardArrowDown, "Đổi đơn vị", tint = VC.muted, modifier = Modifier.size(18.dp))
    }
}

/**
 * Taste Skill §2.3 Signature Element:
 * Thẻ trạng thái sức khỏe cây trồng ở đáy Sidebar với 20 vạch định lượng rõ ràng.
 */
@Composable
fun HealthBattery(stablePct: Int, modifier: Modifier = Modifier) {
    val lit = Math.round(stablePct.coerceIn(0, 100) / 100f * 20)
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(VC.card)
            .border(1.dp, VC.border, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Tỷ lệ cây ổn định", color = VC.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text("$stablePct%", color = VC.text, fontFamily = VDisplayFont, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.padding(top = 8.dp).fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(20) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i < lit) VC.lime else VC.elevated)
                )
            }
        }
    }
}

/** Biểu đồ ma trận viên thuốc: mỗi cột là một tuần, số ô sáng tỷ lệ với mức hoạt động; [lowIndex] được tô lime. */
@Composable
fun PillMatrix(levels: List<Int>, lowIndex: Int?, modifier: Modifier = Modifier, rows: Int = 6) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        levels.forEachIndexed { col, level ->
            val low = col == lowIndex
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(rows) { r ->
                    val on = rows - r <= level || (low && r == rows - 1)
                    Box(
                        Modifier.fillMaxWidth().height(11.dp).clip(CircleShape)
                            .background(if (on) (if (low) VC.neon else Color(0xFFDCE5E0)) else Color.White.copy(alpha = .10f)),
                    )
                }
            }
        }
    }
}

class TreemapTile(val label: String, val count: Int, val pct: Int, val bg: Color, val fg: Color = VC.pine)

/** Treemap tỷ lệ hai tầng: ô lớn nhất bên trái, các ô còn lại xếp bên phải theo tỷ lệ. */
@Composable
fun Treemap(tiles: List<TreemapTile>, modifier: Modifier = Modifier, height: Dp = 190.dp) {
    val items = tiles.filter { it.count > 0 }.sortedByDescending { it.count }
    if (items.isEmpty()) return
    val rest = items.drop(1)
    Row(modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TileBox(items[0], Modifier.weight(items[0].count.toFloat()).fillMaxHeight(), big = true)
        if (rest.isNotEmpty()) Column(Modifier.weight(rest.sumOf { it.count }.toFloat()).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TileBox(rest[0], Modifier.weight(rest[0].count.toFloat()).fillMaxWidth())
            val tail = rest.drop(1)
            if (tail.isNotEmpty()) Row(Modifier.weight(tail.sumOf { it.count }.toFloat()).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tail.forEach { TileBox(it, Modifier.weight(it.count.toFloat()).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun TileBox(t: TreemapTile, modifier: Modifier, big: Boolean = false) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(t.bg).padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Text(t.label, color = t.fg.copy(alpha = .85f), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2)
        Text("${t.pct}%", color = t.fg, fontSize = if (big) 32.sp else 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Thẻ kính mờ nổi trên nền đường đồng mức (Compose không làm mờ nền được nên dùng lớp trắng bán trong suốt). */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier.shadow(10.dp, shape, ambientColor = VC.shadow.copy(alpha = .08f), spotColor = VC.shadow.copy(alpha = .12f))
            .clip(shape).background(Color.White.copy(alpha = .76f)).border(1.dp, Color.White.copy(alpha = .8f), shape).padding(22.dp),
        content = content,
    )
}

/** Đường đồng mức địa hình mờ làm nền cho khối Hero. */
@Composable
fun ContourArt(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        repeat(10) { i ->
            val y = h * (.08f + i * .09f)
            val p = Path().apply {
                moveTo(0f, y + 24f)
                cubicTo(w * .22f, y - 70f, w * .42f, y + 90f, w * .62f, y - 10f)
                cubicTo(w * .78f, y - 80f, w * .9f, y + 40f, w, y - 20f)
            }
            drawPath(p, VC.woodland.copy(alpha = .13f), style = Stroke(1.4.dp.toPx()))
        }
    }
}

@Composable
fun SmallDot(color: Color) = Box(Modifier.size(9.dp).clip(CircleShape).background(color))

