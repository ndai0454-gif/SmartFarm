package com.example.test_tablet.data.vwing

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** DeepForest RGB inference and fine-tune client (labels are the user's own boxes on an uploaded image). */
enum class DeepForestModelPreset(
    val modelId: String,
    val shortTitle: String,
    val fullTitle: String,
    val icon: String,
    val targetClass: String,
) {
    NEON_TREE(
        "weecology/deepforest-tree",
        "🌳 Tán cây NEON",
        "DeepForest NEON Tree Crown (mô hình dựng sẵn)",
        "🌳",
        "Tree",
    ),
    BIRD(
        "weecology/deepforest-bird",
        "🐦 Chim",
        "DeepForest Bird Detector (mô hình dựng sẵn)",
        "🐦",
        "Bird",
    ),
    LIVESTOCK(
        "weecology/deepforest-livestock",
        "🐄 Gia súc",
        "DeepForest Livestock Detector (mô hình dựng sẵn)",
        "🐄",
        "Livestock",
    ),
    ;

    companion object {
        fun fromId(id: String): DeepForestModelPreset =
            entries.firstOrNull { it.modelId == id } ?: NEON_TREE
    }
}

data class DeepForestAnnotationInput(
    val boxId: String,
    val bounds: GeoBounds,
    val label: String = "Tree",
    val userVerified: Boolean = false,
)

data class DeepForestCrownBox(
    val boxId: String,
    val code: String,
    val label: String,
    val labelVi: String,
    val score: Double,
    val confidencePct: Int,
    val xminPx: Int,
    val yminPx: Int,
    val xmaxPx: Int,
    val ymaxPx: Int,
    val bounds: GeoBounds,
    val center: GeoPoint,
    val crownWidthM: Double,
    val crownHeightM: Double,
    val crownDiameterM: Double,
    val crownAreaM2: Double,
    val tileId: String,
    val rowNumber: Int,
    val status: String,
    val statusVi: String,
    val healthScore: Int?,
    val ndvi: Double?,
    val ndre: Double?,
    val isUserAnnotated: Boolean = false,
) {
    fun toSatTreePoint(): SatTreePoint = SatTreePoint(
        code = code,
        status = status,
        ndvi = ndvi,
        ndre = ndre,
        isPeak = true,
        pos = center,
        treeNumber = code.substringAfterLast("-", "1").toIntOrNull() ?: 1,
        rowNumber = rowNumber,
        statusVi = statusVi,
        healthScore = healthScore ?: -1,
        confidencePct = confidencePct,
        crownDiameterM = crownDiameterM,
        detectionSource = if (isUserAnnotated) "Nhãn thủ công chưa xác minh" else "weecology/DeepForest (ảnh RGB)",
        diagnosisVi = "Ảnh RGB chỉ phát hiện hình dạng tán. Chưa đo sức khỏe, NDVI hoặc NDRE.",
        bbox = bounds,
        tileId = tileId,
    )
}

data class DeepForestTileWindow(
    val tileId: String,
    val rowIdx: Int,
    val colIdx: Int,
    val x0Px: Int,
    val y0Px: Int,
    val x1Px: Int,
    val y1Px: Int,
    val bounds: GeoBounds,
    val rawCandidates: Int?,
    val keptAfterNms: Int?,
)

data class DeepForestPredictReport(
    val repoUrl: String,
    val engineRuntime: String,
    val modelId: String,
    val modelName: String,
    val architecture: String,
    val targetClass: String,
    val labelVi: String,
    val areaHa: Double,
    val bounds: GeoBounds,
    val rasterWidthPx: Int,
    val rasterHeightPx: Int,
    val spatialResolutionM: Double,
    val patchSizePx: Int,
    val patchOverlap: Double,
    val iouThreshold: Double,
    val scoreThreshold: Double,
    val totalTilesProcessed: Int?,
    val rawBoxesBeforeNms: Int?,
    val mergedBoxesAfterNms: Int,
    val nmsSuppressedCount: Int?,
    val meanConfidencePct: Double,
    val meanCrownDiameterM: Double,
    val canopyCoverPct: Double,
    val healthyCount: Int?,
    val moderateCount: Int?,
    val stressedCount: Int?,
    val tiles: List<DeepForestTileWindow>,
    val boxes: List<DeepForestCrownBox>,
    val overlayUrl: String?,
    val finetunedCheckpointId: String?,
    val summaryVi: String,
    val source: String? = null,
    val provenance: String? = null,
    val warnings: List<String> = emptyList(),
)

data class DeepForestEpochMetric(
    val epoch: Int,
    val totalLoss: Double?,
    val valMap50: Double?,
    val valRecall: Double?,
)

data class DeepForestFineTuneReport(
    val checkpointId: String,
    val baseModelId: String,
    val cropType: String,
    val label: String,
    val trainingAnnotationsCount: Int,
    val validationAnnotationsCount: Int,
    val droppedAnnotationsCount: Int,
    val augmentedPatchesCount: Int,
    val epochsCompleted: Int,
    val learningRate: Double,
    val initialMap50: Double?,
    val finalMap50: Double?,
    val initialRecall: Double?,
    val finalRecall: Double?,
    val epochHistory: List<DeepForestEpochMetric>,
)

/** Server-side training job: [state] is queued/running/done/failed; [stage] names the current step. */
data class DeepForestFineTuneJob(
    val jobId: String,
    val state: String,
    val stage: String,
    val error: String?,
    val result: DeepForestFineTuneReport?,
)

object DeepForestClient {
    private const val CONNECT_MS = 8_000
    private const val READ_MS = 240_000
    const val MAX_IMAGE_BYTES = 30 * 1024 * 1024

    private fun postJson(cfg: SatelliteConfig, path: String, payload: JSONObject): JSONObject =
        postBytes(cfg, path, payload.toString().toByteArray(Charsets.UTF_8), "application/json")

    private fun postBytes(cfg: SatelliteConfig, path: String, bytes: ByteArray, contentType: String): JSONObject {
        var lastErr: Exception? = null
        for (base in SatelliteClient.candidateUrls(cfg.url)) {
            val conn = (URL(base.trim().trimEnd('/') + path).openConnection() as HttpURLConnection)
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = CONNECT_MS
                conn.readTimeout = READ_MS
                conn.doOutput = true
                conn.setRequestProperty("Authorization", "Bearer ${cfg.token}")
                conn.setRequestProperty("Content-Type", contentType)
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.outputStream.use { it.write(bytes) }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code in 200..299) {
                    return JSONObject(text)
                } else {
                    throw DeepForestApiException("DeepForest API trả lỗi $code: $text")
                }
            } catch (e: DeepForestApiException) {
                throw e
            } catch (e: Exception) {
                lastErr = e
            } finally {
                conn.disconnect()
            }
        }
        throw lastErr ?: IOException("Không kết nối được máy chủ DeepForest.")
    }

    private class DeepForestApiException(message: String) : IOException(message)

    data class UploadedImage(val imageId: String, val width: Int, val height: Int)

    fun uploadImage(cfg: SatelliteConfig, bytes: ByteArray): UploadedImage {
        require(bytes.isNotEmpty() && bytes.size <= MAX_IMAGE_BYTES) { "Ảnh phải có dữ liệu và không quá 30 MiB." }
        val o = postBytes(cfg, "/v1/deepforest/images", bytes, "application/octet-stream")
        return UploadedImage(o.getString("image_id"), o.getInt("width"), o.getInt("height"))
    }

    private fun nullableInt(o: JSONObject, key: String): Int? =
        if (o.isNull(key)) null else o.optInt(key)

    private fun nullableDouble(o: JSONObject, key: String): Double? =
        if (o.isNull(key)) null else o.optDouble(key).takeIf { it.isFinite() }

    private fun getJson(cfg: SatelliteConfig, path: String): JSONObject {
        var lastErr: Exception? = null
        for (base in SatelliteClient.candidateUrls(cfg.url)) {
            val conn = (URL(base.trim().trimEnd('/') + path).openConnection() as HttpURLConnection)
            try {
                conn.connectTimeout = CONNECT_MS
                conn.readTimeout = 30_000
                conn.setRequestProperty("Authorization", "Bearer ${cfg.token}")
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code in 200..299) return JSONObject(text)
                throw DeepForestApiException("DeepForest API trả lỗi $code: $text")
            } catch (e: DeepForestApiException) {
                throw e
            } catch (e: Exception) {
                lastErr = e
            } finally {
                conn.disconnect()
            }
        }
        throw lastErr ?: IOException("Không kết nối được máy chủ DeepForest.")
    }

    private fun ringJson(ring: List<GeoPoint>): JSONObject {
        val arr = JSONArray()
        ring.forEach { pt -> arr.put(JSONArray().put(pt.lng).put(pt.lat)) }
        return JSONObject().put("ring", arr)
    }

    private fun annotationsJson(list: List<DeepForestAnnotationInput>): JSONArray {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(
                JSONObject()
                    .put("box_id", a.boxId)
                    .put("west", a.bounds.west)
                    .put("south", a.bounds.south)
                    .put("east", a.bounds.east)
                    .put("north", a.bounds.north)
                    .put("label", a.label)
                    .put("user_verified", a.userVerified),
            )
        }
        return arr
    }

    fun startFineTune(
        cfg: SatelliteConfig,
        ring: List<GeoPoint>,
        imageId: String,
        annotations: List<DeepForestAnnotationInput>,
        baseModelId: String = "weecology/deepforest-tree",
        cropType: String = "durian",
        epochs: Int = 6,
        learningRate: Double = 0.0005,
    ): DeepForestFineTuneJob {
        val body = JSONObject()
            .put("region", ringJson(ring))
            .put("image_id", imageId)
            .put("base_model_id", baseModelId)
            .put("crop_type", cropType)
            .put("epochs", epochs)
            .put("learning_rate", learningRate)
            .put("annotations", annotationsJson(annotations))
        return parseFineTuneJob(postJson(cfg, "/v1/deepforest/finetune", body))
    }

    fun fineTuneStatus(cfg: SatelliteConfig, jobId: String): DeepForestFineTuneJob =
        parseFineTuneJob(getJson(cfg, "/v1/deepforest/finetune/$jobId"))

    internal fun parseFineTuneJob(o: JSONObject): DeepForestFineTuneJob {
        val report = o.optJSONObject("result")?.let {
            val history = it.optJSONArray("epoch_history") ?: JSONArray()
            DeepForestFineTuneReport(
                checkpointId = it.getString("checkpoint_id"),
                baseModelId = it.optString("base_model_id"),
                cropType = it.optString("crop_type"),
                label = it.optString("label"),
                trainingAnnotationsCount = it.optInt("training_annotations_count"),
                validationAnnotationsCount = it.optInt("validation_annotations_count"),
                droppedAnnotationsCount = it.optInt("dropped_annotations_count"),
                augmentedPatchesCount = it.optInt("augmented_patches_count"),
                epochsCompleted = it.optInt("epochs_completed"),
                learningRate = it.optDouble("learning_rate"),
                initialMap50 = nullableDouble(it, "initial_map50"),
                finalMap50 = nullableDouble(it, "final_map50"),
                initialRecall = nullableDouble(it, "initial_recall"),
                finalRecall = nullableDouble(it, "final_recall"),
                epochHistory = (0 until history.length()).map { i ->
                    val h = history.getJSONObject(i)
                    DeepForestEpochMetric(h.optInt("epoch"), nullableDouble(h, "total_loss"), nullableDouble(h, "val_map50"), nullableDouble(h, "val_recall"))
                },
            )
        }
        return DeepForestFineTuneJob(
            jobId = o.getString("job_id"),
            state = o.optString("state"),
            stage = o.optString("stage"),
            error = if (o.isNull("error")) null else o.optString("error"),
            result = report,
        )
    }

    fun predict(
        cfg: SatelliteConfig,
        ring: List<GeoPoint>,
        modelId: String = "weecology/deepforest-tree",
        cropType: String = "durian",
        patchSize: Int = 400,
        patchOverlap: Double = 0.15,
        iouThreshold: Double = 0.15,
        scoreThreshold: Double = 0.30,
        userAnnotations: List<DeepForestAnnotationInput> = emptyList(),
        imageId: String? = null,
    ): DeepForestPredictReport {
        val body = JSONObject()
            .put("region", ringJson(ring))
            .put("model_id", modelId)
            .put("crop_type", cropType)
            .put("patch_size", patchSize)
            .put("patch_overlap", patchOverlap)
            .put("iou_threshold", iouThreshold)
            .put("score_threshold", scoreThreshold)
            .put("user_annotations", annotationsJson(userAnnotations))
        imageId?.let { body.put("image_id", it) }
        val o = postJson(cfg, "/v1/deepforest/predict", body)
        return parsePredict(o, modelId, patchSize, patchOverlap, iouThreshold, scoreThreshold)
    }

    internal fun parsePredict(o: JSONObject, modelId: String = "weecology/deepforest-tree", patchSize: Int = 400, patchOverlap: Double = 0.15, iouThreshold: Double = 0.15, scoreThreshold: Double = 0.30): DeepForestPredictReport {
        val bObj = o.getJSONObject("bounds")
        val bounds = GeoBounds(
            south = bObj.getDouble("south"),
            west = bObj.getDouble("west"),
            north = bObj.getDouble("north"),
            east = bObj.getDouble("east"),
        )
        val tilesArr = o.optJSONArray("tiles") ?: JSONArray()
        val tiles = (0 until tilesArr.length()).map { i ->
            val t = tilesArr.getJSONObject(i)
            DeepForestTileWindow(
                tileId = t.optString("tile_id", "T00-$i"),
                rowIdx = t.optInt("row_idx", 0),
                colIdx = t.optInt("col_idx", 0),
                x0Px = t.optInt("x0_px", 0),
                y0Px = t.optInt("y0_px", 0),
                x1Px = t.optInt("x1_px", 100),
                y1Px = t.optInt("y1_px", 100),
                bounds = GeoBounds(
                    south = t.getDouble("south"),
                    west = t.getDouble("west"),
                    north = t.getDouble("north"),
                    east = t.getDouble("east"),
                ),
                rawCandidates = nullableInt(t, "raw_candidates"),
                keptAfterNms = nullableInt(t, "kept_after_nms"),
            )
        }
        val boxesArr = o.optJSONArray("boxes") ?: JSONArray()
        val boxes = (0 until boxesArr.length()).map { i ->
            val b = boxesArr.getJSONObject(i)
            DeepForestCrownBox(
                boxId = b.optString("box_id", "DF-%03d".format(i + 1)),
                code = b.optString("code", "SR-%03d".format(i + 1)),
                label = b.optString("label", "Tree"),
                labelVi = b.optString("label_vi", "Tán cây"),
                score = b.getDouble("score"),
                confidencePct = b.optInt("confidence_pct", (b.getDouble("score") * 100).toInt()),
                xminPx = b.optInt("xmin_px", 0),
                yminPx = b.optInt("ymin_px", 0),
                xmaxPx = b.optInt("xmax_px", 10),
                ymaxPx = b.optInt("ymax_px", 10),
                bounds = GeoBounds(
                    south = b.getDouble("south"),
                    west = b.getDouble("west"),
                    north = b.getDouble("north"),
                    east = b.getDouble("east"),
                ),
                center = GeoPoint(b.getDouble("center_lat"), b.getDouble("center_lon")),
                crownWidthM = b.optDouble("crown_width_m", 5.0),
                crownHeightM = b.optDouble("crown_height_m", 5.0),
                crownDiameterM = b.optDouble("crown_diameter_m", 5.0),
                crownAreaM2 = b.optDouble("crown_area_m2", 19.6),
                tileId = b.optString("tile_id", "T00-00"),
                rowNumber = b.optInt("row_number", 1),
                status = "unknown",
                statusVi = "Chưa đo sức khỏe",
                healthScore = null,
                ndvi = null,
                ndre = null,
                isUserAnnotated = b.optBoolean("is_user_annotated", false),
            )
        }
        return DeepForestPredictReport(
            repoUrl = o.optString("repo_url", "https://github.com/weecology/DeepForest.git"),
            engineRuntime = o.optString("engine_runtime", "weecology/DeepForest RetinaNet"),
            modelId = o.optString("model_id", modelId),
            modelName = o.optString("model_name", "DeepForest NEON Tree Crown"),
            architecture = o.optString("architecture", "RetinaNet (ResNet-50 FPN)"),
            targetClass = o.optString("target_class", "Tree"),
            labelVi = o.optString("label_vi", "Tán cây"),
            areaHa = o.optDouble("area_ha", 1.0),
            bounds = bounds,
            rasterWidthPx = o.optInt("raster_width_px", 400),
            rasterHeightPx = o.optInt("raster_height_px", 400),
            spatialResolutionM = nullableDouble(o, "spatial_resolution_m") ?: Double.NaN,
            patchSizePx = o.optInt("patch_size_px", patchSize),
            patchOverlap = o.optDouble("patch_overlap", patchOverlap),
            iouThreshold = o.optDouble("iou_threshold", iouThreshold),
            scoreThreshold = o.optDouble("score_threshold", scoreThreshold),
            totalTilesProcessed = nullableInt(o, "total_tiles_processed"),
            rawBoxesBeforeNms = nullableInt(o, "raw_boxes_before_nms"),
            mergedBoxesAfterNms = o.optInt("merged_boxes_after_nms", boxes.size),
            nmsSuppressedCount = nullableInt(o, "nms_suppressed_count"),
            meanConfidencePct = nullableDouble(o, "mean_confidence_pct") ?: Double.NaN,
            meanCrownDiameterM = nullableDouble(o, "mean_crown_diameter_m") ?: Double.NaN,
            canopyCoverPct = nullableDouble(o, "canopy_cover_pct") ?: Double.NaN,
            healthyCount = nullableInt(o, "healthy_count"),
            moderateCount = nullableInt(o, "moderate_count"),
            stressedCount = nullableInt(o, "stressed_count"),
            tiles = tiles,
            boxes = boxes,
            overlayUrl = o.optString("overlay_url").takeIf { it.isNotBlank() && it != "null" },
            finetunedCheckpointId = o.optString("finetuned_checkpoint_id").takeIf { it.isNotBlank() && it != "null" },
            summaryVi = o.optString("summary_vi", ""),
            source = o.opt("source")?.takeUnless { it == JSONObject.NULL }?.toString(),
            provenance = o.opt("provenance")?.takeUnless { it == JSONObject.NULL }?.toString(),
            warnings = o.optJSONArray("warnings")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
        )
    }

    fun searchOamVietnam(
        cfg: SatelliteConfig,
        regionPreset: String = "vietnam_all",
        limit: Int = 10,
    ): OamSearchResponse {
        val path = "/v1/deepforest/oam/search?region_preset=$regionPreset&limit=$limit&max_gsd_m=0.50"
        val o = getJson(cfg, path)
        return parseOamSearch(o)
    }

    internal fun parseOamSearch(o: JSONObject): OamSearchResponse {
        val arr = o.optJSONArray("results") ?: JSONArray()
        val items = (0 until arr.length()).map { i ->
            val r = arr.getJSONObject(i)
            OamSceneItem(
                id = r.optString("id", "oam-$i"),
                title = r.optString("title", "OpenAerialMap UAV Imagery"),
                provider = r.optString("provider", "OpenAerialMap Community"),
                platform = r.optString("platform", "uav"),
                gsdCm = nullableDouble(r, "gsd_cm"),
                suitableForDeepForest = r.optBoolean("suitable_for_deepforest", false),
                acquisitionEnd = r.optString("acquisition_end").takeIf { it.isNotBlank() && it != "null" },
                viewerUrl = r.optString("viewer_url", "https://openaerialmap.org/"),
                geotiffUrl = r.optString("geotiff_url").takeIf { it.isNotBlank() && it != "null" },
            )
        }
        return OamSearchResponse(
            regionPreset = o.optString("region_preset", "vietnam_all"),
            regionNameVi = o.optString("region_name_vi", "Việt Nam"),
            focusCropsVi = o.optString("focus_crops_vi", ""),
            browserExploreUrl = o.optString("browser_explore_url", "https://openaerialmap.org/"),
            liveApiReachable = o.optBoolean("live_api_reachable", false),
            totalReturned = o.optInt("total_returned", items.size),
            suitableCount = o.optInt("suitable_for_deepforest_count", items.count { it.suitableForDeepForest }),
            results = items,
            networkNoteVi = o.optString("network_note_vi").takeIf { it.isNotBlank() && it != "null" },
        )
    }

    fun convertXmlToDeepForestCsv(
        cfg: SatelliteConfig?,
        xmlDocs: List<PascalVocXmlInput>,
        valRatio: Double = 0.2,
        normalizeLabelToTree: Boolean = true,
    ): DeepForestCsvConversionResult {
        require(xmlDocs.isNotEmpty()) { "Cần ít nhất 1 tệp XML PASCAL VOC để chuyển đổi." }
        if (cfg != null && cfg.configured) {
            runCatching {
                val docsArr = JSONArray()
                xmlDocs.forEach { d ->
                    docsArr.put(
                        JSONObject()
                            .put("filename", d.filename)
                            .put("xml_content", d.xmlContent),
                    )
                }
                val body = JSONObject()
                    .put("xml_documents", docsArr)
                    .put("val_ratio", valRatio)
                    .put("normalize_label_to_tree", normalizeLabelToTree)
                val res = postJson(cfg, "/v1/deepforest/dataset/xml-to-csv", body)
                return DeepForestCsvConversionResult(
                    columns = listOf("image_path", "xmin", "ymin", "xmax", "ymax", "label"),
                    totalBoxes = res.optInt("total_boxes", 0),
                    uniqueImagesCount = res.optInt("unique_images_count", 0),
                    trainBoxesCount = res.optInt("train_boxes_count", 0),
                    validationBoxesCount = res.optInt("validation_boxes_count", 0),
                    splitMode = res.optString("split_mode", "independent_images"),
                    fullCsv = res.optString("full_csv", ""),
                    vietnamTrainCsv = res.optString("vietnam_train_csv", ""),
                    vietnamValidationCsv = res.optString("vietnam_validation_csv", ""),
                )
            }
        }
        return xmlToCsvLocal(xmlDocs, valRatio, normalizeLabelToTree)
    }

    /** Chuyển đổi PASCAL VOC XML sang chuẩn CSV DeepForest (`image_path,xmin,ymin,xmax,ymax,label`) trực tiếp trên thiết bị. */
    fun xmlToCsvLocal(
        xmlDocs: List<PascalVocXmlInput>,
        valRatio: Double = 0.2,
        normalizeLabelToTree: Boolean = true,
    ): DeepForestCsvConversionResult {
        data class Row(val imagePath: String, val xmin: Int, val ymin: Int, val xmax: Int, val ymax: Int, val label: String) {
            fun toCsvLine(): String = "$imagePath,$xmin,$ymin,$xmax,$ymax,$label"
        }
        val rows = mutableListOf<Row>()
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        for ((idx, doc) in xmlDocs.withIndex()) {
            val builder = factory.newDocumentBuilder()
            val dom = builder.parse(doc.xmlContent.byteInputStream(Charsets.UTF_8))
            val root = dom.documentElement
            val fnNodes = root.getElementsByTagName("filename")
            val imagePath = if (fnNodes.length > 0 && fnNodes.item(0).textContent.isNotBlank()) {
                fnNodes.item(0).textContent.trim()
            } else {
                doc.filename.ifBlank { "vietnam_garden_${idx + 1}.png" }
            }
            val objNodes = root.getElementsByTagName("object")
            for (i in 0 until objNodes.length) {
                val obj = objNodes.item(i) as? org.w3c.dom.Element ?: continue
                val bndList = obj.getElementsByTagName("bndbox")
                val nameList = obj.getElementsByTagName("name")
                var xmin = 0
                var ymin = 0
                var xmax = 0
                var ymax = 0
                var rawLabel = "Tree"
                if (bndList.length > 0) {
                    val bnd = bndList.item(0) as org.w3c.dom.Element
                    xmin = bnd.getElementsByTagName("xmin").item(0)?.textContent?.trim()?.toDoubleOrNull()?.toInt() ?: 0
                    ymin = bnd.getElementsByTagName("ymin").item(0)?.textContent?.trim()?.toDoubleOrNull()?.toInt() ?: 0
                    xmax = bnd.getElementsByTagName("xmax").item(0)?.textContent?.trim()?.toDoubleOrNull()?.toInt() ?: 0
                    ymax = bnd.getElementsByTagName("ymax").item(0)?.textContent?.trim()?.toDoubleOrNull()?.toInt() ?: 0
                    if (nameList.length > 0 && nameList.item(0).textContent.isNotBlank()) {
                        rawLabel = nameList.item(0).textContent.trim()
                    }
                } else {
                    // Hỗ trợ cấu trúc member[0][0..3] trong script mẫu của người dùng
                    val firstChild = (0 until obj.childNodes.length)
                        .map { obj.childNodes.item(it) }
                        .firstOrNull { it.nodeType == org.w3c.dom.Node.ELEMENT_NODE } as? org.w3c.dom.Element
                    if (firstChild != null) {
                        val subEls = (0 until firstChild.childNodes.length)
                            .map { firstChild.childNodes.item(it) }
                            .filter { it.nodeType == org.w3c.dom.Node.ELEMENT_NODE }
                        if (subEls.size >= 4) {
                            xmin = subEls[0].textContent.trim().toDoubleOrNull()?.toInt() ?: 0
                            ymin = subEls[1].textContent.trim().toDoubleOrNull()?.toInt() ?: 0
                            xmax = subEls[2].textContent.trim().toDoubleOrNull()?.toInt() ?: 0
                            ymax = subEls[3].textContent.trim().toDoubleOrNull()?.toInt() ?: 0
                        }
                    }
                }
                if (xmax > xmin && ymax > ymin && xmin >= 0 && ymin >= 0) {
                    rows += Row(imagePath, xmin, ymin, xmax, ymax, if (normalizeLabelToTree) "Tree" else rawLabel)
                }
            }
        }
        require(rows.isNotEmpty()) { "Không tìm thấy bounding box hợp lệ (xmin < xmax, ymin < ymax) trong XML." }
        val header = "image_path,xmin,ymin,xmax,ymax,label"
        val uniqueImages = rows.map { it.imagePath }.distinct()
        val (trainRows, valRows, splitMode) = if (uniqueImages.size >= 2 && valRatio > 0.0 && valRatio < 1.0) {
            val valImgCount = maxOf(1, kotlin.math.round(uniqueImages.size * valRatio).toInt())
            val valSet = uniqueImages.takeLast(valImgCount).toSet()
            Triple(rows.filter { it.imagePath !in valSet }, rows.filter { it.imagePath in valSet }, "independent_images")
        } else if (rows.size >= 2 && valRatio > 0.0 && valRatio < 1.0) {
            val valCount = maxOf(1, kotlin.math.round(rows.size * valRatio).toInt())
            Triple(rows.dropLast(valCount), rows.takeLast(valCount), "row_split_single_image")
        } else {
            Triple(rows, emptyList(), "train_only")
        }
        return DeepForestCsvConversionResult(
            columns = listOf("image_path", "xmin", "ymin", "xmax", "ymax", "label"),
            totalBoxes = rows.size,
            uniqueImagesCount = uniqueImages.size,
            trainBoxesCount = trainRows.size,
            validationBoxesCount = valRows.size,
            splitMode = splitMode,
            fullCsv = (listOf(header) + rows.map { it.toCsvLine() }).joinToString("\n") + "\n",
            vietnamTrainCsv = (listOf(header) + trainRows.map { it.toCsvLine() }).joinToString("\n") + "\n",
            vietnamValidationCsv = (listOf(header) + valRows.map { it.toCsvLine() }).joinToString("\n") + "\n",
        )
    }

    val SAMPLE_VIETNAM_XML_DOCS: List<PascalVocXmlInput> = listOf(
        PascalVocXmlInput(
            filename = "ben_tre_coconut_uav_01.xml",
            xmlContent = """
                <annotation>
                    <filename>ben_tre_coconut_uav_01.png</filename>
                    <object><name>Tree</name><bndbox><xmin>64</xmin><ymin>72</ymin><xmax>156</xmax><ymax>164</ymax></bndbox></object>
                    <object><name>Tree</name><bndbox><xmin>210</xmin><ymin>76</ymin><xmax>304</xmax><ymax>170</ymax></bndbox></object>
                    <object><name>Tree</name><bndbox><xmin>358</xmin><ymin>70</ymin><xmax>452</xmax><ymax>166</ymax></bndbox></object>
                    <object><name>Tree</name><bndbox><xmin>68</xmin><ymin>220</ymin><xmax>160</xmax><ymax>314</ymax></bndbox></object>
                </annotation>
            """.trimIndent(),
        ),
        PascalVocXmlInput(
            filename = "dak_lak_coffee_durian_uav_02.xml",
            xmlContent = """
                <annotation>
                    <filename>dak_lak_coffee_durian_uav_02.png</filename>
                    <object><name>Tree</name><bndbox><xmin>90</xmin><ymin>95</ymin><xmax>210</xmax><ymax>215</ymax></bndbox></object>
                    <object><name>Tree</name><bndbox><xmin>260</xmin><ymin>98</ymin><xmax>380</xmax><ymax>218</ymax></bndbox></object>
                </annotation>
            """.trimIndent(),
        ),
    )

}

