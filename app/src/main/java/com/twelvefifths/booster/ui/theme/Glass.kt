package com.twelvefifths.booster.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Translucent "glass" panel built from a dim fill, a pale top veil, and a
 * thin rim. Intentionally avoids [androidx.compose.ui.graphics.RenderEffect]
 * and any blur APIs so the first draw stays cheap and stable on Samsung
 * Android 16 / Mali.
 *
 * Owner: Ui isaac.
 */
val GlassCorner = 22.dp

fun glassBrush(): Brush = Brush.verticalGradient(
    colors = listOf(
        Color(0x33FFFFFF),
        Color(0x14202634),
        Color(0x2212141C),
    ),
)

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    corner: Dp = GlassCorner,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Charcoal.PanelFill, shape)
            .background(glassBrush(), shape)
            .border(1.dp, Charcoal.Line, shape),
        content = content,
    )
}
