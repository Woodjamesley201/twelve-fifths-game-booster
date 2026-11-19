package com.twelvefifths.booster.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.twelvefifths.booster.data.SessionRecord
import com.twelvefifths.booster.metrics.MetricsSnapshot
import com.twelvefifths.booster.optimize.GameEntry
import com.twelvefifths.booster.optimize.OptimizationSession
import com.twelvefifths.booster.ui.components.AdaptiveGrid
import com.twelvefifths.booster.ui.components.GraphPanel
import com.twelvefifths.booster.ui.components.GraphSeries
import com.twelvefifths.booster.ui.components.MetricTile
import com.twelvefifths.booster.ui.components.MetricValue
import com.twelvefifths.booster.ui.components.ScreenTitle
import com.twelvefifths.booster.ui.components.SectionHeader
import com.twelvefifths.booster.ui.components.StatusPill
import com.twelvefifths.booster.ui.components.UsageBar
import com.twelvefifths.booster.ui.components.gridColumns
import com.twelvefifths.booster.ui.format.ComparisonRow
import com.twelvefifths.booster.ui.format.Metric
import com.twelvefifths.booster.ui.format.MetricDisplay
import com.twelvefifths.booster.ui.format.MetricFormat
import com.twelvefifths.booster.ui.format.PermissionKind
import com.twelvefifths.booster.ui.format.SessionCompare
import com.twelvefifths.booster.ui.graph.GraphMath
import com.twelvefifths.booster.ui.graph.MetricHistory
import com.twelvefifths.booster.ui.theme.BoosterTypography
import com.twelvefifths.booster.ui.theme.Charcoal
import com.twelvefifths.booster.ui.theme.GlassPanel
import com.twelvefifths.booster.ui.theme.PrimaryButton
import com.twelvefifths.booster.ui.theme.SecondaryButton
import java.text.DateFormat
import java.util.Date

/**
 * Dashboard: live metrics with honest support states, rolling graphs built
 * from real samples only, and before/after comparisons from SessionRecord.
 *
 * Owner: Ui isaac.
 */