enum class OamVietnamPreset(
    val presetId: String,
    val shortLabelVi: String,
    val fullNameVi: String,
    val focusCropsVi: String,
    val openAerialMapUrl: String,
) {
    CENTRAL_HIGHLANDS(
        "central_highlands",
        "☕ Tây Nguyên",
        "Tây Nguyên (Lâm Đồng, Đắk Lắk, Gia Lai, Đắk Nông)",
        "Cà phê, sầu riêng, hồ tiêu, bơ, cao su & lâm nghiệp",
        "https://map.openaerialmap.org/#/108.05,12.68,9",
    ),
    MEKONG_DELTA(
        "mekong_delta",
        "🥥 ĐBSCL (Miền Tây)",
        "Đồng bằng sông Cửu Long (Tiền Giang, Bến Tre, Cần Thơ, Trà Vinh)",
        "Dừa, sầu riêng, xoài, bưởi, cây ăn quả & rừng ngập mặn",
        "https://map.openaerialmap.org/#/106.15,10.25,9",
    ),
    SOUTHEAST(
        "southeast_orchards",
        "🌳 Đông Nam Bộ",
        "Đông Nam Bộ (Đồng Nai, Bình Phước, Tây Ninh)",
        "Cao su, điều, bưởi/cây có múi, sầu riêng",
        "https://map.openaerialmap.org/#/106.95,11.35,9",
    ),
    NORTHERN_FORESTRY(
        "northern_forestry",
        "⛰️ Lâm nghiệp & Sạt lở Bắc Bộ",
        "Trung du & Miền núi phía Bắc (Bắc Giang, Lào Cai, Yên Bái, Sơn La)",
        "Vải thiều/nhãn, chè, rừng phòng hộ & khu vực sạt lở",
        "https://map.openaerialmap.org/#/105.20,21.50,8",
    ),
    VIETNAM_ALL(
        "vietnam_all",
        "🇻🇳 Toàn Việt Nam",
        "Toàn lãnh thổ Việt Nam (OpenAerialMap Collection)",
        "Tất cả bộ ảnh Drone/UAV mở tại Việt Nam (3cm – 10cm/pixel)",
        "https://map.openaerialmap.org/#/106.50,15.80,6",
    ),
}

data class OamSceneItem(
    val id: String,
    val title: String,
    val provider: String,
    val platform: String,
    val gsdCm: Double?,
    val suitableForDeepForest: Boolean,
    val acquisitionEnd: String?,
    val viewerUrl: String,
    val geotiffUrl: String?,
)

data class OamSearchResponse(
    val regionPreset: String,
    val regionNameVi: String,
    val focusCropsVi: String,
    val browserExploreUrl: String,
    val liveApiReachable: Boolean,
    val totalReturned: Int,
    val suitableCount: Int,
    val results: List<OamSceneItem>,
    val networkNoteVi: String?,
)

data class PascalVocXmlInput(
    val filename: String,
    val xmlContent: String,
)

data class DeepForestCsvConversionResult(
    val columns: List<String>,
    val totalBoxes: Int,
    val uniqueImagesCount: Int,
    val trainBoxesCount: Int,
    val validationBoxesCount: Int,
    val splitMode: String,
    val fullCsv: String,
    val vietnamTrainCsv: String,
    val vietnamValidationCsv: String,
)
