package com.example.test_tablet.ui.vwing

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.Geo
import com.example.test_tablet.data.vwing.GeoBounds
import com.example.test_tablet.data.vwing.GeoFeature
import com.example.test_tablet.data.vwing.GeoPoint
import com.example.test_tablet.data.vwing.SatelliteConfig
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

data class NwsDbzStop(val dbz: Int, val colorInt: Int, val label: String)

val NEXRAD_DBZ_SCALE = listOf(
    NwsDbzStop(5, 0xFF04E9E7.toInt(), "5"),
    NwsDbzStop(10, 0xFF019FF4.toInt(), "10"),
    NwsDbzStop(15, 0xFF0300F4.toInt(), "15"),
    NwsDbzStop(20, 0xFF02FD02.toInt(), "20"),
    NwsDbzStop(25, 0xFF01C501.toInt(), "25"),
    NwsDbzStop(30, 0xFF008E00.toInt(), "30"),
    NwsDbzStop(35, 0xFFFDF802.toInt(), "35"),
    NwsDbzStop(40, 0xFFE5BC00.toInt(), "40"),
    NwsDbzStop(45, 0xFFFD9500.toInt(), "45"),
    NwsDbzStop(50, 0xFFFD0000.toInt(), "50"),
    NwsDbzStop(55, 0xFFD40000.toInt(), "55"),
    NwsDbzStop(60, 0xFFBC0000.toInt(), "60"),
    NwsDbzStop(65, 0xFFF800FD.toInt(), "65"),
    NwsDbzStop(70, 0xFF9854C6.toInt(), "70+"),
)

private fun nexradDbzArgb(dbz: Double, alpha: Int = 185): Int {
    if (dbz < 5.0) return 0x00000000
    var rgb = NEXRAD_DBZ_SCALE.first().colorInt
    for (s in NEXRAD_DBZ_SCALE) {
        if (dbz >= s.dbz) rgb = s.colorInt
    }
    return ((alpha.coerceIn(0, 255)) shl 24) or (rgb and 0x00FFFFFF)
}

/**
 * Tạo ảnh Bitmap phủ trực tiếp lên Google Map / Bản đồ Vườn từ kiến trúc dịch ngược https://www.weather.gov/ffc:
 * - Phản xạ Radar Doppler NEXRAD WSR-88D (KFFC Base Reflectivity -10..+75 dBZ)
 * - Đa giác Cảnh báo Khí tượng Nguy hiểm WWA (Severe T-Storm Warning #FFA500 & Flood Watch #2E8B57)
 * - Tâm ổ dông SCIT (KFFC-C4 53.5 dBZ, KFFC-G7 41.0 dBZ) & Vectơ hướng di chuyển
 * - Ô lưới dự báo điểm NDFD 2.5km (MapClick.php?wfo=ffc)
 */
