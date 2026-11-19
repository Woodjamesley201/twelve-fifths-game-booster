package com.twelvefifths.booster.metrics

/**
 * [android.os.PowerManager] thermal status. This is a coarse throttling level,
 * **not** a temperature, and must never be shown as degrees.
 *
 * Owner: Script Nathan.
 */
enum class ThermalStatusLevel {
    NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN;

    companion object {
        /** Maps `PowerManager.THERMAL_STATUS_*` (0..6). Unknown values return null. */
        fun fromPlatform(value: Int): ThermalStatusLevel? = entries.getOrNull(value)
    }
}

/** Where the best available device temperature came from. */
enum class TemperatureSource {
    /** A readable CPU zone under /sys/class/thermal. Real CPU temperature. */
    CPU_THERMAL_ZONE,
    /** BatteryManager EXTRA_TEMPERATURE. Battery pack temperature, not CPU. */
    BATTERY,
    /** PowerManager thermal status / headroom. A level, not degrees. */
    THERMAL_STATUS,
}

/**
 * The best honest answer to "how hot is the device?" for UI / HUD, following
 * the fallback chain CPU zone → battery → thermal status. [celsius] is null
 * for [TemperatureSource.THERMAL_STATUS]. [label] already names the source.
 */
data class TemperatureSummary(
    val source: TemperatureSource,
    val label: String,
    val celsius: Float?,
    val status: ThermalStatusLevel?,
    val headroom: Float?,
    val detail: String?,
)

fun MetricsSnapshot.temperatureSummary(): TemperatureSummary? {
    val cpu = cpuTempC
    if (cpuTempSupport == MetricSupport.SUPPORTED && cpu != null) {
        return TemperatureSummary(TemperatureSource.CPU_THERMAL_ZONE, "CPU °C", cpu, thermalStatus, thermalHeadroom, cpuTempSource)
    }
    val batt = batteryTempC
    if (batteryTempSupport == MetricSupport.SUPPORTED && batt != null) {
        return TemperatureSummary(TemperatureSource.BATTERY, "BATT °C", batt, thermalStatus, thermalHeadroom, "battery sensor")
    }
    val status = thermalStatus
    if (thermalStatusSupport == MetricSupport.SUPPORTED && status != null) {
        return TemperatureSummary(TemperatureSource.THERMAL_STATUS, "THERMAL", null, status, thermalHeadroom, "PowerManager thermal status")
    }
    return null
}
