package com.example.test_tablet

import com.example.test_tablet.data.vwing.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VWingLogicTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store(dir: File = tmp.newFolder(), seed: Boolean = false) = VWingStore(dir, null, seed)

    private fun VWingStore.tree(code: String, zone: String = "Khu A", fruits: Int? = null): Tree {
        val r = addTree(code, zone, fruits = fruits); check(r.ok) { r.message ?: "" }
        return data.treeByCode(code)!!
    }

    private fun obs(trees: List<Tree>, daysAgo: Int = 0, cond: Condition? = null, note: String = "quan sát") = Event(
        id = newId(), treeIds = trees.map { it.id }, type = EventType.OBSERVATION, activity = "Kiểm tra",
        occurredAt = Dates.daysAgo(daysAgo), author = "t", note = note, conditionSet = cond,
    )

    // ---------- P0-01: lưu bền vững, idempotent, xung đột ----------

    @Test fun `du lieu con nguyen sau khi mo lai`() {
        val dir = tmp.newFolder()
        val a = store(dir)
        val t = a.tree("A-04")
        a.addEvent(obs(listOf(t), note = "lá vàng"))
        a.flush()
        val b = store(dir)
        assertEquals(1, b.data.trees.size)
        assertEquals("lá vàng", b.data.events.single().note)
        assertEquals(t.id, b.data.trees.single().id)
    }

    @Test fun `gui lai cung mot thao tac khong tao hai ban ghi`() {
        val s = store(); val t = s.tree("A-01")
        val e = obs(listOf(t))
        assertTrue(s.addEvent(e).ok); assertTrue(s.addEvent(e).ok)
        assertEquals(1, s.data.events.size)
    }

    @Test fun `sua voi phien ban cu bi bao xung dot va khong ghi de`() {
        val s = store(); val t = s.tree("A-01")
        val ok = s.editTree(t.id, t.version, "x") { it.copy(variety = "Ri6") }
        assertTrue(ok.ok)
        val stale = s.editTree(t.id, t.version, "y") { it.copy(variety = "Monthong") }  // t.version đã cũ
        assertTrue(stale.conflict)
        assertEquals("Ri6", s.data.treeById(t.id)!!.variety)
    }

    @Test fun `ma cay trung bi tu choi`() {
        val s = store(); s.tree("A-04")
        assertFalse(s.addTree("a04", "Khu A").ok)       // chuẩn hóa A04 == A-04
        assertFalse(s.addTree("A-04", "Khu B").ok)
    }

    @Test fun `doi ma khong lam mat lich su`() {
        val s = store(); val t = s.tree("A-04")
        s.addEvent(obs(listOf(t)))
        val cur = s.data.treeById(t.id)!!
        assertTrue(s.editTree(t.id, cur.version, "đổi mã") { it.copy(code = "Z-99") }.ok)
        val after = s.data.treeById(t.id)!!
        assertEquals("Z-99", after.code); assertEquals(t.id, after.id)
        assertEquals(1, s.data.eventsFor(t.id).size)
        assertTrue(after.audit.any { it.summary.contains("đổi mã") })
    }

    @Test fun `hop nhat cung ban sao luu hai lan khong nhan doi`() {
        val a = store(); val t = a.tree("A-04"); a.addEvent(obs(listOf(t)))
        val json = a.exportJsonText()
        val b = store()
        assertTrue(b.importBackup(json.byteInputStream()).isSuccess)
        val r2 = b.importBackup(json.byteInputStream()).getOrThrow()
        assertEquals(1, b.data.trees.size); assertEquals(1, b.data.events.size)
        assertEquals(0, r2.added); assertEquals(0, r2.conflicts)
    }

    @Test fun `hop nhat hai ben cung sua khac nhau tao xung dot khong ghi de am tham`() {
        val a = store(); val t = a.tree("A-04")
        val base = a.exportJsonText()
        val b = store(); b.importBackup(base.byteInputStream())
        val ta = a.data.treeById(t.id)!!
        a.editTree(t.id, ta.version, "a") { it.copy(variety = "A-variety") }
        val tb = b.data.treeById(t.id)!!
        b.editTree(t.id, tb.version, "b") { it.copy(variety = "B-variety") }
        val r = b.importBackup(a.exportJsonText().byteInputStream()).getOrThrow()
        assertEquals(1, r.conflicts)
        assertEquals("B-variety", b.data.treeById(t.id)!!.variety)           // giữ bản trên máy
        assertEquals(1, b.data.conflicts.size)
        b.resolveConflict(b.data.conflicts[0].id, takeRemote = true)
        assertEquals("A-variety", b.data.treeById(t.id)!!.variety)
        assertTrue(b.data.conflicts.isEmpty())
    }

    @Test fun `hop nhat tien len khi ben kia sua tiep tu cung phien ban`() {
        val a = store(); val t = a.tree("A-04")
        val b = store(); b.importBackup(a.exportJsonText().byteInputStream())
        val ta = a.data.treeById(t.id)!!
        a.editTree(t.id, ta.version, "a") { it.copy(variety = "moi") }
        val r = b.importBackup(a.exportJsonText().byteInputStream()).getOrThrow()
        assertEquals(1, r.updated); assertEquals(0, r.conflicts)
        assertEquals("moi", b.data.treeById(t.id)!!.variety)
    }

    // ---------- P0-02: hồ sơ cây ----------

    @Test fun `ban ghi theo khu chup lai danh sach cay tai thoi diem ghi`() {
        val s = store(); val a1 = s.tree("A-01"); val a2 = s.tree("A-02")
        val ev = Event(id = newId(), treeIds = listOf(a1.id, a2.id), scopeZone = "Khu A", type = EventType.CARE, activity = "Tưới nước",
            occurredAt = Dates.now(), author = "t")
        s.addEvent(ev)
        val a3 = s.tree("A-03")
        assertEquals(1, s.data.eventsFor(a1.id).size)
        assertEquals(0, s.data.eventsFor(a3.id).size)         // cây mới không tự thừa kế lịch sử khu
        assertEquals(2, s.data.events.single().treeIds.size)
    }

    @Test fun `cay thay the co id moi va khong ke thua lich su`() {
        val s = store(); val old = s.tree("A-04")
        s.addEvent(obs(listOf(old)))
        assertTrue(s.replaceTree(old.id, "A-04b", "cây chết").ok)
        val fresh = s.data.treeByCode("A-04b")!!
        val oldAfter = s.data.treeById(old.id)!!
        assertNotEquals(old.id, fresh.id); assertEquals(old.id, fresh.replacesId)
        assertFalse(oldAfter.active); assertEquals(fresh.id, oldAfter.replacedById)
        assertEquals(0, s.data.eventsFor(fresh.id).size)
        assertEquals(1, s.data.eventsFor(old.id).size)
    }

    // ---------- P0-03: quy trình vấn đề ----------

    @Test fun `khong the dong van de bang doi trang thai truc tiep`() {
        val s = store(); val t = s.tree("A-04")
        s.addIssue(listOf(t.id), null, "lá vàng", Priority.HIGH, "an", null)
        val i = s.data.issues.single()
        assertFalse(s.setIssueStatus(i.id, i.version, IssueStatus.CONFIRMED).ok)
    }

    @Test fun `tai kiem tra can bang chung va nguoi xac nhan`() {
        val s = store(); val t = s.tree("A-04")
        s.addIssue(listOf(t.id), null, "lá vàng", Priority.HIGH, "an", null)
        val i = s.data.issues.single()
        assertFalse(s.recheckIssue(i.id, i.version, true, "", emptyList(), "chủ vườn", Dates.now()).ok)    // thiếu bằng chứng
        assertFalse(s.recheckIssue(i.id, i.version, true, "đã xanh lại", emptyList(), "", Dates.now()).ok) // thiếu người xác nhận
        assertEquals(IssueStatus.NEW, s.data.issues.single().status)
        val v = s.data.issues.single().version
        assertTrue(s.recheckIssue(i.id, v, true, "đã xanh lại", emptyList(), "chủ vườn", Dates.now()).ok)
        val done = s.data.issues.single()
        assertEquals(IssueStatus.CONFIRMED, done.status); assertEquals("chủ vườn", done.confirmedBy)
        assertEquals(Condition.STABLE, s.data.treeById(t.id)!!.condition)
    }

    @Test fun `hoan tat cong viec chi chuyen sang cho tai kiem tra khong tu xac nhan`() {
        val s = store(); val t = s.tree("B-05")
        s.addIssue(listOf(t.id), null, "đối chiếu", Priority.MEDIUM, "an", null)
        var i = s.data.issues.single()
        s.setIssueStatus(i.id, i.version, IssueStatus.IN_PROGRESS)
        s.addTask("bón lại", listOf(t.id), null, "an", null, issueId = i.id)
        s.setTaskDone(s.data.tasks.single().id, true)
        i = s.data.issues.single()
        assertEquals(IssueStatus.PENDING_RECHECK, i.status)
        assertTrue(s.data.openIssuesFor(t.id).isNotEmpty())            // vẫn còn mở
        assertNotEquals(Condition.STABLE, s.data.effectiveCondition(s.data.treeById(t.id)!!))
    }

    @Test fun `tai kiem tra khong dat quay lai dang xu ly va co the mo lai`() {
        val s = store(); val t = s.tree("A-04")
        s.addIssue(listOf(t.id), null, "x", Priority.MEDIUM, "an", null)
        val i = s.data.issues.single()
        assertTrue(s.recheckIssue(i.id, i.version, false, "vẫn vàng", emptyList(), "an", Dates.now()).ok)
        assertEquals(IssueStatus.IN_PROGRESS, s.data.issues.single().status)
        val j = s.data.issues.single()
        assertTrue(s.recheckIssue(j.id, j.version, true, "ổn", emptyList(), "an", Dates.now()).ok)
        val k = s.data.issues.single()
        assertTrue(s.setIssueStatus(k.id, k.version, IssueStatus.PENDING_VERIFY).ok)   // mở lại
        assertTrue(s.data.issues.single().status.isOpen)
    }

    @Test fun `tinh trang tong hop khong coi cay chua quan sat la on dinh`() {
        val s = store(); val t = s.tree("C-06")
        assertEquals(Condition.UNASSESSED, s.data.effectiveCondition(t))
        assertEquals(CheckStatus.Never, s.data.checkStatus(t))
        s.addIssue(listOf(t.id), null, "x", Priority.HIGH, "an", null)
        assertEquals(Condition.NEEDS_CHECK, s.data.effectiveCondition(s.data.treeById(t.id)!!))
    }

    @Test fun `qua han kiem tra theo chu ky`() {
        val s = store(); val t = s.tree("A-01")
        s.addEvent(obs(listOf(t), daysAgo = 20))
        assertTrue(s.data.checkStatus(t) is CheckStatus.Overdue)
        assertTrue(s.data.todayWork().overdueTrees.any { it.id == t.id })
        s.addEvent(obs(listOf(t), daysAgo = 1))
        assertTrue(s.data.checkStatus(t) is CheckStatus.Ok)
    }

    @Test fun `cong viec va van de qua han hien thi trong viec hom nay`() {
        val s = store(); val t = s.tree("A-01")
        s.addTask("kiểm tra", listOf(t.id), null, "an", Dates.daysAgo(2))
        s.addIssue(listOf(t.id), null, "x", Priority.LOW, "an", Dates.daysAgo(1))
        val w = s.data.todayWork()
        assertEquals(1, w.overdueTasks.size); assertEquals(1, w.overdueIssues.size)
    }

    // ---------- P0-04: trợ lý AI ----------

    @Test fun `ai khong sinh ho so gia cho cay khong ton tai`() {
        val s = store(); s.tree("A-01")
        val a = Assistant.answer("Z-99 từng bị vậy chưa?", s.data)
        assertTrue(a.summary.contains("Không tìm thấy"))
        assertTrue(a.facts.isEmpty())
        assertEquals(1, s.data.trees.size)
    }

    @Test fun `ai tra loi tu ho so va dan nguon mo duoc`() {
        val s = store(); val t = s.tree("A-04")
        s.addEvent(Event(id = newId(), treeIds = listOf(t.id), type = EventType.CARE, activity = "Bón phân", occurredAt = Dates.daysAgo(3),
            author = "t", product = "Phân hữu cơ", dosage = "2 kg"))
        val a = Assistant.answer("A-04 đã được chăm sóc gì?", s.data)
        assertTrue(a.summary.contains("1 ghi chép chăm sóc"))
        val ref = a.facts.mapNotNull { it.ref }.filterIsInstance<SourceRef.Event>().single()
        assertEquals(s.data.events.single().id, ref.id)
        // thêm bản ghi -> câu trả lời cập nhật
        s.addEvent(Event(id = newId(), treeIds = listOf(t.id), type = EventType.CARE, activity = "Tưới nước", occurredAt = Dates.daysAgo(1), author = "t"))
        assertTrue(Assistant.answer("A-04 đã được chăm sóc gì?", s.data).summary.contains("2 ghi chép chăm sóc"))
    }

    @Test fun `ai noi ro khi chua co du lieu va khong suy benh`() {
        val s = store(); val t = s.tree("A-04")
        val a = Assistant.answer("A-04 từng bị vậy chưa?", s.data)
        assertTrue(a.summary.contains("chưa ghi nhận vấn đề"))
        assertTrue(a.missing.isNotEmpty())
        assertFalse(a.summary.contains("bệnh"))
        assertEquals(t.id, s.data.trees.single().id)
    }

    @Test fun `ai khong hieu cau hoi thi khong tra loi doanh thu`() {
        val s = store(); s.tree("A-01", fruits = 10)
        s.updateGarden(s.data.garden.copy(avgWeightKg = 2.0, pricePerKg = 60000.0))
        val a = Assistant.answer("hôm nay trời thế nào", s.data)
        assertFalse(a.understood)
        assertFalse(a.summary.contains("₫")); assertTrue(a.facts.isEmpty())
    }

    @Test fun `ai ban nhap cong viec khong doi trang thai cay`() {
        val s = store(); val t = s.tree("A-01")
        s.addEvent(obs(listOf(t), daysAgo = 40))
        val before = s.data.treeById(t.id)!!
        val a = Assistant.answer("Cây nào quá hạn kiểm tra?", s.data)
        assertTrue(a.drafts.isNotEmpty())
        assertEquals(before, s.data.treeById(t.id)); assertTrue(s.data.tasks.isEmpty())
    }

    @Test fun `ai dem bang phep tinh`() {
        val s = store(); s.tree("A-01", fruits = 10); s.tree("A-02", fruits = 5); s.tree("A-03")
        val a = Assistant.answer("Vườn có bao nhiêu cây và trái?", s.data)
        assertTrue(a.summary.contains("3 cây") && a.summary.contains("15 trái"))
        assertTrue(a.missing.any { it.contains("1 cây chưa đếm") })
    }

    // ---------- Sản lượng ----------

    @Test fun `san luong 0 trai luu duoc va cay chua dem khong bi tinh la 0`() {
        val s = store(); s.tree("A-01", fruits = 0); s.tree("A-02")
        s.updateGarden(s.data.garden.copy(avgWeightKg = 2.0, pricePerKg = 50000.0))
        val y = s.data.yieldCalc()
        assertEquals(1, y.countedTrees); assertEquals(0, y.fruits); assertEquals(0.0, y.estKg!!, 1e-9)
        assertEquals(2, y.totalTrees)
    }

    @Test fun `thieu gia dinh thi khong co uoc tinh`() {
        val s = store(); s.tree("A-01", fruits = 10)
        val y = s.data.yieldCalc()
        assertNull(y.estKg); assertNull(y.revenue)
    }

    @Test fun `uoc tinh kich ban va chenh lech thuc thu`() {
        val s = store(); s.tree("A-01", fruits = 100)
        s.updateGarden(s.data.garden.copy(avgWeightKg = 2.0, commercialRatio = 0.9, spreadPct = 10.0, pricePerKg = 10_000.0))
        val y = s.data.yieldCalc()
        assertEquals(180.0, y.estKg!!, 1e-6)                       // 100 × 2 × 0,9
        assertEquals(1_800_000.0, y.revenue!!, 1e-3)
        assertEquals(162.0 * 9_000.0, y.scenarios[0].revenue!!, 1e-3)   // thấp: -10% cả khối lượng và giá
        assertEquals(198.0 * 11_000.0, y.scenarios[2].revenue!!, 1e-3)
        s.addEvent(Event(id = newId(), treeIds = listOf(s.data.trees[0].id), type = EventType.HARVEST, activity = "Thu hoạch", occurredAt = Dates.now(), author = "t", amountKg = 150.0))
        s.addSale(Dates.now(), 100.0, 9_000.0, "")
        val y2 = s.data.yieldCalc()
        assertEquals(150.0, y2.actualKg, 1e-9); assertEquals(-30.0, y2.varianceKg!!, 1e-9)
        assertEquals(900_000.0, y2.soldAmount, 1e-3)
    }

    // ---------- P1-01: GIS ----------

    private val boundaryJson = """{"type":"FeatureCollection","features":[
      {"type":"Feature","properties":{"name":"Ranh vườn"},"geometry":{"type":"Polygon","coordinates":[[[107.8070,11.5480],[107.8082,11.5480],[107.8082,11.5492],[107.8070,11.5492],[107.8070,11.5480]]]}},
      {"type":"Feature","properties":{"name":"A-04"},"geometry":{"type":"Point","coordinates":[107.8075,11.5486]}},
      {"type":"Feature","properties":{"name":"B-01"},"geometry":{"type":"Point","coordinates":[107.9000,11.6000]}}]}"""

    @Test fun `geojson doc dung thu tu kinh vi va khong dao`() {
        val r = Geo.parseGeoJson(boundaryJson)
        assertTrue(r.errors.isEmpty())
        val p = r.features.first { it.name == "A-04" }.points.single()
        assertEquals(11.5486, p.lat, 1e-9); assertEquals(107.8075, p.lng, 1e-9)
    }

    @Test fun `nhap xuat geojson khong dao kinh vi`() {
        val s = store(); val t = s.tree("A-04")
        s.editTree(t.id, t.version, "pos") { it.copy(pos = GeoPoint(11.5486, 107.8075), posSource = PosSource.ESTIMATED) }
        val back = Geo.parseGeoJson(Geo.exportGeoJson(s.data))
        val p = back.features.first { it.name == "A-04" }.points.single()
        assertEquals(11.5486, p.lat, 1e-9); assertEquals(107.8075, p.lng, 1e-9)
    }

    @Test fun `vi do ngoai pham vi bi bao loi dao kinh vi`() {
        val r = Geo.parseGeoJson("""{"type":"Feature","properties":{"name":"X"},"geometry":{"type":"Point","coordinates":[11.5486,107.8075]}}""")
        assertTrue(r.errors.any { it.contains("đảo") || it.contains("ngoài phạm vi") })
        assertTrue(r.features.isEmpty())
    }

    @Test fun `he toa do khac wgs84 bi tu choi`() {
        val r = Geo.parseGeoJson("""{"type":"FeatureCollection","crs":{"type":"name","properties":{"name":"EPSG:3857"}},"features":[
            {"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[1,1]}}]}""")
        assertTrue(r.errors.any { it.contains("WGS84") })
    }

    @Test fun `ranh tu cat va dien tich duoc kiem tra`() {
        val bowtie = listOf(GeoPoint(11.0, 107.0), GeoPoint(11.001, 107.001), GeoPoint(11.0, 107.001), GeoPoint(11.001, 107.0))
        assertTrue(Geo.selfIntersects(bowtie))
        val sq = listOf(GeoPoint(11.0, 107.0), GeoPoint(11.0, 107.001), GeoPoint(11.001, 107.001), GeoPoint(11.001, 107.0))
        assertFalse(Geo.selfIntersects(sq))
        assertEquals(1.2, Geo.polygonAreaHa(sq), 0.2)             // ~109 m × 111 m ≈ 1,2 ha
    }

    @Test fun `kiem tra truoc khi nhap phat hien cay ngoai ranh va cap nhat cay co san`() {
        val s = store(); s.tree("A-04")
        val r = Geo.parseGeoJson(boundaryJson)
        var first = true
        val items = r.features.map { f -> ImportItem(f, Geo.guessKind(f, first).also { if (f.geom == Geom.POLYGON) first = false }) }
        val issues = Geo.validate(items, s.data)
        assertTrue(issues.none { it.error })
        assertTrue(issues.any { it.text.contains("B-01") && it.text.contains("ngoài ranh") })
        assertTrue(issues.any { it.text.contains("A-04") && it.text.contains("đã có") })
        val twoBoundaries = items + ImportItem(items.first().feature, ImportKind.BOUNDARY)
        twoBoundaries.first().kind = ImportKind.BOUNDARY
        assertTrue(Geo.validate(twoBoundaries, s.data).any { it.error && it.text.contains("một ranh") })
    }

    @Test fun `nhap vi tri giu nguyen id cay va them cay moi o khu bat ky`() {
        val s = store(); val t = s.tree("A-04")
        s.applyGeoImport(emptyList(), listOf(VWingStore.TreePosUpdate("A-04", null, GeoPoint(11.5486, 107.8075)),
            VWingStore.TreePosUpdate("Q-01", "Khu Mới", GeoPoint(11.5487, 107.8076))), "test")
        assertEquals(t.id, s.data.treeByCode("A-04")!!.id)
        assertEquals(PosSource.ESTIMATED, s.data.treeByCode("A-04")!!.posSource)
        assertEquals("Khu Mới", s.data.treeByCode("Q-01")!!.zone)
        assertTrue(s.data.zones.any { it.name == "Khu Mới" })
    }

    @Test fun `kml doc kinh vi dung thu tu`() {
        val kml = """<?xml version="1.0"?><kml><Document>
          <Placemark><name>Ranh vườn</name><Polygon><outerBoundaryIs><LinearRing><coordinates>107.0,11.0,0 107.001,11.0,0 107.001,11.001,0 107.0,11.001,0 107.0,11.0,0</coordinates></LinearRing></outerBoundaryIs></Polygon></Placemark>
          <Placemark><name>A-04</name><Point><coordinates>107.0005,11.0005,0</coordinates></Point></Placemark></Document></kml>"""
        val r = Geo.parseKml(kml)
        assertTrue(r.errors.isEmpty()); assertEquals(2, r.features.size)
        val p = r.features.first { it.name == "A-04" }.points.single()
        assertEquals(11.0005, p.lat, 1e-9); assertEquals(107.0005, p.lng, 1e-9)
        assertEquals(Geom.POLYGON, r.features.first { it.name == "Ranh vườn" }.geom)
    }

    // ---------- P0-05: nhập hàng loạt ----------

    @Test fun `nhap bang tinh phat hien trung va loi, o trai trong la chua dem`() {
        val s = store(); s.tree("A-01")
        val csv = "Mã cây\tKhu\tSố trái\tVĩ độ\tKinh độ\nA-01\tKhu A\t10\t\t\nA-02\tKhu A\t\t\t\nA-02\tKhu A\t5\t\t\nA-03\tKhu A\tabc\t\t\nA-04\tKhu B\t7\t95\t107"
        val p = BulkImport.parse(csv, s.data)
        assertNull(p.fatal)
        val st = p.rows.map { it.status }
        assertEquals(listOf(RowStatus.DUP_EXISTING, RowStatus.OK, RowStatus.DUP_FILE, RowStatus.ERROR, RowStatus.ERROR), st)
        assertNull(p.rows[1].fruits)                   // để trống = chưa đếm, không phải 0
        val n = s.bulkAddTrees(p.addable.map { VWingStore.TreeRow(it.code, it.zone, it.variety, it.year, it.fruits, it.pos) })
        assertEquals(1, n); assertNull(s.data.treeByCode("A-02")!!.fruits)
    }

    @Test fun `chuan hoa ma cay`() {
        assertEquals("A-04", normalizeCode("a4")); assertEquals("A-04", normalizeCode("A04")); assertEquals("B-12", normalizeCode(" b - 12 "))
        assertNull(normalizeCode("khu a"))
    }

    // ---------- Ghi hoạt động ----------

    @Test fun `quan sat cap nhat tinh trang va so trai co dau vet`() {
        val s = store(); val t = s.tree("A-01", fruits = 10)
        s.addEvent(obs(listOf(t), cond = Condition.WATCH), fruits = 12)
        val a = s.data.treeById(t.id)!!
        assertEquals(Condition.WATCH, a.condition); assertEquals(12, a.fruits)
        assertTrue(a.audit.any { it.summary.contains("Số trái") })
    }

    @Test fun `sao luu va khoi phuc giu nguyen noi dung qua zip`() {
        val a = store(); val t = a.tree("A-04"); a.addEvent(obs(listOf(t), note = "ghi chú"))
        val out = java.io.ByteArrayOutputStream(); a.exportBackup(out)
        val b = store()
        val r = b.importBackup(out.toByteArray().inputStream()).getOrThrow()
        assertEquals(2, r.added)
        assertEquals("ghi chú", b.data.events.single().note)
    }

    @Test fun `seed co du trang thai de minh hoa`() {
        val d = Seed.build()
        assertEquals(24, d.activeTrees.size)
        assertTrue(d.activeTrees.any { d.checkStatus(it) == CheckStatus.Never })
        assertTrue(d.activeTrees.any { d.checkStatus(it) is CheckStatus.Overdue })
        assertEquals(3, d.issues.count { it.status.isOpen })
        // JSON roundtrip
        val back = VJson.fromJson(VJson.toJson(d))
        assertEquals(d.trees, back.trees); assertEquals(d.events, back.events); assertEquals(d.issues, back.issues)
    }

    @Test fun `qr quet ra dung cay ke ca khi doi ma hien thi`() {
        val d = Seed.build()
        val a04 = d.trees.first { it.code == "A-04" }
        val payload = Qr.payloadFor(a04)
        assertEquals(a04, (Qr.resolve(d, payload) as Qr.Scan.Found).tree)
        val renamed = d.copy(trees = d.trees.map { if (it.id == a04.id) it.copy(code = "Z-99") else it })
        val found = (Qr.resolve(renamed, payload) as Qr.Scan.Found).tree
        assertEquals(a04.id, found.id); assertEquals("Z-99", found.code)
        // nhãn chỉ có mã cây vẫn dùng được
        assertEquals(a04.id, (Qr.resolve(d, " a04 ") as Qr.Scan.Found).tree.id)
    }

    @Test fun `qr khong khop cay thi khong tao ho so gia`() {
        val d = Seed.build()
        assertTrue(Qr.resolve(d, "vwing://tree/khong-co-id") is Qr.Scan.Unknown)
        assertTrue(Qr.resolve(d, "https://example.com/abc") is Qr.Scan.Foreign)
        assertTrue(Qr.resolve(d, "Z-77") is Qr.Scan.Foreign)
        assertEquals(24, d.trees.size)
    }

    @Test fun `ma qr tao ra giai ma lai dung noi dung`() {
        val d = Seed.build()
        val text = Qr.payloadFor(d.trees[5])
        val hints = mapOf(com.google.zxing.EncodeHintType.MARGIN to 1, com.google.zxing.EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M)
        val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, 300, 300, hints)
        val px = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val src = com.google.zxing.RGBLuminanceSource(m.width, m.height, px)
        val out = com.google.zxing.MultiFormatReader().decode(com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(src))).text
        assertEquals(text, out)
    }
}
