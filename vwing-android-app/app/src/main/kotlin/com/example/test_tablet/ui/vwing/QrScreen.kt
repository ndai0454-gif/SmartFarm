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
                    Text("Người mua quét sẽ mở trang truy xuất chỉ gồm các trường chủ vườn đã chọn công khai.", color = VC.text2, fontSize = 14.sp)
                    publicNote?.let { Text(it, color = VC.amber, fontSize = 14.sp) }
                } else Text("Mã gắn với ID cố định của cây nên đổi mã hiển thị vẫn quét ra đúng hồ sơ. Lưu ảnh để in và dán lên cây.", color = VC.text2, fontSize = 14.sp)
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
    val d = c.d
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf("") }
    var previewQrTree by remember { mutableStateOf<Tree?>(null) }
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
                c.selectTree(r.tree.id)
                c.toast("Đã mở hồ sơ cây ${r.tree.code} (${r.tree.zone}).")
                c.nav.push("tree|${r.tree.id}")
            }
            is Qr.Scan.Unknown -> {
                val fallback = d.activeTrees.firstOrNull { it.code.equals(r.id, ignoreCase = true) }
                if (fallback != null) {
                    c.selectTree(fallback.id)
                    c.nav.push("tree|${fallback.id}")
                } else {
                    c.toast(scanMessage(r) ?: "Không tìm thấy cây ${r.id}.")
                }
            }
            is Qr.Scan.Foreign -> {
                val fallback = d.activeTrees.firstOrNull {
                    it.code.equals(cleaned, ignoreCase = true) || cleaned.contains(it.code, ignoreCase = true)
                }
                if (fallback != null) {
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

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VCard(Modifier.fillMaxWidth(), borderColor = VC.border) {
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

            androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (c.wide) 320.dp else 260.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(androidx.compose.ui.graphics.Color(0xFF07150E)),
                contentAlignment = Alignment.Center,
            ) {
                if (granted) {
                    CameraPreview(onResult = { handleRawScan(it) })
                    Box(
                        Modifier
                            .size(180.dp)
                            .border(2.dp, VC.mint, RoundedCornerShape(16.dp)),
                    )
                } else {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            if (asked) "Chưa có quyền Camera. Hãy bấm cấp quyền bên dưới hoặc nhập mã cây ở ô kế tiếp."
                            else "Đang xin quyền truy cập Camera để quét mã QR…",
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 15.sp,
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
                    "Hoặc nhập mã cây (ví dụ: A-01, SR-001)",
                    Modifier.weight(1f),
                )
                VButton(
                    "Mở hồ sơ cây",
                    { handleRawScan(manual) },
                    enabled = manual.isNotBlank(),
                )
            }
        }

        val trees = d.activeTrees.sortedBy { it.code }
        if (trees.isNotEmpty()) {
            VCard(Modifier.fillMaxWidth(), borderColor = VC.border) {
                Eyebrow("DANH SÁCH NHÃN QR CÂY TRONG VƯỜN (${trees.size} CÂY)")
                Text(
                    "Chạm \"Mở cây\" để xem hồ sơ hoặc \"Mã QR\" để xem và lưu nhãn in dán lên cây.",
                    color = VC.muted,
                    fontSize = 13.sp,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                trees.take(12).forEach { t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(VC.elevated, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Cây ${t.code} · ${t.zone}",
                                color = VC.text,
                                fontSize = 15.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            )
                            Text(
                                "${t.variety.ifBlank { d.garden.crop }} · ${t.condition.label}",
                                color = VC.text2,
                                fontSize = 12.5.sp,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            VButton("Mã QR", { previewQrTree = t }, primary = false)
                            VButton("Mở cây", {
                                c.selectTree(t.id)
                                c.nav.push("tree|${t.id}")
                            })
                        }
                    }
                }
            }
        }
    }
}
