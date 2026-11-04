package com.example.test_tablet.data.vwing

import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

data class DroneUploadedFrameTelemetry(
    val fileName: String,
    val widthPx: Int,
    val heightPx: Int,
    val meanR: Double,
    val meanG: Double,
    val meanB: Double,
    val meanRe: Double,
    val meanNir: Double,
)

data class SurveyDroneProfileInfo(
    val droneId: String,
    val name: String,
    val typeVi: String,
    val rgbSensor: String,
    val msSensors: String,
    val sunlightSensor: String,
    val rtkAccuracy: String,
    val maxCoverageHaPerFlight: Double,
)

data class CropGuideInfo(
    val cropCode: String,
    val nameVi: String,
    val targetDensityPerHa: Int,
    val standardSprayRateLHa: Double,
    val standardFertilizerKgHa: Double,
    val primaryIndex: String,
    val guideNoteVi: String,
)

data class DroneStep1SurveyAndRtk(
    val surveyDrone: SurveyDroneProfileInfo,
    val cropGuide: CropGuideInfo,
    val flightAltitudeM: Double,
    val gsdCmPerPixel: Double,
    val forwardOverlapPct: Double,
    val sideOverlapPct: Double,
    val rawFramesPerBand: Int,
    val total5BandCaptures: Int,
    val rtkFixStatus: String,
    val radiometricCalibration: String,
)

data class DroneVegIndexItem(
    val code: String,
    val nameVi: String,
    val formula: String,
    val bandsUsed: String,
    val mean: Double,
    val p10: Double,
    val p90: Double,
    val statusVi: String,
    val agronomicMeaningVi: String,
    val mapUrl: String,
)

data class DroneStandCountAndYield(
    val cropNameVi: String,
    val targetDensityPerHa: Int,
    val actualDensityPerHa: Int,
    val detectedStandCount: Int,
    val missingGapsCount: Int,
    val emergenceSurvivalPct: Double,
    val estimatedHarvestYieldTons: Double,
)

data class DroneEarlyAnomalyItem(
    val anomalyCode: String,
    val titleVi: String,
    val indicatorUsed: String,
    val affectedPct: Double,
    val affectedHa: Double,
    val severityVi: String,
    val actionVi: String,
)

data class DroneStep2MappingAndIndices(
    val orthomosaicResolutionCm: Double,
    val dsmMinM: Double,
    val dsmMaxM: Double,
    val canopyHeightMeanM: Double,
    val compatibleSoftware: List<String>,
    val fiveVegetationIndices: List<DroneVegIndexItem>,
    val standCountAndYield: DroneStandCountAndYield,
    val earlyAnomalyDetection: List<DroneEarlyAnomalyItem>,
)

data class SprayerHardwareInfo(
    val sprayerId: String,
    val name: String,
    val manufacturer: String,
    val tankVolumeL: Double,
    val spreaderCapacityKg: Double,
    val swathWidthM: Double,
    val maxFlowRateLMin: Double,
    val nozzleSystem: String,
    val exportFormat: String,
)

data class VraPrescriptionZoneItem(
    val zoneId: String,
    val zoneNameVi: String,
    val conditionRule: String,
    val actionVi: String,
    val dosePctOfStandard: Double,
    val sprayRateLHa: Double,
    val fertilizerRateKgHa: Double,
    val areaPct: Double,
    val areaHa: Double,
    val colorHex: String,
)

data class DroneStep3VariableRateSpraying(
    val sprayerHardware: SprayerHardwareInfo,
    val baseSprayRateLHa: Double,
    val vraEffectiveRateLHa: Double,
    val uniformSprayTotalL: Double,
    val vraSprayTotalL: Double,
    val savedSprayVolumeL: Double,
    val uniformFertilizerKg: Double,
    val vraFertilizerKg: Double,
    val savedFertilizerKg: Double,
    val chemicalSavingsPct: Double,
    val guaranteedSavingsRangeText: String,
    val estimatedCostSavedVnd: Double,
    val sprayerTankLoadsNeeded: Int,
    val prescriptionZones: List<VraPrescriptionZoneItem>,
    val exportFormatsReady: List<String>,
)

