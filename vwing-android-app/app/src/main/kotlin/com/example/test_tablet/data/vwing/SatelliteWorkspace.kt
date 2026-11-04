package com.example.test_tablet.data.vwing

import java.security.MessageDigest
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** The seven analysis choices available in the standalone Satellite app. */
enum class SatelliteAnalysisMode(
    val label: String,
    val description: String,
    val indexKey: SatIndexKey? = null,
) {
    GARDEN("Tổng hợp vườn", "Tổng hợp chỉ số, lớp phủ, địa hình và ước tính cây theo ranh giới."),
    NDVI("NDVI", "Theo dõi độ xanh của tán lá và biến động theo thời gian.", SatIndexKey.NDVI),
    EVI("EVI", "Theo dõi độ xanh, giảm bão hòa tại vùng có tán lá dày.", SatIndexKey.EVI),
    NDMI("NDMI", "Theo dõi độ ẩm của tán lá; không phải phép đo độ ẩm đất.", SatIndexKey.NDMI),
    WATER("Nước mặt Sentinel-1", "So sánh nước mặt giữa kỳ nền và kỳ khảo sát bằng radar."),
    DROUGHT("Hạn hán khí tượng", "Khảo sát chuỗi lịch sử 2000–2025; kiểm tra nguồn và trạng thái mô phỏng."),
    DRONE("Drone đa phổ / VRA", "Phân tích đa phổ và gợi ý phân vùng; không điều khiển drone hoặc phun tự động."),
    ;

    val usesDates: Boolean get() = this != DRONE && this != DROUGHT
}

/** Dates represent complete calendar days; the ending day is excluded by the API. */
data class SatelliteDateRange(val start: String, val endExclusive: String) {
    init {
        require(SatelliteWorkspaceDates.validate(start, endExclusive) == null) {
            SatelliteWorkspaceDates.validate(start, endExclusive).orEmpty()
        }
    }

    val days: Int get() = SatelliteWorkspaceDates.dayCount(start, endExclusive)

    fun preceding(): SatelliteDateRange = SatelliteDateRange(
        SatelliteWorkspaceDates.shift(start, -days), start,
    )
}

object SatelliteWorkspaceDates {
    private val isoPattern = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
    private val utc: TimeZone get() = TimeZone.getTimeZone("UTC")

    private fun formatter(zone: TimeZone = utc) = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
        isLenient = false
        timeZone = zone
    }

    /** Rejects normalization (e.g. February 30), partial matches and extra characters. */
    fun parseIsoDate(text: String): Date? {
        if (!isoPattern.matches(text)) return null
        val position = ParsePosition(0)
        val parsed = formatter().parse(text, position) ?: return null
        return parsed.takeIf { position.index == text.length && formatter().format(it) == text }
    }

    fun validate(start: String, endExclusive: String): String? {
        val first = parseIsoDate(start) ?: return "Ngày bắt đầu phải hợp lệ theo định dạng YYYY-MM-DD."
        val last = parseIsoDate(endExclusive) ?: return "Ngày kết thúc phải hợp lệ theo định dạng YYYY-MM-DD."
        val days = (last.time - first.time) / 86_400_000L
        return when {
            days < 1 -> "Ngày kết thúc (không bao gồm) phải sau ngày bắt đầu."
            days > 93 -> "Mỗi kỳ phân tích chỉ được dài từ 1 đến 93 ngày."
            else -> null
        }
    }

    fun range(start: String, endExclusive: String): SatelliteDateRange? =
        if (validate(start, endExclusive) == null) SatelliteDateRange(start, endExclusive) else null

    /** Completed days before today in the device's zone. Calendar arithmetic survives DST. */
    fun rolling(
        days: Int,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): SatelliteDateRange {
        require(days in listOf(30, 60, 90)) { "Chọn kỳ 30, 60 hoặc 90 ngày." }
        val calendar = Calendar.getInstance(timeZone, Locale.ROOT).apply { timeInMillis = nowMillis }
        val format = formatter(timeZone)
        val end = format.format(calendar.time)
        calendar.add(Calendar.DAY_OF_MONTH, -days)
        return SatelliteDateRange(format.format(calendar.time), end)
    }

    internal fun dayCount(start: String, endExclusive: String): Int =
        ((parseIsoDate(endExclusive)!!.time - parseIsoDate(start)!!.time) / 86_400_000L).toInt()

    internal fun shift(date: String, days: Int): String {
        val calendar = Calendar.getInstance(utc, Locale.ROOT).apply {
            time = requireNotNull(parseIsoDate(date))
            add(Calendar.DAY_OF_MONTH, days)
        }
        return formatter().format(calendar.time)
    }
}

/** Empty input uses the crop default. Both Vietnamese comma and decimal point are accepted. */
fun satelliteSpacingM(text: String): Double? {
    val clean = text.trim().replace(',', '.')
    if (!Regex("(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)").matches(clean)) return null
    return clean.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.6..25.0 }
}

