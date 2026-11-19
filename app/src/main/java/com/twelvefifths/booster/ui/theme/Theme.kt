package com.twelvefifths.booster.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Dark-only Material theme. Glass is painted with gradients and veils in
 * [com.twelvefifths.booster.ui.theme.GlassPanel] — never RenderEffect blur,
 * which can crash Mali drivers on Android 16.
 *
 * Owner: Ui isaac.
 */
private val BoosterDarkColors = darkColorScheme(
    primary = Charcoal.NeonBlue,
    onPrimary = Color.White,
    secondary = Charcoal.NeonPurple,
    onSecondary = Color.White,
    background = Charcoal.Night,
    onBackground = Charcoal.Text,
    surface = Charcoal.NightRaised,
    onSurface = Charcoal.Text,
    surfaceVariant = Charcoal.NightRaised,
    onSurfaceVariant = Charcoal.TextMuted,
    outline = Charcoal.Line,
    error = Charcoal.Danger,
)

@Composable
fun TwelveFifthsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BoosterDarkColors,
        typography = BoosterTypography,
        content = content,
    )
}
