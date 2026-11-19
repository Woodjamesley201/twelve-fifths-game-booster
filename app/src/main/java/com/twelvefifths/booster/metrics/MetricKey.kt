package com.twelvefifths.booster.metrics

import com.twelvefifths.booster.optimize.BoosterPermission

/**
 * Identifies one metric in a [MetricsSnapshot], so per-metric facts (support,
 * required permission) can be looked up generically.
 *
 * Owner: Script Nathan.
 */
enum class MetricKey {
    FPS, CPU_TEMP, GPU_TEMP, BATTERY_TEMP, THERMAL_STATUS, THERMAL_HEADROOM,
    RAM, CPU_USAGE, GPU_USAGE, PING, VRAM,
}

/** The [MetricSupport] the snapshot reports for [key]. */
fun MetricsSnapshot.support(key: MetricKey): MetricSupport = when (key) {
    MetricKey.FPS -> fpsSupport
    MetricKey.CPU_TEMP -> cpuTempSupport
    MetricKey.GPU_TEMP -> gpuTempSupport
    MetricKey.BATTERY_TEMP -> batteryTempSupport
    MetricKey.THERMAL_STATUS -> thermalStatusSupport
    MetricKey.THERMAL_HEADROOM -> thermalHeadroomSupport
    MetricKey.RAM -> ramSupport
    MetricKey.CPU_USAGE -> cpuUsageSupport
    MetricKey.GPU_USAGE -> gpuUsageSupport
    MetricKey.PING -> pingSupport
    MetricKey.VRAM -> vramSupport
}

/**
 * The permission that would unlock [key], or null when [key] is not in the
 * [MetricSupport.NEEDS_PERMISSION] state. Resolve the settings intent with
 * `PermissionChecker.state(permission).settingsIntent` (or `openSettings`).
 *
 * If a metric is NEEDS_PERMISSION but the engine did not name a permission
 * (should not happen), this returns null — do not guess one.
 */
fun MetricsSnapshot.requiredPermission(key: MetricKey): BoosterPermission? =
    if (support(key) == MetricSupport.NEEDS_PERMISSION) requiredPermissions[key] else null
