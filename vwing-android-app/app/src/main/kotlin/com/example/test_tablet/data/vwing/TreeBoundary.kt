package com.example.test_tablet.data.vwing

import kotlin.math.hypot

/**
 * Đối chiếu vị trí cây với ranh vườn (tính ngay trên máy, không cần mạng). GPS điện thoại sai vài mét, nên cây nằm sát ranh
 * trong phạm vi [TOLERANCE_M] vẫn tính là "trong ranh"; chỉ cây xa ranh hơn mức đó mới bị báo ngoài ranh.
 */
object TreeBoundary {
    const val TOLERANCE_M = 5.0

    class ZoneRow(val zone: String, val total: Int, val inside: Int, val outside: Int, val unplaced: Int)
    class Report(val inside: List<Tree>, val outside: List<Pair<Tree, Double>>, val unplaced: List<Tree>, val byZone: List<ZoneRow>) {
        val placed get() = inside.size + outside.size
    }

    /** Khoảng cách (m) từ điểm tới cạnh gần nhất của đa giác. */
    fun distanceToEdgeM(p: GeoPoint, ring: List<GeoPoint>): Double {
        val r = Geo.open(ring)
        val m = r.map { Geo.toMeters(it, p) }          // p ở gốc toạ độ (0,0)
        var best = Double.MAX_VALUE
        for (i in m.indices) {
            val a = m[i]; val b = m[(i + 1) % m.size]
            val dx = b.first - a.first; val dy = b.second - a.second
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else ((-a.first * dx - a.second * dy) / len2).coerceIn(0.0, 1.0)
            best = minOf(best, hypot(a.first + t * dx, a.second + t * dy))
        }
        return best
    }

    fun check(d: GardenData, ring: List<GeoPoint>, toleranceM: Double = TOLERANCE_M): Report {
        val inside = mutableListOf<Tree>(); val outside = mutableListOf<Pair<Tree, Double>>(); val unplaced = mutableListOf<Tree>()
        for (t in d.activeTrees) {
            val p = t.pos
            if (p == null) { unplaced += t; continue }
            if (Geo.pointInPolygon(p, ring)) { inside += t; continue }
            val dist = distanceToEdgeM(p, ring)
            if (dist <= toleranceM) inside += t else outside += t to dist
        }
        val zones = d.activeTrees.map { it.zone }.distinct().sorted().map { z ->
            ZoneRow(z, d.activeTrees.count { it.zone == z }, inside.count { it.zone == z }, outside.count { it.first.zone == z }, unplaced.count { it.zone == z })
        }
        return Report(inside, outside.sortedByDescending { it.second }, unplaced, zones)
    }
}
