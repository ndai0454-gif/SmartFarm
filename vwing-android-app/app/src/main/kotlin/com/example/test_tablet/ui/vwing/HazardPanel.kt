package com.example.test_tablet.ui.vwing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun levelColor(level: Int): Color = when (level) {
    0 -> VC.mint; 1 -> Color(0xFFB08900); 2 -> Color(0xFFD9680F); else -> VC.red
}

/** Natural-hazard outlook for the garden point. [compact] shows only a banner, and only when something is flagged. */
@Composable
fun HazardPanel(c: VCtx, compact: Boolean) {
    val context = LocalContext.current
    val point = WeatherClient.gardenPoint(c.d)
    var outlook by remember(point) { mutableStateOf(point?.let { HazardClient.loadCache(context.filesDir, it) }) }
    var error by remember(point) { mutableStateOf<String?>(null) }
    var loading by remember(point) { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(point, refresh) {
        val p = point ?: return@LaunchedEffect
        loading = true
        withContext(Dispatchers.IO) { HazardClient.refresh(SatelliteSettings(context.filesDir).load(), context.filesDir, p) }
            .onSuccess { outlook = it; error = null }
            .onFailure { error = it.message ?: "Chưa tính được cảnh báo."; outlook = outlook?.copy(stale = true) }
        loading = false
    }
    if (point == null) {
        if (!compact) VCard(Modifier.fillMaxWidth()) {
            Text("Cảnh báo thiên tai", color = VC.text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("Khoanh ranh vườn hoặc lưu tọa độ cây để xem nguy cơ ngập úng, gió mạnh, nắng nóng tại đúng vườn.", color = VC.text2, modifier = Modifier.padding(top = 8.dp))
        }
        return
    }
    val o = outlook
    if (o == null) {
        if (!compact) VCard(Modifier.fillMaxWidth()) {
            Text("Cảnh báo thiên tai", color = VC.text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(error ?: if (loading) "Đang tính nguy cơ 3 ngày tới…" else "Chưa có dữ liệu.", color = VC.text2, modifier = Modifier.padding(top = 8.dp))
        }
        return
    }
    if (compact && o.overallLevel == 0) return
    val color = levelColor(o.overallLevel)
    VCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(14.dp).background(color, RoundedCornerShape(7.dp)))
            Text("Cảnh báo thiên tai · 3 ngày tới", color = VC.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Text(o.headline, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        if (o.stale) Text("Dữ liệu đã lưu, chưa cập nhật được · kiểm tra lại thời điểm", color = VC.amber, fontSize = 13.sp)
        if (compact) return@VCard
        Text("Mưa dự báo: 24 giờ ${fmtNum(o.rain.next24, 0)} mm · 48 giờ ${fmtNum(o.rain.next48, 0)} mm · 72 giờ ${fmtNum(o.rain.next72, 0)} mm. " +
            "3 ngày qua đã mưa ${fmtNum(o.rain.past72, 0)} mm.", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        o.hazards.forEach { h ->
            Divider2()
            Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(levelColor(h.level), RoundedCornerShape(5.dp)))
                    Text(h.label, color = VC.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${h.levelLabel} · ${(h.riskIndex * 100).toInt()}/100", color = levelColor(h.level), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                h.reasons.forEach { Text(it, color = VC.text2, fontSize = 14.sp) }
                h.advice.forEach { Text("• $it", color = VC.text, fontSize = 14.sp) }
            }
        }
        o.warnings.forEach { Text(it, color = VC.amber, fontSize = 13.sp) }
        Text(o.method + " Điểm /100 là chỉ số quy ước theo ngưỡng, không phải xác suất xảy ra.", color = VC.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        Text("Cập nhật ${o.fetchedAt} · Weather data by Open-Meteo.com (CC BY 4.0). Hãy theo dõi thêm bản tin chính thức của Trung tâm Dự báo KTTV Quốc gia (nchmf.gov.vn).", color = VC.muted, fontSize = 12.sp)
        VButton(if (loading) "Đang cập nhật…" else "Cập nhật cảnh báo", { refresh++ }, enabled = !loading, primary = false, modifier = Modifier.padding(top = 8.dp))
    }
}
