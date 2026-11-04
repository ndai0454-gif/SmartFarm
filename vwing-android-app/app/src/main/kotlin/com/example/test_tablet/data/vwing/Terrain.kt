package com.example.test_tablet.data.vwing

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.util.PriorityQueue
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Danh mục 12 nguồn dữ liệu DEM miễn phí toàn cầu tích hợp qua 8 kho mã nguồn mở:
 * `elevatr` (Python & R), `srtm.py`, `asf_search`, `OT_3DEP_Workflows`, `eodag`, `pyDEM`, `demcoreg`.
 */
enum class DemSourceKey(
    val id: String,
    val shortLabel: String,
    val fullTitle: String,
    val resolutionLabel: String,
    val coverage: String,
    val url: String,
    val repoTool: String,
) {
    NASA_SRTM_30M(
        "NASA_SRTM_30M",
        "NASA SRTM 30m",
        "1. NASA EARTHDATA – SRTM (30 m & 90 m)",
        "30 m & 90 m",
        "Toàn cầu (60°N – 56°S)",
        "https://earthdata.nasa.gov",
        "srtm.py + elevatr",
    ),
    USGS_EARTHEXPLORER(
        "USGS_EARTHEXPLORER",
        "USGS Earth Explorer",
        "2. USGS Earth Explorer (SRTM / ASTER / ALOS · 1–90 m)",
        "1 m – 90 m",
        "Toàn cầu",
        "https://earthexplorer.usgs.gov/",
        "eodag + OT_3DEP_Workflows",
    ),
    COPERNICUS_GLO30(
        "COPERNICUS_GLO30",
        "Copernicus GLO-30",
        "3. Copernicus DEM (EU-DEM 25m / GLO-30 30m)",
        "25 m – 30 m",
        "Toàn cầu & Châu Âu",
        "https://spacedata.copernicus.eu/collections/copernicus-digital-elevation-model",
        "elevatr + eodag",
    ),
    CGIAR_SRTM_90M(
        "CGIAR_SRTM_90M",
        "CGIAR-CSI SRTM 90m",
        "4. CGIAR-CSI SRTM v4.1 (Đã vá lỗ trống/voids · 90 m)",
        "90 m",
        "Toàn cầu (60°N – 56°S)",
        "https://csi.cgiar.org/",
        "srtm.py + geodata",
    ),
    JAXA_AW3D30(
        "JAXA_AW3D30",
        "JAXA AW3D30 (30m)",
        "5. JAXA – ALOS Global DEM (AW3D30 · 30 m)",
        "30 m",
        "Toàn cầu",
        "https://www.eorc.jaxa.jp/ALOS/en/aw3d30/index.htm",
        "eodag + demcoreg",
    ),
    OPENTOPOGRAPHY(
        "OPENTOPOGRAPHY",
        "OpenTopography COG",
        "6. OpenTopography (LiDAR, SRTM, ALOS, Copernicus · 1–30 m)",
        "1 m – 30 m",
        "Toàn cầu",
        "https://opentopography.org/",
        "OT_3DEP_Workflows + elevatr",
    ),
    ASF_ALOS_PALSAR_12_5M(
        "ASF_ALOS_PALSAR_12_5M",
        "ASF ALOS PALSAR 12,5m",
        "7. ASF Data Search – ALOS PALSAR RTC DEM (12.5 m)",
        "12.5 m",
        "Toàn cầu",
        "https://search.asf.alaska.edu/",
        "asf_search",
    ),
    VIEWFINDER_SRTM(
        "VIEWFINDER_SRTM",
        "Viewfinder SRTM",
        "8. Viewfinder Panoramas – SRTM (90 m)",
        "90 m",
        "Toàn cầu (60°N – 56°S)",
        "https://viewfinderpanoramas.org/",
        "srtm.py",
    ),
    ASTER_GDEM_30M(
        "ASTER_GDEM_30M",
        "ASTER GDEM v3 (30m)",
        "9. ASTER GDEM (JAPAN / METI / NASA · 30 m)",
        "30 m",
        "Toàn cầu",
        "https://www.jspacesystems.or.jp/ersdac/GDEM/E/",
        "demcoreg + eodag",
    ),
    MERIT_DEM_90M(
        "MERIT_DEM_90M",
        "MERIT DEM Thủy văn",
        "10. MERIT DEM (Multi-Error-Removed Improved-Terrain · ~90 m)",
        "3 arc-sec (~90 m)",
        "Toàn cầu",
        "https://www.merit-dem.org/",
        "pyDEM + demcoreg",
    ),
    FABDEM_30M(
        "FABDEM_30M",
        "FABDEM 30m (Đất trần)",
        "11. FABDEM (Forest And Buildings removed Bare-Earth · 30 m)",
        "30 m",
        "Toàn cầu",
        "https://www.fabdem.com/",
        "pyDEM + demcoreg",
    ),
    TANDEMX_90M(
        "TANDEMX_90M",
        "TanDEM-X DLR",
        "12. TanDEM-X (DLR · 12 m / bản mở 90 m)",
        "12 m / 90 m",
        "Toàn cầu",
        "https://scihub.copernicus.eu/",
        "demcoreg + eodag",
    );

    companion object {
        fun fromId(id: String): DemSourceKey = entries.firstOrNull { it.id == id } ?: COPERNICUS_GLO30
    }
}

