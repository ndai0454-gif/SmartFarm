package com.example.test_tablet.data.vwing

import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Chỉ số thực vật NDVI (P2-01). Nguyên tắc:
 *  - chỉ tính khi có băng Đỏ (Red) và cận hồng ngoại (NIR) từ cảm biến đa phổ; ảnh RGB không bao giờ thành NDVI;
 *  - ghi rõ độ phân giải, ngày và vùng thiếu dữ liệu; không gán một pixel lớn hơn tán cho từng cây;
 *  - không tự chẩn đoán bệnh hay thiếu dinh dưỡng.
 */
object Ndvi {
    const val NODATA = 255
    private const val MAX_Q = 254

    /** Lưới giá trị đã lượng tử hóa: 0..254 ↔ -1..1, 255 = thiếu dữ liệu. */
    class Grid(val w: Int, val h: Int, val q: ByteArray) {
        fun at(x: Int, y: Int): Int = q[y * w + x].toInt() and 0xFF
        fun value(x: Int, y: Int): Double? = at(x, y).let { if (it == NODATA) null else dequant(it) }
    }

    fun quant(v: Double): Int = (((v + 1.0) / 2.0) * MAX_Q).roundToInt().coerceIn(0, MAX_Q)
    fun dequant(q: Int): Double = q.toDouble() / MAX_Q * 2.0 - 1.0

    // ---------- kiểm tra đầu vào ----------

    fun checkBands(red: ImageSet, nir: ImageSet): String? {
        if (red.kind != ImageKind.BAND_RED) return "Ảnh \"${red.name}\" không phải băng Đỏ (Red)."
        if (nir.kind != ImageKind.BAND_NIR) return "Ảnh \"${nir.name}\" không phải băng cận hồng ngoại (NIR)."
        if (red.sensor != SensorKind.MULTISPECTRAL || nir.sensor != SensorKind.MULTISPECTRAL)
            return "NDVI chỉ tính từ cảm biến đa phổ có băng Red và NIR. Ảnh RGB thông thường không đủ điều kiện."
        if (red.widthPx != nir.widthPx || red.heightPx != nir.heightPx) return "Hai băng khác kích thước (${red.widthPx}×${red.heightPx} và ${nir.widthPx}×${nir.heightPx}); cần cùng lưới điểm ảnh."
        if (Math.abs(red.capturedAt - nir.capturedAt) > 86_400_000L) return "Hai băng chụp cách nhau quá 1 ngày."
        val a = red.bounds; val b = nir.bounds
        if ((a == null) != (b == null)) return "Một băng đã định vị còn băng kia chưa; cần cùng khung địa lý."
        if (a != null && b != null && (Math.abs(a.south - b.south) > 1e-7 || Math.abs(a.north - b.north) > 1e-7 || Math.abs(a.west - b.west) > 1e-7 || Math.abs(a.east - b.east) > 1e-7))
            return "Hai băng có khung địa lý khác nhau; không thể ghép từng điểm ảnh."
        return null
    }

