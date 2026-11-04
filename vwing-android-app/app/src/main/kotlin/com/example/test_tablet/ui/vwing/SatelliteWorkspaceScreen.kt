package com.example.test_tablet.ui.vwing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.*
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One analysis owns its report and raster. Changing inputs replaces this state, never relabels old data. */
private class SatelliteWorkspaceRun {
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var imageError by mutableStateOf<String?>(null)
    var garden by mutableStateOf<SatGardenAnalysis?>(null)
    var index by mutableStateOf<SatIndex?>(null)
    var water by mutableStateOf<SatWater?>(null)
    var drought by mutableStateOf<DroughtMonitoringReport?>(null)
    var drone by mutableStateOf<DroneMultispectralReport?>(null)
    var bitmap by mutableStateOf<ImageBitmap?>(null)
    var alternateOverlay by mutableStateOf<MapOverlay?>(null)
    var job: Job? = null
    val bounds get() = garden?.bounds ?: index?.bounds ?: water?.bounds ?: drought?.bounds ?: drone?.bounds
    val hasResult get() = bounds != null
}

private fun encodeSatellitePoints(points: List<GeoPoint>) = Geo.open(points).joinToString(";") { "${it.lat},${it.lng}" }
private fun decodeSatellitePoints(value: String): List<GeoPoint> = value.split(';').mapNotNull { pair ->
    val xy = pair.split(',')
    if (xy.size != 2) null else {
        val lat = xy[0].toDoubleOrNull(); val lng = xy[1].toDoubleOrNull()
        if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) GeoPoint(lat, lng) else null
    }
}

