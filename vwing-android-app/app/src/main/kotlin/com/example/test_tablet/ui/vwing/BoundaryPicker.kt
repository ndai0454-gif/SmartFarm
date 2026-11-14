package com.example.test_tablet.ui.vwing

import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.test_tablet.data.vwing.BoundaryWalk
import com.example.test_tablet.data.vwing.FeatureKind
import com.example.test_tablet.data.vwing.Geo
import com.example.test_tablet.data.vwing.GeoFeature
import com.example.test_tablet.data.vwing.GeoPoint
import com.example.test_tablet.data.vwing.SatCropKey
import com.example.test_tablet.data.vwing.SatGeeGardenClarity
import com.example.test_tablet.data.vwing.SatelliteClient
import com.example.test_tablet.data.vwing.SatelliteSettings
import com.example.test_tablet.data.vwing.VIETNAM_GARDEN_PRESETS
import com.example.test_tablet.data.vwing.activeTrees
import com.example.test_tablet.data.vwing.newId
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.UrlTileProvider
import com.google.maps.android.compose.DragState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GroundOverlay
import com.google.maps.android.compose.GroundOverlayPosition
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.TileOverlay
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import kotlin.math.cos

const val MAX_BOUNDARY_HA = 500.0

/** 5 chế độ hiển thị Google Earth Engine (GEE) trong hộp thoại "Chọn vùng vườn của bạn" để nhìn rõ khu vườn và bờ ranh. */
enum class GeePickerClarityMode(val layerId: String, val chipLabel: String, val descriptionVi: String) {
    SATELLITE_CLEAR(
        "NONE",
        "🛰️ Ảnh vệ tinh rõ nét",
        "Hiển thị ảnh vệ tinh rõ ràng kèm đường viền vàng để bác chấm các góc vườn chính xác.",
    ),
    GEE_TRUE_COLOR(
        "GEE_CLARITY_TRUE_COLOR",
        "🌍 Ảnh thực tế tăng nét",
        "Làm rõ từng hàng cây, bờ lô và lối đi trong vườn để bác dễ nhìn ranh đất.",
    ),
    GEE_FALSE_COLOR_NIR(
        "GEE_FALSE_COLOR_NIR",
        "🌿 Nổi bật tán cây (Tách ranh)",
        "Tán cây hiện rõ để bác phân biệt ranh vườn với đường đất, bờ rào và nhà cửa.",
    ),
    GEE_CANOPY_CROWN(
        "GEE_CANOPY_CROWN_HD",
        "🌳 Nhìn rõ vòm tán cây",
        "Làm nổi bật từng tán cây lâu năm trong vườn trên nền bản đồ.",
    ),
    GEE_MOISTURE(
        "GEE_MOISTURE_WATER",
        "💧 Độ ẩm đất & Ao tưới",
        "Nhận diện nhanh ao hồ tưới, kênh mương và vùng đất ẩm trong vườn.",
    ),
}

/** Khóa Google Maps đã được cấu hình lúc build chưa (local.properties → MAPS_API_KEY). Không đọc/hiển thị giá trị khóa. */
fun mapsKeyConfigured(context: android.content.Context): Boolean = try {
    @Suppress("DEPRECATION")
    val v = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData?.getString("com.google.android.geo.API_KEY")
    !v.isNullOrBlank() && v != "DEFAULT_API_KEY"
} catch (_: Exception) { false }

/** Lý do vùng chưa dùng được, hoặc null nếu hợp lệ. */
fun boundaryProblem(pts: List<GeoPoint>): String? = when {
    pts.size < 3 -> "Cần ít nhất 3 đỉnh (đang có ${pts.size})."
    Geo.selfIntersects(pts) -> "Ranh giới tự cắt nhau. Hoàn tác hoặc xóa và vẽ lại."
    Geo.polygonAreaHa(pts) < 0.01 -> "Vùng quá nhỏ (dưới 0,01 ha)."
    Geo.polygonAreaHa(pts) > MAX_BOUNDARY_HA -> "Vùng lớn hơn ${MAX_BOUNDARY_HA.toInt()} ha, vượt giới hạn phân tích vệ tinh."
    else -> null
}

