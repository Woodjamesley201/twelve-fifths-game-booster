package com.twelvefifths.booster.ui.format

import com.twelvefifths.booster.data.SessionRecord
import com.twelvefifths.booster.metrics.MetricsSnapshot

/**
 * Before/after comparison rows built from a real [SessionRecord]. A delta is
 * only produced when both sides are SUPPORTED with a measured value.
 *
 * Owner: Ui isaac.
 */
data class ComparisonRow(
    val metric: Metric,
    val before: String,
    val after: String,
    /** e.g. "−312 MB"; null when either side was not measured. */
    val delta: String?,
    /** true = got better, false = got worse, null = unknown / unchanged. */
    val improved: Boolean?,
)

object SessionCompare {

    private val compared = listOf(Metric.RAM, Metric.CPU_TEMP, Metric.GPU_TEMP, Metric.CPU_USAGE, Metric.PING, Metric.FPS)

    fun rows(record: SessionRecord): List<ComparisonRow> =
        compared.mapNotNull { row(it, record.before, record.after) }

    /** Rows where at least one side has a support state worth showing. */
    fun row(metric: Metric, before: MetricsSnapshot?, after: MetricsSnapshot?): ComparisonRow? {
        if (before == null && after == null) return null
        val b = before?.let { MetricFormat.text(MetricFormat.display(metric, it)) } ?: "Not captured"
        val a = after?.let { MetricFormat.text(MetricFormat.display(metric, it)) } ?: "Not captured"
        // Skip metrics that were unsupported on both ends: nothing to compare.
        val bothUnsupported = b == MetricFormat.UNSUPPORTED_LABEL && a == MetricFormat.UNSUPPORTED_LABEL
        if (bothUnsupported) return null
        val (delta, improved) = deltaFor(metric, before, after)
        return ComparisonRow(metric, b, a, delta, improved)
    }

    private fun deltaFor(metric: Metric, before: MetricsSnapshot?, after: MetricsSnapshot?): Pair<String?, Boolean?> {
        before ?: return null to null
        after ?: return null to null
        return when (metric) {
            Metric.RAM -> {
                val bu = before.ramUsedBytes.takeIf { MetricFormat.numeric(Metric.RAM, before) != null } ?: return null to null
                val au = after.ramUsedBytes.takeIf { MetricFormat.numeric(Metric.RAM, after) != null } ?: return null to null
                val mb = (au - bu) / (1024f * 1024f)
                MetricFormat.signed(mb, " MB") to better(mb, lowerIsBetter = true)
            }
            else -> {
                val bv = MetricFormat.numeric(metric, before) ?: return null to null
                val av = MetricFormat.numeric(metric, after) ?: return null to null
                val d = av - bv
                val unit = when (metric) {
                    Metric.CPU_TEMP, Metric.GPU_TEMP -> " °C"
                    Metric.CPU_USAGE, Metric.GPU_USAGE -> " pts"
                    Metric.PING -> " ms"
                    Metric.FPS -> " fps"
                    else -> ""
                }
                MetricFormat.signed(d, unit) to better(d, lowerIsBetter = metric != Metric.FPS)
            }
        }
    }

    private fun better(delta: Float, lowerIsBetter: Boolean): Boolean? {
        if (delta == 0f) return null
        return if (lowerIsBetter) delta < 0f else delta > 0f
    }
}
