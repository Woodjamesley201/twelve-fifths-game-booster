package com.twelvefifths.booster.ui.format

import com.twelvefifths.booster.metrics.MetricSupport
import com.twelvefifths.booster.metrics.MetricsSnapshot
import com.twelvefifths.booster.metrics.PingStatus
import com.twelvefifths.booster.hud.HudRows
import com.twelvefifths.booster.metrics.MetricKey
import com.twelvefifths.booster.metrics.requiredPermission
import com.twelvefifths.booster.metrics.support
import com.twelvefifths.booster.optimize.BoosterPermission
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
enum class Metric(val title: String, val shortLabel: String, val key: MetricKey) {
    FPS("Game FPS", "FPS", MetricKey.FPS),
    CPU_TEMP("CPU temperature", "CPU °C", MetricKey.CPU_TEMP),
    GPU_TEMP("GPU temperature", "GPU °C", MetricKey.GPU_TEMP),
    RAM("Memory", "RAM", MetricKey.RAM),
    CPU_USAGE("CPU usage", "CPU %", MetricKey.CPU_USAGE),
    GPU_USAGE("GPU usage", "GPU %", MetricKey.GPU_USAGE),
    PING("Latency (TCP)", HudRows.PING_LABEL, MetricKey.PING),
    VRAM("GPU memory", "VRAM", MetricKey.VRAM),
    BATTERY_TEMP("Battery temperature", "BATT °C", MetricKey.BATTERY_TEMP),
    ;

    companion object {
        /** Metrics the HUD can toggle (HudPreferences has a flag for each). */
        val hud: List<Metric> = listOf(FPS, CPU_TEMP, GPU_TEMP, RAM, CPU_USAGE, GPU_USAGE, PING, VRAM)
    }
}


/** What a metric cell should render. */
sealed interface MetricDisplay {
    data class Value(val text: String) : MetricDisplay

    /** Supported, but no sample has arrived yet. Shows a dash, never a number. */
    data object Waiting : MetricDisplay

    /** [permission] is null when the engine did not name one: route to the Access tab. */
    data class NeedsPermission(val permission: BoosterPermission?) : MetricDisplay

    data object Unsupported : MetricDisplay

    /** Supported, but this sample has a non-numeric state (e.g. ping timeout). */
    data class Status(val text: String) : MetricDisplay
}

object MetricFormat {
    // State wording is shared with the live overlay (hud/HudRows) so both match.
    const val UNSUPPORTED_LABEL = HudRows.UNSUPPORTED_TEXT
    const val NEEDS_PERMISSION_LABEL = HudRows.NEEDS_PERMISSION_TEXT
    const val WAITING_LABEL = HudRows.WAITING_TEXT

    /** Button text on a NEEDS_PERMISSION metric. */
    const val GRANT_LABEL = "Grant permission"

    /** Short explanation of *why* a metric is unsupported, shown as a caption. */
    fun unsupportedReason(metric: Metric): String = when (metric) {
        Metric.FPS -> "Other apps' frame rate needs root or an ADB capture."
        Metric.GPU_USAGE -> "Mali GPU load is not exposed to normal apps."
        Metric.VRAM -> "GPU memory is not exposed to normal apps."
        Metric.CPU_USAGE -> "/proc/stat is blocked for apps on Android 8+."
        Metric.CPU_TEMP, Metric.GPU_TEMP -> "No readable thermal sensor on this device."
        Metric.RAM -> "Memory info is unavailable."
        Metric.PING -> "TCP latency check could not run."
        Metric.BATTERY_TEMP -> "Battery temperature is not reported."
    }

    fun display(
        metric: Metric,
        value: String?,
        support: MetricSupport,
        status: String? = null,
        permission: BoosterPermission? = null,
    ): MetricDisplay = when (support) {
        MetricSupport.UNSUPPORTED -> MetricDisplay.Unsupported
        MetricSupport.NEEDS_PERMISSION -> MetricDisplay.NeedsPermission(permission)
        MetricSupport.SUPPORTED -> when {
            value != null -> MetricDisplay.Value(value)
            status != null -> MetricDisplay.Status(status)
            else -> MetricDisplay.Waiting
        }
    }

    /** Plain text for compact surfaces (HUD rows, comparisons). */
    fun text(display: MetricDisplay): String = when (display) {
        is MetricDisplay.Value -> display.text
        MetricDisplay.Waiting -> WAITING_LABEL
        is MetricDisplay.NeedsPermission -> NEEDS_PERMISSION_LABEL
        MetricDisplay.Unsupported -> UNSUPPORTED_LABEL
        is MetricDisplay.Status -> display.text
    }

