package com.twelvefifths.booster.metrics

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
) {
    companion object {
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
