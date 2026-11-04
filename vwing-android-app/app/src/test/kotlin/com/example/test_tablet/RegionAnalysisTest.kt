package com.example.test_tablet

import com.example.test_tablet.data.vwing.*
import org.junit.Assert.*
import org.junit.Test

class RegionAnalysisTest {
    private val ring = listOf(GeoPoint(11.55,107.8), GeoPoint(11.55,107.801), GeoPoint(11.551,107.801), GeoPoint(11.551,107.8))
    private val boundary = GeoFeature("b", FeatureKind.BOUNDARY, "Ranh vườn", ring)
    @Test fun `zone filter never silently analyzes entire garden`() {
        val d = GardenData(features = listOf(boundary))
        assertEquals(listOf(boundary), RegionAnalysis.regions(d, ""))
        assertTrue(RegionAnalysis.regions(d, "Khu A").isEmpty())
        assertNotNull(RegionAnalysis.problem(null))
        val zone = GeoFeature("z", FeatureKind.ZONE, "Khu A", ring)
        assertEquals(listOf(zone), RegionAnalysis.regions(d.copy(features = listOf(boundary, zone)), "Khu A"))
    }
    @Test fun `invalid and crossed polygons blocked before network`() {
        assertNull(RegionAnalysis.problem(boundary))
        assertNotNull(RegionAnalysis.problem(boundary.copy(points = listOf(ring[0],ring[2],ring[1],ring[3]))))
        assertNotNull(RegionAnalysis.problem(boundary.copy(points = listOf(ring[0],ring[1]))))
        assertNotNull(RegionAnalysis.problem(boundary.copy(points = ring.map { GeoPoint(it.lng, it.lat) })))
        assertNull(RegionAnalysis.problem(boundary.copy(points = ring + ring.first())))
    }
}
