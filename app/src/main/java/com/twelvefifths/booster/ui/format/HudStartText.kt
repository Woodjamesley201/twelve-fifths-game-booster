package com.twelvefifths.booster.ui.format

import com.twelvefifths.booster.hud.HudStartResult

/**
 * User-facing text for [HudStartResult]. Null means nothing to say.
 *
 * Owner: Ui isaac.
 */
object HudStartText {
    fun message(result: HudStartResult?): String? = when (result) {
        null, HudStartResult.STARTED -> null
        HudStartResult.ALREADY_RUNNING -> "HUD is already running."
        HudStartResult.NEEDS_OVERLAY_PERMISSION -> "Allow \"Display over other apps\" to show the HUD."
        HudStartResult.NOT_ALLOWED_FROM_BACKGROUND -> "Android blocked starting the HUD from the background. Open the app and try again."
        HudStartResult.FAILED -> "The HUD couldn't start. Try again."
    }
}
