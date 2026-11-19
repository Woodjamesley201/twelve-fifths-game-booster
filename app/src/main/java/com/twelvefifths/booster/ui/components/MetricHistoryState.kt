package com.twelvefifths.booster.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.twelvefifths.booster.metrics.MetricsSnapshot
import com.twelvefifths.booster.ui.graph.MetricHistory

/**
 * Accumulates real snapshots into a rolling [MetricHistory]. Hoist this above
 * the tab switch so graphs keep their history when the user changes screens.
 * The empty placeholder snapshot is never recorded.
 *
 * Owner: Ui isaac.
 */
@Composable
fun rememberMetricHistory(
    snapshot: MetricsSnapshot,
    capacity: Int = MetricHistory.DEFAULT_CAPACITY,
): MetricHistory {
    val state: MutableState<MetricHistory> = remember(capacity) { mutableStateOf(MetricHistory.empty(capacity)) }
    LaunchedEffect(snapshot) {
        if (!MetricHistory.isPlaceholder(snapshot)) {
            state.value = state.value.append(snapshot)
        }
    }
    return state.value
}
