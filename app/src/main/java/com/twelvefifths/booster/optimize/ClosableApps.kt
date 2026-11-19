package com.twelvefifths.booster.optimize

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Launchable, non-system, non-essential packages the user may opt into closing.
 * Pairs are (packageName, label), sorted by label.
 *
 * Runs on [Dispatchers.IO] (package-manager IPC); callers just `suspend`.
 *
 * Note: on Android 14+ the platform ignores killBackgroundProcesses for other
 * apps (see [OptimizerCapabilities]). The list is still useful for the profile
 * and for "Open app info" shortcuts.
 *
 * Owner: Script Nathan.
 */
object ClosableApps {
    suspend fun list(context: Context): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val pm = appContext.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = try {
            pm.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY)
        } catch (_: Throwable) {
            emptyList()
        }
        val protectedSet = PackageSafety.protectedPackages(appContext)
        resolved.mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            pkg to info
        }.distinctBy { it.first }.mapNotNull { (pkg, info) ->
            if (!PackageSafety.canClose(appContext, pkg, protectedSet)) return@mapNotNull null
            val label = try {
                info.loadLabel(pm)?.toString() ?: pkg
            } catch (_: Throwable) {
                pkg
            }
            pkg to label
        }.sortedBy { it.second.lowercase() }
    }
}
