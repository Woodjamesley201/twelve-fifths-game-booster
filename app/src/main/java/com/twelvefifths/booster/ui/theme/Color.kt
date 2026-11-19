package com.twelvefifths.booster.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Dark charcoal, neon blue / purple accents. No blur-dependent colors.
 *
 * Owner: Ui isaac.
 */
object Charcoal {
    val Night = Color(0xFF0B0D12)
    val NightDeep = Color(0xFF07080C)
    val NightRaised = Color(0xFF12141C)
    val NightHigh = Color(0xFF191C27)
    val Panel = Color(0xCC161822)
    val PanelFill = Color(0x99161A24)
    val Line = Color(0x33FFFFFF)
    val LineSoft = Color(0x1AFFFFFF)
    val Highlight = Color(0x22FFFFFF)
    val NeonBlue = Color(0xFF4F8CFF)
    val NeonBlueSoft = Color(0x664F8CFF)
    val NeonBlueFaint = Color(0x224F8CFF)
    val NeonPurple = Color(0xFFA855F7)
    val NeonPurpleSoft = Color(0x55A855F7)
    val NeonPurpleFaint = Color(0x22A855F7)
    val NeonCyan = Color(0xFF22D3EE)
    val Text = Color(0xFFF4F6FB)
    val TextMuted = Color(0xFF9AA3B5)
    val TextDim = Color(0xFF6D7587)
    val Danger = Color(0xFFFF6B6B)
    val Warn = Color(0xFFFBBF24)
    val Ok = Color(0xFF4ADE80)
}

object Accent {
    val Blue = Charcoal.NeonBlue
    val Purple = Charcoal.NeonPurple
    val Cyan = Charcoal.NeonCyan
}
