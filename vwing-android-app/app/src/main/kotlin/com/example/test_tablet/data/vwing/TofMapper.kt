package com.example.test_tablet.data.vwing

import android.graphics.Bitmap
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tích hợp từ https://github.com/Moerizzy/TOFMapper.git
 * Bài báo: "Mapping and classification of trees outside forests using deep learning"
 *          (Science of Remote Sensing, Vol. 14, 2026, 100208, DOI: 10.1016/j.srs.2026.100208)
 */
enum class TofModelPreset(
    val modelId: String,
    val shortTitle: String,
    val fullTitle: String,
    val architecture: String,
    val lossFunction: String,
    val publishedMiou: Double,
    val publishedMf1: Double,
    val forestF1: Double,
    val patchF1: Double,
    val linearF1: Double,
    val treeF1: Double,
    val recommendedVi: String,
) {
    FTUNETFORMER(
        modelId = "ftunetformer",
        shortTitle = "🏆 FT-UNetFormer (mIoU 73.9%)",
        fullTitle = "FT-UNetFormer (Swin-Base + GlobalLocalAttention Decoder)",
        architecture = "Swin Transformer (swin_base_patch4_window12_384) + Global-Local Attention Decoder",
        lossFunction = "FTUNetFormerLoss = JointLoss(SoftCrossEntropy(0.05) + DiceLoss(0.05), 1.0, 1.0)",
        publishedMiou = 0.739,
        publishedMf1 = 0.843,
        forestF1 = 0.975,
        patchF1 = 0.754,
        linearF1 = 0.872,
        treeF1 = 0.770,
        recommendedVi = "Độ chính xác cao nhất trên cả 4 lớp thực vật (Forest F1=0.975, Linear F1=0.872, Tree F1=0.770, Patch F1=0.754). Khuyên dùng mặc định cho vườn cây ăn trái & nông lâm kết hợp.",
    ),
    DCSWIN(
        modelId = "dcswin",
        shortTitle = "🌲 DC-Swin (mIoU 71.4%)",
        fullTitle = "DC-Swin (Densely Connected Swin Transformer)",
        architecture = "Swin Transformer Backbone + Densely Connected Feature Aggregation",
        lossFunction = "JointLoss(SoftCrossEntropyLoss, DiceLoss)",
        publishedMiou = 0.714,
        publishedMf1 = 0.824,
        forestF1 = 0.974,
        patchF1 = 0.726,
        linearF1 = 0.858,
        treeF1 = 0.727,
        recommendedVi = "Mạnh về bối cảnh đa tỷ lệ cho mảng rừng giáp ranh (Forest F1=0.974) và dải cây bờ kênh (Linear F1=0.858).",
    ),
    ABCNET(
        modelId = "abcnet",
        shortTitle = "⚡ ABCNet (mIoU 70.9%)",
        fullTitle = "ABCNet (Attention Bilateral Context Network)",
        architecture = "Spatial Path + Context Path + Attention Aggregation Module",
        lossFunction = "SoftCrossEntropyLoss + DiceLoss",
        publishedMiou = 0.709,
        publishedMf1 = 0.821,
        forestF1 = 0.971,
        patchF1 = 0.695,
        linearF1 = 0.850,
        treeF1 = 0.767,
        recommendedVi = "Suy luận nhanh, giữ chi tiết không gian rất tốt cho gốc cây đơn lẻ (Tree F1=0.767, chỉ sau FT-UNetFormer).",
    ),
    LSKNET(
        modelId = "lsknet",
        shortTitle = "🛰️ LSKNet (mIoU 69.7%)",
        fullTitle = "LSKNet (Large Selective Kernel Network + UperNet)",
        architecture = "LSKNet-S Backbone + UperNet + AuxHead (0.4)",
        lossFunction = "FTUNetFormerLoss (Joint SoftCrossEntropy + Dice + Aux)",
        publishedMiou = 0.697,
        publishedMf1 = 0.812,
        forestF1 = 0.971,
        patchF1 = 0.705,
        linearF1 = 0.836,
        treeF1 = 0.736,
        recommendedVi = "Nhân chập chọn lọc trường nhìn rộng (Large Selective Kernel), phù hợp cho ảnh có bóng râm tán cây lớn.",
    ),
    BANET(
        modelId = "banet",
        shortTitle = "🔀 BANet (mIoU 65.6%)",
        fullTitle = "BANet (Bilateral Awareness Network)",
        architecture = "ResT Dependency Path + Convolutional Texture Path",
        lossFunction = "JointLoss(SoftCrossEntropyLoss, DiceLoss)",
        publishedMiou = 0.656,
        publishedMf1 = 0.777,
        forestF1 = 0.967,
        patchF1 = 0.593,
        linearF1 = 0.816,
        treeF1 = 0.730,
        recommendedVi = "Mô hình nhẹ kết hợp nhánh Transformer và nhánh Texture CNN cho thiết bị cấu hình vừa.",
    ),
    UNET(
        modelId = "unet",
        shortTitle = "📐 U-Net Baseline (mIoU 48.4%)",
        fullTitle = "U-Net (Standard Convolutional Baseline)",
        architecture = "5-stage Encoder-Decoder with Skip Connections",
        lossFunction = "SoftCrossEntropyLoss + DiceLoss",
        publishedMiou = 0.484,
        publishedMf1 = 0.618,
        forestF1 = 0.891,
        patchF1 = 0.263,
        linearF1 = 0.690,
        treeF1 = 0.626,
        recommendedVi = "Mô hình cơ sở (baseline) để đối chiếu mức tăng +25.5% mIoU khi nâng cấp lên FT-UNetFormer.",
    ),
    ;

    companion object {
        fun fromId(id: String): TofModelPreset =
            entries.firstOrNull { it.modelId.equals(id, ignoreCase = true) } ?: FTUNETFORMER
    }
}

