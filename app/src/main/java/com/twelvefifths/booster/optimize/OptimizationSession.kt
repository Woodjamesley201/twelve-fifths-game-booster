package com.twelvefifths.booster.optimize

import com.twelvefifths.booster.metrics.MetricsSnapshot

/**
 * A started optimization session. [before] is a real snapshot taken at start
 * (or null if metrics were unavailable). [after] is filled when the session ends.
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
)
