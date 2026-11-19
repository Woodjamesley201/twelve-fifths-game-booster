package com.twelvefifths.booster.ui

import com.twelvefifths.booster.data.HudPreferences
import com.twelvefifths.booster.data.SessionRecord
import com.twelvefifths.booster.metrics.MetricSupport
import com.twelvefifths.booster.metrics.MetricsSnapshot
import com.twelvefifths.booster.optimize.OptimizationProfile
import com.twelvefifths.booster.ui.format.Metric
import com.twelvefifths.booster.ui.format.MetricFormat
import com.twelvefifths.booster.ui.format.OptimizePlan
import com.twelvefifths.booster.ui.format.SessionCompare
import com.twelvefifths.booster.ui.hud.HudRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAndHudTest {

    private val mb = 1024L * 1024L

    private fun snap(usedMb: Long, temp: Float?) = MetricsSnapshot.empty().copy(
        ramUsedBytes = usedMb * mb,
        ramTotalBytes = 4096 * mb,
        ramPercent = usedMb / 40.96f,
        ramSupport = MetricSupport.SUPPORTED,
        cpuTempC = temp,
        cpuTempSupport = if (temp == null) MetricSupport.UNSUPPORTED else MetricSupport.SUPPORTED,
    )

    @Test
    fun ramDeltaIsRealAndUnsupportedRowsAreSkipped() {
        val record = SessionRecord("id", "game", 0L, 1_000L, snap(3000, null), snap(2688, null), listOf("a"))
        val rows = SessionCompare.rows(record)
        val ram = rows.single { it.metric == Metric.RAM }
        assertEquals("−312 MB", ram.delta)
        assertEquals(true, ram.improved)
        // CPU temp unsupported on both ends -> no row; FPS unsupported -> no row.
        assertTrue(rows.none { it.metric == Metric.CPU_TEMP || it.metric == Metric.FPS })
    }

    @Test
    fun missingAfterSnapshotHasNoDelta() {
        val row = SessionCompare.row(Metric.RAM, snap(3000, 40f), null)!!
        assertEquals("Not captured", row.after)
        assertNull(row.delta)
        assertNull(row.improved)
    }

    @Test
    fun hotterIsWorse() {
        val row = SessionCompare.row(Metric.CPU_TEMP, snap(1, 40f), snap(1, 44f))!!
        assertEquals("+4 °C", row.delta)
        assertEquals(false, row.improved)
    }

    @Test
    fun compactHudCapsRowsAndUsesHonestLabels() {
        val prefs = HudPreferences(compact = true)
        val rows = HudRows.rows(MetricsSnapshot.empty(), prefs)
        assertEquals(HudRows.COMPACT_MAX_ROWS, rows.size)
        assertTrue(rows.all { it.text == MetricFormat.UNSUPPORTED_LABEL })
        val expanded = HudRows.rows(MetricsSnapshot.empty(), prefs.copy(compact = false))
        assertEquals(8, expanded.size)
        assertEquals("PING TCP", expanded.single { it.metric == Metric.PING }.label)
    }

    @Test
    fun toggleStatRoundTrips() {
        var p = HudPreferences()
        for (m in Metric.entries) {
            p = HudRows.withShown(p, m, false)
            assertFalse(HudRows.isShown(p, m))
        }
        assertTrue(HudRows.enabled(p).isEmpty())
    }

    @Test
    fun cornerPresetsStayOnScreen() {
        val (x, y) = HudRows.cornerPosition(HudRows.Corner.BOTTOM_END, 1340, 800, 300, 200, 20)
        assertEquals(1020, x)
        assertEquals(580, y)
        val (x2, y2) = HudRows.cornerPosition(HudRows.Corner.TOP_END, 200, 100, 300, 200, 20)
        assertEquals(0, x2)
        assertEquals(20, y2)
    }

    @Test
    fun planOnlyListsSelectedClosableAppsAndHonestDnd() {
        val profile = OptimizationProfile(
            gamePackage = "game",
            closeBackgroundPackages = setOf("a", "game", "system.not.closable"),
            enableDoNotDisturb = true,
        )
        val plan = OptimizePlan.build(profile, closablePackages = setOf("a", "b", "game"), dndGranted = false)
        val close = plan.single { it.title.startsWith("Close") }
        assertEquals(OptimizePlan.Kind.DO, close.kind)
        assertTrue(close.title.contains("1 background app"))
        assertTrue(plan.any { it.kind == OptimizePlan.Kind.SKIP && it.title.contains("Do Not Disturb") })
        assertTrue(plan.none { it.title.contains("battery", ignoreCase = true) })
    }
}
