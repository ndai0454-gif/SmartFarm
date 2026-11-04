package com.example.test_tablet.ui.vwing

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.Dates
import com.example.test_tablet.data.vwing.EvidenceSource
import com.example.test_tablet.data.vwing.Event
import com.example.test_tablet.data.vwing.EventType
import com.example.test_tablet.data.vwing.FeatureKind
import com.example.test_tablet.data.vwing.Geo
import com.example.test_tablet.data.vwing.GeoFeature
import com.example.test_tablet.data.vwing.GeoPoint
import com.example.test_tablet.data.vwing.GisBoundary
import com.example.test_tablet.data.vwing.GisClient
import com.example.test_tablet.data.vwing.GisExport
import com.example.test_tablet.data.vwing.GisZonal
import com.example.test_tablet.data.vwing.SatelliteConfig
import com.example.test_tablet.data.vwing.SatelliteSettings
import com.example.test_tablet.data.vwing.TreeBoundary
import com.example.test_tablet.data.vwing.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Tên tệp người dùng thấy khi chọn (uri của hộp chọn tệp thường chỉ là mã số, không có đuôi). */
fun displayNameOf(context: Context, uri: Uri): String =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull() ?: (uri.lastPathSegment ?: "tep")

private fun satelliteConfig(context: Context): SatelliteConfig = SatelliteSettings(context.filesDir).load()

