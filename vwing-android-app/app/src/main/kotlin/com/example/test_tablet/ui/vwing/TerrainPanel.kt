package com.example.test_tablet.ui.vwing

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

private enum class TerrainMode(val label: String, val blurb: String) {
    VIEW("Tầm nhìn", "Từ một điểm quan sát (chòi canh, camera, tháp), nhìn thấy được những ô nào, có tính độ cong Trái Đất và khúc xạ. Cho biết cây nào nằm trong tầm nhìn."),
    FLOOD("Nhạy cảm ngập lụt", "Chỉ số địa hình tương đối (TWI + độ cao so với dòng chảy gần nhất) cho biết chỗ nào dễ đọng/tụ nước hơn. KHÔNG phải mô hình thủy lực và không dự báo mức ngập."),
}

private const val GARDEN_OBS = "Tâm vùng vườn"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TerrainPanel(c: VCtx, region: GeoFeature?, problem: String?, onOverlay: (MapOverlay?) -> Unit) {
    val context = LocalContext.current
    val cfg = remember { SatelliteSettings(context.filesDir).load() }
    val places = rememberPlaces()
    var mode by rememberSaveable { mutableStateOf(TerrainMode.VIEW) }
    var buffer by rememberSaveable { mutableStateOf(1000) }
    var obsHeight by rememberSaveable { mutableStateOf(2) }
    var targetHeight by rememberSaveable { mutableStateOf(0) }
    var radius by rememberSaveable { mutableStateOf(1000) }
    var channelHa by rememberSaveable { mutableStateOf(3) }
    var observer by rememberSaveable { mutableStateOf(GARDEN_OBS) }
    var selectedSourceId by rememberSaveable { mutableStateOf(DemSourceKey.COPERNICUS_GLO30.id) }
    val selectedSource = DemSourceKey.fromId(selectedSourceId)
    var alpha by remember { mutableFloatStateOf(.7f) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val regionSig = listOf(region?.id, region?.points, region?.version)
    var dem by remember(regionSig, buffer, selectedSourceId) { mutableStateOf<Dem?>(null) }
    var out by remember(regionSig, buffer, mode, selectedSourceId) { mutableStateOf<ThemeOutput?>(null) }
    val currentOverlay by rememberUpdatedState(onOverlay)
    val currentSig by rememberUpdatedState(listOf(regionSig, buffer, mode, selectedSourceId))
    val scope = rememberCoroutineScope()
    val ring = region?.points?.toList() ?: emptyList()
    val centre = Geo.centroid(Geo.open(ring))

    LaunchedEffect(out, alpha) { currentOverlay(out?.let { MapOverlay(it.bounds, it.bitmap, alpha, it.label) }) }
    DisposableEffect(Unit) { onDispose { currentOverlay(null) } }

    Text(mode.blurb, color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
    ChipRow(TerrainMode.entries.toList(), mode, { it.label }, { mode = it; error = null }, Modifier.padding(top = 8.dp))

    // ---- dữ liệu độ cao (12 nguồn toàn cầu) ----
    VPicker(
        "Nguồn dữ liệu DEM (12 nguồn toàn cầu · elevatr / srtm.py / asf_search / eodag)",
        DemSourceKey.entries.map { it.fullTitle },
        selectedSource.fullTitle,
        { picked ->
            DemSourceKey.entries.firstOrNull { it.fullTitle == picked }?.let { selectedSourceId = it.id }
        },
        Modifier.padding(top = 10.dp),
    )
    Text(
        "Độ phân giải: ${selectedSource.resolutionLabel} · Phạm vi: ${selectedSource.coverage} · Công cụ: ${selectedSource.repoTool}",
        color = VC.mint,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
    Text("Vùng đệm quanh vườn để lấy bối cảnh địa hình/dòng chảy", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
    ChipRow(listOf(500, 1000, 2000), buffer, { "$it m" }, { buffer = it })
    if (!cfg.configured) Notice("Cần kết nối máy chủ vệ tinh (tab GIS & viễn thám → Kết nối vệ tinh) để tải độ cao.", VC.amber, Modifier.padding(top = 8.dp))
    VButton(if (busy && dem == null) "Đang tải ${selectedSource.shortLabel}…" else if (dem == null) "Tải địa hình (${selectedSource.shortLabel})" else "Tải lại (${selectedSource.shortLabel})", {
        val reg = region ?: return@VButton
        val sigAtStart = currentSig; val buf = buffer; val srcId = selectedSource.id
        busy = true; error = null; out = null
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { Terrain.fetchDem(cfg, reg.points.toList(), buf, srcId) }
                if (sigAtStart == currentSig) dem = r
            } catch (e: CancellationException) { throw e }
            catch (e: GisException) { error = e.message }
            catch (e: Exception) { error = "Không tải được địa hình: ${e.message ?: e.javaClass.simpleName}" }
            finally { busy = false }
        }
    }, enabled = !busy && problem == null && region != null && cfg.configured, primary = dem == null, modifier = Modifier.padding(top = 8.dp))
    dem?.let { d ->
        val ok = d.z.filter { !it.isNaN() }
        Text("${d.cols}×${d.rows} ô ≈ ${fmtNum(d.cellM, 0)} m · cao độ ${fmtNum(ok.min(), 0)}–${fmtNum(ok.max(), 0)} m · ${d.source}", color = VC.teal, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        if (d.simulated) Notice("DỮ LIỆU MÔ PHỎNG: địa hình giả để thử giao diện, không dùng để quyết định.", VC.amber, Modifier.padding(top = 6.dp))
        val (cells, fallback) = Terrain.regionCells(d, ring)
        if (cells.size < 30) Notice(
            if (fallback) "Vườn nhỏ hơn một ô 30 m nên kết quả lấy từ ô chứa tâm vườn; hãy xem như bối cảnh khu vực, không phải chi tiết trong vườn."
            else "Vườn chỉ chứa ${cells.size} ô độ cao (30 m/ô): thống kê trong vườn thô; bản đồ cho thấy bối cảnh xung quanh tốt hơn.", VC.amber, Modifier.padding(top = 6.dp))
    }

    // ---- tham số theo chế độ ----
    if (mode == TerrainMode.VIEW) {
        val selTree = c.selectedTree?.let { c.d.treeById(it) }?.takeIf { it.active && it.pos != null }
        val options = listOf(GARDEN_OBS) + listOfNotNull(selTree?.let { "Cây ${it.code} (đang chọn)" }) + places.list.map { it.name }.distinct()
        if (observer !in options) observer = GARDEN_OBS
        VPicker("Điểm quan sát", options, observer, { observer = it }, Modifier.padding(top = 10.dp))
        Text("Chiều cao mắt/camera trên mặt đất", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        ChipRow(listOf(2, 5, 10, 20), obsHeight, { "$it m" }, { obsHeight = it })
        Text("Vật cần nhìn thấy", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        ChipRow(listOf(0, 3), targetHeight, { if (it == 0) "Mặt đất" else "Tán cây 3 m" }, { targetHeight = it })
        Text("Bán kính", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        ChipRow(listOf(500, 1000, 2000), radius, { "$it m" }, { radius = it })
    } else {
        Text("Diện tích thượng nguồn để tính là dòng chảy", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
        ChipRow(listOf(1, 3, 10), channelHa, { "$it ha" }, { channelHa = it })
    }
    PlacesCard(c, places, centre)

    VButton(if (mode == TerrainMode.VIEW) "Tính tầm nhìn" else "Tính nhạy cảm ngập lụt", {
        val d = dem ?: return@VButton
        val sigAtStart = currentSig; val data = c.d; val md = mode
        val obsPoint = when {
            observer == GARDEN_OBS -> centre
            observer.startsWith("Cây ") -> c.selectedTree?.let { data.treeById(it) }?.pos
            else -> places.list.firstOrNull { it.name == observer }?.p
        }
        val oh = obsHeight.toDouble(); val th = targetHeight.toDouble(); val rad = radius.toDouble(); val ch = channelHa.toDouble()
        val obsName = observer
        busy = true; error = null; out = null
        scope.launch {
            try {
                val r = withContext(Dispatchers.Default) {
                    if (md == TerrainMode.VIEW) viewOutput(d, data, ring, obsPoint ?: error("Chưa xác định được điểm quan sát."), obsName, oh, th, rad)
                    else floodOutput(d, data, ring, ch)
                }
                if (sigAtStart == currentSig) out = r
            } catch (e: CancellationException) { throw e }
            catch (e: IllegalStateException) { error = e.message }
            catch (e: Exception) { error = "Không tính được: ${e.message ?: e.javaClass.simpleName}" }
            finally { busy = false }
        }
    }, enabled = !busy && dem != null, modifier = Modifier.padding(top = 10.dp))
    if (dem == null && cfg.configured && region != null) Text("Bấm \"Tải địa hình\" trước; sau đó đổi điểm quan sát hay chiều cao không cần tải lại.", color = VC.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))

    error?.let { Notice(it, VC.amber, Modifier.padding(top = 10.dp)) }
    out?.let { o -> ThemeOutputCard(c, o, alpha, { alpha = it }) }
}

private fun demNotes(d: Dem): List<String> = listOfNotNull(
    "Nguồn độ cao: ${d.source}, ô ≈ ${fmtNum(d.cellM, 0)} m. Là độ cao BỀ MẶT (gồm tán cây/nhà), sai số đứng vài mét; không thay khảo sát địa hình.",
    if (d.simulated) "DỮ LIỆU MÔ PHỎNG — không dùng để ra quyết định." else null,
) + d.warnings

private fun viewOutput(dem: Dem, data: GardenData, ring: List<GeoPoint>, observer: GeoPoint, obsName: String, obsH: Double, tgtH: Double, radiusM: Double): ThemeOutput {
    val vs = Terrain.viewshed(dem, observer, obsH, tgtH, radiusM)
        ?: error("Điểm quan sát nằm ngoài vùng độ cao đã tải hoặc ô đó thiếu dữ liệu. Chọn điểm khác hoặc tăng vùng đệm.")
    val g = dem.grid
    val (ox, oy) = g.centerXY(vs.observerCell)
    val px = IntArray(dem.z.size) { i ->
        when {
            i == vs.observerCell -> argb(255, 0xD6249F)
            dem.z[i].isNaN() -> 0
            vs.visible[i] -> argb(150, 0x1A9850)
            else -> g.centerXY(i).let { (x, y) -> if (sqrt((x - ox) * (x - ox) + (y - oy) * (y - oy)) <= radiusM) argb(95, 0x2B2B2B) else 0 }
        }
    }
    val (cells, fallback) = Terrain.regionCells(dem, ring)
    val inRadius = cells.filter { i -> g.centerXY(i).let { (x, y) -> sqrt((x - ox) * (x - ox) + (y - oy) * (y - oy)) <= radiusM } }
    if (inRadius.isEmpty()) error("Vườn nằm ngoài bán kính ${fmtNum(radiusM, 0)} m quanh điểm quan sát. Tăng bán kính hoặc chọn điểm gần vườn hơn.")
    val seen = inRadius.count { vs.visible[it] }
    val pct = 100.0 * seen / inRadius.size
    val trees = ThematicMaps.treesIn(data, ring)
    val treeIn = trees.filter { t -> g.toXY(t.p).let { (x, y) -> sqrt((x - ox) * (x - ox) + (y - oy) * (y - oy)) <= radiusM } }
    val treeSeen = treeIn.count { t -> g.cellOf(t.p)?.let { vs.visible[it] } == true }
    val legend = listOf(colorOf(argb(255, 0x1A9850)) to "Nhìn thấy", colorOf(argb(255, 0x6B6B6B)) to "Bị che (trong bán kính)", colorOf(argb(255, 0xD6249F)) to "Điểm quan sát")
    val details = listOfNotNull(
        "Quan sát từ \"$obsName\" cao ${fmtNum(obsH, 0)} m so với mặt đất (cao độ ô ${fmtNum(vs.observerZ, 0)} m), vật cần thấy cao ${fmtNum(tgtH, 0)} m, bán kính ${fmtNum(radiusM, 0)} m.",
        "Đã xét ${vs.analysedCells} ô, nhìn thấy ${vs.visibleCells} ô (${fmtNum(100.0 * vs.visibleCells / vs.analysedCells.coerceAtLeast(1), 0)}%). Có hiệu chỉnh độ cong Trái Đất và khúc xạ (k = ${Terrain.REFRACTION_K}).",
        "Ô 30 m không thấy được hàng cây, bờ nhỏ hay nhà thấp; tán cây và nhà có sẵn trong độ cao nên có thể che quá mức.",
        if (fallback) "Vườn nhỏ hơn một ô: dùng ô chứa tâm vườn." else null,
    ) + demNotes(dem)
    return ThemeOutput(dem.bounds, toBitmap(dem.cols, dem.rows, px), "Tầm nhìn từ $obsName", Verdict.UNKNOWN,
        "Từ điểm này nhìn thấy khoảng ${fmtNum(pct, 0)}% diện tích vườn",
        (if (dem.simulated) "DỮ LIỆU MÔ PHỎNG · " else "") + (if (treeIn.isNotEmpty()) "$treeSeen/${treeIn.size} cây trong bán kính nhìn thấy được · " else "") + "mắt cao ${fmtNum(obsH, 0)} m, bán kính ${fmtNum(radiusM, 0)} m.",
        legend, false, details)
}

private fun floodOutput(dem: Dem, data: GardenData, ring: List<GeoPoint>, channelHa: Double): ThemeOutput {
    val f = Terrain.flood(dem, channelHa)
    val g = dem.grid
    val px = IntArray(dem.z.size) { i ->
        when {
            f.channel[i] -> argb(235, 0x00B4D8)
            f.cls[i] == FloodClass.VERY_HIGH -> argb(215, 0x08306B)
            f.cls[i] == FloodClass.HIGH -> argb(175, 0x4F8FD6)
            f.cls[i] == FloodClass.MEDIUM -> argb(115, 0xBDD7EE)
            else -> 0
        }
    }
    val (cells, fallback) = Terrain.regionCells(dem, ring)
    val scored = cells.filter { f.cls[it] != null }
    if (scored.isEmpty()) error("Vườn nằm trên ô thiếu dữ liệu độ cao nên không tính được chỉ số.")
    val byClass = FloodClass.entries.associateWith { k -> scored.count { f.cls[it] == k } }
    val cellHa = g.cellHa
    val hiShare = 100.0 * ((byClass[FloodClass.HIGH] ?: 0) + (byClass[FloodClass.VERY_HIGH] ?: 0)) / scored.size
    val hands = scored.map { f.hand[it] }.filter { !it.isNaN() }
    val trees = ThematicMaps.treesIn(data, ring)
    val treeHigh = trees.count { t -> g.cellOf(t.p)?.let { f.cls[it] == FloodClass.HIGH || f.cls[it] == FloodClass.VERY_HIGH } == true }
    val legend = FloodClass.entries.reversed().map { k ->
        val col = when (k) { FloodClass.VERY_HIGH -> 0x08306B; FloodClass.HIGH -> 0x4F8FD6; FloodClass.MEDIUM -> 0xBDD7EE; FloodClass.LOW -> 0xF2F2F2 }
        colorOf(argb(255, col)) to "${k.label} (${ha((byClass[k] ?: 0) * cellHa)} trong vườn)"
    } + (colorOf(argb(255, 0x00B4D8)) to "Dòng chảy (≥ ${fmtNum(channelHa, 0)} ha thượng nguồn)")
    val verdict = if (hiShare >= 50) Verdict.BAD else if (hiShare >= 10) Verdict.WARN else Verdict.GOOD
    val headline = if (hiShare >= 10) "${fmtNum(hiShare, 0)}% diện tích vườn thuộc nhóm nhạy cảm ngập cao/rất cao" else "Phần lớn vườn không thuộc nhóm nhạy cảm ngập cao (${fmtNum(hiShare, 0)}%)"
    val details = listOfNotNull(
        "Chỉ số = 0,5 × xếp hạng TWI trong cửa sổ + 0,5 × (1 − HAND/10 m). Lớp: < 0,25 Thấp, < 0,5 Vừa, < 0,75 Cao, còn lại Rất cao. Ngưỡng cảnh báo (10% / 50%) là quy ước của app.",
        "TWI mang tính TƯƠNG ĐỐI trong cửa sổ đã tải: \"cao\" là cao hơn phần còn lại của khu vực này, đổi vùng đệm có thể đổi kết quả.",
        if (hands.isNotEmpty()) "Độ cao so với dòng chảy gần nhất (HAND) trong vườn: trung bình ${fmtNum(hands.average(), 1)} m, thấp nhất ${fmtNum(hands.min(), 1)} m." else null,
        if (trees.isNotEmpty()) "$treeHigh/${trees.size} cây nằm ở ô nhạy cảm cao/rất cao." else null,
        "Đã lấp ${f.filledCells} ô trũng để dòng chảy thoát; ô sát mép cửa sổ thiếu thượng nguồn nên chỉ số thấp hơn thực tế.",
        "KHÔNG phải mô hình thủy lực (không có mưa, đất, cống, đê, lòng sông thật); không dự báo mức hay thời điểm ngập. Đối chiếu với lịch sử ngập thực tế và cơ quan thủy văn.",
        if (fallback) "Vườn nhỏ hơn một ô: dùng ô chứa tâm vườn." else null,
    ) + demNotes(dem)
    return ThemeOutput(dem.bounds, toBitmap(dem.cols, dem.rows, px), "Nhạy cảm ngập lụt", verdict, headline,
        (if (dem.simulated) "DỮ LIỆU MÔ PHỎNG · " else "") + "Rất cao ${ha((byClass[FloodClass.VERY_HIGH] ?: 0) * cellHa)}, cao ${ha((byClass[FloodClass.HIGH] ?: 0) * cellHa)} trong vườn · chỉ số địa hình, không phải dự báo ngập.",
        legend, false, details)
}
