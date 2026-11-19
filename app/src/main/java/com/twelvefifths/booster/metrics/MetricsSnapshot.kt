package com.twelvefifths.booster.metrics

import com.twelvefifths.booster.optimize.BoosterPermission

/**
 * One sample of system / game metrics.
 *
 * Every metric is nullable and has a matching [MetricSupport] field.
 * The UI must render a support explanation — never a fabricated number —
 * when [MetricSupport] is not [MetricSupport.SUPPORTED] or when the value is null.
 *
 * [fps] is *game* frames per second only when a legitimate game-FPS
 * source exists. Overlay frame pacing is not game FPS and must not be
 * written here.
 *
 * Temperatures:
 * - [cpuTempC] / [gpuTempC] are only ever real CPU / GPU thermal-zone readings;
 *   [cpuTempSource] / [gpuTempSource] name the zone (e.g. `thermal_zone3 (cpu-big-core0)`).
 * - [batteryTempC] is the battery pack temperature. It is a separate field and is
 *   never copied into the CPU/GPU fields.
 * - [thermalStatus] / [thermalHeadroom] are PowerManager levels, not degrees.
 *   [thermalHeadroom] is a ratio where 1.0 means "throttling now".
 * - Use [temperatureSummary] for the labelled CPU → battery → status fallback.
 *
 * Ping: [pingMs] is the time of a TCP handshake to [pingTarget] (not ICMP).
 * [pingStatus] explains a null value.
 *
 * Fields after [vramSupport] were added by the engine and all have defaults,
 * so existing constructor calls keep compiling.
 *
 * Owner: Script Nathan.
 */
data class MetricsSnapshot(
    val timestampMs: Long,
    val fps: Float?,
    val fpsSupport: MetricSupport,
    val cpuTempC: Float?,
    val cpuTempSupport: MetricSupport,
    val gpuTempC: Float?,
    val gpuTempSupport: MetricSupport,
    val ramUsedBytes: Long?,
    val ramTotalBytes: Long?,
    val ramPercent: Float?,
    val ramSupport: MetricSupport,
    val cpuUsage: Float?,
    val cpuUsageSupport: MetricSupport,
    val gpuUsage: Float?,
    val gpuUsageSupport: MetricSupport,
    val pingMs: Long?,
    val pingSupport: MetricSupport,
    val vramUsedBytes: Long?,
    val vramSupport: MetricSupport,
    val cpuTempSource: String? = null,
    val gpuTempSource: String? = null,
    val batteryTempC: Float? = null,
    val batteryTempSupport: MetricSupport = MetricSupport.UNSUPPORTED,
    val thermalStatus: ThermalStatusLevel? = null,
    val thermalStatusSupport: MetricSupport = MetricSupport.UNSUPPORTED,
    val thermalHeadroom: Float? = null,
    val thermalHeadroomSupport: MetricSupport = MetricSupport.UNSUPPORTED,
    val ramAvailableBytes: Long? = null,
    val lowMemory: Boolean? = null,
    val pingTarget: String? = null,
    val pingStatus: PingStatus = PingStatus.NOT_MEASURED,
    /**
     * For every metric whose support is [MetricSupport.NEEDS_PERMISSION], the
     * permission that unlocks it. Read it with [requiredPermission]. Entries for
     * metrics in other states are ignored. Not persisted in session history.
     */
    val requiredPermissions: Map<MetricKey, BoosterPermission> = emptyMap(),
) {
    companion object {
        /** Ping is always a TCP handshake time; use this string when labelling it. */
        const val PING_METHOD = "TCP connect"

        fun empty(timestampMs: Long = System.currentTimeMillis()): MetricsSnapshot =
            MetricsSnapshot(
                timestampMs = timestampMs,
                fps = null,
                fpsSupport = MetricSupport.UNSUPPORTED,
                cpuTempC = null,
                cpuTempSupport = MetricSupport.UNSUPPORTED,
                gpuTempC = null,
                gpuTempSupport = MetricSupport.UNSUPPORTED,
                ramUsedBytes = null,
                ramTotalBytes = null,
                ramPercent = null,
                ramSupport = MetricSupport.UNSUPPORTED,
                cpuUsage = null,
                cpuUsageSupport = MetricSupport.UNSUPPORTED,
                gpuUsage = null,
                gpuUsageSupport = MetricSupport.UNSUPPORTED,
                pingMs = null,
                pingSupport = MetricSupport.UNSUPPORTED,
                vramUsedBytes = null,
                vramSupport = MetricSupport.UNSUPPORTED,
            )
    }
}
