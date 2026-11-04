package com.example.test_tablet

import com.example.test_tablet.data.vwing.*
import com.example.test_tablet.ui.vwing.RegionHudState
import com.sun.net.httpserver.HttpServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress

class DeepForestIntegrationTest {
    private fun bounds() = JSONObject().put("south", 11.0).put("west", 107.0).put("north", 11.001).put("east", 107.001)
    private fun response(boxes: JSONArray = JSONArray()) = JSONObject().put("bounds", bounds()).put("boxes", boxes)
        .put("total_tiles_processed", JSONObject.NULL).put("raw_boxes_before_nms", JSONObject.NULL)
        .put("nms_suppressed_count", JSONObject.NULL).put("mean_confidence_pct", JSONObject.NULL)
    private fun hud() = RegionHudState("test", "Test", emptyList(), 1.0, 400.0, emptyList(), 0, 0, 0, 0, 0, 0)

    @Test fun `RGB health and unavailable counters stay unknown`() {
        val box = bounds().put("center_lat", 11.0005).put("center_lon", 107.0005).put("score", 0.81)
            .put("health_score", JSONObject.NULL).put("ndvi", JSONObject.NULL).put("ndre", JSONObject.NULL)
        val report = DeepForestClient.parsePredict(response(JSONArray().put(box)))
        assertNull(report.totalTilesProcessed); assertNull(report.rawBoxesBeforeNms); assertNull(report.nmsSuppressedCount)
        assertTrue(report.meanConfidencePct.isNaN())
        val tree = report.boxes.single().toSatTreePoint()
        assertEquals("unknown", tree.status); assertNull(tree.ndvi); assertNull(tree.ndre); assertEquals(-1, tree.healthScore)
        assertFalse(tree.resolvedDiagnosisVi.contains("85"))
    }

    @Test fun `empty real detection takes precedence over legacy sample trees`() {
        val state = hud()
        val fallback = SatTreePoint("legacy", "healthy", 0.8, 0.4, true, GeoPoint(11.0, 107.0))
        state.gardenAnalysis = SatGardenAnalysis("test", "test", true, "", "", 1, 100.0, 1.0, 400.0,
            GeoBounds(11.0, 107.0, 11.001, 107.001), SatTerrainMetrics("", null, null, null, null, null, null, ""),
            emptyMap(), emptyList(), emptyList(),
            SatTreeEstimation("durian", "", 5.0, 5.0, 100.0, 100.0, 1.0, 80.0, 1, 1, 1, 1,
                SatTreeHealthBreakdown(1, 0, 0, 1.0, 0.0, 0.0, 0.0), listOf(fallback)),
            SatSoilMoisture(null, null, null, null, "", 0.0, ""), null, SatLegend(0.0, 1.0, emptyList(), ""), emptyList(), "", emptyList(), "")
        assertEquals(1, state.allGeeTreesInPolygon().size)
        state.deepForestResult = DeepForestClient.parsePredict(response())
        assertTrue(state.allGeeTreesInPolygon().isEmpty())
    }

    @Test fun `sample area changes only analysis selection and clears prior input`() {
        val state = hud()
        state.deepForestImage = DeepForestClient.UploadedImage("old", 10, 10)
        state.selectDeepForestSampleRegion()
        assertTrue(state.deepForestSampleRegion); assertEquals(4, state.points.size); assertNull(state.deepForestImage)
        assertEquals(11.760461, state.points.map { it.lat }.average(), 0.000001)
        assertEquals(107.642313, state.points.map { it.lng }.average(), 0.000001)
        assertEquals(4.0, Geo.polygonAreaHa(state.points), 0.05)
    }

    @Test fun `upload sends exact binary bytes and prediction references returned image`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val bytes = byteArrayOf(1, 2, 3, 4)
        server.createContext("/") { e ->
            val body = e.requestBody.readBytes()
            val result = if (e.requestURI.path.endsWith("/images")) {
                assertEquals("application/octet-stream", e.requestHeaders.getFirst("Content-Type")); assertArrayEquals(bytes, body)
                """{"image_id":"uploaded","width":1024,"height":768}"""
            } else {
                val request = JSONObject(body.toString(Charsets.UTF_8))
                assertEquals("uploaded", request.getString("image_id")); assertEquals(0, request.getJSONArray("user_annotations").length())
                response().toString()
            }
            val output = result.toByteArray(); e.sendResponseHeaders(200, output.size.toLong()); e.responseBody.use { it.write(output) }
        }
        server.start()
        try {
            val cfg = SatelliteConfig("http://127.0.0.1:${server.address.port}", "test")
            val image = DeepForestClient.uploadImage(cfg, bytes)
            assertEquals(1024, image.width)
            DeepForestClient.predict(cfg, listOf(GeoPoint(11.0, 107.0), GeoPoint(11.001, 107.0), GeoPoint(11.0, 107.001)), imageId = image.imageId)
        } finally { server.stop(0) }
    }

    @Test fun `server detail survives HTTP failure`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { e ->
            e.requestBody.readBytes()
            val output = "missing labeled train dataset".toByteArray()
            e.sendResponseHeaders(422, output.size.toLong()); e.responseBody.use { it.write(output) }
        }
        server.start()
        try {
            val result = runCatching { DeepForestClient.uploadImage(SatelliteConfig("http://127.0.0.1:${server.address.port}", "test"), byteArrayOf(1)) }
            assertTrue(result.isFailure); assertTrue(result.exceptionOrNull()!!.message!!.contains("missing labeled train dataset"))
        } finally { server.stop(0) }
    }

    @Test fun `xmlToCsvLocal converts Pascal VOC XML to DeepForest CSV columns and splits train validation`() {
        val conv = DeepForestClient.xmlToCsvLocal(
            xmlDocs = DeepForestClient.SAMPLE_VIETNAM_XML_DOCS,
            valRatio = 0.5,
            normalizeLabelToTree = true,
        )
        assertEquals(listOf("image_path", "xmin", "ymin", "xmax", "ymax", "label"), conv.columns)
        assertEquals(6, conv.totalBoxes)
        assertEquals(2, conv.uniqueImagesCount)
        assertEquals(4, conv.trainBoxesCount)
        assertEquals(2, conv.validationBoxesCount)
        assertTrue(conv.vietnamTrainCsv.startsWith("image_path,xmin,ymin,xmax,ymax,label\n"))
        assertTrue(conv.vietnamTrainCsv.contains("ben_tre_coconut_uav_01.png,64,72,156,164,Tree"))
        assertTrue(conv.vietnamValidationCsv.contains("dak_lak_coffee_durian_uav_02.png,90,95,210,215,Tree"))
    }
}
