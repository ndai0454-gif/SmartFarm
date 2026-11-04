package com.example.test_tablet.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.ui.theme.HealthBad
import com.example.test_tablet.ui.theme.HealthFair
import com.example.test_tablet.ui.theme.HealthGood
import kotlin.random.Random

fun healthColor(score: Int): Color = when {
    score >= 80 -> HealthGood
    score >= 60 -> HealthFair
    else -> HealthBad
}

fun healthLabel(score: Int): String = when {
    score >= 80 -> "Tốt"
    score >= 60 -> "Trung bình"
    else -> "Cảnh báo"
}

/** Tên ô theo lưới: hàng A–F, cột 1–8 (ví dụ "C2"). */
fun cellLabel(row: Int, col: Int): String = "${'A' + row}${col + 1}"

fun PaddingValues.withPad(horizontal: Dp = 16.dp, vertical: Dp = 12.dp) = PaddingValues(
    start = horizontal,
    end = horizontal,
    top = calculateTopPadding() + vertical,
    bottom = calculateBottomPadding() + vertical,
)

@Composable
fun StatusBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = color.copy(alpha = 0.18f)) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun BackHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
        }
        Text(text = title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun WarningBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
fun <T> ToggleTabs(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Surface(
                onClick = { onSelect(option) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50),
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    text = label(option),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private val CanopyGreens = listOf(Color(0xFF2E5B34), Color(0xFF3B7040), Color(0xFF4C8A4B), Color(0xFF27502F))
private val CanopyYellows = listOf(Color(0xFFC9B23A), Color(0xFFD8C34E), Color(0xFFA8A040))

/** Ảnh drone giả lập (chưa có ảnh thật): tán cà phê xanh, tỷ lệ vàng theo [yellowRatio]. */
@Composable
fun DronePhoto(seed: Int, yellowRatio: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(10.dp))) {
        drawRect(Color(0xFF3A2F22))
        val rnd = Random(seed)
        val base = size.minDimension / 7f
        repeat(46) {
            val palette = if (rnd.nextFloat() < yellowRatio) CanopyYellows else CanopyGreens
            drawCircle(
                color = palette[rnd.nextInt(palette.size)],
                radius = base * (0.6f + rnd.nextFloat() * 0.7f),
                center = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height),
            )
        }
    }
}

fun yellowRatioFor(score: Int): Float = ((100 - score) / 100f * 1.3f).coerceIn(0.05f, 0.8f)

@Composable
fun HealthGridMap(
    scores: List<List<Int>>,
    modifier: Modifier = Modifier,
    selected: Pair<Int, Int>? = null,
    showLabels: Boolean = false,
    onCellClick: ((row: Int, col: Int) -> Unit)? = null,
) {
    val outline = MaterialTheme.colorScheme.onSurface
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        scores.forEachIndexed { r, rowScores ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                rowScores.forEachIndexed { c, score ->
                    val shape = RoundedCornerShape(4.dp)
                    var cell = Modifier.weight(1f).aspectRatio(1f).clip(shape).background(healthColor(score))
                    if (selected == (r to c)) cell = cell.border(BorderStroke(3.dp, outline), shape)
                    if (onCellClick != null) cell = cell.clickable { onCellClick(r, c) }
                    Box(cell, contentAlignment = Alignment.Center) {
                        if (showLabels) {
                            Text(
                                text = cellLabel(r, c),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (score in 60..79) Color(0xFF3B2A00) else Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HealthLegend(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(HealthGood to "Khỏe", HealthFair to "Trung bình", HealthBad to "Cần chú ý").forEach { (color, text) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(color, RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(6.dp))
                Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Ranh giới lô (đa giác giả lập) kèm đường bay zig-zag tùy chọn. */
@Composable
fun PlotBoundaryMap(modifier: Modifier = Modifier, showFlightPath: Boolean = false) {
    val fill = MaterialTheme.colorScheme.primary
    val pathColor = MaterialTheme.colorScheme.secondary
    val background = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier.clip(RoundedCornerShape(10.dp))) {
        drawRect(background)
        val w = size.width
        val h = size.height
        val boundary = Path().apply {
            val pts = listOf(
                Offset(.08f, .18f), Offset(.62f, .06f), Offset(.94f, .30f),
                Offset(.86f, .82f), Offset(.35f, .94f), Offset(.06f, .66f),
            )
            moveTo(pts[0].x * w, pts[0].y * h)
            pts.drop(1).forEach { lineTo(it.x * w, it.y * h) }
            close()
        }
        drawPath(boundary, fill.copy(alpha = 0.22f))
        if (showFlightPath) {
            val zigzag = Path().apply {
                val lines = 9
                for (i in 0 until lines) {
                    val y = h * (0.10f + 0.84f * i / (lines - 1))
                    val (x1, x2) = if (i % 2 == 0) 0f to w else w to 0f
                    if (i == 0) moveTo(x1, y) else lineTo(x1, y)
                    lineTo(x2, y)
                }
            }
            clipPath(boundary) {
                drawPath(zigzag, pathColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            drawCircle(pathColor, radius = 6.dp.toPx(), center = Offset(w * 0.12f, h * 0.26f))
        }
        drawPath(boundary, fill, style = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round))
    }
}
