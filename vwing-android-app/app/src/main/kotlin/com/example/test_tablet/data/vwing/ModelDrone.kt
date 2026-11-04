package com.example.test_tablet.data.vwing

/**
 * Một file flight log DJI đã nhập vào VWing (xem [DjiFlightLog]). Vì số field con bên trong từng
 * khung chưa được xác nhận với file thật (xem ghi chú ở DjiFlightLog.kt), bản ghi này CHƯA tách
 * sẵn tọa độ/tốc độ phun thành cột riêng — chỉ lưu số khung đọc được và trạng thái đọc. Khi đã xác
 * nhận ánh xạ trường bằng file thật, nâng cấp để tính quãng đường/khu vực phun từ [frameCount].
 */
data class DroneFlight(
    val id: String,
    val name: String,
    val importedAt: Long,
    /** Tên tệp trong thư mục ảnh của app (dùng chung chỗ lưu với ảnh/NDVI, không phải đường dẫn tuyệt đối). */
    val file: String,
    val fileSizeBytes: Long,
    val frameCount: Int,
    val parseOk: Boolean,
    val parseError: String = "",
    val note: String = "",
    val createdAt: Long = Dates.now(),
    val audit: List<AuditEntry> = emptyList(),
    val version: Int = 1,
    val baseVersion: Int = 0,
    val updatedAt: Long = Dates.now(),
    val opId: String = newId(),
    val sync: SyncState = SyncState.LOCAL,
)
