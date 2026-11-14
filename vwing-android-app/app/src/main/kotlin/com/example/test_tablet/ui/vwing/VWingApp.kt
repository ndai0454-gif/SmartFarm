package com.example.test_tablet.ui.vwing

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.SatelliteAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.R
import com.example.test_tablet.data.vwing.Access
import com.example.test_tablet.data.vwing.BillingCycle
import com.example.test_tablet.data.vwing.PlanTier
import com.example.test_tablet.data.vwing.UiSettings
import com.example.test_tablet.data.vwing.UiSettingsStore
import com.example.test_tablet.data.vwing.VFeature
import com.example.test_tablet.data.vwing.VWingStore
import com.example.test_tablet.data.vwing.Condition
import com.example.test_tablet.data.vwing.Dates
import com.example.test_tablet.data.vwing.activeTrees
import com.example.test_tablet.data.vwing.normalizeCode
import com.example.test_tablet.data.vwing.treesNeedingVisitCount
import com.example.test_tablet.data.vwing.summary
import com.example.test_tablet.data.vwing.todayWork
import com.example.test_tablet.data.vwing.zoneNames
import kotlinx.coroutines.launch

/** Ngăn xếp điều hướng bằng chuỗi: "overview", "tree|<id>", "issues|tasks"... */
open class VNav(val stack: SnapshotStateList<String>, private val canOpenRoute: (String) -> Boolean = { true }) {
    /** Hướng của lần điều hướng gần nhất: 1 = đi vào, -1 = quay lại, 0 = chuyển mục chính (để chọn hiệu ứng chuyển cảnh). */
    var dir = 0
    open val current get() = stack.last()
    open val head get() = current.substringBefore('|')
    open val arg get() = current.substringAfter('|', "")
    fun canNavigate(r: String): Boolean = canOpenRoute(r)
    fun push(r: String) {
        if (!canOpenRoute(r)) return
        if (stack.last() != r) { dir = 1; stack.add(r) }
    }
    fun pop(): Boolean { if (stack.size > 1) { dir = -1; stack.removeAt(stack.lastIndex); return true }; return false }
    fun top(r: String) {
        if (!canOpenRoute(r)) return
        dir = 0; stack.clear(); stack.add(r)
    }
}

/** Cái nhìn của một màn hình lên ngăn xếp: giữ nguyên route của màn đó trong lúc hiệu ứng chuyển cảnh còn chạy. */
private class RoutedNav(private val base: VNav, private val route: String) : VNav(base.stack, { r -> base.canNavigate(r) }) {
    override val current get() = route
}

class VCtx(
    val store: VWingStore,
    val nav: VNav,
    val wide: Boolean,
    val toast: (String) -> Unit,
    val selectedTree: String?,
    val selectTree: (String?) -> Unit,
    val openFarmer: () -> Unit,
    val openOps: () -> Unit,
    val mapAnalysisSession: MapAnalysisSession = MapAnalysisSession(),
    /** Chế độ đơn giản (mặc định bật): màn hình GIS/vệ tinh/drone ẩn số liệu kỹ thuật, chỉ hiện kết luận. */
    val simpleMode: Boolean = true,
    val setSimpleMode: (Boolean) -> Unit = {},
    /** Chế độ khung Điện thoại (cho phép chuyển đổi 1 chạm giữa giao diện Điện thoại và Tablet). */
    val phoneMode: Boolean = false,
    val setPhoneMode: (Boolean) -> Unit = {},
    val canTogglePhoneMode: Boolean = true,
    /** Cấu hình gói cước (Free / Enterprise) và Feature Flags (On/Off từng tính năng). */
    val uiSettings: UiSettings = UiSettings(),
    val updateUiSettings: ((UiSettings) -> UiSettings) -> Unit = {},
    val requestFeature: (VFeature?) -> Unit = {},
    val openFarmerAuth: () -> Unit = {},
) {
    val d get() = store.data
    fun isFeatureEnabled(f: VFeature): Boolean = uiSettings.isFeatureEnabled(f)
    fun withNav(n: VNav) = VCtx(
        store, n, wide, toast, selectedTree, selectTree, openFarmer, openOps, mapAnalysisSession,
        simpleMode, setSimpleMode, phoneMode, setPhoneMode, canTogglePhoneMode,
        uiSettings, updateUiSettings, requestFeature, openFarmerAuth,
    )
}

private class NavItem(
    val route: String,
    val label: String,
    val section: String,
    val icon: ImageVector,
    val iconOn: ImageVector,
    val short: String = label,
    val farmerLabel: String = label,
    val primaryForFarmer: Boolean = true,
)

private val NAV = listOf(
    NavItem("overview", "Tổng quan", "KHÔNG GIAN LÀM VIỆC", Icons.Outlined.Dashboard, Icons.Filled.Dashboard, "Vườn", farmerLabel = "Vườn của tôi", primaryForFarmer = true),
    NavItem("map", "Khoanh vườn & Bản đồ GEE", "KHÔNG GIAN LÀM VIỆC", Icons.Outlined.Map, Icons.Filled.Map, "Khoanh vườn", farmerLabel = "Khoanh vườn", primaryForFarmer = true),
    NavItem("qr", "Quét mã QR cây trồng", "KHÔNG GIAN LÀM VIỆC", Icons.Outlined.QrCodeScanner, Icons.Filled.QrCodeScanner, "Quét QR", farmerLabel = "Quét mã QR cây", primaryForFarmer = true),
    NavItem("issues", "Vấn đề & kiểm tra", "KHÔNG GIAN LÀM VIỆC", Icons.Outlined.Checklist, Icons.Filled.Checklist, "Cần thăm", farmerLabel = "Cây cần thăm", primaryForFarmer = true),
    NavItem("yield", "Sản lượng & giá", "KHÔNG GIAN LÀM VIỆC", Icons.Outlined.Insights, Icons.Filled.Insights, "Thu hoạch", farmerLabel = "Thu hoạch & Tiền", primaryForFarmer = true),
    NavItem("ai", "Hỏi VWing AI", "TRỢ LÝ THÔNG MINH", Icons.Outlined.QuestionAnswer, Icons.Filled.QuestionAnswer, "Hỏi AI", farmerLabel = "Hỏi Kỹ sư AI", primaryForFarmer = true),
    NavItem("workflow", "Quy trình 6 Bước & 3D 7 Tầng", "KHÔNG GIAN LÀM VIỆC", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome, "Quy trình", farmerLabel = "Quy trình 6 bước", primaryForFarmer = false),
    NavItem("satellite", "Vệ tinh & phân tích", "DỮ LIỆU THỰC ĐỊA", Icons.Outlined.SatelliteAlt, Icons.Filled.SatelliteAlt, "Vệ tinh", farmerLabel = "Ảnh vệ tinh vườn", primaryForFarmer = false),
    NavItem("weather", "Thời tiết hôm nay", "DỮ LIỆU THỰC ĐỊA", Icons.Outlined.WbSunny, Icons.Filled.WbSunny, "Thời tiết", farmerLabel = "Thời tiết hôm nay", primaryForFarmer = false),
    NavItem("drone", "Drone Đa phổ & Phun VRA", "DỮ LIỆU THỰC ĐỊA", Icons.Outlined.FlightTakeoff, Icons.Filled.FlightTakeoff, "Bay & Phun", farmerLabel = "Bay chụp & Phun thuốc", primaryForFarmer = false),
    NavItem("log", "Ghi hoạt động", "KHÔNG GIAN LÀM VIỆC", Icons.Outlined.EditNote, Icons.Filled.EditNote, "Ghi sổ", farmerLabel = "Ghi sổ làm vườn", primaryForFarmer = false),
    NavItem("market", "Giá thị trường", "KHÔNG GIAN LÀM VIỆC", Icons.Outlined.ShowChart, Icons.Filled.ShowChart, "Giá bán", farmerLabel = "Giá thị trường", primaryForFarmer = false),
    NavItem("disease", "Dự báo bệnh cây", "DỮ LIỆU THỰC ĐỊA", Icons.Outlined.BugReport, Icons.Filled.BugReport, "Sâu bệnh", farmerLabel = "Dự báo sâu bệnh", primaryForFarmer = false),
    NavItem("records", "Lịch sử chăm sóc", "KHÔNG GIAN LÀM VIỆC", Icons.Outlined.History, Icons.Filled.History, "Nhật ký", farmerLabel = "Lịch sử chăm sóc", primaryForFarmer = false),
    NavItem("imagery", "Ảnh & chỉ số NDVI", "DỮ LIỆU THỰC ĐỊA", Icons.Outlined.Layers, Icons.Filled.Layers, "Ảnh lá", farmerLabel = "So sánh ảnh chụp", primaryForFarmer = false),
    NavItem("env", "Nước, đất & thời tiết", "DỮ LIỆU THỰC ĐỊA", Icons.Outlined.WaterDrop, Icons.Filled.WaterDrop, "Nước & đất", farmerLabel = "Nước tưới & Đất", primaryForFarmer = false),
    NavItem("setup", "Thiết lập vườn", "THỰC ĐỊA & CÔNG CỤ", Icons.Outlined.Settings, Icons.Filled.Settings, "Cài đặt", farmerLabel = "Cài đặt vườn", primaryForFarmer = true),
)

