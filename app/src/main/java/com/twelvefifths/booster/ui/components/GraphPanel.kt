package com.twelvefifths.booster.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twelvefifths.booster.ui.format.MetricDisplay
import com.twelvefifths.booster.ui.format.PermissionKind
import com.twelvefifths.booster.ui.graph.GraphRange
import com.twelvefifths.booster.ui.theme.BoosterTypography
import com.twelvefifths.booster.ui.theme.Charcoal
import com.twelvefifths.booster.ui.theme.GlassPanel

/**
 * Glass card with a title, the current value (or support state), a legend and
 * a live [LineGraph]. When nothing in the panel is supported it shows the
 * support state and reason instead of an empty chart.
 *
 * Owner: Ui isaac.
 */
@Composable
fun GraphPanel(
    title: String,
    current: MetricDisplay,
    series: List<GraphSeries>,
    range: GraphRange?,
    capacity: Int,
    onGrant: (PermissionKind) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Charcoal.NeonBlue,
    unsupportedReason: String? = null,
    rangeLabel: ((GraphRange) -> String)? = null,
    graphHeight: Dp = 110.dp,
) {
    GlassPanel(modifier = modifier, accent = accent) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title.uppercase(), style = BoosterTypography.labelSmall, color = Charcoal.TextMuted)
            Spacer(Modifier.height(6.dp))
            MetricValue(current, onGrant)
            when (current) {
                MetricDisplay.Unsupported -> {
                    if (unsupportedReason != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(unsupportedReason, style = BoosterTypography.bodyMedium, color = Charcoal.TextDim)
                    }
                }
                is MetricDisplay.NeedsPermission -> Unit
                else -> {
                    if (series.size > 1) {
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            series.filter { it.label.isNotEmpty() }.forEach { s -> Legend(s.label, s.color) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Box {
                        LineGraph(series = series, range = range, capacity = capacity, height = graphHeight)
                        if (range != null && rangeLabel != null) {
                            Text(
                                rangeLabel(range),
                                style = BoosterTypography.labelSmall,
                                color = Charcoal.TextDim,
                                modifier = Modifier.align(Alignment.TopEnd),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = BoosterTypography.labelSmall, color = Charcoal.TextMuted)
    }
}
