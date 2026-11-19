package com.twelvefifths.booster.optimize

import com.twelvefifths.booster.metrics.MetricsSnapshot

/**
 * A started optimization session.
 *
 * - [before]: real snapshot taken before anything changed (null if metrics were unavailable).
 * - [afterOptimize]: real snapshot taken shortly after the start actions ran.
 * - [after]: real snapshot taken when the session ended.
 * - [requestedClosePackages]: what the profile asked to close (after safety filtering).
 * - [closedPackages]: packages killBackgroundProcesses was actually called for.
 *   Always empty on Android 14+ where the platform ignores the call for other
 *   apps ([closeSupported] = false).
 * - [dndWasEnabledByUs] / [previousDndFilter] / [dndFilterSetByUs]: what to restore.
 * - [notes]: plain-language log of what was and was not done, for the UI.
 *
 * Owner: Script Nathan.
 */
data class OptimizationSession(
    val id: String,
    val gamePackage: String,
    val startedAtMs: Long,
    val endedAtMs: Long? = null,
    val before: MetricsSnapshot? = null,
    val after: MetricsSnapshot? = null,
    val closedPackages: List<String> = emptyList(),
    val dndWasEnabledByUs: Boolean = false,
    val previousDndFilter: Int? = null,
    val afterOptimize: MetricsSnapshot? = null,
    val requestedClosePackages: List<String> = emptyList(),
    val skippedUnsafePackages: List<String> = emptyList(),
    val closeSupported: Boolean = true,
    val dndFilterSetByUs: Int? = null,
    val dndRestored: Boolean? = null,
    val endReason: SessionEndReason? = null,
    val notes: List<String> = emptyList(),
)

enum class SessionEndReason {
    /** The user tapped End. */
    USER,
    /** The game left the foreground for longer than the grace period. */
    GAME_LEFT_FOREGROUND,
    /** The app process died mid-session; restored on next start. */
    RECOVERED_AFTER_RESTART,
}