data class PyDemHydrology(
    val elevMinM: Double,
    val elevMaxM: Double,
    val elevMeanM: Double,
    val elevStdM: Double,
    val reliefM: Double,
    val bareEarthMeanM: Double,
    val canopyHeightMeanM: Double,
    val slopeMeanDeg: Double,
    val slopeMaxDeg: Double,
    val slopeMeanPct: Double,
    val aspectMeanDeg: Double,
    val aspectCardinalVi: String,
    val catchmentMeanM2: Double,
    val catchmentMaxM2: Double,
    val catchmentMaxHa: Double,
    val twiMean: Double,
    val twiMax: Double,
    val twiP90: Double,
    val highWetnessAreaPct: Double,
    val handMeanM: Double,
    val handMinM: Double,
    val triMeanM: Double,
    val tpiMeanM: Double,
    val terrainClassVi: String,
    val slopeGrid: DoubleArray,
    val twiGrid: DoubleArray,
    val catchmentGridM2: DoubleArray,
)

data class DemCoregResult(
    val referenceSource: String,
    val secondarySource: String,
    val referenceLabel: String,
    val secondaryLabel: String,
    val dxEastM: Double,
    val dyNorthM: Double,
    val dzBiasM: Double,
    val rmseBeforeM: Double,
    val rmseAfterM: Double,
    val nmadBeforeM: Double,
    val nmadAfterM: Double,
    val improvementPct: Double,
    val summaryVi: String,
)

data class DemSampledPoint(
    val label: String,
    val lat: Double,
    val lon: Double,
    val zPrimaryM: Double?,
    val zReferenceM: Double?,
    val zBareEarthM: Double?,
    val canopyOrBuildingHeightM: Double,
)

data class DemSourceComparison(
    val id: String,
    val shortLabel: String,
    val name: String,
    val resolutionLabel: String,
    val coverage: String,
    val demType: String,
    val repoTool: String,
    val url: String,
    val meanElevationM: Double,
)

data class DemSatelliteGranule(
    val provider: String,
    val sceneId: String,
    val platform: String,
    val resolutionM: Double,
    val acquisitionDate: String,
    val downloadUrl: String,
    val productType: String,
)

class DemHubReport(
    val bounds: GeoBounds,
    val rows: Int,
    val cols: Int,
    val cellM: Double,
    val primarySource: DemSourceKey,
    val referenceSource: DemSourceKey,
    val simulated: Boolean,
    val hydrology: PyDemHydrology,
    val coregistration: DemCoregResult,
    val sampledPoints: List<DemSampledPoint>,
    val sourceComparisons: List<DemSourceComparison>,
    val satelliteGranules: List<DemSatelliteGranule>,
    val geotiffFilename: String,
    val geotiffSizeBytes: Int,
    val geotiffBytes: ByteArray,
    val warnings: List<String>,
)

