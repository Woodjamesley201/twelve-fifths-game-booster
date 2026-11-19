package com.twelvefifths.booster.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.twelvefifths.booster.data.HudPreferences
import com.twelvefifths.booster.metrics.MetricsSnapshot
import com.twelvefifths.booster.ui.hud.HudOverlay
import com.twelvefifths.booster.ui.theme.BoosterTypography
import com.twelvefifths.booster.ui.theme.Charcoal
import com.twelvefifths.booster.ui.theme.GlassPanel
import com.twelvefifths.booster.ui.theme.PrimaryButton
import com.twelvefifths.booster.ui.theme.SecondaryButton

/**
 * HUD appearance, which stats to show, compact/expanded, overlay permission.
 * Android has no reliable global keyboard shortcut for a third-party overlay;
 * the notification action and the toggle below are the supported switches.
 *
 * Owner: Ui isaac.
 */
@Composable
fun HudSettingsScreen(
    prefs: HudPreferences,
    snapshot: MetricsSnapshot,
    overlayGranted: Boolean,
    hudRunning: Boolean,
    onPrefsChange: (HudPreferences) -> Unit,
    onRequestOverlay: () -> Unit,
    onToggleHud: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("HUD", style = BoosterTypography.displaySmall)
                Text(
                    "The overlay sits over other apps after you allow “display over other apps.” Drag it to reposition. There is no system-wide keybind for third-party overlays; use the toggle or the HUD notification.",
                    style = BoosterTypography.bodyMedium,
                )
            }
        }
        item {
            GlassPanel {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Preview", style = BoosterTypography.titleMedium)
                    HudOverlay(snapshot = snapshot, prefs = prefs)
                    if (!overlayGranted) {
                        Text("Overlay permission is off — the live HUD cannot appear yet.", style = BoosterTypography.bodyMedium)
                        SecondaryButton("Allow display over other apps", onRequestOverlay, Modifier.fillMaxWidth())
                    }
                    PrimaryButton(
                        text = if (hudRunning) "Stop HUD" else "Start HUD",
                        onClick = onToggleHud,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        item {
            GlassPanel {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Look", style = BoosterTypography.titleMedium)
                    Text("Opacity  ${(prefs.opacity * 100).toInt()}%", style = BoosterTypography.bodyMedium)
                    Slider(
                        value = prefs.opacity,
                        onValueChange = { onPrefsChange(prefs.copy(opacity = it.coerceIn(0.35f, 1f))) },
                        valueRange = 0.35f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Charcoal.NeonBlue,
                            activeTrackColor = Charcoal.NeonBlue,
                            inactiveTrackColor = Charcoal.Line,
                        ),
                    )
                    Text("Width  ${prefs.widthDp} dp", style = BoosterTypography.bodyMedium)
                    Slider(
                        value = prefs.widthDp.toFloat(),
                        onValueChange = { onPrefsChange(prefs.copy(widthDp = it.toInt().coerceIn(160, 360))) },
                        valueRange = 160f..360f,
                        colors = SliderDefaults.colors(
                            thumbColor = Charcoal.NeonPurple,
                            activeTrackColor = Charcoal.NeonPurple,
                            inactiveTrackColor = Charcoal.Line,
                        ),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AccentSwatch("Blue", 0xFF4F8CFF.toInt(), prefs, onPrefsChange, Modifier.weight(1f))
                        AccentSwatch("Purple", 0xFFA855F7.toInt(), prefs, onPrefsChange, Modifier.weight(1f))
                    }
                    CheckRow("Compact layout", prefs.compact) {
                        onPrefsChange(prefs.copy(compact = it))
                    }
                }
            }
        }
        item {
            GlassPanel {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Statistics", style = BoosterTypography.titleMedium)
                    CheckRow("FPS", prefs.showFps) { onPrefsChange(prefs.copy(showFps = it)) }
                    CheckRow("CPU temperature", prefs.showCpuTemp) { onPrefsChange(prefs.copy(showCpuTemp = it)) }
                    CheckRow("GPU temperature", prefs.showGpuTemp) { onPrefsChange(prefs.copy(showGpuTemp = it)) }
                    CheckRow("RAM", prefs.showRam) { onPrefsChange(prefs.copy(showRam = it)) }
                    CheckRow("CPU usage", prefs.showCpuUsage) { onPrefsChange(prefs.copy(showCpuUsage = it)) }
                    CheckRow("GPU usage", prefs.showGpuUsage) { onPrefsChange(prefs.copy(showGpuUsage = it)) }
                    CheckRow("Ping", prefs.showPing) { onPrefsChange(prefs.copy(showPing = it)) }
                    CheckRow("GPU VRAM", prefs.showVram) { onPrefsChange(prefs.copy(showVram = it)) }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun AccentSwatch(
    label: String,
    argb: Int,
    prefs: HudPreferences,
    onPrefsChange: (HudPreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = prefs.accentArgb == argb
    if (selected) {
        PrimaryButton(label, { onPrefsChange(prefs.copy(accentArgb = argb)) }, modifier)
    } else {
        SecondaryButton(label, { onPrefsChange(prefs.copy(accentArgb = argb)) }, modifier)
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = BoosterTypography.bodyLarge, color = Charcoal.Text, modifier = Modifier.weight(1f))
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(checkedColor = Charcoal.NeonBlue, uncheckedColor = Charcoal.TextMuted),
        )
    }
}