data class DroneStep4AgInsurance(
    val certificateId: String,
    val orthomosaicRtkAccuracyCm: Double,
    val totalInsuredAreaHa: Double,
    val verifiedDamagedAreaHa: Double,
    val verifiedDamagedPct: Double,
    val estimatedYieldLossTons: Double,
    val estimatedClaimValueMillionVnd: Double,
    val evidenceSummaryVi: String,
)

data class DroneMultispectralReport(
    val workflowTitleVi: String,
    val simulated: Boolean,
    val areaHa: Double,
    val bounds: GeoBounds,
    val overlayLayer: String,
    val activeOverlayUrl: String,
    val ingestSummaryVi: String,
    val uploadedFramesCount: Int,
    val step1: DroneStep1SurveyAndRtk,
    val step2: DroneStep2MappingAndIndices,
    val step3: DroneStep3VariableRateSpraying,
    val step4: DroneStep4AgInsurance,
)

object DroneMultispectralClient {
    /** Trích xuất phản xạ R, G, B và ước lượng RedEdge/NIR từ Bitmap ảnh Camera Drone người dùng chọn. */
    fun extractFrameTelemetry(fileName: String, bmp: Bitmap): DroneUploadedFrameTelemetry {
        val w = bmp.width.coerceAtLeast(1)
        val h = bmp.height.coerceAtLeast(1)
        val stepX = max(1, w / 32)
        val stepY = max(1, h / 32)
        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var count = 0
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val px = bmp.getPixel(x, y)
                val r = ((px shr 16) and 0xFF) / 255.0
                val g = ((px shr 8) and 0xFF) / 255.0
                val b = (px and 0xFF) / 255.0
                sumR += r
                sumG += g
                sumB += b
                count++
                x += stepX
            }
            y += stepY
        }
        val n = max(1, count).toDouble()
        val mr = (sumR / n * 0.35).coerceIn(0.02, 0.35)
        val mg = (sumG / n * 0.42).coerceIn(0.03, 0.40)
        val mb = (sumB / n * 0.30).coerceIn(0.02, 0.30)
        val greenRatio = (mg / max(mr, 0.02)).coerceIn(0.6, 2.8)
        val mre = (mg * 1.45 * greenRatio.coerceAtMost(1.8)).coerceIn(0.10, 0.48)
        val mnir = (mg * 2.55 * greenRatio.coerceAtMost(2.1)).coerceIn(0.18, 0.75)
        return DroneUploadedFrameTelemetry(
            fileName = fileName,
            widthPx = w,
            heightPx = h,
            meanR = mr,
            meanG = mg,
            meanB = mb,
            meanRe = mre,
            meanNir = mnir,
        )
    }

    fun analyze(
        cfg: SatelliteConfig,
        ring: List<GeoPoint>,
        surveyDroneId: String = "DJI_MAVIC_3_MULTISPECTRAL",
        sprayerId: String = "DJI_AGRAS_T50",
        cropGuide: String = "FRUIT_ORCHARD",
        flightAltitudeM: Double = 50.0,
        overlayLayer: String = "VRA_PRESCRIPTION",
        uploadedFrames: List<DroneUploadedFrameTelemetry> = emptyList(),
        bufferM: Double = 200.0,
    ): DroneMultispectralReport {
        val pts = JSONArray().also { a ->
            Geo.open(ring).let { it + it.first() }.forEach {
                a.put(JSONArray().put(it.lng).put(it.lat))
            }
        }
        val framesArr = JSONArray().also { arr ->
            uploadedFrames.forEach { f ->
                arr.put(
                    JSONObject()
                        .put("file_name", f.fileName)
                        .put("width_px", f.widthPx)
                        .put("height_px", f.heightPx)
                        .put("mean_r", f.meanR)
                        .put("mean_g", f.meanG)
                        .put("mean_b", f.meanB)
                        .put("mean_re", f.meanRe)
                        .put("mean_nir", f.meanNir),
                )
            }
        }
        val body = JSONObject()
            .put("region", JSONObject().put("ring", pts))
            .put("survey_drone_id", surveyDroneId)
            .put("sprayer_id", sprayerId)
            .put("crop_guide", cropGuide)
            .put("flight_altitude_m", flightAltitudeM)
            .put("overlay_layer", overlayLayer)
            .put("uploaded_frames", framesArr)
            .put("buffer_m", bufferM)
        val o = GisClient.call(cfg, "/v1/drone-multispectral/analyze", body)
        return parseReport(o)
    }

    private fun parseReport(o: JSONObject): DroneMultispectralReport {
        val bObj = o.optJSONObject("bounds") ?: JSONObject()
        val bounds = GeoBounds(
            west = bObj.optDouble("west", 106.0),
            south = bObj.optDouble("south", 10.0),
            east = bObj.optDouble("east", 106.01),
            north = bObj.optDouble("north", 10.01),
        )

        val s1 = o.optJSONObject("step_1_survey_and_rtk") ?: JSONObject()
        val sd = s1.optJSONObject("survey_drone") ?: JSONObject()
        val cg = s1.optJSONObject("crop_guide") ?: JSONObject()
        val step1 = DroneStep1SurveyAndRtk(
            surveyDrone = SurveyDroneProfileInfo(
                droneId = sd.optString("drone_id", "DJI_MAVIC_3_MULTISPECTRAL"),
                name = sd.optString("name", "DJI Mavic 3 Multispectral (M3M)"),
                typeVi = sd.optString("type_vi", "Quadcopter Đa phổ + RTK"),
                rgbSensor = sd.optString("rgb_sensor", "20 MP 4/3 CMOS"),
                msSensors = sd.optString("ms_sensors", "4 × 5 MP (G · R · RE · NIR)"),
                sunlightSensor = sd.optString("sunlight_sensor", "Cảm biến DLS tích hợp"),
                rtkAccuracy = sd.optString("rtk_accuracy", "Ngang ±1.0 cm · Đứng ±1.5 cm"),
                maxCoverageHaPerFlight = sd.optDouble("max_coverage_ha_per_flight", 200.0),
            ),
            cropGuide = CropGuideInfo(
                cropCode = cg.optString("crop_code", "FRUIT_ORCHARD"),
                nameVi = cg.optString("name_vi", "Cây ăn quả lâu năm"),
                targetDensityPerHa = cg.optInt("target_density_per_ha", 175),
                standardSprayRateLHa = cg.optDouble("standard_spray_rate_l_ha", 180.0),
                standardFertilizerKgHa = cg.optDouble("standard_fertilizer_kg_ha", 120.0),
                primaryIndex = cg.optString("primary_index", "NDRE"),
                guideNoteVi = cg.optString("guide_note_vi", ""),
            ),
            flightAltitudeM = s1.optDouble("flight_altitude_m", 50.0),
            gsdCmPerPixel = s1.optDouble("gsd_cm_per_pixel", 1.34),
            forwardOverlapPct = s1.optDouble("forward_overlap_pct", 80.0),
            sideOverlapPct = s1.optDouble("side_overlap_pct", 75.0),
            rawFramesPerBand = s1.optInt("raw_frames_per_band", 36),
            total5BandCaptures = s1.optInt("total_5band_captures", 180),
            rtkFixStatus = s1.optString("rtk_fix_status", "RTK FIXED (±1.2 cm)"),
            radiometricCalibration = s1.optString("radiometric_calibration", ""),
        )

        val s2 = o.optJSONObject("step_2_mapping_and_indices") ?: JSONObject()
        val swList = s2.optJSONArray("compatible_software")?.let { arr ->
            (0 until arr.length()).map { i -> arr.optString(i) }
        } ?: emptyList()
        val fiveIdx = s2.optJSONArray("five_vegetation_indices")?.let { arr ->
            (0 until arr.length()).map { i ->
                val ix = arr.getJSONObject(i)
                DroneVegIndexItem(
                    code = ix.optString("code"),
                    nameVi = ix.optString("name_vi"),
                    formula = ix.optString("formula"),
                    bandsUsed = ix.optString("bands_used"),
                    mean = ix.optDouble("mean", 0.5),
                    p10 = ix.optDouble("p10", 0.3),
                    p90 = ix.optDouble("p90", 0.7),
                    statusVi = ix.optString("status_vi"),
                    agronomicMeaningVi = ix.optString("agronomic_meaning_vi"),
                    mapUrl = ix.optString("map_url"),
                )
            }
        } ?: emptyList()
        val scObj = s2.optJSONObject("stand_count_and_yield") ?: JSONObject()
        val standCount = DroneStandCountAndYield(
            cropNameVi = scObj.optString("crop_name_vi", "Cây ăn quả"),
            targetDensityPerHa = scObj.optInt("target_density_per_ha", 175),
            actualDensityPerHa = scObj.optInt("actual_density_per_ha", 162),
            detectedStandCount = scObj.optInt("detected_stand_count", 36),
            missingGapsCount = scObj.optInt("missing_gaps_count", 3),
            emergenceSurvivalPct = scObj.optDouble("emergence_survival_pct", 92.5),
            estimatedHarvestYieldTons = scObj.optDouble("estimated_harvest_yield_tons", 3.8),
        )
        val anomList = s2.optJSONArray("early_anomaly_detection")?.let { arr ->
            (0 until arr.length()).map { i ->
                val a = arr.getJSONObject(i)
                DroneEarlyAnomalyItem(
                    anomalyCode = a.optString("anomaly_code"),
                    titleVi = a.optString("title_vi"),
                    indicatorUsed = a.optString("indicator_used"),
                    affectedPct = a.optDouble("affected_pct", 0.0),
                    affectedHa = a.optDouble("affected_ha", 0.0),
                    severityVi = a.optString("severity_vi"),
                    actionVi = a.optString("action_vi"),
                )
            }
        } ?: emptyList()
        val step2 = DroneStep2MappingAndIndices(
            orthomosaicResolutionCm = s2.optDouble("orthomosaic_resolution_cm", 1.34),
            dsmMinM = s2.optDouble("dsm_min_m", 10.0),
            dsmMaxM = s2.optDouble("dsm_max_m", 18.5),
            canopyHeightMeanM = s2.optDouble("canopy_height_mean_m", 3.4),
            compatibleSoftware = swList,
            fiveVegetationIndices = fiveIdx,
            standCountAndYield = standCount,
            earlyAnomalyDetection = anomList,
        )

        val s3 = o.optJSONObject("step_3_variable_rate_spraying") ?: JSONObject()
        val sp = s3.optJSONObject("sprayer_hardware") ?: JSONObject()
        val rxZones = s3.optJSONArray("prescription_zones")?.let { arr ->
            (0 until arr.length()).map { i ->
                val z = arr.getJSONObject(i)
                VraPrescriptionZoneItem(
                    zoneId = z.optString("zone_id"),
                    zoneNameVi = z.optString("zone_name_vi"),
                    conditionRule = z.optString("condition_rule"),
                    actionVi = z.optString("action_vi"),
                    dosePctOfStandard = z.optDouble("dose_pct_of_standard", 100.0),
                    sprayRateLHa = z.optDouble("spray_rate_l_ha", 150.0),
                    fertilizerRateKgHa = z.optDouble("fertilizer_rate_kg_ha", 100.0),
                    areaPct = z.optDouble("area_pct", 20.0),
                    areaHa = z.optDouble("area_ha", 0.04),
                    colorHex = z.optString("color_hex", "#0288D1"),
                )
            }
        } ?: emptyList()
        val expList = s3.optJSONArray("export_formats_ready")?.let { arr ->
            (0 until arr.length()).map { i -> arr.optString(i) }
        } ?: emptyList()
        val step3 = DroneStep3VariableRateSpraying(
            sprayerHardware = SprayerHardwareInfo(
                sprayerId = sp.optString("sprayer_id", "DJI_AGRAS_T50"),
                name = sp.optString("name", "DJI Agras T50"),
                manufacturer = sp.optString("manufacturer", "DJI Agriculture"),
                tankVolumeL = sp.optDouble("tank_volume_l", 40.0),
                spreaderCapacityKg = sp.optDouble("spreader_capacity_kg", 50.0),
                swathWidthM = sp.optDouble("swath_width_m", 11.0),
                maxFlowRateLMin = sp.optDouble("max_flow_rate_l_min", 24.0),
                nozzleSystem = sp.optString("nozzle_system", ""),
                exportFormat = sp.optString("export_format", ""),
            ),
            baseSprayRateLHa = s3.optDouble("base_spray_rate_l_ha", 180.0),
            vraEffectiveRateLHa = s3.optDouble("vra_effective_rate_l_ha", 132.0),
            uniformSprayTotalL = s3.optDouble("uniform_spray_total_l", 40.0),
            vraSprayTotalL = s3.optDouble("vra_spray_total_l", 29.0),
            savedSprayVolumeL = s3.optDouble("saved_spray_volume_l", 11.0),
            uniformFertilizerKg = s3.optDouble("uniform_fertilizer_kg", 26.0),
            vraFertilizerKg = s3.optDouble("vra_fertilizer_kg", 19.0),
            savedFertilizerKg = s3.optDouble("saved_fertilizer_kg", 7.0),
            chemicalSavingsPct = s3.optDouble("chemical_savings_pct", 26.8),
            guaranteedSavingsRangeText = s3.optString("guaranteed_savings_range_text", "15% – 40%"),
            estimatedCostSavedVnd = s3.optDouble("estimated_cost_saved_vnd", 2000000.0),
            sprayerTankLoadsNeeded = s3.optInt("sprayer_tank_loads_needed", 1),
            prescriptionZones = rxZones,
            exportFormatsReady = expList,
        )

        val s4 = o.optJSONObject("step_4_agricultural_insurance") ?: JSONObject()
        val step4 = DroneStep4AgInsurance(
            certificateId = s4.optString("certificate_id", "UAV-INS-0001"),
            orthomosaicRtkAccuracyCm = s4.optDouble("orthomosaic_rtk_accuracy_cm", 1.5),
            totalInsuredAreaHa = s4.optDouble("total_insured_area_ha", 0.22),
            verifiedDamagedAreaHa = s4.optDouble("verified_damaged_area_ha", 0.02),
            verifiedDamagedPct = s4.optDouble("verified_damaged_pct", 9.5),
            estimatedYieldLossTons = s4.optDouble("estimated_yield_loss_tons", 0.18),
            estimatedClaimValueMillionVnd = s4.optDouble("estimated_claim_value_million_vnd", 11.7),
            evidenceSummaryVi = s4.optString("evidence_summary_vi", ""),
        )

        return DroneMultispectralReport(
            workflowTitleVi = o.optString("workflow_title_vi", "HỆ THỐNG CAMERA ĐA PHỔ DRONE & PHUN BIẾN THIÊN (VRA)"),
            simulated = o.optBoolean("simulated", true),
            areaHa = o.optDouble("area_ha", 0.22),
            bounds = bounds,
            overlayLayer = o.optString("overlay_layer", "VRA_PRESCRIPTION"),
            activeOverlayUrl = o.optString("active_overlay_url", ""),
            ingestSummaryVi = o.optString("ingest_summary_vi", ""),
            uploadedFramesCount = o.optInt("uploaded_frames_count", 0),
            step1 = step1,
            step2 = step2,
            step3 = step3,
            step4 = step4,
        )
    }
}
