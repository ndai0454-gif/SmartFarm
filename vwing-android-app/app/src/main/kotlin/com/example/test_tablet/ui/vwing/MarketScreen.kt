package com.example.test_tablet.ui.vwing

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.test_tablet.data.vwing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val marketCrops = linkedMapOf("all" to "Tất cả mặt hàng", "coffee" to "Cà phê", "pepper" to "Hồ tiêu",
    "rubber" to "Cao su", "rice" to "Lúa gạo", "durian" to "Sầu riêng", "fertilizer" to "Phân bón",
    "cocoa" to "Ca cao", "sugar" to "Đường", "cotton" to "Bông", "cashew" to "Hạt điều", "chilli" to "Ớt", "avocado" to "Bơ", "other" to "Nông sản khác")

@Composable
fun MarketScreen(c: VCtx) {
    val context = LocalContext.current
    var selected by rememberSaveable { mutableStateOf("all") }
    var data by remember { mutableStateOf<MarketOverview?>(MarketClient.loadCache(context.filesDir) ?: MarketClient.sourceDirectory()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var request by remember { mutableIntStateOf(0) }
    var website by remember { mutableStateOf<Pair<String, String>?>(null) }
    var applyQuote by remember { mutableStateOf<MarketQuote?>(null) }
    LaunchedEffect(request) {
        loading = true
        val result = withContext(Dispatchers.IO) {
            MarketClient.refresh(SatelliteSettings(context.filesDir).load(), context.filesDir)
        }
        result.onSuccess { data = it; error = null }.onFailure {
            error = it.message ?: "Chưa tải được dữ liệu thị trường."
            data = data?.copy(stale = true)
        }
        loading = false
    }
    PageHeader(Icons.Filled.Insights, "GIÁ THỊ TRƯỜNG", "Giá có nguồn, ngày và đơn vị rõ ràng.",
        "Đối chiếu thị trường trong nước và sàn quốc tế. Giá tham khảo có thể khác giá thu mua tại vườn.") {
        VButton(if (loading) "Đang cập nhật…" else "Cập nhật bảng giá", { request++ }, enabled = !loading)
    }
    VCard(Modifier.fillMaxWidth()) {
        VPicker("Mặt hàng cần xem", marketCrops.values.toList(), marketCrops.getValue(selected),
            { label -> selected = marketCrops.entries.first { it.value == label }.key })
        Text("Bảng công khai được kiểm tra lại mỗi 15 phút. Giá kỳ hạn quốc tế giữ nguyên đơn vị gốc; không quy đổi thành giá mua tại vườn.",
            color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
        if (loading) Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(22.dp), color = VC.lime)
            Text("Đang lấy dữ liệu từ nguồn…", color = VC.text2)
        }
        error?.let { Notice(it + if (data != null) " Đang giữ bảng đã lưu; chưa có lần tải mới thành công." else "", VC.amber, Modifier.padding(top = 10.dp)) }
        if (data?.stale == true) Notice("Dữ liệu đã lưu / có nguồn chưa cập nhật được. Kiểm tra ngày giá trước khi sử dụng.", VC.amber, Modifier.padding(top = 10.dp))
    }
    val quotes = data?.quotes.orEmpty().filter { selected == "all" || it.commodity == selected }
        .sortedBy { when (it.kind) { "domestic" -> 0; "international" -> 1; else -> 2 } }
    SectionLabel("Bảng giá tham khảo · ${quotes.size} mức giá")
    if (!loading && quotes.isEmpty()) VCard(Modifier.fillMaxWidth()) {
        Text("Chưa có mức giá công khai đã xác minh cho mặt hàng này.", color = VC.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("Bạn vẫn có thể đọc trực tiếp các nguồn bên dưới. App không tự điền giá từ nội dung chưa đọc được.", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
    }
    quotes.forEach { q ->
        VCard(Modifier.fillMaxWidth()) {
            Text(q.label, color = VC.text, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Text(q.market, color = VC.text2, fontSize = 15.sp)
            Text("${fmtNum(q.price, if (q.price % 1 == 0.0) 0 else 2)} ${q.unit}",
                color = VC.lime, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, modifier = Modifier.padding(top = 6.dp))
            q.change?.let { Text("Thay đổi ${if (it > 0) "+" else ""}${fmtNum(it, if (it % 1 == 0.0) 0 else 2)} ${q.unit}",
                color = if (it >= 0) VC.mint else VC.amber, fontSize = 15.sp) }
            val source = data?.sources?.firstOrNull { it.id == q.sourceId }
            Text("${source?.name ?: q.sourceId} · Ngày giá: ${q.asOf ?: q.asOfLabel}", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            if (q.stale || data?.stale == true || MarketClient.oldPrice(q.asOf))
                Tag(if (q.asOf == null) "Nguồn chưa ghi đủ ngày" else "Kiểm tra ngày giá / dữ liệu lưu", VC.amber, Modifier.padding(top = 6.dp))
            Text(when (q.kind) { "futures" -> "Giá kỳ hạn quốc tế · không phải giá thu mua nội địa"; "international" -> "Giá quốc tế / xuất khẩu · giữ nguyên đơn vị nguồn"; else -> "Giá công khai tham khảo · không phải cam kết thu mua" },
                color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            VButton("Đọc nguồn giá", { website = (source?.name ?: "Nguồn giá") to q.sourceUrl }, primary = false, modifier = Modifier.padding(top = 8.dp))
            if (Access.canWriteSales && q.kind == "domestic" && q.unit in setOf("VNĐ/kg", "VND/kg", "đ/kg", "₫/kg") && q.asOf != null && !q.stale && data?.stale != true && !MarketClient.oldPrice(q.asOf)) {
                VButton("Chọn làm giá tham khảo của vườn", { applyQuote = q }, primary = false, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
    val relevant = data?.sources.orEmpty().filter { selected == "all" || selected in it.commodities || "all" in it.commodities }
    SectionLabel("Nguồn thị trường · ${relevant.size} trang")
    relevant.forEach { s ->
        VCard(Modifier.fillMaxWidth()) {
            Text(s.name, color = VC.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(when (s.category) { "prices" -> "Bảng giá chuyên ngành"; "news" -> "Tin thị trường"; "analysis" -> "Phân tích thị trường"; "international" -> "Giá quốc tế"; else -> s.category }, color = VC.muted, fontSize = 13.sp)
            Tag(when { s.stale -> "Đang dùng bản lưu"; s.status == "public" -> "Đọc được dữ liệu công khai"; s.status == "unavailable" -> "Chưa truy cập được nguồn"; else -> "Xem tại trang nguồn" },
                if (s.status == "public" && !s.stale) VC.mint else VC.amber, Modifier.padding(top = 8.dp))
            if (s.note.isNotBlank()) Text(s.note, color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            s.fetchedAt?.let { Text("Lần đọc nguồn: $it", color = VC.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
            val blockedSource = s.id in setOf("giacaosu", "vfa") && s.status == "unavailable"
            VButton(if (blockedSource) "Nguồn chưa khả dụng" else "Mở ${s.name} trong app", { website = s.name to s.url },
                primary = false, enabled = !blockedSource, modifier = Modifier.padding(top = 8.dp))
        }
    }
    val news = data?.news.orEmpty().filter { selected == "all" || relevant.any { s -> s.id == it.sourceId } }
    if (news.isNotEmpty()) {
        SectionLabel("Tin thị trường từ nguồn")
        news.take(8).forEach { n -> VCard(Modifier.fillMaxWidth()) {
            Text(n.title, color = VC.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            val name = data?.sources?.firstOrNull { it.id == n.sourceId }?.name ?: n.sourceId
            Text(name + (n.publishedAt?.let { " · $it" } ?: " · nguồn chưa ghi ngày"), color = VC.muted, fontSize = 13.sp)
            VButton("Đọc bài gốc", { website = name to n.url }, primary = false, modifier = Modifier.padding(top = 8.dp))
        } }
    }
    applyQuote?.let { q -> AlertDialog(onDismissRequest = { applyQuote = null }, containerColor = VC.card,
        title = { Text("Đổi giá tham khảo của vườn?") },
        text = { Text("${q.label} · ${q.market}\n${fmtNum(q.price, 0)} ${q.unit} · ${q.asOf}\nChỉ xác nhận nếu đúng cây trồng, loại hàng và khu vực của vườn. Giá này sẽ thay giả định doanh thu hiện tại.") },
        confirmButton = { TextButton(onClick = {
            if (Access.canWriteSales) {
                val date = q.asOf?.split('-')?.takeIf { it.size == 3 }?.let { Dates.parse("${it[2]}/${it[1]}/${it[0]}") }
                if (date != null) { c.store.updateGarden(c.d.garden.copy(pricePerKg = q.price, priceSource = q.sourceUrl,
                    priceRegion = q.market, priceGrade = q.label, priceDate = date)); c.toast("Đã cập nhật giá tham khảo kèm nguồn và ngày.") }
            }
            applyQuote = null
        }) { Text("Dùng giá này") } },
        dismissButton = { TextButton(onClick = { applyQuote = null }) { Text("Giữ giá hiện tại") } }) }
    website?.let { (title, url) -> MarketWebsite(title, url) { website = null } }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MarketWebsite(title: String, url: String, onClose: () -> Unit) {
    var web by remember { mutableStateOf<WebView?>(null) }
    var busy by remember { mutableStateOf(true) }
    var problem by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { web?.stopLoading(); web?.destroy(); web = null } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        VCard(Modifier.fillMaxSize().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, color = VC.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Đóng nguồn") }
            }
            Text("Trang của nhà cung cấp · có thể cần thuê bao hoặc tài khoản riêng", color = VC.muted, fontSize = 12.sp)
            if (busy) Text("Đang mở trang nguồn…", color = VC.mint, modifier = Modifier.padding(vertical = 6.dp))
            problem?.let { Text(it, color = VC.amber, modifier = Modifier.padding(vertical = 6.dp)) }
            AndroidView(modifier = Modifier.fillMaxWidth().weight(1f), factory = { context ->
                WebView(context).apply {
                    web = this
                    settings.javaScriptEnabled = true; settings.domStorageEnabled = true
                    settings.allowFileAccess = false; settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            if (!request.isForMainFrame) return false
                            return !MarketClient.safeSourceUrl(request.url.toString()).also { allowed ->
                                if (!allowed) problem = "Liên kết ngoài danh sách nguồn đã dừng."
                            }
                        }
                        override fun onPageStarted(view: WebView?, value: String?, favicon: Bitmap?) { busy = true; problem = null }
                        override fun onPageFinished(view: WebView?, value: String?) { busy = false }
                        override fun onReceivedError(view: WebView?, req: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                            if (req?.isForMainFrame == true) { busy = false; problem = "Chưa mở được nguồn. Kiểm tra mạng hoặc thử lại sau." }
                        }
                        override fun onReceivedHttpError(view: WebView?, req: WebResourceRequest?, response: android.webkit.WebResourceResponse?) {
                            if (req?.isForMainFrame == true) { busy = false; problem = "Trang nguồn trả HTTP ${response?.statusCode}. Nguồn có thể yêu cầu tài khoản hoặc giới hạn truy cập." }
                        }
                    }
                    if (MarketClient.safeSourceUrl(url)) loadUrl(url) else { busy = false; problem = "Địa chỉ nguồn không hợp lệ." }
                }
            })
        }
    }
}
