package com.example.test_tablet.ui.ops

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.test_tablet.data.SampleData
import com.example.test_tablet.ui.common.DronePhoto
import com.example.test_tablet.ui.common.ToggleTabs
import com.example.test_tablet.ui.theme.HealthBad
import com.example.test_tablet.ui.theme.HealthFair
import com.example.test_tablet.ui.theme.HealthGood

private enum class QaFlag(val label: String, val color: Color) {
    Ok("Đạt", HealthGood),
    Blur("Mờ/rung", HealthBad),
    Dark("Thiếu sáng", HealthFair),
}

private enum class QaFilter(val label: String) {
    All("Tất cả"),
    Blur("Mờ/rung"),
    Dark("Thiếu sáng"),
    Ok("Đạt"),
}

private const val TOTAL = SampleData.QA_TOTAL

@Composable
fun QaScreen(contentPadding: PaddingValues) {
    // Chỉ lưu ảnh bị gắn cờ (ordinal của QaFlag); ảnh không có trong map = Đạt. 8 ảnh gắn cờ → 142/150 đạt.
    val flags = rememberSavableIntMap(
        initial = listOf(7, 23, 41, 66, 90).associateWith { QaFlag.Blur.ordinal } +
            listOf(12, 58, 120).associateWith { QaFlag.Dark.ordinal },
    )
    var filter by rememberSaveable { mutableStateOf(QaFilter.All) }
    var sent by rememberSaveable { mutableStateOf(false) }

    fun flagOf(i: Int) = QaFlag.entries[flags[i] ?: QaFlag.Ok.ordinal]

    val visible = (0 until TOTAL).filter {
        when (filter) {
            QaFilter.All -> true
            QaFilter.Blur -> flagOf(it) == QaFlag.Blur
            QaFilter.Dark -> flagOf(it) == QaFlag.Dark
            QaFilter.Ok -> flagOf(it) == QaFlag.Ok
        }
    }
    val okCount = TOTAL - flags.size

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Rà soát ảnh thô — Lô A · 22/09 07:40", style = MaterialTheme.typography.titleLarge)
            Text(
                "Chạm vào ảnh để đổi nhãn: Đạt → Mờ/rung → Thiếu sáng.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ToggleTabs(QaFilter.entries, filter, label = { it.label }, onSelect = { filter = it })
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(visible, key = { it }) { i ->
                QaThumb(i, flagOf(i)) {
                    val next = QaFlag.entries[(flagOf(i).ordinal + 1) % QaFlag.entries.size]
                    if (next == QaFlag.Ok) flags.remove(i) else flags[i] = next.ordinal
                    sent = false
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 3.dp) {
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = contentPadding.calculateBottomPadding() + 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("$okCount/$TOTAL ảnh đạt chuẩn", style = MaterialTheme.typography.titleMedium)
                    Text("${flags.size} gắn cờ", style = MaterialTheme.typography.labelLarge, color = HealthFair)
                }
                LinearProgressIndicator(progress = { okCount / TOTAL.toFloat() }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = { sent = true },
                    enabled = !sent,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(if (sent) "Đã gửi — đang chờ xử lý" else "Gửi xử lý cloud")
                }
            }
        }
    }
}

@Composable
private fun QaThumb(index: Int, flag: QaFlag, onClick: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        DronePhoto(seed = 500 + index, yellowRatio = 0.1f, modifier = Modifier.fillMaxSize())
        when (flag) {
            QaFlag.Dark -> Box(Modifier.fillMaxSize().background(Color(0x99000000)))
            QaFlag.Blur -> Box(Modifier.fillMaxSize().background(Color(0x55FFFFFF)))
            QaFlag.Ok -> Unit
        }
        Text(
            flag.label,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .background(flag.color, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (flag == QaFlag.Ok) Color.White else Color(0xFF1B1B1B),
        )
    }
}
