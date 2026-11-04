package com.example.test_tablet

import com.example.test_tablet.data.vwing.*
import org.junit.Assert.*
import org.junit.Test

class ThematicMapsTest {
    // ~222 m x 222 m ở vĩ độ ~11°
    private val ring = listOf(GeoPoint(11.000, 107.000), GeoPoint(11.002, 107.000), GeoPoint(11.002, 107.002), GeoPoint(11.000, 107.002))
    private val g = ThematicMaps.grid(ring)!!
    private fun at(latFrac: Double, lngFrac: Double) = GeoPoint(11.0 + 0.002 * latFrac, 107.0 + 0.002 * lngFrac)
    private val day = 86_400_000L

    @Test fun `grid covers region and rejects degenerate rings`() {
        assertTrue(g.cols >= 2 && g.rows >= 2)
        assertEquals(g.size, g.insideCount)
        assertNull(ThematicMaps.grid(listOf(GeoPoint(11.0, 107.0), GeoPoint(11.0, 107.001))))
        assertNull(ThematicMaps.grid(listOf(GeoPoint(11.0, 107.0), GeoPoint(11.0, 107.0), GeoPoint(11.0, 107.0))))
    }

    @Test fun `kde peaks at the cluster and is zero beyond the bandwidth`() {
        val pts = List(10) { at(.25, .25) to 1.0 }
        val k = ThematicMaps.kde(g, pts, 30.0)
        val peak = g.cellOf(at(.25, .25))!!
        assertEquals(k.max, k.density[peak], k.max * 0.15)
        assertEquals(0.0, k.density[g.cellOf(at(.9, .9))!!], 0.0)
        assertEquals(10, k.points)
    }

    @Test fun `kde mass equals the number of points`() {
        val k = ThematicMaps.kde(g, listOf(at(.5, .5) to 1.0, at(.4, .6) to 2.0), 40.0)
        assertEquals(3.0, k.density.sum() * g.cellHa, 0.35)
    }

    @Test fun `gi star flags a dense cluster hot and is quiet for uniform counts`() {
        val x = DoubleArray(g.size)
        val c = g.cellOf(at(.5, .5))!!
        for (dr in -1..1) for (dc in -1..1) x[c + dr * g.cols + dc] = 10.0
        val z = ThematicMaps.giStar(g, x, 2)
        assertTrue(z[c] > ThematicMaps.Z_HOT)
        assertTrue(z[g.cellOf(at(.05, .95))!!] <= 0.0)
        assertTrue(ThematicMaps.giStar(g, DoubleArray(g.size) { 3.0 }, 2).all { it == 0.0 })
    }