/**
 * Phân tích địa hình trên độ cao số (DEM, hỗ trợ 12 nguồn toàn cầu lấy qua máy chủ vệ tinh):
 * tầm nhìn (viewshed), nhạy cảm ngập lụt (TWI + HAND), thủy văn `pyDEM` và đồng đăng ký `demcoreg`.
 */
class Dem(
    val grid: ThematicGrid,
    val z: DoubleArray,
    val source: String,
    val simulated: Boolean,
    val warnings: List<String>,
    val caution: String,
    val sourceId: String = "NASA_SRTM_30M",
    val resolutionLabel: String = "30 m",
    val repoTool: String = "srtm.py + elevatr",
) {
    val rows get() = grid.rows
    val cols get() = grid.cols
    val bounds get() = grid.bounds
    val cellM get() = sqrt(grid.cellWm * grid.cellHm)
    fun at(r: Int, c: Int) = z[r * cols + c]
    val validCount get() = z.count { !it.isNaN() }
    fun elevationAt(p: GeoPoint): Double? = grid.cellOf(p)?.let { z[it] }?.takeIf { !it.isNaN() }
}

class ViewshedResult(val dem: Dem, val visible: BooleanArray, val observerCell: Int, val observerZ: Double, val radiusM: Double,
                     val analysedCells: Int, val visibleCells: Int)

enum class FloodClass(val label: String) { LOW("Thấp"), MEDIUM("Vừa"), HIGH("Cao"), VERY_HIGH("Rất cao") }

class FloodResult(val dem: Dem, val filledZ: DoubleArray, val acc: IntArray, val slopeTan: DoubleArray, val twi: DoubleArray, val hand: DoubleArray,
                  val channel: BooleanArray, val score: DoubleArray, val cls: Array<FloodClass?>, val channelAreaHa: Double, val filledCells: Int)

object Terrain {
    private const val EARTH_R = 6_371_000.0
    const val REFRACTION_K = 0.13          // hệ số khúc xạ khí quyển chuẩn
    const val HAND_CAP_M = 10.0            // HAND ≥ 10 m coi như không còn nguy cơ từ dòng chảy gần
    const val MIN_SLOPE_TAN = 0.005        // chặn tan β để TWI không vọt lên vô cực ở ô phẳng

    @android.annotation.SuppressLint("NewApi")
    private fun decodeB64(b64: String): ByteArray =
        runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull()
            ?: java.util.Base64.getDecoder().decode(b64)

    // ---------- tải DEM ----------

    /** Dựng DEM từ phản hồi /v1/terrain/dem. `ring` (nếu có) dùng làm mặt nạ vùng phân tích để thống kê. */
    fun parseDem(o: JSONObject, ring: List<GeoPoint>?): Dem {
        val b = o.getJSONObject("bounds").let { GeoBounds(it.getDouble("south"), it.getDouble("west"), it.getDouble("north"), it.getDouble("east")) }
        val rows = o.getInt("rows"); val cols = o.getInt("cols")
        require(b.valid && rows >= 3 && cols >= 3 && rows.toLong() * cols <= 400_000) { "Lưới độ cao không hợp lệ." }
        val a = o.getJSONArray("values")
        require(a.length() == rows * cols) { "Số ô độ cao không khớp kích thước lưới." }
        val z = DoubleArray(a.length()) { i -> if (a.isNull(i)) Double.NaN else a.getDouble(i).takeIf { it.isFinite() } ?: Double.NaN }
        val w = Geo.distanceM(GeoPoint(b.center.lat, b.west), GeoPoint(b.center.lat, b.east))
        val h = Geo.distanceM(GeoPoint(b.south, b.center.lng), GeoPoint(b.north, b.center.lng))
        val poly = ring?.let { Geo.open(it) }?.takeIf { it.size >= 3 }
        val dLat = (b.north - b.south) / rows; val dLng = (b.east - b.west) / cols
        val inside = BooleanArray(rows * cols) { i ->
            poly != null && Geo.pointInPolygon(GeoPoint(b.north - (i / cols + .5) * dLat, b.west + (i % cols + .5) * dLng), poly)
        }
        val warns = o.optJSONArray("warnings")?.let { w2 -> (0 until w2.length()).map { w2.getString(it) } } ?: emptyList()
        return Dem(
            ThematicGrid(b, cols, rows, w, h, inside),
            z,
            o.optString("source"),
            o.optBoolean("simulated"),
            warns,
            o.optString("caution"),
            o.optString("source_id", "NASA_SRTM_30M"),
            o.optString("resolution_label", "30 m"),
            o.optString("repo_tool", "srtm.py + elevatr"),
        )
    }

