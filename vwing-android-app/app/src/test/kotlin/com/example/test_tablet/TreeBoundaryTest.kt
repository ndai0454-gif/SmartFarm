package com.example.test_tablet

import com.example.test_tablet.data.vwing.*
import org.junit.Assert.*
import org.junit.Test

class TreeBoundaryTest {
    // vườn ~111 m × 111 m
    private val ring = listOf(GeoPoint(12.0, 108.0), GeoPoint(12.0, 108.001), GeoPoint(12.001, 108.001), GeoPoint(12.001, 108.0))
    private fun tree(code: String, zone: String, lat: Double?, lng: Double?) =
        Tree(id = code, code = code, zone = zone, pos = if (lat != null) GeoPoint(lat, lng!!) else null)

    @Test fun `phan loai cay trong ngoai va chua co vi tri`() {
        val d = GardenData(trees = listOf(
            tree("A1", "A", 12.0005, 108.0005),              // giữa vườn
            tree("A2", "A", 12.0005, 107.99998),             // ~2 m ngoài biên: sai số GPS, vẫn tính trong
            tree("B1", "B", 12.0005, 108.0003 + 0.002),      // ~200 m ngoài
            tree("B2", "B", null, null),
        ))
        val r = TreeBoundary.check(d, ring)
        assertEquals(listOf("A1", "A2"), r.inside.map { it.code })
        assertEquals(listOf("B1"), r.outside.map { it.first.code })
        assertTrue(r.outside[0].second > 100)
        assertEquals(listOf("B2"), r.unplaced.map { it.code })
        val b = r.byZone.first { it.zone == "B" }
        assertEquals(1, b.outside); assertEquals(1, b.unplaced); assertEquals(2, b.total)
        assertEquals(2, r.byZone.first { it.zone == "A" }.inside)
    }

    @Test fun `khoang cach toi canh dung va dung dung sai`() {
        assertEquals(0.0, TreeBoundary.distanceToEdgeM(GeoPoint(12.0, 108.0005), ring), 0.01)
        assertEquals(54.4, TreeBoundary.distanceToEdgeM(GeoPoint(12.0005, 108.0005), ring), 1.0)
        val far = GardenData(trees = listOf(tree("X", "A", 12.0005, 107.99990)))      // ~11 m ngoài
        assertEquals(1, TreeBoundary.check(far, ring).outside.size)
        assertEquals(0, TreeBoundary.check(far, ring, toleranceM = 15.0).outside.size)
    }
}
