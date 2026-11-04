package com.example.test_tablet.data

import kotlin.math.roundToInt

/** Điểm sức khỏe tán theo ô (0–100). Hàng A–F, cột 1–8; mỗi ô ≈ 11 m. Dữ liệu minh họa. */
object SampleData {
    val farmGrid: List<List<Int>> = listOf(
        listOf(92, 95, 90, 88, 91, 94, 93, 89),
        listOf(90, 87, 84, 79, 88, 92, 95, 91),
        listOf(90, 41, 82, 90, 93, 88, 84, 90),
        listOf(94, 91, 89, 92, 68, 74, 90, 93),
        listOf(96, 93, 90, 87, 85, 91, 55, 89),
        listOf(92, 95, 94, 90, 88, 86, 91, 94),
    )

    private val allScores = farmGrid.flatten()
    val totalCells = allScores.size
    val overallScore = allScores.average().roundToInt()
    val healthyCells = allScores.count { it >= 80 }
    val attentionCells = totalCells - healthyCells

    /** Độ tin cậy (%) của mô hình cho các ô bất thường, khóa = (hàng, cột). */
    val cellConfidence: Map<Pair<Int, Int>, Int> = mapOf(
        (2 to 1) to 78,
        (4 to 6) to 71,
        (3 to 4) to 64,
        (3 to 5) to 60,
        (1 to 3) to 58,
    )

    /** Các ô cần xử lý gấp (điểm < 60), thấp nhất trước. */
    val topAlerts: List<CellAlert> = farmGrid.flatMapIndexed { r, row ->
        row.mapIndexedNotNull { c, score ->
            if (score < 60) CellAlert(r, c, score, cellConfidence[r to c] ?: 60) else null
        }
    }.sortedBy { it.score }

    val weekFlights = listOf(
        FlightPoint("T2", 84), FlightPoint("T4", 86), FlightPoint("T6", 88),
    )
    val monthFlights = listOf(
        FlightPoint("03/09", 79), FlightPoint("07/09", 81), FlightPoint("11/09", 80),
        FlightPoint("15/09", 84), FlightPoint("19/09", 86), FlightPoint("22/09", 88),
    )
    val seasonFlights = listOf(
        FlightPoint("10/05", 91), FlightPoint("02/06", 90), FlightPoint("28/06", 88),
        FlightPoint("24/07", 84), FlightPoint("18/08", 82), FlightPoint("22/09", 88),
    )

    val exportRecords = listOf(
        ExportRecord("22/09/2026 · 07:40", "Phân bón lá (dạng lỏng)", "12 L/ha", "Sức khỏe tán 88% · 43/48 ô khỏe"),
        ExportRecord("08/09/2026 · 06:55", "Chế phẩm sinh học", "10 L/ha", "Sức khỏe tán 84% · 41/48 ô khỏe"),
        ExportRecord("25/08/2026 · 07:15", "Phân bón lá (dạng lỏng)", "12 L/ha", "Sức khỏe tán 82% · 38/48 ô khỏe"),
    )

    val uploadFlights = listOf(
        UploadFlight("Lô A", "22/09/2026 · 07:40", 150, UploadState.Done(scored = 48, flagged = 5)),
        UploadFlight("Lô B", "22/09/2026 · 09:05", 212, UploadState.Uploading(0.64f)),
        UploadFlight("Lô C", "21/09/2026 · 16:20", 96, UploadState.Processing),
        UploadFlight("Lô A", "08/09/2026 · 06:55", 148, UploadState.Done(scored = 48, flagged = 4)),
        UploadFlight("Lô B", "07/09/2026 · 07:10", 205, UploadState.Done(scored = 60, flagged = 7)),
    )

    /** Ngưỡng gió tối đa (m/s) cho checklist — giá trị giữ chỗ, cần xác nhận với đội bay. */
    const val MAX_WIND_MS = 5

    const val CALIBRATION_TOTAL = 30
    const val QA_TOTAL = 150
}

data class CellAlert(val row: Int, val col: Int, val score: Int, val confidence: Int)

data class FlightPoint(val label: String, val score: Int)

data class ExportRecord(val dateTime: String, val sprayed: String, val dose: String, val canopy: String)

data class UploadFlight(
    val plot: String,
    val dateTime: String,
    val images: Int,
    val state: UploadState,
)

sealed interface UploadState {
    data class Uploading(val progress: Float) : UploadState
    data object Processing : UploadState
    data class Done(val scored: Int, val flagged: Int) : UploadState
}
