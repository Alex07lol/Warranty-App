package com.warrantyvault.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type scale taken from the web stylesheet so both front-ends read the same:
 *
 *  - display / greeting name: 30sp, weight 800, letter-spacing -0.8sp (web `.greeting-name`)
 *  - stat value: 26-32sp, weight 800, letter-spacing -1sp (web `.stat-value`)
 *  - body 14-15sp, secondary 12-12.5sp, caption 11sp
 *
 * System sans-serif stands in for Inter; the metrics are tuned to the same rhythm so the layout
 * reads identically once Inter is bundled.
 */
val FontFamilyDefault = FontFamily.SansSerif
val FontFamilyMono = FontFamily.Monospace

val WarrantyVaultTypography = Typography(
    // Hero numbers (34sp)
    displaySmall = TextStyle(
        fontFamily = FontFamilyDefault,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-1).sp
    ),
    // Greeting / display title: web `.greeting-name` clamp(22px, 5.4vw, 30px), weight 800
    headlineLarge = TextStyle(
        fontFamily = FontFamilyDefault,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.8).sp
    ),
    // Screen titles
    headlineMedium = TextStyle(
        fontFamily = FontFamilyDefault,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.5).sp
    ),
    // Primary values: web `.stat-value` clamp(26px, 7.5vw, 32px), weight 800
    headlineSmall = TextStyle(
        fontFamily = FontFamilyDefault,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 29.sp,
        letterSpacing = (-1).sp
    ),
    // Section titles
    titleLarge = TextStyle(
        fontFamily = FontFamilyDefault,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp
    ),
    // Card titles: web `.product-info-name` 14.5px / 700
    titleMedium = TextStyle(
        fontFamily = FontFamilyDefault,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamilyDefault,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamilyDefault,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamilyDefault,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    // Secondary text: web `.product-info-brand` 12px, `.stat-sub` 11.5px
    bodySmall = TextStyle(
        fontFamily = FontFamilyDefault,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.sp
    ),
    // Buttons: web `.btn` 14px / 650
    labelLarge = TextStyle(
        fontFamily = FontFamilyDefault,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    // Labels: web `.stat-label` 12.5px / 650
    labelMedium = TextStyle(
        fontFamily = FontFamilyDefault,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.5.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamilyDefault,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.sp
    )
)

/**
 * Micro-labels that the web stylesheet defines outside its base scale. Compose has no
 * `text-transform`, so callers must pass ALREADY-UPPERCASE text.
 */
object WvTextStyles {
    /** Web `.greeting`: 12px, 600, uppercase, letter-spacing .07em. */
    val Eyebrow = TextStyle(
        fontFamily = FontFamilyDefault,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.84.sp
    )

    /** Web `.attention-label`: 11.5px, 700, uppercase, letter-spacing .12em. */
    val SectionLabel = TextStyle(
        fontFamily = FontFamilyDefault,
        fontWeight = FontWeight.Bold,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.38.sp
    )
}
