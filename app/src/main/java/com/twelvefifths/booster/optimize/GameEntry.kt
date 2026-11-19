package com.twelvefifths.booster.optimize

/**
 * A game in the library: auto-detected via [android.content.pm.ApplicationInfo.CATEGORY_GAME]
 * (or the legacy `android:isGame` flag) or added manually by the user.
 *
 * [isInstalled] is false for a manual entry whose package is no longer installed.
 *
 * Owner: Script Nathan.
 */
data class GameEntry(
    val packageName: String,
    val displayName: String,
    val isManual: Boolean,
    val lastPlayedMs: Long? = null,
    val isSystem: Boolean = false,
    val isInstalled: Boolean = true,
)

/** Pure merge of detected + manual games, used by [DefaultGameLibrary]. */
object GameMerge {
    fun merge(
        detected: List<GameEntry>,
        manual: List<GameEntry>,
        lastPlayed: Map<String, Long>,
        isInstalled: (String) -> Boolean,
    ): List<GameEntry> {
        val byPackage = LinkedHashMap<String, GameEntry>()
        for (game in detected) {
            byPackage[game.packageName] = game.copy(lastPlayedMs = lastPlayed[game.packageName] ?: game.lastPlayedMs)
        }
        for (game in manual) {
            val existing = byPackage[game.packageName]
            byPackage[game.packageName] = if (existing == null) {
                game.copy(
                    isManual = true,
                    lastPlayedMs = lastPlayed[game.packageName] ?: game.lastPlayedMs,
                    isInstalled = isInstalled(game.packageName),
                )
            } else {
                existing.copy(
                    isManual = true,
                    lastPlayedMs = lastPlayed[game.packageName] ?: existing.lastPlayedMs ?: game.lastPlayedMs,
                )
            }
        }
        return byPackage.values.sortedWith(
            compareByDescending<GameEntry> { it.isInstalled }
                .thenByDescending { it.lastPlayedMs ?: 0L }
                .thenBy { it.displayName.lowercase() },
        )
    }
}
