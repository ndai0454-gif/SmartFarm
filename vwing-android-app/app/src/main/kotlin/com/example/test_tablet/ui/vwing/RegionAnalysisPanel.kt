package com.example.test_tablet.ui.vwing

import android.graphics.BitmapFactory
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

private fun analysisDate(ms: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(ms)
internal fun analysisDateProblem(start: String, end: String): String? {
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    fun parse(s: String): Long? = if (!s.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) null else runCatching { parser.parse(s)?.time }.getOrNull()
    val a = parse(start) ?: return "Ngày bắt đầu phải theo YYYY-MM-DD và tồn tại."
    val b = parse(end) ?: return "Ngày kết thúc phải theo YYYY-MM-DD và tồn tại."
    return when {
        b <= a -> "Ngày kết thúc phải sau ngày bắt đầu (không gồm ngày cuối)."
        b - a > 93L * 86_400_000 -> "Khoảng phân tích tối đa 93 ngày."
        else -> null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegionAnalysisPanel(c: VCtx, regions: List<GeoFeature>, region: GeoFeature?, onRegion: (String) -> Unit,
                        onOverlay: (MapOverlay?) -> Unit, onCloud: (CloudReport?) -> Unit,
                        regionHud: RegionHudState? = null) {
    val context = LocalContext.current
    val settings = remember { SatelliteSettings(context.filesDir) }
    var cfg by remember { mutableStateOf(settings.load()) }
    // Cấu hình có thể đổi từ nơi khác (màn khác, hoặc sửa tệp) nên nạp lại mỗi khi app quay lại màn hình.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) cfg = settings.load() }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    var showConfig by rememberSaveable { mutableStateOf(false) }
    var url by remember { mutableStateOf(cfg.url) }
    var token by remember { mutableStateOf(cfg.token) }
    var configError by remember { mutableStateOf<String?>(null) }
    var tab by c.mapAnalysisSession.tab
    var index by rememberSaveable { mutableStateOf(SatIndexKey.NDVI) }
    var crop by rememberSaveable { mutableStateOf(SatCropKey.DURIAN) }
    var customSpacing by rememberSaveable { mutableStateOf("") }
    var start by c.mapAnalysisSession.start
    var end by c.mapAnalysisSession.end
    var crs by rememberSaveable { mutableStateOf(CloudCrs.WGS84) }
    var format by rememberSaveable { mutableStateOf(CloudFormat.LAS) }
    var alpha by remember { mutableFloatStateOf(.75f) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<SatIndex?>(null) }
    var image by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var gardenResult by remember { mutableStateOf<SatGardenAnalysis?>(null) }
    var gardenImage by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var cloud by remember { mutableStateOf<CloudReport?>(null) }
    var cloudName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val signature = listOf(region?.id, region?.points, region?.version, index, crop, customSpacing, start, end, cfg)
    val currentSignature by rememberUpdatedState(signature)
    val cloudSignature = listOf(region?.id, region?.points, region?.version, crs, format)
    val currentCloudSignature by rememberUpdatedState(cloudSignature)
    val currentOverlay by rememberUpdatedState(onOverlay)
    val currentCloud by rememberUpdatedState(onCloud)
    val problem = RegionAnalysis.problem(region)

    LaunchedEffect(signature) { result = null; image = null; gardenResult = null; gardenImage = null; error = null; currentOverlay(null) }
    LaunchedEffect(result, gardenResult, cloud) {
        result?.let { r ->
            regionHud?.activeToolTitle = "Chỉ số ${r.index}"
            regionHud?.activeToolSummary = "TB ${r.mean?.let { fmtNum(it, 2) } ?: "—"} (P5–95: ${r.p5?.let { fmtNum(it, 2) } ?: "—"}..${r.p95?.let { fmtNum(it, 2) } ?: "—"}) · Độ phủ ${r.validCoveragePct?.let { fmtNum(it, 0) } ?: "?"}%"
            regionHud?.activeToolBadges = listOf(
                RegionHudBadge("Đang đo", r.index, r.mean?.let { fmtNum(it, 2) } ?: "—", "${r.start} → ${r.endExclusive} · ${r.sceneCount} cảnh", VC.lime),
            )
        }
        gardenResult?.let { g ->
            regionHud?.gardenAnalysis = g
            if (gardenImage != null) regionHud?.gardenBitmap = gardenImage
            regionHud?.activeToolTitle = "Tổng hợp vườn (${g.treeEstimation.cropLabelVi})"
            regionHud?.activeToolSummary = "~${g.treeEstimation.estimatedTrees} cây · Tán ${fmtNum(g.treeEstimation.canopyCoverPct, 0)}% · ${g.sceneCount} cảnh Sentinel-2"
        }
        cloud?.let { cl ->
            regionHud?.activeToolTitle = "Đám mây điểm LiDAR"
            regionHud?.activeToolSummary = "${cl.inRegion}/${cl.totalPoints} điểm · Z: ${fmtNum(cl.minZ, 1)}–${fmtNum(cl.maxZ, 1)}m (TB ${fmtNum(cl.meanZ, 2)}m)"
            regionHud?.activeToolBadges = listOf(
                RegionHudBadge("LiDAR", "Điểm trong vùng", "${cl.inRegion} điểm", "Mật độ ${fmtNum(cl.density, 2)} điểm/m²", VC.lime),
                RegionHudBadge("LiDAR", "Cao độ Z", "${fmtNum(cl.minZ, 1)}–${fmtNum(cl.maxZ, 1)} m", "TB ${fmtNum(cl.meanZ, 2)} m · P95 ${fmtNum(cl.p95Z, 2)} m", VC.cyan),
            )
        }
    }
    LaunchedEffect(tab, image, gardenImage, alpha, result, gardenResult) {
        when (tab) {
            "GIS & viễn thám" -> currentOverlay(result?.let { r -> image?.let { MapOverlay(r.bounds, it, alpha, "${if (r.simulated) "MÔ PHỎNG · " else ""}${r.index} · ${r.start} → ${r.endExclusive}") } })
            "Tổng hợp vườn" -> currentOverlay(gardenResult?.let { g -> gardenImage?.let { MapOverlay(g.bounds, it, alpha, "${if (g.simulated) "MÔ PHỎNG · " else ""}Tán ${g.treeEstimation.cropLabelVi} · ${g.treeEstimation.estimatedTrees} cây") } })
            else -> currentOverlay(null)
        }
    }

    val importCloud = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && region != null && !busy && problem == null) {
            val snapshot = cloudSignature
            val ring = region.points.toList(); val selectedFormat = format; val selectedCrs = crs
            busy = true; error = null; cloud = null; onCloud(null)
            scope.launch {
                try {
                    val (report, name) = withContext(Dispatchers.IO) {
                        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                            if (it.moveToFirst()) it.getString(0) else "Tệp đám mây điểm"
                        } ?: "Tệp đám mây điểm"
                        val report = context.contentResolver.openInputStream(uri)?.use { PointCloudAnalysis.analyze(it, selectedFormat, selectedCrs, ring) }
                            ?: kotlin.error("Không mở được tệp đã chọn.")
                        report to name
                    }
                    if (currentCloudSignature == snapshot) { cloud = report; cloudName = name; currentCloud(report) }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { if (currentCloudSignature == snapshot) error = e.message ?: "Không đọc được đám mây điểm." }
                finally { busy = false }
            }
        }
    }

    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("PHÂN TÍCH VÙNG ĐÃ CHỌN")
        Text("GIS, viễn thám & đám mây điểm", color = VC.text, fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.padding(top = 6.dp))
        if (regions.isNotEmpty()) {
            val labels = regions.map { "${it.name} · ${it.id.takeLast(5)}" }
            VPicker("Ranh giới phân tích", labels, region?.let { "${it.name} · ${it.id.takeLast(5)}" } ?: "", { label -> onRegion(regions[labels.indexOf(label)].id) }, Modifier.padding(top = 10.dp))
        }
        region?.let {
            Text("${Geo.open(it.points).size} đỉnh · ${fmtNum(Geo.polygonAreaHa(it.points), 2)} ha · ${it.source.ifBlank { "Nguồn chưa ghi" }}", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            if (it.source.contains("mẫu", ignoreCase = true) && !it.source.contains("thực tế", ignoreCase = true)) Notice("Bạn đang chọn ranh mẫu, chưa phải vị trí vườn thực. Vẽ lại ranh vườn để phân tích đúng khu đất.", VC.amber, Modifier.padding(top = 8.dp))
        }
        problem?.let { Notice(it, VC.amber, Modifier.padding(top = 8.dp)) }
        ChipRow(listOf("GIS & viễn thám", "Dữ liệu mở & Shapefile", "Tổng hợp vườn", "Quy trình GIS", "Super-Resolution", "Bản đồ chuyên đề", "Đường đi & dòng chảy", "Địa hình", "Mô hình 3D", "LiDAR & đám mây điểm"), tab, { it }, { tab = it }, Modifier.padding(top = 12.dp))

        if (tab == "GIS & viễn thám") {
            Text("Phân tích Sentinel-2 trong đúng polygon; ảnh phủ sẽ hiện trên bản đồ vườn.", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            ChipRow(SatIndexKey.entries.toList(), index, { it.label }, { index = it }, Modifier.padding(top = 8.dp))
            Text(index.blurb, color = VC.muted, fontSize = 14.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                VField(start, { start = it }, "Từ ngày (YYYY-MM-DD)", Modifier.widthIn(min = 180.dp).weight(1f))
                VField(end, { end = it }, "Đến ngày (không gồm)", Modifier.widthIn(min = 180.dp).weight(1f))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                VButton(if (busy) "Đang phân tích…" else "Phân tích vùng", {
                    if (!cfg.configured) { showConfig = true; error = "Nhập địa chỉ và mã truy cập máy chủ vệ tinh."; return@VButton }
                    val dateError = analysisDateProblem(start, end)
                    if (dateError != null) { error = dateError; return@VButton }
                    val snapshot = signature; val ring = region!!.points.toList(); val selectedIndex = index
                    val from = start; val to = end; val config = cfg
                    busy = true; error = null; result = null; image = null; onOverlay(null)
                    scope.launch {
                        try {
                            val r = withContext(Dispatchers.IO) { SatelliteClient.index(config, ring, selectedIndex, from, to).getOrThrow() }
                            var imageError: String? = null
                            val bitmap = withContext(Dispatchers.IO) {
                                r.imageUrl?.let { u -> SatelliteClient.image(config, u).fold(
                                    onSuccess = { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap().also { if (it == null) imageError = "Ảnh phủ không đọc được." } },
                                    onFailure = { imageError = it.message; null }) }
                            }
                            if (currentSignature == snapshot) { result = r; image = bitmap; error = imageError }
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { if (currentSignature == snapshot) error = e.message ?: "Không phân tích được vùng." }
                        finally { busy = false }
                    }
                }, enabled = !busy && problem == null)
                VButton(if (showConfig) "Ẩn kết nối" else "Kết nối vệ tinh", { showConfig = !showConfig }, primary = false, enabled = !busy)
            }
            if (showConfig) {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VField(url, { url = it }, "Địa chỉ máy chủ", placeholder = "http://192.168.1.10:8000")
                    VField(token, { token = it }, "Mã truy cập", secret = true)
                    configError?.let { Text(it, color = VC.red, fontSize = 14.sp) }
                    VButton("Lưu kết nối", {
                        configError = SyncClient.urlProblem(url) ?: if (token.trim().length < 12) "Mã truy cập cần ít nhất 12 ký tự." else null
                        if (configError == null) runCatching { SatelliteConfig(url.trim(), token.trim()).also { settings.save(it); cfg = it }; showConfig = false }.onFailure { configError = "Không lưu được cấu hình." }
                    }, enabled = !busy)
                }
            }
            result?.let { r ->
                val lowCoverage = (r.validCoveragePct ?: 100.0) < 50
                Spacer(Modifier.height(10.dp))
                VerdictBanner(
                    if (r.mean == null) Verdict.UNKNOWN else if (lowCoverage) Verdict.WARN else Verdict.GOOD,
                    "${r.index}: ${r.mean?.let { fmtNum(it, 2) } ?: "Chưa có giá trị"}",
                    if (lowCoverage) "Mây/thiếu ảnh nhiều (độ phủ hợp lệ ${r.validCoveragePct?.let { fmtNum(it, 0) } ?: "?"}%) — số liệu kém tin cậy."
                    else "${r.start} → ${r.endExclusive}" + (if (r.simulated) " · DỮ LIỆU MÔ PHỎNG, không dùng để quyết định" else ""),
                )
                if (image != null) {
                    Text("Độ đậm lớp ảnh", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
                    androidx.compose.material3.Slider(alpha, { alpha = it }, valueRange = .1f..1f)
                } else Text("Chưa có ảnh phủ; xem thống kê và kiểm tra kết nối tải ảnh.", color = VC.amber, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
                TechnicalDetails(c.simpleMode, Modifier.padding(top = 4.dp)) {
                    Text("${r.sceneCount} cảnh · ${fmtNum(r.areaHa, 2)} ha · ${r.pixelSizeM} m/điểm ảnh", color = VC.text2, fontSize = 14.sp)
                    val colors = r.legend.palette.mapNotNull { hex -> runCatching { Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")) }.getOrNull() }
                    if (colors.size >= 2) Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(12.dp).background(Brush.horizontalGradient(colors)))
                    Text("${r.legend.label}: ${r.legend.min} → ${r.legend.max}", color = VC.muted, fontSize = 13.sp)
                    Text("Phương pháp: ${r.method}", color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    r.warnings.forEach { Text("• $it", color = VC.amber, fontSize = 14.sp) }
                    Text(r.caution, color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        } else if (tab == "Tổng hợp vườn") {
            Text("Đếm số cây ước tính theo tán quang phổ, phân tích 7 chỉ số đất/tán (NDVI, EVI, NDRE, NDMI, SAVI, NDWI, BSI), 9 nguồn lớp phủ Việt Nam (Dynamic World, ESA WorldCover, JAXA ALOS-2, Copernicus) và địa hình DEM.", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            VPicker(
                "Loại cây trồng chủ lực",
                SatCropKey.entries.map { it.fullLabel },
                crop.fullLabel,
                { label -> SatCropKey.entries.firstOrNull { it.fullLabel == label }?.let { crop = it } },
                Modifier.padding(top = 8.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                VField(start, { start = it }, "Từ ngày (YYYY-MM-DD)", Modifier.widthIn(min = 160.dp).weight(1f))
                VField(end, { end = it }, "Đến ngày (không gồm)", Modifier.widthIn(min = 160.dp).weight(1f))
                VField(customSpacing, { customSpacing = it }, "Cự ly cây (m, trống = ${fmtNum(crop.defaultSpacingM, 1)}m)", Modifier.widthIn(min = 180.dp).weight(1f), number = true)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                VButton(if (busy) "Đang phân tích tổng hợp…" else "Phân tích tổng hợp vườn", {
                    if (!cfg.configured) { showConfig = true; error = "Nhập địa chỉ và mã truy cập máy chủ vệ tinh."; return@VButton }
                    val dateError = analysisDateProblem(start, end)
                    if (dateError != null) { error = dateError; return@VButton }
                    val spacingVal = customSpacing.trim().takeIf { it.isNotEmpty() }?.let {
                        it.replace(',', '.').toDoubleOrNull()?.takeIf { v -> v in 0.6..25.0 } ?: run {
                            error = "Cự ly cây tùy chỉnh phải từ 0,6 đến 25 m."
                            return@VButton
                        }
                    }
                    val snapshot = signature; val ring = region!!.points.toList(); val selectedCrop = crop
                    val from = start; val to = end; val config = cfg
                    busy = true; error = null; gardenResult = null; gardenImage = null; onOverlay(null)
                    scope.launch {
                        try {
                            val g = withContext(Dispatchers.IO) { SatelliteClient.gardenAnalysis(config, ring, from, to, selectedCrop, spacingVal).getOrThrow() }
                            val bitmap = loadSatBitmap(config, g.imageUrl)
                            if (currentSignature == snapshot) { gardenResult = g; gardenImage = bitmap }
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { if (currentSignature == snapshot) error = e.message ?: "Không phân tích được vùng vườn." }
                        finally { busy = false }
                    }
                }, enabled = !busy && problem == null)
                VButton(if (showConfig) "Ẩn kết nối" else "Kết nối vệ tinh", { showConfig = !showConfig }, primary = false, enabled = !busy)
            }
            if (showConfig) {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VField(url, { url = it }, "Địa chỉ máy chủ", placeholder = "http://192.168.1.10:8000")
                    VField(token, { token = it }, "Mã truy cập", secret = true)
                    configError?.let { Text(it, color = VC.red, fontSize = 14.sp) }
                    VButton("Lưu kết nối", {
                        configError = SyncClient.urlProblem(url) ?: if (token.trim().length < 12) "Mã truy cập cần ít nhất 12 ký tự." else null
                        if (configError == null) runCatching { SatelliteConfig(url.trim(), token.trim()).also { settings.save(it); cfg = it }; showConfig = false }.onFailure { configError = "Không lưu được cấu hình." }
                    }, enabled = !busy)
                }
            }
            gardenResult?.let { g ->
                if (gardenImage != null) {
                    Text("Độ đậm lớp phân tầng tán trên bản đồ vườn", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
                    androidx.compose.material3.Slider(alpha, { alpha = it }, valueRange = .1f..1f)
                }
                Spacer(Modifier.height(10.dp))
                GardenAnalysisResultCard(c, g, gardenImage, showViewer = true)
            }
        } else if (tab == "Quy trình GIS") {
            RegionWorkflowPanel(c, region, cfg, start, end, { start = it }, { end = it },
                onConfigure = { tab = "GIS & viễn thám"; showConfig = true },
                onOverlay = { ov ->
                    onOverlay(ov)
                    if (ov != null) {
                        regionHud?.activeToolTitle = "Quy trình GIS"
                        regionHud?.activeToolSummary = ov.label
                    }
                })
        } else if (tab == "Super-Resolution") {
            SuperResolutionPanel(c, region, cfg, start, end, { start = it }, { end = it },
                onConfigure = { tab = "GIS & viễn thám"; showConfig = true },
                onOverlay = { ov ->
                    onOverlay(ov)
                    if (ov != null) {
                        regionHud?.activeToolTitle = "Super-Resolution AI"
                        regionHud?.activeToolSummary = ov.label
                    }
                })
        } else if (tab == "Dữ liệu mở & Shapefile") {
            OpenGisPanel(c, region, cfg, onConfigure = { tab = "GIS & viễn thám"; showConfig = true }, regionHud = regionHud)
        } else if (tab == "Bản đồ chuyên đề") {
            ThematicPanel(c, region, problem, onOverlay = { ov ->
                onOverlay(ov)
                if (ov != null) {
                    regionHud?.activeToolTitle = "Bản đồ chuyên đề"
                    regionHud?.activeToolSummary = ov.label
                }
            })
        } else if (tab == "Đường đi & dòng chảy") {
            AccessPanel(c, region, problem, onOverlay = { ov ->
                onOverlay(ov)
                if (ov != null) {
                    regionHud?.activeToolTitle = "Đường đi & dòng chảy"
                    regionHud?.activeToolSummary = ov.label
                }
            })
        } else if (tab == "Mô hình 3D") {
            LayeredTerrain3D(
                region = region,
                hud = regionHud,
                cfg = cfg,
                onOpenDiseaseScreen = { c.nav.push("disease") },
            )
        } else if (tab == "Địa hình") {
            TerrainPanel(c, region, problem, onOverlay = { ov ->
                onOverlay(ov)
                if (ov != null) {
                    regionHud?.activeToolTitle = "Địa hình DEM"
                    regionHud?.activeToolSummary = ov.label
                }
            })
        } else {
            Text(if (cloud == null) "Chưa có dữ liệu LiDAR của vườn. Khi có tệp đo, app sẽ cắt điểm theo ranh đã chọn và tính thống kê cao độ tại máy." else "Đã phân tích tệp điểm trong đúng ranh giới đã chọn.", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
            VPicker("Định dạng tệp", CloudFormat.entries.map { it.name }, format.name, { format = CloudFormat.valueOf(it) }, Modifier.padding(top = 8.dp))
            VPicker("Hệ tọa độ của tệp", CloudCrs.entries.map { it.label }, crs.label, { label -> crs = CloudCrs.entries.first { it.label == label } }, Modifier.padding(top = 8.dp))
            Text("Chọn đúng hệ tọa độ theo hồ sơ đo. CSV: x,y,z[,classification]; WGS84 dùng x=kinh độ, y=vĩ độ. Z phải tính bằng mét. LAS không nén; LAZ chưa hỗ trợ. Tối đa 20 MB / 250.000 điểm.", color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            VButton(if (busy) "Đang đọc và cắt vùng…" else "Nhập & phân tích tệp điểm", { importCloud.launch(arrayOf("*/*")) }, enabled = !busy && problem == null, modifier = Modifier.padding(top = 10.dp))
            cloud?.let { r ->
                Spacer(Modifier.height(10.dp))
                VerdictBanner(Verdict.GOOD, "Đã đọc ${r.inRegion}/${r.totalPoints} điểm trong vùng", "Cao độ Z: ${fmtNum(r.minZ, 1)}–${fmtNum(r.maxZ, 1)} m")
                TechnicalDetails(c.simpleMode, Modifier.padding(top = 4.dp)) {
                    Text("$cloudName · ${crs.label}", color = VC.teal, fontSize = 14.sp)
                    Text("${fmtNum(r.density, 3)} điểm/m² · Trung bình: ${fmtNum(r.meanZ, 2)} m · P95: ${fmtNum(r.p95Z, 2)} m · Điểm có nhãn mặt đất (class 2): ${r.groundPoints}", color = VC.text2, fontSize = 14.sp)
                    Text("Bản đồ hiển thị tối đa ${r.preview.size} điểm: xanh = Z thấp, vàng = Z cao. Thống kê tính trên tất cả điểm trong vùng. Cao độ không phải chiều cao cây; chưa tạo DTM/DSM hay chẩn đoán cây.", color = VC.muted, fontSize = 13.sp)
                }
            }
        }
        error?.let { Notice(it, VC.red, Modifier.padding(top = 10.dp)) }
    }
}
