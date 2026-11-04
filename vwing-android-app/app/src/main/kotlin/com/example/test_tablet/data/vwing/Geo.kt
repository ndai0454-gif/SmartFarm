package com.example.test_tablet.data.vwing

import org.json.JSONArray
import org.json.JSONObject
import java.io.StringReader
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class Geom { POINT, LINE, POLYGON }

class ParsedFeature(val name: String, val geom: Geom, val points: List<GeoPoint>, val hint: String)

class ParseResult(val features: List<ParsedFeature>, val errors: List<String>, val warnings: List<String>)

enum class ImportKind(val label: String) {
    TREE("Cây"), BOUNDARY("Ranh vườn"), ZONE("Khu vực"), ROAD("Đường đi"), WATER("Nguồn nước"), OBSTACLE("Vật cản"), SKIP("Bỏ qua");

    fun toFeatureKind(): FeatureKind? = when (this) {
        BOUNDARY -> FeatureKind.BOUNDARY; ZONE -> FeatureKind.ZONE; ROAD -> FeatureKind.ROAD
        WATER -> FeatureKind.WATER; OBSTACLE -> FeatureKind.OBSTACLE; else -> null
    }
}

class ImportItem(val feature: ParsedFeature, var kind: ImportKind)

class GeoIssue(val error: Boolean, val text: String)

object Geo {
    private const val R = 6_371_000.0

