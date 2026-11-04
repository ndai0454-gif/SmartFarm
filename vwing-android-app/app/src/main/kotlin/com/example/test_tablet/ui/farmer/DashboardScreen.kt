package com.example.test_tablet.ui.farmer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.CellAlert
import com.example.test_tablet.data.SampleData
import com.example.test_tablet.ui.common.DronePhoto
import com.example.test_tablet.ui.common.HealthGridMap
import com.example.test_tablet.ui.common.HealthLegend
import com.example.test_tablet.ui.common.StatusBadge
import com.example.test_tablet.ui.common.cellLabel
import com.example.test_tablet.ui.common.healthColor
import com.example.test_tablet.ui.common.healthLabel
import com.example.test_tablet.ui.common.withPad
import com.example.test_tablet.ui.common.yellowRatioFor
import com.example.test_tablet.ui.theme.HealthBad
import com.example.test_tablet.ui.theme.HealthGood

@Composable
fun DashboardScreen(contentPadding: PaddingValues, onOpenAlert: () -> Unit) {
    LazyColumn(
        contentPadding = contentPadding.withPad(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { TopBar() }
        item { HeroCard() }
        item { Text("Cần chú ý", style = MaterialTheme.typography.titleLarge) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(SampleData.topAlerts) { alert -> AlertCard(alert, onClick = onOpenAlert) }
            }
        }
    }
}

@Composable
private fun TopBar() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlotSelector()
            Text(
                "FarmInsights",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        WeatherCard()
    }
}

@Composable
private fun PlotSelector() {
    var open by remember { mutableStateOf(false) }
    var plot by rememberSaveable { mutableStateOf("Lô A") }
    Column {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
                Text(plot, style = MaterialTheme.typography.titleMedium)
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Chọn lô")
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf("Lô A", "Lô B", "Lô C").forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { plot = name; open = false })
            }
        }
    }
}

@Composable
private fun WeatherCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                WeatherMetric("🌡", "28°C", "Nhiệt độ")
                WeatherMetric("💧", "65%", "Độ ẩm")
                WeatherMetric("💨", "12 km/h", "Gió")
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Phù hợp để phun hôm nay", style = MaterialTheme.typography.bodyMedium)
                StatusBadge("Có", HealthGood)
            }
        }
    }
}

@Composable
private fun WeatherMetric(emoji: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$emoji  $value", style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HeroCard() {
    val score = SampleData.overallScore
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text(
                        "Chỉ số sức khỏe tán tổng thể",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("$score%", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = healthColor(score))
                }
                StatusBadge(healthLabel(score), healthColor(score))
            }
            HealthGridMap(SampleData.farmGrid, Modifier.fillMaxWidth())
            HealthLegend()
            Text(
                "Mỗi ô ≈ 11 m × 11 m (khoảng 4–9 cây). Điểm chấm theo cả ô, không phải từng cây.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AlertCard(alert: CellAlert, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(250.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        DronePhoto(
            seed = alert.row * 10 + alert.col,
            yellowRatio = yellowRatioFor(alert.score),
            modifier = Modifier.fillMaxWidth().height(120.dp),
        )
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Ô ${cellLabel(alert.row, alert.col)}", style = MaterialTheme.typography.titleMedium)
                StatusBadge("Cần chú ý", HealthBad)
            }
            Text("Độ tin cậy mô hình: ${alert.confidence}%", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Chuyến bay gần nhất: 3 ngày trước",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
