package com.example.test_tablet.data.vwing

import kotlin.math.sqrt

/** Quy tắc cho bộ ảnh theo thời gian và điểm ứng viên (P1-02). Thuần Kotlin để kiểm thử được. */
object Imagery {
    const val MATCH_RADIUS_M = 3.0
    private const val DUPLICATE_M = 0.3
    private const val AMBIGUOUS_GAP_M = 1.0

    fun validate(s: ImageSet): String? {
        if (s.name.isBlank()) return "Nhập tên bộ ảnh."
        if (s.source.isBlank()) return "Nhập nguồn ảnh (người chụp hoặc đơn vị cung cấp)."
        if (s.capturedAt > Dates.now() + 86_400_000L) return "Ngày chụp không thể ở tương lai."
        if (s.file.isBlank() || s.widthPx <= 0 || s.heightPx <= 0) return "Ảnh không hợp lệ."
        s.bounds?.let { if (!it.valid) return "Khung địa lý không hợp lệ (vĩ độ nam < bắc, kinh độ tây < đông, trong phạm vi hợp lệ)." }
        s.gsdCm?.let { if (it <= 0 || it.isNaN()) return "Kích thước điểm ảnh (cm/pixel) phải lớn hơn 0." }
        if (s.kind == ImageKind.ORTHO && s.bounds == null) return "Ảnh trực giao cần có khung địa lý. Nếu chưa có, nhập như ảnh thực địa."
        if ((s.kind == ImageKind.BAND_RED || s.kind == ImageKind.BAND_NIR) && s.sensor != SensorKind.MULTISPECTRAL)
            return "Băng Red/NIR phải đến từ cảm biến đa phổ. Ảnh RGB thông thường không chứa băng cận hồng ngoại."
        return null
    }

    fun widthM(b: GeoBounds): Double = Geo.distanceM(GeoPoint(b.center.lat, b.west), GeoPoint(b.center.lat, b.east))
    fun heightM(b: GeoBounds): Double = Geo.distanceM(GeoPoint(b.south, b.center.lng), GeoPoint(b.north, b.center.lng))

    /** Kích thước điểm ảnh ngoài thực địa (m/pixel): ưu tiên giá trị khai báo, nếu không suy ra từ khung địa lý. */
    fun pixelSizeM(s: ImageSet): Double? =
        s.gsdCm?.let { it / 100.0 } ?: s.bounds?.let { maxOf(widthM(it) / s.widthPx, heightM(it) / s.heightPx) }

    /** Điểm mới chưa trùng điểm đã có (trong 0,3 m) hoặc trùng nhau, nên nhập lại cùng một tệp không nhân đôi. */
    fun newDetections(set: ImageSet, points: List<GeoPoint>): List<Detection> {
        val kept = mutableListOf<GeoPoint>().apply { addAll(set.detections.map { it.point }) }
        val out = mutableListOf<Detection>()
        for (p in points) {
            if (kept.any { Geo.distanceM(it, p) <= DUPLICATE_M }) continue
            kept += p; out += Detection(newId(), p)
        }
        return out
    }

    sealed class Match {
        data class Unique(val tree: Tree, val distM: Double) : Match()
        /** Nhiều cây ở khoảng cách gần như nhau: người dùng phải chọn. */
        data class Ambiguous(val options: List<Pair<Tree, Double>>) : Match()
        object None : Match()
    }

    fun nearbyTrees(d: GardenData, p: GeoPoint, maxM: Double = MATCH_RADIUS_M): List<Pair<Tree, Double>> =
        d.activeTrees.mapNotNull { t -> t.pos?.let { t to Geo.distanceM(it, p) } }.filter { it.second <= maxM }.sortedBy { it.second }

    fun suggest(d: GardenData, det: Detection, maxM: Double = MATCH_RADIUS_M): Match {
        val near = nearbyTrees(d, det.point, maxM)
        return when {
            near.isEmpty() -> Match.None
            near.size == 1 -> Match.Unique(near[0].first, near[0].second)
            near[1].second - near[0].second < AMBIGUOUS_GAP_M -> Match.Ambiguous(near)
            else -> Match.Unique(near[0].first, near[0].second)
        }
    }

    fun nearestTree(d: GardenData, p: GeoPoint, maxM: Double = 8.0): Pair<Tree, Double>? = nearbyTrees(d, p, maxM).firstOrNull()

    /**
     * Độ lệch hệ thống (mét) giữa vị trí ảnh và vị trí cây trong hồ sơ: độ dài của vectơ lệch trung vị qua các điểm ghép duy nhất.
     * Cần ít nhất 5 điểm; nếu lệch lớn, ảnh hoặc hồ sơ có thể bị dịch và không nên xác nhận hàng loạt.
     */
    fun systematicOffsetM(d: GardenData, set: ImageSet): Double? {
        val vecs = set.detections.mapNotNull { det ->
            (suggest(d, det) as? Match.Unique)?.let { m ->
                val tp = m.tree.pos ?: return@let null
                val (dx, dy) = Geo.toMeters(det.point, tp)    // từ vị trí cây đến điểm ảnh
                dx to dy
            }
        }
        if (vecs.size < 5) return null
        fun med(v: List<Double>) = v.sorted().let { if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }
        val mx = med(vecs.map { it.first }); val my = med(vecs.map { it.second })
        return sqrt(mx * mx + my * my)
    }

    /** Tọa độ → vị trí chuẩn hóa (0..1) trong khung ảnh; null nếu ngoài khung. */
    fun toUnit(b: GeoBounds, p: GeoPoint): Pair<Double, Double>? {
        if (!b.contains(p)) return null
        return (p.lng - b.west) / (b.east - b.west) to (b.north - p.lat) / (b.north - b.south)
    }

    fun fromUnit(b: GeoBounds, u: Double, v: Double): GeoPoint = GeoPoint(b.north - v * (b.north - b.south), b.west + u * (b.east - b.west))

    /** Đọc điểm (Point) từ GeoJSON: dùng cho kết quả đếm cây của công cụ bên ngoài. */
    fun parsePoints(geoJson: String): Pair<List<GeoPoint>, List<String>> {
        val r = Geo.parseGeoJson(geoJson)
        val pts = r.features.filter { it.geom == Geom.POINT }.mapNotNull { it.points.firstOrNull() }
        val notes = r.errors.toMutableList()
        val skipped = r.features.count { it.geom != Geom.POINT }
        if (skipped > 0) notes += "Bỏ qua $skipped đối tượng không phải điểm."
        return pts to notes
    }

    /** Hai khung ảnh có chồng lên nhau đủ để so sánh (>= 50% diện tích khung nhỏ hơn). */
    fun comparable(a: ImageSet, b: ImageSet): Boolean {
        val ba = a.bounds ?: return false; val bb = b.bounds ?: return false
        if (!ba.overlaps(bb)) return false
        val w = minOf(ba.east, bb.east) - maxOf(ba.west, bb.west)
        val h = minOf(ba.north, bb.north) - maxOf(ba.south, bb.south)
        val inter = w * h
        val small = minOf((ba.east - ba.west) * (ba.north - ba.south), (bb.east - bb.west) * (bb.north - bb.south))
        return inter >= 0.5 * small
    }

}
