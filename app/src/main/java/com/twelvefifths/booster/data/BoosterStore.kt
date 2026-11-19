package com.twelvefifths.booster.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.twelvefifths.booster.metrics.MetricSupport
import com.twelvefifths.booster.metrics.MetricsSnapshot
import com.twelvefifths.booster.metrics.PingStatus
import com.twelvefifths.booster.metrics.PingTarget
import com.twelvefifths.booster.metrics.ThermalStatusLevel
import com.twelvefifths.booster.optimize.GameEntry
import com.twelvefifths.booster.optimize.OptimizationProfile
import com.twelvefifths.booster.optimize.OptimizationSession
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.boosterDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "twelve_fifths_booster",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * DataStore-backed persistence for the game library, per-game profiles,
 * HUD preferences, and session history. Nathan may later split this into Room.
 *
 * All fields used below are declared before any read of stored state.
 *
 * Owner: Script Nathan.
 */
class BoosterStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val dataStore = appContext.boosterDataStore

    /** DataStore data that survives read errors (IOException → empty prefs) instead of crashing collectors. */
    private val safeData: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val hudPreferences: Flow<HudPreferences> = safeData.map { prefs ->
        decodeHud(prefs[KEY_HUD])
    }.distinctUntilChanged()

    val manualGames: Flow<List<GameEntry>> = safeData.map { prefs ->
        decodeGames(prefs[KEY_MANUAL_GAMES])
    }.distinctUntilChanged()

    val lastPlayed: Flow<Map<String, Long>> = safeData.map { prefs ->
        decodeLastPlayed(prefs[KEY_LAST_PLAYED])
    }.distinctUntilChanged()

    val sessionHistory: Flow<List<SessionRecord>> = safeData.map { prefs ->
        decodeSessions(prefs[KEY_SESSIONS])
    }.distinctUntilChanged()

    /** All saved per-game profiles, keyed by package. */
    val profiles: Flow<Map<String, OptimizationProfile>> = safeData.map { prefs ->
        decodeProfiles(prefs[KEY_PROFILES])
    }.distinctUntilChanged()

    /** Host:port for the TCP-connect latency probe. Defaults to [PingTarget.DEFAULT]. */
    val pingTarget: Flow<PingTarget> = safeData.map { prefs ->
        PingTarget.parse(prefs[KEY_PING_TARGET]) ?: PingTarget.DEFAULT
    }.distinctUntilChanged()

    suspend fun setHudPreferences(value: HudPreferences) {
        dataStore.edit { it[KEY_HUD] = encodeHud(value) }
    }

    /** Atomic read-modify-write, so the HUD service and the settings screen do not clobber each other. */
    suspend fun updateHudPreferences(transform: (HudPreferences) -> HudPreferences) {
        dataStore.edit { prefs ->
            prefs[KEY_HUD] = encodeHud(transform(decodeHud(prefs[KEY_HUD])))
        }
    }

    /**
     * Sets the ping target from user text (`host`, `host:port`, `[v6]:port`).
     * Returns the parsed target, or null (and changes nothing) if invalid.
     */
    suspend fun setPingTarget(input: String): PingTarget? {
        val target = PingTarget.parse(input) ?: return null
        dataStore.edit { it[KEY_PING_TARGET] = target.label }
        return target
    }

    suspend fun resetPingTarget() {
        dataStore.edit { it.remove(KEY_PING_TARGET) }
    }

    /** The in-flight session, persisted for crash recovery (null when none). */
    suspend fun activeSession(): OptimizationSession? =
        decodeActiveSession(safeData.first()[KEY_ACTIVE_SESSION])

    suspend fun setActiveSession(session: OptimizationSession?) {
        dataStore.edit { prefs ->
            if (session == null) prefs.remove(KEY_ACTIVE_SESSION) else prefs[KEY_ACTIVE_SESSION] = encodeActiveSession(session)
        }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        dataStore.edit { prefs ->
            val current = decodeHud(prefs[KEY_HUD])
            prefs[KEY_HUD] = encodeHud(current.copy(onboardingDone = done))
        }
    }

    suspend fun upsertManualGame(game: GameEntry) {
        dataStore.edit { prefs ->
            val list = decodeGames(prefs[KEY_MANUAL_GAMES]).toMutableList()
            list.removeAll { it.packageName == game.packageName }
            list.add(game.copy(isManual = true))
            prefs[KEY_MANUAL_GAMES] = encodeGames(list)
        }
    }

    suspend fun removeManualGame(packageName: String) {
        dataStore.edit { prefs ->
            val list = decodeGames(prefs[KEY_MANUAL_GAMES]).filter { it.packageName != packageName }
            prefs[KEY_MANUAL_GAMES] = encodeGames(list)
        }
    }

    suspend fun markPlayed(packageName: String, atMs: Long) {
        dataStore.edit { prefs ->
            val map = decodeLastPlayed(prefs[KEY_LAST_PLAYED]).toMutableMap()
            map[packageName] = atMs
            prefs[KEY_LAST_PLAYED] = encodeLastPlayed(map)
        }
    }

    suspend fun loadProfile(gamePackage: String): OptimizationProfile {
        val profiles = safeData.map { prefs -> decodeProfiles(prefs[KEY_PROFILES]) }.first()
        return profiles[gamePackage] ?: OptimizationProfile(gamePackage = gamePackage)
    }

    suspend fun saveProfile(profile: OptimizationProfile) {
        dataStore.edit { prefs ->
            val map = decodeProfiles(prefs[KEY_PROFILES]).toMutableMap()
            map[profile.gamePackage] = profile
            prefs[KEY_PROFILES] = encodeProfiles(map)
        }
    }

    suspend fun appendSession(record: SessionRecord) {
        dataStore.edit { prefs ->
            val list = decodeSessions(prefs[KEY_SESSIONS]).toMutableList()
            list.removeAll { it.id == record.id }
            list.add(0, record)
            prefs[KEY_SESSIONS] = encodeSessions(list.take(MAX_SESSIONS))
        }
    }

    private fun decodeHud(raw: String?): HudPreferences {
        if (raw.isNullOrBlank()) return HudPreferences()
        return try {
            val o = JSONObject(raw)
            HudPreferences(
                posX = o.optInt("posX", 24),
                posY = o.optInt("posY", 120),
                widthDp = o.optInt("widthDp", 220),
                compact = o.optBoolean("compact", true),
                opacity = o.optDouble("opacity", 0.86).toFloat(),
                accentArgb = o.optInt("accentArgb", 0xFF4F8CFF.toInt()),
                showFps = o.optBoolean("showFps", true),
                showCpuTemp = o.optBoolean("showCpuTemp", true),
                showGpuTemp = o.optBoolean("showGpuTemp", true),
                showRam = o.optBoolean("showRam", true),
                showCpuUsage = o.optBoolean("showCpuUsage", true),
                showGpuUsage = o.optBoolean("showGpuUsage", true),
                showPing = o.optBoolean("showPing", true),
                showVram = o.optBoolean("showVram", true),
                onboardingDone = o.optBoolean("onboardingDone", false),
            )
        } catch (_: Throwable) {
            HudPreferences()
        }
    }

    private fun encodeHud(value: HudPreferences): String = JSONObject()
        .put("posX", value.posX)
        .put("posY", value.posY)
        .put("widthDp", value.widthDp)
        .put("compact", value.compact)
        .put("opacity", value.opacity.toDouble())
        .put("accentArgb", value.accentArgb)
        .put("showFps", value.showFps)
        .put("showCpuTemp", value.showCpuTemp)
        .put("showGpuTemp", value.showGpuTemp)
        .put("showRam", value.showRam)
        .put("showCpuUsage", value.showCpuUsage)
        .put("showGpuUsage", value.showGpuUsage)
        .put("showPing", value.showPing)
        .put("showVram", value.showVram)
        .put("onboardingDone", value.onboardingDone)
        .toString()

    private fun decodeGames(raw: String?): List<GameEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        GameEntry(
                            packageName = o.getString("packageName"),
                            displayName = o.optString("displayName", o.getString("packageName")),
                            isManual = true,
                            lastPlayedMs = o.optLong("lastPlayedMs").takeIf { it > 0L },
                            isSystem = false,
                        ),
                    )
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun encodeGames(games: List<GameEntry>): String {
        val arr = JSONArray()
        for (g in games) {
            arr.put(
                JSONObject()
                    .put("packageName", g.packageName)
                    .put("displayName", g.displayName)
                    .put("lastPlayedMs", g.lastPlayedMs ?: 0L),
            )
        }
        return arr.toString()
    }

    private fun decodeLastPlayed(raw: String?): Map<String, Long> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val o = JSONObject(raw)
            buildMap {
                val keys = o.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    put(k, o.optLong(k))
                }
            }
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    private fun encodeLastPlayed(map: Map<String, Long>): String {
        val o = JSONObject()
        for ((k, v) in map) o.put(k, v)
        return o.toString()
    }

    private fun decodeProfiles(raw: String?): Map<String, OptimizationProfile> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val o = JSONObject(raw)
            buildMap {
                val keys = o.keys()
                while (keys.hasNext()) {
                    val pkg = keys.next()
                    val p = o.getJSONObject(pkg)
                    val close = p.optJSONArray("closeBackgroundPackages")
                    val packages = buildSet {
                        if (close != null) {
                            for (i in 0 until close.length()) add(close.getString(i))
                        }
                    }
                    put(
                        pkg,
                        OptimizationProfile(
                            gamePackage = pkg,
                            closeBackgroundPackages = packages,
                            enableDoNotDisturb = p.optBoolean("enableDoNotDisturb", false),
                            suggestBatterySaverSettings = p.optBoolean("suggestBatterySaverSettings", false),
                            suggestGameModeSettings = p.optBoolean("suggestGameModeSettings", false),
                            autoEndWhenGameLeaves = p.optBoolean("autoEndWhenGameLeaves", true),
                            launchGameOnStart = p.optBoolean("launchGameOnStart", false),
                        ),
                    )
                }
            }
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    private fun encodeProfiles(map: Map<String, OptimizationProfile>): String {
        val o = JSONObject()
        for ((pkg, p) in map) {
            val close = JSONArray()
            p.closeBackgroundPackages.forEach { close.put(it) }
            o.put(
                pkg,
                JSONObject()
                    .put("closeBackgroundPackages", close)
                    .put("enableDoNotDisturb", p.enableDoNotDisturb)
                    .put("suggestBatterySaverSettings", p.suggestBatterySaverSettings)
                    .put("suggestGameModeSettings", p.suggestGameModeSettings)
                    .put("autoEndWhenGameLeaves", p.autoEndWhenGameLeaves)
                    .put("launchGameOnStart", p.launchGameOnStart),
            )
        }
        return o.toString()
    }

    private fun decodeSessions(raw: String?): List<SessionRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (!o.has("id") || !o.has("gamePackage")) continue
                    add(
                        SessionRecord(
                            id = o.getString("id"),
                            gamePackage = o.getString("gamePackage"),
                            startedAtMs = o.getLong("startedAtMs"),
                            endedAtMs = o.optLong("endedAtMs").takeIf { it > 0L },
                            before = o.optJSONObject("before")?.let { decodeSnapshot(it) },
                            after = o.optJSONObject("after")?.let { decodeSnapshot(it) },
                            closedPackages = o.optStringList("closedPackages"),
                            afterOptimize = o.optJSONObject("afterOptimize")?.let { decodeSnapshot(it) },
                            requestedClosePackages = o.optStringList("requestedClosePackages"),
                            closeSupported = o.optBoolean("closeSupported", true),
                            dndEnabledByUs = o.optBoolean("dndEnabledByUs", false),
                            dndRestored = if (o.has("dndRestored") && !o.isNull("dndRestored")) o.optBoolean("dndRestored") else null,
                            endReason = o.optString("endReason", "").ifEmpty { null },
                            notes = o.optStringList("notes"),
                        ),
                    )
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun encodeSessions(records: List<SessionRecord>): String {
        val arr = JSONArray()
        for (r in records) {
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("gamePackage", r.gamePackage)
                    .put("startedAtMs", r.startedAtMs)
                    .put("endedAtMs", r.endedAtMs ?: 0L)
                    .put("before", r.before?.let { encodeSnapshot(it) })
                    .put("after", r.after?.let { encodeSnapshot(it) })
                    .put("closedPackages", r.closedPackages.toJsonArray())
                    .put("afterOptimize", r.afterOptimize?.let { encodeSnapshot(it) })
                    .put("requestedClosePackages", r.requestedClosePackages.toJsonArray())
                    .put("closeSupported", r.closeSupported)
                    .put("dndEnabledByUs", r.dndEnabledByUs)
                    .put("dndRestored", r.dndRestored ?: JSONObject.NULL)
                    .put("endReason", r.endReason ?: "")
                    .put("notes", r.notes.toJsonArray()),
            )
        }
        return arr.toString()
    }

    private fun decodeSnapshot(o: JSONObject): MetricsSnapshot = MetricsSnapshot(
        timestampMs = o.optLong("timestampMs"),
        fps = o.optNullableFloat("fps"),
        fpsSupport = supportOf(o, "fpsSupport"),
        cpuTempC = o.optNullableFloat("cpuTempC"),
        cpuTempSupport = supportOf(o, "cpuTempSupport"),
        gpuTempC = o.optNullableFloat("gpuTempC"),
        gpuTempSupport = supportOf(o, "gpuTempSupport"),
        ramUsedBytes = o.optNullableLong("ramUsedBytes"),
        ramTotalBytes = o.optNullableLong("ramTotalBytes"),
        ramPercent = o.optNullableFloat("ramPercent"),
        ramSupport = supportOf(o, "ramSupport"),
        cpuUsage = o.optNullableFloat("cpuUsage"),
        cpuUsageSupport = supportOf(o, "cpuUsageSupport"),
        gpuUsage = o.optNullableFloat("gpuUsage"),
        gpuUsageSupport = supportOf(o, "gpuUsageSupport"),
        pingMs = o.optNullableLong("pingMs"),
        pingSupport = supportOf(o, "pingSupport"),
        vramUsedBytes = o.optNullableLong("vramUsedBytes"),
        vramSupport = supportOf(o, "vramSupport"),
        cpuTempSource = o.optNullableString("cpuTempSource"),
        gpuTempSource = o.optNullableString("gpuTempSource"),
        batteryTempC = o.optNullableFloat("batteryTempC"),
        batteryTempSupport = supportOf(o, "batteryTempSupport"),
        thermalStatus = o.optNullableString("thermalStatus")?.let { name ->
            ThermalStatusLevel.entries.firstOrNull { it.name == name }
        },
        thermalStatusSupport = supportOf(o, "thermalStatusSupport"),
        thermalHeadroom = o.optNullableFloat("thermalHeadroom"),
        thermalHeadroomSupport = supportOf(o, "thermalHeadroomSupport"),
        ramAvailableBytes = o.optNullableLong("ramAvailableBytes"),
        lowMemory = if (o.has("lowMemory") && !o.isNull("lowMemory")) o.optBoolean("lowMemory") else null,
        pingTarget = o.optNullableString("pingTarget"),
        pingStatus = o.optNullableString("pingStatus")?.let { name ->
            PingStatus.entries.firstOrNull { it.name == name }
        } ?: PingStatus.NOT_MEASURED,
    )

    private fun encodeSnapshot(s: MetricsSnapshot): JSONObject = JSONObject()
        .put("timestampMs", s.timestampMs)
        .put("fps", s.fps ?: JSONObject.NULL)
        .put("fpsSupport", s.fpsSupport.name)
        .put("cpuTempC", s.cpuTempC ?: JSONObject.NULL)
        .put("cpuTempSupport", s.cpuTempSupport.name)
        .put("gpuTempC", s.gpuTempC ?: JSONObject.NULL)
        .put("gpuTempSupport", s.gpuTempSupport.name)
        .put("ramUsedBytes", s.ramUsedBytes ?: JSONObject.NULL)
        .put("ramTotalBytes", s.ramTotalBytes ?: JSONObject.NULL)
        .put("ramPercent", s.ramPercent ?: JSONObject.NULL)
        .put("ramSupport", s.ramSupport.name)
        .put("cpuUsage", s.cpuUsage ?: JSONObject.NULL)
        .put("cpuUsageSupport", s.cpuUsageSupport.name)
        .put("gpuUsage", s.gpuUsage ?: JSONObject.NULL)
        .put("gpuUsageSupport", s.gpuUsageSupport.name)
        .put("pingMs", s.pingMs ?: JSONObject.NULL)
        .put("pingSupport", s.pingSupport.name)
        .put("vramUsedBytes", s.vramUsedBytes ?: JSONObject.NULL)
        .put("vramSupport", s.vramSupport.name)
        .put("cpuTempSource", s.cpuTempSource ?: JSONObject.NULL)
        .put("gpuTempSource", s.gpuTempSource ?: JSONObject.NULL)
        .put("batteryTempC", s.batteryTempC ?: JSONObject.NULL)
        .put("batteryTempSupport", s.batteryTempSupport.name)
        .put("thermalStatus", s.thermalStatus?.name ?: JSONObject.NULL)
        .put("thermalStatusSupport", s.thermalStatusSupport.name)
        .put("thermalHeadroom", s.thermalHeadroom ?: JSONObject.NULL)
        .put("thermalHeadroomSupport", s.thermalHeadroomSupport.name)
        .put("ramAvailableBytes", s.ramAvailableBytes ?: JSONObject.NULL)
        .put("lowMemory", s.lowMemory ?: JSONObject.NULL)
        .put("pingTarget", s.pingTarget ?: JSONObject.NULL)
        .put("pingStatus", s.pingStatus.name)

    private fun supportOf(o: JSONObject, key: String): MetricSupport =
        try {
            MetricSupport.valueOf(o.optString(key, MetricSupport.UNSUPPORTED.name))
        } catch (_: Throwable) {
            MetricSupport.UNSUPPORTED
        }

    private fun JSONObject.optNullableFloat(key: String): Float? =
        if (isNull(key) || !has(key)) null else optDouble(key).toFloat()

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (isNull(key) || !has(key)) null else optLong(key)

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key) || !has(key)) null else optString(key).ifEmpty { null }

    private fun JSONObject.optStringList(key: String): List<String> {
        val a = optJSONArray(key) ?: return emptyList()
        return buildList { for (j in 0 until a.length()) a.optString(j, null)?.let { add(it) } }
    }

    private fun List<String>.toJsonArray(): JSONArray = JSONArray().also { a -> forEach { a.put(it) } }

    private fun encodeActiveSession(s: OptimizationSession): String = JSONObject()
        .put("id", s.id)
        .put("gamePackage", s.gamePackage)
        .put("startedAtMs", s.startedAtMs)
        .put("before", s.before?.let { encodeSnapshot(it) })
        .put("afterOptimize", s.afterOptimize?.let { encodeSnapshot(it) })
        .put("closedPackages", s.closedPackages.toJsonArray())
        .put("requestedClosePackages", s.requestedClosePackages.toJsonArray())
        .put("skippedUnsafePackages", s.skippedUnsafePackages.toJsonArray())
        .put("closeSupported", s.closeSupported)
        .put("dndWasEnabledByUs", s.dndWasEnabledByUs)
        .put("previousDndFilter", s.previousDndFilter ?: JSONObject.NULL)
        .put("dndFilterSetByUs", s.dndFilterSetByUs ?: JSONObject.NULL)
        .put("notes", s.notes.toJsonArray())
        .toString()

    private fun decodeActiveSession(raw: String?): OptimizationSession? {
        if (raw.isNullOrBlank()) return null
        return try {
            val o = JSONObject(raw)
            OptimizationSession(
                id = o.getString("id"),
                gamePackage = o.getString("gamePackage"),
                startedAtMs = o.getLong("startedAtMs"),
                before = o.optJSONObject("before")?.let { decodeSnapshot(it) },
                afterOptimize = o.optJSONObject("afterOptimize")?.let { decodeSnapshot(it) },
                closedPackages = o.optStringList("closedPackages"),
                requestedClosePackages = o.optStringList("requestedClosePackages"),
                skippedUnsafePackages = o.optStringList("skippedUnsafePackages"),
                closeSupported = o.optBoolean("closeSupported", true),
                dndWasEnabledByUs = o.optBoolean("dndWasEnabledByUs", false),
                previousDndFilter = if (o.isNull("previousDndFilter")) null else o.optInt("previousDndFilter"),
                dndFilterSetByUs = if (o.isNull("dndFilterSetByUs")) null else o.optInt("dndFilterSetByUs"),
                notes = o.optStringList("notes"),
            )
        } catch (_: Throwable) {
            null
        }
    }

    private companion object {
        val KEY_HUD = stringPreferencesKey("hud")
        val KEY_MANUAL_GAMES = stringPreferencesKey("manual_games")
        val KEY_LAST_PLAYED = stringPreferencesKey("last_played")
        val KEY_PROFILES = stringPreferencesKey("profiles")
        val KEY_SESSIONS = stringPreferencesKey("sessions")
        val KEY_ACTIVE_SESSION = stringPreferencesKey("active_session")
        val KEY_PING_TARGET = stringPreferencesKey("ping_target")
        const val MAX_SESSIONS = 40
    }
}
