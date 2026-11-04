package com.example.test_tablet.ui.vwing

import androidx.compose.material.icons.filled.Map
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.CheckStatus
import com.example.test_tablet.data.vwing.Condition
import com.example.test_tablet.data.vwing.Dates
import com.example.test_tablet.data.vwing.FeatureKind
import com.example.test_tablet.data.vwing.GardenData
import com.example.test_tablet.data.vwing.Geo
import com.example.test_tablet.data.vwing.GeoFeature
import com.example.test_tablet.data.vwing.GeoPoint
import com.example.test_tablet.data.vwing.ImportItem
import com.example.test_tablet.data.vwing.ImportKind
import com.example.test_tablet.data.vwing.PosSource
import com.example.test_tablet.data.vwing.Geom
import com.example.test_tablet.data.vwing.VFeature
import com.example.test_tablet.data.vwing.VWingStore
import com.example.test_tablet.data.vwing.activeTrees
import com.example.test_tablet.data.vwing.checkStatus
import com.example.test_tablet.data.vwing.dataGaps
import com.example.test_tablet.data.vwing.effectiveCondition
import com.example.test_tablet.data.vwing.newId
import com.example.test_tablet.data.vwing.treeById
import com.example.test_tablet.data.vwing.zoneDesc
import com.example.test_tablet.data.vwing.zoneNames

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapScreen(c: VCtx) {
    val d = c.d
    val hasGeo = d.activeTrees.any { it.pos != null } || d.features.isNotEmpty()
    var mode by c.mapAnalysisSession.mode
    if (!c.mapAnalysisSession.modeInitialized.value) {
        mode = if (hasGeo) MapMode.GEO else MapMode.SCHEMATIC
        c.mapAnalysisSession.modeInitialized.value = true
    }
    var zone by c.mapAnalysisSession.zone
    val layers = remember { mutableStateListOf<MapLayer>() }
    var showAdd by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showBoundary by remember { mutableStateOf(false) }
    var placing by remember { mutableStateOf(false) }
    var pendingPos by remember { mutableStateOf<GeoPoint?>(null) }
    var overlayKey by rememberSaveable { mutableStateOf("") }
    val overlays = rememberMapOverlays(c, overlayKey)
    val regions = com.example.test_tablet.data.vwing.RegionAnalysis.regions(d, zone)
    val googleMarkedBoundary = d.features.firstOrNull { it.kind == FeatureKind.BOUNDARY }
    var regionId by c.mapAnalysisSession.regionId
    val region = regions.firstOrNull { it.id == regionId } ?: googleMarkedBoundary ?: regions.firstOrNull()
    var analysisOverlay by remember(region?.id, region?.points) { mutableStateOf<MapOverlay?>(null) }
    var analysisCloud by remember(region?.id, region?.points) { mutableStateOf<com.example.test_tablet.data.vwing.CloudReport?>(null) }
    var showGoogleMap by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var activeMapTool by rememberSaveable { mutableStateOf("SPACING_QR") } // SPACING_QR | 3D | DEEPLAB | NWS_FFC | TOF | DEEPFOREST | AI_SAT | NONE
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val satCfg = remember { com.example.test_tablet.data.vwing.SatelliteSettings(context.filesDir).load() }
    val regionHud = rememberRegionHudState(
        d = d,
        region = region,
        cfg = satCfg,
        start = c.mapAnalysisSession.start.value,
        end = c.mapAnalysisSession.end.value,
        onAutoOverlay = { if (analysisOverlay == null) analysisOverlay = it },
    )

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(Geo.exportGeoJson(c.store.data).toByteArray()) }
            c.toast("Đã xuất GeoJSON.")
        }.onFailure { c.toast("Không xuất được: ${it.message}") }
    }

    val selected = c.selectedTree?.let { d.treeById(it) }?.takeIf { it.active }
    val unplaced = d.activeTrees.filter { it.pos == null }
    val treeCount = d.activeTrees.size
    val canAddMoreTrees = c.isFeatureEnabled(VFeature.UNLIMITED_TREES) || treeCount < VFeature.FREE_MAX_TREES
    fun tryAddTree() {
        if (canAddMoreTrees) showAdd = true
        else c.requestFeature(VFeature.UNLIMITED_TREES)
    }

    if (c.simpleMode) {
        // Chế độ Nông dân: 2 việc chính bấm to trải rộng, dưới là hàng lựa chọn bản đồ/khu cuộn ngang
        // (≥48dp mỗi ô, nhãn luôn hiển thị đủ — không cắt mép, không emoji theo DESIGN.md §4.3).
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VButton(
                if (canAddMoreTrees) "Thêm cây" else "Thêm cây ($treeCount/${VFeature.FREE_MAX_TREES} · PRO)",
                { tryAddTree() },
                icon = Icons.Filled.Add,
                modifier = Modifier.weight(1f),
            )
            VButton("Khoanh vườn", { showBoundary = true }, primary = false, icon = Icons.Filled.Map, modifier = Modifier.weight(1f))
        }
        val baseSel = if (showGoogleMap) "Vệ tinh Google" else "Bản đồ vườn"
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            @Composable
            fun FarmerChip(label: String, sel: Boolean, onClick: () -> Unit) {
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(LeafShapeSmall)
                        .background(if (sel) VC.pine else VC.card)
                        .border(1.dp, if (sel) VC.pine else VC.border, LeafShapeSmall)
                        .bounceClick(onClick = onClick)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    Text(label, fontSize = 15.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium, color = if (sel) Color.White else VC.text, maxLines = 1)
                }
            }
            FarmerChip("Bản đồ vườn", baseSel == "Bản đồ vườn") { showGoogleMap = false }
            FarmerChip("Vệ tinh Google", baseSel == "Vệ tinh Google") { showGoogleMap = true; mode = MapMode.GEO }
            (listOf("Tất cả khu") + d.zoneNames()).forEach { z ->
                FarmerChip(z, if (z == "Tất cả khu") zone.isEmpty() else zone == z) { zone = if (z == "Tất cả khu") "" else z }
            }
        }
    } else {
        PageHeader(Icons.Filled.Map, "BẢN ĐỒ SỐ", "Mỗi cây, một hồ sơ rõ ràng.", "Chọn một cây để xem tình trạng, vị trí và lịch sử xử lý.") {
            VButton(if (canAddMoreTrees) "Thêm cây" else "Thêm cây (PRO)", { tryAddTree() }, icon = Icons.Filled.Add)
            VButton("Vẽ ranh vườn", { showBoundary = true }, primary = false, icon = Icons.Filled.Map)
            VButton(
                if (c.isFeatureEnabled(VFeature.BULK_IMPORT_EXPORT)) "Nhập dữ liệu bản đồ" else "Nhập dữ liệu bản đồ (PRO)",
                {
                    if (c.isFeatureEnabled(VFeature.BULK_IMPORT_EXPORT)) showImport = true
                    else c.requestFeature(VFeature.BULK_IMPORT_EXPORT)
                },
                primary = false,
            )
            VButton(
                if (c.isFeatureEnabled(VFeature.BULK_IMPORT_EXPORT)) "Xuất GeoJSON" else "Xuất GeoJSON (PRO)",
                {
                    if (c.isFeatureEnabled(VFeature.BULK_IMPORT_EXPORT)) export.launch("vuon-vwing.geojson")
                    else c.requestFeature(VFeature.BULK_IMPORT_EXPORT)
                },
                primary = false,
                enabled = hasGeo,
            )
            VButton(
                if (c.isFeatureEnabled(VFeature.BULK_IMPORT_EXPORT)) "Xuất KML/GPX/CSV…" else "Xuất KML/GPX/CSV… (PRO)",
                {
                    if (c.isFeatureEnabled(VFeature.BULK_IMPORT_EXPORT)) showExport = true
                    else c.requestFeature(VFeature.BULK_IMPORT_EXPORT)
                },
                primary = false,
                enabled = hasGeo,
            )
        }

        FlowRow(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipRow(MapMode.values().toList(), mode, { it.label }, { mode = it })
            val zones = listOf("Tất cả khu") + d.zoneNames()
            ChipRow(zones, if (zone.isEmpty()) "Tất cả khu" else zone, { it.substringBefore(" · ") }, { zone = if (it == "Tất cả khu") "" else it })
        }
        Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Lớp:", color = VC.text2, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MapLayer.values().forEach { l ->
                    val on = l in layers
                    Box(
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (on) VC.amber else VC.elevated)
                            .clickable { if (on) layers.remove(l) else layers.add(l) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        Text(
                            (if (on) "✓ " else "") + l.label,
                            fontSize = 14.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                            color = if (on) VC.onLime else VC.text,
                        )
                    }
                }
            }
        }
        val baseOptions = d.imagery.filter { it.bounds != null && (it.kind == com.example.test_tablet.data.vwing.ImageKind.ORTHO || it.kind == com.example.test_tablet.data.vwing.ImageKind.FIELD) }.sortedByDescending { it.capturedAt }
        val ndviOptions = d.indices.filter { it.bounds != null && it.gridFile != null }.sortedByDescending { it.date }
        if (mode == MapMode.GEO && (baseOptions.isNotEmpty() || ndviOptions.isNotEmpty())) {
            val keys = listOf("") + baseOptions.map { "img:" + it.id } + ndviOptions.map { "ndvi:" + it.id }
            fun labelOf(k: String) = when {
                k.isEmpty() -> "Không"
                k.startsWith("img:") -> baseOptions.first { it.id == k.removePrefix("img:") }.let { "Ảnh " + Dates.fmt(it.capturedAt) }
                else -> ndviOptions.first { it.id == k.removePrefix("ndvi:") }.let { "NDVI " + Dates.fmt(it.date) }
            }
            Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Ảnh nền:", color = VC.text2, fontSize = 14.sp)
                ChipRow(keys, overlayKey, ::labelOf, { overlayKey = it })
            }
        }
        if (mode == MapMode.SCHEMATIC) Text("Sơ đồ không phản ánh vị trí thực: cây được xếp theo khu. Dùng chế độ địa lý khi cây có tọa độ.", color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
        if (mode == MapMode.GEO && (region != null || analysisOverlay != null)) {
            FlowRow(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipRow(listOf(false, true), showGoogleMap, { if (it) "Google Map vệ tinh" else "Bản đồ VWing" }, { showGoogleMap = it })
                if (regions.size > 1) {
                    ChipRow(regions, region ?: regions.first(), { it.name }, { regionId = it.id })
                }
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(VC.mint)
                        .clickable { showBoundary = true }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    Text(
                        "✏️ Đánh dấu vùng trên Google Map",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = VC.onLime,
                    )
                }
            }
        }
    }
    if (placing) Notice("Chạm vào bản đồ để chọn vị trí cho cây ${selected?.code ?: ""}, rồi bấm Lưu vị trí ở bảng bên dưới.", VC.cyan, Modifier.padding(bottom = 8.dp))


    val mapBlock: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            if (mode == MapMode.GEO && showGoogleMap) {
                GoogleMapOverlayView(
                    region = region,
                    overlay = analysisOverlay,
                    modifier = Modifier.fillMaxWidth().height(if (c.wide) 500.dp else 430.dp),
                    regionHud = if (c.simpleMode) null else regionHud,
                    hudCfg = satCfg,
                    hudStart = c.mapAnalysisSession.start.value,
                    hudEnd = c.mapAnalysisSession.end.value,
                    onHudSelectOverlay = { analysisOverlay = it },
                    onHudRefresh = {
                        regionHud?.refreshAll(
                            satCfg,
                            c.mapAnalysisSession.start.value,
                            c.mapAnalysisSession.end.value,
                            scope,
                            onAutoOverlay = { analysisOverlay = it },
                        )
                    },
                )
            } else {
                GardenMap(
                    d, mode, layers.toSet(), selected?.id, Modifier.fillMaxWidth().height(if (c.wide) 500.dp else 430.dp),
                    zoneFilter = zone.ifEmpty { null }, pickMarker = pendingPos,
                    onSelect = { c.selectTree(it); pendingPos = null },
                    onPick = if (placing && selected != null) { p -> pendingPos = p } else null,
                    overlays = overlays + listOfNotNull(analysisOverlay),
                    analysisRegion = region?.points ?: emptyList(), cloud = analysisCloud,
                    regionHud = if (c.simpleMode) null else regionHud,
                    hudCfg = satCfg,
                    hudStart = c.mapAnalysisSession.start.value,
                    hudEnd = c.mapAnalysisSession.end.value,
                    onHudSelectOverlay = { analysisOverlay = it },
                    onHudRefresh = {
                        regionHud?.refreshAll(
                            satCfg,
                            c.mapAnalysisSession.start.value,
                            c.mapAnalysisSession.end.value,
                            scope,
                            onAutoOverlay = { analysisOverlay = it },
                        )
                    },
                )
            }
            if (!(mode == MapMode.GEO && showGoogleMap)) MapLegend()
            if (!c.simpleMode && mode == MapMode.GEO && (region != null || regionHud != null)) {
                Spacer(Modifier.height(10.dp))
                val toolTabs = listOf(
                    "SPACING_QR" to "📐 Đếm Cây (8×8m / 3×3m) & QR",
                    "3D" to "🧊 Mô hình 3D",
                    "DEEPLAB" to "🧠 DeepLabV3+ (Tách tán 3D)",
                    "NWS_FFC" to "📡 Radar NWS FFC (KFFC)",
                    "TOF" to "🌳 Phân loại Cây (TOF)",
                    "DEEPFOREST" to "🔍 Đếm tán (RGB)",
                    "AI_SAT" to "📊 Chỉ số Vệ tinh",
                    "NONE" to "✕ Ẩn bảng dưới",
                )
                ChipRow(
                    toolTabs.map { it.first },
                    activeMapTool,
                    { key -> toolTabs.first { it.first == key }.second },
                    { activeMapTool = it },
                )
                Spacer(Modifier.height(8.dp))
                when (activeMapTool) {
                    "SPACING_QR" -> OrchardSpacingQrHubCard(
                        region = region,
                        hud = regionHud,
                        onSyncTreesToStore = { pts, variety, rName ->
                            val (added, updated) = c.store.syncGeeTrees(pts, variety, rName)
                            c.toast("Đã gán nhãn & lưu ${pts.size} cây + Mã QR vào Hồ sơ vườn (thêm mới $added, cập nhật $updated).")
                        },
                        onToast = { c.toast(it) },
                    )
                    "DEEPLAB" -> DeepLabV3PlusHubCard(
                        region = region,
                        onSelectOverlay = { analysisOverlay = it },
                        onJumpToCalibrated3D = { activeMapTool = "3D" },
                        onToast = { c.toast(it) },
                    )
                    "NWS_FFC" -> NwsFfcMapHubCard(
                        region = region,
                        cfg = satCfg,
                        onSelectOverlay = { analysisOverlay = it },
                        onToast = { c.toast(it) },
                    )
                    "3D" -> if (region != null) {
                        LayeredTerrain3D(
                            region = region,
                            hud = regionHud,
                            cfg = satCfg,
                            onOpenDiseaseScreen = { c.nav.push("disease") },
                        )
                    }
                    "TOF" -> if (regionHud != null) {
                        TofMapperHubCard(
                            hud = regionHud,
                            cfg = satCfg,
                            onSelectOverlay = { analysisOverlay = it },
                            onToast = { c.toast(it) },
                        )
                    }
                    "DEEPFOREST" -> if (regionHud != null) {
                        DeepForestHubCard(
                            hud = regionHud,
                            cfg = satCfg,
                            onSelectOverlay = { analysisOverlay = it },
                            onToast = { c.toast(it) },
                        )
                    }
                    "AI_SAT" -> if (regionHud != null) {
                        AgResearch9PapersCard(
                            hud = regionHud,
                            cfg = satCfg,
                            onSelectOverlay = { analysisOverlay = it },
                            onToast = { c.toast(it) },
                        )
                    }
                }
            }
            if (mode == MapMode.GEO && regionHud != null && !c.simpleMode) {
                TechnicalDetails(c.simpleMode, Modifier.padding(top = 8.dp)) {
                    if (regionHud.allGeeTreesInPolygon().isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        GeeTreeStatusTableCard(
                            hud = regionHud,
                            onSyncTreesToStore = { pts, variety ->
                                val (added, updated) = c.store.syncGeeTrees(pts, variety, region?.name ?: "Vùng GEE")
                                c.toast("Đã gắn nhãn & đồng bộ ${pts.size} cây GEE vào Hồ sơ vườn (thêm mới $added, cập nhật $updated).")
                            },
                        )
                    }
                    if (regionHud.demHubResult != null) {
                        Spacer(Modifier.height(10.dp))
                        DemTerrainHubCard(
                            hud = regionHud,
                            cfg = satCfg,
                            onToast = { c.toast(it) },
                        )
                    }
                    if (regionHud.curriculumResult != null) {
                        Spacer(Modifier.height(10.dp))
                        GisCurriculumHubCard(
                            hud = regionHud,
                            cfg = satCfg,
                            onToast = { c.toast(it) },
                        )
                    }
                    if (regionHud.droughtResult != null) {
                        Spacer(Modifier.height(10.dp))
                        DroughtMonitoringHubCard(
                            hud = regionHud,
                            cfg = satCfg,
                            onApplyOverlay = { analysisOverlay = it },
                            onToast = { c.toast(it) },
                            simpleMode = c.simpleMode,
                        )
                    }
                    if (regionHud.droneMsResult != null) {
                        Spacer(Modifier.height(10.dp))
                        DroneMultispectralHubCard(
                            hud = regionHud,
                            cfg = satCfg,
                            onApplyOverlay = { analysisOverlay = it },
                            onToast = { c.toast(it) },
                            simpleMode = c.simpleMode,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    RegionIconLegendCard(regionHud)
                }
            }
            if (!c.simpleMode && unplaced.isNotEmpty() && mode == MapMode.GEO) {
                SectionLabel("Chưa có vị trí (${unplaced.size})")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    unplaced.forEach { t ->
                        Text(t.code, color = VC.text, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(VC.elevated)
                            .clickable { c.selectTree(t.id) }.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
            if (!c.simpleMode && d.features.isNotEmpty() && mode == MapMode.GEO) {
                SectionLabel("Lớp bản đồ")
                d.features.groupBy { it.kind }.forEach { (k, fs) ->
                    Text("${k.label}: " + fs.joinToString(", ") { it.name }, color = VC.text2, fontSize = 13.sp)
                }
                d.features.firstOrNull { it.kind == FeatureKind.BOUNDARY }?.let {
                    Text("Ranh vườn: %.2f ha tính từ tọa độ · nguồn: %s".format(Geo.polygonAreaHa(it.points), it.source.ifBlank { "không rõ" }), color = VC.muted, fontSize = 13.sp)
                }
            }
        }
    }

    val panel: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            if (selected == null) {
                VCard(Modifier.fillMaxWidth()) {
                    Eyebrow("HỒ SƠ CÂY")
                    Text("Chạm vào một cây trên bản đồ để xem tình trạng và ghi chú.", color = VC.text2, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
                }
            } else TreePanel(c, selected, placing, pendingPos,
                onTogglePlacing = { placing = !placing; pendingPos = null },
                onSaved = { placing = false; pendingPos = null })
            if (!c.simpleMode) {
                Spacer(Modifier.height(12.dp))
                RegionAnalysisPanel(c, regions, region, { regionId = it },
                    onOverlay = { analysisOverlay = it; if (it != null) mode = MapMode.GEO },
                    onCloud = { analysisCloud = it; if (it != null) mode = MapMode.GEO },
                    regionHud = regionHud)
            }
        }
    }
    if (c.wide) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { mapBlock(Modifier.weight(1.5f)); panel(Modifier.weight(1f)) }
    else Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { mapBlock(Modifier); panel(Modifier) }

    if (showAdd) AddTreeDialog(c, onDismiss = { showAdd = false })
    if (showImport) GeoImportDialog(c, onDismiss = { showImport = false })
    if (showExport) GisExportDialog(c) { showExport = false }
    if (showBoundary) BoundaryPickerDialog(c, onDismiss = { showBoundary = false })
}


@Composable
private fun TreePanel(c: VCtx, t: com.example.test_tablet.data.vwing.Tree, placing: Boolean, pending: GeoPoint?, onTogglePlacing: () -> Unit, onSaved: () -> Unit) {
    val d = c.d
    val cond = d.effectiveCondition(t)
    var lat by remember(t.id, t.version) { mutableStateOf(t.pos?.lat?.toString() ?: "") }
    var lng by remember(t.id, t.version) { mutableStateOf(t.pos?.lng?.toString() ?: "") }
    var src by remember(t.id, t.version) { mutableStateOf(if (t.posSource == PosSource.VERIFIED) PosSource.VERIFIED else PosSource.ESTIMATED) }
    if (pending != null) { lat = "%.6f".format(pending.lat); lng = "%.6f".format(pending.lng) }
    var err by remember { mutableStateOf<String?>(null) }
    var gpsAcc by remember(t.id, t.version) { mutableStateOf<Float?>(null) }
    var fromGps by remember(t.id, t.version) { mutableStateOf(false) }
    val locate = rememberLocationFetcher(c) { la, lo, acc ->
        lat = "%.6f".format(la); lng = "%.6f".format(lo); gpsAcc = acc; fromGps = true; src = PosSource.ESTIMATED; err = null
        c.toast("Đã lấy vị trí" + (acc?.let { " (thiết bị báo sai số ±${it.toInt()} m)" } ?: "") + ". Bấm Lưu vị trí để ghi.")
    }

    VCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Eyebrow("HỒ SƠ CÂY"); ConditionTag(cond)
        }
        Text("Cây ${t.code}", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = VC.text, modifier = Modifier.padding(top = 4.dp))
        Text("${t.zone}${d.zoneDesc(t.zone).let { if (it.isNotBlank()) " · $it" else "" }}", color = VC.text2, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniFact("Trái đã ghi nhận", t.fruits?.let { "$it trái" } ?: "Chưa đếm", Modifier.weight(1f))
            MiniFact("Kiểm tra", when (val s = d.checkStatus(t)) {
                CheckStatus.Never -> "Chưa từng"; is CheckStatus.Overdue -> "Quá hạn ${s.days} ngày"; is CheckStatus.Ok -> "${s.days} ngày trước"
            }, Modifier.weight(1f))
        }
        val gaps = d.dataGaps(t)
        if (gaps.isNotEmpty()) Text("Dữ liệu còn thiếu: " + gaps.joinToString(" · "), color = VC.amber, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))

        SectionLabel("Vị trí · ${t.posSource.label}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VField(lat, { lat = it; err = null }, "Vĩ độ", Modifier.weight(1f), number = true)
            VField(lng, { lng = it; err = null }, "Kinh độ", Modifier.weight(1f), number = true)
        }
        VPicker("Nguồn vị trí", listOf(PosSource.ESTIMATED.label, PosSource.VERIFIED.label), src.label, { src = if (it == PosSource.VERIFIED.label) PosSource.VERIFIED else PosSource.ESTIMATED },
            Modifier.padding(top = 8.dp))
        err?.let { Text(it, color = VC.red, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp)) }
        Text("QR/mã cây là cách nhận diện chính; GPS điện thoại có thể lệch vài mét nên được ghi là vị trí ước lượng cho tới khi bạn xác minh tại cây.", color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VButton(if (placing) "Hủy chọn" else "Chạm bản đồ", onTogglePlacing, primary = false)
            VButton("Lấy GPS", locate, primary = false)
            VButton("Lưu vị trí", {
                val la = parseNum(lat); val lo = parseNum(lng)
                if (la == null || lo == null || kotlin.math.abs(la) > 90 || kotlin.math.abs(lo) > 180) err = "Tọa độ không hợp lệ (vĩ độ −90..90, kinh độ −180..180)."
                else {
                    val r = c.store.editTree(t.id, t.version, "Đặt vị trí (${src.label})" + (if (fromGps) " · GPS thiết bị" + (gpsAcc?.let { " ±${it.toInt()} m" } ?: "") else "")) { it.copy(pos = GeoPoint(la, lo), posSource = src) }
                    if (r.ok) { c.toast("Đã lưu vị trí. ID và lịch sử cây không đổi."); onSaved() } else err = r.message
                }
            })
        }
        var showTreeQr by remember { mutableStateOf(false) }
        Spacer(Modifier.height(10.dp))
        Divider2()
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VButton("Mở hồ sơ đầy đủ", { c.nav.push("tree|${t.id}") }, Modifier.weight(1f), primary = false)
            VButton("📱 Mã QR", { showTreeQr = true }, Modifier.weight(0.8f), primary = false)
            VButton("Ghi chăm sóc", { c.nav.push("log|${t.id}") }, Modifier.weight(1f))
        }
        if (showTreeQr) {
            QrDialog(c, t, onDismiss = { showTreeQr = false })
        }
    }
}

