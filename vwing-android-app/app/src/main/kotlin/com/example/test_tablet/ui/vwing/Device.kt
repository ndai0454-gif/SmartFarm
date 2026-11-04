package com.example.test_tablet.ui.vwing

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.example.test_tablet.data.vwing.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

private fun Context.has(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

// ---------- GPS ----------

/**
 * Lấy vị trí hiện tại của thiết bị một lần. [onFix] nhận (vĩ độ, kinh độ, sai số do thiết bị tự báo, mét).
 * Sai số này không phải số đo độc lập; vị trí vẫn nên ghi là "ước lượng" cho tới khi xác minh tại cây.
 */
@Composable
fun rememberLocationFetcher(c: VCtx, onFix: (lat: Double, lng: Double, accuracyM: Float?) -> Unit): () -> Unit {
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }

    fun fetch() {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { p -> runCatching { lm?.isProviderEnabled(p) == true }.getOrDefault(false) }
        if (lm == null || providers.isEmpty()) { c.toast("Chưa bật định vị (GPS) trên thiết bị."); return }
        busy = true
        val handler = Handler(Looper.getMainLooper())
        val signal = CancellationSignal()
        var finished = false
        fun finish(loc: Location?) {
            if (finished) return
            finished = true; busy = false; handler.removeCallbacksAndMessages(null); signal.cancel()
            if (loc == null) c.toast("Không lấy được vị trí. Ra nơi thoáng hơn và thử lại.")
            else onFix(loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy else null)
        }
        handler.postDelayed({ finish(null) }, 25_000)
        try {
            // Ưu tiên GPS; nếu không có thì dùng nguồn còn lại.
            LocationManagerCompat.getCurrentLocation(lm, providers.first(), signal, ContextCompat.getMainExecutor(context)) { loc -> finish(loc) }
        } catch (e: SecurityException) { finish(null) }
    }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) fetch() else c.toast("Chưa được cấp quyền vị trí. Bạn vẫn có thể nhập tọa độ hoặc chạm bản đồ.")
    }
    return {
        when {
            busy -> c.toast("Đang lấy vị trí…")
            context.has(Manifest.permission.ACCESS_FINE_LOCATION) || context.has(Manifest.permission.ACCESS_COARSE_LOCATION) -> { c.toast("Đang lấy vị trí…"); fetch() }
            else -> ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
}

/** Hỏi quyền vị trí (nếu chưa có) rồi chạy [then]. */
@Composable
fun rememberEnsureLocationPermission(c: VCtx): (then: () -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        val then = pending; pending = null
        if (r.values.any { it }) then?.invoke() else c.toast("Chưa được cấp quyền vị trí.")
    }
    return { then ->
        if (context.has(Manifest.permission.ACCESS_FINE_LOCATION) || context.has(Manifest.permission.ACCESS_COARSE_LOCATION)) then()
        else { pending = then; ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
    }
}

/**
 * Nhận vị trí liên tục khi [active] (chỉ khi app đang mở; không có dịch vụ chạy nền). [gpsOnly] = true cho việc cần chính xác
 * như đi bộ ghi ranh: bỏ nguồn mạng vì sai số hàng chục mét.
 */
@Composable
fun LocationUpdates(active: Boolean, gpsOnly: Boolean, intervalMs: Long = 2000, onFix: (Location) -> Unit) {
    val context = LocalContext.current
    val latest = rememberUpdatedState(onFix)
    DisposableEffect(active, gpsOnly) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val listener = android.location.LocationListener { loc -> latest.value(loc) }
        if (active && lm != null) {
            val providers = (if (gpsOnly) listOf(LocationManager.GPS_PROVIDER) else listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER))
                .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            try { providers.forEach { lm.requestLocationUpdates(it, intervalMs, 0f, listener, Looper.getMainLooper()) } } catch (_: SecurityException) {}
        }
        onDispose { runCatching { lm?.removeUpdates(listener) } }
    }
}

// ---------- Nhận diện Giọng nói Tiếng Việt (Trực tiếp trong App + Dự phòng Hệ thống) ----------

/**
 * Mở nhận dạng giọng nói tiếng Việt (vi-VN) trực tiếp trong ứng dụng với sóng âm thời gian thực
 * và phụ đề tức thì (partial results), tự động xin quyền RECORD_AUDIO và có chế độ dự phòng
 * hộp thoại giọng nói hệ thống nếu thiết bị không hỗ trợ SpeechRecognizer trực tiếp.
 */
