package com.twelvefifths.booster.data

import com.twelvefifths.booster.metrics.MetricsSnapshot

/**
 * Persisted history of an optimization session, including the real
 * before / after snapshots (never fabricated).
 *
 * - [before]: sampled before anything changed.
 * - [afterOptimize]: sampled a moment after the start actions ran.
 * - [after]: sampled when the session ended (null while running or after a crash recovery).
 * - [closeSupported] is false on Android 14+, where closing other apps is a platform no-op.
 * - [endReason] is a [com.twelvefifths.booster.optimize.SessionEndReason] name, or null while running.
 *
 * Fields after [closedPackages] have defaults so older call sites compile.
 *
 * Owner: Script Nathan.
 */
data class SessionRecord(
    val id: String,
    val gamePackage: String,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val before: MetricsSnapshot?,
    val after: MetricsSnapshot?,
    val closedPackages: List<String>,
    val afterOptimize: MetricsSnapshot? = null,
    val requestedClosePackages: List<String> = emptyList(),
    val closeSupported: Boolean = true,
    val dndEnabledByUs: Boolean = false,
    val dndRestored: Boolean? = null,
    val endReason: String? = null,
    val notes: List<String> = emptyList(),
)
