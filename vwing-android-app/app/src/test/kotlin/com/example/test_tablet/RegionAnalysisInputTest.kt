package com.example.test_tablet

import com.example.test_tablet.data.vwing.*
import com.example.test_tablet.ui.vwing.analysisDateProblem
import org.junit.Assert.*
import org.junit.Test

class RegionAnalysisInputTest {
    @Test fun `dates are strict and bounded with exclusive end`() {
        assertNull(analysisDateProblem("2026-08-01", "2026-09-01"))
        assertNotNull(analysisDateProblem("2026-02-30", "2026-03-02"))
        assertNotNull(analysisDateProblem("2026-08-01", "2026-08-01"))
        assertNotNull(analysisDateProblem("2026-01-01", "2026-09-01"))
        assertNotNull(analysisDateProblem("2026-8-1", "2026-09-01"))
    }
    @Test fun `image token origin must match exactly`() {
        val c = SatelliteConfig("http://127.0.0.1:8000", "secret-token-123")
        assertTrue(SatelliteClient.imageTarget(c, "/v1/image.png").second)
        assertTrue(SatelliteClient.imageTarget(c, "http://127.0.0.1:8000/v1/image.png").second)
        assertFalse(SatelliteClient.imageTarget(c, "https://earthengine.googleapis.com/v1/image.png").second)
        for (url in listOf("http://127.0.0.1:8000.evil.example/x", "http://127.0.0.1:8001/x", "//evil.example/x", "https://earthengine.googleapis.com.evil.example/x", "https://user@earthengine.googleapis.com/x")) {
            assertTrue(url, runCatching { SatelliteClient.imageTarget(c, url) }.isFailure)
        }
    }
}