@Composable
fun MiniFact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(VC.elevated).padding(10.dp)) {
        Text(label, fontSize = 13.sp, color = VC.muted)
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = VC.text, modifier = Modifier.padding(top = 3.dp))
    }
}

// ---------- Thêm cây ----------
@Composable
fun AddTreeDialog(c: VCtx, onDismiss: () -> Unit) {
    val d = c.d
    var code by remember { mutableStateOf("") }
    val zones = d.zoneNames()
    var zone by remember { mutableStateOf(zones.firstOrNull() ?: "") }
    var newZone by remember { mutableStateOf("") }
    var variety by remember { mutableStateOf(d.garden.crop) }
    var year by remember { mutableStateOf("") }
    var fruits by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = VC.card,
        title = { Text("Thêm cây", color = VC.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VField(code, { code = it }, "Mã cây *", placeholder = "Ví dụ: A-07")
                if (zones.isNotEmpty()) VPicker("Khu vực", zones, zone, { zone = it; newZone = "" })
                VField(newZone, { newZone = it }, if (zones.isEmpty()) "Khu vực *" else "Hoặc tạo khu mới")
                VField(variety, { variety = it }, "Giống (nếu biết)")
                VField(year, { year = it.filter(Char::isDigit).take(4) }, "Năm trồng (nếu biết)")
                VField(fruits, { fruits = it.filter(Char::isDigit) }, "Số trái đã đếm (để trống nếu chưa đếm)")
                Text("Vị trí có thể bổ sung sau. Cây mới không kế thừa lịch sử của cây khác.", color = VC.muted, fontSize = 13.sp)
                err?.let { Text(it, color = VC.red, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            VButton("Thêm cây", {
                val z = newZone.trim().ifEmpty { zone }
                val y = year.toIntOrNull()
                if (year.isNotEmpty() && (y == null || y < 1900 || y > 2100)) err = "Năm trồng không hợp lệ."
                else {
                    val r = c.store.addTree(code, z, variety, y, fruits.toIntOrNull())
                    if (r.ok) { c.toast("Đã thêm cây ${code.trim()}."); onDismiss() } else err = r.message
                }
            })
        },
        dismissButton = { TextButton(onDismiss) { Text("Đóng", color = VC.text2) } },
    )
}

// ---------- Nhập KML / GeoJSON ----------
@Composable
fun GeoImportDialog(c: VCtx, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var text by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    val items = remember { mutableStateListOf<ImportItem>() }
    var parseErrors by remember { mutableStateOf<List<String>>(emptyList()) }
    var parseWarn by remember { mutableStateOf<List<String>>(emptyList()) }
    var kindTick by remember { mutableStateOf(0) }

    fun load(t: String) {
        text = t
        val r = Geo.parse(t)
        items.clear()
        var firstPoly = true
        r.features.forEach { f ->
            val k = Geo.guessKind(f, firstPoly)
            if (f.geom == Geom.POLYGON) firstPoly = false
            items.add(ImportItem(f, k))
        }
        parseErrors = r.errors; parseWarn = r.warnings
    }

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = displayNameOf(context, uri)
            fileName = name
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes == null) parseErrors = listOf("Không đọc được tệp.")
            else if (com.example.test_tablet.data.vwing.GisClient.extOf(name) == "kmz") {
                val r = Geo.parseKmz(bytes)
                items.clear()
                var firstPoly = true
                r.features.forEach { f ->
                    val k = Geo.guessKind(f, firstPoly)
                    if (f.geom == Geom.POLYGON) firstPoly = false
                    items.add(ImportItem(f, k))
                }
                parseErrors = r.errors; parseWarn = r.warnings
            } else if (com.example.test_tablet.data.vwing.GisClient.extOf(name) in setOf("kml", "geojson", "json", "")) {
                // KML/GeoJSON đọc ngay trên máy, không cần mạng
                load(bytes.toString(Charsets.UTF_8))
            } else {
                // KMZ, GPX, Shapefile (zip), GeoPackage, CSV: nhờ máy chủ (engine RustGIS) đổi sang GeoJSON rồi dùng chung bước xem trước/kiểm tra
                busy = true; parseErrors = emptyList(); parseWarn = emptyList(); items.clear()
                val cfg = com.example.test_tablet.data.vwing.SatelliteSettings(context.filesDir).load()
                scope.launch {
                    val r = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.example.test_tablet.data.vwing.GisClient.importFile(cfg, name, bytes) } }
                    busy = false
                    r.onSuccess { imp ->
                        if (imp.layers.isEmpty()) parseErrors = imp.warnings.ifEmpty { listOf("Tệp không có đối tượng bản đồ.") }
                        else { load(com.example.test_tablet.data.vwing.GisClient.mergeLayers(imp.layers)); parseWarn = parseWarn + imp.warnings }
                    }.onFailure { parseErrors = listOf(it.message ?: "Không nhập được tệp.") }
                }
            }
        }
    }

    val issues = remember(kindTick, items.toList(), c.d) { if (items.isEmpty()) emptyList() else Geo.validate(items, c.d) }
    val blocked = parseErrors.isNotEmpty() && items.isEmpty() || issues.any { it.error }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = VC.card,
        title = { Text("Nhập dữ liệu bản đồ", color = VC.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tọa độ theo thứ tự kinh độ, vĩ độ (WGS84). Xem trước và kiểm tra trước khi chấp nhận; không có gì được ghi cho đến khi bạn bấm Nhập.", color = VC.text2, fontSize = 14.sp)
                VButton(if (busy) "Đang đọc tệp…" else if (fileName.isEmpty()) "Chọn tệp bản đồ" else "Đổi tệp ($fileName)", { picker.launch(arrayOf("*/*")) }, primary = false, enabled = !busy)
                Text("Đọc trực tiếp trên máy (Offline): KML, KMZ, GeoJSON và Bảng tọa độ góc ranh Sổ đỏ VN-2000 (X, Y). Qua máy chủ RustGIS: GPX, Shapefile (.zip), GeoPackage, CSV.", color = VC.muted, fontSize = 13.sp)
                val vnProvinceOptions = remember {
                    com.example.test_tablet.data.vwing.Vn2000Converter.AFTER_MERGER_34.map { "${it.name} · L0=${it.defaultL0}° (${it.mergedDetail})" } +
                        com.example.test_tablet.data.vwing.Vn2000Converter.CENTRAL_MERIDIANS_63.map { (k, v) -> "$k (63 tỉnh) · L0=$v°" }
                }
                var selectedVnLabel by remember { mutableStateOf(vnProvinceOptions.first()) }
                VPicker("Hệ quy chiếu Sổ đỏ VN-2000 (34 Tỉnh mới sau sáp nhập & 63 Tỉnh gốc)", vnProvinceOptions, selectedVnLabel, { picked ->
                    selectedVnLabel = picked
                    val l0 = Regex("L0=([0-9.]+)").find(picked)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 107.75
                    com.example.test_tablet.data.vwing.Vn2000Converter.activeProvince = picked.substringBefore(" ·")
                    com.example.test_tablet.data.vwing.Vn2000Converter.activeLon0 = l0
                    if (text.isNotBlank()) load(text)
                })
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.example.test_tablet.data.vwing.Vn2000Converter.PRESETS_VN2000.forEach { pr ->
                        VButton("📐 ${pr.province}", {
                            com.example.test_tablet.data.vwing.Vn2000Converter.activeProvince = pr.province
                            com.example.test_tablet.data.vwing.Vn2000Converter.activeLon0 = pr.lon0
                            load(pr.coordsText)
                        }, primary = false)
                    }
                }
                VField(text, { load(it) }, "Hoặc dán KML / GeoJSON / Bảng tọa độ góc ranh Sổ đỏ VN-2000 (X Bắc, Y Đông)", singleLine = false, minLines = 4)
                parseErrors.forEach { Text("✕ $it", color = VC.red, fontSize = 14.sp) }
                parseWarn.forEach { Text("! $it", color = VC.amber, fontSize = 14.sp) }
                if (items.isNotEmpty()) {
                    SectionLabel("Đối tượng tìm thấy (${items.size})")
                    items.forEach { it0 ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(VC.elevated).padding(10.dp)) {
                            Text("${it0.feature.name} · ${when (it0.feature.geom) { Geom.POINT -> "điểm"; Geom.LINE -> "đường"; Geom.POLYGON -> "vùng" }} · ${it0.feature.points.size} điểm", color = VC.text, fontSize = 14.sp)
                            VPicker("Nhập như", ImportKind.values().map { it.label }, it0.kind.label, { l -> it0.kind = ImportKind.values().first { it.label == l }; kindTick++ })
                        }
                    }
                    SectionLabel("Kiểm tra")
                    if (issues.isEmpty()) Text("✓ Không phát hiện vấn đề.", color = VC.mint, fontSize = 14.sp)
                    issues.forEach { Text((if (it.error) "✕ " else "! ") + it.text, color = if (it.error) VC.red else VC.amber, fontSize = 14.sp) }
                }
            }
        },
        confirmButton = {
            VButton("Nhập", {
                val used = items.filter { it.kind != ImportKind.SKIP }
                val feats = used.filter { it.kind != ImportKind.TREE }.map { u ->
                    GeoFeature(newId(), u.kind.toFeatureKind()!!, u.feature.name, u.feature.points, source = fileName.ifEmpty { "Dán nội dung" })
                }
                val trees = used.filter { it.kind == ImportKind.TREE }.map { VWingStore.TreePosUpdate(it.feature.name, null, it.feature.points[0]) }
                c.store.applyGeoImport(feats, trees, fileName.ifEmpty { "KML/GeoJSON" })
                c.toast("Đã nhập ${feats.size} lớp và ${trees.size} vị trí cây (vị trí ghi là \"ước lượng\").")
                onDismiss()
            }, enabled = items.isNotEmpty() && !blocked)
        },
        dismissButton = { TextButton(onDismiss) { Text("Đóng", color = VC.text2) } },
    )
}