/** Xuất bản đồ vườn sang KML, GPX, CSV, WKT hoặc GeoJSON bằng engine RustGIS trên máy chủ vệ tinh. */
@Composable
fun GisExportDialog(c: VCtx, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var format by remember { mutableStateOf("kml") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<GisExport?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val ex = pending; pending = null
        if (uri != null && ex != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(ex.bytes ?: ex.text.toByteArray()) } }
                .onSuccess { c.toast("Đã xuất ${ex.filename}."); onDismiss() }.onFailure { err = "Không ghi được tệp: ${it.message}" }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = VC.card,
        title = { Text("Xuất bản đồ vườn", color = VC.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Gồm ranh vườn, khu, đường, nguồn nước và vị trí cây. Chuyển đổi bằng RustGIS trên máy chủ vệ tinh.", color = VC.text2, fontSize = 14.sp)
                ChipRow(GisClient.EXPORT.map { it.first }, format, { f -> GisClient.EXPORT.first { it.first == f }.second }, { format = it })
                err?.let { Text(it, color = VC.red, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            VButton(if (busy) "Đang chuyển đổi…" else "Xuất", {
                busy = true; err = null
                val cfg = satelliteConfig(context); val gj = Geo.exportGeoJson(c.d)
                scope.launch {
                    val r = runCatching { withContext(Dispatchers.IO) { GisClient.export(cfg, gj, format, c.d.garden.name.ifBlank { "VWing" }) } }
                    busy = false
                    r.onSuccess { pending = it; save.launch(it.filename) }.onFailure { err = it.message ?: "Không xuất được." }
                }
            }, enabled = !busy)
        },
        dismissButton = { TextButton(onDismiss) { Text("Đóng", color = VC.text2) } },
    )
}

private fun fmtDist(m: Double) = if (m >= 1000) "${"%.1f".format(m / 1000)} km" else "${"%.0f".format(m)} m"

/** Hình ranh vườn (xanh) và các vùng đệm (vàng) để nhìn nhanh hình dạng. */
@Composable
private fun RingPreview(rings: List<Pair<List<GeoPoint>, Color>>) {
    val all = rings.flatMap { it.first }
    if (all.size < 3) return
    Canvas(Modifier.fillMaxWidth().height(170.dp).background(VC.elevated)) {
        val o = all[0]
        val m = rings.map { (pts, col) -> pts.map { Geo.toMeters(it, o) } to col }
        val xs = m.flatMap { it.first.map { p -> p.first } }; val ys = m.flatMap { it.first.map { p -> p.second } }
        val w = (xs.max() - xs.min()).coerceAtLeast(1.0); val h = (ys.max() - ys.min()).coerceAtLeast(1.0)
        val sc = minOf((size.width - 24f) / w, (size.height - 24f) / h).toFloat()
        fun at(p: Pair<Double, Double>) = Offset(12f + ((p.first - xs.min()) * sc).toFloat(), size.height - 12f - ((p.second - ys.min()) * sc).toFloat())
        m.reversed().forEach { (pts, col) ->
            val path = Path().apply { pts.forEachIndexed { i, p -> val q = at(p); if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) }; close() }
            drawPath(path, col.copy(alpha = .12f)); drawPath(path, col, style = Stroke(width = 3f))
        }
    }
}

/** Công cụ GIS cho ranh vườn: đối chiếu cây (ngay trên máy), phân tích ranh và thống kê raster (RustGIS trên máy chủ vệ tinh). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GisToolsCard(c: VCtx) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val d = c.d
    val ring = remember(d.features) { SatelliteBoundary.of(d) }
    val cfg = remember(c.nav.current) { satelliteConfig(context) }
    var report by remember { mutableStateOf<GisBoundary?>(null) }
    var zonal by remember { mutableStateOf<GisZonal?>(null) }
    var rasterName by remember { mutableStateOf<String?>(null) }
    var rasterBytes by remember { mutableStateOf<ByteArray?>(null) }
    var index by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var overlayRes by remember { mutableStateOf<Triple<GisClient.Overlay, String, List<List<GeoPoint>>>?>(null) }
    val check = remember(d.trees, ring) { ring?.let { TreeBoundary.check(d, it) } }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = displayNameOf(context, uri)
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            zonal = null
            if (bytes == null) err = "Không đọc được tệp."
            else if (GisClient.extOf(name) !in GisClient.RASTER_EXT) err = "Chỉ nhận ảnh raster .tif, .tiff hoặc .asc."
            else if (bytes.size > GisClient.MAX_BYTES) err = "Tệp lớn hơn 15 MB."
            else { rasterName = name; rasterBytes = bytes; err = null }
        }
    }

    VCard(Modifier.fillMaxWidth()) {
        Eyebrow("CÔNG CỤ GIS")
        Text("Kiểm tra ranh vườn và cây, tính vùng đệm, thống kê ảnh drone/vệ tinh trong ranh. Phần nặng chạy bằng RustGIS (mã nguồn mở) trên máy chủ vệ tinh.", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))

        if (ring == null) {
            Notice("Chưa có ranh vườn. Hãy chọn vùng vườn trên bản đồ hoặc đi bộ ghi ranh ở phần Vị trí & ranh vườn, rồi quay lại đây.", VC.amber)
        } else {
            // ---- đối chiếu cây với ranh (không cần mạng) ----
            SectionLabel("Cây so với ranh vườn")
            val ck = check
            if (ck != null) {
                when {
                    ck.placed == 0 -> VerdictBanner(Verdict.UNKNOWN, "Chưa có cây nào có vị trí", "Thêm vị trí cho cây (GPS hoặc chạm bản đồ) để đối chiếu với ranh.")
                    ck.outside.isEmpty() -> VerdictBanner(Verdict.GOOD, "Cả ${ck.placed} cây có vị trí đều nằm trong ranh vườn", if (ck.unplaced.isNotEmpty()) "${ck.unplaced.size} cây chưa có vị trí." else null)
                    ck.inside.isEmpty() && ck.outside.minOf { it.second } > 500 ->
                        VerdictBanner(Verdict.BAD, "Ranh vườn và vị trí cây cách nhau hàng km", "Không cây nào gần ranh (gần nhất ${fmtDist(ck.outside.minOf { it.second })}). Có thể ranh vẽ nhầm chỗ hoặc vị trí cây nhập sai — dữ liệu mẫu cũng gây ra tình trạng này.")
                    else -> VerdictBanner(Verdict.WARN, "${ck.outside.size} cây nằm ngoài ranh vườn", "Xa ranh hơn ${TreeBoundary.TOLERANCE_M.toInt()} m. Kiểm tra lại vị trí cây hoặc mở rộng ranh.")
                }
                if (ck.outside.isNotEmpty()) {
                    Text(ck.outside.take(8).joinToString("\n") { (t, m) -> "• ${t.code} (khu ${t.zone}) — cách ranh ${fmtDist(m)}" } + if (ck.outside.size > 8) "\n… và ${ck.outside.size - 8} cây nữa" else "",
                        color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                    VButton("Xem cây ngoài ranh trên bản đồ", { c.selectTree(ck.outside.first().first.id); c.nav.top("map") }, primary = false, modifier = Modifier.padding(top = 6.dp))
                }
                TechnicalDetails(c.simpleMode, Modifier.padding(top = 6.dp)) {
                    ck.byZone.forEach { z ->
                        Text("Khu ${z.zone}: ${z.total} cây · trong ranh ${z.inside} · ngoài ${z.outside} · chưa có vị trí ${z.unplaced}", color = VC.text2, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }

            // ---- phân tích ranh (RustGIS) ----
            SectionLabel("Phân tích ranh vườn")
            if (!cfg.configured) {
                Notice("Chưa kết nối máy chủ vệ tinh nên chưa dùng được phần dưới.", VC.amber, trailing = { VButton("Kết nối", { c.nav.push("satellite") }, primary = false) })
            }
            VButton(if (busy) "Đang phân tích…" else "Phân tích ranh vườn", {
                busy = true; err = null
                scope.launch {
                    val r = runCatching { withContext(Dispatchers.IO) { GisClient.boundary(cfg, ring, listOf(10.0, 30.0), 2.0) } }
                    busy = false
                    r.onSuccess { report = it }.onFailure { err = it.message }
                }
            }, enabled = !busy && cfg.configured, primary = false, modifier = Modifier.padding(top = 4.dp))
            report?.let { r ->
                if (!r.valid) {
                    VerdictBanner(Verdict.BAD, "Ranh vườn có lỗi hình học", r.issues.firstOrNull(), Modifier.padding(top = 8.dp))
                    r.issues.drop(1).forEach { Text("• $it", color = VC.red, fontSize = 14.sp) }
                } else {
                    VerdictBanner(Verdict.GOOD, "Ranh vườn hợp lệ · ${"%.2f".format(r.areaHa)} ha", "Chu vi khoảng ${"%.0f".format(r.perimeterM)} m.", Modifier.padding(top = 8.dp))
                    RingPreview(listOf(ring to VC.lime) + r.buffers.map { it.ring to VC.amber })
                    r.buffers.forEach { b ->
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            Text("Vùng đệm ${b.meters.toInt()} m: ${"%.2f".format(b.areaHa)} ha", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
                            VButton("Lưu vào bản đồ", {
                                c.store.applyGeoImport(listOf(GeoFeature(newId(), FeatureKind.ZONE, "Vùng đệm ${b.meters.toInt()} m", b.ring, note = "Tạo bằng RustGIS", source = "RustGIS buffer")), emptyList(), "vùng đệm")
                                c.toast("Đã lưu vùng đệm ${b.meters.toInt()} m.")
                            }, primary = false)
                        }
                    }
                    TechnicalDetails(c.simpleMode, Modifier.padding(top = 6.dp)) {
                        Text("${r.vertices} đỉnh · chu vi ${"%.1f".format(r.perimeterM)} m", color = VC.text2, fontSize = 13.sp)
                        r.simplified?.takeIf { it.size < r.vertices }?.let { sp ->
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                                Text("Giảm đỉnh: ${r.vertices} → ${sp.size}", color = VC.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
                                VButton("Dùng ranh đã giảm đỉnh", {
                                    c.store.applyGeoImport(listOf(GeoFeature(newId(), FeatureKind.BOUNDARY, "Ranh vườn (đã giảm đỉnh)", sp, source = "RustGIS simplify")), emptyList(), "giảm đỉnh")
                                    report = null; c.toast("Đã thay ranh vườn bằng bản ${sp.size} đỉnh.")
                                }, primary = false)
                            }
                        }
                    }
                }
            }

            // ---- phép toán giữa các khu (RustGIS) ----
            SectionLabel("Xử lý khu vực")
            val polys = remember(d.features) { d.features.filter { it.kind != FeatureKind.ROAD && Geo.open(it.points).size >= 3 } }
            if (polys.size < 2) Text("Cần ít nhất hai vùng trên bản đồ (ranh vườn, khu, vùng đệm…) để cắt, khoét hoặc gộp.", color = VC.muted, fontSize = 14.sp)
            else {
                val labels = polys.mapIndexed { i, f -> "${i + 1}. ${f.name} (${f.kind.label}, ${"%.2f".format(Geo.polygonAreaHa(f.points))} ha)" }
                var ia by remember(polys.size) { mutableStateOf(0) }
                var ib by remember(polys.size) { mutableStateOf(1) }
                var op by remember { mutableStateOf(GisClient.Overlay.CLIP) }
                Text("Chọn hai vùng A và B rồi phép toán; kết quả xem trước, bạn bấm lưu mới thành khu mới (không sửa vùng gốc).", color = VC.text2, fontSize = 14.sp)
                VPicker("Vùng A", labels, labels[ia.coerceAtMost(labels.lastIndex)], { ia = labels.indexOf(it); overlayRes = null }, Modifier.padding(top = 6.dp))
                VPicker("Vùng B", labels, labels[ib.coerceAtMost(labels.lastIndex)], { ib = labels.indexOf(it); overlayRes = null }, Modifier.padding(top = 6.dp))
                ChipRow(GisClient.Overlay.values().toList(), op, { it.label }, { op = it; overlayRes = null }, modifier = Modifier.padding(top = 8.dp))
                VButton(if (busy) "Đang tính…" else "Tính kết quả", {
                    if (ia == ib) { err = "Chọn hai vùng khác nhau."; return@VButton }
                    val a = polys[ia]; val b = polys[ib]
                    busy = true; err = null
                    scope.launch {
                        val r = runCatching { withContext(Dispatchers.IO) { GisClient.overlay(cfg, op, a, b) } }
                        busy = false
                        r.onSuccess { overlayRes = Triple(op, "${a.name} / ${b.name}", it) }.onFailure { err = it.message }
                    }
                }, enabled = !busy && cfg.configured, modifier = Modifier.padding(top = 8.dp))
                overlayRes?.let { (o, label, parts) ->
                    if (parts.isEmpty()) VerdictBanner(Verdict.WARN, "Kết quả rỗng", "Hai vùng không giao nhau hoặc B phủ kín A.", Modifier.padding(top = 8.dp))
                    else {
                        val total = parts.sumOf { Geo.polygonAreaHa(it) }
                        VerdictBanner(Verdict.GOOD, "${o.label}: ${"%.2f".format(total)} ha", "${parts.size} vùng · $label", Modifier.padding(top = 8.dp))
                        RingPreview(parts.map { it to VC.lime })
                        VButton("Lưu thành khu mới", {
                            val feats = parts.mapIndexed { i, ring -> GeoFeature(newId(), FeatureKind.ZONE, "${o.label.substringBefore(" (")} ${label}" + if (parts.size > 1) " #${i + 1}" else "", ring, note = "Tạo bằng RustGIS (${o.tool})", source = "RustGIS ${o.tool}") }
                            c.store.applyGeoImport(feats, emptyList(), "RustGIS ${o.tool}")
                            overlayRes = null; c.toast("Đã lưu ${feats.size} khu mới.")
                        }, primary = false, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }

            // ---- raster ----
            SectionLabel("Thống kê ảnh raster trong ranh vườn")
            Text("Chọn ảnh GeoTIFF/ASC (ví dụ bản đồ NDVI từ drone) để biết giá trị trung bình bên trong ranh.", color = VC.text2, fontSize = 14.sp)
            VButton(rasterName ?: "Chọn ảnh raster (.tif, .asc)", { pick.launch(arrayOf("*/*")) }, primary = false, modifier = Modifier.padding(top = 6.dp))
            if (rasterName != null) {
                ChipRow(listOf<String?>(null, "ndvi"), index, { if (it == null) "Giá trị băng 1" else "NDVI (băng 4 và 3)" }, { index = it; zonal = null }, modifier = Modifier.padding(top = 6.dp))
                VButton(if (busy) "Đang tính…" else "Tính trong ranh vườn", {
                    val bytes = rasterBytes; val name = rasterName
                    if (bytes == null || name == null) return@VButton
                    busy = true; err = null
                    scope.launch {
                        val r = runCatching { withContext(Dispatchers.IO) { GisClient.zonal(cfg, name, bytes, ring, index) } }
                        busy = false
                        r.onSuccess { zonal = it }.onFailure { err = it.message }
                    }
                }, enabled = !busy && cfg.configured, modifier = Modifier.padding(top = 6.dp))
            }
            zonal?.let { z ->
                val cov = z.coveragePct
                VerdictBanner(if (cov != null && cov < 80) Verdict.WARN else Verdict.GOOD, "${z.label} trung bình: ${z.mean?.let { "%.3f".format(it) } ?: "không có"}",
                    if (cov != null && cov < 80) "Ảnh chỉ phủ ${"%.0f".format(cov)}% ranh vườn nên số này chỉ đại diện cho phần có ảnh." else cov?.let { "Ảnh phủ ${"%.0f".format(it)}% ranh vườn." }, Modifier.padding(top = 8.dp))
                val inside = check?.inside.orEmpty()
                if (z.mean != null) VButton(if (inside.isEmpty()) "Không có cây trong ranh để ghi nhật ký" else "Ghi vào nhật ký cho ${inside.size} cây trong ranh", {
                    val ev = Event(
                        id = newId(), treeIds = inside.map { it.id }, type = EventType.OBSERVATION,
                        activity = "Thống kê ${z.label} trong ranh vườn" + (rasterName?.let { " (ảnh $it)" } ?: ""), occurredAt = Dates.now(), author = d.garden.operatorName,
                        note = "${z.label} trung bình ${"%.3f".format(z.mean)} trên ${"%.2f".format(z.areaHa)} ha, ${z.pixels} điểm ảnh" + (cov?.let { ", ảnh phủ ${"%.0f".format(it)}% ranh" } ?: "") + ". Tính bằng RustGIS.",
                        source = EvidenceSource.MEASUREMENT,
                    )
                    val r = c.store.addEvent(ev)
                    c.toast(if (r.ok) "Đã ghi vào nhật ký ${inside.size} cây." else r.message ?: "Không ghi được.")
                }, primary = false, enabled = inside.isNotEmpty(), modifier = Modifier.padding(top = 6.dp))
                TechnicalDetails(c.simpleMode, Modifier.padding(top = 4.dp)) {
                    Text("${z.pixels} điểm ảnh trong ranh ${"%.2f".format(z.areaHa)} ha · ${z.bands} băng · ${z.crs ?: "không rõ hệ toạ độ"}", color = VC.text2, fontSize = 13.sp)
                }
            }
        }
        // ---- Dữ liệu mở & Shapefile ----
        SectionLabel("Kho dữ liệu mở & Bộ công cụ Shapefile (GADM · OSMnx · HDX · World Bank · GDAL)")
        val primaryRegion = d.features.firstOrNull { it.kind == FeatureKind.BOUNDARY }
            ?: d.features.firstOrNull { it.kind == FeatureKind.ZONE }
        OpenGisPanel(
            c = c,
            region = primaryRegion,
            cfg = cfg,
            onConfigure = { c.nav.push("satellite") },
        )
        err?.let { Text(it, color = VC.red, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp)) }
    }
}
