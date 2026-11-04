package com.example.test_tablet.ui.ops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.SampleData
import com.example.test_tablet.data.UploadFlight
import com.example.test_tablet.data.UploadState
import com.example.test_tablet.ui.common.StatusBadge
import com.example.test_tablet.ui.common.withPad
import com.example.test_tablet.ui.theme.HealthBad
import com.example.test_tablet.ui.theme.HealthFair
import com.example.test_tablet.ui.theme.HealthGood
import com.example.test_tablet.ui.theme.OpsAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadStatusScreen(contentPadding: PaddingValues, onOpenFarmerApp: () -> Unit) {
    var openIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    LazyColumn(
        contentPadding = contentPadding.withPad(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Tải lên & xử lý", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Cloud xử lý sau khi tải lên xong (vài chục phút) — không phải thời gian thực.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        itemsIndexed(SampleData.uploadFlights) { index, flight ->
            FlightRow(flight, onClick = if (flight.state is UploadState.Done) ({ openIndex = index }) else null)
        }
    }

    val open = openIndex?.let { SampleData.uploadFlights[it] }
    val done = open?.state as? UploadState.Done
    if (open != null && done != null) {
        ModalBottomSheet(onDismissRequest = { openIndex = null }, sheetState = rememberModalBottomSheetState()) {
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Kết quả — ${open.plot}", style = MaterialTheme.typography.titleLarge)
                Text(open.dateTime, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryTile("Ô đã chấm điểm", "${done.scored}", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    SummaryTile("Ô bị gắn cờ", "${done.flagged}", HealthBad, Modifier.weight(1f))
                }
                Button(
                    onClick = {
                        openIndex = null
                        onOpenFarmerApp()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text("Xem trên app Nông dân")
                }
            }
        }
    }
}

@Composable
private fun FlightRow(flight: UploadFlight, onClick: (() -> Unit)?) {
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text(flight.plot, style = MaterialTheme.typography.titleMedium)
                    Text(flight.dateTime, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${flight.images} ảnh", style = MaterialTheme.typography.labelLarge)
            }
            when (val state = flight.state) {
                is UploadState.Uploading -> {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    val sent = (flight.images * state.progress).toInt()
                    Text(
                        "Đang tải lên · ${(state.progress * 100).toInt()}% · $sent/${flight.images} ảnh",
                        style = MaterialTheme.typography.labelMedium,
                        color = OpsAmber,
                    )
                }
                UploadState.Processing -> StatusBadge("Đã tải lên — đang xử lý", HealthFair)
                is UploadState.Done -> StatusBadge("Đã có kết quả", HealthGood)
            }
        }
    }
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    if (onClick != null) Card(onClick = onClick, colors = colors) { content() } else Card(colors = colors) { content() }
}

@Composable
private fun SummaryTile(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = valueColor)
        }
    }
}
