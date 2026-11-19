package com.twelvefifths.booster.metrics

import kotlinx.coroutines.flow.Flow

/**
 * Produces a continuous stream of real metric samples.
 * Implementations must never invent values; use [MetricSupport] instead.
 *
 * Owner: Script Nathan.
 */
interface MetricsSource {
    fun snapshots(): Flow<MetricsSnapshot>
}
