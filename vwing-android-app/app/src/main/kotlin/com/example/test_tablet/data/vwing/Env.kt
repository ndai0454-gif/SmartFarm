package com.example.test_tablet.data.vwing

/** Quy tắc cho số đo nước, đất, thời tiết (P1-03). Thuần Kotlin để kiểm thử được. */
object Env {
    const val MOISTURE_STALE_DAYS = 7
    const val LAB_STALE_DAYS = 365
    private const val FUTURE_OK_MS = 86_400_000L
    private const val FORECAST_HORIZON_MS = 14 * 86_400_000L

    fun unitFor(kind: MeasureKind) = kind.unit

    fun validate(m: Measure, d: GardenData): String? {
        if (m.periodDays !in 1..365) return "Số ngày bao phủ phải từ 1 đến 365."
        val limit = if (m.origin == MeasureOrigin.FORECAST) FORECAST_HORIZON_MS else FUTURE_OK_MS
        if (m.measuredAt > Dates.now() + limit) return "Số đo thực tế không thể ở tương lai. Nếu là dự báo, chọn \"Dự báo\" (chỉ áp dụng cho lượng mưa)."
        if (m.origin == MeasureOrigin.FORECAST && m.kind != MeasureKind.RAIN) return "Dự báo chỉ áp dụng cho lượng mưa."
        if (m.origin == MeasureOrigin.SENSOR && m.calibration == Calibration.NA) return "Khai báo tình trạng hiệu chuẩn của cảm biến."
        if (m.origin != MeasureOrigin.SENSOR && m.calibration != Calibration.NA) return "Hiệu chuẩn chỉ áp dụng cho cảm biến."
        m.point?.let { if (kotlin.math.abs(it.lat) > 90 || kotlin.math.abs(it.lng) > 180) return "Tọa độ vị trí không hợp lệ." }
        m.scopeZone?.let { z -> if (d.zones.none { it.name == z } && d.trees.none { it.zone == z }) return "Khu vực \"$z\" chưa có trong vườn." }
        m.treeIds.firstOrNull { id -> d.trees.none { it.id == id } }?.let { return "Có cây không tồn tại trong vườn." }
        when (m.kind) {
            MeasureKind.SOIL_LAB -> {
                if (m.origin != MeasureOrigin.LAB) return "Phân tích mẫu đất phải có nguồn \"Phòng thí nghiệm\"."
                if (m.labName.isBlank()) return "Nhập tên phòng thí nghiệm hoặc đơn vị phân tích."
                if (m.lab.isEmpty()) return "Nhập ít nhất một chỉ tiêu (ví dụ pH = 5,8)."
                m.lab.firstOrNull { it.name.isBlank() || it.value.isNaN() || it.value.isInfinite() }?.let { return "Chỉ tiêu phân tích không hợp lệ." }
                m.lab.firstOrNull { it.name.trim().equals("ph", true) && it.value !in 0.0..14.0 }?.let { return "pH phải từ 0 đến 14." }
                m.lab.firstOrNull { it.value < 0 }?.let { return "Chỉ tiêu \"${it.name}\" không được âm." }
                if (m.point == null) return "Nhập vị trí lấy mẫu để mẫu đất có thể truy nguồn."
            }
            else -> {
                val v = m.value ?: return "Nhập giá trị (không để trống; thiếu dữ liệu không được coi là 0)."
                if (v.isNaN() || v.isInfinite() || v < 0) return "Giá trị phải là số không âm."
                if (m.kind == MeasureKind.SOIL_MOISTURE && v > 100) return "Độ ẩm đất không vượt quá 100%."
                if (m.kind == MeasureKind.RAIN && v > 2000) return "Lượng mưa vượt mức hợp lý (2000 mm); kiểm tra lại đơn vị."
                if (m.kind == MeasureKind.RAIN && m.origin == MeasureOrigin.LAB) return "Lượng mưa không đến từ phòng thí nghiệm."
                if (m.kind != MeasureKind.RAIN && m.origin == MeasureOrigin.LAB) return "Nguồn \"Phòng thí nghiệm\" chỉ dành cho phân tích mẫu đất."
                if (m.unit != m.kind.unit) return "Đơn vị của ${m.kind.label.lowercase()} phải là ${m.kind.unit}."
            }
        }
        return null
    }

    fun ageDays(m: Measure, today: Long = Dates.today()): Int = Dates.daysBetween(m.measuredAt, today)

    fun isStale(m: Measure, today: Long = Dates.today()): Boolean = when (m.kind) {
        MeasureKind.SOIL_MOISTURE -> ageDays(m, today) > MOISTURE_STALE_DAYS
        MeasureKind.SOIL_LAB -> ageDays(m, today) > LAB_STALE_DAYS
        else -> false
    }

