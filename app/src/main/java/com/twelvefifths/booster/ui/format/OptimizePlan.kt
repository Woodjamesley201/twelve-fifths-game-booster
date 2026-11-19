package com.twelvefifths.booster.ui.format

import com.twelvefifths.booster.optimize.OptimizationProfile

/**
 * Builds the human-readable "what Optimize will do" list from a profile.
 * Mirrors DefaultOptimizer's real behavior; pure so it is unit-tested.
 *
 * Owner: Ui isaac.
 */
object OptimizePlan {
    enum class Kind { DO, SKIP, NEVER }

    data class Step(val kind: Kind, val title: String, val detail: String? = null)

    fun build(
        profile: OptimizationProfile,
        closablePackages: Set<String>,
        dndGranted: Boolean,
        labelOf: (String) -> String = { it },
        canKillOtherApps: Boolean = true,
        usageGranted: Boolean = true,
    ): List<Step> = buildList {
        add(Step(Kind.DO, "Capture a before snapshot", "Real metrics only, for the before/after comparison."))
        val toClose = profile.closeBackgroundPackages
            .filter { it != profile.gamePackage && it in closablePackages }
        if (!canKillOtherApps) {
            add(
                Step(
                    Kind.SKIP,
                    "Closing other apps isn't available on this Android version",
                    "Android 14+ ignores it. Use App info → Force stop for any app you want gone.",
                ),
            )
        } else if (toClose.isEmpty()) {
            add(Step(Kind.SKIP, "Close no background apps", "Tick apps in the profile to close them."))
        } else {
            add(
                Step(
                    Kind.DO,
                    "Close ${toClose.size} background app(s) you selected",
                    toClose.joinToString { labelOf(it) } + ". Android may restart some of them.",
                ),
            )
        }
        when {
            profile.enableDoNotDisturb && dndGranted ->
                add(Step(Kind.DO, "Turn on Do Not Disturb", "Priority only; set back when you Restore."))
            profile.enableDoNotDisturb && !dndGranted ->
                add(Step(Kind.SKIP, "Skip Do Not Disturb", "Do Not Disturb access is not granted."))
            else -> add(Step(Kind.SKIP, "Leave Do Not Disturb as it is"))
        }
        if (profile.suggestBatterySaverSettings) {
            add(Step(Kind.DO, "Open battery saver settings", "You flip the switch; the app does not change it."))
        }
        if (profile.suggestGameModeSettings) {
            add(Step(Kind.DO, "Open a game-related Settings screen", "There is no shared Android game-mode switch."))
        }
        if (profile.launchGameOnStart) {
            val openedSettings = profile.suggestBatterySaverSettings || profile.suggestGameModeSettings
            add(
                if (openedSettings) Step(Kind.SKIP, "Don't auto-launch the game", "A Settings screen opens instead; launch it when you're done.")
                else Step(Kind.DO, "Launch the game"),
            )
        }
        if (profile.autoEndWhenGameLeaves) {
            add(
                if (usageGranted) Step(Kind.DO, "Restore automatically after you leave the game", "About 8 s after it leaves the screen.")
                else Step(Kind.SKIP, "Can't auto-restore", "Usage access is not granted; tap Restore yourself."),
            )
        }
        add(Step(Kind.NEVER, "Never touches system apps or hidden OS settings", "And it cannot raise a game's FPS directly."))
    }
}
