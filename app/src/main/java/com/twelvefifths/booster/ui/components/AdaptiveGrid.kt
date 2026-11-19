package com.twelvefifths.booster.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Simple non-lazy grid for a handful of cards inside a scrolling column.
 * Cells in a row share the tallest cell's height.
 *
 * Owner: Ui isaac.
 */
@Composable
fun <T> AdaptiveGrid(
    items: List<T>,
    columns: Int,
    modifier: Modifier = Modifier,
    spacing: Dp = 14.dp,
    cell: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    val cols = columns.coerceAtLeast(1)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
        items.chunked(cols).forEach { rowItems ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                rowItems.forEach { item -> cell(item, Modifier.weight(1f).fillMaxHeight()) }
                repeat(cols - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Column count for a content width in dp. */
fun gridColumns(widthDp: Float, minCellDp: Float = 260f, max: Int = 3): Int =
    (widthDp / minCellDp).toInt().coerceIn(1, max)
