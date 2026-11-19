package com.twelvefifths.booster.optimize

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Launchable, non-system packages the user may opt into closing.
 *
 * Owner: Script Nathan.
 */
object ClosableApps {
    fun list(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = try {
            pm.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY)
        } catch (_: Throwable) {
            emptyList()
        }
        return resolved.mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            if (!PackageSafety.canClose(context, pkg)) return@mapNotNull null
            val label = try {
                info.loadLabel(pm)?.toString() ?: pkg
            } catch (_: Throwable) {
                pkg
            }
            pkg to label
        }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
    }
}
