package com.example.test_tablet

import com.example.test_tablet.data.vwing.*
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class RegionWorkflowTest {
    private val ring = listOf(GeoPoint(11.0, 107.0), GeoPoint(11.001, 107.0), GeoPoint(11.001, 107.001), GeoPoint(11.0, 107.001))
    private fun period(start: String, end: String) = JSONObject().put("start", start).put("end_exclusive", end).put("scene_count", 1).put("scene_ids", JSONArray().put("scene-1"))
        .put("valid_coverage_pct", 90.0).put("indices", JSONArray().also { a -> listOf("NDVI", "EVI", "NDMI").forEach { index ->
            a.put(JSONObject().put("index", index).put("mean", 0.4).put("p5", 0.2).put("p95", 0.6).put("valid_coverage_pct", 90.0).put("nominal_pixel_size_m", if (index == "NDMI") 20 else 10))
        } })
    private fun response() = JSONObject().put("dataset", "COPERNICUS/S2_SR_HARMONIZED").put("source", "Earth Engine").put("simulated", false).put("area_ha", 1.2)
        .put("bounds", JSONObject().put("south", 11.0).put("west", 107.0).put("north", 11.001).put("east", 107.001))
        .put("baseline", period("2026-07-01", "2026-08-01")).put("comparison", period("2026-08-01", "2026-09-01"))
        .put("change", JSONObject().put("index", "NDVI").put("threshold", 0.1).put("mean_delta", -0.02).put("common_valid_coverage_pct", 80.0).put("common_valid_area_ha", 0.96)
            .put("increase_ha", 0.2).put("stable_ha", 0.5).put("decrease_ha", 0.26).put("nominal_pixel_size_m", 10).put("image_url", "https://earthengine.googleapis.com/image?token=temporary")
            .put("legend", JSONObject().put("min", -1).put("max", 1).put("palette", JSONArray().put("b2182b").put("f7f7f7").put("1a9850")).put("label", "ΔNDVI (kỳ so sánh − kỳ nền)")))
        .put("method", "Masked median; difference on common mask").put("warnings", JSONArray().put("Vùng nhỏ có ít pixel độc lập")).put("caution", "Không chẩn đoán bệnh từng cây")

    private fun withServer(handler: (HttpExchange) -> Unit, block: (SatelliteConfig) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0); server.createContext("/", handler); server.start()
        try { block(SatelliteConfig("http://127.0.0.1:${server.address.port}", "private-test-token")) } finally { server.stop(0) }
    }
    private fun send(e: HttpExchange, code: Int, body: String) { val b = body.toByteArray(); e.sendResponseHeaders(code, b.size.toLong()); e.responseBody.use { it.write(b) }; e.close() }
    private fun analyze(cfg: SatelliteConfig) = RegionWorkflowClient.analyze(cfg, ring, "2026-07-01", "2026-08-01", "2026-08-01", "2026-09-01")

    @Test fun `valid response preserves source resolution provenance and common-mask area`() {
        val r = RegionWorkflowClient.parse(response())
        assertEquals(listOf(10, 10, 20), r.baseline.indices.map { it.nominalPixelSizeM })
        assertEquals(1, r.comparison.sceneCount); assertEquals(0.96, r.change.commonValidAreaHa!!, 0.0)
        assertEquals(-0.02, r.change.meanDelta!!, 0.0); assertFalse(r.simulated)
        assertEquals(r.areaHa, RegionWorkflowClient.parse(JSONObject(r.rawJson)).areaHa, 0.0)
    }

    @Test fun `no valid pixels retain null statistics rather than healthy zero`() {
        val payload = response()
        for (name in listOf("baseline", "comparison")) {
            val p = payload.getJSONObject(name).put("scene_count", 0).put("scene_ids", JSONArray()).put("valid_coverage_pct", 0)
            for (i in 0..2) p.getJSONArray("indices").getJSONObject(i).apply { for (key in listOf("mean", "p5", "p95")) put(key, JSONObject.NULL); put("valid_coverage_pct", 0) }
        }
        payload.getJSONObject("change").apply {
            for (key in listOf("mean_delta", "common_valid_area_ha", "increase_ha", "stable_ha", "decrease_ha", "image_url")) put(key, JSONObject.NULL)
            put("common_valid_coverage_pct", 0)
        }
        val r = RegionWorkflowClient.parse(payload)
        assertNull(r.baseline.indices.first().mean); assertNull(r.change.increaseHa); assertEquals(0.0, r.change.commonValidCoveragePct!!, 0.0)
        assertTrue(RegionWorkflowReport.html(r, "Vườn", ring).contains("Chưa có dữ liệu"))
    }

    @Test fun `incomplete malformed and scientifically inconsistent payloads are rejected`() {
        val cases = listOf(
            response().apply { remove("caution") },
            response().apply { getJSONObject("bounds").put("south", 12) },
            response().apply { getJSONObject("baseline").getJSONArray("indices").remove(2) },
            response().apply { getJSONObject("baseline").getJSONArray("indices").getJSONObject(0).put("mean", "0.4") },
            response().apply { getJSONObject("comparison").put("scene_count", 2) },
            response().apply { getJSONObject("change").put("stable_ha", 0.8) },
            response().apply { getJSONObject("change").put("mean_delta", JSONObject.NULL) },
            response().apply { getJSONObject("change").put("nominal_pixel_size_m", 1) },
            response().apply { getJSONObject("baseline").getJSONArray("indices").getJSONObject(2).put("nominal_pixel_size_m", 10) }
        )
        cases.forEach { assertTrue(it.toString(), runCatching { RegionWorkflowClient.parse(it) }.isFailure) }
    }

    @Test fun `input rejects invalid dates overlap oversized periods and nonfinite thresholds`() {
        assertNull(RegionWorkflowClient.inputProblem("2026-07-01", "2026-08-01", "2026-08-01", "2026-09-01"))
        for (dates in listOf(listOf("2026-02-30", "2026-03-01", "2026-04-01", "2026-05-01"), listOf("2026-01-01", "2026-06-01", "2026-07-01", "2026-08-01"), listOf("2026-07-01", "2026-08-01", "2026-07-30", "2026-08-30")))
            assertNotNull(RegionWorkflowClient.inputProblem(dates[0], dates[1], dates[2], dates[3]))
        assertNotNull(RegionWorkflowClient.inputProblem("2026-07-01", "2026-08-01", "2026-08-01", "2026-09-01", Double.NaN))
        assertNotNull(RegionWorkflowClient.inputProblem("2026-07-01", "2026-08-01", "2026-08-01", "2026-09-01", threshold = 0.0))
        assertNotNull(RegionWorkflowClient.inputProblem("2026-07-01", "2026-08-01", "2026-08-01", "2026-09-01", threshold = 1.01))
        assertNotNull(RegionWorkflowClient.inputProblem("2026-07-01", "2026-08-01", "2026-08-01", "2026-09-01", threshold = 0.009))
    }

    @Test fun `HTTP request sends correct periods lnglat cloud and authorization`() {
        val request = AtomicReference<JSONObject>(); val auth = AtomicReference<String>(); val path = AtomicReference<String>()
        withServer({ e -> request.set(JSONObject(e.requestBody.bufferedReader().readText())); auth.set(e.requestHeaders.getFirst("Authorization")); path.set(e.requestURI.path); send(e, 200, response().toString()) }) { cfg ->
            assertTrue(analyze(cfg).isSuccess)
            assertEquals("Bearer private-test-token", auth.get()); assertEquals("/v1/satellite/region-workflow", path.get())
            assertEquals(107.0, request.get().getJSONObject("region").getJSONArray("ring").getJSONArray(0).getDouble(0), 0.0)
            assertEquals("2026-08-01", request.get().getString("comparison_start")); assertEquals(80.0, request.get().getDouble("max_cloud_pct"), 0.0)
        }
    }

    @Test fun `redirect is not followed and errors never leak token or server detail`() {
        val targets = AtomicInteger()
        withServer({ e -> if (e.requestURI.path == "/leak") targets.incrementAndGet(); e.responseHeaders.set("Location", "/leak"); send(e, 302, "redirect") }) { cfg ->
            val result = analyze(cfg); assertTrue(result.isFailure); assertTrue(result.exceptionOrNull()!!.message!!.contains("chuyển hướng")); assertEquals(0, targets.get())
        }
        withServer({ e -> send(e, 401, "private-test-token arbitrary server detail") }) { cfg ->
            val message = analyze(cfg).exceptionOrNull()!!.message!!; assertTrue(message.contains("Mã truy cập")); assertFalse(message.contains("private-test-token"))
        }
    }

    @Test fun `bounded HTTP response and invalid region fail without successful parsing`() {
        withServer({ e -> send(e, 200, "x".repeat(1024 * 1024 + 1)) }) { cfg -> assertTrue(analyze(cfg).exceptionOrNull()!!.message!!.contains("1 MB")) }
        val calls = AtomicInteger()
        withServer({ e -> calls.incrementAndGet(); send(e, 200, response().toString()) }) { cfg ->
            val invalid = RegionWorkflowClient.analyze(cfg, ring.take(2), "2026-07-01", "2026-08-01", "2026-08-01", "2026-09-01")
            assertTrue(invalid.isFailure); assertEquals(0, calls.get())
        }
    }

    @Test fun `report escapes untrusted text embeds editable SVG and strips transient URLs`() {
        val payload = response().put("simulated", true).put("source", "<script>alert(1)</script>").put("method", "<img src=x onerror=alert(1)>").put("token", "private-test-token")
        val r = RegionWorkflowClient.parse(payload); val html = RegionWorkflowReport.html(r, "<b>Vườn & cây</b>", ring)
        assertFalse(html.contains("<script>")); assertFalse(html.contains("<img")); assertTrue(html.contains("&lt;b&gt;Vườn &amp; cây&lt;/b&gt;"))
        assertTrue(html.contains("<svg")); assertTrue(html.contains("<polygon")); assertTrue(html.contains("DỮ LIỆU MÔ PHỎNG"))
        assertTrue(html.contains("20 m")); assertTrue(html.contains("vùng hợp lệ chung")); assertFalse(html.contains("earthengine.googleapis.com"))
        val json = JSONObject(RegionWorkflowReport.json(r, "Vườn", ring))
        assertTrue(json.getJSONObject("analysis").getJSONObject("change").isNull("image_url")); assertFalse(json.toString().contains("temporary"))
        assertFalse(json.toString().contains("private-test-token"))
        assertEquals(107.0, json.getJSONObject("region").getJSONArray("ring").getJSONArray(0).getDouble(0), 0.0)
    }

    @Test fun `raster boundary quantization may exceed small polygon geometric area`() {
        val payload = response().put("area_ha", 0.009)
        payload.getJSONObject("change").put("common_valid_area_ha", 0.01).put("increase_ha", 0).put("stable_ha", 0.01).put("decrease_ha", 0)
        val r = RegionWorkflowClient.parse(payload)
        assertTrue(r.change.commonValidAreaHa!! > r.areaHa)
    }
}