    private fun gardenWide(m: Measure) = m.scopeZone == null && m.treeIds.isEmpty()
    private fun observed(m: Measure) = m.origin != MeasureOrigin.FORECAST

    class RainSummary(
        val windowDays: Int,
        /** Tổng mm của các số đo thực tế toàn vườn trong cửa sổ; null nếu chưa có số đo nào (khác với 0 mm). */
        val totalMm: Double?,
        val coveredDays: Int,
        val records: Int,
        val forecastMm: Double?,
        val forecastRecords: Int,
    ) {
        val missingDays get() = windowDays - coveredDays
    }

    /**
     * Mưa đã đo trong [windowDays] ngày gần nhất. Chỉ tính số đo toàn vườn (không theo khu/cây) để không cộng lặp;
     * nhiều số đo cùng ngày lấy bản ghi mới nhất. Dự báo được tách riêng và không bao giờ cộng vào mưa đã đo.
     */
    fun rain(d: GardenData, windowDays: Int, today: Long = Dates.today()): RainSummary {
        val start = today - (windowDays - 1) * 86_400_000L
        val rainAll = d.measures.filter { it.kind == MeasureKind.RAIN && gardenWide(it) }
        val measured = rainAll.filter { observed(it) && Dates.startOf(it.measuredAt) in Dates.startOf(start)..today }
        val byDay = measured.groupBy { Dates.startOf(it.measuredAt) }.mapValues { (_, l) -> l.maxByOrNull { it.createdAt }!! }
        val covered = HashSet<Long>()
        for (m in byDay.values) for (i in 0 until m.periodDays) {
            val day = Dates.startOf(m.measuredAt - i * 86_400_000L)
            if (day in Dates.startOf(start)..today) covered += day
        }
        // dự báo: từ hôm nay tới hết cửa sổ tương lai cùng độ dài; không bao giờ cộng vào mưa đã đo
        val fc = rainAll.filter { !observed(it) && Dates.startOf(it.measuredAt) in today..(today + windowDays * 86_400_000L) }
        return RainSummary(
            windowDays, if (byDay.isEmpty()) null else byDay.values.sumOf { it.value ?: 0.0 }, covered.size, byDay.size,
            if (fc.isEmpty()) null else fc.groupBy { Dates.startOf(it.measuredAt) }.values.sumOf { l -> l.maxByOrNull { it.createdAt }!!.value ?: 0.0 }, fc.size,
        )
    }

    /** Tổng lít nước tưới đã ghi (đơn vị khác mm mưa, không được cộng với mưa). Null nếu chưa ghi lần nào. */
    fun irrigationLitres(d: GardenData, windowDays: Int, today: Long = Dates.today()): Double? {
        val start = Dates.startOf(today - (windowDays - 1) * 86_400_000L)
        val l = d.measures.filter { it.kind == MeasureKind.IRRIGATION && Dates.startOf(it.measuredAt) in start..today }
        return if (l.isEmpty()) null else l.sumOf { it.value ?: 0.0 }
    }

    /** Số đo độ ẩm mới nhất theo từng phạm vi (khu/cây/toàn vườn) kèm tuổi dữ liệu. */
    fun latestMoisture(d: GardenData): List<Measure> =
        d.measures.filter { it.kind == MeasureKind.SOIL_MOISTURE }
            .groupBy { Triple(it.scopeZone, it.treeIds.sorted(), it.point?.let { p -> "%.5f,%.5f".format(p.lat, p.lng) }) }
            .values.map { l -> l.maxByOrNull { it.measuredAt }!! }.sortedByDescending { it.measuredAt }

    fun latestLab(d: GardenData): Measure? = d.measures.filter { it.kind == MeasureKind.SOIL_LAB }.maxByOrNull { it.measuredAt }

    /** Nhắc kiểm tra: chỉ nhắc loại số đo mà vườn đã từng ghi và hiện đã cũ. Không nhắc nếu chưa từng ghi. */
    fun reminders(d: GardenData, today: Long = Dates.today()): List<String> {
        val out = mutableListOf<String>()
        val moist = d.measures.filter { it.kind == MeasureKind.SOIL_MOISTURE }
        moist.maxByOrNull { it.measuredAt }?.let { last ->
            val age = ageDays(last, today)
            if (age > MOISTURE_STALE_DAYS) out += "Độ ẩm đất: lần đo gần nhất cách đây $age ngày (quá $MOISTURE_STALE_DAYS ngày)"
        }
        latestLab(d)?.let { last ->
            val age = ageDays(last, today)
            if (age > LAB_STALE_DAYS) out += "Mẫu đất: lần phân tích gần nhất cách đây $age ngày (hơn 1 năm)"
        }
        return out
    }
}
