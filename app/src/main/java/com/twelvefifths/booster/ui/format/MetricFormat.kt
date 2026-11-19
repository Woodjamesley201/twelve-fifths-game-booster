package com.twelvefifths.booster.ui.format

import com.twelvefifths.booster.metrics.MetricSupport
import com.twelvefifths.booster.metrics.MetricsSnapshot
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Pure formatting + support-label mapping for every metric the UI shows.
 * No Android types here so it is covered by JVM unit tests and can be reused
 * by the platform overlay (HudWindowController) if Nathan wants identical copy.
 *
 * Rule: a number is only ever produced from a real, SUPPORTED, non-null value.
 *
 * Owner: Ui isaac.
 */

/** Which metric a value belongs to. */
enum class Metric(val title: String, val shortLabel: String) {
    FPS("Game FPS", "FPS"),
    CPU_TEMP("CPU temperature", "CPU °C"),
    GPU_TEMP("GPU temperature", "GPU °C"),
    RAM("Memory", "RAM"),
    CPU_USAGE("CPU usage", "CPU %"),
    GPU_USAGE("GPU usage", "GPU %"),
    PING("Latency (TCP)", "PING TCP"),
    VRAM("GPU memory", "VRAM"),
}

/** Android permission flows the UI can route to. */
enum class PermissionKind { OVERLAY, USAGE_ACCESS, DND, NOTIFICATIONS }

/** What a metric cell should render. */
sealed interface MetricDisplay {
    data class Value(val text: String) : MetricDisplay

    /** Supported, but no sample has arrived yet. Shows a dash, never a number. */
    data object Waiting : MetricDisplay

    data class NeedsPermission(val permission: PermissionKind) : MetricDisplay

    data object Unsupported : MetricDisplay
}

object MetricFormat {
    const val UNSUPPORTED_LABEL = "Not available on Android"
    const val GRANT_LABEL = "Grant permission"
    const val WAITING_LABEL = "—"

    /** Short explanation of *why* a metric is unsupported, shown as a caption. */
    fun unsupportedReason(metric: Metric): String = when (metric) {
        Metric.FPS -> "Other apps' frame rate needs root or an ADB capture."
        Metric.GPU_USAGE -> "Mali GPU load is not exposed to normal apps."
        Metric.VRAM -> "GPU memory is not exposed to normal apps."
        Metric.CPU_USAGE -> "/proc/stat is blocked for apps on Android 8+."
        Metric.CPU_TEMP, Metric.GPU_TEMP -> "No readable thermal sensor on this device."
        Metric.RAM -> "Memory info is unavailable."
        Metric.PING -> "TCP latency check could not run."
    }

    /**
     * The permission a NEEDS_PERMISSION metric routes to. MetricSupport does
     * not carry which permission, so this is the UI's best mapping; usage
     * access is the only metric-gating permission the app declares today.
     */
    fun permissionFor(metric: Metric): PermissionKind = when (metric) {
        else -> PermissionKind.USAGE_ACCESS
    }

    fun display(metric: Metric, value: String?, support: MetricSupport): MetricDisplay = when (support) {
        MetricSupport.UNSUPPORTED -> MetricDisplay.Unsupported
        MetricSupport.NEEDS_PERMISSION -> MetricDisplay.NeedsPermission(permissionFor(metric))
        MetricSupport.SUPPORTED -> if (value == null) MetricDisplay.Waiting else MetricDisplay.Value(value)
    }

    /** Plain text for compact surfaces (HUD rows, comparisons). */
    fun text(display: MetricDisplay): String = when (display) {
        is MetricDisplay.Value -> display.text
        MetricDisplay.Waiting -> WAITING_LABEL
        is MetricDisplay.NeedsPermission -> GRANT_LABEL
        MetricDisplay.Unsupported -> UNSUPPORTED_LABEL
    }

    /**
     * One display for a panel that covers several metrics (e.g. CPU + GPU
     * temperature): a value if [joined] is non-null, Waiting if any metric is
     * supported, the first permission ask, else Unsupported.
     */
    fun combine(displays: List<MetricDisplay>, joined: String?): MetricDisplay = when {
        joined != null -> MetricDisplay.Value(joined)
        displays.any { it is MetricDisplay.Waiting || it is MetricDisplay.Value } -> MetricDisplay.Waiting
        displays.any { it is MetricDisplay.NeedsPermission } -> displays.first { it is MetricDisplay.NeedsPermission }
        else -> MetricDisplay.Unsupported
    }

