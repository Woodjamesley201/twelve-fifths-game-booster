package com.twelvefifths.booster.metrics

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Conservative, unprivileged Android metrics.
 *
 * Honest limits (do not "approximate" these as something they are not):
 * - FPS of *other games* is not readable without root / a debug capture.
 *   This class never reports overlay frame pacing as [MetricsSnapshot.fps].
 * - GPU usage and GPU VRAM are not readable without root / vendor APIs.
 * - CPU usage via `/proc/stat` is blocked for apps on Android 8+.
 * - Temperatures: only used when a numeric zone under `/sys/class/thermal`
 *   can actually be read. [android.os.PowerManager] thermal status / headroom
 *   are not degrees Celsius, so they are never written into temp fields.
 *   Battery temperature is also not CPU or GPU temperature.
 * - RAM comes from [ActivityManager.MemoryInfo].
 * - "Ping" is a timed TCP connect (ICMP is not available to apps).
 *
 * Owner: Script Nathan.
 */
class AndroidMetricsSource(
    context: Context,
) : MetricsSource {

    private val appContext = context.applicationContext
    private val activityManager =
        appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?

    /** Cached after the first failed /proc/stat attempt so we do not keep probing. */
    private var cpuUsageReadable: Boolean? = null
    private var lastCpuTotal: Long = 0L
    private var lastCpuIdle: Long = 0L

    private var cpuTempPath: String? = null
    private var gpuTempPath: String? = null
    private var thermalScanned: Boolean = false

    override fun snapshots(): Flow<MetricsSnapshot> = flow {
        while (true) {
            emit(readSnapshot())
            delay(SAMPLE_INTERVAL_MS)
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun readSnapshot(): MetricsSnapshot {
        val now = System.currentTimeMillis()
        val ram = readRam()
        val cpuUsage = readCpuUsage()
        scanThermalZonesIfNeeded()
        val cpuTemp = readTempC(cpuTempPath)
        val gpuTemp = readTempC(gpuTempPath)
        val ping = readTcpConnectMs()
        return MetricsSnapshot(
            timestampMs = now,
            fps = null,
            fpsSupport = MetricSupport.UNSUPPORTED,
            cpuTempC = cpuTemp,
            cpuTempSupport = if (cpuTempPath == null) MetricSupport.UNSUPPORTED else MetricSupport.SUPPORTED,
            gpuTempC = gpuTemp,
            gpuTempSupport = if (gpuTempPath == null) MetricSupport.UNSUPPORTED else MetricSupport.SUPPORTED,
            ramUsedBytes = ram.used,
            ramTotalBytes = ram.total,
            ramPercent = ram.percent,
            ramSupport = ram.support,
            cpuUsage = cpuUsage.value,
            cpuUsageSupport = cpuUsage.support,
            gpuUsage = null,
            gpuUsageSupport = MetricSupport.UNSUPPORTED,
            pingMs = ping.value,
            pingSupport = ping.support,
            vramUsedBytes = null,
            vramSupport = MetricSupport.UNSUPPORTED,
        )
    }

    private data class RamReading(
        val used: Long?,
        val total: Long?,
        val percent: Float?,
        val support: MetricSupport,
    )

    private fun readRam(): RamReading {
        return try {
            val info = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(info)
            val total = info.totalMem
            val avail = info.availMem
            if (total <= 0L) {
                RamReading(null, null, null, MetricSupport.UNSUPPORTED)
            } else {
                val used = (total - avail).coerceAtLeast(0L)
                val percent = (used.toDouble() / total.toDouble() * 100.0).toFloat()
                RamReading(used, total, percent, MetricSupport.SUPPORTED)
            }
        } catch (_: Throwable) {
            RamReading(null, null, null, MetricSupport.UNSUPPORTED)
        }
    }

    private data class UsageReading(
        val value: Float?,
        val support: MetricSupport,
    )

    private fun readCpuUsage(): UsageReading {
        if (cpuUsageReadable == false) {
            return UsageReading(null, MetricSupport.UNSUPPORTED)
        }
        val sample = readProcStat()
        if (sample == null) {
            cpuUsageReadable = false
            return UsageReading(null, MetricSupport.UNSUPPORTED)
        }
        cpuUsageReadable = true
        val prevTotal = lastCpuTotal
        val prevIdle = lastCpuIdle
        lastCpuTotal = sample.total
        lastCpuIdle = sample.idle
        if (prevTotal == 0L || sample.total <= prevTotal) {
            return UsageReading(null, MetricSupport.SUPPORTED)
        }
        val totalDelta = sample.total - prevTotal
        val idleDelta = sample.idle - prevIdle
        val busy = (totalDelta - idleDelta).coerceAtLeast(0L)
        val pct = (busy.toDouble() / totalDelta.toDouble() * 100.0).toFloat()
        return UsageReading(pct, MetricSupport.SUPPORTED)
    }

    private data class ProcStat(val total: Long, val idle: Long)

    private fun readProcStat(): ProcStat? {
        return try {
            val line = File("/proc/stat").bufferedReader().use { it.readLine() } ?: return null
            // cpu  user nice system idle iowait irq softirq steal guest guest_nice
            val parts = line.trim().split(Regex("""\s+"""))
            if (parts.isEmpty() || parts[0] != "cpu" || parts.size < 5) return null
            val nums = parts.drop(1).mapNotNull { it.toLongOrNull() }
            if (nums.size < 4) return null
            val total = nums.sum()
            val idle = nums[3] + (nums.getOrNull(4) ?: 0L) // idle + iowait
            if (total <= 0L) return null
            ProcStat(total, idle)
        } catch (_: Throwable) {
            null
        }
    }

    private fun scanThermalZonesIfNeeded() {
        if (thermalScanned) return
        thermalScanned = true
        val root = File("/sys/class/thermal")
        if (!root.isDirectory) return
        val zones = root.listFiles { f -> f.isDirectory && f.name.startsWith("thermal_zone") }
            ?: return
        for (zone in zones.sortedBy { it.name }) {
            val type = try {
                File(zone, "type").readText().trim().lowercase()
            } catch (_: Throwable) {
                continue
            }
            val tempFile = File(zone, "temp")
            if (!tempFile.canRead()) continue
            val value = readTempC(tempFile.absolutePath)
            if (value == null) continue
            val looksGpu = type.contains("gpu") || type.contains("mali") || type.contains("g3d")
            val looksCpu = type.contains("cpu") || type.contains("soc") || type.contains("cluster") || type == "tz"
            when {
                looksGpu && gpuTempPath == null -> gpuTempPath = tempFile.absolutePath
                looksCpu && cpuTempPath == null -> cpuTempPath = tempFile.absolutePath
            }
        }
    }

    private fun readTempC(path: String?): Float? {
        if (path == null) return null
        return try {
            val raw = File(path).readText().trim().toFloat()
            // Most zones report millidegrees; a few report degrees.
            val celsius = if (raw > 200f) raw / 1000f else raw
            if (celsius in 1f..120f) celsius else null
        } catch (_: Throwable) {
            null
        }
    }

    private data class PingReading(
        val value: Long?,
        val support: MetricSupport,
    )

    private suspend fun readTcpConnectMs(): PingReading {
        val cm = connectivityManager
        if (cm != null) {
            val network = cm.activeNetwork
            if (network == null) {
                return PingReading(null, MetricSupport.SUPPORTED)
            }
        }
        return withContext(Dispatchers.IO) {
            val started = System.nanoTime()
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(PING_HOST, PING_PORT), PING_TIMEOUT_MS)
                }
                val ms = (System.nanoTime() - started) / 1_000_000L
                PingReading(ms, MetricSupport.SUPPORTED)
            } catch (_: Throwable) {
                PingReading(null, MetricSupport.SUPPORTED)
            }
        }
    }

    private companion object {
        const val SAMPLE_INTERVAL_MS = 1_000L
        const val PING_HOST = "8.8.8.8"
        const val PING_PORT = 443
        const val PING_TIMEOUT_MS = 1_500
    }
}