    /**
     * One display for a panel that covers several metrics (e.g. CPU + GPU
     * temperature): a value if [joined] is non-null, Waiting if any metric is
     * supported, the first permission ask, else Unsupported.
     */
    fun combine(displays: List<MetricDisplay>, joined: String?): MetricDisplay = when {
        joined != null -> MetricDisplay.Value(joined)
        displays.any { it is MetricDisplay.Waiting || it is MetricDisplay.Value || it is MetricDisplay.Status } -> MetricDisplay.Waiting
        displays.any { it is MetricDisplay.NeedsPermission } -> displays.first { it is MetricDisplay.NeedsPermission }
        else -> MetricDisplay.Unsupported
    }

    fun support(metric: Metric, s: MetricsSnapshot): MetricSupport = s.support(metric.key)

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
            Metric.BATTERY_TEMP -> s.batteryTempC
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
            Metric.BATTERY_TEMP -> s.batteryTempC?.let(::celsius)
        }
    }

    fun display(metric: Metric, s: MetricsSnapshot): MetricDisplay =
        display(metric, valueText(metric, s), support(metric, s), statusText(metric, s), s.requiredPermission(metric.key))

    /** Non-numeric state for a SUPPORTED metric with no value (ping only today). */
    fun statusText(metric: Metric, s: MetricsSnapshot): String? = when (metric) {
        Metric.PING -> pingStatus(s.pingStatus)
        else -> null
    }

    fun pingStatus(status: PingStatus): String? = when (status) {
        PingStatus.OK, PingStatus.NOT_MEASURED -> null
        PingStatus.TIMEOUT -> "Timed out"
        PingStatus.UNREACHABLE -> "Unreachable"
        PingStatus.UNKNOWN_HOST -> "Unknown host"
        PingStatus.NO_NETWORK -> "No network"
        PingStatus.INVALID_TARGET -> "Invalid target"
    }

    /**
     * Where a value comes from, shown as a caption so the source is never
     * ambiguous (e.g. which thermal zone, which TCP target).
     */
    fun sourceCaption(metric: Metric, s: MetricsSnapshot): String? = when (metric) {
        Metric.CPU_TEMP -> s.cpuTempSource?.takeIf { s.cpuTempSupport == MetricSupport.SUPPORTED }?.let { "Source: $it" }
        Metric.GPU_TEMP -> s.gpuTempSource?.takeIf { s.gpuTempSupport == MetricSupport.SUPPORTED }?.let { "Source: $it" }
        Metric.BATTERY_TEMP -> "Battery pack sensor, not CPU or GPU"
        Metric.PING -> "${MetricsSnapshot.PING_METHOD} to ${s.pingTarget ?: "default target"} (not ICMP)"
        Metric.RAM -> s.ramAvailableBytes?.let { "${bytes(it)} available" + if (s.lowMemory == true) " · low memory" else "" }
        else -> null
    }

    /** Thermal status is a throttling level, never degrees. */
    fun thermalStatus(s: MetricsSnapshot): MetricDisplay {
        if (s.thermalStatusSupport != MetricSupport.SUPPORTED) {
            return display(Metric.CPU_TEMP, null, s.thermalStatusSupport, permission = s.requiredPermission(MetricKey.THERMAL_STATUS))
        }
        val level = s.thermalStatus ?: return MetricDisplay.Waiting
        return MetricDisplay.Value(level.name.lowercase(Locale.US).replaceFirstChar { it.uppercase(Locale.US) })
    }

    /** Headroom ratio as text: 1.0 = throttling now. Not a temperature. */
    fun headroom(s: MetricsSnapshot): String? {
        if (s.thermalHeadroomSupport != MetricSupport.SUPPORTED) return null
        val h = s.thermalHeadroom?.takeIf { it.isFinite() } ?: return null
        return String.format(Locale.US, "Headroom %.2f (1.00 = throttling)", h)
    }

    fun fps(v: Float): String = String.format(Locale.US, "%.0f", v)

    fun celsius(v: Float): String = String.format(Locale.US, "%.0f °C", v)

    fun percent(v: Float): String = String.format(Locale.US, "%.0f%%", v)

    /** Value only; the label (HudRows.PING_LABEL / "Latency (TCP)") says it is a TCP connect, not ICMP. */
    fun pingTcp(ms: Long): String = "$ms ms"

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
