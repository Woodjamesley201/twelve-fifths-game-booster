package com.twelvefifths.booster.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Filled / outlined / ghost buttons. Plain gradients, no custom ripple nodes.
 *
 * Owner: Ui isaac.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(
                Brush.horizontalGradient(listOf(Charcoal.NeonBlue, Charcoal.NeonPurple)),
                shape,
            )
            .border(1.dp, Color(0x40FFFFFF), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        ButtonContent(text, icon, Color.White)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    accent: Color = Charcoal.Text,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .border(1.dp, if (accent == Charcoal.Text) Charcoal.Line else accent.copy(alpha = 0.6f), shape)
            .background(Charcoal.NightRaised.copy(alpha = 0.6f), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        ButtonContent(text, icon, accent)
    }
}

/** Small pill button used inline in cards (e.g. "Grant permission"). */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Charcoal.NeonPurple,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .clip(shape)
            .background(accent.copy(alpha = 0.16f), shape)
            .border(1.dp, accent.copy(alpha = 0.55f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
            Text(text, style = BoosterTypography.labelLarge, color = accent)
        }
    }
}

/** Segmented choice chip. */
@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    val bg by animateColorAsState(if (selected) Charcoal.NeonBlueFaint else Color.Transparent, label = "chipBg")
    val rim by animateColorAsState(if (selected) Charcoal.NeonBlue else Charcoal.Line, label = "chipRim")
    val fg by animateColorAsState(if (selected) Charcoal.Text else Charcoal.TextMuted, label = "chipFg")
    Box(
        modifier = modifier
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, rim, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        ButtonContent(text, icon, fg, style = BoosterTypography.labelLarge)
    }
}

@Composable
private fun ButtonContent(
    text: String,
    icon: ImageVector?,
    color: Color,
    style: androidx.compose.ui.text.TextStyle = BoosterTypography.titleMedium,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, style = style, color = color, maxLines = 1)
    }
}
