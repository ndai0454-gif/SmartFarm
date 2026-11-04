package com.example.test_tablet

import com.example.test_tablet.data.vwing.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.net.URL

/** Ứng dụng khách GIS với máy chủ vệ tinh thật (mock Earth Engine) có engine RustGIS. Bỏ qua nếu máy không có môi trường Python. */
class GisClientTest {
    private var proc: Process? = null
    private lateinit var cfg: SatelliteConfig

    @Before fun start() {
        val backend = File("E:/New folder/SmartFarm/vwing-satellite-backend")
        val py = File(backend, ".venv/Scripts/python.exe")
        assumeTrue("Không thấy môi trường Python của máy chủ vệ tinh", py.isFile)
        val port = ServerSocket(0).use { it.localPort }
        val pb = ProcessBuilder(py.absolutePath, "-m", "uvicorn", "app.main:app", "--host", "127.0.0.1", "--port", port.toString()).directory(backend).redirectErrorStream(true)
        pb.environment()["VWING_SAT_TOKEN"] = "test-token-123456"; pb.environment()["VWING_EE_MODE"] = "mock"
        proc = try { pb.start() } catch (e: Exception) { null }
        assumeTrue("Không chạy được Python", proc != null)
        cfg = SatelliteConfig("http://127.0.0.1:$port", "test-token-123456")
        val deadline = System.currentTimeMillis() + 15000
        while (System.currentTimeMillis() < deadline) {
            try { if ((URL("${cfg.url}/v1/health").openConnection() as java.net.HttpURLConnection).responseCode == 200) return } catch (_: Exception) { Thread.sleep(200) }
        }
        fail("Máy chủ vệ tinh không khởi động được")
    }

    @After fun stop() { proc?.destroyForcibly() }

    private val ring = listOf(GeoPoint(12.0, 108.0), GeoPoint(12.0, 108.001), GeoPoint(12.001, 108.001), GeoPoint(12.001, 108.0))

    @Test fun `nhap gpx va kml qua may chu roi dung luong nhap san co`() {
        val gpx = """<?xml version="1.0"?><gpx version="1.1" creator="t" xmlns="http://www.topografix.com/GPX/1/1"><wpt lat="12.0005" lon="108.0005"><name>A-01</name></wpt><wpt lat="12.0006" lon="108.0006"><name>A-02</name></wpt></gpx>"""
        val imp = GisClient.importFile(cfg, "cay.gpx", gpx.toByteArray())
        assertEquals(1, imp.layers.size)
        val parsed = Geo.parse(GisClient.mergeLayers(imp.layers))
        assertTrue(parsed.errors.toString(), parsed.errors.isEmpty())
        assertEquals(listOf("A-01", "A-02"), parsed.features.map { it.name })
        assertEquals(Geom.POINT, parsed.features[0].geom)
        assertEquals(12.0005, parsed.features[0].points[0].lat, 1e-9)
        assertTrue(runCatching { GisClient.importFile(cfg, "x.exe", byteArrayOf(1)) }.exceptionOrNull() is GisException)
    }

    @Test fun `xuat kml va phan tich ranh vuon`() {
        val store = VWingStore(File.createTempFile("gis", "d").let { it.delete(); it.mkdirs(); it }, null, false)
        store.applyGeoImport(listOf(GeoFeature(newId(), FeatureKind.BOUNDARY, "Ranh", ring)), emptyList(), "test")
        val kml = GisClient.export(cfg, Geo.exportGeoJson(store.data), "kml", "Vuon A")
        assertTrue(kml.text.contains("<kml")); assertEquals("Vuon_A.kml", kml.filename)
        val r = GisClient.boundary(cfg, SatelliteClient.ringOf(store.data)!!, listOf(10.0), 2.0)
        assertTrue(r.valid); assertEquals(1.2, r.areaHa, 0.15)
        assertTrue(r.buffers.single().areaHa > r.areaHa)
        val bow = listOf(GeoPoint(12.0, 108.0), GeoPoint(12.001, 108.001), GeoPoint(12.0, 108.001), GeoPoint(12.001, 108.0))
        val bad = GisClient.boundary(cfg, bow, emptyList(), null)
        assertFalse(bad.valid); assertTrue(bad.issues.any { it.contains("tự cắt") })
    }

    @Test fun `thong ke raster trong ranh va loi ro rang khi sai`() {
        val asc = "ncols 4\nnrows 4\nxllcorner 108.0\nyllcorner 12.0\ncellsize 0.00025\nNODATA_value -9999\n" +
            listOf("0.2 0.3 0.4 0.5", "0.3 0.4 0.5 0.6", "0.4 0.5 0.6 0.7", "0.5 0.6 0.7 0.8").joinToString("\n")
        val half = listOf(GeoPoint(12.0, 108.0), GeoPoint(12.0, 108.0005), GeoPoint(12.0005, 108.0005), GeoPoint(12.0005, 108.0))
        val z = GisClient.zonal(cfg, "ndvi.asc", asc.toByteArray(), half, null)
        assertEquals(4, z.pixels); assertEquals(0.5, z.mean!!, 1e-6)
        val far = half.map { GeoPoint(it.lat + 1, it.lng + 1) }
        val e = runCatching { GisClient.zonal(cfg, "ndvi.asc", asc.toByteArray(), far, null) }.exceptionOrNull()
        assertTrue(e is GisException && e.message!!.contains("ngoài phạm vi"))
        val wrong = runCatching { GisClient.importFile(SatelliteConfig(cfg.url, "sai-ma"), "a.gpx", ByteArray(3)) }.exceptionOrNull()
        assertTrue(wrong is GisException && wrong.message!!.contains("Sai mã"))
    }

    @Test fun `cat khoet gop hai khu bang rustgis`() {
        fun sq(name: String, lng0: Double, lng1: Double) = GeoFeature(newId(), FeatureKind.ZONE, name, listOf(GeoPoint(12.0, lng0), GeoPoint(12.0, lng1), GeoPoint(12.001, lng1), GeoPoint(12.001, lng0)))
        val a = sq("A", 108.0, 108.001); val b = sq("B", 108.0005, 108.0015)       // chồng lên nửa phải của A
        val clip = GisClient.overlay(cfg, GisClient.Overlay.CLIP, a, b)
        assertEquals(0.6, Geo.polygonAreaHa(clip.single()), 0.1)
        val erase = GisClient.overlay(cfg, GisClient.Overlay.ERASE, a, b)
        assertEquals(0.6, Geo.polygonAreaHa(erase.single()), 0.1)
        val merge = GisClient.overlay(cfg, GisClient.Overlay.MERGE, a, b)
        assertEquals(1.8, Geo.polygonAreaHa(merge.single()), 0.15)
        val apart = GisClient.overlay(cfg, GisClient.Overlay.CLIP, a, sq("C", 109.0, 109.001))
        assertTrue(apart.isEmpty() || apart.sumOf { Geo.polygonAreaHa(it) } < 0.001)
    }
}
