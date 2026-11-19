package com.twelvefifths.booster.optimize

/**
 * Per-game optimization options. Only user-selected background packages are
 * eligible to be closed; system packages are never included.
 *
 * Owner: Script Nathan.
 */
data class OptimizationProfile(
    val gamePackage: String,
    val closeBackgroundPackages: Set<String> = emptySet(),
    val enableDoNotDisturb: Boolean = false,
    val suggestBatterySaverSettings: Boolean = false,
    val suggestGameModeSettings: Boolean = false,
)
