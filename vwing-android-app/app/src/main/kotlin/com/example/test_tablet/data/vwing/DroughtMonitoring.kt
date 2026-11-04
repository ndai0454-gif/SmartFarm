package com.example.test_tablet.data.vwing

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class DroughtCoreIndexItem(
    val code: String,
    val icon: String,
    val nameVi: String,
    val nameEn: String,
    val formula: String,
    val satelliteSource: String,
    val focusYearValue: Double,
    val unit: String,
    val range26yText: String,
    val roleVi: String,
)

data class FaoGaulBoundaryInfo(
    val adm0Name: String,
    val adm1Name: String,
    val adm2Name: String,
    val climateZoneCode: String,
    val climateZoneVi: String,
    val faoGaulDataset: String,
    val hydrologicalContextVi: String,
    val isAridBasraProfile: Boolean,
)

data class LstSpatialZones(
    val desertOrBareSoilC: Double,
    val vegetatedCanopyC: Double,
    val waterAdjacentRiparianC: Double,
    val bareVsWaterGradientC: Double,
    val bareVsCanopyGradientC: Double,
)

data class VhiSeverityBreakdown(
    val extremeDroughtPct: Double,
    val severeDroughtPct: Double,
    val moderateDroughtPct: Double,
    val mildDroughtPct: Double,
    val noDroughtPct: Double,
    val extremeDroughtHa: Double,
    val severeDroughtHa: Double,
    val moderateDroughtHa: Double,
    val mildDroughtHa: Double,
    val noDroughtHa: Double,
)

data class DroughtReferenceYearSnapshot(
    val year: Int,
    val ndviMean: Double,
    val ndviMin: Double,
    val ndviMax: Double,
    val vciMeanPct: Double,
    val lstMeanC: Double,
    val lstMinC: Double,
    val lstMaxC: Double,
    val tciMeanPct: Double,
    val vhiMeanPct: Double,
    val rainfallMm: Double,
    val rainfallAnomalyPct: Double,
    val soilMoistureMm: Double,
    val droughtClassCode: String,
    val droughtClassVi: String,
    val droughtColorHex: String,
    val droughtAffectedPct: Double,
    val droughtAffectedHa: Double,
    val lstZones: LstSpatialZones,
    val vhiBreakdown: VhiSeverityBreakdown,
    val vhiImageUrl: String,
    val lstImageUrl: String,
    val ndviImageUrl: String,
    val spatialFindingVi: String,
)

data class NdviChangeClassItem(
    val classCode: String,
    val labelVi: String,
    val thresholdText: String,
    val areaPct: Double,
    val areaHa: Double,
    val colorHex: String,
)

data class NdviChange2000To2025(
    val period: String,
    val ndvi2000Mean: Double,
    val ndvi2025Mean: Double,
    val deltaNdviMean: Double,
    val deltaNdviMin: Double,
    val deltaNdviMax: Double,
    val netDegradedPct: Double,
    val netDegradedHa: Double,
    val netRecoveredPct: Double,
    val netRecoveredHa: Double,
    val stablePct: Double,
    val changeImageUrl: String,
    val changeClasses: List<NdviChangeClassItem>,
    val interpretationVi: String,
)

data class DroughtAnnualYearPoint(
    val year: Int,
    val isReferenceYear: Boolean,
    val ndvi: Double,
    val vciPct: Double,
    val lstC: Double,
    val tciPct: Double,
    val vhiPct: Double,
    val droughtClassCode: String,
    val droughtClassVi: String,
    val droughtColorHex: String,
    val rainfallMm: Double,
    val rainfallAnomalyPct: Double,
    val petMm: Double,
    val climaticWaterDeficitMm: Double,
    val soilMoistureMm: Double,
    val pdsi: Double,
    val eventNoteVi: String,
)

data class DecouplingDrivingFactor(
    val factorCode: String,
    val titleVi: String,
    val impactWeightPct: Double,
    val explanationVi: String,
)

data class DecouplingAndLagAnalysis(
    val keyPrincipleVi: String,
    val pearsonRRainNdviSameYear: Double,
    val pearsonRRainNdviLag1Y: Double,
    val pearsonRLstNdvi: Double,
    val pearsonRPetVhi: Double,
    val wettestYear: Int,
    val wettestRainfallMm: Double,
    val wettestVciPct: Double,
    val wettestLstC: Double,
    val driestYear: Int,
    val driestRainfallMm: Double,
    val driestVhiPct: Double,
    val driestLstC: Double,
    val hottestYear: Int,
    val hottestLstC: Double,
    val mostStressedYear: Int,
    val mostStressedVhiPct: Double,
    val case2018Vs2021SummaryVi: String,
    val drivingFactors: List<DecouplingDrivingFactor>,
)

