package com.twelvefifths.booster.ui

import com.twelvefifths.booster.metrics.MetricSupport
import com.twelvefifths.booster.metrics.MetricsSnapshot
import com.twelvefifths.booster.ui.format.Metric
import com.twelvefifths.booster.ui.graph.GraphMath
import com.twelvefifths.booster.ui.graph.MetricHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphMathTest {

    @Test
    fun rangeIsNullWithoutData() {
        assertNull(GraphMath.range(listOf(null, null)))
        assertNull(GraphMath.range(emptyList()))
    }

    @Test
    fun rangeRespectsPinnedBoundsAndNormalizes() {
        val r = GraphMath.range(listOf(20f, 80f), floor = 0f, ceiling = 100f)!!
        assertEquals(0f, r.min, 0f)
        assertEquals(100f, r.max, 0f)
        assertEquals(0.5f, r.normalize(50f), 0.0001f)
        assertEquals(1f, r.normalize(150f), 0f)
    }

    @Test
    fun flatSeriesGetsMinimumSpan() {
        val r = GraphMath.range(listOf(40f, 40f, 40f), minSpan = 6f, step = 2f)!!
        assertTrue(r.max - r.min >= 6f)
        assertTrue(r.min <= 40f && r.max >= 40f)
    }

    @Test
    fun segmentsBreakAtGaps() {
        val segs = GraphMath.segments(listOf(1f, 2f, null, 3f, Float.NaN, 4f, 5f))
        assertEquals(3, segs.size)
        assertEquals(listOf(0 to 1f, 1 to 2f), segs[0])
        assertEquals(listOf(3 to 3f), segs[1])
        assertEquals(listOf(5 to 4f, 6 to 5f), segs[2])
    }

    @Test
    fun xFractionIsRightAligned() {
        assertEquals(1f, GraphMath.xFraction(2, 3, 5), 0.0001f)
        assertEquals(0.5f, GraphMath.xFraction(0, 3, 5), 0.0001f)
        assertEquals(0f, GraphMath.xFraction(0, 5, 5), 0.0001f)
    }

    @Test
    fun averageIgnoresGaps() {
        assertEquals(15f, GraphMath.average(listOf(10f, null, 20f))!!, 0.0001f)
        assertNull(GraphMath.average(listOf(null)))
    }

    @Test
    fun historyKeepsWindowAndSkipsDuplicates() {
        var h = MetricHistory.empty(capacity = 3)
        for (t in 1L..5L) {
            h = h.append(snap(t, ramPct = t * 10f))
        }
        val same = h.append(snap(5L, ramPct = 99f))
        assertSame(h, same)
        assertEquals(listOf(30f, 40f, 50f), h.series(Metric.RAM))
        assertEquals(40f, h.average(Metric.RAM)!!, 0.0001f)
        assertNull(h.average(Metric.FPS))
    }

    @Test
    fun placeholderSnapshotIsDetected() {
        assertTrue(MetricHistory.isPlaceholder(MetricsSnapshot.empty()))
        assertTrue(!MetricHistory.isPlaceholder(snap(1L, 10f)))
    }

    private fun snap(t: Long, ramPct: Float) = MetricsSnapshot.empty(t).copy(
        ramPercent = ramPct,
        ramUsedBytes = 1L,
        ramTotalBytes = 10L,
        ramSupport = MetricSupport.SUPPORTED,
    )
}