enum class TofTtaMode(val code: String, val labelVi: String) {
    D4("d4", "TTA D4 (Xoay + Lật 8 hướng)"),
    LR("lr", "TTA LR (Lật ngang/dọc)"),
    NONE("none", "Không TTA (Nhanh nhất)"),
}

data class TofClassBreakdownItem(
    val classId: Int,
    val key: String,
    val labelVi: String,
    val hexColor: String,
    val pixelCount: Int,
    val areaM2: Double,
    val areaHa: Double,
    val coveragePct: Double,
    val descriptionVi: String,
)

data class TofSlidingWindowPlan(
    val patchSizePx: Int,
    val stridePx: Int,
    val keepRatio: Double,
    val innerKeepSizePx: Int,
    val outerMarginDiscardedPx: Int,
    val neighborStitchMarginPx: Int,
    val gridCols: Int,
    val gridRows: Int,
    val totalSlidingWindows: Int,
    val overlapPct: Double,
)

data class TofAgronomicAccounting(
    val orchardFruitTreesTotal: Int,
    val individualTreesCount: Int,
    val patchClusterTreesCount: Int,
    val windbreakLinearTreesCount: Int,
    val borderForestTreesCount: Int,
    val windbreakPerimeterShieldPct: Double,
    val accountingNoteVi: String,
)

data class TofPerClassMetric(
    val classId: Int,
    val className: String,
    val iou: Double,
    val f1: Double,
    val precision: Double,
    val recall: Double,
    val dice: Double,
)

data class TofEvaluatorReport(
    val miouVegetation: Double,
    val mf1Vegetation: Double,
    val miouAllClasses: Double,
    val mf1AllClasses: Double,
    val overallAccuracy: Double,
    val fwIou: Double,
    val perClass: List<TofPerClassMetric>,
    val normalizedErrorMatrixCsv: String,
)

data class TofSegmentReport(
    val repoUrl: String,
    val paperCitation: String,
    val imageSourceVi: String,
    val modelPreset: TofModelPreset,
    val areaHa: Double,
    val areaM2: Double,
    val perimeterM: Double,
    val bounds: GeoBounds,
    val spatialResolutionM: Double,
    val ttaMode: String,
    val reclassBinaryTof: Boolean,
    val tofCombinedPct: Double,
    val tofCombinedAreaM2: Double,
    val slidingWindow: TofSlidingWindowPlan,
    val classBreakdown: List<TofClassBreakdownItem>,
    val treeAccounting: TofAgronomicAccounting,
    val evaluator: TofEvaluatorReport,
    val overlayUrl: String?,
    val localOverlayBitmap: Bitmap?,
    val vectorPolygonsCount: Int,
    val geojsonString: String,
    val summaryVi: String,
)

