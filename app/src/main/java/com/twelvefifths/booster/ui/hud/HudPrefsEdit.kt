package com.twelvefifths.booster.ui.hud

import com.twelvefifths.booster.data.HudPreferences
import com.twelvefifths.booster.hud.HudRow
import com.twelvefifths.booster.hud.HudRows
import com.twelvefifths.booster.hud.HudWindowBounds
import com.twelvefifths.booster.ui.format.Metric

/**
 * Pure helpers for HUD settings: stat toggles, accent swatches, corner presets,
 * and the in-app wording for Nathan's [HudRows.build] rows. Row selection and
 * values always come from [HudRows.build] so the preview matches the overlay.
 *
 * Owner: Ui isaac.
 */
object HudPrefsEdit {
    val COMPACT_MAX_ROWS: Int get() = HudRows.COMPACT_MAX

    fun enabled(prefs: HudPreferences): List<Metric> = Metric.hud.filter { isShown(prefs, it) }

    fun isShown(prefs: HudPreferences, metric: Metric): Boolean = when (metric) {
        Metric.FPS -> prefs.showFps
        Metric.CPU_TEMP -> prefs.showCpuTemp
        Metric.GPU_TEMP -> prefs.showGpuTemp
        Metric.RAM -> prefs.showRam
        Metric.CPU_USAGE -> prefs.showCpuUsage
        Metric.GPU_USAGE -> prefs.showGpuUsage
        Metric.PING -> prefs.showPing
        Metric.VRAM -> prefs.showVram
        Metric.BATTERY_TEMP -> false
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
        Metric.BATTERY_TEMP -> prefs
    }

    /** Settings-screen title for a HUD stat (CPU temp row falls back to battery / thermal level). */
    fun hudTitle(metric: Metric): String = when (metric) {
        Metric.CPU_TEMP -> "Temperature (CPU → battery → thermal level)"
        else -> metric.title
    }

    /** Label shown in the in-app preview: identical to the overlay (ping = HudRows.PING_LABEL). */
    fun previewLabel(row: HudRow): String = row.label

    /** Value text for the preview: identical to the overlay's text. */
    fun previewValue(row: HudRow): String = row.display

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
     * Exact corner position from the live overlay's measured bounds
     * (HudService.overlayBounds); falls back to [cornerPosition] with an
     * estimated size when the overlay is not on screen.
     */
    fun cornerPosition(corner: Corner, bounds: HudWindowBounds?, fallback: () -> Pair<Int, Int>, marginPx: Int): Pair<Int, Int> =
        if (bounds == null) fallback()
        else cornerPosition(corner, bounds.screenWidthPx, bounds.screenHeightPx, bounds.widthPx, bounds.heightPx, marginPx)

    /** Overlay x/y (px from top-start) for a corner preset. Moves the live window too. */
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
