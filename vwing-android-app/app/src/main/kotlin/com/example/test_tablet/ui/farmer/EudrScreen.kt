package com.example.test_tablet.ui.farmer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.ExportRecord
import com.example.test_tablet.data.SampleData
import com.example.test_tablet.ui.common.BackHeader
import com.example.test_tablet.ui.common.PlotBoundaryMap
import java.util.Calendar

private const val MILLIS_PER_DAY = 86_400_000L

private fun daysUntil(year: Int, month: Int, day: Int): Long {
    val now = Calendar.getInstance()
    val today = Calendar.getInstance().apply {
        clear()
        set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))
    }
    val target = Calendar.getInstance().apply {
        clear()
        set(year, month - 1, day)
    }
    // +12h để làm tròn đúng khi chênh giờ mùa hè
    return (target.timeInMillis - today.timeInMillis + MILLIS_PER_DAY / 2) / MILLIS_PER_DAY
}

@Composable
fun EudrScreen(onBack: () -> Unit) {
    Scaffold(
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Box(Modifier.navigationBarsPadding().padding(16.dp)) {
                    Button(onClick = { }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("Xuất hồ sơ PDF cho khách hàng nhập khẩu")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BackHeader("Hồ sơ xuất khẩu — Lô A", onBack)
            DeadlineCard()
            Text("Nhật ký chuyến bay", style = MaterialTheme.typography.titleMedium)
            Column {
                SampleData.exportRecords.forEachIndexed { i, record ->
                    TimelineEntry(record, isLast = i == SampleData.exportRecords.lastIndex)
                }
            }
            Text(
                "Dữ liệu minh họa.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeadlineCard() {
    val days = daysUntil(2026, 12, 30)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "EUDR — Quy định chống phá rừng của EU",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                if (days > 0) "Còn $days ngày" else "Đã đến hạn áp dụng",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "Áp dụng từ 30/12/2026 (doanh nghiệp lớn/vừa) · 30/6/2027 (doanh nghiệp nhỏ)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun TimelineEntry(record: ExportRecord, isLast: Boolean) {
    val lineColor = MaterialTheme.colorScheme.outline
    Row(Modifier.height(IntrinsicSize.Min)) {
        Column(Modifier.width(24.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = 18.dp).size(12.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            if (!isLast) Box(Modifier.width(2.dp).weight(1f).background(lineColor))
        }
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(record.dateTime, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlotBoundaryMap(Modifier.size(84.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LabeledLine("Đã phun", record.sprayed)
                        LabeledLine("Liều", record.dose)
                        LabeledLine("Tình trạng tán", record.canopy)
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
