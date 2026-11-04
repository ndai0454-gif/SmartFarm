package com.example.test_tablet.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.test_tablet.data.vwing.*
import com.example.test_tablet.ui.vwing.*
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Kiểm thử giao diện Compose chạy trên JVM bằng Robolectric (không cần tablet), kèm ảnh chụp màn hình ở nhiều cỡ màn hình theo cách của
 * android/nowinandroid: điện thoại dọc, tablet dọc, tablet ngang. Ảnh nằm ở app/build/outputs/roborazzi để xem bằng mắt khi cần.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class VWingComposeUiTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var dir: File
    private lateinit var store: VWingStore

    @Before fun setUp() {
        Access.role = ""
        dir = File(System.getProperty("java.io.tmpdir"), "vwing-ui-" + System.nanoTime()).apply { mkdirs() }
        store = VWingStore(dir, null, false)
    }

    @After fun tearDown() { Access.role = ""; dir.deleteRecursively() }

    private fun ctx(wide: Boolean, route: String = "setup") = VCtx(store, VNav(mutableStateListOf(route)), wide, { }, null, { }, { }, { })

    private fun setScreen(wide: Boolean, content: @androidx.compose.runtime.Composable (VCtx) -> Unit) {
        val c = ctx(wide)
        compose.setContent { VWingTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) { content(c) } } }
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

    // ---------- thành phần dùng chung ----------

    @Test @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun `banner ket luan hien dung noi dung o 4 muc`() {
        compose.setContent {
            VWingTheme {
                Column(Modifier.padding(16.dp)) {
                    VerdictBanner(Verdict.GOOD, "Ranh vườn hợp lệ · 1,20 ha", "Chu vi khoảng 440 m.")
                    VerdictBanner(Verdict.WARN, "1 cây nằm ngoài ranh vườn", "Xa ranh hơn 5 m.")
                    VerdictBanner(Verdict.BAD, "Ranh vườn có lỗi hình học", "Ranh giới tự cắt nhau.")
                    VerdictBanner(Verdict.UNKNOWN, "Chưa có cây nào có vị trí", null)
                }
            }
        }
        for (t in listOf("Ranh vườn hợp lệ · 1,20 ha", "1 cây nằm ngoài ranh vườn", "Ranh vườn có lỗi hình học", "Chưa có cây nào có vị trí", "Ranh giới tự cắt nhau."))
            compose.onNodeWithText(t).assertExists()
        shot("verdict-banner-phone")
    }

    @Test @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun `chip chon dung gia tri khi bam`() {
        val picked = mutableStateOf("Mật khẩu")
        compose.setContent { VWingTheme { ChipRow(listOf("Mật khẩu", "Mã SMS", "Quên mật khẩu"), picked.value, { it }, { picked.value = it }) } }
        compose.onNodeWithText("Mã SMS").performClick()
        compose.runOnIdle { assertEquals("Mã SMS", picked.value) }
    }

    // ---------- phân quyền hiện ra giao diện ----------

    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun `nut luu thong tin vuon bi khoa voi nhan vien va mo voi chu vuon`() {
        Access.role = "worker"
        setScreen(wide = false) { SetupScreen(it) }
        compose.onNodeWithText("Lưu thông tin vườn").assertIsNotEnabled()
        compose.onNodeWithText("Vai trò Nhân viên chỉ xem thông tin vườn; chủ vườn hoặc quản lý mới sửa được.").assertExists()
    }

    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun `chu vuon sua duoc thong tin vuon`() {
        Access.role = "owner"
        setScreen(wide = false) { SetupScreen(it) }
        compose.onNodeWithText("Lưu thông tin vườn").assertIsEnabled()
    }

    // ---------- màn Thiết lập ở 3 cỡ màn hình ----------

    @Test @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun `thiet lap vuon tren dien thoai doc`() {
        setScreen(wide = false) { SetupScreen(it) }
        compose.onNodeWithText("Điền theo vị trí của tôi").assertExists()
        shot("setup-phone-portrait")
    }

    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun `thiet lap vuon tren tablet doc`() {
        setScreen(wide = false) { SetupScreen(it) }
        compose.onNodeWithText("Công cụ bản đồ").assertExists()
        shot("setup-tablet-portrait")
    }

    @Test @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun `thiet lap vuon tren tablet ngang hai cot`() {
        setScreen(wide = true) { SetupScreen(it) }
        compose.onNodeWithText("Thông tin vườn, khu vực, cách hiển thị").assertExists()
        compose.onNodeWithText("Tài khoản và nhóm").assertExists()           // mỗi cột hai nhóm, cân bằng
        shot("setup-tablet-landscape")
    }

    // ---------- công cụ GIS: đối chiếu cây với ranh ----------

    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun `cong cu gis bao cay ngoai ranh va cay trong ranh`() {
        val ring = listOf(GeoPoint(12.0, 108.0), GeoPoint(12.0, 108.001), GeoPoint(12.001, 108.001), GeoPoint(12.001, 108.0))
        store.applyGeoImport(listOf(GeoFeature("b", FeatureKind.BOUNDARY, "Ranh", ring)), emptyList(), "test")
        store.addTree("T-01", "A", pos = GeoPoint(12.0005, 108.0005)); store.addTree("T-02", "A", pos = GeoPoint(12.0005, 108.002))
        setScreen(wide = false) { GisToolsCard(it) }
        compose.onNodeWithText("1 cây nằm ngoài ranh vườn").assertExists()
        compose.onNodeWithText("Xem cây ngoài ranh trên bản đồ").assertExists()
        shot("gis-tools-outside-tree")
    }

    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun `cong cu gis nhac ve ranh khi chua co ranh vuon`() {
        setScreen(wide = false) { GisToolsCard(it) }
        compose.onNodeWithText("Chưa có ranh vườn. Hãy chọn vùng vườn trên bản đồ hoặc đi bộ ghi ranh ở phần Vị trí & ranh vườn, rồi quay lại đây.").assertExists()
    }

    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun `nhom thiet lap mac dinh chi mo vuon cua toi va bam de mo nhom khac`() {
        setScreen(wide = false) { SetupScreen(it) }
        compose.onNodeWithText("Điền theo vị trí của tôi").assertExists()          // nhóm Vườn đang mở
        compose.onNodeWithText("Chọn vùng vườn trên bản đồ").assertDoesNotExist()  // nhóm Công cụ bản đồ đang thu gọn
        compose.onNodeWithText("Công cụ bản đồ").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Chọn vùng vườn trên bản đồ")[0].assertExists()          // tiêu đề mục và nút cùng tên
    }

    // ---------- bản đồ chuyên đề ----------

    private val themeRing = listOf(GeoPoint(11.0, 107.0), GeoPoint(11.002, 107.0), GeoPoint(11.002, 107.002), GeoPoint(11.0, 107.002))

    private fun themePanel(overlay: (MapOverlay?) -> Unit) {
        val c = ctx(wide = false)
        c.mapAnalysisSession.tab.value = "Bản đồ chuyên đề"
        compose.setContent {
            VWingTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    val regions = RegionAnalysis.regions(c.d, "")
                    RegionAnalysisPanel(c, regions, regions.firstOrNull(), { }, onOverlay = overlay, onCloud = { })
                }
            }
        }
    }

    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun `ban do chuyen de tao lop mat do tren ban do tu van de co vi tri`() {
        store.applyGeoImport(listOf(GeoFeature("b", FeatureKind.BOUNDARY, "Ranh", themeRing)), emptyList(), "test")
        store.addTree("T-01", "A", pos = GeoPoint(11.0010, 107.0010)); store.addTree("T-02", "A", pos = GeoPoint(11.0012, 107.0011))
        val ids = store.data.trees.map { it.id }
        store.addIssue(listOf(ids[0]), null, "Lá vàng", Priority.HIGH, "", null); store.addIssue(listOf(ids[1]), null, "Sâu đục", Priority.MEDIUM, "", null)
        var overlay: MapOverlay? = null
        themePanel { overlay = it }
        compose.onNodeWithText("Tạo bản đồ").performScrollTo().performClick()
        compose.waitUntil(10_000) { overlay != null }
        compose.onNodeWithText("Độ đậm lớp bản đồ").assertExists()
        assertEquals(true, overlay!!.bounds.valid)
        assertEquals(true, overlay!!.bitmap.width >= 2 && overlay!!.bitmap.height >= 2)
        shot("thematic-kde")
    }

    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun `ban do chuyen de bao thieu du lieu thay vi ve ban do rong`() {
        store.applyGeoImport(listOf(GeoFeature("b", FeatureKind.BOUNDARY, "Ranh", themeRing)), emptyList(), "test")
        var overlay: MapOverlay? = null
        themePanel { overlay = it }
        compose.onNodeWithText("Tạo bản đồ").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Chưa có vấn đề nào gắn với cây có toạ độ trong vùng này.").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(null, overlay)
    }

    // ---------- địa hình và đường đi (máy chủ vệ tinh giả trong JVM) ----------

    private fun demJson(): String {
        val n = 40
        val vals = org.json.JSONArray().also { a -> for (r in 0 until n) for (c in 0 until n) a.put(2.0 * kotlin.math.abs(c - 20) + 0.5 * (n - 1 - r) + 100.0) }
        return org.json.JSONObject().put("bounds", org.json.JSONObject().put("south", 10.9995).put("west", 106.9995).put("north", 11.0035).put("east", 107.0035))
            .put("rows", n).put("cols", n).put("values", vals).put("cell_m", 30.0).put("source", "TEST DEM").put("simulated", false)
            .put("warnings", org.json.JSONArray()).put("caution", "c").toString()
    }

    private fun ringJson(lat: Double, lng: Double, d: Double) = org.json.JSONArray().also { a ->
        listOf(lat - d to lng - d, lat - d to lng + d, lat + d to lng + d, lat + d to lng - d, lat - d to lng - d).forEach { (la, lo) -> a.put(org.json.JSONArray().put(lo).put(la)) }
    }

    private fun fakeServer(): com.sun.net.httpserver.HttpServer {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { e ->
            val body = e.requestBody.readBytes().decodeToString()
            val out = when (e.requestURI.path) {
                "/v1/terrain/dem" -> demJson()
                "/v1/routing/isochrone" -> org.json.JSONObject().put("profile", "motor_scooter").put("attribution", "© OSM").put("caution", "ước tính")
                    .put("origins", org.json.JSONArray().put(org.json.JSONObject().put("name", "Tâm vườn").put("lat", 11.001).put("lng", 107.001).put("contours", org.json.JSONArray()
                        .put(org.json.JSONObject().put("minutes", 10).put("ring", ringJson(11.001, 107.001, .004)).put("area_ha", 80.0))
                        .put(org.json.JSONObject().put("minutes", 20).put("ring", ringJson(11.001, 107.001, .01)).put("area_ha", 500.0))
                        .put(org.json.JSONObject().put("minutes", 30).put("ring", ringJson(11.001, 107.001, .02)).put("area_ha", 2000.0))))).toString()
                "/v1/routing/matrix" -> {
                    val ns = org.json.JSONObject(body).getJSONArray("sources").length()
                    org.json.JSONObject().put("profile", "motor_scooter").put("attribution", "© OSM")
                        .put("minutes", org.json.JSONArray().also { a -> repeat(ns) { a.put(org.json.JSONArray().put(12.0)) } })
                        .put("km", org.json.JSONArray().also { a -> repeat(ns) { a.put(org.json.JSONArray().put(5.5)) } }).toString()
                }
                else -> "{}"
            }
            val b = out.toByteArray(); e.responseHeaders.add("Content-Type", "application/json"); e.sendResponseHeaders(200, b.size.toLong()); e.responseBody.use { it.write(b) }; e.close()
        }
        server.start(); return server
    }

    private fun seedGarden() {
        store.applyGeoImport(listOf(GeoFeature("b", FeatureKind.BOUNDARY, "Ranh", themeRing)), emptyList(), "test")
        store.addTree("T-01", "Khu A", fruits = 80, pos = GeoPoint(11.0010, 107.0010)); store.addTree("T-02", "Khu B", fruits = 40, pos = GeoPoint(11.0015, 107.0016))
    }

    private fun appFiles() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().filesDir

    private fun connectTo(url: String) { SatelliteSettings(appFiles()).save(SatelliteConfig(url, "private-test-token")) }

    private fun connect(server: com.sun.net.httpserver.HttpServer) {
        SatelliteSettings(appFiles()).save(SatelliteConfig("http://127.0.0.1:${server.address.port}", "private-test-token"))
    }

    private fun openTab(tab: String, overlay: (MapOverlay?) -> Unit) {
        val c = ctx(wide = false); c.mapAnalysisSession.tab.value = tab
        compose.setContent {
            VWingTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    val regions = RegionAnalysis.regions(c.d, "")
                    RegionAnalysisPanel(c, regions, regions.firstOrNull(), { }, onOverlay = overlay, onCloud = { })
                }
            }
        }
    }

    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun `dia hinh tai DEM roi tinh tam nhin va nhay cam ngap lut`() {
        val server = fakeServer()
        try {
            connect(server); seedGarden()
            var overlay: MapOverlay? = null
            openTab("Địa hình") { overlay = it }
            compose.onNodeWithText("Tải địa hình (${com.example.test_tablet.data.vwing.DemSourceKey.COPERNICUS_GLO30.shortLabel})").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Tải lại (${com.example.test_tablet.data.vwing.DemSourceKey.COPERNICUS_GLO30.shortLabel})").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Tính tầm nhìn").performScrollTo().performClick()
            compose.waitUntil(10_000) { overlay != null }
            compose.onNodeWithText("Độ đậm lớp bản đồ").assertExists()
            assertEquals(40, overlay!!.bitmap.width)
            shot("terrain-viewshed")
            overlay = null
            compose.onAllNodesWithText("Nhạy cảm ngập lụt")[0].performScrollTo().performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Tính nhạy cảm ngập lụt").performScrollTo().performClick()
            compose.waitUntil(10_000) { overlay != null }
            assertEquals("Nhạy cảm ngập lụt", overlay!!.label)
            shot("terrain-flood")
        } finally { server.stop(0) }
    }

    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun `dia hinh bao thieu ket noi thay vi treo`() {
        // App có máy chủ mặc định nên không còn trạng thái "chưa cấu hình"; điều cần giữ là máy chủ không với tới thì báo lỗi, không treo ở "Đang tải".
        connectTo("http://127.0.0.1:1")
        seedGarden()
        openTab("Địa hình") { }
        val label = "Tải địa hình (${com.example.test_tablet.data.vwing.DemSourceKey.COPERNICUS_GLO30.shortLabel})"
        compose.onNodeWithText(label).performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Không kết nối được máy chủ", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(label).assertIsEnabled()
    }

    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun `duong di tao vung phuc vu va dong chay diem di den`() {
        val server = fakeServer()
        try {
            connect(server); seedGarden()
            PlaceStore(appFiles()).save(listOf(Place("Chợ Buôn Hồ", GeoPoint(11.05, 107.05))))
            var overlay: MapOverlay? = null
            openTab("Đường đi & dòng chảy") { overlay = it }
            compose.onNodeWithText("Tạo vùng phục vụ").performScrollTo().performClick()
            compose.waitUntil(10_000) { overlay != null }
            assertEquals(true, overlay!!.label.startsWith("Vùng phục vụ"))
            shot("access-service-area")
            overlay = null
            compose.onNodeWithText("Dòng chảy điểm đi–đến").performScrollTo().performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Tính dòng chảy").performScrollTo().performClick()
            compose.waitUntil(10_000) { overlay != null }
            assertEquals(true, overlay!!.label.startsWith("Dòng chảy"))
            shot("access-od-flow")
        } finally { server.stop(0) }
    }

    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun `dong chay bao thieu diem den`() {
        val server = fakeServer()
        try {
            connect(server); seedGarden()
            File(appFiles(), "vwing_places.json").delete()
            var overlay: MapOverlay? = null
            openTab("Đường đi & dòng chảy") { overlay = it }
            compose.onNodeWithText("Dòng chảy điểm đi–đến").performScrollTo().performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Tính dòng chảy").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Thêm ít nhất một điểm đến (chợ, kho, nhà máy…) ở danh sách điểm quan trọng.").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(null, overlay)
        } finally { server.stop(0) }
    }

    @Test
    fun `nhan chu hoa cu duoc doi thanh cau thuong nhung giu chu viet tat`() {
        assertEquals("Thông tin vườn", humanize("THÔNG TIN VƯỜN"))
        assertEquals("Công cụ GIS", humanize("CÔNG CỤ GIS"))
        assertEquals("Tài khoản & đồng bộ", humanize("TÀI KHOẢN & ĐỒNG BỘ"))
        assertEquals("Mã QR công khai", humanize("MÃ QR CÔNG KHAI"))
        assertEquals("Đã viết thường sẵn", humanize("Đã viết thường sẵn"))
    }

    // ---------- trang chủ với dữ liệu mẫu ở 4 cỡ màn hình: ảnh để soát bằng mắt ----------
    private fun overviewShot(name: String, wide: Boolean) {
        store = VWingStore(dir, null, true)
        setScreen(wide = wide) { OverviewScreen(it) }
        compose.onNode(androidx.compose.ui.test.hasText("Việc cần làm hôm nay", substring = true)).assertExists()
        shot(name)
    }

    @Test @Config(qualifiers = "w360dp-h800dp-xhdpi") fun `trang chu dien thoai doc`() = overviewShot("overview-phone-portrait", false)
    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi") fun `trang chu tablet doc`() = overviewShot("overview-tablet-portrait", false)
    @Test @Config(qualifiers = "w1280dp-h800dp-mdpi") fun `trang chu tablet ngang`() = overviewShot("overview-tablet-landscape", true)

    // ---------- khung ứng dụng đầy đủ (thanh điều hướng + đầu trang) ở các cỡ màn hình ----------
    private fun appShot(name: String) {
        compose.setContent { VWingApp({ }, { }) }
        compose.waitForIdle()
        compose.onNode(androidx.compose.ui.test.hasText("Việc cần làm hôm nay", substring = true)).assertExists()
        shot(name)
    }

    @Test @Config(qualifiers = "w1280dp-h800dp-mdpi") fun `khung ung dung tablet ngang co thanh xanh va Thiet lap co dinh`() {
        Access.role = ""
        appShot("app-tablet-landscape")
        compose.onNodeWithText("Cài đặt vườn").assertExists()                 // mục Thiết lập luôn nằm ở đáy thanh điều hướng
    }

    @Test @Config(qualifiers = "w360dp-h800dp-xhdpi") fun `khung ung dung dien thoai co thanh duoi`() = appShot("app-phone-portrait")

    @Test @Config(qualifiers = "w1280dp-h800dp-mdpi") fun `nhan vien khong thay muc San luong`() {
        // vai trò nhân viên đến từ cấu hình đăng nhập (app nạp lại vai trò khi mở), nên ghi cấu hình thay vì đặt thẳng Access.role
        val files = ApplicationProvider.getApplicationContext<android.content.Context>().filesDir
        File(files, "vwing_sync.json").writeText("""{"url":"http://127.0.0.1:1","gardenKey":"vuon-test","token":"t","role":"worker","userName":"Công"}""")
        try {
            compose.setContent { VWingApp({ }, { }) }
            compose.waitForIdle()
            compose.onNodeWithText("Sản lượng & giá").assertDoesNotExist()
        } finally { File(files, "vwing_sync.json").delete() }
        compose.onNodeWithText("Bản đồ vườn cây").assertExists()
    }
}
