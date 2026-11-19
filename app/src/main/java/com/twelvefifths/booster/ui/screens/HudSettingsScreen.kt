package com.twelvefifths.booster.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewStream
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.twelvefifths.booster.data.HudPreferences
import com.twelvefifths.booster.metrics.MetricsSnapshot
import com.twelvefifths.booster.ui.components.LabeledSlider
import com.twelvefifths.booster.ui.components.ScreenTitle
import com.twelvefifths.booster.ui.components.SectionHeader
import com.twelvefifths.booster.ui.components.StatusPill
import com.twelvefifths.booster.ui.components.SupportPill
import com.twelvefifths.booster.ui.components.ToggleRow
import com.twelvefifths.booster.ui.format.Metric
import com.twelvefifths.booster.ui.format.MetricFormat
import com.twelvefifths.booster.ui.format.PermissionKind
import com.twelvefifths.booster.ui.graph.MetricHistory
import com.twelvefifths.booster.ui.hud.HudOverlay
import com.twelvefifths.booster.ui.hud.HudRows
import com.twelvefifths.booster.ui.theme.BoosterTypography
import com.twelvefifths.booster.ui.theme.Charcoal
import com.twelvefifths.booster.ui.theme.ChoiceChip
import com.twelvefifths.booster.ui.theme.GlassPanel
import com.twelvefifths.booster.ui.theme.PillButton
import kotlin.math.roundToInt

/**
 * HUD settings with a live preview over a mock game frame. Sliders edit a
 * local draft and commit on release so DataStore is not written every frame.
 *
 * Owner: Ui isaac.
 */
@Composable
fun HudSettingsScreen(
    prefs: HudPreferences,
    snapshot: MetricsSnapshot,
    metricHistory: MetricHistory,
    overlayGranted: Boolean,
    notificationsGranted: Boolean,
    hudRunning: Boolean,
    onPrefsChange: (HudPreferences) -> Unit,
    onToggleHud: () -> Unit,
    onGrantPermission: (PermissionKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(prefs) { mutableStateOf(prefs) }
    fun commit(next: HudPreferences) {
        draft = next
        onPrefsChange(next)
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth.value >= 760f
        val preview: @Composable (Modifier) -> Unit = { m -> PreviewCard(draft, snapshot, metricHistory, m) }
        val controls: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                RunCard(hudRunning, overlayGranted, notificationsGranted, onToggleHud, onGrantPermission)
                LayoutCard(draft, onDraft = { draft = it }, onCommit = { commit(it) })
                StatsCard(draft, snapshot, onCommit = { commit(it) })
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ScreenTitle(
                title = "HUD",
                subtitle = "Always-on-top overlay · drag it on screen to fine-tune its position",
                trailing = { StatusPill(if (hudRunning) "Running" else "Stopped", if (hudRunning) Charcoal.Ok else Charcoal.TextDim) },
            )
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    preview(Modifier.weight(1f))
                    controls(Modifier.weight(1f))
                }
            } else {
                preview(Modifier.fillMaxWidth())
                controls(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PreviewCard(prefs: HudPreferences, snapshot: MetricsSnapshot, history: MetricHistory, modifier: Modifier) {
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val screenW = with(density) { config.screenWidthDp.dp.toPx() }
    val screenH = with(density) { config.screenHeightDp.dp.toPx() }
    val aspect = (config.screenWidthDp.toFloat() / config.screenHeightDp.coerceAtLeast(1)).coerceIn(0.5f, 2.2f)
    var box by remember { mutableStateOf(IntSize.Zero) }
    var hud by remember { mutableStateOf(IntSize.Zero) }
    val hudWPx = with(density) { prefs.widthDp.dp.toPx() }
    val fx = (prefs.posX / (screenW - hudWPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
    val fy = (prefs.posY / (screenH - hud.height.toFloat().coerceAtLeast(1f)).coerceAtLeast(1f)).coerceIn(0f, 1f)
    val ax by animateFloatAsState(fx, tween(350), label = "hudX")
    val ay by animateFloatAsState(fy, tween(350), label = "hudY")
    GlassPanel(modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader("Live preview", subtitle = "Real values from this device, drawn the way the overlay shows them")
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MockGameBackdrop)
                    .border(1.dp, Charcoal.LineSoft, RoundedCornerShape(18.dp))
                    .onSizeChanged { box = it }
                    .semantics { contentDescription = "HUD preview over a sample game screen" },
            ) {
                MockScenery()
                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                ((box.width - hud.width).coerceAtLeast(0) * ax).roundToInt(),
                                ((box.height - hud.height).coerceAtLeast(0) * ay).roundToInt(),
                            )
                        }
                        .padding(8.dp)
                        .onSizeChanged { hud = it },
                ) {
                    HudOverlay(snapshot = snapshot, prefs = prefs, history = history)
                }
            }
            Text(
                "The sample backdrop is decoration only. Every number in the HUD is a live reading or an honest support label.",
                style = BoosterTypography.bodyMedium,
                color = Charcoal.TextDim,
            )
        }
    }
}