    fun fetchDem(cfg: SatelliteConfig, ring: List<GeoPoint>, bufferM: Int, sourceId: String = "NASA_SRTM_30M"): Dem {
        val pts = JSONArray().also { a -> Geo.open(ring).let { it + it.first() }.forEach { a.put(JSONArray().put(it.lng).put(it.lat)) } }
        val body = JSONObject()
            .put("region", JSONObject().put("ring", pts))
            .put("buffer_m", bufferM)
            .put("source_id", sourceId)
        val o = GisClient.call(cfg, "/v1/terrain/dem", body)
        return try { parseDem(o, ring) } catch (e: Exception) { throw GisException("Dữ liệu độ cao nhận về không đọc được: ${e.message}") }
    }

    /**
     * Gọi `/v1/terrain/dem/hub` để thực thi đồng thời:
     * - Trích xuất DEM theo Bounding Box & đóng gói GeoTIFF (`elevatr` + `OT_3DEP_Workflows`)
     * - Nội suy cao độ điểm & tách chiều cao tán cây (`srtm.py` + `FABDEM`)
     * - Phân tích địa hình & thủy văn song song (`pyDEM`: Slope, Aspect, Catchment Area, TWI, HAND, TRI, TPI)
     * - Đồng đăng ký 3D Nuth & Kääb (`demcoreg`: ΔX, ΔY, ΔZ, RMSE, NMAD) và so sánh 12 nguồn DEM
     * - Tìm kiếm cảnh vệ tinh SAR/DEM thực tế (`asf_search` ALOS PALSAR 12.5m + `eodag` Copernicus/USGS)
     */
    fun fetchDemHub(
        cfg: SatelliteConfig,
        ring: List<GeoPoint>,
        bufferM: Int = 500,
        primarySource: DemSourceKey = DemSourceKey.COPERNICUS_GLO30,
        referenceSource: DemSourceKey = DemSourceKey.NASA_SRTM_30M,
    ): DemHubReport {
        val pts = JSONArray().also { a -> Geo.open(ring).let { it + it.first() }.forEach { a.put(JSONArray().put(it.lng).put(it.lat)) } }
        val body = JSONObject()
            .put("region", JSONObject().put("ring", pts))
            .put("buffer_m", bufferM)
            .put("primary_source", primarySource.id)
            .put("reference_source", referenceSource.id)
        val o = GisClient.call(cfg, "/v1/terrain/dem/hub", body)
        return try {
            parseDemHub(o)
        } catch (e: Exception) {
            throw GisException("Không đọc được kết quả phân tích DEM Hub: ${e.message}")
        }
    }

