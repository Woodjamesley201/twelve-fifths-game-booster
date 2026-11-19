package com.twelvefifths.booster.ui.hud

import android.app.ActivityManager
import android.content.Context
import com.twelvefifths.booster.hud.HudService

/**
 * Best-effort check whether our own HudService is running, so the HUD toggle
 * is correct after the activity is recreated. getRunningServices is
 * deprecated for *other* apps' services but still reports the caller's own.
 * Replace with a HudService state flow if Nathan adds one.
 *
 * Owner: Ui isaac.
 */
object HudRunningState {
    @Suppress("DEPRECATION")
    fun isRunning(context: Context): Boolean = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.getRunningServices(Int.MAX_VALUE)?.any {
            it.service.className == HudService::class.java.name && it.service.packageName == context.packageName
        } == true
    } catch (_: Throwable) {
        false
    }
}
