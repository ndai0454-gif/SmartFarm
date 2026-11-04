package com.example.test_tablet

import com.example.test_tablet.data.vwing.*
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

class RoutingTest {
    private fun sq(lat: Double, lng: Double, d: Double) = listOf(GeoPoint(lat - d, lng - d), GeoPoint(lat - d, lng + d), GeoPoint(lat + d, lng + d), GeoPoint(lat + d, lng - d), GeoPoint(lat - d, lng - d))
    private fun ringJson(r: List<GeoPoint>) = JSONArray().also { a -> r.forEach { a.put(JSONArray().put(it.lng).put(it.lat)) } }
    private fun isoJson(lat: Double = 11.0, lng: Double = 107.0) = JSONObject().put("profile", "motor_scooter").put("attribution", "© OSM").put("caution", "ước tính")
        .put("origins", JSONArray().put(JSONObject().put("name", "Chợ").put("lat", lat).put("lng", lng).put("contours", JSONArray()
            .put(JSONObject().put("minutes", 5).put("ring", ringJson(sq(lat, lng, .005))).put("area_ha", 100.0))
            .put(JSONObject().put("minutes", 15).put("ring", ringJson(sq(lat, lng, .02))).put("area_ha", 1600.0))
            .put(JSONObject().put("minutes", 10).put("ring", ringJson(sq(lat, lng, .01))).put("area_ha", 400.0)))))

