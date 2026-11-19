package com.twelvefifths.booster.optimize

import com.twelvefifths.booster.data.SessionRecord
import com.twelvefifths.booster.metrics.MetricsSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEngineTest {

    private class FakePlatform(
        override val canKillOtherApps: Boolean = true,
        var dndAccess: Boolean = true,
        var dnd: Int? = DndFilter.ALL,
        val unsafe: Set<String> = setOf("com.android.systemui", "com.launcher"),
        var powerSave: Boolean? = false,
    ) : SessionPlatform {
        override val selfPackage = "com.twelvefifths.booster"
        val killed = mutableListOf<String>()
        val dndSets = mutableListOf<Int>()
        var clock = 1_000L
        private var ids = 0

        override fun nowMs() = clock++
        override fun newId() = "s${++ids}"
        override fun isClosable(packageName: String) = packageName !in unsafe
        override fun killBackgroundProcesses(packageName: String): Boolean {
            killed += packageName
            return true
        }
        override fun hasDndAccess() = dndAccess
        override fun currentDndFilter() = dnd
        override fun setDndFilter(filter: Int): Boolean {
            dndSets += filter
            dnd = filter
            return true
        }
        override fun isPowerSaveMode() = powerSave
    }

    private class FakePersistence(var active: OptimizationSession? = null) : SessionPersistence {
        val records = mutableListOf<SessionRecord>()
        override suspend fun appendSession(record: SessionRecord) {
            records.removeAll { it.id == record.id }
            records.add(0, record)
        }
        override suspend fun saveActiveSession(session: OptimizationSession?) {
            active = session
        }
        override suspend fun loadActiveSession() = active
    }

    private val game = GameEntry("com.game", "Game", isManual = false)

    private fun engine(platform: FakePlatform, persistence: FakePersistence = FakePersistence()): SessionEngine {
        var t = 0L
        return SessionEngine(platform, persistence, SnapshotProvider { MetricsSnapshot.empty(++t) })
    }

    @Test
    fun startClosesOnlySafeSelectedAppsAndNeverTheGameOrSelf() = runBlocking {
        val p = FakePlatform()
        val e = engine(p)
        val profile = OptimizationProfile(
            gamePackage = "com.game",
            closeBackgroundPackages = setOf("com.chat", "com.game", "com.twelvefifths.booster", "com.android.systemui", "com.launcher"),
        )
        val s = e.start(game, profile).getOrThrow()
        assertEquals(listOf("com.chat"), p.killed)
        assertEquals(listOf("com.chat"), s.closedPackages)
        assertEquals(listOf("com.android.systemui", "com.launcher"), s.skippedUnsafePackages)
        assertNotNull(s.before)
        assertNotNull(s.afterOptimize)
    }

    @Test
    fun android14PlusRecordsThatNothingWasClosed() = runBlocking {
        val p = FakePlatform(canKillOtherApps = false)
        val s = engine(p).start(game, OptimizationProfile("com.game", closeBackgroundPackages = setOf("com.chat"))).getOrThrow()
        assertTrue(p.killed.isEmpty())
        assertTrue(s.closedPackages.isEmpty())
        assertEquals(listOf("com.chat"), s.requestedClosePackages)
        assertFalse(s.closeSupported)
        assertTrue(s.notes.any { it.contains("Android 14+") })
    }

    @Test
    fun dndEnabledAndRestoredToPreviousFilter() = runBlocking {
        val p = FakePlatform(dnd = DndFilter.ALL)
        val persistence = FakePersistence()
        val e = engine(p, persistence)
        val s = e.start(game, OptimizationProfile("com.game", enableDoNotDisturb = true)).getOrThrow()
        assertTrue(s.dndWasEnabledByUs)
        assertEquals(DndFilter.PRIORITY, p.dnd)
        assertNotNull("active session persisted for crash recovery", persistence.active)

        val ended = e.end(SessionEndReason.USER).getOrThrow()
        assertEquals(DndFilter.ALL, p.dnd)
        assertEquals(true, ended.dndRestored)
        assertNotNull(ended.after)
        assertNotNull(ended.endedAtMs)
        assertEquals(SessionEndReason.USER, ended.endReason)
        assertNull("active session cleared", persistence.active)
        assertEquals(1, persistence.records.size)
        assertEquals("USER", persistence.records[0].endReason)
        assertNull(e.activeSession.value)
    }

    @Test
    fun dndAlreadyOnIsLeftAloneAndNotRestored() = runBlocking {
        val p = FakePlatform(dnd = DndFilter.ALARMS)
        val e = engine(p)
        val s = e.start(game, OptimizationProfile("com.game", enableDoNotDisturb = true)).getOrThrow()
        assertFalse(s.dndWasEnabledByUs)
        e.end(SessionEndReason.USER)
        assertTrue(p.dndSets.isEmpty())
        assertEquals(DndFilter.ALARMS, p.dnd)
    }

    @Test
    fun userChangeDuringSessionIsRespected() = runBlocking {
        val p = FakePlatform(dnd = DndFilter.ALL)
        val e = engine(p)
        e.start(game, OptimizationProfile("com.game", enableDoNotDisturb = true)).getOrThrow()
        p.dnd = DndFilter.NONE // user switched to total silence mid-game
        val ended = e.end(SessionEndReason.GAME_LEFT_FOREGROUND).getOrThrow()
        assertEquals(DndFilter.NONE, p.dnd)
        assertEquals(false, ended.dndRestored)
        assertEquals(SessionEndReason.GAME_LEFT_FOREGROUND, ended.endReason)
    }

    @Test
    fun noDndAccessMeansNoChangeAndNoRestore() = runBlocking {
        val p = FakePlatform(dndAccess = false)
        val e = engine(p)
        val s = e.start(game, OptimizationProfile("com.game", enableDoNotDisturb = true)).getOrThrow()
        assertFalse(s.dndWasEnabledByUs)
        assertTrue(s.notes.any { it.contains("not granted") })
        val ended = e.end(SessionEndReason.USER).getOrThrow()
        assertNull(ended.dndRestored)
        assertTrue(p.dndSets.isEmpty())
    }

    @Test
    fun revokedAccessBeforeEndIsReported() = runBlocking {
        val p = FakePlatform()
        val e = engine(p)
        e.start(game, OptimizationProfile("com.game", enableDoNotDisturb = true)).getOrThrow()
        p.dndAccess = false
        val ended = e.end(SessionEndReason.USER).getOrThrow()
        assertEquals(false, ended.dndRestored)
        assertTrue(ended.notes.any { it.contains("revoked") })
    }

    @Test
    fun staleSessionFromDeadProcessIsRestoredOnce() = runBlocking {
        val p = FakePlatform(dnd = DndFilter.PRIORITY)
        val stale = OptimizationSession(
            id = "old",
            gamePackage = "com.game",
            startedAtMs = 1L,
            dndWasEnabledByUs = true,
            previousDndFilter = DndFilter.ALL,
            dndFilterSetByUs = DndFilter.PRIORITY,
        )
        val persistence = FakePersistence(active = stale)
        val e = engine(p, persistence)
        val recovered = e.recoverStaleSession()
        assertNotNull(recovered)
        assertEquals(SessionEndReason.RECOVERED_AFTER_RESTART, recovered!!.endReason)
        assertEquals(DndFilter.ALL, p.dnd)
        assertNull(persistence.active)
        assertEquals("old", persistence.records.single().id)
        assertNull("second call is a no-op", e.recoverStaleSession())
        assertEquals(1, p.dndSets.size)
    }

    @Test
    fun startRecoversStaleSessionFirst() = runBlocking {
        val p = FakePlatform(dnd = DndFilter.PRIORITY)
        val persistence = FakePersistence(
            active = OptimizationSession("old", "com.game", 1L, dndWasEnabledByUs = true, previousDndFilter = DndFilter.ALL),
        )
        val e = engine(p, persistence)
        val s = e.start(game, OptimizationProfile("com.game")).getOrThrow()
        assertEquals(DndFilter.ALL, p.dnd)
        assertEquals(s.id, e.activeSession.value?.id)
    }

    @Test
    fun doubleStartAndEndWithoutSessionFail() = runBlocking {
        val e = engine(FakePlatform())
        assertTrue(e.end(SessionEndReason.USER).isFailure)
        assertTrue(e.start(game, OptimizationProfile("com.game")).isSuccess)
        assertTrue(e.start(game, OptimizationProfile("com.game")).isFailure)
        assertTrue(e.end(SessionEndReason.USER).isSuccess)
        assertTrue(e.end(SessionEndReason.USER).isFailure)
    }

    @Test
    fun platformAndPersistenceFailuresDoNotBreakTheSession() = runBlocking {
        val p = object : SessionPlatform by FakePlatform() {
            override fun killBackgroundProcesses(packageName: String): Boolean = throw SecurityException()
            override fun isPowerSaveMode(): Boolean = throw IllegalStateException()
        }
        val brokenStore = object : SessionPersistence {
            override suspend fun appendSession(record: SessionRecord) = throw java.io.IOException()
            override suspend fun saveActiveSession(session: OptimizationSession?) = throw java.io.IOException()
            override suspend fun loadActiveSession(): OptimizationSession? = throw java.io.IOException()
        }
        val e = SessionEngine(p, brokenStore, SnapshotProvider { throw IllegalStateException() })
        val s = e.start(game, OptimizationProfile("com.game", closeBackgroundPackages = setOf("com.chat"))).getOrThrow()
        assertTrue(s.closedPackages.isEmpty())
        assertNull(s.before)
        assertTrue(e.end(SessionEndReason.USER).isSuccess)
    }

    @Test
    fun batterySaverStillOnIsNoted() = runBlocking {
        val p = FakePlatform(powerSave = true)
        val e = engine(p)
        e.start(game, OptimizationProfile("com.game", suggestBatterySaverSettings = true))
        val ended = e.end(SessionEndReason.USER).getOrThrow()
        assertTrue(ended.notes.any { it.contains("Battery saver is still on") })
    }

    @Test
    fun recordCarriesBeforeAndAfterSamples() = runBlocking {
        val persistence = FakePersistence()
        val e = engine(FakePlatform(), persistence)
        e.start(game, OptimizationProfile("com.game"))
        e.end(SessionEndReason.USER)
        val r = persistence.records.single()
        assertNotNull(r.before)
        assertNotNull(r.afterOptimize)
        assertNotNull(r.after)
        assertTrue(r.before!!.timestampMs < r.afterOptimize!!.timestampMs)
        assertTrue(r.afterOptimize!!.timestampMs < r.after!!.timestampMs)
    }
}
