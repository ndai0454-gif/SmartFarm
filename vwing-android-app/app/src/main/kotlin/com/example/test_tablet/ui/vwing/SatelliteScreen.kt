package com.example.test_tablet.ui.vwing

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.Dates
import com.example.test_tablet.data.vwing.FeatureKind
import com.example.test_tablet.data.vwing.Geo
import com.example.test_tablet.data.vwing.GeoFeature
import com.example.test_tablet.data.vwing.IndexSource
import com.example.test_tablet.data.vwing.IndexStat
import com.example.test_tablet.data.vwing.Preprocess
import com.example.test_tablet.data.vwing.SatCropKey
import com.example.test_tablet.data.vwing.SatGardenAnalysis
import com.example.test_tablet.data.vwing.SatHealth
import com.example.test_tablet.data.vwing.SatIndex
import com.example.test_tablet.data.vwing.SatIndexKey
import com.example.test_tablet.data.vwing.SatLegend
import com.example.test_tablet.data.vwing.SatSeries
import com.example.test_tablet.data.vwing.SatWater
import com.example.test_tablet.data.vwing.SatelliteClient
import com.example.test_tablet.data.vwing.SatelliteConfig
import com.example.test_tablet.data.vwing.SatelliteSettings
import com.example.test_tablet.data.vwing.SensorKind
import com.example.test_tablet.data.vwing.SyncClient
import com.example.test_tablet.data.vwing.VIETNAM_GARDEN_PRESETS
import com.example.test_tablet.data.vwing.VegIndex
import com.example.test_tablet.data.vwing.activeTrees
import com.example.test_tablet.data.vwing.effectiveCondition
import com.example.test_tablet.data.vwing.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