/** Tải ảnh nền/lưới NDVI đã chọn thành lớp phủ bản đồ (nhãn ghi rõ ngày và độ phân giải). */
@Composable
private fun rememberMapOverlays(c: VCtx, key: String): List<MapOverlay> {
    val d = c.d
    val set = if (key.startsWith("img:")) d.imagery.firstOrNull { it.id == key.removePrefix("img:") } else null
    val idx = if (key.startsWith("ndvi:")) d.indices.firstOrNull { it.id == key.removePrefix("ndvi:") } else null
    val photo = set?.let { rememberBitmap(java.io.File(c.store.photoDir, it.file).absolutePath, 2048) }
    val heat by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, idx?.gridFile) {
        value = idx?.gridFile?.let { f ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.example.test_tablet.data.vwing.NdviIO.loadGrid(java.io.File(c.store.photoDir, f))?.let { com.example.test_tablet.data.vwing.NdviIO.heatBitmap(it).asImageBitmap() }
            }
        }
    }
    return remember(set, idx, photo, heat) {
        when {
            set != null && photo != null && set.bounds != null ->
                listOf(MapOverlay(set.bounds, photo, .85f, "Ảnh ${Dates.fmt(set.capturedAt)}" + (com.example.test_tablet.data.vwing.Imagery.pixelSizeM(set)?.let { " · ${"%.0f".format(it * 100)} cm/px" } ?: "")))
            idx != null && heat != null && idx.bounds != null ->
                listOf(MapOverlay(idx.bounds, heat!!, .7f, "NDVI ${Dates.fmt(idx.date)}" + (idx.resolutionM?.let { " · ${"%.2f".format(it)} m/px" } ?: "") + " · vùng không tô màu = thiếu dữ liệu"))
            else -> emptyList()
        }
    }
}
