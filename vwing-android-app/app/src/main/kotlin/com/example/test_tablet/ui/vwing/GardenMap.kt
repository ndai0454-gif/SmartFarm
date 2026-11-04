package com.example.test_tablet.ui.vwing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.test_tablet.data.vwing.GeoBounds
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.CheckStatus
import com.example.test_tablet.data.vwing.Condition
import com.example.test_tablet.data.vwing.Dates
import com.example.test_tablet.data.vwing.FeatureKind
import com.example.test_tablet.data.vwing.GardenData
import com.example.test_tablet.data.vwing.Geo
import com.example.test_tablet.data.vwing.GeoPoint
import com.example.test_tablet.data.vwing.activeTrees
import com.example.test_tablet.data.vwing.checkStatus
import com.example.test_tablet.data.vwing.effectiveCondition
import com.example.test_tablet.data.vwing.isOverdue
import com.example.test_tablet.data.vwing.lastObservedAt
import com.example.test_tablet.data.vwing.openIssuesFor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class MapMode(val label: String) { GEO("Bản đồ địa lý"), SCHEMATIC("Sơ đồ") }

enum class MapLayer(val label: String) {
    NEEDS("Cây chờ kiểm tra"), TASKS("Việc quá hạn"), AGE("Tuổi bản ghi")
}

/** Lớp ảnh phủ lên bản đồ địa lý (ảnh nền theo ngày hoặc lưới NDVI) với nhãn ghi rõ ngày/độ phân giải. */
class MapOverlay(val bounds: GeoBounds, val bitmap: ImageBitmap, val alpha: Float, val label: String)

private const val EARTH_R = 6_371_000.0

fun fromMeters(x: Double, y: Double, o: GeoPoint): GeoPoint =
    GeoPoint(o.lat + Math.toDegrees(y / EARTH_R), o.lng + Math.toDegrees(x / (EARTH_R * cos(Math.toRadians(o.lat)))))

private class Placed(val treeId: String, val x: Double, val y: Double)

private class World(
    val trees: List<Placed>,
    val origin: GeoPoint?,
    val minX: Double, val maxX: Double, val minY: Double, val maxY: Double,
    val zoneBoxes: List<Triple<String, Pair<Double, Double>, Pair<Double, Double>>>, // chỉ ở chế độ sơ đồ
)

private fun buildWorld(d: GardenData, mode: MapMode, overlays: List<MapOverlay> = emptyList(), analysisRegion: List<GeoPoint> = emptyList()): World? {
    val trees = d.activeTrees
    if (mode == MapMode.GEO) {
        val geoTrees = trees.filter { it.pos != null }
        val corners = overlays.flatMap { listOf(GeoPoint(it.bounds.south, it.bounds.west), GeoPoint(it.bounds.north, it.bounds.east)) }
        val focus = Geo.open(analysisRegion).takeIf { it.size >= 3 }
        val relevantTrees = if (focus != null) geoTrees.filter { Geo.pointInPolygon(it.pos!!, focus) } else geoTrees
        val all = relevantTrees.map { it.pos!! } + d.features.flatMap { it.points } + corners
        val origin = Geo.centroid(focus ?: all) ?: return null
        val pl = relevantTrees.map { t -> Geo.toMeters(t.pos!!, origin).let { Placed(t.id, it.first, it.second) } }
        val extent = focus ?: (relevantTrees.map { it.pos!! } + d.features.flatMap { it.points } + corners)
        val xs = extent.map { Geo.toMeters(it, origin).first }
        val ys = extent.map { Geo.toMeters(it, origin).second }
        return World(pl, origin, xs.min(), max(xs.max(), xs.min() + 20.0), ys.min(), max(ys.max(), ys.min() + 20.0), emptyList())
    }
    if (trees.isEmpty()) return null
    val zones = trees.map { it.zone }.distinct().sorted()
    val cols = if (zones.size <= 2) zones.size else 2
    val perRow = max(1, cols)
    val pl = mutableListOf<Placed>()
    val boxes = mutableListOf<Triple<String, Pair<Double, Double>, Pair<Double, Double>>>()
    var cellW = 0.0; var cellH = 0.0
    val layouts = zones.map { z ->
        val zt = trees.filter { it.zone == z }
        val c = max(1, kotlin.math.ceil(sqrt(zt.size.toDouble())).toInt())
        val r = max(1, kotlin.math.ceil(zt.size.toDouble() / c).toInt())
        cellW = max(cellW, c * 1.0 + 1.0); cellH = max(cellH, r * 1.0 + 1.2)
        Triple(z, zt, c)
    }
    layouts.forEachIndexed { zi, (z, zt, c) ->
        val col = zi % perRow; val row = zi / perRow
        val ox = col * (cellW + 1.0); val oy = -row * (cellH + 1.0)
        zt.forEachIndexed { i, t -> pl += Placed(t.id, ox + 0.5 + (i % c), oy - 0.8 - (i / c)) }
        boxes += Triple(z, ox to oy, (ox + cellW) to (oy - cellH))
    }
    val xs = boxes.flatMap { listOf(it.second.first, it.third.first) }
    val ys = boxes.flatMap { listOf(it.second.second, it.third.second) }
    return World(pl, null, xs.min(), xs.max(), ys.min(), ys.max(), boxes)
}

