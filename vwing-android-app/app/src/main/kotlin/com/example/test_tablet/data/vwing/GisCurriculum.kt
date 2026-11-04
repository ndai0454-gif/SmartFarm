package com.example.test_tablet.data.vwing

import org.json.JSONArray
import org.json.JSONObject

data class PcsBufferMetric(
    val distanceM: Double,
    val bufferedAreaM2: Double,
    val bufferedAreaHa: Double,
    val addedCorridorHa: Double,
)

data class VectorAttributeRow(
    val featureId: String,
    val geometryType: String,
    val wgs84Dms: String,
    val utmXM: Double,
    val utmYM: Double,
    val vn2000XM: Double,
    val vn2000YM: Double,
    val edgeLengthM: Double,
)

data class GeorefGcpPoint(
    val gcpId: String,
    val role: String,
    val wgs84Lat: Double,
    val wgs84Lon: Double,
    val pixelU: Double,
    val pixelV: Double,
    val vn2000XM: Double,
    val vn2000YM: Double,
    val residualXM: Double,
    val residualYM: Double,
    val residualTotalM: Double,
)

data class Module123Foundation(
    val gcsCode: String,
    val gcsName: String,
    val centroidLat: Double,
    val centroidLon: Double,
    val centroidDms: String,
    val ellipsoid: String,
    val geodesicAreaHa: Double,
    val naiveUnprojectedErrorPct: Double,
    val gcsUsageNote: String,
    val utmCode: String,
    val utmName: String,
    val utmZone: String,
    val utmCentralMeridianDeg: Int,
    val utmCentroidEastingM: Double,
    val utmCentroidNorthingM: Double,
    val utmPlanarAreaM2: Double,
    val utmPlanarAreaHa: Double,
    val utmPlanarPerimeterM: Double,
    val vn2000Code: String,
    val vn2000Name: String,
    val vn2000DatumShift: String,
    val vn2000CentroidXM: Double,
    val vn2000CentroidYM: Double,
    val vn2000PlanarAreaM2: Double,
    val vn2000PlanarAreaHa: Double,
    val vn2000PlanarPerimeterM: Double,
    val buffers: List<PcsBufferMetric>,
    val vectorPointsCount: Int,
    val vectorLinesCount: Int,
    val vectorPolygonsCount: Int,
    val totalBoundaryLengthM: Double,
    val attributeTable: List<VectorAttributeRow>,
    val rasterRows: Int,
    val rasterCols: Int,
    val rasterCellSizeM: Double,
    val rasterDataTypes: String,
    val clippedMaskRatioPct: Double,
    val affineName: String,
    val affineRmseXM: Double,
    val affineRmseYM: Double,
    val affineRmseTotalM: Double,
    val poly2Name: String,
    val poly2RmseTotalM: Double,
    val mosaicStatus: String,
    val gcps: List<GeorefGcpPoint>,
)

data class RemoteSensingSensorInfo(
    val sensorId: String,
    val name: String,
    val spatialRes: String,
    val revisitDays: String,
    val strengthVi: String,
)

data class BandCombinationInfo(
    val comboId: String,
    val nameVi: String,
    val sentinel2Bands: String,
    val landsat89Bands: String,
    val meanReflectance: String,
    val interpretationVi: String,
)

data class UnsupervisedLulcCluster(
    val clusterId: String,
    val labelVi: String,
    val ndviCentroid: Double,
    val lstMeanC: Double,
    val areaPct: Double,
    val areaHa: Double,
)

data class SupervisedLulcClass(
    val classCode: String,
    val classNameVi: String,
    val algorithm: String,
    val areaPct: Double,
    val areaHa: Double,
    val userAccuracyPct: Double,
    val producerAccuracyPct: Double,
)

data class Module46RemoteSensing(
    val sensors: List<RemoteSensingSensorInfo>,
    val bandCombinations: List<BandCombinationInfo>,
    val unsupervisedMethod: String,
    val unsupervisedClusters: List<UnsupervisedLulcCluster>,
    val supervisedMethod: String,
    val overallAccuracyPct: Double,
    val kappaCoefficient: Double,
    val supervisedClasses: List<SupervisedLulcClass>,
    val ndviMean: Double,
    val ndviMin: Double,
    val ndviMax: Double,
    val ndbiMean: Double,
    val toaRadianceMean: Double,
    val brightnessTempTbC: Double,
    val vegetationFractionPv: Double,
    val surfaceEmissivityEps: Double,
    val lstMeanC: Double,
    val lstMinC: Double,
    val lstMaxC: Double,
    val uhiThresholdC: Double,
    val uhiHotspotAreaPct: Double,
    val uhiIntensityDeltaC: Double,
    val lstDiagnosisVi: String,
)

