package com.twelvefifths.booster.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.twelvefifths.booster.metrics.MetricSupport
import com.twelvefifths.booster.ui.theme.BoosterTypography
import com.twelvefifths.booster.ui.theme.Charcoal
import com.twelvefifths.booster.ui.theme.GlassPanel

/**
 * Placeholder metric tile. Shows the real value or an honest support label.
 *
 * Owner: Ui isaac.
 */
@Composable
fun MetricCard(
    title: String,
    valueText: String?,
    support: MetricSupport,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    GlassPanel(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(title.uppercase(), style = BoosterTypography.labelSmall, color = Charcoal.TextMuted)
            SupportLabel(support = support, valueText = valueText)
            if (caption != null) {
                Text(caption, style = BoosterTypography.bodyMedium, color = Charcoal.TextDim)
            }
        }
    }
}
