package com.example.test_tablet.ui.vwing

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.test_tablet.data.vwing.GardenData
import com.example.test_tablet.data.vwing.Qr
import com.example.test_tablet.data.vwing.Tree
import com.example.test_tablet.data.vwing.activeTrees
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

// ---------- Tạo mã ----------

fun qrBitmap(text: String, px: Int): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.CHARACTER_SET to "UTF-8")
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, px, px, hints)
    val pixels = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, m.width, m.height, Bitmap.Config.ARGB_8888)
}

private fun Canvas.drawLabel(tree: Tree, left: Float, top: Float, w: Float, h: Float, qr: Bitmap) {
    val border = Paint().apply { style = Paint.Style.STROKE; color = 0xFF999999.toInt(); strokeWidth = 2f }
    drawRect(left, top, left + w, top + h, border)
    val side = minOf(w - 24f, h - 80f)
    drawBitmap(qr, null, android.graphics.RectF(left + (w - side) / 2, top + 12f, left + (w + side) / 2, top + 12f + side), null)
    val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt(); textSize = 34f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF444444.toInt(); textSize = 22f; textAlign = Paint.Align.CENTER }
    drawText(tree.code, left + w / 2, top + 12f + side + 38f, big)
    drawText("${tree.zone} · VWing", left + w / 2, top + 12f + side + 66f, small)
}

/** Một nhãn: mã QR + mã cây + khu. */
fun treeLabelBitmap(tree: Tree): Bitmap {
    val w = 420; val h = 520
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp); cv.drawColor(0xFFFFFFFF.toInt())
    cv.drawLabel(tree, 0f, 0f, w.toFloat(), h.toFloat(), qrBitmap(Qr.payloadFor(tree), 360))
    return bmp
}

/** Tờ nhãn cho nhiều cây (4 cột) để in và dán lên cây. */
fun treeLabelSheet(trees: List<Tree>): Bitmap {
    val cols = 4; val cw = 420; val ch = 520
    val rows = ((trees.size + cols - 1) / cols).coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(cols * cw, rows * ch, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp); cv.drawColor(0xFFFFFFFF.toInt())
    trees.forEachIndexed { i, t -> cv.drawLabel(t, (i % cols) * cw.toFloat(), (i / cols) * ch.toFloat(), cw.toFloat(), ch.toFloat(), qrBitmap(Qr.payloadFor(t), 360)) }
    return bmp
}

fun saveBitmap(context: Context, uri: android.net.Uri, bmp: Bitmap): Boolean = runCatching {
    context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } == true
}.getOrDefault(false)

