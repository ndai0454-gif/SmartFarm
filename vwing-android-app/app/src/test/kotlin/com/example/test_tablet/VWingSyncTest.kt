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

/** Chạy ứng dụng khách đồng bộ với máy chủ Node thật (vwing-sync-server). Bỏ qua nếu máy không có Node hoặc không thấy máy chủ. */
class VWingSyncTest {
    @get:Rule val tmp = TemporaryFolder()
    private var proc: Process? = null
    private lateinit var url: String
    private val token = "test-token-123456"

    @Before fun startServer() {
        val script = listOf("E:/New folder/SmartFarm/vwing-sync-server/server.js", System.getenv("VWING_SYNC_SERVER") ?: "")
            .map(::File).firstOrNull { it.isFile }
        assumeTrue("Không thấy vwing-sync-server/server.js", script != null)
        val port = ServerSocket(0).use { it.localPort }
        val pb = ProcessBuilder("node", script!!.absolutePath).redirectErrorStream(true)
        pb.environment()["PORT"] = port.toString(); pb.environment()["VWING_TOKEN"] = token
        pb.environment()["DATA_DIR"] = tmp.newFolder("srv").absolutePath
        proc = try { pb.start() } catch (e: Exception) { null }
        assumeTrue("Không chạy được node", proc != null)
        url = "http://127.0.0.1:$port"
        val deadline = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < deadline) {
            try { if ((URL("$url/v1/health").openConnection() as java.net.HttpURLConnection).responseCode == 200) return } catch (_: Exception) { Thread.sleep(150) }
        }
        fail("Máy chủ đồng bộ không khởi động được")
    }

    @After fun stopServer() { proc?.destroyForcibly() }

    private fun cfg(key: String = "vuon-test", tok: String = token) = SyncConfig(url, key, tok)
    private fun device() = VWingStore(tmp.newFolder(), null, false)
    private fun VWingStore.tree(code: String) = run { check(addTree(code, "Khu A").ok); data.treeByCode(code)!! }
    private fun ok(r: SyncResult) = (r as? SyncResult.Success) ?: fail("Đồng bộ lỗi: $r").let { throw AssertionError() }

    @Test fun `hai thiet bi dong bo qua lai thay du lieu cua nhau va danh dau da dong bo`() {
        val a = device(); val b = device()
        a.tree("A-01"); a.tree("A-02")
        ok(SyncClient.run(a, cfg()))
        assertEquals(0, a.data.unsyncedCount())
        ok(SyncClient.run(b, cfg()))
        assertEquals(setOf("A-01", "A-02"), b.data.trees.map { it.code }.toSet())
        assertEquals(0, b.data.unsyncedCount())
        // B sửa, A nhận lại
        val t = b.data.treeByCode("A-01")!!
        assertTrue(b.editTree(t.id, t.version, "đổi giống") { it.copy(variety = "Ri6") }.ok)
        ok(SyncClient.run(b, cfg())); ok(SyncClient.run(a, cfg()))
        assertEquals("Ri6", a.data.treeByCode("A-01")!!.variety)
        // đồng bộ lại không tạo bản ghi trùng
        ok(SyncClient.run(a, cfg())); ok(SyncClient.run(b, cfg()))
        assertEquals(2, a.data.trees.size); assertEquals(2, b.data.trees.size)
    }

    @Test fun `bản ghi sửa trong lúc chưa đồng bộ vẫn ở trạng thái chưa đồng bộ`() {
        val a = device(); val t = a.tree("A-01")
        ok(SyncClient.run(a, cfg()))
        val cur = a.data.treeByCode("A-01")!!
        assertTrue(a.editTree(cur.id, cur.version, "sửa") { it.copy(variety = "X") }.ok)
        assertEquals(1, a.data.unsyncedCount())
        assertEquals(SyncState.LOCAL, a.data.treeByCode("A-01")!!.sync); assertNotNull(t)
    }

    @Test fun `hai ben cung sua mot cay thi bi chan va khong ghi de ban tren may chu`() {
        val a = device(); val b = device()
        a.tree("A-01"); ok(SyncClient.run(a, cfg())); ok(SyncClient.run(b, cfg()))
        val ta = a.data.treeByCode("A-01")!!; val tb = b.data.treeByCode("A-01")!!
        a.editTree(ta.id, ta.version, "a sửa") { it.copy(variety = "Của A") }
        b.editTree(tb.id, tb.version, "b sửa") { it.copy(variety = "Của B") }
        ok(SyncClient.run(a, cfg()))                                   // A lên máy chủ trước
        val r = SyncClient.run(b, cfg())
        assertEquals(SyncResult.Blocked(1), r)                          // B bị chặn, không ghi đè bản của A
        assertEquals("Của B", b.data.treeByCode("A-01")!!.variety)      // dữ liệu của B còn nguyên cho tới khi chọn
        val check = device(); ok(SyncClient.run(check, cfg()))
        assertEquals("Của A", check.data.treeByCode("A-01")!!.variety)  // máy chủ vẫn là bản của A
        // B giải quyết (giữ bản của B) rồi đồng bộ lại thì máy chủ cập nhật
        b.resolveConflict(b.data.conflicts.single().id, takeRemote = false)
        ok(SyncClient.run(b, cfg())); ok(SyncClient.run(check, cfg()))
        assertEquals("Của B", check.data.treeByCode("A-01")!!.variety)
    }

    @Test fun `anh di theo ban ghi sang thiet bi khac`() {
        val a = device(); val b = device()
        val t = a.tree("A-01")
        val photo = File(a.photoDir, "p1.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        a.addEvent(Event(id = newId(), treeIds = listOf(t.id), type = EventType.OBSERVATION, activity = "Kiểm tra", occurredAt = Dates.now(),
            author = "t", note = "có ảnh", photos = listOf(Photo(photo.absolutePath, null, Dates.now()))))
        val ra = ok(SyncClient.run(a, cfg())); assertEquals(1, ra.photosUp)
        val rb = ok(SyncClient.run(b, cfg())); assertEquals(1, rb.photosDown)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), File(b.photoDir, "p1.jpg").readBytes())
        assertEquals(File(b.photoDir, "p1.jpg").absolutePath, b.data.events.single().photos.single().path)
        assertEquals(0, ok(SyncClient.run(a, cfg())).photosUp)           // ảnh đã có trên máy chủ, không gửi lại
    }

    @Test fun `anh drone so do nuoc dat va chi so ndvi dong bo kem tep sang thiet bi khac`() {
        val a = device(); val b = device()
        File(a.photoDir, "ortho1.img").writeBytes(byteArrayOf(7, 7, 7, 1))
        File(a.photoDir, "grid1.ndvi.png").writeBytes(byteArrayOf(9, 8, 7))
        val set = ImageSet(newId(), "Bay 1", ImageKind.ORTHO, Dates.daysAgo(2), "Đối tác", SensorKind.RGB, "", "ortho1.img", 10, 10, GeoBounds(11.5, 107.8, 11.6, 107.9))
        assertTrue(a.addImageSet(set).ok)
        assertTrue(a.addMeasure(Measure(newId(), MeasureKind.RAIN, MeasureOrigin.MEASURED, 12.5, "mm", Dates.daysAgo(1))).ok)
        assertTrue(a.addVegIndex(VegIndex(newId(), "NDVI", IndexSource.COMPUTED, Dates.daysAgo(2), "Đối tác", SensorKind.MULTISPECTRAL, Preprocess.REFLECTANCE, 0.5,
            GeoBounds(11.5, 107.8, 11.6, 107.9), set.id, set.id, "grid1.ndvi.png", 10, 10, stats = listOf(IndexStat("garden", 0.5, 0.2, 0.8, 90, 90.0)))).ok)
        val ra = ok(SyncClient.run(a, cfg())); assertEquals(2, ra.photosUp)
        val rb = ok(SyncClient.run(b, cfg())); assertEquals(2, rb.photosDown)
        assertArrayEquals(byteArrayOf(7, 7, 7, 1), File(b.photoDir, "ortho1.img").readBytes())
        assertArrayEquals(byteArrayOf(9, 8, 7), File(b.photoDir, "grid1.ndvi.png").readBytes())
        assertEquals(1, b.data.imagery.size); assertEquals(1, b.data.measures.size); assertEquals(1, b.data.indices.size)
        assertEquals(0, b.data.unsyncedCount())
        // đồng bộ lại không gửi/nhận lại tệp và không nhân đôi
        val again = ok(SyncClient.run(b, cfg())); assertEquals(0, again.photosUp); assertEquals(0, again.photosDown)
        assertEquals(1, b.data.imagery.size)
    }

    @Test fun `sai ma truy cap dia chi loi va du lieu mau deu bi tu choi ro rang`() {
        val a = device(); a.tree("A-01")
        assertEquals(SyncResult.Failure("Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại."), SyncClient.run(a, cfg(tok = "sai-sai-sai-sai")))
        assertTrue(SyncClient.run(a, SyncConfig("khong-phai-url", "vuon-test", token)) is SyncResult.Failure)
        assertTrue(SyncClient.run(a, SyncConfig("http://127.0.0.1:1", "vuon-test", token)) is SyncResult.Failure)   // không có máy chủ
        val sample = VWingStore(tmp.newFolder(), null, true)
        assertTrue((SyncClient.run(sample, cfg()) as SyncResult.Failure).message.contains("dữ liệu mẫu"))
    }

    @Test fun `http ngoai mang noi bo bi tu choi`() {
        assertNull(SyncClient.urlProblem("http://192.168.1.10:8790"))
        assertNull(SyncClient.urlProblem("http://172.20.0.5"))
        assertNull(SyncClient.urlProblem("https://vwing.example.com"))
        assertNotNull(SyncClient.urlProblem("http://vwing.example.com"))
        assertNotNull(SyncClient.urlProblem("http://172.40.0.5"))
        assertNotNull(SyncClient.urlProblem("ftp://192.168.1.2"))
    }
}
