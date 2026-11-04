package com.example.test_tablet.ui.vwing

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun <I, O> rememberTofOptionalLauncher(
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

private enum class TofHubTab(val labelVi: String) {
    FIVE_CLASSES("🌳 1. 5 Lớp TOF & Kiểm kê Cây"),
    SIX_MODELS("🧠 2. 6 Mô hình (FT-UNetFormer)"),
    SLIDING_WINDOW("🪟 3. Cửa sổ trượt & Keep-Ratio"),
    EVALUATOR_MATRIX("📊 4. Evaluator & Ma trận Nhầm lẫn"),
}

private fun parseHexColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(VC.mint)

/**
 * Thẻ tích hợp toàn diện Moerizzy/TOFMapper ("Mapping and classification of trees outside forests using deep learning",
 * Science of Remote Sensing, Vol. 14, 2026) cho phép phân đoạn ngữ nghĩa 5 lớp (Background, Forest, Patch, Linear, Tree),
 * chọn 6 mô hình (FT-UNetFormer, DC-Swin, ABCNet, LSKNet, BANet, U-Net), tùy chỉnh cửa sổ trượt keep_ratio,
 * gộp lớp nhị phân TOF (reclass_tof.py) và xuất đa giác GeoJSON / CSV ma trận nhầm lẫn.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TofMapperHubCard(
    hud: RegionHudState,
    cfg: SatelliteConfig,
    onSelectOverlay: (MapOverlay?) -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val openPts = remember(hud.points) { Geo.open(hud.points) }

    var activeTab by remember { mutableStateOf(TofHubTab.FIVE_CLASSES) }
    var showAdvancedTof by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(TofModelPreset.FTUNETFORMER) }
    var patchSize by remember { mutableStateOf(1024) }
    var stride by remember { mutableStateOf(256) }
    var keepRatio by remember { mutableStateOf(0.70) }
    var ttaMode by remember { mutableStateOf(TofTtaMode.D4) }
    var reclassBinaryTof by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<TofSegmentReport?>(null) }

    fun runSegmentation(applyOverlayToMap: Boolean) {
        if (openPts.size < 3) {
            err = "Cần khoanh vùng vườn ít nhất 3 đỉnh để chạy phân đoạn TOFMapper."
            return
        }
        busy = true
        err = null
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                TofMapperClient.segmentRegion(
                    cfg = cfg,
                    openPoints = openPts,
                    modelPreset = selectedModel,
                    patchSize = patchSize,
                    stride = stride,
                    keepRatio = keepRatio,
                    marginPx = 500,
                    ttaMode = ttaMode,
                    reclassBinaryTof = reclassBinaryTof,
                    treeSpacingM = 8.0,
                    imageId = hud.deepForestImage?.imageId,
                )
            }
            res.onSuccess { rep ->
                report = rep
                if (applyOverlayToMap) {
                    val remoteBmp = rep.overlayUrl?.let { url ->
                        withContext(Dispatchers.IO) {
                            SatelliteClient.image(cfg, url).getOrNull()?.let { bytes ->
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                            }
                        }
                    }
                    val finalBmp = remoteBmp ?: rep.localOverlayBitmap?.asImageBitmap()
                    if (finalBmp != null) {
                        val modeTag = if (rep.reclassBinaryTof) "Gộp TOF nhị phân (reclass_tof.py)" else "5 lớp ngữ nghĩa (Forest/Patch/Linear/Tree)"
                        onSelectOverlay(
                            MapOverlay(
                                bounds = rep.bounds,
                                bitmap = finalBmp,
                                alpha = 0.82f,
                                label = "TOFMapper · ${rep.modelPreset.shortTitle} · $modeTag · ${rep.treeAccounting.orchardFruitTreesTotal} cây ăn trái",
                            ),
                        )
                        onToast("Đã phủ lớp phân đoạn TOFMapper (${rep.modelPreset.shortTitle}) lên bản đồ khu vườn.")
                    }
                }
            }.onFailure { e ->
                err = e.message ?: "Không chạy được phân đoạn TOFMapper."
            }
            busy = false
        }
    }

    LaunchedEffect(openPts, selectedModel, reclassBinaryTof) {
        if (openPts.size >= 3 && report == null) {
            runSegmentation(applyOverlayToMap = false)
        }
    }

    val exportGeoJson = rememberTofOptionalLauncher(ActivityResultContracts.CreateDocument("application/geo+json")) { uri ->
        val json = report?.geojsonString
        if (uri != null && !json.isNullOrBlank()) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                            ?: error("Không mở được tệp đầu ra.")
                    }
                }
                onToast(saved.fold({ "Đã xuất đa giác Vector TOFMapper (.geojson)." }, { "Lỗi xuất GeoJSON: ${it.message}" }))
            }
        }
    }

    val exportMatrixCsv = rememberTofOptionalLauncher(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val csv = report?.evaluator?.normalizedErrorMatrixCsv
        if (uri != null && !csv.isNullOrBlank()) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                            ?: error("Không mở được tệp CSV.")
                    }
                }
                onToast(saved.fold({ "Đã lưu ma trận nhầm lẫn chuẩn hóa (Normalized Error Matrix CSV)." }, { "Lỗi lưu CSV: ${it.message}" }))
            }
        }
    }

    val openUrl: (String) -> Unit = { url ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { onToast("Không mở được liên kết: $url") }
    }

    VCard(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Eyebrow("PHÂN LOẠI CÂY NGOÀI RỪNG · TOFMAPPER")
                Text(
                    "🌳 Tách Cây Ăn Trái, Cụm Khép Tán & Hàng Chắn Gió",
                    color = VC.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "Tách riêng cây ăn trái thu hoạch khỏi hàng cây chắn gió và rừng giáp ranh.",
                    color = VC.text2,
                    fontSize = 12.5.sp,
                )
            }
            if (busy) {
                CircularProgressIndicator(Modifier.size(22.dp), color = VC.mint, strokeWidth = 2.dp)
            }
        }

        err?.let {
            Spacer(Modifier.height(6.dp))
            Text("⚠ $it", color = VC.red, fontSize = 12.sp)
        }

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VButton(
                if (busy) "Đang phân đoạn…" else "🗺️ Phủ 5 Lớp TOF lên Bản đồ",
                { runSegmentation(applyOverlayToMap = true) },
                enabled = !busy && openPts.size >= 3,
            )
            VButton(
                if (reclassBinaryTof) "✓ Đang gộp TOF" else "Gộp nhị phân TOF",
                {
                    reclassBinaryTof = !reclassBinaryTof
                    runSegmentation(applyOverlayToMap = true)
                },
                primary = false,
                enabled = !busy,
            )
            VButton(
                if (showAdvancedTof) "Ẩn tùy chọn kỹ thuật ▴" else "⚙️ Mô hình & Kỹ thuật ▾",
                { showAdvancedTof = !showAdvancedTof },
                primary = false,
            )
        }

        val rep = report
        if (rep != null) {
            Spacer(Modifier.height(10.dp))
            if (!showAdvancedTof) {
                TofFiveClassesSection(rep)
            } else {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VButton(
                        "Xuất GeoJSON",
                        { exportGeoJson("vwing-tofmapper-${selectedModel.modelId}.geojson") },
                        primary = false,
                    )
                    VButton(
                        "Xuất Error Matrix CSV",
                        { exportMatrixCsv("tofmapper-error-matrix-${selectedModel.modelId}.csv") },
                        primary = false,
                    )
                    VButton(
                        "GitHub TOFMapper ↗",
                        { openUrl(TofMapperClient.REPO_URL) },
                        primary = false,
                    )
                }
                ChipRow(
                    TofHubTab.entries.toList(),
                    activeTab,
                    { it.labelVi },
                    { activeTab = it },
                )
                Spacer(Modifier.height(8.dp))
                when (activeTab) {
                    TofHubTab.FIVE_CLASSES -> TofFiveClassesSection(rep)
                    TofHubTab.SIX_MODELS -> TofSixModelsSection(
                        selectedModel = selectedModel,
                        onSelectModel = { m ->
                            selectedModel = m
                            runSegmentation(applyOverlayToMap = false)
                        },
                    )
                    TofHubTab.SLIDING_WINDOW -> TofSlidingWindowSection(
                        rep = rep,
                        patchSize = patchSize,
                        stride = stride,
                        keepRatio = keepRatio,
                        ttaMode = ttaMode,
                        onChangePatchSize = { patchSize = it; runSegmentation(false) },
                        onChangeStride = { stride = it; runSegmentation(false) },
                        onChangeKeepRatio = { keepRatio = it; runSegmentation(false) },
                        onChangeTta = { ttaMode = it; runSegmentation(false) },
                    )
                    TofHubTab.EVALUATOR_MATRIX -> TofEvaluatorSection(rep)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TofFiveClassesSection(rep: TofSegmentReport) {
    val acc = rep.treeAccounting
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MiniFact(
                "🍈 Cây ăn trái",
                "${acc.orchardFruitTreesTotal} cây",
                Modifier.weight(1f),
            )
            MiniFact(
                "🌳 Gốc đơn lẻ",
                "${acc.individualTreesCount} cây",
                Modifier.weight(1f),
            )
            MiniFact(
                "🟨 Cụm khép tán",
                "${acc.patchClusterTreesCount} cây",
                Modifier.weight(1f),
            )
            MiniFact(
                "🟩 Hàng chắn gió",
                "${acc.windbreakLinearTreesCount} cây (${fmtNum(acc.windbreakPerimeterShieldPct, 0)}% bờ)",
                Modifier.weight(1f),
            )
        }

        if (rep.reclassBinaryTof) {
            Text(
                "🔄 Đang gộp 3 lớp cây ngoài rừng (Patch + Linear + Tree → TOF): ${fmtNum(rep.tofCombinedPct, 1)}% diện tích (${fmtNum(rep.tofCombinedAreaM2, 0)} m²).",
                color = VC.cyan,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        rep.classBreakdown.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(VC.elevated)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(parseHexColor(item.hexColor))
                        .border(1.dp, VC.border, CircleShape),
                )
                Text(
                    item.labelVi.substringBefore(" ("),
                    color = VC.text,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${fmtNum(item.coveragePct, 0)}% · ${fmtNum(item.areaHa, 2)} ha",
                    color = VC.mint,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun TofSixModelsSection(
    selectedModel: TofModelPreset,
    onSelectModel: (TofModelPreset) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "2. Bộ 6 Kiến trúc Deep Learning trong TOFMapper (Chọn mô hình để suy luận & so sánh):",
            color = VC.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        ChipRow(
            TofModelPreset.entries.toList(),
            selectedModel,
            { it.shortTitle },
            onSelectModel,
        )

        TofModelPreset.entries.forEach { m ->
            val isSelected = m == selectedModel
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) VC.bg else VC.elevated)
                    .border(
                        width = if (isSelected) 1.5.dp else 0.5.dp,
                        color = if (isSelected) VC.mint else VC.border,
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable { onSelectModel(m) }
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        (if (isSelected) "✓ " else "") + m.fullTitle,
                        color = if (isSelected) VC.mint else VC.text,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "mIoU ${fmtNum(m.publishedMiou * 100, 1)}% · mF1 ${fmtNum(m.publishedMf1 * 100, 1)}%",
                        color = VC.amber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text("Kiến trúc: ${m.architecture}", color = VC.text2, fontSize = 11.5.sp)
                Text("Hàm mất mát: ${m.lossFunction}", color = VC.text2, fontSize = 11.5.sp)
                Text(
                    "F1 từng lớp: Forest ${fmtNum(m.forestF1 * 100, 1)}% · Linear ${fmtNum(m.linearF1 * 100, 1)}% · Tree ${fmtNum(m.treeF1 * 100, 1)}% · Patch ${fmtNum(m.patchF1 * 100, 1)}%",
                    color = VC.cyan,
                    fontSize = 11.5.sp,
                )
                Text(m.recommendedVi, color = VC.text, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun TofSlidingWindowSection(
    rep: TofSegmentReport,
    patchSize: Int,
    stride: Int,
    keepRatio: Double,
    ttaMode: TofTtaMode,
    onChangePatchSize: (Int) -> Unit,
    onChangeStride: (Int) -> Unit,
    onChangeKeepRatio: (Double) -> Unit,
    onChangeTta: (TofTtaMode) -> Unit,
) {
    val sw = rep.slidingWindow
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "3. Suy luận Cửa sổ trượt Chồng lấn & Giữ Vùng lõi Keep-Ratio (inference_with_overlap.py)",
            color = VC.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Thuật toán của TOFMapper chỉ cộng dồn xác suất Softmax ở phần lõi trung tâm `inner_size = int(patch_size * keep_ratio)` và cắt bỏ viền ngoài `outer_margin = (patch_size - inner_size) // 2` để triệt tiêu hoàn toàn vết rách tán cây ở biên mảnh ghép.",
            color = VC.text2,
            fontSize = 12.sp,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChipRow(listOf(512, 1024), patchSize, { "Patch ${it}px" }, onChangePatchSize)
            ChipRow(listOf(128, 256, 512), stride, { "Stride ${it}px" }, onChangeStride)
            ChipRow(listOf(0.60, 0.70, 0.80), keepRatio, { "Keep ${(it * 100).toInt()}%" }, onChangeKeepRatio)
        }
        ChipRow(
            TofTtaMode.entries.toList(),
            ttaMode,
            { it.labelVi },
            onChangeTta,
        )

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(VC.elevated)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Thông số lưới cửa sổ trượt hiện tại:",
                color = VC.mint,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "• Kích thước cửa sổ (patch_size): ${sw.patchSizePx}×${sw.patchSizePx} px · Bước trượt (stride): ${sw.stridePx} px (Chồng lấn ${fmtNum(sw.overlapPct, 1)}%)",
                color = VC.text,
                fontSize = 12.sp,
            )
            Text(
                "• Vùng lõi giữ lại (inner_keep_size): ${sw.innerKeepSizePx}×${sw.innerKeepSizePx} px (keep_ratio = ${fmtNum(sw.keepRatio * 100, 0)}%) · Viền loại bỏ mỗi cạnh (outer_margin): ${sw.outerMarginDiscardedPx} px",
                color = VC.text,
                fontSize = 12.sp,
            )
            Text(
                "• Ghép mảnh lân cận (combine_neighbors margin): ${sw.neighborStitchMarginPx} px · Tổng số cửa sổ suy luận: ${sw.totalSlidingWindows} ô (${sw.gridCols}×${sw.gridRows})",
                color = VC.text2,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun TofEvaluatorSection(rep: TofSegmentReport) {
    val ev = rep.evaluator
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "4. Bộ Đánh giá Không gian Evaluator & Ma trận Nhầm lẫn Chuẩn hóa (tools/metric.py & tof_test.py)",
            color = VC.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MiniFact("mIoU (Thực vật)", "${fmtNum(ev.miouVegetation * 100, 1)}%", Modifier.weight(1f))
            MiniFact("mF1 (Thực vật)", "${fmtNum(ev.mf1Vegetation * 100, 1)}%", Modifier.weight(1f))
            MiniFact("OA (Độ chính xác)", "${fmtNum(ev.overallAccuracy * 100, 1)}%", Modifier.weight(1f))
            MiniFact("FWIoU", "${fmtNum(ev.fwIou * 100, 1)}%", Modifier.weight(1f))
        }

        ev.perClass.forEach { c ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(VC.elevated)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "[${c.classId}] ${c.className}",
                    color = VC.text,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(110.dp),
                )
                Text(
                    "IoU: ${fmtNum(c.iou * 100, 1)}% · F1: ${fmtNum(c.f1 * 100, 1)}% · Prec: ${fmtNum(c.precision * 100, 1)}% · Rec: ${fmtNum(c.recall * 100, 1)}%",
                    color = VC.mint,
                    fontSize = 12.sp,
                )
            }
        }

        if (ev.normalizedErrorMatrixCsv.isNotBlank()) {
            Text(
                "Ma trận nhầm lẫn chuẩn hóa theo hàng (Normalized Error Matrix CSV — tof_test.py):",
                color = VC.text2,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                ev.normalizedErrorMatrixCsv,
                color = VC.text,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(VC.bg)
                    .padding(10.dp),
            )
        }
    }
}
