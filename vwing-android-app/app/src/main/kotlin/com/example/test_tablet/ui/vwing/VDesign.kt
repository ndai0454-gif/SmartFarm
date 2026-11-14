package com.example.test_tablet.ui.vwing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.test_tablet.R
import kotlinx.coroutines.delay

/** Plus Jakarta Sans (font của thiết kế tham chiếu Open Intelligent UI, giấy phép OFL): đủ dấu tiếng Việt, sắc nét cho tiêu đề & số liệu lớn. */
val VDisplayFont = FontFamily(
    Font(R.font.plusjakartasans_regular, FontWeight.Normal),
    Font(R.font.plusjakartasans_medium, FontWeight.Medium),
    Font(R.font.plusjakartasans_semibold, FontWeight.SemiBold),
    Font(R.font.plusjakartasans_bold, FontWeight.Bold),
    Font(R.font.plusjakartasans_extrabold, FontWeight.ExtraBold),
)

/** Be Vietnam Pro: kiểu chữ vẽ riêng cho tiếng Việt (dấu thanh xếp chồng không bị chạm nhau), ấm và dễ đọc ở cỡ lớn ngoài nắng. */
val VFont = FontFamily(
    Font(R.font.bevietnampro_regular, FontWeight.Normal),
    Font(R.font.bevietnampro_medium, FontWeight.Medium),
    Font(R.font.bevietnampro_semibold, FontWeight.SemiBold),
    Font(R.font.bevietnampro_bold, FontWeight.Bold),
    Font(R.font.bevietnampro_extrabold, FontWeight.ExtraBold),
)

/**
 * Bấm có phản hồi theo chuẩn Mobile Design (touch-psychology.md §3 & §5):
 * - Phản hồi thị giác tức thì (<50ms): thu nhỏ nhẹ 0.96f khi nhấn rồi bật lại bằng lò xo + gợn sóng Material.
 * - Phản hồi xúc giác (Haptic Feedback): rung nhẹ xác nhận thao tác chạm ngoài thực địa.
 */
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val view = androidx.compose.ui.platform.LocalView.current
    val s by animateFloatAsState(if (pressed && enabled) .96f else 1f, spring(dampingRatio = .55f, stiffness = Spring.StiffnessMedium), label = "press")
    this.graphicsLayer { scaleX = s; scaleY = s }
        .clickable(
            interactionSource = src,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            },
        )
}

/** Hiện dần và trượt nhẹ lên khi mục xuất hiện lần đầu; [index] làm so le để danh sách nở ra mượt. */
fun Modifier.appear(index: Int = 0, offset: Dp = 18.dp): Modifier = composed {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay((index.coerceAtMost(10) * 45L)); shown = true }
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(420, easing = FastOutSlowInEasing), label = "appear")
    this.graphicsLayer { alpha = a; translationY = (1f - a) * offset.toPx() }
}

/** Huy hiệu biểu tượng tròn/bo góc dùng ở tiêu đề, thẻ số liệu và hàng danh sách. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color = VC.lime, bg: Color = VC.lime.copy(alpha = .12f), size: Dp = 40.dp, round: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(if (round) CircleShape else RoundedCornerShape(size * .32f)).background(bg),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * .54f)) }
}

/**
 * Số chạy từ 0 đến giá trị khi hiện ra (chỉ với chuỗi toàn số như "24" hoặc "1.254" hoặc "2,76"); chuỗi khác hiện nguyên văn.
 * Định dạng giữ đúng kiểu Việt Nam (chấm ngăn nghìn, phẩy thập phân).
 */