private val MockGameBackdrop = Brush.linearGradient(
    colors = listOf(Color(0xFF1B2440), Color(0xFF221A3A), Color(0xFF0E1222)),
    start = Offset.Zero,
    end = Offset.Infinite,
)

/** A few flat shapes so the preview reads as "over a game". Cheap, static. */
@Composable
private fun MockScenery() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(Color(0x22000000), topLeft = Offset(0f, h * 0.68f), size = androidx.compose.ui.geometry.Size(w, h * 0.32f))
        drawCircle(Color(0x334F8CFF), radius = h * 0.16f, center = Offset(w * 0.78f, h * 0.30f))
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, h * 0.70f)
            lineTo(w * 0.25f, h * 0.45f)
            lineTo(w * 0.45f, h * 0.62f)
            lineTo(w * 0.70f, h * 0.38f)
            lineTo(w, h * 0.66f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(path, Color(0x55120F24))
    }
}

@Composable
private fun RunCard(
    hudRunning: Boolean,
    overlayGranted: Boolean,
    notificationsGranted: Boolean,
    onToggleHud: () -> Unit,
    onGrantPermission: (PermissionKind) -> Unit,
) {
    GlassPanel(accent = if (hudRunning) Charcoal.Ok else Charcoal.NeonBlue) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            ToggleRow(
                title = "Show HUD over games",
                subtitle = when {
                    !overlayGranted -> "Needs Display over other apps."
                    hudRunning -> "Running in a foreground service. Stop it here or from its notification."
                    else -> "Starts a small foreground service that draws the overlay."
                },
                checked = hudRunning,
                enabled = overlayGranted || hudRunning,
                onCheckedChange = { onToggleHud() },
                trailingExtra = if (!overlayGranted) {
                    { PillButton("Grant permission", { onGrantPermission(PermissionKind.OVERLAY) }) }
                } else {
                    null
                },
            )
            if (overlayGranted && !notificationsGranted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Allow notifications so the HUD's Stop control is visible.",
                        style = BoosterTypography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    PillButton("Grant permission", { onGrantPermission(PermissionKind.NOTIFICATIONS) })
                }
            }
            Text(
                "Android has no system-wide hotkey for third-party apps, so toggle the HUD here or from its notification.",
                style = BoosterTypography.bodyMedium,
                color = Charcoal.TextDim,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun LayoutCard(draft: HudPreferences, onDraft: (HudPreferences) -> Unit, onCommit: (HudPreferences) -> Unit) {
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    GlassPanel {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Layout")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceChip("Compact", draft.compact, { onCommit(draft.copy(compact = true)) }, Modifier.weight(1f), icon = Icons.Outlined.ViewStream)
                ChoiceChip("Expanded", !draft.compact, { onCommit(draft.copy(compact = false)) }, Modifier.weight(1f), icon = Icons.Outlined.ViewAgenda)
            }
            Text(
                if (draft.compact) "Up to ${HudRows.COMPACT_MAX_ROWS} stats in a tight panel." else "Every selected stat, plus a RAM trend line.",
                style = BoosterTypography.bodyMedium,
                color = Charcoal.TextDim,
            )
            LabeledSlider(
                title = "Size",
                valueLabel = "${draft.widthDp} dp wide",
                value = draft.widthDp.toFloat(),
                onValueChange = { onDraft(draft.copy(widthDp = (it / 10f).roundToInt() * 10)) },
                onValueChangeFinished = { onCommit(draft) },
                range = 160f..360f,
                steps = 19,
            )
            LabeledSlider(
                title = "Opacity",
                valueLabel = "${(draft.opacity * 100).roundToInt()}%",
                value = draft.opacity.coerceIn(0.35f, 1f),
                onValueChange = { onDraft(draft.copy(opacity = it)) },
                onValueChangeFinished = { onCommit(draft) },
                range = 0.35f..1f,
            )
            Text("Accent color", style = BoosterTypography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HudRows.accentSwatches.forEach { argb ->
                    Swatch(Color(argb), selected = draft.accentArgb == argb) { onCommit(draft.copy(accentArgb = argb)) }
                }
            }
            Text("Position", style = BoosterTypography.bodyLarge)
            val corners = listOf(
                HudRows.Corner.TOP_START to "Top left",
                HudRows.Corner.TOP_END to "Top right",
                HudRows.Corner.BOTTOM_START to "Bottom left",
                HudRows.Corner.BOTTOM_END to "Bottom right",
            )
            val screenW = with(density) { config.screenWidthDp.dp.roundToPx() }
            val screenH = with(density) { config.screenHeightDp.dp.roundToPx() }
            val hudW = with(density) { draft.widthDp.dp.roundToPx() }
            val rowsShown = HudRows.enabled(draft).size.let { if (draft.compact) minOf(it, HudRows.COMPACT_MAX_ROWS) else it }
            val hudH = with(density) { (36 + rowsShown * 20 + if (draft.compact) 0 else 36).dp.roundToPx() }
            val margin = with(density) { 16.dp.roundToPx() }
            val currentCorner = corners.firstOrNull { (c, _) ->
                HudRows.cornerPosition(c, screenW, screenH, hudW, hudH, margin) == (draft.posX to draft.posY)
            }?.first
            corners.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { (corner, label) ->
                        ChoiceChip(label, currentCorner == corner, {
                            val (x, y) = HudRows.cornerPosition(corner, screenW, screenH, hudW, hudH, margin)
                            onCommit(draft.copy(posX = x, posY = y))
                        }, Modifier.weight(1f))
                    }
                }
            }
            Text(
                "Presets apply the next time the HUD starts; drag the overlay to move it while it runs.",
                style = BoosterTypography.bodyMedium,
                color = Charcoal.TextDim,
            )
        }
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    val rim by animateColorAsState(if (selected) Charcoal.Text else Color.Transparent, label = "swatch")
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .border(2.dp, rim, CircleShape)
            .padding(4.dp)
            .background(color, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "Accent color" },
    )
}

@Composable
private fun StatsCard(draft: HudPreferences, snapshot: MetricsSnapshot, onCommit: (HudPreferences) -> Unit) {
    GlassPanel {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            SectionHeader("Stats on the HUD", subtitle = "Unsupported stats show their label, never a made-up number")
            Spacer(Modifier.height(6.dp))
            Metric.entries.forEach { metric ->
                ToggleRow(
                    title = metric.title,
                    subtitle = null,
                    checked = HudRows.isShown(draft, metric),
                    onCheckedChange = { onCommit(HudRows.withShown(draft, metric, it)) },
                    trailingExtra = {
                        SupportPill(MetricFormat.support(metric, snapshot), Modifier.padding(end = 10.dp))
                    },
                )
            }
        }
    }
}
