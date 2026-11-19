package com.twelvefifths.booster.optimize

import kotlinx.coroutines.flow.Flow

/**
 * Observes the current foreground package via [android.app.usage.UsageStatsManager].
 * Requires the usage-access permission granted in system settings.
 *
 * Owner: Script Nathan.
 */
interface ForegroundAppDetector {
    fun hasUsageAccess(): Boolean

    /** Emits the foreground package name, or null if unknown / no permission. */
    fun observeForegroundApp(): Flow<String?>
}
