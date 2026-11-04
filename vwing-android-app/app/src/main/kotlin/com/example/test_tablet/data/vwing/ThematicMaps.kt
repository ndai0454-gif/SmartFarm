package com.example.test_tablet.data.vwing

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Bản đồ chuyên đề tính ngay trên máy từ dữ liệu cây/vấn đề của vườn (không cần mạng, không cần ảnh vệ tinh):
 * mật độ Kernel, điểm nóng mới nổi (Getis-Ord Gi* + Mann-Kendall theo thời gian), bản đồ hai biến, và chồng lớp cơ hội/ràng buộc.
 * Toàn bộ là phân tích mô tả trên các điểm đã ghi; không phải chẩn đoán bệnh và không thay cho khảo sát thực địa.
 */
class ThematicGrid(val bounds: GeoBounds, val cols: Int, val rows: Int, val widthM: Double, val heightM: Double, val inside: BooleanArray) {
    val cellWm get() = widthM / cols
    val cellHm get() = heightM / rows
    val cellHa get() = cellWm * cellHm / 10_000.0
    val size get() = cols * rows
    val insideCount get() = inside.count { it }
    fun idx(r: Int, c: Int) = r * cols + c
    /** Toạ độ mét (x sang đông, y xuống nam) của điểm trong khung; hàng 0 là phía bắc. */
    fun toXY(p: GeoPoint): Pair<Double, Double> =
        Pair((p.lng - bounds.west) / (bounds.east - bounds.west) * widthM, (bounds.north - p.lat) / (bounds.north - bounds.south) * heightM)
    fun cellOf(p: GeoPoint): Int? {
        val (x, y) = toXY(p)
        val c = floor(x / cellWm).toInt(); val r = floor(y / cellHm).toInt()
        return if (c in 0 until cols && r in 0 until rows) idx(r, c) else null
    }
    fun centerXY(i: Int): Pair<Double, Double> = Pair((i % cols + .5) * cellWm, (i / cols + .5) * cellHm)
}

data class Incident(val p: GeoPoint, val at: Long, val w: Double = 1.0)
data class TreeSample(val p: GeoPoint, val fruits: Double?)

enum class EmergingClass(val label: String, val hot: Boolean) {
    NEW("Điểm nóng mới", true), CONSECUTIVE("Nóng liên tiếp", true), INTENSIFYING("Nóng tăng dần", true),
    PERSISTENT("Nóng kéo dài", true), DIMINISHING("Nóng giảm dần", true), HISTORICAL("Từng nóng, nay hết", false),
    SPORADIC("Nóng chập chờn", true), NONE("Không có mẫu hình", false),
}

class EmergingResult(val grid: ThematicGrid, val classes: Array<EmergingClass>, val bins: Int, val binLabels: List<String>, val areaHa: Map<EmergingClass, Double>)
class BivariateResult(val grid: ThematicGrid, val cls: IntArray, val aBreaks: DoubleArray, val bBreaks: DoubleArray, val areaHa: DoubleArray)
class SuitabilityResult(val grid: ThematicGrid, val score: DoubleArray, val cls: IntArray, val areaHa: DoubleArray)
class KdeResult(val grid: ThematicGrid, val density: DoubleArray, val max: Double, val bandwidthM: Double, val points: Int)

enum class BivarVar(val label: String) { ISSUES("Mật độ vấn đề"), FRUITS("Sản lượng quả"), TREES("Mật độ cây") }

object ThematicMaps {
    const val MIN_INCIDENTS_EMERGING = 8
    const val MIN_BINS = 4
    const val Z_HOT = 1.96

    /** Lưới phủ ranh vùng: tối đa ~[maxCells] ô, mỗi ô tối thiểu [minCellM] mét. */
    fun grid(ring: List<GeoPoint>, maxCells: Int = 14_400, minCellM: Double = 2.0): ThematicGrid? {
        val poly = Geo.open(ring)
        if (poly.size < 3) return null
        val b = GeoBounds(poly.minOf { it.lat }, poly.minOf { it.lng }, poly.maxOf { it.lat }, poly.maxOf { it.lng })
        if (!b.valid) return null
        val w = Geo.distanceM(GeoPoint(b.center.lat, b.west), GeoPoint(b.center.lat, b.east))
        val h = Geo.distanceM(GeoPoint(b.south, b.center.lng), GeoPoint(b.north, b.center.lng))
        if (w < 1.0 || h < 1.0) return null
        val cell = max(minCellM, sqrt(w * h / maxCells))
        val cols = max(1, ceil(w / cell).toInt()); val rows = max(1, ceil(h / cell).toInt())
        val dLat = (b.north - b.south) / rows; val dLng = (b.east - b.west) / cols
        val inside = BooleanArray(cols * rows) { i ->
            Geo.pointInPolygon(GeoPoint(b.north - (i / cols + .5) * dLat, b.west + (i % cols + .5) * dLng), poly)
        }
        return if (inside.none { it }) null else ThematicGrid(b, cols, rows, w, h, inside)
    }

