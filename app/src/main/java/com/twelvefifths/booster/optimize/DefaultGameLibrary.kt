package com.twelvefifths.booster.optimize

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.twelvefifths.booster.data.BoosterStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Combines [ApplicationInfo.CATEGORY_GAME] (and legacy `FLAG_IS_GAME`) detections
 * with manually added entries persisted in [BoosterStore].
 *
 * Nothing is scanned at construction. The first collector of [observeGames]
 * triggers a scan, and package install / uninstall broadcasts rescan while
 * someone is observing.
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
    private val scanMutex = Mutex()
    private var scannedOnce = false

    override fun observeGames(): Flow<List<GameEntry>> =
        combine(detected, store.manualGames, store.lastPlayed, packageChanges()) { auto, manual, played, _ ->
            GameMerge.merge(auto, manual, played, ::isInstalled)
        }.onStart {
            if (!scannedOnce) refreshInstalledGames()
        }.flowOn(Dispatchers.IO)

    override suspend fun refreshInstalledGames() {
        scanMutex.withLock {
            val found = withContext(Dispatchers.IO) { scanInstalledGames() }
            detected.update { found }
            scannedOnce = true
        }
    }

    override suspend fun addGameManually(packageName: String, displayName: String) {
        val pkg = packageName.trim()
        if (pkg.isEmpty() || !PACKAGE_NAME.matches(pkg)) return
        val name = displayName.trim().ifEmpty { withContext(Dispatchers.IO) { labelFor(pkg) } ?: pkg }
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
        val intent = try {
            packageManager.getLaunchIntentForPackage(packageName)
        } catch (_: Throwable) {
            null
        } ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** Emits once immediately and again on every package add / remove / replace. */
    private fun packageChanges(): Flow<Long> = callbackFlow {
        trySend(0L)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(System.currentTimeMillis())
                launch {
                    try {
                        refreshInstalledGames()
                    } catch (_: Throwable) {
                        // Keep the previous list.
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        val registered = try {
            ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            true
        } catch (_: Throwable) {
            false
        }
        awaitClose {
            if (registered) {
                try {
                    appContext.unregisterReceiver(receiver)
                } catch (_: Throwable) {
                    // Already gone.
                }
            }
        }
    }

    private fun scanInstalledGames(): List<GameEntry> {
        val installed = try {
            packageManager.getInstalledApplications(0)
        } catch (_: Throwable) {
            emptyList()
        }
        return installed.mapNotNull { info ->
            if (info.packageName == appContext.packageName) return@mapNotNull null
            if (!info.enabled) return@mapNotNull null
            @Suppress("DEPRECATION")
            val legacyGame = (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            val isGame = info.category == ApplicationInfo.CATEGORY_GAME || legacyGame
            if (!isGame) return@mapNotNull null
            // Only launchable games are useful in the library.
            val launchable = try {
                packageManager.getLaunchIntentForPackage(info.packageName) != null
            } catch (_: Throwable) {
                false
            }
            if (!launchable) return@mapNotNull null
            GameEntry(
                packageName = info.packageName,
                displayName = labelFor(info) ?: info.packageName,
                isManual = false,
                isSystem = PackageSafety.isSystemPackage(info),
            )
        }
    }

    private fun isInstalled(packageName: String): Boolean = try {
        packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (_: Throwable) {
        false
    }

    private fun labelFor(packageName: String): String? {
        return try {
            labelFor(packageManager.getApplicationInfo(packageName, 0))
        } catch (_: Throwable) {
            null
        }
    }

    private fun labelFor(info: ApplicationInfo): String? =
        try {
            packageManager.getApplicationLabel(info)?.toString()
        } catch (_: Throwable) {
            null
        }

    private companion object {
        val PACKAGE_NAME = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""")
    }
}
