package com.twelvefifths.booster.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetricsSnapshotTest {
    @Test
    fun emptyMarksEveryMetricUnsupportedAndNull() {
        val snap = MetricsSnapshot.empty(timestampMs = 1L)
        assertEquals(1L, snap.timestampMs)
        assertNull(snap.fps)
        assertEquals(MetricSupport.UNSUPPORTED, snap.fpsSupport)
        assertNull(snap.cpuTempC)
        assertEquals(MetricSupport.UNSUPPORTED, snap.cpuTempSupport)
        assertNull(snap.gpuTempC)
        assertEquals(MetricSupport.UNSUPPORTED, snap.gpuTempSupport)
        assertNull(snap.ramUsedBytes)
        assertEquals(MetricSupport.UNSUPPORTED, snap.ramSupport)
        assertNull(snap.cpuUsage)
        assertEquals(MetricSupport.UNSUPPORTED, snap.cpuUsageSupport)
        assertNull(snap.gpuUsage)
        assertEquals(MetricSupport.UNSUPPORTED, snap.gpuUsageSupport)
        assertNull(snap.pingMs)
        assertEquals(MetricSupport.UNSUPPORTED, snap.pingSupport)
        assertNull(snap.vramUsedBytes)
        assertEquals(MetricSupport.UNSUPPORTED, snap.vramSupport)
    }
}
