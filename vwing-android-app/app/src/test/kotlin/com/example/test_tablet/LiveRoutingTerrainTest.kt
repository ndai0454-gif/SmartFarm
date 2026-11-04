package com.example.test_tablet

import com.example.test_tablet.data.vwing.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Chạy với backend vệ tinh THẬT (Valhalla + SRTM qua Earth Engine). Bỏ qua nếu không đặt VWING_SAT_URL và VWING_SAT_TOKEN, nên không
 * ảnh hưởng bộ test thường. Dùng để chắc app đọc đúng phản hồi thật, không chỉ máy chủ giả.
 */
class LiveRoutingTerrainTest {
    private fun cfg(): SatelliteConfig {
        val url = System.getenv("VWING_SAT_URL").orEmpty(); val token = System.getenv("VWING_SAT_TOKEN").orEmpty()
        assumeTrue("không đặt VWING_SAT_URL/VWING_SAT_TOKEN", url.isNotBlank() && token.isNotBlank())
        return SatelliteConfig(url, token)
    }
    private val ring = listOf(GeoPoint(12.6800, 108.0380), GeoPoint(12.6800, 108.0400), GeoPoint(12.6820, 108.0400), GeoPoint(12.6820, 108.0380))

    @Test fun `isochrone thuc te co ba vong long nhau chua diem xuat phat`() {
        val origin = Place("Tâm vườn", GeoPoint(12.681, 108.039))
        val r = RoutingClient.isochrone(cfg(), listOf(origin), TravelProfile.SCOOTER, listOf(5, 10, 20))
        val cs = r.areas[0].contours
        assertEquals(listOf(5, 10, 20), cs.map { it.minutes })
        assertTrue(cs[0].areaHa < cs[1].areaHa && cs[1].areaHa < cs[2].areaHa)
        assertEquals("Tâm vườn", r.areas[0].name)                      // UTF-8 đi và về nguyên vẹn
        assertNotNull(RoutingClient.minutesTo(r.areas[0], origin.p))
        val raster = RoutingClient.raster(r.areas)!!
        assertTrue(raster.minutes.any { it == 5 } && raster.minutes.any { it == 20 })
        println("LIVE isochrone: " + cs.joinToString { "${it.minutes}p=${it.areaHa.toInt()}ha/${it.ring.size}đỉnh" })
    }

    @Test fun `ma tran thuc te`() {
        val m = RoutingClient.matrix(cfg(), listOf(Place("A", GeoPoint(12.68, 108.038))), listOf(Place("B", GeoPoint(12.70, 108.06)), Place("C", GeoPoint(12.60, 108.00))), TravelProfile.CAR)
        assertTrue(m.minutes[0][0]!! < m.minutes[0][1]!!)              // B gần hơn C
        assertTrue(m.km[0][0]!! in 1.0..10.0)
        println("LIVE matrix: ${m.minutes[0]} ${m.km[0]}")
    }

    @Test fun `DEM thuc te phu vung va cho viewshed va ngap lut chay duoc`() {
        val dem = Terrain.fetchDem(cfg(), ring, 1000)
        assertFalse(dem.simulated)
        assertTrue(dem.rows > 50 && dem.cols > 50)
        assertEquals(dem.z.size, dem.validCount)
        assertTrue(dem.z.min() > 300 && dem.z.max() < 700)             // cao nguyên Buôn Ma Thuột ~ 400–500 m
        assertEquals(30.0, dem.cellM, 1.5)
        val centre = Geo.centroid(ring)!!
        assertNotNull(dem.elevationAt(centre))
        val vs = Terrain.viewshed(dem, centre, 5.0, 0.0, 1000.0)!!
        assertTrue(vs.visibleCells in 1..vs.analysedCells)
        val f = Terrain.flood(dem)
        assertTrue(f.channelAreaHa > 0)
        assertTrue(f.cls.any { it == FloodClass.VERY_HIGH } && f.cls.any { it == FloodClass.LOW })
        println("LIVE DEM ${dem.cols}x${dem.rows} z=${dem.z.min().toInt()}..${dem.z.max().toInt()} visible=${vs.visibleCells}/${vs.analysedCells} channel=${f.channelAreaHa}ha")
    }
}
