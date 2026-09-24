package com.warrantyvault.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Consistent geometry + spacing tokens, mirroring the web stylesheet's `--r-*` / `--gutter`
 * custom properties. Do not invent arbitrary radii/gaps in screens.
 */
object WvDimens {
    // Corner radii — web --r-xs / --r-sm / --r-md / --r-lg / --r-xl / --r-pill
    val RadiusXSmall: Dp = 8.dp
    val RadiusSmall: Dp = 10.dp
    val RadiusMedium: Dp = 14.dp
    val RadiusLarge: Dp = 18.dp
    val RadiusExtraLarge: Dp = 24.dp
    /** The floating nav is a full pill (web `.bottom-nav { border-radius: var(--r-pill) }`). */
    val RadiusGlassNav: Dp = 999.dp
    val RadiusPill: Dp = 999.dp

    // Spacing scale
    val Space1: Dp = 4.dp
    val Space2: Dp = 8.dp
    val Space3: Dp = 12.dp
    val Space4: Dp = 16.dp
    val Space5: Dp = 20.dp
    val Space6: Dp = 24.dp
    val Space8: Dp = 32.dp

    // Screen gutter — web --gutter
    val ScreenGutter: Dp = 18.dp

    // Floating glass nav — web --nav-float-h, bottom: max(14px, safe-area), width: 100% - 32px
    val NavHeight: Dp = 62.dp
    val NavBottomPad: Dp = 14.dp
    val NavHorizontalPad: Dp = 16.dp

    // Component sizes — web product tile 52px, stat icon wrapper 34px
    val Thumb: Dp = 52.dp
    val StatIcon: Dp = 34.dp
    val TouchTarget: Dp = 48.dp
}