/** Dữ liệu hiển thị của một cây trên bản đồ (tính một lần mỗi khi dữ liệu đổi). */
private class TreeVis(val cond: Condition, val needsCheck: Boolean, val taskFlag: Boolean, val ageDays: Int?)

@Composable
fun GardenMap(
    d: GardenData,
    mode: MapMode,
    layers: Set<MapLayer>,
    selectedId: String?,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    zoneFilter: String? = null,
    pickMarker: GeoPoint? = null,
    onSelect: (String) -> Unit = {},
    onPick: ((GeoPoint) -> Unit)? = null,
    overlays: List<MapOverlay> = emptyList(),
    analysisRegion: List<GeoPoint> = emptyList(),
    cloud: com.example.test_tablet.data.vwing.CloudReport? = null,
    regionHud: RegionHudState? = null,
    hudCfg: com.example.test_tablet.data.vwing.SatelliteConfig? = null,
    hudStart: String = "",
    hudEnd: String = "",
    onHudSelectOverlay: (MapOverlay?) -> Unit = {},
    onHudRefresh: () -> Unit = {},
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val world = remember(d.trees, d.features, mode, overlays, analysisRegion) { buildWorld(d, mode, overlays, analysisRegion) }
    androidx.compose.runtime.LaunchedEffect(analysisRegion) { zoom = 1f; pan = Offset.Zero }

    val today = Dates.today()
    val vis = remember(d) {
        val overdueTaskTrees = d.tasks.filter { it.isOverdue(today) }.flatMap { it.treeIds }.toSet() +
            d.issues.filter { it.isOverdue(today) }.flatMap { it.treeIds }.toSet()
        d.activeTrees.associate { t ->
            val c = d.effectiveCondition(t)
            val chk = d.checkStatus(t, today)
            t.id to TreeVis(
                cond = c,
                needsCheck = c == Condition.NEEDS_CHECK || c == Condition.UNASSESSED || chk !is CheckStatus.Ok || d.openIssuesFor(t.id).isNotEmpty(),
                taskFlag = t.id in overdueTaskTrees,
                ageDays = d.lastObservedAt(t.id)?.let { Dates.daysBetween(it, today) },
            )
        }
    }

    val currentGisTheme = regionHud?.gisTheme ?: GisVisualTheme.SPECTRAL_NDVI
    val isStacked3d = mode == MapMode.GEO && (regionHud?.stacked3dMode == true)
    val gisHeatmapBitmap = remember(currentGisTheme, world?.minX, world?.maxX, world?.minY, world?.maxY, regionHud?.gardenAnalysis) {
        if (world == null || mode != MapMode.GEO || world.origin == null) null
        else {
            val padX = ((world.maxX - world.minX) * 0.28).coerceAtLeast(18.0)
            val padY = ((world.maxY - world.minY) * 0.28).coerceAtLeast(18.0)
            val x0 = world.minX - padX
            val x1 = world.maxX + padX
            val y0 = world.minY - padY
            val y1 = world.maxY + padY
            val spanX = (x1 - x0).coerceAtLeast(1.0)
            val spanY = (y1 - y0).coerceAtLeast(1.0)
            val seeds = mutableListOf<Triple<Double, Double, Float>>()
            world.trees.forEach { p ->
                val nx = ((p.x - x0) / spanX).coerceIn(0.0, 1.0)
                val ny = (1.0 - (p.y - y0) / spanY).coerceIn(0.0, 1.0)
                val cond = vis[p.treeId]?.cond ?: Condition.STABLE
                val w = when (cond) {
                    Condition.STABLE -> 0.85f
                    Condition.WATCH -> 0.55f
                    Condition.NEEDS_CHECK -> 0.25f
                    Condition.UNASSESSED -> 0.65f
                }
                seeds += Triple(nx, ny, w)
            }
            regionHud?.gardenAnalysis?.treeEstimation?.samplePoints?.take(60)?.forEach { sp ->
                val m = Geo.toMeters(sp.pos, world.origin)
                val nx = ((m.first - x0) / spanX).coerceIn(0.0, 1.0)
                val ny = (1.0 - (m.second - y0) / spanY).coerceIn(0.0, 1.0)
                val w = when (sp.status) {
                    "healthy" -> 0.88f
                    "moderate" -> 0.58f
                    else -> 0.22f
                }
                seeds += Triple(nx, ny, w)
            }
            generateGisHeatmapBitmap(theme = currentGisTheme, seedPoints = seeds, cols = 150, rows = 110)
        }
    }

    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (mode == MapMode.GEO) Color(0xFF0A140F) else Color(0xFFEEF4EA)),
    ) {
        if (world == null) {
            Text(
                if (mode == MapMode.GEO) "Chưa có cây hoặc đối tượng nào có tọa độ.\nNhập KML/GeoJSON hoặc đặt vị trí cho cây." else "Chưa có cây nào.",
                color = VC.muted, fontSize = 15.sp, modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
            return@Box
        }

        fun baseScale(sz: Size, marginPx: Float): Float {
            val bw = (world.maxX - world.minX).coerceAtLeast(1.0); val bh = (world.maxY - world.minY).coerceAtLeast(1.0)
            return min((sz.width - 2 * marginPx) / bw.toFloat(), (sz.height - 2 * marginPx) / bh.toFloat())
        }
        fun place(x: Double, y: Double, sz: Size, marginPx: Float): Offset {
            val s = baseScale(sz, marginPx) * zoom
            val cx = (world.minX + world.maxX) / 2; val cy = (world.minY + world.maxY) / 2
            return Offset(sz.width / 2 + ((x - cx) * s).toFloat() + pan.x, sz.height / 2 - ((y - cy) * s).toFloat() + pan.y)
        }

        val worldState = rememberUpdatedState(world)
        val sizeState = remember { mutableStateOf(Size.Zero) }
        val visibleTrees = world.trees.filter { p -> zoneFilter == null || d.trees.firstOrNull { it.id == p.treeId }?.zone == zoneFilter }
        val margin = with(density) { 28.dp.toPx() }
        val hitR = with(density) { 26.dp.toPx() }

        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(interactive) {
                    if (!interactive) return@pointerInput
                    detectTransformGestures { centroid, p, z, _ ->
                        val nz = (zoom * z).coerceIn(0.6f, 12f)
                        // phóng to quanh điểm chạm
                        val sz = sizeState.value
                        val c = Offset(sz.width / 2, sz.height / 2)
                        pan = (pan - (centroid - c)) * (nz / zoom) + (centroid - c) + p
                        zoom = nz
                    }
                }
                .pointerInput(interactive, mode, zoneFilter, world, regionHud) {
                    detectTapGestures { tap ->
                        val sz = sizeState.value
                        val w = worldState.value
                        val geeList = if (mode == MapMode.GEO && w.origin != null) regionHud?.visibleGeeTrees().orEmpty() else emptyList()
                        val nearGee = geeList.minByOrNull { sp ->
                            val m = Geo.toMeters(sp.pos, w.origin!!)
                            (place(m.first, m.second, sz, margin) - tap).getDistance()
                        }
                        val distGee = nearGee?.let {
                            val m = Geo.toMeters(it.pos, w.origin!!)
                            (place(m.first, m.second, sz, margin) - tap).getDistance()
                        } ?: Float.MAX_VALUE

                        val near = visibleTrees.minByOrNull { p ->
                            val o = place(p.x, p.y, sz, margin); (o - tap).getDistance()
                        }
                        val dist = near?.let { (place(it.x, it.y, sz, margin) - tap).getDistance() } ?: Float.MAX_VALUE

                        if (nearGee != null && distGee <= hitR && distGee <= dist) {
                            regionHud?.selectedGeeTreeCode = nearGee.code
                            d.activeTrees.firstOrNull { it.code.equals(nearGee.code, ignoreCase = true) }?.let { onSelect(it.id) }
                        } else if (near != null && dist <= hitR) {
                            onSelect(near.treeId)
                        } else if (onPick != null && mode == MapMode.GEO && w.origin != null) {
                            val s = baseScale(sz, margin) * zoom
                            val cx = (w.minX + w.maxX) / 2; val cy = (w.minY + w.maxY) / 2
                            val x = cx + (tap.x - sz.width / 2 - pan.x) / s
                            val y = cy - (tap.y - sz.height / 2 - pan.y) / s
                            onPick(fromMeters(x, y, w.origin))
                        }
                    }
                },
        ) {
            sizeState.value = size
            val openAnalysis = Geo.open(analysisRegion)
            if (isStacked3d && openAnalysis.size >= 3) {
                drawStacked3dGisView(
                    analysisPoints = openAnalysis,
                    hud = regionHud,
                    measurer = measurer,
                    dens = density.density,
                )
            } else {
                drawMapContent(
                    d, world, mode, layers, selectedId, zoneFilter, pickMarker, vis, measurer, density.density, overlays, analysisRegion, cloud,
                    regionHud = regionHud,
                    gisHeatmapBitmap = gisHeatmapBitmap,
                    gisTheme = currentGisTheme,
                    place = { x, y -> place(x, y, size, margin) }, scalePxPerM = baseScale(size, margin) * zoom,
                )
            }
        }

        if (mode == MapMode.GEO && regionHud != null && hudCfg != null) {
            RegionMapHudOverlay(
                hud = regionHud,
                cfg = hudCfg,
                start = hudStart,
                end = hudEnd,
                onSelectOverlay = onHudSelectOverlay,
                onRefreshAll = onHudRefresh,
            )
        }

        if (interactive) {
            Column(Modifier.align(Alignment.TopEnd).padding(10.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                listOf("+" to { zoom = (zoom * 1.4f).coerceAtMost(12f) }, "−" to { zoom = (zoom / 1.4f).coerceAtLeast(0.6f) }, "⌖" to { zoom = 1f; pan = Offset.Zero }).forEach { (t, f) ->
                    Text(
                        t, color = VC.text, fontSize = 18.sp,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(VC.elevated).clickableNoRipple(f).padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

private fun Modifier.clickableNoRipple(f: () -> Unit) = this.then(Modifier.pointerInput(Unit) { detectTapGestures { f() } })

private fun DrawScope.drawMapContent(
    d: GardenData, world: World, mode: MapMode, layers: Set<MapLayer>, selectedId: String?, zoneFilter: String?,
    pickMarker: GeoPoint?, vis: Map<String, TreeVis>, measurer: androidx.compose.ui.text.TextMeasurer, dens: Float, overlays: List<MapOverlay>,
    analysisRegion: List<GeoPoint>, cloud: com.example.test_tablet.data.vwing.CloudReport?,
    regionHud: RegionHudState? = null,
    gisHeatmapBitmap: ImageBitmap? = null,
    gisTheme: GisVisualTheme = GisVisualTheme.SPECTRAL_NDVI,
    place: (Double, Double) -> Offset, scalePxPerM: Float,
) {
    val placed = mutableListOf<androidx.compose.ui.geometry.Rect>()
    // Nhãn đã đặt trước có ưu tiên; nhãn chồng lên nhãn khác sẽ bị bỏ qua.
    fun label(text: String, at: Offset, color: Color, sizeSp: Float = 10f, center: Boolean = true, darkHalo: Boolean = false) {
        val r = measurer.measure(text, TextStyle(color = color, fontSize = sizeSp.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
        val tl = if (center) Offset(at.x - r.size.width / 2f, at.y) else at
        val box = androidx.compose.ui.geometry.Rect(tl.x - 2f, tl.y, tl.x + r.size.width + 2f, tl.y + r.size.height)
        if (placed.any { it.overlaps(box) }) return
        placed.add(box)
        if (darkHalo) {
            drawRoundRect(
                Color(0xBF08140D),
                topLeft = Offset(tl.x - 3f * dens, tl.y - 1f * dens),
                size = Size(r.size.width + 6f * dens, r.size.height + 2f * dens),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * dens, 3f * dens),
            )
        }
        drawText(r, topLeft = tl)
    }

    if (mode == MapMode.GEO && world.origin != null) {
        fun pt(p: GeoPoint): Offset { val m = Geo.toMeters(p, world.origin); return place(m.first, m.second) }
        val analysisPoints = Geo.open(analysisRegion)

        // 1. Nền ArcGIS/QGIS Pro: Nhiệt phổ liên tục + Hillshade + Đường đồng mức + Thủy văn phân nhánh + Lưới kinh/vĩ độ
        if (gisHeatmapBitmap != null) {
            drawGisProBackgroundAndVectors(
                heatmap = gisHeatmapBitmap,
                analysisPoints = analysisPoints,
                ptTransform = ::pt,
                origin = world.origin,
                minX = world.minX,
                maxX = world.maxX,
                minY = world.minY,
                maxY = world.maxY,
                place = place,
                measurer = measurer,
                dens = dens,
            )
        } else {
            drawRect(Color(0xFF0A140F), Offset.Zero, size)
        }

        val hasMarkedPoly = analysisPoints.size >= 3
        val outline = if (hasMarkedPoly) {
            Path().apply {
                analysisPoints.forEachIndexed { i, p ->
                    val o = pt(p)
                    if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
                }
                close()
            }
        } else null

        val drawInsideMarkedRegion: DrawScope.() -> Unit = {
            // 2. Lớp ảnh vệ tinh thực độ nét cao (Zoom 20/19 ~0,15–0,29 m/px) cắt gọn trong vùng đã đánh dấu để vùng khoanh luôn sắc nét
            val baseSharpBmp = regionHud?.geeClarityTrueColorBitmap ?: regionHud?.gardenBitmap
            val baseBounds = regionHud?.gardenAnalysis?.bounds
            if (baseSharpBmp != null && baseBounds != null && (overlays.isEmpty() || regionHud?.selectedQuickLayer == "Tắt phủ")) {
                val tl = pt(GeoPoint(baseBounds.north, baseBounds.west))
                val br = pt(GeoPoint(baseBounds.south, baseBounds.east))
                drawImage(
                    baseSharpBmp,
                    dstOffset = IntOffset(tl.x.toInt(), tl.y.toInt()),
                    dstSize = IntSize((br.x - tl.x).toInt().coerceAtLeast(1), (br.y - tl.y).toInt().coerceAtLeast(1)),
                    alpha = 0.96f,
                )
            }
            if (regionHud == null || regionHud.selectedQuickLayer != "Tắt phủ") {
                for (ov in overlays) {
                    val tl = pt(GeoPoint(ov.bounds.north, ov.bounds.west)); val br = pt(GeoPoint(ov.bounds.south, ov.bounds.east))
                    drawImage(
                        ov.bitmap, dstOffset = IntOffset(tl.x.toInt(), tl.y.toInt()),
                        dstSize = IntSize((br.x - tl.x).toInt().coerceAtLeast(1), (br.y - tl.y).toInt().coerceAtLeast(1)),
                        alpha = (ov.alpha * 0.85f).coerceIn(0.30f, 0.94f),
                    )
                }
            }

            // 3. Điểm đám mây LiDAR / DSM 3D nếu có
            cloud?.let { report ->
                val span = (report.maxZ - report.minZ).coerceAtLeast(.01)
                report.preview.forEach { p ->
                    val color = androidx.compose.ui.graphics.lerp(Color(0xFF00E5FF), Color(0xFFFFEA00), ((p.z - report.minZ) / span).toFloat().coerceIn(0f, 1f))
                    drawCircle(color.copy(alpha = .85f), 2.8f * dens, pt(p.position))
                }
            }

            // 4. Các đỉnh tán cây AI phát hiện từ vệ tinh Sentinel-2 trong vùng đã đánh dấu
            regionHud?.gardenAnalysis?.treeEstimation?.samplePoints?.take(100)?.forEach { sp ->
                if (!hasMarkedPoly || Geo.pointInPolygon(sp.pos, analysisPoints)) {
                    val o = pt(sp.pos)
                    val col = when (sp.status) {
                        "healthy" -> Color(0xFF69F0AE)
                        "moderate" -> Color(0xFFFFD740)
                        else -> Color(0xFFFF5252)
                    }
                    drawCircle(Color(0xAA08150E), radius = 4.6f * dens, center = o)
                    drawCircle(col.copy(alpha = 0.90f), radius = 3.3f * dens, center = o)
                    if (sp.isPeak) {
                        drawCircle(Color.White.copy(alpha = 0.92f), radius = 1.4f * dens, center = o)
                    }
                }
            }

            // 5. Các đối tượng véc-tơ nằm trong vùng đánh dấu
            for (f in d.features) {
                when (f.kind) {
                    FeatureKind.ZONE -> {
                        val pts = Geo.open(f.points)
                        if (pts.size >= 3 && (!hasMarkedPoly || pts.any { Geo.pointInPolygon(it, analysisPoints) })) {
                            val path = Path().apply { pts.forEachIndexed { i, p -> val o = pt(p); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }; close() }
                            drawPath(path, Color(0x2200E5FF))
                            drawPath(path, Color(0xFF00E5FF), style = Stroke(1.8f * dens))
                            label(f.name, pt(Geo.centroid(pts)!!), Color(0xFFE0F7FA), 10f, darkHalo = true)
                        }
                    }
                    FeatureKind.BOUNDARY -> Unit
                    FeatureKind.ROAD, FeatureKind.WATER -> if (f.points.size >= 2) {
                        val path = Path().apply { f.points.forEachIndexed { i, p -> val o = pt(p); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
                        val col = if (f.kind == FeatureKind.ROAD) Color(0xFFFFB74D) else Color(0xFF00E5FF)
                        drawPath(path, Color(0xAA08140D), style = Stroke(6f * dens, cap = StrokeCap.Round))
                        drawPath(path, col, style = Stroke(3.5f * dens, cap = StrokeCap.Round))
                    }
                    FeatureKind.WATERLOG -> f.points.firstOrNull()?.let { p ->
                        val o = pt(p); val r = 9f * dens
                        drawCircle(Color(0x4400E5FF), r, o); drawCircle(Color(0xFF00E5FF), r, o, style = Stroke(2f * dens))
                    }
                    FeatureKind.DRY -> f.points.firstOrNull()?.let { p ->
                        val o = pt(p); val r = 9f * dens
                        drawCircle(Color(0x44FFAB00), r, o); drawCircle(Color(0xFFFFD740), r, o, style = Stroke(2f * dens, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f * dens, 4f * dens))))
                    }
                    FeatureKind.OBSTACLE -> f.points.firstOrNull()?.let { p ->
                        val o = pt(p); val r = 6f * dens
                        drawRect(Color(0xFFFFAB00), Offset(o.x - r, o.y - r), Size(2 * r, 2 * r), style = Stroke(2f * dens))
                    }
                }
            }
        }

        if (outline != null) {
            clipPath(outline) {
                drawInsideMarkedRegion()
            }
            // Viền đa giác vùng đã đánh dấu trên Google Map phát sáng sắc nét
            drawPath(outline, Color(0x55FFEA00), style = Stroke(6f * dens))
            drawPath(outline, Color(0xFFFFEA00), style = Stroke(2.6f * dens))
        } else {
            drawInsideMarkedRegion()
        }
    } else {
        // Chế độ Sơ đồ (SCHEMATIC) giữ nguyên lưới sáng
        val gridStep = 40f * dens
        var gx = 0f
        while (gx < size.width) { drawLine(Color(0x221B8A78), Offset(gx, 0f), Offset(gx, size.height)); gx += gridStep }
        var gy = 0f
        while (gy < size.height) { drawLine(Color(0x221B8A78), Offset(0f, gy), Offset(size.width, gy)); gy += gridStep }
        for ((z, a, b) in world.zoneBoxes) {
            val p1 = place(a.first, a.second); val p2 = place(b.first, b.second)
            drawRect(VC.teal.copy(alpha = if (zoneFilter == null || zoneFilter == z) .08f else .02f), p1, Size(p2.x - p1.x, p2.y - p1.y))
            drawRect(VC.teal.copy(alpha = .5f), p1, Size(p2.x - p1.x, p2.y - p1.y), style = Stroke(1.2f * dens))
            label(z, Offset((p1.x + p2.x) / 2, p1.y + 4f * dens), VC.mint, 11f)
        }
    }

    val isGeo = mode == MapMode.GEO
    val showLabels = world.trees.size <= 80 || scalePxPerM > 6f
    for (p in world.trees) {
        val t = d.trees.firstOrNull { it.id == p.treeId } ?: continue
        val v = vis[t.id] ?: continue
        val o = place(p.x, p.y)
        val inZone = zoneFilter == null || t.zone == zoneFilter
        var alpha = if (inZone) 1f else .18f
        if (MapLayer.NEEDS in layers && !v.needsCheck) alpha *= .3f
        if (MapLayer.TASKS in layers && !v.taskFlag) alpha *= .3f
        val r = 8f * dens
        if (isGeo) {
            drawCircle(Color(0xCC07120C), r + 2.5f * dens, o)
        }
        if (t.id == selectedId) drawCircle(if (isGeo) Color(0xFFFFEA00) else VC.lime, r + 7f * dens, o, style = Stroke(2.5f * dens))
        if (MapLayer.NEEDS in layers && v.needsCheck) drawCircle(VC.amber.copy(alpha = alpha), r + 4f * dens, o, style = Stroke(2f * dens))
        if (MapLayer.TASKS in layers && v.taskFlag) drawCircle(VC.red.copy(alpha = alpha), r + 4f * dens, o, style = Stroke(2f * dens, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * dens, 4f * dens))))
        drawContext.canvas.save()
        // vẽ hình theo tình trạng, làm mờ bằng alpha
        val layerAlpha = alpha
        drawConditionShapeAlpha(v.cond, o, r, layerAlpha, isGeo)
        drawContext.canvas.restore()
        if (showLabels && inZone) {
            val lblColor = if (isGeo) Color.White.copy(alpha = layerAlpha) else VC.text.copy(alpha = layerAlpha)
            label(t.code, o + Offset(0f, r + 2f * dens), lblColor, 9f, darkHalo = isGeo)
            if (MapLayer.AGE in layers) {
                val a = v.ageDays
                label(if (a == null) "chưa QS" else "${a} ng", o + Offset(0f, r + 13f * dens),
                    when { a == null -> VC.red; a <= 7 -> VC.mint; a <= d.garden.checkIntervalDays -> VC.amber; else -> VC.red }, 8f, darkHalo = isGeo)
            }
        }
    }
    if (pickMarker != null && mode == MapMode.GEO && world.origin != null) {
        val m = Geo.toMeters(pickMarker, world.origin); val o = place(m.first, m.second)
        drawCircle(Color(0xFF00E5FF), 10f * dens, o, style = Stroke(3f * dens)); drawCircle(Color(0xFF00E5FF), 3f * dens, o)
    }
    if (mode == MapMode.GEO && world.origin != null) {
        val analysisPoints = Geo.open(analysisRegion)
        if (analysisPoints.size >= 3) {
            drawRegionMetricsOnPolygon(
                analysisPoints = analysisPoints,
                hud = regionHud,
                ptTransform = { p -> val m = Geo.toMeters(p, world.origin); place(m.first, m.second) },
                measurer = measurer,
                dens = dens,
            )
        }
        // 7. Các phụ kiện bản đồ chuẩn GIS (La bàn hướng Bắc N, Thang phổ màu Cao/Thấp, Thước tỷ lệ mét)
        drawGisCartographicWidgets(
            scalePxPerM = scalePxPerM,
            theme = gisTheme,
            measurer = measurer,
            dens = dens,
        )
    }
}

private fun DrawScope.drawConditionShapeAlpha(c: Condition, center: Offset, r: Float, alpha: Float, brightOutline: Boolean = false) {
    val col = if (brightOutline) {
        when (c) {
            Condition.STABLE -> Color(0xFF00E676).copy(alpha = alpha)
            Condition.WATCH -> Color(0xFFFFD740).copy(alpha = alpha)
            Condition.NEEDS_CHECK -> Color(0xFFFF5252).copy(alpha = alpha)
            Condition.UNASSESSED -> Color(0xFF80CBC4).copy(alpha = alpha)
        }
    } else conditionColor(c).copy(alpha = alpha)
    when (c) {
        Condition.STABLE -> {
            drawCircle(col, r, center)
            if (brightOutline) drawCircle(Color.White.copy(alpha = alpha * 0.85f), r, center, style = Stroke(1.3f))
        }
        Condition.UNASSESSED -> drawCircle(col, r, center, style = Stroke(r * .35f))
        Condition.WATCH -> drawPath(Path().apply { moveTo(center.x, center.y - r); lineTo(center.x + r, center.y + r * .8f); lineTo(center.x - r, center.y + r * .8f); close() }, col)
        Condition.NEEDS_CHECK -> drawPath(Path().apply {
            moveTo(center.x, center.y - r * 1.15f); lineTo(center.x + r * 1.15f, center.y); lineTo(center.x, center.y + r * 1.15f); lineTo(center.x - r * 1.15f, center.y); close()
        }, col)
    }
}

@Composable
fun MapLegend() {
    androidx.compose.foundation.layout.FlowRow(
        Modifier.padding(top = 10.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) {
        Condition.values().forEach { c ->
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp)) {
                ConditionShape(c); Text(c.label, color = VC.text2, fontSize = 13.sp)
            }
        }
    }
}