data class BasraReferenceStudyInfo(
    val regionNameVi: String,
    val admBoundary: String,
    val wettest2018RainfallMm: Double,
    val wettest2018Ndvi: Double,
    val wettest2018VhiPct: Double,
    val driest2021RainfallMm: Double,
    val driest2021Ndvi: Double,
    val driest2021VhiPct: Double,
    val driest2021LstC: Double,
    val summaryInsightVi: String,
    val annualSeries2000To2025: List<DroughtAnnualYearPoint>,
)

data class DroughtTechDatasetItem(
    val component: String,
    val collections: String,
    val purposeVi: String,
)

data class DroughtMonitoringReport(
    val studyTitleVi: String,
    val studySubtitleVi: String,
    val simulated: Boolean,
    val studyMode: String,
    val focusYear: Int,
    val vhiAlpha: Double,
    val overlayMetric: String,
    val activeOverlayUrl: String,
    val areaHa: Double,
    val bounds: GeoBounds,
    val centroidLon: Double,
    val centroidLat: Double,
    val elevationM: Double,
    val faoGaul: FaoGaulBoundaryInfo,
    val sixCoreIndices: List<DroughtCoreIndexItem>,
    val focusYearSnapshot: DroughtReferenceYearSnapshot,
    val referenceYearsComparison: List<DroughtReferenceYearSnapshot>,
    val ndviChange2000To2025: NdviChange2000To2025,
    val annualTimeSeries: List<DroughtAnnualYearPoint>,
    val decouplingAnalysis: DecouplingAndLagAnalysis,
    val basraReference: BasraReferenceStudyInfo,
    val technologyAndDatasets: List<DroughtTechDatasetItem>,
    val agronomicRecommendationsVi: List<String>,
)

object DroughtMonitoringClient {
    fun analyze(
        cfg: SatelliteConfig,
        ring: List<GeoPoint>,
        studyMode: String = "LOCAL_AND_BASRA",
        focusYear: Int = 2025,
        vhiAlpha: Double = 0.50,
        overlayMetric: String = "VHI",
        bufferM: Double = 300.0,
    ): DroughtMonitoringReport {
        val pts = JSONArray().also { a ->
            Geo.open(ring).let { it + it.first() }.forEach {
                a.put(JSONArray().put(it.lng).put(it.lat))
            }
        }
        val body = JSONObject()
            .put("region", JSONObject().put("ring", pts))
            .put("study_mode", studyMode)
            .put("focus_year", focusYear)
            .put("vhi_alpha", vhiAlpha)
            .put("overlay_metric", overlayMetric)
            .put("buffer_m", bufferM)
        val o = GisClient.call(cfg, "/v1/drought-monitoring/analyze", body)
        return parseReport(o)
    }

