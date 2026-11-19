package com.twelvefifths.booster.optimize

import kotlinx.coroutines.flow.Flow

/**
 * Installed + manually added games, last-played timestamps, and launch.
 *
 * Owner: Script Nathan.
 */
interface GameLibrary {
    fun observeGames(): Flow<List<GameEntry>>

    suspend fun refreshInstalledGames()

    suspend fun addGameManually(packageName: String, displayName: String)

    suspend fun removeManualGame(packageName: String)

    suspend fun markPlayed(packageName: String, atMs: Long = System.currentTimeMillis())

    /** Returns the launch intent action result: true if a launch was started. */
    fun launchGame(packageName: String): Boolean
}
