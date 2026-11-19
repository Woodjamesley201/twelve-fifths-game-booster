package com.twelvefifths.booster.hud

import android.app.Activity
import android.os.Bundle

/**
 * Invisible trampoline used only when Android refuses to start the HUD
 * service from the Quick Settings tile. A visible activity may always start a
 * foreground service, so this starts / stops the HUD and finishes immediately.
 *
 * Owner: Script Nathan.
 */
class HudToggleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (HudService.isRunning) HudService.stop(this) else HudService.start(this)
        } catch (_: Throwable) {
            // Nothing more we can do here.
        }
        finish()
        overridePendingTransitionCompat()
    }

    @Suppress("DEPRECATION")
    private fun overridePendingTransitionCompat() {
        try {
            overridePendingTransition(0, 0)
        } catch (_: Throwable) {
            // Ignore.
        }
    }
}
