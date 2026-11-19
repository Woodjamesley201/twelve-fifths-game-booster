package com.twelvefifths.booster.hud

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.twelvefifths.booster.data.HudPreferences
import com.twelvefifths.booster.metrics.MetricSupport
import com.twelvefifths.booster.metrics.MetricsSnapshot
import kotlin.math.roundToInt

/**
 * WindowManager plumbing for the always-on-top HUD.
 * Uses a lightweight platform view (no Compose, no RenderEffect, no blur)
 * so the first overlay frame stays cheap on Mali / Android 16.
 *
 * Visual styling is intentionally minimal; Ui isaac owns richer HUD visuals
 * in [com.twelvefifths.booster.ui.hud.HudOverlay] (in-app preview).
 *
 * Owner: Script Nathan.
 */
class HudWindowController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val windowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var root: LinearLayout? = null
    private var titleView: TextView? = null
    private var linesView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var preferences: HudPreferences = HudPreferences()
    private var snapshot: MetricsSnapshot = MetricsSnapshot.empty()
    private var dragStartX = 0
    private var dragStartY = 0
    private var dragTouchStartX = 0f
    private var dragTouchStartY = 0f

    val isShowing: Boolean
        get() = root != null

    fun show(prefs: HudPreferences = preferences) {
        if (!Settings.canDrawOverlays(appContext)) return
        preferences = prefs
        if (root != null) {
            applyStyle()
            render()
            return
        }
        val density = appContext.resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            (prefs.widthDp * density).roundToInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = prefs.posX
        params.y = prefs.posY
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            params.fitInsetsTypes = 0
        }

        val container = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (10 * density).roundToInt()
            setPadding(pad, pad, pad, pad)
            isClickable = true
            setOnTouchListener(dragListener)
        }
        val title = TextView(appContext).apply {
            text = "TWELVE FIFTHS"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            letterSpacing = 0.12f
            setTextColor(Color.WHITE)
        }
        val lines = TextView(appContext).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.WHITE)
            setLineSpacing(0f, 1.15f)
        }
        container.addView(title)
        container.addView(lines)

        root = container
        titleView = title
        linesView = lines
        layoutParams = params
        applyStyle()
        render()
        windowManager.addView(container, params)
    }

    fun update(snapshot: MetricsSnapshot, prefs: HudPreferences) {
        this.snapshot = snapshot
        this.preferences = prefs
        if (root == null) {
            show(prefs)
            return
        }
        applyStyle()
        val params = layoutParams
        val view = root
        if (params != null && view != null) {
            val density = appContext.resources.displayMetrics.density
            params.width = (prefs.widthDp * density).roundToInt()
            try {
                windowManager.updateViewLayout(view, params)
            } catch (_: Throwable) {
                // View may already have been removed.
            }
        }
        render()
    }

    fun dismiss() {
        val view = root ?: return
        try {
            windowManager.removeView(view)
        } catch (_: Throwable) {
            // Already detached.
        }
        root = null
        titleView = null
        linesView = null
        layoutParams = null
    }

    fun currentPosition(): Pair<Int, Int> {
        val params = layoutParams
        return if (params != null) params.x to params.y else preferences.posX to preferences.posY
    }

    private fun applyStyle() {
        val view = root ?: return
        val alpha = (preferences.opacity.coerceIn(0.35f, 1f) * 255).roundToInt()
        val fill = Color.argb(alpha, 11, 13, 18)
        val accent = preferences.accentArgb
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f * appContext.resources.displayMetrics.density
            setColor(fill)
            setStroke((1.2f * appContext.resources.displayMetrics.density).roundToInt(), accent)
        }
        view.background = bg
        titleView?.setTextColor(accent)
    }

    private fun render() {
        val prefs = preferences
        val snap = snapshot
        val rows = buildList {
            if (prefs.showFps) add(line("FPS", snap.fps?.let { "%.0f".format(it) }, snap.fpsSupport))
            if (prefs.showCpuTemp) add(line("CPU °C", snap.cpuTempC?.let { "%.0f".format(it) }, snap.cpuTempSupport))
            if (prefs.showGpuTemp) add(line("GPU °C", snap.gpuTempC?.let { "%.0f".format(it) }, snap.gpuTempSupport))
            if (prefs.showRam) add(ramLine(snap))
            if (prefs.showCpuUsage) add(line("CPU %", snap.cpuUsage?.let { "%.0f".format(it) }, snap.cpuUsageSupport))
            if (prefs.showGpuUsage) add(line("GPU %", snap.gpuUsage?.let { "%.0f".format(it) }, snap.gpuUsageSupport))
            if (prefs.showPing) add(line("PING", snap.pingMs?.let { "${it} ms" }, snap.pingSupport))
            if (prefs.showVram) add(line("VRAM", snap.vramUsedBytes?.let { formatBytes(it) }, snap.vramSupport))
        }
        val visible = if (prefs.compact) rows.take(4) else rows
        linesView?.text = visible.joinToString("\n")
    }

    private fun ramLine(snap: MetricsSnapshot): String {
        val value = when {
            snap.ramSupport != MetricSupport.SUPPORTED -> supportLabel(snap.ramSupport)
            snap.ramUsedBytes != null && snap.ramTotalBytes != null ->
                "${formatBytes(snap.ramUsedBytes)} / ${formatBytes(snap.ramTotalBytes)}" +
                    (snap.ramPercent?.let { "  (%.0f%%)".format(it) } ?: "")
            else -> "—"
        }
        return "RAM    $value"
    }

    private fun line(label: String, value: String?, support: MetricSupport): String {
        val shown = when {
            support != MetricSupport.SUPPORTED -> supportLabel(support)
            value == null -> "—"
            else -> value
        }
        return label.padEnd(8) + shown
    }

    private fun supportLabel(support: MetricSupport): String = when (support) {
        MetricSupport.SUPPORTED -> "—"
        MetricSupport.NEEDS_PERMISSION -> "needs permission"
        MetricSupport.UNSUPPORTED -> "unsupported"
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "%.0f MB".format(mb)
    }

    private val dragListener = View.OnTouchListener { _, event ->
        val params = layoutParams ?: return@OnTouchListener false
        val view = root ?: return@OnTouchListener false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = params.x
                dragStartY = params.y
                dragTouchStartX = event.rawX
                dragTouchStartY = event.rawY
                true
            }
            MotionEvent.ACTION_MOVE -> {
                params.x = dragStartX + (event.rawX - dragTouchStartX).roundToInt()
                params.y = dragStartY + (event.rawY - dragTouchStartY).roundToInt()
                try {
                    windowManager.updateViewLayout(view, params)
                } catch (_: Throwable) {
                    // View gone.
                }
                true
            }
            else -> false
        }
    }
}
