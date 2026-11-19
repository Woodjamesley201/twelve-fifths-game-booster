package com.twelvefifths.booster.ui.hud

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twelvefifths.booster.data.HudPreferences
import com.twelvefifths.booster.metrics.MetricsSnapshot
import com.twelvefifths.booster.ui.components.GraphSeries
import com.twelvefifths.booster.ui.components.LineGraph
import com.twelvefifths.booster.ui.format.Metric
import com.twelvefifths.booster.ui.format.MetricDisplay
import com.twelvefifths.booster.ui.graph.GraphMath
import com.twelvefifths.booster.ui.graph.MetricHistory
import com.twelvefifths.booster.ui.theme.BoosterTypography
import com.twelvefifths.booster.ui.theme.Charcoal
import com.twelvefifths.booster.ui.theme.glassBrush

/**
 * In-app preview of the HUD (and the reference look for the platform overlay).
 * The live overlay is a lightweight platform view hosted by HudService, so it
 * never puts a Compose blur stack over other apps. Glass = fill at the user's
 * opacity + veil + accent rim. No RenderEffect.
 *
 * Compact: up to four rows, tight. Expanded: every enabled row plus a RAM
 * sparkline when [history] has real RAM samples.
 *
 * Owner: Ui isaac.
 */
@Composable
fun HudOverlay(
    snapshot: MetricsSnapshot,
    prefs: HudPreferences,
    modifier: Modifier = Modifier,
    history: MetricHistory? = null,
) {
    val shape = RoundedCornerShape(if (prefs.compact) 16.dp else 20.dp)
    val accent = Color(prefs.accentArgb)
    val fill = Charcoal.Night.copy(alpha = prefs.opacity.coerceIn(0.35f, 1f))
    val rows = HudRows.rows(snapshot, prefs)
    Column(
        modifier = modifier
            .width(prefs.widthDp.coerceIn(140, 420).dp)
            .clip(shape)
            .background(fill, shape)
            .background(glassBrush(), shape)
            .border(1.dp, Brush.linearGradient(listOf(accent.copy(alpha = 0.85f), accent.copy(alpha = 0.25f))), shape)
            .animateContentSize()
            .padding(horizontal = 12.dp, vertical = if (prefs.compact) 8.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (prefs.compact) 2.dp else 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(6.dp).background(accent, CircleShape))
            Text("TWELVE FIFTHS", style = BoosterTypography.labelSmall.copy(fontSize = 9.sp), color = accent)
        }
        if (rows.isEmpty()) {
            Text("No stats selected", style = BoosterTypography.bodyMedium, color = Charcoal.TextDim)
        }
        rows.forEach { row -> HudRowView(row, accent, prefs.compact) }
        if (!prefs.compact && history != null && prefs.showRam && history.hasAny(Metric.RAM)) {
            val ram = history.series(Metric.RAM)
            LineGraph(
                series = listOf(GraphSeries(ram, accent)),
                range = GraphMath.range(ram, floor = 0f, ceiling = 100f),
                capacity = history.capacity,
                height = 28.dp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun HudRowView(row: HudRow, accent: Color, compact: Boolean) {
    val valueColor = when (row.display) {
        is MetricDisplay.Value -> Charcoal.Text
        is MetricDisplay.NeedsPermission -> Charcoal.NeonPurple
        else -> Charcoal.TextDim
    }
    val valueStyle = when (row.display) {
        is MetricDisplay.Value -> BoosterTypography.labelLarge.copy(fontSize = if (compact) 12.sp else 13.sp)
        else -> BoosterTypography.labelSmall.copy(letterSpacing = 0.sp)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            row.label,
            style = BoosterTypography.labelSmall.copy(fontSize = 10.sp),
            color = accent.copy(alpha = 0.85f),
            modifier = Modifier.width(64.dp),
            maxLines = 1,
        )
        Text(
            row.text,
            style = valueStyle,
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