data class HotspotCellStat(
    val cellId: String,
    val incidentIntensity: Double,
    val giStarZScore: Double,
    val pValue: Double,
    val classificationVi: String,
)

data class Module5SpatialAnalysis(
    val idwMethod: String,
    val stationCount: Int,
    val idwPm25Ugm3: Double,
    val idwWaterPh: Double,
    val idwNoiseDba: Double,
    val idwLoocvRmse: Double,
    val krigingMethod: String,
    val nuggetC0: Double,
    val sillC0PlusC: Double,
    val rangeAM: Double,
    val krigingPm25Ugm3: Double,
    val krigingStdError: Double,
    val vnAqi: Int,
    val pm25Ugm3: Double,
    val pm10Ugm3: Double,
    val airRatingVi: String,
    val airHotspotNoteVi: String,
    val waterPh: Double,
    val waterDoMgl: Double,
    val waterTdsMgl: Double,
    val waterBufferM: Double,
    val waterRatingVi: String,
    val waterBufferNoteVi: String,
    val meanNoiseDba: Double,
    val roadEdgeDba: Double,
    val orchardCoreDba: Double,
    val dsmCanopyAttenuationDb: Double,
    val noiseRatingVi: String,
    val kdeBandwidthM: Double,
    val kdePeakDensityPerHa: Double,
    val getisOrdSummary: String,
    val maxZScore: Double,
    val minZScore: Double,
    val hotspotCells: List<HotspotCellStat>,
)

data class RtkGcpPlanPoint(
    val gcpCode: String,
    val locationRole: String,
    val wgs84Lat: Double,
    val wgs84Lon: Double,
    val vn2000XM: Double,
    val vn2000YM: Double,
    val rtkHorizAccCm: Double,
    val rtkVertAccCm: Double,
)

data class MetashapeStepInfo(
    val stepNo: Int,
    val stepCode: String,
    val titleVi: String,
    val metricBadge: String,
    val detailVi: String,
)

data class Module7DronePhotogrammetry(
    val cameraModel: String,
    val altitudeHM: Double,
    val focalLengthFMm: Double,
    val pixelSizeUm: Double,
    val gsdCmPerPixel: Double,
    val gsdFormulaText: String,
    val targetGradeVi: String,
    val forwardOverlapPct: Double,
    val sideOverlapPct: Double,
    val footprintWM: Double,
    val footprintHM: Double,
    val forwardPhotoStepM: Double,
    val sideLineStepM: Double,
    val flightLines: Int,
    val totalPhotos: Int,
    val estimatedFlightTimeMin: Double,
    val rtkGcps: List<RtkGcpPlanPoint>,
    val metashapeSteps: List<MetashapeStepInfo>,
)

data class GisCurriculumReport(
    val areaHa: Double,
    val m123: Module123Foundation,
    val m46: Module46RemoteSensing,
    val m5: Module5SpatialAnalysis,
    val m7: Module7DronePhotogrammetry,
)

object GisCurriculumClient {
    fun analyze(
        cfg: SatelliteConfig,
        ring: List<GeoPoint>,
        bufferM: Double = 300.0,
        uavAltitudeM: Double = 60.0,
        forwardOverlapPct: Double = 80.0,
        sideOverlapPct: Double = 70.0,
    ): GisCurriculumReport {
        val pts = JSONArray().also { a ->
            Geo.open(ring).let { it + it.first() }.forEach {
                a.put(JSONArray().put(it.lng).put(it.lat))
            }
        }
        val body = JSONObject()
            .put("region", JSONObject().put("ring", pts))
            .put("buffer_m", bufferM)
            .put("uav_altitude_m", uavAltitudeM)
            .put("forward_overlap_pct", forwardOverlapPct)
            .put("side_overlap_pct", sideOverlapPct)
        val o = GisClient.call(cfg, "/v1/gis-curriculum/analyze", body)
        return parseReport(o)
    }

