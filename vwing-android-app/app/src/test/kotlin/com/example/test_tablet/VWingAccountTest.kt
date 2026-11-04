package com.example.test_tablet

import com.example.test_tablet.data.vwing.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.ServerSocket
import java.net.URL

/** Xoá lan truyền, hợp nhất dấu vết xoá và thông tin vườn (không cần máy chủ). */
class CascadeTest {
    private fun tree(id: String, code: String, zone: String = "A") = Tree(id = id, code = code, zone = zone)
    private fun ev(id: String, vararg trees: String, scopeZone: String? = null, issueId: String? = null) =
        Event(id = id, treeIds = trees.toList(), scopeZone = scopeZone, type = EventType.CARE, activity = "Tưới", occurredAt = 1L, author = "x", issueId = issueId)

    private fun base() = GardenData(
        zones = listOf(Zone("A", ""), Zone("B", "")),
        trees = listOf(tree("t1", "A-01"), tree("t2", "A-02"), tree("t3", "B-01", "B")),
        events = listOf(ev("e1", "t1"), ev("e2", "t1", "t2"), ev("e3", "t3")),
        issues = listOf(Issue(id = "i1", number = 1, treeIds = listOf("t1"), symptom = "x", originEventId = "e1")),
        tasks = listOf(Task(id = "k1", title = "xử lý", issueId = "i1")),
        configured = true,
    )

    @Test fun `xoa cay xoa cac ban ghi chi gan cay do va bo lien ket phan con lai`() {
        val r = Cascade.delete(base(), "trees", "t1", "Chủ", now = 99)
        assertEquals(listOf("t2", "t3"), r.data.trees.map { it.id })
        assertEquals(listOf("e2", "e3"), r.data.events.map { it.id })
        assertEquals(listOf("t2"), r.data.events.first { it.id == "e2" }.treeIds)
        assertEquals(2, r.data.events.first { it.id == "e2" }.version)         // phiên bản tăng để thiết bị khác nhận bản mới
        assertEquals(0, r.data.issues.size); assertEquals(0, r.data.tasks.size)
        assertEquals(setOf("trees:t1", "events:e1", "issues:i1", "tasks:k1"), r.removed.map { it.type + ":" + it.id }.toSet())
        assertTrue(r.removed.all { it.at == 99L && it.by == "Chủ" })
    }

    @Test fun `xoa khu xoa cay trong khu va moi thu phu thuoc`() {
        val r = Cascade.delete(base(), "zones", "A", "Chủ")
        assertEquals(listOf("t3"), r.data.trees.map { it.id }); assertEquals(listOf("e3"), r.data.events.map { it.id })
        assertEquals(listOf("B"), r.data.zones.map { it.name })
        assertEquals(1, r.count("zones")); assertEquals(2, r.count("trees"))
    }

    @Test fun `xoa ghi chep va van de bo lien ket nguoc`() {
        var r = Cascade.delete(base(), "events", "e1", "Chủ")
        assertNull(r.data.issues.single().originEventId)
        val withLink = base().copy(events = base().events + ev("e9", "t2", issueId = "i1"))
        r = Cascade.delete(withLink, "issues", "i1", "Chủ")
        assertNull(r.data.events.first { it.id == "e9" }.issueId)
        assertEquals(0, r.data.tasks.size)
        assertTrue(Cascade.delete(base(), "trees", "khong-co", "x").removed.isEmpty())
    }

    @Test fun `xoa o thiet bi nay se xoa ca o thiet bi kia khi hop nhat va khong dua ban ghi tro lai`() {
        val dir1 = TemporaryFolder().apply { create() }; val dir2 = TemporaryFolder().apply { create() }
        val a = VWingStore(dir1.newFolder(), null, false); val b = VWingStore(dir2.newFolder(), null, false)
        assertTrue(a.addTree("A-01", "A").ok); assertTrue(a.addTree("A-02", "A").ok)
        val t1 = a.data.treeByCode("A-01")!!; val t2 = a.data.treeByCode("A-02")!!
        a.addEvent(ev("e1", t1.id)); a.addEvent(ev("e2", t1.id, t2.id))
        b.mergeRemote(a.data)
        assertEquals(2, b.data.trees.size)
        assertEquals(1, a.deletePreview("trees", t1.id).getValue("events") - 0)                 // e1 bị xoá, e2 chỉ bỏ liên kết
        assertTrue(a.deleteEntity("trees", t1.id).ok)
        val rep = b.mergeRemote(a.data)
        assertEquals(listOf("A-02"), b.data.trees.map { it.code })
        assertEquals(listOf("e2"), b.data.events.map { it.id }); assertEquals(listOf(t2.id), b.data.events.single().treeIds)
        assertEquals(0, b.data.conflicts.size); assertTrue(rep.deleted >= 2)
        // thiết bị B cũ (chưa biết việc xoá) gửi lại bản cũ cho A: không làm cây sống lại
        val stale = VWingStore(dir2.newFolder(), null, false); stale.mergeRemote(GardenData(trees = listOf(t1), configured = true))
        a.mergeRemote(stale.data)
        assertEquals(listOf("A-02"), a.data.trees.map { it.code })
    }

