package com.example.test_tablet.ui.vwing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class OpenGisSection(val label: String) {
    GADM_GEODATA("1. GADM & Geodata"),
    OSM_OVERPASS("2. OSMnx & Overpass"),
    HDX_CKAN("3. Cổng nhân đạo HDX"),
    WORLDBANK("4. World Bank (wbgapi)"),
    GDAL_SHAPEFILE("5. Shapefile / GDAL / GeoPandas"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OpenGisPanel(
    c: VCtx,
    region: GeoFeature?,
    cfg: SatelliteConfig,
    onConfigure: () -> Unit,
    regionHud: RegionHudState? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var section by rememberSaveable { mutableStateOf(OpenGisSection.GADM_GEODATA) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // 1. GADM & Geodata state
    var gadmLevel by rememberSaveable { mutableStateOf(2) }
    var gadmQuery by rememberSaveable { mutableStateOf("Đắk Lắk") }
    var gadmItems by remember { mutableStateOf<List<GadmItem>>(emptyList()) }
    var gadmBoundary by remember { mutableStateOf<GadmBoundaryResult?>(null) }
    var geodataEnv by remember { mutableStateOf<GeodataEnvResult?>(null) }

    // 2. OSM & Overpass state
    var osmBufferM by rememberSaveable { mutableStateOf(500) }
    var osmResult by remember { mutableStateOf<OsmExtractResult?>(null) }
    var overpassQl by rememberSaveable {
        mutableStateOf("[out:json][timeout:15];(way[\"highway\"]({{bbox}});way[\"waterway\"]({{bbox}});way[\"building\"]({{bbox}}););out body;>;out skel qt;")
    }

    // 3. HDX state
    var hdxQuery by rememberSaveable { mutableStateOf("waterways") }
    var hdxDatasets by remember { mutableStateOf<List<HdxDataset>>(emptyList()) }
    var hdxLayer by remember { mutableStateOf<HdxFetchResult?>(null) }

    // 4. World Bank state
    var wbCountry by rememberSaveable { mutableStateOf("VNM") }
    var wbResult by remember { mutableStateOf<WbJoinResult?>(null) }

    // 5. GDAL / Fiona / GeoPandas Vector state
    var targetFormat by rememberSaveable { mutableStateOf("shp") }
    var targetCrs by rememberSaveable { mutableStateOf("EPSG:32648") }
    var pendingSave by remember { mutableStateOf<VectorConvertResult?>(null) }
    var lastConverted by remember { mutableStateOf<VectorConvertResult?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val item = pendingSave
        pendingSave = null
        if (uri != null && item != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(item.bytes) }
            }.onSuccess {
                c.toast("Đã lưu ${item.filename} (${item.crs}, ${item.sizeBytes / 1024} KB).")
            }.onFailure {
                error = "Không ghi được tệp: ${it.message}"
            }
        }
    }

    val ring = region?.points?.toList()

    LaunchedEffect(gadmBoundary, geodataEnv, osmResult, hdxLayer, wbResult, lastConverted) {
        gadmBoundary?.let { gb ->
            regionHud?.gadmResult = gb
            regionHud?.activeToolTitle = "GADM v4.1"
            regionHud?.activeToolSummary = "${listOf(gb.name2, gb.name1, gb.name0).filter { it.isNotBlank() }.joinToString(", ")} · ${fmtNum(gb.areaHa, 1)} ha"
        }
        geodataEnv?.let { env ->
            regionHud?.envResult = env
            val ph = env.soilProperties.firstOrNull { it.key == "phh2o" }?.d0to5
            val soc = env.soilProperties.firstOrNull { it.key == "soc" }?.d0to5
            regionHud?.activeToolTitle = "SoilGrids & WorldClim"
            regionHud?.activeToolSummary = "${env.wrbClass.substringBefore(" (")} · pH ${ph?.let { fmtNum(it, 1) } ?: "—"} · SOC ${soc?.let { fmtNum(it, 1) } ?: "—"}g/kg · Mưa ${fmtNum(env.annualPrecipMm, 0)}mm"
        }
        osmResult?.let { osm ->
            regionHud?.osmResult = osm
            regionHud?.activeToolTitle = "OSMnx & Overpass"
            regionHud?.activeToolSummary = "Đường ${fmtNum(osm.roadLengthKm, 2)} km (${osm.roadsCount}) · Kênh/Suối ${fmtNum(osm.waterwayLengthKm, 2)} km (${osm.waterCount}) · Công trình ${osm.buildingsCount}"
        }
        hdxLayer?.let { hl ->
            regionHud?.activeToolTitle = "Dữ liệu Nhân đạo HDX"
            regionHud?.activeToolSummary = "${hl.title} · ${hl.features} đối tượng"
            regionHud?.activeToolBadges = listOf(
                RegionHudBadge("HDX OCHA", hl.datasetId, "${hl.features} đối tượng", hl.title, VC.mint),
            )
        }
        wbResult?.let { wb ->
            regionHud?.activeToolTitle = "World Bank (${wb.country})"
            regionHud?.activeToolSummary = "${wb.indicators.size} chỉ số phát triển nông nghiệp đã gắn vào vùng"
            regionHud?.activeToolBadges = wb.indicators.map { ind ->
                RegionHudBadge("World Bank", ind.code.substringAfter("."), "${fmtNum(ind.latestValue, 1)} ${ind.unit}", "${ind.label} (${ind.latestYear})", VC.amber)
            }
        }
        lastConverted?.let { vc ->
            regionHud?.activeToolTitle = "GDAL / GeoPandas"
            regionHud?.activeToolSummary = "${vc.filename} · ${vc.crs} · ${vc.features} đối tượng"
        }
    }

    fun importGeoJsonToGarden(geojsonStr: String, sourceLabel: String) {
        val parsed = Geo.parseGeoJson(geojsonStr)
        if (parsed.features.isEmpty()) {
            c.toast("Không tìm thấy đối tượng hình học nào để nhập.")
            return
        }
        val feats = parsed.features.take(60).map { f ->
            val kind = when (f.geom) {
                Geom.LINE -> if (f.name.contains("suối", true) || f.name.contains("kênh", true) || f.name.contains("water", true)) FeatureKind.WATER else FeatureKind.ROAD
                Geom.POLYGON -> FeatureKind.ZONE
                Geom.POINT -> FeatureKind.WATER
            }
            GeoFeature(newId(), kind, f.name.ifBlank { sourceLabel }, f.points, note = "Nhập từ $sourceLabel", source = sourceLabel)
        }
        c.store.applyGeoImport(feats, emptyList(), sourceLabel)
        c.toast("Đã nhập ${feats.size} đối tượng từ $sourceLabel vào bản đồ vườn.")
    }

    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Bộ công cụ GIS & Dữ liệu Không gian Mở tích hợp 10 thư viện chuẩn: gadm-python, rspatial/geodata (SoilGrids & WorldClim), OSMnx, Overpass Turbo, osmtogeojson, HDX Python API, World Bank wbgapi, GDAL/OGR, Fiona & GeoPandas.",
            color = VC.text2, fontSize = 14.sp,
        )
        if (!cfg.configured) {
            Notice(
                "Chưa cấu hình kết nối máy chủ vệ tinh.",
                VC.amber,
                trailing = { VButton("Kết nối", onConfigure, primary = false) },
            )
        }

        ChipRow(
            OpenGisSection.entries.toList(),
            section,
            { it.label },
            { section = it; error = null },
        )

        when (section) {
            OpenGisSection.GADM_GEODATA -> {
                SectionLabel("Ranh giới hành chính GADM v4.1 (gadm-python)")
                Text(
                    "Tra cứu danh mục hành chính Việt Nam (mã ISO: VNM) theo cấp Tỉnh (1), Huyện/Thị xã (2), Xã/Phường (3) hoặc tải đa giác ranh hành chính bao quanh vườn.",
                    color = VC.text2, fontSize = 13.sp,
                )
                ChipRow(
                    listOf(1, 2, 3),
                    gadmLevel,
                    { lvl -> when (lvl) { 1 -> "Cấp 1 (Tỉnh/TP)"; 2 -> "Cấp 2 (Quận/Huyện)"; else -> "Cấp 3 (Xã/Phường)" } },
                    { gadmLevel = it },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VField(gadmQuery, { gadmQuery = it }, "Tìm tên Tỉnh / Huyện / Mã GID", Modifier.weight(1f))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VButton(if (busy) "Đang tra…" else "Tìm đơn vị GADM", {
                        if (!cfg.configured) { onConfigure(); return@VButton }
                        busy = true; error = null
                        scope.launch {
                            try {
                                gadmItems = withContext(Dispatchers.IO) { OpenGisClient.searchGadm(cfg, "VNM", gadmLevel, gadmQuery) }
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = e.message }
                            finally { busy = false }
                        }
                    }, enabled = !busy, primary = false)

                    VButton(if (busy) "Đang tải…" else "Tải ranh GADM tại vườn", {
                        if (!cfg.configured) { onConfigure(); return@VButton }
                        busy = true; error = null
                        scope.launch {
                            try {
                                gadmBoundary = withContext(Dispatchers.IO) {
                                    OpenGisClient.fetchGadmBoundary(cfg, "VNM", gadmLevel, name = gadmQuery, ring = ring)
                                }
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = e.message }
                            finally { busy = false }
                        }
                    }, enabled = !busy)
                }

                if (gadmItems.isNotEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(VC.elevated).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Kết quả GADM (${gadmItems.size} đơn vị):", color = VC.teal, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        gadmItems.take(6).forEach { it ->
                            Text("• ${it.name} (${it.parent}) · Mã GID: ${it.gid}", color = VC.text, fontSize = 13.sp)
                        }
                    }
                }

                gadmBoundary?.let { gb ->
                    VerdictBanner(
                        Verdict.GOOD,
                        "GADM: ${listOf(gb.name2, gb.name1, gb.name0).filter { it.isNotBlank() }.joinToString(", ")}",
                        "Mã GID: ${gb.gid2.ifBlank { gb.gid1 }} · Diện tích hành chính: ${fmtNum(gb.areaHa, 1)} ha · ${gb.source}",
                    )
                    VButton("Lưu ranh hành chính GADM vào bản đồ", {
                        importGeoJsonToGarden(gb.geojson, "GADM ${gb.name2.ifBlank { gb.name1 }}")
                    }, primary = false)
                }

                Spacer(Modifier.height(6.dp))
                SectionLabel("Thổ nhưỡng SoilGrids 250m & Khí hậu WorldClim (rspatial/geodata)")
                Text(
                    "Tự động lấy thông số đất (pH, hữu cơ SOC, sét, cát, thịt, đạm, CEC, dung trọng ở 3 tầng 0–30 cm) và khí hậu WorldClim v2.1 tại tọa độ vườn.",
                    color = VC.text2, fontSize = 13.sp,
                )
                VButton(if (busy) "Đang phân tích đất & khí hậu…" else "Tải dữ liệu SoilGrids & WorldClim", {
                    if (!cfg.configured) { onConfigure(); return@VButton }
                    busy = true; error = null
                    scope.launch {
                        try {
                            geodataEnv = withContext(Dispatchers.IO) { OpenGisClient.fetchGeodataEnv(cfg, ring) }
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.message }
                        finally { busy = false }
                    }
                }, enabled = !busy)

                geodataEnv?.let { env ->
                    VerdictBanner(
                        Verdict.GOOD,
                        "Nhóm đất FAO/WRB: ${env.wrbClass}",
                        "Mưa năm: ${fmtNum(env.annualPrecipMm, 0)} mm · Nhiệt độ TB: ${fmtNum(env.tavgC, 1)} °C (${fmtNum(env.tminC, 1)}–${fmtNum(env.tmaxC, 1)} °C) · Bức xạ: ${fmtNum(env.solarRadMjM2Day, 1)} MJ/m²/ngày",
                    )
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(VC.elevated).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Hồ sơ thổ nhưỡng theo tầng độ sâu (0–5cm | 5–15cm | 15–30cm):", color = VC.teal, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        env.soilProperties.forEach { sp ->
                            Text(
                                "• ${sp.label}: ${fmtNum(sp.d0to5, 2)} | ${fmtNum(sp.d5to15, 2)} | ${fmtNum(sp.d15to30, 2)} ${sp.unit}",
                                color = VC.text, fontSize = 13.sp,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        env.recommendations.forEach { rec ->
                            Text("✓ $rec", color = VC.mint, fontSize = 13.sp)
                        }
                    }
                }
            }

            OpenGisSection.OSM_OVERPASS -> {
                SectionLabel("Trích xuất mạng lưới OpenStreetMap (OSMnx & Overpass Turbo)")
                Text(
                    "Tải mạng lưới giao thông (highway), sông suối/kênh tưới (waterway), công trình (building) và vùng sử dụng đất (landuse) xung quanh ranh vườn.",
                    color = VC.text2, fontSize = 13.sp,
                )
                ChipRow(listOf(300, 500, 1000), osmBufferM, { "Bán kính $it m" }, { osmBufferM = it })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VButton(if (busy) "Đang trích xuất OSM…" else "Trích xuất lớp OSM quanh vườn", {
                        if (!cfg.configured) { onConfigure(); return@VButton }
                        busy = true; error = null
                        scope.launch {
                            try {
                                osmResult = withContext(Dispatchers.IO) { OpenGisClient.extractOsm(cfg, ring, osmBufferM.toDouble()) }
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = e.message }
                            finally { busy = false }
                        }
                    }, enabled = !busy)
                }

                Spacer(Modifier.height(4.dp))
                SectionLabel("Truy vấn Overpass QL tuỳ chỉnh & osmtogeojson")
                VField(overpassQl, { overpassQl = it }, "Câu lệnh Overpass QL (hỗ trợ {{bbox}})", singleLine = false, minLines = 2)
                VButton(if (busy) "Đang chạy Overpass QL…" else "Chạy truy vấn Overpass Turbo → GeoJSON", {
                    if (!cfg.configured) { onConfigure(); return@VButton }
                    busy = true; error = null
                    scope.launch {
                        try {
                            osmResult = withContext(Dispatchers.IO) { OpenGisClient.overpassQuery(cfg, overpassQl, ring) }
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.message }
                        finally { busy = false }
                    }
                }, enabled = !busy, primary = false)

                osmResult?.let { or ->
                    VerdictBanner(
                        Verdict.GOOD,
                        "Đã trích xuất ${or.features} đối tượng OpenStreetMap",
                        "Đường giao thông: ${or.roadsCount} đoạn (${fmtNum(or.roadLengthKm, 2)} km) · Thuỷ văn: ${or.waterCount} đoạn (${fmtNum(or.waterwayLengthKm, 2)} km) · Công trình: ${or.buildingsCount}",
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        VButton("Nhập đường & suối OSM vào bản đồ vườn", {
                            importGeoJsonToGarden(or.geojson, "OSMnx/Overpass")
                        })
                        VButton("Xuất lớp OSM ra Shapefile (.shp.zip)", {
                            busy = true; error = null
                            scope.launch {
                                try {
                                    val conv = withContext(Dispatchers.IO) {
                                        OpenGisClient.convertVector(cfg, or.geojson, "shp", "EPSG:32648", "vwing_osm")
                                    }
                                    pendingSave = conv
                                    saveLauncher.launch(conv.filename)
                                } catch (e: CancellationException) { throw e }
                                catch (e: Exception) { error = e.message }
                                finally { busy = false }
                            }
                        }, primary = false, enabled = !busy)
                    }
                }
            }

            OpenGisSection.HDX_CKAN -> {
                SectionLabel("Cổng Dữ liệu Nhân đạo LHQ — HDX (OCHA-DAP/hdx-python-api)")
                Text(
                    "Kết nối trực tiếp cổng CKAN của Humanitarian Data Exchange (data.humdata.org) để tìm và tải ranh giới chuẩn OCHA COD-AB, mạng lưới sông ngòi HOTOSM, đường giao thông và lượng mưa CHIRPS.",
                    color = VC.text2, fontSize = 13.sp,
                )
                VField(hdxQuery, { hdxQuery = it }, "Từ khoá tìm kiếm trên HDX (ví dụ: waterways, roads, cod-ab)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VButton(if (busy) "Đang tìm trên HDX…" else "Tìm bộ dữ liệu HDX (Việt Nam)", {
                        if (!cfg.configured) { onConfigure(); return@VButton }
                        busy = true; error = null
                        scope.launch {
                            try {
                                hdxDatasets = withContext(Dispatchers.IO) { OpenGisClient.searchHdx(cfg, hdxQuery, "vnm") }
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = e.message }
                            finally { busy = false }
                        }
                    }, enabled = !busy)
                }

                if (hdxDatasets.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        hdxDatasets.forEach { ds ->
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(VC.elevated).padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(ds.title, color = VC.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "${ds.organization} · Định dạng: ${ds.formats.joinToString(", ").ifBlank { "SHP, GeoJSON" }}",
                                    color = VC.muted, fontSize = 12.sp,
                                )
                                VButton("Tải & cắt lớp HDX này quanh vườn", {
                                    busy = true; error = null
                                    scope.launch {
                                        try {
                                            hdxLayer = withContext(Dispatchers.IO) { OpenGisClient.fetchHdxLayer(cfg, ds.id, ring) }
                                        } catch (e: CancellationException) { throw e }
                                        catch (e: Exception) { error = e.message }
                                        finally { busy = false }
                                    }
                                }, primary = false, enabled = !busy)
                            }
                        }
                    }
                }

                hdxLayer?.let { hl ->
                    VerdictBanner(
                        Verdict.GOOD,
                        "${hl.title} (${hl.features} đối tượng)",
                        hl.source,
                    )
                    VButton("Nhập lớp HDX vào bản đồ vườn", {
                        importGeoJsonToGarden(hl.geojson, "HDX ${hl.datasetId}")
                    })
                }
            }

            OpenGisSection.WORLDBANK -> {
                SectionLabel("Dữ liệu Ngân hàng Thế giới — World Bank (tgherzog/wbgapi)")
                Text(
                    "Truy xuất các chỉ số phát triển nông nghiệp, đất canh tác, năng suất, tưới tiêu & lượng mưa của World Bank và kết nối (spatial/attribute join) trực tiếp vào thuộc tính lớp bản đồ véc-tơ.",
                    color = VC.text2, fontSize = 13.sp,
                )
                ChipRow(
                    listOf("VNM", "THA", "IDN", "MYS"),
                    wbCountry,
                    { cCode -> when (cCode) { "VNM" -> "Việt Nam (VNM)"; "THA" -> "Thái Lan (THA)"; "IDN" -> "Indonesia (IDN)"; else -> "Malaysia (MYS)" } },
                    { wbCountry = it },
                )
                VButton(if (busy) "Đang truy xuất World Bank…" else "Tải chỉ số World Bank & Join vào Ranh vườn", {
                    if (!cfg.configured) { onConfigure(); return@VButton }
                    busy = true; error = null
                    scope.launch {
                        try {
                            wbResult = withContext(Dispatchers.IO) { OpenGisClient.fetchWorldBank(cfg, wbCountry, ring) }
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.message }
                        finally { busy = false }
                    }
                }, enabled = !busy)

                wbResult?.let { wb ->
                    VerdictBanner(
                        Verdict.GOOD,
                        "Đã kết nối ${wb.indicators.size} chỉ số World Bank (${wb.country}) vào lớp véc-tơ",
                        wb.source,
                    )
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(VC.elevated).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        wb.indicators.forEach { ind ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${ind.label} (${ind.latestYear})", color = VC.text2, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text("${fmtNum(ind.latestValue, 2)} ${ind.unit}", color = VC.lime, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    wb.joinedGeojson?.let { jgj ->
                        VButton("Xuất lớp bản đồ đã Join World Bank ra Shapefile (.shp.zip)", {
                            busy = true; error = null
                            scope.launch {
                                try {
                                    val conv = withContext(Dispatchers.IO) {
                                        OpenGisClient.convertVector(cfg, jgj, "shp", "EPSG:4326", "vwing_worldbank_${wb.country.lowercase()}")
                                    }
                                    pendingSave = conv
                                    saveLauncher.launch(conv.filename)
                                } catch (e: CancellationException) { throw e }
                                catch (e: Exception) { error = e.message }
                                finally { busy = false }
                            }
                        }, primary = false, enabled = !busy)
                    }
                }
            }

            OpenGisSection.GDAL_SHAPEFILE -> {
                SectionLabel("Bộ công cụ Shapefile / GDAL ogr2ogr / Fiona / GeoPandas")
                Text(
                    "Chuyển đổi toàn bộ bản đồ vườn (ranh giới, khu, đường, nguồn nước, vị trí cây) sang ESRI Shapefile (.shp.zip), GeoPackage (.gpkg), FlatGeobuf, KML, GPX hoặc tập lệnh nạp vào cơ sở dữ liệu PostGIS (.sql) kèm tái chiếu hệ toạ độ.",
                    color = VC.text2, fontSize = 13.sp,
                )
                VPicker(
                    "Định dạng đích (GDAL/OGR & Fiona)",
                    OpenGisClient.VECTOR_FORMATS.map { it.second },
                    OpenGisClient.VECTOR_FORMATS.first { it.first == targetFormat }.second,
                    { label -> targetFormat = OpenGisClient.VECTOR_FORMATS.first { it.second == label }.first },
                )
                VPicker(
                    "Hệ quy chiếu toạ độ (CRS Reprojection)",
                    OpenGisClient.CRS_OPTIONS.map { it.second },
                    OpenGisClient.CRS_OPTIONS.first { it.first == targetCrs }.second,
                    { label -> targetCrs = OpenGisClient.CRS_OPTIONS.first { it.second == label }.first },
                )
                VButton(if (busy) "Đang chuyển đổi bằng GDAL/GeoPandas…" else "Chuyển đổi & Lưu tệp bản đồ vườn", {
                    if (!cfg.configured) { onConfigure(); return@VButton }
                    busy = true; error = null
                    val gj = Geo.exportGeoJson(c.d)
                    val gardenName = c.d.garden.name.ifBlank { "VWing" }
                    scope.launch {
                        try {
                            val conv = withContext(Dispatchers.IO) {
                                OpenGisClient.convertVector(cfg, gj, targetFormat, targetCrs, gardenName)
                            }
                            lastConverted = conv
                            pendingSave = conv
                            saveLauncher.launch(conv.filename)
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.message }
                        finally { busy = false }
                    }
                }, enabled = !busy)

                lastConverted?.let { lc ->
                    VerdictBanner(
                        Verdict.GOOD,
                        "Đã tạo ${lc.filename} (${lc.features} đối tượng · ${lc.crs})",
                        "Dung lượng: ${lc.sizeBytes} bytes · Engine: GDAL 3.12 / Fiona / GeoPandas",
                    )
                }
            }
        }

        error?.let { Notice(it, VC.red) }
    }
}
