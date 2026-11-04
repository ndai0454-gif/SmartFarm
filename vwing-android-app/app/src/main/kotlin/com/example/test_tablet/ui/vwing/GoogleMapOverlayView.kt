package com.example.test_tablet.ui.vwing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.GeoBounds
import com.example.test_tablet.data.vwing.GeoFeature
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GroundOverlay
import com.google.maps.android.compose.GroundOverlayPosition
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import kotlin.math.ln
import kotlin.math.max

/** Zoom chuẩn xác vào sát khu vườn (Zoom 18.4–20.0 đối với vườn cây ăn trái) để hiển thị ảnh vệ tinh Google ở độ nét tối đa. */
private fun zoomFor(spanDeg: Double): Float {
    val safe = max(spanDeg, 0.00025)
    val raw = (ln(360.0 / safe) / ln(2.0)).toFloat() - 0.15f
    return if (spanDeg <= 0.006) raw.coerceIn(18.4f, 20.0f) else raw.coerceIn(3f, 20f)
}

/**
 * Hiển thị một [MapOverlay] (ảnh chỉ số/biến động/Super-Resolution đã tính) chồng lên bản đồ vệ tinh
 * Google thật, cùng ranh giới vùng đã khoanh. Đây là lớp hiển thị trực quan để đối chiếu với bối cảnh
 * thật (đường, công trình xung quanh) — số liệu/thống kê vẫn tính từ ảnh vệ tinh nguồn ở các thẻ bên
 * trên, KHÔNG tính lại từ nền Google Maps.
 */