    fun parseDemHub(o: JSONObject): DemHubReport {
        val b = o.getJSONObject("bounds").let { GeoBounds(it.getDouble("south"), it.getDouble("west"), it.getDouble("north"), it.getDouble("east")) }
        val rows = o.optInt("rows", 20)
        val cols = o.optInt("cols", 20)
        val cellM = o.optDouble("cell_m", 30.0)
        val primId = o.optJSONObject("primary_source")?.optString("id") ?: "COPERNICUS_GLO30"
        val refId = o.optJSONObject("reference_source")?.optString("id") ?: "NASA_SRTM_30M"

        val h = o.getJSONObject("hydrology")
        fun dblArr(arr: JSONArray?): DoubleArray =
            if (arr == null) DoubleArray(0) else DoubleArray(arr.length()) { i -> arr.optDouble(i, Double.NaN) }

        val hydro = PyDemHydrology(
            elevMinM = h.optDouble("elev_min_m", 0.0),
            elevMaxM = h.optDouble("elev_max_m", 0.0),
            elevMeanM = h.optDouble("elev_mean_m", 0.0),
            elevStdM = h.optDouble("elev_std_m", 0.0),
            reliefM = h.optDouble("relief_m", 0.0),
            bareEarthMeanM = h.optDouble("bare_earth_mean_m", h.optDouble("elev_mean_m", 0.0)),
            canopyHeightMeanM = h.optDouble("canopy_height_mean_m", 0.0),
            slopeMeanDeg = h.optDouble("slope_mean_deg", 0.0),
            slopeMaxDeg = h.optDouble("slope_max_deg", 0.0),
            slopeMeanPct = h.optDouble("slope_mean_pct", 0.0),
            aspectMeanDeg = h.optDouble("aspect_mean_deg", 0.0),
            aspectCardinalVi = h.optString("aspect_cardinal_vi", "Đông-Nam"),
            catchmentMeanM2 = h.optDouble("catchment_mean_m2", 0.0),
            catchmentMaxM2 = h.optDouble("catchment_max_m2", 0.0),
            catchmentMaxHa = h.optDouble("catchment_max_ha", 0.0),
            twiMean = h.optDouble("twi_mean", 0.0),
            twiMax = h.optDouble("twi_max", 0.0),
            twiP90 = h.optDouble("twi_p90", 0.0),
            highWetnessAreaPct = h.optDouble("high_wetness_area_pct", 0.0),
            handMeanM = h.optDouble("hand_mean_m", 0.0),
            handMinM = h.optDouble("hand_min_m", 0.0),
            triMeanM = h.optDouble("tri_mean_m", 0.0),
            tpiMeanM = h.optDouble("tpi_mean_m", 0.0),
            terrainClassVi = h.optString("terrain_class_vi", ""),
            slopeGrid = dblArr(h.optJSONArray("slope_grid")),
            twiGrid = dblArr(h.optJSONArray("twi_grid")),
            catchmentGridM2 = dblArr(h.optJSONArray("catchment_grid_m2")),
        )

        val cg = o.getJSONObject("coregistration")
        val coreg = DemCoregResult(
            referenceSource = cg.optString("reference_source", refId),
            secondarySource = cg.optString("secondary_source", primId),
            referenceLabel = cg.optString("reference_label", refId),
            secondaryLabel = cg.optString("secondary_label", primId),
            dxEastM = cg.optDouble("dx_east_m", 0.0),
            dyNorthM = cg.optDouble("dy_north_m", 0.0),
            dzBiasM = cg.optDouble("dz_bias_m", 0.0),
            rmseBeforeM = cg.optDouble("rmse_before_m", 0.0),
            rmseAfterM = cg.optDouble("rmse_after_m", 0.0),
            nmadBeforeM = cg.optDouble("nmad_before_m", 0.0),
            nmadAfterM = cg.optDouble("nmad_after_m", 0.0),
            improvementPct = cg.optDouble("improvement_pct", 0.0),
            summaryVi = cg.optString("summary_vi", ""),
        )

        val spArr = o.optJSONArray("sampled_points")
        val sampledPts = if (spArr == null) emptyList() else (0 until spArr.length()).map { i ->
            val p = spArr.getJSONObject(i)
            DemSampledPoint(
                label = p.optString("label", "Điểm #${i + 1}"),
                lat = p.optDouble("lat", 0.0),
                lon = p.optDouble("lon", 0.0),
                zPrimaryM = if (p.isNull("z_primary_m")) null else p.optDouble("z_primary_m"),
                zReferenceM = if (p.isNull("z_reference_m")) null else p.optDouble("z_reference_m"),
                zBareEarthM = if (p.isNull("z_bare_earth_m")) null else p.optDouble("z_bare_earth_m"),
                canopyOrBuildingHeightM = p.optDouble("canopy_or_building_height_m", 0.0),
            )
        }

        val scArr = o.optJSONArray("source_comparisons")
        val sourceComparisons = if (scArr == null) emptyList() else (0 until scArr.length()).map { i ->
            val s = scArr.getJSONObject(i)
            DemSourceComparison(
                id = s.optString("id"),
                shortLabel = s.optString("short_label"),
                name = s.optString("name"),
                resolutionLabel = s.optString("resolution_label"),
                coverage = s.optString("coverage"),
                demType = s.optString("dem_type"),
                repoTool = s.optString("repo_tool"),
                url = s.optString("url"),
                meanElevationM = s.optDouble("mean_elevation_m", 0.0),
            )
        }

        val grArr = o.optJSONArray("satellite_granules")
        val granules = if (grArr == null) emptyList() else (0 until grArr.length()).map { i ->
            val gObj = grArr.getJSONObject(i)
            DemSatelliteGranule(
                provider = gObj.optString("provider"),
                sceneId = gObj.optString("scene_id"),
                platform = gObj.optString("platform"),
                resolutionM = gObj.optDouble("resolution_m", 30.0),
                acquisitionDate = gObj.optString("acquisition_date"),
                downloadUrl = gObj.optString("download_url"),
                productType = gObj.optString("product_type"),
            )
        }

        val tifB64 = o.optString("geotiff_b64", "")
        val tifBytes = if (tifB64.isNotBlank()) decodeB64(tifB64) else ByteArray(0)
        val warns = o.optJSONArray("warnings")?.let { w -> (0 until w.length()).map { w.getString(it) } } ?: emptyList()

        return DemHubReport(
            bounds = b,
            rows = rows,
            cols = cols,
            cellM = cellM,
            primarySource = DemSourceKey.fromId(primId),
            referenceSource = DemSourceKey.fromId(refId),
            simulated = o.optBoolean("simulated", false),
            hydrology = hydro,
            coregistration = coreg,
            sampledPoints = sampledPts,
            sourceComparisons = sourceComparisons,
            satelliteGranules = granules,
            geotiffFilename = o.optString("geotiff_filename", "vwing_dem.tif"),
            geotiffSizeBytes = o.optInt("geotiff_size_bytes", tifBytes.size),
            geotiffBytes = tifBytes,
            warnings = warns,
        )
    }

