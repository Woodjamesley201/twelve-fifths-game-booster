package com.twelvefifths.booster.optimize

/**
 * Per-game optimization options, persisted per package in BoosterStore.
 * Only user-selected background packages are eligible to be closed; system
 * and essential packages are filtered out again at session start.
 *
 * [autoEndWhenGameLeaves]: end the session and restore everything once the game
 * has been out of the foreground for a few seconds (needs usage access).
 * [launchGameOnStart]: launch the game right after the session starts. Ignored
 * when a settings suggestion is opened, so the two activities do not fight.
 *
 * Owner: Script Nathan.
 */
data class OptimizationProfile(
    val gamePackage: String,
    val closeBackgroundPackages: Set<String> = emptySet(),
    val enableDoNotDisturb: Boolean = false,
    val suggestBatterySaverSettings: Boolean = false,
    val suggestGameModeSettings: Boolean = false,
    val autoEndWhenGameLeaves: Boolean = true,
    val launchGameOnStart: Boolean = false,
)
