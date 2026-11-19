package com.twelvefifths.booster.metrics

import java.net.ConnectException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PingTest {

    private val loopback: InetAddress = InetAddress.getByAddress("fake", byteArrayOf(10, 0, 0, 1))

    @Test
    fun parsesTargets() {
        assertEquals(PingTarget("8.8.8.8", 443), PingTarget.parse("8.8.8.8:443"))
        assertEquals(PingTarget("example.com", 443), PingTarget.parse("example.com"))
        assertEquals(PingTarget("game.server.net", 7777), PingTarget.parse(" game.server.net:7777 "))
        assertEquals(PingTarget("2001:4860:4860::8888", 53), PingTarget.parse("[2001:4860:4860::8888]:53"))
        assertEquals(PingTarget("::1", 443), PingTarget.parse("::1"))
        assertEquals("[::1]:443", PingTarget("::1", 443).label)
    }

    @Test
    fun rejectsInvalidTargets() {
        assertNull(PingTarget.parse(null))
        assertNull(PingTarget.parse(""))
        assertNull(PingTarget.parse("host:0"))
        assertNull(PingTarget.parse("host:70000"))
        assertNull(PingTarget.parse("host:abc"))
        assertNull(PingTarget.parse("bad host"))
        assertNull(PingTarget.parse("-bad.com"))
        assertNull(PingTarget.parse("[::1"))
    }

    private fun prober(
        connect: () -> Unit = {},
        resolve: (String) -> InetAddress = { loopback },
        elapsedNs: Long = 23_400_000L,
    ): TcpPingProber {
        var t = 0L
        return TcpPingProber(
            resolver = { resolve(it) },
            connector = { _, _ -> connect(); t += elapsedNs },
            nanoClock = { t },
        )
    }

    @Test
    fun successReportsHandshakeTimeRoundedUp() {
        val r = prober().probe(PingTarget.DEFAULT)
        assertEquals(PingStatus.OK, r.status)
        assertEquals(24L, r.millis)
    }

    @Test
    fun subMillisecondIsReportedAsOneNotZero() {
        assertEquals(1L, prober(elapsedNs = 200_000L).probe(PingTarget.DEFAULT).millis)
    }

    @Test
    fun dnsTimeIsNotMeasured() {
        var t = 0L
        val p = TcpPingProber(
            resolver = { t += 500_000_000L; loopback },
            connector = { _, _ -> t += 10_000_000L },
            nanoClock = { t },
        )
        assertEquals(10L, p.probe(PingTarget.DEFAULT).millis)
    }

    @Test
    fun failuresNeverCarryANumber() {
        val timeout = prober(connect = { throw SocketTimeoutException("t") }).probe(PingTarget.DEFAULT)
        assertEquals(PingStatus.TIMEOUT, timeout.status)
        assertNull(timeout.millis)

        val refused = prober(connect = { throw ConnectException("refused") }).probe(PingTarget.DEFAULT)
        assertEquals(PingStatus.UNREACHABLE, refused.status)
        assertNull(refused.millis)

        val dns = prober(resolve = { throw UnknownHostException(it) }).probe(PingTarget("nope.invalid", 443))
        assertEquals(PingStatus.UNKNOWN_HOST, dns.status)
        assertNull(dns.millis)

        val sec = prober(connect = { throw SecurityException("no INTERNET") }).probe(PingTarget.DEFAULT)
        assertEquals(PingStatus.UNREACHABLE, sec.status)
    }

    @Test
    fun noNetworkAndInvalidTargetSkipTheSocket() {
        var connects = 0
        val p = prober(connect = { connects++ })
        assertEquals(PingStatus.NO_NETWORK, p.probe(PingTarget.DEFAULT, networkAvailable = false).status)
        assertEquals(PingStatus.INVALID_TARGET, p.probe(PingTarget("bad host", 1)).status)
        assertEquals(0, connects)
    }

    @Test
    fun batteryTemperatureParsing() {
        assertEquals(31.5f, BatteryTemperature.fromTenths(315)!!, 0.001f)
        assertNull(BatteryTemperature.fromTenths(Int.MIN_VALUE))
        assertNull(BatteryTemperature.fromTenths(-1))
        assertNull(BatteryTemperature.fromTenths(2000))
    }

    @Test
    fun temperatureSummaryFollowsFallbackChainWithHonestLabels() {
        val base = MetricsSnapshot.empty(1L)
        assertNull(base.temperatureSummary())

        val status = base.copy(thermalStatus = ThermalStatusLevel.MODERATE, thermalStatusSupport = MetricSupport.SUPPORTED)
        val s1 = status.temperatureSummary()!!
        assertEquals(TemperatureSource.THERMAL_STATUS, s1.source)
        assertNull("thermal status is never degrees", s1.celsius)

        val batt = status.copy(batteryTempC = 33f, batteryTempSupport = MetricSupport.SUPPORTED)
        val s2 = batt.temperatureSummary()!!
        assertEquals(TemperatureSource.BATTERY, s2.source)
        assertEquals("BATT °C", s2.label)
        assertNull("battery never becomes CPU temp", batt.cpuTempC)

        val cpu = batt.copy(cpuTempC = 51f, cpuTempSupport = MetricSupport.SUPPORTED, cpuTempSource = "thermal_zone2 (cpu)")
        val s3 = cpu.temperatureSummary()!!
        assertEquals(TemperatureSource.CPU_THERMAL_ZONE, s3.source)
        assertEquals(51f, s3.celsius!!, 0.001f)
        assertTrue(s3.detail!!.contains("thermal_zone2"))
    }

    @Test
    fun thermalStatusMapping() {
        assertEquals(ThermalStatusLevel.NONE, ThermalStatusLevel.fromPlatform(0))
        assertEquals(ThermalStatusLevel.SHUTDOWN, ThermalStatusLevel.fromPlatform(6))
        assertNull(ThermalStatusLevel.fromPlatform(7))
        assertNull(ThermalStatusLevel.fromPlatform(-1))
    }
}
