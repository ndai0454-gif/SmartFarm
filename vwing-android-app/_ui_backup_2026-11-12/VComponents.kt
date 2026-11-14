package com.example.test_tablet.ui.vwing

import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.Shapes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.Condition
import com.example.test_tablet.data.vwing.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VC {
    /**
     * Chế độ giao diện theo Gói dịch vụ:
     * - `isPro = false` (Gói Free): Giao diện Trắng sáng ban ngày (Clean Daylight White #F5F8F6 / #FFFFFF, nút xanh rừng đậm #0F2B1D).
     * - `isPro = true` (Gói Pro / Enterprise): Giao diện Aurora Liquid-Glass Dark (#04121B / #0A1E2B, hiệu ứng kính mờ & bão đêm).
     */
    var isPro by mutableStateOf(false)

    val bg: Color get() = if (isPro) Color(0xFF04121B) else Color(0xFFF5F8F6)
    val sidebar: Color get() = if (isPro) Color(0xFF071824) else Color(0xFFFFFFFF)
    val card: Color get() = if (isPro) Color(0xFF0A1E2B) else Color(0xFFFFFFFF)
    val elevated: Color get() = if (isPro) Color(0xFF102A3A) else Color(0xFFEEF4F0)
    val active: Color get() = if (isPro) Color(0xFF15384C) else Color(0xFFE2ECE7)
    val border: Color get() = if (isPro) Color(0x52FFFFFF) else Color(0xFFB8CEC3)
    val line: Color get() = if (isPro) Color(0x2EFFFFFF) else Color(0xFFD5E2DC)
    val lime: Color get() = if (isPro) Color(0xFFA8DF65) else Color(0xFF15803D)
    val onLime: Color get() = if (isPro) Color(0xFF051810) else Color(0xFFFFFFFF)
    val teal: Color get() = if (isPro) Color(0xFF38BDF8) else Color(0xFF0284C7)
    val mint: Color get() = if (isPro) Color(0xFF38EF7D) else Color(0xFF16794B)
    val cyan: Color get() = if (isPro) Color(0xFF38BDF8) else Color(0xFF0284C7)
    val amber: Color get() = if (isPro) Color(0xFFFBBF24) else Color(0xFFB45309)
    val red: Color get() = if (isPro) Color(0xFFFF6B6B) else Color(0xFFC83232)
    val text: Color get() = if (isPro) Color(0xFFF4F8FA) else Color(0xFF0F2419)
    val text2: Color get() = if (isPro) Color(0xFFE4F0F5) else Color(0xFF284235)
    val muted: Color get() = if (isPro) Color(0xFFBED5DF) else Color(0xFF3B594B)
    val gold: Color get() = if (isPro) Color(0xFFA8DF65) else Color(0xFFB45309)
    val rail: Color get() = if (isPro) Color(0xFF071824) else Color(0xFFFFFFFF)
    val onRail: Color get() = if (isPro) Color(0xFFFFFFFF) else Color(0xFF0F2419)
    val onRailMuted: Color get() = if (isPro) Color(0xFFD5E6EC) else Color(0xFF4E6B5D)
    val heroA: Color get() = if (isPro) Color(0xFF0F3B29) else Color(0xFF0F2B1D)
    val heroB: Color get() = if (isPro) Color(0xFF145239) else Color(0xFF184530)
    val onHero: Color get() = Color(0xFFFFFFFF)
    // Bảng màu bổ sung
    val pine: Color get() = if (isPro) Color(0xFF0F3B29) else Color(0xFF0F2B1D)
    val forestCard: Color get() = if (isPro) Color(0xFF0C2432) else Color(0xFFFFFFFF)
    val woodland: Color get() = if (isPro) Color(0xFF38EF7D) else Color(0xFF16794B)
    val moss: Color get() = if (isPro) Color(0xFFA8DF65) else Color(0xFF15803D)
    val neon: Color get() = if (isPro) Color(0xFF5CE67E) else Color(0xFF16A34A)
    val sage: Color get() = if (isPro) Color(0xFF94B4C2) else Color(0xFF527062)
    val shadow: Color get() = if (isPro) Color(0xFF020A10) else Color(0x140F2419)
}

