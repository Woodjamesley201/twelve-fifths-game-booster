package com.twelvefifths.booster.hud

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.twelvefifths.booster.BoosterApplication
import com.twelvefifths.booster.MainActivity
import com.twelvefifths.booster.R
import com.twelvefifths.booster.data.HudPreferences
import com.twelvefifths.booster.metrics.MetricsSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** Outcome of [HudService.start]. */
enum class HudStartResult {
    STARTED,
    ALREADY_RUNNING,
    /** "Display over other apps" is not granted; open [com.twelvefifths.booster.optimize.PermissionChecker] OVERLAY settings. */
    NEEDS_OVERLAY_PERMISSION,
    /** Android refused a background FGS start; retry from a visible activity / user action. */
    NOT_ALLOWED_FROM_BACKGROUND,
    FAILED,
}

/**
 * Foreground service (`specialUse`) that hosts the TYPE_APPLICATION_OVERLAY HUD.
 *
 * Toggles (Android has no global keyboard shortcuts for third-party apps):
 * - in-app: [start] / [stop] / [toggle];
 * - notification actions: Hide/Show overlay and Stop;
 * - Quick Settings tile: [HudTileService].
 *
 * Missing permissions are handled gracefully: without SYSTEM_ALERT_WINDOW the
 * service never starts (and stops itself if the grant is revoked mid-run);
 * without POST_NOTIFICATIONS it still runs, but Android hides its notification,
 * so the tile and in-app toggle are the controls.
 *
 * Window plumbing lives here + [HudWindowController]; visuals are Ui isaac's.
 *
 * Owner: Script Nathan (service / window); Ui isaac (visuals).
 */