/** Bốn mục chính ở thanh dưới (Quét QR nằm chính giữa ở vị trí 3/5); các mục còn lại nằm trong "Thêm". */
private val BOTTOM = listOf("overview", "map", "qr", "ai")

private fun titleOf(head: String, simpleMode: Boolean = true): String {
    val sh = AppStrings.shell(VUi.country)
    val fallback = when (head) {
        "tree" -> "Hồ sơ cây"
        "issue" -> "Chi tiết vấn đề"
        "imageset" -> "Bộ ảnh"
        "ndvi" -> if (simpleMode) "Sức khỏe lá cây" else "Chỉ số NDVI"
        "drone" -> if (simpleMode) "Bay chụp & Phun thuốc tiết kiệm" else "Drone Đa phổ & Phun VRA"
        else -> NAV.firstOrNull { it.route == head }?.let { if (simpleMode) it.farmerLabel else it.label } ?: "VWing"
    }
    return sh.fullLabel(head, fallback)
}

private fun activeHead(head: String) = when (head) { "tree" -> "map"; "issue" -> "issues"; "imageset", "ndvi" -> "imagery"; else -> head }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VWingApp(onOpenFarmer: () -> Unit, onOpenOps: () -> Unit, startOnLanding: Boolean = false) {
    VWingTheme {
        val context = LocalContext.current
        val activity = context as? android.app.Activity
        val intent = activity?.intent
        val store = remember { VWingStore.get(context) }
        val exportRouteExtra = intent?.getStringExtra("export_route")
        val exportDialogExtra = intent?.getStringExtra("export_dialog")
        val exportTierExtra = intent?.getStringExtra("export_tier")
        val exportLoginExtra = intent?.getBooleanExtra("export_login", false) ?: false
        val exportWideExtra = if (intent?.hasExtra("export_wide") == true) intent.getBooleanExtra("export_wide", false) else null

        val resolvedInitRoute = remember(exportRouteExtra) {
            when (exportRouteExtra) {
                null, "" -> "overview"
                "tree" -> "tree|${store.data.activeTrees.firstOrNull()?.id ?: "t-a03"}"
                "issue" -> "issue|${store.data.issues.firstOrNull()?.id ?: "iss-1"}"
                else -> exportRouteExtra
            }
        }
        var showLanding by rememberSaveable { mutableStateOf(startOnLanding) }
        val stack = rememberSaveable(saver = listSaver<SnapshotStateList<String>, String>(save = { it.toList() }, restore = { it.toMutableStateList() })) {
            mutableStateListOf(resolvedInitRoute)
        }
        var selected by rememberSaveable { mutableStateOf<String?>(null) }
        val snack = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        var showMore by remember { mutableStateOf(exportDialogExtra == "more") }
        val mapAnalysisSession = rememberSaveable(saver = MapAnalysisSession.Saver) { MapAnalysisSession() }
        val uiSettingsStore = remember { UiSettingsStore(context.filesDir) }
        var uiSettings by remember {
            val loaded = uiSettingsStore.load()
            val tierOverride = when (exportTierExtra) {
                "FREE" -> PlanTier.FREE
                "PRO" -> PlanTier.ENTERPRISE
                else -> loaded.planTier
            }
            mutableStateOf(loaded.copy(planTier = tierOverride))
        }
        val simpleMode = uiSettings.simpleMode
        var phoneMode by rememberSaveable { mutableStateOf(false) }
        var paywallFeature by remember { mutableStateOf<VFeature?>(null) }
        var pendingLockedRoute by remember { mutableStateOf<String?>(null) }
        var showPlanDialog by remember { mutableStateOf(exportDialogExtra == "plan") }
        var showFarmerAuthDialog by remember { mutableStateOf(exportDialogExtra == "auth") }
        VUi.simpleMode = simpleMode
        // Chế độ Nông dân (simpleMode = true) mặc định dùng giao diện sáng Daylight dễ đọc ngoài nắng;
        // Chế độ Kỹ sư (simpleMode = false) trên gói Pro dùng giao diện tối Aurora.
        VC.isPro = (exportTierExtra == "PRO") || (!simpleMode && uiSettings.planTier == PlanTier.ENTERPRISE)
        if (exportDialogExtra == "search") {
            VUi.searchOpen = true
        }

        androidx.compose.runtime.LaunchedEffect(Unit) {
            com.example.test_tablet.data.vwing.FirebaseFarmerRepo.initializeAndAttachStore(context, store)
            if (exportRouteExtra != null || exportDialogExtra != null) {
                com.example.test_tablet.data.vwing.FirebaseFarmerRepo.dismissLoginGate()
            }
        }

        val updateSettings: ((UiSettings) -> UiSettings) -> Unit = { transform ->
            val next = transform(uiSettings)
            uiSettings = next
            VUi.simpleMode = next.simpleMode
            VC.isPro = (exportTierExtra == "PRO") || (!next.simpleMode && next.planTier == PlanTier.ENTERPRISE)
            uiSettingsStore.save(next)
        }

        val activeFarmerKey = com.example.test_tablet.data.vwing.FirebaseFarmerRepo.currentFarmer?.phoneKey
        val activeFarmerTier = com.example.test_tablet.data.vwing.FirebaseFarmerRepo.currentFarmer?.planTier
        androidx.compose.runtime.LaunchedEffect(activeFarmerKey, activeFarmerTier) {
            if (exportTierExtra == null && activeFarmerTier != null && (uiSettings.planTier != activeFarmerTier || !uiSettings.simpleMode)) {
                updateSettings { cur -> cur.copy(simpleMode = true, planTier = activeFarmerTier, enabledFeatures = emptySet(), disabledFeatures = emptySet()) }
            }
        }

        val nav = remember(stack) {
            VNav(stack) { r ->
                val req = VFeature.forRoute(r)
                if (req != null && !uiSettings.isFeatureEnabled(req)) {
                    paywallFeature = req
                    pendingLockedRoute = r
                    showPlanDialog = true
                    false
                } else {
                    true
                }
            }
        }

        BackHandler(enabled = !showLanding && (stack.size > 1 || startOnLanding)) {
            if (!nav.pop() && startOnLanding) {
                showLanding = true
            }
        }

        if (showLanding) {
            LandingScreen(
                onEnterOverview = { initialRoute ->
                    nav.top(initialRoute)
                    showLanding = false
                }
            )
            return@VWingTheme
        }

        if (exportLoginExtra || (exportRouteExtra == null && exportDialogExtra == null && com.example.test_tablet.data.vwing.FirebaseFarmerRepo.showLoginGate)) {
            FarmerPhoneOtpLoginScreen(
                store = store,
                onLoginSuccess = { profile ->
                    updateSettings { cur -> cur.copy(simpleMode = true, planTier = profile.planTier, enabledFeatures = emptySet(), disabledFeatures = emptySet()) }
                    com.example.test_tablet.data.vwing.FirebaseFarmerRepo.dismissLoginGate()
                    scope.launch {
                        snack.showSnackbar("🎉 Đã mở sổ vườn của số ${profile.displayPhone}")
                    }
                },
                onSkipDemo = {
                    com.example.test_tablet.data.vwing.FirebaseFarmerRepo.dismissLoginGate()
                },
            )
            return@VWingTheme
        }

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true }
                .background(if (phoneMode) Color(0xFF04121B) else VC.bg)
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.isCtrlPressed && e.key == Key.K) { VUi.searchOpen = true; true } else false
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            val isPhysicalTablet = maxWidth >= 600.dp
            val wide = exportWideExtra ?: (!phoneMode && maxWidth >= 840.dp)
            val ctx = VCtx(
                store, nav, wide, { m -> scope.launch { snack.showSnackbar(m) } }, selected, { selected = it }, onOpenFarmer, onOpenOps, mapAnalysisSession,
                simpleMode, { v -> updateSettings { it.copy(simpleMode = v) } },
                phoneMode, { v -> phoneMode = v },
                canTogglePhoneMode = isPhysicalTablet,
                uiSettings = uiSettings,
                updateUiSettings = updateSettings,
                requestFeature = { f ->
                    paywallFeature = f
                    pendingLockedRoute = null
                    showPlanDialog = true
                },
                openFarmerAuth = { showFarmerAuthDialog = true },
            )
            val active = activeHead(nav.head)
            AutoSyncOnOpen(ctx)

            val shellModifier = if (phoneMode && isPhysicalTablet) {
                Modifier
                    .width(420.dp)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .border(1.5.dp, if (VC.isPro) Color.White.copy(alpha = 0.24f) else VC.border, RoundedCornerShape(32.dp))
            } else {
                Modifier.fillMaxSize()
            }

            Box(modifier = shellModifier.background(VC.bg)) {
                if (VC.isPro) {
                    // Gói Pro (Enterprise): Lớp nền Aurora đại dương - rừng già đồng nhất cho toàn bộ ứng dụng
                    Image(
                        painter = painterResource(R.drawable.storm_background),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = 0.56f,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF04121B).copy(alpha = 0.80f),
                                        Color(0xFF061722).copy(alpha = 0.72f),
                                        Color(0xFF04121B).copy(alpha = 0.88f),
                                    )
                                )
                            )
                    )
                }

                Scaffold(
                    containerColor = Color.Transparent,
                    snackbarHost = { SnackbarHost(snack) },
                    bottomBar = { if (!wide) BottomBar(active, nav, onMore = { showMore = true }) },
                    floatingActionButton = {
                        val showQuickLogFab = nav.head in setOf(
                            "records",
                            "tree",
                            "env",
                            "weather",
                            "disease",
                            "yield",
                            "market",
                        )
                        AnimatedVisibility(
                            visible = showQuickLogFab,
                            enter = scaleIn(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                            exit = scaleOut(tween(150)) + fadeOut(tween(110)),
                        ) {
                            ExtendedFloatingActionButton(
                                onClick = { nav.push("log") },
                                containerColor = VC.pine,
                                contentColor = Color.White,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .testTag("fab_log_garden")
                                    .semantics { contentDescription = if (simpleMode) "Ghi sổ vườn" else "Ghi hoạt động" }
                                    .border(1.dp, if (VC.isPro) VC.mint.copy(alpha = 0.55f) else VC.pine, RoundedCornerShape(16.dp)),
                                elevation = FloatingActionButtonDefaults.elevation(4.dp, 2.dp),
                                icon = { Icon(Icons.Filled.Add, null, tint = if (VC.isPro) VC.mint else Color.White, modifier = Modifier.size(18.dp)) },
                                text = { Text(if (simpleMode) "Ghi sổ vườn" else "Ghi hoạt động", fontWeight = FontWeight.SemiBold, fontSize = 14.sp) },
                            )
                        }
                    },
                ) { pad ->
                    Row(Modifier.fillMaxSize().padding(pad)) {
                        if (wide) Sidebar(active, nav, ctx, onLogoClick = { showLanding = true })
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            Header(ctx, active, onLogoClick = { showLanding = true })
                            val sheetMod = if (wide) {
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(end = 10.dp, bottom = 10.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(if (VC.isPro) Color(0xFF051520).copy(alpha = 0.50f) else Color.White.copy(alpha = 0.72f))
                                    .border(1.dp, VC.border, RoundedCornerShape(22.dp))
                            } else {
                                Modifier.weight(1f).fillMaxWidth()
                            }
                            AnimatedContent(
                                targetState = nav.current,
                                modifier = sheetMod,
                                transitionSpec = {
                                    when (nav.dir) {
                                        1 -> (slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { it / 9 } + fadeIn(tween(200))) togetherWith
                                            (slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { -it / 14 } + fadeOut(tween(120)))
                                        -1 -> (slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { -it / 9 } + fadeIn(tween(200))) togetherWith
                                            (slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { it / 14 } + fadeOut(tween(120)))
                                        else -> fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                                    }.using(SizeTransform(clip = false))
                                },
                                label = "screens",
                            ) { route ->
                                val routed = remember(route, wide, selected, phoneMode, simpleMode, uiSettings) { ctx.withNav(RoutedNav(nav, route)) }
                                if (routed.nav.head == "map") {
                                    // Màn hình Khoanh vườn (Google Map + GEE HD) thay thế hoàn toàn màn hình Bản đồ cũ
                                    Box(Modifier.fillMaxSize()) {
                                        BoundaryPickerScreen(routed, isDialog = false)
                                    }
                                } else {
                                    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                        Column(
                                            Modifier.padding(
                                                start = if (wide) 24.dp else 14.dp,
                                                end = if (wide) 24.dp else 14.dp,
                                                top = if (wide) 20.dp else 10.dp,
                                                // Sửa lỗi nội dung bị nút Ghi hoạt động (FAB) đè lên: chừa đủ chiều cao
                                                // cho FAB ở cuối trang (chiều cao thanh điều hướng dưới đã được Scaffold pad xử lý).
                                                bottom = if (wide) 110.dp else 116.dp,
                                            )
                                        ) {
                                            Screens(routed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (VUi.searchOpen) QuickSearch(ctx) { VUi.searchOpen = false }
            if (VUi.countryPickerOpen) {
                CountryPickerDialog(
                    current = VUi.country,
                    onSelect = { chosen ->
                        VUi.country = chosen
                        AppLocaleStore.save(context, chosen)
                        java.util.Locale.setDefault(chosen.toLocale())
                        VUi.countryPickerOpen = false
                        ctx.toast(AppStrings.forCountry(chosen).switchedToast(chosen))
                    },
                    onDismiss = { VUi.countryPickerOpen = false },
                )
            }

            if (showMore) {
                ModalBottomSheet(onDismissRequest = { showMore = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = VC.card, shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)) {
                    MoreSheet(active, ctx, onPick = { showMore = false; nav.top(it) })
                }
            }

            if (showPlanDialog) {
                PlanManagerDialog(
                    c = ctx,
                    paywallFeature = paywallFeature,
                    onDismiss = {
                        showPlanDialog = false
                        paywallFeature = null
                        pendingLockedRoute = null
                    },
                    onUnlockedFeature = {
                        val routeToOpen = pendingLockedRoute
                        val feat = paywallFeature
                        showPlanDialog = false
                        paywallFeature = null
                        pendingLockedRoute = null
                        if (feat == VFeature.ENGINEER_MODE) {
                            ctx.setSimpleMode(false)
                            ctx.toast("Đã mở khóa và bật Chế độ Kỹ sư.")
                        } else if (routeToOpen != null) {
                            nav.top(routeToOpen)
                        }
                    },
                )
            }

            if (showFarmerAuthDialog) {
                FarmerAuthMapDialog(
                    c = ctx,
                    onDismiss = { showFarmerAuthDialog = false },
                )
            }
        }
    }
}

/** Tìm nhanh (Ctrl+K hoặc nút kính lúp): nhảy tới cây theo mã/khu, tới màn hình, hoặc tới chỉ số quang phổ. */
@Composable
private fun QuickSearch(c: VCtx, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val d = c.d
    val query = q.trim()
    val norm = normalizeCode(query)
    val trees = if (query.isEmpty()) emptyList() else d.activeTrees.filter {
        it.code.contains(query, ignoreCase = true) || it.zone.contains(query, ignoreCase = true) || (norm != null && normalizeCode(it.code) == norm)
    }.take(6)
    val indices = setOf("NDVI", "GNDVI", "NDRE", "NDMI", "EVI", "VARI", "SAVI", "NDWI", "BSI", "VCI", "TCI", "VHI", "LST", "VRA")
    val screens = visibleNav().filter {
        query.isEmpty() || it.label.contains(query, true) || it.short.contains(query, true) ||
            ((it.route == "imagery" || it.route == "satellite" || it.route == "drone") && query.uppercase() in indices)
    }.take(6)
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(VC.card).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VField(q, { q = it }, "Tìm cây (A-04), khu, hoặc màn hình / chỉ số (NDVI)")
            if (trees.isNotEmpty()) SectionLabel("Cây")
            trees.forEach { t ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).bounceClick { c.selectTree(t.id); c.nav.push("tree|${t.id}"); onClose() }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Cây ${t.code}", color = VC.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(t.zone, color = VC.text2, fontSize = 15.sp)
                }
            }
            SectionLabel("Màn hình")
            screens.forEach { n ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).bounceClick { c.nav.top(n.route); onClose() }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(n.icon, null, tint = VC.lime, modifier = Modifier.size(24.dp))
                    Text(AppStrings.shell(VUi.country).fullLabel(n.route, n.label), color = VC.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun Screens(c: VCtx) {
    when (c.nav.head) {
        "overview" -> OverviewScreen(c)
        "workflow" -> AuroraWorkflowScreen(c)
        "map" -> BoundaryPickerScreen(c, isDialog = false)
        "qr" -> QrScannerScreen(c)
        "tree" -> TreeProfileScreen(c, c.d.activeTrees.firstOrNull { it.id == c.nav.arg }?.id ?: c.d.activeTrees.firstOrNull()?.id ?: c.nav.arg)
        "records" -> RecordsScreen(c)
        "issues" -> IssuesScreen(c, c.nav.arg.ifEmpty { "issues" })
        "issue" -> IssueDetailScreen(c, c.d.issues.firstOrNull { it.id == c.nav.arg }?.id ?: c.d.issues.firstOrNull()?.id ?: c.nav.arg)
        "yield" -> YieldScreen(c)
        "market" -> MarketScreen(c)
        "weather" -> WeatherScreen(c)
        "disease" -> DiseaseForecastScreen(c)
        "ai" -> AssistantScreen(c)
        "log" -> LogFormScreen(c, c.nav.arg)
        "setup" -> SetupScreen(c)
        "imagery" -> ImageryScreen(c, c.nav.arg)
        "imageset" -> ImageSetScreen(c, c.nav.arg)
        "ndvi" -> NdviDetailScreen(c, c.nav.arg)
        "env" -> EnvScreen(c)
        "satellite" -> SatelliteWorkspaceScreen(c)
        "satelliteAdvanced" -> SatelliteScreen(c)
        "drone" -> DroneFlightScreen(c)
        else -> OverviewScreen(c)
    }
}

/** Mục điều hướng hiện ra theo vai trò: người không xem được doanh thu (nhân viên, phi công) không thấy mục Sản lượng. */
private fun visibleNav(): List<NavItem> = NAV.filter { it.route != "yield" || Access.canSeeSales }

@Composable
fun LogoPlate(size: androidx.compose.ui.unit.Dp) {
    Image(
        painter = painterResource(com.example.test_tablet.R.drawable.vwing_logo),
        contentDescription = "VWing",
        modifier = Modifier.size(size + 10.dp),
    )
}

@Composable
private fun CircleIconButton(
    onClick: () -> Unit,
    dot: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(VC.card)
            .border(1.dp, VC.border, RoundedCornerShape(14.dp))
            .bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
        if (dot) Box(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp).size(8.dp).clip(CircleShape).background(VC.amber))
    }
}

private fun initialsOf(name: String): String {
    val w = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        w.isEmpty() -> "VW"
        w.size == 1 -> w[0].take(2).uppercase()
        else -> (w.first().take(1) + w.last().take(1)).uppercase()
    }
}

/**
 * Top Bar phong cách Aurora Liquid-Glass:
 * Chữ trắng sắc nét trên nền đêm đại dương - rừng già, nút Gói dịch vụ & Chế độ hiển thị dạng viên thuốc phát sáng.
 */
@Composable
private fun Header(c: VCtx, active: String, onLogoClick: () -> Unit = {}) {
    val d = c.d
    val canBack = c.nav.stack.size > 1
    val onOverview = c.nav.head == "overview"
    val showControls = c.wide && onOverview && !c.simpleMode
    val needsVisitCount = d.treesNeedingVisitCount()
    val btnSize = if (c.wide) 48.dp else 42.dp
    val currentFarmer = com.example.test_tablet.data.vwing.FirebaseFarmerRepo.currentFarmer
    val isEnt = c.uiSettings.planTier == PlanTier.ENTERPRISE

    if (!c.wide) {
        // Bố cục 2 hàng cho Điện thoại (360×800dp & 411×914dp): chuẩn vùng chạm ≥44–46dp, hiển thị đầy đủ không cắt chữ
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (canBack) {
                    IconButton({ c.nav.pop() }, Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = VC.text, modifier = Modifier.size(22.dp))
                    }
                } else {
                    Box(Modifier.size(48.dp).bounceClick(onClick = onLogoClick), contentAlignment = Alignment.Center) {
                        LogoPlate(26.dp)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        titleOf(c.nav.head, c.simpleMode),
                        color = VC.text,
                        fontFamily = VDisplayFont,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        if (c.simpleMode) "${d.garden.name} · ${d.garden.location.ifBlank { Dates.fmt(Dates.now()) }}"
                        else "${d.garden.name} · ${Dates.fmt(Dates.now())}",
                        color = VC.muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                CircleIconButton({ VUi.countryPickerOpen = true }, size = 48.dp) {
                    Text(VUi.country.flag, fontSize = 20.sp)
                }
                CircleIconButton({ c.nav.top("issues|issues") }, dot = needsVisitCount > 0, size = 48.dp) {
                    Icon(Icons.Filled.NotificationsNone, "Cây cần thăm ($needsVisitCount)", tint = VC.text, modifier = Modifier.size(20.dp))
                }
            }

            // Hàng SĐT · Gói · Chế độ chỉ hiện ở Tổng quan và Cài đặt: các màn khác nhường ~50dp chiều cao cho nội dung.
            // (Đổi số điện thoại, gói cước và chế độ Nông dân/Kỹ sư đều có sẵn trong Cài đặt vườn.)
            if (onOverview || c.nav.head == "setup") Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .weight(1.15f)
                        .heightIn(min = 48.dp)
                        .testTag("top_phone_auth")
                        .semantics { contentDescription = "Tài khoản SĐT nông dân" }
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (currentFarmer != null) VC.pine else VC.card)
                        .border(
                            1.dp,
                            if (currentFarmer != null) VC.lime.copy(alpha = 0.65f) else VC.mint.copy(alpha = 0.48f),
                            RoundedCornerShape(12.dp),
                        )
                        .bounceClick { c.openFarmerAuth() }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Outlined.Dashboard,
                        contentDescription = null,
                        tint = if (currentFarmer != null) (if (VC.isPro) VC.lime else Color.White) else VC.text2,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (currentFarmer != null) currentFarmer.displayPhone else "Nhập SĐT",
                        color = if (currentFarmer != null) (if (VC.isPro) VC.lime else Color.White) else VC.text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }

                Row(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("top_plan_tier")
                        .semantics { contentDescription = if (isEnt) "Pro Account 499.000đ" else "Basic Account 149.000đ" }
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isEnt) VC.pine else VC.card)
                        .border(1.dp, if (isEnt) VC.gold.copy(alpha = 0.65f) else VC.border, RoundedCornerShape(12.dp))
                        .bounceClick { c.requestFeature(null) }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        if (isEnt) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = if (isEnt) VC.gold else VC.mint,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (isEnt) "Pro · 499K" else "Basic · 149K",
                        color = if (isEnt) VC.gold else VC.mint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }

                Row(
                    Modifier
                        .weight(0.92f)
                        .heightIn(min = 48.dp)
                        .testTag("top_mode_switch")
                        .semantics { contentDescription = if (c.simpleMode) "Chế độ Nông dân" else "Chế độ Kỹ sư" }
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (c.simpleMode) VC.card else VC.pine)
                        .border(1.dp, if (c.simpleMode) VC.border else VC.mint.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                        .bounceClick {
                            val next = !c.simpleMode
                            if (!next && !c.isFeatureEnabled(VFeature.ENGINEER_MODE)) {
                                c.requestFeature(VFeature.ENGINEER_MODE)
                            } else {
                                c.setSimpleMode(next)
                                c.toast(if (next) "Chế độ Nông dân: ưu tiên kết luận rõ ràng." else "Chế độ Kỹ sư: hiển thị đầy đủ chỉ số GIS & OBIA.")
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (c.simpleMode) VC.lime else VC.mint)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (c.simpleMode) "Nông dân" else "Kỹ sư",
                        color = if (c.simpleMode) VC.text else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
        return
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = if (c.wide) 24.dp else 14.dp, vertical = if (c.wide) 10.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (c.wide) 10.dp else 8.dp),
    ) {
        AnimatedContent(canBack, transitionSpec = { (fadeIn(tween(180)) + scaleIn(tween(200), .85f)) togetherWith (fadeOut(tween(110)) + scaleOut(tween(110), .85f)) }, label = "lead") { back ->
            if (back) IconButton({ c.nav.pop() }, Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = VC.text, modifier = Modifier.size(24.dp)) }
            else if (!c.wide) Box(Modifier.size(48.dp).bounceClick(onClick = onLogoClick), contentAlignment = Alignment.Center) { LogoPlate(28.dp) }
            else Spacer(Modifier.size(1.dp))
        }
        Column(Modifier.weight(1f).widthIn(min = 140.dp)) {
            Text(
                titleOf(c.nav.head, c.simpleMode),
                color = VC.text,
                fontFamily = VDisplayFont,
                fontSize = if (c.wide) 22.sp else 17.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                if (onOverview) "${d.garden.name} · ${Dates.fmt(Dates.now())}"
                else if (c.simpleMode) "${d.garden.name} · ${d.garden.location.ifBlank { Dates.fmt(Dates.now()) }}"
                else d.garden.name + if (d.isSample) " · dữ liệu mẫu" else "",
                color = VC.muted,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showControls) PeriodSwitcher()
            if (c.canTogglePhoneMode && !c.simpleMode) {
                Row(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (c.phoneMode) VC.pine else VC.card)
                        .border(1.dp, if (c.phoneMode) VC.mint.copy(alpha = 0.55f) else VC.border, RoundedCornerShape(14.dp))
                        .bounceClick {
                            val next = !c.phoneMode
                            c.setPhoneMode(next)
                            c.toast(if (next) "Đã chuyển sang bố cục Điện thoại (≤420dp)." else "Đã chuyển về bố cục Máy tính bảng.")
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        Icons.Outlined.Layers,
                        contentDescription = null,
                        tint = if (c.phoneMode) VC.mint else VC.text2,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        if (c.phoneMode) "Tablet" else "Khung ĐT",
                        color = if (c.phoneMode) Color.White else VC.text,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }

            val currentFarmer = com.example.test_tablet.data.vwing.FirebaseFarmerRepo.currentFarmer
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .testTag("top_phone_auth")
                    .semantics { contentDescription = "Tài khoản SĐT nông dân" }
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (currentFarmer != null) VC.pine else VC.card)
                    .border(
                        1.dp,
                        if (currentFarmer != null) VC.lime.copy(alpha = 0.65f) else VC.mint.copy(alpha = 0.48f),
                        RoundedCornerShape(14.dp),
                    )
                    .bounceClick { c.openFarmerAuth() }
                    .padding(horizontal = 9.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    Icons.Outlined.Dashboard,
                    contentDescription = null,
                    tint = if (currentFarmer != null) (if (VC.isPro) VC.lime else Color.White) else VC.text2,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    if (currentFarmer != null) {
                        currentFarmer.phoneDisplay
                    } else {
                        if (c.wide) "Số điện thoại" else "SĐT"
                    },
                    color = if (currentFarmer != null) (if (VC.isPro) VC.lime else Color.White) else VC.text,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }

            val isEnt = c.uiSettings.planTier == PlanTier.ENTERPRISE
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .testTag("top_plan_tier")
                    .semantics { contentDescription = if (isEnt) "Pro Account 499.000đ" else "Basic Account 149.000đ" }
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isEnt) VC.pine else VC.card)
                    .border(1.dp, if (isEnt) VC.gold.copy(alpha = 0.65f) else VC.border, RoundedCornerShape(14.dp))
                    .bounceClick { c.requestFeature(null) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    if (isEnt) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = if (isEnt) VC.gold else VC.mint,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    if (c.wide) {
                        if (isEnt) "Pro Account · 499K" else "Basic Account · 149K"
                    } else {
                        if (isEnt) "Pro · 499K" else "Basic · 149K"
                    },
                    color = if (isEnt) VC.gold else VC.mint,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }

            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .testTag("top_mode_switch")
                    .semantics { contentDescription = if (c.simpleMode) "Chế độ Nông dân" else "Chế độ Kỹ sư" }
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (c.simpleMode) VC.card else VC.pine)
                    .border(1.dp, if (c.simpleMode) VC.border else VC.mint.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                    .bounceClick {
                        val next = !c.simpleMode
                        if (!next && !c.isFeatureEnabled(VFeature.ENGINEER_MODE)) {
                            c.requestFeature(VFeature.ENGINEER_MODE)
                        } else {
                            c.setSimpleMode(next)
                            c.toast(if (next) "Chế độ Nông dân: ưu tiên kết luận rõ ràng." else "Chế độ Kỹ sư: hiển thị đầy đủ chỉ số GIS & OBIA.")
                        }
                    }
                    .padding(horizontal = 9.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (c.simpleMode) VC.lime else VC.mint)
                )
                val shMode = AppStrings.shell(VUi.country)
                Text(
                    if (c.wide) {
                        if (c.simpleMode) shMode.farmerFull else shMode.engineerFull
                    } else {
                        if (c.simpleMode) shMode.farmerShort else shMode.engineerShort
                    },
                    color = if (c.simpleMode) VC.text else Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                if (c.wide && c.simpleMode && !c.isFeatureEnabled(VFeature.ENGINEER_MODE)) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(VC.amber.copy(alpha = 0.20f))
                            .border(1.dp, VC.amber.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text("PRO", color = VC.amber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            CircleIconButton({ VUi.countryPickerOpen = true }, size = btnSize) { Text(VUi.country.flag, fontSize = 20.sp) }
            if (showControls) UnitPill()
            CircleIconButton({ c.nav.top("issues|issues") }, dot = needsVisitCount > 0, size = btnSize) { Icon(Icons.Filled.NotificationsNone, "Cây cần thăm ($needsVisitCount)", tint = VC.text, modifier = Modifier.size(21.dp)) }
            if (c.wide) Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(VC.pine).border(1.dp, VC.mint.copy(alpha = 0.45f), RoundedCornerShape(14.dp)).bounceClick { c.openFarmerAuth() },
                contentAlignment = Alignment.Center,
            ) { Text(initialsOf(d.garden.operatorName), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun RailItem(
    n: NavItem,
    sel: Boolean,
    simpleMode: Boolean = true,
    badgeCount: Int = 0,
    locked: Boolean = false,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(if (sel) VC.pine else Color.Transparent, tween(180), label = "sbBg")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(if (sel) Modifier.border(1.dp, VC.mint.copy(alpha = 0.45f), RoundedCornerShape(12.dp)) else Modifier)
            .bounceClick(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .width(2.5.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (sel) (if (VC.isPro) VC.lime else Color.White) else Color.Transparent)
        )
        Icon(
            if (sel) n.iconOn else n.icon,
            null,
            tint = if (sel) (if (VC.isPro) VC.lime else Color.White) else VC.muted,
            modifier = Modifier.size(18.dp),
        )
        Text(
            AppStrings.shell(VUi.country).fullLabel(n.route, if (simpleMode) n.farmerLabel else n.label),
            color = if (sel) Color.White else VC.text2,
            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (locked) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(VC.amber.copy(alpha = 0.18f))
                    .border(1.dp, VC.amber.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "PRO",
                    color = VC.amber,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else if (badgeCount > 0) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(VC.amber.copy(alpha = 0.18f))
                    .border(1.dp, VC.amber.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$badgeCount",
                    color = VC.amber,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun Sidebar(active: String, nav: VNav, c: VCtx, onLogoClick: () -> Unit = {}) {
    val sum = c.d.summary()
    val needsVisitCount = c.d.treesNeedingVisitCount()
    val stablePct = if (sum.totalTrees == 0) 0 else Math.round(sum.byCondition.getValue(Condition.STABLE) * 100f / sum.totalTrees)
    var showSecondaryInFarmerMode by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .width(248.dp)
            .fillMaxHeight()
            .background(VC.sidebar.copy(alpha = 0.85f))
            .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(VC.card)
                    .border(1.dp, VC.border, RoundedCornerShape(14.dp))
                    .bounceClick(onClick = onLogoClick)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(painterResource(com.example.test_tablet.R.drawable.vwing_logo), "VWing", Modifier.size(30.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("VWing", color = VC.text, fontFamily = VDisplayFont, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (c.uiSettings.planTier == PlanTier.ENTERPRISE) VC.pine else VC.elevated)
                                .border(1.dp, if (c.uiSettings.planTier == PlanTier.ENTERPRISE) VC.gold.copy(alpha = 0.55f) else VC.border, RoundedCornerShape(6.dp))
                                .bounceClick { c.requestFeature(null) }
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                if (c.uiSettings.planTier == PlanTier.ENTERPRISE) "✦ PRO 499K" else "BASIC 149K",
                                color = if (c.uiSettings.planTier == PlanTier.ENTERPRISE) VC.gold else VC.mint,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Text(
                        c.d.garden.name.ifBlank { "Vườn Bảo Lộc" },
                        color = VC.muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            if (c.simpleMode) {
                val mainItems = visibleNav().filter { it.route != "setup" && it.primaryForFarmer }
                val extraItems = visibleNav().filter { it.route != "setup" && !it.primaryForFarmer }
                Text(
                    "Không gian làm việc",
                    color = VC.muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp, start = 10.dp),
                )
                mainItems.forEach { n ->
                    val badge = if (n.route == "issues") needsVisitCount else 0
                    val locked = VFeature.forRoute(n.route)?.let { !c.isFeatureEnabled(it) } ?: false
                    RailItem(n, n.route == active, simpleMode = true, badgeCount = badge, locked = locked) { nav.top(n.route) }
                }
                if (extraItems.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .bounceClick { showSecondaryInFarmerMode = !showSecondaryInFarmerMode }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (showSecondaryInFarmerMode) "Thu gọn công cụ phụ" else "Thêm công cụ chuyên sâu (${extraItems.size})",
                            color = VC.muted,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    if (showSecondaryInFarmerMode || extraItems.any { it.route == active }) {
                        extraItems.forEach { n ->
                            val locked = VFeature.forRoute(n.route)?.let { !c.isFeatureEnabled(it) } ?: false
                            RailItem(n, n.route == active, simpleMode = true, locked = locked) { nav.top(n.route) }
                        }
                    }
                }
            } else {
                visibleNav().filter { it.route != "setup" }.groupBy { it.section }.forEach { (section, items) ->
                    Text(
                        humanize(section),
                        color = VC.muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 10.dp),
                    )
                    items.forEach { n ->
                        val badge = if (n.route == "issues") needsVisitCount else 0
                        val locked = VFeature.forRoute(n.route)?.let { !c.isFeatureEnabled(it) } ?: false
                        RailItem(n, n.route == active, simpleMode = false, badgeCount = badge, locked = locked) { nav.top(n.route) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(VC.line))
            NAV.firstOrNull { it.route == "setup" }?.let { RailItem(it, active == "setup", simpleMode = c.simpleMode) { nav.top("setup") } }
            HealthBattery(stablePct, Modifier.bounceClick { nav.top("map") })
        }
    }
}

@Composable
private fun BottomBar(active: String, nav: VNav, onMore: () -> Unit) {
    val view = androidx.compose.ui.platform.LocalView.current
    val isPro = VC.isPro
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = if (isPro) VC.lime else VC.pine,
        selectedTextColor = if (isPro) Color.White else VC.pine,
        indicatorColor = if (isPro) VC.pine else Color(0xFFE2ECE7),
        unselectedIconColor = VC.muted,
        unselectedTextColor = VC.muted,
    )
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(VC.border))
        NavigationBar(
            containerColor = if (isPro) Color(0xFF061621).copy(alpha = 0.96f) else Color.White,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            BOTTOM.map { r -> NAV.first { it.route == r } }.forEach { n ->
                val sel = n.route == active
                val isCenterQr = n.route == "qr"
                NavigationBarItem(
                    selected = sel,
                    onClick = {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        nav.top(n.route)
                    },
                    modifier = Modifier
                        .testTag("nav_${n.route}")
                        .semantics { contentDescription = n.short },
                    icon = {
                        if (isCenterQr) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isPro) {
                                            if (sel) VC.pine else Color(0xFF0F2E24)
                                        } else {
                                            if (sel) Color(0xFFE2ECE7) else Color(0xFFEEF5F1)
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        if (isPro) {
                                            if (sel) VC.lime else VC.mint.copy(alpha = 0.55f)
                                        } else {
                                            if (sel) VC.pine.copy(alpha = 0.45f) else Color(0xFFC8DCD2)
                                        },
                                        RoundedCornerShape(14.dp),
                                    )
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (sel) n.iconOn else n.icon,
                                    n.short,
                                    tint = if (isPro) VC.lime else VC.pine,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        } else {
                            Icon(if (sel) n.iconOn else n.icon, n.short, Modifier.size(24.dp))
                        }
                    },
                    label = {
                        Text(
                            AppStrings.shell(VUi.country).shortLabel(n.route, n.short),
                            fontSize = 10.5.sp,
                            letterSpacing = (-0.35).sp,
                            fontWeight = if (sel || isCenterQr) FontWeight.Bold else FontWeight.Medium,
                            color = if (isPro) {
                                if (sel) Color.White else if (isCenterQr) VC.lime else VC.muted
                            } else {
                                if (sel || isCenterQr) VC.pine else VC.muted
                            },
                            maxLines = 1,
                            softWrap = false,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    },
                    colors = colors,
                )
            }
            val moreSel = active !in BOTTOM
            val shMore = AppStrings.shell(VUi.country)
            NavigationBarItem(
                selected = moreSel,
                onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                    onMore()
                },
                icon = { Icon(if (moreSel) Icons.Filled.MoreHoriz else Icons.Outlined.MoreHoriz, shMore.more, Modifier.size(24.dp)) },
                label = { Text(shMore.more, fontSize = 11.2.sp, letterSpacing = (-0.25).sp, fontWeight = if (moreSel) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, softWrap = false) },
                colors = colors,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoreSheet(active: String, c: VCtx, onPick: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
    ) {
        val sh = AppStrings.shell(VUi.country)
        FlowRow(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                AppStrings.tr(if (c.simpleMode) "Tất cả mục trong sổ vườn" else "Tất cả phân hệ"),
                color = VC.text,
                fontSize = 17.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp),
            )
            VButton(
                if (c.uiSettings.planTier == PlanTier.ENTERPRISE) "✦ Pro Account · 499K" else AppStrings.tr("🌱 Basic · 149K ➔ Mở Pro"),
                { c.requestFeature(null) },
                primary = false,
            )
        }
        val groups = visibleNav().filter { it.route !in BOTTOM && it.route != "setup" }.groupBy { it.section }
        groups.forEach { (section, items) ->
            Text(humanize(sh.sectionLabel(section)), color = VC.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp, top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 3, modifier = Modifier.padding(bottom = 8.dp)) {
                items.forEachIndexed { i, n ->
                    val sel = n.route == active
                    val locked = VFeature.forRoute(n.route)?.let { !c.isFeatureEnabled(it) } ?: false
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (sel) VC.pine else VC.card)
                            .border(1.dp, if (sel) VC.mint.copy(alpha = 0.65f) else VC.border, RoundedCornerShape(14.dp))
                            .bounceClick { onPick(n.route) }
                            .padding(vertical = 14.dp, horizontal = 8.dp)
                            .appear(i),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(if (sel) n.iconOn else n.icon, null, tint = if (sel) (if (VC.isPro) VC.lime else Color.White) else VC.text2, modifier = Modifier.size(22.dp))
                            if (locked) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(VC.amber.copy(alpha = 0.18f))
                                        .border(1.dp, VC.amber.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                ) {
                                    Text("PRO", color = VC.amber, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Text(sh.shortLabel(n.route, n.short), color = if (sel) Color.White else VC.text, fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                    }
                }
                val extra = (3 - items.size % 3) % 3
                repeat(extra) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Banner đặc sắc giới thiệu & so sánh 2 gói tài khoản chính thức của SmartFarm:
 * - **Basic Account**: `149.000 đ / tháng` | `1.499.000 đ / năm` (Tiết kiệm 289.000 đ/năm)
 * - **Pro Account**: `499.000 đ / tháng` | `4.999.000 đ / năm` (Tiết kiệm 989.000 đ/năm)
 *
 * Dùng trực tiếp trên màn hình Tổng quan (`OverviewScreen`, `inDialog = false`) và trong hộp thoại
 * Quản lý Gói Dịch Vụ (`PlanManagerDialog`, `inDialog = true`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountTierPricingBanner(
    c: VCtx,
    inDialog: Boolean = false,
    onPlanActivated: (() -> Unit)? = null,
) {
    val ui = c.uiSettings
    val isEnt = ui.planTier == PlanTier.ENTERPRISE
    val cycle = ui.billingCycle
    val isYearly = cycle == BillingCycle.YEARLY
    val todayStr = Dates.fmt(Dates.today())
    val remainingAi = ui.remainingAiQuestions(todayStr)
    var expandedOnHome by rememberSaveable { mutableStateOf(c.wide) }
    val showFullCards = inDialog || expandedOnHome

    val bannerBg = Brush.linearGradient(
        colors = if (isEnt) {
            listOf(Color(0xFF061B26), Color(0xFF0B2E24), Color(0xFF061822))
        } else {
            listOf(Color(0xFF0A281E), Color(0xFF123D2C), Color(0xFF082219))
        }
    )

    Column(
        Modifier
            .fillMaxWidth()
            .testTag(if (inDialog) "dialog_account_pricing_banner" else "home_account_pricing_banner")
            .clip(RoundedCornerShape(22.dp))
            .background(bannerBg)
            .border(
                1.5.dp,
                if (isEnt) Color(0xFFEAB308).copy(alpha = 0.72f) else Color(0xFFA8DF65).copy(alpha = 0.58f),
                RoundedCornerShape(22.dp),
            )
            .padding(if (c.wide) 18.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Header của Banner + Nút chuyển đổi Chu kỳ Month / Year (trên điện thoại xếp dọc 100% chiều ngang để không bao giờ bóp chữ)
        val headerTextBlock: @Composable (Modifier) -> Unit = { blkMod ->
            Column(blkMod, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFFEAB308))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                    ) {
                        Text(
                            "✦ SMARTFARM MEMBERSHIP",
                            color = Color(0xFF071B12),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                        )
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFFA8DF65).copy(alpha = 0.20f))
                            .border(1.dp, Color(0xFFA8DF65).copy(alpha = 0.55f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                    ) {
                        Text(
                            if (isEnt) "ĐANG DÙNG: PRO ACCOUNT" else "ĐANG DÙNG: BASIC ACCOUNT",
                            color = Color(0xFFA8DF65),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                        )
                    }
                }
                Text(
                    "Nâng tầm Quản trị Vườn Thông Minh: Basic Account & Pro Account",
                    color = Color.White,
                    fontFamily = VDisplayFont,
                    fontSize = if (c.wide) 19.sp else 16.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "Chọn gói theo Tháng (Month) linh hoạt hoặc theo Năm (Year) tiết kiệm tới 989.000 đ/năm.",
                    color = Color.White.copy(alpha = 0.84f),
                    fontSize = 12.5.sp,
                )
            }
        }

        val cycleToggleRow: @Composable (Modifier) -> Unit = { rowMod ->
            Row(
                rowMod,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (c.wide) Arrangement.spacedBy(8.dp) else Arrangement.SpaceBetween,
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.32f))
                        .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(14.dp))
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BillingCycle.entries.forEach { bc ->
                        val activeCycle = cycle == bc
                        Box(
                            Modifier
                                .heightIn(min = 40.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    if (activeCycle) {
                                        if (bc == BillingCycle.YEARLY) Color(0xFFEAB308) else Color(0xFFA8DF65)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .bounceClick {
                                    c.updateUiSettings { it.copy(billingCycle = bc) }
                                }
                                .padding(horizontal = 11.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (bc == BillingCycle.MONTHLY) "Month (Tháng)" else "Year (Năm -17%)",
                                color = if (activeCycle) Color(0xFF071B12) else Color.White.copy(alpha = 0.86f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                            )
                        }
                    }
                }

                if (!inDialog) {
                    Box(
                        Modifier
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .bounceClick { expandedOnHome = !expandedOnHome }
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (expandedOnHome) "Thu gọn" else "Chi tiết 2 gói",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        if (c.wide) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                headerTextBlock(Modifier.weight(1f))
                cycleToggleRow(Modifier)
            }
        } else {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                headerTextBlock(Modifier.fillMaxWidth())
                cycleToggleRow(Modifier.fillMaxWidth())
            }
        }

        // 2. Bảng niêm yết chuẩn: ITEM | Month | Year (luôn hiển thị rõ ràng với Tabular Numerals)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF04131B).copy(alpha = 0.82f))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
        ) {
            // Dòng tiêu đề bảng
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "ITEM",
                    color = Color(0xFFA8DF65),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1.2f),
                )
                Text(
                    if (c.wide) "Month (Tháng)" else "Month",
                    color = if (!isYearly) Color(0xFFA8DF65) else Color.White.copy(alpha = 0.78f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.95f),
                )
                Text(
                    if (c.wide) "Year (Năm ★)" else "Year ★",
                    color = if (isYearly) Color(0xFFEAB308) else Color.White.copy(alpha = 0.78f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1.05f),
                )
            }

            // Dòng 1: Basic Account | 149.000 đ | 1.499.000 đ
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (!isEnt) Color(0xFF1B7A4E).copy(alpha = 0.26f) else Color.Transparent)
                    .bounceClick {
                        c.updateUiSettings {
                            it.copy(
                                planTier = PlanTier.FREE,
                                simpleMode = true,
                                enabledFeatures = emptySet(),
                                disabledFeatures = emptySet(),
                            )
                        }
                        c.toast("Đã chọn ${PlanTier.FREE.itemName} (${PlanTier.FREE.priceFor(cycle)}${cycle.unitVi}).")
                        onPlanActivated?.invoke()
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (!isEnt) "🌱 Basic Account ✓" else "🌱 Basic Account",
                        color = Color.White,
                        fontSize = if (c.wide) 14.sp else 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                    )
                    Text(
                        "Nông hộ · 100 cây QR",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
                Text(
                    PlanTier.FREE.monthlyPriceVi,
                    color = if (!isYearly) Color(0xFFA8DF65) else Color.White,
                    fontFamily = VDisplayFont,
                    fontSize = if (c.wide) 15.sp else 13.5.sp,
                    fontWeight = if (!isYearly) FontWeight.ExtraBold else FontWeight.SemiBold,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.weight(0.95f),
                )
                Column(
                    Modifier.weight(1.05f),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        PlanTier.FREE.yearlyPriceVi,
                        color = if (isYearly) Color(0xFFEAB308) else Color.White,
                        fontFamily = VDisplayFont,
                        fontSize = if (c.wide) 15.sp else 13.5.sp,
                        fontWeight = if (isYearly) FontWeight.ExtraBold else FontWeight.SemiBold,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                    Text(
                        "Tiết kiệm 289K",
                        color = Color(0xFFA8DF65),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))

            // Dòng 2: Pro Account | 499.000 đ | 4.999.000 đ
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (isEnt) Color(0xFFEAB308).copy(alpha = 0.18f) else Color.Transparent)
                    .bounceClick {
                        c.updateUiSettings {
                            it.copy(
                                planTier = PlanTier.ENTERPRISE,
                                enabledFeatures = emptySet(),
                                disabledFeatures = emptySet(),
                            )
                        }
                        c.toast("Đã kích hoạt ${PlanTier.ENTERPRISE.itemName} (${PlanTier.ENTERPRISE.priceFor(cycle)}${cycle.unitVi})!")
                        onPlanActivated?.invoke()
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (isEnt) "✦ Pro Account ✓" else "✦ Pro Account",
                        color = Color(0xFFEAB308),
                        fontSize = if (c.wide) 14.sp else 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                    )
                    Text(
                        "Vệ tinh GEE + Drone",
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
                Text(
                    PlanTier.ENTERPRISE.monthlyPriceVi,
                    color = if (!isYearly) Color(0xFFEAB308) else Color.White,
                    fontFamily = VDisplayFont,
                    fontSize = if (c.wide) 15.sp else 13.5.sp,
                    fontWeight = if (!isYearly) FontWeight.ExtraBold else FontWeight.SemiBold,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.weight(0.95f),
                )
                Column(
                    Modifier.weight(1.05f),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        PlanTier.ENTERPRISE.yearlyPriceVi,
                        color = Color(0xFFEAB308),
                        fontFamily = VDisplayFont,
                        fontSize = if (c.wide) 15.sp else 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                    Text(
                        "Tiết kiệm 989K",
                        color = Color(0xFFEAB308),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }

        // 3. Hai Thẻ Gói Tài Khoản Đặc Sắc (Basic Account vs Pro Account)
        if (showFullCards) {
            val basicCard: @Composable (Modifier) -> Unit = { cardMod ->
                Column(
                    cardMod
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFFF5F8F6), Color(0xFFEAF2EE))
                            )
                        )
                        .border(
                            width = if (!isEnt) 2.dp else 1.dp,
                            color = if (!isEnt) Color(0xFFA8DF65) else Color.White.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(16.dp),
                        )
                        .bounceClick {
                            c.updateUiSettings {
                                it.copy(
                                    planTier = PlanTier.FREE,
                                    simpleMode = true,
                                    enabledFeatures = emptySet(),
                                    disabledFeatures = emptySet(),
                                )
                            }
                            c.toast("Đã kích hoạt Basic Account (${PlanTier.FREE.priceFor(cycle)}${cycle.unitVi}).")
                            onPlanActivated?.invoke()
                        }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "🌱 Basic Account",
                                color = Color(0xFF0F5132),
                                fontFamily = VDisplayFont,
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (!isEnt) Color(0xFF0F5132) else Color(0xFFD6E3DC))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                if (!isEnt) "✓ ĐANG KÍCH HOẠT" else "GÓI CƠ BẢN",
                                color = if (!isEnt) Color.White else Color(0xFF0F5132),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }

                    // Mức giá nổi bật theo chu kỳ đang chọn + đối chiếu chu kỳ còn lại
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            PlanTier.FREE.priceFor(cycle),
                            color = Color(0xFF0D1F17),
                            fontFamily = VDisplayFont,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.ExtraBold,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                        )
                        Text(
                            cycle.unitVi,
                            color = Color(0xFF3E5649),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                    Text(
                        if (isYearly) "Month: 149.000 đ/tháng · Ưu đãi năm tiết kiệm 289.000 đ"
                        else "Year: 1.499.000 đ/năm (Chỉ ~125.000 đ/tháng · Tiết kiệm 289.000 đ)",
                        color = Color(0xFF1B7A4E),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                    )

                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFD0DED6)))

                    Text("• Giao diện Trắng sáng ban ngày chống chói ngoài nắng", color = Color(0xFF0D1F17), fontSize = 12.5.sp)
                    Text("• Quản lý tối đa ${VFeature.FREE_MAX_TREES} cây QR & hồ sơ chăm sóc tận gốc", color = Color(0xFF0D1F17), fontSize = 12.5.sp)
                    Text("• Dự báo thời tiết 6 ngày, lịch tưới nước, bón phân & thu hoạch", color = Color(0xFF0D1F17), fontSize = 12.5.sp)
                    Text("• Hỏi Kỹ sư AI (${VFeature.FREE_MAX_AI_PER_DAY} câu/ngày · còn $remainingAi câu) & lưu sổ theo SĐT", color = Color(0xFF0D1F17), fontSize = 12.5.sp)

                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (!isEnt) Color(0xFF0F5132) else Color(0xFFE2ECE7))
                            .border(1.dp, Color(0xFF0F5132).copy(alpha = 0.35f), RoundedCornerShape(11.dp))
                            .padding(vertical = 9.dp, horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (!isEnt) "✓ Đang dùng Basic Account (${PlanTier.FREE.priceFor(cycle)})"
                            else "Chọn Basic Account · ${PlanTier.FREE.priceFor(cycle)}${cycle.unitVi}",
                            color = if (!isEnt) Color.White else Color(0xFF0F5132),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }

            val proCard: @Composable (Modifier) -> Unit = { cardMod ->
                Column(
                    cardMod
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF0B2A22), Color(0xFF071A24))
                            )
                        )
                        .border(
                            width = if (isEnt) 2.dp else 1.2.dp,
                            color = if (isEnt) Color(0xFFEAB308) else Color(0xFFEAB308).copy(alpha = 0.55f),
                            shape = RoundedCornerShape(16.dp),
                        )
                        .bounceClick {
                            c.updateUiSettings {
                                it.copy(
                                    planTier = PlanTier.ENTERPRISE,
                                    enabledFeatures = emptySet(),
                                    disabledFeatures = emptySet(),
                                )
                            }
                            c.toast("Đã kích hoạt Pro Account (${PlanTier.ENTERPRISE.priceFor(cycle)}${cycle.unitVi}) — Mở khóa toàn bộ công nghệ!")
                            onPlanActivated?.invoke()
                        }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "✦ Pro Account",
                            color = Color(0xFFEAB308),
                            fontFamily = VDisplayFont,
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFFEAB308))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                if (isEnt) "✦ ĐANG KÍCH HOẠT" else "★ ĐẶC SẮC NHẤT",
                                color = Color(0xFF071B12),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            PlanTier.ENTERPRISE.priceFor(cycle),
                            color = Color.White,
                            fontFamily = VDisplayFont,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.ExtraBold,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                        )
                        Text(
                            cycle.unitVi,
                            color = Color(0xFFA8DF65),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                    Text(
                        if (isYearly) "Month: 499.000 đ/tháng · Ưu đãi năm tiết kiệm 989.000 đ"
                        else "Year: 4.999.000 đ/năm (Chỉ ~416.000 đ/tháng · Tiết kiệm 989.000 đ)",
                        color = Color(0xFFEAB308),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                    )

                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.16f)))

                    Text("• Giao diện Aurora Liquid-Glass & Chế độ Kỹ sư GIS chuyên sâu", color = Color.White, fontSize = 12.5.sp)
                    Text("• Không giới hạn số lượng cây gắn QR, lô canh tác & hỏi AI 24/7", color = Color.White, fontSize = 12.5.sp)
                    Text("• Vệ tinh Google Earth Engine (7 chỉ số) & Địa tầng 3D 7 tầng", color = Color.White, fontSize = 12.5.sp)
                    Text("• Bay Drone đa phổ OBIA, Phun thuốc VRA & Đồng bộ nhóm Cloud", color = Color.White, fontSize = 12.5.sp)

                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(Color(0xFFEAB308))
                            .padding(vertical = 9.dp, horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (isEnt) "✦ Đang dùng Pro Account (${PlanTier.ENTERPRISE.priceFor(cycle)})"
                            else "✦ Nâng cấp Pro Account · ${PlanTier.ENTERPRISE.priceFor(cycle)}${cycle.unitVi}",
                            color = Color(0xFF071B12),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }

            if (c.wide) {
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    basicCard(Modifier.weight(1f).fillMaxHeight())
                    proCard(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    basicCard(Modifier.fillMaxWidth())
                    proCard(Modifier.fillMaxWidth())
                }
            }
        }

        if (!inDialog) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "💡 Chạm vào dòng hoặc thẻ bất kỳ để chuyển nhanh Basic ⇄ Pro hoặc mở bảng Feature Flags.",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 11.5.sp,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
                        .bounceClick { c.requestFeature(null) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        "Tùy chỉnh Feature Flags",
                        color = Color(0xFFA8DF65),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanManagerDialog(
    c: VCtx,
    paywallFeature: VFeature?,
    onDismiss: () -> Unit,
    onUnlockedFeature: () -> Unit,
) {
    val ui = c.uiSettings

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xB8020A10))
                .padding(horizontal = if (c.wide) 40.dp else 12.dp, vertical = if (c.wide) 24.dp else 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .widthIn(max = 920.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(VC.card)
                    .border(1.dp, VC.border, RoundedCornerShape(24.dp))
                    .padding(if (c.wide) 22.dp else 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(
                            "Gói Tài Khoản & Bật/Tắt Chức Năng",
                            color = VC.text,
                            fontFamily = VDisplayFont,
                            fontSize = if (c.wide) 21.sp else 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            "Basic Account (149.000 đ/tháng · 1.499.000 đ/năm) & Pro Account (499.000 đ/tháng · 4.999.000 đ/năm).",
                            color = VC.text2,
                            fontSize = 12.5.sp,
                        )
                    }
                    VButton("Đóng", onDismiss, primary = false)
                }

                if (paywallFeature != null && !c.isFeatureEnabled(paywallFeature)) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF143528))
                            .border(1.dp, VC.gold, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(VC.gold)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text("✦ TÍNH NĂNG PRO ACCOUNT", color = Color(0xFF0A2218), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                paywallFeature.title,
                                color = Color.White,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            paywallFeature.summary,
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 13.5.sp,
                        )
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .widthIn(min = 180.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(VC.gold)
                                    .bounceClick {
                                        c.updateUiSettings {
                                            it.copy(
                                                planTier = PlanTier.ENTERPRISE,
                                                enabledFeatures = emptySet(),
                                                disabledFeatures = emptySet(),
                                            )
                                        }
                                        c.toast("Đã kích hoạt Pro Account — mở khóa toàn bộ chức năng!")
                                        onUnlockedFeature()
                                    }
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "✦ Mở khóa Pro Account (${PlanTier.ENTERPRISE.priceFor(ui.billingCycle)}${ui.billingCycle.unitVi})",
                                    color = Color(0xFF0A2218),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.14f))
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                    .bounceClick {
                                        c.updateUiSettings { it.toggleFeatureOverride(paywallFeature, true) }
                                        c.toast("Đã bật riêng: ${paywallFeature.title}")
                                        onUnlockedFeature()
                                    }
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "Chỉ bật tính năng này",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }

                // Banner Bảng giá Month/Year & 2 thẻ Basic Account vs Pro Account
                AccountTierPricingBanner(
                    c = c,
                    inDialog = true,
                    onPlanActivated = {
                        if (paywallFeature != null && c.uiSettings.planTier == PlanTier.ENTERPRISE) {
                            onUnlockedFeature()
                        }
                    },
                )

                // Bảng gạt On/Off từng chức năng (Feature Flags)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(VC.bg)
                        .border(1.dp, VC.border, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f).widthIn(min = 200.dp)) {
                            Text(
                                "Bảng điều khiển Feature Flags (On / Off từng chức năng)",
                                color = VC.text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Bạn có thể bật/tắt độc lập từng mô-đun để kiểm thử hoặc tùy biến gói dịch vụ cho khách hàng.",
                                color = VC.muted,
                                fontSize = 12.sp,
                            )
                        }
                        if (ui.enabledFeatures.isNotEmpty() || ui.disabledFeatures.isNotEmpty()) {
                            VButton(
                                "Đặt lại mặc định gói",
                                {
                                    c.updateUiSettings {
                                        val resetSimple = if (it.planTier == PlanTier.FREE) true else it.simpleMode
                                        it.copy(
                                            simpleMode = resetSimple,
                                            enabledFeatures = emptySet(),
                                            disabledFeatures = emptySet(),
                                        )
                                    }
                                    c.toast("Đã khôi phục cấu hình mặc định của ${ui.planTier.itemName}.")
                                },
                                primary = false,
                            )
                        }
                    }

                    VFeature.entries.forEach { feat ->
                        val enabled = ui.isFeatureEnabled(feat)
                        val isOverridden = (feat.key in ui.enabledFeatures) || (feat.key in ui.disabledFeatures)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(VC.card)
                                .border(1.dp, if (enabled) VC.pine.copy(alpha = 0.35f) else VC.border, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                FlowRow(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        feat.title,
                                        color = VC.text,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (enabled) VC.mint.copy(alpha = 0.16f) else VC.amber.copy(alpha = 0.16f))
                                            .border(1.dp, if (enabled) VC.mint.copy(alpha = 0.45f) else VC.amber.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 1.dp),
                                    ) {
                                        Text(
                                            if (enabled) "ĐANG BẬT" else "PRO · TẮT",
                                            color = if (enabled) VC.mint else VC.amber,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    if (isOverridden) {
                                        Text(
                                            "(Tùy chỉnh)",
                                            color = VC.muted,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                                Text(
                                    feat.summary,
                                    color = VC.text2,
                                    fontSize = 12.sp,
                                )
                                Text(
                                    "Basic Account: ${feat.freeLimitNote}",
                                    color = VC.muted,
                                    fontSize = 11.5.sp,
                                )
                            }
                            Switch(
                                checked = enabled,
                                onCheckedChange = { next ->
                                    c.updateUiSettings { cur ->
                                        val updated = cur.toggleFeatureOverride(feat, next)
                                        if (feat == VFeature.ENGINEER_MODE && !next) {
                                            updated.copy(simpleMode = true)
                                        } else {
                                            updated
                                        }
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = VC.pine,
                                    uncheckedThumbColor = VC.muted,
                                    uncheckedTrackColor = VC.elevated,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