@Composable
private fun currentVScheme() = if (VC.isPro) {
    darkColorScheme(
        primary = VC.lime, onPrimary = VC.onLime, secondary = VC.teal, background = VC.bg, onBackground = VC.text,
        surface = VC.card, onSurface = VC.text, surfaceVariant = VC.elevated, onSurfaceVariant = VC.text2,
        outline = VC.border, error = VC.red, surfaceContainer = VC.card, surfaceContainerHigh = VC.elevated,
    )
} else {
    lightColorScheme(
        primary = VC.pine, onPrimary = Color.White, secondary = VC.mint, background = VC.bg, onBackground = VC.text,
        surface = VC.card, onSurface = VC.text, surfaceVariant = VC.elevated, onSurfaceVariant = VC.text2,
        outline = VC.border, error = VC.red, surfaceContainer = VC.card, surfaceContainerHigh = VC.elevated,
    )
}

@Composable
fun VWingTheme(content: @Composable () -> Unit) {
    val view = androidx.compose.ui.platform.LocalView.current
    val isProTheme = VC.isPro
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            runCatching {
                val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !isProTheme
                insetsController.isAppearanceLightNavigationBars = !isProTheme
            }
        }
    }
    val base = com.example.test_tablet.ui.theme.Typography
    val typo = androidx.compose.material3.Typography(
        displayLarge = base.displayLarge.copy(fontFamily = VDisplayFont, fontFeatureSettings = "tnum"),
        displayMedium = base.displayMedium.copy(fontFamily = VDisplayFont, fontFeatureSettings = "tnum"),
        displaySmall = base.displaySmall.copy(fontFamily = VDisplayFont, fontFeatureSettings = "tnum"),
        headlineLarge = base.headlineLarge.copy(fontFamily = VDisplayFont, fontFeatureSettings = "tnum"),
        headlineMedium = base.headlineMedium.copy(fontFamily = VDisplayFont, fontFeatureSettings = "tnum"),
        headlineSmall = base.headlineSmall.copy(fontFamily = VDisplayFont, fontFeatureSettings = "tnum"),
        titleLarge = base.titleLarge.copy(fontFamily = VFont, fontFeatureSettings = "tnum"),
        titleMedium = base.titleMedium.copy(fontFamily = VFont, fontFeatureSettings = "tnum"),
        titleSmall = base.titleSmall.copy(fontFamily = VFont, fontFeatureSettings = "tnum"),
        bodyLarge = base.bodyLarge.copy(fontFamily = VFont, fontFeatureSettings = "tnum"),
        bodyMedium = base.bodyMedium.copy(fontFamily = VFont, fontFeatureSettings = "tnum"),
        bodySmall = base.bodySmall.copy(fontFamily = VFont, fontFeatureSettings = "tnum"),
        labelLarge = base.labelLarge.copy(fontFamily = VFont, fontFeatureSettings = "tnum"),
        labelMedium = base.labelMedium.copy(fontFamily = VFont, fontFeatureSettings = "tnum"),
        labelSmall = base.labelSmall.copy(fontFamily = VFont, fontFeatureSettings = "tnum"),
    )
    val shapes = androidx.compose.material3.Shapes(
        small = RoundedCornerShape(6.dp), medium = RoundedCornerShape(10.dp), large = RoundedCornerShape(14.dp), extraLarge = RoundedCornerShape(20.dp),
    )
    MaterialTheme(colorScheme = currentVScheme(), typography = typo, shapes = shapes, content = content)
}

fun conditionColor(c: Condition): Color = when (c) {
    Condition.STABLE -> VC.mint
    Condition.WATCH -> VC.amber
    Condition.NEEDS_CHECK -> VC.red
    Condition.UNASSESSED -> VC.text2
}

fun conditionMark(c: Condition): String = when (c) {
    Condition.STABLE -> "✓"; Condition.WATCH -> "~"; Condition.NEEDS_CHECK -> "!"; Condition.UNASSESSED -> "?"
}

/**
 * Aurora Liquid-Glass Card:
 * Nền kính tối chuyển sắc dọc (#0C2230 -> #091B26 -> #071620) cùng viền kính mảnh 1dp sắc nét,
 * bo góc 22dp đồng bộ tuyệt đối với các thẻ trong AuroraSmartFarmDashboard.
 */