    // ---------- tầm nhìn ----------

    /**
     * Viewshed bằng tia ngắm từ mắt người quan sát tới từng ô, có hiệu chỉnh độ cong Trái Đất và khúc xạ (k = 0,13).
     * Ô nhìn thấy khi góc tới đích ≥ góc cao nhất của mọi ô chắn giữa đường. Ô thiếu dữ liệu không chắn và không được coi là thấy.
     */
    fun viewshed(dem: Dem, observer: GeoPoint, observerHeightM: Double, targetHeightM: Double, radiusM: Double): ViewshedResult? {
        val oc = dem.grid.cellOf(observer) ?: return null
        val oz = dem.z[oc]
        if (oz.isNaN()) return null
        val g = dem.grid
        val (ox, oy) = g.centerXY(oc)
        val eye = oz + observerHeightM
        val step = min(g.cellWm, g.cellHm) / 2.0
        val vis = BooleanArray(dem.z.size)
        val curv = (1 - REFRACTION_K) / (2 * EARTH_R)
        var analysed = 0; var seen = 0
        val rCells = ceil(radiusM / min(g.cellWm, g.cellHm)).toInt() + 1
        val r0 = oc / g.cols; val c0 = oc % g.cols
        for (r in max(0, r0 - rCells)..min(g.rows - 1, r0 + rCells)) for (c in max(0, c0 - rCells)..min(g.cols - 1, c0 + rCells)) {
            val i = r * g.cols + c
            val (tx, ty) = g.centerXY(i)
            val dx = tx - ox; val dy = ty - oy
            val dist = sqrt(dx * dx + dy * dy)
            if (dist > radiusM || dem.z[i].isNaN()) continue
            analysed++
            if (i == oc) { vis[i] = true; seen++; continue }
            val targetAngle = (dem.z[i] + targetHeightM - dist * dist * curv - eye) / dist
            var maxAngle = Double.NEGATIVE_INFINITY
            var d = step
            var blocked = false
            while (d < dist - step / 2) {
                val sx = ox + dx * d / dist; val sy = oy + dy * d / dist
                val sc = floor(sx / g.cellWm).toInt(); val sr = floor(sy / g.cellHm).toInt()
                if (sc in 0 until g.cols && sr in 0 until g.rows) {
                    val sz = dem.z[sr * g.cols + sc]
                    if (!sz.isNaN()) {
                        val ang = (sz - d * d * curv - eye) / d
                        if (ang > maxAngle) maxAngle = ang
                        if (maxAngle > targetAngle) { blocked = true; break }
                    }
                }
                d += step
            }
            if (!blocked) { vis[i] = true; seen++ }
        }
        return ViewshedResult(dem, vis, oc, oz, radiusM, analysed, seen)
    }

