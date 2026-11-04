package com.example.test_tablet.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.test_tablet.data.vwing.SATELLITE_GUIDANCE
import com.example.test_tablet.data.vwing.SATELLITE_WORKSPACE_PRESETS
import com.example.test_tablet.data.vwing.SatelliteAnalysisMode
import com.example.test_tablet.data.vwing.SatelliteConfig
import com.example.test_tablet.data.vwing.SatelliteSettings
import com.example.test_tablet.data.vwing.VWingStore
import com.example.test_tablet.ui.vwing.SatelliteWorkspaceScreen
import com.example.test_tablet.ui.vwing.VCtx
import com.example.test_tablet.ui.vwing.VNav
import com.example.test_tablet.ui.vwing.VWingTheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

/** JVM UI coverage; the native Google map is deliberately replaced by the screen's fallback. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class SatelliteWorkspaceUiTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var dir: File
    private lateinit var context: Context
    private lateinit var store: VWingStore
    private lateinit var server: HttpServer
    private val requests = AtomicInteger()
    @Volatile private var indexResponse: String? = null

    @Before fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "satellite-workspace-ui-${System.nanoTime()}").apply { mkdirs() }
        context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getFilesDir(): File = dir
        }
        store = VWingStore(File(dir, "store").apply { mkdirs() }, null, false)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                // Thẻ "9 tài liệu nghiên cứu" tự tải bộ phân tích nền ngay khi có vùng chọn (chủ ý của thiết kế),
                // nên không tính vào số lần gọi do thao tác người dùng gây ra.
                if (exchange.requestURI.path != "/v1/satellite/ag-research-suite") requests.incrementAndGet()
                val fixture = indexResponse.takeIf { exchange.requestURI.path == "/v1/satellite/index" }
                val response = (fixture ?: "Unexpected request in a local UI validation test").toByteArray()
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(if (fixture == null) 500 else 200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
                exchange.close()
            }
            start()
        }
        SatelliteSettings(dir).save(SatelliteConfig("http://127.0.0.1:${server.address.port}", "workspace-test-token"))
    }

    @After fun tearDown() {
        server.stop(0)
        dir.deleteRecursively()
    }

    private fun showScreen(wide: Boolean = false) {
        val c = VCtx(store, VNav(mutableStateListOf("satellite")), wide, {}, null, {}, {}, {}, simpleMode = true)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                VWingTheme {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        SatelliteWorkspaceScreen(c, showNativeMap = false)
                    }
                }
            }
        }
    }

    private fun selectMode(mode: SatelliteAnalysisMode) {
        compose.onNodeWithText(SatelliteAnalysisMode.GARDEN.label).performScrollTo().performClick()
        compose.onNodeWithText(mode.label).performScrollTo().performClick()
    }

    private fun selectSample() {
        compose.onNodeWithText("Chọn vùng mẫu…").performScrollTo().performClick()
        compose.onNodeWithText(SATELLITE_WORKSPACE_PRESETS.first().name).performScrollTo().performClick()
    }

    private fun analyze(mode: SatelliteAnalysisMode = SatelliteAnalysisMode.GARDEN) {
        compose.onNodeWithText("Phân tích ${mode.label}").performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun assertNoRequests() {
        compose.runOnIdle { assertEquals("Local UI actions must not contact the analysis backend", 0, requests.get()) }
    }

    private fun screenshot(name: String) {
        File("build/outputs/roborazzi").mkdirs()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun `simple mode retains all seven analysis choices on phone`() {
        showScreen()
        compose.onNodeWithText("Chọn phương pháp (7 chế độ)").assertExists()
        screenshot("satellite-workspace-phone-portrait")
        compose.onNodeWithText(SatelliteAnalysisMode.GARDEN.label).performScrollTo().performClick()
        SatelliteAnalysisMode.entries.forEach { mode ->
            compose.onAllNodesWithText(mode.label).fetchSemanticsNodes().also { nodes ->
                org.junit.Assert.assertTrue("Missing mode ${mode.label}", nodes.isNotEmpty())
            }
        }
        compose.onNodeWithText(SatelliteAnalysisMode.DRONE.label).performScrollTo().performClick()
        compose.onNodeWithText("Phân tích ${SatelliteAnalysisMode.DRONE.label}").assertExists()
        compose.onNodeWithText("Kỳ ảnh: từ (YYYY-MM-DD)").assertDoesNotExist()
        compose.onNodeWithText("Phân tích minh họa:", substring = true).assertExists()
        assertNoRequests()
    }

    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun `sample sets polygon and redraw clears its points and area`() {
        showScreen()
        selectSample()
        compose.onNodeWithText("Vùng mẫu: ${SATELLITE_WORKSPACE_PRESETS.first().name}", substring = true)
            .assertTextContains("5 đỉnh", substring = true)
        screenshot("satellite-workspace-tablet-portrait")
        compose.onNodeWithText("Vẽ lại").performScrollTo().performClick()
        compose.onNodeWithText("Ranh vẽ mới", substring = true).assertTextContains("0 đỉnh", substring = true)
        compose.onNodeWithText("Ranh vẽ mới", substring = true).assertTextContains("0 ha", substring = true)
        compose.onNodeWithText("Bỏ đỉnh cuối").assertIsNotEnabled()
        assertNoRequests()
    }

    @Test @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun `guidance opens and dismisses on landscape tablet`() {
        showScreen(wide = true)
        screenshot("satellite-workspace-tablet-landscape")
        val guide = SATELLITE_GUIDANCE.first()
        compose.onNodeWithText(guide.title).performScrollTo().performClick()
        compose.onNodeWithText(guide.body).assertExists()
        compose.onNodeWithText("Đã hiểu").performClick()
        compose.onNodeWithText(guide.body).assertDoesNotExist()
        assertNoRequests()
    }

    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun `analyze without a polygon validates locally and region change clears stale error`() {
        showScreen()
        analyze()
        val message = "Chọn vùng mẫu hoặc nhấn giữ bản đồ để thêm ít nhất 3 đỉnh ranh giới."
        compose.onNodeWithText(message).assertExists()
        selectSample()
        compose.onNodeWithText(message).assertDoesNotExist()
        compose.onNodeWithText("Chưa có kết quả cho vùng và phương pháp đang chọn.").assertExists()
        assertNoRequests()
    }

    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun `impossible calendar date is rejected before a backend request`() {
        showScreen()
        selectSample()
        compose.onNodeWithText("Kỳ ảnh: từ (YYYY-MM-DD)").performScrollTo().performTextReplacement("2026-02-30")
        analyze()
        compose.onNodeWithText("Ngày bắt đầu phải hợp lệ theo định dạng YYYY-MM-DD.").assertExists()
        assertNoRequests()
    }

    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun `water mode exposes both periods and can use preceding baseline`() {
        showScreen()
        selectMode(SatelliteAnalysisMode.WATER)
        compose.onNodeWithText("Kỳ sự kiện: từ (YYYY-MM-DD)").assertExists()
        compose.onNodeWithText("Kỳ sự kiện: đến, không gồm ngày cuối").assertExists()
        compose.onNodeWithText("Kỳ nền: từ (YYYY-MM-DD)").assertExists()
        compose.onNodeWithText("Kỳ nền: đến, không gồm ngày cuối").assertExists()
        compose.onNodeWithText("Loại cây trồng").assertDoesNotExist()
        compose.onNodeWithText("Kỳ sự kiện: từ (YYYY-MM-DD)").performScrollTo().performTextReplacement("2026-05-01")
        compose.onNodeWithText("Kỳ sự kiện: đến, không gồm ngày cuối").performScrollTo().performTextReplacement("2026-05-31")
        compose.onNodeWithText("Dùng kỳ liền trước làm nền").performScrollTo().performClick()
        compose.onNodeWithText("Nhập kỳ sự kiện hợp lệ trước.").assertDoesNotExist()
        compose.onNodeWithText("Kỳ nền: từ (YYYY-MM-DD)").assertTextContains("2026-04-01", substring = true)
        compose.onNodeWithText("Kỳ nền: đến, không gồm ngày cuối").assertTextContains("2026-05-01", substring = true)
        assertNoRequests()
    }

    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun `index request renders report and changing period removes stale report`() {
        indexResponse = """{"index":"NDVI","dataset":"fixture","source":"MÔ PHỎNG HTTP fixture","simulated":true,
            "start":"2026-08-01","end_exclusive":"2026-09-01","scene_count":3,"mean":0.61,"p5":0.4,"p95":0.8,
            "valid_coverage_pct":92.0,"nominal_pixel_size_m":10,"method":"UI integration fixture","area_ha":2.68,
            "bounds":{"south":11.5497,"west":107.7996,"north":11.5512,"east":107.8016},"image_url":null,
            "legend":{"min":0.0,"max":1.0,"palette":["ff0000","00ff00"],"label":"Fixture legend"},
            "warnings":[],"caution":"Không dùng ngoài thực địa."}"""
        showScreen()
        selectSample()
        selectMode(SatelliteAnalysisMode.NDVI)
        compose.onNodeWithText("Kỳ ảnh: từ (YYYY-MM-DD)").performScrollTo().performTextReplacement("2026-08-01")
        compose.onNodeWithText("Kỳ ảnh: đến, không gồm ngày cuối").performScrollTo().performTextReplacement("2026-09-01")
        analyze(SatelliteAnalysisMode.NDVI)
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("UI integration fixture").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("UI integration fixture").assertExists()
        assertEquals(1, requests.get())
        compose.onNodeWithText("Kỳ ảnh: đến, không gồm ngày cuối").performScrollTo().performTextReplacement("2026-09-02")
        compose.onNodeWithText("UI integration fixture").assertDoesNotExist()
        compose.onNodeWithText("Chưa có kết quả cho vùng và phương pháp đang chọn.").assertExists()
        assertEquals(1, requests.get())
    }
}