fun satelliteSpacingProblem(text: String): String? =
    if (text.isBlank() || satelliteSpacingM(text) != null) null
    else "Khoảng cách cây phải là số từ 0,6 đến 25 m; để trống để dùng mặc định theo cây."

/** Hash all request inputs, including credentials, without displaying the credential itself. */
data class SatelliteRequestIdentity(
    val mode: SatelliteAnalysisMode,
    val ring: List<GeoPoint>,
    val start: String,
    val endExclusive: String,
    val baselineStart: String = "",
    val baselineEndExclusive: String = "",
    val crop: SatCropKey = SatCropKey.DURIAN,
    val spacingM: Double? = null,
    val serverUrl: String = "",
    val serverToken: String = "",
) {
    fun signature(): String {
        val inputs = listOf(
            mode.name, start, endExclusive, baselineStart, baselineEndExclusive,
            crop.key, spacingM?.toString().orEmpty(), serverUrl.trim().trimEnd('/'), serverToken,
        ) + ring.flatMap { listOf(it.lat.toString(), it.lng.toString()) }
        val canonical = inputs.joinToString("") { "${it.length}:$it" }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

val SATELLITE_WORKSPACE_PRESETS: List<VietnamGardenPreset> = VIETNAM_GARDEN_PRESETS + listOf(
    VietnamGardenPreset(
        "Vùng mẫu Bảo Lộc (1,21 ha)", "Lâm Đồng · vùng mẫu · 4 đỉnh", SatCropKey.DURIAN,
        listOf(GeoPoint(11.55, 107.8), GeoPoint(11.55, 107.801), GeoPoint(11.551, 107.801), GeoPoint(11.551, 107.8)),
    ),
    VietnamGardenPreset(
        "Basra, Iraq — Hành lang Shatt al-Arab", "Iraq · vùng đối chiếu hạn hán · 4 đỉnh", SatCropKey.GENERAL_ORCHARD,
        listOf(GeoPoint(30.5000, 47.7800), GeoPoint(30.5000, 47.7830), GeoPoint(30.5030, 47.7830), GeoPoint(30.5030, 47.7800)),
    ),
)

data class SatelliteGuidance(val title: String, val body: String)

val SATELLITE_GUIDANCE = listOf(
    SatelliteGuidance(
        "GIS & viễn thám",
        "1. Xác định ranh giới và thời gian khảo sát.\n\n" +
            "2. Chọn Tổng hợp vườn hoặc NDVI/EVI/NDMI phù hợp với câu hỏi.\n\n" +
            "3. Đọc nguồn, số cảnh, độ phủ hợp lệ và cảnh báo; phân biệt dữ liệu thật và mô phỏng.\n\n" +
            "4. Đối chiếu thực địa trước khi quyết định tưới hoặc xử lý cây. Chỉ số vệ tinh không chẩn đoán bệnh từng cây.",
    ),
    SatelliteGuidance(
        "Ảnh drone & photogrammetry",
        "Chuẩn bị ảnh gốc có tọa độ, độ cao bay, mức chồng phủ và điểm khống chế nếu có.\n\n" +
            "Kiểm tra độ nét → căn chỉnh ảnh → tạo orthomosaic/DSM → kiểm tra sai số tọa độ → đối chiếu ranh giới.\n\n" +
            "Ghép ảnh photogrammetry cần phần mềm chuyên dụng. Phân tích đa phổ/VRA trong app cần backend và dữ liệu phù hợp; không thay thế quy trình ghép ảnh hoặc vận hành drone.",
    ),
    SatelliteGuidance(
        "LiDAR & đám mây điểm",
        "Chuẩn bị LAS/LAZ, hệ tọa độ, đơn vị cao độ, mật độ điểm và thông tin lần đo.\n\n" +
            "Kiểm tra nhiễu → phân loại mặt đất → tạo DTM/DSM → đối chiếu chiều cao tán với số đo thực địa.\n\n" +
            "Kiểm tra vùng mẫu và sai số cao độ trước khi mở rộng. App hiện chưa hiển thị hoặc xử lý đám mây điểm LiDAR.",
    ),
    SatelliteGuidance(
        "Từ câu hỏi đến hành động",
        "01. Xác định câu hỏi, khu vực, dữ liệu hiện có và đầu ra cần đạt.\n\n" +
            "02. Rà soát hệ tọa độ, thời gian, độ phân giải, độ phủ và sai số.\n\n" +
            "03. Chọn phương pháp; ghi rõ giả định và cách kiểm chứng.\n\n" +
            "04. Chạy thử vùng nhỏ, đối chiếu thực địa rồi mới mở rộng.\n\n" +
            "Đây là hướng dẫn trong app; chưa có dịch vụ đặt lịch hoặc tư vấn trực tuyến.",
    ),
)
