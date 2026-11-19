package com.twelvefifths.booster.optimize

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.twelvefifths.booster.data.BoosterStore
import com.twelvefifths.booster.data.SessionRecord
import com.twelvefifths.booster.metrics.MetricsSource
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Session-based optimizer. Only closes user-selected non-system packages,
 * optionally enables DND when policy access is granted, and restores both
 * when the session ends. Battery saver / game mode are opened as Settings
 * screens — this app does not flip those switches itself.
 *
 * Owner: Script Nathan.
 */
class DefaultOptimizer(
    context: Context,
    private val metricsSource: MetricsSource,
    private val store: BoosterStore,
) : Optimizer {

    private val appContext = context.applicationContext
    private val activityManager =
        appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val sessionState = MutableStateFlow<OptimizationSession?>(null)

    override val activeSession: Flow<OptimizationSession?> = sessionState.asStateFlow()

    override suspend fun startSession(
        game: GameEntry,
        profile: OptimizationProfile,
    ): Result<OptimizationSession> {
        if (sessionState.value != null) {
            return Result.failure(IllegalStateException("A session is already running"))
        }
        val before = withTimeoutOrNull(2_000L) { metricsSource.snapshots().first() }
        val closed = closeUserSelected(profile.closeBackgroundPackages, skip = game.packageName)
        val dnd = maybeEnableDnd(profile.enableDoNotDisturb)
        val session = OptimizationSession(
            id = UUID.randomUUID().toString(),
            gamePackage = game.packageName,
            startedAtMs = System.currentTimeMillis(),
            before = before,
            closedPackages = closed,
            dndWasEnabledByUs = dnd.enabledByUs,
            previousDndFilter = dnd.previousFilter,
        )
        sessionState.value = session
        store.saveProfile(profile)
        store.appendSession(session.toRecord())
        maybeOpenSettings(profile)
        return Result.success(session)
    }

    override suspend fun endSession(): Result<OptimizationSession> {
        val current = sessionState.value
            ?: return Result.failure(IllegalStateException("No active session"))
        restoreDnd(current)
        val after = withTimeoutOrNull(2_000L) { metricsSource.snapshots().first() }
        val ended = current.copy(
            endedAtMs = System.currentTimeMillis(),
            after = after,
        )
        sessionState.value = null
        store.appendSession(ended.toRecord())
        return Result.success(ended)
    }

    private fun closeUserSelected(packages: Set<String>, skip: String): List<String> {
        val closed = mutableListOf<String>()
        for (pkg in packages) {
            if (pkg == skip) continue
            if (!PackageSafety.canClose(appContext, pkg)) continue
            try {
                activityManager.killBackgroundProcesses(pkg)
                closed.add(pkg)
            } catch (_: Throwable) {
                // Permission or platform restriction — skip this package.
            }
        }
        return closed
    }

    private data class DndChange(val enabledByUs: Boolean, val previousFilter: Int?)

    private fun maybeEnableDnd(requested: Boolean): DndChange {
        if (!requested) return DndChange(false, null)
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            return DndChange(false, null)
        }
        val previous = notificationManager.currentInterruptionFilter
        if (previous == NotificationManager.INTERRUPTION_FILTER_NONE ||
            previous == NotificationManager.INTERRUPTION_FILTER_PRIORITY
        ) {
            return DndChange(false, previous)
        }
        return try {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            DndChange(true, previous)
        } catch (_: Throwable) {
            DndChange(false, previous)
        }
    }

    private fun restoreDnd(session: OptimizationSession) {
        if (!session.dndWasEnabledByUs) return
        if (!notificationManager.isNotificationPolicyAccessGranted) return
        val restore = session.previousDndFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
        try {
            notificationManager.setInterruptionFilter(restore)
        } catch (_: Throwable) {
            // Best-effort restore.
        }
    }

    private fun maybeOpenSettings(profile: OptimizationProfile) {
        if (profile.suggestBatterySaverSettings) {
            openSettings(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        }
        if (profile.suggestGameModeSettings) {
            val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Settings.ACTION_APP_USAGE_SETTINGS
            } else {
                Settings.ACTION_SETTINGS
            }
            // There is no cross-OEM "game mode" intent. Open a nearby settings
            // screen; Samsung / others expose Game Booster / Game Plugins themselves.
            openSettings(action)
        }
    }

    private fun openSettings(action: String) {
        try {
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
        } catch (_: Throwable) {
            try {
                val fallback = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(fallback)
            } catch (_: Throwable) {
                // Ignore — settings are a suggestion, not a requirement.
            }
        }
    }

    private fun OptimizationSession.toRecord(): SessionRecord = SessionRecord(
        id = id,
        gamePackage = gamePackage,
        startedAtMs = startedAtMs,
        endedAtMs = endedAtMs,
        before = before,
        after = after,
        closedPackages = closedPackages,
    )
}