    @Test fun `thong tin vuon ban sua sau cung thang nhung ten nguoi dung rieng tung may`() {
        val a = VWingStore(TemporaryFolder().apply { create() }.newFolder(), null, false)
        val b = VWingStore(TemporaryFolder().apply { create() }.newFolder(), null, false)
        a.updateGarden(a.data.garden.copy(name = "Vườn cũ", operatorName = "An"))
        b.mergeRemote(a.data)
        Thread.sleep(5)
        b.updateGarden(b.data.garden.copy(name = "Vườn mới", operatorName = "Bình"))
        a.mergeRemote(b.data)
        assertEquals("Vườn mới", a.data.garden.name); assertEquals("An", a.data.garden.operatorName)
        b.mergeRemote(a.data.copy(garden = a.data.garden.copy(name = "Cũ hơn", updatedAt = 1)))
        assertEquals("Vườn mới", b.data.garden.name)
    }

    @Test fun `ma moi QR doc duoc ca dang day du va ma tran`() {
        val link = InviteLink.payload("http://192.168.1.5:8790/", "ABCDEFGHIJ")
        assertEquals("ABCDEFGHIJ" to "http://192.168.1.5:8790", InviteLink.parse(link))
        assertEquals("ABCDEFGHIJ" to null, InviteLink.parse("abcde-fghij"))
        assertNull(InviteLink.parse("vwing://tree/123")); assertNull(InviteLink.parse("xyz"))
        assertEquals("http://h:1/p/vuon/t1", publicTreeUrl("http://h:1/", "vuon", "t1"))
    }
}

/** Tài khoản, phân quyền, lời mời, thu hồi và xoá lan truyền với máy chủ Node thật. Bỏ qua nếu máy không có Node. */
class VWingAccountTest {
    @get:Rule val tmp = TemporaryFolder()
    private var proc: Process? = null
    private lateinit var url: String

