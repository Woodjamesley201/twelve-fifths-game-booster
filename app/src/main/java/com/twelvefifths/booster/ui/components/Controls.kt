package com.twelvefifths.booster.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.twelvefifths.booster.ui.theme.BoosterTypography
import com.twelvefifths.booster.ui.theme.Charcoal

/**
 * Shared form controls in the booster style.
 *
 * Owner: Ui isaac.
 */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title.uppercase(), style = BoosterTypography.labelSmall, color = Charcoal.NeonBlue)
            if (subtitle != null) Text(subtitle, style = BoosterTypography.bodyMedium, color = Charcoal.TextMuted)
        }
        trailing?.invoke()
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = BoosterTypography.displaySmall)
            Text(subtitle, style = BoosterTypography.bodyMedium, color = Charcoal.TextMuted)
        }
        trailing?.invoke()
    }
}

@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    trailingExtra: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = BoosterTypography.bodyLarge, color = if (enabled) Charcoal.Text else Charcoal.TextDim)
            if (subtitle != null) Text(subtitle, style = BoosterTypography.bodyMedium, color = Charcoal.TextDim)
        }
        trailingExtra?.invoke()
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Charcoal.Text,
                checkedTrackColor = Charcoal.NeonBlue,
                uncheckedThumbColor = Charcoal.TextMuted,
                uncheckedTrackColor = Charcoal.NightHigh,
                uncheckedBorderColor = Charcoal.Line,
            ),
        )
    }
}

@Composable
fun CheckRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Charcoal.NeonBlue,
                uncheckedColor = Charcoal.TextDim,
                checkmarkColor = Charcoal.Text,
            ),
        )
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, style = BoosterTypography.bodyLarge, maxLines = 1)
            if (subtitle != null) Text(subtitle, style = BoosterTypography.bodyMedium, color = Charcoal.TextDim, maxLines = 1)
        }
    }
}

@Composable
fun LabeledSlider(
    title: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(title, style = BoosterTypography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueLabel, style = BoosterTypography.labelLarge, color = Charcoal.NeonBlue)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            colors = SliderDefaults.colors(
                thumbColor = Charcoal.Text,
                activeTrackColor = Charcoal.NeonBlue,
                inactiveTrackColor = Charcoal.NightHigh,
            ),
        )
    }
}

/** Horizontal usage bar (e.g. RAM %). Animated via the caller's animated value. */
@Composable
fun UsageBar(fraction: Float, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.fillMaxWidth().padding(vertical = 2.dp).height(6.dp)) {
        val r = size.height / 2f
        drawRoundRect(Charcoal.NightHigh, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
        val w = size.width * fraction.coerceIn(0f, 1f)
        if (w > 0f) {
            drawRoundRect(
                androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Charcoal.NeonBlue, Charcoal.NeonPurple), endX = size.width),
                size = androidx.compose.ui.geometry.Size(w, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            )
        }
    }
}
