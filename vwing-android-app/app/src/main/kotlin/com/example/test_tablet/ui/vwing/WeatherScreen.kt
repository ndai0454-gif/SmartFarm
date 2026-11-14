package com.example.test_tablet.ui.vwing

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

@Composable
fun WeatherScreen(c: VCtx) {
    val context = LocalContext.current
    val satCfg = remember { SatelliteSettings(context.filesDir).load() }
    val boundary = c.d.features.firstOrNull { it.kind == FeatureKind.BOUNDARY }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AuroraSmartFarmDashboard(c)
        NwsFfcMapHubCard(
            region = boundary,
            cfg = satCfg,
            onSelectOverlay = { c.nav.push("map") },
            onToast = { c.toast(it) },
        )
        WeatherPanel(c, compact = false)
        HazardPanel(c, compact = false)
    }
}

@Composable
fun WeatherPanel(c: VCtx, compact: Boolean = true) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val gardenPoint = WeatherClient.gardenPoint(c.d)
    val places = linkedMapOf<String, GeoPoint>()
    gardenPoint?.let { places["Vị trí vườn · ${c.d.garden.location.ifBlank { c.d.garden.name }}"] = it }
    if (!compact) {
        places["Bảo Lộc, Lâm Đồng · địa điểm tham khảo"] = GeoPoint(11.547, 107.807)
        places["Di Linh, Lâm Đồng · địa điểm tham khảo"] = GeoPoint(11.584, 108.065)
        places["Đạ Huoai, Lâm Đồng · địa điểm tham khảo"] = GeoPoint(11.412, 107.641)
        places["Lâm Hà / Đức Trọng, Lâm Đồng · địa điểm tham khảo"] = GeoPoint(11.735, 108.372)
    }
    var selected by remember(gardenPoint) { mutableStateOf(places.keys.firstOrNull().orEmpty()) }
    val point = places[selected]
    var report by remember(point) { mutableStateOf(point?.let { WeatherClient.loadCache(context.filesDir, it) }) }
    var error by remember(point) { mutableStateOf<String?>(null) }
    var loading by remember(point) { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(point, refresh) {
        val position = point ?: return@LaunchedEffect
        loading = true
        withContext(Dispatchers.IO) { WeatherClient.refresh(SatelliteSettings(context.filesDir).load(), context.filesDir, position) }
            .onSuccess { report = it; error = null }
            .onFailure { error = it.message ?: "Chưa tải được thời tiết."; report = report?.copy(stale = true) }
        loading = false
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!compact && places.isNotEmpty()) VCard(Modifier.fillMaxWidth()) {
            VPicker("Địa điểm dự báo", places.keys.toList(), selected, { selected = it })
            Text(if (gardenPoint != null && point == gardenPoint) "Vị trí tính từ tọa độ ranh vườn / cây đã lưu."
                else "Đây là địa điểm tham khảo được chọn, chưa phải vị trí vườn của bạn.",
                color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            VButton(if (loading) "Đang cập nhật…" else "Cập nhật thời tiết", { refresh++ }, enabled = !loading, primary = false, modifier = Modifier.padding(top = 8.dp))
        }
        if (point == null) {
            VCard(Modifier.fillMaxWidth()) {
                Text("Thời tiết tại vườn", color = VC.text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("Khoanh ranh vườn hoặc lưu tọa độ cây để xem thời tiết đúng nơi. Bạn cũng có thể chọn một địa điểm tham khảo.", color = VC.text2, modifier = Modifier.padding(top = 8.dp))
                VButton("Chọn địa điểm thời tiết", { c.nav.push("weather") }, primary = false, modifier = Modifier.padding(top = 8.dp))
            }; return@Column
        }
        val data = report
        val night = data?.now?.isDay == false
        val sky = if (night) listOf(Color(0xff102243), Color(0xff334b6a)) else listOf(Color(0xff17679a), Color(0xff4996bc))
        val today = data?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone(it.timezone) }.format(java.util.Date()) }
        val currentDay = data?.now?.time?.take(10)
        val isToday = currentDay != null && currentDay == today
        if (compact) {
            val day = data?.days?.firstOrNull { it.date == currentDay }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(sky), RoundedCornerShape(22.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (data != null) WeatherClient.symbol(data.now.code, data.now.isDay) else "⛅",
                        fontSize = 34.sp,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                if (data != null) "${data.now.temperature.roundToInt()}°C · ${WeatherClient.condition(data.now.code)}"
                                else if (loading) "Đang tải thời tiết…" else "Thời tiết tại vườn",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            buildString {
                                append(if (point == gardenPoint) c.d.garden.name else selected.substringBefore(" · "))
                                if (data != null) {
                                    append(" · Mưa ")
                                    append(day?.rainChance?.let { "${it.roundToInt()}%" } ?: "—")
                                    append(" · Ẩm ${data.now.humidity.roundToInt()}% · Gió ${fmtNum(data.now.wind, 1)} km/h")
                                }
                            },
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 13.5.sp,
                        )
                    }
                }
                VButton("7 ngày →", { c.nav.push("weather") }, primary = true)
            }
            return@Column
        }
        // Chỉ hiển thị thẻ bầu trời phụ khi người dùng chọn địa điểm tham khảo khác vị trí vườn (tránh lặp lại khối Hero của AuroraSmartFarmDashboard)
        if (point != gardenPoint || data == null) {
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(sky), RoundedCornerShape(28.dp)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (isToday) "THỜI TIẾT TẠI ĐỊA ĐIỂM THAM KHẢO" else "THỜI TIẾT TẠI ĐỊA ĐIỂM", color = Color.White.copy(alpha = .85f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(if (point == gardenPoint) c.d.garden.name else selected.substringBefore(" · "), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (data != null) {
                    Text(WeatherClient.symbol(data.now.code, data.now.isDay), fontSize = 42.sp)
                    Text("${data.now.temperature.roundToInt()}°", color = Color.White, fontSize = 68.sp, fontWeight = FontWeight.Light)
                    Text(WeatherClient.condition(data.now.code), color = Color.White, fontSize = 19.sp)
                    val day = data.days.firstOrNull { it.date == currentDay }
                    Text("Cảm giác như ${data.now.feels.roundToInt()}°" + (day?.let { " · Cao ${it.high.roundToInt()}°  Thấp ${it.low.roundToInt()}°" } ?: ""),
                        color = Color.White.copy(alpha = .9f), fontSize = 15.sp)
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        WeatherStat("Mưa hôm nay", day?.rainChance?.let { "${it.roundToInt()}%" } ?: "—")
                        WeatherStat("Gió", "${fmtNum(data.now.wind, 1)} km/h")
                        WeatherStat("Độ ẩm", "${data.now.humidity.roundToInt()}%")
                    }
                } else {
                    Text(if (loading) "Đang lấy dự báo…" else "Chưa có dữ liệu thời tiết", color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(vertical = 16.dp))
                }
                error?.let { Text(it, color = Color(0xffffe3a4), fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
        if (data != null) {
            VCard(Modifier.fillMaxWidth()) {
                SectionLabel("24 giờ tới")
                val hourStart = data.now.time.take(13) + ":00"
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    data.hours.filter { it.time >= hourStart }.take(24).forEach { hour ->
                        Column(Modifier.width(64.dp).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(hour.time.substringAfter('T'), color = VC.text2, fontSize = 14.sp)
                            Text(WeatherClient.symbol(hour.code), fontSize = 24.sp)
                            Text("${hour.temperature.roundToInt()}°", color = VC.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(hour.rainChance?.let { "${it.roundToInt()}% mưa" } ?: "—", color = VC.mint, fontSize = 12.sp)
                        }
                    }
                }
            }
            VCard(Modifier.fillMaxWidth()) {
                SectionLabel("Dự báo 7 ngày")
                data.days.forEach { day ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(if (day.date == today) "Hôm nay" else day.date.split('-').let { "${it[2]}/${it[1]}" }, color = VC.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(WeatherClient.condition(day.code), color = VC.text2, fontSize = 13.sp)
                        }
                        Text(WeatherClient.symbol(day.code), fontSize = 24.sp)
                        Text(day.rainChance?.let { "${it.roundToInt()}%" } ?: "—", color = VC.mint, fontSize = 14.sp)
                        Text("${day.low.roundToInt()}° — ${day.high.roundToInt()}°", color = VC.text, fontSize = 17.sp)
                    }; Divider2()
                }
            }
            data.days.firstOrNull { it.date == currentDay }?.let { day -> VCard(Modifier.fillMaxWidth()) {
                SectionLabel("Ánh sáng & mưa")
                Text("Bình minh ${day.sunrise?.substringAfter('T') ?: "—"} · Hoàng hôn ${day.sunset?.substringAfter('T') ?: "—"}", color = VC.text, fontSize = 16.sp)
                Text("Chỉ số UV cao nhất: ${day.uv?.let { fmtNum(it, 1) } ?: "—"}", color = VC.text2, modifier = Modifier.padding(top = 8.dp))
                Text("Lượng mưa tại thời điểm dự báo: ${fmtNum(data.now.precipitation, 1)} mm", color = VC.text2, modifier = Modifier.padding(top = 8.dp))
            } }
        }
        if (!compact) VCard(Modifier.fillMaxWidth()) {
            Text("Dữ liệu dự báo mô hình Open-Meteo, không phải phép đo tại vườn. Ô lưới dự báo có thể cách vị trí chọn vài km. Tọa độ chọn được gửi tới Open-Meteo để lấy dự báo.", color = VC.muted, fontSize = 13.sp)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VButton("Nguồn Open-Meteo", { uriHandler.openUri("https://open-meteo.com/") }, primary = false)
                VButton("CC BY 4.0", { uriHandler.openUri("https://creativecommons.org/licenses/by/4.0/") }, primary = false)
            }
        }
    }
}

@Composable
private fun WeatherStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