    private fun parseReport(o: JSONObject): GisCurriculumReport {
        val m1 = o.getJSONObject("module_1_2_3_foundation")
        val crs = m1.getJSONObject("crs_projection")
        val gcs = crs.getJSONObject("gcs")
        val utm = crs.getJSONObject("pcs_utm")
        val vn = crs.getJSONObject("pcs_vn2000")
        val bufs = vn.optJSONArray("buffers")?.let { arr ->
            (0 until arr.length()).map { i ->
                val b = arr.getJSONObject(i)
                PcsBufferMetric(
                    distanceM = b.optDouble("distance_m", 10.0),
                    bufferedAreaM2 = b.optDouble("buffered_area_m2", 0.0),
                    bufferedAreaHa = b.optDouble("buffered_area_ha", 0.0),
                    addedCorridorHa = b.optDouble("added_corridor_ha", 0.0),
                )
            }
        } ?: emptyList()

        val sm = m1.getJSONObject("spatial_models")
        val vs = sm.getJSONObject("vector_summary")
        val attrList = vs.optJSONArray("attribute_table")?.let { arr ->
            (0 until arr.length()).map { i ->
                val r = arr.getJSONObject(i)
                VectorAttributeRow(
                    featureId = r.optString("feature_id"),
                    geometryType = r.optString("geometry_type"),
                    wgs84Dms = r.optString("wgs84_dms"),
                    utmXM = r.optDouble("utm_x_m", 0.0),
                    utmYM = r.optDouble("utm_y_m", 0.0),
                    vn2000XM = r.optDouble("vn2000_x_m", 0.0),
                    vn2000YM = r.optDouble("vn2000_y_m", 0.0),
                    edgeLengthM = r.optDouble("edge_length_m", 0.0),
                )
            }
        } ?: emptyList()

        val rs = sm.getJSONObject("raster_summary")
        val gr = m1.getJSONObject("georeferencing")
        val aff = gr.getJSONObject("method_affine_order1")
        val poly2 = gr.getJSONObject("method_polynomial_order2")
        val mos = gr.getJSONObject("mosaicking_clipping")
        val gcpList = mos.optJSONArray("gcps")?.let { arr ->
            (0 until arr.length()).map { i ->
                val g = arr.getJSONObject(i)
                GeorefGcpPoint(
                    gcpId = g.optString("gcp_id"),
                    role = g.optString("role"),
                    wgs84Lat = g.optDouble("wgs84_lat", 0.0),
                    wgs84Lon = g.optDouble("wgs84_lon", 0.0),
                    pixelU = g.optDouble("pixel_u", 0.0),
                    pixelV = g.optDouble("pixel_v", 0.0),
                    vn2000XM = g.optDouble("vn2000_x_m", 0.0),
                    vn2000YM = g.optDouble("vn2000_y_m", 0.0),
                    residualXM = g.optDouble("residual_x_m", 0.0),
                    residualYM = g.optDouble("residual_y_m", 0.0),
                    residualTotalM = g.optDouble("residual_total_m", 0.0),
                )
            }
        } ?: emptyList()

        val mod123 = Module123Foundation(
            gcsCode = gcs.optString("code", "EPSG:4326"),
            gcsName = gcs.optString("name"),
            centroidLat = gcs.optDouble("centroid_lat", 0.0),
            centroidLon = gcs.optDouble("centroid_lon", 0.0),
            centroidDms = gcs.optString("centroid_dms"),
            ellipsoid = gcs.optString("ellipsoid"),
            geodesicAreaHa = gcs.optDouble("geodesic_area_ha", 0.0),
            naiveUnprojectedErrorPct = gcs.optDouble("naive_unprojected_error_pct", 0.0),
            gcsUsageNote = gcs.optString("usage_note"),
            utmCode = utm.optString("code", "EPSG:32648"),
            utmName = utm.optString("name"),
            utmZone = utm.optString("zone", "48N"),
            utmCentralMeridianDeg = utm.optInt("central_meridian_deg", 105),
            utmCentroidEastingM = utm.optDouble("centroid_easting_m", 0.0),
            utmCentroidNorthingM = utm.optDouble("centroid_northing_m", 0.0),
            utmPlanarAreaM2 = utm.optDouble("planar_area_m2", 0.0),
            utmPlanarAreaHa = utm.optDouble("planar_area_ha", 0.0),
            utmPlanarPerimeterM = utm.optDouble("planar_perimeter_m", 0.0),
            vn2000Code = vn.optString("code", "EPSG:3405"),
            vn2000Name = vn.optString("name"),
            vn2000DatumShift = vn.optString("datum_shift_helmert7"),
            vn2000CentroidXM = vn.optDouble("centroid_x_m", 0.0),
            vn2000CentroidYM = vn.optDouble("centroid_y_m", 0.0),
            vn2000PlanarAreaM2 = vn.optDouble("planar_area_m2", 0.0),
            vn2000PlanarAreaHa = vn.optDouble("planar_area_ha", 0.0),
            vn2000PlanarPerimeterM = vn.optDouble("planar_perimeter_m", 0.0),
            buffers = bufs,
            vectorPointsCount = vs.optInt("points_count", 0),
            vectorLinesCount = vs.optInt("lines_count", 0),
            vectorPolygonsCount = vs.optInt("polygons_count", 1),
            totalBoundaryLengthM = vs.optDouble("total_boundary_length_m", 0.0),
            attributeTable = attrList,
            rasterRows = rs.optInt("grid_rows", 32),
            rasterCols = rs.optInt("grid_cols", 32),
            rasterCellSizeM = rs.optDouble("cell_size_m", 30.0),
            rasterDataTypes = rs.optString("data_types"),
            clippedMaskRatioPct = rs.optDouble("clipped_mask_ratio_pct", 0.0),
            affineName = aff.optString("name"),
            affineRmseXM = aff.optDouble("rmse_x_m", 0.0),
            affineRmseYM = aff.optDouble("rmse_y_m", 0.0),
            affineRmseTotalM = aff.optDouble("rmse_total_m", 0.0),
            poly2Name = poly2.optString("name"),
            poly2RmseTotalM = poly2.optDouble("rmse_total_m", 0.0),
            mosaicStatus = mos.optString("status"),
            gcps = gcpList,
        )

        val m4 = o.getJSONObject("module_4_6_remote_sensing")
        val sensors = m4.optJSONArray("sensors")?.let { arr ->
            (0 until arr.length()).map { i ->
                val s = arr.getJSONObject(i)
                RemoteSensingSensorInfo(
                    sensorId = s.optString("sensor_id"),
                    name = s.optString("name"),
                    spatialRes = s.optString("spatial_res"),
                    revisitDays = s.optString("revisit_days"),
                    strengthVi = s.optString("strength_vi"),
                )
            }
        } ?: emptyList()

        val combos = m4.optJSONArray("band_combinations")?.let { arr ->
            (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                BandCombinationInfo(
                    comboId = c.optString("combo_id"),
                    nameVi = c.optString("name_vi"),
                    sentinel2Bands = c.optString("sentinel2_bands"),
                    landsat89Bands = c.optString("landsat89_bands"),
                    meanReflectance = c.optString("mean_rgb_reflectance"),
                    interpretationVi = c.optString("interpretation_vi"),
                )
            }
        } ?: emptyList()

        val lulc = m4.getJSONObject("lulc_classification")
        val unsup = lulc.optJSONArray("unsupervised_clusters")?.let { arr ->
            (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                UnsupervisedLulcCluster(
                    clusterId = c.optString("cluster_id"),
                    labelVi = c.optString("label_vi"),
                    ndviCentroid = c.optDouble("ndvi_centroid", 0.0),
                    lstMeanC = c.optDouble("lst_mean_c", 0.0),
                    areaPct = c.optDouble("area_pct", 0.0),
                    areaHa = c.optDouble("area_ha", 0.0),
                )
            }
        } ?: emptyList()

        val sup = lulc.optJSONArray("supervised_classes")?.let { arr ->
            (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                SupervisedLulcClass(
                    classCode = c.optString("class_code"),
                    classNameVi = c.optString("class_name_vi"),
                    algorithm = c.optString("algorithm"),
                    areaPct = c.optDouble("area_pct", 0.0),
                    areaHa = c.optDouble("area_ha", 0.0),
                    userAccuracyPct = c.optDouble("user_accuracy_pct", 0.0),
                    producerAccuracyPct = c.optDouble("producer_accuracy_pct", 0.0),
                )
            }
        } ?: emptyList()

        val lst = m4.getJSONObject("lst_uhi")
        val mod46 = Module46RemoteSensing(
            sensors = sensors,
            bandCombinations = combos,
            unsupervisedMethod = lulc.optString("unsupervised_method"),
            unsupervisedClusters = unsup,
            supervisedMethod = lulc.optString("supervised_method"),
            overallAccuracyPct = lulc.optDouble("overall_accuracy_pct", 91.4),
            kappaCoefficient = lulc.optDouble("kappa_coefficient", 0.88),
            supervisedClasses = sup,
            ndviMean = lst.optDouble("ndvi_mean", 0.0),
            ndviMin = lst.optDouble("ndvi_min", 0.0),
            ndviMax = lst.optDouble("ndvi_max", 0.0),
            ndbiMean = lst.optDouble("ndbi_mean", 0.0),
            toaRadianceMean = lst.optDouble("toa_radiance_mean", 0.0),
            brightnessTempTbC = lst.optDouble("brightness_temp_tb_c", 0.0),
            vegetationFractionPv = lst.optDouble("vegetation_fraction_pv", 0.0),
            surfaceEmissivityEps = lst.optDouble("surface_emissivity_eps", 0.988),
            lstMeanC = lst.optDouble("lst_mean_c", 25.0),
            lstMinC = lst.optDouble("lst_min_c", 23.0),
            lstMaxC = lst.optDouble("lst_max_c", 28.0),
            uhiThresholdC = lst.optDouble("uhi_threshold_c", 26.5),
            uhiHotspotAreaPct = lst.optDouble("uhi_hotspot_area_pct", 0.0),
            uhiIntensityDeltaC = lst.optDouble("uhi_intensity_delta_c", 0.0),
            lstDiagnosisVi = lst.optString("diagnosis_vi"),
        )

        val m5 = o.getJSONObject("module_5_spatial_analysis")
        val interp = m5.getJSONObject("interpolation")
        val idw = interp.getJSONObject("idw")
        val krig = interp.getJSONObject("kriging")
        val pol = m5.getJSONObject("pollution_maps")
        val air = pol.getJSONObject("air_quality")
        val wat = pol.getJSONObject("water_quality")
        val noi = pol.getJSONObject("noise_mapping")
        val hs = m5.getJSONObject("hotspot_analysis")
        val hsCells = hs.optJSONArray("cells")?.let { arr ->
            (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                HotspotCellStat(
                    cellId = c.optString("cell_id"),
                    incidentIntensity = c.optDouble("incident_intensity", 0.0),
                    giStarZScore = c.optDouble("gi_star_z_score", 0.0),
                    pValue = c.optDouble("p_value", 1.0),
                    classificationVi = c.optString("classification_vi"),
                )
            }
        } ?: emptyList()

        val mod5 = Module5SpatialAnalysis(
            idwMethod = idw.optString("method"),
            stationCount = idw.optInt("station_count", 8),
            idwPm25Ugm3 = idw.optDouble("pm25_estimate_ugm3", 16.0),
            idwWaterPh = idw.optDouble("water_ph_estimate", 5.65),
            idwNoiseDba = idw.optDouble("noise_estimate_dba", 49.0),
            idwLoocvRmse = idw.optDouble("loocv_rmse", 1.14),
            krigingMethod = krig.optString("method"),
            nuggetC0 = krig.optDouble("nugget_c0", 0.45),
            sillC0PlusC = krig.optDouble("sill_c0_plus_c", 5.25),
            rangeAM = krig.optDouble("range_a_m", 240.0),
            krigingPm25Ugm3 = krig.optDouble("pm25_prediction_ugm3", 17.0),
            krigingStdError = krig.optDouble("prediction_std_error", 1.18),
            vnAqi = air.optInt("vn_aqi", 41),
            pm25Ugm3 = air.optDouble("pm25_ugm3", 17.0),
            pm10Ugm3 = air.optDouble("pm10_ugm3", 30.0),
            airRatingVi = air.optString("rating_vi"),
            airHotspotNoteVi = air.optString("hotspot_note_vi"),
            waterPh = wat.optDouble("ph", 5.65),
            waterDoMgl = wat.optDouble("dissolved_oxygen_do_mgl", 6.8),
            waterTdsMgl = wat.optDouble("tds_mgl", 145.0),
            waterBufferM = wat.optDouble("buffer_corridor_m", 30.0),
            waterRatingVi = wat.optString("rating_vi"),
            waterBufferNoteVi = wat.optString("buffer_analysis_vi"),
            meanNoiseDba = noi.optDouble("mean_noise_dba", 50.0),
            roadEdgeDba = noi.optDouble("road_edge_dba", 57.5),
            orchardCoreDba = noi.optDouble("orchard_core_dba", 43.8),
            dsmCanopyAttenuationDb = noi.optDouble("dsm_canopy_attenuation_db", 5.8),
            noiseRatingVi = noi.optString("rating_vi"),
            kdeBandwidthM = hs.optDouble("kde_bandwidth_m", 45.0),
            kdePeakDensityPerHa = hs.optDouble("kde_peak_density_per_ha", 3.4),
            getisOrdSummary = hs.optString("getis_ord_summary"),
            maxZScore = hs.optDouble("max_z_score", 2.15),
            minZScore = hs.optDouble("min_z_score", -1.42),
            hotspotCells = hsCells,
        )

        val m7 = o.getJSONObject("module_7_drone_photogrammetry")
        val fp = m7.getJSONObject("flight_plan")
        val rtkList = fp.optJSONArray("rtk_gcps")?.let { arr ->
            (0 until arr.length()).map { i ->
                val g = arr.getJSONObject(i)
                RtkGcpPlanPoint(
                    gcpCode = g.optString("gcp_code"),
                    locationRole = g.optString("location_role"),
                    wgs84Lat = g.optDouble("wgs84_lat", 0.0),
                    wgs84Lon = g.optDouble("wgs84_lon", 0.0),
                    vn2000XM = g.optDouble("vn2000_x_m", 0.0),
                    vn2000YM = g.optDouble("vn2000_y_m", 0.0),
                    rtkHorizAccCm = g.optDouble("rtk_horiz_acc_cm", 1.2),
                    rtkVertAccCm = g.optDouble("rtk_vert_acc_cm", 1.8),
                )
            }
        } ?: emptyList()

        val steps = m7.optJSONArray("metashape_pipeline")?.let { arr ->
            (0 until arr.length()).map { i ->
                val s = arr.getJSONObject(i)
                MetashapeStepInfo(
                    stepNo = s.optInt("step_no", i + 1),
                    stepCode = s.optString("step_code"),
                    titleVi = s.optString("title_vi"),
                    metricBadge = s.optString("metric_badge"),
                    detailVi = s.optString("detail_vi"),
                )
            }
        } ?: emptyList()

        val mod7 = Module7DronePhotogrammetry(
            cameraModel = fp.optString("camera_model"),
            altitudeHM = fp.optDouble("altitude_h_m", 60.0),
            focalLengthFMm = fp.optDouble("focal_length_f_mm", 12.29),
            pixelSizeUm = fp.optDouble("pixel_size_um", 3.30),
            gsdCmPerPixel = fp.optDouble("gsd_cm_per_pixel", 1.61),
            gsdFormulaText = fp.optString("gsd_formula_text"),
            targetGradeVi = fp.optString("target_grade_vi"),
            forwardOverlapPct = fp.optDouble("forward_overlap_pct", 80.0),
            sideOverlapPct = fp.optDouble("side_overlap_pct", 70.0),
            footprintWM = fp.optDouble("footprint_w_m", 85.0),
            footprintHM = fp.optDouble("footprint_h_m", 63.7),
            forwardPhotoStepM = fp.optDouble("forward_photo_step_m", 12.7),
            sideLineStepM = fp.optDouble("side_line_step_m", 25.5),
            flightLines = fp.optInt("flight_lines", 4),
            totalPhotos = fp.optInt("total_photos", 36),
            estimatedFlightTimeMin = fp.optDouble("estimated_flight_time_min", 4.5),
            rtkGcps = rtkList,
            metashapeSteps = steps,
        )

        return GisCurriculumReport(
            areaHa = o.optDouble("area_ha", 0.22),
            m123 = mod123,
            m46 = mod46,
            m5 = mod5,
            m7 = mod7,
        )
    }
}