private fun iso(ms: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(ms)
private fun isoToMs(s: String): Long = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(s)?.time ?: 0L
private fun colorOf(hex: String) = Color(0xFF000000.toInt() or hex.removePrefix("#").toInt(16))
private val DAY = 86_400_000L

internal suspend fun loadSatBitmap(cfg: SatelliteConfig, url: String?): ImageBitmap? {
    if (url == null) return null
    return withContext(Dispatchers.IO) {
        SatelliteClient.image(cfg, url).getOrNull()?.let { b -> BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SatelliteScreen(c: VCtx) {
    val d = c.d
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SatelliteSettings(context.filesDir) }
    var cfg by remember { mutableStateOf(settings.load()) }
    // Cấu hình có thể đổi từ nơi khác (màn khác, hoặc sửa tệp) nên nạp lại mỗi khi app quay lại màn hình.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) cfg = settings.load() }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    var editing by remember { mutableStateOf(!cfg.configured) }
    var url by remember { mutableStateOf(cfg.url) }
    var token by remember { mutableStateOf(cfg.token) }
    var cfgErr by remember { mutableStateOf<String?>(null) }
    var health by remember { mutableStateOf<SatHealth?>(null) }
    var healthErr by remember { mutableStateOf<String?>(null) }
    var checkingHealth by remember { mutableStateOf(false) }

    var crop by remember { mutableStateOf(SatCropKey.DURIAN) }
    var customSpacing by remember { mutableStateOf("") }
    var gardenAnalysis by remember { mutableStateOf<SatGardenAnalysis?>(null) }
    var gardenImg by remember { mutableStateOf<ImageBitmap?>(null) }

    var index by remember { mutableStateOf(SatIndexKey.NDVI) }
    var days by remember { mutableStateOf(30) }
    var busy by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<SatIndex?>(null) }
    var resultImg by remember { mutableStateOf<ImageBitmap?>(null) }
    var series by remember { mutableStateOf<SatSeries?>(null) }
    var water by remember { mutableStateOf<SatWater?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var scan by remember { mutableStateOf<List<ScanRow>?>(null) }
    var scanProgress by remember { mutableStateOf<String?>(null) }
    var waterImg by remember { mutableStateOf<ImageBitmap?>(null) }
    val today = Dates.today()
    var b0 by remember { mutableStateOf(Dates.fmt(today - 60 * DAY)) }; var b1 by remember { mutableStateOf(Dates.fmt(today - 30 * DAY)) }
    var e0 by remember { mutableStateOf(Dates.fmt(today - 10 * DAY)) }; var e1 by remember { mutableStateOf(Dates.fmt(today + DAY)) }
    val ring = SatelliteClient.ringOf(d)

    fun checkServer(targetCfg: SatelliteConfig = cfg) {
        if (!targetCfg.configured) return
        checkingHealth = true; healthErr = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { SatelliteClient.health(targetCfg) }
            r.onSuccess { health = it; healthErr = null }.onFailure { health = null; healthErr = it.message }
            checkingHealth = false
        }
    }

    LaunchedEffect(cfg.url, cfg.token) {
        if (cfg.configured) checkServer(cfg)
    }

    PageHeader(
        Icons.Filled.SatelliteAlt,
        if (c.simpleMode) "NẮNG HẠN & SỨC KHỎE VƯỜN" else "VỆ TINH & LỚP PHỦ GEE",
        if (c.simpleMode) "Kiểm tra nắng hạn, độ ẩm & đếm cây trong vườn" else "Sentinel-2 · Sentinel-1 · RustGIS · 9 nguồn LULC VN",
        if (c.simpleMode) "Xem ngay vườn có bị khô nóng thiếu nước hay không, đếm số lượng cây khỏe/yếu từ trên cao và gợi ý cách tưới nước, bón phân."
        else "Đếm cây ước tính theo tán quang phổ, phân tích 7 chỉ số đất/tán (NDVI, EVI, NDRE, NDMI, SAVI, NDWI, BSI), lớp phủ đất Việt Nam và nước mặt sau mưa lớn.",
    )

    if (showPicker) BoundaryPickerDialog(
        c,
        onDismiss = { showPicker = false },
        onSaved = { gardenAnalysis = null; gardenImg = null; result = null; resultImg = null; scan = null; series = null; water = null },
    )

    // ---- kết nối (ẩn ở Chế độ Nông dân khi đã kết nối ổn định để tránh rối mắt) ----
    if (!c.simpleMode || !cfg.configured || editing || healthErr != null) {
    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("MÁY CHỦ VỆ TINH (BACKEND PYTHON + EARTH ENGINE)")
        if (!editing && cfg.configured) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(cfg.url, color = VC.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    when {
                        checkingHealth -> Text("Đang kiểm tra kết nối tới máy chủ vệ tinh…", color = VC.muted, fontSize = 13.sp)
                        health != null -> {
                            val modeLabel = if (health!!.mode == "live") "LIVE (Google Earth Engine thật)" else "MOCK (Mô phỏng)"
                            Text("Đã kết nối · Chế độ: $modeLabel · ${health!!.cropProfilesCount} cây trồng · ${health!!.lulcSourcesCount} nguồn LULC VN", color = VC.mint, fontSize = 13.sp)
                        }
                        healthErr != null -> Text(healthErr!!, color = VC.red, fontSize = 13.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VButton(if (checkingHealth) "Đang thử…" else "Kiểm tra kết nối", { checkServer(cfg) }, primary = false, enabled = !checkingHealth)
                    VButton("Đổi", { url = cfg.url; token = cfg.token; editing = true }, primary = false)
                }
            }
        } else {
            Text("Backend Python gọi Google Earth Engine & RustGIS; thông tin xác thực Earth Engine chỉ nằm ở máy chủ, app chỉ giữ mã truy cập của máy chủ này.", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VField(url, { url = it; cfgErr = null }, "Địa chỉ máy chủ vệ tinh", placeholder = "http://192.168.1.10:8000 hoặc http://127.0.0.1:8000")
                VField(token, { token = it }, "Mã truy cập", secret = true)
            }
            cfgErr?.let { Text(it, color = VC.red, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp)) }
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VButton("Lưu cấu hình", {
                    val p = SyncClient.urlProblem(url)
                    if (p != null) cfgErr = p else if (token.isBlank()) cfgErr = "Nhập mã truy cập."
                    else {
                        val next = SatelliteConfig(url.trim(), token.trim())
                        cfg = next; settings.save(next); editing = false
                        checkServer(next)
                        c.toast("Đã lưu cấu hình vệ tinh.")
                    }
                })
                VButton("4G / Cloudflare HTTPS", { url = SatelliteConfig.DEFAULT_SAT_URL; cfgErr = null }, primary = false)
                VButton("Wi-Fi LAN (192.168.1.42:8000)", { url = "http://192.168.1.42:8000"; cfgErr = null }, primary = false)
                VButton("Cổng USB (127.0.0.1:8000)", { url = "http://127.0.0.1:8000"; cfgErr = null }, primary = false)
                if (cfg.configured) VButton("Hủy", { editing = false; cfgErr = null }, primary = false)
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    }

    // ---- vùng vườn cần quét ----
    val presetLabels = remember { listOf("Nạp nhanh tọa độ vườn thực tế tại Việt Nam…") + VIETNAM_GARDEN_PRESETS.map { "${it.name} (${it.regionLabel})" } }
    val fallbackPreset = remember { VIETNAM_GARDEN_PRESETS.first() }
    val primaryBoundaryFeature = d.features.firstOrNull { it.kind == FeatureKind.BOUNDARY && it.points.size >= 3 }
        ?: d.features.firstOrNull { it.kind == FeatureKind.ZONE && it.points.size >= 3 }
        ?: GeoFeature(
            id = "sat_default_boundary",
            kind = FeatureKind.BOUNDARY,
            name = fallbackPreset.name,
            points = fallbackPreset.points,
            source = "Tọa độ mẫu VN: ${fallbackPreset.name}",
        )
    var satHudOverlay by remember(primaryBoundaryFeature.id, primaryBoundaryFeature.points) { mutableStateOf<MapOverlay?>(null) }
    val satRegionHud = rememberRegionHudState(
        d = d,
        region = primaryBoundaryFeature,
        cfg = cfg,
        start = iso(today - days * DAY),
        end = iso(today + DAY),
        onAutoOverlay = { if (satHudOverlay == null) satHudOverlay = it },
    )
    // ---- 1. Thẻ Nắng hạn & Tưới nước đặt ngay đầu trang để nông dân nhìn thấy 3 con số to ngay ----
    if (satRegionHud != null) {
        DroughtMonitoringHubCard(
            hud = satRegionHud,
            cfg = cfg,
            onApplyOverlay = { satHudOverlay = it },
            onToast = c.toast,
            simpleMode = c.simpleMode,
        )
        Spacer(Modifier.height(14.dp))
        DeepForestHubCard(
            hud = satRegionHud,
            cfg = cfg,
            onSelectOverlay = { satHudOverlay = it },
            onToast = c.toast,
        )
        Spacer(Modifier.height(14.dp))
        AgResearch9PapersCard(
            hud = satRegionHud,
            cfg = cfg,
            onSelectOverlay = { satHudOverlay = it },
            onToast = c.toast,
        )
        Spacer(Modifier.height(14.dp))
    }

    var showSatGoogleMap by remember { mutableStateOf(true) }
    val hasMapsKey = remember { mapsKeyConfigured(context) }

    // ---- 2. Bản đồ vườn & Đổi nhanh khu vườn ----
    VCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val ha = ring?.let { Geo.polygonAreaHa(it) } ?: 0.0
            Text(
                "📍 ${primaryBoundaryFeature.name} · ${fmtNum(ha, 2)} ha",
                color = VC.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            VButton(if (ring == null) "Chọn vùng vườn" else "Vẽ lại ranh vườn", { showPicker = true }, primary = false, icon = Icons.Filled.Map)
        }
        if (hasMapsKey) {
            ChipRow(
                listOf(true, false),
                showSatGoogleMap,
                { if (it) "🛰 Vệ tinh Google Siêu nét" else "🗺 Bản đồ GIS 2D" },
                { showSatGoogleMap = it },
                Modifier.padding(top = 8.dp),
            )
        }
        VPicker(
            "Đổi khu vườn",
            presetLabels,
            presetLabels.first(),
            { picked ->
                val idx = presetLabels.indexOf(picked) - 1
                if (idx in VIETNAM_GARDEN_PRESETS.indices) {
                    val pr = VIETNAM_GARDEN_PRESETS[idx]
                    val f = GeoFeature(newId(), FeatureKind.BOUNDARY, pr.name, pr.points, source = "Tọa độ thực tế VN: ${pr.name}")
                    c.store.applyGeoImport(listOf(f), emptyList(), "tọa độ vườn thực tế VN")
                    crop = pr.crop
                    gardenAnalysis = null; gardenImg = null; result = null; resultImg = null; scan = null; series = null; water = null
                    c.toast("Đã nạp ${pr.name} (${fmtNum(Geo.polygonAreaHa(pr.points), 2)} ha).")
                }
            },
            Modifier.padding(top = 8.dp),
        )
        if (ring != null) {
            Spacer(Modifier.height(10.dp))
            if (showSatGoogleMap && hasMapsKey) {
                GoogleMapOverlayView(
                    region = primaryBoundaryFeature,
                    overlay = satHudOverlay,
                    modifier = Modifier.fillMaxWidth().height(if (c.wide) 440.dp else 380.dp),
                    regionHud = satRegionHud,
                    hudCfg = cfg,
                    hudStart = iso(today - days * DAY),
                    hudEnd = iso(today + DAY),
                    onHudSelectOverlay = { satHudOverlay = it },
                    onHudRefresh = {
                        satRegionHud?.refreshAll(
                            cfg,
                            iso(today - days * DAY),
                            iso(today + DAY),
                            scope,
                            onAutoOverlay = { satHudOverlay = it },
                        )
                    },
                )
            } else {
                GardenMap(
                    d = d,
                    mode = MapMode.GEO,
                    layers = emptySet(),
                    selectedId = c.selectedTree,
                    modifier = Modifier.fillMaxWidth().height(if (c.wide) 420.dp else 360.dp),
                    overlays = listOfNotNull(satHudOverlay),
                    analysisRegion = ring,
                    regionHud = satRegionHud,
                    hudCfg = cfg,
                    hudStart = iso(today - days * DAY),
                    hudEnd = iso(today + DAY),
                    onHudSelectOverlay = { satHudOverlay = it },
                    onHudRefresh = {
                        satRegionHud?.refreshAll(
                            cfg,
                            iso(today - days * DAY),
                            iso(today + DAY),
                            scope,
                            onAutoOverlay = { satHudOverlay = it },
                        )
                    },
                )
            }
        }
    }
    Spacer(Modifier.height(14.dp))

    // ---- 3. Toàn bộ công cụ chuyên sâu GEE / Sentinel-2 / Sentinel-1 / OpenGIS bọc gọn trong TechnicalDetails ----
    TechnicalDetails(c.simpleMode) {
    if (satRegionHud != null) {
        RegionIconLegendCard(satRegionHud)
        Spacer(Modifier.height(14.dp))
        DroneMultispectralHubCard(
            hud = satRegionHud,
            cfg = cfg,
            onApplyOverlay = { satHudOverlay = it },
            onToast = c.toast,
            simpleMode = c.simpleMode,
        )
        Spacer(Modifier.height(14.dp))
    }

    // ---- phân tích tổng hợp vườn (GEE + RustGIS: Đếm cây + 7 chỉ số + 9 nguồn LULC VN) ----
    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("PHÂN TÍCH TỔNG HỢP VƯỜN · GEE + RUSTGIS")
        Text("Ước tính số lượng cây trồng & phân tầng sức khỏe tán, kết hợp 7 chỉ số vùng đất/tán (NDVI, EVI, NDRE, NDMI, SAVI, NDWI, BSI), đa nguồn lớp phủ Việt Nam (Dynamic World, ESA WorldCover, JAXA ALOS-2, Copernicus) và địa hình DEM.", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        VPicker(
            "Loại cây trồng chủ lực (ước tính mật độ & đường kính tán)",
            SatCropKey.entries.map { it.fullLabel },
            crop.fullLabel,
            { label -> SatCropKey.entries.firstOrNull { it.fullLabel == label }?.let { crop = it } },
            Modifier.padding(top = 10.dp),
        )
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VField(
                customSpacing,
                { customSpacing = it },
                "Khoảng cách cây tùy chỉnh (m, để trống = chuẩn ${fmtNum(crop.defaultSpacingM, 1)} m)",
                Modifier.widthIn(min = 240.dp).weight(1f),
                number = true,
            )
        }
        ChipRow(listOf(30, 60, 90), days, { "$it ngày qua" }, { days = it }, Modifier.padding(top = 10.dp))
        Row(Modifier.padding(top = 12.dp)) {
            VButton(
                if (busy == "garden") "Đang phân tích tổng hợp GEE & RustGIS…" else "Phân tích tổng hợp vườn (Đếm cây & Lớp phủ VN)",
                {
                    val spacingVal = customSpacing.trim().takeIf { it.isNotEmpty() }?.let {
                        it.replace(',', '.').toDoubleOrNull()?.takeIf { v -> v in 0.6..25.0 } ?: run {
                            err = "Khoảng cách cây tùy chỉnh phải từ 0,6 đến 25 m."
                            return@VButton
                        }
                    }
                    err = null; busy = "garden"
                    scope.launch {
                        val r = withContext(Dispatchers.IO) {
                            SatelliteClient.gardenAnalysis(cfg, ring!!, iso(today - days * DAY), iso(today + DAY), crop, spacingVal)
                        }
                        r.onSuccess {
                            gardenAnalysis = it
                            gardenImg = loadSatBitmap(cfg, it.imageUrl)
                        }.onFailure {
                            err = it.message
                            gardenAnalysis = null
                            gardenImg = null
                        }
                        busy = null
                    }
                },
                icon = Icons.Filled.Forest,
                enabled = busy == null && cfg.configured && ring != null,
            )
        }
    }

    gardenAnalysis?.let { g ->
        Spacer(Modifier.height(14.dp))
        GardenAnalysisResultCard(c, g, gardenImg)
    }

    Spacer(Modifier.height(14.dp))

    // ---- chỉ số thực vật / độ ẩm tán ----
    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("CHỈ SỐ THEO VƯỜN · SENTINEL-2")
        ChipRow(SatIndexKey.values().toList(), index, { it.label }, { index = it }, Modifier.padding(top = 10.dp))
        Text(index.blurb, color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        ChipRow(listOf(30, 60, 90), days, { "$it ngày qua" }, { days = it }, Modifier.padding(top = 10.dp))
        FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VButton(if (busy == "index") "Đang lấy dữ liệu…" else "Xem chỉ số", {
                err = null; busy = "index"; series = null
                scope.launch {
                    val r = withContext(Dispatchers.IO) { SatelliteClient.index(cfg, ring!!, index, iso(today - days * DAY), iso(today + DAY)) }
                    r.onSuccess { result = it; resultImg = loadSatBitmap(cfg, it.imageUrl) }.onFailure { err = it.message; result = null; resultImg = null }
                    busy = null
                }
            }, icon = Icons.Filled.SatelliteAlt, enabled = busy == null && cfg.configured && ring != null)
            VButton(if (busy == "series") "Đang tính chuỗi…" else "Xem theo thời gian (6 tháng)", {
                err = null; busy = "series"
                scope.launch {
                    val r = withContext(Dispatchers.IO) { SatelliteClient.series(cfg, ring!!, index, iso(today - 180 * DAY), iso(today + DAY)) }
                    r.onSuccess { series = it }.onFailure { err = it.message; series = null }
                    busy = null
                }
            }, primary = false, enabled = busy == null && cfg.configured && ring != null)
            VButton(scanProgress ?: "Quét toàn diện (3 chỉ số, so kỳ trước)", {
                err = null; busy = "scan"; scan = null
                scope.launch {
                    val rows = mutableListOf<ScanRow>()
                    for ((i, k) in SatIndexKey.values().withIndex()) {
                        scanProgress = "Đang quét ${i + 1}/3: ${k.label}…"
                        val now = withContext(Dispatchers.IO) { SatelliteClient.index(cfg, ring!!, k, iso(today - days * DAY), iso(today + DAY)) }
                        val prev = withContext(Dispatchers.IO) { SatelliteClient.index(cfg, ring!!, k, iso(today - 2 * days * DAY), iso(today - days * DAY)) }
                        rows += ScanRow(k, now.getOrNull(), prev.getOrNull(), now.exceptionOrNull()?.message, prev.exceptionOrNull()?.message)
                        if (now.exceptionOrNull()?.message?.contains("mã truy cập") == true) break
                    }
                    scan = rows; scanProgress = null; busy = null
                    rows.firstOrNull { it.key == SatIndexKey.NDVI }?.now?.let { r -> result = r; resultImg = loadSatBitmap(cfg, r.imageUrl) }
                    if (rows.all { it.now == null }) err = rows.firstNotNullOfOrNull { it.error } ?: "Không quét được."
                }
            }, icon = Icons.Filled.Insights, enabled = busy == null && cfg.configured && ring != null)
        }
        err?.let { Notice(it, VC.red, Modifier.padding(top = 10.dp)) }
        if (busy != null) Text("Earth Engine có thể mất vài chục giây.", color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
    }

    scan?.let { rows -> ScanCard(c, rows, days) }

    result?.let { r ->
        Spacer(Modifier.height(14.dp))
        SourceBanner(r.simulated, r.source)
        VCard(Modifier.fillMaxWidth()) {
            Eyebrow("${r.index} · ${r.start} → ${r.endExclusive} (không gồm ngày cuối)")
            Spacer(Modifier.height(8.dp))
            resultImg?.let { img ->
                ImageViewer(
                    ViewerImage(img, r.bounds, "${r.index} ${r.start}…", alpha = 1f), Modifier.fillMaxWidth().height(380.dp),
                    trees = d.activeTrees.mapNotNull { t -> t.pos?.let { ViewerTree(t.code, it, d.effectiveCondition(t)) } },
                )
                Legend(r.legend)
            } ?: Text("Không tải được ảnh phủ (số liệu bên dưới vẫn hợp lệ).", color = VC.amber, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Kpi("Trung bình toàn vườn", r.mean?.let { fmtNum(it, 2) } ?: "—", "", "Trung vị các cảnh, ${r.sceneCount} cảnh", Modifier.weight(1f))
                Kpi("Điểm ảnh hợp lệ", r.validCoveragePct?.let { fmtNum(it, 0) } ?: "—", "%", "Sau khi loại mây/bóng/tuyết", Modifier.weight(1f), warn = (r.validCoveragePct ?: 100.0) < 50)
            }
            TechnicalDetails(c.simpleMode, Modifier.padding(top = 8.dp)) {
                Text("Phân vị 5–95: ${r.p5?.let { fmtNum(it, 2) } ?: "—"} … ${r.p95?.let { fmtNum(it, 2) } ?: "—"} · ${fmtNum(r.areaHa, 1)} ha · ${r.pixelSizeM} m/điểm ảnh", color = VC.text2, fontSize = 14.sp)
                Text("Phương pháp: ${r.method}", color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                r.warnings.forEach { Text("• $it", color = VC.amber, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
                Text(r.caution, color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            }
            if (r.index == "NDVI") {
                Row(Modifier.padding(top = 12.dp)) {
                    VButton("Lưu vào hồ sơ NDVI", {
                        if (r.simulated) { c.toast("Dữ liệu mô phỏng không được lưu vào hồ sơ."); return@VButton }
                        val mean = r.mean ?: return@VButton
                        val lo = (r.p5 ?: mean).coerceAtMost(mean); val hi = (r.p95 ?: mean).coerceAtLeast(mean)
                        val pixels = (r.areaHa * 10_000.0 / (r.pixelSizeM * r.pixelSizeM)).toInt().coerceAtLeast(1)
                        val rec = VegIndex(
                            newId(), "NDVI Sentinel-2 ${r.start}→${r.endExclusive}", IndexSource.PROVIDED, isoToMs(r.endExclusive) - DAY + 12 * 3_600_000L, r.source,
                            SensorKind.MULTISPECTRAL, Preprocess.REFLECTANCE, r.pixelSizeM.toDouble(), r.bounds, canopyRadiusM = 3.0,
                            stats = listOf(IndexStat("garden", mean, lo, hi, pixels, r.validCoveragePct ?: 0.0)),
                            notes = listOf(r.method, "Ảnh ghép trung vị ${r.sceneCount} cảnh, không phải ảnh một ngày.", "Min/Max là phân vị 5 và 95.", r.caution),
                        )
                        val o = c.store.addVegIndex(rec)
                        c.toast(if (o.ok) "Đã lưu vào hồ sơ NDVI." else o.message ?: "Không lưu được.")
                    }, primary = false, enabled = !r.simulated)
                }
                if (r.simulated) Text("Dữ liệu mô phỏng không thể lưu vào hồ sơ.", color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }

    series?.let { s ->
        Spacer(Modifier.height(14.dp))
        SourceBanner(s.simulated, s.source)
        VCard(Modifier.fillMaxWidth()) {
            Eyebrow("${s.index} THEO THỜI GIAN · ${s.points.size} ngày có dữ liệu")
            if (s.points.size < 2) Text("Cần ít nhất hai ngày có dữ liệu để vẽ biến thiên.", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            else SeriesChart(s.points.map { isoToMs(it.date) to it.mean }, Modifier.fillMaxWidth().height(150.dp).padding(top = 8.dp))
            s.points.takeLast(6).reversed().forEach {
                Text("${it.date}: ${fmtNum(it.mean, 2)} · ${fmtNum(it.validCoveragePct, 0)}% điểm ảnh hợp lệ", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
            s.warnings.forEach { Text("• $it", color = VC.amber, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
            Text("Ngày thiếu ảnh/mây được bỏ khỏi chuỗi, không tính là 0. Độ phân giải ${s.pixelSizeM} m. ${s.caution}", color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }

    // ---- nước mặt / ngập (Sentinel-1) ----
    Spacer(Modifier.height(14.dp))
    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("NƯỚC MẶT SAU MƯA LỚN · SENTINEL-1")
        Text("So nước mặt trong kỳ nền với kỳ sau sự kiện để gợi ý chỗ cần đi kiểm tra. Không phân biệt được hết mặt đường nhẵn, bóng địa hình hay đất rất ướt; nước mặt mới không đồng nghĩa với thiệt hại cây trồng.", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { VField(b0, { b0 = it }, "Kỳ nền: từ", Modifier.weight(1f)); VField(b1, { b1 = it }, "đến (không gồm)", Modifier.weight(1f)) }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { VField(e0, { e0 = it }, "Kỳ sự kiện: từ", Modifier.weight(1f)); VField(e1, { e1 = it }, "đến (không gồm)", Modifier.weight(1f)) }
        Row(Modifier.padding(top = 10.dp)) {
            VButton(if (busy == "water") "Đang phân tích radar…" else "Phân tích nước mặt", {
                val x = listOf(b0, b1, e0, e1).map { Dates.parse(it) }
                if (x.any { it == null }) { err = "Ngày không hợp lệ (dd/MM/yyyy)."; return@VButton }
                err = null; busy = "water"
                scope.launch {
                    val r = withContext(Dispatchers.IO) { SatelliteClient.water(cfg, ring!!, iso(x[0]!!), iso(x[1]!!), iso(x[2]!!), iso(x[3]!!)) }
                    r.onSuccess { water = it; waterImg = loadSatBitmap(cfg, it.imageUrl) }.onFailure { err = it.message; water = null; waterImg = null }
                    busy = null
                }
            }, enabled = busy == null && cfg.configured && ring != null)
        }
    }
    water?.let { w ->
        Spacer(Modifier.height(14.dp))
        SourceBanner(w.simulated, w.source)
        VCard(Modifier.fillMaxWidth()) {
            Eyebrow("KẾT QUẢ · ${w.baselineScenes} cảnh nền, ${w.eventScenes} cảnh sự kiện")
            Spacer(Modifier.height(8.dp))
            waterImg?.let { img ->
                ImageViewer(ViewerImage(img, w.bounds, "Nước mặt"), Modifier.fillMaxWidth().height(340.dp),
                    trees = d.activeTrees.mapNotNull { t -> t.pos?.let { ViewerTree(t.code, it, d.effectiveCondition(t)) } })
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    listOf("8a9a8f" to "Nước thường xuyên", "1e78b8" to "Mới xuất hiện", "e0a030" to "Đã rút").forEach { (col, t) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Canvas(Modifier.padding(end = 2.dp).height(12.dp).width(12.dp)) { drawRect(colorOf(col)) }; Text(t, color = VC.text2, fontSize = 13.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Nước mới xuất hiện: ${w.newWaterHa?.let { fmtNum(it, 2) } ?: "—"} ha · thường xuyên: ${w.permanentWaterHa?.let { fmtNum(it, 2) } ?: "—"} ha · đã rút: ${w.recededHa?.let { fmtNum(it, 2) } ?: "—"} ha (trên ${fmtNum(w.areaHa, 1)} ha vườn)",
                color = VC.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            )
            TechnicalDetails(c.simpleMode, Modifier.padding(top = 4.dp)) {
                Text("Phương pháp: ${w.method}", color = VC.muted, fontSize = 13.sp)
                w.warnings.forEach { Text("• $it", color = VC.amber, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
            }
            Row(Modifier.padding(top = 12.dp)) {
                VButton("Tạo công việc kiểm tra thực địa", {
                    val ha = w.newWaterHa ?: 0.0
                    val o = c.store.addTask("Kiểm tra thực địa chỗ nghi có nước mới sau mưa (Sentinel-1 ≈ ${fmtNum(ha, 2)} ha)" + if (w.simulated) " [dữ liệu mô phỏng]" else "", emptyList(), null, "", Dates.now() + 3 * DAY)
                    c.toast(if (o.ok) "Đã tạo công việc kiểm tra." else o.message ?: "")
                }, primary = false, icon = Icons.Filled.SatelliteAlt)
            }
        }
    }

    // ---- Dữ liệu không gian mở & Shapefile (GADM, Geodata, OSMnx, Overpass, HDX, World Bank, GDAL/GeoPandas) ----
    Spacer(Modifier.height(14.dp))
    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("DỮ LIỆU KHÔNG GIAN MỞ · GADM · GEODATA · OSMNX · HDX · WORLD BANK · GDAL/GEOPANDAS")
        val primaryRegion = d.features.firstOrNull { it.kind == FeatureKind.BOUNDARY }
            ?: d.features.firstOrNull { it.kind == FeatureKind.ZONE }
        OpenGisPanel(
            c = c,
            region = primaryRegion,
            cfg = cfg,
            onConfigure = { url = cfg.url; token = cfg.token; editing = true },
            regionHud = satRegionHud,
        )
    }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GardenAnalysisResultCard(c: VCtx, g: SatGardenAnalysis, gardenImg: ImageBitmap?, showViewer: Boolean = true) {
    val d = c.d
    val te = g.treeEstimation
    val hb = te.healthBreakdown
    var showDetectedPoints by remember { mutableStateOf(true) }

    SourceBanner(g.simulated, g.source)
    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("KẾT QUẢ TỔNG HỢP VƯỜN · ${g.start} → ${g.endExclusive} (${g.sceneCount} cảnh Sentinel-2)")
        Text(
            "${te.cropLabelVi} · Diện tích ${fmtNum(g.areaHa, 2)} ha · Chu vi ${fmtNum(g.perimeterM, 0)} m · Độ phủ hợp lệ ${fmtNum(g.validCoveragePct, 0)}%",
            color = VC.text, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp),
        )
        if (showViewer) {
            Spacer(Modifier.height(8.dp))
            gardenImg?.let { img ->
                val viewerTrees = if (showDetectedPoints && te.samplePoints.isNotEmpty()) {
                    te.samplePoints.map { ViewerTree(it.code, it.pos, it.condition) }
                } else {
                    d.activeTrees.mapNotNull { t -> t.pos?.let { ViewerTree(t.code, it, d.effectiveCondition(t)) } }
                }
                ImageViewer(
                    ViewerImage(img, g.bounds, "Phân tầng tán ${te.cropLabelVi}", alpha = 1f),
                    Modifier.fillMaxWidth().height(360.dp),
                    trees = viewerTrees,
                )
                Legend(g.legend)
                if (te.samplePoints.isNotEmpty()) {
                    ChipRow(
                        listOf(true, false),
                        showDetectedPoints,
                        { if (it) "Điểm tán vệ tinh (${te.samplePoints.size})" else "Cây trong sổ vườn (${d.activeTrees.size})" },
                        { showDetectedPoints = it },
                        Modifier.padding(top = 8.dp),
                    )
                }
            } ?: Text("Không tải được ảnh phân tầng tán (báo cáo số liệu bên dưới vẫn đầy đủ).", color = VC.amber, fontSize = 14.sp)
        }

        Spacer(Modifier.height(10.dp))
        Eyebrow("1. ƯỚC TÍNH SỐ LƯỢNG CÂY & PHÂN TẦNG SỨC KHỎE TÁN")
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Kpi(
                "Số cây ước tính",
                "${te.estimatedTrees}",
                "cây",
                "Khoảng ${te.estimatedTreesMin}–${te.estimatedTreesMax} cây · mật độ ${fmtNum(te.effectiveDensityPerHa, 0)} cây/ha (chuẩn ${fmtNum(te.assumedSpacingM, 1)} m)",
                Modifier.weight(1f),
            )
            Kpi(
                "Độ che phủ tán",
                fmtNum(te.canopyCoverPct, 1),
                "%",
                "${fmtNum(te.canopyAreaHa, 2)} ha có tán · ${te.detectedCanopyPeaks} đỉnh tán quang phổ",
                Modifier.weight(1f),
                warn = te.canopyCoverPct < 45.0,
            )
        }
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Kpi("Tán khỏe", "${hb.healthyTrees}", "cây", "${fmtNum(hb.healthyHa, 2)} ha", Modifier.weight(1f))
            Kpi("Tán trung bình", "${hb.moderateTrees}", "cây", "${fmtNum(hb.moderateHa, 2)} ha", Modifier.weight(1f))
            Kpi("Tán yếu / cần khám", "${hb.stressedTrees}", "cây", "${fmtNum(hb.stressedHa, 2)} ha", Modifier.weight(1f), warn = hb.stressedTrees > 0)
        }

        Spacer(Modifier.height(12.dp))
        Divider2()
        Spacer(Modifier.height(10.dp))
        Eyebrow("2. BỘ 7 CHỈ SỐ VÙNG ĐẤT & TÁN CÂY (SENTINEL-2 SR)")
        g.indices.forEach { ix ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${ix.index} · ${ix.title}", color = VC.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text(ix.mean?.let { fmtNum(it, 3) } ?: "—", color = VC.teal, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
                Text(
                    "P5–P95: ${ix.p5?.let { fmtNum(it, 2) } ?: "—"} … ${ix.p95?.let { fmtNum(it, 2) } ?: "—"} · ${ix.pixelSizeM} m · ${ix.statusVi}",
                    color = VC.text2, fontSize = 13.sp,
                )
                if (ix.interpretationVi.isNotBlank()) {
                    Text(ix.interpretationVi, color = VC.muted, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Divider2()
        Spacer(Modifier.height(10.dp))
        Eyebrow("3. LỚP PHỦ BỀ MẶT VIỆT NAM (GEE LULC)")
        g.landCover.values.forEach { lc ->
            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Text("${lc.title} (${lc.resolutionM} m)", color = VC.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "Chiếm ưu thế: ${lc.dominantLabelVi} · Tán cây ${fmtNum(lc.treeCanopyPct, 1)}% · Cây trồng ${fmtNum(lc.cropPct, 1)}% · Mặt nước ${fmtNum(lc.waterPct, 1)}% · Đất trống/Xây dựng ${fmtNum(lc.bareOrBuiltPct, 1)}%",
                    color = VC.text2, fontSize = 13.sp,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Divider2()
        Spacer(Modifier.height(10.dp))
        Eyebrow("4. ĐỘ ẨM, ĐẤT TRỐNG & ĐỊA HÌNH DEM")
        val sm = g.soilAndMoisture
        val tr = g.terrain
        Text("• Trạng thái ẩm: ${sm.moistureStatusVi} · Tỷ lệ đất lộ nắng (BSI): ${fmtNum(sm.bareSoilExposurePct, 1)}%", color = VC.text, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        if (sm.irrigationAdviceVi.isNotBlank()) {
            Text("• Gợi ý tưới & giữ ẩm: ${sm.irrigationAdviceVi}", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Text(
            "• Địa hình (${tr.dataset}): cao độ TB ${tr.elevationMeanM?.let { fmtNum(it, 1) } ?: "—"} m (${tr.elevationMinM?.let { fmtNum(it, 1) } ?: "—"}–${tr.elevationMaxM?.let { fmtNum(it, 1) } ?: "—"} m) · Độ dốc TB ${tr.slopeMeanDeg?.let { fmtNum(it, 1) } ?: "—"}° (${tr.terrainClassVi})",
            color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp),
        )

        if (g.recommendations.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Divider2()
            Spacer(Modifier.height(10.dp))
            Eyebrow("5. KHUYẾN NGHỊ KIỂM TRA THỰC ĐỊA")
            g.recommendations.forEach { rec ->
                Text("• $rec", color = VC.text, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }

        TechnicalDetails(c.simpleMode, Modifier.padding(top = 10.dp)) {
            Text("Phương pháp: ${g.method}", color = VC.muted, fontSize = 13.sp)
            if (g.vietnamLulcRef.isNotEmpty()) {
                Text("Danh mục ${g.vietnamLulcRef.size} nguồn LULC Việt Nam & Mekong đối chiếu: " + g.vietnamLulcRef.joinToString("; ") { "${it.title} (${it.resolutionM}m)" }, color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
            g.warnings.forEach { Text("• $it", color = VC.amber, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
            Text(g.caution, color = VC.text2, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }

        FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VButton(
                "Tạo công việc kiểm tra thực địa",
                {
                    val summary = "Kiểm tra thực địa (${te.cropLabelVi}): ${hb.stressedTrees}/${te.estimatedTrees} cây tán yếu (${fmtNum(hb.stressedHa, 2)} ha), độ phủ tán ${fmtNum(te.canopyCoverPct, 1)}%" +
                        if (g.simulated) " [dữ liệu mô phỏng]" else ""
                    val notes = g.recommendations.joinToString("\n")
                    val o = c.store.addTask(summary, emptyList(), null, notes, Dates.now() + 3 * DAY)
                    c.toast(if (o.ok) "Đã tạo công việc kiểm tra thực địa." else o.message ?: "Không tạo được công việc.")
                },
                primary = false,
                icon = Icons.Filled.CheckCircle,
            )
        }
    }
}

class ScanRow(val key: SatIndexKey, val now: SatIndex?, val prev: SatIndex?, val error: String?, val prevError: String? = null)

@Composable
private fun ScanCard(c: VCtx, rows: List<ScanRow>, days: Int) {
    Spacer(Modifier.height(14.dp))
    rows.firstOrNull { it.now != null }?.now?.let { SourceBanner(it.simulated, it.source) }
    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("QUÉT TOÀN DIỆN · $days NGÀY GẦN NHẤT SO VỚI $days NGÀY TRƯỚC ĐÓ")
        Spacer(Modifier.height(6.dp))
        rows.forEach { r ->
            val n = r.now?.mean; val p = r.prev?.mean
            val delta = if (n != null && p != null) n - p else null
            val lowCover = (r.now?.validCoveragePct ?: 100.0) < 50 || (r.prev?.validCoveragePct ?: 100.0) < 50
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.key.label, color = VC.text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.weight(.18f))
                    Text(n?.let { fmtNum(it, 2) } ?: "—", color = VC.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(.2f))
                    Text("trước: " + (p?.let { fmtNum(it, 2) } ?: "—"), color = VC.text2, fontSize = 14.sp, modifier = Modifier.weight(.28f))
                    Text(
                        delta?.let { (if (it >= 0) "+" else "") + fmtNum(it, 2) } ?: "—",
                        color = when { delta == null -> VC.muted; lowCover -> VC.muted; delta <= -0.05 -> VC.red; delta >= 0.05 -> VC.mint; else -> VC.text2 },
                        fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(.16f),
                    )
                    Text(r.now?.validCoveragePct?.let { "${fmtNum(it, 0)}%" } ?: "", color = VC.muted, fontSize = 13.sp, modifier = Modifier.weight(.18f))
                }
                r.error?.let { Text(it, color = VC.red, fontSize = 13.sp) }
                if (n != null && p == null) Text("Không so sánh được với kỳ trước: ${r.prevError ?: "thiếu ảnh"}", color = VC.muted, fontSize = 13.sp)
                if (lowCover && delta != null) Text("Độ phủ hợp lệ thấp (<50%): chênh lệch kém tin cậy.", color = VC.amber, fontSize = 13.sp)
            }
            Divider2()
        }
        val ndvi = rows.firstOrNull { it.key == SatIndexKey.NDVI }
        val dn = ndvi?.let { r -> if (r.now?.mean != null && r.prev?.mean != null && (r.now.validCoveragePct ?: 0.0) >= 50 && (r.prev.validCoveragePct ?: 0.0) >= 50) r.now.mean - r.prev.mean else null }
        if (dn != null) {
            Text(
                when { dn <= -0.05 -> "NDVI giảm ${fmtNum(-dn, 2)} so với kỳ trước."; dn >= 0.05 -> "NDVI tăng ${fmtNum(dn, 2)} so với kỳ trước."; else -> "NDVI gần như không đổi so với kỳ trước." } +
                    " Ngưỡng ±0,05 chỉ để gợi ý, chưa hiệu chỉnh cho vườn của bạn; biến đổi theo mùa, mưa và mây cũng làm số đổi.",
                color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp),
            )
            if (dn <= -0.05) Row(Modifier.padding(top = 10.dp)) {
                VButton("Tạo công việc kiểm tra thực địa", {
                    val o = c.store.addTask("Kiểm tra thực địa: NDVI Sentinel-2 giảm ${fmtNum(-dn, 2)} so với kỳ trước (chưa phải kết luận bệnh/thiếu nước)", emptyList(), null, "", Dates.now() + 3 * DAY)
                    c.toast(if (o.ok) "Đã tạo công việc kiểm tra." else o.message ?: "")
                }, primary = false, icon = Icons.Filled.Insights)
            }
        }
        Text("Ảnh ghép trung vị, 10 m/điểm ảnh: chỉ theo dõi xu hướng cả vườn, không đủ kết luận từng cây hay bệnh. NDMI là độ ẩm tán lá, không phải độ ẩm đất.", color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
internal fun SourceBanner(simulated: Boolean, source: String) {
    if (simulated) Notice("DỮ LIỆU MÔ PHỎNG — $source. Chỉ để thử giao diện, không phải ảnh vệ tinh thật.", VC.amber, Modifier.padding(bottom = 10.dp))
    else Text("Nguồn: $source", color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
internal fun Legend(l: SatLegend) {
    Column(Modifier.padding(top = 8.dp)) {
        val colors = l.palette.map(::colorOf)
        Canvas(Modifier.fillMaxWidth().height(10.dp)) { drawRect(Brush.horizontalGradient(if (colors.size > 1) colors else colors + colors), size = Size(size.width, size.height)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmtNum(l.min, 1), color = VC.muted, fontSize = 13.sp); Text(l.label, color = VC.text2, fontSize = 13.sp); Text(fmtNum(l.max, 1), color = VC.muted, fontSize = 13.sp)
        }
        Text("Vùng trong suốt = thiếu dữ liệu (mây/bóng hoặc ngoài ranh).", color = VC.muted, fontSize = 13.sp)
    }
}
