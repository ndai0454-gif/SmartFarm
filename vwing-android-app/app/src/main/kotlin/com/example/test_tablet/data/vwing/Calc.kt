package com.example.test_tablet.data.vwing

import kotlin.math.min

// Mọi con số hiển thị đều được tính từ dữ liệu ở đây, không gắn cố định.

val GardenData.activeTrees: List<Tree> get() = trees.filter { it.active }

fun GardenData.treeById(id: String): Tree? = trees.firstOrNull { it.id == id }

/** Chuẩn hóa "a4", "A04", "a-4" -> "A-04" để tra mã; trả null nếu không giống mã cây. */
fun normalizeCode(raw: String): String? {
    val m = Regex("""^\s*([A-Za-zĐđ]{1,3})\s*-?\s*0*(\d{1,3})\s*$""").find(raw) ?: return null
    return m.groupValues[1].uppercase() + "-" + m.groupValues[2].padStart(2, '0')
}

fun GardenData.treeByCode(raw: String): Tree? {
    val n = normalizeCode(raw)
    val list = activeTrees
    return list.firstOrNull { it.code.equals(raw.trim(), ignoreCase = true) }
        ?: if (n != null) list.firstOrNull { normalizeCode(it.code) == n } else null
}

fun GardenData.zoneNames(): List<String> =
    (zones.map { it.name } + trees.map { it.zone }).filter { it.isNotBlank() }.distinct().sorted()

fun GardenData.zoneDesc(zone: String): String = zones.firstOrNull { it.name == zone }?.desc ?: ""

// ---------- Vấn đề / công việc ----------

fun GardenData.openIssuesFor(treeId: String): List<Issue> = issues.filter { it.status.isOpen && treeId in it.treeIds }
fun GardenData.issuesFor(treeId: String): List<Issue> = issues.filter { treeId in it.treeIds }.sortedByDescending { it.createdAt }
fun GardenData.eventsFor(treeId: String): List<Event> =
    events.filter { treeId in it.treeIds }.sortedByDescending { it.occurredAt }

fun Issue.isOverdue(today: Long = Dates.today()) = status.isOpen && dueAt != null && dueAt < today
fun Task.isOverdue(today: Long = Dates.today()) = !done && dueAt != null && dueAt < today

/**
 * Tình trạng tổng hợp của cây: lấy mức nặng hơn giữa đánh giá thủ công và các vấn đề đang mở.
 * Vấn đề ưu tiên Cao đang mở -> Cần kiểm tra; vấn đề đang mở khác -> tối thiểu Theo dõi.
 * Hoàn tất công việc (bón/phun) không làm giảm mức này; chỉ xác nhận kết quả mới đóng vấn đề.
 */
fun GardenData.effectiveCondition(t: Tree): Condition {
    val open = openIssuesFor(t.id)
    return when {
        open.any { it.priority == Priority.HIGH } -> Condition.NEEDS_CHECK
        open.isNotEmpty() && t.condition.ordinal < Condition.WATCH.ordinal -> Condition.WATCH
        else -> t.condition
    }
}

// ---------- Kiểm tra định kỳ & chất lượng dữ liệu ----------

sealed interface CheckStatus {
    data object Never : CheckStatus
    data class Overdue(val days: Int) : CheckStatus
    data class Ok(val days: Int) : CheckStatus
}

fun GardenData.lastObservedAt(treeId: String): Long? =
    events.filter { treeId in it.treeIds && (it.type == EventType.OBSERVATION || it.type == EventType.RECHECK) }
        .maxOfOrNull { it.occurredAt }

fun GardenData.checkStatus(t: Tree, today: Long = Dates.today()): CheckStatus {
    val last = lastObservedAt(t.id) ?: return CheckStatus.Never
    val age = Dates.daysBetween(last, today)
    return if (age > garden.checkIntervalDays) CheckStatus.Overdue(age) else CheckStatus.Ok(age)
}

fun GardenData.dataGaps(t: Tree): List<String> = buildList {
    if (t.pos == null) add("Chưa có vị trí")
    else if (t.posSource == PosSource.ESTIMATED) add("Vị trí chưa xác minh")
    if (t.fruits == null) add("Chưa đếm trái")
    when (val c = checkStatus(t)) {
        CheckStatus.Never -> add("Chưa từng quan sát")
        is CheckStatus.Overdue -> add("Quá hạn kiểm tra ${c.days} ngày")
        is CheckStatus.Ok -> {}
    }
}