    fun distanceM(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).let { it * it } + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).let { it * it }
        return 2 * R * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Chiếu phẳng (m) quanh gốc; đủ chính xác cho vườn vài hecta. */
    fun toMeters(p: GeoPoint, o: GeoPoint): Pair<Double, Double> =
        Pair(Math.toRadians(p.lng - o.lng) * R * cos(Math.toRadians(o.lat)), Math.toRadians(p.lat - o.lat) * R)

    fun centroid(pts: List<GeoPoint>): GeoPoint? =
        if (pts.isEmpty()) null else GeoPoint(pts.sumOf { it.lat } / pts.size, pts.sumOf { it.lng } / pts.size)

    fun open(points: List<GeoPoint>): List<GeoPoint> =
        if (points.size > 1 && points.first() == points.last()) points.dropLast(1) else points

    fun polygonAreaHa(points: List<GeoPoint>): Double {
        val p = open(points)
        if (p.size < 3) return 0.0
        val o = p[0]
        val m = p.map { toMeters(it, o) }
        var s = 0.0
        for (i in m.indices) { val j = (i + 1) % m.size; s += m[i].first * m[j].second - m[j].first * m[i].second }
        return abs(s) / 2.0 / 10_000.0
    }

    private fun ccw(a: Pair<Double, Double>, b: Pair<Double, Double>, c: Pair<Double, Double>) =
        (c.second - a.second) * (b.first - a.first) > (b.second - a.second) * (c.first - a.first)

    private fun segCross(a: Pair<Double, Double>, b: Pair<Double, Double>, c: Pair<Double, Double>, d: Pair<Double, Double>) =
        ccw(a, c, d) != ccw(b, c, d) && ccw(a, b, c) != ccw(a, b, d)

    fun selfIntersects(points: List<GeoPoint>): Boolean {
        val p = open(points)
        if (p.size < 4) return false
        val m = p.map { toMeters(it, p[0]) }
        val n = m.size
        for (i in 0 until n) for (j in i + 1 until n) {
            val i2 = (i + 1) % n; val j2 = (j + 1) % n
            if (i == j || i2 == j || j2 == i) continue
            if (segCross(m[i], m[i2], m[j], m[j2])) return true
        }
        return false
    }

    fun pointInPolygon(pt: GeoPoint, poly: List<GeoPoint>): Boolean {
        val p = open(poly)
        if (p.size < 3) return false
        var inside = false
        var j = p.size - 1
        for (i in p.indices) {
            if ((p[i].lat > pt.lat) != (p[j].lat > pt.lat) &&
                pt.lng < (p[j].lng - p[i].lng) * (pt.lat - p[i].lat) / (p[j].lat - p[i].lat) + p[i].lng) inside = !inside
            j = i
        }
        return inside
    }

    private fun plain(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace('đ', 'd')

    fun guessKind(f: ParsedFeature, firstPolygon: Boolean): ImportKind {
        val h = plain(f.name + " " + f.hint)
        return when (f.geom) {
            Geom.POINT -> when {
                Regex("nuoc|water|gieng|\\bao\\b|\\bho\\b").containsMatchIn(h) -> ImportKind.WATER
                Regex("vat can|obstacle|cot dien|pole|tower|can tro").containsMatchIn(h) -> ImportKind.OBSTACLE
                else -> ImportKind.TREE
            }
            Geom.LINE -> if (Regex("nuoc|suoi|water|kenh|muong").containsMatchIn(h)) ImportKind.WATER else ImportKind.ROAD
            Geom.POLYGON -> when {
                Regex("ranh|boundary|vuon").containsMatchIn(h) -> ImportKind.BOUNDARY
                Regex("khu|zone").containsMatchIn(h) -> ImportKind.ZONE
                firstPolygon -> ImportKind.BOUNDARY
                else -> ImportKind.ZONE
            }
        }
    }

    // ---------- đọc tệp ----------
    fun parse(text: String): ParseResult {
        val t = text.trim().removePrefix("﻿")
        return when {
            t.startsWith("{") -> parseGeoJson(t)
            t.startsWith("<") -> parseKml(t)
            else -> Vn2000Converter.parseOrError(t)
        }
    }

    private fun coord(a: JSONArray, label: String, errors: MutableList<String>): GeoPoint? {
        if (a.length() < 2) { errors += "\"$label\": tọa độ thiếu giá trị."; return null }
        val lng = a.optDouble(0, Double.NaN); val lat = a.optDouble(1, Double.NaN)
        return checkCoord(lat, lng, label, errors)
    }

    private fun checkCoord(lat: Double, lng: Double, label: String, errors: MutableList<String>): GeoPoint? {
        if (lat.isNaN() || lng.isNaN() || abs(lat) > 90 || abs(lng) > 180) {
            errors += "\"$label\": tọa độ ngoài phạm vi (vĩ độ=$lat, kinh độ=$lng). Có thể bị đảo kinh/vĩ hoặc không phải WGS84."
            return null
        }
        return GeoPoint(lat, lng)
    }

    fun parseGeoJson(text: String): ParseResult {
        val errors = mutableListOf<String>(); val warnings = mutableListOf<String>()
        val out = mutableListOf<ParsedFeature>()
        val root = try { JSONObject(text) } catch (e: Exception) {
            return ParseResult(emptyList(), listOf("GeoJSON không hợp lệ: ${e.message}"), emptyList())
        }
        root.optJSONObject("crs")?.optJSONObject("properties")?.optString("name")?.takeIf { it.isNotEmpty() }?.let { crs ->
            if (!Regex("CRS84|4326", RegexOption.IGNORE_CASE).containsMatchIn(crs))
                errors += "Hệ tọa độ \"$crs\" không phải WGS84 (EPSG:4326). Hãy chuyển đổi trước khi nhập."
        }

        fun geometry(g: JSONObject, name: String, hint: String) {
            val coords = g.optJSONArray("coordinates")
            fun ring(a: JSONArray): List<GeoPoint> = (0 until a.length()).mapNotNull { coord(a.getJSONArray(it), name, errors) }
            when (g.optString("type")) {
                "Point" -> coords?.let { c -> coord(c, name, errors)?.let { out += ParsedFeature(name, Geom.POINT, listOf(it), hint) } }
                "MultiPoint" -> coords?.let { c -> for (i in 0 until c.length()) coord(c.getJSONArray(i), name, errors)?.let { out += ParsedFeature(name, Geom.POINT, listOf(it), hint) } }
                "LineString" -> coords?.let { out += ParsedFeature(name, Geom.LINE, ring(it), hint) }
                "MultiLineString" -> coords?.let { c -> for (i in 0 until c.length()) out += ParsedFeature(name, Geom.LINE, ring(c.getJSONArray(i)), hint) }
                "Polygon" -> coords?.let { c ->
                    if (c.length() > 1) warnings += "\"$name\": bỏ qua ${c.length() - 1} lỗ (vòng trong); chỉ dùng vòng ngoài."
                    if (c.length() > 0) out += ParsedFeature(name, Geom.POLYGON, ring(c.getJSONArray(0)), hint)
                }
                "MultiPolygon" -> coords?.let { c -> for (i in 0 until c.length()) { val p = c.getJSONArray(i); if (p.length() > 0) out += ParsedFeature(name, Geom.POLYGON, ring(p.getJSONArray(0)), hint) } }
                "GeometryCollection" -> g.optJSONArray("geometries")?.let { a -> for (i in 0 until a.length()) geometry(a.getJSONObject(i), name, hint) }
                else -> warnings += "\"$name\": bỏ qua loại hình học \"${g.optString("type")}\"."
            }
        }

        fun feature(f: JSONObject, idx: Int) {
            val props = f.optJSONObject("properties")
            val name = props?.let { p -> listOf("name", "Name", "ten", "code", "ma").firstNotNullOfOrNull { k -> p.optString(k).takeIf { it.isNotBlank() } } } ?: ""
            val hint = props?.let { p -> listOf("kind", "type", "loai", "zone", "khu").mapNotNull { p.optString(it).takeIf { s -> s.isNotBlank() } }.joinToString(" ") } ?: ""
            f.optJSONObject("geometry")?.let { geometry(it, name.ifBlank { "Đối tượng ${idx + 1}" }, hint + " " + (props?.optString("zone") ?: "")) }
        }

        when (root.optString("type")) {
            "FeatureCollection" -> root.optJSONArray("features")?.let { a -> for (i in 0 until a.length()) feature(a.getJSONObject(i), i) }
            "Feature" -> feature(root, 0)
            "Point", "LineString", "Polygon", "MultiPoint", "MultiLineString", "MultiPolygon", "GeometryCollection" -> geometry(root, "Đối tượng 1", "")
            else -> errors += "Không tìm thấy FeatureCollection/Feature trong tệp."
        }
        if (out.isEmpty() && errors.isEmpty()) errors += "Tệp không có đối tượng nào dùng được."
        return ParseResult(out, errors, warnings)
    }

    fun parseKml(text: String): ParseResult {
        val errors = mutableListOf<String>(); val warnings = mutableListOf<String>()
        val out = mutableListOf<ParsedFeature>()
        try {
            val p = org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser()
            p.setInput(StringReader(text))
            var name = ""; var geom: Geom? = null; var inPlacemark = false; var untitled = 0
            var ev = p.eventType
            while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> when (p.name) {
                        "Placemark" -> { inPlacemark = true; name = ""; geom = null; untitled++ }
                        "name" -> if (inPlacemark && geom == null) name = p.nextText().trim()
                        "Point" -> geom = Geom.POINT
                        "LineString" -> geom = Geom.LINE
                        "Polygon" -> geom = Geom.POLYGON
                        "coordinates" -> if (inPlacemark) {
                            val label = name.ifBlank { "Đối tượng $untitled" }
                            val pts = p.nextText().trim().split(Regex("\\s+")).filter { it.isNotBlank() }.mapNotNull { s ->
                                val v = s.split(",")
                                if (v.size < 2) { errors += "\"$label\": tọa độ \"$s\" thiếu giá trị."; null }
                                else checkCoord(v[1].toDoubleOrNull() ?: Double.NaN, v[0].toDoubleOrNull() ?: Double.NaN, label, errors)
                            }
                            val g = geom ?: Geom.POINT
                            if (pts.isNotEmpty()) out += ParsedFeature(label, g, pts, "")
                        }
                        "innerBoundaryIs" -> warnings += "Bỏ qua vòng trong của ${name.ifBlank { "đối tượng" }}."
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> if (p.name == "Placemark") inPlacemark = false
                }
                ev = p.next()
            }
        } catch (e: Exception) {
            errors += "KML không hợp lệ: ${e.message}"
        }
        if (out.isEmpty() && errors.isEmpty()) errors += "Tệp không có đối tượng nào dùng được."
        return ParseResult(out, errors, warnings)
    }

    // ---------- kiểm tra trước khi chấp nhận ----------
    fun validate(items: List<ImportItem>, d: GardenData): List<GeoIssue> {
        val res = mutableListOf<GeoIssue>()
        val used = items.filter { it.kind != ImportKind.SKIP }
        if (used.isEmpty()) return listOf(GeoIssue(true, "Chưa chọn đối tượng nào để nhập."))

        val boundaries = used.filter { it.kind == ImportKind.BOUNDARY }
        if (boundaries.size > 1) res += GeoIssue(true, "Chỉ được nhập một ranh vườn (đang chọn ${boundaries.size}).")

        for (u in used) {
            val f = u.feature
            when (u.kind) {
                ImportKind.BOUNDARY, ImportKind.ZONE -> {
                    if (f.geom != Geom.POLYGON) res += GeoIssue(true, "\"${f.name}\" không phải vùng khép kín nhưng được chọn là ${u.kind.label}.")
                    else {
                        val o = open(f.points)
                        if (o.toSet().size < 3) res += GeoIssue(true, "\"${f.name}\": cần ít nhất 3 điểm khác nhau.")
                        else if (selfIntersects(o)) res += GeoIssue(true, "\"${f.name}\": ranh tự cắt nhau.")
                        else if (u.kind == ImportKind.BOUNDARY) {
                            val ha = polygonAreaHa(o)
                            val declared = d.garden.areaHa
                            if (declared != null && declared > 0 && (ha > declared * 2 || ha < declared / 2))
                                res += GeoIssue(false, "Diện tích ranh tính được %.2f ha, lệch nhiều so với khai báo %.2f ha.".format(ha, declared))
                            else res += GeoIssue(false, "Diện tích ranh tính được %.2f ha.".format(ha))
                        }
                    }
                }
                ImportKind.ROAD -> if (f.points.size < 2) res += GeoIssue(true, "\"${f.name}\": đường cần ít nhất 2 điểm.")
                ImportKind.TREE -> if (f.geom != Geom.POINT) res += GeoIssue(true, "\"${f.name}\" không phải điểm nhưng được chọn là Cây.")
                    else if (f.name.isBlank() || f.name.startsWith("Đối tượng ")) res += GeoIssue(true, "Có điểm cây không có tên/mã.")
                else -> {}
            }
        }

        // xa trung tâm hiện có -> nghi sai hệ tọa độ
        val existingCenter = centroid(d.trees.mapNotNull { it.pos } + d.features.flatMap { it.points })
        val importedCenter = centroid(used.flatMap { it.feature.points })
        if (existingCenter != null && importedCenter != null && distanceM(existingCenter, importedCenter) > 50_000)
            res += GeoIssue(false, "Dữ liệu nhập cách vườn hiện có hơn 50 km. Kiểm tra lại kinh/vĩ và hệ tọa độ.")

        val boundary = boundaries.firstOrNull()?.feature?.points
            ?: d.features.firstOrNull { it.kind == FeatureKind.BOUNDARY }?.points
        val treeItems = used.filter { it.kind == ImportKind.TREE && it.feature.geom == Geom.POINT }
        val codes = mutableSetOf<String>()
        for (u in treeItems) {
            val f = u.feature; val pt = f.points[0]
            val key = normalizeCode(f.name) ?: f.name.uppercase()
            if (!codes.add(key)) res += GeoIssue(false, "Mã cây \"${f.name}\" xuất hiện nhiều lần trong tệp (bản sau sẽ ghi đè).")
            if (boundary != null && boundary.size >= 3 && !pointInPolygon(pt, boundary))
                res += GeoIssue(false, "Cây \"${f.name}\" nằm ngoài ranh vườn.")
            if (d.treeByCode(f.name) != null) res += GeoIssue(false, "Cây \"${f.name}\" đã có: sẽ cập nhật vị trí (giữ nguyên ID và lịch sử).")
            val near = d.activeTrees.firstOrNull { t -> t.pos != null && (normalizeCode(t.code) ?: t.code) != key && distanceM(t.pos, pt) < 1.0 }
            if (near != null) res += GeoIssue(false, "Cây \"${f.name}\" cách cây ${near.code} dưới 1 m; có thể trùng vị trí.")
        }
        for (i in treeItems.indices) for (j in i + 1 until treeItems.size) {
            val a = treeItems[i].feature; val b = treeItems[j].feature
            if ((normalizeCode(a.name) ?: a.name) != (normalizeCode(b.name) ?: b.name) && distanceM(a.points[0], b.points[0]) < 1.0)
                res += GeoIssue(false, "Cây \"${a.name}\" và \"${b.name}\" cách nhau dưới 1 m.")
        }
        return res
    }

    // ---------- xuất ----------
    fun exportGeoJson(d: GardenData): String {
        fun pt(p: GeoPoint) = JSONArray().put(p.lng).put(p.lat)
        fun feat(geom: JSONObject, props: JSONObject) = JSONObject().put("type", "Feature").put("geometry", geom).put("properties", props)
        val arr = JSONArray()
        for (f in d.features) {
            val closed = f.kind == FeatureKind.BOUNDARY || f.kind == FeatureKind.ZONE
            val g = when {
                closed -> JSONObject().put("type", "Polygon").put("coordinates", JSONArray().put(JSONArray().also { a ->
                    open(f.points).forEach { a.put(pt(it)) }; a.put(pt(open(f.points).first())) }))
                f.points.size == 1 -> JSONObject().put("type", "Point").put("coordinates", pt(f.points[0]))
                else -> JSONObject().put("type", "LineString").put("coordinates", JSONArray().also { a -> f.points.forEach { a.put(pt(it)) } })
            }
            arr.put(feat(g, JSONObject().put("name", f.name).put("kind", f.kind.name)))
        }
        for (t in d.activeTrees) {
            val p = t.pos ?: continue
            arr.put(feat(JSONObject().put("type", "Point").put("coordinates", pt(p)),
                JSONObject().put("name", t.code).put("kind", "TREE").put("zone", t.zone)
                    .put("posSource", t.posSource.name).put("condition", t.condition.name)))
        }
        return JSONObject().put("type", "FeatureCollection").put("features", arr).toString(2)
    }

    /** Giải nén và đọc trực tiếp tệp .KMZ (ZIP chứa doc.kml) ngay trên thiết bị không cần máy chủ. */
    fun parseKmz(bytes: ByteArray): ParseResult {
        return try {
            java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.lowercase().endsWith(".kml")) {
                        val kmlText = zis.readBytes().toString(Charsets.UTF_8)
                        return parseKml(kmlText)
                    }
                    entry = zis.nextEntry
                }
            }
            ParseResult(emptyList(), listOf("Không tìm thấy tệp .kml bên trong gói .kmz."), emptyList())
        } catch (e: Exception) {
            ParseResult(emptyList(), listOf("KMZ không hợp lệ: ${e.message}"), emptyList())
        }
    }
}

