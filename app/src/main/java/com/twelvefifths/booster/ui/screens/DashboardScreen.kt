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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.twelvefifths.booster.data.SessionRecord
import com.twelvefifths.booster.metrics.MetricSupport
import com.twelvefifths.booster.metrics.MetricsSnapshot
import com.twelvefifths.booster.optimize.GameEntry
import com.twelvefifths.booster.optimize.OptimizationSession
import com.twelvefifths.booster.ui.components.MetricCard
import com.twelvefifths.booster.ui.theme.BoosterTypography
import com.twelvefifths.booster.ui.theme.Charcoal
import com.twelvefifths.booster.ui.theme.GlassPanel
import com.twelvefifths.booster.ui.theme.PrimaryButton
import com.twelvefifths.booster.ui.theme.SecondaryButton

/**
 * Live metrics dashboard. Cards show real samples or an honest support label.
 * History only renders measurements that were actually collected.
 *
 * Owner: Ui isaac.
 */
@Composable
fun DashboardScreen(
    snapshot: MetricsSnapshot,
    games: List<GameEntry>,
    history: List<SessionRecord>,
    activeSession: OptimizationSession?,
    onOpenHudSettings: () -> Unit,
    onToggleHud: () -> Unit,
    hudRunning: Boolean,
    onOptimizeLast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lastPlayed = games.maxByOrNull { it.lastPlayedMs ?: 0L }?.takeIf { it.lastPlayedMs != null }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text("TWELVE FIFTHS", style = BoosterTypography.labelSmall, color = Charcoal.NeonBlue)
                Text("Game Booster", style = BoosterTypography.displaySmall)
                Text(
                    "Live readings from this device. Missing numbers are unsupported, not estimated.",
                    style = BoosterTypography.bodyMedium,
                )
            }
        }
        item { MetricCard("FPS", snapshot.fps?.let { "%.0f".format(it) }, snapshot.fpsSupport, caption = "Other apps' FPS is not readable") }
        item { MetricCard("CPU temp", snapshot.cpuTempC?.let { "%.0f °C".format(it) }, snapshot.cpuTempSupport) }
        item { MetricCard("GPU temp", snapshot.gpuTempC?.let { "%.0f °C".format(it) }, snapshot.gpuTempSupport) }
        item {
            val ram = if (snapshot.ramSupport == MetricSupport.SUPPORTED && snapshot.ramUsedBytes != null && snapshot.ramTotalBytes != null) {
                val used = formatBytes(snapshot.ramUsedBytes)
                val total = formatBytes(snapshot.ramTotalBytes)
                val pct = snapshot.ramPercent?.let { " · %.0f%%".format(it) } ?: ""
                "$used / $total$pct"
            } else null
            MetricCard("RAM", ram, snapshot.ramSupport)
        }
        item { MetricCard("CPU usage", snapshot.cpuUsage?.let { "%.0f%%".format(it) }, snapshot.cpuUsageSupport) }
        item { MetricCard("GPU usage", snapshot.gpuUsage?.let { "%.0f%%".format(it) }, snapshot.gpuUsageSupport, caption = "Needs root or a vendor API") }
        item { MetricCard("Ping", snapshot.pingMs?.let { "$it ms TCP" }, snapshot.pingSupport, caption = "Timed TCP connect, not ICMP") }
        item { MetricCard("GPU VRAM", snapshot.vramUsedBytes?.let { formatBytes(it) }, snapshot.vramSupport, caption = "Not exposed without root") }
        item(span = { GridItemSpan(maxLineSpan) }) {
            GlassPanel {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Session", style = BoosterTypography.titleMedium)
                    Text(
                        when {
                            activeSession != null -> "Optimization is on for ${activeSession.gamePackage}."
                            lastPlayed != null -> "Last played: ${lastPlayed.displayName}"
                            else -> "No session yet. Add a game in Library, then Optimize."
                        },
                        style = BoosterTypography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton(
                            text = if (hudRunning) "Stop HUD" else "Start HUD",
                            onClick = onToggleHud,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(
                            text = "HUD settings",
                            onClick = onOpenHudSettings,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (activeSession == null && lastPlayed != null) {
                        PrimaryButton(
                            text = "Optimize ${lastPlayed.displayName}",
                            onClick = onOptimizeLast,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            ComparisonCard(history = history)
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ComparisonCard(history: List<SessionRecord>) {
    val finished = history.firstOrNull { it.before != null || it.after != null }
    GlassPanel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Before / after", style = BoosterTypography.titleMedium)
            if (finished == null) {
                Text(
                    "Comparisons appear after a session ends, using only measurements that were actually collected.",
                    style = BoosterTypography.bodyMedium,
                )
            } else {
                Text(finished.gamePackage, style = BoosterTypography.bodyMedium, color = Charcoal.Text)
                Text("Before  ${summarize(finished.before)}", style = BoosterTypography.bodyMedium)
                Text("After     ${summarize(finished.after)}", style = BoosterTypography.bodyMedium)
            }
        }
    }
}

private fun summarize(snapshot: MetricsSnapshot?): String {
    if (snapshot == null) return "no sample"
    val parts = buildList {
        if (snapshot.ramSupport == MetricSupport.SUPPORTED && snapshot.ramPercent != null) {
            add("RAM %.0f%%".format(snapshot.ramPercent))
        }
        if (snapshot.cpuUsageSupport == MetricSupport.SUPPORTED && snapshot.cpuUsage != null) {
            add("CPU %.0f%%".format(snapshot.cpuUsage))
        }
        if (snapshot.pingSupport == MetricSupport.SUPPORTED && snapshot.pingMs != null) {
            add("ping ${snapshot.pingMs} ms")
        }
    }
    return if (parts.isEmpty()) "no supported readings in this sample" else parts.joinToString(" · ")
}

internal fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) "%.1f GB".format(mb / 1024.0) else "%.0f MB".format(mb)
}
