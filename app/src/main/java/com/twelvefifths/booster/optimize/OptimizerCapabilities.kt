package com.twelvefifths.booster.optimize

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * What the optimizer can really do on this device, and the Settings / app
 * shortcuts it can offer instead of pretending to flip hidden switches.
 *
 * Owner: Script Nathan.
 */
object OptimizerCapabilities {

    /**
     * Android 14 (API 34) made killBackgroundProcesses a no-op for other apps,
     * for every app regardless of targetSdk. Rahim's Tab A9 runs Android 16.
     */
    val canKillOtherApps: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    /** Known OEM game hubs that a user can open to change game mode / performance. */
    val GAME_HUB_PACKAGES: List<String> = listOf(
        "com.samsung.android.game.gamehome", // Samsung Gaming Hub (Game Launcher)
        "com.samsung.android.game.gametools", // Samsung Game Booster
    )
}

/** A Settings or app screen the UI can offer as a button. */
data class SettingsShortcut(
    val id: Id,
    val title: String,
    val description: String,
    val intent: Intent,
) {
    enum class Id { BATTERY_SAVER, GAME_HUB, GAME_APP_INFO, APP_INFO, OVERLAY, USAGE_ACCESS, DND_ACCESS, NOTIFICATIONS }
}

object SettingsShortcuts {

    fun batterySaver(): SettingsShortcut = SettingsShortcut(
        id = SettingsShortcut.Id.BATTERY_SAVER,
        title = "Battery saver settings",
        description = "Opens Android's battery saver screen. This app does not switch it for you.",
        intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
    )

    /**
     * There is no cross-OEM "game mode" intent. On Samsung, the Gaming Hub /
     * Game Booster owns performance modes, so open it if installed; otherwise
     * open the game's App info screen (where Android 13+ Game settings live on
     * some devices).
     */
    fun gameMode(context: Context, gamePackage: String?): SettingsShortcut? {
        val pm = context.packageManager
        for (hub in OptimizerCapabilities.GAME_HUB_PACKAGES) {
            val launch = try {
                pm.getLaunchIntentForPackage(hub)
            } catch (_: Throwable) {
                null
            } ?: continue
            return SettingsShortcut(
                id = SettingsShortcut.Id.GAME_HUB,
                title = "Open Gaming Hub",
                description = "Samsung's game performance settings live here.",
                intent = launch,
            )
        }
        if (gamePackage.isNullOrBlank()) return null
        return appInfo(gamePackage, SettingsShortcut.Id.GAME_APP_INFO, "Game app info")
    }

    /** App info screen — the user can Force stop from there (we cannot on Android 14+). */
    fun appInfo(
        packageName: String,
        id: SettingsShortcut.Id = SettingsShortcut.Id.APP_INFO,
        title: String = "App info",
    ): SettingsShortcut = SettingsShortcut(
        id = id,
        title = title,
        description = "Android's App info for $packageName.",
        intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
    )

    /** Starts [shortcut]; falls back to the main Settings screen. Returns false if nothing opened. */
    fun open(context: Context, shortcut: SettingsShortcut): Boolean = open(context, shortcut.intent)

    fun open(context: Context, intent: Intent): Boolean {
        val launch = Intent(intent)
        if (context !is android.app.Activity) launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(launch)
            true
        } catch (_: Throwable) {
            try {
                val fallback = Intent(Settings.ACTION_SETTINGS)
                if (context !is android.app.Activity) fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }
}