    private fun withServer(handler: (HttpExchange) -> Unit, block: (SatelliteConfig) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0); server.createContext("/", handler); server.start()
        try { block(SatelliteConfig("http://127.0.0.1:${server.address.port}", "private-test-token")) } finally { server.stop(0) }
    }
    private fun send(e: HttpExchange, code: Int, body: String) { val b = body.toByteArray(); e.sendResponseHeaders(code, b.size.toLong()); e.responseBody.use { it.write(b) }; e.close() }

    @Test fun `isochrone parse sorts contours and keeps names`() {
        val r = RoutingClient.parseIsochrone(isoJson())
        assertEquals(TravelProfile.SCOOTER, r.profile)
        assertEquals(listOf(5, 10, 15), r.areas[0].contours.map { it.minutes })
        assertEquals("Chợ", r.areas[0].name)
    }

    @Test fun `isochrone client sends the contract the backend expects and surfaces server errors`() {
        val seen = AtomicReference<JSONObject>(); val auth = AtomicReference<String>()
        withServer({ e ->
            auth.set(e.requestHeaders.getFirst("Authorization")); seen.set(JSONObject(e.requestBody.readBytes().decodeToString()))
            if (e.requestURI.path == "/v1/routing/isochrone") send(e, 200, isoJson().toString()) else send(e, 404, "{}")
        }) { cfg ->
            val r = RoutingClient.isochrone(cfg, listOf(Place("Chợ", GeoPoint(11.0, 107.0))), TravelProfile.SCOOTER, listOf(15, 5, 10, 5))
            assertEquals(3, r.areas[0].contours.size)
            assertEquals("Bearer private-test-token", auth.get())
            assertEquals("motor_scooter", seen.get().getString("profile"))
            assertEquals(listOf(5, 10, 15), (0 until 3).map { seen.get().getJSONArray("minutes").getInt(it) })
            assertEquals(11.0, seen.get().getJSONArray("origins").getJSONObject(0).getDouble("lat"), 0.0)
        }
        withServer({ e -> send(e, 422, """{"detail":"Vượt giới hạn của máy chủ đường đi."}""") }) { cfg ->
            val ex = assertThrows(GisException::class.java) { RoutingClient.isochrone(cfg, listOf(Place("A", GeoPoint(1.0, 1.0))), TravelProfile.CAR, listOf(5)) }
            assertTrue(ex.message!!.contains("Vượt giới hạn"))
        }
    }

    @Test fun `client validates before touching the network`() {
        val cfg = SatelliteConfig("http://127.0.0.1:9", "private-test-token")
        val p = Place("A", GeoPoint(1.0, 1.0))
        for (bad in listOf(emptyList(), listOf(0), listOf(61), listOf(1, 2, 3, 4, 5))) assertThrows(GisException::class.java) { RoutingClient.isochrone(cfg, listOf(p), TravelProfile.CAR, bad) }
        assertThrows(GisException::class.java) { RoutingClient.isochrone(cfg, emptyList(), TravelProfile.CAR, listOf(5)) }
        assertThrows(GisException::class.java) { RoutingClient.isochrone(cfg, List(6) { p }, TravelProfile.CAR, listOf(5)) }
        assertThrows(GisException::class.java) { RoutingClient.matrix(cfg, List(11) { p }, listOf(p), TravelProfile.CAR) }
    }

    @Test fun `matrix parse keeps unreachable pairs as null`() {
        val o = JSONObject().put("profile", "auto").put("attribution", "x")
            .put("minutes", JSONArray("[[10.0,null],[3.5,7.0]]")).put("km", JSONArray("[[4.2,null],[1.1,2.9]]"))
        val m = RoutingClient.parseMatrix(o, 2, 2)
        assertNull(m.minutes[0][1]); assertEquals(3.5, m.minutes[1][0]!!, 0.0); assertEquals(2.9, m.km[1][1]!!, 0.0)
        assertThrows(Exception::class.java) { RoutingClient.parseMatrix(o, 3, 2) }
    }

    @Test fun `raster paints the smallest containing contour and leaves the outside empty`() {
        val a = RoutingClient.parseIsochrone(isoJson()).areas
        val r = RoutingClient.raster(a, 128)!!
        fun at(lat: Double, lng: Double): Int {
            val c = ((lng - r.bounds.west) / (r.bounds.east - r.bounds.west) * r.cols).toInt().coerceIn(0, r.cols - 1)
            val row = ((r.bounds.north - lat) / (r.bounds.north - r.bounds.south) * r.rows).toInt().coerceIn(0, r.rows - 1)
            return r.minutes[row * r.cols + c]
        }
        assertEquals(5, at(11.0, 107.0)); assertEquals(10, at(11.0, 107.0 + .008)); assertEquals(15, at(11.0, 107.0 + .015))
        assertEquals(0, at(11.0 + .0205, 107.0 + .0205))       // trong khung đệm nhưng ngoài mọi vòng
        assertNull(RoutingClient.raster(emptyList()))
    }

    @Test fun `raster of several origins takes the minimum minutes`() {
        val a = RoutingClient.parseIsochrone(isoJson(11.0, 107.0)).areas + RoutingClient.parseIsochrone(isoJson(11.0, 107.03)).areas
        val r = RoutingClient.raster(a, 160)!!
        assertTrue(r.minutes.any { it == 5 }); assertEquals(5, RoutingClient.minutesTo(a, GeoPoint(11.0, 107.03)))
        assertEquals(15, RoutingClient.minutesTo(a, GeoPoint(11.0, 107.015)))   // giữa hai điểm: chỉ vòng 15 phút phủ tới
        assertNull(RoutingClient.minutesTo(a, GeoPoint(12.0, 107.0)))
    }

    @Test fun `coverage counts trees and garden area per band`() {
        val a = RoutingClient.parseIsochrone(isoJson()).areas
        val trees = listOf(TreeSample(GeoPoint(11.0, 107.001), null), TreeSample(GeoPoint(11.0, 107.008), null), TreeSample(GeoPoint(11.0, 107.03), null))
        val (byBand, outside) = RoutingClient.treeCoverage(a, trees, listOf(5, 10, 15))
        assertEquals(mapOf(5 to 1, 10 to 2, 15 to 2), byBand); assertEquals(1, outside)
        val garden = sq(11.0, 107.0, .002)                                     // nằm trọn trong vòng 5 phút
        val cov = RoutingClient.areaCoverage(a, garden, listOf(5, 10))!!
        assertEquals(100.0, cov[5]!!, 0.01)
        val far = sq(11.0, 107.0 + .016, .002)                                 // chỉ trong vòng 15 phút
        val cov2 = RoutingClient.areaCoverage(a, far, listOf(5, 10, 15))!!
        assertEquals(0.0, cov2[10]!!, 0.01); assertEquals(100.0, cov2[15]!!, 0.01)
    }

    @Test fun `od zones weight by fruits when recorded and flows go to the quickest destination`() {
        val ring = sq(11.0, 107.0, .01)
        val d = GardenData(trees = listOf(
            Tree("1", "A1", "Khu A", fruits = 100, pos = GeoPoint(11.001, 107.001)), Tree("2", "A2", "Khu A", fruits = 50, pos = GeoPoint(11.003, 107.003)),
            Tree("3", "B1", "Khu B", fruits = 30, pos = GeoPoint(10.995, 106.995)),
            Tree("4", "C1", "Khu C", fruits = 5, pos = null), Tree("5", "D1", "Khu D", fruits = 5, pos = GeoPoint(20.0, 120.0)),
        ))
        val z = OdFlow.zones(d, ring)
        assertEquals("quả", z.unit); assertEquals(listOf("Khu A", "Khu B"), z.nodes.map { it.name }); assertEquals(150.0, z.nodes[0].weight, 0.0)
        assertEquals(2, z.skippedZones)                                         // C (chưa có toạ độ), D (ngoài ranh)
        assertEquals(11.002, z.nodes[0].p.lat, 1e-9)
        val dests = listOf(Place("Chợ", GeoPoint(11.1, 107.1)), Place("Kho", GeoPoint(10.9, 106.9)))
        val m = OdMatrix(TravelProfile.CAR, listOf(listOf(20.0, 35.0), listOf(null, null)), listOf(listOf(10.0, 18.0), listOf(null, null)), "")
        val edges = OdFlow.allocate(z.nodes, dests, m)
        assertEquals(1, edges.size)                                             // Khu B không tới được điểm nào → bỏ
        assertEquals("Chợ", edges[0].to.name); assertEquals(20.0, edges[0].minutes, 0.0); assertEquals(150.0, edges[0].weight, 0.0)
    }

    @Test fun `od falls back to tree counts when no fruit data`() {
        val ring = sq(11.0, 107.0, .01)
        val d = GardenData(trees = listOf(Tree("1", "A1", "A", pos = GeoPoint(11.0, 107.0)), Tree("2", "A2", "A", pos = GeoPoint(11.001, 107.0)), Tree("3", "B1", "B", pos = GeoPoint(11.0, 107.002))))
        val z = OdFlow.zones(d, ring)
        assertEquals("cây", z.unit); assertEquals(listOf(2.0, 1.0), z.nodes.map { it.weight })
    }

    @Test fun `place store round trips and ignores corrupt entries and the cap`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "places-" + System.nanoTime()).apply { mkdirs() }
        try {
            val s = PlaceStore(dir)
            assertTrue(s.load().isEmpty())
            s.save(List(12) { Place("P$it", GeoPoint(11.0 + it * .01, 107.0)) })
            assertEquals(PlaceStore.MAX, s.load().size)
            File(dir, "vwing_places.json").writeText("""[{"name":"ok","lat":11,"lng":107},{"name":"","lat":1,"lng":1},{"name":"bad","lat":999,"lng":1},{"lat":1}]""")
            assertEquals(listOf("ok"), s.load().map { it.name })
            File(dir, "vwing_places.json").writeText("không phải json")
            assertTrue(s.load().isEmpty())
        } finally { dir.deleteRecursively() }
    }
}
