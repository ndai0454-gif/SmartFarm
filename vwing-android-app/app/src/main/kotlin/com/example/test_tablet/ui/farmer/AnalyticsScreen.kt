package com.example.test_tablet.ui.farmer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.FlightPoint
import com.example.test_tablet.data.SampleData
import com.example.test_tablet.ui.common.ToggleTabs
import com.example.test_tablet.ui.common.withPad
import com.example.test_tablet.ui.theme.HealthBad
import com.example.test_tablet.ui.theme.HealthGood

private enum class StatRange(val label: String) {
    Week("Tuần này"),
    Month("Tháng này"),
    Season("Theo vụ"),
}

@Composable
fun AnalyticsScreen(contentPadding: PaddingValues) {
    var range by rememberSaveable { mutableStateOf(StatRange.Month) }
    val points = when (range) {
        StatRange.Week -> SampleData.weekFlights
        StatRange.Month -> SampleData.monthFlights
        StatRange.Season -> SampleData.seasonFlights
    }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(contentPadding.withPad()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Thống kê & tiến trình", style = MaterialTheme.typography.headlineSmall)
        ToggleTabs(StatRange.entries, range, label = { it.label }, onSelect = { range = it })

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Chỉ số sức khỏe tán theo thời gian", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Mỗi điểm là một chuyến bay — không phải theo dõi liên tục.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlightLineChart(points, Modifier.fillMaxWidth())
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Tổng số ô theo dõi", "${SampleData.totalCells}", null, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            StatCard("Số ô khỏe mạnh", "${SampleData.healthyCells}", null, HealthGood, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Số ô cần chú ý", "${SampleData.attentionCells}", null, HealthBad, Modifier.weight(1f))
            StatCard(
                "Sản lượng dự kiến",
                "≈ 2,8 tấn/ha",
                "ước tính, không phải số đo trực tiếp",
                MaterialTheme.colorScheme.secondary,
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, caption: String?, valueColor: Color, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = valueColor)
            if (caption != null) {
                Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FlightLineChart(points: List<FlightPoint>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline
    val dotCenter = MaterialTheme.colorScheme.surface
    val minValue = 50f
    val maxValue = 100f

    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            fun yOf(score: Int) = size.height * (1f - (score.coerceIn(50, 100) - minValue) / (maxValue - minValue))
            listOf(50, 75, 100).forEach { level ->
                drawLine(gridColor, Offset(0f, yOf(level)), Offset(size.width, yOf(level)), strokeWidth = 1.dp.toPx())
            }
            val step = size.width / points.size
            val offsets = points.mapIndexed { i, p -> Offset(step * (i + 0.5f), yOf(p.score)) }
            offsets.zipWithNext().forEach { (a, b) ->
                drawLine(lineColor, a, b, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            }
            offsets.forEach { o ->
                drawCircle(lineColor, radius = 7.dp.toPx(), center = o)
                drawCircle(dotCenter, radius = 3.5.dp.toPx(), center = o)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            points.forEach { p ->
                Column(Modifier.weight(1f)) {
                    Text(
                        "${p.score}",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        p.label,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
