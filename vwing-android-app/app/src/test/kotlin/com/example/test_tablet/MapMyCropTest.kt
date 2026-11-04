package com.example.test_tablet

import com.example.test_tablet.data.vwing.MapMyCropClient
import com.example.test_tablet.data.vwing.MmcAuthException
import org.junit.Assert.*
import org.junit.Test

class MapMyCropTest {
    private val forecast = """{"daily_risk_analysis":[
      {"date":"2026-10-30","advisories":[{"advisory_title":"Blast","risk_level":"High","risk_percentage":82,"hex":"#E53E3E"},{"advisory_title":"Rust","risk_level":"low","risk_percentage":10.5,"hex":"#38A169"}]},
      {"date":"2026-10-31","advisories":[{"advisory_title":"Blast","risk_level":"medium","risk_percentage":55,"hex":"#DD6B20"}]},
      {"date":"2026-11-01","advisories":[]},
      {"date":"2026-11-02","advisories":[]}]}"""

    @Test fun pivotsDiseaseByDayAndMarksMissingAsNoData() {
        val f = MapMyCropClient.parseForecast(forecast)
        assertEquals(4, f.dates.size)
        assertEquals(listOf("Blast", "Rust"), f.rows.map { it.disease })
        assertEquals(82.0, f.rows[0].cells["2026-10-30"]!!.percent!!, 0.0)
        assertNull(f.rows[1].cells["2026-10-31"]!!.percent)
        assertEquals(listOf("High", "medium", "low"), f.legend.map { it.level })
    }

    @Test fun wrapsDataEnvelopeLikeTheWebClient() {
        val f = MapMyCropClient.parseForecast("""{"data":$forecast}""")
        assertEquals(2, f.rows.size)
    }

    @Test fun highRiskSummaryIgnoresHiddenTailDays() {
        val f = MapMyCropClient.parseForecast(forecast)
        assertEquals(1 to "2026-10-30", MapMyCropClient.highRiskSummary(f))
        val tail = MapMyCropClient.parseForecast("""{"daily_risk_analysis":[
          {"date":"2026-10-30","advisories":[{"advisory_title":"A","risk_level":"low","risk_percentage":1,"hex":"#38A169"}]},
          {"date":"2026-10-31","advisories":[]},
          {"date":"2026-11-01","advisories":[{"advisory_title":"A","risk_level":"high","risk_percentage":90,"hex":"#E53E3E"}]}]}""")
        assertEquals(0, MapMyCropClient.highRiskSummary(tail).first)
    }

    @Test fun emptyOrMalformedForecastGivesEmptyTable() {
        assertTrue(MapMyCropClient.parseForecast("""{"daily_risk_analysis":[]}""").rows.isEmpty())
        assertTrue(MapMyCropClient.parseForecast("{}").dates.isEmpty())
    }

    @Test fun rejectsInvalidHexColour() {
        val f = MapMyCropClient.parseForecast("""{"daily_risk_analysis":[{"date":"2026-10-30","advisories":[{"advisory_title":"A","risk_level":"low","risk_percentage":1,"hex":"red;x"}]}]}""")
        assertNull(f.rows[0].cells["2026-10-30"]!!.colorHex)
    }

    @Test fun parsesApiKeyFromEitherShapeAndFailsWithoutOne() {
        assertEquals("k1", MapMyCropClient.parseApiKey("""{"apikey":"k1"}"""))
        assertEquals("k2", MapMyCropClient.parseApiKey("""{"data":{"api_key":"k2"}}"""))
        assertThrows(MmcAuthException::class.java) { MapMyCropClient.parseApiKey("""{"message":"ok"}""") }
    }

    @Test fun parsesFarmPageWithCursor() {
        val p = MapMyCropClient.parseFarms("""{"farms":[{"id":7,"name":"Vườn A"},{"id":"8","farm_name":"Vườn B"},{"name":"no id"}],
          "pagination":{"has_next":true,"next_cursor":{"created_at":"2026-10-01T00:00:00Z","id":"8"}}}""")
        assertEquals(listOf("7" to "Vườn A", "8" to "Vườn B"), p.farms.map { it.id to it.name })
        assertTrue(p.hasNext); assertEquals("8", p.nextCursorId)
    }

    @Test fun invalidKeyAndExpiredSessionCountAsSessionLost() {
        assertTrue(MapMyCropClient.isSessionLost(404, """{"error_code":"USER_NOT_FOUND"}"""))
        assertTrue(MapMyCropClient.isSessionLost(406, ""))
        assertFalse(MapMyCropClient.isSessionLost(404, """{"message":"farm not found"}"""))
        assertFalse(MapMyCropClient.isSessionLost(500, ""))
    }
}