class TodayWork(
    val overdueTasks: List<Task>,
    val dueTodayTasks: List<Task>,
    val overdueIssues: List<Issue>,
    val pendingVerify: List<Issue>,
    val pendingRecheck: List<Issue>,
    val overdueTrees: List<Tree>,
    val neverObserved: List<Tree>,
    val unsynced: Int,
) {
    val total get() = overdueTasks.size + dueTodayTasks.size + overdueIssues.size + pendingVerify.size +
        pendingRecheck.size + overdueTrees.size + neverObserved.size
}

fun GardenData.todayWork(): TodayWork {
    val today = Dates.today()
    val tasks = tasks.filter { !it.done }
    val trees = activeTrees
    return TodayWork(
        overdueTasks = tasks.filter { it.isOverdue(today) },
        dueTodayTasks = tasks.filter { it.dueAt != null && Dates.startOf(it.dueAt) == today },
        overdueIssues = issues.filter { it.isOverdue(today) },
        pendingVerify = issues.filter { it.status == IssueStatus.PENDING_VERIFY || it.status == IssueStatus.NEW },
        pendingRecheck = issues.filter { it.status == IssueStatus.PENDING_RECHECK },
        overdueTrees = trees.filter { checkStatus(it, today) is CheckStatus.Overdue },
        neverObserved = trees.filter { checkStatus(it, today) is CheckStatus.Never },
        unsynced = unsyncedCount(),
    )
}

fun GardenData.unsyncedCount(): Int =
    trees.count { it.sync != SyncState.SYNCED } + events.count { it.sync != SyncState.SYNCED } +
        issues.count { it.sync != SyncState.SYNCED } + tasks.count { it.sync != SyncState.SYNCED } +
        sales.count { it.sync != SyncState.SYNCED } + features.count { it.sync != SyncState.SYNCED } +
        imagery.count { it.sync != SyncState.SYNCED } + measures.count { it.sync != SyncState.SYNCED } + indices.count { it.sync != SyncState.SYNCED }

// ---------- Tổng hợp ----------

class Summary(
    val totalTrees: Int,
    val countedTrees: Int,
    val fruits: Int,
    val byCondition: Map<Condition, Int>,
)

fun GardenData.summary(): Summary {
    val trees = activeTrees
    return Summary(
        totalTrees = trees.size,
        countedTrees = trees.count { it.fruits != null },
        fruits = trees.sumOf { it.fruits ?: 0 },
        byCondition = Condition.values().associateWith { c -> trees.count { effectiveCondition(it) == c } },
    )
}

// ---------- Sản lượng & doanh thu ----------

class Scenario(val label: String, val kg: Double?, val revenue: Double?)

class YieldCalc(
    val countedTrees: Int,
    val totalTrees: Int,
    val fruits: Int,
    val weightKg: Double?,
    val ratio: Double,
    val estKg: Double?,
    val price: Double?,
    val revenue: Double?,
    val scenarios: List<Scenario>,
    val actualKg: Double,
    val soldKg: Double,
    val soldAmount: Double,
    /** Chênh lệch thực thu - ước tính (kg); null nếu thiếu một trong hai. */
    val varianceKg: Double?,
)

fun GardenData.yieldCalc(): YieldCalc {
    val s = summary()
    val g = garden
    val kg = g.avgWeightKg?.let { s.fruits * it * g.ratioClamped() }
    val rev = if (kg != null && g.pricePerKg != null) kg * g.pricePerKg else null
    val sp = g.spreadPct / 100.0
    fun scen(label: String, f: Double): Scenario {
        val w = g.avgWeightKg?.times(f)
        val p = g.pricePerKg?.times(f)
        val k = w?.let { s.fruits * it * g.ratioClamped() }
        return Scenario(label, k, if (k != null && p != null) k * p else null)
    }
    val actual = events.filter { it.type == EventType.HARVEST }.sumOf { it.amountKg ?: 0.0 }
    return YieldCalc(
        countedTrees = s.countedTrees, totalTrees = s.totalTrees, fruits = s.fruits,
        weightKg = g.avgWeightKg, ratio = g.ratioClamped(), estKg = kg, price = g.pricePerKg, revenue = rev,
        scenarios = listOf(scen("Thấp", 1 - sp), scen("Cơ sở", 1.0), scen("Cao", 1 + sp)),
        actualKg = actual, soldKg = sales.sumOf { it.kg }, soldAmount = sales.sumOf { it.kg * it.pricePerKg },
        varianceKg = if (kg != null && actual > 0) actual - kg else null,
    )
}

private fun Garden.ratioClamped() = min(1.0, commercialRatio.coerceAtLeast(0.0))