    /** Kernel quartic (như ArcGIS Kernel Density): mật độ theo "điểm trên mỗi ha", chỉ tính ô nằm trong vùng. */
    fun kde(g: ThematicGrid, pts: List<Pair<GeoPoint, Double>>, bandwidthM: Double): KdeResult {
        require(bandwidthM > 0)
        val d = DoubleArray(g.size)
        val k = 3.0 / (PI * bandwidthM * bandwidthM)
        var used = 0
        for ((p, w) in pts) {
            val (x, y) = g.toXY(p)
            if (x < -bandwidthM || y < -bandwidthM || x > g.widthM + bandwidthM || y > g.heightM + bandwidthM) continue
            used++
            val c0 = max(0, floor((x - bandwidthM) / g.cellWm).toInt()); val c1 = min(g.cols - 1, floor((x + bandwidthM) / g.cellWm).toInt())
            val r0 = max(0, floor((y - bandwidthM) / g.cellHm).toInt()); val r1 = min(g.rows - 1, floor((y + bandwidthM) / g.cellHm).toInt())
            for (r in r0..r1) for (c in c0..c1) {
                val i = g.idx(r, c); if (!g.inside[i]) continue
                val dx = (c + .5) * g.cellWm - x; val dy = (r + .5) * g.cellHm - y
                val u = (dx * dx + dy * dy) / (bandwidthM * bandwidthM)
                if (u < 1) d[i] += w * k * (1 - u) * (1 - u) * 10_000.0
            }
        }
        return KdeResult(g, d, d.indices.filter { g.inside[it] }.maxOfOrNull { d[it] } ?: 0.0, bandwidthM, used)
    }

    /** Thống kê Getis-Ord Gi* (trọng số nhị phân, gồm cả ô đang xét) cho một lưới đếm. */
    fun giStar(g: ThematicGrid, x: DoubleArray, radiusCells: Int): DoubleArray {
        val idxs = (0 until g.size).filter { g.inside[it] }
        val n = idxs.size
        val z = DoubleArray(g.size)
        if (n < 3) return z
        val mean = idxs.sumOf { x[it] } / n
        val s = sqrt(idxs.sumOf { x[it] * x[it] } / n - mean * mean)
        if (s <= 1e-12) return z
        for (i in idxs) {
            val r = i / g.cols; val c = i % g.cols
            var sum = 0.0; var cnt = 0
            for (rr in max(0, r - radiusCells)..min(g.rows - 1, r + radiusCells)) for (cc in max(0, c - radiusCells)..min(g.cols - 1, c + radiusCells)) {
                val j = g.idx(rr, cc); if (!g.inside[j]) continue
                sum += x[j]; cnt++
            }
            val denom = s * sqrt((n * cnt.toDouble() - cnt.toDouble() * cnt) / (n - 1))
            z[i] = if (denom <= 1e-12) 0.0 else (sum - mean * cnt) / denom
        }
        return z
    }

    /** Mann-Kendall: trả (S chuẩn hoá Z, có ý nghĩa 95% hay không). Cần ≥ [MIN_BINS] mốc; không hiệu chỉnh đồng hạng. */
    fun mannKendallZ(series: DoubleArray): Double {
        val n = series.size
        if (n < MIN_BINS) return 0.0
        var s = 0.0
        for (i in 0 until n - 1) for (j in i + 1 until n) s += sign(series[j] - series[i])
        val v = n * (n - 1.0) * (2 * n + 5.0) / 18.0
        return when { s > 0 -> (s - 1) / sqrt(v); s < 0 -> (s + 1) / sqrt(v); else -> 0.0 }
    }

