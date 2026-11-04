package com.example.test_tablet.ui.ops

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.test_tablet.data.SampleData
import com.example.test_tablet.ui.common.BackHeader
import com.example.test_tablet.ui.common.DronePhoto
import com.example.test_tablet.ui.common.withPad

private const val TOTAL = SampleData.CALIBRATION_TOTAL
private const val MAX_SCORE = 9

/** Map Int→Int lưu được qua xoay màn hình (lưu dạng danh sách phẳng key,value,key,value…). */
@Composable
fun rememberSavableIntMap(initial: Map<Int, Int>): SnapshotStateMap<Int, Int> = rememberSaveable(
    saver = listSaver<SnapshotStateMap<Int, Int>, Int>(
        save = { map -> map.flatMap { listOf(it.key, it.value) } },
        restore = { flat ->
            mutableStateMapOf<Int, Int>().apply { flat.chunked(2).forEach { put(it[0], it[1]) } }
        },
    ),
) { mutableStateMapOf<Int, Int>().apply { putAll(initial) } }

@Composable
fun CalibrationScreen(contentPadding: PaddingValues) {
    // 18/30 ô đã chấm sẵn để khớp mockup; giá trị điểm là dữ liệu minh họa.
    val scores = rememberSavableIntMap(initial = (0 until 18).associateWith { (it * 7 + 3) % (MAX_SCORE + 1) })
    val notes = remember { mutableStateMapOf<Int, String>() }
    var openIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    BackHandler(enabled = openIndex != null) { openIndex = null }

    val index = openIndex
    if (index != null) {
        CalibrationDetail(
            index = index,
            currentScore = scores[index],
            currentNote = notes[index].orEmpty(),
            contentPadding = contentPadding,
            onBack = { openIndex = null },
            onSave = { score, note ->
                scores[index] = score
                notes[index] = note
                openIndex = (index + 1 until TOTAL).firstOrNull { it !in scores }
                    ?: (0 until TOTAL).firstOrNull { it !in scores }
            },
        )
        return
    }

    Column(Modifier.padding(top = contentPadding.calculateTopPadding()).fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Chấm điểm hiệu chuẩn (thang SAD)", style = MaterialTheme.typography.titleLarge)
            Text(
                "${scores.size}/$TOTAL ô đã chấm",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            LinearProgressIndicator(progress = { scores.size / TOTAL.toFloat() }, modifier = Modifier.fillMaxWidth())
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = contentPadding.calculateBottomPadding() + 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items((0 until TOTAL).toList()) { i ->
                CalibrationThumb(i, scores[i], onClick = { openIndex = i })
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Ảnh và điểm là dữ liệu minh họa.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun thumbYellow(index: Int, score: Int?): Float =
    if (score != null) score / MAX_SCORE.toFloat() * 0.7f + 0.05f else ((index * 37) % 50) / 100f

@Composable
private fun CalibrationThumb(index: Int, score: Int?, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .then(if (score == null) Modifier.border(2.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(onClick = onClick),
    ) {
        DronePhoto(seed = 100 + index, yellowRatio = thumbYellow(index, score), modifier = Modifier.fillMaxSize())
        Text(
            "Ô ${index + 1}",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .background(Color(0x99000000), RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
        if (score != null) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).background(primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("$score", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun CalibrationDetail(
    index: Int,
    currentScore: Int?,
    currentNote: String,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSave: (score: Int, note: String) -> Unit,
) {
    var selected by rememberSaveable(index) { mutableStateOf(currentScore) }
    var note by rememberSaveable(index) { mutableStateOf(currentNote) }

    Column(
        modifier = Modifier
            .padding(contentPadding)
            .consumeWindowInsets(contentPadding)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BackHeader("Ô mẫu ${index + 1}/$TOTAL", onBack)
        DronePhoto(
            seed = 100 + index,
            yellowRatio = thumbYellow(index, selected),
            modifier = Modifier.fillMaxWidth().height(260.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Thang SAD — cà phê vối (0–$MAX_SCORE)", style = MaterialTheme.typography.titleMedium)
            listOf(0..4, 5..MAX_SCORE).forEach { rowRange ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowRange.forEach { n ->
                        ScoreButton(n, isSelected = selected == n, modifier = Modifier.weight(1f)) { selected = n }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0 = không triệu chứng", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$MAX_SCORE = nặng nhất", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Ghi chú") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Button(
            onClick = { selected?.let { onSave(it, note) } },
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("Lưu & tiếp theo")
        }
    }
}

@Composable
private fun ScoreButton(value: Int, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) scheme.primary else scheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$value",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = if (isSelected) scheme.onPrimary else scheme.onSurface,
        )
    }
}
