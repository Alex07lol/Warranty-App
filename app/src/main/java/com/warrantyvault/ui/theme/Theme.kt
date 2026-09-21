package com.warrantyvault.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Extended semantic tokens beyond Material's scheme. Every screen consumes these
 * instead of hardcoding colors; the visual hierarchy lives here:
 * neutral surfaces dominate, indigo = interaction, green = success,
 * amber = attention, red = error, blue = information.
 */
@Immutable
data class WvColors(
    // Surfaces (neutral, dominate the UI)
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceHighest: Color,

    // Interaction / brand
    val primary: Color,
    val primaryPressed: Color,
    val onPrimary: Color,
    val primarySoft: Color,

    // Text
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,

    // Lines
    val border: Color,
    val borderSubtle: Color,

    // Semantic
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val error: Color,
    val errorSoft: Color,
    val info: Color,
    val infoSoft: Color,

    // Glass navigation
    val glassNav: Color,
    val glassBorder: Color
) {
    /** Subtle hero gradient for the dashboard hero card only. */
    val heroBrush: Brush
        get() = Brush.linearGradient(listOf(surfaceElevated, surfaceHighest))

    /** Status -> (color, soft container) from the central tokens. */
    fun statusColors(status: String): Pair<Color, Color> = when (status) {
        "active" -> success to successSoft
        "expiring_soon" -> warning to warningSoft
        "expired" -> error to errorSoft
        else -> textMuted to borderSubtle
    }
}

fun darkWvColors(): WvColors = WvColors(
    background = Color(0xFF090D12),
    surface = Color(0xFF101720),
    surfaceElevated = Color(0xFF151F2A),
    surfaceHighest = Color(0xFF1B2633),

    primary = Color(0xFF5B7CFF),
    primaryPressed = Color(0xFF4869E8),
    onPrimary = Color(0xFF0A0F1A),
    primarySoft = Color(0xFF5B7CFF).copy(alpha = 0.14f),

    textPrimary = Color(0xFFF3F6FA),
    textSecondary = Color(0xFFA8B3C2),
    textMuted = Color(0xFF778496),

    border = Color(0xFF263341),
    borderSubtle = Color(0xFF1A242F),

    success = Color(0xFF4CC9A0),
    successSoft = Color(0xFF4CC9A0).copy(alpha = 0.14f),
    warning = Color(0xFFF0B35A),
    warningSoft = Color(0xFFF0B35A).copy(alpha = 0.14f),
    error = Color(0xFFF06B78),
    errorSoft = Color(0xFFF06B78).copy(alpha = 0.13f),
    info = Color(0xFF79A7FF),
    infoSoft = Color(0xFF79A7FF).copy(alpha = 0.13f),

    glassNav = Color(0xFF121922).copy(alpha = 0.78f),
    glassBorder = Color(0xFFFFFFFF).copy(alpha = 0.08f)
)

fun lightWvColors(): WvColors = WvColors(
    background = Color(0xFFF4F5F7),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFBFBFD),
    surfaceHighest = Color(0xFFF1F2F6),

    primary = Color(0xFF4A63D8),
    primaryPressed = Color(0xFF3B52C2),
    onPrimary = Color(0xFFFFFFFF),
    primarySoft = Color(0xFF4A63D8).copy(alpha = 0.10f),

    textPrimary = Color(0xFF1A1F28),
    textSecondary = Color(0xFF525B6B),
    textMuted = Color(0xFF7A8494),

    border = Color(0xFFE1E4EA),
    borderSubtle = Color(0xFFEDEFF3),

    success = Color(0xFF2E8F6E),
    successSoft = Color(0xFF2E8F6E).copy(alpha = 0.11f),
    warning = Color(0xFFB57A1E),
    warningSoft = Color(0xFFB57A1E).copy(alpha = 0.12f),
    error = Color(0xFFC4484F),
    errorSoft = Color(0xFFC4484F).copy(alpha = 0.10f),
    info = Color(0xFF3E72C9),
    infoSoft = Color(0xFF3E72C9).copy(alpha = 0.10f),

    glassNav = Color(0xFFFFFFFF).copy(alpha = 0.82f),
    glassBorder = Color(0xFF1A1F28).copy(alpha = 0.08f)
)

private val DarkMaterialScheme = darkColorScheme(
    primary = darkWvColors().primary,
    onPrimary = darkWvColors().onPrimary,
    secondary = darkWvColors().info,
    tertiary = darkWvColors().success,
    error = darkWvColors().error,
    background = darkWvColors().background,
    onBackground = darkWvColors().textPrimary,
    surface = darkWvColors().surface,
    onSurface = darkWvColors().textPrimary,
    surfaceVariant = darkWvColors().surfaceElevated,
    onSurfaceVariant = darkWvColors().textSecondary,
    surfaceContainer = darkWvColors().surfaceElevated,
    surfaceContainerHigh = darkWvColors().surfaceHighest,
    surfaceContainerHighest = darkWvColors().surfaceHighest,
    outline = darkWvColors().border,
    outlineVariant = darkWvColors().borderSubtle,
    primaryContainer = darkWvColors().primarySoft,
    onPrimaryContainer = darkWvColors().primary,
    secondaryContainer = darkWvColors().infoSoft,
    tertiaryContainer = darkWvColors().warningSoft
)

private val LightMaterialScheme = lightColorScheme(
    primary = lightWvColors().primary,
    onPrimary = lightWvColors().onPrimary,
    secondary = lightWvColors().info,
    tertiary = lightWvColors().success,
    error = lightWvColors().error,
    background = lightWvColors().background,
    onBackground = lightWvColors().textPrimary,
    surface = lightWvColors().surface,
    onSurface = lightWvColors().textPrimary,
    surfaceVariant = lightWvColors().surfaceElevated,
    onSurfaceVariant = lightWvColors().textSecondary,
    surfaceContainer = lightWvColors().surfaceElevated,
    surfaceContainerHigh = lightWvColors().surfaceHighest,
    surfaceContainerHighest = lightWvColors().surfaceHighest,
    outline = lightWvColors().border,
    outlineVariant = lightWvColors().borderSubtle,
    primaryContainer = lightWvColors().primarySoft,
    onPrimaryContainer = lightWvColors().primary,
    secondaryContainer = lightWvColors().infoSoft,
    tertiaryContainer = lightWvColors().warningSoft
)

val LocalWvColors = staticCompositionLocalOf { darkWvColors() }

/** Convenience accessors. */
object WvTheme {
    val colors: WvColors
        @Composable get() = LocalWvColors.current
}

@Composable
fun WarrantyVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val wv = if (darkTheme) darkWvColors() else lightWvColors()
    val scheme = if (darkTheme) DarkMaterialScheme else LightMaterialScheme

    CompositionLocalProvider(LocalWvColors provides wv) {
        MaterialTheme(
            colorScheme = scheme,
            typography = WarrantyVaultTypography,
            content = content
        )
    }
}