data class MergedProvinceInfo(
    val name: String,
    val defaultL0: Double,
    val mergedDetail: String,
    val areaKm2: Double,
    val population: Int,
)

data class Vn2000SheetPreset(
    val label: String,
    val province: String,
    val lon0: Double,
    val sheetNumber: String,
    val plotNumber: String,
    val coordsText: String,
)

/**
 * Bộ quy đổi tọa độ Góc ranh Sổ đỏ VN-2000 ⇄ WGS84 (Proj4 Transverse Mercator + 7 tham số Helmert Bộ TN&MT)
 * và Danh mục 34 Tỉnh/Thành sau sáp nhập (tích hợp từ MobileLand GIS).
 */
object Vn2000Converter {
    var activeProvince: String = "Lâm Đồng (Mới)"
    var activeLon0: Double = 107.75

    val CENTRAL_MERIDIANS_63: Map<String, Double> = linkedMapOf(
        "Lâm Đồng" to 107.75, "Đắk Lắk" to 108.5, "Đắk Nông" to 108.5, "Gia Lai" to 108.5, "Kon Tum" to 107.5,
        "Bình Phước" to 106.25, "Đồng Nai" to 107.75, "Bình Dương" to 105.75, "Tây Ninh" to 105.5, "Bà Rịa - Vũng Tàu" to 107.75,
        "TP. Hồ Chí Minh" to 105.75, "TP. Hà Nội" to 105.0, "TP. Đà Nẵng" to 107.75, "TP. Hải Phòng" to 105.75, "TP. Cần Thơ" to 105.0,
        "Khánh Hòa" to 108.25, "Bình Thuận" to 108.5, "Ninh Thuận" to 108.25, "Phú Yên" to 108.5, "Bình Định" to 108.25,
        "Quảng Ngãi" to 108.0, "Quảng Nam" to 107.75, "Thừa Thiên Huế" to 107.0, "Quảng Trị" to 106.25, "Quảng Bình" to 106.0,
        "Hà Tĩnh" to 105.5, "Nghệ An" to 104.75, "Thanh Hóa" to 105.0, "Tiền Giang" to 105.75, "Bến Tre" to 105.75,
        "Vĩnh Long" to 105.5, "Đồng Tháp" to 105.0, "An Giang" to 104.75, "Kiên Giang" to 104.5, "Cà Mau" to 104.5,
        "Sóc Trăng" to 105.5, "Bạc Liêu" to 105.0, "Hậu Giang" to 105.0, "Trà Vinh" to 105.5, "Long An" to 105.75,
        "Lào Cai" to 104.75, "Yên Bái" to 104.75, "Sơn La" to 104.0, "Điện Biên" to 103.0, "Lai Châu" to 103.0,
        "Hòa Bình" to 106.0, "Thái Nguyên" to 106.5, "Tuyên Quang" to 106.0, "Phú Thọ" to 104.75, "Bắc Giang" to 107.0,
        "Bắc Ninh" to 105.5, "Hải Dương" to 105.5, "Hưng Yên" to 105.5, "Quảng Ninh" to 107.75, "Lạng Sơn" to 107.25,
        "Cao Bằng" to 105.75, "Bắc Kạn" to 106.5, "Hà Giang" to 105.5, "Vĩnh Phúc" to 105.0, "Hà Nam" to 105.0,
        "Nam Định" to 105.5, "Ninh Bình" to 105.0, "Thái Bình" to 105.5,
    )

