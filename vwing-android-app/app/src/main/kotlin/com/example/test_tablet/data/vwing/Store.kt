package com.example.test_tablet.data.vwing

import android.content.ContentResolver
import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class Outcome private constructor(val ok: Boolean, val message: String?, val conflict: Boolean) {
    companion object {
        val OK = Outcome(true, null, false)
        fun err(msg: String) = Outcome(false, msg, false)
        fun conflict() = Outcome(false, "Bản ghi vừa được thay đổi ở nơi khác. Mở lại để xem bản mới nhất rồi sửa lại.", true)
    }
}

class MergeReport(var added: Int = 0, var updated: Int = 0, var skipped: Int = 0, var conflicts: Int = 0, var photos: Int = 0, var deleted: Int = 0) {
    override fun toString() = "Thêm $added · cập nhật $updated · bỏ qua (đã có) $skipped · xung đột $conflicts · ảnh $photos"
}

/**
 * Kho dữ liệu duy nhất của vườn. Mọi thay đổi được ghi xuống tệp trên thiết bị ngay (ghi nguyên tử), nên đóng/mở app
 * hoặc mất mạng không làm mất dữ liệu. Chưa có máy chủ đồng bộ: bản ghi giữ trạng thái "Đã lưu tại máy".
 */
class VWingStore internal constructor(private val baseDir: File, private val resolver: ContentResolver?, seedIfEmpty: Boolean = true) {
    private constructor(ctx: Context) : this(ctx.filesDir, ctx.contentResolver)

    private val seedIfEmpty = seedIfEmpty
    var activeFarmerKey: String? by mutableStateOf(null)
        private set
    private val file: File
        get() {
            val k = activeFarmerKey?.trim()?.replace(Regex("[^A-Za-z0-9_+-]"), "_")?.takeIf { it.isNotEmpty() }
            return if (k == null) File(baseDir, "vwing_data.json") else File(baseDir, "vwing_farmer_$k.json")
        }
    val photoDir = File(baseDir, "vwing_photos").apply { mkdirs() }
    private val io = Executors.newSingleThreadExecutor()

    /** Hook tự động đồng bộ dữ liệu vườn của nông dân lên Firebase Cloud Firestore mỗi khi dữ liệu thay đổi. */
    var onPersistHook: ((farmerKey: String?, snapshotJson: String, gardenData: GardenData) -> Unit)? = null

    var data by mutableStateOf(GardenData())
        private set

    /** Có lỗi đọc dữ liệu khi khởi động (tệp cũ được giữ lại với đuôi .corrupt). */
    var loadError: String? by mutableStateOf(null)
        private set

    init {
        load()
    }

    companion object {
        @Volatile private var instance: VWingStore? = null
        fun get(ctx: Context): VWingStore = instance ?: synchronized(this) {
            instance ?: VWingStore(ctx.applicationContext).also { instance = it }
        }
    }

    private fun load(customInitial: GardenData? = null) {
        val targetFile = file
        if (!targetFile.exists()) {
            data = customInitial ?: if (seedIfEmpty) Seed.build() else GardenData(configured = true)
            persist(notifyCloud = customInitial != null)
            return
        }
        try {
            data = VJson.fromJson(JSONObject(targetFile.readText()))
        } catch (e: Exception) {
            targetFile.renameTo(File(targetFile.parentFile, "${targetFile.name}.corrupt"))
            loadError = "Không đọc được dữ liệu cũ (${e.message}). Tệp được giữ lại với đuôi .corrupt."
            data = customInitial ?: Seed.build()
            persist(notifyCloud = false)
        }
    }

