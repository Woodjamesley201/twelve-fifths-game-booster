package com.twelvefifths.booster.metrics

/**
 * `BatteryManager.EXTRA_TEMPERATURE` is reported in tenths of a degree C.
 * This is the battery pack temperature — never a CPU or GPU temperature.
 *
 * Owner: Script Nathan.
 */
object BatteryTemperature {
    /** Returns °C, or null for the "missing" sentinel and implausible values. */
    fun fromTenths(tenths: Int): Float? {
        if (tenths == Int.MIN_VALUE || tenths == -1) return null
        val c = tenths / 10f
        return if (c in -30f..90f) c else null
    }
}
