package com.example.test_tablet.ui.vwing

import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.MessageDigest

private fun decodeSrPreview(bytes: ByteArray): ImageBitmap {
    val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, size)
    if (size.outWidth !in 1..4096 || size.outHeight !in 1..4096 || size.outWidth.toLong() * size.outHeight > 8_000_000) {
        throw IOException("Kích thước ảnh xem trước không hợp lệ hoặc quá lớn.")
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        ?: throw IOException("Không đọc được ảnh PNG xem trước.")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuperResolutionPanel(c: VCtx, region: GeoFeature?, cfg: SatelliteConfig, start: String, end: String,
                         onStart: (String) -> Unit, onEnd: (String) -> Unit, onConfigure: () -> Unit,
                         onOverlay: (MapOverlay?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var outputSize by c.mapAnalysisSession.outputSize
    var cloudText by c.mapAnalysisSession.cloudText
    val cloudPct = cloudText.replace(',', '.').toDoubleOrNull()
    val signature = remember(region, cfg, start, end, outputSize, cloudText) {
        val raw = listOf(region?.id, region?.version, region?.points, cfg.url, cfg.token, start, end, outputSize, cloudText).joinToString("|")
        MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    val currentSignature by rememberUpdatedState(signature)
    val currentCfg by rememberUpdatedState(cfg)
    val currentOverlay by rememberUpdatedState(onOverlay)
    val currentToast by rememberUpdatedState(c.toast)
    // Persist only the job ID/fingerprint/time; never persist the token in saved UI state.
    var jobId by c.mapAnalysisSession.jobId
    var jobSignature by c.mapAnalysisSession.jobSignature
    var jobStartedAt by c.mapAnalysisSession.jobStartedAt
    var submitNumber by remember { mutableIntStateOf(0) }
    var requestedSignature by remember { mutableStateOf("") }
    var pollNumber by remember { mutableIntStateOf(0) }
    var submitting by remember(signature) { mutableStateOf(false) }
    var polling by remember(signature) { mutableStateOf(false) }
    var downloading by remember(signature) { mutableStateOf(false) }
    var error by remember(signature) { mutableStateOf<String?>(null) }
    var snapshot by remember(signature) { mutableStateOf<SrJobSnapshot?>(null) }
    var original by remember(signature) { mutableStateOf<ImageBitmap?>(null) }
    var enhanced by remember(signature) { mutableStateOf<ImageBitmap?>(null) }
    var overlayMode by remember(signature) { mutableStateOf("Ảnh tăng cường") }
    var alpha by remember(signature) { mutableFloatStateOf(.8f) }
    var pendingPath by c.mapAnalysisSession.pendingPath
    var pendingSignature by c.mapAnalysisSession.pendingSignature
    val busy = submitting || polling
    val result = snapshot?.result
    val inputProblem = RegionAnalysis.problem(region)
        ?: if (region != null && Geo.polygonAreaHa(region.points) > 25) "Super-Resolution hỗ trợ vùng tối đa 25 ha." else null

    LaunchedEffect(signature) {
        if (jobSignature != signature) { jobId = null; jobSignature = ""; jobStartedAt = 0 }
        currentOverlay(null)
    }
    DisposableEffect(signature) { onDispose { currentOverlay(null) } }

    LaunchedEffect(signature, submitNumber) {
        if (submitNumber == 0 || requestedSignature != signature) return@LaunchedEffect
        submitting = true
        try {
            val selected = region ?: throw IOException("Chưa chọn ranh giới phân tích.")
            val job = withContext(Dispatchers.IO) {
                SuperResolutionClient.submit(cfg, selected.points.toList(), start, end, outputSize, cloudPct ?: 80.0).getOrThrow()
            }
            ensureActive()
            if (currentSignature == signature) { snapshot = job; jobSignature = signature; jobId = job.jobId }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (currentSignature == signature) error = e.message ?: "Không gửi được tác vụ AI." }
        finally { if (currentSignature == signature) submitting = false }
    }

    LaunchedEffect(signature, jobId, pollNumber) {
        val id = jobId ?: return@LaunchedEffect
        if (jobSignature != signature) return@LaunchedEffect
        polling = true
        error = null
        try {
            var previous: SrJobSnapshot? = null
            while (true) {
                ensureActive()
                if (System.currentTimeMillis() - jobStartedAt > 15 * 60_000L) {
                    throw IOException("Đã theo dõi 15 phút. Bạn có thể tiếp tục kiểm tra tác vụ; máy chủ vẫn giữ mã tác vụ này.")
                }
                val job = withContext(Dispatchers.IO) { SuperResolutionClient.status(cfg, id).getOrThrow() }
                if (previous?.accepts(job) == false) throw IOException("Máy chủ trả tiến trình không nhất quán. Hãy kiểm tra lại tác vụ.")
                previous = job
                snapshot = job
                if (job.state == SrJobState.FAILED) { error = job.error; break }
                if (job.state == SrJobState.SUCCEEDED) {
                    val r = job.result ?: throw IOException("Máy chủ chưa trả kết quả ảnh.")
                    val images = withContext(Dispatchers.IO) {
                        val before = decodeSrPreview(SuperResolutionClient.preview(cfg, r.files.getValue("original_preview")).getOrThrow())
                        currentCoroutineContext().ensureActive()
                        val after = decodeSrPreview(SuperResolutionClient.preview(cfg, r.files.getValue("enhanced_preview")).getOrThrow())
                        if (before.width != after.width || before.height != after.height) throw IOException("Hai ảnh xem trước không cùng khung so sánh.")
                        before to after
                    }
                    ensureActive()
                    original = images.first
                    enhanced = images.second
                    break
                }
                delay(2_000)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (currentSignature == signature) error = e.message ?: "Không theo dõi được tác vụ AI." }
        finally { if (currentSignature == signature) polling = false }
    }

    LaunchedEffect(result, original, enhanced, overlayMode, alpha) {
        val bitmap = when (overlayMode) { "Ảnh gốc" -> original; "Ảnh tăng cường" -> enhanced; else -> null }
        currentOverlay(result?.let { r -> bitmap?.let { MapOverlay(r.bounds, it, alpha,
            "${if (overlayMode == "Ảnh gốc") "Sentinel-2 gốc 10 m" else "AI suy diễn · ${r.outputPixelSizeM} m"} · ${r.sourceDate}") } })
    }

    val saveDocument: (Uri?) -> Unit = save@{ uri ->
        val path = pendingPath
        val selectedSignature = pendingSignature
        pendingPath = null
        pendingSignature = ""
        if (uri == null || path == null) return@save
        if (selectedSignature != currentSignature) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            currentToast("Vùng hoặc cấu hình đã thay đổi. Hãy tải kết quả mới.")
            return@save
        }
        val selectedCfg = currentCfg
        downloading = true
        scope.launch {
            var saved = false
            try {
                withContext(Dispatchers.IO) {
                    val task = currentCoroutineContext()
                    context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        SuperResolutionClient.download(selectedCfg, path, output) {
                            task.ensureActive()
                            if (selectedSignature != currentSignature) throw CancellationException("SR inputs changed")
                        }.getOrThrow()
                    } ?: throw IOException("Không mở được tài liệu để lưu.")
                }
                saved = true
                if (selectedSignature == currentSignature) currentToast("Đã lưu tệp Super-Resolution.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (selectedSignature == currentSignature) error = e.message ?: "Không tải được tệp." }
            finally {
                if (!saved) runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                if (selectedSignature == currentSignature) downloading = false
            }
        }
    }
    val saveTiff = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/tiff"), saveDocument)
    val saveMetadata = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), saveDocument)

    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("SENTINEL-2 SUPER-RESOLUTION")
        Text("Quan sát rõ hơn vùng đã chọn", color = VC.text, fontWeight = FontWeight.Bold, fontSize = 19.sp,
            modifier = Modifier.padding(top = 6.dp))
        Notice("AI suy diễn · chưa kiểm chứng thực địa", VC.amber, Modifier.padding(top = 10.dp))
        Text("ESAOpenSR SEN2SRLite: Sentinel-2 RGB + NIR 10 m → AI 2,5 m. Ảnh hỗ trợ quan sát; không dùng để tính lại NDVI, đo cao độ hoặc xác nhận từng cây.",
            color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        inputProblem?.let { Notice(it, VC.amber, Modifier.padding(top = 8.dp)) }
        if (!cfg.configured) {
            Notice("Cần kết nối backend live có mô hình AI để tạo ảnh thật.", VC.amber, Modifier.padding(top = 8.dp))
            VButton("Kết nối vệ tinh", onConfigure, primary = false, modifier = Modifier.padding(top = 8.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
            VField(start, onStart, "Từ ngày (YYYY-MM-DD)", Modifier.widthIn(min = 180.dp).weight(1f))
            VField(end, onEnd, "Đến ngày (không gồm)", Modifier.widthIn(min = 180.dp).weight(1f))
        }
        VField(cloudText, { cloudText = it }, "Mây tối đa của cảnh (%)", number = true, modifier = Modifier.padding(top = 8.dp))
        ChipRow(listOf(2.5, 1.0), outputSize, { if (it == 2.5) "AI 2,5 m" else "Xuất 1 m (lấy mẫu lại)" },
            { outputSize = it }, Modifier.padding(top = 8.dp))
        if (outputSize == 1.0) Text("1 m là kích thước pixel xuất từ ảnh AI 2,5 m, không phải dữ liệu vệ tinh gốc 1 m hay mô hình AI tăng 10 lần.",
            color = VC.amber, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        VButton(if (busy) "Đang xử lý tác vụ…" else if (snapshot?.state == SrJobState.FAILED) "Thử tạo lại ảnh AI" else "Tạo ảnh AI cho vùng", {
            if (!cfg.configured) { onConfigure(); return@VButton }
            val validation = inputProblem ?: analysisDateProblem(start, end)
                ?: if (cloudPct == null || !cloudPct.isFinite() || cloudPct !in 0.0..100.0) "Ngưỡng mây phải từ 0 đến 100%." else null
            if (validation != null) { error = validation; return@VButton }
            error = null; snapshot = null; original = null; enhanced = null; jobId = null
            currentOverlay(null)
            jobStartedAt = System.currentTimeMillis()
            jobSignature = signature
            requestedSignature = signature
            submitting = true
            submitNumber++
        }, enabled = !busy && !downloading && inputProblem == null, modifier = Modifier.padding(top = 10.dp))

        if (busy) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), color = VC.teal, strokeWidth = 2.dp)
            Text(when (snapshot?.state) {
                SrJobState.QUEUED -> "Đã vào hàng đợi; đang chờ mô hình."
                SrJobState.RUNNING -> "Mô hình đang xử lý ảnh Sentinel-2 thật…"
                SrJobState.SUCCEEDED -> "Đang tải hai ảnh xem trước…"
                else -> "Đang gửi hoặc kiểm tra tác vụ…"
            }, color = VC.text2, fontSize = 14.sp)
        }
        error?.let { Notice(it, VC.red, Modifier.padding(top = 10.dp)) }
        if (error != null && jobId != null && snapshot?.state != SrJobState.FAILED && !busy) {
            VButton("Tiếp tục kiểm tra tác vụ", { jobStartedAt = System.currentTimeMillis(); pollNumber++ }, primary = false,
                modifier = Modifier.padding(top = 8.dp))
        }
        result?.let { r ->
            Text("Cảnh ${r.sourceDate} · độ phủ hợp lệ ${fmtNum(r.validCoveragePct, 1)}%", color = VC.teal,
                fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp))
            if (original != null && enhanced != null) {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    if (maxWidth >= 600.dp) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SrPreview("Sentinel-2 gốc · 10 m", original!!, r.bounds, Modifier.weight(1f))
                        SrPreview("AI suy diễn · ${fmtNum(r.outputPixelSizeM, 1)} m", enhanced!!, r.bounds, Modifier.weight(1f))
                    } else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SrPreview("Sentinel-2 gốc · 10 m", original!!, r.bounds, Modifier.fillMaxWidth())
                        SrPreview("AI suy diễn · ${fmtNum(r.outputPixelSizeM, 1)} m", enhanced!!, r.bounds, Modifier.fillMaxWidth())
                    }
                }
                ChipRow(listOf("Ảnh gốc", "Ảnh tăng cường", "Ẩn lớp ảnh"), overlayMode, { it }, { overlayMode = it }, Modifier.padding(top = 10.dp))
                Text("Độ đậm lớp ảnh trên bản đồ", color = VC.text2, fontSize = 14.sp)
                Slider(alpha, { alpha = it }, valueRange = .1f..1f)
            }
            TechnicalDetails(c.simpleMode, Modifier.padding(top = 8.dp)) {
                Text("Gốc ${fmtNum(r.nativePixelSizeM, 0)} m → AI ${fmtNum(r.modelPixelSizeM, 1)} m → xuất ${fmtNum(r.outputPixelSizeM, 1)} m · ${r.width} × ${r.height} px", color = VC.text2, fontSize = 14.sp)
                Text("Mô hình: ${r.model}\nPhiên bản: ${r.modelRevision}\nCảnh nguồn: ${r.sourceScene}\nGeoTIFF: ${r.crs}\nPhương pháp: ${r.method}",
                    color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                r.warnings.forEach { Text("• $it", color = VC.amber, fontSize = 14.sp) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                VButton("Tải GeoTIFF AI", { pendingPath = r.files.getValue("enhanced_geotiff"); pendingSignature = signature; saveTiff.launch("VWING-AI-${r.sourceDate}-${r.outputPixelSizeM}m.tif") }, primary = false, enabled = !downloading)
                VButton("Tải GeoTIFF gốc", { pendingPath = r.files.getValue("original_geotiff"); pendingSignature = signature; saveTiff.launch("VWING-Sentinel2-${r.sourceDate}-10m.tif") }, primary = false, enabled = !downloading)
                VButton("Tải metadata", { pendingPath = r.files.getValue("metadata"); pendingSignature = signature; saveMetadata.launch("VWING-SR-${r.sourceDate}-metadata.json") }, primary = false, enabled = !downloading)
            }
            if (downloading) Text("Đang lưu tệp đã chọn…", color = VC.teal, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SrPreview(label: String, bitmap: ImageBitmap, bounds: GeoBounds, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = VC.text2, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        ImageViewer(ViewerImage(bitmap, bounds, label), Modifier.fillMaxWidth().height(220.dp).padding(top = 5.dp))
    }
}
