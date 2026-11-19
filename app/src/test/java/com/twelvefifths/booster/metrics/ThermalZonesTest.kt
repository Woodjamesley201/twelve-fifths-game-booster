package com.twelvefifths.booster.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalZonesTest {

    private class FakeFs(private val zones: Map<String, Pair<String?, String?>>) : ThermalFs {
        val temps = zones.mapValues { it.value.second }.toMutableMap()
        override fun zoneNames(): List<String> = zones.keys.toList()
        override fun read(zone: String, file: String): String? = when (file) {
            "type" -> zones[zone]?.first
            "temp" -> temps[zone]
            else -> null
        }
        override fun pathOf(zone: String, file: String) = "/fake/$zone/$file"
    }

    @Test
    fun parsesMilliDeciAndWholeDegrees() {
        assertEquals(45.5f, ThermalZones.parseCelsius("45500\n")!!, 0.001f)
        assertEquals(45.5f, ThermalZones.parseCelsius("455")!!, 0.001f)
        assertEquals(45f, ThermalZones.parseCelsius("45")!!, 0.001f)
    }

    @Test
    fun rejectsImplausibleAndGarbage() {
        assertNull(ThermalZones.parseCelsius(null))
        assertNull(ThermalZones.parseCelsius(""))
        assertNull(ThermalZones.parseCelsius("abc"))
        assertNull(ThermalZones.parseCelsius("0"))
        assertNull(ThermalZones.parseCelsius("-274000"))
        assertNull(ThermalZones.parseCelsius("200000")) // 200 °C
        assertNull(ThermalZones.parseCelsius("2"))
    }

    @Test
    fun classifiesCpuGpuAndNeverBatteryOrSkin() {
        assertEquals(ZoneKind.CPU, ThermalZones.classify("cpu-big-core0"))
        assertEquals(ZoneKind.CPU, ThermalZones.classify("mtktscpu"))
        assertEquals(ZoneKind.CPU, ThermalZones.classify("cpu_little0"))
        assertEquals(ZoneKind.CPU, ThermalZones.classify("soc_max"))
        assertEquals(ZoneKind.GPU, ThermalZones.classify("mtk-gpu"))
        assertEquals(ZoneKind.GPU, ThermalZones.classify("mali-gpu"))
        assertEquals(ZoneKind.OTHER, ThermalZones.classify("battery"))
        assertEquals(ZoneKind.OTHER, ThermalZones.classify("mtktsbattery"))
        assertEquals(ZoneKind.OTHER, ThermalZones.classify("skin-therm"))
        assertEquals(ZoneKind.OTHER, ThermalZones.classify("xo-therm"))
        assertEquals(ZoneKind.OTHER, ThermalZones.classify("pmic"))
        assertEquals(ZoneKind.OTHER, ThermalZones.classify("usb_port"))
        assertEquals(ZoneKind.OTHER, ThermalZones.classify("board_ntc"))
    }

    @Test
    fun selectsMostSpecificReadableZonesAndReadsHottest() {
        val fs = FakeFs(
            mapOf(
                "thermal_zone0" to ("soc_max" to "50000"),
                "thermal_zone1" to ("cpu-little-core0" to "41000"),
                "thermal_zone2" to ("cpu-big-core0" to "47000"),
                "thermal_zone3" to ("battery" to "33000"),
                "thermal_zone4" to ("mtk-gpu" to "44000"),
                "thermal_zone5" to ("cpu-big-core1" to null), // SELinux-denied read
                "cooling_device0" to ("thermal-cpufreq" to "0"),
            ),
        )
        val sel = ThermalZones.scan(fs)
        val cpu = sel[ZoneKind.CPU]!!
        assertEquals(listOf("thermal_zone1", "thermal_zone2"), cpu.zones.map { it.name })
        val hot = ThermalZones.readHottest(fs, cpu)!!
        assertEquals(47f, hot.celsius, 0.001f)
        assertEquals("thermal_zone2 (cpu-big-core0)", hot.sourceLabel)
        assertEquals(listOf("thermal_zone4"), sel[ZoneKind.GPU]!!.zones.map { it.name })
    }

    @Test
    fun fallsBackToSocTierWhenNoCpuZone() {
        val fs = FakeFs(mapOf("thermal_zone0" to ("soc_max" to "52000"), "thermal_zone1" to ("battery" to "30000")))
        val sel = ThermalZones.scan(fs)
        assertEquals(listOf("thermal_zone0"), sel[ZoneKind.CPU]!!.zones.map { it.name })
        assertNull(sel[ZoneKind.GPU])
    }

    @Test
    fun nothingSelectedWhenAllDeniedOrOnlyBattery() {
        val denied = FakeFs(mapOf("thermal_zone0" to ("cpu0" to null), "thermal_zone1" to (null to "40000")))
        assertTrue(ThermalZones.scan(denied).isEmpty())
        val battOnly = FakeFs(mapOf("thermal_zone0" to ("battery" to "31000")))
        assertTrue(ThermalZones.scan(battOnly).isEmpty())
    }

    @Test
    fun readHottestSkipsZonesThatBecomeUnreadable() {
        val fs = FakeFs(mapOf("thermal_zone0" to ("cpu0" to "40000"), "thermal_zone1" to ("cpu1" to "42000")))
        val sel = ThermalZones.scan(fs)[ZoneKind.CPU]
        fs.temps["thermal_zone1"] = null
        assertEquals(40f, ThermalZones.readHottest(fs, sel)!!.celsius, 0.001f)
        fs.temps["thermal_zone0"] = "garbage"
        assertNull(ThermalZones.readHottest(fs, sel))
    }

    @Test
    fun zoneScanSurvivesListingFailure() {
        val fs = object : ThermalFs {
            override fun zoneNames(): List<String> = throw SecurityException()
            override fun read(zone: String, file: String): String? = null
            override fun pathOf(zone: String, file: String) = ""
        }
        assertTrue(ThermalZones.scan(fs).isEmpty())
    }
}
