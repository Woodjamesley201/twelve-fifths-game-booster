package com.twelvefifths.booster.optimize

import com.twelvefifths.booster.data.SessionRecord
import com.twelvefifths.booster.metrics.MetricsSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Mirrors `NotificationManager.INTERRUPTION_FILTER_*` so the engine is JVM-testable. */
object DndFilter {
    const val UNKNOWN = 0
    const val ALL = 1
    const val PRIORITY = 2
    const val NONE = 3
    const val ALARMS = 4
}

/** Everything the engine needs from the device. Android impl lives in [DefaultOptimizer]. */
interface SessionPlatform {
    /** False on Android 14+, where killBackgroundProcesses ignores other packages. */
    val canKillOtherApps: Boolean
    val selfPackage: String

    fun nowMs(): Long
    fun newId(): String

    /** Applies [CloseRules] with this device's protected set. */
    fun isClosable(packageName: String): Boolean
    fun killBackgroundProcesses(packageName: String): Boolean

    fun hasDndAccess(): Boolean
    fun currentDndFilter(): Int?
    fun setDndFilter(filter: Int): Boolean

    /** Power saver state, or null if unknown. */
    fun isPowerSaveMode(): Boolean?
}

/** Session persistence: history plus the in-flight session for crash recovery. */
interface SessionPersistence {
    suspend fun appendSession(record: SessionRecord)
    suspend fun saveActiveSession(session: OptimizationSession?)
    suspend fun loadActiveSession(): OptimizationSession?
}

/** Returns a real snapshot taken at or after [notBeforeMs], or null on timeout / unavailable. */
fun interface SnapshotProvider {
    suspend fun freshSnapshot(notBeforeMs: Long): MetricsSnapshot?
}

/**
 * Pure session logic: start, end, restore, and crash recovery. No Android types,
 * so it is unit-tested with fakes.
 *
 * Restore rules:
 * - DND is restored only if *we* changed it, and only if it is still the filter
 *   we set. If the user changed DND during the session we leave their choice.
 * - Killed background apps are not relaunched; Android restarts them on demand.
 * - Battery saver / game settings are only ever opened, never toggled, so there
 *   is nothing to revert; we note if battery saver is still on at the end.
 *
 * Owner: Script Nathan.
 */
