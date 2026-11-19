package com.twelvefifths.booster.hud

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.twelvefifths.booster.data.HudPreferences
import com.twelvefifths.booster.metrics.MetricsSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

/** Geometry the user set by dragging / resizing. Persisted by [HudService]. */
data class HudGeometry(val x: Int, val y: Int, val widthDp: Int)

/**
 * Where the overlay window is and how big it really is, in pixels, in the same
 * coordinate space as [HudPreferences.posX] / [HudPreferences.posY] (top-left
 * gravity, full display). [screenWidthPx] / [screenHeightPx] are the bounds the
 * controller clamps to, so a bottom-right preset is
 * `posX = screenWidthPx - widthPx - margin`, `posY = screenHeightPx - heightPx - margin`.
 * [heightPx] is the measured height after layout (it changes with compact /
 * expanded mode, enabled rows and width-scaled text).
 */
data class HudWindowBounds(
    val x: Int,
    val y: Int,
    val widthPx: Int,
    val heightPx: Int,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val density: Float,
) {
    val widthDp: Int get() = (widthPx / density).roundToInt()
    val heightDp: Int get() = (heightPx / density).roundToInt()
}

/** Result of trying to put the overlay on screen. */
enum class HudShowResult { SHOWN, ALREADY_SHOWN, NO_OVERLAY_PERMISSION, FAILED }

/**
 * WindowManager plumbing for the always-on-top HUD.
 *
 * - `TYPE_APPLICATION_OVERLAY`, not focusable, not touch-modal: touches outside
 *   the HUD go to the game.
 * - Drag anywhere on the HUD to move; drag the ◢ corner to resize (width, text
 *   scales with it); tap the title to toggle compact / expanded.
 * - Opacity, accent and which rows show come from [HudPreferences].
 * - Lightweight platform views only (no Compose, no RenderEffect, no blur), and
 *   styles are rebuilt only when prefs change, not every sample.
 *
 * Visual styling is intentionally minimal; Ui isaac owns richer HUD visuals in
 * [com.twelvefifths.booster.ui.hud.HudOverlay] (in-app preview). Row content
 * comes from [HudRows] so both stay consistent.
 *
 * Must be used from the main thread.
 *
 * Owner: Script Nathan.
 */
