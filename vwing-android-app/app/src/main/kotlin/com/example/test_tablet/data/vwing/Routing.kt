package com.example.test_tablet.data.vwing

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Phân tích theo MẠNG LƯỚI ĐƯỜNG (không phải đường chim bay): vùng phục vụ / đường đồng thời gian (isochrone) và ma trận thời gian
 * điểm đi–điểm đến, lấy từ Valhalla/OpenStreetMap qua máy chủ vệ tinh. Thời gian là ước tính theo dữ liệu đường OSM và tốc độ mặc định:
 * không tính kẹt xe, thời tiết hay đường đất chưa có trên bản đồ. Toạ độ điểm được gửi tới dịch vụ định tuyến (mặc định là máy chủ cộng đồng OSM).
 */
enum class TravelProfile(val key: String, val label: String) {
    WALK("pedestrian", "Đi bộ"), BIKE("bicycle", "Xe đạp"), SCOOTER("motor_scooter", "Xe máy"), CAR("auto", "Ô tô")
}

data class Place(val name: String, val p: GeoPoint)
data class Contour(val minutes: Int, val ring: List<GeoPoint>, val areaHa: Double)
data class ServiceArea(val name: String, val origin: GeoPoint, val contours: List<Contour>)
data class IsochroneResult(val profile: TravelProfile, val areas: List<ServiceArea>, val attribution: String, val caution: String)
data class OdMatrix(val profile: TravelProfile, val minutes: List<List<Double?>>, val km: List<List<Double?>>, val attribution: String)
class ServiceRaster(val bounds: GeoBounds, val cols: Int, val rows: Int, val minutes: IntArray)

object RoutingClient {
    const val MAX_MINUTES = 60
    const val MAX_ORIGINS = 5
    const val MAX_MATRIX = 10

    private fun pointJson(name: String, p: GeoPoint) = JSONObject().put("lat", p.lat).put("lng", p.lng).put("name", name.take(80))

    fun parseIsochrone(o: JSONObject): IsochroneResult {
        val profile = TravelProfile.entries.firstOrNull { it.key == o.optString("profile") } ?: throw GisException("Phản hồi vùng phục vụ thiếu loại phương tiện.")
        val areas = o.getJSONArray("origins").let { a ->
            (0 until a.length()).map { i ->
                val x = a.getJSONObject(i)
                val cs = x.getJSONArray("contours").let { ca ->
                    (0 until ca.length()).map { j ->
                        val c = ca.getJSONObject(j)
                        val ringA = c.getJSONArray("ring")
                        val ring = (0 until ringA.length()).map { k -> ringA.getJSONArray(k).let { GeoPoint(it.getDouble(1), it.getDouble(0)) } }
                        Contour(c.getInt("minutes"), ring, c.optDouble("area_ha", Geo.polygonAreaHa(ring)))
                    }
                }.filter { it.ring.size >= 4 }.sortedBy { it.minutes }
                ServiceArea(x.optString("name"), GeoPoint(x.getDouble("lat"), x.getDouble("lng")), cs)
            }
        }
        if (areas.any { it.contours.isEmpty() }) throw GisException("Máy chủ không tạo được vùng phục vụ cho một điểm.")
        return IsochroneResult(profile, areas, o.optString("attribution"), o.optString("caution"))
    }

    fun isochrone(cfg: SatelliteConfig, origins: List<Place>, profile: TravelProfile, minutes: List<Int>): IsochroneResult {
        if (origins.isEmpty() || origins.size > MAX_ORIGINS) throw GisException("Chọn từ 1 đến $MAX_ORIGINS điểm xuất phát.")
        val m = minutes.distinct().sorted()
        if (m.isEmpty() || m.size > 4 || m.first() < 1 || m.last() > MAX_MINUTES) throw GisException("Chọn 1–4 mốc phút trong khoảng 1–$MAX_MINUTES.")
        val body = JSONObject().put("origins", JSONArray().also { a -> origins.forEach { a.put(pointJson(it.name, it.p)) } })
            .put("profile", profile.key).put("minutes", JSONArray(m))
        val o = GisClient.call(cfg, "/v1/routing/isochrone", body)
        return try { parseIsochrone(o) } catch (e: GisException) { throw e } catch (e: Exception) { throw GisException("Dữ liệu vùng phục vụ nhận về không đọc được.") }
    }

