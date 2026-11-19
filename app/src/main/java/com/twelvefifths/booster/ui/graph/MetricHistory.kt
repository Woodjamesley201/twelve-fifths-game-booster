package com.twelvefifths.booster.ui.graph

import com.twelvefifths.booster.metrics.MetricsSnapshot
import com.twelvefifths.booster.ui.format.Metric
import com.twelvefifths.booster.ui.format.MetricFormat

/**
 * Immutable rolling window of real snapshots for the dashboard graphs.
 * Duplicate timestamps are ignored so recomposition never double-counts.
 *
 * Owner: Ui isaac.
 */
class MetricHistory private constructor(
    val capacity: Int,
    val samples: List<MetricsSnapshot>,
) {
    val isEmpty: Boolean get() = samples.isEmpty()
    val latest: MetricsSnapshot? get() = samples.lastOrNull()

    fun append(snapshot: MetricsSnapshot): MetricHistory {
        val last = samples.lastOrNull()
        if (last != null && snapshot.timestampMs <= last.timestampMs) return this
        val next = if (samples.size >= capacity) samples.drop(samples.size - capacity + 1) + snapshot
        else samples + snapshot
        return MetricHistory(capacity, next)
    }

    /** Per-sample value for [metric]; null where unsupported or not sampled. */
    fun series(metric: Metric): List<Float?> = samples.map { MetricFormat.numeric(metric, it) }

    fun hasAny(metric: Metric): Boolean = samples.any { MetricFormat.numeric(metric, it) != null }

    fun average(metric: Metric): Float? = GraphMath.average(series(metric))

    companion object {
        const val DEFAULT_CAPACITY = 90

        fun empty(capacity: Int = DEFAULT_CAPACITY): MetricHistory =
            MetricHistory(capacity.coerceAtLeast(2), emptyList())

        /** Snapshots from MetricsSnapshot.empty() carry no data; skip them. */
        fun isPlaceholder(s: MetricsSnapshot): Boolean =
            Metric.entries.all { MetricFormat.numeric(it, s) == null } &&
                Metric.entries.all { MetricFormat.support(it, s) == com.twelvefifths.booster.metrics.MetricSupport.UNSUPPORTED }
    }
}