@Composable
fun QrDialog(c: VCtx, tree: Tree, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // Hai loại mã: nội bộ (vwing://tree/<id>, chỉ app quét được) và công khai (địa chỉ web cho người mua, xem trang chủ vườn đã chọn hiện gì).
    val sync = remember { com.example.test_tablet.data.vwing.SyncSettings(context.filesDir).load() }
    val canPublic = sync.configured
    var publicMode by remember { mutableStateOf(false) }
    var publicNote by remember { mutableStateOf<String?>(null) }
    var publicBase by remember { mutableStateOf(sync.url) }
    LaunchedEffect(publicMode) {
        if (publicMode) {
            publicNote = null
            runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.example.test_tablet.data.vwing.Account.publicConfig(sync) } }
                .onSuccess { pc ->
                    if (pc.baseUrl.isNotBlank()) publicBase = pc.baseUrl
                    if (!pc.enabled) publicNote = "Trang công khai đang TẮT nên người mua quét sẽ không thấy gì. Bật trong Thiết lập vườn → Trang công khai."
                }
                .onFailure { publicNote = "Không đọc được cấu hình công khai (cần quyền chủ vườn/quản lý). Mã vẫn đúng nếu chủ đã bật." }
        }
    }
    val payload = if (publicMode) com.example.test_tablet.data.vwing.publicTreeUrl(publicBase, sync.gardenKey, tree.id) else Qr.payloadFor(tree)
    val bmp = remember(tree.id, payload) { if (publicMode) qrBitmap(payload, 360).let { q ->
        Bitmap.createBitmap(420, 520, Bitmap.Config.ARGB_8888).also { b -> Canvas(b).also { cv -> cv.drawColor(0xFFFFFFFF.toInt()); cv.drawLabel(tree, 0f, 0f, 420f, 520f, q) } }
    } else treeLabelBitmap(tree) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) c.toast(if (saveBitmap(context, uri, bmp)) "Đã lưu nhãn QR ${tree.code}." else "Không lưu được ảnh.")
    }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = VC.card,
        title = { Text("Mã QR · ${tree.code}", color = VC.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canPublic) ChipRow(listOf(false, true), publicMode, { if (it) "QR cho người mua" else "QR nội bộ" }, { publicMode = it })
                Image(bmp.asImageBitmap(), "Mã QR cây ${tree.code}", Modifier.size(260.dp).background(androidx.compose.ui.graphics.Color.White).align(Alignment.CenterHorizontally))
                if (publicMode) {
                    Text(payload, color = VC.text2, fontSize = 13.sp)
                    Text(
                        "🌐 Nội dung người mua/thương lái nhận được khi quét:\n" +
                            "• Trang web Truy xuất nguồn gốc (✓ VWing Verified)\n" +
                            "• Câu chuyện vườn, Tên vườn, Địa điểm, Cây trồng & Diện tích\n" +
                            "• Mã cây (${tree.code}), Khu (${tree.zone}), Giống (${tree.variety.ifBlank { c.d.garden.crop }}), Năm trồng (${tree.plantedYear ?: "—"}), Tình trạng (${tree.condition.label}) & Vị trí GPS làm tròn ~10m\n" +
                            "• Nhật ký chăm sóc, vật tư/liều lượng, khối lượng thu hoạch (kg) & ảnh thực tế (ẩn hoàn toàn tên nhân công, bệnh nội bộ và doanh thu).",
                        color = VC.text2,
                        fontSize = 13.sp,
                    )
                    publicNote?.let { Text(it, color = VC.amber, fontSize = 13.5.sp) }
                } else {
                    Text("Payload: $payload", color = VC.mint, fontSize = 12.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text(
                        "📱 Nội dung nhận được khi quét bằng App VWing:\n" +
                            "1. Định danh & Trạng thái: Mã ${tree.code} · ${tree.zone}, ID cố định (${tree.id}), Thẻ ${tree.condition.label}\n" +
                            "2. Thông số & Năng suất: Số trái đếm trên cây (${tree.fruits ?: 0} trái), Giống ${tree.variety.ifBlank { c.d.garden.crop }}, Năm trồng ${tree.plantedYear ?: "—"}, Tọa độ GPS 6 số lẻ\n" +
                            "3. Danh sách Vấn đề & Sâu bệnh đang mở + Phác đồ xử lý\n" +
                            "4. So sánh Ảnh Trước – Sau theo mốc thời gian chăm sóc\n" +
                            "5. Dòng thời gian Nhật ký: Quan sát, Bón phân, Phun thuốc, Tưới nước, Thu hoạch & Kiểm toán (Audit)\n" +
                            "6. Thao tác nhanh 1 chạm: Ghi hoạt động, Báo bệnh, Sửa cây, Thay thế cây, Xem trên bản đồ.",
                        color = VC.text2,
                        fontSize = 13.sp,
                    )
                }
            }
        },
        confirmButton = { VButton("Lưu ảnh nhãn", { save.launch("vwing-qr-${tree.code}${if (publicMode) "-nguoi-mua" else ""}.png") }) },
        dismissButton = { TextButton(onDismiss) { Text("Đóng", color = VC.text2) } },
    )
}

/** Xuất tờ nhãn cho mọi cây đang quản lý (dùng ở Thiết lập vườn). */
@Composable
fun rememberQrSheetExport(c: VCtx): () -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Bitmap?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val b = pending; pending = null
        if (uri != null && b != null) c.toast(if (saveBitmap(context, uri, b)) "Đã lưu tờ nhãn QR." else "Không lưu được ảnh.")
    }
    return {
        val trees = c.d.activeTrees.sortedBy { it.code }
        if (trees.isEmpty()) c.toast("Chưa có cây nào để tạo nhãn.") else { pending = treeLabelSheet(trees); save.launch("vwing-qr-tat-ca-cay.png") }
    }
}

// ---------- Quét mã ----------

private fun Context.findActivity(): ComponentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) { if (ctx is ComponentActivity) return ctx; ctx = ctx.baseContext }
    return null
}

