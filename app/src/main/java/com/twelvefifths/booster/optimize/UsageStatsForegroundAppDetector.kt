package com.twelvefifths.booster.optimize

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Foreground-app detection through [UsageStatsManager]. Emits null until the
 * user grants usage access in system settings.
 *
 * Owner: Script Nathan.
 */
class UsageStatsForegroundAppDetector(
    context: Context,
) : ForegroundAppDetector {

    private val appContext = context.applicationContext
    private val usageStatsManager =
        appContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val appOpsManager =
        appContext.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager

    override fun hasUsageAccess(): Boolean {
        val ops = appOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun observeForegroundApp(): Flow<String?> = flow {
        while (true) {
            emit(if (hasUsageAccess()) currentForegroundPackage() else null)
            delay(POLL_MS)
        }
    }

    private fun currentForegroundPackage(): String? {
        val manager = usageStatsManager ?: return null
        val end = System.currentTimeMillis()
        val start = end - WINDOW_MS
        val events = try {
            manager.queryEvents(start, end)
        } catch (_: Throwable) {
            return null
        } ?: return null
        var latestPkg: String? = null
        var latestStamp = 0L
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val isMoveToFg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            } else {
                @Suppress("DEPRECATION")
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            }
            if (isMoveToFg && event.timeStamp >= latestStamp) {
                latestStamp = event.timeStamp
                latestPkg = event.packageName
            }
        }
        return latestPkg
    }

    private companion object {
        const val POLL_MS = 1_000L
        const val WINDOW_MS = 10_000L
    }
}