    fun parseMatrix(o: JSONObject, nSrc: Int, nTgt: Int): OdMatrix {
        val profile = TravelProfile.entries.firstOrNull { it.key == o.optString("profile") } ?: throw GisException("Phản hồi ma trận thiếu loại phương tiện.")
        fun grid(name: String): List<List<Double?>> {
            val a = o.getJSONArray(name)
            require(a.length() == nSrc)
            return (0 until nSrc).map { i -> a.getJSONArray(i).also { require(it.length() == nTgt) }.let { r -> (0 until nTgt).map { j -> if (r.isNull(j)) null else r.getDouble(j) } } }
        }
        return OdMatrix(profile, grid("minutes"), grid("km"), o.optString("attribution"))
    }

    fun matrix(cfg: SatelliteConfig, sources: List<Place>, targets: List<Place>, profile: TravelProfile): OdMatrix {
        if (sources.isEmpty() || targets.isEmpty() || sources.size > MAX_MATRIX || targets.size > MAX_MATRIX) throw GisException("Cần 1–$MAX_MATRIX điểm đi và 1–$MAX_MATRIX điểm đến.")
        val body = JSONObject().put("sources", JSONArray().also { a -> sources.forEach { a.put(pointJson(it.name, it.p)) } })
            .put("targets", JSONArray().also { a -> targets.forEach { a.put(pointJson(it.name, it.p)) } }).put("profile", profile.key)
        val o = GisClient.call(cfg, "/v1/routing/matrix", body)
        return try { parseMatrix(o, sources.size, targets.size) } catch (e: Exception) { throw GisException("Ma trận thời gian nhận về không đọc được.") }
    }

    // ---------- vùng phục vụ: raster, độ phủ ----------

    /** Số phút nhỏ nhất trong các đường đồng mức chứa điểm; null nếu nằm ngoài mọi đường. */
    fun minutesTo(area: ServiceArea, p: GeoPoint): Int? = area.contours.firstOrNull { Geo.pointInPolygon(p, it.ring) }?.minutes

    fun minutesTo(areas: List<ServiceArea>, p: GeoPoint): Int? = areas.mapNotNull { minutesTo(it, p) }.minOrNull()

    /** Tô raster theo số phút nhỏ nhất qua mọi điểm xuất phát (0 = ngoài vùng). Khung bao tất cả đường ngoài cùng, đệm 2%. */
    fun raster(areas: List<ServiceArea>, size: Int = 256): ServiceRaster? {
        val rings = areas.flatMap { a -> a.contours.map { it.ring } }
        if (rings.isEmpty()) return null
        val pts = rings.flatten()
        var b = GeoBounds(pts.minOf { it.lat }, pts.minOf { it.lng }, pts.maxOf { it.lat }, pts.maxOf { it.lng })
        val padLat = (b.north - b.south) * .02; val padLng = (b.east - b.west) * .02
        b = GeoBounds(max(-90.0, b.south - padLat), max(-180.0, b.west - padLng), min(90.0, b.north + padLat), min(180.0, b.east + padLng))
        if (!b.valid) return null
        val w = Geo.distanceM(GeoPoint(b.center.lat, b.west), GeoPoint(b.center.lat, b.east))
        val h = Geo.distanceM(GeoPoint(b.south, b.center.lng), GeoPoint(b.north, b.center.lng))
        if (w <= 0 || h <= 0) return null
        val cols = if (w >= h) size else max(8, (size * w / h).roundToInt())
        val rows = if (w >= h) max(8, (size * h / w).roundToInt()) else size
        val boxes = areas.map { a -> a.contours.map { c -> c to Box.of(c.ring) } }
        val out = IntArray(cols * rows)
        for (r in 0 until rows) for (c in 0 until cols) {
            val p = GeoPoint(b.north - (r + .5) * (b.north - b.south) / rows, b.west + (c + .5) * (b.east - b.west) / cols)
            var best = 0
            for (cs in boxes) for ((ct, box) in cs) {
                if (best != 0 && ct.minutes >= best) break
                if (box.contains(p) && Geo.pointInPolygon(p, ct.ring)) { best = if (best == 0) ct.minutes else min(best, ct.minutes); break }
            }
            out[r * cols + c] = best
        }
        return ServiceRaster(b, cols, rows, out)
    }

    private class Box(val s: Double, val w: Double, val n: Double, val e: Double) {
        fun contains(p: GeoPoint) = p.lat in s..n && p.lng in w..e
        companion object { fun of(r: List<GeoPoint>) = Box(r.minOf { it.lat }, r.minOf { it.lng }, r.maxOf { it.lat }, r.maxOf { it.lng }) }
    }