    @Before fun startServer() {
        val script = listOf("E:/New folder/SmartFarm/vwing-sync-server/server.js", System.getenv("VWING_SYNC_SERVER") ?: "")
            .map(::File).firstOrNull { it.isFile }
        assumeTrue("Không thấy vwing-sync-server/server.js", script != null)
        val port = ServerSocket(0).use { it.localPort }
        val pb = ProcessBuilder("node", script!!.absolutePath).redirectErrorStream(true)
        pb.environment()["PORT"] = port.toString(); pb.environment()["VWING_TOKEN"] = "test-token-123456"
        pb.environment()["DATA_DIR"] = tmp.newFolder("srv").absolutePath
        proc = try { pb.start() } catch (e: Exception) { null }
        assumeTrue("Không chạy được node", proc != null)
        url = "http://127.0.0.1:$port"
        val deadline = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < deadline) {
            try { if ((URL("$url/v1/health").openConnection() as java.net.HttpURLConnection).responseCode == 200) return } catch (_: Exception) { Thread.sleep(150) }
        }
        fail("Máy chủ không khởi động được")
    }

    @After fun stop() { proc?.destroyForcibly() }

    private fun device() = VWingStore(tmp.newFolder(), null, false)
    private fun ok(r: SyncResult) = (r as? SyncResult.Success) ?: throw AssertionError("Đồng bộ lỗi: $r")
    private fun cfgFor(s: Session, key: String, role: String = "") = SyncConfig(url, key, s.token, userName = s.userName, phone = s.phone, role = role)

    @Test fun `quy trinh day du chu moi nhan vien bi tu choi quyen xoa lan truyen va thu hoi`() {
        val owner = Account.register(url, "0911000001", "Chủ", "matkhau-12345")
        val worker = Account.register(url, "0911000002", "Công", "matkhau-12345")
        val ocfg = cfgFor(owner, "vuon-x", "owner")
        Account.createGarden(ocfg, "vuon-x", "Vườn X")
        val a = device(); val w = device()
        a.addTree("A-01", "A"); a.addTree("A-02", "A")
        val t1 = a.data.treeByCode("A-01")!!
        a.addEvent(Event(id = newId(), treeIds = listOf(t1.id), type = EventType.CARE, activity = "Bón", occurredAt = Dates.now(), author = "Chủ"))
        a.addSale(Dates.now(), 100.0, 90000.0, "")
        a.updateGarden(a.data.garden.copy(name = "Vườn X", pricePerKg = 90000.0))
        ok(SyncClient.run(a, ocfg))

        // mời bằng QR → nhân viên tham gia
        val inv = Account.invite(ocfg, "worker", emptyList(), null, "")
        val code = InviteLink.parse(InviteLink.payload(url, inv.code!!))!!.first
        val wcfg0 = cfgFor(worker, "")
        val joined = Account.acceptInvite(wcfg0, code)
        assertEquals("worker", joined.role)
        assertThrows(AccountException::class.java) { Account.acceptInvite(wcfg0, code) }               // dùng một lần
        val wcfg = cfgFor(worker, "vuon-x", "worker")
        val first = ok(SyncClient.run(w, wcfg))
        assertEquals("worker", first.role)
        assertEquals(2, w.data.trees.size)
        assertTrue("nhân viên không thấy doanh thu", w.data.sales.isEmpty())
        assertNull("nhân viên không thấy giá bán", w.data.garden.pricePerKg)

        // nhân viên cố ghi doanh thu và đổi tên vườn: máy chủ từ chối, chủ không bị ảnh hưởng
        w.addSale(Dates.now(), 5.0, 1.0, "")
        w.updateGarden(w.data.garden.copy(name = "Bị sửa trái phép"))
        val wr = ok(SyncClient.run(w, wcfg))
        assertTrue(wr.rejected.isNotEmpty())
        ok(SyncClient.run(a, ocfg))
        assertEquals(1, a.data.sales.size); assertEquals("Vườn X", a.data.garden.name)
        // nhật ký kiểm toán thấy việc bị từ chối
        assertTrue(Account.audit(ocfg).any { it.detail.contains("TỪ CHỐI") })
        // nhân viên không xem được thành viên / kiểm toán
        assertThrows(AccountException::class.java) { Account.members(wcfg) }
        assertThrows(AccountException::class.java) { Account.audit(wcfg) }

        // chủ xoá cây A-01 → nhật ký "Bón" bị xoá theo → nhân viên đồng bộ thì mất theo
        assertTrue(a.deleteEntity("trees", t1.id).ok)
        ok(SyncClient.run(a, ocfg)); ok(SyncClient.run(w, wcfg))
        assertEquals(listOf("A-02"), w.data.trees.map { it.code }); assertEquals(0, w.data.events.size)

        // thu hồi: nhân viên mất quyền ngay
        val m = Account.members(ocfg).first { it.name == "Công" }
        Account.revokeMember(ocfg, m.userId)
        val after = SyncClient.run(w, wcfg)
        assertTrue(after is SyncResult.Failure)
        assertTrue(Account.me(cfgFor(worker, "")).isEmpty())
    }

    @Test fun `trang cong khai chi hien truong chu chon`() {
        val owner = Account.register(url, "0911000009", "Chủ", "matkhau-12345")
        val ocfg = cfgFor(owner, "vuon-pub", "owner")
        Account.createGarden(ocfg, "vuon-pub", "Vườn công khai")
        val a = device(); a.addTree("A-01", "Khu A", variety = "Ri6")
        val t = a.data.treeByCode("A-01")!!
        a.addEvent(Event(id = newId(), treeIds = listOf(t.id), type = EventType.CARE, activity = "Bón phân", occurredAt = Dates.now(), author = "Tên bí mật", product = "Phân hữu cơ"))
        ok(SyncClient.run(a, ocfg))
        fun page() = Api.call(url, null, "GET", "/p/vuon-pub/${t.id}")
        assertEquals(404, page().code)                                         // mặc định tắt
        val cfg = Account.publicConfig(ocfg)
        assertTrue(cfg.allFields.any { it.id == "timeline.activities" })
        Account.savePublicConfig(ocfg, true, setOf("tree.code", "timeline.activities"), "")
        val p = page(); assertEquals(200, p.code)
        assertTrue(p.text.contains("A-01") && p.text.contains("Bón phân"))
        assertFalse(p.text.contains("Tên bí mật")); assertFalse(p.text.contains("Phân hữu cơ")); assertFalse(p.text.contains("Ri6"))
        Account.savePublicConfig(ocfg, false, emptySet(), "")
        assertEquals(404, page().code)
    }

    @Test fun `mat khau sai va dang ky trung bi tu choi`() {
        Account.register(url, "0911000100", "A", "matkhau-12345")
        assertThrows(AccountException::class.java) { Account.register(url, "0911000100", "B", "matkhau-12345") }
        assertThrows(AccountException::class.java) { Account.login(url, "0911000100", "sai-mat-khau") }
        assertEquals("A", Account.login(url, "0911000100", "matkhau-12345").userName)
    }

    @Test fun `chia se vi tri chi khi nguoi dung tu bat va chi chu thay`() {
        val owner = Account.register(url, "0911000200", "Chủ", "matkhau-12345")
        val farmer = Account.register(url, "0911000201", "Nông dân", "matkhau-12345")
        val ocfg = cfgFor(owner, "vuon-live", "owner")
        Account.createGarden(ocfg, "vuon-live", "Vườn")
        val inv = Account.invite(ocfg, "worker", emptyList(), null, "")
        Account.acceptInvite(cfgFor(farmer, ""), inv.code!!)
        val fcfg = cfgFor(farmer, "vuon-live", "worker")
        assertThrows(AccountException::class.java) { LiveShare.post(fcfg, 12.68, 108.04, 4f) }       // chưa bật
        assertTrue(LiveShare.start(fcfg, 15) > System.currentTimeMillis())
        LiveShare.post(fcfg, 12.680123, 108.040456, 3.5f)
        val seen = LiveShare.get(ocfg).people.single()
        assertEquals("Nông dân", seen.name); assertEquals(12.680123, seen.lat, 1e-9); assertEquals(3.5, seen.accM!!, 1e-6)
        LiveShare.stop(fcfg)
        assertTrue(LiveShare.get(ocfg).people.isEmpty())
    }

    @Test fun `don viec mo phi cong nhan viec den truoc duoc giu va chu cu chuyen quyen`() {
        val owner = Account.register(url, "0911000300", "Chủ", "matkhau-12345")
        val p1 = Account.register(url, "0911000301", "Phi Một", "matkhau-12345")
        val p2 = Account.register(url, "0911000302", "Phi Hai", "matkhau-12345")
        val ocfg = cfgFor(owner, "vuon-job", "owner")
        Account.createGarden(ocfg, "vuon-job", "Vườn")
        val a = device(); val b = device(); val c = device()
        a.addTree("A-01", "A")
        assertTrue(a.addTask("Phun thuốc khu A", emptyList(), "A", "", null, open = true).ok)
        assertFalse(a.addTask("Sai", emptyList(), null, "Ai đó", null, open = true).ok)       // đơn mở không được giao sẵn
        ok(SyncClient.run(a, ocfg))
        for ((u, ph) in listOf(p1 to "p1", p2 to "p2")) {
            val inv = Account.invite(ocfg, "pilot", listOf("A"), 7, "")
            Account.acceptInvite(cfgFor(u, ""), inv.code!!)
        }
        val c1 = cfgFor(p1, "vuon-job", "pilot"); val c2 = cfgFor(p2, "vuon-job", "pilot")
        ok(SyncClient.run(b, c1)); ok(SyncClient.run(c, c2))
        val id = b.data.tasks.single().id
        assertTrue(b.data.tasks.single().open)
        // hai người cùng nhận khi chưa đồng bộ: người đồng bộ trước được giữ
        assertTrue(b.claimTask(id, "Phi Một").ok); assertTrue(c.claimTask(id, "Phi Hai").ok)
        assertTrue(ok(SyncClient.run(b, c1)).rejected.isEmpty())
        val lost = SyncClient.run(c, c2)
        assertTrue(lost is SyncResult.Blocked || (lost is SyncResult.Success && lost.rejected.isNotEmpty()))   // người sau không giành được
        ok(SyncClient.run(a, ocfg))
        assertEquals("Phi Một", a.data.tasks.single().assignee); assertFalse(a.data.tasks.single().open)
        // người nhận trả lại thì thành đơn mở lại
        assertTrue(b.releaseTask(id, "Phi Một").ok); ok(SyncClient.run(b, c1)); ok(SyncClient.run(a, ocfg))
        assertTrue(a.data.tasks.single().open)
        assertFalse(b.releaseTask(id, "Phi Một").ok)                                         // không còn là việc của họ

        // chuyển quyền chủ: sai mật khẩu bị từ chối, đúng thì chủ cũ thành quản lý
        val m = Account.members(ocfg).first { it.name == "Phi Một" }
        assertTrue(runCatching { Account.transferOwner(ocfg, m.userId, "sai-mat-khau") }.exceptionOrNull() is AccountException)
        Account.transferOwner(ocfg, m.userId, "matkhau-12345")
        assertEquals("owner", Account.me(c1).first { it.key == "vuon-job" }.role)
        assertEquals("manager", Account.me(ocfg).first { it.key == "vuon-job" }.role)
    }
}
