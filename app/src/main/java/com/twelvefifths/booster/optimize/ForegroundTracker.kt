package com.twelvefifths.booster.optimize

/**
 * Pure reducer over usage events. Only "activity resumed" (or the pre-Q
 * MOVE_TO_FOREGROUND) changes the foreground package; pauses alone do not,
 * because a pause is always followed by the next app's resume (launcher,
 * another app, ours). Replaying an overlapping window is idempotent.
 *
 * Owner: Script Nathan.
 */
class ForegroundTracker {
    enum class Kind { RESUMED, OTHER }

    data class Event(val packageName: String?, val kind: Kind, val timestampMs: Long)

    var current: String? = null
        private set
    var currentSinceMs: Long = 0L
        private set

    /** Applies events in the order given (UsageEvents are chronological). */
    fun apply(events: Iterable<Event>): String? {
        for (e in events) {
            if (e.kind != Kind.RESUMED) continue
            val pkg = e.packageName?.takeIf { it.isNotBlank() } ?: continue
            if (e.timestampMs < currentSinceMs) continue
            if (pkg != current) {
                current = pkg
            }
            currentSinceMs = e.timestampMs
        }
        return current
    }

    fun reset() {
        current = null
        currentSinceMs = 0L
    }
}

/**
 * Decides when an optimization session should auto-end because its game left
 * the foreground. Pure and clock-injected for tests.
 *
 * - Unknown foreground (null: no usage access / no data) never ends a session.
 * - Until the game has been seen in the foreground once, nothing ends it
 *   (the user may start the session first and launch the game afterwards).
 * - Packages in [ignoredPackages] (this app, System UI, permission dialogs)
 *   neither count as "left" nor reset the timer.
 * - Any other app in front for at least [graceMs] ends the session.
 *
 * Owner: Script Nathan.
 */
class GameExitPolicy(
    private val gamePackage: String,
    private val ignoredPackages: Set<String>,
    private val graceMs: Long = DEFAULT_GRACE_MS,
) {
    enum class Decision { KEEP, END }

    var gameSeen: Boolean = false
        private set
    private var leftAtMs: Long? = null

    fun onForeground(packageName: String?, nowMs: Long): Decision {
        when {
            packageName == null -> return Decision.KEEP
            packageName == gamePackage -> {
                gameSeen = true
                leftAtMs = null
                return Decision.KEEP
            }
            packageName in ignoredPackages -> {
                val left = leftAtMs ?: return Decision.KEEP
                return if (nowMs - left >= graceMs) Decision.END else Decision.KEEP
            }
            !gameSeen -> return Decision.KEEP
            else -> {
                val left = leftAtMs ?: nowMs.also { leftAtMs = it }
                return if (nowMs - left >= graceMs) Decision.END else Decision.KEEP
            }
        }
    }

    companion object {
        const val DEFAULT_GRACE_MS = 8_000L
        val SYSTEM_TRANSIENTS: Set<String> = setOf(
            "com.android.systemui",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.samsung.android.game.gametools",
        )
    }
}
