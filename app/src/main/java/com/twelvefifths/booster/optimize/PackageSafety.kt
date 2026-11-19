package com.twelvefifths.booster.optimize

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.WallpaperManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager

/** Facts about one package that decide whether it may be closed. */
data class PackageFacts(
    val packageName: String,
    val installed: Boolean,
    val isSystem: Boolean,
    val isPersistent: Boolean,
)

/**
 * Pure close-eligibility rule. A package may be closed only when it is
 * installed, not ours, not a system / updated-system / persistent app, and not
 * in the protected set (launchers, keyboards, accessibility services, device
 * admins, live wallpaper, default dialer / SMS, plus [ALWAYS_PROTECTED]).
 *
 * Owner: Script Nathan.
 */
object CloseRules {
    val ALWAYS_PROTECTED: Set<String> = setOf(
        "android",
        "com.android.systemui",
        "com.android.phone",
        "com.android.settings",
        "com.android.vending",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.samsung.android.game.gos",
        "com.samsung.android.game.gametools",
        "com.samsung.android.game.gamehome",
        "com.sec.android.app.launcher",
    )

    fun isClosable(
        packageName: String,
        facts: PackageFacts?,
        selfPackage: String,
        protectedPackages: Set<String>,
    ): Boolean {
        if (packageName.isBlank()) return false
        if (packageName == selfPackage) return false
        if (packageName in ALWAYS_PROTECTED || packageName in protectedPackages) return false
        // Unknown / unresolvable packages are never treated as safe.
        val f = facts ?: return false
        if (!f.installed || f.isSystem || f.isPersistent) return false
        return true
    }
}

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
        } catch (_: Throwable) {
            // If we cannot resolve it, do not treat it as safe to kill.
            true
        }
    }

    fun isOurPackage(context: Context, packageName: String): Boolean =
        packageName == context.packageName

    fun facts(packageManager: PackageManager, packageName: String): PackageFacts? = try {
        val info = packageManager.getApplicationInfo(packageName, 0)
        PackageFacts(
            packageName = packageName,
            installed = true,
            isSystem = isSystemPackage(info),
            isPersistent = (info.flags and ApplicationInfo.FLAG_PERSISTENT) != 0,
        )
    } catch (_: Throwable) {
        null
    }

    /**
     * Packages that must never be closed on this device right now. Each lookup
     * is best-effort; a failure only means fewer extra protections, never fewer
     * of the [CloseRules] base checks. Does binder IPC — call off the main thread.
     */
    fun protectedPackages(context: Context): Set<String> {
        val out = HashSet<String>()
        val pm = context.packageManager
        tryAdd(out) {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            pm.queryIntentActivities(home, 0).mapNotNull { it.activityInfo?.packageName }
        }
        tryAdd(out) {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.enabledInputMethodList?.map { it.packageName }.orEmpty()
        }
        tryAdd(out) {
            val raw = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            listOfNotNull(raw?.substringBefore('/')?.takeIf { it.isNotBlank() })
        }
        tryAdd(out) {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                ?.mapNotNull { it.resolveInfo?.serviceInfo?.packageName }
                .orEmpty()
        }
        tryAdd(out) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            dpm?.activeAdmins?.map { it.packageName }.orEmpty()
        }
        tryAdd(out) {
            listOfNotNull(WallpaperManager.getInstance(context).wallpaperInfo?.packageName)
        }
        tryAdd(out) {
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            listOfNotNull(telecom?.defaultDialerPackage)
        }
        tryAdd(out) { listOfNotNull(Telephony.Sms.getDefaultSmsPackage(context)) }
        return out
    }

    fun canClose(context: Context, packageName: String): Boolean =
        canClose(context, packageName, protectedPackages(context))

    fun canClose(context: Context, packageName: String, protectedPackages: Set<String>): Boolean =
        CloseRules.isClosable(
            packageName = packageName,
            facts = facts(context.packageManager, packageName),
            selfPackage = context.packageName,
            protectedPackages = protectedPackages,
        )

    private inline fun tryAdd(out: MutableSet<String>, block: () -> List<String>) {
        try {
            out.addAll(block())
        } catch (_: Throwable) {
            // Best-effort.
        }
    }
}