    /**
     * Điểm nóng mới nổi (rút gọn từ Emerging Hot Spot Analysis của Esri): chia thời gian thành [bins] mốc bằng nhau,
     * đếm sự cố mỗi ô mỗi mốc, tính Gi* từng mốc rồi xếp loại theo chuỗi mốc "nóng" (z ≥ 1,96) và xu hướng Mann-Kendall của z.
     * Chỉ có 8 loại (Esri có 17, kể cả điểm lạnh); không hiệu chỉnh kiểm định nhiều lần (FDR).
     */
    fun emerging(g: ThematicGrid, incidents: List<Incident>, bins: Int, radiusCells: Int = 2): EmergingResult? {
        if (bins < MIN_BINS || incidents.size < MIN_INCIDENTS_EMERGING) return null
        val t0 = incidents.minOf { it.at }; val t1 = incidents.maxOf { it.at }
        if (t1 <= t0) return null
        val span = (t1 - t0 + 1).toDouble() / bins
        val counts = Array(bins) { DoubleArray(g.size) }
        for (e in incidents) {
            val cell = g.cellOf(e.p) ?: continue
            counts[min(bins - 1, ((e.at - t0) / span).toInt())][cell] += e.w
        }
        if (counts.sumOf { it.sum() } < MIN_INCIDENTS_EMERGING) return null
        val zs = counts.map { giStar(g, it, radiusCells) }
        val out = Array(g.size) { EmergingClass.NONE }
        for (i in 0 until g.size) {
            if (!g.inside[i]) continue
            out[i] = classify(DoubleArray(bins) { zs[it][i] })
        }
        val cellHa = g.cellHa
        val area = EmergingClass.entries.associateWith { k -> out.indices.count { g.inside[it] && out[it] == k } * cellHa }
        val fmt = java.text.SimpleDateFormat("dd/MM/yy", java.util.Locale.US)
        val labels = (0 until bins).map { fmt.format(java.util.Date(t0 + (it * span).toLong())) }
        return EmergingResult(g, out, bins, labels, area)
    }

    internal fun classify(z: DoubleArray): EmergingClass {
        val hot = BooleanArray(z.size) { z[it] >= Z_HOT }
        val total = hot.count { it }
        if (total == 0) return EmergingClass.NONE
        val last = hot.last()
        var run = 0; for (i in hot.indices.reversed()) { if (hot[i]) run++ else break }
        val trend = mannKendallZ(z)
        val frac = total.toDouble() / z.size
        return when {
            last && total == 1 -> EmergingClass.NEW
            last && frac >= .9 -> if (trend >= Z_HOT) EmergingClass.INTENSIFYING else if (trend <= -Z_HOT) EmergingClass.DIMINISHING else EmergingClass.PERSISTENT
            last && run >= 3 -> if (trend >= Z_HOT) EmergingClass.INTENSIFYING else if (trend <= -Z_HOT) EmergingClass.DIMINISHING else EmergingClass.CONSECUTIVE
            last -> EmergingClass.SPORADIC
            frac >= .5 -> EmergingClass.HISTORICAL
            else -> EmergingClass.SPORADIC
        }
    }

    /** Điểm cho từng ô theo biến chọn, làm trơn bằng kernel cùng bán kính để hai biến so sánh được. */
    fun variableSurface(g: ThematicGrid, v: BivarVar, incidents: List<Incident>, trees: List<TreeSample>, bandwidthM: Double): DoubleArray = when (v) {
        BivarVar.ISSUES -> kde(g, incidents.map { it.p to it.w }, bandwidthM).density
        BivarVar.TREES -> kde(g, trees.map { it.p to 1.0 }, bandwidthM).density
        BivarVar.FRUITS -> kde(g, trees.mapNotNull { t -> t.fruits?.let { t.p to it } }, bandwidthM).density
    }

    /** Ngưỡng tam phân vị (2 giá trị) của các ô trong vùng. */
    internal fun terciles(g: ThematicGrid, v: DoubleArray): DoubleArray {
        val s = v.indices.filter { g.inside[it] }.map { v[it] }.sorted()
        if (s.isEmpty()) return doubleArrayOf(0.0, 0.0)
        return doubleArrayOf(s[(s.size - 1) / 3], s[(s.size - 1) * 2 / 3])
    }

    /** Lớp = 3*b + a (a,b ∈ 0..2, thấp→cao theo tam phân vị). -1 là ngoài vùng. */
    fun bivariate(g: ThematicGrid, a: DoubleArray, b: DoubleArray): BivariateResult {
        val ta = terciles(g, a); val tb = terciles(g, b)
        fun bin(x: Double, t: DoubleArray) = if (x <= t[0]) 0 else if (x <= t[1]) 1 else 2
        val cls = IntArray(g.size) { if (g.inside[it]) 3 * bin(b[it], tb) + bin(a[it], ta) else -1 }
        val area = DoubleArray(9) { k -> cls.count { it == k } * g.cellHa }
        return BivariateResult(g, cls, ta, tb, area)
    }

    /** Tiêu chí cơ hội (0..1, cao = tốt) cùng trọng số. */
    enum class Criterion(val label: String) { SPACE("Còn chỗ trống giữa các cây"), HEALTH("Ít vấn đề xung quanh"), INTERIOR("Cách xa mép ranh") }

