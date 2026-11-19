package com.twelvefifths.booster.optimize

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/**
 * Guards that prevent the optimizer from touching system or essential packages.
 *
 * Owner: Script Nathan.
 */
object PackageSafety {

    fun isSystemPackage(info: ApplicationInfo): Boolean {
        val flags = info.flags
        return (flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }

    fun isSystemPackage(packageManager: PackageManager, packageName: String): Boolean {
        return try {
            isSystemPackage(packageManager.getApplicationInfo(packageName, 0))
        } catch (_: PackageManager.NameNotFoundException) {
            // If we cannot resolve it, do not treat it as safe to kill.
            true
        }
    }

    fun isOurPackage(context: Context, packageName: String): Boolean =
        packageName == context.packageName

    fun canClose(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (isOurPackage(context, packageName)) return false
        if (isSystemPackage(context.packageManager, packageName)) return false
        return true
    }
}
