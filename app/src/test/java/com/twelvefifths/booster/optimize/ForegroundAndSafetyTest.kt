package com.twelvefifths.booster.optimize

import com.twelvefifths.booster.optimize.ForegroundTracker.Event
import com.twelvefifths.booster.optimize.ForegroundTracker.Kind
import com.twelvefifths.booster.optimize.GameExitPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundAndSafetyTest {

    @Test
    fun trackerFollowsResumesAndKeepsStateWithoutNewEvents() {
        val t = ForegroundTracker()
        assertNull(t.apply(emptyList()))
        assertEquals("com.game", t.apply(listOf(Event("com.launcher", Kind.RESUMED, 1), Event("com.game", Kind.RESUMED, 2))))
        assertEquals("game stays foreground when no events arrive", "com.game", t.apply(emptyList()))
        assertEquals("pauses alone do not change it", "com.game", t.apply(listOf(Event("com.game", Kind.OTHER, 3))))
    }

    @Test
    fun trackerReplayOfOverlapIsIdempotent() {
        val t = ForegroundTracker()
        val window = listOf(Event("a", Kind.RESUMED, 10), Event("b", Kind.RESUMED, 20))
        t.apply(window)
        assertEquals("b", t.apply(window))
        assertEquals("c", t.apply(window + Event("c", Kind.RESUMED, 30)))
        t.reset()
        assertNull(t.current)
    }

    @Test
    fun exitPolicyWaitsForGameThenGrace() {
        val p = GameExitPolicy("com.game", setOf("com.twelvefifths.booster"), graceMs = 5_000)
        assertEquals("game not launched yet", Decision.KEEP, p.onForeground("com.launcher", 0))
        assertEquals(Decision.KEEP, p.onForeground("com.game", 1_000))
        assertEquals(Decision.KEEP, p.onForeground("com.launcher", 2_000))
        assertEquals(Decision.KEEP, p.onForeground("com.launcher", 6_000))
        assertEquals(Decision.END, p.onForeground("com.launcher", 7_000))
    }

    @Test
    fun exitPolicyReturningResetsTimerAndUnknownNeverEnds() {
        val p = GameExitPolicy("com.game", emptySet(), graceMs = 5_000)
        p.onForeground("com.game", 0)
        p.onForeground("com.chat", 1_000)
        p.onForeground("com.game", 4_000)
        assertEquals(Decision.KEEP, p.onForeground("com.chat", 8_000))
        assertEquals(Decision.KEEP, p.onForeground(null, 100_000))
    }

    @Test
    fun exitPolicyIgnoresOwnAppAndSystemUi() {
        val p = GameExitPolicy("com.game", setOf("com.twelvefifths.booster", "com.android.systemui"), graceMs = 5_000)
        p.onForeground("com.game", 0)
        assertEquals(Decision.KEEP, p.onForeground("com.twelvefifths.booster", 60_000))
        assertEquals(Decision.KEEP, p.onForeground("com.android.systemui", 120_000))
        assertTrue(p.gameSeen)
    }

    @Test
    fun closeRulesProtectSystemSelfEssentialAndUnknown() {
        val user = PackageFacts("com.chat", installed = true, isSystem = false, isPersistent = false)
        assertTrue(CloseRules.isClosable("com.chat", user, "me", emptySet()))
        assertFalse(CloseRules.isClosable("me", user.copy(packageName = "me"), "me", emptySet()))
        assertFalse(CloseRules.isClosable("com.chat", user.copy(isSystem = true), "me", emptySet()))
        assertFalse(CloseRules.isClosable("com.chat", user.copy(isPersistent = true), "me", emptySet()))
        assertFalse("unresolvable package", CloseRules.isClosable("com.chat", null, "me", emptySet()))
        assertFalse("launcher / IME", CloseRules.isClosable("com.chat", user, "me", setOf("com.chat")))
        assertFalse(CloseRules.isClosable("com.android.systemui", user.copy(packageName = "com.android.systemui"), "me", emptySet()))
        assertFalse(CloseRules.isClosable("", user, "me", emptySet()))
    }

    @Test
    fun gameMergeCombinesDetectedManualAndLastPlayed() {
        val detected = listOf(GameEntry("com.a", "Alpha", false), GameEntry("com.b", "Beta", false))
        val manual = listOf(GameEntry("com.b", "Beta", true), GameEntry("com.gone", "Gone", true), GameEntry("com.c", "Cee", true))
        val merged = GameMerge.merge(detected, manual, mapOf("com.b" to 50L, "com.c" to 10L)) { it != "com.gone" }
        assertEquals(listOf("com.b", "com.c", "com.a", "com.gone"), merged.map { it.packageName })
        assertTrue(merged.first { it.packageName == "com.b" }.isManual)
        assertFalse(merged.first { it.packageName == "com.gone" }.isInstalled)
        assertEquals(50L, merged.first().lastPlayedMs)
    }
}
