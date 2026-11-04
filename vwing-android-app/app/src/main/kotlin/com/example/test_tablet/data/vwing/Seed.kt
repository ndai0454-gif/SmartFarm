package com.example.test_tablet.data.vwing

/** Dữ liệu mẫu "Vườn mẫu Tây Nguyên" (từ prototype VWING). Ngày được tính theo hôm nay để minh họa quá hạn/chưa quan sát. */
object Seed {
    private class T(val code: String, val zone: String, val fruits: Int, val cond: Condition, val note: String, val lastDays: Int?)

    private val trees = listOf(
        T("A-01", "Khu A", 54, Condition.STABLE, "Phát triển tốt, tán lá dày", 3),
        T("A-02", "Khu A", 58, Condition.STABLE, "Sinh trưởng bình thường", 5),
        T("A-03", "Khu A", 48, Condition.WATCH, "Độ ẩm đất hơi thấp, cần tưới", 4),
        T("A-04", "Khu A", 46, Condition.NEEDS_CHECK, "Lá có dấu hiệu vàng; cần kiểm tra trực tiếp.", 2),
        T("A-05", "Khu A", 50, Condition.STABLE, "Cây ổn định", 6),
        T("A-06", "Khu A", 52, Condition.STABLE, "Tán lá đều", 19),
        T("B-01", "Khu B", 60, Condition.STABLE, "Năng suất dự kiến cao", 7),
        T("B-02", "Khu B", 55, Condition.STABLE, "Cây phát triển tốt", 8),
        T("B-03", "Khu B", 49, Condition.WATCH, "Theo dõi xói mòn chân gốc", 5),
        T("B-04", "Khu B", 53, Condition.STABLE, "Bộ rễ khỏe", 9),
        T("B-05", "Khu B", 46, Condition.NEEDS_CHECK, "Cần đối chiếu sau bón phân hữu cơ.", 12),
        T("B-06", "Khu B", 57, Condition.STABLE, "Cây ổn định", 10),
        T("C-01", "Khu C", 56, Condition.STABLE, "Trái đậu đều", 4),
        T("C-02", "Khu C", 52, Condition.STABLE, "Cây xanh tốt", 6),
        T("C-03", "Khu C", 50, Condition.WATCH, "Quan sát ngọn mới", 3),
        T("C-04", "Khu C", 54, Condition.STABLE, "Bình thường", 21),
        T("C-05", "Khu C", 53, Condition.STABLE, "Bình thường", 8),
        T("C-06", "Khu C", 55, Condition.UNASSESSED, "", null),
        T("D-01", "Khu D", 52, Condition.STABLE, "Bình thường", 7),
        T("D-02", "Khu D", 44, Condition.NEEDS_CHECK, "Theo dõi sau lần phun thuốc.", 16),
        T("D-03", "Khu D", 48, Condition.WATCH, "Cần tỉa bớt cành phụ", 4),
        T("D-04", "Khu D", 51, Condition.STABLE, "Ổn định", 9),
        T("D-05", "Khu D", 53, Condition.UNASSESSED, "", null),
        T("D-06", "Khu D", 58, Condition.STABLE, "Ổn định", 11),
    )

    private val zoneDesc = mapOf("Khu A" to "Gần lối vào", "Khu B" to "Đất dốc", "Khu C" to "Giữa vườn", "Khu D" to "Cuối vườn")

    // Góc tây-nam của ranh mẫu và kích thước (độ) ~ 134 m x 134 m ≈ 1,8 ha.
    private const val LAT0 = 11.5480
    private const val LNG0 = 107.8070
    private const val DLAT = 0.00121
    private const val DLNG = 0.00123

    private fun at(u: Double, v: Double) = GeoPoint(LAT0 + v * DLAT, LNG0 + u * DLNG)

    private fun position(zone: String, idxInZone: Int): GeoPoint {
        // A, B ở nửa trên; C, D ở nửa dưới; đường nội bộ (u = 0,5) và giếng (0,5; 0,5) nằm giữa các hàng cây.
        val left = zone == "Khu A" || zone == "Khu C"
        val upper = zone == "Khu A" || zone == "Khu B"
        val col = idxInZone % 3
        val row = idxInZone / 3
        val u = (if (left) 0.06 else 0.60) + col * 0.17
        val v = (if (upper) 0.88 else 0.36) - row * 0.24
        return at(u, v)
    }

