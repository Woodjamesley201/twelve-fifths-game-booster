package com.twelvefifths.booster.hud

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.twelvefifths.booster.R

/**
 * Quick Settings tile that toggles the HUD — the closest Android gets to a
 * system-wide shortcut for a third-party app.
 *
 * A tile tap is a user interaction, so starting the foreground service is
 * normally allowed; if Android refuses anyway, we fall back to the invisible
 * [HudToggleActivity] trampoline. Missing overlay permission opens its
 * Settings screen instead.
 *
 * Owner: Script Nathan.
 */
class HudTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        if (HudService.isRunning) {
            HudService.stop(this)
            setTileState(false)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            launch(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        when (HudService.start(this)) {
            HudStartResult.STARTED, HudStartResult.ALREADY_RUNNING -> setTileState(true)
            HudStartResult.NEEDS_OVERLAY_PERMISSION -> Unit
            HudStartResult.NOT_ALLOWED_FROM_BACKGROUND, HudStartResult.FAILED ->
                launch(Intent(this, HudToggleActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun refreshTile() = setTileState(HudService.isRunning)

    private fun setTileState(active: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.hud_tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (active) R.string.hud_tile_on else R.string.hud_tile_off)
        }
        try {
            tile.updateTile()
        } catch (_: Throwable) {
            // Tile went away.
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launch(intent: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pending = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                startActivityAndCollapse(pending)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (_: Throwable) {
            // Nothing else to try from a tile.
        }
    }
}
