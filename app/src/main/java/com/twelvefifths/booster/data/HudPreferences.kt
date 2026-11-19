package com.twelvefifths.booster.data

/**
 * User-configurable HUD appearance and which stats to show.
 *
 * Colors are ARGB ints. [accentArgb] defaults to neon blue.
 * Owner: Script Nathan (storage); Ui isaac (visual interpretation).
 */
data class HudPreferences(
    val posX: Int = 24,
    val posY: Int = 120,
    val widthDp: Int = 220,
    val compact: Boolean = true,
    val opacity: Float = 0.86f,
    val accentArgb: Int = 0xFF4F8CFF.toInt(),
    val showFps: Boolean = true,
    val showCpuTemp: Boolean = true,
    val showGpuTemp: Boolean = true,
    val showRam: Boolean = true,
    val showCpuUsage: Boolean = true,
    val showGpuUsage: Boolean = true,
    val showPing: Boolean = true,
    val showVram: Boolean = true,
    val onboardingDone: Boolean = false,
)
