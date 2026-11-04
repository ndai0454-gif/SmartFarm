package com.example.test_tablet

import com.example.test_tablet.data.vwing.*
import java.io.File
import java.net.ServerSocket
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** The two historical/drone modes use the same authenticated backend as the five Sentinel modes. */
class SatelliteWorkspaceBackendTest {
    @Test fun `drought and drone reports and rasters round trip through mock backend`() {
        val backend = File("E:/New folder/SmartFarm/vwing-satellite-backend")
        val python = File(backend, ".venv/Scripts/python.exe")
        assumeTrue("Backend Python is required for this integration test", python.isFile)
        val port = ServerSocket(0).use { it.localPort }
        val cfg = SatelliteConfig("http://127.0.0.1:$port", "workspace-integration-test-token")
        val serverLog = File.createTempFile("vwing-workspace-backend-", ".log")
        val builder = ProcessBuilder(python.absolutePath, "-m", "uvicorn", "app.main:app", "--host", "127.0.0.1", "--port", "$port")
            .directory(backend).redirectErrorStream(true).redirectOutput(serverLog)
        builder.environment()["VWING_EE_MODE"] = "mock"
        builder.environment()["VWING_SAT_TOKEN"] = cfg.token
        builder.environment()["VWING_PUBLIC_URL"] = cfg.url
        val process = builder.start()
        try {
            val deadline = System.currentTimeMillis() + 20_000
            while (SatelliteClient.health(cfg).isFailure) {
                check(process.isAlive && System.currentTimeMillis() < deadline) { "Mock backend did not become ready" }
                Thread.sleep(150)
            }
            val points = SATELLITE_WORKSPACE_PRESETS.first().points
            val drought = DroughtMonitoringClient.analyze(cfg, points)
            assertTrue(drought.simulated)
            assertEquals(2025, drought.focusYear)
            assertEquals(26, drought.annualTimeSeries.size)
            assertTrue(drought.sixCoreIndices.isNotEmpty())
            assertPng(SatelliteClient.image(cfg, drought.activeOverlayUrl).getOrThrow())
            val drone = DroneMultispectralClient.analyze(cfg, points)
            assertTrue(drone.simulated)
            assertEquals(5, drone.step2.fiveVegetationIndices.size)
            assertTrue(drone.step3.prescriptionZones.isNotEmpty())
            assertPng(SatelliteClient.image(cfg, drone.activeOverlayUrl).getOrThrow())
        } finally {
            process.destroyForcibly()
            process.waitFor()
            serverLog.delete()
        }
    }

    private fun assertPng(bytes: ByteArray) {
        assertArrayEquals(byteArrayOf(0x89.toByte(), 80, 78, 71), bytes.copyOf(4))
    }
}