    val AFTER_MERGER_34: List<MergedProvinceInfo> = listOf(
        MergedProvinceInfo("Lâm Đồng (Mới)", 107.75, "Lâm Đồng (107.75°) + Đắk Nông (108.5°) + Bình Thuận (108.5°)", 24233.1, 3324400),
        MergedProvinceInfo("Đắk Lắk (Mới)", 108.50, "Đắk Lắk (108.5°) + Phú Yên (108.5°)", 18096.4, 2831300),
        MergedProvinceInfo("Gia Lai (Mới)", 108.50, "Gia Lai (108.5°) + Bình Định (108.25°)", 21576.5, 3153300),
        MergedProvinceInfo("Quảng Ngãi (Mới)", 108.00, "Quảng Ngãi (108.0°) + Kon Tum (107.5°)", 14832.6, 1861700),
        MergedProvinceInfo("Đồng Nai (Mới)", 107.75, "Đồng Nai (107.75°) + Bình Phước (106.25°)", 12737.2, 4427700),
        MergedProvinceInfo("TP. Hồ Chí Minh (Mới)", 105.75, "TP.HCM (105.75°) + Bình Dương (105.75°) + Bà Rịa - Vũng Tàu (107.75°)", 6772.6, 13608800),
        MergedProvinceInfo("Khánh Hòa (Mới)", 108.25, "Khánh Hòa (108.25°) + Ninh Thuận (108.25°)", 8555.9, 1882000),
        MergedProvinceInfo("Tây Ninh (Mới)", 105.50, "Tây Ninh (105.5°) + Long An (105.75°)", 8536.5, 2959000),
        MergedProvinceInfo("Đồng Tháp (Mới)", 105.00, "Đồng Tháp (105.0°) + Tiền Giang (105.75°)", 5938.7, 3397200),
        MergedProvinceInfo("Vĩnh Long (Mới)", 105.50, "Vĩnh Long (105.5°) + Bến Tre (105.75°) + Trà Vinh (105.5°)", 6296.2, 3367400),
        MergedProvinceInfo("TP. Cần Thơ (Mới)", 105.00, "Cần Thơ (105.0°) + Sóc Trăng (105.5°) + Hậu Giang (105.0°)", 6360.8, 3207000),
        MergedProvinceInfo("An Giang (Mới)", 104.75, "An Giang (104.75°) + Kiên Giang (104.5°)", 9888.9, 3679200),
        MergedProvinceInfo("TP. Đà Nẵng (Mới)", 107.75, "Đà Nẵng (107.75°) + Quảng Nam (107.75°)", 11859.6, 2765500),
        MergedProvinceInfo("TP. Hà Nội", 105.00, "Giữ nguyên địa giới Thủ đô Hà Nội (105.0°)", 3359.8, 8587100),
    )

