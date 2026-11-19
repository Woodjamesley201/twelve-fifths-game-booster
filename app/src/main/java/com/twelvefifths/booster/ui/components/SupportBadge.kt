package com.twelvefifths.booster.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.twelvefifths.booster.metrics.MetricSupport
import com.twelvefifths.booster.ui.theme.BoosterTypography
import com.twelvefifths.booster.ui.theme.Charcoal

/**
 * Renders a metric's support state so the UI never invents a number.
 *
 * Owner: Ui isaac.
 */
@Composable
fun SupportLabel(
    support: MetricSupport,
    valueText: String?,
    modifier: Modifier = Modifier,
) {
    val (text, color) = when {
        support == MetricSupport.SUPPORTED && valueText != null -> valueText to Charcoal.Text
        support == MetricSupport.SUPPORTED -> "—" to Charcoal.TextDim
        support == MetricSupport.NEEDS_PERMISSION -> "Needs permission" to Charcoal.NeonPurple
        else -> "Unsupported" to Charcoal.TextDim
    }
    Text(
        text = text,
        modifier = modifier,
        style = BoosterTypography.titleMedium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
