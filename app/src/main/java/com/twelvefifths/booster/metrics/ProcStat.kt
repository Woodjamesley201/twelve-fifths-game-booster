package com.twelvefifths.booster.metrics

/**
 * Aggregate CPU jiffies from the first `cpu ` line of `/proc/stat`.
 *
 * `guest` / `guest_nice` are already folded into `user` / `nice` by the kernel,
 * so they are excluded from [total] to avoid double counting.
 *
 * Owner: Script Nathan.
 */
data class CpuTimes(
    val user: Long,
    val nice: Long,
    val system: Long,
    val idle: Long,
    val iowait: Long,
    val irq: Long,
    val softirq: Long,
    val steal: Long,
) {
    val idleAll: Long get() = idle + iowait
    val total: Long get() = user + nice + system + idle + iowait + irq + softirq + steal
}

object ProcStat {

    /** Parses the aggregate `cpu` line. Returns null for anything malformed or all-zero. */
    fun parseAggregate(text: String?): CpuTimes? {
        if (text.isNullOrBlank()) return null
        val line = text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("cpu ") || it == "cpu" }
            ?: return null
        val parts = line.split(WHITESPACE)
        if (parts.isEmpty() || parts[0] != "cpu") return null
        val nums = parts.drop(1).map { it.toLongOrNull() ?: return null }
        if (nums.size < 4) return null
        if (nums.any { it < 0L }) return null
        val times = CpuTimes(
            user = nums[0],
            nice = nums[1],
            system = nums[2],
            idle = nums[3],
            iowait = nums.getOrElse(4) { 0L },
            irq = nums.getOrElse(5) { 0L },
            softirq = nums.getOrElse(6) { 0L },
            steal = nums.getOrElse(7) { 0L },
        )
        return if (times.total <= 0L) null else times
    }

    /**
     * Busy percentage between two samples, or null when the delta is not
     * meaningful (no time passed, counters reset / went backwards).
     */
    fun usagePercent(previous: CpuTimes, current: CpuTimes): Float? {
        val totalDelta = current.total - previous.total
        val idleDelta = current.idleAll - previous.idleAll
        if (totalDelta <= 0L || idleDelta < 0L || idleDelta > totalDelta) return null
        val busy = totalDelta - idleDelta
        return (busy.toDouble() / totalDelta.toDouble() * 100.0).toFloat().coerceIn(0f, 100f)
    }

    private val WHITESPACE = Regex("""\s+""")
}

/** Result of one CPU-usage sample. */
data class CpuUsageReading(val percent: Float?, val support: MetricSupport)

/**
 * Stateful `/proc/stat` delta sampler. [reader] returns the file text or
 * throws / returns null when the read is denied (the normal case for apps on
 * Android 8+ because of SELinux). Once the first read fails the sampler
 * reports [MetricSupport.UNSUPPORTED] permanently and stops probing.
 *
 * Not thread-safe; call from one sampling coroutine.
 */
class CpuUsageSampler(
    private val reader: () -> String?,
) {
    private var readable: Boolean? = null
    private var previous: CpuTimes? = null
    private var consecutiveFailures: Int = 0

    fun sample(): CpuUsageReading {
        if (readable == false) return CpuUsageReading(null, MetricSupport.UNSUPPORTED)
        val current = try {
            ProcStat.parseAggregate(reader())
        } catch (_: Throwable) {
            null
        }
        if (current == null) {
            consecutiveFailures++
            // Never readable, or it stopped being readable: stop pretending.
            if (readable == null || consecutiveFailures >= MAX_FAILURES) {
                readable = false
                previous = null
                return CpuUsageReading(null, MetricSupport.UNSUPPORTED)
            }
            return CpuUsageReading(null, MetricSupport.SUPPORTED)
        }
        consecutiveFailures = 0
        readable = true
        val prev = previous
        previous = current
        val pct = if (prev == null) null else ProcStat.usagePercent(prev, current)
        return CpuUsageReading(pct, MetricSupport.SUPPORTED)
    }

    private companion object {
        const val MAX_FAILURES = 3
    }
}