    // ---------- nhạy cảm ngập lụt ----------

    private val DR = intArrayOf(-1, -1, -1, 0, 0, 1, 1, 1)
    private val DC = intArrayOf(-1, 0, 1, -1, 1, -1, 0, 1)

    /** Lấp trũng (Priority-Flood, Barnes 2014) với độ dốc ε rất nhỏ để mọi ô có đường thoát; trả (DEM đã lấp, số ô bị nâng). */
    fun fillDepressions(dem: Dem): Pair<DoubleArray, Int> {
        val n = dem.z.size; val cols = dem.cols; val rows = dem.rows
        val out = dem.z.copyOf()
        val done = BooleanArray(n)
        val q = PriorityQueue<Pair<Int, Double>>(compareBy { it.second })
        fun push(i: Int) { if (!done[i]) { done[i] = true; q.add(i to out[i]) } }
        for (i in 0 until n) if (out[i].isNaN()) done[i] = true      // ô trống: dòng chảy được thoát qua
        for (i in 0 until n) {
            val r = i / cols; val c = i % cols
            if (done[i] && out[i].isNaN()) continue
            val onEdge = r == 0 || c == 0 || r == rows - 1 || c == cols - 1
            val nextToVoid = (0 until 8).any { k ->
                val rr = r + DR[k]; val cc = c + DC[k]
                rr in 0 until rows && cc in 0 until cols && out[rr * cols + cc].isNaN()
            }
            if (onEdge || nextToVoid) push(i)
        }
        val eps = 1e-4
        var filled = 0
        while (q.isNotEmpty()) {
            val (i, zi) = q.poll()
            val r = i / cols; val c = i % cols
            for (k in 0 until 8) {
                val rr = r + DR[k]; val cc = c + DC[k]
                if (rr !in 0 until rows || cc !in 0 until cols) continue
                val j = rr * cols + cc
                if (done[j]) continue
                done[j] = true
                if (out[j] <= zi) { out[j] = zi + eps; filled++ }
                q.add(j to out[j])
            }
        }
        return out to filled
    }