    private fun parseSnapshot(s: JSONObject): DroughtReferenceYearSnapshot {
        val lz = s.optJSONObject("lst_spatial_zones") ?: JSONObject()
        val vb = s.optJSONObject("vhi_severity_breakdown") ?: JSONObject()
        val mu = s.optJSONObject("map_urls") ?: JSONObject()
        return DroughtReferenceYearSnapshot(
            year = s.optInt("year", 2025),
            ndviMean = s.optDouble("ndvi_mean", 0.58),
            ndviMin = s.optDouble("ndvi_min", 0.32),
            ndviMax = s.optDouble("ndvi_max", 0.78),
            vciMeanPct = s.optDouble("vci_mean_pct", 52.0),
            lstMeanC = s.optDouble("lst_mean_c", 31.2),
            lstMinC = s.optDouble("lst_min_c", 27.5),
            lstMaxC = s.optDouble("lst_max_c", 35.8),
            tciMeanPct = s.optDouble("tci_mean_pct", 48.0),
            vhiMeanPct = s.optDouble("vhi_mean_pct", 50.0),
            rainfallMm = s.optDouble("rainfall_mm", 1650.0),
            rainfallAnomalyPct = s.optDouble("rainfall_anomaly_pct", -3.2),
            soilMoistureMm = s.optDouble("soil_moisture_mm", 48.0),
            droughtClassCode = s.optString("drought_class_code", "NO_DROUGHT"),
            droughtClassVi = s.optString("drought_class_vi", "Không hạn / Thực vật khỏe (Healthy ≥ 40)"),
            droughtColorHex = s.optString("drought_color_hex", "#1A9850"),
            droughtAffectedPct = s.optDouble("drought_affected_pct", 24.5),
            droughtAffectedHa = s.optDouble("drought_affected_ha", 0.06),
            lstZones = LstSpatialZones(
                desertOrBareSoilC = lz.optDouble("desert_or_bare_soil_c", 35.4),
                vegetatedCanopyC = lz.optDouble("vegetated_canopy_c", 30.8),
                waterAdjacentRiparianC = lz.optDouble("water_adjacent_riparian_c", 28.1),
                bareVsWaterGradientC = lz.optDouble("bare_vs_water_gradient_c", 7.3),
                bareVsCanopyGradientC = lz.optDouble("bare_vs_canopy_gradient_c", 4.6),
            ),
            vhiBreakdown = VhiSeverityBreakdown(
                extremeDroughtPct = vb.optDouble("extreme_drought_pct", 2.0),
                severeDroughtPct = vb.optDouble("severe_drought_pct", 5.5),
                moderateDroughtPct = vb.optDouble("moderate_drought_pct", 8.0),
                mildDroughtPct = vb.optDouble("mild_drought_pct", 9.0),
                noDroughtPct = vb.optDouble("no_drought_pct", 75.5),
                extremeDroughtHa = vb.optDouble("extreme_drought_ha", 0.01),
                severeDroughtHa = vb.optDouble("severe_drought_ha", 0.01),
                moderateDroughtHa = vb.optDouble("moderate_drought_ha", 0.02),
                mildDroughtHa = vb.optDouble("mild_drought_ha", 0.02),
                noDroughtHa = vb.optDouble("no_drought_ha", 0.16),
            ),
            vhiImageUrl = mu.optString("vhi_image_url", ""),
            lstImageUrl = mu.optString("lst_image_url", ""),
            ndviImageUrl = mu.optString("ndvi_image_url", ""),
            spatialFindingVi = s.optString("spatial_finding_vi", ""),
        )
    }

    private fun parseAnnualPoint(p: JSONObject): DroughtAnnualYearPoint = DroughtAnnualYearPoint(
        year = p.optInt("year", 2025),
        isReferenceYear = p.optBoolean("is_reference_year", false),
        ndvi = p.optDouble("ndvi", 0.60),
        vciPct = p.optDouble("vci_pct", 50.0),
        lstC = p.optDouble("lst_c", 31.0),
        tciPct = p.optDouble("tci_pct", 50.0),
        vhiPct = p.optDouble("vhi_pct", 50.0),
        droughtClassCode = p.optString("drought_class_code", "NO_DROUGHT"),
        droughtClassVi = p.optString("drought_class_vi", ""),
        droughtColorHex = p.optString("drought_color_hex", "#1A9850"),
        rainfallMm = p.optDouble("rainfall_mm", 1600.0),
        rainfallAnomalyPct = p.optDouble("rainfall_anomaly_pct", 0.0),
        petMm = p.optDouble("pet_mm", 1500.0),
        climaticWaterDeficitMm = p.optDouble("climatic_water_deficit_mm", -100.0),
        soilMoistureMm = p.optDouble("soil_moisture_mm", 45.0),
        pdsi = p.optDouble("pdsi", 0.0),
        eventNoteVi = p.optString("event_note_vi", ""),
    )

