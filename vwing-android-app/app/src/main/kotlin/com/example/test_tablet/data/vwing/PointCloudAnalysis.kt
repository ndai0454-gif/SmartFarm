package com.example.test_tablet.data.vwing

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import kotlin.math.*

enum class CloudFormat { CSV, LAS }
enum class CloudCrs(val label: String) {
    WGS84("WGS84 · kinh độ/vĩ độ (EPSG:4326)"),
    UTM48N("WGS84 / UTM 48N · mét (EPSG:32648)"),
    UTM49N("WGS84 / UTM 49N · mét (EPSG:32649)")
}

data class CloudPoint(val position: GeoPoint, val z: Double, val classification: Int?)
data class CloudReport(
    val totalPoints: Int, val inRegion: Int, val groundPoints: Int,
    val minZ: Double, val maxZ: Double, val meanZ: Double, val p95Z: Double,
    val areaHa: Double, val density: Double, val preview: List<CloudPoint>
)

/** Local, bounded analysis of actual input. Z retains the file's vertical datum/unit;
 * density is retained points/m². No terrain normalization or canopy-height inference.
 * CRS is explicitly supplied by the caller, never guessed from coordinates/header.
 * LAS withheld records are excluded; totalPoints still counts all source records.
 * LAS layout: ASPRS LAS 1.4 R15; UTM inverse: USGS PP 1395, eq. 8-17 through 8-20.
 */
object PointCloudAnalysis {
    const val MAX_BYTES = 20 * 1024 * 1024
    const val MAX_POINTS = 250_000
    const val MAX_PREVIEW = 2_000
    private const val EARTH_R = 6_371_000.0
    private data class XY(val x: Double, val y: Double)

    fun analyze(input: InputStream, format: CloudFormat, crs: CloudCrs, ring: List<GeoPoint>): CloudReport {
        val polygon = polygon(ring)
        val origin = ring.first()
        fun local(p: GeoPoint) = XY(Math.toRadians(p.lng - origin.lng) * EARTH_R * cos(Math.toRadians(origin.lat)),
            Math.toRadians(p.lat - origin.lat) * EARTH_R)
        val areaM2 = abs(polygon.indices.sumOf { i ->
            val a = polygon[i]; val b = polygon[(i + 1) % polygon.size]; a.x * b.y - b.x * a.y
        }) / 2
        val bytes = readBounded(input)
        val clipped = ArrayList<CloudPoint>()
        val minX = polygon.minOf { it.x }; val maxX = polygon.maxOf { it.x }
        val minY = polygon.minOf { it.y }; val maxY = polygon.maxOf { it.y }
        fun retain(point: CloudPoint) {
            val p = local(point.position)
            if (p.x >= minX - 1e-6 && p.x <= maxX + 1e-6 && p.y >= minY - 1e-6 && p.y <= maxY + 1e-6 && contains(p, polygon)) clipped.add(point)
        }
        val total = when (format) {
            CloudFormat.CSV -> csv(bytes, crs, ::retain)
            CloudFormat.LAS -> las(bytes, crs, ::retain)
        }
        require(clipped.isNotEmpty()) { "Không có điểm hợp lệ trong vùng đã chọn. Kiểm tra ranh vùng và hệ tọa độ của tệp." }
        val heights = clipped.map { it.z }.sorted()
        // Nearest-rank percentile, computed over every retained point, before preview sampling.
        val p95 = heights[(ceil(heights.size * 0.95).toInt() - 1).coerceAtLeast(0)]
        val preview = if (clipped.size <= MAX_PREVIEW) clipped.toList() else List(MAX_PREVIEW) { i ->
            clipped[(i.toLong() * (clipped.size - 1) / (MAX_PREVIEW - 1)).toInt()]
        }
        // Divide before summing to avoid overflow for finite, large absolute elevations.
        return CloudReport(total, clipped.size, clipped.count { it.classification == 2 },
            heights.first(), heights.last(), heights.sumOf { it / heights.size }, p95,
            areaM2 / 10_000, clipped.size / areaM2, preview)
    }

