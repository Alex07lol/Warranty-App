package com.warrantyvault.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Consistent geometry + spacing tokens. Do not invent arbitrary radii/gaps in screens:
 * small control 10-12, medium card 16-18, large hero 20-24, glass nav 24-30;
 * spacing scale 4/8/12/16/20/24/32.
 */
object WvDimens {
    // Corner radii
    val RadiusSmall: Dp = 12.dp
    val RadiusMedium: Dp = 16.dp
    val RadiusLarge: Dp = 22.dp
    val RadiusGlassNav: Dp = 28.dp
    val RadiusPill: Dp = 999.dp

    // Spacing scale
    val Space1: Dp = 4.dp
    val Space2: Dp = 8.dp
    val Space3: Dp = 12.dp
    val Space4: Dp = 16.dp
    val Space5: Dp = 20.dp
    val Space6: Dp = 24.dp
    val Space8: Dp = 32.dp

    // Screen gutter
    val ScreenGutter: Dp = 16.dp

    // Floating glass nav
    val NavHeight: Dp = 62.dp
    val NavBottomPad: Dp = 10.dp
    val NavHorizontalPad: Dp = 18.dp

    // Component sizes
    val Thumb: Dp = 52.dp
    val TouchTarget: Dp = 48.dp
}
