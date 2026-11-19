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

    /**
     * Every app with a launcher (MAIN/LAUNCHER) activity — system apps included,
     * this app excluded — for the "add game" picker. Runs on Dispatchers.IO;
     * safe to call from the main thread / a LaunchedEffect. Sorted by label.
     * Never throws; returns an empty list if the package manager fails.
     */
    suspend fun listLaunchableApps(): List<LaunchableApp>
}

/**
 * One launchable app. [isGame] is true for CATEGORY_GAME or the legacy
 * `android:isGame` flag. [isSystem] is informational (system / updated-system).
 */
data class LaunchableApp(
    val packageName: String,
    val label: String,
    val isGame: Boolean,
    val isSystem: Boolean,
)

/** Pure dedupe / filter / sort used by [DefaultGameLibrary.listLaunchableApps]. */
object LaunchableApps {
    fun normalize(apps: List<LaunchableApp>, selfPackage: String): List<LaunchableApp> =
        apps.asSequence()
            .filter { it.packageName.isNotBlank() && it.packageName != selfPackage }
            .distinctBy { it.packageName }
            .map { if (it.label.isBlank()) it.copy(label = it.packageName) else it }
            .sortedWith(compareBy<LaunchableApp> { it.label.lowercase() }.thenBy { it.packageName })
            .toList()
}