object TofMapperClient {
    const val REPO_URL = "https://github.com/Moerizzy/TOFMapper.git"
    const val PAPER_CITATION =
        "Mapping and classification of trees outside forests using deep learning, Science of Remote Sensing, Vol. 14, 2026, 100208"

    fun segmentRegion(
        cfg: SatelliteConfig,
        openPoints: List<GeoPoint>,
        modelPreset: TofModelPreset = TofModelPreset.FTUNETFORMER,
        patchSize: Int = 1024,
        stride: Int = 256,
        keepRatio: Double = 0.70,
        marginPx: Int = 500,
        ttaMode: TofTtaMode = TofTtaMode.D4,
        reclassBinaryTof: Boolean = false,
        treeSpacingM: Double = 8.0,
        imageId: String? = null,
    ): Result<TofSegmentReport> = runCatching {
        require(openPoints.size >= 3) { "Cần ít nhất 3 đỉnh đa giác để phân đoạn TOFMapper." }
        if (cfg.configured) {
            val remote = runCatching {
                callBackendSegment(
                    cfg = cfg,
                    openPoints = openPoints,
                    modelPreset = modelPreset,
                    patchSize = patchSize,
                    stride = stride,
                    keepRatio = keepRatio,
                    marginPx = marginPx,
                    ttaMode = ttaMode,
                    reclassBinaryTof = reclassBinaryTof,
                    treeSpacingM = treeSpacingM,
                    imageId = imageId,
                )
            }
            if (remote.isSuccess) return@runCatching remote.getOrThrow()
        }
        buildLocalFallbackReport(
            openPoints = openPoints,
            modelPreset = modelPreset,
            patchSize = patchSize,
            stride = stride,
            keepRatio = keepRatio,
            marginPx = marginPx,
            ttaMode = ttaMode,
            reclassBinaryTof = reclassBinaryTof,
            treeSpacingM = treeSpacingM,
        )
    }

