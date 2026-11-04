package com.example.test_tablet.ui.vwing

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Các điểm quan trọng đã lưu (dùng chung giữa các panel): đọc một lần, mọi thay đổi ghi xuống tệp ngay. */
class PlacesState(private val store: PlaceStore) {
    val list = mutableStateListOf<Place>().also { it.addAll(store.load()) }
    fun add(p: Place): String? {
        if (list.size >= PlaceStore.MAX) return "Tối đa ${PlaceStore.MAX} điểm. Xoá bớt trước khi thêm."
        if (list.any { it.name == p.name && Geo.distanceM(it.p, p.p) < 20 }) return "Điểm này đã có trong danh sách."
        list.add(p); store.save(list.toList()); return null
    }
    fun remove(p: Place) { list.remove(p); store.save(list.toList()) }
}

@Composable
internal fun rememberPlaces(): PlacesState {
    val dir = LocalContext.current.filesDir
    return remember { PlacesState(PlaceStore(dir)) }
}

/** Danh sách điểm quan trọng + thêm bằng tìm địa điểm hoặc vị trí hiện tại. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlacesCard(c: VCtx, places: PlacesState, near: GeoPoint?) {
    var msg by remember { mutableStateOf<String?>(null) }
    val here = rememberLocationFetcher(c) { la, lo, _ -> msg = places.add(Place("Vị trí hiện tại", GeoPoint(la, lo))) }
    Text("Điểm quan trọng đã lưu", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
    Text("Chợ, kho, nhà máy, bệnh viện, trạm cứu hỏa… dùng làm điểm xuất phát hoặc điểm đến. Lưu trên máy này.", color = VC.muted, fontSize = 13.sp)
    if (places.list.isEmpty()) Text("Chưa có điểm nào.", color = VC.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
    places.list.toList().forEach { p ->
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("${p.name} (${fmtNum(p.p.lat, 4)}, ${fmtNum(p.p.lng, 4)})", color = VC.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("Xoá", color = VC.red, fontSize = 14.sp, modifier = Modifier.clickable { places.remove(p) }.padding(8.dp))
        }
    }
    PlaceSearchBox(near, { pl -> msg = places.add(Place(pl.name.ifBlank { pl.label }.take(60), GeoPoint(pl.lat, pl.lng))) }, Modifier.padding(top = 8.dp), "Thêm điểm: tìm địa điểm")
    VButton("Thêm vị trí hiện tại", { here() }, primary = false, modifier = Modifier.padding(top = 6.dp))
    msg?.let { Notice(it, VC.amber, Modifier.padding(top = 6.dp)) }
}

private enum class AccessMode(val label: String, val blurb: String) {
    SERVICE("Vùng phục vụ", "Vùng có thể đi tới trong 5–60 phút THEO MẠNG LƯỚI ĐƯỜNG từ một điểm (không phải đường chim bay): từ vườn ra ngoài, hoặc vùng phục vụ của chợ/kho/bệnh viện."),
    FLOW("Dòng chảy điểm đi–đến", "Nối từng khu của vườn với điểm đến gần nhất theo thời gian đường; độ dày nét theo khối lượng (quả hoặc số cây)."),
}

private val PRESETS = listOf(listOf(5, 10, 15), listOf(10, 20, 30), listOf(15, 30, 45), listOf(10, 30, 60))
private val BAND_COLORS = longArrayOf(0x1A9850, 0xA6D96A, 0xFEE08B, 0xFDAE61, 0xD73027)
private const val GARDEN = "Tâm vườn"
private const val ALL = "Tất cả điểm đã lưu"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccessPanel(c: VCtx, region: GeoFeature?, problem: String?, onOverlay: (MapOverlay?) -> Unit) {
    val context = LocalContext.current
    val cfg = remember { SatelliteSettings(context.filesDir).load() }
    val places = rememberPlaces()
    var mode by rememberSaveable { mutableStateOf(AccessMode.SERVICE) }
    var profile by rememberSaveable { mutableStateOf(TravelProfile.SCOOTER) }
    var preset by rememberSaveable { mutableStateOf(1) }
    var origin by rememberSaveable { mutableStateOf(GARDEN) }
    var alpha by remember { mutableFloatStateOf(.7f) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val signature = listOf(region?.id, region?.points, region?.version, mode)
    var out by remember(signature) { mutableStateOf<ThemeOutput?>(null) }
    val currentOverlay by rememberUpdatedState(onOverlay)
    val currentSignature by rememberUpdatedState(signature)
    val scope = rememberCoroutineScope()
    val ring = region?.points?.toList() ?: emptyList()
    val centre = Geo.centroid(Geo.open(ring))

    LaunchedEffect(out, alpha) { currentOverlay(out?.let { MapOverlay(it.bounds, it.bitmap, alpha, it.label) }) }
    DisposableEffect(Unit) { onDispose { currentOverlay(null) } }

    Text(mode.blurb, color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
    ChipRow(AccessMode.entries.toList(), mode, { it.label }, { mode = it; error = null }, Modifier.padding(top = 8.dp))
    Text("Phương tiện", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
    ChipRow(TravelProfile.entries.toList(), profile, { it.label }, { profile = it })
    if (!cfg.configured) Notice("Cần kết nối máy chủ vệ tinh (tab GIS & viễn thám → Kết nối vệ tinh) để lấy dữ liệu đường.", VC.amber, Modifier.padding(top = 8.dp))

    if (mode == AccessMode.SERVICE) {
        Text("Mốc thời gian (phút)", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        ChipRow(PRESETS.indices.toList(), preset, { PRESETS[it].joinToString(" · ") }, { preset = it })
        val options = listOf(GARDEN) + places.list.map { it.name }.distinct() + (if (places.list.size > 1) listOf(ALL) else emptyList())
        if (origin !in options) origin = GARDEN
        VPicker("Điểm xuất phát", options, origin, { origin = it }, Modifier.padding(top = 8.dp))
    }
    PlacesCard(c, places, centre)

    val ready = !busy && problem == null && region != null && cfg.configured
    VButton(if (busy) "Đang tính…" else if (mode == AccessMode.SERVICE) "Tạo vùng phục vụ" else "Tính dòng chảy", {
        val reg = region ?: return@VButton
        val snapshot = signature; val data = c.d; val prof = profile; val ctr = centre ?: return@VButton
        val saved = places.list.toList(); val bands = PRESETS[preset]; val org = origin; val md = mode
        busy = true; error = null; out = null
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) {
                    if (md == AccessMode.SERVICE) serviceArea(cfg, data, reg.points.toList(), ctr, saved, org, prof, bands)
                    else odFlow(cfg, data, reg.points.toList(), saved, prof)
                }
                if (snapshot == currentSignature) out = r
            } catch (e: CancellationException) { throw e }
            catch (e: GisException) { error = e.message }
            catch (e: IllegalStateException) { error = e.message }
            catch (e: Exception) { error = "Không tính được: ${e.message ?: e.javaClass.simpleName}" }
            finally { busy = false }
        }
    }, enabled = ready, modifier = Modifier.padding(top = 10.dp))
    Text("Toạ độ các điểm được gửi tới dịch vụ định tuyến (mặc định là máy chủ cộng đồng OpenStreetMap qua máy chủ vệ tinh của bạn).", color = VC.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))

    error?.let { Notice(it, VC.amber, Modifier.padding(top = 10.dp)) }
    out?.let { o -> ThemeOutputCard(c, o, alpha, { alpha = it }) }
}

private fun bandColor(index: Int, n: Int): Long = BAND_COLORS[if (n <= 1) 0 else (index * (BAND_COLORS.size - 1)) / (n - 1)]

private fun serviceArea(cfg: SatelliteConfig, d: GardenData, ring: List<GeoPoint>, centre: GeoPoint, saved: List<Place>, origin: String,
                        profile: TravelProfile, bands: List<Int>): ThemeOutput {
    val gardenOrigin = origin == GARDEN
    val origins = when {
        gardenOrigin -> listOf(Place(GARDEN, centre))
        origin == ALL -> saved.take(RoutingClient.MAX_ORIGINS).also { if (saved.size > RoutingClient.MAX_ORIGINS) error("Chọn tối đa ${RoutingClient.MAX_ORIGINS} điểm: xoá bớt điểm đã lưu hoặc chọn từng điểm.") }
        else -> saved.filter { it.name == origin }.take(1)
    }
    if (origins.isEmpty()) error("Chưa chọn được điểm xuất phát.")
    val res = RoutingClient.isochrone(cfg, origins, profile, bands)
    val raster = RoutingClient.raster(res.areas, if (res.areas.size > 1) 200 else 256) ?: error("Không dựng được bản đồ vùng phục vụ.")
    val px = IntArray(raster.minutes.size) { i ->
        val m = raster.minutes[i]
        if (m == 0) 0 else argb(185, bandColor(bands.indexOf(m).coerceAtLeast(0), bands.size))
    }
    val legend = bands.mapIndexed { i, m -> colorOf(argb(255, bandColor(i, bands.size))) to "≤ $m phút" }
    val trees = ThematicMaps.treesIn(d, ring)
    val (byBand, outside) = RoutingClient.treeCoverage(res.areas, trees, bands)
    val cov = RoutingClient.areaCoverage(res.areas, ring, bands)
    val details = ArrayList<String>()
    res.areas.forEach { a -> details += "${a.name}: " + a.contours.joinToString(" · ") { "${it.minutes} phút = ${fmtNum(it.areaHa, 0)} ha" } }
    if (trees.isNotEmpty() && !gardenOrigin) details += "Cây trong vùng: " + bands.joinToString(" · ") { "≤ $it phút: ${byBand[it]}/${trees.size}" } + (if (outside > 0) " · ngoài vùng: $outside" else "")
    details += "Phương tiện: ${profile.label}. ${res.caution}"
    details += "Nguồn: ${res.attribution.ifBlank { "OpenStreetMap" }}. Vùng đã bỏ lỗ thủng nhỏ, coi như liền khối."
    val maxBand = bands.last()
    val (verdict, headline, sub) = if (gardenOrigin) {
        val a = res.areas[0].contours.last()
        Triple(Verdict.GOOD, "Từ vườn, $maxBand phút ${profile.label.lowercase()} tới được ${ha(a.areaHa)}",
            res.areas[0].contours.joinToString(" · ") { "${it.minutes} phút: ${fmtNum(it.areaHa, 0)} ha" })
    } else {
        val best = res.areas.mapNotNull { a -> RoutingClient.minutesTo(a, centre)?.let { a.name to it } }.minByOrNull { it.second }
        if (best != null) Triple(Verdict.GOOD, "Vườn nằm trong vùng ≤ ${best.second} phút của ${best.first}",
            cov?.get(maxBand)?.let { "Khoảng ${fmtNum(it, 0)}% diện tích vườn nằm trong vùng ≤ $maxBand phút." } ?: "Theo ${profile.label.lowercase()}.")
        else Triple(Verdict.WARN, "Vườn nằm ngoài vùng $maxBand phút của ${if (origins.size == 1) origins[0].name else "các điểm đã chọn"}",
            "Thử tăng mốc thời gian hoặc đổi phương tiện.")
    }
    return ThemeOutput(raster.bounds, toBitmap(raster.cols, raster.rows, px), "Vùng phục vụ · ${profile.label}", verdict, headline, sub, legend, false, details)
}

private fun odFlow(cfg: SatelliteConfig, d: GardenData, ring: List<GeoPoint>, saved: List<Place>, profile: TravelProfile): ThemeOutput {
    if (saved.isEmpty()) error("Thêm ít nhất một điểm đến (chợ, kho, nhà máy…) ở danh sách điểm quan trọng.")
    val z = OdFlow.zones(d, ring)
    if (z.nodes.isEmpty()) error("Chưa có khu nào có cây gắn toạ độ trong vùng này.")
    val nodes = z.nodes.take(RoutingClient.MAX_MATRIX)
    val dests = saved.take(RoutingClient.MAX_MATRIX)
    val m = RoutingClient.matrix(cfg, nodes.map { Place(it.name, it.p) }, dests, profile)
    val edges = OdFlow.allocate(nodes, dests, m)
    if (edges.isEmpty()) error("Không có đường nối từ các khu tới điểm đến nào (điểm có thể nằm xa đường hoặc ngoài vùng dữ liệu).")
    val bmp = renderFlows(edges, nodes, dests)
    val total = edges.sumOf { it.weight }
    val avgMin = edges.sumOf { it.weight * it.minutes } / total
    val longest = edges.maxBy { it.minutes }
    val unit = z.unit
    val lines = edges.sortedByDescending { it.weight }.map { "${it.from.name} → ${it.to.name}: ${fmtNum(it.weight, 0)} $unit · ${fmtNum(it.minutes, 0)} phút · ${fmtNum(it.km, 1)} km" }
    val unreachable = nodes.size - edges.size
    val details = lines + listOfNotNull(
        "GIẢ ĐỊNH: mỗi khu chở toàn bộ khối lượng về điểm đến có thời gian đường NGẮN NHẤT; chưa tính giá, công suất hay hợp đồng thật. Khối lượng = tổng $unit đã ghi ở các cây có toạ độ.",
        "Thời gian/quãng đường theo mạng đường OSM (${profile.label.lowercase()}), không tính kẹt xe.",
        if (unreachable > 0) "$unreachable khu không tới được điểm đến nào nên bị bỏ." else null,
        if (z.skippedZones > 0) "${z.skippedZones} khu bị bỏ vì chưa có cây nào gắn toạ độ trong vùng." else null,
        if (z.nodes.size > nodes.size || saved.size > dests.size) "Chỉ dùng tối đa ${RoutingClient.MAX_MATRIX} khu và ${RoutingClient.MAX_MATRIX} điểm đến." else null,
        "Nguồn: ${m.attribution.ifBlank { "OpenStreetMap" }}.")
    val legend = listOf(colorOf(argb(255, 0x1A9850)) to "Khu (điểm đi)", colorOf(argb(255, 0x1F5FBF)) to "Điểm đến", colorOf(argb(255, 0xE8590C)) to "Tuyến (dày = nhiều $unit)")
    return ThemeOutput(bmp.first, bmp.second, "Dòng chảy điểm đi–đến · ${profile.label}", Verdict.UNKNOWN,
        "${fmtNum(total, 0)} $unit đi trên ${edges.size} tuyến, trung bình ${fmtNum(avgMin, 0)} phút",
        "Tuyến xa nhất: ${longest.from.name} → ${longest.to.name} (${fmtNum(longest.minutes, 0)} phút, ${fmtNum(longest.km, 1)} km).", legend, false, details)
}

/** Vẽ các tuyến cong (độ dày theo khối lượng), khu (tròn xanh) và điểm đến (vuông xanh dương) lên một bitmap phủ bản đồ. */
private fun renderFlows(edges: List<OdEdge>, nodes: List<OdNode>, dests: List<Place>): Pair<GeoBounds, androidx.compose.ui.graphics.ImageBitmap> {
    val pts = nodes.map { it.p } + dests.map { it.p }
    var south = pts.minOf { it.lat }; var north = pts.maxOf { it.lat }; var west = pts.minOf { it.lng }; var east = pts.maxOf { it.lng }
    val padLat = max((north - south) * .12, .003); val padLng = max((east - west) * .12, .003)
    south -= padLat; north += padLat; west -= padLng; east += padLng
    val b = GeoBounds(max(-90.0, south), max(-180.0, west), min(90.0, north), min(180.0, east))
    val wM = Geo.distanceM(GeoPoint(b.center.lat, b.west), GeoPoint(b.center.lat, b.east)); val hM = Geo.distanceM(GeoPoint(b.south, b.center.lng), GeoPoint(b.north, b.center.lng))
    val size = 640
    val cols = if (wM >= hM) size else max(64, (size * wM / hM).toInt()); val rows = if (wM >= hM) max(64, (size * hM / wM).toInt()) else size
    val bmp = android.graphics.Bitmap.createBitmap(cols, rows, android.graphics.Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    fun x(p: GeoPoint) = ((p.lng - b.west) / (b.east - b.west) * cols).toFloat()
    fun y(p: GeoPoint) = ((b.north - p.lat) / (b.north - b.south) * rows).toFloat()
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = argb(205, 0xE8590C) }
    val maxW = edges.maxOf { it.weight }
    edges.sortedByDescending { it.weight }.forEach { e ->
        val x1 = x(e.from.p); val y1 = y(e.from.p); val x2 = x(e.to.p); val y2 = y(e.to.p)
        val mx = (x1 + x2) / 2; val my = (y1 + y2) / 2
        val dx = x2 - x1; val dy = y2 - y1; val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val path = Path().apply { moveTo(x1, y1); quadTo(mx - dy / len * len * .18f, my + dx / len * len * .18f, x2, y2) }
        line.strokeWidth = 4f + 22f * sqrt((e.weight / maxW).toFloat())
        cv.drawPath(path, line)
    }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = argb(255, 0xFFFFFF) }
    nodes.forEach { n -> fill.color = argb(255, 0x1A9850); cv.drawCircle(x(n.p), y(n.p), 11f, fill); cv.drawCircle(x(n.p), y(n.p), 11f, ring) }
    dests.forEach { dst -> fill.color = argb(255, 0x1F5FBF); cv.drawRect(x(dst.p) - 12f, y(dst.p) - 12f, x(dst.p) + 12f, y(dst.p) + 12f, fill); cv.drawRect(x(dst.p) - 12f, y(dst.p) - 12f, x(dst.p) + 12f, y(dst.p) + 12f, ring) }
    return b to bmp.asImageBitmap()
}
