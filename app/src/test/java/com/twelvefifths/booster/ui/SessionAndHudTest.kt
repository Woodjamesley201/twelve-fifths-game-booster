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
import com.twelvefifths.booster.hud.HudRows
import com.twelvefifths.booster.hud.HudStartResult
import com.twelvefifths.booster.ui.format.HudStartText
import com.twelvefifths.booster.ui.hud.HudPrefsEdit
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
    fun previewUsesEngineRowsWithHonestLabels() {
        val prefs = HudPreferences(compact = false)
        val rows = HudRows.build(MetricsSnapshot.empty(), prefs)
        assertEquals(8, rows.size)
        assertTrue(rows.all { HudPrefsEdit.previewValue(it) == MetricFormat.UNSUPPORTED_LABEL })
        val ping = rows.single { it.key == com.twelvefifths.booster.hud.HudRow.Key.PING }
        assertEquals(HudRows.PING_LABEL, HudPrefsEdit.previewLabel(ping))
        // Compact keeps only live values, so an all-unsupported snapshot shows nothing.
        assertTrue(HudRows.build(MetricsSnapshot.empty(), prefs.copy(compact = true)).isEmpty())
    }

    @Test
    fun previewPingMatchesOverlay() {
        val s = MetricsSnapshot.empty().copy(pingMs = 31, pingSupport = MetricSupport.SUPPORTED)
        val ping = HudRows.build(s, HudPreferences(compact = true)).single()
        assertEquals("TCP ping", HudPrefsEdit.previewLabel(ping))
        assertEquals("31 ms", HudPrefsEdit.previewValue(ping))
    }

    @Test
    fun cornerPresetUsesMeasuredBoundsWhenOverlayIsShown() {
        val bounds = com.twelvefifths.booster.hud.HudWindowBounds(
            x = 10, y = 10, widthPx = 330, heightPx = 142, screenWidthPx = 1340, screenHeightPx = 800, density = 1.5f,
        )
        val exact = HudPrefsEdit.cornerPosition(HudPrefsEdit.Corner.BOTTOM_END, bounds, { error("no fallback") }, 24)
        assertEquals(1340 - 330 - 24 to 800 - 142 - 24, exact)
        val estimated = HudPrefsEdit.cornerPosition(HudPrefsEdit.Corner.BOTTOM_END, null, { 1 to 2 }, 24)
        assertEquals(1 to 2, estimated)
    }

    @Test
    fun addGamePickerListsGamesFirstAndHidesSystemApps() {
        val apps = listOf(
            com.twelvefifths.booster.optimize.LaunchableApp("com.a.notes", "Notes", isGame = false, isSystem = false),
            com.twelvefifths.booster.optimize.LaunchableApp("com.sys.clock", "Clock", isGame = false, isSystem = true),
            com.twelvefifths.booster.optimize.LaunchableApp("com.sys.game", "Solitaire", isGame = true, isSystem = true),
            com.twelvefifths.booster.optimize.LaunchableApp("com.z.racer", "Racer", isGame = true, isSystem = false),
            com.twelvefifths.booster.optimize.LaunchableApp("com.in.lib", "Already", isGame = true, isSystem = false),
        )
        val shown = com.twelvefifths.booster.ui.format.AddGamePicker.filter(apps, "", showSystem = false, excluded = setOf("com.in.lib"))
        assertEquals(listOf("Racer", "Solitaire", "Notes"), shown.map { it.label })
        val all = com.twelvefifths.booster.ui.format.AddGamePicker.filter(apps, "clo", showSystem = true, excluded = emptySet())
        assertEquals(listOf("Clock"), all.map { it.label })
    }

    @Test
    fun previewNeedsPermissionCarriesEngineRouting() {
        val s = MetricsSnapshot.empty().copy(
            fpsSupport = MetricSupport.NEEDS_PERMISSION,
            requiredPermissions = mapOf(com.twelvefifths.booster.metrics.MetricKey.FPS to com.twelvefifths.booster.optimize.BoosterPermission.USAGE_ACCESS),
        )
        val fps = HudRows.build(s, HudPreferences(compact = false)).first()
        assertEquals(HudRows.NEEDS_PERMISSION_TEXT, HudPrefsEdit.previewValue(fps))
        assertEquals(com.twelvefifths.booster.optimize.BoosterPermission.USAGE_ACCESS, fps.requiredPermission)
    }

    @Test
    fun toggleStatRoundTrips() {
        var p = HudPreferences()
        for (m in Metric.hud) {
            p = HudPrefsEdit.withShown(p, m, false)
            assertFalse(HudPrefsEdit.isShown(p, m))
        }
        assertTrue(HudPrefsEdit.enabled(p).isEmpty())
    }

    @Test
    fun cornerPresetsStayOnScreen() {
        val (x, y) = HudPrefsEdit.cornerPosition(HudPrefsEdit.Corner.BOTTOM_END, 1340, 800, 300, 200, 20)
        assertEquals(1020, x)
        assertEquals(580, y)
        val (x2, y2) = HudPrefsEdit.cornerPosition(HudPrefsEdit.Corner.TOP_END, 200, 100, 300, 200, 20)
        assertEquals(0, x2)
        assertEquals(20, y2)
    }

    @Test
    fun hudStartResultsHaveMessages() {
        assertNull(HudStartText.message(HudStartResult.STARTED))
        assertNull(HudStartText.message(null))
        assertTrue(HudStartText.message(HudStartResult.NEEDS_OVERLAY_PERMISSION)!!.contains("Display over other apps"))
        assertTrue(HudStartText.message(HudStartResult.NOT_ALLOWED_FROM_BACKGROUND)!!.contains("background"))
    }

    @Test
    fun planSaysClosingIsUnavailableOnAndroid14Plus() {
        val profile = OptimizationProfile(gamePackage = "game", closeBackgroundPackages = setOf("a"))
        val plan = OptimizePlan.build(profile, setOf("a"), dndGranted = true, canKillOtherApps = false)
        assertTrue(plan.none { it.kind == OptimizePlan.Kind.DO && it.title.startsWith("Close") })
        assertTrue(plan.any { it.kind == OptimizePlan.Kind.SKIP && it.title.contains("isn't available") })
    }

    @Test
    fun launchOnStartIsSkippedWhenSettingsOpen() {
        val profile = OptimizationProfile(gamePackage = "g", launchGameOnStart = true, suggestBatterySaverSettings = true, autoEndWhenGameLeaves = true)
        val plan = OptimizePlan.build(profile, emptySet(), dndGranted = false, usageGranted = false)
        assertTrue(plan.any { it.kind == OptimizePlan.Kind.SKIP && it.title.contains("auto-launch") })
        assertTrue(plan.any { it.kind == OptimizePlan.Kind.SKIP && it.title.contains("auto-restore") })
    }

    @Test
    fun comparisonPrefersAfterOptimizeSample() {
        val record = SessionRecord(
            "id", "game", 0L, 9_000L,
            before = snap(3000, null), after = snap(3500, null), closedPackages = emptyList(),
            afterOptimize = snap(2900, null),
        )
        assertEquals("−100 MB", SessionCompare.rows(record).single { it.metric == Metric.RAM }.delta)
        assertEquals("AFTER BOOST", SessionCompare.afterLabel(record))
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