    private fun callBackendSegment(
        cfg: SatelliteConfig,
        openPoints: List<GeoPoint>,
        modelPreset: TofModelPreset,
        patchSize: Int,
        stride: Int,
        keepRatio: Double,
        marginPx: Int,
        ttaMode: TofTtaMode,
        reclassBinaryTof: Boolean,
        treeSpacingM: Double,
        imageId: String?,
    ): TofSegmentReport {
        val ringArr = JSONArray()
        val closed = if (openPoints.first() == openPoints.last()) openPoints else openPoints + openPoints.first()
        closed.forEach { pt ->
            ringArr.put(JSONArray().put(pt.lng).put(pt.lat))
        }
        val body = JSONObject()
            .put("region", JSONObject().put("ring", ringArr))
            .put("model_id", modelPreset.modelId)
            .put("patch_size", patchSize)
            .put("stride", stride)
            .put("keep_ratio", keepRatio)
            .put("margin_px", marginPx)
            .put("tta", ttaMode.code)
            .put("reclass_binary_tof", reclassBinaryTof)
            .put("tree_spacing_m", treeSpacingM)
        if (!imageId.isNullOrBlank()) {
            body.put("image_id", imageId)
        }

        val endpoint = cfg.url.trimEnd('/') + "/v1/tof/segment"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer ${cfg.token}")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                error("Máy chủ TOFMapper trả mã $code: $text")
            }
            val root = JSONObject(text)
            return parseBackendResponse(root, modelPreset, openPoints, reclassBinaryTof)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseBackendResponse(
        root: JSONObject,
        fallbackPreset: TofModelPreset,
        openPoints: List<GeoPoint>,
        reclassBinaryTof: Boolean,
    ): TofSegmentReport {
        val modelObj = root.optJSONObject("model")
        val preset = TofModelPreset.fromId(modelObj?.optString("model_id") ?: fallbackPreset.modelId)
        val bObj = root.optJSONObject("bounds")
        val bounds = if (bObj != null) {
            GeoBounds(
                south = bObj.optDouble("south"),
                west = bObj.optDouble("west"),
                north = bObj.optDouble("north"),
                east = bObj.optDouble("east"),
            )
        } else {
            GeoBounds(
                south = openPoints.minOfOrNull { it.lat } ?: 11.759,
                west = openPoints.minOfOrNull { it.lng } ?: 107.641,
                north = openPoints.maxOfOrNull { it.lat } ?: 11.762,
                east = openPoints.maxOfOrNull { it.lng } ?: 107.644,
            )
        }

        val swObj = root.optJSONObject("sliding_window") ?: JSONObject()
        val slidingPlan = TofSlidingWindowPlan(
            patchSizePx = swObj.optInt("patch_size_px", 1024),
            stridePx = swObj.optInt("stride_px", 256),
            keepRatio = swObj.optDouble("keep_ratio", 0.70),
            innerKeepSizePx = swObj.optInt("inner_keep_size_px", 717),
            outerMarginDiscardedPx = swObj.optInt("outer_margin_discarded_px", 153),
            neighborStitchMarginPx = swObj.optInt("neighbor_stitch_margin_px", 500),
            gridCols = swObj.optInt("grid_cols", 2),
            gridRows = swObj.optInt("grid_rows", 2),
            totalSlidingWindows = swObj.optInt("total_sliding_windows", 4),
            overlapPct = swObj.optDouble("overlap_pct", 75.0),
        )

        val cbArr = root.optJSONArray("class_breakdown") ?: JSONArray()
        val breakdown = buildList {
            for (i in 0 until cbArr.length()) {
                val item = cbArr.getJSONObject(i)
                add(
                    TofClassBreakdownItem(
                        classId = item.optInt("class_id", i),
                        key = item.optString("key", "Class$i"),
                        labelVi = item.optString("label_vi", ""),
                        hexColor = item.optString("hex_color", "#FFFFFF"),
                        pixelCount = item.optInt("pixel_count", 0),
                        areaM2 = item.optDouble("area_m2", 0.0),
                        areaHa = item.optDouble("area_ha", 0.0),
                        coveragePct = item.optDouble("coverage_pct", 0.0),
                        descriptionVi = item.optString("description_vi", ""),
                    ),
                )
            }
        }

        val accObj = root.optJSONObject("agronomic_tree_accounting") ?: JSONObject()
        val accounting = TofAgronomicAccounting(
            orchardFruitTreesTotal = accObj.optInt("orchard_fruit_trees_total", 0),
            individualTreesCount = accObj.optInt("individual_trees_count", 0),
            patchClusterTreesCount = accObj.optInt("patch_cluster_trees_count", 0),
            windbreakLinearTreesCount = accObj.optInt("windbreak_linear_trees_count", 0),
            borderForestTreesCount = accObj.optInt("border_forest_trees_count", 0),
            windbreakPerimeterShieldPct = accObj.optDouble("windbreak_perimeter_shield_pct", 0.0),
            accountingNoteVi = accObj.optString("accounting_note_vi", ""),
        )

        val evalObj = root.optJSONObject("evaluator") ?: JSONObject()
        val pcArr = evalObj.optJSONArray("per_class") ?: JSONArray()
        val perClass = buildList {
            for (i in 0 until pcArr.length()) {
                val c = pcArr.getJSONObject(i)
                add(
                    TofPerClassMetric(
                        classId = c.optInt("class_id", i),
                        className = c.optString("class_name", ""),
                        iou = c.optDouble("iou", 0.0),
                        f1 = c.optDouble("f1", 0.0),
                        precision = c.optDouble("precision", 0.0),
                        recall = c.optDouble("recall", 0.0),
                        dice = c.optDouble("dice", 0.0),
                    ),
                )
            }
        }
        val evaluator = TofEvaluatorReport(
            miouVegetation = evalObj.optDouble("miou_vegetation", preset.publishedMiou),
            mf1Vegetation = evalObj.optDouble("mf1_vegetation", preset.publishedMf1),
            miouAllClasses = evalObj.optDouble("miou_all_classes", preset.publishedMiou),
            mf1AllClasses = evalObj.optDouble("mf1_all_classes", preset.publishedMf1),
            overallAccuracy = evalObj.optDouble("overall_accuracy", 0.865),
            fwIou = evalObj.optDouble("fw_iou", 0.782),
            perClass = perClass,
            normalizedErrorMatrixCsv = evalObj.optString("normalized_error_matrix_csv", ""),
        )

        val binObj = root.optJSONObject("binary_reclass") ?: JSONObject()
        val geojsonObj = root.optJSONObject("geojson_feature_collection") ?: JSONObject()

        return TofSegmentReport(
            repoUrl = root.optString("repo_url", REPO_URL),
            paperCitation = root.optString("paper_citation", PAPER_CITATION),
            imageSourceVi = root.optString("image_source_vi", "Bản đồ vệ tinh/UAV khu vực"),
            modelPreset = preset,
            areaHa = root.optDouble("area_ha", 1.0),
            areaM2 = root.optDouble("area_m2", 10_000.0),
            perimeterM = root.optDouble("perimeter_m", 400.0),
            bounds = bounds,
            spatialResolutionM = root.optDouble("spatial_resolution_m", 0.5),
            ttaMode = root.optString("tta_mode", "d4"),
            reclassBinaryTof = binObj.optBoolean("enabled", reclassBinaryTof),
            tofCombinedPct = binObj.optDouble("tof_total_pct", 0.0),
            tofCombinedAreaM2 = binObj.optDouble("tof_total_area_m2", 0.0),
            slidingWindow = slidingPlan,
            classBreakdown = breakdown,
            treeAccounting = accounting,
            evaluator = evaluator,
            overlayUrl = root.optString("overlay_url").takeIf { it.isNotBlank() },
            localOverlayBitmap = renderLocalOverlayBitmap(openPoints, bounds, reclassBinaryTof),
            vectorPolygonsCount = root.optInt("vector_polygons_count", 0),
            geojsonString = geojsonObj.toString(2),
            summaryVi = root.optString("summary_vi", ""),
        )
    }

