package com.example.test_tablet

import com.example.test_tablet.data.vwing.*
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class SatelliteWorkspaceTest {
    @Test fun `ISO date parsing rejects normalized and partial dates`() {
        listOf(
            "2026-02-29", "2024-02-30", "2026-04-31", "2026-13-01", "2026-00-10",
            "2026-01-00", "2026-1-01", "26-01-01", "2026-01-01x", "2026-01-01\n",
            " 2026-01-01", "2026-01-01 ", "0000-01-01", "",
        ).forEach { assertNull(it, SatelliteWorkspaceDates.parseIsoDate(it)) }
        assertNotNull(SatelliteWorkspaceDates.parseIsoDate("2024-02-29"))
        assertNotNull(SatelliteWorkspaceDates.parseIsoDate("2026-10-23"))
    }

    @Test fun `range includes 93 complete days but rejects 94`() {
        val range = SatelliteWorkspaceDates.range("2026-01-01", "2026-04-04")!!
        assertEquals(93, range.days)
        assertNull(SatelliteWorkspaceDates.range("2026-01-01", "2026-04-05"))
        assertEquals(1, SatelliteWorkspaceDates.range("2026-01-01", "2026-01-02")!!.days)
    }

    @Test fun `range rejects empty reversed and invalid periods`() {
        assertNotNull(SatelliteWorkspaceDates.validate("2026-01-01", "2026-01-01"))
        assertNotNull(SatelliteWorkspaceDates.validate("2026-01-02", "2026-01-01"))
        assertNotNull(SatelliteWorkspaceDates.validate("2026-02-30", "2026-03-01"))
        assertNotNull(SatelliteWorkspaceDates.validate("2026-02-01", "2026-02-30"))
    }

    @Test fun `preceding period has equal length and no overlap at boundary`() {
        val current = SatelliteDateRange("2026-03-01", "2026-04-01")
        val previous = current.preceding()
        assertEquals("2026-01-29", previous.start)
        assertEquals(current.start, previous.endExclusive)
        assertEquals(current.days, previous.days)
        assertEquals("2024-02-28", SatelliteDateRange("2024-03-01", "2024-03-03").preceding().start)
    }

    @Test fun `rolling ranges use supplied local day across spring and fall DST`() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply { timeZone = zone }
        listOf("2026-03-30 00:05", "2026-11-15 23:55").forEach { supplied ->
            val now = format.parse(supplied)!!.time
            listOf(30, 60, 90).forEach { days ->
                val range = SatelliteWorkspaceDates.rolling(days, now, zone)
                assertEquals(days, range.days)
                assertEquals(supplied.substring(0, 10), range.endExclusive)
                assertNull(SatelliteWorkspaceDates.validate(range.start, range.endExclusive))
            }
        }
    }

    @Test fun `rolling ranges respect device zone rather than UTC date`() {
        val instant = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse("2026-10-22 19:00")!!.time
        val range = SatelliteWorkspaceDates.rolling(30, instant, TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
        assertEquals(SatelliteDateRange("2026-09-23", "2026-10-23"), range)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rolling only accepts available preset periods`() {
        SatelliteWorkspaceDates.rolling(94)
    }

    @Test fun `spacing accepts comma and both inclusive bounds`() {
        assertEquals(0.6, satelliteSpacingM("0,6")!!, 0.0)
        assertEquals(2.5, satelliteSpacingM(" 2,5 ")!!, 0.0)
        assertEquals(25.0, satelliteSpacingM("25")!!, 0.0)
        assertNull(satelliteSpacingProblem(""))
        assertNull(satelliteSpacingProblem("  "))
        assertNull(satelliteSpacingM(""))
    }

    @Test fun `spacing rejects non decimal and outside range values`() {
        listOf("0.59", "25.01", "-1", "NaN", "Infinity", "1e1", "2,5,5", "abc").forEach {
            assertNull(it, satelliteSpacingM(it))
            assertNotNull(it, satelliteSpacingProblem(it))
        }
    }

    @Test fun `workspace exposes exactly seven standalone modes`() {
        assertEquals(
            listOf("GARDEN", "NDVI", "EVI", "NDMI", "WATER", "DROUGHT", "DRONE"),
            SatelliteAnalysisMode.entries.map { it.name },
        )
        assertEquals(SatIndexKey.NDVI, SatelliteAnalysisMode.NDVI.indexKey)
        assertEquals(SatIndexKey.EVI, SatelliteAnalysisMode.EVI.indexKey)
        assertEquals(SatIndexKey.NDMI, SatelliteAnalysisMode.NDMI.indexKey)
        assertTrue(SatelliteAnalysisMode.WATER.usesDates)
        assertFalse(SatelliteAnalysisMode.DRONE.usesDates)
        assertFalse(SatelliteAnalysisMode.DROUGHT.usesDates)
    }

    @Test fun `all standalone presets preserve original coordinates without changing Vietnam list`() {
        assertEquals(6, VIETNAM_GARDEN_PRESETS.size)
        assertEquals(8, SATELLITE_WORKSPACE_PRESETS.size)
        assertEquals(VIETNAM_GARDEN_PRESETS, SATELLITE_WORKSPACE_PRESETS.take(6))
        val sample = SATELLITE_WORKSPACE_PRESETS.first { "1,21" in it.name }
        assertEquals(GeoPoint(11.55, 107.8), sample.points.first())
        val basra = SATELLITE_WORKSPACE_PRESETS.first { "Basra" in it.name }
        assertEquals(
            listOf(GeoPoint(30.5, 47.78), GeoPoint(30.5, 47.783), GeoPoint(30.503, 47.783), GeoPoint(30.503, 47.78)),
            basra.points,
        )
    }

    @Test fun `request signature changes for each analysis input and never displays token`() {
        val original = SatelliteRequestIdentity(
            SatelliteAnalysisMode.GARDEN, SATELLITE_WORKSPACE_PRESETS.first().points,
            "2026-09-23", "2026-10-23", "2026-08-24", "2026-09-23",
            serverUrl = "http://localhost:8000", serverToken = "secret-token",
        )
        val signature = original.signature()
        assertEquals(64, signature.length)
        assertEquals(signature, original.copy().signature())
        assertEquals(signature, original.copy(serverUrl = " http://localhost:8000/ ").signature())
        listOf(
            original.copy(mode = SatelliteAnalysisMode.NDVI),
            original.copy(ring = original.ring.dropLast(1)),
            original.copy(start = "2026-09-24"),
            original.copy(endExclusive = "2026-10-24"),
            original.copy(baselineStart = "2026-08-25"),
            original.copy(baselineEndExclusive = "2026-09-24"),
            original.copy(crop = SatCropKey.COFFEE),
            original.copy(spacingM = 3.0),
            original.copy(serverUrl = "http://localhost:8001"),
            original.copy(serverToken = "another-token"),
        ).forEach { assertNotEquals(signature, it.signature()) }
        assertFalse(signature.contains("secret-token"))
    }

    @Test fun `guidance preserves four topics and capability boundaries`() {
        assertEquals(4, SATELLITE_GUIDANCE.size)
        assertTrue(SATELLITE_GUIDANCE[0].body.contains("mô phỏng"))
        assertTrue(SATELLITE_GUIDANCE[1].body.contains("phần mềm chuyên dụng"))
        assertTrue(SATELLITE_GUIDANCE[2].body.contains("chưa hiển thị"))
        assertTrue(SATELLITE_GUIDANCE[3].body.contains("chưa có dịch vụ đặt lịch"))
    }
}
