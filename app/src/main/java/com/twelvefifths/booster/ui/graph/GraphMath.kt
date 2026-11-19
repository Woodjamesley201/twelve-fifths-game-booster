package com.twelvefifths.booster.ui.graph

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * Pure helpers for the performance graphs. Null samples are gaps: the graph
 * never interpolates a value that was not measured.
 *
 * Owner: Ui isaac.
 */
data class GraphRange(val min: Float, val max: Float) {
    val span: Float get() = (max - min).takeIf { it > 0f } ?: 1f

    /** 0 at [min], 1 at [max], clamped. */
    fun normalize(v: Float): Float = ((v - min) / span).coerceIn(0f, 1f)
}

object GraphMath {

    /**
     * Range for a set of series. [floor]/[ceiling] pin known bounds (e.g. 0–100 %).
     * Otherwise pads the data range by 10 % and rounds to [step].
     */
    fun range(
        values: List<Float?>,
        floor: Float? = null,
        ceiling: Float? = null,
        minSpan: Float = 1f,
        step: Float = 1f,
    ): GraphRange? {
        val real = values.filterNotNull().filter { it.isFinite() }
        if (real.isEmpty()) return null
        var lo = real.min()
        var hi = real.max()
        val pad = max((hi - lo) * 0.1f, minSpan / 2f)
        lo = floor ?: (floor((lo - pad) / step) * step)
        hi = ceiling ?: (ceil((hi + pad) / step) * step)
        if (hi - lo < minSpan) hi = lo + minSpan
        return GraphRange(lo, hi)
    }

    /**
     * Splits a series into contiguous runs of (index, value) so the path is
     * broken across gaps instead of drawing a fake line through them.
     */
    fun segments(values: List<Float?>): List<List<Pair<Int, Float>>> {
        val out = mutableListOf<List<Pair<Int, Float>>>()
        var run = mutableListOf<Pair<Int, Float>>()
        values.forEachIndexed { i, v ->
            if (v == null || !v.isFinite()) {
                if (run.isNotEmpty()) out += run
                run = mutableListOf()
            } else {
                run += i to v
            }
        }
        if (run.isNotEmpty()) out += run
        return out
    }

    /** x in [0,1] for a sample index in a window of [capacity] slots, right-aligned. */
    fun xFraction(index: Int, count: Int, capacity: Int): Float {
        val slots = max(capacity, count)
        if (slots <= 1) return 1f
        val offset = slots - count
        return (offset + index).toFloat() / (slots - 1).toFloat()
    }

    fun average(values: List<Float?>): Float? {
        val real = values.filterNotNull().filter { it.isFinite() }
        return if (real.isEmpty()) null else real.sum() / real.size
    }
}