@Composable
fun rememberVoiceInput(c: VCtx, onText: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val latestOnText by rememberUpdatedState(onText)
    var listeningDialog by remember { mutableStateOf(false) }
    var partialTranscript by remember { mutableStateOf("") }
    var statusLabel by remember { mutableStateOf("Đang khởi động micro…") }
    var rmsDb by remember { mutableFloatStateOf(2f) }
    var recognizerRef by remember { mutableStateOf<SpeechRecognizer?>(null) }

    val fallbackIntentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val text = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        if (!text.isNullOrEmpty()) {
            latestOnText(text)
            c.toast("Đã nhận giọng nói: \"$text\"")
        }
    }

    fun launchSystemDialogFallback() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Hãy nói câu hỏi hoặc nội dung ghi chú (Tiếng Việt)…")
        }
        runCatching { fallbackIntentLauncher.launch(intent) }
            .onFailure { c.toast("Thiết bị chưa cài đặt dịch vụ Giọng nói của Google (Google Voice Typing).") }
    }

    fun stopAndCleanRecognizer() {
        runCatching {
            recognizerRef?.stopListening()
            recognizerRef?.cancel()
            recognizerRef?.destroy()
        }
        recognizerRef = null
    }

    fun startInAppListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            launchSystemDialogFallback()
            return
        }
        stopAndCleanRecognizer()
        partialTranscript = ""
        statusLabel = "🎙 Đang nghe (Tiếng Việt)… Hãy nói vào micro"
        rmsDb = 2f
        listeningDialog = true

        runCatching {
            val sr = SpeechRecognizer.createSpeechRecognizer(context)
            recognizerRef = sr
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    statusLabel = "🎙 Đang lắng nghe bạn nói…"
                }
                override fun onBeginningOfSpeech() {
                    statusLabel = "🗣 Đang nhận diện giọng nói…"
                }
                override fun onRmsChanged(rmsdB: Float) {
                    rmsDb = rmsdB.coerceIn(0f, 12f)
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    statusLabel = "⏳ Đang xử lý câu nói…"
                }
                override fun onError(error: Int) {
                    if (partialTranscript.isNotBlank()) {
                        val finalTxt = partialTranscript.trim()
                        listeningDialog = false
                        stopAndCleanRecognizer()
                        latestOnText(finalTxt)
                        c.toast("Đã nhận giọng nói: \"$finalTxt\"")
                    } else {
                        listeningDialog = false
                        stopAndCleanRecognizer()
                        // Tự động chuyển sang hộp thoại hệ thống nếu dịch vụ ngầm báo lỗi
                        launchSystemDialogFallback()
                    }
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = matches?.firstOrNull()?.trim().orEmpty().ifEmpty { partialTranscript.trim() }
                    listeningDialog = false
                    stopAndCleanRecognizer()
                    if (best.isNotEmpty()) {
                        latestOnText(best)
                        c.toast("Đã nhận giọng nói: \"$best\"")
                    } else {
                        c.toast("Chưa nghe rõ, bạn hãy thử nói lại nhé.")
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val part = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                    if (!part.isNullOrEmpty()) {
                        partialTranscript = part
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "vi-VN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            sr.startListening(speechIntent)
        }.onFailure {
            listeningDialog = false
            stopAndCleanRecognizer()
            launchSystemDialogFallback()
        }
    }

    val askAudioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startInAppListening()
        else {
            c.toast("Chưa cấp quyền Micro. Đang mở hộp thoại nhận giọng nói hệ thống…")
            launchSystemDialogFallback()
        }
    }

    DisposableEffect(Unit) {
        onDispose { stopAndCleanRecognizer() }
    }

    if (listeningDialog) {
        AlertDialog(
            onDismissRequest = {
                listeningDialog = false
                stopAndCleanRecognizer()
            },
            containerColor = VC.card,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(VC.lime),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null, tint = Color.White)
                    }
                    Column {
                        Text("Nhận diện Giọng nói Tiếng Việt", color = VC.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        Text(statusLabel, color = VC.lime, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            text = {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Thanh sóng âm trực quan theo cường độ giọng nói (rmsDb)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(VC.elevated)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val factors = listOf(0.4f, 0.7f, 1.0f, 0.85f, 1.15f, 0.9f, 0.6f, 1.05f, 0.75f, 0.45f)
                        factors.forEach { f ->
                            val barH = min(44f, max(8f, (rmsDb + 2f) * 3.8f * f))
                            Box(
                                Modifier
                                    .width(6.dp)
                                    .height(barH.dp)
                                    .clip(CircleShape)
                                    .background(VC.lime),
                            )
                        }
                    }

                    // Ô hiển thị văn bản đang nhận diện tức thì
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(VC.elevated)
                            .border(1.dp, VC.border, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (partialTranscript.isBlank()) "Ví dụ: \"Vườn cần bón phân gì và xịt thuốc lưu lượng bao nhiêu?\" hoặc \"Cây SR-001 lá xanh tốt, có 68 trái\"…"
                            else partialTranscript,
                            color = if (partialTranscript.isBlank()) VC.muted else VC.text,
                            fontSize = if (partialTranscript.isBlank()) 13.5.sp else 16.sp,
                            fontWeight = if (partialTranscript.isBlank()) FontWeight.Normal else FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }

                    TextButton(onClick = {
                        listeningDialog = false
                        stopAndCleanRecognizer()
                        launchSystemDialogFallback()
                    }) {
                        Text("Chuyển sang hộp thoại Google Voice hệ thống", color = VC.teal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                VButton(
                    if (partialTranscript.isNotBlank()) "✓ Dùng câu này" else "Đang nghe…",
                    {
                        val finalTxt = partialTranscript.trim()
                        listeningDialog = false
                        stopAndCleanRecognizer()
                        if (finalTxt.isNotEmpty()) {
                            latestOnText(finalTxt)
                        }
                    },
                )
            },
            dismissButton = {
                TextButton(onClick = {
                    listeningDialog = false
                    stopAndCleanRecognizer()
                }) {
                    Text("Hủy", color = VC.text2)
                }
            },
        )
    }

    return {
        if (context.has(Manifest.permission.RECORD_AUDIO)) {
            startInAppListening()
        } else {
            askAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

// ---------- Đọc to văn bản bằng giọng nói Tiếng Việt (Text-To-Speech) ----------

class VoiceSpeakerState(
    val isSpeaking: Boolean,
    val speak: (String) -> Unit,
    val stop: () -> Unit,
)

@Composable
fun rememberVoiceSpeaker(c: VCtx): VoiceSpeakerState {
    val context = LocalContext.current
    var ttsRef by remember { mutableStateOf<TextToSpeech?>(null) }
    var ready by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val res = engine?.setLanguage(Locale("vi", "VN"))
                ready = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
                if (!ready) {
                    engine?.language = Locale.getDefault()
                    ready = true
                }
            }
        }
        ttsRef = engine
        onDispose {
            runCatching {
                engine?.stop()
                engine?.shutdown()
            }
            ttsRef = null
        }
    }

    return remember(ready, speaking) {
        VoiceSpeakerState(
            isSpeaking = speaking,
            speak = { rawText ->
                val engine = ttsRef
                if (engine == null || !ready) {
                    c.toast("Đang khởi tạo giọng đọc tiếng Việt, vui lòng bấm lại sau 1 giây.")
                } else {
                    val clean = rawText
                        .replace(Regex("[*#_`~•→]"), " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    engine.stop()
                    speaking = true
                    engine.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "vwing-tts-${System.currentTimeMillis()}")
                    c.toast("🔊 Đang đọc câu trả lời bằng giọng nói…")
                }
            },
            stop = {
                ttsRef?.stop()
                speaking = false
            },
        )
    }
}

// ---------- Chụp ảnh ----------

/** Chụp ảnh bằng ứng dụng camera của máy, rồi lưu như ảnh bằng chứng (ngày chụp lấy từ EXIF nếu có). */
@Composable
fun rememberCameraCapture(c: VCtx, onPicked: (List<Photo>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Uri?>(null) }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pending; pending = null
        if (ok && uri != null) scope.launch {
            val p = withContext(Dispatchers.IO) { c.store.importPhoto(uri) }
            if (p == null) c.toast("Không đọc được ảnh vừa chụp.") else onPicked(listOf(p))
        }
    }
    fun launchCamera() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val f = File(dir, UUID.randomUUID().toString() + ".jpg")
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
        pending = uri
        runCatching { take.launch(uri) }.onFailure { pending = null; c.toast("Không mở được camera.") }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else c.toast("Chưa được cấp quyền camera. Bạn vẫn có thể chọn ảnh từ thư viện.")
    }
    return { if (context.has(Manifest.permission.CAMERA)) launchCamera() else ask.launch(Manifest.permission.CAMERA) }
}
