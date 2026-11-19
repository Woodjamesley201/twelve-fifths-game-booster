package com.twelvefifths.booster.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.twelvefifths.booster.metrics.MetricSupport
import com.twelvefifths.booster.metrics.MetricsSnapshot
import com.twelvefifths.booster.ui.format.Metric
import com.twelvefifths.booster.ui.format.MetricDisplay
import com.twelvefifths.booster.ui.format.MetricFormat
import com.twelvefifths.booster.ui.format.PermissionKind
import com.twelvefifths.booster.ui.theme.BoosterTypography
import com.twelvefifths.booster.ui.theme.Charcoal
import com.twelvefifths.booster.ui.theme.GlassPanel

/**
 * Metric tile: title, real value or honest support state, optional caption and
 * a slot underneath (sparkline / bar).
 *
 * Owner: Ui isaac.
 */
@Composable
fun MetricTile(
    metric: Metric,
    snapshot: MetricsSnapshot,
    onGrant: (PermissionKind) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Charcoal.NeonBlue,
    caption: String? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    val display = MetricFormat.display(metric, snapshot)
    GlassPanel(modifier = modifier, accent = accent) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(metric.title.uppercase(), style = BoosterTypography.labelSmall, color = Charcoal.TextMuted, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            MetricValue(display, onGrant, valueColor = Charcoal.Text)
            val note = when (display) {
                MetricDisplay.Unsupported -> MetricFormat.unsupportedReason(metric)
                else -> caption
            }
            if (note != null) {
                Spacer(Modifier.height(4.dp))
                Text(note, style = BoosterTypography.bodyMedium, color = Charcoal.TextDim, maxLines = 2)
            }
            if (footer != null && display !is MetricDisplay.Unsupported) {
                Spacer(Modifier.height(10.dp))
                footer()
            }
        }
    }
}

/** Back-compat simple card from the scaffold. */
@Composable
fun MetricCard(
    title: String,
    valueText: String?,
    support: MetricSupport,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    GlassPanel(modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title.uppercase(), style = BoosterTypography.labelSmall, color = Charcoal.TextMuted)
            SupportLabel(support = support, valueText = valueText)
            if (caption != null) {
                Text(caption, style = BoosterTypography.bodyMedium, color = Charcoal.TextDim)
            }
        }
    }
}
