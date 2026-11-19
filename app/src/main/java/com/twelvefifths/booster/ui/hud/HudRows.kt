package com.twelvefifths.booster.ui.hud

import com.twelvefifths.booster.data.HudPreferences
import com.twelvefifths.booster.metrics.MetricsSnapshot
import com.twelvefifths.booster.ui.format.Metric
import com.twelvefifths.booster.ui.format.MetricDisplay
import com.twelvefifths.booster.ui.format.MetricFormat

/**
 * Pure HUD row model shared by the in-app preview and (optionally) the
 * platform overlay so both show identical, honest labels.
 *
 * Owner: Ui isaac.
 */
data class HudRow(val metric: Metric, val label: String, val display: MetricDisplay) {
    val text: String get() = MetricFormat.text(display)
}

object HudRows {
    const val COMPACT_MAX_ROWS = 4

    /** Metrics in HUD order with whether the prefs enable them. */
    fun enabled(prefs: HudPreferences): List<Metric> = buildList {
        if (prefs.showFps) add(Metric.FPS)
        if (prefs.showCpuTemp) add(Metric.CPU_TEMP)
        if (prefs.showGpuTemp) add(Metric.GPU_TEMP)
        if (prefs.showRam) add(Metric.RAM)
        if (prefs.showCpuUsage) add(Metric.CPU_USAGE)
        if (prefs.showGpuUsage) add(Metric.GPU_USAGE)
        if (prefs.showPing) add(Metric.PING)
        if (prefs.showVram) add(Metric.VRAM)
    }

    fun rows(snapshot: MetricsSnapshot, prefs: HudPreferences): List<HudRow> {
        val all = enabled(prefs).map { m ->
            HudRow(m, m.shortLabel, MetricFormat.display(m, snapshot))
        }
        return if (prefs.compact) all.take(COMPACT_MAX_ROWS) else all
    }

    fun isShown(prefs: HudPreferences, metric: Metric): Boolean = when (metric) {
        Metric.FPS -> prefs.showFps
        Metric.CPU_TEMP -> prefs.showCpuTemp
        Metric.GPU_TEMP -> prefs.showGpuTemp
        Metric.RAM -> prefs.showRam
        Metric.CPU_USAGE -> prefs.showCpuUsage
        Metric.GPU_USAGE -> prefs.showGpuUsage
        Metric.PING -> prefs.showPing
        Metric.VRAM -> prefs.showVram
    }

    fun withShown(prefs: HudPreferences, metric: Metric, shown: Boolean): HudPreferences = when (metric) {
        Metric.FPS -> prefs.copy(showFps = shown)
        Metric.CPU_TEMP -> prefs.copy(showCpuTemp = shown)
        Metric.GPU_TEMP -> prefs.copy(showGpuTemp = shown)
        Metric.RAM -> prefs.copy(showRam = shown)
        Metric.CPU_USAGE -> prefs.copy(showCpuUsage = shown)
        Metric.GPU_USAGE -> prefs.copy(showGpuUsage = shown)
        Metric.PING -> prefs.copy(showPing = shown)
        Metric.VRAM -> prefs.copy(showVram = shown)
    }

    /** Accent swatches offered in HUD settings (ARGB). */
    val accentSwatches: List<Int> = listOf(
        0xFF4F8CFF.toInt(), // neon blue (default)
        0xFFA855F7.toInt(), // neon purple
        0xFF22D3EE.toInt(), // cyan
        0xFF4ADE80.toInt(), // green
        0xFFF472B6.toInt(), // pink
        0xFFFBBF24.toInt(), // amber
        0xFFF4F6FB.toInt(), // white
    )

    enum class Corner { TOP_START, TOP_END, BOTTOM_START, BOTTOM_END }

    /**
     * Overlay x/y (px from top-start) for a corner preset. HUD height is an
     * estimate; the user can drag the live overlay to fine-tune.
     */
    fun cornerPosition(
        corner: Corner,
        screenWidthPx: Int,
        screenHeightPx: Int,
        hudWidthPx: Int,
        hudHeightPx: Int,
        marginPx: Int,
    ): Pair<Int, Int> {
        val right = (screenWidthPx - hudWidthPx - marginPx).coerceAtLeast(0)
        val bottom = (screenHeightPx - hudHeightPx - marginPx).coerceAtLeast(0)
        return when (corner) {
            Corner.TOP_START -> marginPx to marginPx
            Corner.TOP_END -> right to marginPx
            Corner.BOTTOM_START -> marginPx to bottom
            Corner.BOTTOM_END -> right to bottom
        }
    }
}