class HudService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowController: HudWindowController? = null
    private var metricsJob: Job? = null
    private var prefsJob: Job? = null
    private var latestSnapshot: MetricsSnapshot = MetricsSnapshot.empty()
    private var latestPrefs: HudPreferences? = null
    private var overlayHidden = false
    private var foregroundStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        // Promote first: startForegroundService requires startForeground within a few seconds.
        foregroundStarted = startInForeground()
        if (!foregroundStarted) {
            stopSelf()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowController = HudWindowController(
            context = this,
            onGeometryChanged = ::persistGeometry,
            onCompactToggled = ::persistCompact,
        )
        mutableRunning.value = true
        requestTileUpdate(this)
        startCollecting()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!foregroundStarted) return START_NOT_STICKY
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_OVERLAY -> setOverlayHidden(!overlayHidden)
            ACTION_SHOW_OVERLAY -> setOverlayHidden(false)
            ACTION_HIDE_OVERLAY -> setOverlayHidden(true)
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        windowController?.onConfigurationChanged()
    }

    override fun onDestroy() {
        metricsJob?.cancel()
        prefsJob?.cancel()
        windowController?.let { persistGeometryDetached(it.currentGeometry()) }
        windowController?.dismiss()
        windowController = null
        mutableRunning.value = false
        mutableOverlayVisible.value = false
        requestTileUpdate(this)
        scope.cancel()
        super.onDestroy()
    }

    private fun startCollecting() {
        val app = application as? BoosterApplication ?: return
        prefsJob = scope.launch {
            app.store.hudPreferences
                .catch { emit(HudPreferences()) }
                .collect { prefs ->
                    latestPrefs = prefs
                    pushToWindow()
                }
        }
        metricsJob = scope.launch {
            app.metricsSource.snapshots()
                .catch { emit(MetricsSnapshot.empty()) }
                .collect { snap ->
                    latestSnapshot = snap
                    pushToWindow()
                }
        }
    }

    private fun pushToWindow() {
        val prefs = latestPrefs ?: return // wait for real prefs so position is right on first show
        val controller = windowController ?: return
        if (overlayHidden) return
        if (!Settings.canDrawOverlays(this)) {
            controller.dismiss()
            stopSelf()
            return
        }
        when (controller.update(latestSnapshot, prefs)) {
            HudShowResult.SHOWN, HudShowResult.ALREADY_SHOWN -> mutableOverlayVisible.value = true
            HudShowResult.NO_OVERLAY_PERMISSION -> stopSelf()
            HudShowResult.FAILED -> mutableOverlayVisible.value = false
        }
    }

    private fun setOverlayHidden(hidden: Boolean) {
        overlayHidden = hidden
        val controller = windowController
        if (hidden) {
            controller?.let { persistGeometry(it.currentGeometry()) }
            controller?.dismiss()
            mutableOverlayVisible.value = false
        } else {
            pushToWindow()
        }
        refreshNotification()
    }

    private fun persistGeometry(geometry: HudGeometry) {
        latestPrefs = latestPrefs?.copy(posX = geometry.x, posY = geometry.y, widthDp = geometry.widthDp)
        val app = application as? BoosterApplication ?: return
        scope.launch(Dispatchers.IO) {
            try {
                app.store.updateHudPreferences { it.copy(posX = geometry.x, posY = geometry.y, widthDp = geometry.widthDp) }
            } catch (_: Throwable) {
                // Best-effort.
            }
        }
    }

    /** onDestroy cancels [scope]; use a short-lived scope so the final position still lands. */
    private fun persistGeometryDetached(geometry: HudGeometry) {
        val app = application as? BoosterApplication ?: return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                app.store.updateHudPreferences { it.copy(posX = geometry.x, posY = geometry.y, widthDp = geometry.widthDp) }
            } catch (_: Throwable) {
                // Best-effort.
            }
        }
    }

    private fun persistCompact(compact: Boolean) {
        latestPrefs = latestPrefs?.copy(compact = compact)
        val app = application as? BoosterApplication ?: return
        scope.launch(Dispatchers.IO) {
            try {
                app.store.updateHudPreferences { it.copy(compact = compact) }
            } catch (_: Throwable) {
                // Best-effort.
            }
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val toggle = PendingIntent.getService(
            this,
            2,
            Intent(this, HudService::class.java).setAction(ACTION_TOGGLE_OVERLAY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, HudService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val toggleLabel = getString(if (overlayHidden) R.string.hud_notification_show else R.string.hud_notification_hide)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_hud)
            .setContentTitle(getString(R.string.hud_notification_title))
            .setContentText(
                getString(if (overlayHidden) R.string.hud_notification_text_hidden else R.string.hud_notification_text),
            )
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, toggleLabel, toggle)
            .addAction(0, getString(R.string.hud_notification_stop), stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun startInForeground(): Boolean {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
            true
        } catch (_: Throwable) {
            // ForegroundServiceStartNotAllowedException, SecurityException (missing FGS permission), etc.
            false
        }
    }

    private fun refreshNotification() {
        try {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            manager.notify(NOTIFICATION_ID, buildNotification())
        } catch (_: Throwable) {
            // POST_NOTIFICATIONS denied: nothing to update.
        }
    }

    private fun ensureChannel() {
        try {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.hud_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            channel.description = getString(R.string.hud_notification_text)
            channel.setShowBadge(false)
            manager.createNotificationChannel(channel)
        } catch (_: Throwable) {
            // Channel creation failure is not fatal.
        }
    }

    companion object {
        const val ACTION_STOP = "com.twelvefifths.booster.hud.STOP"
        const val ACTION_TOGGLE_OVERLAY = "com.twelvefifths.booster.hud.TOGGLE_OVERLAY"
        const val ACTION_SHOW_OVERLAY = "com.twelvefifths.booster.hud.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.twelvefifths.booster.hud.HIDE_OVERLAY"
        const val CHANNEL_ID = "hud"
        const val NOTIFICATION_ID = 12_500

        private val mutableRunning = MutableStateFlow(false)
        private val mutableOverlayVisible = MutableStateFlow(false)

        /** True while the service is alive. Use this instead of local UI state. */
        val running: StateFlow<Boolean> = mutableRunning.asStateFlow()

        /** True while the overlay window is on screen (false when hidden from the notification). */
        val overlayVisible: StateFlow<Boolean> = mutableOverlayVisible.asStateFlow()

        val isRunning: Boolean get() = mutableRunning.value

        fun start(context: Context): HudStartResult {
            if (!Settings.canDrawOverlays(context)) return HudStartResult.NEEDS_OVERLAY_PERMISSION
            if (mutableRunning.value) {
                // Make sure a hidden overlay comes back.
                return try {
                    context.startService(Intent(context, HudService::class.java).setAction(ACTION_SHOW_OVERLAY))
                    HudStartResult.ALREADY_RUNNING
                } catch (_: Throwable) {
                    HudStartResult.ALREADY_RUNNING
                }
            }
            return try {
                ContextCompat.startForegroundService(context, Intent(context, HudService::class.java))
                HudStartResult.STARTED
            } catch (e: Throwable) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                    HudStartResult.NOT_ALLOWED_FROM_BACKGROUND
                } else if (e is IllegalStateException) {
                    HudStartResult.NOT_ALLOWED_FROM_BACKGROUND
                } else {
                    HudStartResult.FAILED
                }
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, HudService::class.java))
            } catch (_: Throwable) {
                // Not running.
            }
        }

        /** Starts the HUD if stopped, stops it if running. */
        fun toggle(context: Context): HudStartResult? =
            if (mutableRunning.value) {
                stop(context)
                null
            } else {
                start(context)
            }

        fun requestTileUpdate(context: Context) {
            try {
                TileService.requestListeningState(context, ComponentName(context, HudTileService::class.java))
            } catch (_: Throwable) {
                // Tile not added / not supported.
            }
        }
    }
}
