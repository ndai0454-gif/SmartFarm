package com.example.test_tablet.data.vwing

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Khoanh ranh vườn bằng cách đi bộ dọc bờ: ghi các điểm GPS, bỏ điểm kém chính xác, lược bớt điểm thừa rồi khép thành đa giác.
 * Độ chính xác phụ thuộc GPS của máy (thường 3–5 m ngoài trời thoáng), nên kết quả luôn kèm sai số ước tính để người dùng
 * xác nhận, không được trình bày như số đo trắc địa.
 */
object BoundaryWalk {
    /** Điểm có sai số tự báo lớn hơn mức này bị bỏ (trong rặng cây hoặc vừa mở GPS thường rất lớn). */
    const val MAX_ACC_M = 15f
    /** Chỉ ghi điểm mới khi đã đi xa điểm trước ít nhất ngần này, để đứng yên không sinh ra hàng trăm điểm. */
    const val MIN_STEP_M = 2.0
    /** Điểm cuối cách điểm đầu hơn mức này coi là chưa đi hết vòng. */
    const val MAX_CLOSE_GAP_M = 40.0
    const val SIMPLIFY_TOL_M = 1.5
    const val MIN_POINTS = 4

    class Fix(val p: GeoPoint, val accM: Float?, val at: Long = 0)

    class Result(
        val polygon: List<GeoPoint>, val areaHa: Double, val kept: Int, val dropped: Int,
        val meanAccM: Double?, val areaErrHa: Double?, val closeGapM: Double, val warnings: List<String>, val error: String?,
    ) { val ok get() = error == null }

    /** Có nên ghi điểm này khi điểm đã ghi gần nhất là [last]? Dùng khi đang đi để chỉ giữ điểm tốt. */
    fun accept(last: GeoPoint?, fix: Fix): Boolean {
        val acc = fix.accM ?: return false
        if (acc > MAX_ACC_M) return false
        return last == null || Geo.distanceM(last, fix.p) >= MIN_STEP_M
    }

    fun build(fixes: List<Fix>): Result {
        val good = mutableListOf<Fix>()
        var dropped = 0
        for (f in fixes) if (accept(good.lastOrNull()?.p, f)) good += f else dropped++
        val warnings = mutableListOf<String>()
        if (good.size < MIN_POINTS) return Result(emptyList(), 0.0, good.size, dropped, null, null, 0.0, warnings,
            "Mới có ${good.size} điểm đủ tốt (cần ít nhất $MIN_POINTS). Hãy đi chậm hơn dọc bờ vườn, ra chỗ thoáng và thử lại.")
        val gap = Geo.distanceM(good.last().p, good.first().p)
        if (gap > MAX_CLOSE_GAP_M) return Result(emptyList(), 0.0, good.size, dropped, null, null, gap, warnings,
            "Điểm cuối cách điểm đầu ${gap.toInt()} m. Hãy đi hết một vòng và quay lại gần điểm xuất phát (trong ${MAX_CLOSE_GAP_M.toInt()} m).")
        val ring = simplify(good.map { it.p })
        val accs = good.mapNotNull { it.accM }
        val meanAcc = accs.average()
        if (Geo.selfIntersects(ring)) warnings += "Đường đi tự cắt nhau (có thể đi vòng lại hoặc GPS nhảy). Kiểm tra lại hình trên bản đồ trước khi lưu."
        if (dropped > good.size) warnings += "Bỏ $dropped điểm sai số lớn hoặc trùng; nên đi lại ở chỗ thoáng hơn nếu hình lệch."
        if (meanAcc > 8) warnings += "Sai số GPS trung bình ${"%.1f".format(meanAcc)} m khá lớn; diện tích chỉ mang tính tham khảo."
        val area = Geo.polygonAreaHa(ring)
        if (area <= 0.0) return Result(emptyList(), 0.0, good.size, dropped, meanAcc, null, gap, warnings, "Đường đi không tạo thành vùng có diện tích.")
        // Sai số diện tích xấp xỉ = chu vi × sai số vị trí trung bình (dải rộng ±acc quanh ranh).
        val perimeter = ring.indices.sumOf { Geo.distanceM(ring[it], ring[(it + 1) % ring.size]) }
        val err = perimeter * meanAcc / 10_000.0
        return Result(ring, area, good.size, dropped, meanAcc, err, gap, warnings, null)
    }

    /** Douglas–Peucker trên đường khép kín, tính bằng mét; giữ nguyên điểm đầu. */
    fun simplify(points: List<GeoPoint>, tolM: Double = SIMPLIFY_TOL_M): List<GeoPoint> {
        if (points.size <= 3) return points
        val o = points[0]
        val m = points.map { Geo.toMeters(it, o) }
        // Tách vòng tại điểm xa điểm đầu nhất để mỗi nửa là một đường hở.
        val far = m.indices.maxByOrNull { hypot(m[it].first, m[it].second) } ?: return points
        val keep = BooleanArray(points.size)
        keep[0] = true; keep[far] = true
        fun dp(a: Int, b: Int, idx: (Int) -> Int) {
            if (b - a < 2) return
            val pa = m[idx(a)]; val pb = m[idx(b)]
            var best = -1.0; var bi = -1
            for (i in a + 1 until b) {
                val d = distToSeg(m[idx(i)], pa, pb)
                if (d > best) { best = d; bi = i }
            }
            if (best > tolM) { keep[idx(bi)] = true; dp(a, bi, idx); dp(bi, b, idx) }
        }
        dp(0, far, { it })
        val n = points.size
        dp(0, n - far, { (far + it) % n })       // nửa còn lại, đi từ `far` vòng về 0
        return points.filterIndexed { i, _ -> keep[i] }
    }

    private fun distToSeg(p: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val dx = b.first - a.first; val dy = b.second - a.second
        val len2 = dx * dx + dy * dy
        if (len2 == 0.0) return hypot(p.first - a.first, p.second - a.second)
        val t = (((p.first - a.first) * dx + (p.second - a.second) * dy) / len2).coerceIn(0.0, 1.0)
        return abs(hypot(p.first - (a.first + t * dx), p.second - (a.second + t * dy)))
    }
}