    @Test fun `mann kendall detects monotonic trend and ignores flat or short series`() {
        assertTrue(ThematicMaps.mannKendallZ(doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0)) > ThematicMaps.Z_HOT)
        assertTrue(ThematicMaps.mannKendallZ(doubleArrayOf(6.0, 5.0, 4.0, 3.0, 2.0, 1.0)) < -ThematicMaps.Z_HOT)
        assertEquals(0.0, ThematicMaps.mannKendallZ(doubleArrayOf(2.0, 2.0, 2.0, 2.0, 2.0)), 0.0)
        assertEquals(0.0, ThematicMaps.mannKendallZ(doubleArrayOf(1.0, 2.0, 3.0)), 0.0)
    }

    @Test fun `classification of hot histories`() {
        val c = ThematicMaps::classify
        assertEquals(EmergingClass.NONE, c(doubleArrayOf(0.0, 0.5, 1.0, 0.2, 0.0, 1.0)))
        assertEquals(EmergingClass.NEW, c(doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 3.0)))
        assertEquals(EmergingClass.PERSISTENT, c(doubleArrayOf(2.5, 2.5, 2.5, 2.5, 2.5, 2.5)))
        assertEquals(EmergingClass.INTENSIFYING, c(doubleArrayOf(2.0, 2.1, 2.4, 2.8, 3.2, 3.9)))
        assertEquals(EmergingClass.DIMINISHING, c(doubleArrayOf(4.0, 3.5, 3.0, 2.6, 2.3, 2.0)))
        assertEquals(EmergingClass.CONSECUTIVE, c(doubleArrayOf(0.0, 0.0, 0.0, 2.5, 2.0, 2.4)))
        assertEquals(EmergingClass.HISTORICAL, c(doubleArrayOf(3.0, 3.0, 3.0, 3.0, 0.0, 0.0)))
        assertEquals(EmergingClass.SPORADIC, c(doubleArrayOf(3.0, 0.0, 0.0, 3.0, 0.0, 0.0)))
    }

    @Test fun `emerging finds a hotspot that appears only in the latest period`() {
        val t0 = 1_700_000_000_000L
        val rnd = java.util.Random(7)
        val background = List(20) { Incident(at(rnd.nextDouble(), rnd.nextDouble()), t0 + (it * 3L) * day) }
        val cluster = List(14) { Incident(at(.5 + rnd.nextGaussian() * .02, .5 + rnd.nextGaussian() * .02), t0 + 57L * day + it * day / 2) }
        val r = ThematicMaps.emerging(g, background + cluster, 6)!!
        val centre = g.cellOf(at(.5, .5))!!
        assertEquals("trung tâm cụm: ${r.classes[centre]}", EmergingClass.NEW, r.classes[centre])
        assertEquals(6, r.binLabels.size)
        assertEquals(g.insideCount * g.cellHa, r.areaHa.values.sum(), 1e-6)
    }

    @Test fun `emerging refuses too little data`() {
        assertNull(ThematicMaps.emerging(g, List(5) { Incident(at(.5, .5), it * day) }, 6))
        assertNull(ThematicMaps.emerging(g, List(12) { Incident(at(.5, .5), 1000L) }, 6))
        assertNull(ThematicMaps.emerging(g, List(12) { Incident(at(.5, .5), it * day) }, 3))
    }

    @Test fun `bivariate assigns the 3x3 class from terciles and leaves outside cells unclassified`() {
        val a = DoubleArray(g.size) { (it % g.cols).toDouble() }
        val b = DoubleArray(g.size) { (it / g.cols).toDouble() }
        val r = ThematicMaps.bivariate(g, a, b)
        assertEquals(0, r.cls[g.idx(0, 0)])
        assertEquals(8, r.cls[g.idx(g.rows - 1, g.cols - 1)])
        assertEquals(2, r.cls[g.idx(0, g.cols - 1)])
        assertEquals(6, r.cls[g.idx(g.rows - 1, 0)])
        assertEquals(g.insideCount * g.cellHa, r.areaHa.sum(), 1e-6)
        val tg = ThematicMaps.grid(listOf(GeoPoint(11.0, 107.0), GeoPoint(11.002, 107.0), GeoPoint(11.0, 107.002)))!!
        val tr = ThematicMaps.bivariate(tg, DoubleArray(tg.size) { it.toDouble() }, DoubleArray(tg.size) { 1.0 })
        assertTrue(tr.cls.indices.all { tg.inside[it] == (tr.cls[it] >= 0) })
    }

    @Test fun `suitability applies hard constraints and rewards open healthy interior`() {
        val trees = listOf(TreeSample(at(.5, .5), null))
        val issues = listOf(at(.5, .5))
        val w = mapOf(ThematicMaps.Criterion.SPACE to 1.0, ThematicMaps.Criterion.HEALTH to 1.0, ThematicMaps.Criterion.INTERIOR to 1.0)
        val s = ThematicMaps.suitability(g, ring, trees, issues, w, spacingM = 4.0, edgeM = 5.0, issueM = 15.0, bandwidthM = 30.0)
        assertEquals(0, s.cls[g.cellOf(at(.5, .5))!!])
        assertEquals(0, s.cls[g.idx(0, 0)])
        val open = g.cellOf(at(.15, .85))!!
        assertTrue("điểm=${s.score[open]}", s.cls[open] >= 2)
        assertEquals(g.insideCount * g.cellHa, s.areaHa.sum(), 1e-6)
        assertTrue(s.score.all { it in 0.0..1.0 })
    }

    @Test fun `incidents are located through trees and weighted by priority, unlocated issues are counted`() {
        val d = GardenData(
            trees = listOf(Tree("t1", "A1", "Z", pos = at(.3, .3)), Tree("t2", "A2", "Z", pos = null), Tree("t3", "A3", "Z", pos = GeoPoint(12.0, 108.0))),
            issues = listOf(
                Issue("i1", 1, listOf("t1"), symptom = "a", priority = Priority.HIGH),
                Issue("i2", 2, listOf("t2"), symptom = "b"),
                Issue("i3", 3, listOf("t3"), symptom = "c"),
                Issue("i4", 4, emptyList(), scopeZone = "Z", symptom = "d"),
            ),
        )
        val (inc, skipped) = ThematicMaps.incidentsOf(d, ring)
        assertEquals(1, inc.size); assertEquals(2.0, inc[0].w, 0.0)
        assertEquals(3, skipped)
        assertEquals(1, ThematicMaps.treesIn(d, ring).size)
    }
}
