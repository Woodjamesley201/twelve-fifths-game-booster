package com.twelvefifths.booster

import android.app.Application

/**
 * Creates the dependency graph after the Application is constructed.
 * [graph] is assigned at the top of [onCreate] before anything else reads it.
 *
 * Owner: Goji.
 */
class BoosterApplication : Application() {

    lateinit var graph: BoosterGraph
        private set

    val store get() = graph.store
    val metricsSource get() = graph.metricsSource
    val gameLibrary get() = graph.gameLibrary
    val foregroundAppDetector get() = graph.foregroundAppDetector
    val optimizer get() = graph.optimizer

    override fun onCreate() {
        super.onCreate()
        graph = BoosterGraph(this)
    }
}