class HudWindowController(
    context: Context,
    private val onGeometryChanged: (HudGeometry) -> Unit = {},
    private val onCompactToggled: (Boolean) -> Unit = {},
    private val onBoundsChanged: (HudWindowBounds?) -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val windowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val touchSlop = ViewConfiguration.get(appContext).scaledTouchSlop

    private var root: FrameLayout? = null
    private var panel: LinearLayout? = null
    private var titleView: TextView? = null
    private var linesView: TextView? = null
    private var handleView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var preferences: HudPreferences = HudPreferences()
    private var styledPrefs: HudPreferences? = null
    private var snapshot: MetricsSnapshot = MetricsSnapshot.empty()
    private var renderedText: String? = null
    private var widthDp: Int = HudPreferences().widthDp
    private var title: String = DEFAULT_TITLE

    // Gesture state.
    private var dragging = false
    private var resizing = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var startWidthPx = 0

    private val mutableBounds = MutableStateFlow<HudWindowBounds?>(null)

    /** Live position + measured size while the window is shown; null when it is not. */
    val bounds: StateFlow<HudWindowBounds?> = mutableBounds.asStateFlow()

    val isShowing: Boolean
        get() = root != null

    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> publishBounds() }

    private val density: Float
        get() = appContext.resources.displayMetrics.density

    @SuppressLint("ClickableViewAccessibility")
    private val moveListener = View.OnTouchListener { _, event -> handleMove(event) }

    @SuppressLint("ClickableViewAccessibility")
    private val resizeListener = View.OnTouchListener { _, event -> handleResize(event) }

    fun show(prefs: HudPreferences = preferences): HudShowResult {
        if (!canDraw()) return HudShowResult.NO_OVERLAY_PERMISSION
        preferences = prefs
        if (root != null) {
            applyStyleIfChanged()
            render()
            return HudShowResult.ALREADY_SHOWN
        }
        widthDp = clampWidthDp(prefs.widthDp)
        val params = WindowManager.LayoutParams(
            dp(widthDp),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = prefs.posX
        params.y = prefs.posY
        params.title = "TwelveFifthsHud"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            params.fitInsetsTypes = 0
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        clampToScreen(params)

        val container = FrameLayout(appContext)
        val inner = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(10)
            setPadding(pad, dp(8), pad, dp(12))
            setOnTouchListener(moveListener)
        }
        val titleText = TextView(appContext).apply {
            text = title
            letterSpacing = 0.12f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val lines = TextView(appContext).apply {
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            setLineSpacing(0f, 1.15f)
        }
        inner.addView(titleText)
        inner.addView(lines)
        val handle = TextView(appContext).apply {
            text = "◢"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.WHITE)
            alpha = 0.7f
            gravity = Gravity.CENTER
            contentDescription = "Resize HUD"
            setOnTouchListener(resizeListener)
        }
        container.addView(inner, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        container.addView(
            handle,
            FrameLayout.LayoutParams(dp(28), dp(28), Gravity.BOTTOM or Gravity.END),
        )

        container.addOnLayoutChangeListener(layoutListener)
        root = container
        panel = inner
        titleView = titleText
        linesView = lines
        handleView = handle
        layoutParams = params
        styledPrefs = null
        renderedText = null
        applyStyleIfChanged()
        render()
        return try {
            windowManager.addView(container, params)
            HudShowResult.SHOWN
        } catch (_: Throwable) {
            // BadTokenException / SecurityException if the permission was just revoked.
            clearViews()
            HudShowResult.FAILED
        }
    }

    /** Pushes a new sample + prefs. Shows the window if it is not up yet. */
    fun update(snapshot: MetricsSnapshot, prefs: HudPreferences): HudShowResult {
        this.snapshot = snapshot
        val previous = preferences
        preferences = prefs
        if (root == null) return show(prefs)
        if (!canDraw()) {
            dismiss()
            return HudShowResult.NO_OVERLAY_PERMISSION
        }
        // Only take geometry from prefs when someone else changed it (e.g. "reset position"),
        // never while the user is dragging.
        if (!dragging && !resizing && geometryChanged(previous, prefs)) {
            val params = layoutParams
            if (params != null) {
                params.x = prefs.posX
                params.y = prefs.posY
                widthDp = clampWidthDp(prefs.widthDp)
                params.width = dp(widthDp)
                clampToScreen(params)
                updateLayout(params)
            }
        }
        applyStyleIfChanged()
        render()
        return HudShowResult.ALREADY_SHOWN
    }

    /** Optional title, e.g. the detected game name. */
    fun setTitle(text: String?) {
        title = text?.takeIf { it.isNotBlank() }?.uppercase() ?: DEFAULT_TITLE
        titleView?.text = title
    }

    fun dismiss() {
        val view = root ?: return
        try {
            windowManager.removeViewImmediate(view)
        } catch (_: Throwable) {
            // Already detached.
        }
        clearViews()
    }

    fun currentPosition(): Pair<Int, Int> {
        val params = layoutParams
        return if (params != null) params.x to params.y else preferences.posX to preferences.posY
    }

    fun currentGeometry(): HudGeometry {
        val (x, y) = currentPosition()
        return HudGeometry(x, y, widthDp)
    }

    /** Re-clamps after rotation / display change. */
    fun onConfigurationChanged() {
        val params = layoutParams ?: return
        clampToScreen(params)
        updateLayout(params)
    }

    private fun clearViews() {
        root?.removeOnLayoutChangeListener(layoutListener)
        root = null
        panel = null
        titleView = null
        linesView = null
        handleView = null
        layoutParams = null
        styledPrefs = null
        renderedText = null
        if (mutableBounds.value != null) {
            mutableBounds.value = null
            onBoundsChanged(null)
        }
    }

    private fun canDraw(): Boolean = try {
        Settings.canDrawOverlays(appContext)
    } catch (_: Throwable) {
        false
    }

    private fun geometryChanged(a: HudPreferences, b: HudPreferences): Boolean =
        a.posX != b.posX || a.posY != b.posY || a.widthDp != b.widthDp

    private fun applyStyleIfChanged() {
        val prefs = preferences
        val last = styledPrefs
        if (last != null && last.opacity == prefs.opacity && last.accentArgb == prefs.accentArgb && last.widthDp == prefs.widthDp) {
            return
        }
        styledPrefs = prefs
        applyStyle()
    }

    private fun applyStyle() {
        val view = panel ?: return
        val alpha = (preferences.opacity.coerceIn(MIN_OPACITY, 1f) * 255).roundToInt()
        val fill = Color.argb(alpha, 11, 13, 18)
        val accent = preferences.accentArgb
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 18f * density
            setColor(fill)
            setStroke((1.2f * density).roundToInt().coerceAtLeast(1), accent)
        }
        titleView?.setTextColor(accent)
        handleView?.setTextColor(accent)
        val scale = (widthDp / DEFAULT_WIDTH_DP.toFloat()).coerceIn(0.75f, 1.8f)
        titleView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f * scale)
        linesView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * scale)
    }

    private fun render() {
        val rows = HudRows.build(snapshot, preferences)
        val text = if (rows.isEmpty()) {
            if (preferences.compact) "waiting for data…" else "no stats enabled"
        } else {
            rows.joinToString("\n") { it.label.padEnd(LABEL_WIDTH) + it.display }
        }
        if (text == renderedText) return
        renderedText = text
        linesView?.text = text
    }

    private fun handleMove(event: MotionEvent): Boolean {
        val params = layoutParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = false
                downRawX = event.rawX
                downRawY = event.rawY
                startX = params.x
                startY = params.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) dragging = true
                if (dragging) {
                    params.x = startX + dx.roundToInt()
                    params.y = startY + dy.roundToInt()
                    clampToScreen(params)
                    updateLayout(params)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    dragging = false
                    onGeometryChanged(currentGeometry())
                } else {
                    toggleCompact()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging) onGeometryChanged(currentGeometry())
                dragging = false
                return true
            }
        }
        return false
    }

    private fun handleResize(event: MotionEvent): Boolean {
        val params = layoutParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resizing = true
                downRawX = event.rawX
                startWidthPx = params.width
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val newPx = startWidthPx + (event.rawX - downRawX).roundToInt()
                widthDp = clampWidthDp((newPx / density).roundToInt())
                params.width = dp(widthDp)
                clampToScreen(params)
                updateLayout(params)
                applyStyle()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                resizing = false
                onGeometryChanged(currentGeometry())
                return true
            }
        }
        return false
    }

    private fun toggleCompact() {
        val next = !preferences.compact
        preferences = preferences.copy(compact = next)
        renderedText = null
        render()
        onCompactToggled(next)
    }

    private fun updateLayout(params: WindowManager.LayoutParams) {
        val view = root ?: return
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Throwable) {
            // View gone.
        }
        publishBounds()
    }

    private fun publishBounds() {
        val view = root
        val params = layoutParams
        val next = if (view == null || params == null || view.height <= 0) {
            if (view == null) null else mutableBounds.value
        } else {
            val (sw, sh) = screenSizePx()
            HudWindowBounds(
                x = params.x,
                y = params.y,
                widthPx = view.width.takeIf { it > 0 } ?: params.width,
                heightPx = view.height,
                screenWidthPx = sw,
                screenHeightPx = sh,
                density = density,
            )
        }
        if (next != mutableBounds.value) {
            mutableBounds.value = next
            onBoundsChanged(next)
        }
    }

    /** Full display size in the overlay's coordinate space. */
    private fun screenSizePx(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val b = windowManager.currentWindowMetrics.bounds
                if (b.width() > 0 && b.height() > 0) return b.width() to b.height()
            } catch (_: Throwable) {
                // Fall through.
            }
        }
        val m = appContext.resources.displayMetrics
        return m.widthPixels to m.heightPixels
    }

    private fun clampToScreen(params: WindowManager.LayoutParams) {
        val (sw, sh) = screenSizePx()
        val w = params.width.coerceAtLeast(dp(MIN_WIDTH_DP))
        val visible = dp(48)
        params.x = params.x.coerceIn(-(w - visible), (sw - visible).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (sh - visible).coerceAtLeast(0))
    }

    private fun clampWidthDp(value: Int): Int = value.coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP)

    private fun dp(value: Int): Int = (value * density).roundToInt()

    companion object {
        const val DEFAULT_TITLE = "TWELVE FIFTHS"
        const val MIN_WIDTH_DP = 140
        const val MAX_WIDTH_DP = 520
        const val DEFAULT_WIDTH_DP = 220
        const val MIN_OPACITY = 0.2f
        private const val LABEL_WIDTH = 9
    }
}
