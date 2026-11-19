package com.twelvefifths.booster.ui.format

import com.twelvefifths.booster.optimize.LaunchableApp

/**
 * Pure filter/sort for the add-game picker: drops apps already in the library,
 * hides system apps unless asked (system *games* always show), matches label
 * or package, and lists games first.
 *
 * Owner: Ui isaac.
 */
object AddGamePicker {
    fun filter(
        apps: List<LaunchableApp>,
        query: String,
        showSystem: Boolean,
        excluded: Set<String>,
    ): List<LaunchableApp> {
        val q = query.trim().lowercase()
        return apps.asSequence()
            .filter { it.packageName !in excluded }
            .filter { showSystem || !it.isSystem || it.isGame }
            .filter { q.isEmpty() || it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
            .sortedWith(compareByDescending<LaunchableApp> { it.isGame }.thenBy { it.label.lowercase() })
            .toList()
    }
}