    fun support(metric: Metric, s: MetricsSnapshot): MetricSupport = when (metric) {
        Metric.FPS -> s.fpsSupport
        Metric.CPU_TEMP -> s.cpuTempSupport
        Metric.GPU_TEMP -> s.gpuTempSupport
        Metric.RAM -> s.ramSupport
        Metric.CPU_USAGE -> s.cpuUsageSupport
        Metric.GPU_USAGE -> s.gpuUsageSupport
        Metric.PING -> s.pingSupport
        Metric.VRAM -> s.vramSupport
    }

    /** Raw numeric value, only when SUPPORTED. RAM is percent used. */
    fun numeric(metric: Metric, s: MetricsSnapshot): Float? {
        if (support(metric, s) != MetricSupport.SUPPORTED) return null
        return when (metric) {
            Metric.FPS -> s.fps
            Metric.CPU_TEMP -> s.cpuTempC
            Metric.GPU_TEMP -> s.gpuTempC
            Metric.RAM -> s.ramPercent ?: ramPercentOf(s.ramUsedBytes, s.ramTotalBytes)
            Metric.CPU_USAGE -> s.cpuUsage
            Metric.GPU_USAGE -> s.gpuUsage
            Metric.PING -> s.pingMs?.toFloat()
            Metric.VRAM -> s.vramUsedBytes?.toFloat()
        }?.takeIf { it.isFinite() }
    }

    fun valueText(metric: Metric, s: MetricsSnapshot): String? {
        if (support(metric, s) != MetricSupport.SUPPORTED) return null
        return when (metric) {
            Metric.FPS -> s.fps?.let(::fps)
            Metric.CPU_TEMP -> s.cpuTempC?.let(::celsius)
            Metric.GPU_TEMP -> s.gpuTempC?.let(::celsius)
            Metric.RAM -> ram(s.ramUsedBytes, s.ramTotalBytes)
            Metric.CPU_USAGE -> s.cpuUsage?.let(::percent)
            Metric.GPU_USAGE -> s.gpuUsage?.let(::percent)
            Metric.PING -> s.pingMs?.let(::pingTcp)
            Metric.VRAM -> s.vramUsedBytes?.let(::bytes)
        }
    }

    fun display(metric: Metric, s: MetricsSnapshot): MetricDisplay =
        display(metric, valueText(metric, s), support(metric, s))

    fun fps(v: Float): String = String.format(Locale.US, "%.0f", v)

    fun celsius(v: Float): String = String.format(Locale.US, "%.0f °C", v)

    fun percent(v: Float): String = String.format(Locale.US, "%.0f%%", v)

    /** Always labeled TCP: apps cannot send ICMP, this is a timed TCP connect. */
    fun pingTcp(ms: Long): String = "$ms ms TCP"

    fun bytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) String.format(Locale.US, "%.1f GB", mb / 1024.0)
        else String.format(Locale.US, "%.0f MB", mb)
    }

    fun ram(used: Long?, total: Long?): String? {
        used ?: return null
        if (total == null || total <= 0) return bytes(used)
        return "${bytes(used)} / ${bytes(total)}"
    }

    fun ramPercentOf(used: Long?, total: Long?): Float? {
        if (used == null || total == null || total <= 0) return null
        return (used.toDouble() / total.toDouble() * 100.0).toFloat()
    }

    /** "Just now", "5 min ago", "3 h ago", "2 days ago", or "Never played". */
    fun lastPlayed(lastMs: Long?, nowMs: Long): String {
        if (lastMs == null || lastMs <= 0L) return "Never played"
        val diff = (nowMs - lastMs).coerceAtLeast(0L)
        val min = diff / 60_000L
        return when {
            min < 1 -> "Played just now"
            min < 60 -> "Played $min min ago"
            min < 60 * 24 -> "Played ${min / 60} h ago"
            min < 60 * 24 * 2 -> "Played yesterday"
            else -> "Played ${min / (60 * 24)} days ago"
        }
    }

    fun duration(ms: Long): String {
        val totalSec = (ms / 1000.0).roundToLong().coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }

    /** Signed delta text such as "−312 MB" or "+2 °C". */
    fun signed(delta: Float, unit: String, decimals: Int = 0): String {
        val sign = when {
            delta > 0f -> "+"
            delta < 0f -> "−"
            else -> "±"
        }
        val body = String.format(Locale.US, "%.${decimals}f", abs(delta))
        return "$sign$body$unit"
    }
}