/** Cắt (mask) bất kỳ ảnh Bitmap GEE/vệ tinh nào đúng theo đa giác vùng đã khoanh [ring], phần ngoài đa giác trong suốt 100%. */
fun clipBitmapToPolygon(
    src: ImageBitmap,
    bounds: com.example.test_tablet.data.vwing.GeoBounds,
    ring: List<GeoPoint>,
): ImageBitmap {
    if (ring.size < 3) return src
    val srcBmp = src.asAndroidBitmap()
    val w = srcBmp.width.coerceAtLeast(1)
    val h = srcBmp.height.coerceAtLeast(1)
    val lonSpan = (bounds.east - bounds.west).takeIf { it > 1e-9 } ?: return src
    val latSpan = (bounds.north - bounds.south).takeIf { it > 1e-9 } ?: return src

    val out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    val path = android.graphics.Path().apply {
        fillType = android.graphics.Path.FillType.EVEN_ODD
        ring.forEachIndexed { idx, pt ->
            val x = (((pt.lng - bounds.west) / lonSpan) * w).toFloat()
            val y = (((bounds.north - pt.lat) / latSpan) * h).toFloat()
            if (idx == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    val maskPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        color = android.graphics.Color.WHITE
    }
    canvas.drawPath(path, maskPaint)
    val srcPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
    }
    canvas.drawBitmap(srcBmp, 0f, 0f, srcPaint)
    return out.asImageBitmap()
}

/** Tạo ô chữ nhật 160m×160m quanh tâm camera khi người dùng muốn làm rõ khu vườn bằng GEE trước khi chấm đủ 3 đỉnh. */
private fun cameraInspectionRing(center: LatLng, halfSideM: Double = 85.0): List<GeoPoint> {
    val dLat = halfSideM / 110_574.0
    val dLon = halfSideM / (111_320.0 * cos(Math.toRadians(center.latitude)).coerceAtLeast(0.1))
    return listOf(
        GeoPoint(center.latitude - dLat, center.longitude - dLon),
        GeoPoint(center.latitude - dLat, center.longitude + dLon),
        GeoPoint(center.latitude + dLat, center.longitude + dLon),
        GeoPoint(center.latitude + dLat, center.longitude - dLon),
    )
}

/**
 * Hộp thoại Khoanh vùng vườn (dùng khi mở từ các màn hình phụ như Quy trình 6 bước / Vệ tinh).
 */
@Composable
fun BoundaryPickerDialog(c: VCtx, onDismiss: () -> Unit, onSaved: () -> Unit = {}) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        BoundaryPickerScreen(c = c, isDialog = true, onDismiss = onDismiss, onSaved = onSaved)
    }
}

/**
 * Màn hình Khoanh vườn & Phân tích Google Earth Engine HD trực tiếp:
 * chạm vào bản đồ vệ tinh để thêm đỉnh, kéo đỉnh để chỉnh, bật lớp GEE True-Color / Hồng ngoại NIR / Tách vòm tán / Ẩm độ & Ao tưới
 * được cắt (clip) chính xác bên trong vùng đã khoanh.
 */
