package com.twelvefifths.booster.hud

import com.twelvefifths.booster.data.HudPreferences
import com.twelvefifths.booster.metrics.MetricKey
import com.twelvefifths.booster.metrics.MetricSupport
import com.twelvefifths.booster.metrics.MetricsSnapshot
import com.twelvefifths.booster.metrics.TemperatureSource
import com.twelvefifths.booster.metrics.requiredPermission
import com.twelvefifths.booster.metrics.temperatureSummary
import com.twelvefifths.booster.optimize.BoosterPermission
import java.util.Locale

/**
 * One HUD line. [value] is null when there is no real number to show; the
 * renderer then shows [statusText] (e.g. "unsupported") instead.
 */
data class HudRow(
    val key: Key,
    val label: String,
    val value: String?,
    val support: MetricSupport,
    val statusText: String,
    /** Set only when [support] is NEEDS_PERMISSION: the permission that unlocks this row. */
    val requiredPermission: BoosterPermission? = null,
) {
    enum class Key {
        FPS, TEMP, GPU_TEMP, RAM, CPU_USAGE, GPU_USAGE, PING, VRAM;

        /** The snapshot metric behind this row (TEMP resolves to CPU temp; see [HudRows]). */
        val metric: MetricKey
            get() = when (this) {
                FPS -> MetricKey.FPS
                TEMP -> MetricKey.CPU_TEMP
                GPU_TEMP -> MetricKey.GPU_TEMP
                RAM -> MetricKey.RAM
                CPU_USAGE -> MetricKey.CPU_USAGE
                GPU_USAGE -> MetricKey.GPU_USAGE
                PING -> MetricKey.PING
                VRAM -> MetricKey.VRAM
            }
    }

    /** What a plain-text renderer should print in the value column. */
    val display: String get() = value ?: statusText

    val hasRealValue: Boolean get() = support == MetricSupport.SUPPORTED && value != null
}

/**
 * Pure snapshot → rows mapping shared by the overlay window (and available to
 * ui/ for previews). Never formats a value for an unsupported metric, and the
 * temperature row names its true source (CPU zone, battery, or thermal level).
 *
 * Compact mode keeps only rows that have a real value right now (max
 * [COMPACT_MAX]), so the small HUD is all signal; expanded shows every enabled
 * row with its support state.
 *
 * Owner: Script Nathan.
 */
object HudRows {
    const val COMPACT_MAX = 4

    /** Overlay wording, shared with the in-app preview. */
    const val UNSUPPORTED_TEXT = "Not available on Android"
    const val NEEDS_PERMISSION_TEXT = "Needs permission"
    const val WAITING_TEXT = "—"
    const val PING_LABEL = "TCP ping"

    fun build(snapshot: MetricsSnapshot, prefs: HudPreferences): List<HudRow> {
        val rows = ArrayList<HudRow>(8)
        if (prefs.showFps) rows += row(HudRow.Key.FPS, "FPS", snapshot.fps?.let { fmt("%.0f", it) }, snapshot.fpsSupport)
        if (prefs.showCpuTemp) rows += temperatureRow(snapshot)
        if (prefs.showGpuTemp) rows += row(HudRow.Key.GPU_TEMP, "GPU °C", snapshot.gpuTempC?.let { fmt("%.0f", it) }, snapshot.gpuTempSupport)
        if (prefs.showRam) rows += ramRow(snapshot)
        if (prefs.showCpuUsage) rows += row(HudRow.Key.CPU_USAGE, "CPU %", snapshot.cpuUsage?.let { fmt("%.0f", it) }, snapshot.cpuUsageSupport)
        if (prefs.showGpuUsage) rows += row(HudRow.Key.GPU_USAGE, "GPU %", snapshot.gpuUsage?.let { fmt("%.0f", it) }, snapshot.gpuUsageSupport)
        if (prefs.showPing) rows += pingRow(snapshot)
        if (prefs.showVram) rows += row(HudRow.Key.VRAM, "VRAM", snapshot.vramUsedBytes?.let { formatBytes(it) }, snapshot.vramSupport)
        val withPermissions = rows.map { row ->
            if (row.support == MetricSupport.NEEDS_PERMISSION) {
                row.copy(requiredPermission = snapshot.requiredPermission(row.key.metric))
            } else {
                row
            }
        }
        return if (prefs.compact) withPermissions.filter { it.hasRealValue }.take(COMPACT_MAX) else withPermissions
    }

    private fun temperatureRow(s: MetricsSnapshot): HudRow {
        val summary = s.temperatureSummary()
            ?: return row(HudRow.Key.TEMP, "CPU °C", null, MetricSupport.UNSUPPORTED)
        return when (summary.source) {
            TemperatureSource.CPU_THERMAL_ZONE, TemperatureSource.BATTERY ->
                row(HudRow.Key.TEMP, summary.label, summary.celsius?.let { fmt("%.0f", it) }, MetricSupport.SUPPORTED)
            TemperatureSource.THERMAL_STATUS ->
                row(HudRow.Key.TEMP, summary.label, summary.status?.name?.lowercase(Locale.US), MetricSupport.SUPPORTED)
        }
    }

    private fun ramRow(s: MetricsSnapshot): HudRow {
        val used = s.ramUsedBytes
        val total = s.ramTotalBytes
        val value = if (s.ramSupport == MetricSupport.SUPPORTED && used != null && total != null) {
            "${formatBytes(used)} / ${formatBytes(total)}" + (s.ramPercent?.let { fmt("  (%.0f%%)", it) } ?: "")
        } else {
            null
        }
        return row(HudRow.Key.RAM, "RAM", value, s.ramSupport)
    }

    private fun pingRow(s: MetricsSnapshot): HudRow {
        val ms = s.pingMs
        if (s.pingSupport == MetricSupport.SUPPORTED && ms != null) {
            return row(HudRow.Key.PING, PING_LABEL, "$ms ms", MetricSupport.SUPPORTED)
        }
        val status = s.pingStatus.name.lowercase(Locale.US).replace('_', ' ')
        return HudRow(HudRow.Key.PING, PING_LABEL, null, s.pingSupport, if (s.pingSupport == MetricSupport.SUPPORTED) status else supportText(s.pingSupport))
    }

    private fun row(key: HudRow.Key, label: String, value: String?, support: MetricSupport): HudRow =
        HudRow(key, label, if (support == MetricSupport.SUPPORTED) value else null, support, supportText(support))

    fun supportText(support: MetricSupport): String = when (support) {
        MetricSupport.SUPPORTED -> WAITING_TEXT
        MetricSupport.NEEDS_PERMISSION -> NEEDS_PERMISSION_TEXT
        MetricSupport.UNSUPPORTED -> UNSUPPORTED_TEXT
    }

    fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) fmt("%.1f GB", mb / 1024.0) else fmt("%.0f MB", mb)
    }

    private fun fmt(pattern: String, value: Number): String = String.format(Locale.US, pattern, value)
}
