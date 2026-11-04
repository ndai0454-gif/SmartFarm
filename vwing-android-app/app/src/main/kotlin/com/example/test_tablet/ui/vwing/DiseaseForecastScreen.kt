package com.example.test_tablet.ui.vwing

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private fun hexColor(hex: String?, fallback: Color = Color(0xFF9CA3AF)) =
    try { if (hex == null) fallback else Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { fallback }

private fun shortDate(iso: String) = if (iso.length >= 10) "${iso.substring(8, 10)}/${iso.substring(5, 7)}" else iso

/**
 * Dự báo bệnh cây theo từng vườn, lấy từ máy chủ MapMyCrop bằng tài khoản của chính người dùng.
 * Mô hình rủi ro chạy ở máy chủ đó; VWing chỉ tải và hiển thị, giữ bản gần nhất để xem khi mất mạng.
 */
@Composable
fun DiseaseForecastScreen(c: VCtx) {
    val context = LocalContext.current
    val store = remember { MmcStore(context.filesDir) }
    var apiKey by remember { mutableStateOf(store.apiKey()) }
    val regions = remember(c.d.features) { RegionAnalysis.regions(c.d, "") }
    val region = regions.firstOrNull { it.kind == FeatureKind.BOUNDARY } ?: regions.firstOrNull()
    PageHeader(
        Icons.Filled.BugReport,
        "SÂU BỆNH · MAPMYCROP & VI KHÍ HẬU",
        "Dự báo dịch bệnh cây trồng 7 ngày",
        "Kiểm tra trực tiếp API app.mapmycrop.com/disease-forecast# kết hợp mô hình dịch bệnh theo đa giác vùng đã khoanh (Open-Meteo Agro + Sentinel-2 NDVI/NDMI).",
    ) {}
    val key = apiKey
    if (key == null) MmcLoginCard(store) { apiKey = it }
    else MmcForecastPanel(c, store, key, onSignOut = { store.signOut(); apiKey = null })
    Spacer(Modifier.height(12.dp))
    LayeredTerrain3D(region = region)
}

@Composable
private fun MmcLoginCard(store: MmcStore, onDone: (String) -> Unit) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var go by remember { mutableIntStateOf(0) }
    LaunchedEffect(go) {
        if (go == 0) return@LaunchedEffect
        busy = true; error = null
        withContext(Dispatchers.IO) { runCatching { MapMyCropClient.login(user, pass) } }
            .onSuccess { store.saveApiKey(it); pass = ""; onDone(it) }
            .onFailure { error = it.message ?: "Không đăng nhập được." }
        busy = false
    }
    VCard(Modifier.fillMaxWidth()) {
        Text("Đăng nhập MapMyCrop", color = VC.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Dự báo bệnh do máy chủ MapMyCrop tính, nên cần tài khoản app.mapmycrop.com của bạn. Mật khẩu chỉ dùng để đăng nhập một lần và không được lưu trong VWing.",
            color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
        VField(user, { user = it }, "Email, hoặc mã vùng + số điện thoại (+84…)", enabled = !busy)
        Spacer(Modifier.height(8.dp))
        VField(pass, { pass = it }, "Mật khẩu", enabled = !busy, secret = true)
        error?.let { Notice(it, VC.red, Modifier.padding(top = 8.dp)) }
        VButton(if (busy) "Đang đăng nhập…" else "Đăng nhập", { go++ }, enabled = !busy && user.isNotBlank() && pass.isNotBlank(),
            modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun MmcForecastPanel(c: VCtx, store: MmcStore, key: String, onSignOut: () -> Unit) {
    var search by remember { mutableStateOf("") }
    var farms by remember { mutableStateOf<List<MmcFarm>>(emptyList()) }
    var hasNext by remember { mutableStateOf(false) }
    var cursor by remember { mutableStateOf<Pair<String?, String?>>(null to null) }
    var selected by remember { mutableStateOf<MmcFarm?>(null) }
    var loadMore by remember { mutableIntStateOf(0) }
    var listError by remember { mutableStateOf<String?>(null) }
    var lastSearch by remember { mutableStateOf("") }

    LaunchedEffect(search, loadMore) {
        val append = loadMore > 0 && search == lastSearch
        lastSearch = search
        val (cAt, cId) = if (append) cursor else (null to null)
        withContext(Dispatchers.IO) { runCatching { MapMyCropClient.farms(key, search, cAt, cId) } }
            .onSuccess { p ->
                farms = if (append) (farms + p.farms).distinctBy { it.id } else p.farms
                hasNext = p.hasNext; cursor = p.nextCursorCreatedAt to p.nextCursorId; listError = null
                if (selected == null || !append && farms.none { it.id == selected?.id }) selected = farms.firstOrNull()
            }
            .onFailure { listError = it.message ?: "Chưa tải được danh sách vườn."; if (it is MmcAuthException) onSignOut() }
    }

    VCard(Modifier.fillMaxWidth()) {
        VField(search, { search = it }, "Tìm vườn trên MapMyCrop")
        listError?.let { Notice(it, VC.red, Modifier.padding(top = 8.dp)) }
        if (farms.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            VPicker("Vườn", farms.map { it.name }, selected?.name.orEmpty(), { n -> selected = farms.firstOrNull { it.name == n } })
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hasNext) VButton("Tải thêm vườn", { loadMore++ }, primary = false)
            VButton("Đăng xuất MapMyCrop", onSignOut, primary = false)
        }
    }
    val farm = selected
    if (farm == null) { if (listError == null) EmptyNote("Tài khoản MapMyCrop này chưa có vườn nào."); return }
    ForecastTable(c, store, key, farm, onAuthLost = onSignOut)
}

@Composable
private fun ForecastTable(c: VCtx, store: MmcStore, key: String, farm: MmcFarm, onAuthLost: () -> Unit) {
    var forecast by remember(farm.id) { mutableStateOf<DiseaseForecast?>(null) }
    var fetchedAt by remember(farm.id) { mutableStateOf<Long?>(null) }
    var offline by remember(farm.id) { mutableStateOf(false) }
    var error by remember(farm.id) { mutableStateOf<String?>(null) }
    var loading by remember(farm.id) { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(farm.id, refresh) {
        loading = true
        val cached = withContext(Dispatchers.IO) { store.loadForecast(farm.id) }
        if (forecast == null && cached != null) runCatching { MapMyCropClient.parseForecast(cached.second) }.onSuccess { forecast = it; fetchedAt = cached.first }
        withContext(Dispatchers.IO) { runCatching { MapMyCropClient.forecastRaw(key, farm.id).also { store.saveForecast(farm.id, it) } } }
            .onSuccess { raw -> forecast = MapMyCropClient.parseForecast(raw); fetchedAt = System.currentTimeMillis(); offline = false; error = null }
            .onFailure {
                if (it is MmcAuthException) { onAuthLost(); return@onFailure }
                offline = forecast != null; error = "Chưa tải được dự báo bệnh. Hãy thử lại sau ít phút."
            }
        loading = false
    }

    val f = forecast
    if (f == null) {
        if (loading) Notice("Đang tải dự báo bệnh…", VC.teal)
        else { error?.let { Notice(it, VC.red) }; EmptyNote("Chưa có dữ liệu dự báo bệnh cho vườn này.") }
        return
    }
    val shown = f.dates.dropLast(MapMyCropClient.TAIL_DAYS_HIDDEN)
    val (highCount, firstDay) = MapMyCropClient.highRiskSummary(f)
    if (f.rows.isEmpty() || shown.isEmpty()) { EmptyNote("Chưa có dữ liệu dự báo bệnh cho vườn này."); return }

    if (highCount > 0) VerdictBanner(Verdict.BAD, "$highCount bệnh có nguy cơ cao", "Sớm nhất ngày ${shortDate(firstDay.orEmpty())}. Hãy ra vườn kiểm tra trước ngày đó.")
    else VerdictBanner(Verdict.GOOD, "Chưa có bệnh ở mức nguy cơ cao", "Trong ${shown.size} ngày tới theo dự báo của MapMyCrop.")
    if (offline || error != null) Notice((error ?: "") + (fetchedAt?.let { " Đang hiện bản lưu lúc ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))}." } ?: ""), VC.amber)

    VCard(Modifier.fillMaxWidth()) {
        Text("Dự báo là ước lượng xác suất, không phải chẩn đoán. Mức cao hoặc trung bình nghĩa là điều kiện thuận lợi cho bệnh phát triển. Luôn kiểm tra thực tế ngoài vườn trước khi xử lý; đừng phun thuốc chỉ dựa vào dự báo.",
            color = VC.text2, fontSize = 13.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            f.legend.forEach { l ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(hexColor(l.colorHex)))
                    Text(humanize(l.level), color = VC.text, fontSize = 14.sp)
                }
            }
        }
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row {
                Cell("Bệnh", 150, header = true, left = true)
                shown.forEach { Cell(shortDate(it), 76, header = true) }
            }
            f.rows.forEach { r ->
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cell(r.disease, 150, left = true)
                    shown.forEach { d ->
                        val cell = r.cells[d]
                        val pct = cell?.percent
                        Box(Modifier.width(76.dp), contentAlignment = Alignment.Center) {
                            Text(if (pct == null) "N/A" else "${if (pct % 1.0 == 0.0) pct.toInt().toString() else "%.1f".format(pct)}%",
                                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(hexColor(cell?.colorHex)).widthIn(min = 58.dp).padding(horizontal = 6.dp, vertical = 5.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        }
        VButton(if (loading) "Đang cập nhật…" else "Cập nhật dự báo", { refresh++ }, enabled = !loading, primary = false, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun Cell(text: String, width: Int, header: Boolean = false, left: Boolean = false) {
    Text(text, color = VC.text, fontSize = if (header) 13.sp else 14.sp, fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
        textAlign = if (left) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.width(width.dp).padding(horizontal = 4.dp, vertical = 6.dp))
}
