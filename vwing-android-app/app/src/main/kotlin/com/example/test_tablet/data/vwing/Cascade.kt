package com.example.test_tablet.data.vwing

/**
 * Xoá lan truyền. Quy tắc giống hệt máy chủ (vwing-sync-server/rbac.js, hàm cascade) để hai bên cho cùng kết quả:
 *  - cây: xoá cây; ghi chép/vấn đề/công việc/số đo chỉ gắn cây này (không có khu, không có điểm) cũng bị xoá, còn lại chỉ bỏ liên kết;
 *    cây thay thế mất liên kết; điểm ứng viên ảnh trỏ cây này quay về "chờ xem".
 *  - ghi chép: xoá; vấn đề trỏ ghi chép gốc mất liên kết.
 *  - vấn đề: xoá cùng công việc của nó; ghi chép gắn vấn đề mất liên kết.
 *  - khu: xoá khu, mọi cây trong khu (kéo theo phần trên) và các mục chỉ gắn khu đó.
 *  - các loại còn lại (bán hàng, lớp bản đồ, bộ ảnh, chỉ số, số đo, nhật ký bay): xoá chính nó.
 * Bản ghi chỉ bị bỏ liên kết được tăng phiên bản để thiết bị khác nhận như một lần cập nhật bình thường (không thành xung đột).
 * Mỗi bản ghi bị xoá có một [Tombstone]; hàm này thuần (không đụng đĩa) để kiểm thử.
 */
object Cascade {
    class Result(val data: GardenData, val removed: List<Tombstone>) {
        fun count(type: String) = removed.count { it.type == type }
    }

    val TYPES = setOf("trees", "events", "issues", "tasks", "sales", "features", "imagery", "measures", "indices", "droneFlights", "zones")

    fun delete(d: GardenData, type: String, id: String, by: String, now: Long = Dates.now()): Result {
        val s = State(d, by, now)
        s.remove(type, id)
        return Result(s.d, s.removed)
    }

    /** Tên tệp ảnh/lưới/log chỉ còn được tham chiếu bởi các bản ghi bị xoá — an toàn để xoá khỏi máy. */
    fun orphanFiles(before: GardenData, after: GardenData, referenced: (GardenData) -> Set<String>): Set<String> =
        referenced(before) - referenced(after)

    private class State(var d: GardenData, val by: String, val now: Long) {
        val removed = mutableListOf<Tombstone>()
        private fun tomb(type: String, id: String) { removed += Tombstone(type, id, now, by) }
        private fun audit(note: String) = AuditEntry(now, by, note)

        fun remove(type: String, id: String) {
            when (type) {
                "trees" -> removeTree(id)
                "events" -> {
                    if (d.events.none { it.id == id }) return
                    d = d.copy(events = d.events.filter { it.id != id }); tomb("events", id)
                    d = d.copy(issues = d.issues.map {
                        if (it.originEventId == id) it.copy(originEventId = null, version = it.version + 1, baseVersion = it.version, opId = newId(), updatedAt = now,
                            sync = SyncState.LOCAL, audit = it.audit + audit("Ghi chép gốc đã bị xoá")) else it
                    })
                }
                "issues" -> {
                    if (d.issues.none { it.id == id }) return
                    d = d.copy(issues = d.issues.filter { it.id != id }); tomb("issues", id)
                    d.tasks.filter { it.issueId == id }.forEach { remove("tasks", it.id) }
                    d = d.copy(events = d.events.map {
                        if (it.issueId == id) it.copy(issueId = null, version = it.version + 1, baseVersion = it.version, opId = newId(), updatedAt = now,
                            sync = SyncState.LOCAL, audit = it.audit + audit("Vấn đề liên quan đã bị xoá")) else it
                    })
                }
                "zones" -> {
                    if (d.zones.none { it.name == id }) return
                    d = d.copy(zones = d.zones.filter { it.name != id }); tomb("zones", id)
                    d.trees.filter { it.zone == id }.forEach { removeTree(it.id) }
                    d.events.filter { it.scopeZone == id && it.treeIds.isEmpty() }.forEach { remove("events", it.id) }
                    d.issues.filter { it.scopeZone == id && it.treeIds.isEmpty() }.forEach { remove("issues", it.id) }
                    d.tasks.filter { it.scopeZone == id && it.treeIds.isEmpty() }.forEach { remove("tasks", it.id) }
                    d.measures.filter { it.scopeZone == id && it.treeIds.isEmpty() }.forEach { remove("measures", it.id) }
                }
                "tasks" -> if (d.tasks.any { it.id == id }) { d = d.copy(tasks = d.tasks.filter { it.id != id }); tomb(type, id) }
                "sales" -> if (d.sales.any { it.id == id }) { d = d.copy(sales = d.sales.filter { it.id != id }); tomb(type, id) }
                "features" -> if (d.features.any { it.id == id }) { d = d.copy(features = d.features.filter { it.id != id }); tomb(type, id) }
                "imagery" -> if (d.imagery.any { it.id == id }) { d = d.copy(imagery = d.imagery.filter { it.id != id }); tomb(type, id) }
                "measures" -> if (d.measures.any { it.id == id }) { d = d.copy(measures = d.measures.filter { it.id != id }); tomb(type, id) }
                "indices" -> if (d.indices.any { it.id == id }) { d = d.copy(indices = d.indices.filter { it.id != id }); tomb(type, id) }
                "droneFlights" -> if (d.droneFlights.any { it.id == id }) { d = d.copy(droneFlights = d.droneFlights.filter { it.id != id }); tomb(type, id) }
            }
        }

