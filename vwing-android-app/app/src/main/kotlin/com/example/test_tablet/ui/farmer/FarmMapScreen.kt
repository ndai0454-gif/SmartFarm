package com.example.test_tablet.ui.farmer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.example.test_tablet.data.SampleData
import com.example.test_tablet.ui.common.DronePhoto
import com.example.test_tablet.ui.common.HealthGridMap
import com.example.test_tablet.ui.common.HealthLegend
import com.example.test_tablet.ui.common.StatusBadge
import com.example.test_tablet.ui.common.cellLabel
import com.example.test_tablet.ui.common.healthColor
import com.example.test_tablet.ui.common.withPad
import com.example.test_tablet.ui.common.yellowRatioFor
import com.example.test_tablet.ui.theme.HealthBad
import com.example.test_tablet.ui.theme.HealthFair
import com.example.test_tablet.ui.theme.HealthGood

private val GRID_COLS = SampleData.farmGrid.first().size

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmMapScreen(contentPadding: PaddingValues, onOpenRecommendation: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val selectedCell = selected?.let { it / GRID_COLS to it % GRID_COLS }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(contentPadding.withPad()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Bản đồ sức khỏe vườn", style = MaterialTheme.typography.headlineSmall)
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Dữ liệu từ chuyến bay gần nhất — không phải thời gian thực",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Lô A · chuyến bay 22/09/2026", style = MaterialTheme.typography.titleMedium)
                HealthGridMap(
                    scores = SampleData.farmGrid,
                    modifier = Modifier.fillMaxWidth(),
                    selected = selectedCell,
                    showLabels = true,
                    onCellClick = { r, c -> selected = r * GRID_COLS + c },
                )
                HealthLegend()
                Text(
                    "Chạm vào một ô để xem chi tiết.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val moderate = SampleData.farmGrid.flatten().count { it in 60..79 }
            val red = SampleData.attentionCells - moderate
            StatusBadge("${SampleData.healthyCells} ô khỏe", HealthGood)
            StatusBadge("$moderate ô trung bình", HealthFair)
            StatusBadge("$red ô cần chú ý", HealthBad)
        }
    }

    selectedCell?.let { (r, c) ->
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            sheetState = rememberModalBottomSheetState(),
        ) {
            CellSheet(
                row = r,
                col = c,
                onOpenRecommendation = {
                    selected = null
                    onOpenRecommendation()
                },
            )
        }
    }
}

@Composable
private fun CellSheet(row: Int, col: Int, onOpenRecommendation: () -> Unit) {
    val score = SampleData.farmGrid[row][col]
    val confidence = SampleData.cellConfidence[row to col]
    val status = when {
        score >= 80 -> "Khỏe mạnh"
        score >= 60 -> "Căng thẳng nhẹ — theo dõi"
        else -> "Nghi vấn bất thường"
    }
    Column(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Ô ${cellLabel(row, col)} - $status", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            StatusBadge("$score/100", healthColor(score))
        }
        DronePhoto(
            seed = row * 10 + col,
            yellowRatio = yellowRatioFor(score),
            modifier = Modifier.fillMaxWidth().height(170.dp),
        )
        if (confidence != null) {
            Text("$confidence% khả năng lá vàng lan rộng", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "Chấm điểm theo cả ô, không phải từng cây.",
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (score < 80) {
            Button(onClick = onOpenRecommendation, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Xem đề xuất xử lý")
            }
        }
    }
}
