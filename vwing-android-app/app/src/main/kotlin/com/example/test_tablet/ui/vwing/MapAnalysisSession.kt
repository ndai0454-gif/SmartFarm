package com.example.test_tablet.ui.vwing

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import com.example.test_tablet.data.vwing.Dates
import java.text.SimpleDateFormat
import java.util.Locale

/** Anchored above the adaptive app shell. Contains no credentials, bitmaps or form draft IDs. */
class MapAnalysisSession {
    val tab = mutableStateOf("GIS & viễn thám")
    val zone = mutableStateOf("")
    val regionId = mutableStateOf("")
    val mode = mutableStateOf(MapMode.GEO)
    val modeInitialized = mutableStateOf(false)
    private fun date(ms: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(ms)
    val start = mutableStateOf(date(Dates.today() - 30L * 86_400_000))
    val end = mutableStateOf(date(Dates.today()))
    val outputSize = mutableStateOf(2.5)
    val cloudText = mutableStateOf("80")
    val jobId = mutableStateOf<String?>(null)
    val jobSignature = mutableStateOf("")
    val jobStartedAt = mutableStateOf(0L)
    val pendingPath = mutableStateOf<String?>(null)
    val pendingSignature = mutableStateOf("")
    val workflowBaselineStart = mutableStateOf(date(Dates.today() - 60L * 86_400_000))
    val workflowBaselineEnd = mutableStateOf(date(Dates.today() - 30L * 86_400_000))
    val workflowCloud = mutableStateOf("80")
    val workflowThreshold = mutableStateOf("0.1")
    val workflowPending = mutableStateOf("")

    companion object {
        val Saver = listSaver<MapAnalysisSession, Any>(
            save = { listOf(it.tab.value, it.zone.value, it.regionId.value, it.mode.value.name, it.modeInitialized.value,
                it.start.value, it.end.value, it.outputSize.value, it.cloudText.value, it.jobId.value.orEmpty(),
                it.jobSignature.value, it.jobStartedAt.value, it.pendingPath.value.orEmpty(), it.pendingSignature.value,
                it.workflowBaselineStart.value, it.workflowBaselineEnd.value, it.workflowCloud.value,
                it.workflowThreshold.value, it.workflowPending.value) },
            restore = { values -> MapAnalysisSession().apply {
                tab.value = values[0] as String; zone.value = values[1] as String; regionId.value = values[2] as String
                mode.value = MapMode.valueOf(values[3] as String); modeInitialized.value = values[4] as Boolean
                start.value = values[5] as String; end.value = values[6] as String; outputSize.value = values[7] as Double
                cloudText.value = values[8] as String; jobId.value = (values[9] as String).ifEmpty { null }
                jobSignature.value = values[10] as String; jobStartedAt.value = values[11] as Long
                pendingPath.value = (values[12] as String).ifEmpty { null }; pendingSignature.value = values[13] as String
                (values.getOrNull(14) as? String)?.let { workflowBaselineStart.value = it }
                (values.getOrNull(15) as? String)?.let { workflowBaselineEnd.value = it }
                (values.getOrNull(16) as? String)?.let { workflowCloud.value = it }
                (values.getOrNull(17) as? String)?.let { workflowThreshold.value = it }
                (values.getOrNull(18) as? String)?.let { workflowPending.value = it }
            } },
        )
    }
}
