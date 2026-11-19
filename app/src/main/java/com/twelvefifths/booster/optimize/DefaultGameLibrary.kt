package com.twelvefifths.booster.optimize

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.twelvefifths.booster.data.BoosterStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Combines [ApplicationInfo.CATEGORY_GAME] detections with manually added entries.
 *
 * Owner: Script Nathan.
 */
class DefaultGameLibrary(
    context: Context,
    private val store: BoosterStore,
) : GameLibrary {

    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val detected = MutableStateFlow<List<GameEntry>>(emptyList())

    override fun observeGames(): Flow<List<GameEntry>> =
        combine(detected, store.manualGames, store.lastPlayed) { auto, manual, played ->
            val byPackage = LinkedHashMap<String, GameEntry>()
            for (game in auto) {
                byPackage[game.packageName] = game.copy(lastPlayedMs = played[game.packageName])
            }
            for (game in manual) {
                val existing = byPackage[game.packageName]
                if (existing == null) {
                    byPackage[game.packageName] = game.copy(lastPlayedMs = played[game.packageName] ?: game.lastPlayedMs)
                } else {
                    byPackage[game.packageName] = existing.copy(
                        isManual = true,
                        lastPlayedMs = played[game.packageName] ?: existing.lastPlayedMs ?: game.lastPlayedMs,
                    )
                }
            }
            byPackage.values.sortedWith(
                compareByDescending<GameEntry> { it.lastPlayedMs ?: 0L }
                    .thenBy { it.displayName.lowercase() },
            )
        }

    override suspend fun refreshInstalledGames() {
        val found = withContext(Dispatchers.IO) { scanInstalledGames() }
        detected.update { found }
    }

    override suspend fun addGameManually(packageName: String, displayName: String) {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return
        val name = displayName.trim().ifEmpty { labelFor(pkg) ?: pkg }
        store.upsertManualGame(
            GameEntry(
                packageName = pkg,
                displayName = name,
                isManual = true,
            ),
        )
    }

    override suspend fun removeManualGame(packageName: String) {
        store.removeManualGame(packageName)
    }

    override suspend fun markPlayed(packageName: String, atMs: Long) {
        store.markPlayed(packageName, atMs)
    }

    override fun launchGame(packageName: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun scanInstalledGames(): List<GameEntry> {
        val flags = PackageManager.GET_META_DATA
        val installed = try {
            packageManager.getInstalledApplications(flags)
        } catch (_: Throwable) {
            emptyList()
        }
        return installed.mapNotNull { info ->
            if (info.packageName == appContext.packageName) return@mapNotNull null
            val isGame = info.category == ApplicationInfo.CATEGORY_GAME
            if (!isGame) return@mapNotNull null
            GameEntry(
                packageName = info.packageName,
                displayName = labelFor(info) ?: info.packageName,
                isManual = false,
                isSystem = PackageSafety.isSystemPackage(info),
            )
        }
    }

    private fun labelFor(packageName: String): String? {
        return try {
            labelFor(packageManager.getApplicationInfo(packageName, 0))
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun labelFor(info: ApplicationInfo): String? =
        try {
            packageManager.getApplicationLabel(info)?.toString()
        } catch (_: Throwable) {
            null
        }
}