private fun decodeQr(data: ByteArray, rowStride: Int, width: Int, height: Int, reader: MultiFormatReader): String? = try {
    val src = PlanarYUVLuminanceSource(data, rowStride, height, 0, 0, width, height, false)
    reader.decodeWithState(BinaryBitmap(HybridBinarizer(src))).text
} catch (e: com.google.zxing.ReaderException) {
    null
} finally {
    reader.reset()
}

/**
 * Hộp thoại quét QR toàn màn hình. [onResult] nhận nội dung mã (một lần) rồi hộp thoại tự đóng.
 * Luôn có ô nhập mã tay để dùng khi chưa cấp quyền camera hoặc nhãn bị mờ.
 */
@Composable
fun QrScannerDialog(onDismiss: () -> Unit, onResult: (String) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf("") }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    DisposableEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA); onDispose { } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(VC.bg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Eyebrow("QUÉT MÃ QR"); Text("Đưa mã QR trên cây vào khung", color = VC.text, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
                VButton("Đóng", onDismiss, primary = false)
            }
            Box(Modifier.weight(1f).fillMaxWidth().background(androidx.compose.ui.graphics.Color.Black), contentAlignment = Alignment.Center) {
                if (granted) CameraPreview(onResult) else {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (asked) "Chưa có quyền camera. Cấp quyền trong Cài đặt hoặc nhập mã cây bên dưới." else "Đang xin quyền camera…", color = androidx.compose.ui.graphics.Color.White, fontSize = 16.sp)
                        if (asked) VButton("Xin quyền lại", { ask.launch(Manifest.permission.CAMERA) })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                VField(manual, { manual = it }, "Hoặc nhập mã cây, ví dụ A-04", Modifier.weight(1f))
                VButton("Mở", { onResult(manual) }, enabled = manual.isNotBlank())
            }
        }
    }
}

@Composable
private fun CameraPreview(onResult: (String) -> Unit) {
    val context = LocalContext.current
    val owner = remember { context.findActivity() }
    val done = remember { AtomicBoolean(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val reader = remember {
        MultiFormatReader().apply { setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true)) }
    }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            if (owner == null) return@addListener
            val p = future.get(); provider = p
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(executor) { img ->
                try {
                    if (!done.get()) {
                        val plane = img.planes[0]; val buf = plane.buffer
                        val bytes = ByteArray(buf.remaining()); buf.get(bytes)
                        val text = decodeQr(bytes, plane.rowStride, img.width, img.height, reader)
                        if (text != null && done.compareAndSet(false, true)) previewView.post { onResult(text) }
                    }
                } finally { img.close() }
            }
            runCatching {
                p.unbindAll()
                p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { provider?.unbindAll(); executor.shutdown() }
    }
    AndroidView({ previewView }, Modifier.fillMaxSize())
}

/** Kết quả quét → thông báo cho người dùng nếu không khớp cây nào. */
fun scanMessage(scan: Qr.Scan): String? = when (scan) {
    is Qr.Scan.Found -> null
    is Qr.Scan.Unknown -> "Mã QR hợp lệ (${scan.id}) nhưng cây chưa có trong danh sách cây đã lưu của vườn."
    is Qr.Scan.Foreign -> "Mã này không phải mã cây của VWing: \"${scan.text}\"."
}

fun resolveScan(d: GardenData, raw: String) = Qr.resolve(d, raw)

/**
 * Màn hình Quét mã QR cây trồng (nằm ở vị trí chính giữa thanh điều hướng dưới):
 * - Mở trực tiếp khung Camera quét mã QR dán trên thân cây (`vwing://tree/...` hoặc mã cây `A-04`, `SR-001`).
 * - Hỗ trợ nhập nhanh mã cây bằng tay khi nhãn mờ hoặc đứng xa.
 * - Hiển thị danh sách nhanh các cây trong vườn kèm nút xem/lưu nhãn QR từng cây và xuất tờ nhãn QR toàn vườn.
 */
