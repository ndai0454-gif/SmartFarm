package com.example.test_tablet.ui.ops

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.test_tablet.data.SampleData
import com.example.test_tablet.ui.common.PlotBoundaryMap
import com.example.test_tablet.ui.common.withPad

private val CHECKLIST = listOf(
    "Camera gắn chắc chắn",
    "Thẻ nhớ còn trống",
    "Thời tiết đạt (gió < ${SampleData.MAX_WIND_MS} m/s)",
    "Bình phun đầy đủ",
)

@Composable
fun FlightPlanScreen(contentPadding: PaddingValues) {
    // Bitmask các mục đã tick — lưu được qua xoay màn hình.
    var checkedMask by rememberSaveable { mutableIntStateOf(0) }
    val allChecked = checkedMask == (1 shl CHECKLIST.size) - 1

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(contentPadding.withPad()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Kế hoạch chuyến bay", style = MaterialTheme.typography.titleLarge)
            Text("Lô A · 0,58 ha", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlotBoundaryMap(Modifier.fillMaxWidth().height(190.dp), showFlightPath = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Metric("Thời gian bay", "~14 phút")
                    Metric("Pin cần", "~55%")
                    Metric("Quãng đường", "~2,1 km")
                    Metric("Độ cao", "12 m")
                }
                Text(
                    "Giá trị ước tính minh họa — sẽ tính từ ranh giới lô và cấu hình drone.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Checklist trước bay", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${Integer.bitCount(checkedMask)}/${CHECKLIST.size}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                HorizontalDivider()
                CHECKLIST.forEachIndexed { i, item ->
                    val bit = 1 shl i
                    val checked = checkedMask and bit != 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { checkedMask = checkedMask xor bit }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { checkedMask = checkedMask xor bit })
                        Text(item, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        Button(
            onClick = { },
            enabled = allChecked,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("Bắt đầu chuyến bay", style = MaterialTheme.typography.titleMedium)
        }
        OutlinedButton(onClick = { checkedMask = 0 }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Hủy")
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
