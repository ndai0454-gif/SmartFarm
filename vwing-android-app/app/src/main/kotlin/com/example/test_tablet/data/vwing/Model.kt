package com.example.test_tablet.data.vwing

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

// ---------- Ngày giờ (minSdk 24 nên không dùng java.time) ----------

object Dates {
    private const val DAY = 86_400_000L
    fun now() = System.currentTimeMillis()

    fun startOf(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun today() = startOf(now())
    fun daysAgo(n: Int) = startOf(now() - n * DAY) + 12 * 3_600_000L
    fun daysBetween(from: Long, to: Long): Int = Math.round((startOf(to) - startOf(from)).toDouble() / DAY).toInt()
    fun fmt(ms: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).format(ms)
    fun fmtTime(ms: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN")).format(ms)

    /** Đọc dd/MM/yyyy nghiêm ngặt; trả null nếu sai. */
    fun parse(s: String): Long? = try {
        SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).apply { isLenient = false }.parse(s.trim())?.time
    } catch (_: Exception) { null }
}

fun newId(): String = UUID.randomUUID().toString()

// ---------- Kiểu liệt kê ----------

enum class SyncState(val label: String) {
    LOCAL("Đã lưu tại máy"), PENDING("Chờ đồng bộ"), SYNCED("Đã đồng bộ"), ERROR("Lỗi đồng bộ")
}

/** Tình trạng cây do người dùng đánh giá. "Chưa đánh giá" khác với "Ổn định". */
enum class Condition(val label: String) {
    UNASSESSED("Chưa đánh giá"), STABLE("Ổn định"), WATCH("Theo dõi"), NEEDS_CHECK("Cần kiểm tra")
}

enum class PosSource(val label: String) { NONE("Chưa có vị trí"), ESTIMATED("Vị trí ước lượng"), VERIFIED("Vị trí đã xác minh") }

enum class EventType(val label: String) {
    OBSERVATION("Quan sát"), CARE("Chăm sóc"), HARVEST("Thu hoạch"), RECHECK("Tái kiểm tra")
}

enum class EvidenceSource(val label: String) {
    OWNER_NOTE("Ghi chép của chủ vườn"), MEASUREMENT("Kết quả đo"), DOCUMENT("Tài liệu")
}

enum class Priority(val label: String) { LOW("Thấp"), MEDIUM("Vừa"), HIGH("Cao") }

enum class IssueStatus(val label: String) {
    NEW("Mới ghi nhận"), PENDING_VERIFY("Chờ xác minh"), IN_PROGRESS("Đang xử lý"),
    PENDING_RECHECK("Chờ tái kiểm tra"), CONFIRMED("Đã xác nhận kết quả");

