package com.example.test_tablet.ui.vwing

import android.graphics.BitmapFactory
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Mỗi chỉ số đo được trên vùng khoanh gồm:
 * - [icon]: 1 icon nhỏ hiển thị trực tiếp trên vùng đã khoanh
 * - [shortValue]: con số ngắn gọn đi kèm icon trên bản đồ (ví dụ: "0,80", "3,79 ha", "pH 5,4")
 * - [code]: ký hiệu ngắn (NDVI, EVI, pH, SOC...)
 * - [title]: tên đầy đủ của chỉ số trong bảng giải thích bên dưới
 * - [fullValue]: giá trị đầy đủ kèm đơn vị / khoảng phân vị
 * - [explanation]: giải thích rõ ý nghĩa của icon và đánh giá ngưỡng cho nhà vườn
 */
data class RegionHudBadge(
    val category: String,
    val label: String,
    val value: String,
    val sub: String = "",
    val color: Color = VC.mint,
    val icon: String = "📊",
    val explanation: String = "",
)

/** Dữ liệu tổng hợp của vùng đã khoanh để hiển thị bằng icon nhỏ trên vùng và bảng giải thích bên dưới. */
class RegionHudState(
    val regionId: String,
    val regionName: String,
    points: List<GeoPoint>,
    areaHa: Double,
    perimeterM: Double,
    edgeLengthsM: List<Double>,
    val treesInside: Int,
    val totalPlacedTrees: Int,
    val fruitsInside: Int,
    val stableTreesInside: Int,
    val watchTreesInside: Int,
    val needsCheckTreesInside: Int,
) {
    var points by mutableStateOf(points)
    var areaHa by mutableStateOf(areaHa)
    var perimeterM by mutableStateOf(perimeterM)
    var edgeLengthsM by mutableStateOf(edgeLengthsM)
    var deepForestImage by mutableStateOf<DeepForestClient.UploadedImage?>(null)
    var deepForestError by mutableStateOf<String?>(null)
    var deepForestSampleRegion by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var gardenAnalysis by mutableStateOf<SatGardenAnalysis?>(null)
    var gardenBitmap by mutableStateOf<ImageBitmap?>(null)
    var ndviResult by mutableStateOf<SatIndex?>(null)
    var ndviBitmap by mutableStateOf<ImageBitmap?>(null)
    var eviResult by mutableStateOf<SatIndex?>(null)
    var eviBitmap by mutableStateOf<ImageBitmap?>(null)
    var ndmiResult by mutableStateOf<SatIndex?>(null)
    var ndmiBitmap by mutableStateOf<ImageBitmap?>(null)
    var envResult by mutableStateOf<GeodataEnvResult?>(null)
    var gadmResult by mutableStateOf<GadmBoundaryResult?>(null)
    var osmResult by mutableStateOf<OsmExtractResult?>(null)
    var demHubResult by mutableStateOf<DemHubReport?>(null)
    var curriculumResult by mutableStateOf<GisCurriculumReport?>(null)
    var agroLayers3dResult by mutableStateOf<AgroLayers3DReport?>(null)
    var droughtResult by mutableStateOf<DroughtMonitoringReport?>(null)
    var droughtBitmap by mutableStateOf<ImageBitmap?>(null)
    var droughtFocusYear by mutableStateOf(2025)
    var droughtStudyMode by mutableStateOf("LOCAL_AND_BASRA")
    var droughtOverlayMetric by mutableStateOf("VHI")
    var droneMsResult by mutableStateOf<DroneMultispectralReport?>(null)
    var droneMsBitmap by mutableStateOf<ImageBitmap?>(null)
    var droneSurveyId by mutableStateOf("DJI_MAVIC_3_MULTISPECTRAL")
    var droneSprayerId by mutableStateOf("DJI_AGRAS_T50")
    var droneCropGuide by mutableStateOf("FRUIT_ORCHARD")
    var droneOverlayLayer by mutableStateOf("VRA_PRESCRIPTION")
    var droneUploadedFrames by mutableStateOf<List<DroneUploadedFrameTelemetry>>(emptyList())
    var uavAltitudeM by mutableStateOf(60.0)
    var selectedDemSource by mutableStateOf(DemSourceKey.COPERNICUS_GLO30)
    var selectedRefDemSource by mutableStateOf(DemSourceKey.NASA_SRTM_30M)
    var activeToolTitle by mutableStateOf<String?>(null)
    var activeToolSummary by mutableStateOf<String?>(null)
    var activeToolBadges by mutableStateOf<List<RegionHudBadge>>(emptyList())
    var showIconsOnPolygon by mutableStateOf(false)
    var showGeeTreeLabels by mutableStateOf(false)
    var showDeepForestBboxes by mutableStateOf(true)
    var showDeepForestTileGrid by mutableStateOf(false)
    var deepForestAnnotateMode by mutableStateOf(false)
    var deepForestResult by mutableStateOf<DeepForestPredictReport?>(null)
    var deepForestBitmap by mutableStateOf<ImageBitmap?>(null)
    var agResearchSuite by mutableStateOf<SatAgResearchSuite?>(null)
    var geeClarityTrueColorBitmap by mutableStateOf<ImageBitmap?>(null)
    var deepForestFineTuneResult by mutableStateOf<DeepForestFineTuneReport?>(null)
    var deepForestFineTuneStage by mutableStateOf<String?>(null)
    var deepForestModelId by mutableStateOf("weecology/deepforest-tree")
    var deepForestPatchSize by mutableStateOf(400)
    var deepForestPatchOverlap by mutableStateOf(0.15)
    var deepForestIouThreshold by mutableStateOf(0.15)
    var userAnnotatedBboxes by mutableStateOf<List<DeepForestAnnotationInput>>(emptyList())
    var selectedGeeTreeCode by mutableStateOf<String?>(null)
    var geeTreeStatusFilter by mutableStateOf("ALL")
    var selectedBadgeKey by mutableStateOf<String?>(null)
    var selectedQuickLayer by mutableStateOf("Tắt phủ")
    var gisTheme by mutableStateOf(GisVisualTheme.SPECTRAL_NDVI)
    var stacked3dMode by mutableStateOf(false)

    /** Toàn bộ danh sách cây / đối tượng sinh thái đếm được từ DeepForest + GEE nằm bên trong đa giác vùng khoanh. */
    fun allGeeTreesInPolygon(): List<SatTreePoint> {
        val df = deepForestResult
        val raw = if (df != null) df.boxes.map { it.toSatTreePoint() }
            else gardenAnalysis?.treeEstimation?.samplePoints ?: emptyList()
        if (points.size < 3) return raw
        return raw.filter { Geo.pointInPolygon(it.pos, points) }
    }

    /** Danh sách cây GEE sau khi lọc theo tình trạng (ALL / healthy / moderate / stressed). */
    fun visibleGeeTrees(): List<SatTreePoint> {
        val all = allGeeTreesInPolygon()
        return if (geeTreeStatusFilter == "ALL") all else all.filter { it.status == geeTreeStatusFilter }
    }

    fun refreshAll(
        cfg: SatelliteConfig,
        start: String,
        end: String,
        scope: kotlinx.coroutines.CoroutineScope,
        sig: String = "",
        onAutoOverlay: ((MapOverlay?) -> Unit)? = null,
    ) {
        if (!cfg.configured || points.size < 3) return
        loading = true
        val openPts = points
        val st = this
        scope.launch {
            withContext(Dispatchers.IO) {
                coroutineScope {
                    val envJob = launch {
                        val env = runCatching { OpenGisClient.fetchGeodataEnv(cfg, openPts) }.getOrNull()
                        if (env != null) withContext(Dispatchers.Main) { st.envResult = env }
                    }
                    val gadmJob = launch {
                        val gadm = runCatching { OpenGisClient.fetchGadmBoundary(cfg, level = 2, ring = openPts) }.getOrNull()
                        if (gadm != null) withContext(Dispatchers.Main) { st.gadmResult = gadm }
                    }
                    val osmJob = launch {
                        val osm = runCatching { OpenGisClient.extractOsm(cfg, openPts, bufferM = 300.0) }.getOrNull()
                        if (osm != null) withContext(Dispatchers.Main) { st.osmResult = osm }
                    }
                    val demHubJob = launch {
                        val dh = runCatching {
                            Terrain.fetchDemHub(
                                cfg = cfg,
                                ring = openPts,
                                bufferM = 500,
                                primarySource = st.selectedDemSource,
                                referenceSource = st.selectedRefDemSource,
                            )
                        }.getOrNull()
                        if (dh != null) withContext(Dispatchers.Main) { st.demHubResult = dh }
                    }
                    val curriculumJob = launch {
                        val cr = runCatching {
                            GisCurriculumClient.analyze(
                                cfg = cfg,
                                ring = openPts,
                                bufferM = 300.0,
                                uavAltitudeM = st.uavAltitudeM,
                            )
                        }.getOrNull()
                        if (cr != null) withContext(Dispatchers.Main) { st.curriculumResult = cr }
                    }
                    val agro3dJob = launch {
                        val ag3d = runCatching {
                            AgroLayers3DClient.analyze(
                                cfg = cfg,
                                ring = openPts,
                                cropType = "DURIAN",
                            )
                        }.getOrNull()
                        if (ag3d != null) withContext(Dispatchers.Main) { st.agroLayers3dResult = ag3d }
                    }
                    val droughtJob = launch {
                        val dr = runCatching {
                            DroughtMonitoringClient.analyze(
                                cfg = cfg,
                                ring = openPts,
                                studyMode = st.droughtStudyMode,
                                focusYear = st.droughtFocusYear,
                                vhiAlpha = 0.50,
                                overlayMetric = st.droughtOverlayMetric,
                                bufferM = 300.0,
                            )
                        }.getOrNull()
                        val drBmp = dr?.activeOverlayUrl?.takeIf { it.isNotBlank() }?.let { u ->
                            SatelliteClient.image(cfg, u).getOrNull()?.let { bytes ->
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                            }
                        }
                        if (dr != null) {
                            withContext(Dispatchers.Main) {
                                st.droughtResult = dr
                                st.droughtBitmap = drBmp
                            }
                        }
                    }
                    val droneMsJob = launch {
                        val dm = runCatching {
                            DroneMultispectralClient.analyze(
                                cfg = cfg,
                                ring = openPts,
                                surveyDroneId = st.droneSurveyId,
                                sprayerId = st.droneSprayerId,
                                cropGuide = st.droneCropGuide,
                                flightAltitudeM = st.uavAltitudeM,
                                overlayLayer = st.droneOverlayLayer,
                                uploadedFrames = st.droneUploadedFrames,
                                bufferM = 200.0,
                            )
                        }.getOrNull()
                        val dmBmp = dm?.activeOverlayUrl?.takeIf { it.isNotBlank() }?.let { u ->
                            SatelliteClient.image(cfg, u).getOrNull()?.let { bytes ->
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                            }
                        }
                        if (dm != null) {
                            withContext(Dispatchers.Main) {
                                st.droneMsResult = dm
                                st.droneMsBitmap = dmBmp
                            }
                        }
                    }
                    val gardenJob = launch {
                        val g = SatelliteClient.gardenAnalysis(cfg, openPts, start, end, SatCropKey.DURIAN, null).getOrNull()
                        val bmp = g?.imageUrl?.let { u ->
                            SatelliteClient.image(cfg, u).getOrNull()?.let { bytes ->
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()?.let { raw ->
                                    clipBitmapToPolygon(raw, g.bounds, openPts)
                                }
                            }
                        }
                        if (g != null) {
                            withContext(Dispatchers.Main) {
                                st.gardenAnalysis = g
                                st.gardenBitmap = bmp
                                if (!VUi.simpleMode && st.selectedGeeTreeCode == null) {
                                    st.selectedGeeTreeCode = st.allGeeTreesInPolygon().firstOrNull()?.code
                                }
                                if (bmp != null && st.selectedQuickLayer != "Tắt phủ") {
                                    onAutoOverlay?.invoke(
                                        MapOverlay(
                                            g.bounds,
                                            bmp,
                                            0.72f,
                                            "Tán ${g.treeEstimation.cropLabelVi} · ~${g.treeEstimation.estimatedTrees} cây · NDVI ${g.indices.firstOrNull { it.index == "NDVI" }?.mean?.let { fmtNum(it, 2) } ?: "—"}",
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    val deepForestJob = launch {
                        val dfAttempt = runCatching {
                            DeepForestClient.predict(
                                cfg = cfg,
                                ring = openPts,
                                modelId = st.deepForestModelId,
                                cropType = "durian",
                                patchSize = st.deepForestPatchSize,
                                patchOverlap = st.deepForestPatchOverlap,
                                iouThreshold = st.deepForestIouThreshold,
                                imageId = st.deepForestImage?.imageId,
                            )
                        }
                        val df = dfAttempt.getOrNull()
                        withContext(Dispatchers.Main) {
                            st.deepForestError = dfAttempt.exceptionOrNull()?.message
                        }
                        val dfBmp = df?.overlayUrl?.takeIf { it.isNotBlank() }?.let { u ->
                            SatelliteClient.image(cfg, u).getOrNull()?.let { bytes ->
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                            }
                        }
                        if (df != null) {
                            withContext(Dispatchers.Main) {
                                st.deepForestResult = df
                                st.deepForestBitmap = dfBmp
                            }
                        }
                    }
                    val agResearchJob = launch {
                        val ag = SatelliteClient.agResearchSuite(cfg, openPts, SatCropKey.DURIAN).getOrNull()
                        if (ag != null) {
                            withContext(Dispatchers.Main) {
                                st.agResearchSuite = ag
                                if (st.geeClarityTrueColorBitmap == null && st.gardenBitmap != null) {
                                    st.geeClarityTrueColorBitmap = st.gardenBitmap
                                }
                            }
                        }
                    }
                    envJob.join(); gadmJob.join(); osmJob.join(); demHubJob.join(); curriculumJob.join(); agro3dJob.join(); droughtJob.join(); droneMsJob.join(); gardenJob.join(); deepForestJob.join(); agResearchJob.join()
                    withContext(Dispatchers.Main) {
                        if (st.geeClarityTrueColorBitmap == null && st.gardenBitmap != null) {
                            st.geeClarityTrueColorBitmap = st.gardenBitmap
                        }
                        st.loading = false
                        if (sig.isNotBlank() && (st.gardenAnalysis != null || st.envResult != null || st.gadmResult != null || st.osmResult != null || st.demHubResult != null || st.curriculumResult != null || st.droughtResult != null || st.droneMsResult != null || st.deepForestResult != null || st.agResearchSuite != null)) {
                            regionHudCache[sig] = CachedRegionData(st.gardenAnalysis, st.gardenBitmap, st.envResult, st.gadmResult, st.osmResult, st.demHubResult, st.curriculumResult, st.droughtResult, st.droughtBitmap, st.droneMsResult, st.droneMsBitmap, st.deepForestResult, st.deepForestBitmap, st.agResearchSuite, st.geeClarityTrueColorBitmap)
                        }
                    }
                }
            }
        }
    }

    fun updateUavFlightPlan(
        cfg: SatelliteConfig,
        altitudeM: Double,
        scope: kotlinx.coroutines.CoroutineScope,
        onDone: ((String) -> Unit)? = null,
    ) {
        uavAltitudeM = altitudeM
        if (!cfg.configured || points.size < 3) return
        val openPts = points
        val st = this
        loading = true
        scope.launch {
            val cr = withContext(Dispatchers.IO) {
                runCatching {
                    GisCurriculumClient.analyze(
                        cfg = cfg,
                        ring = openPts,
                        bufferM = 300.0,
                        uavAltitudeM = altitudeM,
                    )
                }.getOrNull()
            }
            loading = false
            if (cr != null) {
                st.curriculumResult = cr
                onDone?.invoke("Đã cập nhật độ cao bay UAV H = ${fmtNum(altitudeM, 0)}m → GSD = ${fmtNum(cr.m7.gsdCmPerPixel, 2)} cm/pixel (${cr.m7.totalPhotos} ảnh).")
            }
        }
    }

    fun updateDroughtAnalysis(
        cfg: SatelliteConfig,
        studyMode: String = droughtStudyMode,
        focusYear: Int = droughtFocusYear,
        overlayMetric: String = droughtOverlayMetric,
        scope: kotlinx.coroutines.CoroutineScope,
        onOverlay: ((MapOverlay?) -> Unit)? = null,
        onDone: ((String) -> Unit)? = null,
    ) {
        droughtStudyMode = studyMode
        droughtFocusYear = focusYear
        droughtOverlayMetric = overlayMetric
        if (!cfg.configured || points.size < 3) return
        val openPts = points
        val st = this
        loading = true
        scope.launch {
            val pair = withContext(Dispatchers.IO) {
                val dr = runCatching {
                    DroughtMonitoringClient.analyze(
                        cfg = cfg,
                        ring = openPts,
                        studyMode = studyMode,
                        focusYear = focusYear,
                        vhiAlpha = 0.50,
                        overlayMetric = overlayMetric,
                        bufferM = 300.0,
                    )
                }.getOrNull()
                val bmp = dr?.activeOverlayUrl?.takeIf { it.isNotBlank() }?.let { u ->
                    SatelliteClient.image(cfg, u).getOrNull()?.let { bytes ->
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
                }
                dr to bmp
            }
            loading = false
            val dr = pair.first
            val bmp = pair.second
            if (dr != null) {
                st.droughtResult = dr
                st.droughtBitmap = bmp
                if (bmp != null && onOverlay != null) {
                    onOverlay(
                        MapOverlay(
                            dr.bounds,
                            bmp,
                            0.74f,
                            "Hạn hán $focusYear · ${dr.overlayMetric} · VHI ${fmtNum(dr.focusYearSnapshot.vhiMeanPct, 1)}% (${dr.focusYearSnapshot.droughtClassVi})",
                        ),
                    )
                }
                onDone?.invoke("Đã nạp bản đồ ${dr.overlayMetric} năm $focusYear · VHI=${fmtNum(dr.focusYearSnapshot.vhiMeanPct, 1)}% · LST=${fmtNum(dr.focusYearSnapshot.lstMeanC, 1)}°C.")
            } else {
                onDone?.invoke("Không tải được dữ liệu theo dõi hạn hán 26 năm.")
            }
        }
    }

    fun updateDroneMultispectral(
        cfg: SatelliteConfig,
        surveyDroneId: String = droneSurveyId,
        sprayerId: String = droneSprayerId,
        cropGuide: String = droneCropGuide,
        overlayLayer: String = droneOverlayLayer,
        uploadedFrames: List<DroneUploadedFrameTelemetry> = droneUploadedFrames,
        scope: kotlinx.coroutines.CoroutineScope,
        onOverlay: ((MapOverlay?) -> Unit)? = null,
        onDone: ((String) -> Unit)? = null,
    ) {
        droneSurveyId = surveyDroneId
        droneSprayerId = sprayerId
        droneCropGuide = cropGuide
        droneOverlayLayer = overlayLayer
        droneUploadedFrames = uploadedFrames
        if (!cfg.configured || points.size < 3) return
        val openPts = points
        val st = this
        loading = true
        scope.launch {
            val pair = withContext(Dispatchers.IO) {
                val dm = runCatching {
                    DroneMultispectralClient.analyze(
                        cfg = cfg,
                        ring = openPts,
                        surveyDroneId = surveyDroneId,
                        sprayerId = sprayerId,
                        cropGuide = cropGuide,
                        flightAltitudeM = st.uavAltitudeM,
                        overlayLayer = overlayLayer,
                        uploadedFrames = uploadedFrames,
                        bufferM = 200.0,
                    )
                }.getOrNull()
                val bmp = dm?.activeOverlayUrl?.takeIf { it.isNotBlank() }?.let { u ->
                    SatelliteClient.image(cfg, u).getOrNull()?.let { bytes ->
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
                }
                dm to bmp
            }
            loading = false
            val dm = pair.first
            val bmp = pair.second
            if (dm != null) {
                st.droneMsResult = dm
                st.droneMsBitmap = bmp
                if (bmp != null && onOverlay != null) {
                    onOverlay(
                        MapOverlay(
                            dm.bounds,
                            bmp,
                            0.76f,
                            "Drone ${dm.overlayLayer} · ${dm.step3.sprayerHardware.name} · Tiết kiệm ${fmtNum(dm.step3.chemicalSavingsPct, 1)}% thuốc/phân",
                        ),
                    )
                }
                onDone?.invoke("Đã cập nhật Bản đồ Drone ${dm.overlayLayer} (${dm.step1.surveyDrone.name} → ${dm.step3.sprayerHardware.name}): tiết kiệm ${fmtNum(dm.step3.chemicalSavingsPct, 1)}% vật tư.")
            } else {
                onDone?.invoke("Không tải được dữ liệu phân tích Camera Đa phổ Drone.")
            }
        }
    }

    fun switchDemSource(
        cfg: SatelliteConfig,
        primary: DemSourceKey,
        scope: kotlinx.coroutines.CoroutineScope,
        onDone: ((String) -> Unit)? = null,
    ) {
        selectedDemSource = primary
        if (!cfg.configured || points.size < 3) return
        val openPts = points
        val ref = selectedRefDemSource
        val st = this
        loading = true
        scope.launch {
            val dh = withContext(Dispatchers.IO) {
                runCatching {
                    Terrain.fetchDemHub(
                        cfg = cfg,
                        ring = openPts,
                        bufferM = 500,
                        primarySource = primary,
                        referenceSource = ref,
                    )
                }.getOrNull()
            }
            loading = false
            if (dh != null) {
                st.demHubResult = dh
                onDone?.invoke("Đã nạp ${primary.shortLabel} (${primary.resolutionLabel}) · pyDEM & demcoreg cập nhật.")
            } else {
                onDone?.invoke("Không tải được nguồn ${primary.shortLabel}.")
            }
        }
    }

    /** Thêm 1 khung bao tán mẫu (Bounding Box) tại vị trí chạm trên Google Map để phục vụ Fine-tune DeepForest. */
    fun addDeepForestAnnotationAt(pt: GeoPoint, diameterM: Double = 5.2) {
        val halfM = (diameterM * 0.5).coerceIn(1.2, 12.0)
        val dLat = halfM / 110_574.0
        val cosLat = kotlin.math.cos(Math.toRadians(pt.lat)).coerceAtLeast(0.1)
        val dLon = halfM / (111_320.0 * cosLat)
        val box = GeoBounds(
            south = pt.lat - dLat,
            west = pt.lng - dLon,
            north = pt.lat + dLat,
            east = pt.lng + dLon,
        )
        val nextId = "GT-%03d".format(userAnnotatedBboxes.size + 1)
        val target = DeepForestModelPreset.fromId(deepForestModelId).targetClass
        userAnnotatedBboxes = userAnnotatedBboxes + DeepForestAnnotationInput(nextId, box, target, false)
    }

    /** Changes only this analysis selection; the saved garden polygon is untouched. */
    fun selectDeepForestSampleRegion() {
        val lat = 11.760461
        val lon = 107.642313
        val dLat = 100.0 / 110_574.0
        val dLon = 100.0 / (111_320.0 * kotlin.math.cos(Math.toRadians(lat)))
        points = listOf(GeoPoint(lat - dLat, lon - dLon), GeoPoint(lat - dLat, lon + dLon),
            GeoPoint(lat + dLat, lon + dLon), GeoPoint(lat + dLat, lon - dLon))
        areaHa = Geo.polygonAreaHa(points)
        perimeterM = 800.0
        edgeLengthsM = List(4) { 200.0 }
        deepForestSampleRegion = true
        deepForestImage = null
        deepForestResult = null
        deepForestBitmap = null
        deepForestError = null
        gardenAnalysis = null
        userAnnotatedBboxes = emptyList()
    }

    fun runDeepForestPredict(
        cfg: SatelliteConfig,
        modelId: String = deepForestModelId,
        patchSize: Int = deepForestPatchSize,
        patchOverlap: Double = deepForestPatchOverlap,
        iouThreshold: Double = deepForestIouThreshold,
        scope: kotlinx.coroutines.CoroutineScope,
        onOverlay: ((MapOverlay?) -> Unit)? = null,
        onDone: ((String) -> Unit)? = null,
    ) {
        deepForestModelId = modelId
        deepForestPatchSize = patchSize
        deepForestPatchOverlap = patchOverlap
        deepForestIouThreshold = iouThreshold
        if (!cfg.configured || points.size < 3) {
            deepForestError = "Cần cấu hình máy chủ và chọn vùng có ít nhất ba đỉnh."
            onDone?.invoke(deepForestError!!)
            return
        }
        val openPts = points
        val st = this
        loading = true
        scope.launch {
            val pair = withContext(Dispatchers.IO) {
                val attempt = runCatching {
                    DeepForestClient.predict(
                        cfg = cfg,
                        ring = openPts,
                        modelId = modelId,
                        cropType = st.gardenAnalysis?.treeEstimation?.cropType ?: "durian",
                        patchSize = patchSize,
                        patchOverlap = patchOverlap,
                        iouThreshold = iouThreshold,
                        imageId = st.deepForestImage?.imageId,
                    )
                }
                val df = attempt.getOrNull()
                val bmp = df?.overlayUrl?.takeIf { it.isNotBlank() }?.let { u ->
                    SatelliteClient.image(cfg, u).getOrNull()?.let { bytes ->
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
                }
                Triple(df, bmp, attempt.exceptionOrNull()?.message)
            }
            loading = false
            deepForestError = pair.third
            val df = pair.first
            val bmp = pair.second
            if (df != null) {
                st.deepForestResult = df
                st.deepForestBitmap = bmp
                st.showDeepForestBboxes = true
                if (bmp != null && onOverlay != null) {
                    onOverlay(
                        MapOverlay(
                            df.bounds,
                            bmp,
                            0.78f,
                            "DeepForest (${df.labelVi}) · ${df.mergedBoxesAfterNms} khung tán",
                        ),
                    )
                }
                onDone?.invoke("DeepForest phát hiện ${df.boxes.size} tán từ ảnh RGB. Chưa đo sức khỏe cây.")
            } else {
                onDone?.invoke(deepForestError ?: "Không chạy được mô hình DeepForest trên vùng này.")
            }
        }
    }

    /** Fine-tunes on the uploaded RGB image using the user's boxes; progress is polled from the server job. */
    fun runDeepForestFineTune(
        cfg: SatelliteConfig,
        epochs: Int = 6,
        scope: kotlinx.coroutines.CoroutineScope,
        onDone: ((String) -> Unit)? = null,
    ) {
        val image = deepForestImage
        val labels = userAnnotatedBboxes
        val refusal = when {
            !cfg.configured || points.size < 3 -> "Cần cấu hình máy chủ và chọn vùng có ít nhất ba đỉnh."
            image == null -> "Fine-tune cần ảnh RGB do bạn tải lên (ảnh bản đồ máy chủ không dùng để huấn luyện)."
            labels.size < 8 -> "Cần ít nhất 8 khung nhãn trên ảnh (đang có ${labels.size}); nên gán 30 khung trở lên rải đều ảnh."
            else -> null
        }
        if (refusal != null || image == null) {
            onDone?.invoke(refusal ?: "")
            return
        }
        val ring = points
        val base = deepForestModelId.takeIf { it.startsWith("weecology/") } ?: "weecology/deepforest-tree"
        val crop = gardenAnalysis?.treeEstimation?.cropType ?: "durian"
        loading = true
        deepForestError = null
        deepForestFineTuneResult = null
        deepForestFineTuneStage = "queued"
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    var job = DeepForestClient.startFineTune(cfg, ring, image.imageId, labels, base, crop, epochs)
                    while (job.state == "queued" || job.state == "running") {
                        deepForestFineTuneStage = job.stage
                        kotlinx.coroutines.delay(4_000)
                        job = DeepForestClient.fineTuneStatus(cfg, job.jobId)
                    }
                    job
                }
            }
            loading = false
            deepForestFineTuneStage = null
            val job = outcome.getOrNull()
            val report = job?.result
            if (report != null) {
                deepForestFineTuneResult = report
                deepForestModelId = report.checkpointId
                val map = report.finalMap50?.let { "mAP50 ${fmtNum(it, 2)}" } ?: "chưa có mAP"
                onDone?.invoke("Fine-tune xong: ${report.checkpointId} ($map trên ${report.validationAnnotationsCount} nhãn kiểm định). Bấm Quét để dùng.")
            } else {
                deepForestError = job?.error ?: outcome.exceptionOrNull()?.message ?: "Fine-tune thất bại."
                onDone?.invoke(deepForestError!!)
            }
        }
    }

    /**
     * Danh sách các cặp (Icon + Số ngắn gọn) vẽ trực tiếp trên vùng đã khoanh trong lòng bản đồ.
     * Ngắn gọn, mỗi chỉ số 1 icon nhỏ để nhìn thoáng, không che khuất bản đồ.
     */
    fun compactPolygonPills(): List<Triple<String, String, Color>> {
        val pills = mutableListOf<Triple<String, String, Color>>()
        pills += Triple("📐", "${fmtNum(areaHa, 2)} ha", VC.mint)
        val g = gardenAnalysis
        if (g != null) {
            pills += Triple("🌳", "~${g.treeEstimation.estimatedTrees} cây", VC.lime)
            pills += Triple("🎯", "${fmtNum(g.treeEstimation.overallAccuracyPct, 0)}% ±${fmtNum(g.treeEstimation.spatialPrecisionM, 1)}m", VC.mint)
            g.indices.forEach { idx ->
                val ic = iconForIndex(idx.index)
                val v = idx.mean?.let { fmtNum(it, 2) } ?: "—"
                pills += Triple(ic, v, VC.cyan)
            }
            g.terrain.elevationMeanM?.let { elev ->
                pills += Triple("⛰", "${fmtNum(elev, 0)}m", VC.teal)
            }
        } else {
            pills += Triple("🏷", "$treesInside/$totalPlacedTrees cây", VC.teal)
        }
        demHubResult?.let { dh ->
            if (g?.terrain?.elevationMeanM == null) {
                pills += Triple("⛰", "${fmtNum(dh.hydrology.elevMeanM, 0)}m", VC.teal)
            }
            pills += Triple("🌊", "TWI ${fmtNum(dh.hydrology.twiMean, 1)}", VC.cyan)
            pills += Triple("⚖", "ΔZ ${fmtNum(dh.coregistration.dzBiasM, 1)}m", VC.lime)
        }
        envResult?.let { env ->
            env.soilProperties.firstOrNull { it.key == "phh2o" }?.let {
                pills += Triple("⚗", "pH ${fmtNum(it.d0to5, 1)}", VC.amber)
            }
            env.soilProperties.firstOrNull { it.key == "soc" }?.let {
                pills += Triple("🌱", "${fmtNum(it.d0to5, 1)}g/kg", VC.mint)
            }
            pills += Triple("🌧", "${fmtNum(env.annualPrecipMm, 0)}mm", VC.cyan)
            pills += Triple("🌡", "${fmtNum(env.tavgC, 1)}°C", VC.amber)
        }
        osmResult?.let { osm ->
            pills += Triple("🛣", "${fmtNum(osm.roadLengthKm, 1)}km", VC.teal)
        }
        droughtResult?.let { dr ->
            pills += Triple("🌾", "VHI ${fmtNum(dr.focusYearSnapshot.vhiMeanPct, 0)}%", VC.lime)
            pills += Triple("🔥", "LST ${fmtNum(dr.focusYearSnapshot.lstMeanC, 1)}°C", VC.amber)
        }
        droneMsResult?.let { dm ->
            pills += Triple("🚁", "VRA -${fmtNum(dm.step3.chemicalSavingsPct, 0)}%", VC.mint)
        }
        return pills
    }

    /**
     * Toàn bộ danh sách chỉ số kèm Icon, Giá trị và Giải thích rõ ràng cho Bảng giải thích bên dưới bản đồ.
     */
    fun allBadges(): List<RegionHudBadge> {
        val out = mutableListOf<RegionHudBadge>()

        // 0. Công cụ đang chạy (nếu có)
        if (!activeToolTitle.isNullOrBlank() && !activeToolSummary.isNullOrBlank()) {
            out += RegionHudBadge(
                category = "Công cụ đang đo",
                label = activeToolTitle!!,
                value = activeToolSummary!!,
                sub = "Kết quả vừa thực thi trên vùng khoanh",
                color = VC.lime,
                icon = "⚡",
                explanation = "Kết quả từ công cụ phân tích chuyên sâu bạn vừa chọn ở bảng điều khiển.",
            )
        }
        out.addAll(activeToolBadges)

        // 1. Hình học & Cây trồng
        out += RegionHudBadge(
            category = "Hình học & Cây",
            label = "Diện tích & Chu vi vùng",
            value = "${fmtNum(areaHa, 2)} ha",
            sub = "Chu vi ${fmtNum(perimeterM, 0)} m · ${points.size} đỉnh",
            color = VC.mint,
            icon = "📐",
            explanation = "Diện tích phẳng và tổng chiều dài đường ranh giới của vùng đất đã khoanh (tính trên hệ chiếu mét UTM).",
        )

        val g = gardenAnalysis
        if (g != null) {
            val te = g.treeEstimation
            val hb = te.healthBreakdown
            out += RegionHudBadge(
                category = "Hình học & Cây",
                label = "Số cây ước tính (AI & GEE)",
                value = "~${te.estimatedTrees} cây",
                sub = "Ước tính từ ảnh vệ tinh: Google Sat 0,3m (${te.highresDetectedCrowns} tán) × GEE S2 (${te.spectralEstimatedTrees} cây)",
                color = VC.lime,
                icon = "🌳",
                explanation = "Số cây ước tính kết hợp giữa thuật toán tách đỉnh vòm tán trên ảnh vệ tinh 0,3 m/px (Zoom 19) và mặt nạ tán quang phổ GEE Sentinel-2 10m.",
            )
            out += RegionHudBadge(
                category = "Hình học & Cây",
                label = "Điểm tin cậy ước tính",
                value = "${fmtNum(te.overallAccuracyPct, 1)}%",
                sub = "Khớp chéo ${fmtNum(te.crossValidationAgreementPct, 1)}% · Độ lệch ước tính ±${fmtNum(te.spatialPrecisionM, 2)} m",
                color = VC.mint,
                icon = "🎯",
                explanation = "Điểm tin cậy nội bộ ước tính giữa thuật toán tách vòm tán 0,3m (${fmtNum(te.crownDetectionConfidencePct, 1)}%) và quang phổ GEE (${fmtNum(te.spectralConfidencePct, 1)}%). Cần kiểm chứng thực địa hoặc quét QR cây để đạt độ chính xác tuyệt đối.",
            )
            out += RegionHudBadge(
                category = "Hình học & Cây",
                label = "Sức khỏe tán (Khỏe/TB/Yếu)",
                value = "${hb.healthyTrees} / ${hb.moderateTrees} / ${hb.stressedTrees}",
                sub = "Tán khỏe ${fmtNum(hb.healthyHa, 2)} ha · Yếu ${fmtNum(hb.stressedHa, 2)} ha",
                color = VC.mint,
                icon = "💚",
                explanation = "Phân loại số cây trong vùng theo sức sống quang phổ: Khỏe mạnh / Trung bình / Suy yếu cần đi kiểm tra.",
            )
        }

        out += RegionHudBadge(
            category = "Hình học & Cây",
            label = "Cây hồ sơ trong ranh",
            value = "$treesInside / $totalPlacedTrees cây",
            sub = "$fruitsInside trái · Khỏe $stableTreesInside · Cần KT $needsCheckTreesInside",
            color = VC.teal,
            icon = "🏷",
            explanation = "Số cây đã gắn tọa độ GPS trong hồ sơ vườn nằm bên trong đa giác vùng khoanh và tổng số trái đã đếm.",
        )

        // 2. 7 Chỉ số Vệ tinh Sentinel-2
        if (g != null) {
            g.indices.forEach { idx ->
                val col = when {
                    idx.statusVi.contains("tốt", true) || idx.statusVi.contains("cao", true) || idx.statusVi.contains("khỏe", true) -> VC.mint
                    idx.statusVi.contains("thấp", true) || idx.statusVi.contains("kém", true) || idx.statusVi.contains("khô", true) -> VC.amber
                    else -> VC.cyan
                }
                out += RegionHudBadge(
                    category = "Vệ tinh Sentinel-2",
                    label = "${idx.index} (${idx.title})",
                    value = idx.mean?.let { fmtNum(it, 2) } ?: "—",
                    sub = "${idx.statusVi} · P5–95: ${idx.p5?.let { fmtNum(it, 2) } ?: "—"}..${idx.p95?.let { fmtNum(it, 2) } ?: "—"}",
                    color = col,
                    icon = iconForIndex(idx.index),
                    explanation = idx.interpretationVi.ifBlank { defaultIndexExplanation(idx.index) },
                )
            }
            g.landCover.values.firstOrNull()?.let { lc ->
                out += RegionHudBadge(
                    category = "Vệ tinh Sentinel-2",
                    label = "Lớp phủ mặt đất (LULC)",
                    value = lc.dominantLabelVi,
                    sub = "Tán cây ${fmtNum(lc.treeCanopyPct, 0)}% · Cây trồng ${fmtNum(lc.cropPct, 0)}%",
                    color = VC.teal,
                    icon = "🗺",
                    explanation = "Phân loại lớp phủ bề mặt từ vệ tinh (${lc.title}): tỷ lệ diện tích cây lâu năm, mùa vụ, mặt nước và đất trống.",
                )
            }
            g.terrain.elevationMeanM?.let { elev ->
                out += RegionHudBadge(
                    category = "Địa hình & Khí hậu",
                    label = "Cao độ & Độ dốc (DEM)",
                    value = "${fmtNum(elev, 0)} m",
                    sub = "Dốc ${fmtNum(g.terrain.slopeMeanDeg ?: 0.0, 1)}° · ${g.terrain.terrainClassVi}",
                    color = VC.cyan,
                    icon = "⛰",
                    explanation = "Độ cao trung bình so với mực nước biển và độ dốc địa hình (SRTM/Copernicus DEM 30m) giúp đánh giá thoát nước.",
                )
            }
        }

        // 2b. Phân tích Địa hình – Thủy văn chuyên sâu (`pyDEM`, `demcoreg`, `FABDEM`, `srtm.py`, 12 nguồn DEM)
        demHubResult?.let { dh ->
            val hy = dh.hydrology
            val cg = dh.coregistration
            out += RegionHudBadge(
                category = "Địa hình & Khí hậu",
                label = "Mặt đất trần FABDEM & Chiều cao tán",
                value = "${fmtNum(hy.bareEarthMeanM, 1)} m",
                sub = "DSM ${fmtNum(hy.elevMeanM, 1)} m · Tán/công trình ~${fmtNum(hy.canopyHeightMeanM, 1)} m",
                color = VC.lime,
                icon = "🌲",
                explanation = "So sánh độ cao bề mặt (${dh.primarySource.shortLabel}) với mô hình mặt đất trần FABDEM 30m (đã bóc tách chiều cao tán cây và công trình).",
            )
            out += RegionHudBadge(
                category = "Địa hình & Khí hậu",
                label = "Thủy văn & Tụ thủy (pyDEM TWI)",
                value = "TWI ${fmtNum(hy.twiMean, 2)}",
                sub = "Tụ thủy max ${fmtNum(hy.catchmentMaxHa, 2)} ha · HAND ${fmtNum(hy.handMeanM, 1)} m · Ẩm cao ${fmtNum(hy.highWetnessAreaPct, 0)}%",
                color = VC.cyan,
                icon = "🌊",
                explanation = "Chỉ số ẩm ướt địa hình TWI = ln(a / tan β) và diện tích vùng tụ thủy thượng nguồn tính bằng thư viện pyDEM giúp xác định tuyến thoát nước.",
            )
            out += RegionHudBadge(
                category = "Địa hình & Khí hậu",
                label = "Hướng sườn & Độ gồ ghề (pyDEM)",
                value = "${hy.aspectCardinalVi} (${fmtNum(hy.aspectMeanDeg, 0)}°)",
                sub = "Dốc ${fmtNum(hy.slopeMeanDeg, 1)}° (${fmtNum(hy.slopeMeanPct, 1)}%) · TRI ${fmtNum(hy.triMeanM, 2)} m",
                color = VC.teal,
                icon = "🧭",
                explanation = "Hướng sườn đón nắng/gió chủ đạo, độ dốc phần trăm và chỉ số gồ ghề địa hình TRI tính trên lưới ${dh.cols}×${dh.rows} ô.",
            )
            out += RegionHudBadge(
                category = "Địa hình & Khí hậu",
                label = "Đồng đăng ký 3D DEM (demcoreg)",
                value = "RMSE ${fmtNum(cg.rmseAfterM, 2)} m",
                sub = "ΔX ${fmtNum(cg.dxEastM, 1)}m · ΔY ${fmtNum(cg.dyNorthM, 1)}m · ΔZ ${fmtNum(cg.dzBiasM, 2)}m (NMAD ${fmtNum(cg.nmadAfterM, 2)}m)",
                color = VC.mint,
                icon = "⚖",
                explanation = cg.summaryVi,
            )
        }

        // 3. Thổ nhưỡng SoilGrids 250m & Khí hậu WorldClim
        envResult?.let { env ->
            out += RegionHudBadge(
                category = "Thổ nhưỡng SoilGrids",
                label = "Nhóm đất FAO/WRB",
                value = env.wrbClass.substringBefore(" ("),
                sub = env.wrbClass.substringAfter("(", "").removeSuffix(")"),
                color = VC.amber,
                icon = "🪨",
                explanation = "Nhóm đất chính theo phân loại quốc tế FAO/WRB tại tọa độ vườn (ISRIC SoilGrids 250m).",
            )
            env.soilProperties.forEach { prop ->
                val (ic, shortName, expl) = soilMeta(prop.key, prop.label)
                out += RegionHudBadge(
                    category = "Thổ nhưỡng SoilGrids",
                    label = shortName,
                    value = "${fmtNum(prop.d0to5, if (prop.key == "nitrogen" || prop.key == "bdod") 2 else 1)} ${prop.unit}",
                    sub = "Tầng 5–15cm: ${fmtNum(prop.d5to15, 1)} · 15–30cm: ${fmtNum(prop.d15to30, 1)} ${prop.unit}",
                    color = VC.mint,
                    icon = ic,
                    explanation = expl,
                )
            }
            out += RegionHudBadge(
                category = "Địa hình & Khí hậu",
                label = "Lượng mưa năm (WorldClim)",
                value = "${fmtNum(env.annualPrecipMm, 0)} mm",
                sub = "Mùa mưa ${fmtNum(env.wetSeasonPrecipMm, 0)} mm · Mùa khô ${fmtNum(env.drySeasonPrecipMm, 0)} mm",
                color = VC.cyan,
                icon = "🌧",
                explanation = "Tổng lượng mưa trung bình năm và phân bố mưa mùa mưa / mùa khô (WorldClim v2.1) để lập lịch tưới.",
            )
            out += RegionHudBadge(
                category = "Địa hình & Khí hậu",
                label = "Nhiệt độ & Bức xạ",
                value = "${fmtNum(env.tavgC, 1)} °C",
                sub = "${fmtNum(env.tminC, 1)}–${fmtNum(env.tmaxC, 1)}°C · Bức xạ ${fmtNum(env.solarRadMjM2Day, 1)} MJ/m²/ngày",
                color = VC.amber,
                icon = "🌡",
                explanation = "Nhiệt độ trung bình năm (biên độ thấp–cao nhất) và cường độ bức xạ mặt trời phục vụ quang hợp.",
            )
        }

        // 4. Hành chính GADM & Hạ tầng OSM
        gadmResult?.let { gadm ->
            val name = listOf(gadm.name2, gadm.name1, gadm.name0).filter { it.isNotBlank() }.joinToString(", ")
            out += RegionHudBadge(
                category = "Hành chính & Hạ tầng",
                label = "Ranh hành chính GADM",
                value = name.ifBlank { gadm.gid2.ifBlank { gadm.gid1 } },
                sub = "Mã GID: ${gadm.gid2.ifBlank { gadm.gid1 }} · ${fmtNum(gadm.areaHa, 0)} ha",
                color = VC.teal,
                icon = "🏛",
                explanation = "Đơn vị hành chính chuẩn quốc tế GADM v4.1 chứa khu vườn (phục vụ truy xuất nguồn gốc / EUDR).",
            )
        }
        osmResult?.let { osm ->
            out += RegionHudBadge(
                category = "Hành chính & Hạ tầng",
                label = "Giao thông & Thủy văn OSM",
                value = "${fmtNum(osm.roadLengthKm, 2)} km đường",
                sub = "Kênh/suối ${fmtNum(osm.waterwayLengthKm, 2)} km · ${osm.buildingsCount} công trình",
                color = VC.cyan,
                icon = "🛣",
                explanation = "Mạng lưới đường vận chuyển nông sản, kênh suối tự nhiên và công trình trong bán kính quanh vườn (OpenStreetMap).",
            )
        }

        // 5. Học phần GIS · Viễn thám · Địa thống kê · UAV Quang trắc (Modules 1 -> 7)
        curriculumResult?.let { cr ->
            out += RegionHudBadge(
                category = "Học phần GIS & UAV (M1–M7)",
                label = "Hệ tọa độ PCS VN-2000 & Georef",
                value = "${cr.m123.vn2000Code} · RMSE ±${fmtNum(cr.m123.affineRmseTotalM, 2)}m",
                sub = "VN-2000 (${fmtNum(cr.m123.vn2000CentroidXM, 0)}; ${fmtNum(cr.m123.vn2000CentroidYM, 0)}) · Diện tích phẳng ${fmtNum(cr.m123.vn2000PlanarAreaM2, 0)} m²",
                color = VC.mint,
                icon = "🌐",
                explanation = "Module 1–3: Chuyển đổi hệ tọa độ cầu GCS WGS 84 (EPSG:4326) sang hệ chiếu phẳng PCS UTM Zone ${cr.m123.utmZone} & VN-2000 (${cr.m123.vn2000Code}) và nắn chỉnh hình học Affine bậc 1 từ các điểm khống chế GCPs.",
            )
            out += RegionHudBadge(
                category = "Học phần GIS & UAV (M1–M7)",
                label = "Nhiệt độ bề mặt LST & Đảo nhiệt UHI",
                value = "LST ${fmtNum(cr.m46.lstMeanC, 1)} °C · UHI ${fmtNum(cr.m46.uhiHotspotAreaPct, 1)}%",
                sub = "Landsat TIRS Tb ${fmtNum(cr.m46.brightnessTempTbC, 1)}°C · ε ${fmtNum(cr.m46.surfaceEmissivityEps, 4)} · LULC Kappa ${fmtNum(cr.m46.kappaCoefficient, 2)}",
                color = VC.amber,
                icon = "🌡",
                explanation = "Module 4 & 6: Nhiệt độ bề mặt đất (LST) trích xuất từ kênh nhiệt hồng ngoại TIRS hiệu chỉnh độ phát xạ ε theo Pv(NDVI), chồng lớp NDBI phát hiện vùng tích nhiệt UHI và phân loại lớp phủ LULC (K-Means / Random Forest).",
            )
            out += RegionHudBadge(
                category = "Học phần GIS & UAV (M1–M7)",
                label = "Nội suy Kriging & Điểm nóng Gi*",
                value = "PM2.5 ${fmtNum(cr.m5.krigingPm25Ugm3, 1)} ±${fmtNum(cr.m5.krigingStdError, 1)} · Zmax ${fmtNum(cr.m5.maxZScore, 2)}",
                sub = "AQI ${cr.m5.vnAqi} · Nước pH ${fmtNum(cr.m5.waterPh, 2)} (DO ${fmtNum(cr.m5.waterDoMgl, 1)}) · Ồn ${fmtNum(cr.m5.meanNoiseDba, 1)} dBA",
                color = VC.cyan,
                icon = "📈",
                explanation = "Module 5: Nội suy địa thống kê Ordinary Kriging (Semivariogram) & IDW cho chất lượng không khí/nước/tiếng ồn, kết hợp mật độ KDE và thống kê Getis-Ord Gi* nhận diện cụm điểm nóng có ý nghĩa thống kê.",
            )
            out += RegionHudBadge(
                category = "Học phần GIS & UAV (M1–M7)",
                label = "Khảo sát UAV & Agisoft Metashape",
                value = "GSD ${fmtNum(cr.m7.gsdCmPerPixel, 2)} cm/px · ${cr.m7.totalPhotos} ảnh",
                sub = "Bay H=${fmtNum(cr.m7.altitudeHM, 0)}m · Phủ dọc ${fmtNum(cr.m7.forwardOverlapPct, 0)}% / ngang ${fmtNum(cr.m7.sideOverlapPct, 0)}% · ${cr.m7.rtkGcps.size} GCP RTK",
                color = VC.lime,
                icon = "🚁",
                explanation = "Module 7: Tính GSD = (H × Pixel Size)/F, lập kế hoạch đường bay UAV chồng phủ dọc/ngang, bố trí điểm khống chế mặt đất RTK/GNSS và quy trình 7 bước dựng Orthomosaic/DSM/DTM trên Agisoft Metashape.",
            )
        }

        // 7. Theo dõi Hạn hán 26 Năm (2000–2025: MODIS NDVI · VCI · LST · TCI · VHI · CHIRPS · TerraClimate · FAO GAUL)
        droughtResult?.let { dr ->
            val snap = dr.focusYearSnapshot
            val dec = dr.decouplingAnalysis
            val chg = dr.ndviChange2000To2025
            out += RegionHudBadge(
                category = "Hạn hán 26 năm (2000–2025)",
                label = "Sức khỏe Thực vật VHI (${dr.focusYear})",
                value = "VHI ${fmtNum(snap.vhiMeanPct, 1)}% · VCI ${fmtNum(snap.vciMeanPct, 1)}%",
                sub = "${snap.droughtClassVi} · TCI ${fmtNum(snap.tciMeanPct, 1)}% · Diện tích chịu hạn ${fmtNum(snap.droughtAffectedPct, 1)}%",
                color = if (snap.vhiMeanPct >= 40.0) VC.mint else VC.amber,
                icon = "🌾",
                explanation = "Chỉ số sức khỏe thực vật VHI = 0.5×VCI + 0.5×TCI kết hợp tình trạng thực vật (VCI) và điều kiện nhiệt (TCI) chuẩn hóa trên chuỗi 26 năm (2000–2025).",
            )
            out += RegionHudBadge(
                category = "Hạn hán 26 năm (2000–2025)",
                label = "Nhiệt độ bề mặt LST & Chênh lệch nhiệt",
                value = "${fmtNum(snap.lstMeanC, 1)} °C (Δ+${fmtNum(snap.lstZones.bareVsWaterGradientC, 1)}°C)",
                sub = "Đất trống ${fmtNum(snap.lstZones.desertOrBareSoilC, 1)}°C · Tán cây ${fmtNum(snap.lstZones.vegetatedCanopyC, 1)}°C · Ven nước ${fmtNum(snap.lstZones.waterAdjacentRiparianC, 1)}°C",
                color = VC.amber,
                icon = "🔥",
                explanation = "Phân bố không gian LST (MODIS MOD11A2 & Landsat TIRS) làm rõ chênh lệch nhiệt giữa vùng đất trống/sa mạc, thảm thực vật và khu vực gần nguồn nước.",
            )
            out += RegionHudBadge(
                category = "Hạn hán 26 năm (2000–2025)",
                label = "Mưa CHIRPS 2000–2025 & Độ trễ thực vật",
                value = "Đỉnh ${dec.wettestYear} (${fmtNum(dec.wettestRainfallMm, 0)}mm) · Đáy ${dec.driestYear} (${fmtNum(dec.driestRainfallMm, 0)}mm)",
                sub = "r(Mưa,NDVI cùng năm)=${fmtNum(dec.pearsonRRainNdviSameYear, 2)} · Trễ 1 năm r=${fmtNum(dec.pearsonRRainNdviLag1Y, 2)} · r(LST,NDVI)=${fmtNum(dec.pearsonRLstNdvi, 2)}",
                color = VC.cyan,
                icon = "🌧",
                explanation = "Mưa tăng không đồng nghĩa thực vật phục hồi tức thì: phản ứng phụ thuộc nhiệt độ LST, bốc thoát hơi nước PET, tưới tiêu, độ mặn đất và độ trễ tích ẩm tầng rễ.",
            )
            out += RegionHudBadge(
                category = "Hạn hán 26 năm (2000–2025)",
                label = "Biến động NDVI 26 năm (2000 → 2025)",
                value = "ΔNDVI ${if (chg.deltaNdviMean >= 0) "+" else ""}${fmtNum(chg.deltaNdviMean, 3)}",
                sub = "Suy giảm ${fmtNum(chg.netDegradedPct, 1)}% (${fmtNum(chg.netDegradedHa, 2)} ha) · Phục hồi ${fmtNum(chg.netRecoveredPct, 1)}% · Ổn định ${fmtNum(chg.stablePct, 1)}%",
                color = VC.teal,
                icon = "🛰",
                explanation = chg.interpretationVi,
            )
        }

        // 8. Camera Đa phổ Drone & Phun biến thiên VRA (NDVI · GNDVI · NDRE · EVI · VARI · DJI Agras / XAG / ISOBUS)
        droneMsResult?.let { dm ->
            val s2 = dm.step2
            val s3 = dm.step3
            val s4 = dm.step4
            val ndreVal = s2.fiveVegetationIndices.firstOrNull { it.code == "NDRE" }?.mean ?: 0.31
            val gndviVal = s2.fiveVegetationIndices.firstOrNull { it.code == "GNDVI" }?.mean ?: 0.58
            val variVal = s2.fiveVegetationIndices.firstOrNull { it.code == "VARI" }?.mean ?: 0.22
            out += RegionHudBadge(
                category = "Camera Drone & Phun VRA",
                label = "5 Chỉ số Đa phổ Drone (M3M/P4M)",
                value = "NDRE ${fmtNum(ndreVal, 2)} · GNDVI ${fmtNum(gndviVal, 2)} · VARI ${fmtNum(variVal, 2)}",
                sub = "Orthomosaic ${fmtNum(s2.orthomosaicResolutionCm, 2)} cm/px · DSM ${fmtNum(s2.dsmMinM, 1)}–${fmtNum(s2.dsmMaxM, 1)}m · RTK FIXED",
                color = VC.mint,
                icon = "📷",
                explanation = "Thu nhận 5 dải sóng (Green 560nm, Red 650nm, RedEdge 730nm, NIR 860nm + RGB) từ DJI Mavic 3M / Phantom 4M / WingtraOne để tính NDVI, GNDVI, NDRE, EVI, VARI phát hiện sớm sâu bệnh và thiếu đạm trước khi xuất hiện triệu chứng.",
            )
            out += RegionHudBadge(
                category = "Camera Drone & Phun VRA",
                label = "Kê đơn Phun biến thiên (${s3.sprayerHardware.name})",
                value = "Tiết kiệm ${fmtNum(s3.chemicalSavingsPct, 1)}% (-${fmtNum(s3.savedSprayVolumeL, 1)} L)",
                sub = "VRA ${fmtNum(s3.vraSprayTotalL, 1)}L vs Đồng loạt ${fmtNum(s3.uniformSprayTotalL, 1)}L · Tiết kiệm ~${fmtNum(s3.estimatedCostSavedVnd / 1000.0, 0)}k VNĐ",
                color = VC.lime,
                icon = "🚁",
                explanation = "Bản đồ phân vùng kê đơn 5 mức (Prescription Map) truyền sang DJI Agras T25/T40/T50, XAG P-Series hoặc máy kéo ISOBUS: tự động ngắt vòi chỗ đất trống, phun ít chỗ cây khỏe và phun tăng cường chỗ cây suy yếu.",
            )
            out += RegionHudBadge(
                category = "Camera Drone & Phun VRA",
                label = "Đếm mật độ Stand Count & Bảo hiểm",
                value = "${s2.standCountAndYield.detectedStandCount} cây (${fmtNum(s2.standCountAndYield.emergenceSurvivalPct, 1)}%)",
                sub = "Dự báo thu hoạch ${fmtNum(s2.standCountAndYield.estimatedHarvestYieldTons, 2)} tấn · Mã BH #${s4.certificateId}",
                color = VC.cyan,
                icon = "🛡",
                explanation = s4.evidenceSummaryVi,
            )
        }

        return out
    }
}

private fun iconForIndex(index: String): String = when (index.uppercase(Locale.US)) {
    "NDVI" -> "🌿"
    "EVI" -> "🍃"
    "NDRE" -> "🧪"
    "NDMI" -> "💧"
    "SAVI" -> "🌾"
    "NDWI" -> "🌊"
    "BSI" -> "🏜"
    else -> "🛰"
}

private fun defaultIndexExplanation(index: String): String = when (index.uppercase(Locale.US)) {
    "NDVI" -> "Độ xanh và mật độ lá (-1..+1). Trên 0,65 là tán cây dày khỏe; dưới 0,35 là đất trống hoặc cây suy yếu."
    "EVI" -> "Chỉ số thực vật tăng cường, nhạy với cấu trúc tán dày của vườn cây ăn trái mà không bị bão hòa."
    "NDRE" -> "Đánh giá hàm lượng diệp lục và đạm ở tầng lá già/tán trong, phát hiện sớm suy giảm dinh dưỡng."
    "NDMI" -> "Độ ẩm của tán lá (> 0,20 là đủ nước; < 0 là dấu hiệu thiếu ẩm hoặc stress nhiệt)."
    "SAVI" -> "Chỉ số thực vật hiệu chỉnh nền đất, giúp đo chính xác hơn khi vườn mới trồng còn nhiều đất trống."
    "NDWI" -> "Chỉ số nước mặt (> 0 là có mặt nước hoặc đọng nước; âm là bề mặt tán lá/đất khô)."
    "BSI" -> "Chỉ số đất trống phơi lộ (giá trị càng dương chứng tỏ mặt đất phơi nắng càng nhiều, thiếu cỏ/tán che phủ)."
    else -> "Chỉ số quang phổ vệ tinh Sentinel-2 đo trong ranh giới vùng."
}

private fun soilMeta(key: String, defaultLabel: String): Triple<String, String, String> = when (key) {
    "phh2o" -> Triple("⚗", "Độ pH đất (H₂O)", "Độ chua/kiềm của đất ở 3 tầng độ sâu. Ngưỡng 5,5–6,5 là tối ưu cho sầu riêng, cà phê, hồ tiêu hấp thu phân bón.")
    "soc" -> Triple("🌱", "Cacbon hữu cơ (SOC)", "Hàm lượng chất hữu cơ/mùn trong đất (g/kg). Trên 20 g/kg giúp đất tơi xốp, giữ nước và nuôi hệ vi sinh vật rễ.")
    "clay" -> Triple("🧱", "Tỷ lệ hạt sét (Clay)", "Phần trăm hạt sét trong đất (%). Sét giúp giữ ẩm và dinh dưỡng nhưng nếu quá cao (> 55%) cần chú ý thoát nước.")
    "sand" -> Triple("🏖", "Tỷ lệ hạt cát (Sand)", "Phần trăm hạt cát trong đất (%). Cát giúp đất thoáng khí và thoát nước nhanh sau mưa lớn.")
    "silt" -> Triple("🌾", "Tỷ lệ hạt thịt (Silt)", "Phần trăm hạt limon/thịt (%). Cân bằng giữa khả năng giữ ẩm của sét và độ thoáng khí của cát.")
    "nitrogen" -> Triple("🧬", "Đạm tổng số (Total N)", "Trữ lượng nitơ tự nhiên trong đất (g/kg), phản ánh độ màu mỡ nền tảng của tầng đất canh tác.")
    "cec" -> Triple("🔋", "Trao đổi cation (CEC)", "Khả năng giữ và trao đổi các chất dinh dưỡng (Ca²⁺, Mg²⁺, K⁺, NH₄⁺) của keo đất (cmol(+)/kg).")
    "bdod" -> Triple("⚖", "Dung trọng đất (Bulk Density)", "Độ nén chặt của đất (g/cm³). Dưới 1,3 g/cm³ là đất tơi xốp, rễ tơ phát triển thuận lợi.")
    else -> Triple("🌱", defaultLabel, "Thông số lý hóa đất trích xuất từ ISRIC SoilGrids 250m.")
}

private class CachedRegionData(
    val gardenAnalysis: SatGardenAnalysis?,
    val gardenBitmap: ImageBitmap?,
    val envResult: GeodataEnvResult?,
    val gadmResult: GadmBoundaryResult?,
    val osmResult: OsmExtractResult?,
    val demHubResult: DemHubReport?,
    val curriculumResult: GisCurriculumReport?,
    val droughtResult: DroughtMonitoringReport? = null,
    val droughtBitmap: ImageBitmap? = null,
    val droneMsResult: DroneMultispectralReport? = null,
    val droneMsBitmap: ImageBitmap? = null,
    val deepForestResult: DeepForestPredictReport? = null,
    val deepForestBitmap: ImageBitmap? = null,
    val agResearchSuite: SatAgResearchSuite? = null,
    val geeClarityTrueColorBitmap: ImageBitmap? = null,
)

private val regionHudCache = ConcurrentHashMap<String, CachedRegionData>()

fun computeEdgeLengthsM(pts: List<GeoPoint>): List<Double> {
    val open = Geo.open(pts)
    if (open.size < 2) return emptyList()
    return open.indices.map { i ->
        val a = open[i]
        val b = open[(i + 1) % open.size]
        Geo.distanceM(a, b)
    }
}

@Composable
fun rememberRegionHudState(
    d: GardenData,
    region: GeoFeature?,
    cfg: SatelliteConfig,
    start: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Dates.today() - 30L * 86_400_000),
    end: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Dates.today()),
    onAutoOverlay: ((MapOverlay?) -> Unit)? = null,
): RegionHudState? {
    val reg = region ?: return null
    val openPts = remember(reg.id, reg.points) { Geo.open(reg.points).takeIf { it.size >= 3 } } ?: return null
    val sig = remember(reg.id, openPts, cfg.url, start, end) {
        "${reg.id}|${openPts.joinToString(";") { "${"%.5f".format(Locale.US, it.lat)},${"%.5f".format(Locale.US, it.lng)}" }}|${cfg.url}|$start|$end"
    }

    val state = remember(sig, d.trees) {
        val areaHa = Geo.polygonAreaHa(openPts)
        val edges = computeEdgeLengthsM(openPts)
        val perim = edges.sum()
        val placed = d.activeTrees.filter { it.pos != null }
        val inside = placed.filter { Geo.pointInPolygon(it.pos!!, openPts) }
        val fruits = inside.sumOf { it.fruits ?: 0 }
        val stable = inside.count { d.effectiveCondition(it) == Condition.STABLE }
        val watch = inside.count { d.effectiveCondition(it) == Condition.WATCH }
        val needs = inside.count {
            val c = d.effectiveCondition(it)
            c == Condition.NEEDS_CHECK || c == Condition.UNASSESSED
        }
        RegionHudState(
            regionId = reg.id,
            regionName = reg.name.ifBlank { "Vùng đã khoanh" },
            points = openPts,
            areaHa = areaHa,
            perimeterM = perim,
            edgeLengthsM = edges,
            treesInside = inside.size,
            totalPlacedTrees = placed.size,
            fruitsInside = fruits,
            stableTreesInside = stable,
            watchTreesInside = watch,
            needsCheckTreesInside = needs,
        ).also { st ->
            regionHudCache[sig]?.let { cached ->
                st.gardenAnalysis = cached.gardenAnalysis
                st.gardenBitmap = cached.gardenBitmap
                st.envResult = cached.envResult
                st.gadmResult = cached.gadmResult
                st.osmResult = cached.osmResult
                st.demHubResult = cached.demHubResult
                st.curriculumResult = cached.curriculumResult
                st.droughtResult = cached.droughtResult
                st.droughtBitmap = cached.droughtBitmap
                st.droneMsResult = cached.droneMsResult
                st.droneMsBitmap = cached.droneMsBitmap
                st.deepForestResult = cached.deepForestResult
                st.deepForestBitmap = cached.deepForestBitmap
                st.agResearchSuite = cached.agResearchSuite
                st.geeClarityTrueColorBitmap = cached.geeClarityTrueColorBitmap ?: cached.gardenBitmap
                if (!VUi.simpleMode && st.selectedGeeTreeCode == null) {
                    st.selectedGeeTreeCode = st.allGeeTreesInPolygon().firstOrNull()?.code
                }
            }
        }
    }

    val scope = rememberCoroutineScope()
    val currentOnAutoOverlay by rememberUpdatedState(onAutoOverlay)

    LaunchedEffect(sig, cfg.url, cfg.token) {
        if (cfg.configured && !regionHudCache.containsKey(sig)) {
            state.refreshAll(cfg, start, end, scope, sig, currentOnAutoOverlay)
        } else if (state.gardenAnalysis != null && state.gardenBitmap != null && state.selectedQuickLayer != "Tắt phủ") {
            val g = state.gardenAnalysis!!
            currentOnAutoOverlay?.invoke(
                MapOverlay(
                    g.bounds,
                    state.gardenBitmap!!,
                    0.72f,
                    "Tán ${g.treeEstimation.cropLabelVi} · ~${g.treeEstimation.estimatedTrees} cây · NDVI ${g.indices.firstOrNull { it.index == "NDVI" }?.mean?.let { fmtNum(it, 2) } ?: "—"}",
                ),
            )
        }
    }

    return state
}

/**
 * Vẽ từng gốc cây đếm được từ Google Earth Engine (GEE) trong vùng khoanh kèm Label mã cây & tình trạng sức khỏe:
 * - Cây khỏe mạnh (healthy): chấm xanh ngọc (#00E676) + nhãn [SR-001]
 * - Cây cần theo dõi (moderate): chấm vàng (#FFD740) + nhãn [SR-008 · TB]
 * - Cây suy yếu (stressed): chấm đỏ cam (#FF5252) + nhãn [⚠ SR-015 · Yếu]
 * - Cây đang được chạm chọn (selectedGeeTreeCode): vòng tròn phát sáng kép + nhãn nổi bật
 */
fun DrawScope.drawGeeTreesAndLabelsOnCanvas(
    analysisPoints: List<GeoPoint>,
    hud: RegionHudState?,
    ptTransform: (GeoPoint) -> Offset,
    measurer: TextMeasurer,
    dens: Float,
) {
    if (hud == null || analysisPoints.size < 3) return
    val trees = hud.visibleGeeTrees()
    if (trees.isEmpty()) return

    val selectedCode = hud.selectedGeeTreeCode
    val placedLabelRects = mutableListOf<Rect>()

    // 0. Vẽ lưới cửa sổ trượt Sliding Window Tiles của DeepForest (khi bật showDeepForestTileGrid)
    if (hud.showDeepForestTileGrid) {
        hud.deepForestResult?.tiles?.forEach { t ->
            val tl = ptTransform(GeoPoint(t.bounds.north, t.bounds.west))
            val br = ptTransform(GeoPoint(t.bounds.south, t.bounds.east))
            val rx = minOf(tl.x, br.x)
            val ry = minOf(tl.y, br.y)
            val rw = kotlin.math.abs(br.x - tl.x).coerceAtLeast(4f)
            val rh = kotlin.math.abs(br.y - tl.y).coerceAtLeast(4f)
            drawRect(
                color = Color(0x8800E5FF),
                topLeft = Offset(rx, ry),
                size = Size(rw, rh),
                style = Stroke(1.3f * dens),
            )
        }
    }

    // 1. Vẽ toàn bộ vị trí các gốc cây + Khung bao tán cây (Bounding Box) weecology/DeepForest
    trees.forEach { sp ->
        val o = ptTransform(sp.pos)
        if (o.x < -20f || o.x > size.width + 20f || o.y < -20f || o.y > size.height + 20f) return@forEach
        val isSel = sp.code == selectedCode
        val col = when (sp.status) {
            "unknown" -> VC.teal
            "healthy" -> Color(0xFF00E676)
            "moderate" -> Color(0xFFFFD740)
            else -> Color(0xFFFF5252)
        }
        if (hud.showDeepForestBboxes) {
            val bb = sp.resolvedBbox
            val tl = ptTransform(GeoPoint(bb.north, bb.west))
            val br = ptTransform(GeoPoint(bb.south, bb.east))
            val minSide = 9f * dens
            val rawW = kotlin.math.abs(br.x - tl.x)
            val rawH = kotlin.math.abs(br.y - tl.y)
            val bw = maxOf(minSide, rawW)
            val bh = maxOf(minSide, rawH)
            val topLeft = Offset(o.x - bw * 0.5f, o.y - bh * 0.5f)
            if (isSel) {
                drawRoundRect(
                    color = col.copy(alpha = 0.18f),
                    topLeft = topLeft,
                    size = Size(bw, bh),
                    cornerRadius = CornerRadius(2.5f * dens, 2.5f * dens),
                )
            }
            drawRoundRect(
                color = if (isSel) Color(0xFFFFEA00) else col.copy(alpha = 0.88f),
                topLeft = topLeft,
                size = Size(bw, bh),
                cornerRadius = CornerRadius(2.5f * dens, 2.5f * dens),
                style = Stroke(if (isSel) 2.2f * dens else 1.3f * dens),
            )
        }
        if (isSel) {
            drawCircle(Color(0x5500E5FF), radius = 14f * dens, center = o)
            drawCircle(Color(0xFFFFEA00), radius = 10.5f * dens, center = o, style = Stroke(2.4f * dens))
        }
        val baseR = if (isSel) 5.2f * dens else if (sp.status == "stressed") 4.2f * dens else 3.0f * dens
        drawCircle(Color(0xD906120B), radius = baseR + 1.2f * dens, center = o)
        drawCircle(col, radius = baseR, center = o)
        drawCircle(Color.White.copy(alpha = 0.90f), radius = 1.2f * dens, center = o)
    }

    // 2. Vẽ Label cho từng cây: mặc định chỉ hiện nhãn của cây đang chạm chọn (hoặc cây suy yếu) để không che mờ vùng khoanh;
    // khi bật hud.showGeeTreeLabels thì hiện toàn bộ mã cây.
    val prioritized = trees.sortedWith(
        compareByDescending<SatTreePoint> { it.code == selectedCode }
            .thenBy {
                when (it.status) {
                    "stressed" -> 0
                    "moderate" -> 1
                    else -> 2
                }
            }
            .thenBy { it.treeNumber },
    )

    for (sp in prioritized) {
        val isSel = sp.code == selectedCode
        if (!hud.showGeeTreeLabels && !isSel && sp.status != "stressed") continue
        if (VUi.simpleMode && !isSel && sp.status != "stressed") continue
        val o = ptTransform(sp.pos)
        if (o.x < 10f * dens || o.x > size.width - 10f * dens || o.y < 36f * dens || o.y > size.height - 42f * dens) continue
        val borderCol = when (sp.status) {
            "unknown" -> VC.teal
            "healthy" -> Color(0xFF00E676)
            "moderate" -> Color(0xFFFFD740)
            else -> Color(0xFFFF5252)
        }
        val labelTxt = when {
            isSel -> "🌳 ${sp.code} · ${sp.resolvedStatusVi} (${if (sp.healthScore < 0) "?" else "${sp.healthScore}%"})"
            sp.status == "stressed" -> "⚠ ${sp.code} · Yếu"
            sp.status == "moderate" -> "${sp.code} · TB"
            else -> sp.code
        }
        val fontSp = if (isSel) 10.5f else 8.5f
        val m = measurer.measure(
            labelTxt,
            TextStyle(
                color = if (isSel) Color(0xFFFFF59D) else Color.White,
                fontSize = fontSp.sp,
                fontWeight = FontWeight.ExtraBold,
            ),
        )
        val padX = if (isSel) 5f * dens else 3.2f * dens
        val padY = if (isSel) 2.2f * dens else 1.2f * dens
        val boxW = m.size.width + padX * 2
        val boxH = m.size.height + padY * 2
        val topLeft = Offset(o.x - boxW / 2f, o.y - 6.5f * dens - boxH)
        val candidateRect = Rect(
            topLeft.x - 1.5f * dens,
            topLeft.y - 1.5f * dens,
            topLeft.x + boxW + 1.5f * dens,
            topLeft.y + boxH + 1.5f * dens,
        )
        if (!isSel && placedLabelRects.any { it.overlaps(candidateRect) }) continue
        placedLabelRects.add(candidateRect)

        drawRoundRect(
            color = if (isSel) Color(0xF2081810) else Color(0xCC08150E),
            topLeft = topLeft,
            size = Size(boxW, boxH),
            cornerRadius = CornerRadius(4.5f * dens, 4.5f * dens),
        )
        drawRoundRect(
            color = if (isSel) Color(0xFFFFEA00) else borderCol.copy(alpha = 0.88f),
            topLeft = topLeft,
            size = Size(boxW, boxH),
            cornerRadius = CornerRadius(4.5f * dens, 4.5f * dens),
            style = Stroke(if (isSel) 1.8f * dens else 1.1f * dens),
        )
        drawText(m, topLeft = Offset(topLeft.x + padX, topLeft.y + padY))
    }
}

/**
 * Vẽ nhẹ nhàng trên đa giác vùng đã khoanh:
 * - Chiều dài từng cạnh (m) và số thứ tự đỉnh (1, 2, 3...)
 * - Các gốc cây GEE đếm được trong vùng khoanh kèm Label từng cây (`SR-001`, `SR-002`...)
 * - Các viên thuốc nhỏ (Icon + Số ngắn gọn) nếu bật `showIconsOnPolygon`.
 */
fun DrawScope.drawRegionMetricsOnPolygon(
    analysisPoints: List<GeoPoint>,
    hud: RegionHudState?,
    ptTransform: (GeoPoint) -> Offset,
    measurer: TextMeasurer,
    dens: Float,
) {
    if (analysisPoints.size < 2) return
    val screenPts = analysisPoints.map(ptTransform)

    // 0. Vẽ từng gốc cây GEE đếm được và Label mã cây ngay bên trong vùng khoanh
    if (analysisPoints.size >= 3) {
        drawGeeTreesAndLabelsOnCanvas(
            analysisPoints = analysisPoints,
            hud = hud,
            ptTransform = ptTransform,
            measurer = measurer,
            dens = dens,
        )
    }

    // 1. Nhãn độ dài từng cạnh (m) và số thứ tự đỉnh (1, 2, 3...) luôn hiển thị ở cả Nông dân & Kỹ sư để minh bạch diện tích
    val edgeCount = if (screenPts.size == 2) 1 else screenPts.size
    for (i in 0 until edgeCount) {
        val nextIdx = (i + 1) % screenPts.size
        val a = screenPts[i]
        val b = screenPts[nextIdx]
        val distM = Geo.distanceM(analysisPoints[i], analysisPoints[nextIdx])
        val mid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
        val pxDist = (b - a).getDistance()
        if (pxDist > 22f * dens) {
            val edgeText = if (pxDist > 78f * dens) {
                "${i + 1}→${nextIdx + 1}: ${fmtNum(distM, 1)}m"
            } else {
                "${fmtNum(distM, 1)}m"
            }
            val m = measurer.measure(edgeText, TextStyle(color = Color(0xFFFFF59D), fontSize = 10.5f.sp, fontWeight = FontWeight.ExtraBold))
            val padX = 4.5f * dens
            val padY = 2f * dens
            val bgRect = Rect(
                mid.x - m.size.width / 2f - padX,
                mid.y - m.size.height / 2f - padY,
                mid.x + m.size.width / 2f + padX,
                mid.y + m.size.height / 2f + padY,
            )
            drawRoundRect(Color(0xE6081810), bgRect.topLeft, bgRect.size, CornerRadius(5f * dens, 5f * dens))
            drawRoundRect(Color(0xAAFFD54F), bgRect.topLeft, bgRect.size, CornerRadius(5f * dens, 5f * dens), style = Stroke(1.1f * dens))
            drawText(m, topLeft = Offset(mid.x - m.size.width / 2f, mid.y - m.size.height / 2f))
        }
    }

    for (i in screenPts.indices) {
        val a = screenPts[i]
        drawCircle(Color(0xFF102218), radius = 8.5f * dens, center = a)
        drawCircle(Color(0xFFFFEA00), radius = 8.5f * dens, center = a, style = Stroke(2f * dens))
        val vLabel = measurer.measure("${i + 1}", TextStyle(color = Color.White, fontSize = 9.5f.sp, fontWeight = FontWeight.ExtraBold))
        drawText(vLabel, topLeft = Offset(a.x - vLabel.size.width / 2f, a.y - vLabel.size.height / 2f))
    }

    if (analysisPoints.size < 3) return

    // 2. Các viên thuốc nhỏ (Icon + Số) ngay tại tâm vùng đã khoanh (khi người dùng bật)
    if (hud == null || !hud.showIconsOnPolygon) return
    val centroidGeo = Geo.centroid(analysisPoints) ?: return
    val centerPx = ptTransform(centroidGeo)
    val pills = hud.compactPolygonPills().take(8)
    if (pills.isEmpty()) return

    val perRow = 4
    val rows = pills.chunked(perRow)
    val pillPadX = 5f * dens
    val pillPadY = 2.5f * dens
    val gapX = 4f * dens
    val gapY = 4f * dens

    val measuredRows = rows.map { row ->
        row.map { (ic, txt, col) ->
            val m = measurer.measure("$ic $txt", TextStyle(color = Color.White, fontSize = 9.5f.sp, fontWeight = FontWeight.Bold))
            Triple(m, Size(m.size.width + pillPadX * 2, m.size.height + pillPadY * 2), col)
        }
    }

    val totalH = measuredRows.sumOf { r -> r.maxOf { it.second.height }.toDouble() }.toFloat() + gapY * (measuredRows.size - 1)
    var curY = (centerPx.y - totalH / 2f).coerceIn(48f * dens, (size.height - totalH - 48f * dens).coerceAtLeast(48f * dens))

    for (row in measuredRows) {
        val rowW = row.sumOf { it.second.width.toDouble() }.toFloat() + gapX * (row.size - 1)
        var curX = (centerPx.x - rowW / 2f).coerceIn(8f * dens, (size.width - rowW - 8f * dens).coerceAtLeast(8f * dens))
        val rowH = row.maxOf { it.second.height }
        for ((m, sz, col) in row) {
            drawRoundRect(
                color = Color(0xD90B1911),
                topLeft = Offset(curX, curY),
                size = sz,
                cornerRadius = CornerRadius(8f * dens, 8f * dens),
            )
            drawRoundRect(
                color = col.copy(alpha = 0.85f),
                topLeft = Offset(curX, curY),
                size = sz,
                cornerRadius = CornerRadius(8f * dens, 8f * dens),
                style = Stroke(1.2f * dens),
            )
            drawText(m, topLeft = Offset(curX + pillPadX, curY + pillPadY))
            curX += sz.width + gapX
        }
        curY += rowH + gapY
    }
}

/**
 * Thanh Icon nhỏ gọn gắn trên khung bản đồ:
 * - Góc trên: bật/tắt Label từng cây GEE (`🏷 Nhãn cây GEE`), lọc nhanh theo sức khỏe cây (`🟢/🟡/🔴`), chọn lớp ảnh phủ.
 * - Cạnh dưới bản đồ: Thẻ chi tiết tình hình cây GEE đang chọn + 1 hàng ngang các nút Icon nhỏ.
 */
@Composable
fun BoxScope.RegionMapHudOverlay(
    hud: RegionHudState,
    cfg: SatelliteConfig,
    start: String,
    end: String,
    onSelectOverlay: (MapOverlay?) -> Unit,
    onRefreshAll: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val allBadges = hud.allBadges()
    val selectedBadge = allBadges.firstOrNull { "${it.category}:${it.label}" == hud.selectedBadgeKey }
    val geeTrees = hud.allGeeTreesInPolygon()
    val selectedGeeTree = geeTrees.firstOrNull { it.code == hud.selectedGeeTreeCode }

    // Góc trên trái: Thanh chọn lớp ảnh phủ & bật/tắt Label từng cây GEE
    Row(
        Modifier
            .align(Alignment.TopStart)
            .padding(start = 8.dp, top = 8.dp, end = 58.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xD90D1F14))
            .border(1.dp, Color(0x663DDC84), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hud.loading) {
            CircularProgressIndicator(Modifier.size(13.dp), color = VC.mint, strokeWidth = 2.dp)
        } else {
            Text(
                "↻",
                color = Color(0xFF0B1911),
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(VC.mint)
                    .clickable { onRefreshAll() }
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
        if (geeTrees.isNotEmpty()) {
            val teAcc = hud.gardenAnalysis?.treeEstimation
            Text(
                if (hud.showDeepForestBboxes) "🌲 Khung tán (${geeTrees.size})" else "🌲 Hiện khung tán",
                color = if (hud.showDeepForestBboxes) VC.onLime else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (hud.showDeepForestBboxes) Color(0xFF4CAF50) else Color(0x3DFFFFFF))
                    .clickable { hud.showDeepForestBboxes = !hud.showDeepForestBboxes }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
            if (!VUi.simpleMode) {
                Text(
                    if (hud.showGeeTreeLabels) "🏷 Nhãn cây GEE (${geeTrees.size})" else "🏷 Hiện nhãn ${geeTrees.size} cây",
                    color = if (hud.showGeeTreeLabels) Color(0xFF0B1911) else Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (hud.showGeeTreeLabels) VC.lime else Color(0x3DFFFFFF))
                        .clickable { hud.showGeeTreeLabels = !hud.showGeeTreeLabels }
                        .padding(horizontal = 8.dp, vertical = 2.5.dp),
                )
                if (teAcc != null) {
                    Text(
                        "🎯 Điểm tin cậy ước tính ${fmtNum(teAcc.overallAccuracyPct, 1)}% (±${fmtNum(teAcc.spatialPrecisionM, 2)}m)",
                        color = Color(0xFF0B1911),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(VC.mint)
                            .padding(horizontal = 7.dp, vertical = 2.5.dp),
                    )
                }
            }
            val hCount = geeTrees.count { it.status == "healthy" }
            val mCount = geeTrees.count { it.status == "moderate" }
            val sCount = geeTrees.count { it.status == "stressed" }
            listOf(
                Triple("ALL", "🌳 Tất cả (${geeTrees.size})", Color(0xFF4CAF50)),
                Triple("stressed", "🔴 Cây yếu ($sCount)", Color(0xFFE53935)),
                Triple("moderate", "🟡 Theo dõi ($mCount)", Color(0xFFFFB300)),
                Triple("healthy", "🟢 Cây khỏe ($hCount)", Color(0xFF43A047)),
            ).forEach { (key, lbl, col) ->
                val active = hud.geeTreeStatusFilter == key
                Text(
                    lbl,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (active) col else Color(0x26FFFFFF))
                        .clickable { hud.geeTreeStatusFilter = key }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        if (!VUi.simpleMode) {
        // Chuyển nhanh chế độ Bản đồ GIS 2D (Nhiệt phổ & Dòng chảy) <-> Xếp lớp 3D (5 tầng GIS)
        Text(
            if (hud.stacked3dMode) "🥞 Xếp lớp 3D (5 tầng)" else "🗺 GIS Pro 2D",
            color = Color(0xFF0B1911),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (hud.stacked3dMode) VC.amber else VC.cyan)
                .clickable { hud.stacked3dMode = !hud.stacked3dMode }
                .padding(horizontal = 8.dp, vertical = 2.5.dp),
        )
        Text(
            if (hud.stacked3dMode) "Xem 2D" else "Xem 3D 5 tầng",
            color = Color.White,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x3DFFFFFF))
                .clickable { hud.stacked3dMode = !hud.stacked3dMode }
                .padding(horizontal = 7.dp, vertical = 2.5.dp),
        )
        GisVisualTheme.values().forEach { th ->
            val active = !hud.stacked3dMode && hud.gisTheme == th
            Text(
                th.label,
                color = if (active) Color(0xFF0B1911) else Color(0xFFD5E6DA),
                fontSize = 11.5.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) VC.lime else Color(0x26FFFFFF))
                    .clickable {
                        hud.stacked3dMode = false
                        hud.gisTheme = th
                    }
                    .padding(horizontal = 7.dp, vertical = 2.5.dp),
            )
        }
        val quickLayers = listOf("Tán cây AI", "NDVI", "EVI", "NDMI", "Tắt phủ")
        quickLayers.forEach { layer ->
            val active = hud.selectedQuickLayer == layer
            Text(
                layer,
                color = if (active) Color(0xFF0B1911) else Color.White,
                fontSize = 11.5.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) VC.mint else Color(0x2EFFFFFF))
                    .clickable {
                        hud.selectedQuickLayer = layer
                        when (layer) {
                            "Tắt phủ" -> onSelectOverlay(null)
                            "Tán cây AI" -> {
                                val g = hud.gardenAnalysis
                                val bmp = hud.gardenBitmap
                                if (g != null && bmp != null) {
                                    onSelectOverlay(MapOverlay(g.bounds, bmp, 0.75f, "Tán ${g.treeEstimation.cropLabelVi} · ~${g.treeEstimation.estimatedTrees} cây"))
                                }
                            }
                            "NDVI", "EVI", "NDMI" -> {
                                val key = SatIndexKey.valueOf(layer)
                                scope.launch {
                                    val cached = when (key) {
                                        SatIndexKey.NDVI -> hud.ndviResult to hud.ndviBitmap
                                        SatIndexKey.EVI -> hud.eviResult to hud.eviBitmap
                                        SatIndexKey.NDMI -> hud.ndmiResult to hud.ndmiBitmap
                                    }
                                    if (cached.first != null && cached.second != null) {
                                        val r = cached.first!!
                                        onSelectOverlay(MapOverlay(r.bounds, cached.second!!, 0.78f, "${r.index} TB ${r.mean?.let { fmtNum(it, 2) } ?: "—"}"))
                                    } else if (cfg.configured) {
                                        hud.loading = true
                                        val fetched = withContext(Dispatchers.IO) {
                                            val r = SatelliteClient.index(cfg, hud.points, key, start, end).getOrNull()
                                            val b = r?.imageUrl?.let { u ->
                                                SatelliteClient.image(cfg, u).getOrNull()?.let { bytes ->
                                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()?.let { raw ->
                                                        clipBitmapToPolygon(raw, r.bounds, hud.points)
                                                    }
                                                }
                                            }
                                            r to b
                                        }
                                        hud.loading = false
                                        val r = fetched.first
                                        val b = fetched.second
                                        if (r != null) {
                                            when (key) {
                                                SatIndexKey.NDVI -> { hud.ndviResult = r; hud.ndviBitmap = b }
                                                SatIndexKey.EVI -> { hud.eviResult = r; hud.eviBitmap = b }
                                                SatIndexKey.NDMI -> { hud.ndmiResult = r; hud.ndmiBitmap = b }
                                            }
                                            if (b != null) {
                                                onSelectOverlay(MapOverlay(r.bounds, b, 0.78f, "${r.index} TB ${r.mean?.let { fmtNum(it, 2) } ?: "—"}"))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .padding(horizontal = 7.dp, vertical = 2.5.dp),
            )
        }
        Text(
            if (hud.showIconsOnPolygon) "Ẩn icon tâm vùng" else "Hiện icon tâm vùng",
            color = Color(0xFFD5E6DA),
            fontSize = 11.5.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x2EFFFFFF))
                .clickable { hud.showIconsOnPolygon = !hud.showIconsOnPolygon }
                .padding(horizontal = 7.dp, vertical = 2.5.dp),
        )
        }
    }

    // Cạnh dưới bản đồ: Thẻ Tình Hình Cây GEE đang chọn + Dải 1 hàng các Icon nhỏ + Số đo
    // Chừa lề phải 54.dp để không che nút Zoom +/- mặc định của Google Maps
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(start = 8.dp, end = 54.dp, top = 6.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        selectedGeeTree?.let { sp ->
            val stCol = when (sp.status) {
            "unknown" -> VC.teal
                "healthy" -> VC.mint
                "moderate" -> VC.amber
                else -> VC.red
            }
            val visibleList = hud.visibleGeeTrees().ifEmpty { geeTrees }
            val curIdx = visibleList.indexOfFirst { it.code == sp.code }.coerceAtLeast(0)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xF208160F))
                    .border(1.5.dp, stCol, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "🌳 Cây ${sp.code}",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            "${sp.resolvedStatusVi} · ${if (sp.healthScore < 0) "?" else "${sp.healthScore}/100"}",
                            color = Color(0xFF08160F),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(stCol)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                        if (!VUi.simpleMode) {
                            Text(
                                "Hàng ${sp.rowNumber} · #${sp.treeNumber}/${geeTrees.size}",
                                color = VC.cyan,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "🎯 Tin cậy ${sp.confidencePct}% · Tán Ø ${sp.crownDiameterM?.let { fmtNum(it, 1) } ?: "5,5"}m",
                                color = VC.lime,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x26AEEA00))
                                    .border(0.8.dp, VC.lime.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (visibleList.size > 1) {
                            Text(
                                "◀ Trước",
                                color = VC.mint,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0x26FFFFFF))
                                    .clickable {
                                        val prev = visibleList[(curIdx - 1 + visibleList.size) % visibleList.size]
                                        hud.selectedGeeTreeCode = prev.code
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                            Text(
                                "Tiếp ▶",
                                color = VC.mint,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0x26FFFFFF))
                                    .clickable {
                                        val next = visibleList[(curIdx + 1) % visibleList.size]
                                        hud.selectedGeeTreeCode = next.code
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Text(
                            "✕",
                            color = Color(0xFFB0C8B8),
                            fontSize = 13.5.sp,
                            modifier = Modifier.clickable { hud.selectedGeeTreeCode = null }.padding(start = 4.dp),
                        )
                    }
                }
                if (!VUi.simpleMode) {
                    Text(
                        "🌿 NDVI: ${sp.ndvi?.let { fmtNum(it, 2) } ?: "—"}   ·   🧪 NDRE: ${sp.ndre?.let { fmtNum(it, 2) } ?: "—"}   ·   🍃 EVI: ${sp.evi?.let { fmtNum(it, 2) } ?: "—"}   ·   💧 NDMI: ${sp.ndmi?.let { fmtNum(it, 2) } ?: "—"}   ·   📍 ${"%.5f".format(Locale.US, sp.pos.lat)}, ${"%.5f".format(Locale.US, sp.pos.lng)}",
                        color = Color(0xFFD5E6DA),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    sp.resolvedDiagnosisVi,
                    color = stCol,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (!VUi.simpleMode) {
            selectedBadge?.let { b ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xEB0B1911))
                        .border(1.dp, b.color, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${b.icon} ${b.label}: ${b.value} (${b.sub})", color = b.color, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        if (b.explanation.isNotBlank()) {
                            Text(b.explanation, color = Color.White, fontSize = 11.5.sp)
                        }
                    }
                    Text(
                        "✕",
                        color = Color(0xFFB0C8B8),
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { hud.selectedBadgeKey = null }.padding(start = 8.dp),
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xD90B1911))
                    .border(1.dp, Color(0x663DDC84), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                allBadges.forEach { b ->
                    val key = "${b.category}:${b.label}"
                    val isSel = hud.selectedBadgeKey == key
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSel) b.color.copy(alpha = 0.28f) else Color(0xFF152C20))
                            .border(1.dp, if (isSel) b.color else b.color.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                            .clickable { hud.selectedBadgeKey = if (isSel) null else key }
                            .padding(horizontal = 8.dp, vertical = 3.5.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(b.icon, fontSize = 12.sp)
                        Text(b.value, color = b.color, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

/**
 * BẢNG ĐẾM SỐ CÂY & GẮN LABEL TRẠNG THÁI TỪNG CÂY TRONG VÙNG KHOANH (GOOGLE EARTH ENGINE):
 * Hiển thị danh sách từng cây đếm được từ GEE trong đa giác vùng khoanh (SR-001..SR-N),
 * kèm điểm sức khỏe (/100), chỉ số NDVI/NDRE/NDMI và chẩn đoán tình hình từng cây.
 * Chạm vào bất kỳ cây nào sẽ tự động highlight nhãn cây đó trên Bản đồ VWing và Google Map vệ tinh.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GeeTreeStatusTableCard(
    hud: RegionHudState,
    onSyncTreesToStore: (List<SatTreePoint>, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val df = hud.deepForestResult
    val te = hud.gardenAnalysis?.treeEstimation
    if (df == null && te == null) return
    val allTrees = hud.allGeeTreesInPolygon()
    if (allTrees.isEmpty()) return

    val healthyCount = allTrees.count { it.status == "healthy" }
    val moderateCount = allTrees.count { it.status == "moderate" }
    val stressedCount = allTrees.count { it.status == "stressed" }
    var showAllRows by remember { mutableStateOf(false) }
    val rowsAvailable = remember(allTrees) { allTrees.map { it.rowNumber }.distinct().sorted() }
    var selectedRowFilter by remember { mutableStateOf(0) } // 0 = Tất cả hàng

    val filteredTrees = remember(allTrees, hud.geeTreeStatusFilter, selectedRowFilter) {
        allTrees.filter { sp ->
            (hud.geeTreeStatusFilter == "ALL" || sp.status == hud.geeTreeStatusFilter) &&
                (selectedRowFilter == 0 || sp.rowNumber == selectedRowFilter)
        }
    }
    val displayedTrees = if (showAllRows) filteredTrees else filteredTrees.take(24)
    var qrPreviewPoint by remember { mutableStateOf<SatTreePoint?>(null) }

    VCard(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Eyebrow(if (df != null) "DEEPFOREST · PHÁT HIỆN TÁN TỪ ẢNH RGB" else "ƯỚC TÍNH TỪ PHÂN TÍCH VƯỜN")
                Text(
                    if (df != null) "${allTrees.size} tán được phát hiện (${allTrees.first().code} → ${allTrees.last().code}); chưa kiểm chứng độ chính xác tại vườn."
                    else "${allTrees.size} vị trí ước tính · ${te?.cropLabelVi.orEmpty()}",
                    color = VC.text,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    if (df != null) "Phương pháp: DeepForest predict_tile; sức khỏe cây chưa được đo." else "Phương pháp: ${te?.detectionMethodVi.orEmpty()}",
                    color = VC.teal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                "📥 Lưu ${allTrees.size} nhãn cây vào Hồ sơ vườn",
                color = VC.onLime,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VC.lime)
                    .clickable { onSyncTreesToStore(allTrees, te?.cropLabelVi ?: "Tán cây") }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        Spacer(Modifier.height(10.dp))

        // Khối Đánh Giá Điểm Tin Cậy Ước Tính & Kiểm Chứng Chéo (Google Satellite 0,3 m/px × GEE Sentinel-2 10m)
        if (df == null && te != null) Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                Quadruple(
                    "🎯 Điểm tin cậy ước tính",
                    "${fmtNum(te.overallAccuracyPct, 1)}%",
                    "Độ lệch tâm tán ước tính ±${fmtNum(te.spatialPrecisionM, 2)} m",
                    VC.lime,
                ),
                Quadruple(
                    "🛰 Tách vòm tán Google Sat (${fmtNum(te.highresResolutionM, 2)}m/px)",
                    "${te.highresDetectedCrowns.takeIf { it > 0 } ?: allTrees.size} vòm tán",
                    "Độ tin cậy thị giác ${fmtNum(te.crownDetectionConfidencePct, 1)}%",
                    VC.cyan,
                ),
                Quadruple(
                    "🌍 Đa phổ GEE Sentinel-2 (10m)",
                    "~${te.spectralEstimatedTrees.takeIf { it > 0 } ?: te.estimatedTrees} cây",
                    "Độ tin cậy quang phổ ${fmtNum(te.spectralConfidencePct, 1)}%",
                    VC.mint,
                ),
                Quadruple(
                    "⚖ Kiểm chứng chéo 2 nguồn API",
                    "${fmtNum(te.crossValidationAgreementPct, 1)}% khớp",
                    "Khoảng ước tính: ${te.estimatedTreesMin}–${te.estimatedTreesMax} cây",
                    VC.teal,
                ),
            ).forEach { (title, mainVal, subTxt, col) ->
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0E2116))
                        .border(1.2.dp, col.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(title, color = VC.text2, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(mainVal, color = col, fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 2.dp))
                    Text(subTxt, color = Color(0xFFD5E6DA), fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Thanh tổng hợp nhanh số lượng cây theo 3 mức tình trạng (chạm để lọc ngay trên bản đồ & bảng)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (if (df != null) listOf(
                Quadruple("ALL", "🌳 Tổng số tán phát hiện", "${allTrees.size} tán", VC.cyan),
                Quadruple("unknown", "Chưa đo sức khỏe", "${allTrees.size} tán", VC.teal),
            ) else listOf(
                Quadruple("ALL", "🌳 Tổng số cây đã gắn nhãn", "${allTrees.size} cây", VC.cyan),
                Quadruple("healthy", "🟢 Cây khỏe mạnh", "$healthyCount cây (${(healthyCount * 100f / allTrees.size.coerceAtLeast(1)).toInt()}%)", VC.mint),
                Quadruple("moderate", "🟡 Cần theo dõi", "$moderateCount cây (${(moderateCount * 100f / allTrees.size.coerceAtLeast(1)).toInt()}%)", VC.amber),
                Quadruple("stressed", "🔴 Suy yếu — cần kiểm tra", "$stressedCount cây (${(stressedCount * 100f / allTrees.size.coerceAtLeast(1)).toInt()}%)", VC.red),
            )).forEach { (key, title, valTxt, col) ->
                val active = hud.geeTreeStatusFilter == key
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) col.copy(alpha = 0.22f) else VC.elevated)
                        .border(if (active) 1.6.dp else 1.dp, if (active) col else col.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .clickable { hud.geeTreeStatusFilter = key }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    Text(title, color = VC.text2, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    Text(valTxt, color = col, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }

        // Lọc theo Hàng cây trong vùng khoanh
        if (rowsAvailable.size > 1) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Hàng cây:", color = VC.text2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                val rowOptions = listOf(0) + rowsAvailable
                rowOptions.forEach { rNum ->
                    val active = selectedRowFilter == rNum
                    val lbl = if (rNum == 0) "Tất cả (${rowsAvailable.size} hàng)" else "Hàng $rNum"
                    Text(
                        lbl,
                        color = if (active) Color(0xFF08160F) else VC.text2,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (active) VC.mint else VC.elevated)
                            .clickable { selectedRowFilter = rNum }
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Danh sách thẻ trạng thái từng cây (2 cột)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            displayedTrees.chunked(2).forEach { pair ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pair.forEach { sp ->
                        val isSel = hud.selectedGeeTreeCode == sp.code
                        val stCol = when (sp.status) {
            "unknown" -> VC.teal
                            "healthy" -> VC.mint
                            "moderate" -> VC.amber
                            else -> VC.red
                        }
                        val stIcon = when (sp.status) {
            "unknown" -> "?"
                            "healthy" -> "🟢"
                            "moderate" -> "🟡"
                            else -> "🔴"
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) stCol.copy(alpha = 0.16f) else VC.elevated)
                                .border(
                                    if (isSel) 1.8.dp else 1.dp,
                                    if (isSel) Color(0xFFFFEA00) else stCol.copy(alpha = 0.45f),
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable {
                                    hud.selectedGeeTreeCode = if (isSel) null else sp.code
                                    hud.showGeeTreeLabels = true
                                }
                                .padding(9.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        sp.code,
                                        color = Color(0xFF08160F),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(stCol)
                                            .padding(horizontal = 7.dp, vertical = 2.dp),
                                    )
                                    Text(
                                        "Hàng ${sp.rowNumber}",
                                        color = VC.cyan,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        "🎯 ${sp.confidencePct}% · Ø ${sp.crownDiameterM?.let { fmtNum(it, 1) } ?: "—"}m",
                                        color = VC.lime,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        "📱 QR",
                                        color = Color(0xFF06150D),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(VC.cyan)
                                            .clickable { qrPreviewPoint = sp }
                                            .padding(horizontal = 6.dp, vertical = 1.5.dp),
                                    )
                                }
                                Text(
                                    "$stIcon ${sp.resolvedStatusVi} · ${if (sp.healthScore < 0) "?" else "${sp.healthScore}/100"}",
                                    color = stCol,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                            Text(
                                "🌿 NDVI ${sp.ndvi?.let { fmtNum(it, 2) } ?: "—"}  ·  🧪 NDRE ${sp.ndre?.let { fmtNum(it, 2) } ?: "—"}  ·  💧 NDMI ${sp.ndmi?.let { fmtNum(it, 2) } ?: "—"}",
                                color = VC.text,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                sp.resolvedDiagnosisVi,
                                color = VC.text2,
                                fontSize = 11.5.sp,
                            )
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        if (filteredTrees.size > 24) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    if (showAllRows) "▲ Thu gọn danh sách cây (hiện 24/${filteredTrees.size} cây)"
                    else "▼ Xem toàn bộ ${filteredTrees.size} cây đã gắn nhãn trong vùng khoanh",
                    color = VC.mint,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(VC.elevated)
                        .border(1.dp, VC.mint.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        .clickable { showAllRows = !showAllRows }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                )
            }
        }
    }

    qrPreviewPoint?.let { sp ->
        val rule = if (sp.code.startsWith("CF", ignoreCase = true)) OrchardCropSpacingRule.COFFEE else OrchardCropSpacingRule.DURIAN
        val areaM2 = hud.areaHa * 10_000.0
        val mappedTree = SpacingLabeledTree(
            treeNumber = sp.treeNumber.coerceAtLeast(1),
            code = sp.code,
            rule = rule,
            rowNumber = sp.rowNumber,
            colNumber = 1,
            pos = sp.pos,
            regionName = hud.regionName,
            areaM2 = areaM2,
            totalTreesInRegion = allTrees.size,
            qrPayload = Qr.spacingPayloadFor(
                code = sp.code,
                cropType = rule.id,
                spacingM = rule.spacingM,
                row = sp.rowNumber,
                col = 1,
                lat = sp.pos.lat,
                lng = sp.pos.lng,
                areaM2 = areaM2,
                totalTrees = allTrees.size,
            ),
        )
        SpacingTreeQrDialog(
            tree = mappedTree,
            onDismiss = { qrPreviewPoint = null },
            onToast = {},
        )
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/**
 * BẢNG GIẢI THÍCH CÁC ICON & CHỈ SỐ ĐO ĐƯỢC TRÊN VÙNG KHOANH:
 * Đặt ngay bên dưới bản đồ để giải thích rõ ràng từng icon nhỏ trên vùng khoanh có ý nghĩa gì,
 * giá trị bao nhiêu và đánh giá tốt/xấu ra sao.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegionIconLegendCard(hud: RegionHudState, modifier: Modifier = Modifier) {
    val allBadges = hud.allBadges()
    val categories = remember(allBadges) { listOf("Tất cả (${allBadges.size})") + allBadges.map { it.category }.distinct() }
    var selectedCat by remember { mutableStateOf(categories.first()) }
    if (selectedCat !in categories) selectedCat = categories.first()
    val filtered = if (selectedCat.startsWith("Tất cả")) allBadges else allBadges.filter { it.category == selectedCat }

    VCard(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Eyebrow("BẢNG GIẢI THÍCH CÁC ICON CHỈ SỐ TRÊN VÙNG ĐÃ KHOANH")
                Text(
                    "Mỗi icon nhỏ trên vùng khoanh tương ứng với 1 chỉ số đo đạc dưới đây (${hud.regionName} · ${fmtNum(hud.areaHa, 2)} ha)",
                    color = VC.text2,
                    fontSize = 13.5.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (hud.loading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = VC.teal, strokeWidth = 2.dp)
                    Text("Đang đồng bộ…", color = VC.teal, fontSize = 12.5.sp)
                }
            }
        }

        // Bộ lọc nhóm icon
        ChipRow(
            categories,
            selectedCat,
            { it },
            { selectedCat = it },
            Modifier.padding(top = 8.dp, bottom = 8.dp),
        )

        // Bảng giải thích rõ ràng từng Icon: [Huy hiệu Icon] | Tên chỉ số & Giá trị | Giải thích ý nghĩa
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            filtered.chunked(2).forEach { rowItems ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowItems.forEach { b ->
                        Row(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(VC.elevated)
                                .border(1.dp, b.color.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            // Cột Icon nhỏ nổi bật
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0E2217))
                                    .border(1.dp, b.color.copy(alpha = 0.7f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(b.icon, fontSize = 17.sp)
                            }
                            // Cột Tên chỉ số, Giá trị & Giải thích rõ ràng
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        b.label,
                                        color = VC.text,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        b.value,
                                        color = b.color,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                    )
                                }
                                if (b.sub.isNotBlank()) {
                                    Text(b.sub, color = VC.teal, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                                if (b.explanation.isNotBlank()) {
                                    Text(b.explanation, color = VC.text2, fontSize = 11.5.sp)
                                }
                            }
                        }
                    }
                    if (rowItems.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * TRUNG TÂM ĐỊA HÌNH · THỦY VĂN · 12 NGUỒN DEM TOÀN CẦU
 * Tích hợp 8 kho mã nguồn mở:
 * - elevatr (Python & R) & srtm.py: Tải DEM theo Bounding Box/Polygon, xuất GeoTIFF (.tif) & nội suy .hgt
 * - asf_search, OT_3DEP_Workflows, eodag: Truy vấn vệ tinh USGS, ASF ALOS PALSAR 12.5m, Copernicus, OpenTopography COG
 * - pyDEM & demcoreg: Phân tích Slope, Aspect, Catchment Area, TWI, HAND, TRI, TPI & chuẩn hóa đồng đăng ký 3D Nuth & Kääb (2011)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DemTerrainHubCard(
    hud: RegionHudState,
    cfg: SatelliteConfig,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dh = hud.demHubResult ?: return
    val hyd = dh.hydrology
    val cor = dh.coregistration
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var subTab by remember { mutableStateOf("SOURCES") }

    VCard(modifier.fillMaxWidth()) {
        // Header + Nút Xuất GeoTIFF (.tif)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Eyebrow("⛰ TRUNG TÂM ĐỊA HÌNH · THỦY VĂN · 12 NGUỒN DEM TOÀN CẦU (elevatr · pyDEM · demcoreg)")
                Text(
                    "Đang phân tích: ${dh.primarySource.shortLabel} (${dh.primarySource.resolutionLabel}) so khớp 3D với ${dh.referenceSource.shortLabel} · Lưới ${dh.cols}×${dh.rows} ô (~${fmtNum(dh.cellM, 1)}m/ô)",
                    color = VC.text2,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0E2A1E))
                    .border(1.3.dp, VC.mint, RoundedCornerShape(10.dp))
                    .clickable {
                        if (dh.geotiffBytes.isEmpty()) {
                            onToast("Chưa có dữ liệu GeoTIFF để xuất.")
                        } else {
                            runCatching {
                                val outDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "dem_geotiff").apply { mkdirs() }
                                val outFile = File(outDir, dh.geotiffFilename)
                                outFile.writeBytes(dh.geotiffBytes)
                                outFile
                            }.onSuccess { f ->
                                onToast("Đã xuất tệp GeoTIFF chuẩn OGC (${dh.geotiffSizeBytes / 1024} KB): ${f.name} tại ${f.parentFile?.name}/${f.name}")
                            }.onFailure { e ->
                                onToast("Lỗi xuất GeoTIFF: ${e.message}")
                            }
                        }
                    }
                    .padding(horizontal = 11.dp, vertical = 7.dp),
            ) {
                Text(
                    "💾 Xuất GeoTIFF (.tif · ${dh.geotiffSizeBytes / 1024} KB)",
                    color = VC.mint,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Thanh chọn nhanh 12 Nguồn dữ liệu DEM miễn phí toàn cầu
        Text(
            "Chọn nguồn DEM phân tích trực tiếp cho vùng \"${hud.regionName}\" (12 nguồn toàn cầu):",
            color = VC.teal,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DemSourceKey.entries.forEach { src ->
                val active = hud.selectedDemSource == src
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (active) VC.mint.copy(alpha = 0.22f) else VC.elevated)
                        .border(
                            if (active) 1.5.dp else 1.dp,
                            if (active) VC.mint else VC.border,
                            RoundedCornerShape(16.dp),
                        )
                        .clickable {
                            hud.switchDemSource(cfg, src, scope, onToast)
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        "${src.shortLabel} (${src.resolutionLabel})",
                        color = if (active) VC.mint else VC.text,
                        fontSize = 11.5.sp,
                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 4 Hộp chỉ số chuyên sâu: FABDEM Bare-Earth, pyDEM TWI & Catchment, pyDEM Slope & Aspect, demcoreg 3D Nuth & Kääb
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Box 1: FABDEM Bare-Earth vs DSM Canopy
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(VC.elevated)
                        .border(1.2.dp, VC.lime.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                        .padding(9.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("🌲 MẶT ĐẤT TRẦN FABDEM & CHIỀU CAO TÁN", color = VC.lime, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "Đất trần ${fmtNum(hyd.bareEarthMeanM, 1)} m · Tán ~${fmtNum(hyd.canopyHeightMeanM, 1)} m",
                        color = VC.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "DSM ${fmtNum(hyd.elevMeanM, 1)} m (${fmtNum(hyd.elevMinM, 1)}–${fmtNum(hyd.elevMaxM, 1)} m · ΔH ${fmtNum(hyd.reliefM, 1)} m)",
                        color = VC.text2,
                        fontSize = 11.sp,
                    )
                }

                // Box 2: pyDEM Thủy văn & Tụ thủy (TWI + Catchment + HAND)
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(VC.elevated)
                        .border(1.2.dp, VC.cyan.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                        .padding(9.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("🌊 THỦY VĂN & TỤ THỦY (pyDEM TWI / HAND)", color = VC.cyan, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "TWI ${fmtNum(hyd.twiMean, 2)} (max ${fmtNum(hyd.twiMax, 2)}) · Ẩm cao ${fmtNum(hyd.highWetnessAreaPct, 1)}%",
                        color = VC.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "Tụ thủy max ${fmtNum(hyd.catchmentMaxHa, 2)} ha (${fmtNum(hyd.catchmentMeanM2, 0)} m²) · HAND ${fmtNum(hyd.handMeanM, 2)} m",
                        color = VC.text2,
                        fontSize = 11.sp,
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Box 3: pyDEM Độ dốc, Hướng sườn, TRI, TPI
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(VC.elevated)
                        .border(1.2.dp, VC.teal.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                        .padding(9.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("🧭 ĐỘ DỐC & HƯỚNG SƯỜN (pyDEM SLOPE / ASPECT)", color = VC.teal, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "Dốc ${fmtNum(hyd.slopeMeanDeg, 2)}° (${fmtNum(hyd.slopeMeanPct, 1)}%) · Hướng ${hyd.aspectCardinalVi}",
                        color = VC.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "Max ${fmtNum(hyd.slopeMaxDeg, 1)}° · TRI ${fmtNum(hyd.triMeanM, 2)} m · TPI ${fmtNum(hyd.tpiMeanM, 2)} m · ${hyd.terrainClassVi}",
                        color = VC.text2,
                        fontSize = 11.sp,
                    )
                }

                // Box 4: demcoreg Đồng đăng ký 3D Nuth & Kääb (2011)
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(VC.elevated)
                        .border(1.2.dp, VC.mint.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                        .padding(9.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("⚖ ĐỒNG ĐĂNG KÝ 3D DEM (demcoreg Nuth & Kääb)", color = VC.mint, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "ΔX ${fmtNum(cor.dxEastM, 2)}m · ΔY ${fmtNum(cor.dyNorthM, 2)}m · ΔZ ${fmtNum(cor.dzBiasM, 2)}m",
                        color = VC.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "RMSE ${fmtNum(cor.rmseBeforeM, 2)}m → ${fmtNum(cor.rmseAfterM, 2)}m (NMAD ${fmtNum(cor.nmadAfterM, 2)}m · +${fmtNum(cor.improvementPct, 0)}%)",
                        color = VC.text2,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 3 Tab chi tiết: 12 Nguồn DEM | Cao độ đỉnh ranh srtm.py | Cảnh vệ tinh asf_search & eodag
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                "SOURCES" to "🌐 12 Nguồn DEM Toàn Cầu (${dh.sourceComparisons.size})",
                "POINTS" to "📍 Nội suy Đỉnh Ranh srtm.py (${dh.sampledPoints.size})",
                "GRANULES" to "🛰 Cảnh Vệ tinh asf_search & eodag (${dh.satelliteGranules.size})",
            ).forEach { (key, title) ->
                val active = subTab == key
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) VC.teal.copy(alpha = 0.22f) else VC.elevated)
                        .border(1.dp, if (active) VC.teal else VC.border, RoundedCornerShape(8.dp))
                        .clickable { subTab = key }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        title,
                        color = if (active) VC.mint else VC.text2,
                        fontSize = 11.5.sp,
                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        when (subTab) {
            "SOURCES" -> {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    dh.sourceComparisons.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { sc ->
                                val isCur = sc.id == dh.primarySource.id
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isCur) VC.mint.copy(alpha = 0.14f) else VC.elevated)
                                        .border(1.dp, if (isCur) VC.mint else VC.border, RoundedCornerShape(8.dp))
                                        .clickable {
                                            hud.switchDemSource(cfg, DemSourceKey.fromId(sc.id), scope, onToast)
                                        }
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            sc.shortLabel,
                                            color = if (isCur) VC.mint else VC.text,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                        )
                                        Text(
                                            "${fmtNum(sc.meanElevationM, 1)} m · ${sc.resolutionLabel}",
                                            color = VC.cyan,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Text(
                                        "${sc.demType} · ${sc.coverage} · Repo: ${sc.repoTool}",
                                        color = VC.text2,
                                        fontSize = 10.5.sp,
                                    )
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            "POINTS" -> {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    dh.sampledPoints.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { pt ->
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(VC.elevated)
                                        .border(1.dp, VC.border, RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(pt.label, color = VC.mint, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                        Text(
                                            "DSM ${pt.zPrimaryM?.let { fmtNum(it, 1) } ?: "—"} m · Đất trần ${pt.zBareEarthM?.let { fmtNum(it, 1) } ?: "—"} m",
                                            color = VC.lime,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Text(
                                        "📍 (${fmtNum(pt.lat, 5)}, ${fmtNum(pt.lon, 5)}) · SRTM ${pt.zReferenceM?.let { fmtNum(it, 1) } ?: "—"} m · Tán ~${fmtNum(pt.canopyOrBuildingHeightM, 1)} m",
                                        color = VC.text2,
                                        fontSize = 10.5.sp,
                                    )
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    dh.satelliteGranules.forEach { gr ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(VC.elevated)
                                .border(1.dp, VC.border, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "🛰 ${gr.provider} · ${gr.platform} (${gr.productType})",
                                    color = VC.mint,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                                Text(
                                    "Scene: ${gr.sceneId} · Ngày chụp: ${gr.acquisitionDate}",
                                    color = VC.text2,
                                    fontSize = 11.sp,
                                )
                            }
                            Text(
                                "${fmtNum(gr.resolutionM, 1)} m",
                                color = VC.cyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }
            }
        }
    }
}