    /** Phần trăm diện tích vùng (lấy mẫu theo lưới) nằm trong từng mốc phút (tích luỹ: ≤ N phút). */
    fun areaCoverage(areas: List<ServiceArea>, region: List<GeoPoint>, minutes: List<Int>): Map<Int, Double>? {
        val g = ThematicMaps.grid(region, maxCells = 2_500, minCellM = 3.0) ?: return null
        val cells = (0 until g.size).filter { g.inside[it] }
        val b = g.bounds
        val got = cells.map { i ->
            minutesTo(areas, GeoPoint(b.north - (i / g.cols + .5) * (b.north - b.south) / g.rows, b.west + (i % g.cols + .5) * (b.east - b.west) / g.cols))
        }
        return minutes.associateWith { m -> 100.0 * got.count { it != null && it <= m } / cells.size }
    }

    /** Số cây (có toạ độ) nằm trong từng mốc phút (tích luỹ) và số cây ngoài mọi mốc. */
    fun treeCoverage(areas: List<ServiceArea>, trees: List<TreeSample>, minutes: List<Int>): Pair<Map<Int, Int>, Int> {
        val got = trees.map { minutesTo(areas, it.p) }
        return minutes.associateWith { m -> got.count { it != null && it <= m } } to got.count { it == null }
    }
}

// ---------- dòng chảy điểm đi – điểm đến ----------

class OdNode(val name: String, val p: GeoPoint, val weight: Double)
class OdEdge(val from: OdNode, val to: Place, val weight: Double, val minutes: Double, val km: Double)
class OdZones(val nodes: List<OdNode>, val unit: String, val skippedZones: Int)

object OdFlow {
    /**
     * Mỗi khu là một điểm đi tại trọng tâm các cây có toạ độ trong vùng; khối lượng = tổng số quả đã ghi (nếu vườn có ghi quả), không thì số cây.
     * Khu không có cây nào có toạ độ bị bỏ qua (đếm lại để báo).
     */
    fun zones(d: GardenData, ring: List<GeoPoint>): OdZones {
        val located = d.activeTrees.filter { it.pos != null && Geo.pointInPolygon(it.pos, ring) }
        val useFruits = located.any { (it.fruits ?: 0) > 0 }
        val byZone = located.groupBy { it.zone }
        val nodes = byZone.mapNotNull { (zone, ts) ->
            val c = Geo.centroid(ts.map { it.pos!! }) ?: return@mapNotNull null
            val w = if (useFruits) ts.sumOf { (it.fruits ?: 0).toDouble() } else ts.size.toDouble()
            if (w > 0) OdNode(zone, c, w) else null
        }.sortedByDescending { it.weight }
        val skipped = d.activeTrees.groupBy { it.zone }.keys.count { z -> nodes.none { it.name == z } }
        return OdZones(nodes, if (useFruits) "quả" else "cây", skipped)
    }

    /** GIẢ ĐỊNH (nêu rõ cho người dùng): mỗi khu chở toàn bộ khối lượng về điểm đến có thời gian đường ngắn nhất. Khu không tới được điểm nào bị bỏ. */
    fun allocate(nodes: List<OdNode>, dests: List<Place>, m: OdMatrix): List<OdEdge> = nodes.indices.mapNotNull { i ->
        val j = dests.indices.filter { m.minutes[i][it] != null && m.km[i][it] != null }.minByOrNull { m.minutes[i][it]!! } ?: return@mapNotNull null
        OdEdge(nodes[i], dests[j], nodes[i].weight, m.minutes[i][j]!!, m.km[i][j]!!)
    }
}

/** Các điểm quan trọng người dùng lưu (chợ, kho, bệnh viện, trạm cứu hỏa…): làm điểm xuất phát vùng phục vụ và điểm đến dòng chảy. */
class PlaceStore(dir: File) {
    private val file = File(dir, "vwing_places.json")
    fun load(): List<Place> = try {
        val a = JSONArray(file.readText())
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val lat = o.optDouble("lat", Double.NaN); val lng = o.optDouble("lng", Double.NaN)
            if (lat in -85.0..85.0 && lng in -180.0..180.0 && o.optString("name").isNotBlank()) Place(o.getString("name"), GeoPoint(lat, lng)) else null
        }.take(MAX)
    } catch (_: Exception) { emptyList() }
    fun save(list: List<Place>) {
        val tmp = File(file.parentFile, "vwing_places.json.tmp")
        tmp.writeText(JSONArray().also { a -> list.take(MAX).forEach { a.put(JSONObject().put("name", it.name).put("lat", it.p.lat).put("lng", it.p.lng)) } }.toString())
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }
    companion object { const val MAX = 10 }
}
