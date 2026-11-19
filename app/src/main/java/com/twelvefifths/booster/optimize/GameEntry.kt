package com.twelvefifths.booster.optimize

/**
 * A game in the library: auto-detected via [android.content.pm.ApplicationInfo.CATEGORY_GAME]
 * or added manually by the user.
 *
 * Owner: Script Nathan.
 */
data class GameEntry(
    val packageName: String,
    val displayName: String,
    val isManual: Boolean,
    val lastPlayedMs: Long? = null,
    val isSystem: Boolean = false,
)
