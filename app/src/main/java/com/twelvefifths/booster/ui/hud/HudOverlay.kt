package com.twelvefifths.booster.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.twelvefifths.booster.data.HudPreferences
import com.twelvefifths.booster.metrics.MetricSupport
import com.twelvefifths.booster.metrics.MetricsSnapshot
import com.twelvefifths.booster.ui.theme.BoosterTypography
import com.twelvefifths.booster.ui.theme.Charcoal
import com.twelvefifths.booster.ui.theme.glassBrush

/**
 * In-app preview of the HUD. The live overlay is a platform view hosted by
 * HudService so it does not put a Compose blur stack over other apps.
 *
 * Owner: Ui isaac.
 */
@Composable
fun HudOverlay(
    snapshot: MetricsSnapshot,
    prefs: HudPreferences,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    val accent = Color(prefs.accentArgb)
    val fill = Charcoal.Night.copy(alpha = prefs.opacity.coerceIn(0.35f, 1f))
    Column(
        modifier = modifier
            .clip(shape)
            .background(fill, shape)
            .background(glassBrush(), shape)
            .border(1.dp, accent.copy(alpha = 0.7f), shape)
            .padding(12.dp),
    ) {
        Text("TWELVE FIFTHS", style = BoosterTypography.labelSmall, color = accent)
        val rows = buildList {
            if (prefs.showFps) add("FPS" to format(snapshot.fps?.let { "%.0f".format(it) }, snapshot.fpsSupport))
            if (prefs.showCpuTemp) add("CPU °C" to format(snapshot.cpuTempC?.let { "%.0f".format(it) }, snapshot.cpuTempSupport))
            if (prefs.showGpuTemp) add("GPU °C" to format(snapshot.gpuTempC?.let { "%.0f".format(it) }, snapshot.gpuTempSupport))
            if (prefs.showRam) add("RAM" to ram(snapshot))
            if (prefs.showCpuUsage) add("CPU %" to format(snapshot.cpuUsage?.let { "%.0f".format(it) }, snapshot.cpuUsageSupport))
            if (prefs.showGpuUsage) add("GPU %" to format(snapshot.gpuUsage?.let { "%.0f".format(it) }, snapshot.gpuUsageSupport))
            if (prefs.showPing) add("PING" to format(snapshot.pingMs?.let { "$it ms" }, snapshot.pingSupport))
            if (prefs.showVram) add("VRAM" to format(snapshot.vramUsedBytes?.let { formatBytes(it) }, snapshot.vramSupport))
        }
        val visible = if (prefs.compact) rows.take(4) else rows
        visible.forEach { (label, value) ->
            Text("$label  $value", style = BoosterTypography.bodyMedium, color = Charcoal.Text)
        }
    }
}

private fun format(value: String?, support: MetricSupport): String = when {
    support == MetricSupport.SUPPORTED && value != null -> value
    support == MetricSupport.SUPPORTED -> "—"
    support == MetricSupport.NEEDS_PERMISSION -> "needs permission"
    else -> "unsupported"
}

private fun ram(snapshot: MetricsSnapshot): String {
    if (snapshot.ramSupport != MetricSupport.SUPPORTED) return format(null, snapshot.ramSupport)
    val used = snapshot.ramUsedBytes ?: return "—"
    val total = snapshot.ramTotalBytes ?: return formatBytes(used)
    val pct = snapshot.ramPercent?.let { " (%.0f%%)".format(it) } ?: ""
    return "${formatBytes(used)} / ${formatBytes(total)}$pct"
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "%.0f MB".format(mb)
}
