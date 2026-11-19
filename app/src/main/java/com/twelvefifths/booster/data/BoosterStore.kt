package com.twelvefifths.booster.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.twelvefifths.booster.metrics.MetricSupport
import com.twelvefifths.booster.metrics.MetricsSnapshot
import com.twelvefifths.booster.optimize.GameEntry
import com.twelvefifths.booster.optimize.OptimizationProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.boosterDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "twelve_fifths_booster",
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

    val hudPreferences: Flow<HudPreferences> = dataStore.data.map { prefs ->
        decodeHud(prefs[KEY_HUD])
    }

    val manualGames: Flow<List<GameEntry>> = dataStore.data.map { prefs ->
        decodeGames(prefs[KEY_MANUAL_GAMES])
    }

    val lastPlayed: Flow<Map<String, Long>> = dataStore.data.map { prefs ->
        decodeLastPlayed(prefs[KEY_LAST_PLAYED])
    }

    val sessionHistory: Flow<List<SessionRecord>> = dataStore.data.map { prefs ->
        decodeSessions(prefs[KEY_SESSIONS])
    }

    suspend fun setHudPreferences(value: HudPreferences) {
        dataStore.edit { it[KEY_HUD] = encodeHud(value) }
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
        val profiles = dataStore.data.map { prefs -> decodeProfiles(prefs[KEY_PROFILES]) }.first()
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
                    .put("suggestGameModeSettings", p.suggestGameModeSettings),
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
                    val o = arr.getJSONObject(i)
                    add(
                        SessionRecord(
                            id = o.getString("id"),
                            gamePackage = o.getString("gamePackage"),
                            startedAtMs = o.getLong("startedAtMs"),
                            endedAtMs = o.optLong("endedAtMs").takeIf { it > 0L },
                            before = o.optJSONObject("before")?.let { decodeSnapshot(it) },
                            after = o.optJSONObject("after")?.let { decodeSnapshot(it) },
                            closedPackages = o.optJSONArray("closedPackages")?.let { a ->
                                buildList { for (j in 0 until a.length()) add(a.getString(j)) }
                            } ?: emptyList(),
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
                    .put(
                        "closedPackages",
                        JSONArray().also { a -> r.closedPackages.forEach { a.put(it) } },
                    ),
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

    private companion object {
        val KEY_HUD = stringPreferencesKey("hud")
        val KEY_MANUAL_GAMES = stringPreferencesKey("manual_games")
        val KEY_LAST_PLAYED = stringPreferencesKey("last_played")
        val KEY_PROFILES = stringPreferencesKey("profiles")
        val KEY_SESSIONS = stringPreferencesKey("sessions")
        const val MAX_SESSIONS = 40
    }
}