@Composable
fun VCard(
    modifier: Modifier = Modifier, bg: Color = VC.card, borderColor: Color = VC.border,
    padding: PaddingValues = PaddingValues(18.dp), onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit,
) {
    val shape = LeafShape
    val bgMod = if (VC.isPro && bg == VC.card) {
        Modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0C2230).copy(alpha = 0.92f),
                    Color(0xFF091B26).copy(alpha = 0.90f),
                    Color(0xFF071620).copy(alpha = 0.92f),
                )
            )
        )
    } else {
        Modifier.background(bg)
    }
    Column(
        modifier
            .clip(shape)
            .then(bgMod)
            .border(1.2.dp, borderColor, shape)
            .then(if (onClick != null) Modifier.bounceClick(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Bán kính bo góc chuẩn Aurora Liquid-Glass: thẻ 22dp, nút/viên thuốc 14dp. */
val LeafShape = RoundedCornerShape(22.dp)
val LeafShapeSmall = RoundedCornerShape(14.dp)

private val ACRONYMS = mapOf("gis" to "GIS", "qr" to "QR", "ndvi" to "NDVI", "sms" to "SMS", "otp" to "OTP", "kml" to "KML", "gpx" to "GPX", "csv" to "CSV",
    "ai" to "AI", "gps" to "GPS", "dji" to "DJI", "rustgis" to "RustGIS", "vwing" to "VWing", "lidar" to "LiDAR", "evi" to "EVI", "ndmi" to "NDMI", "zip" to "zip",
    "3d" to "3D", "gee" to "GEE", "tof" to "TOF", "iot" to "IoT", "rtk" to "RTK", "gnss" to "GNSS", "dxf" to "DXF", "dem" to "DEM", "vra" to "VRA", "rgb" to "RGB",
    "npk" to "NPK", "bvtv" to "BVTV", "ph" to "pH", "ec" to "EC", "te" to "TE", "sop" to "SOP")

/** Nhãn viết hoa toàn bộ (kiểu cũ) → viết câu thường dễ đọc; giữ nguyên chữ viết tắt (GIS, QR, NDVI…). Chữ đã viết thường thì để nguyên. */
fun humanize(s: String): String {
    if (s.isEmpty() || s != s.uppercase()) return s
    val words = s.lowercase().split(' ').map { w -> ACRONYMS[w.trim('&', ',', '.', '·', '(', ')')]?.let { a -> w.replace(w.trim('&', ',', '.', '·', '(', ')'), a) } ?: w }
    val joined = words.joinToString(" ")
    val idx = joined.indexOfFirst { it.isLetter() }
    return if (idx >= 0) joined.substring(0, idx) + joined[idx].uppercase() + joined.substring(idx + 1) else joined
}

/**
 * Taste Skill §9.1 & Impeccable Craft Floor:
 * Tiêu đề thẻ dùng chữ thường đầu câu (Sentence case), cỡ 16sp SemiBold, tuyệt đối không dùng chữ hoa toàn bộ (UPPERCASE eyebrow).
 */
@Composable
fun Eyebrow(text: String, color: Color = VC.text) {
    Text(humanize(text), color = color, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun SectionLabel(text: String) {
    Text(humanize(text), color = VC.text2, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun PageHeader(eyebrow: String, title: String, desc: String, icon: ImageVector? = null, actions: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit = {}) {
    if (VUi.simpleMode) {
        // Chế độ Nông dân vẫn cần tiêu đề trang (h1) để người dùng biết mình đang ở đâu;
        // chỉ ẩn phần chú giải dài, giữ mô tả ngắn gọn 1–2 dòng.
        Column(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = VC.text, lineHeight = 27.sp, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (desc.isNotBlank()) Text(desc, fontSize = 14.5.sp, color = VC.text2, lineHeight = 20.sp, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp).appear(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = VC.text, lineHeight = 30.sp)
            Text(desc, fontSize = 14.5.sp, color = VC.text2, lineHeight = 21.sp)
            FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun PageHeader(icon: ImageVector, eyebrow: String, title: String, desc: String, actions: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit = {}) =
    PageHeader(eyebrow, title, desc, icon, actions)

@Composable
fun VButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true,
    icon: ImageVector? = null, danger: Boolean = false,
) {
    val shape = LeafShapeSmall
    val pad = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
    val icon = icon ?: inferIcon(text)
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val view = androidx.compose.ui.platform.LocalView.current
    val hapticClick: () -> Unit = {
        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        onClick()
    }
    val scale by animateFloatAsState(if (pressed && enabled) .96f else 1f, spring(dampingRatio = .55f, stiffness = Spring.StiffnessMedium), label = "btn")
    val m = modifier.heightIn(min = 48.dp).graphicsLayer { scaleX = scale; scaleY = scale }
    if (primary) {
        val accentBorder = if (danger) VC.red.copy(alpha = 0.65f) else if (VC.isPro) VC.mint.copy(alpha = 0.55f) else VC.pine
        val containerCol = if (danger) (if (VC.isPro) Color(0xFF3B1219) else Color(0xFFFEE2E2)) else VC.pine
        val fgCol = if (danger && !VC.isPro) VC.red else Color.White
        val iconCol = if (danger) VC.red else if (VC.isPro) VC.mint else Color.White
        OutlinedButton(hapticClick, m, enabled, shape = shape, contentPadding = pad, interactionSource = src,
            border = BorderStroke(1.dp, if (enabled) accentBorder else VC.border),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = containerCol,
                contentColor = fgCol,
                disabledContainerColor = VC.elevated,
                disabledContentColor = VC.muted,
            )) {
            if (icon != null) { Icon(icon, null, Modifier.size(18.dp), tint = iconCol); Spacer(Modifier.size(8.dp)) }
            Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    } else {
        OutlinedButton(hapticClick, m, enabled, shape = shape, contentPadding = pad, interactionSource = src,
            border = BorderStroke(1.dp, if (danger) VC.red.copy(alpha = .7f) else VC.border),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (danger) VC.red else VC.text, containerColor = VC.card, disabledContentColor = VC.muted)) {
            if (icon != null) { Icon(icon, null, Modifier.size(18.dp), tint = if (danger) VC.red else if (VC.isPro) VC.lime else VC.text); Spacer(Modifier.size(8.dp)) }
            Text(text, fontWeight = FontWeight.Medium, fontSize = 15.sp)
        }
    }
}

/**
 * Aurora Liquid-Glass Status Pill:
 * Nền kính bán trong suốt + viền màu phát sáng 1dp + chấm tròn trạng thái đồng bộ với AuroraStageStatusBar.
 */
@Composable
fun Tag(text: String, color: Color = VC.mint, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier
            .clip(RoundedCornerShape(19.dp))
            .background(color.copy(alpha = .16f))
            .border(1.dp, color.copy(alpha = .45f), RoundedCornerShape(19.dp))
            .padding(horizontal = 11.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
        else Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ConditionTag(c: Condition) = Tag(c.label, conditionColor(c), icon = when (c) {
    Condition.STABLE -> Icons.Filled.Check; Condition.WATCH -> Icons.Filled.Visibility; Condition.NEEDS_CHECK -> Icons.Filled.PriorityHigh; Condition.UNASSESSED -> Icons.Filled.HelpOutline
})

@Composable
fun SyncTag(s: SyncState) = Tag(s.label, when (s) {
    SyncState.SYNCED -> VC.mint; SyncState.LOCAL -> VC.cyan; SyncState.PENDING -> VC.amber; SyncState.ERROR -> VC.red
})

@Composable
fun Notice(text: String, color: Color = VC.teal, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(LeafShapeSmall)
            .background(color.copy(alpha = .08f))
            .border(1.dp, color.copy(alpha = .22f), LeafShapeSmall)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                if (color == VC.red || color == VC.amber) Icons.Filled.WarningAmber else Icons.Filled.Info, null,
                tint = color, modifier = Modifier.size(20.dp),
            )
            Text(text, Modifier.weight(1f), color = VC.text2, fontSize = 14.sp, lineHeight = 20.sp)
        }
        trailing?.let { t -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { t() } }
    }
}

/**
 * Taste Skill §5 KPIs:
 * "KPIs are type, not tiles. No screen puts an icon in a coloured circle on a KPI...
 * Label above the value; labels grey, sentence case; values 24-30px in ink with tabular figures, units as a smaller grey suffix."
 */
@Composable
fun Kpi(title: String, value: String, unit: String, sub: String, modifier: Modifier = Modifier, warn: Boolean = false, icon: ImageVector? = null, index: Int = 0) {
    VCard(modifier.appear(index), borderColor = if (warn) VC.amber.copy(alpha = .45f) else VC.border, padding = PaddingValues(16.dp)) {
        Text(humanize(title), fontSize = 13.sp, color = VC.muted, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
            AnimatedCount(value, androidx.compose.ui.text.TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = VDisplayFont, fontFeatureSettings = "tnum"), if (warn) VC.amber else VC.text)
            if (unit.isNotEmpty()) Text(" $unit", fontSize = 13.sp, color = VC.muted, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 3.dp))
        }
        Text(sub, fontSize = 12.5.sp, color = if (warn) VC.amber else VC.text2, modifier = Modifier.padding(top = 4.dp))
    }
}

data class TasteKpiCell(
    val label: String,
    val value: String,
    val unit: String = "",
    val sub: String = "",
    val accentColor: Color? = null,
    val onClick: (() -> Unit)? = null,
)

/**
 * Taste Skill §5 Divided KPI Strip:
 * Một khung chứa duy nhất chia bằng đường kẻ dọc 1dp (`VC.line`), nhãn xám phía trên,
 * chữ số đẳng rộng (`tnum`) phía dưới, không lạm dụng ô màu rời rạc.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasteKpiStrip(
    cells: List<TasteKpiCell>,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    val glassBg = if (VC.isPro) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0C2230).copy(alpha = 0.92f),
                Color(0xFF091B26).copy(alpha = 0.90f),
                Color(0xFF071620).copy(alpha = 0.92f),
            )
        )
    } else {
        Brush.verticalGradient(colors = listOf(Color.White, Color.White))
    }
    if (wide) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(LeafShape)
                .background(glassBg)
                .border(1.dp, VC.border, LeafShape),
        ) {
            cells.forEachIndexed { idx, cell ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(if (cell.onClick != null) Modifier.bounceClick(onClick = cell.onClick) else Modifier)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        humanize(cell.label),
                        color = VC.muted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            cell.value,
                            color = cell.accentColor ?: VC.text,
                            fontFamily = VDisplayFont,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        if (cell.unit.isNotEmpty()) {
                            Text(
                                cell.unit,
                                color = VC.lime,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 3.dp),
                            )
                        }
                    }
                    if (cell.sub.isNotEmpty()) {
                        Text(
                            cell.sub,
                            color = VC.text2,
                            fontSize = 12.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                if (idx < cells.lastIndex) {
                    Box(
                        Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(VC.line)
                    )
                }
            }
        }
    } else {
        // Trên điện thoại (Taste Skill §12): lưới 2 cột chia bằng đường kẻ 1dp
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(LeafShape)
                .background(glassBg)
                .border(1.dp, VC.border, LeafShape),
        ) {
            cells.chunked(2).forEachIndexed { rIdx, pair ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    pair.forEachIndexed { cIdx, cell ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .then(if (cell.onClick != null) Modifier.bounceClick(onClick = cell.onClick) else Modifier)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Text(humanize(cell.label), color = VC.muted, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    cell.value,
                                    color = cell.accentColor ?: VC.text,
                                    fontFamily = VDisplayFont,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                                if (cell.unit.isNotEmpty()) {
                                    Text(cell.unit, color = VC.lime, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 2.dp))
                                }
                            }
                            if (cell.sub.isNotEmpty()) {
                                Text(cell.sub, color = VC.text2, fontSize = 11.5.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
                            }
                        }
                        if (cIdx == 0 && pair.size > 1) {
                            Box(Modifier.width(1.dp).fillMaxHeight().background(VC.line))
                        }
                    }
                }
                if (rIdx < (cells.size - 1) / 2) {
                    Divider2()
                }
            }
        }
    }
}

@Composable
fun VField(
    value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    singleLine: Boolean = true, minLines: Int = 1, number: Boolean = false, error: String? = null, placeholder: String? = null,
    enabled: Boolean = true, secret: Boolean = false,
) {
    OutlinedTextField(
        value, onChange, modifier.fillMaxWidth(), enabled = enabled,
        visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        label = { Text(label, fontSize = 13.5.sp) }, singleLine = singleLine, minLines = minLines,
        placeholder = placeholder?.let { { Text(it, fontSize = 14.sp, color = VC.muted) } },
        isError = error != null, supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else if (secret) KeyboardType.Password else KeyboardType.Text),
        shape = LeafShapeSmall,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = VC.text, unfocusedTextColor = VC.text, disabledTextColor = VC.text2,
            focusedBorderColor = VC.lime, unfocusedBorderColor = VC.border, cursorColor = VC.lime,
            focusedLabelColor = VC.lime, unfocusedLabelColor = VC.text2,
            errorBorderColor = VC.red, errorLabelColor = VC.red, errorSupportingTextColor = VC.red,
            focusedContainerColor = VC.card, unfocusedContainerColor = VC.card,
        ),
    )
}

@Composable
fun VPicker(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, fontSize = 13.5.sp, color = VC.text2, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
        Box {
            OutlinedButton(
                { open = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = LeafShapeSmall, border = BorderStroke(1.dp, VC.border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VC.text, containerColor = VC.card),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(selected.ifEmpty { "Chọn…" }, Modifier.weight(1f), maxLines = 1, fontSize = 15.sp)
                Icon(Icons.Filled.ArrowDropDown, null, tint = VC.text2)
            }
            DropdownMenu(open, { open = false }, containerColor = VC.card) {
                options.forEach { o -> DropdownMenuItem({ Text(o, color = VC.text) }, { onSelect(o); open = false }) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier, icon: ((T) -> ImageVector?)? = null) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            val sel = o == selected
            val bg by animateColorAsState(if (sel) VC.pine else VC.card, tween(200), label = "chipBg")
            val fg by animateColorAsState(if (sel) Color.White else if (VC.isPro) VC.text2 else VC.text, tween(200), label = "chipFg")
            val borderCol by animateColorAsState(if (sel) (if (VC.isPro) VC.mint.copy(alpha = 0.65f) else VC.pine) else VC.border, tween(200), label = "chipBorder")
            Row(
                Modifier.heightIn(min = 48.dp).clip(LeafShapeSmall).background(bg).border(1.dp, borderCol, LeafShapeSmall).bounceClick { onSelect(o) }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                val ic = icon?.invoke(o)
                if (ic != null) Icon(ic, null, tint = if (sel) (if (VC.isPro) VC.mint else Color.White) else fg, modifier = Modifier.size(16.dp))
                else if (VC.isPro) Box(Modifier.size(7.dp).clip(CircleShape).background(if (sel) VC.mint else VC.teal.copy(alpha = 0.75f)))
                Text(label(o), fontSize = 14.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium, color = fg)
            }
        }
    }
}

/**
 * Hàng lựa chọn cuộn ngang kiểu Aurora Glowing Pill (ở Pro) và Pill trắng/xanh rừng đậm (ở Free):
 * mỗi ô ≥48dp, chữ 15sp.
 */
@Composable
fun ScrollChipRow(options: List<String>, selected: String, label: (String) -> String = { it }, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { o ->
            val sel = o == selected
            val bg by animateColorAsState(if (sel) VC.pine else VC.card, tween(200), label = "scrollChipBg")
            val fg by animateColorAsState(if (sel) Color.White else if (VC.isPro) VC.text2 else VC.text, tween(200), label = "scrollChipFg")
            val borderCol by animateColorAsState(if (sel) (if (VC.isPro) VC.mint.copy(alpha = 0.65f) else VC.pine) else VC.border, tween(200), label = "scrollChipBorder")
            Row(
                Modifier.heightIn(min = 48.dp).clip(LeafShapeSmall).background(bg).border(1.dp, borderCol, LeafShapeSmall)
                    .bounceClick { onSelect(o) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (VC.isPro) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (sel) VC.mint else VC.teal.copy(alpha = 0.75f)))
                }
                Text(label(o), fontSize = 15.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium, color = fg, maxLines = 1)
            }
        }
    }
}

@Composable
fun EmptyNote(text: String, icon: ImageVector = Icons.Outlined.Inbox) {
    Column(Modifier.fillMaxWidth().padding(vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IconBadge(icon, tint = VC.muted, bg = VC.elevated, size = 52.dp, round = true)
        Text(text, color = VC.muted, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 22.sp)
    }
}

@Composable
fun Divider2() = Box(Modifier.fillMaxWidth().height(1.dp).background(VC.line))

/** Ký hiệu hình dạng theo tình trạng (không chỉ dựa vào màu): tròn=ổn định, tam giác=theo dõi, thoi=cần kiểm tra, vòng rỗng=chưa đánh giá. */
@Composable
fun ConditionShape(c: Condition, size: androidx.compose.ui.unit.Dp = 14.dp) {
    Canvas(Modifier.size(size)) {
        drawConditionShape(c, Offset(this.size.width / 2, this.size.height / 2), this.size.width / 2 * .9f)
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawConditionShape(c: Condition, center: Offset, r: Float) {
    val col = conditionColor(c)
    when (c) {
        Condition.STABLE -> drawCircle(col, r, center)
        Condition.UNASSESSED -> drawCircle(col, r, center, style = Stroke(r * .35f))
        Condition.WATCH -> drawPath(Path().apply {
            moveTo(center.x, center.y - r); lineTo(center.x + r, center.y + r * .8f); lineTo(center.x - r, center.y + r * .8f); close()
        }, col)
        Condition.NEEDS_CHECK -> drawPath(Path().apply {
            moveTo(center.x, center.y - r * 1.1f); lineTo(center.x + r * 1.1f, center.y); lineTo(center.x, center.y + r * 1.1f); lineTo(center.x - r * 1.1f, center.y); close()
        }, col)
    }
}

@Composable
fun rememberBitmap(path: String, maxPx: Int = 480): androidx.compose.ui.graphics.ImageBitmap? {
    val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            try {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, o)
                var s = 1
                while (o.outWidth / (s * 2) >= maxPx || o.outHeight / (s * 2) >= maxPx) s *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
            } catch (_: Throwable) { null }
        }
    }
    return bmp
}

@Composable
fun PhotoThumb(path: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 72.dp, onClick: (() -> Unit)? = null) {
    val bmp = rememberBitmap(path)
    Box(
        modifier.size(size).clip(RoundedCornerShape(10.dp)).background(VC.elevated)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) Image(bmp, null, Modifier.size(size), contentScale = ContentScale.Crop)
        else Text("Ảnh", color = VC.muted, fontSize = 13.sp)
    }
}

fun Modifier.dot(color: Color, size: androidx.compose.ui.unit.Dp = 8.dp): Modifier = this.size(size).clip(CircleShape).background(color)

/**
 * Bọc nội dung kỹ thuật (số liệu/thuật ngữ chuyên môn) sau nút "Xem chi tiết kỹ thuật", chỉ khi
 * [simpleMode] đang bật (xem VCtx.simpleMode, bật ở Thiết lập vườn). Khi tắt chế độ đơn giản,
 * hiện toàn bộ nội dung ngay — không ẩn hay xóa dữ liệu nào, chỉ thay thứ tự hiển thị.
 */
@Composable
fun TechnicalDetails(simpleMode: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    if (!simpleMode) { Column(modifier, content = content); return }
    var open by remember { mutableStateOf(false) }
    Column(modifier.animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable { open = !open }.padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (open) "Ẩn chi tiết kỹ thuật" else "Xem chi tiết kỹ thuật", color = VC.teal, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = VC.teal, modifier = Modifier.size(20.dp))
        }
        if (open) Column(content = content)
    }
}

enum class Verdict { GOOD, WARN, BAD, UNKNOWN }

/** Banner kết luận bằng ngôn ngữ thường, dùng ở chế độ đơn giản trước khi vào chi tiết kỹ thuật. */
@Composable
fun VerdictBanner(verdict: Verdict, headline: String, subtext: String? = null, modifier: Modifier = Modifier) {
    val color = when (verdict) { Verdict.GOOD -> VC.mint; Verdict.WARN -> VC.amber; Verdict.BAD -> VC.red; Verdict.UNKNOWN -> VC.muted }
    val icon = when (verdict) { Verdict.GOOD -> Icons.Filled.CheckCircle; Verdict.WARN -> Icons.Filled.WarningAmber; Verdict.BAD -> Icons.Filled.Error; Verdict.UNKNOWN -> Icons.Filled.HelpOutline }
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(color.copy(alpha = .11f)).border(1.dp, color.copy(alpha = .32f), shape).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
        Column { Text(headline, color = VC.text, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 17.sp); subtext?.let { Text(it, color = VC.text2, fontSize = 14.sp) } }
    }
}
