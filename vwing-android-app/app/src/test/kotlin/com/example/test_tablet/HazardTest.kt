package com.example.test_tablet

import com.example.test_tablet.data.vwing.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HazardTest {
    private fun hazard(id: String, level: Int, risk: Double = 0.2) = JSONObject().put("id", id).put("label_vi", id)
        .put("level", level).put("level_vi", "x").put("risk_index", risk)
        .put("reasons", JSONArray().put("lý do")).put("advice", JSONArray())

    private fun payload(levels: List<Int> = listOf(2, 0), overall: Int = 2): JSONObject = JSONObject()
        .put("fetched_at", "2026-10-22T15:20:00Z").put("stale", false).put("as_of", "2026-10-22T22:00")
        .put("overall_level", overall).put("headline", "Ngập úng: cảnh báo cao")
        .put("hazards", JSONArray().also { a -> levels.forEachIndexed { i, l -> a.put(hazard("h$i", l)) } })
        .put("rain", JSONObject().put("next_24h_mm", 120.0).put("next_48h_mm", 150.0).put("next_72h_mm", 160.0)
            .put("past_72h_mm", 30.0).put("max_hourly_mm_24h", 41.0).put("soil_moisture_0_7cm", JSONObject.NULL))
        .put("terrain", JSONObject().put("low_lying", true).put("elevation_m", 12.5))
        .put("method", "Quy tắc theo ngưỡng").put("warnings", JSONArray())

    @Test fun `parses levels rain and terrain without inventing soil moisture`() {
        val o = HazardClient.parse(payload().toString())
        assertEquals(2, o.overallLevel); assertEquals(2, o.hazards.size)
        assertEquals(120.0, o.rain.next24, 0.001); assertNull(o.rain.soilMoisture)
        assertEquals(true, o.lowLying); assertEquals(12.5, o.elevationM!!, 0.001)
    }

    @Test fun `missing terrain stays unknown rather than false`() {
        val p = payload().also { it.remove("terrain") }
        val o = HazardClient.parse(p.toString())
        assertNull(o.lowLying); assertNull(o.elevationM)
    }

    @Test fun `overall level must match the worst hazard and ranges are enforced`() {
        assertTrue(runCatching { HazardClient.parse(payload(listOf(2, 0), overall = 0).toString()) }.isFailure)
        assertTrue(runCatching { HazardClient.parse(payload(listOf(4), overall = 4).toString()) }.isFailure)
        val negative = payload(); negative.getJSONObject("rain").put("next_24h_mm", -1.0)
        assertTrue(runCatching { HazardClient.parse(negative.toString()) }.isFailure)
        val risk = payload(); risk.getJSONArray("hazards").getJSONObject(0).put("risk_index", 1.5)
        assertTrue(runCatching { HazardClient.parse(risk.toString()) }.isFailure)
    }

    @Test fun `cache round trip is marked stale`() {
        val dir = java.nio.file.Files.createTempDirectory("hz").toFile()
        val point = GeoPoint(11.547, 107.807)
        assertNull(HazardClient.loadCache(dir, point))
        java.io.File(dir, "vwing_hazard_11.547_107.807.json").writeText(payload().toString())
        assertTrue(HazardClient.loadCache(dir, point)!!.stale)
    }
}
