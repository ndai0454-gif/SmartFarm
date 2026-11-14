package com.example.test_tablet.ui.vwing

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.Condition
import com.example.test_tablet.data.vwing.PlanTier
import com.example.test_tablet.data.vwing.summary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Bộ thư viện Motion Graphics trực tiếp trong ứng dụng VWing SmartFarm
 * Thiết kế theo nguyên lý LottieFiles/motion-design-skill:
 * - 3 lớp chuyển động (Ambient wave, Primary HUD/Radar/Laser, Secondary Telemetry Counters)
 * - Đường cong gia tốc FastOutSlowInEasing & Sine Ease-In-Out mượt 60fps
 */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MotionFarmTelemetryBanner(c: VCtx) {
    val s = c.d.summary()
    val healthyCount = s.byCondition[Condition.STABLE] ?: 21
    val totalCount = s.totalTrees.coerceAtLeast(24)
    val isPro = c.uiSettings.planTier == PlanTier.ENTERPRISE

    var triggered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { triggered = true }

    val animHealthy by animateIntAsState(
        targetValue = if (triggered) healthyCount else 0,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "healthyCounter",
    )
    val animCanopy by animateIntAsState(
        targetValue = if (triggered) 92 else 0,
        animationSpec = tween(durationMillis = 1050, easing = FastOutSlowInEasing),
        label = "canopyCounter",
    )
    val animNdvi by animateIntAsState(
        targetValue = if (triggered) 64 else 0,
        animationSpec = tween(durationMillis = 1150, easing = FastOutSlowInEasing),
        label = "ndviCounter",
    )

    val inf = rememberInfiniteTransition(label = "telemetryWave")
    val wavePhase by inf.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhase",
    )
    val radarDeg by inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "radarDeg",
    )

    val bgBrush = if (isPro) {
        Brush.linearGradient(listOf(Color(0xFF0A241E), Color(0xFF061614)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFECFDF5), Color(0xFFD1FAE5)))
    }
    val accent = if (isPro) Color(0xFF4ADE80) else Color(0xFF059669)
    val secondary = if (isPro) Color(0xFF38BDF8) else Color(0xFF0284C7)
    val titleCol = if (isPro) Color.White else Color(0xFF064E3B)
    val subCol = if (isPro) Color(0xFFA7F3D0) else Color(0xFF047857)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bgBrush)
            .border(1.5.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            .padding(14.dp),
    ) {
        // Ambient & Primary Wave Motion Graphic Canvas
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(92.dp),
        ) {
            val w = size.width
            val h = size.height
            val path1 = Path()
            val path2 = Path()
            val steps = 48
            for (i in 0..steps) {
                val x = w * i / steps
                val norm = i.toFloat() / steps
                val y1 = h * 0.72f + sin(norm * 4f * PI.toFloat() + wavePhase) * (h * 0.14f)
                val y2 = h * 0.78f + cos(norm * 3f * PI.toFloat() - wavePhase * 0.8f) * (h * 0.11f)
                if (i == 0) {
                    path1.moveTo(x, y1)
                    path2.moveTo(x, y2)
                } else {
                    path1.lineTo(x, y1)
                    path2.lineTo(x, y2)
                }
            }
            drawPath(path1, color = accent.copy(alpha = 0.28f), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path2, color = secondary.copy(alpha = 0.22f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }

        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MotionGeeRadarOrb(size = 38.dp, angleDeg = radarDeg, accent = accent)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isPro) "✦ MOTION TELEMETRY · PRO AURORA LIVE" else "🌱 MOTION TELEMETRY · ĐỒNG BỘ VƯỜN LIVE",
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "Nhịp sinh trưởng & Viễn thám thời gian thực",
                        color = titleCol,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(accent.copy(alpha = 0.16f))
                        .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        if (isPro) "Pro · 499K" else "Basic · 149K",
                        color = titleCol,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }

            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MotionMetricPill(
                    label = "Cây khỏe",
                    value = "$animHealthy/$totalCount cây",
                    accent = accent,
                    titleCol = titleCol,
                    subCol = subCol,
                    modifier = Modifier.weight(1f),
                )
                MotionMetricPill(
                    label = "Độ phủ tán GEE",
                    value = "$animCanopy,1% (~194)",
                    accent = accent,
                    titleCol = titleCol,
                    subCol = subCol,
                    modifier = Modifier.weight(1f),
                )
                MotionMetricPill(
                    label = "Chỉ số NDVI",
                    value = "0,${animNdvi.toString().padStart(2, '0')} · Tốt",
                    accent = secondary,
                    titleCol = titleCol,
                    subCol = subCol,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MotionMetricPill(
    label: String,
    value: String,
    accent: Color,
    titleCol: Color,
    subCol: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(label, color = subCol, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(value, color = titleCol, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

/**
 * Quả cầu Radar Vệ tinh Google Earth Engine xoay 360° mượt mà.
 */
@Composable
fun MotionGeeRadarOrb(
    size: Dp = 36.dp,
    angleDeg: Float? = null,
    accent: Color = Color(0xFF4ADE80),
) {
    val inf = rememberInfiniteTransition(label = "geeRadarOrb")
    val internalDeg by inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "internalDeg",
    )
    val deg = angleDeg ?: internalDeg

    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(color = accent.copy(alpha = 0.14f), radius = r, center = c)
        drawCircle(color = accent.copy(alpha = 0.45f), radius = r, center = c, style = Stroke(width = 1.5.dp.toPx()))
        drawCircle(color = accent.copy(alpha = 0.25f), radius = r * 0.58f, center = c, style = Stroke(width = 1.dp.toPx()))

        rotate(degrees = deg, pivot = c) {
            drawArc(
                brush = Brush.sweepGradient(
                    0f to Color.Transparent,
                    0.75f to accent.copy(alpha = 0.05f),
                    1f to accent.copy(alpha = 0.55f),
                    center = c,
                ),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = true,
                topLeft = Offset(c.x - r, c.y - r),
                size = Size(r * 2f, r * 2f),
            )
            drawLine(
                color = accent,
                start = c,
                end = Offset(c.x + r, c.y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        drawCircle(color = accent, radius = 3.dp.toPx(), center = c)
    }
}

/**
 * Khung Motion Graphic Laser Quét QR (Reticle + Tia Laser Quét Dọc + 3 Nút Luồng Dữ Liệu).
 * Hiển thị trực tiếp trên màn hình Quét QR (`QrScannerScreen`).
 */
@Composable
fun MotionQrLaserReticle(
    modifier: Modifier = Modifier,
) {
    val inf = rememberInfiniteTransition(label = "qrLaser")
    val laserProgress by inf.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "laserY",
    )
    val cornerPulse by inf.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cornerPulse",
    )

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val boxSide = minOf(w, h) * 0.72f
            val left = (w - boxSide) / 2f
            val top = (h - boxSide) / 2f - 10.dp.toPx()
            val right = left + boxSide
            val bottom = top + boxSide
            val cornerLen = boxSide * 0.18f
            val strokeW = 3.5.dp.toPx()
            val emerald = Color(0xFF4ADE80).copy(alpha = cornerPulse)

            // 4 góc chữ L phát sáng
            // Top-left
            drawLine(emerald, Offset(left, top), Offset(left + cornerLen, top), strokeW, StrokeCap.Round)
            drawLine(emerald, Offset(left, top), Offset(left, top + cornerLen), strokeW, StrokeCap.Round)
            // Top-right
            drawLine(emerald, Offset(right - cornerLen, top), Offset(right, top), strokeW, StrokeCap.Round)
            drawLine(emerald, Offset(right, top), Offset(right, top + cornerLen), strokeW, StrokeCap.Round)
            // Bottom-left
            drawLine(emerald, Offset(left, bottom - cornerLen), Offset(left, bottom), strokeW, StrokeCap.Round)
            drawLine(emerald, Offset(left, bottom), Offset(left + cornerLen, bottom), strokeW, StrokeCap.Round)
            // Bottom-right
            drawLine(emerald, Offset(right - cornerLen, bottom), Offset(right, bottom), strokeW, StrokeCap.Round)
            drawLine(emerald, Offset(right, bottom - cornerLen), Offset(right, bottom), strokeW, StrokeCap.Round)

            // Lưới điểm ma trận QR mô phỏng bên trong khung
            val gridN = 5
            for (gx in 1 until gridN) {
                for (gy in 1 until gridN) {
                    val px = left + boxSide * gx / gridN
                    val py = top + boxSide * gy / gridN
                    val dist = kotlin.math.abs((py - top) / boxSide - laserProgress)
                    val dotAlpha = (1f - (dist * 4f).coerceIn(0f, 1f)) * 0.65f + 0.12f
                    drawCircle(
                        color = Color(0xFF6EE7B7).copy(alpha = dotAlpha),
                        radius = 2.5.dp.toPx(),
                        center = Offset(px, py),
                    )
                }
            }

            // Tia Laser quét ngang kèm vệt sáng mờ (glow trail)
            val ly = top + boxSide * laserProgress
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0xFF34D399).copy(alpha = 0.28f), Color.Transparent),
                    startY = ly - 18.dp.toPx(),
                    endY = ly + 18.dp.toPx(),
                ),
                topLeft = Offset(left + 4.dp.toPx(), ly - 18.dp.toPx()),
                size = Size(boxSide - 8.dp.toPx(), 36.dp.toPx()),
            )
            drawLine(
                color = Color(0xFF6EE7B7),
                start = Offset(left + 6.dp.toPx(), ly),
                end = Offset(right - 6.dp.toPx(), ly),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // Huy hiệu trạng thái Motion Laser ở đáy khung camera
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xD9062016))
                .border(1.dp, Color(0xFF34D399).copy(alpha = 0.55f), RoundedCornerShape(999.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF4ADE80)))
            Text(
                "LASER AI SCAN · Tự động giải mã 3 chế độ QR",
                color = Color(0xFFD1FAE5),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
