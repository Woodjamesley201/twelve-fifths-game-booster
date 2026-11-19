package com.twelvefifths.booster.optimize

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn

/**
 * Foreground-app detection through [UsageStatsManager]. Emits null until the
 * user grants usage access in system settings.
 *
 * Incremental: each poll reads only the events since the previous poll (plus a
 * small overlap) and feeds them to a [ForegroundTracker], so a game that has
 * been in front for a long time stays detected even when no new events arrive.
 * One poller is shared by every collector.
 *
 * Owner: Script Nathan.
 */
class UsageStatsForegroundAppDetector(
    context: Context,
) : ForegroundAppDetector {

    private val appContext = context.applicationContext
    private val usageStatsManager =
        appContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val permissions = PermissionChecker(appContext)
    private val tracker = ForegroundTracker()
    private var lastQueryEndMs: Long = 0L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Declared after every field it reads.
    private val shared: SharedFlow<String?> = flow {
        while (true) {
            emit(if (hasUsageAccess()) poll() else resetAndNull())
            delay(POLL_MS)
        }
    }.distinctUntilChanged()
        .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    override fun hasUsageAccess(): Boolean = permissions.hasUsageAccess()

    override fun observeForegroundApp(): Flow<String?> = shared

    private fun resetAndNull(): String? {
        tracker.reset()
        lastQueryEndMs = 0L
        return null
    }

    private fun poll(): String? {
        val manager = usageStatsManager ?: return null
        val end = System.currentTimeMillis()
        val start = if (lastQueryEndMs == 0L || end < lastQueryEndMs) {
            end - INITIAL_WINDOW_MS
        } else {
            lastQueryEndMs - OVERLAP_MS
        }
        val events = try {
            manager.queryEvents(start, end)
        } catch (_: Throwable) {
            return tracker.current
        } ?: return tracker.current
        lastQueryEndMs = end
        val batch = ArrayList<ForegroundTracker.Event>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            if (!events.getNextEvent(event)) break
            val resumed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            } else {
                @Suppress("DEPRECATION")
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            }
            if (resumed) {
                batch.add(ForegroundTracker.Event(event.packageName, ForegroundTracker.Kind.RESUMED, event.timeStamp))
            }
        }
        return tracker.apply(batch)
    }

    private companion object {
        const val POLL_MS = 1_000L
        const val OVERLAP_MS = 2_000L
        const val INITIAL_WINDOW_MS = 30 * 60_000L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
