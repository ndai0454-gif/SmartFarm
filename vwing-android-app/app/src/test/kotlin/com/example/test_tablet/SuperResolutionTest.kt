package com.example.test_tablet

import com.example.test_tablet.data.vwing.*
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class SuperResolutionTest {
    private val id = "a".repeat(32)
    private val api = "/v1/satellite/super-resolution"
    private val png = byteArrayOf(0x89.toByte(), 80, 78, 71, 13, 10, 26, 10, 0)

    private fun result(): JSONObject = JSONObject().put("model", "ESAOpenSR SEN2SRLite")
        .put("model_revision", "pinned-test-revision").put("source_scene", "Sentinel-2-test-scene")
        .put("source_date", "2026-09-18").put("native_pixel_size_m", 10).put("model_pixel_size_m", 2.5)
        .put("output_pixel_size_m", 1.0).put("crs", "EPSG:32648").put("width", 80).put("height", 91)
        .put("valid_coverage_pct", 97.5).put("method", "Synthetic protocol test, not model inference")
        .put("bounds", JSONObject().put("south", 11.55).put("west", 107.8).put("north", 11.551).put("east", 107.801))
        .put("warnings", JSONArray().put("AI suy diễn · chưa kiểm chứng thực địa"))
        .put("files", JSONObject().put("original_preview", "$api/$id/files/original.png")
            .put("enhanced_preview", "$api/$id/files/enhanced.png").put("original_geotiff", "$api/$id/files/original.tif")
            .put("enhanced_geotiff", "$api/$id/files/enhanced.tif").put("metadata", "$api/$id/files/metadata.json"))

    private fun job(state: String, result: JSONObject? = null, error: String? = null): JSONObject =
        JSONObject().put("job_id", id).put("state", state).put("result", result ?: JSONObject.NULL).put("error", error ?: JSONObject.NULL)

    private fun respond(exchange: HttpExchange, code: Int, body: ByteArray) {
        exchange.sendResponseHeaders(code, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
        exchange.close()
    }

    private fun withServer(handler: (HttpExchange) -> Unit, block: (SatelliteConfig) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/", handler)
        server.start()
        try { block(SatelliteConfig("http://127.0.0.1:${server.address.port}", "test-token-123456")) }
        finally { server.stop(0) }
    }

    @Test fun `terminal result retains native model and exported resolution separately`() {
        val parsed = SuperResolutionClient.parseJob(job("succeeded", result()))
        assertEquals(SrJobState.SUCCEEDED, parsed.state)
        assertEquals(10.0, parsed.result!!.nativePixelSizeM, 0.0)
        assertEquals(2.5, parsed.result.modelPixelSizeM, 0.0)
        assertEquals(1.0, parsed.result.outputPixelSizeM, 0.0)
        assertEquals("EPSG:32648", parsed.result.crs)
        assertTrue(parsed.result.bounds.valid)
        assertEquals(97.5, parsed.result.validCoveragePct, 0.0)
        assertEquals(5, parsed.result.files.size)
    }

    @Test fun `unknown states inconsistent results and unsafe manifests fail closed`() {
        val invalid = listOf(job("processing"), job("queued", result()), job("succeeded"),
            job("running", error = "unexpected"),
            job("succeeded", result().apply { getJSONObject("files").put("enhanced_geotiff", "https://evil.test/stolen.tif") }),
            job("succeeded", result().apply { put("native_pixel_size_m", 1) }),
            job("succeeded", result().apply { put("valid_coverage_pct", 101) }),
            job("succeeded", result().apply { getJSONObject("bounds").put("south", 12) }),
            job("queued").put("job_id", "../invalid"))
        invalid.forEach { response -> assertTrue(response.toString(), runCatching { SuperResolutionClient.parseJob(response) }.isFailure) }
    }

    @Test fun `poll transitions reject regression different jobs and terminal mutation`() {
        val queued = SuperResolutionClient.parseJob(job("queued"))
        val running = SuperResolutionClient.parseJob(job("running"))
        val succeeded = SuperResolutionClient.parseJob(job("succeeded", result()))
        val failed = SuperResolutionClient.parseJob(job("failed", error = "Mô hình không sẵn sàng"))
        assertTrue(queued.accepts(running))
        assertTrue(queued.accepts(succeeded)) // A fast inference can finish between two polls.
        assertTrue(running.accepts(failed))
        assertFalse(running.accepts(queued))
        assertFalse(succeeded.accepts(failed))
        assertFalse(queued.accepts(running.copy(jobId = "b".repeat(32))))
        assertNotNull(SuperResolutionClient.parseJob(job("failed")).error)
    }

    @Test fun `submit uses GeoJSON longitude first and polls actual HTTP job sequence`() {
        val captured = AtomicReference<JSONObject>()
        val authorization = AtomicReference<String>()
        val polls = AtomicInteger(0)
        withServer({ exchange ->
            authorization.set(exchange.requestHeaders.getFirst("Authorization"))
            if (exchange.requestMethod == "POST") {
                captured.set(JSONObject(exchange.requestBody.use { it.readBytes().toString(Charsets.UTF_8) }))
                respond(exchange, 202, job("queued").toString().toByteArray())
            } else {
                val response = if (polls.incrementAndGet() == 1) job("running") else job("succeeded", result())
                respond(exchange, 200, response.toString().toByteArray())
            }
        }) { cfg ->
            val ring = listOf(GeoPoint(11.55, 107.8), GeoPoint(11.55, 107.801), GeoPoint(11.551, 107.8))
            val queued = SuperResolutionClient.submit(cfg, ring, "2026-09-01", "2026-10-01", 1.0).getOrThrow()
            val body = captured.get()
            val point = body.getJSONObject("region").getJSONArray("ring").getJSONArray(0)
            assertEquals(107.8, point.getDouble(0), 0.0)
            assertEquals(11.55, point.getDouble(1), 0.0)
            assertEquals(1.0, body.getDouble("output_pixel_size_m"), 0.0)
            assertEquals(80.0, body.getDouble("max_cloud_pct"), 0.0)
            assertEquals("Bearer ${cfg.token}", authorization.get())
            val running = SuperResolutionClient.status(cfg, queued.jobId).getOrThrow()
            val complete = SuperResolutionClient.status(cfg, queued.jobId).getOrThrow()
            assertTrue(queued.accepts(running) && running.accepts(complete))
            assertEquals(SrJobState.SUCCEEDED, complete.state)
        }
    }

    @Test fun `artifact downloads are authenticated streaming and preview stays PNG`() {
        val auth = AtomicReference<String>()
        withServer({ exchange ->
            auth.set(exchange.requestHeaders.getFirst("Authorization"))
            val body = if (exchange.requestURI.path.endsWith(".png")) png else byteArrayOf(73, 73, 42, 0, 7, 8, 9)
            respond(exchange, 200, body)
        }) { cfg ->
            assertArrayEquals(png, SuperResolutionClient.preview(cfg, "$api/$id/files/original.png").getOrThrow())
            val output = ByteArrayOutputStream()
            assertEquals(7L, SuperResolutionClient.download(cfg, "$api/$id/files/enhanced.tif", output).getOrThrow())
            assertArrayEquals(byteArrayOf(73, 73, 42, 0, 7, 8, 9), output.toByteArray())
            assertEquals("Bearer ${cfg.token}", auth.get())
            assertTrue(SuperResolutionClient.preview(cfg, "$api/$id/files/metadata.json").isFailure)
        }
    }

    @Test fun `redirect is never followed with token`() {
        val receivedAtRedirect = AtomicInteger(0)
        withServer({ target -> receivedAtRedirect.incrementAndGet(); respond(target, 200, png) }) { external ->
            withServer({ exchange ->
                exchange.responseHeaders.add("Location", external.url + "$api/$id/files/enhanced.png")
                respond(exchange, 302, "redirect".toByteArray())
            }) { cfg ->
                val response = SuperResolutionClient.preview(cfg, "$api/$id/files/enhanced.png")
                assertTrue(response.isFailure)
                assertTrue(response.exceptionOrNull()!!.message!!.contains("chuyển hướng"))
                assertEquals(0, receivedAtRedirect.get())
            }
        }
    }

    @Test fun `external traversal credentials and unrelated artifacts are blocked before network`() {
        val cfg = SatelliteConfig("http://127.0.0.1:8000", "test-token-123456")
        val invalid = listOf("https://earthengine.googleapis.com$api/$id/files/enhanced.tif",
            "http://127.0.0.1:8001$api/$id/files/enhanced.tif", "http://user:pass@127.0.0.1:8000$api/$id/files/enhanced.tif",
            "$api/$id/files/../enhanced.tif", "$api/$id/files/%2e%2e/enhanced.tif", "$api/$id/files/private.txt",
            "$api/$id/files/enhanced.tif?token=leak", "$api/$id/files/enhanced.tif#fragment")
        invalid.forEach { path -> assertTrue(path, runCatching { SuperResolutionClient.artifactTarget(cfg, path) }.isFailure) }
        assertEquals("http://127.0.0.1:8000$api/$id/files/enhanced.tif", SuperResolutionClient.artifactTarget(cfg, "$api/$id/files/enhanced.tif").toString())
    }

    @Test fun `bounded stream rejects oversize without buffering all data and propagates cancellation`() {
        val output = ByteArrayOutputStream()
        assertTrue(runCatching { SuperResolutionClient.copyLimited(ByteArrayInputStream(ByteArray(9)), output, 8) }.isFailure)
        assertEquals(0, output.size())
        assertEquals(8L, SuperResolutionClient.copyLimited(ByteArrayInputStream(ByteArray(8)), output, 8))
        assertTrue(runCatching {
            SuperResolutionClient.copyLimited(ByteArrayInputStream(ByteArray(1)), output, 8) { throw CancellationException("cancel") }
        }.exceptionOrNull() is CancellationException)
    }

    @Test fun `mock backend and full queue report actionable localized errors`() {
        for ((code, expected) in listOf(503 to "backend live", 429 to "thử lại", 401 to "Sai mã", 404 to "hết hạn")) {
            withServer({ exchange -> respond(exchange, code, JSONObject().put("detail", "unit-test").toString().toByteArray()) }) { cfg ->
                val response = SuperResolutionClient.status(cfg, id)
                assertTrue(response.isFailure)
                assertTrue(response.exceptionOrNull()!!.message!!, response.exceptionOrNull()!!.message!!.contains(expected))
            }
        }
    }
}
