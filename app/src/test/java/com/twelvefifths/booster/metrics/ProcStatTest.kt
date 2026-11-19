package com.twelvefifths.booster.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProcStatTest {

    private val sample = """
        cpu  4705 356 584 3699 23 23 0 0 0 0
        cpu0 1393280 32966 572056 13343292 6130 0 17875 0 0 0
        intr 114930548 113199788 3 0 5 263 0 4 [...]
        ctxt 1990473
    """.trimIndent()

    @Test
    fun parsesAggregateLineAndIgnoresGuestColumns() {
        val t = ProcStat.parseAggregate(sample)
        assertNotNull(t)
        t!!
        assertEquals(4705L, t.user)
        assertEquals(3699L, t.idle)
        assertEquals(23L, t.iowait)
        assertEquals(4705L + 356 + 584 + 3699 + 23 + 23 + 0 + 0, t.total)
        assertEquals(3699L + 23, t.idleAll)
    }

    @Test
    fun parsesOldKernelWithFourColumns() {
        val t = ProcStat.parseAggregate("cpu 10 0 10 80")!!
        assertEquals(100L, t.total)
        assertEquals(0L, t.iowait)
    }

    @Test
    fun rejectsMalformedEmptyAndZero() {
        assertNull(ProcStat.parseAggregate(null))
        assertNull(ProcStat.parseAggregate(""))
        assertNull(ProcStat.parseAggregate("cpu0 1 2 3 4"))
        assertNull(ProcStat.parseAggregate("cpu 1 2 x 4"))
        assertNull(ProcStat.parseAggregate("cpu 1 2"))
        assertNull(ProcStat.parseAggregate("cpu 0 0 0 0 0 0 0"))
        assertNull(ProcStat.parseAggregate("cpu -1 2 3 4"))
    }

    @Test
    fun usageFromDeltas() {
        val a = ProcStat.parseAggregate("cpu 100 0 100 800 0 0 0 0")!!
        val b = ProcStat.parseAggregate("cpu 150 0 150 900 0 0 0 0")!!
        // busy delta 100, total delta 200
        assertEquals(50f, ProcStat.usagePercent(a, b)!!, 0.001f)
    }

    @Test
    fun usageNullWhenNoTimePassedOrCountersReset() {
        val a = ProcStat.parseAggregate("cpu 100 0 100 800")!!
        assertNull(ProcStat.usagePercent(a, a))
        val reset = ProcStat.parseAggregate("cpu 1 0 1 8")!!
        assertNull(ProcStat.usagePercent(a, reset))
    }

    @Test
    fun samplerReportsUnsupportedWhenDenied() {
        var calls = 0
        val sampler = CpuUsageSampler { calls++; throw SecurityException("EACCES") }
        assertEquals(CpuUsageReading(null, MetricSupport.UNSUPPORTED), sampler.sample())
        assertEquals(CpuUsageReading(null, MetricSupport.UNSUPPORTED), sampler.sample())
        assertEquals("stops probing after the first denial", 1, calls)
    }

    @Test
    fun samplerWarmsUpThenReportsDelta() {
        val lines = ArrayDeque(listOf("cpu 100 0 100 800", "cpu 150 0 150 900"))
        val sampler = CpuUsageSampler { lines.removeFirst() }
        val first = sampler.sample()
        assertEquals(MetricSupport.SUPPORTED, first.support)
        assertNull("no delta on the first sample", first.percent)
        assertEquals(50f, sampler.sample().percent!!, 0.001f)
    }

    @Test
    fun samplerGivesUpAfterRepeatedFailuresOnceReadable() {
        val lines = ArrayDeque(listOf<String?>("cpu 100 0 100 800", null, null, null))
        val sampler = CpuUsageSampler { lines.removeFirst() }
        sampler.sample()
        assertEquals(MetricSupport.SUPPORTED, sampler.sample().support)
        assertEquals(MetricSupport.SUPPORTED, sampler.sample().support)
        assertEquals(MetricSupport.UNSUPPORTED, sampler.sample().support)
    }
}
