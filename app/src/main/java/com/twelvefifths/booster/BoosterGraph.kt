package com.twelvefifths.booster

import android.content.Context
import com.twelvefifths.booster.data.BoosterStore
import com.twelvefifths.booster.metrics.AndroidMetricsSource
import com.twelvefifths.booster.metrics.MetricsSource
import com.twelvefifths.booster.optimize.DefaultGameLibrary
import com.twelvefifths.booster.optimize.DefaultOptimizer
import com.twelvefifths.booster.optimize.ForegroundAppDetector
import com.twelvefifths.booster.optimize.GameLibrary
import com.twelvefifths.booster.optimize.Optimizer
import com.twelvefifths.booster.optimize.UsageStatsForegroundAppDetector

/**
 * Process-wide dependencies. Every field is assigned in [init] in declaration
 * order so later fields can safely read earlier ones. Do not read these
 * properties before [init] returns.
 *
 * Owner: Goji (integration).
 */
class BoosterGraph(context: Context) {
    private val appContext = context.applicationContext
    val store: BoosterStore = BoosterStore(appContext)
    val metricsSource: MetricsSource = AndroidMetricsSource(appContext, store.pingTarget)
    val gameLibrary: GameLibrary = DefaultGameLibrary(appContext, store)
    val foregroundAppDetector: ForegroundAppDetector = UsageStatsForegroundAppDetector(appContext)
    val optimizer: Optimizer = DefaultOptimizer(appContext, metricsSource, store, foregroundAppDetector)
}