    /**
     * Chỉ số nhạy cảm ngập lụt theo địa hình: kết hợp TWI (ln(a/tanβ), xếp hạng phần trăm trong cửa sổ) và HAND (độ cao so với dòng chảy gần nhất,
     * tuyệt đối, ≥ 10 m coi là 0), trọng số 0,5/0,5. Dòng chảy: D8 trên DEM đã lấp trũng; "kênh" là ô có diện tích thượng nguồn ≥ [channelHa].
     */
    fun flood(dem: Dem, channelHa: Double = 3.0): FloodResult {
        val n = dem.z.size; val cols = dem.cols; val rows = dem.rows
        val (zf, filled) = fillDepressions(dem)
        val cw = dem.grid.cellWm; val ch = dem.grid.cellHm
        val dist = DoubleArray(8) { k -> sqrt((DC[k] * cw).let { it * it } + (DR[k] * ch).let { it * it }) }
        val recv = IntArray(n) { -1 }
        for (i in 0 until n) {
            if (zf[i].isNaN()) continue
            val r = i / cols; val c = i % cols
            var best = 0.0; var bj = -1
            for (k in 0 until 8) {
                val rr = r + DR[k]; val cc = c + DC[k]
                if (rr !in 0 until rows || cc !in 0 until cols) continue
                val j = rr * cols + cc
                if (zf[j].isNaN()) continue
                val s = (zf[i] - zf[j]) / dist[k]
                if (s > best) { best = s; bj = j }
            }
            recv[i] = bj
        }
        val order = (0 until n).filter { !zf[it].isNaN() }.sortedByDescending { zf[it] }
        val acc = IntArray(n) { if (zf[it].isNaN()) 0 else 1 }
        for (i in order) { val j = recv[i]; if (j >= 0) acc[j] += acc[i] }
        val tanB = DoubleArray(n) { Double.NaN }
        for (i in 0 until n) {
            if (dem.z[i].isNaN()) continue
            val r = i / cols; val c = i % cols
            fun zz(rr: Int, cc: Int): Double { val v = dem.z[rr.coerceIn(0, rows - 1) * cols + cc.coerceIn(0, cols - 1)]; return if (v.isNaN()) dem.z[i] else v }
            val dzdx = (zz(r, c + 1) - zz(r, c - 1)) / (2 * cw); val dzdy = (zz(r + 1, c) - zz(r - 1, c)) / (2 * ch)
            tanB[i] = max(MIN_SLOPE_TAN, sqrt(dzdx * dzdx + dzdy * dzdy))
        }
        val cell = dem.cellM
        val twi = DoubleArray(n) { i -> if (tanB[i].isNaN()) Double.NaN else ln(acc[i] * cell / tanB[i]) }
        val channelCells = max(1, ceil(channelHa * 10_000.0 / (cw * ch)).toInt())
        val channel = BooleanArray(n) { !zf[it].isNaN() && acc[it] >= channelCells }
        val ref = DoubleArray(n) { Double.NaN }
        for (i in order.asReversed()) {
            ref[i] = if (channel[i] || recv[i] < 0) zf[i] else ref[recv[i]]
        }
        val hand = DoubleArray(n) { i -> if (ref[i].isNaN()) Double.NaN else max(0.0, zf[i] - ref[i]) }
        val sorted = twi.filter { !it.isNaN() }.sorted()
        fun pct(v: Double): Double {
            if (sorted.size < 2) return 0.5
            var lo = 0; var hi = sorted.size
            while (lo < hi) { val m = (lo + hi) / 2; if (sorted[m] < v) lo = m + 1 else hi = m }
            return lo.toDouble() / (sorted.size - 1)
        }
        val score = DoubleArray(n) { Double.NaN }
        val cls = arrayOfNulls<FloodClass>(n)
        for (i in 0 until n) {
            if (twi[i].isNaN() || hand[i].isNaN()) continue
            val sHand = 1.0 - (hand[i] / HAND_CAP_M).coerceIn(0.0, 1.0)
            val s = (0.5 * pct(twi[i]) + 0.5 * sHand).coerceIn(0.0, 1.0)
            score[i] = s
            cls[i] = if (s < .25) FloodClass.LOW else if (s < .5) FloodClass.MEDIUM else if (s < .75) FloodClass.HIGH else FloodClass.VERY_HIGH
        }
        return FloodResult(dem, zf, acc, tanB, twi, hand, channel, score, cls, channel.count { it } * dem.grid.cellHa, filled)
    }

    /** Ô nằm trong vùng phân tích; nếu vùng quá nhỏ không trúng tâm ô nào thì lấy ô chứa tâm vùng (và báo cho người dùng). */
    fun regionCells(dem: Dem, ring: List<GeoPoint>): Pair<List<Int>, Boolean> {
        val inside = dem.grid.inside.indices.filter { dem.grid.inside[it] && !dem.z[it].isNaN() }
        if (inside.isNotEmpty()) return inside to false
        val c = Geo.centroid(Geo.open(ring))?.let { dem.grid.cellOf(it) }
        return (if (c != null && !dem.z[c].isNaN()) listOf(c) else emptyList()) to true
    }
}
