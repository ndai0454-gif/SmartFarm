package com.example.test_tablet

import com.example.test_tablet.data.vwing.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TerrainTest {
    private val n = 40
    private val bounds = GeoBounds(11.0, 107.0, 11.0 + 1200.0 / 111_320.0, 107.0 + 1200.0 / (111_320.0 * Math.cos(Math.toRadians(11.0))))
    private fun dem(mask: BooleanArray = BooleanArray(n * n), f: (r: Int, c: Int) -> Double): Dem {
        val g = ThematicGrid(bounds, n, n, 1200.0, 1200.0, mask)   // ô 30 m
        return Dem(g, DoubleArray(n * n) { f(it / n, it % n) }, "test", false, emptyList(), "")
    }
    private fun centre(r: Int, c: Int) = GeoPoint(bounds.north - (r + .5) * (bounds.north - bounds.south) / n, bounds.west + (c + .5) * (bounds.east - bounds.west) / n)

    // ---------- tải DEM ----------

    private fun json(rows: Int, cols: Int, values: List<Any?>) = JSONObject()
        .put("bounds", JSONObject().put("south", bounds.south).put("west", bounds.west).put("north", bounds.north).put("east", bounds.east))
        .put("rows", rows).put("cols", cols).put("values", JSONArray(values)).put("source", "S").put("simulated", true)
        .put("warnings", JSONArray().put("w")).put("caution", "c")

    @Test fun `parse maps null to NaN keeps metadata and masks the region`() {
        val ring = listOf(centre(10, 10), centre(10, 20), centre(20, 20), centre(20, 10))
        val d = Terrain.parseDem(json(n, n, List(n * n) { if (it == 5) JSONObject.NULL else 100.0 + it % 7 }), ring)
        assertTrue(d.z[5].isNaN()); assertEquals(n * n - 1, d.validCount)
        assertTrue(d.simulated); assertEquals(listOf("w"), d.warnings); assertEquals("S", d.source)
        assertEquals(30.0, d.cellM, 0.5)
        assertTrue(d.grid.inside[d.grid.idx(15, 15)]); assertFalse(d.grid.inside[d.grid.idx(2, 2)])
    }

    @Test fun `parse rejects malformed grids`() {
        assertThrows(IllegalArgumentException::class.java) { Terrain.parseDem(json(n, n, List(10) { 1.0 }), null) }
        assertThrows(IllegalArgumentException::class.java) { Terrain.parseDem(json(2, 2, List(4) { 1.0 }), null) }
        assertThrows(Exception::class.java) { Terrain.parseDem(json(n, n, List(n * n) { 1.0 }).put("bounds", JSONObject().put("south", 1).put("west", 1).put("north", 0).put("east", 2)), null) }
    }

    // ---------- tầm nhìn ----------

    @Test fun `flat ground is fully visible inside the radius and not beyond it`() {
        val d = dem { _, _ -> 100.0 }
        val v = Terrain.viewshed(d, centre(20, 20), 2.0, 0.0, 300.0)!!
        assertEquals(d.grid.idx(20, 20), v.observerCell)
        assertTrue(v.visible[d.grid.idx(20, 25)]); assertTrue(v.visible[d.grid.idx(13, 22)])
        assertFalse(v.visible[d.grid.idx(20, 35)])                      // 450 m > 300 m
        assertEquals(v.analysedCells, v.visibleCells)                   // trong bán kính: thấy hết
    }

    @Test fun `a wall hides the ground behind it, a taller observer sees more, and tall targets stay visible`() {
        val wall = dem { _, c -> if (c == 25) 130.0 else 100.0 }
        val low = Terrain.viewshed(wall, centre(20, 10), 2.0, 0.0, 1000.0)!!
        assertTrue(low.visible[wall.grid.idx(20, 20)])                  // trước tường
        assertFalse(low.visible[wall.grid.idx(20, 28)])                 // sau tường
        assertFalse(low.visible[wall.grid.idx(20, 38)])
        val hiWall = dem { _, c -> if (c == 25) 130.0 else 100.0 }
        val a = Terrain.viewshed(hiWall, centre(20, 10), 2.0, 0.0, 1000.0)!!
        val b = Terrain.viewshed(hiWall, centre(20, 10), 60.0, 0.0, 1000.0)!!
        assertTrue(b.visibleCells > a.visibleCells)
        val tree = Terrain.viewshed(hiWall, centre(20, 10), 2.0, 100.0, 1000.0)!!      // đích cao 100 m vẫn nhìn thấy đỉnh qua tường 30 m
        assertTrue(tree.visible[hiWall.grid.idx(20, 26)])
    }

    @Test fun `viewshed handles observer outside, void observer and void cells`() {
        val d = dem { r, c -> if (r == 20 && c == 24) Double.NaN else 100.0 }
        assertNull(Terrain.viewshed(d, GeoPoint(50.0, 10.0), 2.0, 0.0, 500.0))
        val v = Terrain.viewshed(d, centre(20, 20), 2.0, 0.0, 500.0)!!
        assertFalse(v.visible[d.grid.idx(20, 24)])                      // ô thiếu dữ liệu không được coi là thấy
        assertTrue(v.visible[d.grid.idx(20, 28)])                       // và cũng không chắn
        assertNull(Terrain.viewshed(dem { _, _ -> Double.NaN }, centre(20, 20), 2.0, 0.0, 500.0))
    }

    // ---------- ngập lụt ----------

    // thung lũng chữ V chạy bắc → nam: đáy ở cột 20, thấp dần về phía nam (hàng 39)
    private fun valley() = dem { r, c -> 2.0 * kotlin.math.abs(c - 20) + 0.5 * (39 - r) }

    @Test fun `pits are filled so that every cell drains`() {
        val d = dem { r, c -> 0.5 * (39 - r) - (if (r == 20 && c == 20) 5.0 else 0.0) }
        val (zf, filled) = Terrain.fillDepressions(d)
        assertTrue(filled >= 1)
        assertTrue(zf[d.grid.idx(20, 20)] >= d.at(21, 20))
        assertTrue(zf.indices.all { zf[it] >= d.z[it] })
        val f = Terrain.flood(d)
        assertTrue("nước ở cột có hố phải thoát hết ra biên nam, được ${f.acc[d.grid.idx(39, 20)]}", f.acc[d.grid.idx(39, 20)] >= 40)
    }

    @Test fun `flow accumulates into the valley floor and hand is zero on the channel`() {
        val f = Terrain.flood(valley(), channelHa = 3.0)
        val d = f.dem
        assertTrue(f.acc[d.grid.idx(39, 20)] >= 1500)                   // gần như cả cửa sổ thoát qua đáy ở biên nam
        assertTrue(f.channel[d.grid.idx(30, 20)]); assertFalse(f.channel[d.grid.idx(30, 2)])
        assertEquals(0.0, f.hand[d.grid.idx(30, 20)], 1e-9)
        assertEquals(2.0 * 10, f.hand[d.grid.idx(30, 30)], 1.5)         // 10 ô bên sườn × 2 m/ô
        val twiAlong = (0 until n).maxBy { c -> f.twi[d.grid.idx(35, c)] }
        assertEquals(20, twiAlong)                                      // TWI cao nhất ở đáy
        assertTrue(f.channelAreaHa > 0)
    }

    @Test fun `susceptibility is highest on the valley floor and low on the high slopes`() {
        val f = Terrain.flood(valley())
        val g = f.dem.grid
        assertEquals(FloodClass.VERY_HIGH, f.cls[g.idx(35, 20)])
        assertTrue(f.cls[g.idx(35, 3)]!!.ordinal <= FloodClass.MEDIUM.ordinal)
        assertTrue(f.score[g.idx(35, 20)] > f.score[g.idx(35, 8)])
        assertTrue(f.score.filter { !it.isNaN() }.all { it in 0.0..1.0 })
    }

    @Test fun `void cells get no class and do not crash`() {
        val f = Terrain.flood(dem { r, c -> if (r in 10..12 && c in 10..12) Double.NaN else 0.5 * (39 - r) + 0.1 * c })
        assertNull(f.cls[f.dem.grid.idx(11, 11)])
        assertNotNull(f.cls[f.dem.grid.idx(30, 30)])
    }

    @Test fun `region cells fall back to the centroid cell for tiny regions`() {
        val d = dem { _, _ -> 10.0 }
        val tiny = listOf(centre(20, 20), GeoPoint(centre(20, 20).lat + 1e-6, centre(20, 20).lng), GeoPoint(centre(20, 20).lat, centre(20, 20).lng + 1e-6))
        val (cells, fallback) = Terrain.regionCells(d, tiny)
        assertEquals(listOf(d.grid.idx(20, 20)), cells); assertTrue(fallback)
        val big = dem(BooleanArray(n * n) { it / n in 5..9 && it % n in 5..9 }) { _, _ -> 10.0 }
        val (c2, f2) = Terrain.regionCells(big, tiny)
        assertEquals(25, c2.size); assertFalse(f2)
    }
}