@Composable
fun DashboardScreen(
    snapshot: MetricsSnapshot,
    metricHistory: MetricHistory,
    games: List<GameEntry>,
    sessions: List<SessionRecord>,
    activeSession: OptimizationSession?,
    hudRunning: Boolean,
    onToggleHud: () -> Unit,
    onOpenHudSettings: () -> Unit,
    onOptimizeLast: () -> Unit,
    onGrantPermission: (PermissionKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nameOf: (String) -> String = { pkg -> games.firstOrNull { it.packageName == pkg }?.displayName ?: pkg }
    val lastGame = games.maxByOrNull { it.lastPlayedMs ?: 0L } ?: games.firstOrNull()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val cols = gridColumns(maxWidth.value - 48f, minCellDp = 280f)
        val wide = cols >= 2
        val tileCols = gridColumns(maxWidth.value - 48f, minCellDp = 200f, max = 3)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ScreenTitle(
                title = "Dashboard",
                subtitle = activeSession?.let { "Boost session running · ${nameOf(it.gamePackage)}" }
                    ?: "Live device metrics · nothing shown here is estimated",
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (activeSession != null) StatusPill("Boosted", Charcoal.NeonPurple)
                        StatusPill(if (hudRunning) "HUD on" else "HUD off", if (hudRunning) Charcoal.Ok else Charcoal.TextDim)
                    }
                },
            )

            // Hero: FPS + RAM.
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    FpsCard(snapshot, metricHistory, onGrantPermission, Modifier.weight(1f))
                    RamCard(snapshot, metricHistory, onGrantPermission, Modifier.weight(1f))
                }
            } else {
                FpsCard(snapshot, metricHistory, onGrantPermission, Modifier.fillMaxWidth())
                RamCard(snapshot, metricHistory, onGrantPermission, Modifier.fillMaxWidth())
            }

            QuickActions(
                hudRunning = hudRunning,
                lastGame = lastGame,
                activeSession = activeSession,
                onToggleHud = onToggleHud,
                onOpenHudSettings = onOpenHudSettings,
                onOptimizeLast = onOptimizeLast,
                stacked = !wide,
            )

            SectionHeader("Live graphs", subtitle = "Last ${metricHistory.capacity} samples · gaps mean no reading")
            val graphs = listOf("temp", "cpu", "ping")
            AdaptiveGrid(graphs, columns = cols) { key, m ->
                when (key) {
                    "temp" -> TemperatureGraph(snapshot, metricHistory, onGrantPermission, m)
                    "cpu" -> SingleGraph(Metric.CPU_USAGE, snapshot, metricHistory, onGrantPermission, m, floor = 0f, ceiling = 100f, accent = Charcoal.NeonPurple)
                    else -> SingleGraph(Metric.PING, snapshot, metricHistory, onGrantPermission, m, floor = 0f, accent = Charcoal.NeonCyan, minSpan = 20f)
                }
            }

            SectionHeader("Sensors", subtitle = "What this Android build exposes to an unprivileged app")
            val tiles = listOf(Metric.CPU_TEMP, Metric.GPU_TEMP, Metric.CPU_USAGE, Metric.GPU_USAGE, Metric.PING, Metric.VRAM)
            AdaptiveGrid(tiles, columns = tileCols) { metric, m ->
                MetricTile(metric, snapshot, onGrantPermission, m, accent = if (metric.ordinal % 2 == 0) Charcoal.NeonBlue else Charcoal.NeonPurple)
            }

            SectionHeader("Before / after", subtitle = "Real snapshots captured when a boost started and when it was restored")
            val ended = sessions.filter { it.endedAtMs != null }.take(if (wide) 4 else 3)
            if (ended.isEmpty()) {
                GlassPanel(Modifier.fillMaxWidth()) {
                    Text(
                        "No finished boost sessions yet. Run Optimize, play, then Restore to record a comparison.",
                        style = BoosterTypography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                AdaptiveGrid(ended, columns = if (wide) 2 else 1) { record, m ->
                    SessionCard(record, nameOf(record.gamePackage), m)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FpsCard(
    snapshot: MetricsSnapshot,
    history: MetricHistory,
    onGrant: (PermissionKind) -> Unit,
    modifier: Modifier,
) {
    val display = MetricFormat.display(Metric.FPS, snapshot)
    GlassPanel(modifier, accent = Charcoal.NeonBlue) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text("GAME FPS", style = BoosterTypography.labelSmall, color = Charcoal.TextMuted)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column {
                    Text("Current", style = BoosterTypography.labelSmall)
                    MetricValue(display, onGrant, valueStyle = BoosterTypography.headlineMedium)
                }
                if (display is MetricDisplay.Value || display is MetricDisplay.Waiting) {
                    val avg = history.average(Metric.FPS)
                    Column {
                        Text("Average", style = BoosterTypography.labelSmall)
                        Text(avg?.let(MetricFormat::fps) ?: MetricFormat.WAITING_LABEL, style = BoosterTypography.headlineSmall, color = Charcoal.TextMuted)
                    }
                }
            }
            when (display) {
                MetricDisplay.Unsupported -> {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        MetricFormat.unsupportedReason(Metric.FPS) + " This app's own frame pacing is not game FPS, so it is not shown here.",
                        style = BoosterTypography.bodyMedium,
                        color = Charcoal.TextDim,
                    )
                }
                is MetricDisplay.NeedsPermission -> Unit
                else -> {
                    val fps = history.series(Metric.FPS)
                    Spacer(Modifier.height(10.dp))
                    com.twelvefifths.booster.ui.components.LineGraph(
                        series = listOf(GraphSeries(fps, Charcoal.NeonBlue)),
                        range = GraphMath.range(fps, floor = 0f, minSpan = 30f, step = 10f),
                        capacity = history.capacity,
                        height = 72.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun RamCard(
    snapshot: MetricsSnapshot,
    history: MetricHistory,
    onGrant: (PermissionKind) -> Unit,
    modifier: Modifier,
) {
    val display = MetricFormat.display(Metric.RAM, snapshot)
    val pct = MetricFormat.numeric(Metric.RAM, snapshot)
    val animated by animateFloatAsState((pct ?: 0f) / 100f, tween(600), label = "ram")
    GlassPanel(modifier, accent = Charcoal.NeonPurple) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("MEMORY", style = BoosterTypography.labelSmall, color = Charcoal.TextMuted, modifier = Modifier.weight(1f))
                if (pct != null) Text(MetricFormat.percent(pct), style = BoosterTypography.labelLarge, color = Charcoal.NeonPurple)
            }
            Spacer(Modifier.height(6.dp))
            MetricValue(display, onGrant, valueStyle = BoosterTypography.headlineSmall)
            if (display is MetricDisplay.Unsupported) {
                Text(MetricFormat.unsupportedReason(Metric.RAM), style = BoosterTypography.bodyMedium, color = Charcoal.TextDim)
            }
            AnimatedVisibility(pct != null) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    UsageBar(animated)
                    val ram = history.series(Metric.RAM)
                    Spacer(Modifier.height(10.dp))
                    com.twelvefifths.booster.ui.components.LineGraph(
                        series = listOf(GraphSeries(ram, Charcoal.NeonPurple)),
                        range = GraphMath.range(ram, floor = 0f, ceiling = 100f),
                        capacity = history.capacity,
                        height = 64.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun TemperatureGraph(
    snapshot: MetricsSnapshot,
    history: MetricHistory,
    onGrant: (PermissionKind) -> Unit,
    modifier: Modifier,
) {
    val cpu = MetricFormat.display(Metric.CPU_TEMP, snapshot)
    val gpu = MetricFormat.display(Metric.GPU_TEMP, snapshot)
    val parts = listOfNotNull(
        (cpu as? MetricDisplay.Value)?.let { "CPU ${it.text}" },
        (gpu as? MetricDisplay.Value)?.let { "GPU ${it.text}" },
    )
    val current = MetricFormat.combine(listOf(cpu, gpu), parts.takeIf { it.isNotEmpty() }?.joinToString("  ·  "))
    val cpuSeries = history.series(Metric.CPU_TEMP)
    val gpuSeries = history.series(Metric.GPU_TEMP)
    val series = buildList {
        if (cpu !is MetricDisplay.Unsupported) add(GraphSeries(cpuSeries, Charcoal.NeonBlue, "CPU"))
        if (gpu !is MetricDisplay.Unsupported) add(GraphSeries(gpuSeries, Charcoal.NeonPurple, "GPU"))
    }
    GraphPanel(
        title = "Temperature history",
        current = current,
        series = series,
        range = GraphMath.range(cpuSeries + gpuSeries, minSpan = 6f, step = 2f),
        capacity = history.capacity,
        onGrant = onGrant,
        modifier = modifier,
        unsupportedReason = "No readable CPU/GPU thermal zone. Android's thermal status is a level, not °C, so it is not converted.",
        rangeLabel = { r -> "${r.min.toInt()}–${r.max.toInt()} °C" },
    )
}

@Composable
private fun SingleGraph(
    metric: Metric,
    snapshot: MetricsSnapshot,
    history: MetricHistory,
    onGrant: (PermissionKind) -> Unit,
    modifier: Modifier,
    floor: Float? = null,
    ceiling: Float? = null,
    minSpan: Float = 1f,
    accent: androidx.compose.ui.graphics.Color = Charcoal.NeonBlue,
) {
    val values = history.series(metric)
    GraphPanel(
        title = metric.title,
        current = MetricFormat.display(metric, snapshot),
        series = listOf(GraphSeries(values, accent)),
        range = GraphMath.range(values, floor = floor, ceiling = ceiling, minSpan = minSpan),
        capacity = history.capacity,
        onGrant = onGrant,
        modifier = modifier,
        accent = accent,
        unsupportedReason = MetricFormat.unsupportedReason(metric),
    )
}

@Composable
private fun QuickActions(
    hudRunning: Boolean,
    lastGame: GameEntry?,
    activeSession: OptimizationSession?,
    onToggleHud: () -> Unit,
    onOpenHudSettings: () -> Unit,
    onOptimizeLast: () -> Unit,
    stacked: Boolean,
) {
    val optimizeLabel = when {
        activeSession != null -> "Session running"
        lastGame != null -> "Optimize ${lastGame.displayName}"
        else -> "Add a game to optimize"
    }
    val buttons: @Composable (Modifier) -> Unit = { m ->
        PrimaryButton(optimizeLabel, onOptimizeLast, m, enabled = lastGame != null && activeSession == null, icon = Icons.Outlined.Bolt)
        SecondaryButton(if (hudRunning) "Stop HUD" else "Start HUD", onToggleHud, m, icon = Icons.Outlined.Layers)
        SecondaryButton("HUD settings", onOpenHudSettings, m)
    }
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { buttons(Modifier.fillMaxWidth()) }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { buttons(Modifier.weight(1f)) }
    }
}

@Composable
private fun SessionCard(record: SessionRecord, gameName: String, modifier: Modifier) {
    val rows = SessionCompare.rows(record)
    val started = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(record.startedAtMs))
    val length = record.endedAtMs?.let { MetricFormat.duration(it - record.startedAtMs) }
    GlassPanel(modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(gameName, style = BoosterTypography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "$started" + (length?.let { " · $it" } ?: "") + " · closed ${record.closedPackages.size} app(s)",
                style = BoosterTypography.bodyMedium,
                color = Charcoal.TextDim,
            )
            Spacer(Modifier.height(4.dp))
            Row {
                Text("", Modifier.weight(1.1f))
                Text("START", style = BoosterTypography.labelSmall, modifier = Modifier.weight(1f))
                Text("RESTORE", style = BoosterTypography.labelSmall, modifier = Modifier.weight(1f))
                Text("CHANGE", style = BoosterTypography.labelSmall, modifier = Modifier.weight(0.8f))
            }
            if (rows.isEmpty()) {
                Text("No comparable metrics were captured.", style = BoosterTypography.bodyMedium)
            }
            rows.forEach { ComparisonLine(it) }
        }
    }
}

@Composable
private fun ComparisonLine(row: ComparisonRow) {
    val deltaColor = when (row.improved) {
        true -> Charcoal.Ok
        false -> Charcoal.Warn
        null -> Charcoal.TextMuted
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(row.metric.shortLabel, style = BoosterTypography.labelLarge, color = Charcoal.TextMuted, modifier = Modifier.weight(1.1f))
        CompareCell(row.before, Modifier.weight(1f))
        CompareCell(row.after, Modifier.weight(1f))
        Text(row.delta ?: "—", style = BoosterTypography.labelLarge, color = deltaColor, modifier = Modifier.weight(0.8f))
    }
}

@Composable
private fun CompareCell(text: String, modifier: Modifier) {
    val dim = text == MetricFormat.UNSUPPORTED_LABEL || text == MetricFormat.GRANT_LABEL || text == "Not captured"
    Text(
        if (text == MetricFormat.GRANT_LABEL) "Permission missing" else text,
        style = if (dim) BoosterTypography.labelSmall else BoosterTypography.labelLarge,
        color = if (dim) Charcoal.TextDim else Charcoal.Text,
        modifier = modifier,
        maxLines = 2,
    )
}
