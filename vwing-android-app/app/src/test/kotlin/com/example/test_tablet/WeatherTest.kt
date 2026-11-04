package com.example.test_tablet

import com.example.test_tablet.data.vwing.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WeatherTest {
    private fun payload(): JSONObject {
        val current = JSONObject().put("time", "2026-10-22T22:15").put("temperature_2m", 26.0)
            .put("apparent_temperature", 28.1).put("relative_humidity_2m", 96).put("wind_speed_10m", 4.7)
            .put("precipitation", 0.0).put("weather_code", 3).put("is_day", 0)
        val hourly = JSONObject().put("time", JSONArray().put("2026-10-22T22:00").put("2026-10-22T23:00"))
            .put("temperature_2m", JSONArray().put(26).put(25)).put("weather_code", JSONArray().put(3).put(61))
            .put("precipitation_probability", JSONArray().put(JSONObject.NULL).put(80))
        val daily = JSONObject().put("time", JSONArray().put("2026-10-22")).put("temperature_2m_min", JSONArray().put(22))
            .put("temperature_2m_max", JSONArray().put(29)).put("precipitation_probability_max", JSONArray().put(85))
            .put("weather_code", JSONArray().put(61)).put("sunrise", JSONArray().put("2026-10-22T05:42"))
            .put("sunset", JSONArray().put("2026-10-22T17:30")).put("uv_index_max", JSONArray().put(8.3))
        return JSONObject().put("fetched_at", "2026-10-22T15:20:00Z").put("timezone", "Asia/Ho_Chi_Minh")
            .put("stale", false).put("current", current).put("hourly", hourly).put("daily", daily)
            .put("current_units", JSONObject().put("temperature_2m", "°C").put("wind_speed_10m", "km/h"))
    }
    @Test fun `model time and null rain probability stay separate from fetch time and zero rainfall`() {
        val w = WeatherClient.parse(payload().toString())
        assertEquals("2026-10-22T22:15", w.now.time); assertEquals("2026-10-22T15:20:00Z", w.fetchedAt)
        assertNull(w.hours.first().rainChance); assertEquals(0.0, w.now.precipitation, .001)
        assertEquals(80.0, w.hours.last().rainChance!!, .001); assertFalse(w.now.isDay)
    }
    @Test fun `wrong units and invalid weather ranges are not shown as real weather`() {
        val fahrenheit = payload(); fahrenheit.getJSONObject("current_units").put("temperature_2m", "°F")
        assertTrue(runCatching { WeatherClient.parse(fahrenheit.toString()) }.isFailure)
        val humidity = payload(); humidity.getJSONObject("current").put("relative_humidity_2m", 140)
        assertTrue(runCatching { WeatherClient.parse(humidity.toString()) }.isFailure)
        val range = payload(); range.getJSONObject("daily").put("temperature_2m_min", JSONArray().put(40))
        assertTrue(runCatching { WeatherClient.parse(range.toString()) }.isFailure)
    }
    @Test fun `garden weather uses saved boundary and never invents a default garden coordinate`() {
        assertNull(WeatherClient.gardenPoint(GardenData()))
        val boundary = GeoFeature("b", FeatureKind.BOUNDARY, "Vườn", listOf(GeoPoint(11.0,107.0), GeoPoint(11.0,107.01), GeoPoint(11.01,107.0)))
        val p = WeatherClient.gardenPoint(GardenData(features = listOf(boundary)))!!
        assertEquals(11.003333, p.lat, .00001); assertEquals(107.003333, p.lng, .00001)
    }
    @Test fun `weather code labels distinguish thunderstorm rain fog and unknown`() {
        assertEquals("Dông", WeatherClient.condition(95)); assertEquals("Mưa rào", WeatherClient.condition(81))
        assertEquals("Sương mù", WeatherClient.condition(45)); assertEquals("Chưa xác định", WeatherClient.condition(-1))
        assertNotEquals(WeatherClient.symbol(0, true), WeatherClient.symbol(0, false))
    }
}
