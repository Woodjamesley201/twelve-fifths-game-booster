package com.twelvefifths.booster.hud

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.twelvefifths.booster.BoosterApplication
import com.twelvefifths.booster.MainActivity
import com.twelvefifths.booster.R
import com.twelvefifths.booster.data.BoosterStore
import com.twelvefifths.booster.data.HudPreferences
import com.twelvefifths.booster.metrics.MetricsSnapshot
import com.twelvefifths.booster.metrics.MetricsSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service that hosts the TYPE_APPLICATION_OVERLAY HUD.
 * Window plumbing lives here; in-app HUD visuals live under ui/hud.
 *
 * Owner: Script Nathan (service / window); Ui isaac (visuals).
 */
class HudService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowController: HudWindowController? = null
    private var collectJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startInForeground()
        windowController = HudWindowController(this)
        startCollecting()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (windowController?.isShowing != true && Settings.canDrawOverlays(this)) {
            windowController?.show()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        collectJob?.cancel()
        collectJob = null
        persistPosition()
        windowController?.dismiss()
        windowController = null
        scope.cancel()
        super.onDestroy()
    }

    private fun startCollecting() {
        val app = application as? BoosterApplication ?: return
        val metrics: MetricsSource = app.metricsSource
        val store: BoosterStore = app.store
        collectJob?.cancel()
        collectJob = scope.launch {
            combine(metrics.snapshots(), store.hudPreferences) { snap: MetricsSnapshot, prefs: HudPreferences ->
                snap to prefs
            }.collect { (snap, prefs) ->
                if (Settings.canDrawOverlays(this@HudService)) {
                    windowController?.update(snap, prefs)
                }
            }
        }
    }

    private fun persistPosition() {
        val app = application as? BoosterApplication ?: return
        val controller = windowController ?: return
        val (x, y) = controller.currentPosition()
        scope.launch(Dispatchers.IO) {
            try {
                val current = app.store.hudPreferences.first()
                app.store.setHudPreferences(current.copy(posX = x, posY = y))
            } catch (_: Throwable) {
                // Service is shutting down; position save is best-effort.
            }
        }
    }

    private fun startInForeground() {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, HudService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_hud)
            .setContentTitle(getString(R.string.hud_notification_title))
            .setContentText(getString(R.string.hud_notification_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(0, getString(R.string.hud_notification_stop), stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.hud_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.description = getString(R.string.hud_notification_text)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_STOP = "com.twelvefifths.booster.hud.STOP"
        const val CHANNEL_ID = "hud"
        const val NOTIFICATION_ID = 12_500

        fun start(context: Context) {
            val intent = Intent(context, HudService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HudService::class.java))
        }
    }
}
