package com.twelvefifths.booster.optimize

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Every access the engine can use. */
enum class BoosterPermission {
    /** SYSTEM_ALERT_WINDOW — "Display over other apps". Needed for the live HUD. */
    OVERLAY,
    /** PACKAGE_USAGE_STATS — "Usage access". Needed for foreground-game detection and auto-restore. */
    USAGE_ACCESS,
    /** Notification-policy access. Needed to switch Do Not Disturb. */
    DND_POLICY,
    /** POST_NOTIFICATIONS (runtime on Android 13+). Needed for the HUD notification's Hide / Stop actions. */
    NOTIFICATIONS,
}

/**
 * State of one permission plus how to ask for it.
 *
 * If [runtimePermission] is non-null, request it with an ActivityResult
 * `RequestPermission` launcher first; fall back to [settingsIntent] when the
 * user has permanently denied it. Otherwise start [settingsIntent].
 */
data class PermissionState(
    val permission: BoosterPermission,
    val granted: Boolean,
    val title: String,
    val reason: String,
    val settingsIntent: Intent,
    val runtimePermission: String? = null,
    /** False when the feature is optional (the app still works without it). */
    val required: Boolean = false,
)

/**
 * Engine-side permission checks the UI can query (e.g. on every ON_RESUME).
 * All checks are cheap, local, and never throw.
 *
 * Owner: Script Nathan.
 */
class PermissionChecker(context: Context) {

    private val appContext = context.applicationContext

    fun all(): List<PermissionState> = BoosterPermission.entries.map { state(it) }

    fun missing(): List<PermissionState> = all().filter { !it.granted }

    fun isGranted(permission: BoosterPermission): Boolean = when (permission) {
        BoosterPermission.OVERLAY -> canDrawOverlays()
        BoosterPermission.USAGE_ACCESS -> hasUsageAccess()
        BoosterPermission.DND_POLICY -> hasDndAccess()
        BoosterPermission.NOTIFICATIONS -> hasNotificationPermission()
    }

    fun state(permission: BoosterPermission): PermissionState {
        val pkgUri = Uri.parse("package:${appContext.packageName}")
        return when (permission) {
            BoosterPermission.OVERLAY -> PermissionState(
                permission = permission,
                granted = canDrawOverlays(),
                title = "Display over other apps",
                reason = "Shows the live HUD on top of your game.",
                settingsIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri),
                required = true,
            )
            BoosterPermission.USAGE_ACCESS -> PermissionState(
                permission = permission,
                granted = hasUsageAccess(),
                title = "Usage access",
                reason = "Detects which game is in front and restores settings when you leave it.",
                settingsIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            )
            BoosterPermission.DND_POLICY -> PermissionState(
                permission = permission,
                granted = hasDndAccess(),
                title = "Do Not Disturb access",
                reason = "Silences notifications during a game session and restores them after.",
                settingsIntent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
            )
            BoosterPermission.NOTIFICATIONS -> PermissionState(
                permission = permission,
                granted = hasNotificationPermission(),
                title = "Notifications",
                reason = "Shows the HUD notification with Hide / Stop buttons.",
                settingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName),
                runtimePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.POST_NOTIFICATIONS
                } else {
                    null
                },
            )
        }
    }

    /** Opens the settings screen for [permission]; returns false if nothing could open. */
    fun openSettings(context: Context, permission: BoosterPermission): Boolean =
        SettingsShortcuts.open(context, state(permission).settingsIntent)

    fun canDrawOverlays(): Boolean = try {
        Settings.canDrawOverlays(appContext)
    } catch (_: Throwable) {
        false
    }

    fun hasUsageAccess(): Boolean {
        val ops = appContext.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        return try {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), appContext.packageName)
            } else {
                @Suppress("DEPRECATION")
                ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), appContext.packageName)
            }
            if (mode == AppOpsManager.MODE_DEFAULT) {
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.PACKAGE_USAGE_STATS) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                mode == AppOpsManager.MODE_ALLOWED
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun hasDndAccess(): Boolean = try {
        (appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
            ?.isNotificationPolicyAccessGranted == true
    } catch (_: Throwable) {
        false
    }

    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
}