    private fun persist(notifyCloud: Boolean = true) {
        val currentData = data
        val currentKey = activeFarmerKey
        val targetFile = file
        val text = VJson.toJson(currentData).toString()
        io.execute {
            val tmp = File(targetFile.parentFile, "${targetFile.name}.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(targetFile)) { targetFile.delete(); tmp.renameTo(targetFile) }
        }
        if (notifyCloud) {
            onPersistHook?.invoke(currentKey, text, currentData)
        }
    }

    /**
     * Chuyển đổi không gian dữ liệu theo số điện thoại của từng người nông dân:
     * Mỗi số điện thoại có một tệp dữ liệu riêng (`vwing_farmer_<phone>.json`) và tài liệu riêng trên Firebase Firestore.
     */
    fun switchFarmerAccount(farmerKey: String?, initialGardenData: GardenData? = null) {
        val normalized = farmerKey?.trim()?.takeIf { it.isNotEmpty() }
        if (activeFarmerKey == normalized && initialGardenData == null) return
        activeFarmerKey = normalized
        load(customInitial = initialGardenData)
    }

    /** Nạp bản ghi dữ liệu vườn của người nông dân tải về từ Firebase Cloud Firestore. */
    fun replaceWithCloudSnapshot(cloudData: GardenData) {
        data = cloudData
        persist(notifyCloud = false)
    }

    private fun mutate(f: (GardenData) -> GardenData) {
        data = f(data)
        persist()
    }

    fun dismissLoadError() { loadError = null }

    /** Chờ các lần ghi tệp đang chờ hoàn tất (dùng cho kiểm thử và khi cần chắc chắn đã ghi). */
    fun flush() { io.submit {}.get() }

    // ---------- bump phiên bản ----------
    private fun Tree.bump(by: String, summary: String) = copy(
        version = version + 1, baseVersion = version, updatedAt = Dates.now(), opId = newId(), sync = SyncState.LOCAL,
        audit = audit + AuditEntry(Dates.now(), by, summary),
    )
    private fun Event.bump(by: String, summary: String) = copy(
        version = version + 1, baseVersion = version, updatedAt = Dates.now(), opId = newId(), sync = SyncState.LOCAL,
        audit = audit + AuditEntry(Dates.now(), by, summary),
    )
    private fun Issue.bump(by: String, summary: String) = copy(
        version = version + 1, baseVersion = version, updatedAt = Dates.now(), opId = newId(), sync = SyncState.LOCAL,
        audit = audit + AuditEntry(Dates.now(), by, summary),
    )
    private fun Task.bump() = copy(version = version + 1, baseVersion = version, updatedAt = Dates.now(), opId = newId(), sync = SyncState.LOCAL)

    private val by get() = data.garden.operatorName

    // ---------- vườn ----------
    fun updateGarden(g: Garden) = mutate { it.copy(garden = g.copy(updatedAt = Dates.now()), configured = true) }

    // ---------- xoá lan truyền (xem Cascade.kt) ----------
    /** Số bản ghi mỗi loại sẽ bị xoá nếu xoá mục này — để hiện trong hộp xác nhận. */
    fun deletePreview(type: String, id: String): Map<String, Int> =
        Cascade.delete(data, type, id, by).removed.groupingBy { it.type }.eachCount()

    /** Xoá một bản ghi cùng mọi thứ phụ thuộc và ghi dấu vết để lần đồng bộ sau xoá trên mọi thiết bị. */
    fun deleteEntity(type: String, id: String): Outcome {
        if (type !in Cascade.TYPES) return Outcome.err("Loại bản ghi không hợp lệ.")
        val before = data
        val r = Cascade.delete(before, type, id, by)
        if (r.removed.isEmpty()) return Outcome.err("Không tìm thấy bản ghi.")
        val known = r.removed.map { it.type to it.id }.toSet()
        mutate { r.data.copy(tombstones = r.data.tombstones.filter { (it.type to it.id) !in known } + r.removed) }
        // dọn tệp ảnh/lưới/log trên máy mà không còn bản ghi nào dùng
        Cascade.orphanFiles(before, data, ::referencedFiles).forEach { runCatching { File(photoDir, it).delete() } }
        return Outcome.OK
    }

    fun addZone(name: String, desc: String): Outcome {
        val n = name.trim()
        if (n.isEmpty()) return Outcome.err("Nhập tên khu.")
        if (data.zones.any { it.name.equals(n, true) }) return Outcome.err("Khu \"$n\" đã tồn tại.")
        mutate { it.copy(zones = it.zones + Zone(n, desc.trim())) }
        return Outcome.OK
    }

    fun clearSample() = mutate {
        GardenData(garden = it.garden.copy(name = "Vườn của tôi", location = "", crop = "", areaHa = null,
            avgWeightKg = null, pricePerKg = null, priceSource = "", priceDate = null), configured = true)
    }

    // ---------- cây ----------
    private fun codeTaken(code: String, exceptId: String? = null): Boolean {
        val n = normalizeCode(code) ?: code.trim().uppercase()
        return data.trees.any { it.active && it.id != exceptId && (normalizeCode(it.code) ?: it.code.trim().uppercase()) == n }
    }

    fun addTree(
        code: String, zone: String, variety: String = "", plantedYear: Int? = null, fruits: Int? = null,
        pos: GeoPoint? = null, posSource: PosSource = PosSource.NONE,
    ): Outcome {
        val c = code.trim()
        if (c.isEmpty()) return Outcome.err("Nhập mã cây.")
        if (zone.isBlank()) return Outcome.err("Chọn hoặc nhập khu vực.")
        if (codeTaken(c)) return Outcome.err("Mã cây \"$c\" đã tồn tại.")
        if (fruits != null && fruits < 0) return Outcome.err("Số trái không được âm.")
        val t = Tree(
            id = newId(), code = c, zone = zone.trim(), variety = variety.trim(), plantedYear = plantedYear,
            fruits = fruits, fruitsAt = if (fruits != null) Dates.now() else null,
            pos = pos, posSource = if (pos != null && posSource == PosSource.NONE) PosSource.ESTIMATED else posSource,
            posAt = if (pos != null) Dates.now() else null,
            audit = listOf(AuditEntry(Dates.now(), by, "Tạo hồ sơ cây")),
        )
        mutate { d ->
            d.copy(trees = d.trees + t, zones = if (d.zones.any { it.name == t.zone }) d.zones else d.zones + Zone(t.zone, ""))
        }
        return Outcome.OK
    }

    /** Sửa cây có kiểm tra phiên bản. Đổi mã/vị trí giữ nguyên ID nên lịch sử và liên kết không mất. */
    fun editTree(id: String, baseVersion: Int, summary: String, f: (Tree) -> Tree): Outcome {
        val cur = data.treeById(id) ?: return Outcome.err("Không tìm thấy cây.")
        if (cur.version != baseVersion) return Outcome.conflict()
        val next = f(cur)
        if (next.code != cur.code && codeTaken(next.code, id)) return Outcome.err("Mã cây \"${next.code}\" đã tồn tại.")
        if (next.fruits != null && next.fruits < 0) return Outcome.err("Số trái không được âm.")
        val stamped = next.copy(
            fruitsAt = if (next.fruits != cur.fruits) Dates.now() else next.fruitsAt,
            posAt = if (next.pos != cur.pos) Dates.now() else next.posAt,
        ).bump(by, summary)
        mutate { d ->
            d.copy(trees = d.trees.map { if (it.id == id) stamped else it },
                zones = if (d.zones.any { it.name == stamped.zone } || stamped.zone.isBlank()) d.zones else d.zones + Zone(stamped.zone, ""))
        }
        return Outcome.OK
    }

    /** Cây thay thế nhận ID mới và liên kết tới cây cũ; không thừa kế lịch sử của cây cũ. */
    fun replaceTree(oldId: String, newCode: String, reason: String): Outcome {
        val old = data.treeById(oldId) ?: return Outcome.err("Không tìm thấy cây.")
        if (newCode.isBlank()) return Outcome.err("Nhập mã cây mới.")
        if (codeTaken(newCode, oldId)) return Outcome.err("Mã cây \"$newCode\" đã tồn tại.")
        val fresh = Tree(
            id = newId(), code = newCode.trim(), zone = old.zone, pos = old.pos,
            posSource = if (old.pos != null) PosSource.ESTIMATED else PosSource.NONE, posAt = old.posAt,
            replacesId = old.id, audit = listOf(AuditEntry(Dates.now(), by, "Thay thế cây ${old.code}. $reason".trim())),
        )
        mutate { d ->
            d.copy(trees = d.trees.map {
                if (it.id == oldId) it.copy(active = false, replacedById = fresh.id).bump(by, "Đã thay bằng ${fresh.code}. $reason".trim()) else it
            } + fresh)
        }
        return Outcome.OK
    }

    // ---------- nhật ký / sự kiện ----------
    /** Idempotent theo e.id: gửi lại cùng một thao tác không tạo hai bản ghi. */
    fun addEvent(e: Event, fruits: Int? = null): Outcome {
        if (data.events.any { it.id == e.id }) return Outcome.OK
        if (e.treeIds.isEmpty() && e.scopeZone == null) return Outcome.err("Chọn cây hoặc khu vực áp dụng.")
        mutate { d ->
            var trees = d.trees
            val cond = e.conditionSet
            if (cond != null || fruits != null) {
                trees = trees.map { t ->
                    if (t.id !in e.treeIds) t else {
                        var n = t
                        val notes = mutableListOf<String>()
                        if (cond != null && cond != t.condition) { n = n.copy(condition = cond); notes += "Tình trạng: ${t.condition.label} → ${cond.label}" }
                        if (fruits != null && e.treeIds.size == 1 && fruits != t.fruits) {
                            n = n.copy(fruits = fruits, fruitsAt = e.occurredAt); notes += "Số trái: ${t.fruits ?: "chưa đếm"} → $fruits"
                        }
                        if (notes.isEmpty()) t else n.bump(by, notes.joinToString("; "))
                    }
                }
            }
            var issues = d.issues
            if (e.issueId != null && e.type == EventType.CARE) {
                issues = issues.map {
                    if (it.id == e.issueId && (it.status == IssueStatus.NEW || it.status == IssueStatus.PENDING_VERIFY))
                        it.copy(status = IssueStatus.IN_PROGRESS).bump(by, "Bắt đầu xử lý: ${e.activity}") else it
                }
            }
            d.copy(events = d.events + e, trees = trees, issues = issues)
        }
        return Outcome.OK
    }

    fun editEvent(id: String, baseVersion: Int, f: (Event) -> Event): Outcome {
        val cur = data.events.firstOrNull { it.id == id } ?: return Outcome.err("Không tìm thấy ghi chép.")
        if (cur.version != baseVersion) return Outcome.conflict()
        val next = f(cur)
        val diff = buildList {
            if (next.note != cur.note) add("ghi chú")
            if (next.product != cur.product) add("vật tư")
            if (next.dosage != cur.dosage) add("liều lượng")
            if (next.occurredAt != cur.occurredAt) add("ngày thực hiện ${Dates.fmt(cur.occurredAt)} → ${Dates.fmt(next.occurredAt)}")
            if (next.amountKg != cur.amountKg) add("khối lượng")
        }
        if (diff.isEmpty()) return Outcome.OK
        val stamped = next.bump(by, "Sửa: " + diff.joinToString(", "))
        mutate { d -> d.copy(events = d.events.map { if (it.id == id) stamped else it }) }
        return Outcome.OK
    }

    // ---------- vấn đề ----------
    fun addIssue(
        treeIds: List<String>, scopeZone: String?, symptom: String, priority: Priority,
        assignee: String, dueAt: Long?, originEventId: String? = null,
    ): Outcome {
        if (symptom.isBlank()) return Outcome.err("Mô tả biểu hiện quan sát.")
        if (treeIds.isEmpty() && scopeZone == null) return Outcome.err("Chọn cây hoặc khu vực.")
        val number = (data.issues.maxOfOrNull { it.number } ?: 0) + 1
        val issue = Issue(
            id = newId(), number = number, treeIds = treeIds, scopeZone = scopeZone, symptom = symptom.trim(),
            priority = priority, status = IssueStatus.NEW, assignee = assignee.trim(), dueAt = dueAt,
            originEventId = originEventId, audit = listOf(AuditEntry(Dates.now(), by, "Ghi nhận vấn đề")),
        )
        mutate { it.copy(issues = it.issues + issue) }
        return Outcome.OK
    }

    fun editIssue(id: String, baseVersion: Int, summary: String, f: (Issue) -> Issue): Outcome {
        val cur = data.issues.firstOrNull { it.id == id } ?: return Outcome.err("Không tìm thấy vấn đề.")
        if (cur.version != baseVersion) return Outcome.conflict()
        val next = f(cur).bump(by, summary)
        mutate { d -> d.copy(issues = d.issues.map { if (it.id == id) next else it }) }
        return Outcome.OK
    }

    private val transitions = mapOf(
        IssueStatus.NEW to setOf(IssueStatus.PENDING_VERIFY, IssueStatus.IN_PROGRESS),
        IssueStatus.PENDING_VERIFY to setOf(IssueStatus.IN_PROGRESS),
        IssueStatus.IN_PROGRESS to setOf(IssueStatus.PENDING_RECHECK),
        IssueStatus.PENDING_RECHECK to setOf(IssueStatus.IN_PROGRESS),
        IssueStatus.CONFIRMED to setOf(IssueStatus.PENDING_VERIFY),
    )

    /** Chuyển trạng thái trừ "Đã xác nhận kết quả" (chỉ qua [recheckIssue] có bằng chứng và người xác nhận). */
    fun setIssueStatus(id: String, baseVersion: Int, to: IssueStatus): Outcome {
        val cur = data.issues.firstOrNull { it.id == id } ?: return Outcome.err("Không tìm thấy vấn đề.")
        if (to == IssueStatus.CONFIRMED) return Outcome.err("Chỉ xác nhận kết quả qua bước tái kiểm tra có bằng chứng.")
        if (to !in (transitions[cur.status] ?: emptySet())) return Outcome.err("Không thể chuyển từ \"${cur.status.label}\" sang \"${to.label}\".")
        val reopen = cur.status == IssueStatus.CONFIRMED
        return editIssue(id, baseVersion, if (reopen) "Mở lại vấn đề" else "Chuyển sang ${to.label}") {
            it.copy(status = to, closedAt = if (reopen) null else it.closedAt, confirmedBy = if (reopen) null else it.confirmedBy)
        }
    }

    /**
     * Tái kiểm tra: ghi một sự kiện RECHECK. Chỉ đóng vấn đề khi đạt, có bằng chứng (ghi chú hoặc ảnh) và người xác nhận.
     * Không đạt -> quay lại "Đang xử lý".
     */
    fun recheckIssue(
        id: String, baseVersion: Int, passed: Boolean, note: String, photos: List<Photo>,
        confirmer: String, occurredAt: Long,
    ): Outcome {
        val cur = data.issues.firstOrNull { it.id == id } ?: return Outcome.err("Không tìm thấy vấn đề.")
        if (cur.version != baseVersion) return Outcome.conflict()
        if (confirmer.isBlank()) return Outcome.err("Nhập người xác nhận.")
        if (note.isBlank() && photos.isEmpty()) return Outcome.err("Cần ghi chú hoặc ảnh làm bằng chứng.")
        val ev = Event(
            id = newId(), treeIds = cur.treeIds, scopeZone = cur.scopeZone, type = EventType.RECHECK,
            activity = "Tái kiểm tra ${cur.code}", occurredAt = occurredAt, author = by, note = note.trim(),
            photos = photos, issueId = cur.id, result = if (passed) "Đã đạt" else "Chưa đạt", confirmedBy = confirmer.trim(),
            conditionSet = if (passed) Condition.STABLE else Condition.NEEDS_CHECK,
        )
        val r = addEvent(ev)
        if (!r.ok) return r
        val fresh = data.issues.first { it.id == id }
        return editIssue(id, fresh.version,
            if (passed) "Xác nhận kết quả bởi ${confirmer.trim()}" else "Tái kiểm tra chưa đạt (${confirmer.trim()})") {
            if (passed) it.copy(status = IssueStatus.CONFIRMED, closedAt = Dates.now(), confirmedBy = confirmer.trim())
            else it.copy(status = IssueStatus.IN_PROGRESS)
        }
    }

    // ---------- công việc ----------
    fun addTask(
        title: String, treeIds: List<String>, scopeZone: String?, assignee: String, dueAt: Long?,
        issueId: String? = null, fromAi: Boolean = false, open: Boolean = false,
    ): Outcome {
        if (title.isBlank()) return Outcome.err("Nhập tên công việc.")
        if (open && assignee.isNotBlank()) return Outcome.err("Đơn việc mở chưa giao ai: để trống người thực hiện.")
        mutate {
            it.copy(tasks = it.tasks + Task(newId(), title.trim(), treeIds, scopeZone, assignee.trim(), dueAt, issueId = issueId, fromAi = fromAi, open = open))
        }
        return Outcome.OK
    }

    /** Nhận một đơn việc mở. [who] phải đúng tên tài khoản (máy chủ so khớp theo tên này). Hai người nhận cùng lúc: máy chủ giữ người đến trước. */
    fun claimTask(id: String, who: String): Outcome {
        val t = data.tasks.firstOrNull { it.id == id } ?: return Outcome.err("Không tìm thấy công việc.")
        if (!t.open || t.assignee.isNotBlank()) return Outcome.err("Việc này đã có người nhận.")
        if (who.isBlank()) return Outcome.err("Chưa biết tên người nhận việc.")
        mutate { d -> d.copy(tasks = d.tasks.map { if (it.id == id) it.bump().copy(assignee = who.trim(), open = false) else it }) }
        return Outcome.OK
    }

    /** Trả lại việc đã nhận (chưa xong) về danh sách đơn việc mở. */
    fun releaseTask(id: String, who: String): Outcome {
        val t = data.tasks.firstOrNull { it.id == id } ?: return Outcome.err("Không tìm thấy công việc.")
        if (t.done) return Outcome.err("Việc đã xong, không trả lại được.")
        if (!t.assignee.trim().equals(who.trim(), true)) return Outcome.err("Đây không phải việc của bạn.")
        mutate { d -> d.copy(tasks = d.tasks.map { if (it.id == id) it.bump().copy(assignee = "", open = true) else it }) }
        return Outcome.OK
    }

    /** Hoàn tất công việc không tự xác nhận hồi phục: vấn đề chỉ chuyển sang "Chờ tái kiểm tra". */
    fun setTaskDone(id: String, done: Boolean) {
        val t = data.tasks.firstOrNull { it.id == id } ?: return
        mutate { d ->
            val tasks = d.tasks.map {
                if (it.id == id) it.bump().copy(done = done, doneAt = if (done) Dates.now() else null, doneBy = if (done) by else null) else it
            }
            var issues = d.issues
            if (done && t.issueId != null && tasks.filter { it.issueId == t.issueId }.all { it.done }) {
                issues = issues.map {
                    if (it.id == t.issueId && it.status == IssueStatus.IN_PROGRESS)
                        it.copy(status = IssueStatus.PENDING_RECHECK).bump(by, "Hoàn tất công việc; chờ tái kiểm tra") else it
                }
            }
            d.copy(tasks = tasks, issues = issues)
        }
    }

    // ---------- sản lượng ----------
    fun addSale(date: Long, kg: Double, pricePerKg: Double, note: String): Outcome {
        if (kg <= 0) return Outcome.err("Khối lượng phải lớn hơn 0.")
        if (pricePerKg < 0) return Outcome.err("Giá không được âm.")
        mutate { it.copy(sales = it.sales + Sale(newId(), date, kg, pricePerKg, note.trim())) }
        return Outcome.OK
    }

    // ---------- bản đồ ----------
    class TreePosUpdate(val code: String, val zone: String?, val pos: GeoPoint)

    fun applyGeoImport(features: List<GeoFeature>, trees: List<TreePosUpdate>, source: String): Outcome {
        mutate { d ->
            var fs = d.features
            for (f in features) {
                fs = if (f.kind == FeatureKind.BOUNDARY) fs.filter { it.kind != FeatureKind.BOUNDARY } + f
                else fs.filter { !(it.kind == f.kind && it.name == f.name) } + f
            }
            var ts = d.trees
            val newTrees = mutableListOf<Tree>()
            for (u in trees) {
                val existing = ts.firstOrNull { it.active && (normalizeCode(it.code) ?: it.code) == (normalizeCode(u.code) ?: u.code) }
                if (existing != null) {
                    ts = ts.map { if (it.id == existing.id) it.copy(pos = u.pos, posSource = PosSource.ESTIMATED, posAt = Dates.now()).bump(by, "Nhập vị trí từ $source") else it }
                } else {
                    newTrees += Tree(newId(), u.code, u.zone ?: "Chưa phân khu", pos = u.pos, posSource = PosSource.ESTIMATED, posAt = Dates.now(),
                        audit = listOf(AuditEntry(Dates.now(), by, "Tạo cây từ $source")))
                }
            }
            val zoneAdds = newTrees.map { it.zone }.distinct().filter { z -> d.zones.none { it.name == z } }.map { Zone(it, "") }
            d.copy(features = fs, trees = ts + newTrees, zones = d.zones + zoneAdds)
        }
        return Outcome.OK
    }

    class TreeRow(val code: String, val zone: String, val variety: String, val year: Int?, val fruits: Int?, val pos: GeoPoint?)

    fun bulkAddTrees(rows: List<TreeRow>): Int {
        var added = 0
        mutate { d ->
            val taken = d.trees.filter { it.active }.map { normalizeCode(it.code) ?: it.code.uppercase() }.toMutableSet()
            val add = mutableListOf<Tree>()
            for (r in rows) {
                val key = normalizeCode(r.code) ?: r.code.trim().uppercase()
                if (!taken.add(key)) continue
                add += Tree(newId(), r.code.trim(), r.zone.trim(), r.variety.trim(), r.year, r.fruits,
                    if (r.fruits != null) Dates.now() else null, pos = r.pos,
                    posSource = if (r.pos != null) PosSource.ESTIMATED else PosSource.NONE, posAt = if (r.pos != null) Dates.now() else null,
                    audit = listOf(AuditEntry(Dates.now(), by, "Nhập hàng loạt")))
            }
            added = add.size
            val zoneAdds = add.map { it.zone }.distinct().filter { z -> d.zones.none { it.name == z } }.map { Zone(it, "") }
            d.copy(trees = d.trees + add, zones = d.zones + zoneAdds)
        }
        return added
    }

    fun syncGeeTrees(points: List<SatTreePoint>, variety: String, regionName: String): Pair<Int, Int> {
        var added = 0
        var updated = 0
        mutate { d ->
            var ts = d.trees
            val newTrees = mutableListOf<Tree>()
            val now = Dates.now()
            for (sp in points) {
                val codeClean = sp.code.trim()
                val zoneName = "Hàng ${sp.rowNumber} · ${regionName.ifBlank { "Vùng GEE" }}"
                val summary = "GEE (${sp.resolvedStatusVi} · ${sp.healthScore}/100 · NDVI ${sp.ndvi?.let { "%.2f".format(Locale.US, it) } ?: "—"})"
                val existing = ts.firstOrNull { it.active && (normalizeCode(it.code) ?: it.code.uppercase()) == (normalizeCode(codeClean) ?: codeClean.uppercase()) }
                if (existing != null) {
                    updated++
                    ts = ts.map {
                        if (it.id == existing.id) {
                            it.copy(
                                pos = sp.pos,
                                posSource = PosSource.ESTIMATED,
                                posAt = now,
                                condition = sp.condition,
                                variety = it.variety.ifBlank { variety },
                            ).bump(by, "Cập nhật từ $summary")
                        } else it
                    }
                } else {
                    added++
                    newTrees += Tree(
                        id = newId(),
                        code = codeClean,
                        zone = zoneName,
                        variety = variety.trim(),
                        condition = sp.condition,
                        pos = sp.pos,
                        posSource = PosSource.ESTIMATED,
                        posAt = now,
                        audit = listOf(AuditEntry(now, by, "Đếm & gắn nhãn từ Google Earth Engine: $summary — ${sp.resolvedDiagnosisVi}")),
                    )
                }
            }
            val zoneAdds = newTrees.map { it.zone }.distinct().filter { z -> d.zones.none { it.name == z } }.map { Zone(it, "Phân hàng tự động từ Google Earth Engine") }
            d.copy(trees = ts + newTrees, zones = d.zones + zoneAdds)
        }
        return added to updated
    }

    // ---------- ảnh ----------
    /** Sao chép ảnh vào bộ nhớ app; lấy ngày chụp từ EXIF nếu có (ngày tải lên được lưu riêng). */
    fun importPhoto(uri: Uri): Photo? {
        return try {
            val f = File(photoDir, newId() + ".jpg")
            val copied = resolver?.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) }; true } ?: false
            if (!copied) return null
            val taken = try {
                ExifInterface(f.absolutePath).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let {
                    SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(it)?.time
                }
            } catch (_: Exception) { null }
            Photo(f.absolutePath, taken, Dates.now())
        } catch (_: Exception) { null }
    }

    // ---------- sao lưu / hợp nhất ----------
    fun exportBackup(out: OutputStream) {
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("data.json")); z.write(VJson.toJson(data).toString().toByteArray()); z.closeEntry()
            (data.events.flatMap { it.photos }.map { File(it.path) } + referencedFiles(data).map { File(photoDir, it) }).distinct().filter { it.exists() }.forEach { f ->
                z.putNextEntry(ZipEntry("photos/" + f.name)); f.inputStream().use { it.copyTo(z) }; z.closeEntry()
            }
        }
    }

    fun exportJsonText(): String = VJson.toJson(data).toString(2)

    /** Hợp nhất bản sao lưu (.zip hoặc .json). Không ghi đè âm thầm: khác biệt không thể hòa giải được đưa vào danh sách xung đột. */
    fun importBackup(input: InputStream): Result<MergeReport> = runCatching {
        val bytes = input.readBytes()
        val report = MergeReport()
        val jsonText: String
        if (bytes.size > 3 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            var text: String? = null
            ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    if (e.name == "data.json") text = z.readBytes().toString(Charsets.UTF_8)
                    else if (e.name.startsWith("photos/") && !e.isDirectory) {
                        val name = File(e.name).name
                        val dest = File(photoDir, name)
                        if (!dest.exists()) { dest.outputStream().use { z.copyTo(it) }; report.photos++ }
                    }
                    e = z.nextEntry
                }
            }
            jsonText = text ?: error("Tệp sao lưu không có data.json")
        } else jsonText = bytes.toString(Charsets.UTF_8)
        val remote = VJson.fromJson(JSONObject(jsonText))
        mutate { merge(it, remote, report) }
        report
    }

    private fun photoPathsFix(e: Event): Event = e.copy(photos = e.photos.map { p ->
        val name = File(p.path).name
        p.copy(path = File(photoDir, name).absolutePath)
    })

    private fun <T> mergeList(
        local: List<T>, remote: List<T>, type: String, report: MergeReport, conflicts: MutableList<Conflict>,
        id: (T) -> String, ver: (T) -> Int, base: (T) -> Int, op: (T) -> String, label: (T) -> String, json: (T) -> JSONObject,
    ): List<T> {
        val byId = local.associateBy(id).toMutableMap()
        val order = local.map(id).toMutableList()
        for (r in remote) {
            val l = byId[id(r)]
            when {
                l == null -> { byId[id(r)] = r; order += id(r); report.added++ }
                op(l) == op(r) -> report.skipped++
                base(r) == ver(l) -> { byId[id(r)] = r; report.updated++ }
                base(l) == ver(r) -> report.skipped++
                else -> {
                    val lj = json(l).toString(); val rj = json(r).toString()
                    if (conflicts.none { it.entityId == id(r) && it.remoteJson == rj }) {
                        conflicts += Conflict(newId(), type, id(r), label(l), lj, rj)
                    }
                    report.conflicts++
                }
            }
        }
        return order.map { byId.getValue(it) }
    }

    private fun merge(l: GardenData, r: GardenData, rep: MergeReport): GardenData {
        val cf = l.conflicts.toMutableList()
        val trees = mergeList(l.trees, r.trees, "tree", rep, cf, { it.id }, { it.version }, { it.baseVersion }, { it.opId }, { it.code }, VJson::treeJ)
        val events = mergeList(l.events, r.events.map(::photoPathsFix), "event", rep, cf, { it.id }, { it.version }, { it.baseVersion }, { it.opId },
            { it.activity }, VJson::eventJ)
        val issues = mergeList(l.issues, r.issues, "issue", rep, cf, { it.id }, { it.version }, { it.baseVersion }, { it.opId }, { it.code }, VJson::issueJ)
        val tasks = mergeList(l.tasks, r.tasks, "task", rep, cf, { it.id }, { it.version }, { it.baseVersion }, { it.opId }, { it.title }, VJson::taskJ)
        val sales = mergeList(l.sales, r.sales, "sale", rep, cf, { it.id }, { it.version }, { it.baseVersion }, { it.opId }, { "Bán ${it.kg} kg" }, VJson::saleJ)
        val feats = mergeList(l.features, r.features, "feature", rep, cf, { it.id }, { it.version }, { it.baseVersion }, { it.opId }, { it.name }, VJson::featureJ)
        val imagery = mergeList(l.imagery, r.imagery, "image", rep, cf, { it.id }, { it.version }, { it.baseVersion }, { it.opId }, { it.name }, VJson::imageJ)
        val measures = mergeList(l.measures, r.measures, "measure", rep, cf, { it.id }, { it.version }, { it.baseVersion }, { it.opId }, { it.kind.label }, VJson::measureJ)
        val indices = mergeList(l.indices, r.indices, "index", rep, cf, { it.id }, { it.version }, { it.baseVersion }, { it.opId }, { it.name }, VJson::indexJ)
        val droneFlights = mergeList(l.droneFlights, r.droneFlights, "drone", rep, cf, { it.id }, { it.version }, { it.baseVersion }, { it.opId }, { it.name }, VJson::droneFlightJ)
        val zones = l.zones + r.zones.filter { z -> l.zones.none { it.name == z.name } }

        // Xoá: dấu vết của hai bên được gộp; bản ghi có dấu vết bị bỏ đi (xoá thắng sửa). Không cascade lại ở đây — bản ghi bị bỏ liên kết
        // do việc xoá đã nằm sẵn trong bản chụp của bên xoá, đến như một lần cập nhật bình thường.
        val tombs = (l.tombstones + r.tombstones).distinctBy { it.type to it.id }
        val dead = tombs.map { it.type to it.id }.toSet()
        fun <T> alive(type: String, list: List<T>, id: (T) -> String): List<T> = list.filter { (type to id(it)) !in dead }
        fun <T> mine(type: String, list: List<T>, id: (T) -> String) = list.count { (type to id(it)) in dead }
        rep.deleted += mine("trees", l.trees) { it.id } + mine("events", l.events) { it.id } + mine("issues", l.issues) { it.id } + mine("tasks", l.tasks) { it.id } +
            mine("sales", l.sales) { it.id } + mine("features", l.features) { it.id } + mine("imagery", l.imagery) { it.id } + mine("measures", l.measures) { it.id } +
            mine("indices", l.indices) { it.id } + mine("droneFlights", l.droneFlights) { it.id } + mine("zones", l.zones) { it.name }
        val nTrees = alive("trees", trees) { it.id }; val nEvents = alive("events", events) { it.id }; val nIssues = alive("issues", issues) { it.id }
        val nTasks = alive("tasks", tasks) { it.id }; val nSales = alive("sales", sales) { it.id }; val nFeats = alive("features", feats) { it.id }
        val nImagery = alive("imagery", imagery) { it.id }; val nMeasures = alive("measures", measures) { it.id }; val nIndices = alive("indices", indices) { it.id }
        val nDrone = alive("droneFlights", droneFlights) { it.id }; val nZones = alive("zones", zones) { it.name }
        val deadIds = dead.map { it.second }.toSet()

        // Thông tin vườn: bản sửa sau cùng thắng. Tên người dùng là của riêng từng thiết bị nên không bị ghi đè.
        val garden = when {
            !l.configured -> r.garden
            r.configured && r.garden.updatedAt > l.garden.updatedAt -> r.garden.copy(operatorName = l.garden.operatorName)
            else -> l.garden
        }
        return l.copy(
            trees = nTrees, events = nEvents, issues = nIssues, tasks = nTasks, sales = nSales, features = nFeats, zones = nZones,
            conflicts = cf.filter { it.entityId !in deadIds },
            imagery = nImagery, measures = nMeasures, indices = nIndices, droneFlights = nDrone, tombstones = tombs,
            garden = garden, configured = l.configured || r.configured,
            isSample = l.isSample && r.isSample,
        )
    }

    /**
     * Giải quyết xung đột. "Lấy bản kia" nhận nguyên bản từ nguồn (cùng phiên bản/opId nên lần hợp nhất sau không tạo lại xung đột).
     * "Giữ bản của tôi" đặt bản của tôi nối tiếp sau bản kia (phiên bản mới hơn, dựa trên phiên bản của bản kia) để lần đồng bộ sau
     * ghi đè bản kia một cách có chủ đích và hai bên cùng đi tiếp.
     */
    fun resolveConflict(id: String, takeRemote: Boolean) {
        val c = data.conflicts.firstOrNull { it.id == id } ?: return
        mutate { d ->
            val o = JSONObject(c.remoteJson)
            fun <T> resolve(list: List<T>, getId: (T) -> String, make: (JSONObject) -> T, ver: (T) -> Int, rebase: (T, Int) -> T): List<T> =
                list.map {
                    if (getId(it) != c.entityId) it
                    else if (takeRemote) make(o) else rebase(it, make(o).let(ver))
                }
            val n = d.copy(conflicts = d.conflicts.filter { it.id != id })
            val now = Dates.now()
            when (c.entityType) {
                "tree" -> n.copy(trees = resolve(n.trees, { it.id }, VJson::treeOf, { it.version }) { l, rv -> l.copy(version = maxOf(l.version, rv) + 1, baseVersion = rv, opId = newId(), updatedAt = now, sync = SyncState.LOCAL) })
                "event" -> n.copy(events = resolve(n.events, { it.id }, { photoPathsFix(VJson.eventOf(it)) }, { it.version }) { l, rv -> l.copy(version = maxOf(l.version, rv) + 1, baseVersion = rv, opId = newId(), updatedAt = now, sync = SyncState.LOCAL) })
                "issue" -> n.copy(issues = resolve(n.issues, { it.id }, VJson::issueOf, { it.version }) { l, rv -> l.copy(version = maxOf(l.version, rv) + 1, baseVersion = rv, opId = newId(), updatedAt = now, sync = SyncState.LOCAL) })
                "task" -> n.copy(tasks = resolve(n.tasks, { it.id }, VJson::taskOf, { it.version }) { l, rv -> l.copy(version = maxOf(l.version, rv) + 1, baseVersion = rv, opId = newId(), updatedAt = now, sync = SyncState.LOCAL) })
                "sale" -> n.copy(sales = resolve(n.sales, { it.id }, VJson::saleOf, { it.version }) { l, rv -> l.copy(version = maxOf(l.version, rv) + 1, baseVersion = rv, opId = newId(), updatedAt = now, sync = SyncState.LOCAL) })
                "feature" -> n.copy(features = resolve(n.features, { it.id }, VJson::featureOf, { it.version }) { l, rv -> l.copy(version = maxOf(l.version, rv) + 1, baseVersion = rv, opId = newId(), updatedAt = now, sync = SyncState.LOCAL) })
                "image" -> n.copy(imagery = resolve(n.imagery, { it.id }, VJson::imageOf, { it.version }) { l, rv -> l.copy(version = maxOf(l.version, rv) + 1, baseVersion = rv, opId = newId(), updatedAt = now, sync = SyncState.LOCAL) })
                "measure" -> n.copy(measures = resolve(n.measures, { it.id }, VJson::measureOf, { it.version }) { l, rv -> l.copy(version = maxOf(l.version, rv) + 1, baseVersion = rv, opId = newId(), updatedAt = now, sync = SyncState.LOCAL) })
                "index" -> n.copy(indices = resolve(n.indices, { it.id }, VJson::indexOf, { it.version }) { l, rv -> l.copy(version = maxOf(l.version, rv) + 1, baseVersion = rv, opId = newId(), updatedAt = now, sync = SyncState.LOCAL) })
                "drone" -> n.copy(droneFlights = resolve(n.droneFlights, { it.id }, VJson::droneFlightOf, { it.version }) { l, rv -> l.copy(version = maxOf(l.version, rv) + 1, baseVersion = rv, opId = newId(), updatedAt = now, sync = SyncState.LOCAL) })
                else -> n
            }
        }
    }

    // ---------- Đợt C/D: ảnh, nước/đất, chỉ số thực vật ----------

    /** Tên tệp (không đường dẫn) của mọi tệp ảnh/lưới mà dữ liệu đang tham chiếu ngoài ảnh sự kiện. */
    fun referencedFiles(d: GardenData = data): Set<String> =
        (d.imagery.map { it.file } + d.indices.mapNotNull { it.gridFile } + d.events.flatMap { it.photos }.map { File(it.path).name }
            + d.droneFlights.map { it.file }).filter { it.isNotBlank() }.toSet()

    /** Sao chép ảnh vào bộ nhớ app và đọc kích thước. Trả (tên tệp, rộng, cao) hoặc null nếu không đọc được. */
    fun importImageFile(uri: Uri): Triple<String, Int, Int>? = try {
        val name = newId() + ".img"
        val f = File(photoDir, name)
        val ok = resolver?.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) }; true } ?: false
        if (!ok) null else {
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(f.absolutePath, o)
            if (o.outWidth <= 0 || o.outHeight <= 0) { f.delete(); null } else Triple(name, o.outWidth, o.outHeight)
        }
    } catch (_: Exception) { null }

    fun addImageSet(s: ImageSet): Outcome {
        Imagery.validate(s)?.let { return Outcome.err(it) }
        if (data.imagery.any { it.id == s.id }) return Outcome.OK
        mutate { it.copy(imagery = it.imagery + s.copy(audit = listOf(AuditEntry(Dates.now(), by, "Nhập bộ ảnh")))) }
        return Outcome.OK
    }

    fun editImageSet(id: String, baseVersion: Int, summary: String, f: (ImageSet) -> ImageSet): Outcome {
        val cur = data.imagery.firstOrNull { it.id == id } ?: return Outcome.err("Không tìm thấy bộ ảnh.")
        if (cur.version != baseVersion) return Outcome.conflict()
        val next = f(cur)
        Imagery.validate(next)?.let { return Outcome.err(it) }
        mutate { d ->
            d.copy(imagery = d.imagery.map {
                if (it.id != id) it else next.copy(
                    version = cur.version + 1, baseVersion = cur.version, updatedAt = Dates.now(), opId = newId(), sync = SyncState.LOCAL,
                    audit = cur.audit + AuditEntry(Dates.now(), by, summary),
                )
            })
        }
        return Outcome.OK
    }

    /** Nhập điểm ứng viên cho một bộ ảnh. Điểm gần điểm đã có (cùng bộ) bị bỏ qua nên nhập lại cùng tệp không nhân đôi. */
    fun importDetections(setId: String, points: List<GeoPoint>, model: String, modelVersion: String): Result<Int> = runCatching {
        val cur = data.imagery.firstOrNull { it.id == setId } ?: error("Không tìm thấy bộ ảnh.")
        val fresh = Imagery.newDetections(cur, points)
        if (fresh.isEmpty()) return@runCatching 0
        val r = editImageSet(setId, cur.version, "Nhập ${fresh.size} điểm ứng viên (${model.ifBlank { "không rõ mô hình" }} ${modelVersion})") {
            it.copy(detections = it.detections + fresh, detectionModel = model.trim(), detectionVersion = modelVersion.trim())
        }
        if (!r.ok) error(r.message ?: "Không lưu được.")
        fresh.size
    }

    /** Xác nhận/bỏ qua một điểm ứng viên. Chỉ người dùng gọi hàm này; không có đường tự động ghép. */
    fun resolveDetection(setId: String, detId: String, status: DetStatus, treeId: String?): Outcome {
        val cur = data.imagery.firstOrNull { it.id == setId } ?: return Outcome.err("Không tìm thấy bộ ảnh.")
        val det = cur.detections.firstOrNull { it.id == detId } ?: return Outcome.err("Không tìm thấy điểm ứng viên.")
        if ((status == DetStatus.CONFIRMED || status == DetStatus.NEW_TREE) && treeId == null) return Outcome.err("Chọn cây để ghép.")
        if (status == DetStatus.CONFIRMED) {
            val t = data.trees.firstOrNull { it.id == treeId } ?: return Outcome.err("Không tìm thấy cây.")
            if (cur.detections.any { it.id != detId && it.status == DetStatus.CONFIRMED && it.treeId == t.id })
                return Outcome.err("Cây ${t.code} đã được ghép với một điểm khác trong bộ ảnh này.")
        }
        return editImageSet(setId, cur.version, "Điểm ứng viên → ${status.label}") { s ->
            s.copy(detections = s.detections.map { if (it.id == det.id) it.copy(status = status, treeId = treeId, by = by, at = Dates.now()) else it })
        }
    }

    fun addMeasure(m: Measure): Outcome {
        Env.validate(m, data)?.let { return Outcome.err(it) }
        if (data.measures.any { it.id == m.id }) return Outcome.OK
        mutate { it.copy(measures = it.measures + m.copy(author = m.author.ifBlank { by }, audit = listOf(AuditEntry(Dates.now(), by, "Ghi số đo")))) }
        return Outcome.OK
    }

    fun editMeasure(id: String, baseVersion: Int, summary: String, f: (Measure) -> Measure): Outcome {
        val cur = data.measures.firstOrNull { it.id == id } ?: return Outcome.err("Không tìm thấy số đo.")
        if (cur.version != baseVersion) return Outcome.conflict()
        val next = f(cur)
        Env.validate(next, data)?.let { return Outcome.err(it) }
        mutate { d ->
            d.copy(measures = d.measures.map {
                if (it.id != id) it else next.copy(
                    version = cur.version + 1, baseVersion = cur.version, updatedAt = Dates.now(), opId = newId(), sync = SyncState.LOCAL,
                    audit = cur.audit + AuditEntry(Dates.now(), by, summary),
                )
            })
        }
        return Outcome.OK
    }

    /** Ghi chú vị trí đọng nước / khô hạn lên bản đồ. */
    fun addSpotNote(kind: FeatureKind, point: GeoPoint, note: String): Outcome {
        if (kind != FeatureKind.WATERLOG && kind != FeatureKind.DRY) return Outcome.err("Loại ghi chú không hợp lệ.")
        if (kotlin.math.abs(point.lat) > 90 || kotlin.math.abs(point.lng) > 180) return Outcome.err("Tọa độ không hợp lệ.")
        val name = kind.label + " " + Dates.fmt(Dates.now())
        mutate { it.copy(features = it.features + GeoFeature(newId(), kind, name, listOf(point), note = note.trim(), source = "Ghi chú thực địa")) }
        return Outcome.OK
    }

    fun addVegIndex(v: VegIndex): Outcome {
        Ndvi.validateRecord(v)?.let { return Outcome.err(it) }
        if (data.indices.any { it.id == v.id }) return Outcome.OK
        mutate { it.copy(indices = it.indices + v.copy(audit = listOf(AuditEntry(Dates.now(), by, "Tạo bản đồ chỉ số")))) }
        return Outcome.OK
    }

    // ---------- nhật ký bay DJI (xem DjiFlightLog.kt) ----------

    /** Sao chép file log vào bộ nhớ app, giải mã và lưu thành [DroneFlight]. Luôn lưu lại kể cả khi giải mã lỗi/rỗng
     *  (parseOk=false) để người dùng vẫn thấy đã nhập file nào và vì sao không đọc được. */
    fun importDroneLogFile(uri: Uri, name: String): Outcome {
        val bytes = try { resolver?.openInputStream(uri)?.use { it.readBytes() } } catch (_: Exception) { null }
            ?: return Outcome.err("Không đọc được tệp.")
        if (bytes.isEmpty()) return Outcome.err("Tệp rỗng.")
        val fileName = newId() + ".djilog"
        try { File(photoDir, fileName).writeBytes(bytes) } catch (_: Exception) { return Outcome.err("Không lưu được tệp vào bộ nhớ app.") }
        val decoded = DjiFlightLog.decode(bytes)
        val flight = DroneFlight(
            id = newId(), name = name.ifBlank { "Nhật ký bay ${Dates.fmt(Dates.now())}" }, importedAt = Dates.now(),
            file = fileName, fileSizeBytes = bytes.size.toLong(), frameCount = decoded.frames.size,
            parseOk = decoded.wellFormed, parseError = decoded.error,
            audit = listOf(AuditEntry(Dates.now(), by, "Nhập nhật ký bay DJI")),
        )
        mutate { it.copy(droneFlights = it.droneFlights + flight) }
        return Outcome.OK
    }

    fun deleteDroneFlight(id: String): Outcome {
        if (data.droneFlights.none { it.id == id }) return Outcome.err("Không tìm thấy nhật ký bay.")
        return deleteEntity("droneFlights", id)
    }

    // ---------- đồng bộ (xem Sync.kt) ----------
    /** Hợp nhất bản chụp từ máy chủ vào dữ liệu hiện có (cùng quy tắc với khôi phục sao lưu). */
    fun mergeRemote(remote: GardenData): MergeReport {
        val rep = MergeReport()
        mutate { merge(it, remote, rep) }
        return rep
    }

    /** Đánh dấu "Đã đồng bộ" cho những bản ghi vẫn đúng như phiên bản đã gửi lên (bản ghi vừa sửa trong lúc gửi giữ nguyên trạng thái). */
    fun markSynced(uploaded: GardenData) {
        fun <T> mark(cur: List<T>, up: List<T>, id: (T) -> String, op: (T) -> String, with: (T) -> T): List<T> {
            val ops = up.associate { id(it) to op(it) }
            return cur.map { if (ops[id(it)] == op(it)) with(it) else it }
        }
        mutate { d ->
            d.copy(
                trees = mark(d.trees, uploaded.trees, { it.id }, { it.opId }) { it.copy(sync = SyncState.SYNCED) },
                events = mark(d.events, uploaded.events, { it.id }, { it.opId }) { it.copy(sync = SyncState.SYNCED) },
                issues = mark(d.issues, uploaded.issues, { it.id }, { it.opId }) { it.copy(sync = SyncState.SYNCED) },
                tasks = mark(d.tasks, uploaded.tasks, { it.id }, { it.opId }) { it.copy(sync = SyncState.SYNCED) },
                sales = mark(d.sales, uploaded.sales, { it.id }, { it.opId }) { it.copy(sync = SyncState.SYNCED) },
                features = mark(d.features, uploaded.features, { it.id }, { it.opId }) { it.copy(sync = SyncState.SYNCED) },
                imagery = mark(d.imagery, uploaded.imagery, { it.id }, { it.opId }) { it.copy(sync = SyncState.SYNCED) },
                measures = mark(d.measures, uploaded.measures, { it.id }, { it.opId }) { it.copy(sync = SyncState.SYNCED) },
                indices = mark(d.indices, uploaded.indices, { it.id }, { it.opId }) { it.copy(sync = SyncState.SYNCED) },
                droneFlights = mark(d.droneFlights, uploaded.droneFlights, { it.id }, { it.opId }) { it.copy(sync = SyncState.SYNCED) },
            )
        }
    }

    /** Dùng cho kiểm thử/ảnh chụp: nạp lại dữ liệu mẫu. */
    fun resetToSample() { data = Seed.build(); persist() }
}
