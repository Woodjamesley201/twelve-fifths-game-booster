package com.twelvefifths.booster.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.twelvefifths.booster.metrics.MetricSupport
import com.twelvefifths.booster.ui.format.Metric
import com.twelvefifths.booster.ui.format.MetricDisplay
import com.twelvefifths.booster.ui.format.MetricFormat
import com.twelvefifths.booster.ui.format.PermissionKind
import com.twelvefifths.booster.ui.theme.BoosterTypography
import com.twelvefifths.booster.ui.theme.Charcoal
import com.twelvefifths.booster.ui.theme.PillButton

/**
 * Renders a metric's value or its honest support state. Never invents a number:
 * UNSUPPORTED -> "Not available on Android"; NEEDS_PERMISSION -> a Grant
 * permission button routed to the right flow.
 *
 * Owner: Ui isaac.
 */
@Composable
fun MetricValue(
    display: MetricDisplay,
    onGrant: (PermissionKind) -> Unit,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = BoosterTypography.headlineSmall,
    valueColor: Color = Charcoal.Text,
) {
    AnimatedContent(
        targetState = display,
        contentKey = { it::class },
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "metricValue",
        modifier = modifier,
    ) { d ->
        when (d) {
            is MetricDisplay.Value -> Text(
                d.text,
                style = valueStyle,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MetricDisplay.Waiting -> Text(MetricFormat.WAITING_LABEL, style = valueStyle, color = Charcoal.TextDim)
            is MetricDisplay.NeedsPermission -> PillButton(
                text = MetricFormat.GRANT_LABEL,
                onClick = { onGrant(d.permission) },
                icon = Icons.Outlined.Lock,
            )
            MetricDisplay.Unsupported -> Text(
                MetricFormat.UNSUPPORTED_LABEL,
                style = BoosterTypography.titleMedium,
                color = Charcoal.TextDim,
            )
        }
    }
}

/** Back-compat wrapper for the scaffold API. */
@Composable
fun SupportLabel(
    support: MetricSupport,
    valueText: String?,
    modifier: Modifier = Modifier,
    metric: Metric = Metric.RAM,
    onGrant: (PermissionKind) -> Unit = {},
) {
    MetricValue(MetricFormat.display(metric, valueText, support), onGrant, modifier, BoosterTypography.titleMedium)
}

/** Small status pill with a dot, e.g. "Granted" / "HUD on". */
@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.12f), shape)
            .border(1.dp, color.copy(alpha = 0.4f), shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(text, style = BoosterTypography.labelSmall, color = color, maxLines = 1)
    }
}

/** Support-only pill used in lists where there is no room for a value. */
@Composable
fun SupportPill(support: MetricSupport, modifier: Modifier = Modifier) {
    when (support) {
        MetricSupport.SUPPORTED -> StatusPill("Live", Charcoal.Ok, modifier)
        MetricSupport.NEEDS_PERMISSION -> StatusPill("Needs permission", Charcoal.NeonPurple, modifier)
        MetricSupport.UNSUPPORTED -> StatusPill(MetricFormat.UNSUPPORTED_LABEL, Charcoal.TextDim, modifier)
    }
}