@Composable
fun QrScannerScreen(c: VCtx) {
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val d = c.d
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf("") }
    var previewQrTree by remember { mutableStateOf<Tree?>(null) }
    var scannedSpacingMeta by remember { mutableStateOf<Qr.SpacingQrMeta?>(null) }
    var showQrGuideDetails by remember { mutableStateOf(true) }
    val exportAllQr = rememberQrSheetExport(c)
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    DisposableEffect(Unit) {
        if (!granted) ask.launch(Manifest.permission.CAMERA)
        onDispose { }
    }

    fun handleRawScan(raw: String) {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return
        val r = resolveScan(d, cleaned)
        when (r) {
            is Qr.Scan.Found -> {
                scannedSpacingMeta = r.spacingMeta
                performConfirmHaptic(view)
                c.selectTree(r.tree.id)
                c.toast("Đã mở hồ sơ cây ${r.tree.code} (${r.tree.zone}).")
                c.nav.push("tree|${r.tree.id}")
            }
            is Qr.Scan.Unknown -> {
                scannedSpacingMeta = r.spacingMeta
                val fallback = d.activeTrees.firstOrNull { it.code.equals(r.id, ignoreCase = true) }
                if (fallback != null) {
                    performConfirmHaptic(view)
                    c.selectTree(fallback.id)
                    c.nav.push("tree|${fallback.id}")
                } else if (r.spacingMeta != null) {
                    performConfirmHaptic(view)
                    c.toast("Đã giải mã QR Lưới Khoảng Cách cây ${r.spacingMeta.code} (${r.spacingMeta.cropVi} · ${r.spacingMeta.spacing}).")
                } else {
                    c.toast(scanMessage(r) ?: "Không tìm thấy cây ${r.id}.")
                }
            }
            is Qr.Scan.Foreign -> {
                val fallback = d.activeTrees.firstOrNull {
                    it.code.equals(cleaned, ignoreCase = true) || cleaned.contains(it.code, ignoreCase = true)
                }
                if (fallback != null) {
                    performConfirmHaptic(view)
                    c.selectTree(fallback.id)
                    c.nav.push("tree|${fallback.id}")
                } else {
                    c.toast(scanMessage(r) ?: "Không nhận diện được mã cây.")
                }
            }
        }
    }

    previewQrTree?.let { t ->
        QrDialog(c, t, onDismiss = { previewQrTree = null })
    }

    @OptIn(ExperimentalLayoutApi::class)
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VCard(Modifier.fillMaxWidth(), borderColor = VC.border) {
            if (c.wide) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Eyebrow("QUÉT MÃ QR GỐC CÂY")
                        Text(
                            "Đưa mã QR dán trên thân cây vào khung camera",
                            color = VC.text,
                            fontSize = 18.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        )
                        Text(
                            "Tự động mở ngay hồ sơ cây, lịch sử bón phân/tưới nước và tình trạng sâu bệnh.",
                            color = VC.text2,
                            fontSize = 13.sp,
                        )
                    }
                    VButton(
                        "Xuất tờ QR cả vườn",
                        { exportAllQr() },
                        primary = false,
                    )
                }
            } else {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Eyebrow("QUÉT MÃ QR GỐC CÂY")
                    Text(
                        "Đưa mã QR dán trên thân cây vào khung camera",
                        color = VC.text,
                        fontSize = 16.5.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                    Text(
                        "Tự động mở ngay hồ sơ cây, lịch sử bón phân/tưới nước và tình trạng sâu bệnh.",
                        color = VC.text2,
                        fontSize = 13.sp,
                    )
                    VButton(
                        "Xuất tờ QR cả vườn",
                        { exportAllQr() },
                        modifier = Modifier.fillMaxWidth(),
                        primary = false,
                    )
                }
            }

            androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (c.wide) 320.dp else 230.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(androidx.compose.ui.graphics.Color(0xFF07150E))
                    .border(1.dp, VC.border, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (granted) {
                    CameraPreview(onResult = { handleRawScan(it) })
                    MotionQrLaserReticle()
                } else {
                    MotionQrLaserReticle()
                    Column(
                        Modifier
                            .padding(horizontal = 24.dp, vertical = 14.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(androidx.compose.ui.graphics.Color(0xCC04120C))
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            if (asked) "Chưa có quyền Camera. Hãy bấm cấp quyền bên dưới hoặc nhập mã cây ở ô kế tiếp."
                            else "Khung Laser AI sẵn sàng · Cấp quyền Camera để quét trực tiếp",
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 13.5.sp,
                        )
                        VButton("Cấp quyền Camera", { ask.launch(Manifest.permission.CAMERA) })
                    }
                }
            }

            androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VField(
                    manual,
                    { manual = it },
                    "Hoặc nhập mã cây (ví dụ: A-01, A-04)",
                    Modifier.weight(1f),
                )
                VButton(
                    "Mở hồ sơ cây",
                    { handleRawScan(manual) },
                    enabled = manual.isNotBlank(),
                )
            }

            val quickTrees = remember(d.activeTrees) {
                val attention = d.activeTrees.filter { it.condition != com.example.test_tablet.data.vwing.Condition.STABLE }
                (attention + d.activeTrees.filter { it.condition == com.example.test_tablet.data.vwing.Condition.STABLE }).distinctBy { it.id }.take(5)
            }
            if (quickTrees.isNotEmpty()) {
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                Text(
                    "Chạm mở nhanh cây trong vườn (không cần gõ chữ):",
                    color = VC.text2,
                    fontSize = 13.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    quickTrees.forEach { qt ->
                        val isWarn = qt.condition != com.example.test_tablet.data.vwing.Condition.STABLE
                        VButton(
                            text = if (isWarn) "⚠ Cây ${qt.code}" else "🌱 Cây ${qt.code}",
                            onClick = {
                                c.selectTree(qt.id)
                                c.nav.push("tree|${qt.id}")
                            },
                            primary = isWarn,
                        )
                    }
                }
            }
        }

        // Nếu vừa quét một mã QR Lưới Khoảng Cách (vwing://tree/SR-001?code=...&crop=DURIAN&spacing=8x8m...) chưa có trong danh sách cây đã lưu:
        scannedSpacingMeta?.let { meta ->
            VCard(Modifier.fillMaxWidth(), borderColor = VC.lime) {
                Eyebrow("KẾT QUẢ GIẢI MÃ QR LƯỚI KHOẢNG CÁCH · ${meta.code}", VC.lime)
                Text(
                    "🌳 Cây ${meta.code} · ${meta.cropVi} (Khoảng cách chuẩn ${meta.spacing.ifBlank { "8x8m" }} · ${meta.unitM2 ?: 64} m²/cây)",
                    color = VC.text,
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
                Text(
                    "• Vị trí lưới: Hàng ${meta.row ?: "—"} · Cây thứ ${meta.col ?: "—"}" +
                        (meta.totalTrees?.let { " / Tổng $it cây" } ?: "") +
                        (meta.areaM2?.let { " · Diện tích vườn ${String.format(java.util.Locale.US, "%.0f", it)} m²" } ?: "") +
                        "\n• Tọa độ tâm tán GPS: ${meta.lat?.let { String.format(java.util.Locale.US, "%.6f", it) } ?: "—"}, ${meta.lng?.let { String.format(java.util.Locale.US, "%.6f", it) } ?: "—"}",
                    color = VC.text2,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VButton(
                        "➕ Lưu cây ${meta.code} vào vườn & Mở hồ sơ",
                        {
                            val zoneName = meta.row?.let { "Hàng $it" } ?: "Khu A"
                            val geoPos = if (meta.lat != null && meta.lng != null) {
                                com.example.test_tablet.data.vwing.GeoPoint(meta.lat, meta.lng)
                            } else null
                            val out = c.store.addTree(
                                code = meta.code,
                                zone = zoneName,
                                variety = meta.cropVi,
                                plantedYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
                                pos = geoPos,
                                posSource = com.example.test_tablet.data.vwing.PosSource.ESTIMATED,
                            )
                            scannedSpacingMeta = null
                            val saved = c.store.data.activeTrees.firstOrNull { it.code.equals(meta.code, ignoreCase = true) }
                            if (saved != null) {
                                c.selectTree(saved.id)
                                c.toast("Đã lưu cây ${saved.code} vào vườn và mở hồ sơ.")
                                c.nav.push("tree|${saved.id}")
                            } else {
                                c.toast(out.message ?: "Đã xử lý mã QR cây ${meta.code}.")
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    VButton("Đóng", { scannedSpacingMeta = null }, primary = false)
                }
            }
        }

        // Thẻ giải thích chi tiết: NỘI DUNG NHẬN ĐƯỢC KHI QUÉT MÃ QR CÂY TRỒNG
        VCard(Modifier.fillMaxWidth(), borderColor = VC.mint.copy(alpha = 0.55f)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Eyebrow("NỘI DUNG NHẬN ĐƯỢC KHI QUÉT MÃ QR CỦA CÂY", VC.mint)
                    Text(
                        "3 chế độ dữ liệu trả về tự động khi quét mã QR dán trên thân cây",
                        color = VC.text,
                        fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                }
                VButton(
                    if (showQrGuideDetails) "Thu gọn" else "Xem chi tiết",
                    { showQrGuideDetails = !showQrGuideDetails },
                    primary = false,
                )
            }

            if (showQrGuideDetails) {
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(VC.elevated)
                        .border(1.dp, VC.border, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "1️⃣ Khi Chủ vườn / Kỹ sư / Nhân công quét bằng App VWing (QR Nội bộ `vwing://tree/<treeId>`):",
                        color = VC.mint,
                        fontSize = 14.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                    Text(
                        "• Định danh & Trạng thái: Mã cây (VD: A-04, SR-001), Khu/Lô canh tác, ID cố định (đổi tên mã cây vẫn giữ nguyên lịch sử), Thẻ sức khỏe (Ổn định / Theo dõi / Cần kiểm tra) & trạng thái đồng bộ Cloud.\n" +
                            "• Thông số sinh trưởng & Đếm trái (`THÔNG TIN`): Số trái đã đếm trên cây (tính sản lượng & doanh thu dự kiến), Giống cây & Năm trồng (VD: Sầu riêng Ri6 · 2019), Ngày kiểm tra gần nhất / cảnh báo quá hạn, Tọa độ GPS 6 số lẻ (`lat, lng`) & chất lượng dữ liệu.\n" +
                            "• Danh sách Sâu bệnh (`VẤN ĐỀ`): Các bệnh đang mở trên cây (vàng lá, xì mủ, rầy…), ngày phát hiện, hạn xử lý và nút mở phác đồ điều trị.\n" +
                            "• Đối chiếu Ảnh Trước – Sau (`SO SÁNH ẢNH TRƯỚC – SAU`): Đặt 2 ảnh chụp thực tế của cây theo mốc thời gian cạnh nhau để kiểm chứng hiệu quả phân thuốc.\n" +
                            "• Dòng thời gian Nhật ký (`DÒNG THỜI GIAN`): Toàn bộ lịch sử Quan sát, Bón phân, Phun thuốc, Tưới nước, Thu hoạch (số kg), Tái kiểm tra và Lịch sử kiểm toán chỉnh sửa (Audit).\n" +
                            "• Thao tác nhanh 1 chạm tại gốc: Ghi hoạt động, Báo vấn đề, Sửa thông tin, Thay thế cây, Xem trên bản đồ, In lại nhãn QR.",
                        color = VC.text,
                        fontSize = 12.8.sp,
                    )
                }

                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(VC.elevated)
                        .border(1.dp, VC.border, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "2️⃣ Khi quét Mã QR Lưới Khoảng Cách từ Bản đồ Khoanh vùng (`vwing://tree/SR-001?crop=DURIAN&spacing=8x8m...`):",
                        color = VC.lime,
                        fontSize = 14.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                    Text(
                        "• Giải mã trực tiếp thông số quy hoạch gắn sẵn trong QR: Mã cây (`SR-001`, `CF-0001`), Loại cây (`Sầu riêng 8m×8m = 64m²/cây` hoặc `Cà phê 3m×3m = 9m²/cây`), Vị trí `Hàng thứ mấy · Cây thứ mấy`, Tọa độ GPS tâm tán 6 số lẻ, Diện tích vườn (`m²`) và Tổng số cây.\n" +
                            "• Hỗ trợ nút 1 chạm lưu ngay cây từ lưới quy hoạch vào Sổ vườn nếu cây chưa có trong danh sách.",
                        color = VC.text,
                        fontSize = 12.8.sp,
                    )
                }

                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(VC.elevated)
                        .border(1.dp, VC.border, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "3️⃣ Khi Thương lái / Người tiêu dùng quét bằng Camera điện thoại thường (`QR cho người mua` · `/p/<gardenKey>/<treeId>`):",
                        color = VC.gold,
                        fontSize = 14.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                    Text(
                        "• Mở trang web Truy xuất nguồn gốc chính hãng (`✓ VWing Verified`) không cần cài app.\n" +
                            "• Hiển thị minh bạch các mục chủ vườn cho phép công khai: Lời giới thiệu vườn (`story`), Thông tin vườn (Tên vườn, Địa điểm, Cây trồng, Diện tích), Thông tin cây (Mã cây, Khu, Giống, Năm trồng, Tình trạng, Vị trí GPS làm tròn ~10m bảo mật), Ngày kiểm tra gần nhất, và Nhật ký chăm sóc & thu hoạch (Ngày, Hoạt động, Vật tư & Liều lượng, Khối lượng thu hoạch kg, Ảnh thực tế).\n" +
                            "• Bảo mật tuyệt đối: Tên nhân công, sâu bệnh nội bộ, công việc tồn đọng và doanh thu luôn được ẩn khỏi trang công khai.",
                        color = VC.text,
                        fontSize = 12.8.sp,
                    )
                }
            }
        }

        val trees = d.activeTrees.sortedBy { it.code }
        if (trees.isNotEmpty()) {
            var selectedZoneFilter by remember { mutableStateOf("Tất cả") }
            var showAllTrees by remember(selectedZoneFilter) { mutableStateOf(false) }
            val zoneOptions = remember(trees) {
                listOf("Tất cả") + trees.map { it.zone.ifBlank { "Khu A" } }.distinct().sorted()
            }
            val filteredTrees = remember(trees, selectedZoneFilter) {
                if (selectedZoneFilter == "Tất cả") trees
                else trees.filter { it.zone.equals(selectedZoneFilter, ignoreCase = true) }
            }
            val displayedTrees = remember(filteredTrees, showAllTrees) {
                if (showAllTrees) filteredTrees else filteredTrees.take(12)
            }
            VCard(Modifier.fillMaxWidth(), borderColor = VC.border) {
                Eyebrow("DANH SÁCH NHÃN QR CÂY TRONG VƯỜN (${filteredTrees.size}/${trees.size} CÂY)")
                Text(
                    "Chạm chọn Khu/Lô bên dưới để lọc nhanh, sau đó chạm trực tiếp vào dòng cây hoặc bấm \"Mã QR\" để in nhãn dán lên cây.",
                    color = VC.muted,
                    fontSize = 13.5.sp,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    zoneOptions.forEach { zOpt ->
                        val count = if (zOpt == "Tất cả") trees.size else trees.count { it.zone.equals(zOpt, ignoreCase = true) }
                        val active = selectedZoneFilter == zOpt
                        VButton(
                            text = "$zOpt ($count cây)",
                            onClick = { selectedZoneFilter = zOpt },
                            primary = active,
                        )
                    }
                }
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                displayedTrees.forEach { t ->
                    val openTreeAction = {
                        c.selectTree(t.id)
                        c.nav.push("tree|${t.id}")
                    }
                    if (c.wide) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                .background(VC.elevated, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                .border(1.dp, VC.border, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                .bounceClick(onClick = openTreeAction)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(
                                    "Cây ${t.code} · ${t.zone}",
                                    color = VC.text,
                                    fontSize = 15.5.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                )
                                Text(
                                    "${t.variety.ifBlank { d.garden.crop }} · ${t.condition.label} · ✓ Đã lưu",
                                    color = VC.text2,
                                    fontSize = 13.sp,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                VButton("Mã QR", { previewQrTree = t }, primary = false)
                                VButton("Mở cây", openTreeAction)
                            }
                        }
                    } else {
                        // Bố cục tối ưu cho Điện thoại (360dp & 411dp): thông tin cây hiển thị trọn chiều ngang, 2 nút thao tác chia đều bên dưới
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                                .background(VC.elevated, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                                .border(1.dp, VC.border, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                                .bounceClick(onClick = openTreeAction)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        "Cây ${t.code} · ${t.zone}",
                                        color = VC.text,
                                        fontSize = 16.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    )
                                    Text(
                                        "${t.variety.ifBlank { d.garden.crop }} · ✓ Đã lưu",
                                        color = VC.text2,
                                        fontSize = 13.sp,
                                    )
                                }
                                ConditionTag(t.condition)
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                VButton("Mã QR", { previewQrTree = t }, modifier = Modifier.weight(1f), primary = false)
                                VButton("Mở cây", openTreeAction, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (filteredTrees.size > 12) {
                    androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                    VButton(
                        text = if (!showAllTrees) "Xem đầy đủ tất cả ${filteredTrees.size} cây (còn ${filteredTrees.size - 12} cây)"
                        else "Thu gọn danh sách (chỉ hiện 12 cây đầu)",
                        onClick = { showAllTrees = !showAllTrees },
                        modifier = Modifier.fillMaxWidth(),
                        primary = false,
                    )
                }
            }
        }
    }
}