@Composable
fun GoogleMapOverlayView(
    region: GeoFeature?,
    overlay: MapOverlay?,
    modifier: Modifier = Modifier,
    regionHud: RegionHudState? = null,
    hudCfg: com.example.test_tablet.data.vwing.SatelliteConfig? = null,
    hudStart: String = "",
    hudEnd: String = "",
    onHudSelectOverlay: (MapOverlay?) -> Unit = {},
    onHudRefresh: () -> Unit = {},
) {
    val context = LocalContext.current
    val keyOk = remember { mapsKeyConfigured(context) }
    if (!keyOk) {
        Box(modifier.clip(RoundedCornerShape(16.dp)).background(VC.elevated), contentAlignment = Alignment.Center) {
            Text("Chưa có khóa Google Maps trong bản build này.", color = VC.text2, fontSize = 15.sp, modifier = Modifier.padding(16.dp))
        }
        return
    }

    val analysisPoints = regionHud?.points ?: region?.points
    val bounds: GeoBounds? = overlay?.bounds ?: analysisPoints?.takeIf { it.size >= 3 }?.let { pts ->
        GeoBounds(pts.minOf { it.lat }, pts.minOf { it.lng }, pts.maxOf { it.lat }, pts.maxOf { it.lng })
    }
    if (bounds == null) {
        Box(modifier.clip(RoundedCornerShape(16.dp)).background(VC.elevated), contentAlignment = Alignment.Center) {
            Text("Chưa có vùng hoặc lớp phân tích để hiển thị.", color = VC.text2, fontSize = 15.sp, modifier = Modifier.padding(16.dp))
        }
        return
    }

    val center = LatLng((bounds.south + bounds.north) / 2, (bounds.west + bounds.east) / 2)
    val zoom = zoomFor(max(bounds.north - bounds.south, bounds.east - bounds.west))
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(center, zoom) }
    androidx.compose.runtime.LaunchedEffect(analysisPoints, overlay?.bounds) {
        camera.position = CameraPosition.fromLatLngZoom(center, zoom)
    }
    val latLngBounds = remember(bounds) { LatLngBounds(LatLng(bounds.south, bounds.west), LatLng(bounds.north, bounds.east)) }
    val currentTheme = regionHud?.gisTheme ?: GisVisualTheme.SPECTRAL_NDVI
    val isStacked3d = regionHud?.stacked3dMode == true
    val clippedGisOverlay = remember(region?.points, currentTheme, regionHud?.gardenAnalysis) {
        val pts = region?.points ?: emptyList()
        if (pts.size >= 3 && regionHud?.selectedQuickLayer != "Tắt phủ" && overlay == null) generatePolygonClippedGisBitmap(pts, currentTheme, regionHud) else null
    }
    val clippedActiveOverlayBitmap = remember(overlay, analysisPoints) {
        val o = overlay
        val pts = analysisPoints
        if (o != null && pts != null && pts.size >= 3) clipBitmapToPolygon(o.bitmap, o.bounds, pts) else o?.bitmap
    }
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current

    Column(modifier.focusProperties { canFocus = false }) {
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)).focusProperties { canFocus = false }) {
            if (isStacked3d && (region?.points?.size ?: 0) >= 3) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    drawStacked3dGisView(
                        analysisPoints = com.example.test_tablet.data.vwing.Geo.open(region!!.points),
                        hud = regionHud,
                        measurer = measurer,
                        dens = density.density,
                    )
                }
            } else {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(), cameraPositionState = camera,
                    properties = MapProperties(mapType = MapType.HYBRID),
                    uiSettings = MapUiSettings(zoomControlsEnabled = true, mapToolbarEnabled = false, myLocationButtonEnabled = false),
                    onMapClick = { latLng ->
                        val tapGeo = com.example.test_tablet.data.vwing.GeoPoint(latLng.latitude, latLng.longitude)
                        if (regionHud?.deepForestAnnotateMode == true) {
                            regionHud.addDeepForestAnnotationAt(tapGeo, regionHud.gardenAnalysis?.treeEstimation?.assumedCrownDiameterM ?: 5.2)
                        } else {
                            val candidates = regionHud?.visibleGeeTrees().orEmpty()
                            val near = candidates.minByOrNull { com.example.test_tablet.data.vwing.Geo.distanceM(it.pos, tapGeo) }
                            if (near != null && com.example.test_tablet.data.vwing.Geo.distanceM(near.pos, tapGeo) <= 22.0) {
                                regionHud?.selectedGeeTreeCode = near.code
                            }
                        }
                    },
                ) {
                    clippedGisOverlay?.let { (b, bmp) ->
                        val polyBounds = LatLngBounds(LatLng(b.south, b.west), LatLng(b.north, b.east))
                        GroundOverlay(
                            position = GroundOverlayPosition.create(polyBounds),
                            image = BitmapDescriptorFactory.fromBitmap(bmp.asAndroidBitmap()),
                            transparency = 0.14f,
                        )
                    }
                    if (regionHud == null || regionHud.selectedQuickLayer != "Tắt phủ") {
                        overlay?.let { o ->
                            val bmpToDraw = clippedActiveOverlayBitmap ?: o.bitmap
                            GroundOverlay(
                                position = GroundOverlayPosition.create(latLngBounds),
                                image = BitmapDescriptorFactory.fromBitmap(bmpToDraw.asAndroidBitmap()),
                                transparency = 1f - (o.alpha * 0.85f).coerceIn(0.15f, 0.92f),
                            )
                        }
                    }
                    analysisPoints?.takeIf { it.size >= 3 }?.let { pts ->
                        Polygon(
                            points = pts.map { LatLng(it.lat, it.lng) },
                            fillColor = Color.Transparent,
                            strokeColor = Color(0xCC06140C),
                            strokeWidth = 9.5f,
                        )
                        Polygon(
                            points = pts.map { LatLng(it.lat, it.lng) },
                            fillColor = Color.Transparent,
                            strokeColor = Color(0xFFFFEA00),
                            strokeWidth = 5.5f,
                        )
                    }
                    if (regionHud?.showDeepForestTileGrid == true) {
                        regionHud.deepForestResult?.tiles?.forEach { tile ->
                            val tb = tile.bounds
                            Polygon(
                                points = listOf(
                                    LatLng(tb.south, tb.west),
                                    LatLng(tb.north, tb.west),
                                    LatLng(tb.north, tb.east),
                                    LatLng(tb.south, tb.east),
                                ),
                                fillColor = Color.Transparent,
                                strokeColor = Color(0x9900E5FF),
                                strokeWidth = 2.5f,
                            )
                        }
                    }
                    if (regionHud?.showDeepForestBboxes == true) {
                        regionHud.userAnnotatedBboxes.forEach { ub ->
                            val bb = ub.bounds
                            Polygon(
                                points = listOf(
                                    LatLng(bb.south, bb.west),
                                    LatLng(bb.north, bb.west),
                                    LatLng(bb.north, bb.east),
                                    LatLng(bb.south, bb.east),
                                ),
                                fillColor = Color(0x2200E5FF),
                                strokeColor = Color(0xFF00E5FF),
                                strokeWidth = 4.5f,
                            )
                        }
                    }
                }
                // Phụ kiện GIS (La bàn N, Thang phổ màu Cao/Thấp), Label từng cây GEE & cụm Icon tại tâm vùng đánh dấu trên Google Map
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    drawGisCartographicWidgets(
                        scalePxPerM = 2.2f * density.density,
                        theme = currentTheme,
                        measurer = measurer,
                        dens = density.density,
                    )
                    if (regionHud != null && (analysisPoints?.size ?: 0) >= 3) {
                        // Đọc camera.position để Canvas tự vẽ lại khớp từng pixel khi người dùng kéo/zoom Google Map
                        val camPos = camera.position
                        val proj = camera.projection
                        if (proj != null && camPos.zoom > 0f) {
                            val openPts = com.example.test_tablet.data.vwing.Geo.open(analysisPoints!!)
                            drawRegionMetricsOnPolygon(
                                analysisPoints = openPts,
                                hud = regionHud,
                                ptTransform = { p ->
                                    val sc = proj.toScreenLocation(LatLng(p.lat, p.lng))
                                    androidx.compose.ui.geometry.Offset(sc.x.toFloat(), sc.y.toFloat())
                                },
                                measurer = measurer,
                                dens = density.density,
                            )
                        }
                    }
                }
            }
            if (regionHud != null && hudCfg != null) {
                RegionMapHudOverlay(
                    hud = regionHud,
                    cfg = hudCfg,
                    start = hudStart,
                    end = hudEnd,
                    onSelectOverlay = onHudSelectOverlay,
                    onRefreshAll = onHudRefresh,
                )
            } else {
                overlay?.let { o ->
                    Box(Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xCC14241A))) {
                        Text(o.label, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
        }
        if (!VUi.simpleMode) {
            Text(
                "Nền bản đồ vệ tinh Google chỉ để đối chiếu trực quan (đường, công trình xung quanh). " +
                    "Số liệu và lớp ảnh phủ vẫn tính từ ảnh Sentinel-2/mô hình AI ở các thẻ phân tích phía trên, không tính lại từ nền bản đồ này.",
                color = VC.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
