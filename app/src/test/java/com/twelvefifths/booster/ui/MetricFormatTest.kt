package com.twelvefifths.booster.ui

import com.twelvefifths.booster.hud.HudRows
import com.twelvefifths.booster.metrics.MetricKey
import com.twelvefifths.booster.metrics.MetricSupport
import com.twelvefifths.booster.metrics.MetricsSnapshot
import com.twelvefifths.booster.ui.format.Metric
import com.twelvefifths.booster.ui.format.MetricDisplay
import com.twelvefifths.booster.ui.format.MetricFormat
import com.twelvefifths.booster.optimize.BoosterPermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricFormatTest {

    private val gb = 1024L * 1024L * 1024L

    @Test
    fun unsupportedNeverShowsANumber() {
        val s = MetricsSnapshot.empty().copy(fps = 60f, fpsSupport = MetricSupport.UNSUPPORTED)
        assertEquals(MetricDisplay.Unsupported, MetricFormat.display(Metric.FPS, s))
        assertEquals("Not available on Android", MetricFormat.text(MetricFormat.display(Metric.FPS, s)))
        assertNull(MetricFormat.numeric(Metric.FPS, s))
    }

    @Test
    fun needsPermissionUsesTheEngineNamedPermission() {
        val s = MetricsSnapshot.empty().copy(
            cpuUsage = 40f,
            cpuUsageSupport = MetricSupport.NEEDS_PERMISSION,
            requiredPermissions = mapOf(MetricKey.CPU_USAGE to BoosterPermission.USAGE_ACCESS),
        )
        val d = MetricFormat.display(Metric.CPU_USAGE, s)
        assertEquals(MetricDisplay.NeedsPermission(BoosterPermission.USAGE_ACCESS), d)
        assertEquals(HudRows.NEEDS_PERMISSION_TEXT, MetricFormat.text(d))
        assertNull(MetricFormat.numeric(Metric.CPU_USAGE, s))
    }

    @Test
    fun needsPermissionWithoutANamedPermissionDoesNotGuess() {
        val s = MetricsSnapshot.empty().copy(gpuUsageSupport = MetricSupport.NEEDS_PERMISSION)
        assertEquals(MetricDisplay.NeedsPermission(null), MetricFormat.display(Metric.GPU_USAGE, s))
    }

    @Test
    fun stateWordingMatchesTheOverlay() {
        assertEquals(HudRows.UNSUPPORTED_TEXT, MetricFormat.UNSUPPORTED_LABEL)
        assertEquals(HudRows.NEEDS_PERMISSION_TEXT, MetricFormat.NEEDS_PERMISSION_LABEL)
        assertEquals(HudRows.WAITING_TEXT, MetricFormat.WAITING_LABEL)
        assertEquals(HudRows.PING_LABEL, Metric.PING.shortLabel)
    }

    @Test
    fun supportedWithoutSampleIsWaitingNotZero() {
        val s = MetricsSnapshot.empty().copy(cpuTempSupport = MetricSupport.SUPPORTED, cpuTempC = null)
        assertEquals(MetricDisplay.Waiting, MetricFormat.display(Metric.CPU_TEMP, s))
        assertEquals("—", MetricFormat.text(MetricDisplay.Waiting))
    }

    @Test
    fun supportedValuesFormat() {
        val s = MetricsSnapshot.empty().copy(
            ramUsedBytes = 2 * gb,
            ramTotalBytes = 4 * gb,
            ramPercent = null,
            ramSupport = MetricSupport.SUPPORTED,
            pingMs = 23,
            pingSupport = MetricSupport.SUPPORTED,
            cpuTempC = 41.6f,
            cpuTempSupport = MetricSupport.SUPPORTED,
        )
        assertEquals(MetricDisplay.Value("2.0 GB / 4.0 GB"), MetricFormat.display(Metric.RAM, s))
        assertEquals(50f, MetricFormat.numeric(Metric.RAM, s)!!, 0.01f)
        assertEquals("23 ms", MetricFormat.valueText(Metric.PING, s))
        assertEquals("42 °C", MetricFormat.valueText(Metric.CPU_TEMP, s))
    }

    @Test
    fun pingFailureIsAStatusNotANumber() {
        val s = MetricsSnapshot.empty().copy(
            pingSupport = MetricSupport.SUPPORTED,
            pingMs = null,
            pingStatus = com.twelvefifths.booster.metrics.PingStatus.TIMEOUT,
            pingTarget = "1.1.1.1:443",
        )
        assertEquals(MetricDisplay.Status("Timed out"), MetricFormat.display(Metric.PING, s))
        assertTrue(MetricFormat.sourceCaption(Metric.PING, s)!!.contains("TCP connect to 1.1.1.1:443"))
    }

    @Test
    fun thermalStatusIsALevelNotDegrees() {
        val s = MetricsSnapshot.empty().copy(
            thermalStatus = com.twelvefifths.booster.metrics.ThermalStatusLevel.MODERATE,
            thermalStatusSupport = MetricSupport.SUPPORTED,
        )
        assertEquals(MetricDisplay.Value("Moderate"), MetricFormat.thermalStatus(s))
        assertEquals(MetricDisplay.Unsupported, MetricFormat.thermalStatus(MetricsSnapshot.empty()))
        assertEquals(MetricDisplay.Unsupported, MetricFormat.display(Metric.CPU_TEMP, s))
    }

    @Test
    fun bytesAndDurations() {
        assertEquals("512 MB", MetricFormat.bytes(512L * 1024 * 1024))
        assertEquals("1.5 GB", MetricFormat.bytes(1536L * 1024 * 1024))
        assertEquals("45s", MetricFormat.duration(45_000))
        assertEquals("2m 5s", MetricFormat.duration(125_000))
        assertEquals("1h 1m", MetricFormat.duration(3_660_000))
        assertEquals("−312 MB", MetricFormat.signed(-312f, " MB"))
        assertEquals("+2 °C", MetricFormat.signed(2f, " °C"))
    }

    @Test
    fun lastPlayedLabels() {
        val now = 10L * 24 * 3_600_000
        assertEquals("Never played", MetricFormat.lastPlayed(null, now))
        assertEquals("Played just now", MetricFormat.lastPlayed(now - 10_000, now))
        assertEquals("Played 5 min ago", MetricFormat.lastPlayed(now - 5 * 60_000, now))
        assertEquals("Played 3 h ago", MetricFormat.lastPlayed(now - 3 * 3_600_000, now))
        assertEquals("Played yesterday", MetricFormat.lastPlayed(now - 30 * 3_600_000, now))
        assertEquals("Played 4 days ago", MetricFormat.lastPlayed(now - 4 * 24 * 3_600_000, now))
    }

    @Test
    fun combineForMultiMetricPanels() {
        assertEquals(
            MetricDisplay.Unsupported,
            MetricFormat.combine(listOf(MetricDisplay.Unsupported, MetricDisplay.Unsupported), null),
        )
        assertEquals(
            MetricDisplay.Waiting,
            MetricFormat.combine(listOf(MetricDisplay.Unsupported, MetricDisplay.Waiting), null),
        )
        assertEquals(
            MetricDisplay.Value("CPU 40 °C"),
            MetricFormat.combine(listOf(MetricDisplay.Value("40 °C"), MetricDisplay.Unsupported), "CPU 40 °C"),
        )
    }
}