@Composable
fun AnimatedCount(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val tabularStyle = remember(style) { style.copy(fontFeatureSettings = "tnum") }
    val target = remember(text) { parseVnNumber(text) }
    if (target == null) { Text(text, style = tabularStyle, color = color, modifier = modifier); return }
    val anim = remember(text) { Animatable(0f) }
    LaunchedEffect(text) { anim.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val decimals = remember(text) { text.substringAfter(',', "").length }
    Text(fmtNum(target * anim.value, decimals), style = tabularStyle, color = color, modifier = modifier)
}

internal fun parseVnNumber(s: String): Double? {
    if (s.isEmpty() || !s.all { it.isDigit() || it == '.' || it == ',' }) return null
    if (s.count { it == ',' } > 1) return null
    val intPart = s.substringBefore(',')
    if (intPart.contains('.') && !Regex("""\d{1,3}(\.\d{3})+""").matches(intPart)) return null
    return (intPart.replace(".", "") + if (s.contains(',')) "." + s.substringAfter(',') else "").toDoubleOrNull()
}


/** Phản hồi xúc giác xác nhận thành công (Lưu sổ vườn, Quét trúng mã QR, Đọc giọng nói). */
fun performConfirmHaptic(view: android.view.View) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
    } else {
        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
    }
}

/** Kiểm tra chuỗi có bắt đầu bằng biểu tượng/emoji hay không để tránh gắn trùng cả Icon Vector lẫn Emoji trên cùng một nút. */
fun hasLeadingSymbolOrEmoji(text: String): Boolean {
    val trimmed = text.trimStart()
    if (trimmed.isEmpty()) return false
    val cp = trimmed.codePointAt(0)
    return !Character.isLetterOrDigit(cp)
}

/** Gợi ý biểu tượng theo nội dung nút để mọi nút đều có biểu tượng dễ hiểu (nút có biểu tượng riêng hoặc đã có emoji đầu dòng thì không dùng hàm này). */
fun inferIcon(text: String): ImageVector? {
    if (hasLeadingSymbolOrEmoji(text)) return null
    val t = text.lowercase()
    fun has(vararg k: String) = k.any { t.contains(it) }
    return when {
        has("quét qr") -> Icons.Filled.QrCodeScanner
        has("mã qr", "nhãn qr") -> Icons.Filled.QrCode2
        has("đồng bộ") -> Icons.Filled.Sync
        has("sao lưu") -> Icons.Filled.CloudUpload
        has("khôi phục", "hợp nhất") -> Icons.Filled.Restore
        has("xuất") -> Icons.Filled.FileUpload
        has("nhập") -> Icons.Filled.FileDownload
        has("chụp ảnh") -> Icons.Filled.PhotoCamera
        has("thêm ảnh") -> Icons.Filled.AddPhotoAlternate
        has("nói") -> Icons.Filled.Mic
        has("gps", "vị trí hiện tại") -> Icons.Filled.MyLocation
        has("tính ndvi", "tính từ") -> Icons.Filled.Calculate
        has("so sánh") -> Icons.Filled.Compare
        has("báo vấn đề", "ghi nhận vấn đề") -> Icons.Filled.ReportProblem
        has("ghép") -> Icons.Filled.Link
        has("bỏ qua") -> Icons.Filled.Block
        has("thay thế") -> Icons.Filled.SwapHoriz
        has("xóa") -> Icons.Filled.Delete
        has("sửa", "chỉnh") -> Icons.Filled.Edit
        has("lưu") -> Icons.Filled.Check
        has("hủy", "đóng") -> Icons.Filled.Close
        has("quay lại") -> Icons.AutoMirrored.Filled.ArrowBack
        has("bản đồ") -> Icons.Filled.Map
        has("mở hồ sơ", "hồ sơ") -> Icons.Filled.Park
        has("xem") -> Icons.Filled.Visibility
        has("gửi") -> Icons.AutoMirrored.Filled.Send
        has("thêm", "tạo") -> Icons.Filled.Add
        has("ghi") -> Icons.Filled.EditNote
        has("công việc") -> Icons.Filled.TaskAlt
        has("hoàn tất", "xác nhận") -> Icons.Filled.DoneAll
        has("tái kiểm tra") -> Icons.Filled.Replay
        has("bắt đầu") -> Icons.Filled.PlayArrow
        has("chọn") -> Icons.Filled.TouchApp
        has("chạm") -> Icons.Filled.TouchApp
        else -> null
    }
}