    fun validateRecord(v: VegIndex): String? {
        if (v.name.isBlank()) return "Nhập tên bản ghi chỉ số."
        if (v.provider.isBlank()) return "Nhập nguồn dữ liệu (đơn vị bay/cung cấp hoặc thiết bị)."
        if (v.date > Dates.now() + 86_400_000L) return "Ngày dữ liệu không thể ở tương lai."
        if (v.sensor != SensorKind.MULTISPECTRAL) return "NDVI cần dữ liệu có băng Red và NIR (cảm biến đa phổ). Ảnh RGB không thể là NDVI."
        v.resolutionM?.let { if (it <= 0 || it.isNaN()) return "Độ phân giải phải lớn hơn 0." }
        if (v.canopyRadiusM <= 0) return "Bán kính tán phải lớn hơn 0."
        v.bounds?.let { if (!it.valid) return "Khung địa lý không hợp lệ." }
        for (s in v.stats) {
            if (s.mean !in -1.0..1.0 || s.min !in -1.0..1.0 || s.max !in -1.0..1.0) return "Giá trị NDVI phải nằm trong khoảng -1 đến 1."
            if (s.min > s.max || s.mean < s.min - 1e-9 || s.mean > s.max + 1e-9) return "Giá trị trung bình/nhỏ nhất/lớn nhất không khớp nhau."
        }
        if (v.stats.any { it.scope.startsWith("tree:") }) {
            val r = v.resolutionM ?: return "Giá trị theo từng cây cần biết độ phân giải để kiểm tra có đủ tách tán cây không."
            if (r > v.canopyRadiusM) return "Độ phân giải ${"%.1f".format(r)} m/pixel lớn hơn bán kính tán ${"%.1f".format(v.canopyRadiusM)} m: không nhận giá trị theo từng cây."
        }
        if (v.source == IndexSource.COMPUTED && (v.redSetId == null || v.nirSetId == null || v.gridFile == null)) return "Bản tính trên máy cần băng Red, băng NIR và tệp lưới."
        return null
    }

    // ---------- tính toán ----------

    /** NDVI = (NIR − Red) / (NIR + Red). Mẫu bằng 0 hoặc cả hai băng bằng 0 → thiếu dữ liệu. */
    fun compute(red: IntArray, nir: IntArray, w: Int, h: Int): Grid {
        require(red.size == w * h && nir.size == w * h) { "Kích thước băng không khớp" }
        val q = ByteArray(w * h)
        for (i in 0 until w * h) {
            val r = red[i]; val n = nir[i]
            val v = if (r < 0 || n < 0 || r + n == 0) null else (n - r).toDouble() / (n + r)
            q[i] = (if (v == null) NODATA else quant(v)).toByte()
        }
        return Grid(w, h, q)
    }

    fun pixelSize(bounds: GeoBounds, w: Int, h: Int): Double = maxOf(Imagery.widthM(bounds) / w, Imagery.heightM(bounds) / h)

    private class Acc { var n = 0; var sum = 0.0; var min = 2.0; var max = -2.0; var total = 0
        fun add(v: Double?) { total++; if (v != null) { n++; sum += v; if (v < min) min = v; if (v > max) max = v } }
        fun stat(scope: String) = if (n == 0) null else IndexStat(scope, sum / n, min, max, n, 100.0 * n / total)
    }

    class Result(val stats: List<IndexStat>, val notes: List<String>)

