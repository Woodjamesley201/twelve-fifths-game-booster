package com.twelvefifths.booster.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twelvefifths.booster.ui.graph.GraphMath
import com.twelvefifths.booster.ui.graph.GraphRange
import com.twelvefifths.booster.ui.theme.BoosterTypography
import com.twelvefifths.booster.ui.theme.Charcoal

/** One line on a [LineGraph]. Null values are gaps, never interpolated. */
data class GraphSeries(val values: List<Float?>, val color: Color, val label: String = "")

/**
 * Cheap Canvas line graph: one Path per contiguous run, a linear-gradient fill
 * under the primary series, and a short ease-in of the newest segment. The
 * animation only runs when a new sample arrives, so an idle or empty graph
 * costs a single static draw.
 *
 * Owner: Ui isaac.
 */
@Composable
fun LineGraph(
    series: List<GraphSeries>,
    range: GraphRange?,
    capacity: Int,
    modifier: Modifier = Modifier,
    height: Dp = 96.dp,
    emptyText: String = "Waiting for samples…",
) {
    val count = series.maxOfOrNull { it.values.size } ?: 0
    val hasData = range != null && series.any { s -> s.values.any { it != null } }
    val reveal = remember { Animatable(1f) }
    LaunchedEffect(count, hasData) {
        if (hasData && count > 1) {
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(durationMillis = 450, easing = FastOutSlowInEasing))
        }
    }
    Box(modifier.fillMaxWidth().height(height)) {
        if (!hasData) {
            Text(emptyText, style = BoosterTypography.bodyMedium, color = Charcoal.TextDim, modifier = Modifier.align(Alignment.Center))
            return@Box
        }
        val r = range!!
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Grid: three faint horizontal guides.
            for (i in 0..2) {
                val y = h * i / 2f
                drawLine(Charcoal.LineSoft, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            val progress = reveal.value
            series.forEachIndexed { si, s ->
                val n = s.values.size
                GraphMath.segments(s.values).forEach { run ->
                    val path = Path()
                    val fill = Path()
                    run.forEachIndexed { k, (idx, v) ->
                        var x = GraphMath.xFraction(idx, n, capacity) * w
                        var y = h - r.normalize(v) * h
                        // Ease the newest point in from the previous one.
                        if (idx == n - 1 && k > 0 && progress < 1f) {
                            val (pIdx, pv) = run[k - 1]
                            val px = GraphMath.xFraction(pIdx, n, capacity) * w
                            val py = h - r.normalize(pv) * h
                            x = px + (x - px) * progress
                            y = py + (y - py) * progress
                        }
                        if (k == 0) {
                            path.moveTo(x, y)
                            fill.moveTo(x, h)
                            fill.lineTo(x, y)
                        } else {
                            path.lineTo(x, y)
                            fill.lineTo(x, y)
                        }
                        if (k == run.lastIndex) {
                            fill.lineTo(x, h)
                            fill.close()
                            drawCircle(s.color, radius = 3.dp.toPx(), center = Offset(x, y))
                        }
                    }
                    if (si == 0 && run.size > 1) {
                        drawPath(
                            fill,
                            Brush.verticalGradient(listOf(s.color.copy(alpha = 0.28f), Color.Transparent)),
                        )
                    }
                    drawPath(
                        path,
                        s.color,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
        }
    }
}
