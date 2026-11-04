package com.example.test_tablet.data.vwing

// ---------- Đợt C: ảnh theo thời gian, nước/đất/thời tiết · Đợt D: chỉ số thực vật ----------

enum class ImageKind(val label: String) {
    FIELD("Ảnh thực địa"), ORTHO("Ảnh trực giao đã xử lý"), BAND_RED("Băng Đỏ (Red)"), BAND_NIR("Băng cận hồng ngoại (NIR)")
}

/** Loại cảm biến do người nhập khai báo. Chỉ "Đa phổ" mới được dùng để tính NDVI. */
enum class SensorKind(val label: String) {
    RGB("Camera RGB thông thường"), MULTISPECTRAL("Đa phổ (có băng Red và NIR)"), OTHER("Cảm biến khác"), UNKNOWN("Chưa rõ")
}

/** Khung địa lý của ảnh hướng bắc, trục thẳng (ảnh trực giao). Ảnh xoay/nghiêng cần xử lý trước khi nhập. */
data class GeoBounds(val south: Double, val west: Double, val north: Double, val east: Double) {
    val valid get() = south < north && west < east && south >= -90 && north <= 90 && west >= -180 && east <= 180
    fun contains(p: GeoPoint) = p.lat in south..north && p.lng in west..east
    val center get() = GeoPoint((south + north) / 2, (west + east) / 2)
    fun overlaps(o: GeoBounds) = south < o.north && north > o.south && west < o.east && east > o.west
    override fun toString() = "%.6f,%.6f → %.6f,%.6f".format(south, west, north, east)
}

enum class DetStatus(val label: String) {
    PENDING("Chờ xác nhận"), CONFIRMED("Đã ghép với cây"), NEW_TREE("Đã thêm thành cây mới"), REJECTED("Đã bỏ qua")
}

/** Điểm ứng viên do người dùng nhập từ công cụ đếm cây. Chỉ là gợi ý cho tới khi được xác nhận. */
data class Detection(
    val id: String, val point: GeoPoint, val status: DetStatus = DetStatus.PENDING,
    val treeId: String? = null, val by: String? = null, val at: Long? = null,
)

data class ImageSet(
    val id: String,
    val name: String,
    val kind: ImageKind,
    val capturedAt: Long,
    val source: String,
    val sensor: SensorKind,
    val sensorModel: String = "",
    /** Tên tệp trong thư mục ảnh của app (không phải đường dẫn tuyệt đối). */
    val file: String,
    val widthPx: Int,
    val heightPx: Int,
    val bounds: GeoBounds? = null,
    /** Kích thước điểm ảnh ngoài thực địa (cm/pixel) do người nhập khai báo; nếu trống sẽ suy ra từ khung địa lý. */
    val gsdCm: Double? = null,
    val quality: String = "",
    val detectionModel: String = "",
    val detectionVersion: String = "",
    val detections: List<Detection> = emptyList(),
    val createdAt: Long = Dates.now(),
    val audit: List<AuditEntry> = emptyList(),
    val version: Int = 1,
    val baseVersion: Int = 0,
    val updatedAt: Long = Dates.now(),
    val opId: String = newId(),
    val sync: SyncState = SyncState.LOCAL,
) {
    val georeferenced get() = bounds != null
}

// ---------- nước, đất, thời tiết ----------

enum class MeasureKind(val label: String, val unit: String) {
    RAIN("Lượng mưa", "mm"), IRRIGATION("Nước tưới", "lít"), SOIL_MOISTURE("Độ ẩm đất", "%"), SOIL_LAB("Phân tích mẫu đất", "")
}

enum class MeasureOrigin(val label: String) {
    MEASURED("Đo thực tế"), SENSOR("Cảm biến"), LAB("Phòng thí nghiệm"), FORECAST("Dự báo (chưa đo)")
}

enum class Calibration(val label: String) {
    NA("Không áp dụng"), CALIBRATED("Đã hiệu chuẩn"), UNCALIBRATED("Chưa hiệu chuẩn"), UNKNOWN("Không rõ")
}

data class LabValue(val name: String, val value: Double, val unit: String)

data class Measure(
    val id: String,
    val kind: MeasureKind,
    val origin: MeasureOrigin,
    /** Null cho phân tích đất (dùng [lab]). Không bao giờ thay thế giá trị thiếu bằng 0. */
    val value: Double?,
    val unit: String,
    val measuredAt: Long,
    /** Số ngày mà số đo bao phủ (ví dụ mưa tích lũy 1 ngày). */
    val periodDays: Int = 1,
    val scopeZone: String? = null,
    val treeIds: List<String> = emptyList(),
    /** Vị trí lấy mẫu / đặt cảm biến (nếu biết). */
    val point: GeoPoint? = null,
    val calibration: Calibration = Calibration.NA,
    val labName: String = "",
    val lab: List<LabValue> = emptyList(),
    val note: String = "",
    val author: String = "",
    val createdAt: Long = Dates.now(),
    val audit: List<AuditEntry> = emptyList(),
    val version: Int = 1,
    val baseVersion: Int = 0,
    val updatedAt: Long = Dates.now(),
    val opId: String = newId(),
    val sync: SyncState = SyncState.LOCAL,
)

// ---------- chỉ số thực vật (NDVI) ----------

enum class Preprocess(val label: String) {
    REFLECTANCE("Phản xạ bề mặt đã hiệu chỉnh"), RAW_DN("Giá trị thô (DN) chưa hiệu chỉnh"), UNKNOWN("Chưa rõ")
}

enum class IndexSource(val label: String) {
    COMPUTED("Tính trên máy từ băng Red và NIR"), PROVIDED("Giá trị do nhà cung cấp")
}

/** scope: "garden", "zone:<tên khu>" hoặc "tree:<id>". */
data class IndexStat(val scope: String, val mean: Double, val min: Double, val max: Double, val n: Int, val validPct: Double)

data class VegIndex(
    val id: String,
    val name: String,
    val source: IndexSource,
    val date: Long,
    val provider: String,
    val sensor: SensorKind,
    val preprocess: Preprocess,
    /** Kích thước điểm ảnh khi tính (mét). Null nếu nhà cung cấp không cho biết. */
    val resolutionM: Double? = null,
    val bounds: GeoBounds? = null,
    val redSetId: String? = null,
    val nirSetId: String? = null,
    /** Tệp lưới NDVI đã lượng tử hóa (PNG 8 bit) trong thư mục ảnh; null với giá trị nhập sẵn. */
    val gridFile: String? = null,
    val gridW: Int = 0,
    val gridH: Int = 0,
    val canopyRadiusM: Double = 3.0,
    val stats: List<IndexStat> = emptyList(),
    val notes: List<String> = emptyList(),
    val createdAt: Long = Dates.now(),
    val audit: List<AuditEntry> = emptyList(),
    val version: Int = 1,
    val baseVersion: Int = 0,
    val updatedAt: Long = Dates.now(),
    val opId: String = newId(),
    val sync: SyncState = SyncState.LOCAL,
)