    /**
     * Thống kê theo toàn vườn / khu / từng cây.
     *  - Toàn vườn: các điểm ảnh hợp lệ (trong ranh vườn nếu có).
     *  - Theo cây/khu: chỉ khi độ phân giải ≤ bán kính tán (đủ vài điểm ảnh trong một tán); ngược lại không tách cây.
     */
    fun summarize(d: GardenData, grid: Grid, bounds: GeoBounds, canopyRadiusM: Double): Result {
        val notes = mutableListOf<String>()
        val stats = mutableListOf<IndexStat>()
        val res = pixelSize(bounds, grid.w, grid.h)
        val lat0 = bounds.center.lat
        val mPerDegLat = 111_320.0; val mPerDegLng = 111_320.0 * cos(Math.toRadians(lat0))
        val dLat = (bounds.north - bounds.south) / grid.h; val dLng = (bounds.east - bounds.west) / grid.w
        fun centerOf(x: Int, y: Int) = GeoPoint(bounds.north - (y + 0.5) * dLat, bounds.west + (x + 0.5) * dLng)

        // toàn vườn
        val boundary = d.features.firstOrNull { it.kind == FeatureKind.BOUNDARY }?.points?.let(Geo::open)?.takeIf { it.size >= 3 }
        val all = Acc()
        for (y in 0 until grid.h) for (x in 0 until grid.w) {
            if (boundary != null && !Geo.pointInPolygon(centerOf(x, y), boundary)) continue
            all.add(grid.value(x, y))
        }
        all.stat("garden")?.let { stats += it } ?: notes.add("Không có điểm ảnh hợp lệ nào trong vùng vườn.")
        if (all.total > 0 && all.n < all.total) notes.add("Vùng thiếu dữ liệu: ${all.total - all.n}/${all.total} điểm ảnh (${"%.0f".format(100.0 * (all.total - all.n) / all.total)}%).")

        if (res > canopyRadiusM) {
            notes.add("Độ phân giải ${"%.1f".format(res)} m/pixel lớn hơn bán kính tán ${"%.1f".format(canopyRadiusM)} m: không tách được từng cây, chỉ có thống kê toàn vườn. Một điểm ảnh có thể gồm nhiều cây và nền đất.")
            return Result(stats, notes)
        }
        val rPxX = canopyRadiusM / (mPerDegLng * dLng); val rPxY = canopyRadiusM / (mPerDegLat * dLat)
        val zoneAcc = HashMap<String, Acc>()
        var outside = 0; var tooFew = 0
        for (t in d.activeTrees) {
            val p = t.pos ?: continue
            val u = Imagery.toUnit(bounds, p) ?: run { outside++; null } ?: continue
            val cx = u.first * grid.w; val cy = u.second * grid.h
            val acc = Acc()
            for (y in maxOf(0, (cy - rPxY).toInt())..minOf(grid.h - 1, (cy + rPxY).toInt())) {
                for (x in maxOf(0, (cx - rPxX).toInt())..minOf(grid.w - 1, (cx + rPxX).toInt())) {
                    val ex = (x + 0.5 - cx) / rPxX; val ey = (y + 0.5 - cy) / rPxY
                    if (ex * ex + ey * ey <= 1.0) acc.add(grid.value(x, y))
                }
            }
            if (acc.n < 3) { tooFew++; continue }
            acc.stat("tree:${t.id}")?.let { stats += it }
            val za = zoneAcc.getOrPut(t.zone) { Acc() }
            // gộp theo khu bằng cách cộng dồn các điểm ảnh của từng cây (một điểm ảnh nằm trong hai tán sẽ được tính hai lần; chấp nhận cho thống kê khu)
            za.n += acc.n; za.sum += acc.sum; za.total += acc.total; za.min = minOf(za.min, acc.min); za.max = maxOf(za.max, acc.max)
        }
        zoneAcc.forEach { (z, a) -> a.stat("zone:$z")?.let { stats += it } }
        if (outside > 0) notes.add("$outside cây có tọa độ nằm ngoài vùng ảnh nên không có giá trị.")
        if (tooFew > 0) notes.add("$tooFew cây không đủ điểm ảnh hợp lệ trong tán (ít hơn 3) nên không có giá trị.")
        val noPos = d.activeTrees.count { it.pos == null }
        if (noPos > 0) notes.add("$noPos cây chưa có vị trí nên không có giá trị.")
        return Result(stats, notes)
    }

    // ---------- chuỗi thời gian ----------

    class SeriesPoint(val index: VegIndex, val stat: IndexStat)

    fun series(d: GardenData, scope: String): List<SeriesPoint> =
        d.indices.mapNotNull { v -> v.stats.firstOrNull { it.scope == scope }?.let { SeriesPoint(v, it) } }.sortedBy { it.index.date }

    /** So sánh chỉ hợp lệ khi cả hai đều là phản xạ đã hiệu chỉnh và cùng loại độ phân giải thô. */
    fun comparable(a: VegIndex, b: VegIndex) = a.preprocess == Preprocess.REFLECTANCE && b.preprocess == Preprocess.REFLECTANCE

    fun delta(prev: SeriesPoint, cur: SeriesPoint): Double? = if (comparable(prev.index, cur.index)) cur.stat.mean - prev.stat.mean else null

    fun resolutionWarning(v: VegIndex): String? {
        val r = v.resolutionM ?: return "Nhà cung cấp không cho biết độ phân giải."
        return if (r > v.canopyRadiusM) "Độ phân giải ${"%.1f".format(r)} m/pixel: chưa đủ tách từng cây." else null
    }
}