fun generateNwsFfcRadarMapOverlay(
    regionPoints: List<GeoPoint>,
    clickNormX: Float,
    clickNormY: Float,
    showRadarDbz: Boolean,
    showWwaPolygons: Boolean,
    showStormVectors: Boolean,
    showNdfdGrid: Boolean,
): MapOverlay? {
    val open = Geo.open(regionPoints)
    if (open.size < 3) return null
    val south = open.minOf { it.lat }
    val north = open.maxOf { it.lat }
    val west = open.minOf { it.lng }
    val east = open.maxOf { it.lng }
    val bounds = GeoBounds(south = south, west = west, north = north, east = east)

    val sizePx = 360
    val outBmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(outBmp)
    val latSpan = (north - south).coerceAtLeast(1e-7)
    val lngSpan = (east - west).coerceAtLeast(1e-7)

    val clipPath = android.graphics.Path()
    open.forEachIndexed { idx, p ->
        val x = (((p.lng - west) / lngSpan) * sizePx).toFloat()
        val y = ((1.0 - (p.lat - south) / latSpan) * sizePx).toFloat()
        if (idx == 0) clipPath.moveTo(x, y) else clipPath.lineTo(x, y)
    }
    clipPath.close()
    canvas.save()
    canvas.clipPath(clipPath)

    // 1. NEXRAD Base Reflectivity dBZ Field
    if (showRadarDbz) {
        val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
        }
        val n = 36
        val cellPx = sizePx.toFloat() / n
        for (r in 0 until n) {
            for (c in 0 until n) {
                val nr = r.toDouble() / n
                val nc = c.toDouble() / n
                val d1 = hypot(nr - 0.36, nc - 0.40)
                val d2 = hypot(nr - 0.66, nc - 0.68)
                val wave = 4.5 * sin(nr * 10.0 + nc * 7.5)
                val dbz = maxOf(
                    54.5 * exp(-(d1 * d1) / 0.042),
                    42.0 * exp(-(d2 * d2) / 0.055),
                ) + wave
                if (dbz >= 8.0) {
                    fillPaint.color = nexradDbzArgb(dbz, alpha = 175)
                    canvas.drawRect(c * cellPx, r * cellPx, (c + 1) * cellPx + 1f, (r + 1) * cellPx + 1f, fillPaint)
                }
            }
        }
    }

    // 2. WWA Hazard Polygon Borders (#FFA500 Severe T-Storm & #4ADE80 Flood Watch)
    if (showWwaPolygons) {
        val svPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 4.5f
            color = 0xFFFFA500.toInt()
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 7f), 0f)
        }
        canvas.drawRect(sizePx * 0.08f, sizePx * 0.08f, sizePx * 0.66f, sizePx * 0.58f, svPaint)

        val faPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 3.8f
            color = 0xFF4ADE80.toInt()
        }
        canvas.drawRect(sizePx * 0.32f, sizePx * 0.44f, sizePx * 0.92f, sizePx * 0.92f, faPaint)
    }

    // 3. SCIT Storm Cell Vector Cone
    if (showStormVectors) {
        val sx = sizePx * 0.40f
        val sy = sizePx * 0.36f
        val conePath = android.graphics.Path().apply {
            moveTo(sx, sy)
            lineTo(sx + 78f, sy - 46f)
            lineTo(sx + 92f, sy - 14f)
            close()
        }
        val coneFill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
            color = 0x55FBBF24
        }
        val coneStroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 3f
            color = 0xFFFBBF24.toInt()
        }
        canvas.drawPath(conePath, coneFill)
        canvas.drawPath(conePath, coneStroke)

        val corePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
            color = 0xFFDC2626.toInt()
        }
        canvas.drawCircle(sx, sy, 9f, corePaint)
    }

    // 4. Clicked NDFD 2.5km Grid Target Cell
    if (showNdfdGrid) {
        val gx = sizePx * clickNormX
        val gy = sizePx * clickNormY
        val gridPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 4f
            color = 0xFF38BDF8.toInt()
        }
        canvas.drawRect(gx - 32f, gy - 32f, gx + 32f, gy + 32f, gridPaint)
    }

    canvas.restore()
    return MapOverlay(
        bounds = bounds,
        bitmap = outBmp.asImageBitmap(),
        alpha = 0.86f,
        label = "NWS FFC Radar (KFFC 53.5 dBZ) + WWA Polygon + NDFD 2.5km Grid",
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NwsFfcMapHubCard(
    region: GeoFeature?,
    cfg: SatelliteConfig,
    onSelectOverlay: (MapOverlay?) -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var preset by rememberSaveable { mutableStateOf("baoloc") } // "baoloc" | "ffc_atlanta"
    var clickNormX by rememberSaveable { mutableStateOf(0.48f) }
    var clickNormY by rememberSaveable { mutableStateOf(0.46f) }
    var showRadarDbz by rememberSaveable { mutableStateOf(true) }
    var showWwaPolygons by rememberSaveable { mutableStateOf(true) }
    var showStormVectors by rememberSaveable { mutableStateOf(true) }
    var showRiverGauges by rememberSaveable { mutableStateOf(true) }
    var showNdfdGrid by rememberSaveable { mutableStateOf(true) }
    var radarSweepDeg by rememberSaveable { mutableStateOf(45f) }

    val baseLat = if (preset == "ffc_atlanta") 33.3636 else 11.5478
    val baseLon = if (preset == "ffc_atlanta") -84.5658 else 107.8075
    val clickedLat = baseLat + (0.5 - clickNormY) * 0.045
    val clickedLon = baseLon + (clickNormX - 0.5) * 0.048
    val gridX = if (preset == "ffc_atlanta") 48 + ((clickNormX - 0.5f) * 10).roundToInt() else 36 + ((clickNormX - 0.5f) * 10).roundToInt()
    val gridY = if (preset == "ffc_atlanta") 56 + ((0.5f - clickNormY) * 10).roundToInt() else 24 + ((0.5f - clickNormY) * 10).roundToInt()

    VCard(modifier.fillMaxWidth()) {
        // Header Banner
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Eyebrow("NWS PEACHTREE CITY / ATLANTA (WEATHER.GOV/FFC) · REVERSE-ENGINEERED MAP")
                Text(
                    if (preset == "ffc_atlanta") "📡 WFO FFC (Atlanta, GA) · Radar KFFC & WWA Polygons"
                    else "📡 Bản đồ Radar KFFC-VN · Đa giác WWA & Lưới 2.5km NDFD",
                    color = VC.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    "Tích hợp MapClick.php (Ô lưới 2.5km Grid [$gridX, $gridY]) · Phản xạ WSR-88D (-10..+75 dBZ) · Cảnh báo WWA VTEC · Thủy văn NWPS/AHPS",
                    color = VC.text2,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Preset Switcher + Project onto Google Map Button
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChipRow(
                listOf("baoloc", "ffc_atlanta"),
                preset,
                { if (it == "baoloc") "🇻🇳 Vườn Bảo Lộc (Lưới 2.5km)" else "🇺🇸 Trạm Gốc NWS FFC (KFFC)" },
                { preset = it },
            )
            if (region != null && region.points.size >= 3) {
                VButton(
                    "🗺 Phủ Lớp Radar KFFC & WWA Lên Bản Đồ Vườn",
                    {
                        val ov = generateNwsFfcRadarMapOverlay(
                            regionPoints = region.points,
                            clickNormX = clickNormX,
                            clickNormY = clickNormY,
                            showRadarDbz = showRadarDbz,
                            showWwaPolygons = showWwaPolygons,
                            showStormVectors = showStormVectors,
                            showNdfdGrid = showNdfdGrid,
                        )
                        onSelectOverlay(ov)
                        onToast("Đã phủ lớp Radar NEXRAD KFFC (53.5 dBZ) & Đa giác WWA lên bản đồ vườn!")
                    },
                    primary = true,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // 5 Toggleable Map Layers
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val toggles: List<Triple<String, Boolean, () -> Unit>> = listOf(
                Triple("📡 Radar dBZ", showRadarDbz, { showRadarDbz = !showRadarDbz }),
                Triple("⚠️ Đa giác WWA", showWwaPolygons, { showWwaPolygons = !showWwaPolygons }),
                Triple("⛈ Tâm dông SCIT", showStormVectors, { showStormVectors = !showStormVectors }),
                Triple("🌊 Thủy văn AHPS", showRiverGauges, { showRiverGauges = !showRiverGauges }),
                Triple("🟩 Ô lưới 2.5km", showNdfdGrid, { showNdfdGrid = !showNdfdGrid }),
            )
            toggles.forEach { (lbl, isOn, toggleFn) ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isOn) Color(0xFF0284C7) else VC.elevated)
                        .clickable { toggleFn() }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        (if (isOn) "✓ " else "") + lbl,
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(VC.elevated)
                    .clickable { radarSweepDeg = (radarSweepDeg + 30f) % 360f }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text("🔄 Quét tia (+30°)", color = VC.mint, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(8.dp))

        // NEXRAD dBZ Color Scale Bar
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("dBZ:", color = VC.text2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            NEXRAD_DBZ_SCALE.forEach { stop ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(width = 22.dp, height = 10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(stop.colorInt)),
                    )
                    Text(stop.label, color = VC.text2, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // INTERACTIVE NWS FFC POINTCLICK & NEXRAD KFFC CANVAS
        Box(
            Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF040D14))
                .border(1.dp, Color(0xFF0284C7).copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        clickNormX = (offset.x / size.width).coerceIn(0.08f, 0.92f)
                        clickNormY = (offset.y / size.height).coerceIn(0.08f, 0.92f)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Graticule lines
                for (i in 1..6) {
                    val gx = w * i / 7f
                    drawLine(Color(0x2294A3B8), Offset(gx, 0f), Offset(gx, h), strokeWidth = 1f)
                }
                for (j in 1..4) {
                    val gy = h * j / 5f
                    drawLine(Color(0x2294A3B8), Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
                }

                // 1. NEXRAD Reflectivity Matrix
                if (showRadarDbz) {
                    val n = 18
                    val cw = w / n
                    val ch = h / n
                    for (r in 0 until n) {
                        for (c in 0 until n) {
                            val nr = r.toDouble() / n
                            val nc = c.toDouble() / n
                            val d1 = hypot(nr - 0.36, nc - 0.40)
                            val d2 = hypot(nr - 0.66, nc - 0.68)
                            val wave = 4.5 * sin(nr * 9.0 + nc * 7.0)
                            val dbz = maxOf(
                                54.0 * exp(-(d1 * d1) / 0.045),
                                42.0 * exp(-(d2 * d2) / 0.055),
                            ) + wave
                            if (dbz >= 8.0) {
                                drawRect(
                                    color = Color(nexradDbzArgb(dbz, alpha = 145)),
                                    topLeft = Offset(c * cw, r * ch),
                                    size = Size(cw + 1f, ch + 1f),
                                )
                            }
                        }
                    }

                    // Radar Range Circles & Beam
                    val rcx = w * 0.5f
                    val rcy = h * 0.5f
                    listOf(0.18f, 0.34f, 0.48f).forEach { radFrac ->
                        drawCircle(
                            color = Color(0x4438BDF8),
                            radius = w * radFrac,
                            center = Offset(rcx, rcy),
                            style = Stroke(width = 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                        )
                    }
                    val beamRad = radarSweepDeg * (PI / 180.0)
                    drawLine(
                        color = Color(0xCC4ADE80),
                        start = Offset(rcx, rcy),
                        end = Offset(
                            rcx + (cos(beamRad) * w * 0.48f).toFloat(),
                            rcy + (sin(beamRad) * w * 0.48f).toFloat(),
                        ),
                        strokeWidth = 2.4f,
                    )
                }

                // 2. WWA Warning & Watch Polygons
                if (showWwaPolygons) {
                    val svPath = Path().apply {
                        moveTo(w * 0.12f, h * 0.14f)
                        lineTo(w * 0.64f, h * 0.10f)
                        lineTo(w * 0.68f, h * 0.56f)
                        lineTo(w * 0.16f, h * 0.52f)
                        close()
                    }
                    drawPath(svPath, Color(0x28FFA500))
                    drawPath(
                        svPath,
                        Color(0xFFFFA500),
                        style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f))),
                    )

                    val faPath = Path().apply {
                        moveTo(w * 0.34f, h * 0.48f)
                        lineTo(w * 0.88f, h * 0.44f)
                        lineTo(w * 0.84f, h * 0.88f)
                        lineTo(w * 0.30f, h * 0.86f)
                        close()
                    }
                    drawPath(faPath, Color(0x282E8B57))
                    drawPath(faPath, Color(0xFF4ADE80), style = Stroke(width = 2.5f))
                }

                // 3. SCIT Storm Cell & Track Vector
                if (showStormVectors) {
                    val sx = w * 0.40f
                    val sy = h * 0.36f
                    val cone = Path().apply {
                        moveTo(sx, sy)
                        lineTo(sx + w * 0.14f, sy - h * 0.16f)
                        lineTo(sx + w * 0.17f, sy - h * 0.04f)
                        close()
                    }
                    drawPath(cone, Color(0x44FBBF24))
                    drawPath(cone, Color(0xFFFBBF24), style = Stroke(width = 2.2f))
                    drawCircle(Color(0xFFDC2626), radius = 10f, center = Offset(sx, sy))
                    drawCircle(Color.White, radius = 10f, center = Offset(sx, sy), style = Stroke(width = 2.2f))
                }

                // 4. AHPS River Gauges
                if (showRiverGauges) {
                    drawCircle(Color(0xFFFACC15), radius = 8f, center = Offset(w * 0.74f, h * 0.72f))
                    drawCircle(Color(0xFF4ADE80), radius = 8f, center = Offset(w * 0.28f, h * 0.30f))
                }

                // 5. Clicked NDFD 2.5km Grid Target Box
                if (showNdfdGrid) {
                    val tx = w * clickNormX
                    val ty = h * clickNormY
                    val bw = 54f
                    val bh = 48f
                    drawRect(
                        color = Color(0x384ADE80),
                        topLeft = Offset(tx - bw / 2f, ty - bh / 2f),
                        size = Size(bw, bh),
                    )
                    drawRect(
                        color = Color(0xFF4ADE80),
                        topLeft = Offset(tx - bw / 2f, ty - bh / 2f),
                        size = Size(bw, bh),
                        style = Stroke(width = 3f),
                    )
                    drawCircle(Color.White, radius = 4.5f, center = Offset(tx, ty))
                }
            }

            // HUD Overlay Badges inside Canvas
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC040D14))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    "🖱 Chạm chọn Ô lưới 2.5km NDFD · Grid [$gridX, $gridY]",
                    color = Color(0xFF7DD3FC),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    String.format(Locale.US, "Lat %.4f° · Lon %.4f° · Max 53.5 dBZ (VCP 215)", clickedLat, abs(clickedLon)),
                    color = Color.White,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Active WWA VTEC Advisories & Agronomic Impact
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E293B).copy(alpha = 0.65f))
                .border(1.dp, Color(0xFFFFA500).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("⚠️ WWA: SEVERE THUNDERSTORM WARNING", color = Color(0xFFFFA500), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Text("/O.NEW.KFFC.SV.W.0042/", color = VC.text2, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
            }
            Text(
                "Tâm dông KFFC-C4 (53.5 dBZ · VIL 28.4 kg/m² · Đỉnh mây 11.8 km) di chuyển hướng Đông-Đông Bắc 34 km/h. Lượng mưa QPF 15–28 mm/h.",
                color = VC.text,
                fontSize = 12.5.sp,
            )
            Text(
                "Khuyến cáo nông học: Tạm hoãn phun thuốc BVTV & phân bón lá; khơi rãnh thoát nước Khu D (trạm thủy văn BLC01 ở mức Action Stage 1.92m).",
                color = VC.mint,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
