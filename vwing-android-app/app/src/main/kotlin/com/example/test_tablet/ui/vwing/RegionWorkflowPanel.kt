package com.example.test_tablet.ui.vwing

import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

private fun workflowNumber(v: Double?, digits: Int = 3) = v?.let { fmtNum(it, digits) } ?: "Không có dữ liệu"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegionWorkflowPanel(c: VCtx, region: GeoFeature?, cfg: SatelliteConfig, start: String, end: String,
                        onStart: (String) -> Unit, onEnd: (String) -> Unit, onConfigure: () -> Unit,
                        onOverlay: (MapOverlay?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var b0 by c.mapAnalysisSession.workflowBaselineStart
    var b1 by c.mapAnalysisSession.workflowBaselineEnd
    var cloudText by c.mapAnalysisSession.workflowCloud
    var thresholdText by c.mapAnalysisSession.workflowThreshold
    var pending by c.mapAnalysisSession.workflowPending
    val cloud = cloudText.replace(',', '.').toDoubleOrNull()
    val threshold = thresholdText.replace(',', '.').toDoubleOrNull()
    val signature = remember(region, cfg, b0, b1, start, end, cloudText, thresholdText) {
        MessageDigest.getInstance("SHA-256").digest(listOf(region?.id, region?.version, region?.points, cfg.url,
            cfg.token, b0, b1, start, end, cloudText, thresholdText).joinToString("|").toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
    val currentSignature by rememberUpdatedState(signature)
    val currentOverlay by rememberUpdatedState(onOverlay)
    val currentToast by rememberUpdatedState(c.toast)
    val cache = remember { File(context.cacheDir, "region-workflow-result.json") }
    var result by remember(signature) { mutableStateOf<RegionWorkflowResult?>(null) }
    var image by remember(signature) { mutableStateOf<ImageBitmap?>(null) }
    var error by remember(signature) { mutableStateOf<String?>(null) }
    var busy by remember(signature) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var showOverlay by remember(signature) { mutableStateOf(true) }
    var alpha by remember(signature) { mutableFloatStateOf(.75f) }
    val problem = RegionAnalysis.problem(region)

    // Cache only the completed response. Credentials are represented by a fingerprint, never serialized.
    LaunchedEffect(signature) {
        val restored = withContext(Dispatchers.IO) {
            runCatching {
                require(cache.length() <= 1_100_000)
                val o = JSONObject(cache.readText())
                require(o.getString("signature") == signature)
                require(System.currentTimeMillis() - o.getLong("saved_at") in 0..86_400_000L)
                RegionWorkflowClient.parse(o.getJSONObject("result"))
            }.getOrNull()
        }
        if (currentSignature == signature) result = restored
    }
    LaunchedEffect(result) {
        val r = result ?: return@LaunchedEffect
        val url = r.change.imageUrl ?: return@LaunchedEffect
        try {
            image = withContext(Dispatchers.IO) {
                val bytes = SatelliteClient.image(cfg, url).getOrThrow()
                val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, size)
                if (size.outWidth !in 1..4096 || size.outHeight !in 1..4096 || size.outWidth.toLong() * size.outHeight > 8_000_000)
                    throw IOException("Ảnh biến động quá lớn hoặc không hợp lệ.")
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() ?: throw IOException("Không đọc được ảnh biến động.")
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = "Đã có thống kê; chưa tải được lớp biến động. ${e.message.orEmpty()}" }
    }
    LaunchedEffect(result, image, alpha, showOverlay) {
        currentOverlay(if (showOverlay) result?.let { r -> image?.let { MapOverlay(r.bounds, it, alpha,
            "${if (r.simulated) "MÔ PHỎNG · " else ""}ΔNDVI · ${r.baseline.start} → ${r.comparison.endExclusive}") } } else null)
    }
    DisposableEffect(signature) { onDispose { currentOverlay(null) } }

    val saveReport: (Uri?) -> Unit = { uri ->
        val filename = pending
        pending = ""
        if (uri != null && filename.matches(Regex("workflow-export-[a-f0-9]{64}\\.(html|json)"))) {
            saving = true
            scope.launch {
                var saved = false
                try {
                    withContext(Dispatchers.IO) {
                        val file = File(context.cacheDir, filename)
                        if (!file.isFile || file.length() > 2_000_000) throw IOException("Báo cáo tạm đã hết hạn. Hãy xuất lại.")
                        context.contentResolver.openOutputStream(uri, "wt")?.use { out -> file.inputStream().use { it.copyTo(out) } }
                            ?: throw IOException("Không mở được tệp để lưu.")
                        saved = true
                    }
                    currentToast("Đã lưu báo cáo phân tích vùng.")
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message ?: "Không lưu được báo cáo." }
                finally {
                    if (!saved) runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                    saving = false
                }
            }
        }
    }
    val saveHtml = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html"), saveReport)
    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), saveReport)
    fun export(r: RegionWorkflowResult, html: Boolean) {
        val selected = region ?: return
        val text = if (html) RegionWorkflowReport.html(r, selected.name, selected.points) else RegionWorkflowReport.json(r, selected.name, selected.points)
        val extension = if (html) "html" else "json"
        // This immutable snapshot remains available if Android recreates the screen while the picker is open.
        val name = "workflow-export-$signature.$extension"
        runCatching {
            File(context.cacheDir, name).writeText(text)
            pending = name
            if (html) saveHtml.launch("VWING-bao-cao-vung-${r.comparison.endExclusive}.html")
            else saveJson.launch("VWING-phan-tich-vung-${r.comparison.endExclusive}.json")
        }.onFailure { error = "Không chuẩn bị được báo cáo: ${it.message}" }
    }

    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Eyebrow("QUY TRÌNH PHÂN TÍCH VÙNG")
        Text("Từ dữ liệu đến quyết định kiểm tra", color = VC.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("1. Kiểm tra ảnh  →  2. Tính 3 chỉ số  →  3. So sánh  →  4. Xuất báo cáo", color = VC.teal, fontSize = 14.sp)
        Text("Chỉ số tính từ Sentinel-2 gốc đã loại mây, tách biệt với ảnh AI Super-Resolution. Ngày cuối mỗi kỳ không được tính.", color = VC.text2, fontSize = 14.sp)
        Text("Kỳ nền", color = VC.text, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VField(b0, { b0 = it }, "Nền: từ ngày", Modifier.widthIn(min = 180.dp).weight(1f))
            VField(b1, { b1 = it }, "Nền: đến ngày", Modifier.widthIn(min = 180.dp).weight(1f))
        }
        Text("Kỳ so sánh", color = VC.text, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VField(start, onStart, "So sánh: từ ngày", Modifier.widthIn(min = 180.dp).weight(1f))
            VField(end, onEnd, "So sánh: đến ngày", Modifier.widthIn(min = 180.dp).weight(1f))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VField(cloudText, { cloudText = it }, "Mây tối đa của cảnh (%)", Modifier.widthIn(min = 180.dp).weight(1f), number = true)
            VField(thresholdText, { thresholdText = it }, "Ngưỡng biến động NDVI", Modifier.widthIn(min = 180.dp).weight(1f), number = true)
        }
        Text("Mây của toàn cảnh dùng để lọc ảnh; độ phủ hợp lệ trong vườn được tính riêng. Ngưỡng mặc định ±0,1 là ngưỡng khảo sát, không phải chẩn đoán cây bệnh.", color = VC.muted, fontSize = 13.sp)
        if (!cfg.configured) VButton("Kết nối vệ tinh", onConfigure, primary = false)
        VButton(if (busy) "Đang kiểm tra và phân tích…" else "Chạy quy trình GIS", {
            if (!cfg.configured) { onConfigure(); return@VButton }
            val validation = problem ?: RegionWorkflowClient.inputProblem(b0, b1, start, end, cloud ?: Double.NaN, threshold ?: Double.NaN)
            if (validation != null) { error = validation; return@VButton }
            val selected = region ?: return@VButton
            val snapshot = signature
            val dates = listOf(b0, b1, start, end)
            val cloudValue = cloud!!; val thresholdValue = threshold!!
            busy = true; result = null; image = null; error = null; onOverlay(null)
            scope.launch {
                try {
                    val r = withContext(Dispatchers.IO) {
                        val r = RegionWorkflowClient.analyze(cfg, selected.points.toList(), dates[0], dates[1], dates[2], dates[3], cloudValue, thresholdValue).getOrThrow()
                        val envelope = JSONObject().put("signature", snapshot).put("saved_at", System.currentTimeMillis()).put("result", JSONObject(r.rawJson))
                        val temporary = File(context.cacheDir, "workflow-$snapshot.tmp")
                        temporary.writeText(envelope.toString())
                        if (!temporary.renameTo(cache)) { cache.writeText(envelope.toString()); temporary.delete() }
                        r
                    }
                    ensureActive()
                    if (currentSignature == snapshot) result = r
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { if (currentSignature == snapshot) error = e.message ?: "Không phân tích được vùng." }
                finally { if (currentSignature == snapshot) busy = false }
            }
        }, enabled = !busy && problem == null)
        if (busy) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), color = VC.teal, strokeWidth = 2.dp)
            Text("Đang lọc ảnh, kiểm tra độ phủ và so sánh điểm ảnh ở cả hai kỳ…", color = VC.text2, fontSize = 14.sp)
        }
        error?.let { Notice(it, VC.red) }
        result?.let { r ->
            val change = r.change
            val delta = change.meanDelta
            val verdict = when { delta == null -> Verdict.UNKNOWN; delta <= -change.threshold -> Verdict.WARN; delta >= change.threshold -> Verdict.GOOD; else -> Verdict.UNKNOWN }
            val headline = when { delta == null -> "Chưa đủ dữ liệu chung giữa 2 kỳ"; delta <= -change.threshold -> "NDVI giảm ở phần lớn vùng"; delta >= change.threshold -> "NDVI tăng ở phần lớn vùng"; else -> "NDVI ít thay đổi" }
            Spacer(Modifier.height(8.dp))
            VerdictBanner(verdict, headline, if (r.simulated) "DỮ LIỆU MÔ PHỎNG — không dùng để ra quyết định" else "Tăng ${workflowNumber(change.increaseHa, 2)} ha · Giảm ${workflowNumber(change.decreaseHa, 2)} ha · Ổn định ${workflowNumber(change.stableHa, 2)} ha")
            if (verdict == Verdict.WARN) Text("Nên đi kiểm tra thực địa vùng giảm; đối chiếu lịch tưới, thời tiết và ảnh chụp trước khi kết luận nguyên nhân.", color = VC.text, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
            image?.let { bitmap ->
                Spacer(Modifier.height(10.dp))
                ImageViewer(ViewerImage(bitmap, r.bounds, "ΔNDVI"), Modifier.fillMaxWidth().height(230.dp))
                val colors = change.legend.palette.mapNotNull { runCatching { Color(android.graphics.Color.parseColor("#${it.removePrefix("#")}")) }.getOrNull() }
                if (colors.size >= 2) Box(Modifier.fillMaxWidth().height(12.dp).background(Brush.horizontalGradient(colors)))
                Text("${change.legend.label} · ${change.legend.min} → ${change.legend.max} · đỏ: giảm, xanh: tăng", color = VC.muted, fontSize = 13.sp)
                ChipRow(listOf(true, false), showOverlay, { if (it) "Hiện biến động trên bản đồ" else "Ẩn lớp biến động" }, { showOverlay = it })
                Slider(alpha, { alpha = it }, valueRange = .1f..1f)
            }
            TechnicalDetails(c.simpleMode, Modifier.padding(top = 8.dp)) {
                Notice(if (r.simulated) "MÔ PHỎNG · không phải kết quả đo vườn" else "DỮ LIỆU SENTINEL-2 · ${workflowNumber(r.areaHa, 2)} ha", if (r.simulated) VC.amber else VC.teal)
                WorkflowQuality("Kỳ nền", r.baseline)
                WorkflowQuality("Kỳ so sánh", r.comparison)
                Text("Chỉ số thực vật · nền → so sánh", color = VC.text, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 10.dp))
                r.baseline.indices.forEach { a ->
                    val b = r.comparison.indices.firstOrNull { it.index == a.index }
                    Text("${a.index} · ${a.nominalPixelSizeM} m: ${workflowNumber(a.mean)} → ${workflowNumber(b?.mean)}", color = VC.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("P5–P95 nền: ${workflowNumber(a.p5)}–${workflowNumber(a.p95)} · so sánh: ${workflowNumber(b?.p5)}–${workflowNumber(b?.p95)}\nĐộ phủ: ${workflowNumber(a.validCoveragePct, 1)}% → ${workflowNumber(b?.validCoveragePct, 1)}%", color = VC.text2, fontSize = 14.sp)
                }
                Text("ΔNDVI trung bình: ${workflowNumber(change.meanDelta)}\nĐộ phủ chung: ${workflowNumber(change.commonValidCoveragePct, 1)}% · ${workflowNumber(change.commonValidAreaHa, 3)} ha", color = VC.teal, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
                Text("Chỉ so sánh pixel có dữ liệu ở cả hai kỳ. Không cộng phần thiếu ảnh vào vùng ổn định. NDMI thể hiện độ ẩm tán lá, không phải độ ẩm đất.", color = VC.muted, fontSize = 13.sp)
                r.warnings.forEach { Notice(it, VC.amber) }
                Text(r.caution, color = VC.text2, fontSize = 14.sp)
                Text("Nguồn: ${r.source}\nPhương pháp: ${r.method}", color = VC.muted, fontSize = 13.sp)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VButton("Xuất báo cáo HTML", { export(r, true) }, primary = false, enabled = !saving)
                VButton("Xuất dữ liệu JSON", { export(r, false) }, primary = false, enabled = !saving)
            }
            Text("Báo cáo gồm ranh giới, nguồn ảnh, chất lượng, chỉ số, biến động và giới hạn. Mở HTML trong trình duyệt để in thành PDF.", color = VC.muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun WorkflowQuality(title: String, period: RegionWorkflowPeriod) {
    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("CHẤT LƯỢNG ẢNH · $title")
        Text("${period.sceneCount} cảnh · độ phủ hợp lệ ${workflowNumber(period.validCoveragePct, 1)}%", color = VC.text, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
        Text("${period.start} → ${period.endExclusive} (không gồm ngày cuối)", color = VC.text2, fontSize = 14.sp)
        Text("${period.sceneIds.size} mã cảnh được ghi trong báo cáo.", color = VC.muted, fontSize = 13.sp)
    }
}
