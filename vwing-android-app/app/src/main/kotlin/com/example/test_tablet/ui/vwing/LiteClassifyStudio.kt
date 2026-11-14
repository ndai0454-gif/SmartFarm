package com.example.test_tablet.ui.vwing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun parseHexColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(VC.lime)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiteClassifyStudioCard(
    c: VCtx,
    points: List<GeoPoint>,
    cropMode: String,
) {
    val context = LocalContext.current
    var activeTab by remember { mutableIntStateOf(1) }
    var flightAltitudeM by remember { mutableDoubleStateOf(60.0) }
    var slicCompactness by remember { mutableDoubleStateOf(0.15) }
    var growTolerance by remember { mutableDoubleStateOf(0.85) }
    var selectedSegId by remember { mutableIntStateOf(1) }
    var showQmlDialog by remember { mutableStateOf(false) }
    var userLabels by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }

    var report by remember(points, cropMode, flightAltitudeM, slicCompactness) {
        mutableStateOf(
            LiteClassifyObiaEngine.runFullPipeline(
                points = points,
                cropMode = cropMode,
                slicCompactness = slicCompactness,
                flightAltitudeM = flightAltitudeM,
            )
        )
    }

    LaunchedEffect(points, cropMode, flightAltitudeM, slicCompactness) {
        val srvReport = withContext(Dispatchers.IO) {
            LiteClassifyObiaEngine.fetchFromServerOrFallback(
                cfg = SatelliteSettings(context.filesDir).load(),
                points = points,
                cropMode = cropMode,
                slicCompactness = slicCompactness,
                flightAltitudeM = flightAltitudeM,
            )
        }
        report = srvReport
    }

    val grownSet = remember(report, selectedSegId, growTolerance) {
        LiteClassifyObiaEngine.growSelection(report.segments, selectedSegId, growTolerance)
    }

    val selectedSeg = remember(report, selectedSegId) {
        report.segments.firstOrNull { it.segmentId == selectedSegId } ?: report.segments.first()
    }

    VCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "VWing OBIA Studio — Phân tích ảnh Drone hướng đối tượng",
                    color = VC.muted,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "Phân loại lớp phủ vườn OBIA 4 bước (SLIC 24 đặc trưng · K-Means/DINOv2 · Random Forest · Olofsson 2014)",
                    color = VC.text,
                    fontSize = 17.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Based on Lite Classify by L. D. Barua (LeoBar) — github.com/leobarua/liteclassify (DOI: 10.5281/zenodo.23462352)",
                    color = VC.muted,
                    fontSize = 12.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Tag("VWing OBIA", VC.mint)
                Tag(
                    if (report.isFromServer) "⚡ ĐỘNG CƠ OBIA THẬT (SERVER)" else "DỮ LIỆU MINH HỌA (OFFLINE)",
                    if (report.isFromServer) VC.lime else VC.amber,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        // Segmented 4-Tab Bar chuẩn Lite Classify & Taste Skill §3.3
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .clip(RoundedCornerShape(10.dp))
                .background(VC.elevated)
                .border(1.dp, VC.border, RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(
                1 to "1. SLIC & 24 đặc trưng (WebODM/DSM)",
                2 to "2. Discover classes (DINOv2 5m/10m)",
                3 to "3. Classification (JM · RF · ExG)",
                4 to "4. Accuracy assessment (Olofsson 2014)",
            ).forEach { (tabId, title) ->
                val sel = activeTab == tabId
                Box(
                    Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (sel) VC.card else Color.Transparent)
                        .then(
                            if (sel) Modifier.border(1.dp, VC.border, RoundedCornerShape(7.dp))
                            else Modifier
                        )
                        .clickable { activeTab = tabId }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        title,
                        color = if (sel) VC.text else VC.muted,
                        fontSize = 13.sp,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ═══════════════════════════════════════════════════════════════════════
        // TAB 1: LOAD, VIEW & SLIC SEGMENT (24 FEATURES + WEBODM + HAG 40M)
        // ═══════════════════════════════════════════════════════════════════════
        if (activeTab == 1) {
            val odm = report.droneSummary
            TasteKpiStrip(
                wide = c.wide,
                cells = listOf(
                    TasteKpiCell(
                        label = "Chuyến bay WebODM Drone",
                        value = "${odm.photoCount}",
                        unit = "ảnh (${odm.flightAltitudeM.toInt()}m)",
                        sub = odm.cameraModel,
                        accentColor = VC.mint,
                    ),
                    TasteKpiCell(
                        label = "Độ phân giải không gian GSD",
                        value = "${odm.estimatedGsdCmPerPx}",
                        unit = "cm/px",
                        sub = "Orthomosaic RGB (${odm.orthoResolutionCm} cm)",
                        accentColor = VC.text2,
                    ),
                    TasteKpiCell(
                        label = "Mô hình độ cao số DSM & HAG",
                        value = "${odm.dsmResolutionCm}",
                        unit = "cm/px",
                        sub = "Mặt đất chuẩn ${odm.groundOpeningWindowM.toInt()}m",
                        accentColor = VC.cyan,
                    ),
                    TasteKpiCell(
                        label = "Siêu điểm ảnh SLIC",
                        value = "${report.segments.size}",
                        unit = "đối tượng",
                        sub = "24 đặc trưng / ô",
                        accentColor = VC.mint,
                    ),
                )
            )

            Spacer(Modifier.height(10.dp))
            // Thanh trượt chỉnh Độ cao bay UAV (GSD) và Độ chặt SLIC Compactness
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Độ cao bay Drone: ${flightAltitudeM.toInt()}m (GSD ${odm.estimatedGsdCmPerPx} cm/px)", color = VC.text2, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    Slider(
                        value = flightAltitudeM.toFloat(),
                        onValueChange = { flightAltitudeM = it.toDouble() },
                        valueRange = 35f..110f,
                        colors = SliderDefaults.colors(thumbColor = VC.lime, activeTrackColor = VC.lime),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("SLIC Compactness: ${"%.2f".format(slicCompactness)} · Region Grow Tol: ${"%.2f".format(growTolerance)}", color = VC.text2, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    Slider(
                        value = growTolerance.toFloat(),
                        onValueChange = { growTolerance = it.toDouble() },
                        valueRange = 0.40f..1.60f,
                        colors = SliderDefaults.colors(thumbColor = VC.cyan, activeTrackColor = VC.cyan),
                    )
                }
            }

            // Bản đồ tương tác các đối tượng SLIC Superpixel + Region Growing (BFS)
            SectionLabel("Bản đồ Phân đoạn Đối tượng SLIC & Lan truyền vùng chọn BFS (Chạm vào từng ô đối tượng để đo 24 đặc trưng)")
            ObiaSuperpixelMapCanvas(
                segments = report.segments,
                selectedSegId = selectedSegId,
                grownIds = grownSet,
                userLabels = userLabels,
                onSelectSeg = { segId -> selectedSegId = segId },
            )

            Spacer(Modifier.height(8.dp))
            // Chú giải 7 Lớp phủ Nông nghiệp OBIA
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LITE_CLASSIFY_CLASSES.forEach { cls ->
                    val col = parseHexColor(cls.hexColor)
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(VC.elevated)
                            .border(1.dp, col.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(col))
                        Text(cls.nameVi, color = VC.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            // Bảng 24 Đặc trưng OBIA + DINOv2 của Segment đang chọn
            val clsDef = LITE_CLASSIFY_CLASSES.firstOrNull { it.id == (userLabels[selectedSeg.segmentId] ?: selectedSeg.predictedClassId) }
                ?: LITE_CLASSIFY_CLASSES.first()
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(VC.elevated)
                    .border(1.dp, parseHexColor(clsDef.hexColor), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "🔍 Đối tượng #${selectedSeg.segmentId} (Hàng ${selectedSeg.gridRow + 1}, Cột ${selectedSeg.gridCol + 1}) · ${clsDef.nameVi}",
                        color = VC.text,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f),
                    )
                    Tag("Lan vùng BFS: ${grownSet.size} segs", VC.cyan)
                }
                Text(
                    "1️⃣ Phổ RGB & Chỉ số thực vật (10 biến): R=${selectedSeg.rMean}±${selectedSeg.rStd} · G=${selectedSeg.gMean}±${selectedSeg.gStd} · B=${selectedSeg.bMean}±${selectedSeg.bStd} · ExG=${selectedSeg.exgMean} · VARI=${selectedSeg.variMean} · NGRDI=${selectedSeg.ngrdiMean} · Brightness=${selectedSeg.brightnessMean}",
                    color = VC.text2,
                    fontSize = 12.5.sp,
                )
                Text(
                    "2️⃣ Kết cấu GLCM 4 góc & Hình học (8 biến): Contrast=${selectedSeg.glcmContrast} · Homogeneity=${selectedSeg.glcmHomogeneity} · Entropy=${selectedSeg.glcmEntropy} · Energy=${selectedSeg.glcmEnergy} · Diện tích=${selectedSeg.areaM2}m² · Chu vi=${selectedSeg.perimeterM}m · Circularity=${selectedSeg.circularity} · Convexity=${selectedSeg.convexity}",
                    color = VC.text2,
                    fontSize = 12.5.sp,
                )
                Text(
                    "3️⃣ Đồ thị kề RAG, Độ cao DSM & Chiều cao HAG 40m (6 biến + DINOv2): ΔBright_RAG=${selectedSeg.brightnessRelNeighbors} · ΔDSM_RAG=${selectedSeg.dsmRelNeighbors}m · DSM=${selectedSeg.dsmMeanM}m · Slope=${selectedSeg.slopeMeanDeg}° · Chiều cao trên đất HAG=${selectedSeg.hagMeanM}m · DINOv2(5m/10m)=(${selectedSeg.dino5mPc1}, ${selectedSeg.dino10mPc1})",
                    color = VC.lime,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // ═══════════════════════════════════════════════════════════════════════
        // TAB 2: DISCOVER CLASSES (DINOV2 VIT-S/14 5M & 10M + K-MEANS CLUSTERING)
        // ═══════════════════════════════════════════════════════════════════════
        if (activeTab == 2) {
            Text(
                "Khám phá Lớp phủ Tự động bằng DINOv2 ViT-S/14 (Cửa sổ 5m bản thân đối tượng & 10m ngữ cảnh xung quanh) + Phân cụm K-Means",
                color = VC.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                "Mỗi đối tượng SLIC được trích xuất vector 384 chiều ở 2 tỷ lệ 5m & 10m kết hợp 24 đặc trưng hình thái, sau đó phân cụm K-Means để gộp/tách thành các lớp phủ thực tế trong vườn.",
                color = VC.text2,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                report.dinoClusters.forEach { cl ->
                    val clsDef = LITE_CLASSIFY_CLASSES.firstOrNull { it.id == cl.assignedClassId } ?: LITE_CLASSIFY_CLASSES.first()
                    val col = parseHexColor(clsDef.hexColor)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(VC.elevated)
                            .border(1.dp, col.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Cụm DINOv2 #${cl.clusterId} → ${clsDef.nameVi}",
                                color = VC.text,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                "${cl.segmentCount} đối tượng · Diện tích: ${cl.areaM2} m² · ExG TB: ${cl.meanExg} · Chiều cao HAG TB: ${cl.meanHagM} m",
                                color = VC.text2,
                                fontSize = 12.5.sp,
                            )
                        }
                        Tag("K-Means G${cl.clusterId}", col)
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════════════
        // TAB 3: CLASSIFICATION (JM SEPARABILITY + ACTIVE LEARNING + HYBRID EXG + QGIS)
        // ═══════════════════════════════════════════════════════════════════════
        if (activeTab == 3) {
            val gr = report.greennessRefinement
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniFact("NGƯỠNG EXG TỐI ƯU", "${gr.suggestedExgCutoff} (Score ${gr.balancedScore})", Modifier.weight(1f))
                MiniFact("DẢI CỎ HẸP THU HỒI", "+${gr.grassStripRecoveredM2} m²", Modifier.weight(1f))
                MiniFact("ĐIỂM ẢNH CHUYỂN XANH", "${gr.pixelsMovedToGreen} px", Modifier.weight(1f))
            }

            Spacer(Modifier.height(10.dp))
            SectionLabel("1. Học chủ động 'What to Label Next' (Các đối tượng có độ tự tin Random Forest thấp nhất cần gán nhãn)")
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                report.activeLearningQueue.forEach { seg ->
                    val cls = LITE_CLASSIFY_CLASSES.firstOrNull { it.id == (userLabels[seg.segmentId] ?: seg.predictedClassId) }
                        ?: LITE_CLASSIFY_CLASSES.first()
                    Column(
                        Modifier
                            .width(210.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(VC.elevated)
                            .border(1.dp, VC.amber, RoundedCornerShape(12.dp))
                            .clickable {
                                selectedSegId = seg.segmentId
                                val nextClassId = ((userLabels[seg.segmentId] ?: seg.predictedClassId) % LITE_CLASSIFY_CLASSES.size) + 1
                                userLabels = userLabels + (seg.segmentId to nextClassId)
                                c.toast("Đã gán nhãn mẫu #${seg.segmentId} và huấn luyện lại Random Forest!")
                            }
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text("Đối tượng #${seg.segmentId} (Độ tự tin ${(seg.rfConfidence * 100).toInt()}%)", color = VC.amber, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
                        Text(cls.nameVi, color = VC.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("ExG: ${seg.exgMean} · HAG: ${seg.hagMeanM}m · Chạm đổi nhãn", color = VC.text2, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            SectionLabel("2. Khoảng cách Tách biệt Lớp Jeffries–Matusita (JM 0.00..2.00 · Kiểm định chéo Shrinkage LDA)")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                report.jmSeparability.take(6).forEach { pair ->
                    val jmColor = when {
                        pair.jmDistance >= 1.85 -> VC.lime
                        pair.jmDistance >= 1.35 -> VC.amber
                        else -> VC.red
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(VC.elevated)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${pair.classAName} ↔ ${pair.classBName}", color = VC.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Đặc trưng phân tách mạnh nhất: ${pair.topFeature}", color = VC.text2, fontSize = 12.sp)
                        }
                        Tag("JM = ${pair.jmDistance} / 2.00", jmColor)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VButton("Huấn luyện lại Random Forest + Hybrid ExG", {
                    c.toast("Đã chạy Random Forest (24 đặc trưng + DINOv2) & tinh chỉnh Hybrid ExG!")
                })
                VButton("Xem / xuất style QGIS (.qml)", { showQmlDialog = true }, primary = false)
            }
        }

        // ═══════════════════════════════════════════════════════════════════════
        // TAB 4: ACCURACY ASSESSMENT (OLOFSSON ET AL. 2014 & STEHMAN 2014)
        // ═══════════════════════════════════════════════════════════════════════
        if (activeTab == 4) {
            val acc = report.accuracyReport
            TasteKpiStrip(
                wide = c.wide,
                cells = listOf(
                    TasteKpiCell(
                        label = "Điểm tin cậy ước tính (OA)",
                        value = "${acc.overallAccuracy}",
                        unit = "% (±${acc.oaCi95}%)",
                        sub = if (report.isFromServer) "Trọng số diện tích Olofsson 2014 (Server)" else "Mô phỏng phân tầng Olofsson 2014",
                        accentColor = VC.mint,
                    ),
                    TasteKpiCell(
                        label = "Số điểm mẫu kiểm định phân tầng",
                        value = "${acc.totalSamplePoints}",
                        unit = "điểm",
                        sub = "Random seed #${acc.seed}",
                        accentColor = VC.text2,
                    ),
                    TasteKpiCell(
                        label = "Tổng diện tích khu vườn",
                        value = "${acc.totalAreaHa}",
                        unit = "ha",
                        sub = "7 lớp phủ nông nghiệp",
                        accentColor = VC.cyan,
                    ),
                )
            )

            Spacer(Modifier.height(10.dp))
            SectionLabel("Bảng Ước lượng Diện tích Không chệch & Khoảng tin cậy 95% CI theo Olofsson et al. (2014)")
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                acc.classEstimates.forEach { est ->
                    val col = parseHexColor(est.classDef.hexColor)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(VC.elevated)
                            .border(1.dp, col.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(est.classDef.nameVi, color = VC.text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                            Tag("${est.estimatedAreaHa} ± ${est.ci95Ha} ha (95% CI)", col)
                        }
                        Text(
                            "Diện tích bản đồ: ${est.mappedAreaHa} ha · User's Acc (UA): ${est.usersAccuracy}% (±${est.uaCi95}%) · Producer's Acc (PA): ${est.producersAccuracy}% (±${est.paCi95}%) · Mẫu: ${est.samplePoints} điểm",
                            color = VC.text2,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }

    if (showQmlDialog) {
        AlertDialog(
            onDismissRequest = { showQmlDialog = false },
            containerColor = VC.card,
            title = { Text("Bảng màu chuẩn QGIS (.qml) — Lite Classify OBIA", color = VC.text, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        report.qgisQmlPreview,
                        color = VC.lime,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            },
            confirmButton = {
                VButton("Đóng", { showQmlDialog = false })
            },
            dismissButton = {
                TextButton(onClick = { showQmlDialog = false }) { Text("Hủy", color = VC.text2) }
            },
        )
    }
}

@Composable
private fun ObiaSuperpixelMapCanvas(
    segments: List<ObiaSegmentFeatures>,
    selectedSegId: Int,
    grownIds: Set<Int>,
    userLabels: Map<Int, Int>,
    onSelectSeg: (Int) -> Unit,
) {
    val maxRow = (segments.maxOfOrNull { it.gridRow } ?: 11) + 1
    val maxCol = (segments.maxOfOrNull { it.gridCol } ?: 13) + 1

    Box(
        Modifier
            .fillMaxWidth()
            .height(270.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF06150E))
            .border(1.5.dp, VC.lime.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(segments) {
                    detectTapGestures { tap ->
                        val cellW = size.width / maxCol
                        val cellH = size.height / maxRow
                        val col = (tap.x / cellW).toInt().coerceIn(0, maxCol - 1)
                        val row = (tap.y / cellH).toInt().coerceIn(0, maxRow - 1)
                        val hit = segments.firstOrNull { it.gridRow == row && it.gridCol == col }
                        if (hit != null) onSelectSeg(hit.segmentId)
                    }
                }
        ) {
            val cellW = size.width / maxCol
            val cellH = size.height / maxRow

            segments.forEach { seg ->
                val clsId = userLabels[seg.segmentId] ?: seg.predictedClassId
                val clsDef = LITE_CLASSIFY_CLASSES.firstOrNull { it.id == clsId } ?: LITE_CLASSIFY_CLASSES.first()
                val baseColor = parseHexColor(clsDef.hexColor)
                val isSelected = seg.segmentId == selectedSegId
                val isGrown = seg.segmentId in grownIds

                val left = seg.gridCol * cellW
                val top = seg.gridRow * cellH

                drawRect(
                    color = baseColor.copy(alpha = if (isSelected) 0.95f else if (isGrown) 0.78f else 0.52f),
                    topLeft = Offset(left + 1.5f, top + 1.5f),
                    size = Size(cellW - 3f, cellH - 3f),
                )

                if (isGrown) {
                    drawRect(
                        color = if (isSelected) Color.White else Color(0xFF38BDF8),
                        topLeft = Offset(left + 1.5f, top + 1.5f),
                        size = Size(cellW - 3f, cellH - 3f),
                        style = Stroke(width = if (isSelected) 4f else 2f),
                    )
                }
            }
        }
    }
}