    private fun readBounded(input: InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (n == 0) { // Also handles unusual streams that return zero without EOF.
                val b = input.read(); if (b < 0) break
                require(out.size() < MAX_BYTES) { "Tệp vượt giới hạn 20 MB." }; out.write(b)
            } else {
                require(n <= MAX_BYTES - out.size()) { "Tệp vượt giới hạn 20 MB." }; out.write(buf, 0, n)
            }
        }
        require(out.size() > 0) { "Tệp đám mây điểm rỗng." }
        return out.toByteArray()
    }

    private fun csv(bytes: ByteArray, crs: CloudCrs, retain: (CloudPoint) -> Unit): Int {
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try { decoder.decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF") }
        catch (_: java.nio.charset.CharacterCodingException) { throw IllegalArgumentException("CSV phải là văn bản UTF-8 hợp lệ.") }
        var count = 0; var columns = 0; var delimiter: Char? = null; var first = true
        text.lineSequence().forEachIndexed { row, raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEachIndexed
            if (first) delimiter = when { ',' in line -> ','; ';' in line -> ';'; else -> null }
            val cells = if (delimiter == null) line.split(Regex("\\s+")) else line.split(delimiter!!).map { it.trim() }
            if (first) {
                first = false
                val names = cells.map { it.lowercase(java.util.Locale.ROOT) }
                if (names.first() == "x") {
                    require(names == listOf("x", "y", "z") || names == listOf("x", "y", "z", "classification")) {
                        "Tiêu đề CSV phải là x,y,z hoặc x,y,z,classification."
                    }
                    columns = names.size; return@forEachIndexed
                }
                columns = cells.size
                require(columns == 3 || columns == 4) { "CSV cần 3 cột x,y,z và cột classification tùy chọn." }
            }
            require(cells.size == columns) { "CSV dòng ${row + 1}: số cột không khớp." }
            require(count < MAX_POINTS) { "Tệp vượt giới hạn 250.000 điểm." }
            val values = cells.take(3).map { it.toDoubleOrNull() }
            require(values.all { it != null && it.isFinite() }) { "CSV dòng ${row + 1}: x, y, z phải là số hữu hạn." }
            val classification = if (columns == 4) cells[3].toIntOrNull().also {
                require(it != null && it in 0..255) { "CSV dòng ${row + 1}: classification phải là số nguyên 0–255." }
            } else null
            retain(point(values[0]!!, values[1]!!, values[2]!!, classification, crs))
            count++
        }
        require(count > 0) { "CSV không chứa điểm dữ liệu." }
        return count
    }

    private fun las(bytes: ByteArray, crs: CloudCrs, retain: (CloudPoint) -> Unit): Int {
        require(bytes.size >= 227 && bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(76, 65, 83, 70))) { "Tệp LAS thiếu tiêu đề LASF hợp lệ." }
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        fun u8(pos: Int) = b.get(pos).toInt() and 255
        fun u16(pos: Int) = b.getShort(pos).toInt() and 65535
        fun u32(pos: Int) = b.getInt(pos).toLong() and 0xffffffffL
        val minor = u8(25)
        require(u8(24) == 1 && minor in 1..4) { "Chỉ hỗ trợ LAS 1.1–1.4 chưa nén. Hãy xuất lại tệp LAS." }
        val headerSize = u16(94)
        val minimumHeader = when (minor) { 4 -> 375; 3 -> 235; else -> 227 }
        require(headerSize >= minimumHeader && bytes.size >= headerSize) { "Tiêu đề LAS bị cắt hoặc có kích thước không hợp lệ." }
        val offset = u32(96)
        require(offset >= headerSize && offset <= bytes.size) { "Vị trí dữ liệu LAS không hợp lệ hoặc tệp bị cắt." }
        val format = u8(104)
        require(format and 0xc0 == 0) { "LAZ/LAS nén chưa được hỗ trợ. Hãy giải nén thành LAS trước khi nhập." }
        val allowed = when (minor) { 1 -> 1; 2 -> 3; 3 -> 5; else -> 10 }
        require(format in 0..allowed) { "Định dạng điểm LAS $format không được hỗ trợ cho phiên bản 1.$minor." }
        val recordSize = u16(105)
        val minimumSizes = intArrayOf(20, 28, 26, 34, 57, 63, 30, 36, 38, 59, 67)
        require(recordSize >= minimumSizes[format]) { "Bản ghi điểm LAS ngắn hơn định dạng quy định." }
        var count = u32(107)
        if (minor == 4) {
            val extended = b.getLong(247)
            require(extended >= 0) { "Số điểm LAS vượt giới hạn 250.000 điểm." }
            if (extended > 0) {
                require(count == 0L || count == extended) { "Số điểm trong tiêu đề LAS không nhất quán." }; count = extended
            }
        }
        require(count in 1..MAX_POINTS.toLong()) { "LAS rỗng hoặc vượt giới hạn 250.000 điểm." }
        require(offset + count * recordSize <= bytes.size.toLong()) { "Tệp LAS bị cắt: thiếu bản ghi điểm so với tiêu đề." }
        // Validate VLR bounds and detect LASzip even if its compressed point-format flag is missing.
        var vlrOffset = headerSize.toLong()
        val vlrCount = u32(100)
        require(vlrCount <= (offset - headerSize) / 54) { "Số bản ghi VLR trong LAS không hợp lệ." }
        repeat(vlrCount.toInt()) {
            require(vlrOffset + 54 <= offset) { "Tiêu đề VLR của LAS bị cắt." }
            val pos = vlrOffset.toInt()
            val user = String(bytes, pos + 2, 16, Charsets.US_ASCII).trimEnd('\u0000', ' ')
            require(!user.equals("laszip encoded", ignoreCase = true)) { "LAZ/LAS nén chưa được hỗ trợ. Hãy giải nén thành LAS trước khi nhập." }
            vlrOffset += 54 + u16(pos + 20)
            require(vlrOffset <= offset) { "Dữ liệu VLR của LAS bị cắt." }
        }
        val scales = List(3) { b.getDouble(131 + it * 8) }
        val offsets = List(3) { b.getDouble(155 + it * 8) }
        require(scales.all { it.isFinite() && it > 0 } && offsets.all { it.isFinite() }) { "Hệ số tỉ lệ hoặc độ lệch LAS không hợp lệ." }
        repeat(count.toInt()) { i ->
            val p = offset.toInt() + i * recordSize
            val x = b.getInt(p) * scales[0] + offsets[0]
            val y = b.getInt(p + 4) * scales[1] + offsets[1]
            val z = b.getInt(p + 8) * scales[2] + offsets[2]
            val classification = if (format <= 5) u8(p + 15) and 31 else u8(p + 16)
            val withheld = if (format <= 5) u8(p + 15) and 128 != 0 else u8(p + 15) and 4 != 0
            val point = point(x, y, z, classification, crs) // Validate withheld coordinates too.
            if (!withheld) retain(point)
        }
        return count.toInt()
    }

    private fun point(x: Double, y: Double, z: Double, classification: Int?, crs: CloudCrs): CloudPoint {
        require(x.isFinite() && y.isFinite() && z.isFinite()) { "Tệp chứa tọa độ/cao độ không hữu hạn." }
        val p = when (crs) {
            CloudCrs.WGS84 -> GeoPoint(y, x)
            CloudCrs.UTM48N, CloudCrs.UTM49N -> {
                require(x in 100_000.0..900_000.0 && y in 0.0..9_400_000.0) { "Tọa độ không hợp lệ cho UTM Bắc. Kiểm tra hệ tọa độ đã chọn." }
                inverseUtm(x, y, if (crs == CloudCrs.UTM48N) 48 else 49).also {
                    require(it.lat in 0.0..84.0) { "Tọa độ nằm ngoài phạm vi UTM Bắc 0–84 độ." }
                }
            }
        }
        require(p.lat.isFinite() && p.lng.isFinite() && p.lat in -90.0..90.0 && p.lng in -180.0..180.0) {
            "Tọa độ ngoài phạm vi WGS84. CSV dùng x = kinh độ, y = vĩ độ; hãy kiểm tra hệ tọa độ."
        }
        return CloudPoint(p, z, classification)
    }

    private fun inverseUtm(x: Double, y: Double, zone: Int): GeoPoint {
        val a = 6378137.0; val e2 = 0.0066943799901413165; val k0 = 0.9996
        val ep2 = e2 / (1 - e2)
        val mu = (y / k0) / (a * (1 - e2 / 4 - 3 * e2.pow(2) / 64 - 5 * e2.pow(3) / 256))
        val e1 = (1 - sqrt(1 - e2)) / (1 + sqrt(1 - e2))
        val phi = mu + (3 * e1 / 2 - 27 * e1.pow(3) / 32) * sin(2 * mu) +
            (21 * e1.pow(2) / 16 - 55 * e1.pow(4) / 32) * sin(4 * mu) +
            151 * e1.pow(3) / 96 * sin(6 * mu) + 1097 * e1.pow(4) / 512 * sin(8 * mu)
        val n = a / sqrt(1 - e2 * sin(phi).pow(2))
        val r = a * (1 - e2) / (1 - e2 * sin(phi).pow(2)).pow(1.5)
        val t = tan(phi).pow(2); val c = ep2 * cos(phi).pow(2); val d = (x - 500000) / (n * k0)
        val lat = phi - n * tan(phi) / r * (d.pow(2) / 2 - (5 + 3 * t + 10 * c - 4 * c.pow(2) - 9 * ep2) * d.pow(4) / 24 +
            (61 + 90 * t + 298 * c + 45 * t.pow(2) - 252 * ep2 - 3 * c.pow(2)) * d.pow(6) / 720)
        val lng = (d - (1 + 2 * t + c) * d.pow(3) / 6 +
            (5 - 2 * c + 28 * t - 3 * c.pow(2) + 8 * ep2 + 24 * t.pow(2)) * d.pow(5) / 120) / cos(phi)
        return GeoPoint(Math.toDegrees(lat), zone * 6 - 183 + Math.toDegrees(lng))
    }

    private fun polygon(ring: List<GeoPoint>): List<XY> {
        val p = if (ring.size > 1 && ring.first() == ring.last()) ring.dropLast(1) else ring
        require(p.size in 3..512 && p.distinct().size == p.size) { "Ranh vùng cần 3–512 đỉnh khác nhau (có thể lặp đỉnh đầu để đóng ranh)." }
        require(p.all { it.lat.isFinite() && it.lng.isFinite() && it.lat in -85.0..85.0 && it.lng in -180.0..180.0 }) { "Ranh vùng chứa tọa độ không hợp lệ; hỗ trợ vùng từ 85° Nam đến 85° Bắc." }
        require(p.maxOf { it.lng } - p.minOf { it.lng } < 180) { "Chưa hỗ trợ ranh vùng đi qua kinh tuyến 180°." }
        val o = p.first()
        val poly = p.map { XY(Math.toRadians(it.lng - o.lng) * EARTH_R * cos(Math.toRadians(o.lat)), Math.toRadians(it.lat - o.lat) * EARTH_R) }
        for (i in poly.indices) for (j in i + 1 until poly.size) {
            val i2 = (i + 1) % poly.size; val j2 = (j + 1) % poly.size
            if (i2 == j || j2 == i) continue
            require(!intersect(poly[i], poly[i2], poly[j], poly[j2])) { "Ranh vùng tự cắt hoặc có cạnh chồng nhau." }
        }
        val area = abs(poly.indices.sumOf { i -> val a = poly[i]; val b = poly[(i + 1) % poly.size]; a.x * b.y - b.x * a.y }) / 2
        require(area.isFinite() && area > 0.0001) { "Ranh vùng có diện tích bằng 0 hoặc quá nhỏ." }
        return poly
    }

    private fun cross(a: XY, b: XY, p: XY) = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
    private fun onEdge(p: XY, a: XY, b: XY): Boolean {
        val length = hypot(b.x - a.x, b.y - a.y)
        return abs(cross(a, b, p)) <= 1e-6 * length &&
            p.x >= min(a.x, b.x) - 1e-6 && p.x <= max(a.x, b.x) + 1e-6 &&
            p.y >= min(a.y, b.y) - 1e-6 && p.y <= max(a.y, b.y) + 1e-6
    }
    private fun intersect(a: XY, b: XY, c: XY, d: XY): Boolean {
        if (onEdge(a, c, d) || onEdge(b, c, d) || onEdge(c, a, b) || onEdge(d, a, b)) return true
        return (cross(a, b, c) > 0) != (cross(a, b, d) > 0) && (cross(c, d, a) > 0) != (cross(c, d, b) > 0)
    }
    private fun contains(p: XY, polygon: List<XY>): Boolean {
        var inside = false
        for (i in polygon.indices) {
            val a = polygon[i]; val b = polygon[(i + 1) % polygon.size]
            if (onEdge(p, a, b)) return true
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
        }
        return inside
    }
}