    private fun parseReport(o: JSONObject): DroughtMonitoringReport {
        val bObj = o.optJSONObject("bounds") ?: JSONObject()
        val bounds = GeoBounds(
            west = bObj.optDouble("west", 106.0),
            south = bObj.optDouble("south", 10.0),
            east = bObj.optDouble("east", 106.01),
            north = bObj.optDouble("north", 10.01),
        )
        val cObj = o.optJSONObject("centroid") ?: JSONObject()
        val fg = o.optJSONObject("fao_gaul_boundary") ?: JSONObject()
        val faoGaul = FaoGaulBoundaryInfo(
            adm0Name = fg.optString("adm0_name", "Viet Nam"),
            adm1Name = fg.optString("adm1_name", "Đồng bằng Sông Cửu Long"),
            adm2Name = fg.optString("adm2_name", "Tiểu vùng Nông nghiệp"),
            climateZoneCode = fg.optString("climate_zone_code", "TROPICAL_MONSOON_AW"),
            climateZoneVi = fg.optString("climate_zone_vi", "Nhiệt đới gió mùa"),
            faoGaulDataset = fg.optString("fao_gaul_dataset", "FAO/GAUL/2015/level2"),
            hydrologicalContextVi = fg.optString("hydrological_context_vi", ""),
            isAridBasraProfile = fg.optBoolean("is_arid_basra_profile", false),
        )

        val coreList = o.optJSONArray("six_core_indices")?.let { arr ->
            (0 until arr.length()).map { i ->
                val item = arr.getJSONObject(i)
                DroughtCoreIndexItem(
                    code = item.optString("code"),
                    icon = item.optString("icon"),
                    nameVi = item.optString("name_vi"),
                    nameEn = item.optString("name_en"),
                    formula = item.optString("formula"),
                    satelliteSource = item.optString("satellite_source"),
                    focusYearValue = item.optDouble("focus_year_value", 0.0),
                    unit = item.optString("unit"),
                    range26yText = item.optString("range_26y_text"),
                    roleVi = item.optString("role_vi"),
                )
            }
        } ?: emptyList()

        val focusSnap = parseSnapshot(o.optJSONObject("focus_year_snapshot") ?: JSONObject())
        val refSnaps = o.optJSONArray("reference_years_comparison")?.let { arr ->
            (0 until arr.length()).map { i -> parseSnapshot(arr.getJSONObject(i)) }
        } ?: emptyList()

        val chgObj = o.optJSONObject("ndvi_change_2000_2025") ?: JSONObject()
        val chgClasses = chgObj.optJSONArray("change_classes")?.let { arr ->
            (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                NdviChangeClassItem(
                    classCode = c.optString("class_code"),
                    labelVi = c.optString("label_vi"),
                    thresholdText = c.optString("threshold_text"),
                    areaPct = c.optDouble("area_pct", 0.0),
                    areaHa = c.optDouble("area_ha", 0.0),
                    colorHex = c.optString("color_hex", "#7FBC41"),
                )
            }
        } ?: emptyList()
        val ndviChange = NdviChange2000To2025(
            period = chgObj.optString("period", "2000–2025 (26 năm)"),
            ndvi2000Mean = chgObj.optDouble("ndvi_2000_mean", 0.65),
            ndvi2025Mean = chgObj.optDouble("ndvi_2025_mean", 0.62),
            deltaNdviMean = chgObj.optDouble("delta_ndvi_mean", -0.03),
            deltaNdviMin = chgObj.optDouble("delta_ndvi_min", -0.14),
            deltaNdviMax = chgObj.optDouble("delta_ndvi_max", 0.11),
            netDegradedPct = chgObj.optDouble("net_degraded_pct", 28.0),
            netDegradedHa = chgObj.optDouble("net_degraded_ha", 0.06),
            netRecoveredPct = chgObj.optDouble("net_recovered_pct", 24.0),
            netRecoveredHa = chgObj.optDouble("net_recovered_ha", 0.05),
            stablePct = chgObj.optDouble("stable_pct", 48.0),
            changeImageUrl = chgObj.optString("change_image_url", ""),
            changeClasses = chgClasses,
            interpretationVi = chgObj.optString("interpretation_vi", ""),
        )

        val series = o.optJSONArray("annual_time_series_2000_2025")?.let { arr ->
            (0 until arr.length()).map { i -> parseAnnualPoint(arr.getJSONObject(i)) }
        } ?: emptyList()

        val decObj = o.optJSONObject("decoupling_and_lag_analysis") ?: JSONObject()
        val wetObj = decObj.optJSONObject("wettest_year") ?: JSONObject()
        val dryObj = decObj.optJSONObject("driest_year") ?: JSONObject()
        val hotObj = decObj.optJSONObject("hottest_year") ?: JSONObject()
        val strObj = decObj.optJSONObject("most_stressed_vhi_year") ?: JSONObject()
        val factors = decObj.optJSONArray("driving_factors")?.let { arr ->
            (0 until arr.length()).map { i ->
                val f = arr.getJSONObject(i)
                DecouplingDrivingFactor(
                    factorCode = f.optString("factor_code"),
                    titleVi = f.optString("title_vi"),
                    impactWeightPct = f.optDouble("impact_weight_pct", 20.0),
                    explanationVi = f.optString("explanation_vi"),
                )
            }
        } ?: emptyList()
        val decoupling = DecouplingAndLagAnalysis(
            keyPrincipleVi = decObj.optString("key_principle_vi"),
            pearsonRRainNdviSameYear = decObj.optDouble("pearson_r_rain_ndvi_same_year", 0.42),
            pearsonRRainNdviLag1Y = decObj.optDouble("pearson_r_rain_ndvi_lag_1y", 0.68),
            pearsonRLstNdvi = decObj.optDouble("pearson_r_lst_ndvi", -0.78),
            pearsonRPetVhi = decObj.optDouble("pearson_r_pet_vhi", -0.84),
            wettestYear = wetObj.optInt("year", 2018),
            wettestRainfallMm = wetObj.optDouble("rainfall_mm", 2240.0),
            wettestVciPct = wetObj.optDouble("vci_pct", 68.0),
            wettestLstC = wetObj.optDouble("lst_c", 30.2),
            driestYear = dryObj.optInt("year", 2021),
            driestRainfallMm = dryObj.optDouble("rainfall_mm", 1310.0),
            driestVhiPct = dryObj.optDouble("vhi_pct", 14.5),
            driestLstC = dryObj.optDouble("lst_c", 32.8),
            hottestYear = hotObj.optInt("year", 2021),
            hottestLstC = hotObj.optDouble("lst_c", 32.8),
            mostStressedYear = strObj.optInt("year", 2021),
            mostStressedVhiPct = strObj.optDouble("vhi_pct", 14.5),
            case2018Vs2021SummaryVi = decObj.optString("case_2018_vs_2021_summary_vi"),
            drivingFactors = factors,
        )

        val basraObj = o.optJSONObject("basra_iraq_reference_study") ?: JSONObject()
        val b2018 = basraObj.optJSONObject("wettest_year_2018") ?: JSONObject()
        val b2021 = basraObj.optJSONObject("driest_year_2021") ?: JSONObject()
        val basraSeries = basraObj.optJSONArray("annual_series_2000_2025")?.let { arr ->
            (0 until arr.length()).map { i -> parseAnnualPoint(arr.getJSONObject(i)) }
        } ?: emptyList()
        val basraRef = BasraReferenceStudyInfo(
            regionNameVi = basraObj.optString("region_name_vi", "Tỉnh Basra, Miền Nam Iraq (GISology Benchmark 2000–2025)"),
            admBoundary = basraObj.optString("adm_boundary", "FAO GAUL Level 1: Basra Governorate (ID 1542)"),
            wettest2018RainfallMm = b2018.optDouble("rainfall_mm", 248.6),
            wettest2018Ndvi = b2018.optDouble("ndvi", 0.171),
            wettest2018VhiPct = b2018.optDouble("vhi_pct", 54.8),
            driest2021RainfallMm = b2021.optDouble("rainfall_mm", 54.2),
            driest2021Ndvi = b2021.optDouble("ndvi", 0.114),
            driest2021VhiPct = b2021.optDouble("vhi_pct", 8.8),
            driest2021LstC = b2021.optDouble("lst_c", 41.4),
            summaryInsightVi = basraObj.optString("summary_insight_vi", ""),
            annualSeries2000To2025 = basraSeries,
        )

        val techList = o.optJSONArray("technology_and_datasets")?.let { arr ->
            (0 until arr.length()).map { i ->
                val t = arr.getJSONObject(i)
                DroughtTechDatasetItem(
                    component = t.optString("component"),
                    collections = t.optString("collections"),
                    purposeVi = t.optString("purpose_vi"),
                )
            }
        } ?: emptyList()

        val recs = o.optJSONArray("agronomic_recommendations_vi")?.let { arr ->
            (0 until arr.length()).map { i -> arr.optString(i) }
        } ?: emptyList()

        return DroughtMonitoringReport(
            studyTitleVi = o.optString("study_title_vi", "26 NĂM THEO DÕI HẠN HÁN & SỨC KHỎE THẢM THỰC VẬT (2000–2025)"),
            studySubtitleVi = o.optString("study_subtitle_vi", ""),
            simulated = o.optBoolean("simulated", true),
            studyMode = o.optString("study_mode", "LOCAL_AND_BASRA"),
            focusYear = o.optInt("focus_year", 2025),
            vhiAlpha = o.optDouble("vhi_alpha", 0.50),
            overlayMetric = o.optString("overlay_metric", "VHI"),
            activeOverlayUrl = o.optString("active_overlay_url", ""),
            areaHa = o.optDouble("area_ha", 0.22),
            bounds = bounds,
            centroidLon = cObj.optDouble("lon", 106.35),
            centroidLat = cObj.optDouble("lat", 10.28),
            elevationM = cObj.optDouble("elevation_m", 12.0),
            faoGaul = faoGaul,
            sixCoreIndices = coreList,
            focusYearSnapshot = focusSnap,
            referenceYearsComparison = refSnaps,
            ndviChange2000To2025 = ndviChange,
            annualTimeSeries = series,
            decouplingAnalysis = decoupling,
            basraReference = basraRef,
            technologyAndDatasets = techList,
            agronomicRecommendationsVi = recs,
        )
    }
}
