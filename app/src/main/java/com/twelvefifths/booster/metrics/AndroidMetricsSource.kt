package com.twelvefifths.booster.metrics

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Conservative, unprivileged Android metrics, sampled about once a second.
 *
 * One sampler is shared by every collector (dashboard, HUD service, optimizer):
 * it starts on the first subscriber and stops 5 s after the last one leaves, so
 * nothing is sampled while nobody is looking and construction is free.
 *
 * Honest limits (do not "approximate" these as something they are not):
 * - FPS of *other games* is not readable without root / a debug capture.
 *   This class never reports overlay frame pacing as [MetricsSnapshot.fps].
 * - GPU usage and GPU VRAM are not readable without root / vendor APIs.
 * - CPU usage via `/proc/stat` is normally blocked for apps on Android 8+;
 *   it is probed once and then reported [MetricSupport.UNSUPPORTED].
 * - CPU/GPU temperature only from readable, plausibly-valued CPU/GPU zones under
 *   `/sys/class/thermal`. Battery temperature and PowerManager thermal status /
 *   headroom are reported in their own fields (see [temperatureSummary]).
 * - RAM comes from [ActivityManager.MemoryInfo].
 * - "Ping" is a timed TCP connect (ICMP is not available to apps).
 *
 * Owner: Script Nathan.
 */