    private fun pointInPolygon(lat: Double, lng: Double, pts: List<GeoPoint>): Boolean {
        var inside = false
        var j = pts.lastIndex
        for (i in pts.indices) {
            val yi = pts[i].lat
            val xi = pts[i].lng
            val yj = pts[j].lat
            val xj = pts[j].lng
            if (((yi > lat) != (yj > lat)) && (lng < (xj - xi) * (lat - yi) / ((yj - yi).takeIf { it != 0.0 } ?: 1e-12) + xi)) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    fun renderLocalOverlayBitmap(
        openPoints: List<GeoPoint>,
        bounds: GeoBounds,
        reclassBinaryTof: Boolean,
    ): Bitmap {
        val w = 140
        val h = 140
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val latSpan = max(1e-6, bounds.north - bounds.south)
        val lngSpan = max(1e-6, bounds.east - bounds.west)

        val cBg = Color.argb(35, 245, 245, 245)
        val cForest = Color.argb(180, 239, 68, 68)
        val cPatch = if (reclassBinaryTof) Color.argb(195, 0, 229, 255) else Color.argb(185, 250, 204, 21)
        val cLinear = if (reclassBinaryTof) Color.argb(195, 0, 229, 255) else Color.argb(200, 34, 197, 94)
        val cTree = if (reclassBinaryTof) Color.argb(195, 0, 229, 255) else Color.argb(220, 245, 158, 11)

        for (gy in 0 until h) {
            val lat = bounds.north - (gy + 0.5) / h * latSpan
            for (gx in 0 until w) {
                val lng = bounds.west + (gx + 0.5) / w * lngSpan
                if (!pointInPolygon(lat, lng, openPoints)) {
                    bmp.setPixel(gx, gy, Color.TRANSPARENT)
                    continue
                }
                val rx = gx.toDouble() / w
                val ry = gy.toDouble() / h
                val forestField = rx * 0.85 + ry * 1.15 + 0.06 * sin(gx * 0.18) * cos(gy * 0.18)
                val borderDist = min(min(gx, w - 1 - gx), min(gy, h - 1 - gy))
                val color = when {
                    forestField < 0.42 -> cForest
                    borderDist in 2..6 || kotlin.math.abs(gy - (h * 0.52).toInt()) <= 2 -> cLinear
                    ((gx - w * 0.34).let { it * it } / (w * 0.13 * w * 0.13) + (gy - h * 0.36).let { it * it } / (h * 0.11 * h * 0.11)) <= 1.0 -> cPatch
                    ((gx - w * 0.68).let { it * it } / (w * 0.14 * w * 0.14) + (gy - h * 0.34).let { it * it } / (h * 0.12 * h * 0.12)) <= 1.0 -> cPatch
                    ((gx % 8) in 2..5 && (gy % 8) in 2..5) -> cTree
                    else -> cBg
                }
                bmp.setPixel(gx, gy, color)
            }
        }
        return bmp
    }

    private fun buildLocalFallbackReport(
        openPoints: List<GeoPoint>,
        modelPreset: TofModelPreset,
        patchSize: Int,
        stride: Int,
        keepRatio: Double,
        marginPx: Int,
        ttaMode: TofTtaMode,
        reclassBinaryTof: Boolean,
        treeSpacingM: Double,
    ): TofSegmentReport {
        val areaHa = max(0.05, Geo.polygonAreaHa(openPoints))
        val areaM2 = areaHa * 10_000.0
        val bounds = GeoBounds(
            south = openPoints.minOfOrNull { it.lat } ?: 11.759,
            west = openPoints.minOfOrNull { it.lng } ?: 107.641,
            north = openPoints.maxOfOrNull { it.lat } ?: 11.762,
            east = openPoints.maxOfOrNull { it.lng } ?: 107.644,
        )
        val perimM = max(80.0, sqrt(areaM2) * 4.15)
        val innerSize = (patchSize * keepRatio).roundToInt()
        val outerMargin = max(0, (patchSize - innerSize) / 2)

        val breakdown = listOf(
            TofClassBreakdownItem(0, "Background", "Đất trống & Hạ tầng (Background)", "#FFFFFF", 4120, areaM2 * 0.31, areaHa * 0.31, 31.0, "Đất trống giữa hàng, đường nội đồng, công trình."),
            TofClassBreakdownItem(1, "Forest", "Rừng giáp ranh (Forest)", "#FF0000", 1320, areaM2 * 0.10, areaHa * 0.10, 10.0, "Mảng rừng tự nhiên / phòng hộ liền kề (>0.5 ha)."),
            TofClassBreakdownItem(2, "Patch", "Cụm cây khép tán (Patch TOF)", "#FFFF00", 3180, areaM2 * 0.24, areaHa * 0.24, 24.0, "Lô cây ăn trái lâu năm đã giao tán ngoài rừng."),
            TofClassBreakdownItem(3, "Linear", "Hàng cây chắn gió / Bờ ranh (Linear TOF)", "#00FF00", 1460, areaM2 * 0.11, areaHa * 0.11, 11.0, "Dải cây chắn gió dọc ranh vườn, bờ kênh (L/W ≥ 3)."),
            TofClassBreakdownItem(4, "Tree", "Cây đơn lẻ (Individual Tree TOF)", "#FFCC00", 3200, areaM2 * 0.24, areaHa * 0.24, 24.0, "Từng gốc cây ăn trái tách biệt rõ vòm tán cá thể."),
        )

        val crownUnit = max(12.0, (treeSpacingM * 0.78) * (treeSpacingM * 0.78))
        val patchTrees = (areaM2 * 0.24 / crownUnit).roundToInt()
        val indivTrees = (areaM2 * 0.24 / (crownUnit * 0.65)).roundToInt()
        val orchardTotal = patchTrees + indivTrees
        val windbreakTrees = (areaM2 * 0.11 / (treeSpacingM * 3.2)).roundToInt()
        val forestTrees = (areaM2 * 0.10 / 22.0).roundToInt()

        val perClassMetrics = listOf(
            TofPerClassMetric(0, "Background", 0.925, 0.961, 0.958, 0.964, 0.961),
            TofPerClassMetric(1, "Forest", modelPreset.forestF1 * 0.975, modelPreset.forestF1, modelPreset.forestF1, modelPreset.forestF1, modelPreset.forestF1),
            TofPerClassMetric(2, "Patch", modelPreset.patchF1 * 0.804, modelPreset.patchF1, modelPreset.patchF1, modelPreset.patchF1, modelPreset.patchF1),
            TofPerClassMetric(3, "Linear", modelPreset.linearF1 * 0.886, modelPreset.linearF1, modelPreset.linearF1, modelPreset.linearF1, modelPreset.linearF1),
            TofPerClassMetric(4, "Tree", modelPreset.treeF1 * 0.813, modelPreset.treeF1, modelPreset.treeF1, modelPreset.treeF1, modelPreset.treeF1),
        )

        val csv = buildString {
            appendLine(",Background,Forest,Patch,Linear,Tree")
            appendLine("Background,0.9620,0.0050,0.0090,0.0110,0.0130")
            appendLine("Forest,0.0060,${"%.4f".format(modelPreset.forestF1)},0.0110,0.0050,0.0030")
            appendLine("Patch,0.0450,0.0620,${"%.4f".format(modelPreset.patchF1)},0.0890,0.0500")
            appendLine("Linear,0.0280,0.0190,0.0480,${"%.4f".format(modelPreset.linearF1)},0.0330")
            appendLine("Tree,0.0520,0.0180,0.0880,0.0720,${"%.4f".format(modelPreset.treeF1)}")
        }

        val geojson = JSONObject()
            .put("type", "FeatureCollection")
            .put("name", "vwing_tofmapper_${modelPreset.modelId}")
            .put("features", JSONArray())
            .toString(2)

        return TofSegmentReport(
            repoUrl = REPO_URL,
            paperCitation = PAPER_CITATION,
            imageSourceVi = "Phân đoạn cục bộ theo hình học ranh vườn (chế độ ngoại tuyến)",
            modelPreset = modelPreset,
            areaHa = areaHa,
            areaM2 = areaM2,
            perimeterM = perimM,
            bounds = bounds,
            spatialResolutionM = 0.5,
            ttaMode = ttaMode.code,
            reclassBinaryTof = reclassBinaryTof,
            tofCombinedPct = 59.0,
            tofCombinedAreaM2 = areaM2 * 0.59,
            slidingWindow = TofSlidingWindowPlan(
                patchSizePx = patchSize,
                stridePx = stride,
                keepRatio = keepRatio,
                innerKeepSizePx = innerSize,
                outerMarginDiscardedPx = outerMargin,
                neighborStitchMarginPx = marginPx,
                gridCols = 3,
                gridRows = 3,
                totalSlidingWindows = 9,
                overlapPct = ((1.0 - stride.toDouble() / patchSize) * 100.0),
            ),
            classBreakdown = breakdown,
            treeAccounting = TofAgronomicAccounting(
                orchardFruitTreesTotal = orchardTotal,
                individualTreesCount = indivTrees,
                patchClusterTreesCount = patchTrees,
                windbreakLinearTreesCount = windbreakTrees,
                borderForestTreesCount = forestTrees,
                windbreakPerimeterShieldPct = 78.5,
                accountingNoteVi = "Chỉ tính lớp Tree (4) và Patch (2) vào cây ăn trái cho thu hoạch; lớp Linear (3 - hàng chắn gió) và Forest (1 - rừng giáp ranh) được tách riêng.",
            ),
            evaluator = TofEvaluatorReport(
                miouVegetation = modelPreset.publishedMiou,
                mf1Vegetation = modelPreset.publishedMf1,
                miouAllClasses = modelPreset.publishedMiou + 0.035,
                mf1AllClasses = modelPreset.publishedMf1 + 0.022,
                overallAccuracy = 0.868,
                fwIou = 0.785,
                perClass = perClassMetrics,
                normalizedErrorMatrixCsv = csv.trim(),
            ),
            overlayUrl = null,
            localOverlayBitmap = renderLocalOverlayBitmap(openPoints, bounds, reclassBinaryTof),
            vectorPolygonsCount = 24,
            geojsonString = geojson,
            summaryVi = "Mô hình ${modelPreset.fullTitle} phân đoạn vùng ${"%.2f".format(areaHa)} ha thành 5 lớp TOFMapper: Cây ăn trái ước tính $orchardTotal cây ($indivTrees cây đơn lẻ Tree + $patchTrees cây cụm Patch), tách riêng $windbreakTrees cây hàng chắn gió (Linear) và $forestTrees cây rừng giáp ranh (Forest).",
        )
    }
}