        private fun removeTree(id: String) {
            val t = d.trees.firstOrNull { it.id == id } ?: return
            d = d.copy(trees = d.trees.filter { it.id != id }); tomb("trees", id)
            val note = "Cây ${t.code} đã bị xoá"
            for (e in d.events.filter { id in it.treeIds }) {
                val left = e.treeIds - id
                if (left.isEmpty() && e.scopeZone == null) remove("events", e.id)
                else d = d.copy(events = d.events.map {
                    if (it.id == e.id) it.copy(treeIds = left, version = it.version + 1, baseVersion = it.version, opId = newId(), updatedAt = now,
                        sync = SyncState.LOCAL, audit = it.audit + audit(note)) else it
                })
            }
            for (i in d.issues.filter { id in it.treeIds }) {
                val left = i.treeIds - id
                if (left.isEmpty() && i.scopeZone == null) remove("issues", i.id)
                else d = d.copy(issues = d.issues.map {
                    if (it.id == i.id) it.copy(treeIds = left, version = it.version + 1, baseVersion = it.version, opId = newId(), updatedAt = now,
                        sync = SyncState.LOCAL, audit = it.audit + audit(note)) else it
                })
            }
            for (k in d.tasks.filter { id in it.treeIds }) {
                val left = k.treeIds - id
                if (left.isEmpty() && k.scopeZone == null) remove("tasks", k.id)
                else d = d.copy(tasks = d.tasks.map {
                    if (it.id == k.id) it.copy(treeIds = left, version = it.version + 1, baseVersion = it.version, opId = newId(), updatedAt = now, sync = SyncState.LOCAL) else it
                })
            }
            for (m in d.measures.filter { id in it.treeIds }) {
                val left = m.treeIds - id
                if (left.isEmpty() && m.scopeZone == null && m.point == null) remove("measures", m.id)
                else d = d.copy(measures = d.measures.map {
                    if (it.id == m.id) it.copy(treeIds = left, version = it.version + 1, baseVersion = it.version, opId = newId(), updatedAt = now, sync = SyncState.LOCAL) else it
                })
            }
            d = d.copy(
                trees = d.trees.map {
                    if (it.replacesId == id || it.replacedById == id) it.copy(
                        replacesId = if (it.replacesId == id) null else it.replacesId,
                        replacedById = if (it.replacedById == id) null else it.replacedById,
                        version = it.version + 1, baseVersion = it.version, opId = newId(), updatedAt = now, sync = SyncState.LOCAL,
                        audit = it.audit + audit("Cây liên kết ${t.code} đã bị xoá")) else it
                },
                imagery = d.imagery.map { s ->
                    if (s.detections.none { it.treeId == id }) s else s.copy(
                        detections = s.detections.map { det -> if (det.treeId == id) det.copy(treeId = null, status = DetStatus.PENDING) else det },
                        version = s.version + 1, baseVersion = s.version, opId = newId(), updatedAt = now, sync = SyncState.LOCAL,
                    )
                },
            )
        }
    }
}
