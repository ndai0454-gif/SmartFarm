package com.example.test_tablet.ui.vwing

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

@Composable
private fun <I, O> rememberOptionalLauncher(
    contract: ActivityResultContract<I, O>,
    onResult: (O) -> Unit,
): (I) -> Unit {
    val owner = LocalActivityResultRegistryOwner.current
    return if (owner != null) {
        val launcher = rememberLauncherForActivityResult(contract, onResult)
        ({ input: I -> launcher.launch(input) })
    } else {
        ({ _: I -> })
    }
}

/** Real RGB inference; manual annotations are exported separately from predictions. */
@Composable
fun DeepForestHubCard(hud: RegionHudState, cfg: SatelliteConfig, onSelectOverlay: (MapOverlay?) -> Unit, onToast: (String) -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val df = hud.deepForestResult
    var showVietnamPipeline by remember { mutableStateOf(false) }
    var selectedOamPreset by remember { mutableStateOf(OamVietnamPreset.CENTRAL_HIGHLANDS) }
    var oamSearchResult by remember { mutableStateOf<OamSearchResponse?>(null) }
    var oamSearching by remember { mutableStateOf(false) }
    var csvConversionResult by remember { mutableStateOf<DeepForestCsvConversionResult?>(null) }
    var csvConverting by remember { mutableStateOf(false) }

    val openUrl: (String) -> Unit = { url ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { onToast("Không mở được liên kết: $url") }
    }

    val upload = rememberOptionalLauncher(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            hud.loading = true
            hud.deepForestError = null
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                        val out = ByteArrayOutputStream()
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            require(out.size() + read <= DeepForestClient.MAX_IMAGE_BYTES) { "Ảnh vượt quá 30 MiB." }
                            out.write(buffer, 0, read)
                        }
                        out.toByteArray()
                    } ?: error("Không đọc được ảnh.")
                    DeepForestClient.uploadImage(cfg, bytes)
                }
            }
            hud.loading = false
            result.onSuccess {
                hud.deepForestImage = it
                hud.deepForestResult = null
                hud.deepForestBitmap = null
                onSelectOverlay(null)
                onToast("Đã tải ảnh RGB ${it.width}×${it.height}px; bấm Quét để nhận diện.")
            }.onFailure { hud.deepForestError = it.message ?: "Không tải được ảnh." }
        }
    }

    val pickPascalVocXmls = rememberOptionalLauncher(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            csvConverting = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val docs = uris.mapIndexed { idx, uri ->
                        val xmlText = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                            ?: error("Không đọc được tệp XML thứ ${idx + 1}.")
                        val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "vietnam_garden_${idx + 1}.xml"
                        PascalVocXmlInput(filename = name, xmlContent = xmlText)
                    }
                    DeepForestClient.convertXmlToDeepForestCsv(cfg, docs, valRatio = 0.2, normalizeLabelToTree = true)
                }
            }
            csvConverting = false
            result.onSuccess {
                csvConversionResult = it
                onToast("Đã chuyển đổi ${it.totalBoxes} nhãn từ ${it.uniqueImagesCount} ảnh sang chuẩn CSV DeepForest.")
            }.onFailure {
                onToast("Lỗi xml_to_csv: ${it.message}")
            }
        }
    }

    val exportTrainCsv = rememberOptionalLauncher(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val csv = csvConversionResult?.vietnamTrainCsv
        if (uri != null && !csv.isNullOrBlank()) scope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val out = context.contentResolver.openOutputStream(uri) ?: error("Không mở được tệp đầu ra.")
                    out.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                }
            }
            onToast(res.fold({ "Đã lưu vietnam_train.csv chuẩn DeepForest." }, { "Lỗi lưu CSV: ${it.message}" }))
        }
    }

    val exportValCsv = rememberOptionalLauncher(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val csv = csvConversionResult?.vietnamValidationCsv
        if (uri != null && !csv.isNullOrBlank()) scope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val out = context.contentResolver.openOutputStream(uri) ?: error("Không mở được tệp đầu ra.")
                    out.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                }
            }
            onToast(res.fold({ "Đã lưu vietnam_validation.csv chuẩn DeepForest." }, { "Lỗi lưu CSV: ${it.message}" }))
        }
    }

    val export = rememberOptionalLauncher(ActivityResultContracts.CreateDocument("application/geo+json")) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val features = JSONArray()
                    hud.userAnnotatedBboxes.forEach { a ->
                        val b = a.bounds
                        val ring = JSONArray()
                        listOf(b.west to b.south, b.east to b.south, b.east to b.north, b.west to b.north, b.west to b.south)
                            .forEach { (x, y) -> ring.put(JSONArray().put(x).put(y)) }
                        features.put(JSONObject().put("type", "Feature")
                            .put("properties", JSONObject().put("id", a.boxId).put("label", a.label).put("verified", false))
                            .put("geometry", JSONObject().put("type", "Polygon").put("coordinates", JSONArray().put(ring))))
                    }
                    val bytes = JSONObject().put("type", "FeatureCollection").put("features", features).toString(2).toByteArray()
                    val output = context.contentResolver.openOutputStream(uri) ?: error("Không ghi được tệp.")
                    output.use { it.write(bytes) }
                }
            }
            onToast(result.fold({ "Đã xuất nhãn thủ công chưa xác minh." }, { "Không xuất được: ${it.message}" }))
        }
    }

    VCard(modifier.fillMaxWidth()) {
        Text("🌲 Đếm tán cây trên ảnh RGB (DeepForest)", color = VC.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        Text("Nhận diện vị trí và kích thước vòm tán từ ảnh chụp trực giao hoặc bản đồ khu vực.", color = VC.text2, fontSize = 12.5.sp)
        if (hud.loading || oamSearching || csvConverting) CircularProgressIndicator(Modifier.size(22.dp), color = VC.mint, strokeWidth = 2.dp)
        hud.deepForestError?.let { Text(it, color = VC.red, fontSize = 12.sp) }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VButton("Thử vị trí được gửi", {
                hud.selectDeepForestSampleRegion()
                onSelectOverlay(null)
                onToast("Vùng thử 200×200 m quanh 11.760461, 107.642313; chưa biết ranh đất thực tế. Bấm Quét để chạy.")
            }, primary = false, enabled = !hud.loading)
            VButton("Chọn ảnh RGB", { upload(arrayOf("image/*", "image/tiff")) }, primary = false, enabled = cfg.configured && !hud.loading)
            VButton("Quét tán cây", { hud.runDeepForestPredict(cfg = cfg, scope = scope, onOverlay = onSelectOverlay, onDone = onToast) }, enabled = cfg.configured && !hud.loading)
            VButton(if (showVietnamPipeline) "Ẩn Nguồn Ảnh & Dataset VN ▴" else "Nguồn Ảnh & Dataset Vườn VN ▾", {
                showVietnamPipeline = !showVietnamPipeline
            }, primary = false)
        }
        Text(if (hud.deepForestSampleRegion) "VÙNG THỬ: ô 200×200 m quanh điểm người dùng gửi, không phải ranh vườn được xác nhận."
            else "Phạm vi quét: vùng đang chọn trên bản đồ.", color = VC.amber, fontSize = 12.sp)
        val image = hud.deepForestImage
        if (image != null) {
            Text("Ảnh tải lên ${image.width}×${image.height}px. Toàn bộ ảnh được gán vào khung vùng chọn: chỉ dùng ảnh trực giao phủ đúng vùng này; ảnh chụp bất kỳ không tạo vị trí cây đáng tin cậy.", color = VC.amber, fontSize = 12.sp)
            VButton("Dùng ảnh bản đồ máy chủ", { hud.deepForestImage = null; hud.deepForestResult = null; onSelectOverlay(null) }, primary = false, enabled = !hud.loading)
        } else Text("Nguồn ảnh bản đồ và cảnh báo sẽ được máy chủ ghi trong kết quả.", color = VC.text2, fontSize = 12.sp)
        if (df != null) {
            Text("${df.boxes.size} tán được phát hiện · Tin cậy TB ${df.meanConfidencePct.takeIf { it.isFinite() }?.let { "${fmtNum(it, 1)}%" } ?: "—"}", color = VC.mint, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(df.summaryVi, color = VC.text2, fontSize = 12.sp)
            df.source?.let { Text("Nguồn: $it", color = VC.text2, fontSize = 11.sp) }
            df.provenance?.let { Text("Truy xuất: $it", color = VC.text2, fontSize = 11.sp) }
            df.warnings.forEach { Text(it, color = VC.amber, fontSize = 12.sp) }
            Text("Raster ${df.rasterWidthPx}×${df.rasterHeightPx}px · Số tile: ${df.totalTilesProcessed ?: "chưa có"} · Trước NMS: ${df.rawBoxesBeforeNms ?: "chưa có"} · Loại trùng: ${df.nmsSuppressedCount ?: "chưa có"}", color = VC.text2, fontSize = 11.sp)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VButton(if (hud.showDeepForestBboxes) "Ẩn khung tán" else "Hiện khung tán", { hud.showDeepForestBboxes = !hud.showDeepForestBboxes }, primary = false)
            VButton(if (hud.deepForestAnnotateMode) "Dừng gán nhãn" else "Gán nhãn thủ công", { hud.deepForestAnnotateMode = !hud.deepForestAnnotateMode }, primary = false)
            VButton("Xuất nhãn (${hud.userAnnotatedBboxes.size})", { export("deepforest-manual-labels.geojson") }, primary = false, enabled = hud.userAnnotatedBboxes.isNotEmpty())
            VButton(if (hud.deepForestFineTuneStage != null) "Đang huấn luyện…" else "Fine-tune (${hud.userAnnotatedBboxes.size} nhãn)",
                { hud.runDeepForestFineTune(cfg = cfg, scope = scope, onDone = onToast) },
                primary = false, enabled = cfg.configured && !hud.loading && hud.deepForestImage != null && hud.userAnnotatedBboxes.size >= 8)
        }
        Text("Nhãn chạm bản đồ là bản nháp. Fine-tune dùng chính các khung này trên ảnh RGB bạn đã tải lên (cần ≥8 khung, nên 30+); máy chủ tự giữ ~20% nhãn bên phải ảnh làm tập kiểm định riêng. Chạy CPU nên mất vài phút đến hàng chục phút.", color = VC.text2, fontSize = 12.sp)
        hud.deepForestFineTuneStage?.let { Text("Huấn luyện: $it", color = VC.amber, fontSize = 12.sp) }
        hud.deepForestFineTuneResult?.let { r ->
            val map = if (r.initialMap50 != null && r.finalMap50 != null) "mAP50 ${fmtNum(r.initialMap50, 2)} → ${fmtNum(r.finalMap50, 2)}" else "mAP50 chưa có"
            val rec = if (r.initialRecall != null && r.finalRecall != null) " · recall ${fmtNum(r.initialRecall, 2)} → ${fmtNum(r.finalRecall, 2)}" else ""
            Text("Checkpoint ${r.checkpointId}: $map$rec trên ${r.validationAnnotationsCount} nhãn kiểm định (huấn luyện ${r.trainingAnnotationsCount}, bỏ ${r.droppedAnnotationsCount}). Chỉ đo trên vùng giữ lại của chính ảnh này, chưa kiểm định ngoài thực địa.", color = VC.mint, fontSize = 12.sp)
        }
        if (!VUi.simpleMode) {
            ChipRow(listOf(320, 400, 512), hud.deepForestPatchSize, { "Patch ${it}px" }, { hud.deepForestPatchSize = it })
            ChipRow(listOf(0.15, 0.25), hud.deepForestPatchOverlap, { "Overlap ${(it * 100).toInt()}%" }, { hud.deepForestPatchOverlap = it })
        }

        if (showVietnamPipeline) {
            Spacer(Modifier.height(10.dp))
            Text("🇻🇳 1. Nguồn Dữ liệu Vệ tinh & UAV Độ phân giải cao (<0,5m–1m/pixel)", color = VC.mint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("• OpenAerialMap (OAM) — Việt Nam Collection (3 cm – 10 cm/pixel): Nguồn ảnh Drone/UAV mở công khai tại ĐBSCL, Tây Nguyên và các khu vực lâm nghiệp/sạt lở — rất thích hợp để tách tán cây đơn lẻ.", color = VC.text, fontSize = 12.sp)
            ChipRow(
                OamVietnamPreset.entries.toList(),
                selectedOamPreset,
                { it.shortLabelVi },
                { selectedOamPreset = it },
            )
            Text("Vùng OAM: ${selectedOamPreset.fullNameVi} · Cây trồng trọng điểm: ${selectedOamPreset.focusCropsVi}", color = VC.text2, fontSize = 11.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VButton("Tìm ảnh OAM VN (<50cm)", {
                    scope.launch {
                        oamSearching = true
                        val res = withContext(Dispatchers.IO) {
                            runCatching { DeepForestClient.searchOamVietnam(cfg, selectedOamPreset.presetId, limit = 8) }
                        }
                        oamSearching = false
                        res.onSuccess {
                            oamSearchResult = it
                            onToast("OpenAerialMap (${it.regionNameVi}): ${it.suitableCount}/${it.totalReturned} ảnh đạt chuẩn <0,5m/px.")
                        }.onFailure {
                            onToast("Không kết nối được OAM qua máy chủ: ${it.message}. Bạn có thể bấm Mở OpenAerialMap.org.")
                        }
                    }
                }, primary = false, enabled = cfg.configured && !oamSearching)
                VButton("Mở OpenAerialMap.org ↗", { openUrl(selectedOamPreset.openAerialMapUrl) }, primary = false)
                VButton("PlanetScope (3m) & SkySat (50cm) E&R ↗", {
                    openUrl("https://www.planet.com/markets/education-and-research/")
                }, primary = false)
            }
            Text("• PlanetScope / Planet Labs (Education & Research License): Ảnh vệ tinh chụp hàng ngày toàn lãnh thổ Việt Nam (3m/pixel dùng cho phân vùng tán rừng rộng lớn; xin mẫu SkySat 50cm/pixel để nhận diện tán cây bằng DeepForest).", color = VC.text2, fontSize = 11.sp)

            oamSearchResult?.let { oam ->
                Text(
                    if (oam.liveApiReachable) "Kết quả OAM (${oam.regionNameVi}): ${oam.totalReturned} bộ ảnh UAV (${oam.suitableCount} bộ đạt GSD ≤ 50 cm/px)"
                    else (oam.networkNoteVi ?: "Chưa kết nối trực tiếp tới api.openaerialmap.org."),
                    color = if (oam.liveApiReachable) VC.mint else VC.amber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                oam.results.take(4).forEach { item ->
                    val gsdText = item.gsdCm?.let { "${fmtNum(it, 1)} cm/px" } ?: "chưa rõ GSD"
                    Text("  • ${item.title} ($gsdText · ${item.provider}) ${if (item.suitableForDeepForest) "✅ Đạt chuẩn DeepForest" else "⚠️ GSD thô"}", color = VC.text2, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("📚 2. Bộ Dữ liệu Mẫu / Nghiên cứu Lâm nghiệp & Nông nghiệp Việt Nam", color = VC.mint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("• Global Forest Watch / Hansen Data (UMD/hansen/global_forest_change_2023_v1_11): Bản đồ độ che phủ rừng và tán cây Việt Nam (Landsat/Sentinel) dùng làm lớp mặt nạ (canopy mask) hoặc nhãn tham chiếu vùng lâm nghiệp.", color = VC.text, fontSize = 12.sp)
            Text("• Dữ liệu Ảnh Drone Cây Công nghiệp & Cây Ăn quả (Zenodo / Mendeley Data / Kaggle): Các nghiên cứu đếm cây dừa (Coconut detection), cà phê (Coffee canopy), cao su tại Việt Nam có đính kèm ảnh UAV gốc và tọa độ bounding box.", color = VC.text, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VButton("Zenodo: Coconut UAV VN ↗", { openUrl("https://zenodo.org/search?q=Coconut+tree+detection+UAV+Vietnam") }, primary = false)
                VButton("Zenodo: Coffee Canopy UAV ↗", { openUrl("https://zenodo.org/search?q=Coffee+tree+canopy+UAV") }, primary = false)
                VButton("Mendeley: VN Ag Drone ↗", { openUrl("https://data.mendeley.com/research-data/?search=Vietnam%20Agricultural%20Drone%20Dataset") }, primary = false)
                VButton("GFW Forest Watch ↗", { openUrl("https://www.globalforestwatch.org/") }, primary = false)
            }

            Spacer(Modifier.height(8.dp))
            Text("🛠️ 3. Quy trình 3 Bước Tự Xây dựng Dataset Vườn Việt Nam (xml_to_csv)", color = VC.mint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("• Bước 1 (Thu thập ảnh): Dùng flycam/drone chụp vườn (Tây Nguyên: cà phê/sầu riêng, Miền Tây: dừa/cây ăn quả) hoặc tải ảnh Orthomosaic từ OpenAerialMap VN.\n• Bước 2 (Gán nhãn): Dùng LabelImg hoặc Roboflow khoanh vùng tán cây → xuất định dạng PASCAL VOC (.xml) hoặc YOLO.\n• Bước 3 (Chuyển đổi sang chuẩn CSV của DeepForest): Gồm 6 cột bắt buộc image_path, xmin, ymin, xmax, ymax, label (thay cho dữ liệu smoke test OSBS/SJER).", color = VC.text2, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VButton("Chọn file nhãn .XML (LabelImg/Roboflow)", {
                    pickPascalVocXmls(arrayOf("text/xml", "application/xml", "*/*"))
                }, primary = false, enabled = !csvConverting)
                VButton("Chạy thử xml_to_csv (Mẫu Vườn VN)", {
                    scope.launch {
                        csvConverting = true
                        val res = withContext(Dispatchers.IO) {
                            runCatching {
                                DeepForestClient.convertXmlToDeepForestCsv(
                                    cfg = cfg,
                                    xmlDocs = DeepForestClient.SAMPLE_VIETNAM_XML_DOCS,
                                    valRatio = 0.5,
                                    normalizeLabelToTree = true,
                                )
                            }
                        }
                        csvConverting = false
                        res.onSuccess {
                            csvConversionResult = it
                            onToast("Đã tạo vietnam_train.csv (${it.trainBoxesCount} box) & vietnam_validation.csv (${it.validationBoxesCount} box).")
                        }.onFailure { onToast("Lỗi chuyển đổi mẫu: ${it.message}") }
                    }
                }, primary = false, enabled = !csvConverting)
                if (csvConversionResult != null) {
                    VButton("Lưu vietnam_train.csv", { exportTrainCsv("vietnam_train.csv") }, primary = false)
                    VButton("Lưu vietnam_validation.csv", { exportValCsv("vietnam_validation.csv") }, primary = false)
                }
            }
            csvConversionResult?.let { csvRes ->
                Text(
                    "✅ Kết quả xml_to_csv: Tổng ${csvRes.totalBoxes} box hợp lệ trên ${csvRes.uniqueImagesCount} ảnh (Train: ${csvRes.trainBoxesCount} box · Validation: ${csvRes.validationBoxesCount} box · Chế độ chia: ${csvRes.splitMode})",
                    color = VC.mint,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    csvRes.fullCsv.trim(),
                    color = VC.text2,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
