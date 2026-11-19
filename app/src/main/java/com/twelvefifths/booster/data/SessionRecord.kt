package com.twelvefifths.booster.data

import com.twelvefifths.booster.metrics.MetricsSnapshot

/**
 * Persisted history of an optimization session, including the real
 * before/after snapshots (never fabricated).
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
)
