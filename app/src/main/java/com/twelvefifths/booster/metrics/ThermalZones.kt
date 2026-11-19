package com.twelvefifths.booster.metrics

/**
 * Thermal-zone parsing and selection for `/sys/class/thermal/thermal_zone*`.
 *
 * Only zones whose `type` clearly names a CPU (or GPU) are used. Battery,
 * skin, board, PMIC, modem, charger, etc. are never relabelled as CPU/GPU.
 * Values must be plausible die temperatures or they are dropped.
 *
 * Owner: Script Nathan.
 */
enum class ZoneKind { CPU, GPU, OTHER }

data class ThermalZone(
    /** Directory name, e.g. `thermal_zone3`. */
    val name: String,
    /** Contents of the zone's `type` file, trimmed. */
    val type: String,
    /** Absolute path of the zone's `temp` file. */
    val tempPath: String,
)

/** A chosen set of zones for one kind. [zones] are read every sample; the hottest wins. */
data class ZoneSelection(val kind: ZoneKind, val zones: List<ThermalZone>)

/** One temperature read from a zone selection, with the zone that produced it. */
data class ZoneTemperature(val celsius: Float, val zone: ThermalZone) {
    /** Human-readable source label, e.g. `thermal_zone3 (cpu-big-core0)`. */
    val sourceLabel: String get() = "${zone.name} (${zone.type})"
}

/** Filesystem seam so selection is JVM-testable. */
interface ThermalFs {
    /** Zone directory names under the thermal root, e.g. `thermal_zone0`. */
    fun zoneNames(): List<String>

    /** Text of `<zone>/<file>` or null if it cannot be read (SELinux etc.). */
    fun read(zone: String, file: String): String?

    fun pathOf(zone: String, file: String): String
}

object ThermalZones {

    private val EXCLUDE = listOf(
        "batt", "bms", "charger", "chg", "usb", "skin", "xo", "modem", "rf", "wifi", "wlan",
        "pa_", "pa-", "pmic", "ambient", "board", "case", "shell", "lcd", "display", "flash",
        "camera", "nfc", "ufs", "emmc", "sdcard", "conn", "nrpa", "mmw", "quiet", "back",
        "front", "sensor_hub", "dram", "ddr", "ap_ntc", "btemp", "consys", "md_", "md-",
    )

    /** Higher is more specific. 0 means "not this kind". */
    fun cpuScore(type: String): Int {
        val t = type.lowercase()
        if (EXCLUDE.any { t.contains(it) } || gpuScore(t) > 0) return 0
        return when {
            t.contains("cpu") -> 3
            t.contains("cluster") || t.contains("apc") || t.contains("little") ||
                t.contains("big") || t.contains("core") -> 2
            t.contains("soc") -> 1
            else -> 0
        }
    }

    fun gpuScore(type: String): Int {
        val t = type.lowercase()
        if (EXCLUDE.any { t.contains(it) }) return 0
        return when {
            t.contains("gpu") || t.contains("mali") || t.contains("g3d") -> 2
            else -> 0
        }
    }

    fun classify(type: String): ZoneKind = when {
        gpuScore(type) > 0 -> ZoneKind.GPU
        cpuScore(type) > 0 -> ZoneKind.CPU
        else -> ZoneKind.OTHER
    }

    /**
     * Parses a `temp` file. Kernels report millidegrees (most), deci-degrees
     * (some vendors), or whole degrees. Returns null unless the result is a
     * plausible SoC temperature.
     */
    fun parseCelsius(raw: String?): Float? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val value = text.toDoubleOrNull() ?: return null
        val celsius = when {
            value >= 1_000.0 -> value / 1_000.0
            value >= 150.0 -> value / 10.0
            else -> value
        }
        return if (celsius in MIN_PLAUSIBLE_C..MAX_PLAUSIBLE_C) celsius.toFloat() else null
    }

    /**
     * Scans [fs] once and returns the CPU and GPU zone selections that are both
     * readable and plausible right now. Keeps only the most specific score tier
     * per kind so a generic `soc` zone does not override real CPU zones.
     */
    fun scan(fs: ThermalFs, maxZones: Int = MAX_ZONES): Map<ZoneKind, ZoneSelection> {
        val names = try {
            fs.zoneNames()
        } catch (_: Throwable) {
            emptyList()
        }
        val candidates = names
            .filter { it.startsWith("thermal_zone") }
            .sortedBy { it.removePrefix("thermal_zone").toIntOrNull() ?: Int.MAX_VALUE }
            .take(maxZones)
            .mapNotNull { name ->
                val type = fs.read(name, "type")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                if (parseCelsius(fs.read(name, "temp")) == null) return@mapNotNull null
                ThermalZone(name = name, type = type, tempPath = fs.pathOf(name, "temp"))
            }
        val result = LinkedHashMap<ZoneKind, ZoneSelection>()
        select(candidates, ZoneKind.CPU, ::cpuScore)?.let { result[ZoneKind.CPU] = it }
        select(candidates, ZoneKind.GPU, ::gpuScore)?.let { result[ZoneKind.GPU] = it }
        return result
    }

    private fun select(
        zones: List<ThermalZone>,
        kind: ZoneKind,
        score: (String) -> Int,
    ): ZoneSelection? {
        val scored = zones.map { it to score(it.type) }.filter { it.second > 0 }
        val best = scored.maxOfOrNull { it.second } ?: return null
        val chosen = scored.filter { it.second == best }.map { it.first }.take(MAX_PER_KIND)
        return if (chosen.isEmpty()) null else ZoneSelection(kind, chosen)
    }

    /** Reads every zone in [selection]; returns the hottest plausible reading. */
    fun readHottest(fs: ThermalFs, selection: ZoneSelection?): ZoneTemperature? {
        if (selection == null) return null
        var best: ZoneTemperature? = null
        for (zone in selection.zones) {
            val c = parseCelsius(fs.read(zone.name, "temp")) ?: continue
            if (best == null || c > best.celsius) best = ZoneTemperature(c, zone)
        }
        return best
    }

    const val MIN_PLAUSIBLE_C = 5.0
    const val MAX_PLAUSIBLE_C = 130.0
    private const val MAX_ZONES = 96
    private const val MAX_PER_KIND = 12
}

/** Real `/sys/class/thermal` access. */
class SysfsThermalFs(private val root: String = "/sys/class/thermal") : ThermalFs {
    override fun zoneNames(): List<String> =
        java.io.File(root).list()?.toList().orEmpty()

    override fun read(zone: String, file: String): String? = try {
        java.io.File(pathOf(zone, file)).readText()
    } catch (_: Throwable) {
        null
    }

    override fun pathOf(zone: String, file: String): String = "$root/$zone/$file"
}
