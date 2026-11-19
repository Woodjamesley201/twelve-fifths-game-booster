package com.twelvefifths.booster.hud

import com.twelvefifths.booster.data.HudPreferences
import com.twelvefifths.booster.metrics.MetricKey
import com.twelvefifths.booster.metrics.MetricSupport
import com.twelvefifths.booster.metrics.requiredPermission
import com.twelvefifths.booster.optimize.BoosterPermission
import com.twelvefifths.booster.metrics.MetricsSnapshot
import com.twelvefifths.booster.metrics.PingStatus
import com.twelvefifths.booster.metrics.ThermalStatusLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HudRowsTest {

    private val gb = 1024L * 1024 * 1024
    private val snap = MetricsSnapshot.empty(1L).copy(
        ramUsedBytes = 2 * gb,
        ramTotalBytes = 4 * gb,
        ramPercent = 50f,
        ramSupport = MetricSupport.SUPPORTED,
        pingMs = 31L,
        pingSupport = MetricSupport.SUPPORTED,
        pingStatus = PingStatus.OK,
        batteryTempC = 33.4f,
        batteryTempSupport = MetricSupport.SUPPORTED,
        thermalStatus = ThermalStatusLevel.LIGHT,
        thermalStatusSupport = MetricSupport.SUPPORTED,
    )

    @Test
    fun expandedShowsEveryEnabledRowAndNeverNumbersForUnsupported() {
        val rows = HudRows.build(snap, HudPreferences(compact = false))
        assertEquals(8, rows.size)
        val fps = rows.first { it.key == HudRow.Key.FPS }
        assertNull(fps.value)
        assertEquals("Not available on Android", fps.display)
        assertEquals(HudRows.UNSUPPORTED_TEXT, fps.display)
        assertNull(rows.first { it.key == HudRow.Key.GPU_USAGE }.value)
        assertNull(rows.first { it.key == HudRow.Key.VRAM }.value)
        assertEquals("2.0 GB / 4.0 GB  (50%)", rows.first { it.key == HudRow.Key.RAM }.value)
        val ping = rows.first { it.key == HudRow.Key.PING }
        assertEquals("31 ms", ping.value)
        assertEquals("TCP ping", ping.label)
    }

    @Test
    fun temperatureRowNamesBatteryWhenNoCpuZone() {
        val row = HudRows.build(snap, HudPreferences(compact = false)).first { it.key == HudRow.Key.TEMP }
        assertEquals("BATT °C", row.label)
        assertEquals("33", row.value)
    }

    @Test
    fun temperatureRowShowsThermalLevelNotDegreesAsLastResort() {
        val s = snap.copy(batteryTempC = null, batteryTempSupport = MetricSupport.UNSUPPORTED)
        val row = HudRows.build(s, HudPreferences(compact = false)).first { it.key == HudRow.Key.TEMP }
        assertEquals("THERMAL", row.label)
        assertEquals("light", row.value)
    }

    @Test
    fun compactKeepsOnlyRealValues() {
        val rows = HudRows.build(snap, HudPreferences(compact = true))
        assertTrue(rows.all { it.hasRealValue })
        assertEquals(listOf(HudRow.Key.TEMP, HudRow.Key.RAM, HudRow.Key.PING), rows.map { it.key })
    }

    @Test
    fun pingFailureShowsStatusNotNumber() {
        val s = snap.copy(pingMs = null, pingStatus = PingStatus.TIMEOUT)
        val row = HudRows.build(s, HudPreferences(compact = false)).first { it.key == HudRow.Key.PING }
        assertNull(row.value)
        assertEquals("timeout", row.display)
        assertEquals("TCP ping", row.label)
    }

    @Test
    fun needsPermissionRowsCarryTheExactPermission() {
        val s = MetricsSnapshot.empty(1L).copy(
            cpuUsageSupport = MetricSupport.NEEDS_PERMISSION,
            pingSupport = MetricSupport.NEEDS_PERMISSION,
            requiredPermissions = mapOf(
                MetricKey.CPU_USAGE to BoosterPermission.USAGE_ACCESS,
                MetricKey.PING to BoosterPermission.NOTIFICATIONS,
                MetricKey.FPS to BoosterPermission.OVERLAY, // ignored: FPS is UNSUPPORTED
            ),
        )
        val rows = HudRows.build(s, HudPreferences(compact = false))
        val cpu = rows.first { it.key == HudRow.Key.CPU_USAGE }
        assertEquals(BoosterPermission.USAGE_ACCESS, cpu.requiredPermission)
        assertEquals("Needs permission", cpu.display)
        assertEquals(BoosterPermission.NOTIFICATIONS, rows.first { it.key == HudRow.Key.PING }.requiredPermission)
        assertNull(rows.first { it.key == HudRow.Key.FPS }.requiredPermission)
        assertEquals(BoosterPermission.USAGE_ACCESS, s.requiredPermission(MetricKey.CPU_USAGE))
        assertNull(s.requiredPermission(MetricKey.FPS))
        assertNull("no guessing when the engine named nothing", s.requiredPermission(MetricKey.RAM))
    }

    @Test
    fun disabledRowsAreHidden() {
        val rows = HudRows.build(snap, HudPreferences(compact = false, showFps = false, showVram = false))
        assertTrue(rows.none { it.key == HudRow.Key.FPS || it.key == HudRow.Key.VRAM })
    }
}