    fun build(): GardenData {
        val now = Dates.now()
        val author = "Người làm vườn"
        val ids = trees.associate { it.code to newId() }
        val zoneCounter = mutableMapOf<String, Int>()

        val treeList = trees.map { t ->
            val idx = zoneCounter.merge(t.zone, 1, Int::plus)!! - 1
            Tree(
                id = ids.getValue(t.code), code = t.code, zone = t.zone, variety = "Sầu riêng", fruits = t.fruits,
                fruitsAt = Dates.daysAgo(12), condition = t.cond, pos = position(t.zone, idx),
                posSource = PosSource.ESTIMATED, posAt = Dates.daysAgo(30),
                audit = listOf(AuditEntry(Dates.daysAgo(30), author, "Tạo hồ sơ cây (dữ liệu mẫu)")),
            )
        }

        val events = mutableListOf<Event>()
        // Mỗi cây đã quan sát có một lần quan sát với ghi chú gốc của prototype.
        for (t in trees) {
            val d = t.lastDays ?: continue
            events += Event(
                id = newId(), treeIds = listOf(ids.getValue(t.code)), type = EventType.OBSERVATION, activity = "Kiểm tra",
                occurredAt = Dates.daysAgo(d), recordedAt = Dates.daysAgo(d), author = author, note = t.note,
                conditionSet = t.cond,
            )
        }
        val a04Obs = events.first { it.treeIds == listOf(ids.getValue("A-04")) }
        events += Event(
            id = newId(), treeIds = listOf(ids.getValue("B-05")), type = EventType.CARE, activity = "Bón phân",
            occurredAt = Dates.daysAgo(12), author = "Nguyễn Văn A", note = "Bón quanh tán cây; theo dõi phản ứng.",
            product = "Phân hữu cơ vi sinh", dosage = "2 kg",
        )
        events += Event(
            id = newId(), treeIds = trees.filter { it.zone == "Khu C" }.map { ids.getValue(it.code) }, scopeZone = "Khu C",
            type = EventType.CARE, activity = "Tưới nước", occurredAt = Dates.daysAgo(14), author = "Hệ thống tự động",
            note = "Tưới toàn khu vực C trong 45 phút.", product = "Nước giếng khoan", dosage = "45 phút",
        )
        val d02Care = Event(
            id = newId(), treeIds = listOf(ids.getValue("D-02")), type = EventType.CARE, activity = "Phun thuốc",
            occurredAt = Dates.daysAgo(16), author = "Trần Văn B", note = "Cần xác nhận lại kết quả sau 7 ngày.",
            product = "Thuốc sinh học phòng rầy", dosage = "1 lần",
        )
        events += d02Care

        val issues = listOf(
            Issue(
                id = newId(), number = 1, treeIds = listOf(ids.getValue("A-04")), symptom = "Lá có dấu hiệu vàng; cần kiểm tra trực tiếp.",
                priority = Priority.HIGH, status = IssueStatus.PENDING_VERIFY, assignee = author, dueAt = Dates.daysAgo(-1),
                originEventId = a04Obs.id, createdAt = Dates.daysAgo(2),
                audit = listOf(AuditEntry(Dates.daysAgo(2), author, "Ghi nhận vấn đề")),
            ),
            Issue(
                id = newId(), number = 2, treeIds = listOf(ids.getValue("B-05")), symptom = "Cần đối chiếu sau bón phân hữu cơ.",
                priority = Priority.MEDIUM, status = IssueStatus.IN_PROGRESS, assignee = "Nguyễn Văn A", dueAt = Dates.daysAgo(1),
                createdAt = Dates.daysAgo(12), audit = listOf(AuditEntry(Dates.daysAgo(12), author, "Ghi nhận vấn đề")),
            ),
            Issue(
                id = newId(), number = 3, treeIds = listOf(ids.getValue("D-02")), symptom = "Theo dõi sau lần phun thuốc.",
                priority = Priority.MEDIUM, status = IssueStatus.PENDING_RECHECK, assignee = "Trần Văn B", dueAt = Dates.daysAgo(9),
                originEventId = d02Care.id, createdAt = Dates.daysAgo(16),
                audit = listOf(AuditEntry(Dates.daysAgo(16), author, "Ghi nhận vấn đề"), AuditEntry(Dates.daysAgo(9), author, "Hoàn tất công việc; chờ tái kiểm tra")),
            ),
        )
        val tasks = listOf(
            Task(newId(), "Kiểm tra lại B-05 sau bón phân", listOf(ids.getValue("B-05")), null, "Nguyễn Văn A", Dates.daysAgo(1), issueId = issues[1].id),
            Task(newId(), "Phun lại D-02 nếu còn rầy", listOf(ids.getValue("D-02")), null, "Trần Văn B", Dates.daysAgo(10), done = true,
                doneAt = Dates.daysAgo(9), doneBy = "Trần Văn B", issueId = issues[2].id),
        )

        val boundary = GeoFeature(
            newId(), FeatureKind.BOUNDARY, "Ranh vườn (mẫu)",
            listOf(at(0.0, 0.0), at(1.0, 0.0), at(1.0, 1.0), at(0.0, 1.0)), source = "Dữ liệu mẫu",
        )
        val road = GeoFeature(newId(), FeatureKind.ROAD, "Đường nội bộ (mẫu)", listOf(at(0.5, 0.0), at(0.5, 1.0)), source = "Dữ liệu mẫu")
        val water = GeoFeature(newId(), FeatureKind.WATER, "Giếng khoan (mẫu)", listOf(at(0.5, 0.5)), source = "Dữ liệu mẫu")
        val obstacle = GeoFeature(newId(), FeatureKind.OBSTACLE, "Cột điện (mẫu)", listOf(at(0.97, 0.5)), note = "Chú ý khi bay drone", source = "Dữ liệu mẫu")

        return GardenData(
            garden = Garden(
                name = "Vườn mẫu Tây Nguyên", location = "Bảo Lộc, Lâm Đồng", crop = "Sầu riêng", areaHa = 1.8,
                harvestCycle = "Tháng 7–8 hằng năm", checkIntervalDays = 14, operatorName = author,
                avgWeightKg = 2.2, commercialRatio = 1.0, spreadPct = 10.0,
                pricePerKg = 62000.0, priceSource = "Người dùng nhập (dữ liệu mẫu)", priceDate = Dates.daysAgo(10),
            ),
            zones = zoneDesc.map { Zone(it.key, it.value) },
            trees = treeList, events = events, issues = issues, tasks = tasks,
            features = listOf(boundary, road, water, obstacle),
            isSample = true, configured = false,
        ).also { check(now > 0) }
    }
}
