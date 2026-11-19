package com.twelvefifths.booster.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Translucent "glass" panel built from a dim fill, a pale top veil, an
 * optional accent tint, and a thin gradient rim. Intentionally avoids
 * [androidx.compose.ui.graphics.RenderEffect], blur, and radial shader stacks
 * so the first draw stays cheap and stable on Samsung Android 16 / Mali.
 * Every brush here is a two/three-stop linear gradient.
 *
 * Owner: Ui isaac.
 */
val GlassCorner = 22.dp

fun glassBrush(): Brush = Brush.verticalGradient(
    colors = listOf(
        Color(0x26FFFFFF),
        Color(0x0F202634),
        Color(0x1A12141C),
    ),
)

/** Rim: brighter on the top-left edge, fading toward the bottom-right. */
fun glassRim(accent: Color? = null): Brush = Brush.linearGradient(
    colors = listOf(
        Color(0x40FFFFFF),
        (accent ?: Color.White).copy(alpha = if (accent != null) 0.28f else 0.08f),
        Color(0x0DFFFFFF),
    ),
    start = Offset.Zero,
    end = Offset.Infinite,
)

/** Low-alpha diagonal accent tint (linear, never radial). */
fun accentVeil(accent: Color): Brush = Brush.linearGradient(
    colors = listOf(accent.copy(alpha = 0.12f), Color.Transparent, Color.Transparent),
    start = Offset.Zero,
    end = Offset.Infinite,
)

/** App backdrop: flat charcoal with a faint blue→purple diagonal wash. */
fun appBackdrop(): Brush = Brush.linearGradient(
    colors = listOf(Color(0xFF0D1020), Charcoal.Night, Color(0xFF120D1C)),
    start = Offset.Zero,
    end = Offset.Infinite,
)

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    corner: Dp = GlassCorner,
    accent: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    val rim = remember(accent) { glassRim(accent) }
    val veil = remember(accent) { accent?.let { accentVeil(it) } }
    var m = modifier
        .clip(shape)
        .background(Charcoal.PanelFill, shape)
        .background(glassBrush(), shape)
    if (veil != null) m = m.background(veil, shape)
    Box(
        modifier = m.border(1.dp, rim, shape),
        content = content,
    )
}