class SessionEngine(
    private val platform: SessionPlatform,
    private val persistence: SessionPersistence,
    private val snapshots: SnapshotProvider,
    private val settleDelay: suspend () -> Unit = {},
) {
    private val mutex = Mutex()
    private val state = MutableStateFlow<OptimizationSession?>(null)
    private var recovered = false

    val activeSession: StateFlow<OptimizationSession?> = state.asStateFlow()

    suspend fun start(game: GameEntry, profile: OptimizationProfile): Result<OptimizationSession> = mutex.withLock {
        recoverLocked()
        if (state.value != null) {
            return Result.failure(IllegalStateException("A session is already running"))
        }
        if (game.packageName.isBlank()) {
            return Result.failure(IllegalArgumentException("No game selected"))
        }
        val notes = ArrayList<String>()
        val startedAt = platform.nowMs()
        val before = safeSnapshot(startedAt)
        if (before == null) notes += "Before-metrics unavailable."

        // 1. Background apps — user-selected only, safety-filtered again here.
        val requested = profile.closeBackgroundPackages
            .filter { it != game.packageName && it != platform.selfPackage }
            .sorted()
        val safe = requested.filter { safeIsClosable(it) }
        val unsafe = requested - safe.toSet()
        if (unsafe.isNotEmpty()) notes += "Skipped ${unsafe.size} protected app(s)."
        val closed = ArrayList<String>()
        if (safe.isNotEmpty()) {
            if (platform.canKillOtherApps) {
                for (pkg in safe) {
                    val ok = try {
                        platform.killBackgroundProcesses(pkg)
                    } catch (_: Throwable) {
                        false
                    }
                    if (ok) closed += pkg
                }
                notes += "Asked Android to close ${closed.size} of ${safe.size} background app(s)."
            } else {
                notes += "Android 14+ does not let apps close other apps' background processes; nothing was closed."
            }
        }

        // 2. Do Not Disturb.
        var dndByUs = false
        var previousFilter: Int? = null
        var setFilter: Int? = null
        if (profile.enableDoNotDisturb) {
            if (!safeHasDndAccess()) {
                notes += "Do Not Disturb skipped: notification-policy access not granted."
            } else {
                previousFilter = safeCall { platform.currentDndFilter() }
                if (previousFilter == null || previousFilter == DndFilter.UNKNOWN) {
                    notes += "Do Not Disturb skipped: current state unknown."
                } else if (previousFilter != DndFilter.ALL) {
                    notes += "Do Not Disturb was already on; left unchanged."
                } else if (safeCall { platform.setDndFilter(DndFilter.PRIORITY) } == true) {
                    dndByUs = true
                    setFilter = DndFilter.PRIORITY
                    notes += "Do Not Disturb on (priority only)."
                } else {
                    notes += "Do Not Disturb could not be enabled."
                }
            }
        }

        var session = OptimizationSession(
            id = platform.newId(),
            gamePackage = game.packageName,
            startedAtMs = startedAt,
            before = before,
            closedPackages = closed,
            dndWasEnabledByUs = dndByUs,
            previousDndFilter = previousFilter,
            requestedClosePackages = safe,
            skippedUnsafePackages = unsafe,
            closeSupported = platform.canKillOtherApps,
            dndFilterSetByUs = setFilter,
            notes = notes,
        )
        // Persist before anything else can fail so a crash still restores DND.
        safePersist { persistence.saveActiveSession(session) }
        state.value = session

        // 3. Let the system settle, then take the "after optimize" sample.
        try {
            settleDelay()
        } catch (_: Throwable) {
            // Ignore.
        }
        val afterOptimize = safeSnapshot(platform.nowMs())
        session = session.copy(afterOptimize = afterOptimize)
        state.value = session
        safePersist { persistence.saveActiveSession(session) }
        safePersist { persistence.appendSession(session.toRecord()) }
        Result.success(session)
    }

    suspend fun end(reason: SessionEndReason): Result<OptimizationSession> = mutex.withLock {
        recoverLocked()
        val current = state.value
            ?: return Result.failure(IllegalStateException("No active session"))
        val ended = finish(current, reason, takeAfterSnapshot = true)
        state.value = null
        Result.success(ended)
    }

    /**
     * Restores a session left behind by a dead process. Safe to call repeatedly;
     * only the first call does work. Returns the recovered session, if any.
     */
    suspend fun recoverStaleSession(): OptimizationSession? = mutex.withLock { recoverLocked() }

    private suspend fun recoverLocked(): OptimizationSession? {
        if (recovered) return null
        recovered = true
        if (state.value != null) return null
        val stale = try {
            persistence.loadActiveSession()
        } catch (_: Throwable) {
            null
        } ?: return null
        return finish(stale, SessionEndReason.RECOVERED_AFTER_RESTART, takeAfterSnapshot = false)
    }

    private suspend fun finish(
        current: OptimizationSession,
        reason: SessionEndReason,
        takeAfterSnapshot: Boolean,
    ): OptimizationSession {
        val notes = current.notes.toMutableList()
        val dndRestored = restoreDnd(current, notes)
        if (current.closedPackages.isNotEmpty()) {
            notes += "Closed apps are not relaunched; Android restarts them when needed."
        }
        if (safeCall { platform.isPowerSaveMode() } == true) {
            notes += "Battery saver is still on (it was opened in Settings, never switched by this app)."
        }
        val endedAt = platform.nowMs()
        val after = if (takeAfterSnapshot) safeSnapshot(endedAt) else null
        val ended = current.copy(
            endedAtMs = endedAt,
            after = after,
            dndRestored = dndRestored,
            endReason = reason,
            notes = notes,
        )
        safePersist { persistence.saveActiveSession(null) }
        safePersist { persistence.appendSession(ended.toRecord()) }
        return ended
    }

    /** Returns null when there was nothing of ours to restore. */
    private fun restoreDnd(session: OptimizationSession, notes: MutableList<String>): Boolean? {
        if (!session.dndWasEnabledByUs) return null
        if (!safeHasDndAccess()) {
            notes += "Could not restore Do Not Disturb: policy access was revoked."
            return false
        }
        val now = safeCall { platform.currentDndFilter() }
        val ours = session.dndFilterSetByUs ?: DndFilter.PRIORITY
        if (now != null && now != ours) {
            notes += "Do Not Disturb was changed during the session; kept your setting."
            return false
        }
        val target = session.previousDndFilter?.takeIf { it != DndFilter.UNKNOWN } ?: DndFilter.ALL
        val ok = safeCall { platform.setDndFilter(target) } == true
        notes += if (ok) "Do Not Disturb restored." else "Could not restore Do Not Disturb."
        return ok
    }

    private suspend fun safeSnapshot(notBeforeMs: Long): MetricsSnapshot? = try {
        snapshots.freshSnapshot(notBeforeMs)
    } catch (_: Throwable) {
        null
    }

    private fun safeIsClosable(pkg: String): Boolean = try {
        platform.isClosable(pkg)
    } catch (_: Throwable) {
        false
    }

    private fun safeHasDndAccess(): Boolean = try {
        platform.hasDndAccess()
    } catch (_: Throwable) {
        false
    }

    private inline fun <T> safeCall(block: () -> T): T? = try {
        block()
    } catch (_: Throwable) {
        null
    }

    private suspend inline fun safePersist(crossinline block: suspend () -> Unit) {
        try {
            block()
        } catch (_: Throwable) {
            // Persistence is best-effort; in-memory state stays authoritative.
        }
    }
}

fun OptimizationSession.toRecord(): SessionRecord = SessionRecord(
    id = id,
    gamePackage = gamePackage,
    startedAtMs = startedAtMs,
    endedAtMs = endedAtMs,
    before = before,
    after = after,
    closedPackages = closedPackages,
    afterOptimize = afterOptimize,
    requestedClosePackages = requestedClosePackages,
    closeSupported = closeSupported,
    dndEnabledByUs = dndWasEnabledByUs,
    dndRestored = dndRestored,
    endReason = endReason?.name,
    notes = notes,
)