    val PRESETS_VN2000: List<Vn2000SheetPreset> = listOf(
        Vn2000SheetPreset(
            label = "Sổ đỏ Vườn Sầu riêng Bảo Lộc (Lâm Đồng · L0=107.75°)",
            province = "Lâm Đồng (Mới)",
            lon0 = 107.75,
            sheetNumber = "18",
            plotNumber = "142",
            coordsText = "1276912.40, 504680.15\n1276928.85, 504812.60\n1276810.20, 504835.40\n1276785.50, 504702.10\n1276854.10, 504658.90",
        ),
        Vn2000SheetPreset(
            label = "Sổ đỏ Vườn Cư M'gar (Đắk Lắk · L0=108.50°)",
            province = "Đắk Lắk (Mới)",
            lon0 = 108.50,
            sheetNumber = "34",
            plotNumber = "209",
            coordsText = "1416420.10, 452310.50\n1416445.80, 452468.20\n1416310.40, 452490.75\n1416288.00, 452332.10\n1416362.50, 452295.00",
        ),
        Vn2000SheetPreset(
            label = "Sổ đỏ Vườn Chư Prông (Gia Lai · L0=108.50°)",
            province = "Gia Lai (Mới)",
            lon0 = 108.50,
            sheetNumber = "12",
            plotNumber = "88",
            coordsText = "1510240.00, 438120.00\n1510255.00, 438275.00\n1510115.00, 438282.00\n1510102.00, 438130.00",
        ),
    )