/** All seven standalone Satellite workflows, using VWing clients, identity and visual components. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SatelliteWorkspaceScreen(c: VCtx, showNativeMap: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SatelliteSettings(context.filesDir) }
    var cfg by remember { mutableStateOf(settings.load()) }
    var editingConnection by rememberSaveable { mutableStateOf(false) }
    var connectionUrl by remember { mutableStateOf(cfg.url) }
    var connectionToken by remember { mutableStateOf(cfg.token) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var health by remember(cfg) { mutableStateOf<SatHealth?>(null) }
    var checking by remember(cfg) { mutableStateOf(false) }
    var modeName by rememberSaveable { mutableStateOf(SatelliteAnalysisMode.GARDEN.name) }
    val mode = SatelliteAnalysisMode.valueOf(modeName)
    val initialDates = remember { SatelliteWorkspaceDates.rolling(30) }
    var start by rememberSaveable { mutableStateOf(initialDates.start) }
    var end by rememberSaveable { mutableStateOf(initialDates.endExclusive) }
    var baselineStart by rememberSaveable { mutableStateOf(initialDates.preceding().start) }
    var baselineEnd by rememberSaveable { mutableStateOf(initialDates.preceding().endExclusive) }
    var cropName by rememberSaveable { mutableStateOf(SatCropKey.DURIAN.name) }
    val crop = SatCropKey.valueOf(cropName)
    var spacing by rememberSaveable { mutableStateOf("") }
    var encodedPoints by rememberSaveable { mutableStateOf(encodeSatellitePoints(SatelliteClient.ringOf(c.d).orEmpty())) }
    var regionName by rememberSaveable { mutableStateOf("Vùng phân tích của vườn") }
    val points = remember(encodedPoints) { decodeSatellitePoints(encodedPoints) }
    var opacity by rememberSaveable { mutableStateOf(.75f) }
    var helpIndex by rememberSaveable { mutableStateOf(-1) }
    val region = remember(points, regionName) { GeoFeature(id = "satellite-workspace", kind = FeatureKind.BOUNDARY, name = regionName, points = points) }
    val deepForestHud = remember(points, regionName) {
        val edges = computeEdgeLengthsM(points)
        RegionHudState("workspace-deepforest", regionName, points, Geo.polygonAreaHa(points), edges.sum(), edges, 0, 0, 0, 0, 0, 0)
    }
    val identity = SatelliteRequestIdentity(mode, points, start, end, baselineStart, baselineEnd, crop,
        satelliteSpacingM(spacing), cfg.url, cfg.token)
    val inputKey = identity.signature() + spacing
    val run = remember(inputKey) { SatelliteWorkspaceRun() }
    DisposableEffect(run) { onDispose { run.job?.cancel() } }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) cfg = settings.load()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun checkConnection(target: SatelliteConfig) {
        checking = true; connectionError = null
        scope.launch {
            val response = withContext(Dispatchers.IO) { SatelliteClient.health(target) }
            if (target == cfg) {
                health = response.getOrNull()
                connectionError = response.exceptionOrNull()?.message
                checking = false
            }
        }
    }

    fun analyze() {
        val problem = when {
            points.size < 3 -> "Chọn vùng mẫu hoặc nhấn giữ bản đồ để thêm ít nhất 3 đỉnh ranh giới."
            Geo.polygonAreaHa(points) <= 0.0 -> "Ranh giới chưa có diện tích. Hãy chọn các đỉnh khác nhau."
            !cfg.configured -> "Nhập địa chỉ và mã truy cập máy chủ vệ tinh."
            SyncClient.urlProblem(cfg.url) != null -> SyncClient.urlProblem(cfg.url)
            cfg.token.length < 12 -> "Mã truy cập máy chủ phải có ít nhất 12 ký tự."
            mode.usesDates && SatelliteWorkspaceDates.validate(start, end) != null -> SatelliteWorkspaceDates.validate(start, end)
            mode == SatelliteAnalysisMode.WATER && SatelliteWorkspaceDates.validate(baselineStart, baselineEnd) != null -> "Kỳ nền: ${SatelliteWorkspaceDates.validate(baselineStart, baselineEnd)}"
            mode == SatelliteAnalysisMode.WATER && baselineEnd > start -> "Kỳ nền phải kết thúc trước hoặc đúng ngày bắt đầu kỳ sự kiện."
            mode == SatelliteAnalysisMode.GARDEN -> satelliteSpacingProblem(spacing)
            else -> null
        }
        if (problem != null) { run.error = problem; return }
        run.error = null; run.imageError = null; run.busy = true
        run.garden = null; run.index = null; run.water = null; run.drought = null; run.drone = null
        run.bitmap = null; run.alternateOverlay = null
        run.job = scope.launch {
            try {
                val imageUrl = withContext(Dispatchers.IO) {
                    when (mode) {
                        SatelliteAnalysisMode.GARDEN -> SatelliteClient.gardenAnalysis(cfg, points, start, end, crop, satelliteSpacingM(spacing)).getOrThrow().let { g -> Pair(g, g.imageUrl) }
                        SatelliteAnalysisMode.NDVI, SatelliteAnalysisMode.EVI, SatelliteAnalysisMode.NDMI -> SatelliteClient.index(cfg, points, mode.indexKey!!, start, end).getOrThrow().let { i -> Pair(i, i.imageUrl) }
                        SatelliteAnalysisMode.WATER -> SatelliteClient.water(cfg, points, baselineStart, baselineEnd, start, end).getOrThrow().let { w -> Pair(w, w.imageUrl) }
                        SatelliteAnalysisMode.DROUGHT -> DroughtMonitoringClient.analyze(cfg, points).let { d -> Pair(d, d.activeOverlayUrl) }
                        SatelliteAnalysisMode.DRONE -> DroneMultispectralClient.analyze(cfg, points).let { d -> Pair(d, d.activeOverlayUrl) }
                    }
                }
                when (val report = imageUrl.first) {
                    is SatGardenAnalysis -> run.garden = report
                    is SatIndex -> run.index = report
                    is SatWater -> run.water = report
                    is DroughtMonitoringReport -> run.drought = report
                    is DroneMultispectralReport -> run.drone = report
                }
                run.bitmap = loadSatBitmap(cfg, imageUrl.second)
                if (imageUrl.second != null && run.bitmap == null) run.imageError = "Chưa tải được ảnh phủ. Báo cáo số liệu vẫn hiển thị; bạn có thể phân tích lại để lấy ảnh mới."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                run.error = "Không phân tích được: ${error.message ?: "Lỗi kết nối"}. Kiểm tra địa chỉ, mã truy cập, vùng và khoảng ngày."
            } finally { run.busy = false }
        }
    }

    PageHeader(Icons.Filled.SatelliteAlt, "SATELLITE TRONG VWING", "Phân tích vườn từ ảnh vệ tinh",
        "Chọn vùng, phương pháp và kỳ ảnh. Kết quả gắn với vùng đang chọn; dữ liệu mô phỏng luôn được ghi rõ.")
    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("Phương pháp phân tích")
        VPicker("Chọn phương pháp (7 chế độ)", SatelliteAnalysisMode.entries.map { it.label }, mode.label,
            { label -> modeName = SatelliteAnalysisMode.entries.first { it.label == label }.name }, Modifier.padding(top = 8.dp))
        Text(mode.description, color = VC.text2, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
        if (mode == SatelliteAnalysisMode.DROUGHT || mode == SatelliteAnalysisMode.DRONE) {
            Notice("Phân tích minh họa: số liệu và vùng phân loại của chức năng này hiện được tạo bằng mô phỏng/công thức, kể cả khi máy chủ đặt LIVE. Chưa dùng làm căn cứ phun thuốc, tưới hoặc bảo hiểm.", VC.amber, Modifier.padding(top = 10.dp))
        }
        if (mode.usesDates) {
            Spacer(Modifier.height(12.dp))
            ChipRow(listOf(30, 60, 90), SatelliteWorkspaceDates.range(start, end)?.days ?: -1, { "$it ngày qua" }, { days ->
                val range = SatelliteWorkspaceDates.rolling(days)
                start = range.start; end = range.endExclusive
            })
            Text(
                "Ảnh được lấy từ khoảng " + start + " → từng ngày trước\n" + end,
                color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
            )
            // Nhập ngày chính xác YYYY-MM-DD là công việc của kỹ sư: nông dân dùng chip 30/60/90 ngày
            TechnicalDetails(c.simpleMode) {
                SatelliteDateFields(if (mode == SatelliteAnalysisMode.WATER) "Kỳ sự kiện" else "Kỳ ảnh", start, end, { start = it }, { end = it })
                if (mode == SatelliteAnalysisMode.WATER) {
                    SatelliteDateFields("Kỳ nền", baselineStart, baselineEnd, { baselineStart = it }, { baselineEnd = it })
                    VButton("Dùng kỳ liền trước làm nền", {
                        val previous = SatelliteWorkspaceDates.range(start, end)?.preceding()
                        if (previous != null) { baselineStart = previous.start; baselineEnd = previous.endExclusive }
                        else run.error = "Nhập kỳ sự kiện hợp lệ trước."
                    }, primary = false, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        if (mode == SatelliteAnalysisMode.GARDEN) {
            VPicker("Loại cây trồng", SatCropKey.entries.map { it.fullLabel }, crop.fullLabel,
                { label -> cropName = SatCropKey.entries.first { it.fullLabel == label }.name }, Modifier.padding(top = 12.dp))
            // Khoảng cách cây có giá trị mặc định theo giống; chỉ cần chỉnh khi có lưới trồng đặc biệt
            TechnicalDetails(c.simpleMode) {
                VField(spacing, { spacing = it }, "Khoảng cách cây (m; để trống = ${crop.defaultSpacingM})", Modifier.fillMaxWidth().padding(top = 8.dp), number = true)
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("Vùng phân tích")
        Text("${points.size} đỉnh · ${fmtNum(Geo.polygonAreaHa(points), 2)} ha · $regionName", color = VC.text2, fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
        VPicker("Nạp vùng mẫu", listOf("Chọn vùng mẫu…") + SATELLITE_WORKSPACE_PRESETS.map { it.name }, "Chọn vùng mẫu…", { name ->
            SATELLITE_WORKSPACE_PRESETS.firstOrNull { it.name == name }?.let { preset ->
                encodedPoints = encodeSatellitePoints(preset.points); regionName = "Vùng mẫu: ${preset.name}"; cropName = preset.crop.name
            }
        })
        FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VButton("Dùng ranh vườn đã lưu", {
                val saved = SatelliteClient.ringOf(c.d)
                if (saved != null) { encodedPoints = encodeSatellitePoints(saved); regionName = "Ranh vườn đã lưu" }
                else run.error = "Vườn chưa có ranh giới. Hãy vẽ trên bản đồ hoặc chọn vùng mẫu."
            }, primary = false)
            VButton("Vẽ lại", { encodedPoints = ""; regionName = "Ranh vẽ mới" }, primary = false)
            VButton("Bỏ đỉnh cuối", { encodedPoints = encodeSatellitePoints(points.dropLast(1)) }, primary = false, enabled = points.isNotEmpty())
        }
        val deepForestSamplePoints = remember(deepForestHud.deepForestResult) {
            deepForestHud.deepForestResult?.boxes.orEmpty().map { it.toSatTreePoint() }
        }
        val mapSamplePoints = if (deepForestSamplePoints.isNotEmpty()) deepForestSamplePoints else run.garden?.treeEstimation?.samplePoints.orEmpty()
        val overlayBounds = run.alternateOverlay?.bounds ?: run.bounds
        val overlayBitmap = run.alternateOverlay?.bitmap ?: run.bitmap
        SatelliteWorkspaceMap(points, overlayBounds, overlayBitmap, opacity, mapSamplePoints,
            { p -> encodedPoints = encodeSatellitePoints(points + p); regionName = "Ranh vẽ mới" }, showNativeMap,
            Modifier.fillMaxWidth().height(if (c.wide) 400.dp else 300.dp))
        Text("Nhấn giữ bản đồ để thêm đỉnh (ít nhất 3). Vùng phân tích tạm thời không thay ranh vườn đã lưu.", color = VC.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        Text("Độ trong suốt lớp phủ: ${((1f - opacity) * 100).toInt()}%", color = VC.text, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        Slider(value = opacity, onValueChange = { opacity = it }, modifier = Modifier.fillMaxWidth(), valueRange = 0f..1f)
        Text("Nền Google Maps để đối chiếu vị trí; số liệu tính từ dữ liệu phân tích, không đo từ ảnh nền.", color = VC.muted, fontSize = 13.sp)
    }
    Spacer(Modifier.height(14.dp))
    VButton(if (run.busy) "Đang phân tích…" else "Phân tích ${mode.label}", { analyze() }, enabled = !run.busy, modifier = Modifier.fillMaxWidth())
    if (run.busy) {
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
        Text("Máy chủ có thể cần vài phút. Đổi vùng hoặc phương pháp sẽ bỏ kết quả yêu cầu cũ.", color = VC.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
    }
    run.error?.let { Notice(it, VC.red, Modifier.padding(top = 10.dp)) }
    run.imageError?.let { Notice(it, VC.amber, Modifier.padding(top = 10.dp)) }
    if (!run.busy && !run.hasResult && run.error == null) Text("Chưa có kết quả cho vùng và phương pháp đang chọn.", color = VC.text2, fontSize = 16.sp, modifier = Modifier.padding(vertical = 12.dp))
    run.garden?.let { Spacer(Modifier.height(14.dp)); GardenAnalysisResultCard(c, it, run.bitmap) }
    run.index?.let { SatelliteWorkspaceIndexResult(it) }
    run.water?.let { SatelliteWorkspaceWaterResult(it) }
    if (run.drought != null || run.drone != null) {
        val hud = remember(run, run.drought, run.drone, run.bitmap) {
            val edges = computeEdgeLengthsM(points)
            RegionHudState("workspace", regionName, points, Geo.polygonAreaHa(points), edges.sum(), edges, 0, 0, 0, 0, 0, 0).apply {
                droughtResult = run.drought?.copy(simulated = true); droughtBitmap = run.bitmap
                droneMsResult = run.drone?.copy(simulated = true); droneMsBitmap = run.bitmap
            }
        }
        // No auto-refresh of other domains: these cards receive only the requested report.
        if (run.drought != null) {
            DroughtMonitoringHubCard(hud, cfg, { run.alternateOverlay = it }, c.toast, simpleMode = c.simpleMode)
        }
        if (run.drone != null) {
            DroneMultispectralHubCard(hud, cfg, { run.alternateOverlay = it }, c.toast, simpleMode = c.simpleMode)
        }
    }
    Spacer(Modifier.height(14.dp))
    DeepForestHubCard(
        hud = deepForestHud,
        cfg = cfg,
        onSelectOverlay = { run.alternateOverlay = it },
        onToast = c.toast,
    )
    Spacer(Modifier.height(14.dp))
    AgResearch9PapersCard(
        hud = deepForestHud,
        cfg = cfg,
        onSelectOverlay = { run.alternateOverlay = it },
        onToast = c.toast,
    )
    Spacer(Modifier.height(14.dp))
    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("Kết nối máy chủ")
        Text(health?.let { "Đã kết nối · Máy chủ ${it.mode.uppercase()}" } ?: "Chưa kiểm tra kết nối trong phiên này.", color = VC.text2, fontSize = 15.sp, modifier = Modifier.padding(vertical = 8.dp))
        if (editingConnection || !cfg.configured) {
            VField(connectionUrl, { connectionUrl = it }, "Địa chỉ máy chủ", Modifier.fillMaxWidth())
            VField(connectionToken, { connectionToken = it }, "Mã truy cập", Modifier.fillMaxWidth().padding(top = 8.dp), secret = true)
            VButton("Lưu cấu hình kết nối", {
                connectionError = SyncClient.urlProblem(connectionUrl) ?: if (connectionToken.trim().length < 12) "Mã truy cập phải có ít nhất 12 ký tự." else null
                if (connectionError == null) {
                    cfg = SatelliteConfig(connectionUrl.trim().trimEnd('/'), connectionToken.trim())
                    settings.save(cfg); editingConnection = false; checkConnection(cfg)
                }
            }, modifier = Modifier.padding(top = 8.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VButton(if (checking) "Đang thử…" else "Kiểm tra kết nối", { checkConnection(cfg) }, primary = false, enabled = !checking && cfg.configured)
            VButton("Đổi kết nối", { connectionUrl = cfg.url; connectionToken = cfg.token; editingConnection = true }, primary = false)
        }
        connectionError?.let { Notice(it, VC.red, Modifier.padding(top = 8.dp)) }
    }
    Spacer(Modifier.height(14.dp))
    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("Hướng dẫn sử dụng dữ liệu")
        SATELLITE_GUIDANCE.forEachIndexed { i, guide ->
            VButton(guide.title, { helpIndex = i }, primary = false, modifier = Modifier.padding(top = 8.dp))
        }
        VButton("Mở công cụ vệ tinh nâng cao", { c.nav.push("satelliteAdvanced") }, primary = false, modifier = Modifier.padding(top = 12.dp))
        Text("Chuỗi thời gian, quét nhiều chỉ số, lưu NDVI vào hồ sơ và công cụ GIS hiện có vẫn ở mục nâng cao.", color = VC.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
    }
    SATELLITE_GUIDANCE.getOrNull(helpIndex)?.let { guide ->
        AlertDialog(onDismissRequest = { helpIndex = -1 }, title = { Text(guide.title) }, text = { Text(guide.body, Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { helpIndex = -1 }) { Text("Đã hiểu") } })
    }
}

@Composable
private fun SatelliteDateFields(label: String, start: String, end: String, onStart: (String) -> Unit, onEnd: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VField(start, onStart, "$label: từ (YYYY-MM-DD)", Modifier.fillMaxWidth())
        VField(end, onEnd, "$label: đến, không gồm ngày cuối", Modifier.fillMaxWidth())
    }
}

@Composable
private fun SatelliteWorkspaceMap(points: List<GeoPoint>, bounds: GeoBounds?, image: ImageBitmap?, opacity: Float,
    samples: List<SatTreePoint>, onAdd: (GeoPoint) -> Unit, showNativeMap: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val mapKeyAvailable = remember { mapsKeyConfigured(context) }
    if (!showNativeMap || !mapKeyAvailable) {
        if (image != null) ImageViewer(ViewerImage(image, bounds, "Lớp phân tích", opacity), modifier,
            trees = samples.map { ViewerTree(it.code, it.pos, it.condition) })
        else Column(modifier.padding(16.dp)) {
            Text("Bản đồ phân tích", color = VC.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Chọn vùng mẫu hoặc dùng ranh vườn đã lưu. Nền Google Maps cần khóa hợp lệ trong bản build.", color = VC.text2, fontSize = 16.sp)
        }
        return
    }
    val centerPt = remember(points) {
        if (points.isNotEmpty()) GeoPoint(points.sumOf { it.lat } / points.size, points.sumOf { it.lng } / points.size)
        else GeoPoint(11.55, 107.8)
    }
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(centerPt.lat, centerPt.lng), 18.6f) }
    LaunchedEffect(centerPt) { camera.position = CameraPosition.fromLatLngZoom(LatLng(centerPt.lat, centerPt.lng), 18.6f) }
    GoogleMap(modifier = modifier, cameraPositionState = camera, properties = MapProperties(mapType = MapType.HYBRID),
        uiSettings = MapUiSettings(zoomControlsEnabled = true, mapToolbarEnabled = false),
        onMapLongClick = { onAdd(GeoPoint(it.latitude, it.longitude)) }) {
        if (image != null && bounds != null && opacity > 0.02f) GroundOverlay(
            position = GroundOverlayPosition.create(LatLngBounds(LatLng(bounds.south, bounds.west), LatLng(bounds.north, bounds.east))),
            image = BitmapDescriptorFactory.fromBitmap(image.asAndroidBitmap()), transparency = 1f - opacity)
        if (points.size >= 3) {
            Polygon(points.map { LatLng(it.lat, it.lng) }, strokeColor = Color(0xCC06140C), fillColor = Color.Transparent, strokeWidth = 9.5f)
            Polygon(points.map { LatLng(it.lat, it.lng) }, strokeColor = Color(0xFFFFEA00), fillColor = Color.Transparent, strokeWidth = 5.5f)
        }
        points.forEachIndexed { index, point ->
            Marker(state = remember(point) { MarkerState(LatLng(point.lat, point.lng)) }, title = "Đỉnh ${index + 1}")
        }
        samples.take(80).forEach { point ->
            val hue = when (point.status) { "healthy" -> BitmapDescriptorFactory.HUE_GREEN; "moderate" -> BitmapDescriptorFactory.HUE_YELLOW; else -> BitmapDescriptorFactory.HUE_ORANGE }
            Marker(state = remember(point.code, point.pos) { MarkerState(LatLng(point.pos.lat, point.pos.lng)) },
                title = "${point.code} (${point.status})", snippet = "NDVI ${point.ndvi ?: "—"} · NDRE ${point.ndre ?: "—"}", icon = BitmapDescriptorFactory.defaultMarker(hue))
        }
    }
}

@Composable
private fun SatelliteWorkspaceIndexResult(result: SatIndex) {
    Spacer(Modifier.height(14.dp))
    SourceBanner(result.simulated, result.source)
    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("${result.index} · ${result.start} → ${result.endExclusive}")
        Text("Trung bình: ${result.mean?.let { fmtNum(it, 3) } ?: "Chưa có dữ liệu"}", color = VC.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Độ phủ hợp lệ: ${result.validCoveragePct?.let { fmtNum(it, 1) } ?: "—"}% · ${result.sceneCount} cảnh · ${fmtNum(result.areaHa, 2)} ha", color = VC.text2, fontSize = 16.sp)
        Legend(result.legend)
        Text(result.method, color = VC.text2, fontSize = 14.sp)
        result.warnings.forEach { Notice(it, VC.amber, Modifier.padding(top = 8.dp)) }
        Text(result.caution, color = VC.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun SatelliteWorkspaceWaterResult(result: SatWater) {
    Spacer(Modifier.height(14.dp))
    SourceBanner(result.simulated, result.source)
    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("Kết quả nước mặt Sentinel-1")
        Text("Nước mới: ${result.newWaterHa?.let { fmtNum(it, 2) } ?: "—"} ha\nNước thường xuyên: ${result.permanentWaterHa?.let { fmtNum(it, 2) } ?: "—"} ha\nNước đã rút: ${result.recededHa?.let { fmtNum(it, 2) } ?: "—"} ha", color = VC.text, fontSize = 18.sp)
        Text("${result.baselineScenes} cảnh nền · ${result.eventScenes} cảnh sự kiện · ${fmtNum(result.areaHa, 2)} ha", color = VC.text2, fontSize = 15.sp)
        Text("Chú giải: xám xanh = nước thường xuyên; xanh lam = nước mới; vàng cam = nước đã rút.", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        Text(result.method, color = VC.text2, fontSize = 14.sp)
        result.warnings.forEach { Notice(it, VC.amber, Modifier.padding(top = 8.dp)) }
        Text(result.caution, color = VC.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
    }
}
