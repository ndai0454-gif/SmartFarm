package com.example.test_tablet

import com.example.test_tablet.data.vwing.*
import com.example.test_tablet.ui.vwing.boundaryProblem
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.net.URL

/** Chạy máy khách vệ tinh với backend Python thật ở chế độ MÔ PHỎNG. Bỏ qua nếu không có .venv của backend. */
class VWingSatelliteTest {
    private var proc: Process? = null
    private lateinit var cfg: SatelliteConfig
    private val token = "sat-test-token-123456"
    private val ring = SatelliteClient.ringOf(Seed.build())!!

    @Before fun start() {
        val dir = File("E:/New folder/SmartFarm/vwing-satellite-backend")
        val py = File(dir, ".venv/Scripts/python.exe")
        assumeTrue("Không có backend vệ tinh (.venv)", py.isFile)
        val port = ServerSocket(0).use { it.localPort }
        val pb = ProcessBuilder(py.absolutePath, "-m", "uvicorn", "app.main:app", "--host", "127.0.0.1", "--port", port.toString()).directory(dir).redirectErrorStream(true)
        pb.environment()["VWING_SAT_TOKEN"] = token; pb.environment()["VWING_EE_MODE"] = "mock"
        proc = try { pb.start() } catch (e: Exception) { null }
        assumeTrue("Không chạy được backend", proc != null)
        cfg = SatelliteConfig("http://127.0.0.1:$port", token)
        val end = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < end) {
            try { if ((URL("${cfg.url}/v1/health").openConnection() as java.net.HttpURLConnection).responseCode == 200) return } catch (_: Exception) { Thread.sleep(250) }
        }
        fail("Backend vệ tinh không khởi động được")
    }

    @After fun stop() { proc?.destroyForcibly() }

    @Test fun `ranh gioi lay tu ranh vuon va khong co thi khong goi`() {
        assertTrue(ring.size >= 3)
        assertNull(SatelliteClient.ringOf(GardenData()))
        assertEquals(6, VIETNAM_GARDEN_PRESETS.size)
        VIETNAM_GARDEN_PRESETS.forEach { preset ->
            assertNull(preset.name, boundaryProblem(preset.points))
        }
    }

    @Test fun `kiem tra suc khoe may chu va xac thuc token`() {
        val h = SatelliteClient.health(cfg).getOrThrow()
        assertTrue(h.ok)
        assertEquals("mock", h.mode)
        assertEquals(3, h.indicesCount)
        assertEquals(9, h.cropProfilesCount)
        assertEquals(9, h.lulcSourcesCount)
        val bad = SatelliteClient.health(cfg.copy(token = "sai-token-123456"))
        assertTrue(bad.isFailure)
    }

    @Test fun `phan tich tong hop vuon tra du so cay 7 chi so va 9 nguon lop phu VN`() {
        val g = SatelliteClient.gardenAnalysis(cfg, ring, "2026-08-01", "2026-09-01", SatCropKey.DURIAN, treeSpacingM = 7.5).getOrThrow()
        assertTrue(g.simulated)
        assertTrue(g.areaHa > 1.0)
        assertTrue(g.perimeterM > 100.0)
        assertEquals(7.5, g.treeEstimation.assumedSpacingM, 0.01)
        assertTrue(g.treeEstimation.estimatedTrees > 0)
        assertEquals(
            g.treeEstimation.estimatedTrees,
            g.treeEstimation.healthBreakdown.healthyTrees + g.treeEstimation.healthBreakdown.moderateTrees + g.treeEstimation.healthBreakdown.stressedTrees,
        )
        assertTrue(g.treeEstimation.samplePoints.isNotEmpty())
        assertEquals(7, g.indices.size)
        assertEquals(setOf("NDVI", "EVI", "NDRE", "NDMI", "SAVI", "NDWI", "BSI"), g.indices.map { it.index }.toSet())
        assertTrue(g.landCover.keys.containsAll(listOf("dynamic_world", "esa_worldcover", "jaxa_vietnam_lulc", "copernicus_cgls_lc100")))
        assertEquals(9, g.vietnamLulcRef.size)
        assertNotNull(g.terrain.elevationMeanM)
        assertTrue(g.soilAndMoisture.moistureStatusVi.isNotBlank())
        assertTrue(g.recommendations.isNotEmpty())
        val png = SatelliteClient.image(cfg, g.imageUrl!!).getOrThrow()
        assertArrayEquals(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()), png.copyOf(4))
    }

    @Test fun `chi so ndvi tra du nguon ngay do phu va nhan mo phong`() {
        val r = SatelliteClient.index(cfg, ring, SatIndexKey.NDVI, "2026-08-01", "2026-09-01").getOrThrow()
        assertTrue(r.simulated); assertTrue(r.source.contains("MÔ PHỎNG"))
        assertEquals(10, r.pixelSizeM); assertTrue(r.sceneCount >= 1)
        assertTrue(r.mean!! in -1.0..1.0); assertTrue(r.validCoveragePct!! in 0.0..100.0)
        assertTrue(r.areaHa > 1.0); assertTrue(r.caution.isNotBlank()); assertTrue(r.warnings.isNotEmpty())
        assertTrue(r.bounds.south < r.bounds.north && r.bounds.west < r.bounds.east)
        // ảnh phủ tải được (đường dẫn tương đối của chính máy chủ) và là PNG
        val png = SatelliteClient.image(cfg, r.imageUrl!!).getOrThrow()
        assertArrayEquals(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()), png.copyOf(4))
    }

    @Test fun `ba chi so deu chay va chuoi thoi gian khong chua diem thieu`() {
        for (k in SatIndexKey.values()) assertEquals(k.name, SatelliteClient.index(cfg, ring, k, "2026-08-01", "2026-09-01").getOrThrow().index)
        val s = SatelliteClient.series(cfg, ring, SatIndexKey.NDVI, "2026-03-01", "2026-09-01").getOrThrow()
        assertTrue(s.points.isNotEmpty()); assertTrue(s.simulated)
        assertTrue(s.points.all { it.validCoveragePct >= 10 && it.mean in -1.0..1.0 })
        assertEquals(s.points.map { it.date }, s.points.map { it.date }.sorted())
    }

    @Test fun `nuoc mat tach ky nen va ky su kien va co canh bao kiem tra thuc dia`() {
        val w = SatelliteClient.water(cfg, ring, "2026-08-01", "2026-08-20", "2026-09-01", "2026-09-10").getOrThrow()
        assertTrue(w.simulated); assertTrue(w.newWaterHa!! >= 0)
        assertTrue(w.warnings.joinToString(" ").contains("kiểm tra thực địa"))
        // kỳ sự kiện trước kỳ nền bị từ chối kèm lý do
        val bad = SatelliteClient.water(cfg, ring, "2026-08-01", "2026-08-20", "2026-08-10", "2026-08-15")
        assertTrue(bad.isFailure)
    }

    @Test fun `loi duoc dich ra thong bao ro rang`() {
        val wrong = SatelliteClient.index(cfg.copy(token = "sai-sai-sai-sai"), ring, SatIndexKey.NDVI, "2026-08-01", "2026-09-01")
        assertEquals("Sai mã truy cập máy chủ vệ tinh.", wrong.exceptionOrNull()!!.message)
        val tooLong = SatelliteClient.index(cfg, ring, SatIndexKey.NDVI, "2026-01-01", "2026-09-01")
        assertTrue(tooLong.exceptionOrNull()!!.message!!.contains("93"))
        val swapped = SatelliteClient.index(cfg, ring.map { GeoPoint(it.lng, it.lat) }, SatIndexKey.NDVI, "2026-08-01", "2026-09-01")
        assertTrue(swapped.isFailure)                       // kinh/vĩ bị đảo → máy chủ từ chối
        val big = listOf(GeoPoint(11.0, 107.0), GeoPoint(11.0, 107.2), GeoPoint(11.2, 107.2), GeoPoint(11.2, 107.0))
        assertTrue(SatelliteClient.index(cfg, big, SatIndexKey.NDVI, "2026-08-01", "2026-09-01").exceptionOrNull()!!.message!!.contains("giới hạn"))
        assertTrue(SatelliteClient.index(SatelliteConfig("http://127.0.0.1:1", token), ring, SatIndexKey.NDVI, "2026-08-01", "2026-09-01").exceptionOrNull()!!.message!!.contains("Không kết nối"))
        assertTrue(SatelliteClient.index(SatelliteConfig("http://vwing.example.com", token), ring, SatIndexKey.NDVI, "2026-08-01", "2026-09-01").isFailure)   // http ngoài mạng nội bộ
    }
}