    /** Quy đổi tọa độ phẳng VN-2000 (X Bắc, Y Đông) sang WGS84 (Vĩ độ, Kinh độ). Tự động nhận diện đảo trục X/Y. */
    fun vn2000ToWgs84(xIn: Double, yIn: Double, lon0Deg: Double): GeoPoint? {
        if (xIn.isNaN() || yIn.isNaN()) return null
        var n = xIn
        var e = yIn
        if (n < e && e > 800_000.0) {
            val tmp = n; n = e; e = tmp
        }
        if (n < 700_000.0 || n > 2_700_000.0 || e < 150_000.0 || e > 850_000.0) return null

        val a = 6378137.0
        val f = 1.0 / 298.257223563
        val e2 = 2.0 * f - f * f
        val ep2 = e2 / (1.0 - e2)
        val k0 = 0.9999
        val x0 = 500000.0
        val y0 = 0.0
        val lon0 = Math.toRadians(lon0Deg)

        val m = (n - y0) / k0
        val mu = m / (a * (1.0 - e2 / 4.0 - 3.0 * e2 * e2 / 64.0 - 5.0 * e2 * e2 * e2 / 256.0))
        val e1 = (1.0 - sqrt(1.0 - e2)) / (1.0 + sqrt(1.0 - e2))
        val phi1 = mu +
            (3.0 * e1 / 2.0 - 27.0 * e1 * e1 * e1 / 32.0) * sin(2.0 * mu) +
            (21.0 * e1 * e1 / 16.0 - 55.0 * e1 * e1 * e1 * e1 / 32.0) * sin(4.0 * mu) +
            (151.0 * e1 * e1 * e1 / 96.0) * sin(6.0 * mu) +
            (1097.0 * e1 * e1 * e1 * e1 / 512.0) * sin(8.0 * mu)

        val sinPhi1 = sin(phi1)
        val cosPhi1 = cos(phi1)
        val tanPhi1 = kotlin.math.tan(phi1)
        val n1 = a / sqrt(1.0 - e2 * sinPhi1 * sinPhi1)
        val t1 = tanPhi1 * tanPhi1
        val c1 = ep2 * cosPhi1 * cosPhi1
        val r1 = a * (1.0 - e2) / Math.pow(1.0 - e2 * sinPhi1 * sinPhi1, 1.5)
        val d = (e - x0) / (n1 * k0)

        val latVn = phi1 - (n1 * tanPhi1 / r1) * (
            d * d / 2.0 -
                (5.0 + 3.0 * t1 + 10.0 * c1 - 4.0 * c1 * c1 - 9.0 * ep2) * Math.pow(d, 4.0) / 24.0 +
                (61.0 + 90.0 * t1 + 298.0 * c1 + 45.0 * t1 * t1 - 252.0 * ep2 - 3.0 * c1 * c1) * Math.pow(d, 6.0) / 720.0
            )
        val lonVn = lon0 + (
            d -
                (1.0 + 2.0 * t1 + c1) * Math.pow(d, 3.0) / 6.0 +
                (5.0 - 2.0 * c1 + 28.0 * t1 - 3.0 * c1 * c1 + 8.0 * ep2 + 24.0 * t1 * t1) * Math.pow(d, 5.0) / 120.0
            ) / cosPhi1

        val sinLat = sin(latVn)
        val cosLat = cos(latVn)
        val sinLon = sin(lonVn)
        val cosLon = cos(lonVn)
        val nu = a / sqrt(1.0 - e2 * sinLat * sinLat)
        val xCart = nu * cosLat * cosLon
        val yCart = nu * cosLat * sinLon
        val zCart = nu * (1.0 - e2) * sinLat

        val dx = -191.90441429
        val dy = -39.30318279
        val dz = -111.45032835
        val as2rad = Math.PI / (180.0 * 3600.0)
        val rx = 0.00928836 * as2rad
        val ry = -0.01975479 * as2rad
        val rz = 0.00427372 * as2rad
        val s = 1.0 + 0.252906278 * 1e-6

        val xW = dx + s * (xCart - rz * yCart + ry * zCart)
        val yW = dy + s * (rz * xCart + yCart - rx * zCart)
        val zW = dz + s * (-ry * xCart + rx * yCart + zCart)

        val p = sqrt(xW * xW + yW * yW)
        val lonWgs = Math.toDegrees(atan2(yW, xW))
        var latWgsRad = atan2(zW, p * (1.0 - e2))
        repeat(5) {
            val sinL = sin(latWgsRad)
            val nuW = a / sqrt(1.0 - e2 * sinL * sinL)
            latWgsRad = atan2(zW + e2 * nuW * sinL, p)
        }
        val latWgs = Math.toDegrees(latWgsRad)
        if (abs(latWgs) > 90.0 || abs(lonWgs) > 180.0) return null
        return GeoPoint(latWgs, lonWgs)
    }