class AndroidMetricsSource(
    context: Context,
    pingTargets: Flow<PingTarget> = flowOf(PingTarget.DEFAULT),
) : MetricsSource {

    private val appContext = context.applicationContext
    private val pingTargetFlow = pingTargets
    private val activityManager =
        appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val powerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val thermalFs: ThermalFs = SysfsThermalFs()
    private val cpuSampler = CpuUsageSampler { File("/proc/stat").readText() }
    private val pingProber = TcpPingProber()

    // Sampler-local state; only touched from the single sampling coroutine.
    private var thermalSelection: Map<ZoneKind, ZoneSelection>? = null
    private var batteryReadAtMs: Long = 0L
    private var batteryCache: Float? = null
    private var batterySupport: MetricSupport = MetricSupport.UNSUPPORTED
    private var headroomReadAtMs: Long = 0L
    private var headroomCache: Float? = null
    private var headroomNaNCount: Int = 0

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Declared last: reads every field above when the first collector arrives.
    private val shared: SharedFlow<MetricsSnapshot> = sampling()
        .catch { emitAll(flowOf(MetricsSnapshot.empty())) }
        .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    override fun snapshots(): Flow<MetricsSnapshot> = shared

    private fun sampling(): Flow<MetricsSnapshot> = channelFlow {
        val latestPing = MutableStateFlow(PingResult.NOT_MEASURED to PingTarget.DEFAULT.label)
        launch {
            pingTargetFlow
                .catch { emit(PingTarget.DEFAULT) }
                .distinctUntilChanged()
                .collectLatest { target ->
                    latestPing.value = PingResult.NOT_MEASURED to target.label
                    while (isActive) {
                        val started = SystemClock.elapsedRealtime()
                        val result = try {
                            pingProber.probe(target, networkAvailable = hasActiveNetwork())
                        } catch (t: Throwable) {
                            PingResult(PingStatus.UNREACHABLE, detail = t.javaClass.simpleName)
                        }
                        latestPing.value = result to target.label
                        val elapsed = SystemClock.elapsedRealtime() - started
                        delay((PING_INTERVAL_MS - elapsed).coerceAtLeast(MIN_GAP_MS))
                    }
                }
        }
        while (isActive) {
            val started = SystemClock.elapsedRealtime()
            val snapshot = try {
                readSnapshot(latestPing.value.first, latestPing.value.second)
            } catch (_: Throwable) {
                MetricsSnapshot.empty()
            }
            send(snapshot)
            val elapsed = SystemClock.elapsedRealtime() - started
            delay((SAMPLE_INTERVAL_MS - elapsed).coerceAtLeast(MIN_GAP_MS))
        }
    }

    private fun readSnapshot(ping: PingResult, pingLabel: String): MetricsSnapshot {
        val now = System.currentTimeMillis()
        val ram = readRam()
        val cpu = cpuSampler.sample()
        val zones = thermalSelection ?: ThermalZones.scan(thermalFs).also { thermalSelection = it }
        val cpuTemp = ThermalZones.readHottest(thermalFs, zones[ZoneKind.CPU])
        val gpuTemp = ThermalZones.readHottest(thermalFs, zones[ZoneKind.GPU])
        val battery = readBatteryTemp()
        val status = readThermalStatus()
        val headroom = readHeadroom()
        return MetricsSnapshot(
            timestampMs = now,
            fps = null,
            fpsSupport = MetricSupport.UNSUPPORTED,
            cpuTempC = cpuTemp?.celsius,
            cpuTempSupport = if (zones[ZoneKind.CPU] != null) MetricSupport.SUPPORTED else MetricSupport.UNSUPPORTED,
            gpuTempC = gpuTemp?.celsius,
            gpuTempSupport = if (zones[ZoneKind.GPU] != null) MetricSupport.SUPPORTED else MetricSupport.UNSUPPORTED,
            ramUsedBytes = ram.used,
            ramTotalBytes = ram.total,
            ramPercent = ram.percent,
            ramSupport = ram.support,
            cpuUsage = cpu.percent,
            cpuUsageSupport = cpu.support,
            gpuUsage = null,
            gpuUsageSupport = MetricSupport.UNSUPPORTED,
            pingMs = if (ping.status == PingStatus.OK) ping.millis else null,
            pingSupport = MetricSupport.SUPPORTED,
            vramUsedBytes = null,
            vramSupport = MetricSupport.UNSUPPORTED,
            cpuTempSource = cpuTemp?.sourceLabel,
            gpuTempSource = gpuTemp?.sourceLabel,
            batteryTempC = battery.first,
            batteryTempSupport = battery.second,
            thermalStatus = status.first,
            thermalStatusSupport = status.second,
            thermalHeadroom = headroom.first,
            thermalHeadroomSupport = headroom.second,
            ramAvailableBytes = ram.available,
            lowMemory = ram.low,
            pingTarget = pingLabel,
            pingStatus = ping.status,
        )
    }

    private data class RamReading(
        val used: Long?,
        val total: Long?,
        val available: Long?,
        val percent: Float?,
        val low: Boolean?,
        val support: MetricSupport,
    )

    private fun readRam(): RamReading {
        val am = activityManager ?: return RamReading(null, null, null, null, null, MetricSupport.UNSUPPORTED)
        return try {
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            val total = info.totalMem
            val avail = info.availMem
            if (total <= 0L || avail < 0L) {
                RamReading(null, null, null, null, null, MetricSupport.UNSUPPORTED)
            } else {
                val used = (total - avail).coerceIn(0L, total)
                val percent = (used.toDouble() / total.toDouble() * 100.0).toFloat()
                RamReading(used, total, avail, percent, info.lowMemory, MetricSupport.SUPPORTED)
            }
        } catch (_: Throwable) {
            RamReading(null, null, null, null, null, MetricSupport.UNSUPPORTED)
        }
    }

    /** Sticky ACTION_BATTERY_CHANGED read, at most every [BATTERY_INTERVAL_MS]. */
    private fun readBatteryTemp(): Pair<Float?, MetricSupport> {
        val now = SystemClock.elapsedRealtime()
        if (batteryReadAtMs != 0L && now - batteryReadAtMs < BATTERY_INTERVAL_MS) {
            return batteryCache to batterySupport
        }
        batteryReadAtMs = now
        try {
            val sticky: Intent? = ContextCompat.registerReceiver(
                appContext,
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            val tenths = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
            val c = BatteryTemperature.fromTenths(tenths)
            batteryCache = c
            batterySupport = if (sticky != null && tenths != Int.MIN_VALUE) MetricSupport.SUPPORTED else MetricSupport.UNSUPPORTED
        } catch (_: Throwable) {
            batteryCache = null
            batterySupport = MetricSupport.UNSUPPORTED
        }
        return batteryCache to batterySupport
    }

    private fun readThermalStatus(): Pair<ThermalStatusLevel?, MetricSupport> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null to MetricSupport.UNSUPPORTED
        val pm = powerManager ?: return null to MetricSupport.UNSUPPORTED
        return try {
            ThermalStatusLevel.fromPlatform(pm.currentThermalStatus) to MetricSupport.SUPPORTED
        } catch (_: Throwable) {
            null to MetricSupport.UNSUPPORTED
        }
    }

    /**
     * getThermalHeadroom is rate-limited by the platform (it returns NaN when
     * polled too often) and NaN when the HAL has no data, so it is read at most
     * every [HEADROOM_INTERVAL_MS] and marked unsupported after repeated NaNs.
     */
    private fun readHeadroom(): Pair<Float?, MetricSupport> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null to MetricSupport.UNSUPPORTED
        val pm = powerManager ?: return null to MetricSupport.UNSUPPORTED
        if (headroomNaNCount >= MAX_HEADROOM_NAN) return null to MetricSupport.UNSUPPORTED
        val now = SystemClock.elapsedRealtime()
        if (headroomReadAtMs == 0L || now - headroomReadAtMs >= HEADROOM_INTERVAL_MS) {
            headroomReadAtMs = now
            val value = try {
                pm.getThermalHeadroom(0)
            } catch (_: Throwable) {
                Float.NaN
            }
            if (value.isNaN() || value < 0f) {
                headroomNaNCount++
                headroomCache = null
            } else {
                headroomNaNCount = 0
                headroomCache = value
            }
        }
        val support = if (headroomNaNCount >= MAX_HEADROOM_NAN) MetricSupport.UNSUPPORTED else MetricSupport.SUPPORTED
        return headroomCache to support
    }

    private fun hasActiveNetwork(): Boolean {
        val cm = connectivityManager ?: return true // Unknown: let the probe decide.
        return try {
            cm.activeNetwork != null
        } catch (_: Throwable) {
            true
        }
    }

    private companion object {
        const val SAMPLE_INTERVAL_MS = 1_000L
        const val PING_INTERVAL_MS = 1_000L
        const val MIN_GAP_MS = 200L
        const val STOP_TIMEOUT_MS = 5_000L
        const val BATTERY_INTERVAL_MS = 5_000L
        const val HEADROOM_INTERVAL_MS = 10_000L
        const val MAX_HEADROOM_NAN = 3
    }
}