    val isOpen get() = this != CONFIRMED
}

enum class FeatureKind(val label: String) {
    BOUNDARY("Ranh vườn"), ZONE("Khu vực"), ROAD("Đường đi"), WATER("Nguồn nước"), OBSTACLE("Vật cản"),
    WATERLOG("Chỗ đọng nước"), DRY("Chỗ khô hạn")
}

// ---------- Thực thể ----------

data class GeoPoint(val lat: Double, val lng: Double)

data class AuditEntry(val at: Long, val by: String, val summary: String)

data class Photo(val path: String, val takenAt: Long?, val uploadedAt: Long)

data class Zone(val name: String, val desc: String)

data class Garden(
    val name: String = "Vườn của tôi",
    val location: String = "",
    val crop: String = "",
    val areaHa: Double? = null,
    val harvestCycle: String = "",
    val checkIntervalDays: Int = 14,
    val operatorName: String = "Người làm vườn",
    val avgWeightKg: Double? = null,
    val commercialRatio: Double = 1.0,
    val spreadPct: Double = 10.0,
    val pricePerKg: Double? = null,
    val priceSource: String = "",
    val priceRegion: String = "",
    val priceGrade: String = "",
    val priceDate: Long? = null,
    /** Thời điểm sửa thông tin vườn; bản mới hơn thắng khi đồng bộ. 0 = chưa từng sửa. */
    val updatedAt: Long = 0,
)

/** Dấu vết đã xoá: truyền đi khi đồng bộ để thiết bị khác xoá theo và không đưa bản ghi đã xoá trở lại. type là tên bộ sưu tập ("trees", "events", …, "zones" với id = tên khu). */
data class Tombstone(val type: String, val id: String, val at: Long, val by: String)

data class Tree(
    val id: String,
    val code: String,
    val zone: String,
    val variety: String = "",
    val plantedYear: Int? = null,
    val fruits: Int? = null,
    val fruitsAt: Long? = null,
    val condition: Condition = Condition.UNASSESSED,
    val pos: GeoPoint? = null,
    val posSource: PosSource = PosSource.NONE,
    val posAt: Long? = null,
    val replacesId: String? = null,
    val replacedById: String? = null,
    val active: Boolean = true,
    val createdAt: Long = Dates.now(),
    val audit: List<AuditEntry> = emptyList(),
    val version: Int = 1,
    val baseVersion: Int = 0,
    val updatedAt: Long = Dates.now(),
    val opId: String = newId(),
    val sync: SyncState = SyncState.LOCAL,
)

data class Event(
    val id: String,
    val treeIds: List<String>,
    /** Phạm vi khu tại thời điểm ghi; danh sách cây được chụp lại, không tự mở rộng khi thêm cây sau này. */
    val scopeZone: String? = null,
    val type: EventType,
    val activity: String,
    val occurredAt: Long,
    val recordedAt: Long = Dates.now(),
    val author: String,
    val note: String = "",
    val product: String? = null,
    val dosage: String? = null,
    val amountKg: Double? = null,
    val photos: List<Photo> = emptyList(),
    val source: EvidenceSource = EvidenceSource.OWNER_NOTE,
    val issueId: String? = null,
    val result: String? = null,
    val confirmedBy: String? = null,
    val conditionSet: Condition? = null,
    val audit: List<AuditEntry> = emptyList(),
    val version: Int = 1,
    val baseVersion: Int = 0,
    val updatedAt: Long = Dates.now(),
    val opId: String = newId(),
    val sync: SyncState = SyncState.LOCAL,
)

data class Issue(
    val id: String,
    val number: Int,
    val treeIds: List<String>,
    val scopeZone: String? = null,
    val symptom: String,
    val priority: Priority = Priority.MEDIUM,
    val status: IssueStatus = IssueStatus.NEW,
    val assignee: String = "",
    val dueAt: Long? = null,
    val createdAt: Long = Dates.now(),
    val closedAt: Long? = null,
    val confirmedBy: String? = null,
    val originEventId: String? = null,
    val audit: List<AuditEntry> = emptyList(),
    val version: Int = 1,
    val baseVersion: Int = 0,
    val updatedAt: Long = Dates.now(),
    val opId: String = newId(),
    val sync: SyncState = SyncState.LOCAL,
) {
    val code get() = "VĐ-%03d".format(number)
}

data class Task(
    val id: String,
    val title: String,
    val treeIds: List<String> = emptyList(),
    val scopeZone: String? = null,
    val assignee: String = "",
    val dueAt: Long? = null,
    val done: Boolean = false,
    val doneAt: Long? = null,
    val doneBy: String? = null,
    val issueId: String? = null,
    val fromAi: Boolean = false,
    /** Đơn việc mở: chưa giao ai, người nhận việc (nhân viên/phi công) tự nhận. Khi có người nhận thì open = false. */
    val open: Boolean = false,
    val version: Int = 1,
    val baseVersion: Int = 0,
    val updatedAt: Long = Dates.now(),
    val opId: String = newId(),
    val sync: SyncState = SyncState.LOCAL,
)

data class Sale(
    val id: String,
    val date: Long,
    val kg: Double,
    val pricePerKg: Double,
    val note: String = "",
    val version: Int = 1,
    val baseVersion: Int = 0,
    val updatedAt: Long = Dates.now(),
    val opId: String = newId(),
    val sync: SyncState = SyncState.LOCAL,
)

data class GeoFeature(
    val id: String,
    val kind: FeatureKind,
    val name: String,
    val points: List<GeoPoint>,
    val note: String = "",
    val source: String = "",
    val importedAt: Long = Dates.now(),
    val version: Int = 1,
    val baseVersion: Int = 0,
    val updatedAt: Long = Dates.now(),
    val opId: String = newId(),
    val sync: SyncState = SyncState.LOCAL,
)

data class Conflict(
    val id: String,
    val entityType: String,
    val entityId: String,
    val label: String,
    val localJson: String,
    val remoteJson: String,
    val at: Long = Dates.now(),
)

data class GardenData(
    val garden: Garden = Garden(),
    val zones: List<Zone> = emptyList(),
    val trees: List<Tree> = emptyList(),
    val events: List<Event> = emptyList(),
    val issues: List<Issue> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val sales: List<Sale> = emptyList(),
    val features: List<GeoFeature> = emptyList(),
    val imagery: List<ImageSet> = emptyList(),
    val measures: List<Measure> = emptyList(),
    val indices: List<VegIndex> = emptyList(),
    val droneFlights: List<DroneFlight> = emptyList(),
    val conflicts: List<Conflict> = emptyList(),
    val tombstones: List<Tombstone> = emptyList(),
    val isSample: Boolean = false,
    val configured: Boolean = false,
)