    fun parseVn2000Table(rawText: String, lon0Deg: Double = activeLon0): List<GeoPoint> {
        return rawText.lines().mapNotNull { line ->
            val nums = line.trim().split(Regex("[,;|\\s]+")).mapNotNull { it.replace(',', '.').toDoubleOrNull() }
            if (nums.size >= 2) vn2000ToWgs84(nums[0], nums[1], lon0Deg) else null
        }
    }

    fun parseOrError(text: String): ParseResult {
        val vnPts = parseVn2000Table(text, activeLon0)
        return if (vnPts.size >= 3) {
            ParseResult(
                listOf(ParsedFeature("Ranh Sổ đỏ VN-2000 ($activeProvince · L0=$activeLon0°)", Geom.POLYGON, vnPts, "ranh boundary vn2000")),
                emptyList(),
                listOf("Đã tự động quy đổi ${vnPts.size} đỉnh góc ranh Sổ đỏ VN-2000 ($activeProvince · KT trục $activeLon0°) sang WGS84."),
            )
        } else {
            ParseResult(
                emptyList(),
                listOf("Không nhận ra định dạng. Hỗ trợ KML, KMZ, GeoJSON hoặc bảng tọa độ góc ranh Sổ đỏ VN-2000 (ít nhất 3 đỉnh X, Y)."),
                emptyList(),
            )
        }
    }
}
object GeoEndMarker { init {
    }
}
