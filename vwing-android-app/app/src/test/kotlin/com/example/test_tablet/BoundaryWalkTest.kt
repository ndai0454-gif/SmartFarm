package com.example.test_tablet

import com.example.test_tablet.data.vwing.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos

class BoundaryWalkTest {
    private val lat0 = 12.68; private val lng0 = 108.04
    private val mLat = 1.0 / 111_320.0
    private val mLng = 1.0 / (111_320.0 * cos(Math.toRadians(lat0)))

    /** Đi bộ quanh hình chữ nhật w×h mét, mỗi [step] m một điểm, nhiễu cố định ±1 m. */
    private fun walk(w: Double, h: Double, step: Double = 5.0, acc: Float = 4f, closeBack: Boolean = true): List<BoundaryWalk.Fix> {
        val pts = mutableListOf<Pair<Double, Double>>()
        var x = 0.0; var y = 0.0
        while (x < w) { pts += x to y; x += step }
        x = w; while (y < h) { pts += x to y; y += step }
        y = h; while (x > 0) { pts += x to y; x -= step }
        x = 0.0; while (y > (if (closeBack) 0.0 else 70.0)) { pts += x to y; y -= step }
        return pts.mapIndexed { i, (px, py) ->
            val n = if (i % 2 == 0) 0.8 else -0.8
            BoundaryWalk.Fix(GeoPoint(lat0 + (py + n) * mLat, lng0 + (px - n) * mLng), acc, i * 1000L)
        }
    }

    @Test fun `di bo quanh vuon 1 ha cho dien tich dung trong sai so gps`() {
        val r = BoundaryWalk.build(walk(100.0, 100.0))
        assertTrue(r.error, r.ok)
        assertEquals(1.0, r.areaHa, 0.08)                    // nhiễu ±1 m trên chu vi 400 m
        assertEquals(4.0, r.meanAccM!!, 0.01)
        assertTrue("đã lược bớt điểm thẳng hàng", r.polygon.size < 20)
        assertTrue(r.areaErrHa!! > 0.1)                      // sai số công bố cỡ chu vi × 4 m = 0,16 ha
        assertFalse(Geo.selfIntersects(r.polygon))
    }

    @Test fun `diem sai so lon va dung yen bi bo`() {
        val good = walk(60.0, 60.0)
        val noisy = good.flatMap { listOf(it, BoundaryWalk.Fix(GeoPoint(it.p.lat + 0.001, it.p.lng), 80f), BoundaryWalk.Fix(it.p, 3f)) }   // điểm xấu + điểm trùng
        val r = BoundaryWalk.build(noisy)
        assertTrue(r.error, r.ok)
        assertEquals(good.size, r.kept)
        assertEquals(0.36, r.areaHa, 0.06)
        assertFalse(BoundaryWalk.accept(null, BoundaryWalk.Fix(GeoPoint(1.0, 1.0), null)))   // không rõ sai số thì không tin
    }

    @Test fun `chua di het vong hoac qua it diem thi bao loi ro rang`() {
        val open = BoundaryWalk.build(walk(100.0, 100.0, closeBack = false))
        assertFalse(open.ok); assertTrue(open.error!!.contains("quay lại"))
        val few = BoundaryWalk.build(walk(100.0, 100.0).take(2))
        assertFalse(few.ok); assertTrue(few.error!!.contains("ít nhất"))
        val bad = BoundaryWalk.build(walk(100.0, 100.0, acc = 40f))
        assertFalse(bad.ok)
    }

    @Test fun `gps yeu thi canh bao dien tich chi tham khao`() {
        val r = BoundaryWalk.build(walk(100.0, 100.0, acc = 12f))
        assertTrue(r.ok); assertTrue(r.warnings.any { it.contains("tham khảo") })
    }
}
