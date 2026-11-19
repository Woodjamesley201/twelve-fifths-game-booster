package com.twelvefifths.booster.optimize

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import com.twelvefifths.booster.data.BoosterStore
import com.twelvefifths.booster.data.SessionRecord
import com.twelvefifths.booster.metrics.MetricsSource
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Android adapter around [SessionEngine].
 *
 * - Closes only user-selected, safety-filtered packages (a platform no-op for
 *   other apps on Android 14+, reported honestly in the session notes).
 * - Enables DND when policy access is granted, and restores it at the end.
 * - Opens battery-saver / game-hub screens when the profile asks; never toggles them.
 * - Optionally launches the game, and auto-ends the session (restoring
 *   everything) when the game leaves the foreground.
 * - Recovers a session left behind by a killed process the first time the
 *   session state is observed or a session is started (never at construction).
 *
 * Owner: Script Nathan.
 */
class DefaultOptimizer(
    context: Context,
    private val metricsSource: MetricsSource,
    private val store: BoosterStore,
    private val foregroundAppDetector: ForegroundAppDetector? = null,
) : Optimizer {

    private val appContext = context.applicationContext
    private val activityManager =
        appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    private val powerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watchJob: Job? = null

    private val platform: SessionPlatform = object : SessionPlatform {
        override val canKillOtherApps: Boolean get() = OptimizerCapabilities.canKillOtherApps
        override val selfPackage: String get() = appContext.packageName
        private var protectedCache: Set<String>? = null

        override fun nowMs(): Long = System.currentTimeMillis()
        override fun newId(): String = UUID.randomUUID().toString()

        override fun isClosable(packageName: String): Boolean {
            val protectedSet = protectedCache ?: PackageSafety.protectedPackages(appContext).also { protectedCache = it }
            return PackageSafety.canClose(appContext, packageName, protectedSet)
        }

        override fun killBackgroundProcesses(packageName: String): Boolean {
            val am = activityManager ?: return false
            am.killBackgroundProcesses(packageName)
            return true
        }

        override fun hasDndAccess(): Boolean = notificationManager?.isNotificationPolicyAccessGranted == true

        override fun currentDndFilter(): Int? = notificationManager?.currentInterruptionFilter

        override fun setDndFilter(filter: Int): Boolean {
            val nm = notificationManager ?: return false
            nm.setInterruptionFilter(filter)
            return true
        }

        override fun isPowerSaveMode(): Boolean? = powerManager?.isPowerSaveMode
    }

    private val persistence: SessionPersistence = object : SessionPersistence {
        override suspend fun appendSession(record: SessionRecord) = store.appendSession(record)
        override suspend fun saveActiveSession(session: OptimizationSession?) = store.setActiveSession(session)
        override suspend fun loadActiveSession(): OptimizationSession? = store.activeSession()
    }

    private val snapshotProvider = SnapshotProvider { notBeforeMs ->
        withTimeoutOrNull(SNAPSHOT_TIMEOUT_MS) {
            metricsSource.snapshots().first { it.timestampMs >= notBeforeMs - FRESHNESS_SLACK_MS }
        }
    }

    // Declared after everything it reads.
    private val engine = SessionEngine(
        platform = platform,
        persistence = persistence,
        snapshots = snapshotProvider,
        settleDelay = { delay(SETTLE_MS) },
    )

    override val activeSession: Flow<OptimizationSession?> = flow {
        withContext(Dispatchers.IO) { engine.recoverStaleSession() }
        emitAll(engine.activeSession)
    }

    override suspend fun startSession(
        game: GameEntry,
        profile: OptimizationProfile,
    ): Result<OptimizationSession> {
        val result = withContext(Dispatchers.IO) {
            try {
                engine.start(game, profile.copy(gamePackage = game.packageName))
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
        val session = result.getOrNull() ?: return result
        try {
            store.saveProfile(profile.copy(gamePackage = game.packageName))
        } catch (_: Throwable) {
            // Profile save is best-effort.
        }
        val openedSettings = openSuggestedSettings(profile, game.packageName)
        if (profile.launchGameOnStart && !openedSettings) launchGame(game.packageName)
        if (profile.autoEndWhenGameLeaves) watchForeground(session)
        return result
    }

    override suspend fun endSession(reason: SessionEndReason): Result<OptimizationSession> {
        if (reason == SessionEndReason.USER) {
            watchJob?.cancel()
            watchJob = null
        }
        return withContext(Dispatchers.IO) {
            try {
                engine.end(reason)
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
    }

    private fun watchForeground(session: OptimizationSession) {
        val detector = foregroundAppDetector ?: return
        watchJob?.cancel()
        watchJob = scope.launch {
            if (!detector.hasUsageAccess()) return@launch
            val policy = GameExitPolicy(
                gamePackage = session.gamePackage,
                ignoredPackages = GameExitPolicy.SYSTEM_TRANSIENTS + appContext.packageName,
            )
            detector.observeForegroundApp().collect { pkg ->
                val active = engine.activeSession.value
                if (active == null || active.id != session.id) {
                    watchJob?.cancel()
                    return@collect
                }
                if (policy.onForeground(pkg, System.currentTimeMillis()) == GameExitPolicy.Decision.END) {
                    engine.end(SessionEndReason.GAME_LEFT_FOREGROUND)
                    watchJob?.cancel()
                }
            }
        }
    }

    /** Returns true if a settings screen was opened. */
    private fun openSuggestedSettings(profile: OptimizationProfile, gamePackage: String): Boolean {
        var opened = false
        if (profile.suggestBatterySaverSettings) {
            opened = SettingsShortcuts.open(appContext, SettingsShortcuts.batterySaver()) || opened
        }
        if (profile.suggestGameModeSettings) {
            val shortcut = SettingsShortcuts.gameMode(appContext, gamePackage)
            if (shortcut != null) opened = SettingsShortcuts.open(appContext, shortcut) || opened
        }
        return opened
    }

    private fun launchGame(packageName: String): Boolean {
        val intent = try {
            appContext.packageManager.getLaunchIntentForPackage(packageName)
        } catch (_: Throwable) {
            null
        } ?: return false
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private companion object {
        const val SNAPSHOT_TIMEOUT_MS = 3_000L
        const val FRESHNESS_SLACK_MS = 250L
        const val SETTLE_MS = 1_500L
    }
}

/** Convenience for UI: did this session sample both ends? */
val OptimizationSession.hasComparison: Boolean
    get() = before != null && (after != null || afterOptimize != null)