@Composable
fun BoundaryPickerScreen(
    c: VCtx,
    isDialog: Boolean = false,
    onDismiss: () -> Unit = { c.nav.top("overview") },
    onSaved: () -> Unit = {},
) {
    val context = LocalContext.current
    val d = c.d
    val keyOk = remember { mapsKeyConfigured(context) }
    val existing = remember { SatelliteBoundary.of(d) }
    val pts = remember { mutableStateListOf<GeoPoint>().also { if (existing != null) it.addAll(existing) } }
    var manual by remember { mutableStateOf("") }
    var presetSource by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val satCfg = remember { SatelliteSettings(context.filesDir).load() }
    var pickerOverlay by remember { mutableStateOf<MapOverlay?>(null) }

    // Trạng thái Google Earth Engine Garden Clarity (mặc định SATELLITE_CLEAR để giữ 100% ảnh vệ tinh Google Zoom 19–20 sắc nét trong vùng khoanh)
    var clarityMode by remember { mutableStateOf(GeePickerClarityMode.SATELLITE_CLEAR) }
    var geeClarity by remember { mutableStateOf<SatGeeGardenClarity?>(null) }
    var geeLayerBitmaps by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    var geeLoading by remember { mutableStateOf(false) }
    var geeError by remember { mutableStateOf<String?>(null) }
    var geeOpacity by remember { mutableFloatStateOf(0.72f) }

    val previewFeature = remember(pts.toList(), presetSource) {
        if (pts.size >= 3 && boundaryProblem(pts) == null) {
            GeoFeature("picker-preview", FeatureKind.BOUNDARY, presetSource?.substringBefore(" (") ?: "Vùng đang khoanh", pts.toList())
        } else null
    }
    val pickerHud = rememberRegionHudState(
        d = d,
        region = previewFeature,
        cfg = satCfg,
        onAutoOverlay = { pickerOverlay = it },
    )
    val openedAt = remember { System.currentTimeMillis() }   // bỏ qua cú chạm "lọt" qua từ nút mở hộp thoại

    val center = remember {
        val all = existing ?: d.activeTrees.mapNotNull { it.pos }
        if (all.isNotEmpty()) LatLng(all.sumOf { it.lat } / all.size, all.sumOf { it.lng } / all.size) else LatLng(11.5500, 107.8000)
    }
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(center, if (existing != null) 19.2f else 18.2f) }
    var located by remember { mutableStateOf(false) }
    val ensure = rememberEnsureLocationPermission(c)
    val locate = rememberLocationFetcher(c) { la, lo, _ -> located = true; scope.launch { camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(la, lo), 18.5f)) } }
    val addHere = rememberLocationFetcher(c) { la, lo, acc ->
        located = true
        presetSource = null
        pts.add(GeoPoint(la, lo))
        scope.launch { camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(la, lo), 19f)) }
        c.toast(if (acc != null && acc > BoundaryWalk.MAX_ACC_M) "Đã thêm đỉnh, nhưng sai số GPS ±${acc.toInt()} m khá lớn — nên kéo đỉnh cho đúng." else "Đã thêm đỉnh tại vị trí của bạn" + (acc?.let { " (±${it.toInt()} m)" } ?: "") + ".")
    }
    LaunchedEffect(Unit) { if (keyOk && existing == null) ensure { locate() } }

    fun fetchGeeClarityForRing(targetRing: List<GeoPoint>, switchModeIfClear: Boolean = false) {
        if (!satCfg.configured || targetRing.size < 3 || boundaryProblem(targetRing) != null) return
        geeLoading = true
        geeError = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                SatelliteClient.geeGardenClarity(satCfg, targetRing, SatCropKey.DURIAN, bufferM = 0.0).map { rep ->
                    val bmps = mutableMapOf<String, ImageBitmap>()
                    rep.layers.forEach { lyr ->
                        val url = lyr.imageUrl
                        if (!url.isNullOrBlank()) {
                            SatelliteClient.image(satCfg, url).getOrNull()?.let { bytes ->
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()?.let { bmp ->
                                    bmps[lyr.layerId] = clipBitmapToPolygon(bmp, lyr.bounds, targetRing)
                                }
                            }
                        }
                    }
                    rep to bmps.toMap()
                }
            }
            geeLoading = false
            result.onSuccess { (rep, bmps) ->
                geeClarity = rep
                geeLayerBitmaps = bmps
                if (switchModeIfClear && clarityMode == GeePickerClarityMode.SATELLITE_CLEAR) {
                    clarityMode = GeePickerClarityMode.GEE_TRUE_COLOR
                }
            }.onFailure { e ->
                geeError = e.message ?: "Không tải được lớp làm rõ Google Earth Engine."
            }
        }
    }

    // Tự động gọi Google Earth Engine API làm rõ khu vườn ngay khi có từ 3 đỉnh hợp lệ trở lên
    val ptsSnapshot = pts.toList()
    LaunchedEffect(ptsSnapshot, satCfg.url, satCfg.token) {
        if (ptsSnapshot.size >= 3 && boundaryProblem(ptsSnapshot) == null && satCfg.configured) {
            fetchGeeClarityForRing(ptsSnapshot, switchModeIfClear = false)
        }
    }

    // chế độ nhập tay: mỗi dòng "vĩ độ, kinh độ"
    LaunchedEffect(manual) {
        if (!keyOk) {
            val parsed = manual.lines().mapNotNull { l ->
                val p = l.split(',', ';', ' ', '\t').filter { it.isNotBlank() }.mapNotNull { it.replace(',', '.').toDoubleOrNull() }
                if (p.size >= 2) GeoPoint(p[0], p[1]) else null
            }
            pts.clear(); pts.addAll(parsed)
        }
    }

    val problem = boundaryProblem(pts)
    val area = if (pts.size >= 3) Geo.polygonAreaHa(pts) else 0.0
    val activeGeeLayer = remember(geeClarity, clarityMode) {
        geeClarity?.layers?.firstOrNull { it.layerId == clarityMode.layerId }
    }
    val activeGeeBitmap = remember(geeLayerBitmaps, clarityMode, activeGeeLayer?.bounds, ptsSnapshot) {
        val raw = geeLayerBitmaps[clarityMode.layerId]
        val b = activeGeeLayer?.bounds
        if (raw != null && b != null && ptsSnapshot.size >= 3) clipBitmapToPolygon(raw, b, ptsSnapshot) else raw
    }
    val clippedPickerOverlayBitmap = remember(pickerOverlay, ptsSnapshot) {
        val o = pickerOverlay
        if (o != null && ptsSnapshot.size >= 3) clipBitmapToPolygon(o.bitmap, o.bounds, ptsSnapshot) else o?.bitmap
    }

    val rootMod = if (isDialog) {
        Modifier.fillMaxSize().background(VC.bg).statusBarsPadding().navigationBarsPadding()
    } else {
        Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(VC.bg)
    }

    Column(rootMod) {
        if (isDialog || !c.simpleMode) {
            Column(Modifier.fillMaxWidth().background(VC.card).padding(horizontal = 12.dp, vertical = 4.dp)) {
                if (isDialog) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Chọn vùng vườn · Google Earth Engine HD",
                            color = VC.text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                        )
                        if (geeLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                CircularProgressIndicator(Modifier.size(14.dp), color = VC.mint, strokeWidth = 2.dp)
                                Text("Đang tải GEE…", color = VC.mint, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
                // Tích hợp Bộ quy đổi Góc ranh Sổ đỏ VN-2000 ⇄ WGS84 & 34 Tỉnh sau sáp nhập (chỉ hiện ở Chế độ Kỹ sư)
                var showVn2000Panel by remember { mutableStateOf(false) }
                var vn2000Text by remember { mutableStateOf(com.example.test_tablet.data.vwing.Vn2000Converter.PRESETS_VN2000.first().coordsText) }
                val vnProvinceOptions = remember {
                    com.example.test_tablet.data.vwing.Vn2000Converter.AFTER_MERGER_34.map { "${it.name} · L0=${it.defaultL0}° (${it.mergedDetail})" } +
                        com.example.test_tablet.data.vwing.Vn2000Converter.CENTRAL_MERIDIANS_63.map { (k, v) -> "$k (63 tỉnh) · L0=$v°" }
                }
                var selectedVnProvince by remember { mutableStateOf(vnProvinceOptions.first()) }
                if (!c.simpleMode) Row(
                    Modifier.fillMaxWidth().padding(top = if (isDialog) 4.dp else 0.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (showVn2000Panel) "📐 Ẩn bảng Góc ranh Sổ đỏ VN-2000" else "📐 Nhập Góc ranh Sổ đỏ VN-2000 & 34 Tỉnh mới",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (showVn2000Panel) VC.teal else VC.lime)
                            .clickable { showVn2000Panel = !showVn2000Panel }
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                    com.example.test_tablet.data.vwing.Vn2000Converter.PRESETS_VN2000.forEach { pr ->
                        Text(
                            "⚡ Mẫu Sổ đỏ ${pr.province}",
                            color = VC.lime,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(VC.elevated)
                                .clickable {
                                    com.example.test_tablet.data.vwing.Vn2000Converter.activeProvince = pr.province
                                    com.example.test_tablet.data.vwing.Vn2000Converter.activeLon0 = pr.lon0
                                    vn2000Text = pr.coordsText
                                    val converted = com.example.test_tablet.data.vwing.Vn2000Converter.parseVn2000Table(pr.coordsText, pr.lon0)
                                    if (converted.size >= 3) {
                                        presetSource = "${pr.label} (Tờ ${pr.sheetNumber} · Thửa ${pr.plotNumber})"
                                        pts.clear(); pts.addAll(converted)
                                        manual = converted.joinToString("\n") { "${it.lat}, ${it.lng}" }
                                        if (keyOk) {
                                            val south = converted.minOf { it.lat }; val west = converted.minOf { it.lng }
                                            val north = converted.maxOf { it.lat }; val east = converted.maxOf { it.lng }
                                            scope.launch {
                                                camera.animate(CameraUpdateFactory.newLatLngBounds(LatLngBounds(LatLng(south, west), LatLng(north, east)), 90))
                                            }
                                        }
                                        c.toast("Đã quy đổi ${converted.size} đỉnh VN-2000 (${pr.province}, L0=${pr.lon0}°) sang WGS84.")
                                    }
                                }
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                        )
                    }
                    VIETNAM_GARDEN_PRESETS.forEach { pr ->
                        Text(
                            "🌿 ${pr.name}",
                            color = VC.text,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(VC.elevated)
                                .clickable {
                                    presetSource = "${pr.name} (${pr.regionLabel})"
                                    pts.clear(); pts.addAll(pr.points)
                                    manual = pr.points.joinToString("\n") { "${it.lat}, ${it.lng}" }
                                    if (keyOk && pr.points.isNotEmpty()) {
                                        val south = pr.points.minOf { it.lat }
                                        val west = pr.points.minOf { it.lng }
                                        val north = pr.points.maxOf { it.lat }
                                        val east = pr.points.maxOf { it.lng }
                                        scope.launch {
                                            camera.animate(CameraUpdateFactory.newLatLngBounds(LatLngBounds(LatLng(south, west), LatLng(north, east)), 90))
                                        }
                                    }
                                    c.toast("Đã nạp tọa độ: ${pr.name}")
                                }
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                        )
                    }
                }
                if (showVn2000Panel) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(VC.elevated)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        VPicker(
                            "Tỉnh/Thành & Kinh tuyến trục L0 (34 Tỉnh mới sau sáp nhập & 63 Tỉnh gốc)",
                            vnProvinceOptions,
                            selectedVnProvince,
                            { picked ->
                                selectedVnProvince = picked
                                val l0 = Regex("L0=([0-9.]+)").find(picked)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 107.75
                                com.example.test_tablet.data.vwing.Vn2000Converter.activeProvince = picked.substringBefore(" ·")
                                com.example.test_tablet.data.vwing.Vn2000Converter.activeLon0 = l0
                            },
                        )
                        VField(
                            vn2000Text,
                            { vn2000Text = it },
                            "Bảng tọa độ Góc ranh VN-2000 trên Sổ đỏ (mỗi dòng: X_Bắc, Y_Đông — tự động nhận diện đảo trục)",
                            singleLine = false,
                            minLines = 3,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VButton("⚡ Quy đổi VN-2000 → Khoanh vùng bản đồ", {
                                val l0 = com.example.test_tablet.data.vwing.Vn2000Converter.activeLon0
                                val prov = com.example.test_tablet.data.vwing.Vn2000Converter.activeProvince
                                val converted = com.example.test_tablet.data.vwing.Vn2000Converter.parseVn2000Table(vn2000Text, l0)
                                if (converted.size < 3) {
                                    c.toast("Cần ít nhất 3 đỉnh tọa độ VN-2000 (X, Y) hợp lệ để dựng đa giác ranh vườn.")
                                } else {
                                    presetSource = "Sổ đỏ VN-2000 ($prov · L0=$l0°)"
                                    pts.clear(); pts.addAll(converted)
                                    manual = converted.joinToString("\n") { "${it.lat}, ${it.lng}" }
                                    showVn2000Panel = false
                                    if (keyOk) {
                                        val south = converted.minOf { it.lat }; val west = converted.minOf { it.lng }
                                        val north = converted.maxOf { it.lat }; val east = converted.maxOf { it.lng }
                                        scope.launch {
                                            camera.animate(CameraUpdateFactory.newLatLngBounds(LatLngBounds(LatLng(south, west), LatLng(north, east)), 90))
                                        }
                                    }
                                    c.toast("Đã quy đổi ${converted.size} đỉnh VN-2000 ($prov, L0=$l0°) sang WGS84 (${fmtNum(Geo.polygonAreaHa(converted), 2)} ha).")
                                }
                            })
                        }
                    }
                }
            }
        }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (keyOk) {
                    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val geeTileProvider = remember(activeGeeLayer?.tileUrlTemplate) {
                        val tpl = activeGeeLayer?.tileUrlTemplate
                        if (tpl.isNullOrBlank()) null
                        else object : UrlTileProvider(256, 256) {
                            override fun getTileUrl(x: Int, y: Int, zoom: Int): URL? = runCatching {
                                URL(tpl.replace("{x}", x.toString()).replace("{y}", y.toString()).replace("{z}", zoom.toString()))
                            }.getOrNull()
                        }
                    }
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(), cameraPositionState = camera,
                        properties = MapProperties(mapType = MapType.HYBRID, isMyLocationEnabled = located),
                        uiSettings = MapUiSettings(zoomControlsEnabled = true, mapToolbarEnabled = false, myLocationButtonEnabled = false),
                        onMapClick = { ll ->
                            if (System.currentTimeMillis() - openedAt > 600) {
                                val tapGeo = GeoPoint(ll.latitude, ll.longitude)
                                if (pickerHud?.deepForestAnnotateMode == true && pts.size >= 3) {
                                    pickerHud.addDeepForestAnnotationAt(tapGeo, pickerHud.gardenAnalysis?.treeEstimation?.assumedCrownDiameterM ?: 5.2)
                                    c.toast("➕ Đã thêm khung tán mẫu #${pickerHud.userAnnotatedBboxes.size} để Fine-tune DeepForest.")
                                } else {
                                    presetSource = null
                                    pts.add(tapGeo)
                                }
                            }
                        },
                    ) {
                        existing?.let { ex ->
                            Polygon(points = ex.map { LatLng(it.lat, it.lng) }, fillColor = Color(0x08FFFFFF), strokeColor = Color(0xAAFFFFFF), strokeWidth = 3f)
                        }
                        // 1. Lớp ảnh phủ GEE độ phân giải cao (GEE Thực tế Tăng nét, GEE Hồng ngoại, GEE Tách Vòm Tán & Hàng Cây, GEE Ẩm độ & Ao tưới)
                        // được cắt (clip) chính xác 100% bên trong đa giác ranh vườn đã khoanh — ngoài ranh vườn giữ nguyên ảnh vệ tinh gốc.
                        if (clarityMode != GeePickerClarityMode.SATELLITE_CLEAR && activeGeeLayer != null && activeGeeBitmap != null) {
                            val b = activeGeeLayer.bounds
                            val polyBounds = LatLngBounds(LatLng(b.south, b.west), LatLng(b.north, b.east))
                            GroundOverlay(
                                position = GroundOverlayPosition.create(polyBounds),
                                image = BitmapDescriptorFactory.fromBitmap(activeGeeBitmap.asAndroidBitmap()),
                                transparency = (1f - geeOpacity).coerceIn(0f, 0.95f),
                            )
                        } else if (clarityMode != GeePickerClarityMode.SATELLITE_CLEAR && geeTileProvider != null && pts.size < 3) {
                            TileOverlay(
                                tileProvider = geeTileProvider,
                                transparency = (1f - geeOpacity).coerceIn(0f, 0.9f),
                            )
                        } else if (pickerOverlay != null && clippedPickerOverlayBitmap != null && pickerHud?.selectedQuickLayer != "Tắt phủ") {
                            val o = pickerOverlay!!
                            val polyBounds = LatLngBounds(LatLng(o.bounds.south, o.bounds.west), LatLng(o.bounds.north, o.bounds.east))
                            GroundOverlay(
                                position = GroundOverlayPosition.create(polyBounds),
                                image = BitmapDescriptorFactory.fromBitmap(clippedPickerOverlayBitmap.asAndroidBitmap()),
                                transparency = (1f - o.alpha).coerceIn(0.15f, 0.9f),
                            )
                        }
                        // 3. Đa giác ranh vườn hoàn toàn trong suốt bên trong (Color.Transparent — không làm mờ tán cây!) + viền kép phát sáng sắc nét
                        if (pts.size >= 3) {
                            Polygon(
                                points = pts.map { LatLng(it.lat, it.lng) },
                                fillColor = Color.Transparent,
                                strokeColor = Color(0xCC06140C),
                                strokeWidth = 10f,
                            )
                            Polygon(
                                points = pts.map { LatLng(it.lat, it.lng) },
                                fillColor = Color.Transparent,
                                strokeColor = Color(0xFFFFEA00),
                                strokeWidth = 5.5f,
                            )
                        } else if (pts.size == 2) {
                            Polyline(points = pts.map { LatLng(it.lat, it.lng) }, color = Color(0xFFFFEA00), width = 5f)
                        }
                        if (pickerHud != null && pts.size >= 3) {
                            if (pickerHud.showDeepForestTileGrid) {
                                pickerHud.deepForestResult?.tiles?.forEach { tile ->
                                    val tb = tile.bounds
                                    Polygon(
                                        points = listOf(
                                            LatLng(tb.south, tb.west),
                                            LatLng(tb.north, tb.west),
                                            LatLng(tb.north, tb.east),
                                            LatLng(tb.south, tb.east),
                                        ),
                                        fillColor = Color.Transparent,
                                        strokeColor = Color(0xAA00E5FF),
                                        strokeWidth = 2.5f,
                                    )
                                }
                            }
                            if (pickerHud.showDeepForestBboxes) {
                                pickerHud.userAnnotatedBboxes.forEach { ub ->
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
                        pts.forEachIndexed { i, p -> Vertex(i, p) { moved -> if (i < pts.size) { presetSource = null; pts[i] = moved } } }
                    }
                    if (pts.size >= 2) {
                        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                            val camPos = camera.position
                            val proj = camera.projection
                            if (proj != null && camPos.zoom > 0f) {
                                val openPts = Geo.open(pts)
                                drawRegionMetricsOnPolygon(
                                    analysisPoints = openPts,
                                    hud = pickerHud,
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
                    if (pickerHud != null && !c.simpleMode) {
                        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(com.example.test_tablet.data.vwing.Dates.today())
                        val startStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(com.example.test_tablet.data.vwing.Dates.today() - 30L * 86_400_000)
                        RegionMapHudOverlay(
                            hud = pickerHud,
                            cfg = satCfg,
                            start = startStr,
                            end = todayStr,
                            onSelectOverlay = { pickerOverlay = it },
                            onRefreshAll = {
                                pickerHud.refreshAll(satCfg, startStr, todayStr, scope, onAutoOverlay = { pickerOverlay = it })
                                fetchGeeClarityForRing(pts.toList())
                            },
                        )
                    } else if (pickerHud == null) {
                        Column(Modifier.align(Alignment.TopStart).padding(12.dp).fillMaxWidth(.5f).background(VC.card.copy(alpha = .94f)).padding(10.dp)) {
                            PlaceSearchBox(near = camera.position.target.let { GeoPoint(it.latitude, it.longitude) }, onPick = { pl ->
                                scope.launch {
                                    val b = pl.bbox
                                    if (b != null && (b.north - b.south) > 0.0005) camera.animate(CameraUpdateFactory.newLatLngBounds(LatLngBounds(LatLng(b.south, b.west), LatLng(b.north, b.east)), 80))
                                    else camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(pl.lat, pl.lng), 18.5f))
                                }
                            }, label = "Tìm địa điểm để bay tới")
                        }
                    }
                    if (pickerHud == null || c.simpleMode) {
                        Row(
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 8.dp, bottom = 12.dp, end = 58.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                AppStrings.tr(if (c.wide) "🎯 Vị trí của tôi" else "🎯 Vị trí"),
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color(0xD80A1D13))
                                    .clickable { ensure { locate() } }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                            )
                            Text(
                                AppStrings.tr(if (c.wide) "➕ Chấm vị trí đang đứng" else "➕ Chấm góc"),
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color(0xD80A1D13))
                                    .clickable { ensure { addHere() } }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                            )
                            Text(
                                AppStrings.tr("🔍 Phóng 19x"),
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color(0xD80A1D13))
                                    .clickable {
                                        scope.launch {
                                            val target = if (pts.isNotEmpty()) LatLng(pts.sumOf { it.lat } / pts.size, pts.sumOf { it.lng } / pts.size) else camera.position.target
                                            camera.animate(CameraUpdateFactory.newLatLngZoom(target, 19.2f))
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                            )
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                        VField(manual, { manual = it; presetSource = null }, "Các đỉnh (vĩ độ, kinh độ)", singleLine = false, minLines = 6, placeholder = "11.5500, 107.8000\n11.5500, 107.8010\n11.5510, 107.8010")
                    }
                }
            }
            Column(Modifier.fillMaxWidth().background(VC.card).padding(horizontal = 10.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Thanh điều khiển Làm rõ khu vườn bằng Google Earth Engine API (cuộn ngang 1 dòng gọn gàng, không che khuất bản đồ)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0A1D13))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (keyOk && pickerHud != null && !c.simpleMode) {
                            Text(
                                AppStrings.tr("🎯 Vị trí"),
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x38FFFFFF))
                                    .clickable { ensure { locate() } }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                            Text(
                                AppStrings.tr("➕ Chấm góc"),
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x38FFFFFF))
                                    .clickable { ensure { addHere() } }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                            Text(
                                "🔍 19x",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x38FFFFFF))
                                    .clickable {
                                        scope.launch {
                                            val target = if (pts.isNotEmpty()) LatLng(pts.sumOf { it.lat } / pts.size, pts.sumOf { it.lng } / pts.size) else camera.position.target
                                            camera.animate(CameraUpdateFactory.newLatLngZoom(target, 19.2f))
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                        Text(
                            AppStrings.tr("🛰️ Chế độ:"),
                            color = Color(0xFF69F0AE),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                        GeePickerClarityMode.entries.forEach { mode ->
                            val selected = clarityMode == mode
                            Text(
                                AppStrings.tr(mode.chipLabel),
                                color = if (selected) Color(0xFF07150D) else Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) Color(0xFF69F0AE) else Color(0x28FFFFFF))
                                    .clickable {
                                        clarityMode = mode
                                        if (mode != GeePickerClarityMode.SATELLITE_CLEAR && geeClarity == null && satCfg.configured) {
                                            val ringToScan = if (pts.size >= 3 && boundaryProblem(pts) == null) pts.toList()
                                            else cameraInspectionRing(camera.position.target)
                                            fetchGeeClarityForRing(ringToScan, switchModeIfClear = false)
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                        Text(
                            AppStrings.tr(if (geeLoading) "⏳ Đang làm rõ…" else "✨ Làm rõ vùng xem"),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1565C0))
                                .clickable(enabled = !geeLoading && satCfg.configured) {
                                    val ringToScan = if (pts.size >= 3 && boundaryProblem(pts) == null) pts.toList()
                                    else cameraInspectionRing(camera.position.target)
                                    fetchGeeClarityForRing(ringToScan, switchModeIfClear = true)
                                    c.toast("Đang gọi Google Earth Engine API để làm rõ khu vườn…")
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    val gc = geeClarity
                    if (gc != null) {
                        val modeTag = if (gc.simulated) "GEE MÔ PHỎNG" else "GEE LIVE"
                        val dateTag = gc.bestSceneDate ?: "gần nhất"
                        val cloudTag = gc.bestSceneCloudPct?.let { "mây ${fmtNum(it, 1)}%" } ?: "quang mây ${fmtNum(gc.clearSkyPct, 0)}%"
                        Text(
                            "✅ $modeTag ($dateTag · $cloudTag) · Tán ${fmtNum(gc.canopyCoverPct, 1)}% · ~${gc.estimatedTrees} cây · NDVI ${gc.meanNdvi?.let { fmtNum(it, 2) } ?: "—"}" +
                                (gc.elevationMeanM?.let { " · Cao độ ${fmtNum(it, 0)}m" } ?: ""),
                            color = Color(0xFFB9F6CA),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    } else if (geeError != null) {
                        Text("⚠ $geeError", color = Color(0xFFFFD166), fontSize = 11.5.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    if (clarityMode != GeePickerClarityMode.SATELLITE_CLEAR) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            listOf(0.55f to "Độ đậm 55%", 0.80f to "Độ đậm 80%", 0.95f to "Độ đậm 95%").forEach { (alphaVal, lbl) ->
                                val activeAlpha = kotlin.math.abs(geeOpacity - alphaVal) < 0.08f
                                Text(
                                    lbl,
                                    color = if (activeAlpha) Color(0xFF07150D) else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (activeAlpha) Color(0xFF69F0AE) else Color(0x24FFFFFF))
                                        .clickable { geeOpacity = alphaVal }
                                        .padding(horizontal = 7.dp, vertical = 2.5.dp),
                                )
                            }
                        }
                    }
                }

                if (pickerHud != null && pts.size >= 3 && !c.simpleMode) {
                    val detectedTrees = pickerHud.allGeeTreesInPolygon().ifEmpty {
                        geeClarity?.samplePoints?.filter { Geo.pointInPolygon(it.pos, pts) }.orEmpty()
                    }
                    val dfRep = pickerHud.deepForestResult
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0E2217))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (dfRep != null) "🌲 DeepForest: ${detectedTrees.size} tán cây"
                            else if (detectedTrees.isNotEmpty()) "🌳 GEE + Crown-NMS: ~${detectedTrees.size} tán cây"
                            else "🌲 Chưa quét DeepForest",
                            color = Color(0xFF69F0AE),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        com.example.test_tablet.data.vwing.DeepForestModelPreset.entries.forEach { preset ->
                            val active = pickerHud.deepForestModelId == preset.modelId
                            Text(
                                preset.shortTitle,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (active) Color(0xFF2E7D32) else Color(0x2EFFFFFF))
                                    .clickable {
                                        pickerHud.runDeepForestPredict(
                                            cfg = satCfg,
                                            modelId = preset.modelId,
                                            scope = scope,
                                            onDone = { msg -> c.toast(msg) },
                                        )
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.5.dp),
                            )
                        }
                        Text(
                            if (pickerHud.showDeepForestBboxes) "🔲 Ẩn khung tán" else "🔲 Hiện khung tán",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (pickerHud.showDeepForestBboxes) Color(0xFF388E3C) else Color(0x2EFFFFFF))
                                .clickable { pickerHud.showDeepForestBboxes = !pickerHud.showDeepForestBboxes }
                                .padding(horizontal = 8.dp, vertical = 3.5.dp),
                        )
                        Text(
                            if (pickerHud.showDeepForestTileGrid) "🧩 Ẩn lưới Tile" else "🧩 Lưới cắt ảnh",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (pickerHud.showDeepForestTileGrid) Color(0xFF00838F) else Color(0x2EFFFFFF))
                                .clickable { pickerHud.showDeepForestTileGrid = !pickerHud.showDeepForestTileGrid }
                                .padding(horizontal = 8.dp, vertical = 3.5.dp),
                        )
                        Text(
                            if (pickerHud.deepForestAnnotateMode) "✋ Đang gán nhãn (${pickerHud.userAnnotatedBboxes.size})" else "➕ Gán nhãn mẫu (${pickerHud.userAnnotatedBboxes.size})",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (pickerHud.deepForestAnnotateMode) Color(0xFFEF6C00) else Color(0x2EFFFFFF))
                                .clickable {
                                    pickerHud.deepForestAnnotateMode = !pickerHud.deepForestAnnotateMode
                                    c.toast(if (pickerHud.deepForestAnnotateMode) "Chạm vào tán cây trên Google Map để thêm khung mẫu Fine-tune." else "Đã quay lại chế độ đặt đỉnh ranh vườn.")
                                }
                                .padding(horizontal = 8.dp, vertical = 3.5.dp),
                        )
                        Text(
                            if (pickerHud.deepForestFineTuneStage != null) "🎯 Đang huấn luyện…" else "🎯 Fine-tune (${pickerHud.userAnnotatedBboxes.size} nhãn)",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E88E5))
                                .clickable {
                                    pickerHud.runDeepForestFineTune(
                                        cfg = satCfg,
                                        epochs = 6,
                                        scope = scope,
                                        onDone = { msg -> c.toast(msg) },
                                    )
                                }
                                .padding(horizontal = 8.dp, vertical = 3.5.dp),
                        )
                    }
                }
                val openRing = Geo.open(pts)
                val edgeDistances = if (openRing.size < 2) emptyList()
                else if (openRing.size == 2) listOf(Triple(1, 2, Geo.distanceM(openRing[0], openRing[1])))
                else openRing.indices.map { i ->
                    val j = (i + 1) % openRing.size
                    Triple(i + 1, j + 1, Geo.distanceM(openRing[i], openRing[j]))
                }
                val perimeterM = edgeDistances.sumOf { it.third }
                val saoTayNguyen = fmtNum(area * 10.0, 1)
                val m2Text = fmtInt((area * 10000.0).toInt())
                if (edgeDistances.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (openRing.size >= 3) "${AppStrings.tr("📏 Chu vi:")} ${fmtNum(perimeterM, 1)} m"
                            else "${AppStrings.tr("📏 Khoảng cách:")} ${fmtNum(perimeterM, 1)} m",
                            color = Color(0xFF07150D),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFFD54F))
                                .padding(horizontal = 7.dp, vertical = 2.5.dp),
                        )
                        edgeDistances.forEach { (fromIdx, toIdx, distM) ->
                            Text(
                                "${AppStrings.tr("Đỉnh")} $fromIdx→$toIdx: ${fmtNum(distM, 1)} m",
                                color = VC.text,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(VC.elevated)
                                    .padding(horizontal = 7.dp, vertical = 2.5.dp),
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (pts.isEmpty()) AppStrings.tr("Chạm bản đồ để chấm góc vườn (≥3 đỉnh).")
                        else if (problem != null && pts.size >= 3) "${pts.size} ${AppStrings.tr("góc")} · $problem"
                        else "${pts.size} ${AppStrings.tr("góc vườn")}" + if (pts.size >= 3) " · ${fmtNum(area, 2)} ha (~$saoTayNguyen ${AppStrings.tr("sào")} · $m2Text m²)" else " · ${AppStrings.tr("Chấm thêm 1 đỉnh để khép kín diện tích")}",
                        color = if (problem != null && pts.size >= 3) VC.red else VC.text,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 6.dp),
                    )
                    if (keyOk && pts.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                AppStrings.tr("↶ Hoàn tác"),
                                color = VC.text,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(VC.elevated)
                                    .clickable { presetSource = null; pts.removeAt(pts.lastIndex) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                            Text(
                                AppStrings.tr("🗑 Xóa"),
                                color = VC.red,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(VC.red.copy(alpha = 0.10f))
                                    .clickable { presetSource = null; pts.clear() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (isDialog) {
                        VButton("Hủy", onDismiss, modifier = Modifier.weight(1f), primary = false, icon = Icons.Filled.Close)
                    } else {
                        VButton("Về Vườn", onDismiss, modifier = Modifier.weight(1f), primary = false)
                    }
                    VButton(if (isDialog) "Lưu vùng" else "Lưu vùng vườn", {
                        val bad = boundaryProblem(pts)
                        if (bad != null) { c.toast(bad); return@VButton }
                        val src = presetSource?.let { "Tọa độ thực tế VN: $it" }
                            ?: if (keyOk) "Vẽ tay trên bản đồ vệ tinh Google + GEE HD" else "Nhập tọa độ bằng tay"
                        val f = GeoFeature(newId(), FeatureKind.BOUNDARY, "Ranh vườn (chọn trên bản đồ)", pts.toList(), source = src)
                        c.store.applyGeoImport(listOf(f), emptyList(), "vùng chọn tay")
                        c.mapAnalysisSession.zone.value = ""
                        c.mapAnalysisSession.regionId.value = f.id
                        c.mapAnalysisSession.mode.value = MapMode.GEO
                        c.toast("Đã lưu ranh vườn trên Google Map: ${fmtNum(Geo.polygonAreaHa(pts), 2)} ha.")
                        onSaved()
                        if (isDialog) onDismiss()
                    }, modifier = Modifier.weight(1.35f), icon = Icons.Filled.Check, enabled = problem == null)
                }
            }
        }
}

@Composable
private fun Vertex(i: Int, p: GeoPoint, onMoved: (GeoPoint) -> Unit) {
    val state = rememberMarkerState(key = "v$i", position = LatLng(p.lat, p.lng))
    LaunchedEffect(state.dragState) {
        if (state.dragState == DragState.END) onMoved(GeoPoint(state.position.latitude, state.position.longitude))
    }
    Marker(state = state, draggable = true, title = "Đỉnh ${i + 1}")
}

/** Ranh vườn dùng cho phân tích vệ tinh. */
object SatelliteBoundary {
    fun of(d: com.example.test_tablet.data.vwing.GardenData): List<GeoPoint>? = SatelliteClient.ringOf(d)
}
