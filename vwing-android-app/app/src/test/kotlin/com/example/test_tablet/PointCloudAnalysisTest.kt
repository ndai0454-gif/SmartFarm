package com.example.test_tablet

import com.example.test_tablet.data.vwing.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PointCloudAnalysisTest {
    private val ring = listOf(GeoPoint(10.0, 105.0), GeoPoint(10.0, 105.002), GeoPoint(10.002, 105.002), GeoPoint(10.002, 105.0))
    private fun csv(s: String, polygon: List<GeoPoint> = ring, crs: CloudCrs = CloudCrs.WGS84) =
        PointCloudAnalysis.analyze(s.byteInputStream(), CloudFormat.CSV, crs, polygon)
    private fun rejects(fragment: String, block: () -> Unit) {
        try { block(); fail("Expected rejection containing $fragment") }
        catch (e: IllegalArgumentException) { assertTrue("${e.message} does not contain $fragment", e.message.orEmpty().contains(fragment)) }
    }

    @Test fun `clips outside points and keeps boundary vertices and edges with closed ring`() {
        val report = csv("x,y,z,classification\n105.001,10.001,20,2\n105,10,10,5\n105.002,10.001,30,2\n106,11,900,2", ring + ring.first())
        assertEquals(4, report.totalPoints); assertEquals(3, report.inRegion); assertEquals(2, report.groundPoints)
        assertEquals(10.0, report.minZ, 0.0); assertEquals(30.0, report.maxZ, 0.0)
        assertEquals(20.0, report.meanZ, 0.0); assertEquals(30.0, report.p95Z, 0.0)
        assertEquals(4.870588, report.areaHa, 0.001)
        assertEquals(3 / (report.areaHa * 10000), report.density, 1e-12)
        assertEquals(3, report.preview.size)
    }

    @Test fun `concave polygon excludes points within bbox but outside shape`() {
        val l = listOf(GeoPoint(10.0, 105.0), GeoPoint(10.0, 105.002), GeoPoint(10.001, 105.002),
            GeoPoint(10.001, 105.001), GeoPoint(10.002, 105.001), GeoPoint(10.002, 105.0))
        val report = csv("105.0005 10.0015 10\n105.0015 10.0015 99", l)
        assertEquals(2, report.totalPoints); assertEquals(1, report.inRegion); assertEquals(10.0, report.maxZ, 0.0)
    }

    @Test fun `semicolon whitespace BOM and optional header parse consistently`() {
        assertEquals(2, csv("\uFEFFx;y;z\n105.001;10.001;4\n105.001;10.001;8").inRegion)
        assertNull(csv("105.001\t10.001\t4").preview.single().classification)
        assertEquals(2, csv("x y z classification\n105.001 10.001 4 2").preview.single().classification)
    }

    @Test fun `UTM inverse matches independent PROJ reference in both zones`() {
        // Reference generated with pyproj 3.8 / PROJ from EPSG:4326, always_xy=True.
        val cases = listOf(
            Triple(CloudCrs.UTM48N, "685894.8201317384,1188851.5510017164,42", GeoPoint(10.75, 106.7)),
            Triple(CloudCrs.UTM49N, "304206.5525367919,1354851.1085515888,42", GeoPoint(12.25, 109.2))
        )
        for ((crs, row, expected) in cases) {
            val region = listOf(GeoPoint(expected.lat - 0.001, expected.lng - 0.001), GeoPoint(expected.lat - 0.001, expected.lng + 0.001),
                GeoPoint(expected.lat + 0.001, expected.lng + 0.001), GeoPoint(expected.lat + 0.001, expected.lng - 0.001))
            val p = csv(row, region, crs).preview.single()
            assertEquals(expected.lat, p.position.lat, 1e-7); assertEquals(expected.lng, p.position.lng, 1e-7)
            assertEquals(42.0, p.z, 0.0)
        }
    }

    @Test fun `wrong CRS or empty selected region cannot produce invented results`() {
        rejects("WGS84") { csv("685894.82,1188851.55,42") }
        rejects("UTM") { csv("105.001,10.001,42", crs = CloudCrs.UTM48N) }
        rejects("Không có điểm") { csv("106,11,42") }
        rejects("không chứa điểm") { csv("x,y,z") }
    }

    @Test fun `malformed numeric fields header and classifications rejected`() {
        for (row in listOf("105.001,10.001,NaN", "105.001,Infinity,42", "bad,10.001,42", "105.001,10.001,"))
            rejects("hữu hạn") { csv(row) }
        rejects("số cột") { csv("x,y,z\n105.001,10.001,42,2") }
        rejects("Tiêu đề") { csv("x,z,y\n105.001,42,10.001") }
        for (cls in listOf("2.5", "256", "-1", "", "NaN"))
            rejects("classification") { csv("x,y,z,classification\n105.001,10.001,42,$cls") }
        rejects("UTF-8") { PointCloudAnalysis.analyze(ByteArrayInputStream(byteArrayOf(0xc3.toByte(), 0x28)), CloudFormat.CSV, CloudCrs.WGS84, ring) }
    }

    @Test fun `invalid self crossing and degenerate polygons rejected`() {
        val row = "105.001,10.001,42"
        rejects("3–512") { csv(row, ring.take(2)) }
        rejects("3–512") { csv(row, ring + ring[1]) }
        rejects("tự cắt") { csv(row, listOf(ring[0], ring[2], ring[1], ring[3])) }
        rejects("diện tích") { csv(row, listOf(GeoPoint(10.0, 105.0), GeoPoint(10.0, 105.001), GeoPoint(10.0, 105.002))) }
        rejects("tọa độ") { csv(row, ring.dropLast(1) + GeoPoint(Double.NaN, 105.0)) }
    }

    @Test fun `statistics use all points and preview is bounded evenly across source order`() {
        val rows = (1..4001).joinToString("\n") { "105.001,10.001,$it" }
        val report = csv(rows)
        assertEquals(4001, report.inRegion); assertEquals(2000, report.preview.size)
        assertEquals(2001.0, report.meanZ, 1e-9); assertEquals(3801.0, report.p95Z, 0.0)
        assertEquals(1.0, report.preview.first().z, 0.0); assertEquals(4001.0, report.preview.last().z, 0.0)
    }

    @Test fun `input byte and point limits enforced without trusting metadata`() {
        val overLimit = object : InputStream() {
            var left = PointCloudAnalysis.MAX_BYTES + 1
            override fun read(): Int = if (left-- > 0) 32 else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (left <= 0) return -1
                val n = minOf(left, len); b.fill(32, off, off + n); left -= n; return n
            }
        }
        rejects("20 MB") { PointCloudAnalysis.analyze(overLimit, CloudFormat.CSV, CloudCrs.WGS84, ring) }
        val rows = "105.001,10.001,1\n".repeat(PointCloudAnalysis.MAX_POINTS + 1)
        rejects("250.000") { csv(rows) }
    }

    private fun fixture(format: Int = 0, minor: Int = 2, classes: List<Int> = listOf(2)): ByteArray {
        val headerSize = when (minor) { 4 -> 375; 3 -> 235; else -> 227 }
        val recordSize = intArrayOf(20, 28, 26, 34, 57, 63, 30, 36, 38, 59, 67)[format]
        val bytes = ByteArray(headerSize + recordSize * classes.size)
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        b.put(0, 76); b.put(1, 65); b.put(2, 83); b.put(3, 70)
        b.put(24, 1); b.put(25, minor.toByte()); b.putShort(94, headerSize.toShort())
        b.putInt(96, headerSize); b.put(104, format.toByte()); b.putShort(105, recordSize.toShort())
        if (minor == 4) b.putLong(247, classes.size.toLong()) else b.putInt(107, classes.size)
        for (axis in 0..2) b.putDouble(131 + axis * 8, if (axis == 2) 0.5 else 0.000001)
        b.putDouble(155, 105.0); b.putDouble(163, 10.0); b.putDouble(171, 100.0)
        classes.forEachIndexed { i, cls ->
            val p = headerSize + i * recordSize
            b.putInt(p, 1000); b.putInt(p + 4, 1000); b.putInt(p + 8, i * 10 - 20)
            b.put(p + if (format >= 6) 16 else 15, cls.toByte())
        }
        return bytes
    }
    private fun las(bytes: ByteArray) = PointCloudAnalysis.analyze(bytes.inputStream(), CloudFormat.LAS, CloudCrs.WGS84, ring)

    @Test fun `LAS scale offsets negative integer Z and legacy class flags honored`() {
        val report = las(fixture(classes = listOf(2 or 32, 5, 2 or 128)))
        assertEquals(3, report.totalPoints); assertEquals(2, report.inRegion); assertEquals(1, report.groundPoints)
        assertEquals(90.0, report.minZ, 0.0); assertEquals(95.0, report.maxZ, 0.0)
        assertEquals(92.5, report.meanZ, 0.0)
        assertEquals(105.001, report.preview.first().position.lng, 1e-10)
        assertEquals(10.001, report.preview.first().position.lat, 1e-10)
    }

    @Test fun `LAS14 extended count and modern class byte preserve values above 31`() {
        val bytes = fixture(format = 6, minor = 4, classes = listOf(2, 64, 2))
        bytes[375 + 2 * 30 + 15] = 4 // Modern withheld flag, separate from classification.
        val report = las(bytes)
        assertEquals(3, report.totalPoints); assertEquals(2, report.inRegion); assertEquals(1, report.groundPoints)
        assertEquals(64, report.preview.last().classification)
    }

    @Test fun `every supported LAS point format reads coordinates and class at correct offset`() {
        for (format in 0..10) {
            val report = las(fixture(format = format, minor = 4))
            assertEquals("format $format", 1, report.groundPoints)
            assertEquals("format $format", 90.0, report.minZ, 0.0)
        }
        assertEquals(1, las(fixture(format = 1, minor = 1)).inRegion)
        assertEquals(1, las(fixture(format = 5, minor = 3)).inRegion)
    }

    @Test fun `LAS rejects compression truncation unsupported format and corrupt scales`() {
        rejects("nén") { las(fixture().also { it[104] = 128.toByte() }) }
        rejects("thiếu bản ghi") { las(fixture().dropLast(1).toByteArray()) }
        rejects("ngắn hơn") { las(fixture().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putShort(105, 19) }) }
        rejects("không được hỗ trợ") { las(fixture().also { it[104] = 11 }) }
        rejects("tỉ lệ") { las(fixture().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putDouble(131, Double.NaN) }) }
        rejects("250.000") { las(fixture().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(107, PointCloudAnalysis.MAX_POINTS + 1) }) }
        rejects("LASF") { las(fixture().also { it[0] = 0 }) }
    }

    @Test fun `LAS VLR bounds and LASzip VLR detection protect uncompressed parser`() {
        fun withVlr(user: String, declaredLength: Int): ByteArray {
            val original = fixture()
            val bytes = ByteArray(original.size + 54)
            original.copyInto(bytes, 0, 0, 227); original.copyInto(bytes, 281, 227)
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            b.putInt(96, 281); b.putInt(100, 1)
            user.toByteArray().copyInto(bytes, 229); b.putShort(247, declaredLength.toShort())
            return bytes
        }
        rejects("nén") { las(withVlr("laszip encoded", 0)) }
        rejects("VLR") { las(withVlr("other", 100)) }
        assertEquals(1, las(withVlr("other", 0)).inRegion)
    }
}