    /**
     * Chồng lớp có trọng số cho vị trí trồng thêm. Ràng buộc cứng (điểm 0): sát cây hiện có (< [spacingM]), sát ranh (< [edgeM]),
     * gần cây đang có vấn đề (< [issueM]). Còn lại điểm = tổng trọng số × tiêu chí. Lớp: 0 ràng buộc, 1 thấp (<0,4), 2 vừa (<0,7), 3 cao.
     */
    fun suitability(g: ThematicGrid, ring: List<GeoPoint>, trees: List<TreeSample>, issueTrees: List<GeoPoint>, weights: Map<Criterion, Double>,
                    spacingM: Double, edgeM: Double, issueM: Double, bandwidthM: Double): SuitabilityResult {
        val wSum = weights.values.sum().takeIf { it > 0 } ?: 1.0
        val treeXY = trees.map { g.toXY(it.p) }
        val issueXY = issueTrees.map { g.toXY(it) }
        val poly = Geo.open(ring).map { g.toXY(it) }
        val health = kde(g, issueTrees.map { it to 1.0 }, bandwidthM)
        val spaceCap = max(spacingM * 3, 6.0); val edgeCap = max(edgeM * 4, 10.0)
        val score = DoubleArray(g.size); val cls = IntArray(g.size) { -1 }
        fun nearest(x: Double, y: Double, pts: List<Pair<Double, Double>>): Double {
            var best = Double.MAX_VALUE
            for ((px, py) in pts) { val dx = px - x; val dy = py - y; val d2 = dx * dx + dy * dy; if (d2 < best) best = d2 }
            return sqrt(best)
        }
        fun edgeDist(x: Double, y: Double): Double {
            var best = Double.MAX_VALUE
            for (i in poly.indices) {
                val (x1, y1) = poly[i]; val (x2, y2) = poly[(i + 1) % poly.size]
                val dx = x2 - x1; val dy = y2 - y1; val l2 = dx * dx + dy * dy
                val t = if (l2 == 0.0) 0.0 else (((x - x1) * dx + (y - y1) * dy) / l2).coerceIn(0.0, 1.0)
                val ex = x1 + t * dx - x; val ey = y1 + t * dy - y
                best = min(best, sqrt(ex * ex + ey * ey))
            }
            return best
        }
        for (i in 0 until g.size) {
            if (!g.inside[i]) continue
            val (x, y) = g.centerXY(i)
            val dTree = if (treeXY.isEmpty()) Double.MAX_VALUE else nearest(x, y, treeXY)
            val dIssue = if (issueXY.isEmpty()) Double.MAX_VALUE else nearest(x, y, issueXY)
            val dEdge = edgeDist(x, y)
            if (dTree < spacingM || dEdge < edgeM || dIssue < issueM) { score[i] = 0.0; cls[i] = 0; continue }
            val sSpace = (min(dTree, spaceCap) / spaceCap)
            val sHealth = if (health.max <= 0) 1.0 else 1 - (health.density[i] / health.max).coerceIn(0.0, 1.0)
            val sInterior = min(dEdge, edgeCap) / edgeCap
            val s = (weights[Criterion.SPACE] ?: 0.0) * sSpace + (weights[Criterion.HEALTH] ?: 0.0) * sHealth + (weights[Criterion.INTERIOR] ?: 0.0) * sInterior
            score[i] = s / wSum
            cls[i] = if (score[i] < .4) 1 else if (score[i] < .7) 2 else 3
        }
        return SuitabilityResult(g, score, cls, DoubleArray(4) { k -> cls.count { it == k } * g.cellHa })
    }

    /** Sự cố có vị trí: mỗi vấn đề tính một điểm tại mỗi cây của nó (chỉ cây đã có toạ độ). Trả (sự cố, số vấn đề bỏ qua do chưa có toạ độ). */
    fun incidentsOf(d: GardenData, ring: List<GeoPoint>): Pair<List<Incident>, Int> {
        val byId = d.trees.associateBy { it.id }
        var skipped = 0
        val out = ArrayList<Incident>()
        for (iss in d.issues) {
            val pts = iss.treeIds.mapNotNull { byId[it]?.pos }.filter { Geo.pointInPolygon(it, ring) }
            if (pts.isEmpty()) { skipped++; continue }
            val w = when (iss.priority) { Priority.HIGH -> 2.0; Priority.MEDIUM -> 1.0; Priority.LOW -> .5 }
            pts.forEach { out += Incident(it, iss.createdAt, w) }
        }
        return out to skipped
    }

    fun treesIn(d: GardenData, ring: List<GeoPoint>): List<TreeSample> =
        d.activeTrees.filter { it.pos != null && Geo.pointInPolygon(it.pos, ring) }.map { TreeSample(it.pos!!, it.fruits?.toDouble()) }
}
