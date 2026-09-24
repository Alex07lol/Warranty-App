package com.warrantyvault.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Extended semantic tokens beyond Material's scheme. Every screen consumes these instead of
 * hardcoding colours; the visual hierarchy lives here:
 * neutral surfaces dominate, brand blue = interaction, green = success,
 * amber = attention, red = error, blue = information.
 *
 * **The values are the same numbers as the web app's stylesheet**
 * (`API/backend/public/css/app.css`, `:root` and `body.dark-mode`), so the phone app and the
 * browser app are one design system running in two runtimes. If a colour has to change, change it
 * here and in the CSS together — never inline a hex value in a screen.
 */
@Immutable
data class WvColors(
    /** True when this palette is the dark one; components that must branch read this. */
    val isDark: Boolean,

    // Surfaces (neutral, dominate the UI) — web --canvas / --surface / --surface-2 / --surface-3
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceHighest: Color,

    // Interaction / brand — web --brand / --brand-soft / --brand-ink / --brand-2
    val primary: Color,
    val primaryPressed: Color,
    val onPrimary: Color,
    val primarySoft: Color,
    /** Text drawn on a `primarySoft` fill (web `--brand-ink`). */
    val brandInk: Color,
    /** Cyan partner of `primary` inside the brand gradient (web `--brand-2`). */
    val brand2: Color,
    /** Second stop of the brand gradient (web `--brand-grad`). */
    val gradientEnd: Color,
    /** Ink for a solid accent fill that is itself light (web `--on-accent`). */
    val onAccent: Color,

    // Text — web --ink / --ink-2 / --ink-3
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,

    // Lines — `border` is the emphasis line (web --line-2), `borderSubtle` the resting card line
    // (web --line) that most cards and dividers use.
    val border: Color,
    val borderSubtle: Color,

    // Semantic — web --ok / --warn / --danger / --info / --neutral (+ -soft twins)
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val error: Color,
    val errorSoft: Color,
    val info: Color,
    val infoSoft: Color,
    val neutral: Color,
    val neutralSoft: Color,

    // Glass navigation — dark in BOTH themes, exactly like the web nav pill
    val glassNav: Color,
    val glassBorder: Color,
    val navInk: Color
) {
    /** Subtle surface gradient for hero-style cards that carry normal ink on top. */
    val heroBrush: Brush
        get() = Brush.linearGradient(listOf(surfaceElevated, surfaceHighest))

    /** Web `--brand-grad`: solid brand fill for the primary CTA and the active nav item. */
    val brandBrush: Brush
        get() = Brush.linearGradient(listOf(primary, gradientEnd))

    /**
     * Web `.stat-card.accent`: the deep navy -> brand -> cyan sweep used for the one hero stat.
     * Theme-independent by design; `onAccent`/white ink sits on it.
     */
    val accentBrush: Brush
        get() = Brush.linearGradient(listOf(AccentGradientStart, AccentGradientMid, AccentGradientEnd))

    /** Status -> (color, soft container) from the central tokens. */
    fun statusColors(status: String): Pair<Color, Color> = when (status) {
        "active" -> success to successSoft
        "expiring_soon" -> warning to warningSoft
        "expired" -> error to errorSoft
        else -> textMuted to borderSubtle
    }

    private companion object {
        val AccentGradientStart = Color(0xFF18317A)
        val AccentGradientMid = Color(0xFF2457F5)
        val AccentGradientEnd = Color(0xFF12A7C9)
    }
}

/** Light palette — the web stylesheet's `:root` block, value for value. */
fun lightWvColors(): WvColors = WvColors(
    isDark = false,

    background = Color(0xFFEDF2F9), // --canvas
    surface = Color(0xFFFFFFFF), // --surface
    surfaceElevated = Color(0xFFF7F9FD), // --surface-2
    surfaceHighest = Color(0xFFEEF3FA), // --surface-3

    primary = Color(0xFF2457F5), // --brand
    primaryPressed = Color(0xFF1C40AD), // --brand-ink
    onPrimary = Color(0xFFFFFFFF), // --on-brand-grad
    primarySoft = Color(0xFFE9EFFF), // --brand-soft
    brandInk = Color(0xFF1C40AD), // --brand-ink
    brand2 = Color(0xFF12A7C9), // --brand-2
    gradientEnd = Color(0xFF346FE2), // --brand-grad stop 2
    onAccent = Color(0xFFFFFFFF), // --on-accent

    textPrimary = Color(0xFF0F1F3A), // --ink
    textSecondary = Color(0xFF4D5C76), // --ink-2
    textMuted = Color(0xFF626D7F), // --ink-3

    border = Color(0xFFC6D4E8), // --line-2
    borderSubtle = Color(0xFFDEE6F1), // --line

    success = Color(0xFF0B7C71), // --ok
    successSoft = Color(0xFFE1F7F3), // --ok-soft
    warning = Color(0xFFA05C05), // --warn
    warningSoft = Color(0xFFFDF0D9), // --warn-soft
    error = Color(0xFFC13648), // --danger
    errorSoft = Color(0xFFFFE9EC), // --danger-soft
    info = Color(0xFF1E6BC9), // --info
    infoSoft = Color(0xFFE7F0FD), // --info-soft
    neutral = Color(0xFF5F6E84), // --neutral
    neutralSoft = Color(0xFFEEF2F8), // --neutral-soft

    glassNav = Color(0xE6091630), // --nav-bg   rgba(9, 22, 48, .9)
    glassBorder = Color(0x1AFFFFFF), // --nav-line rgba(255, 255, 255, .1)
    navInk = Color(0xFFA9BAD4) // --nav-ink
)

/** Dark palette — the web stylesheet's `body.dark-mode` block, value for value. */
fun darkWvColors(): WvColors = WvColors(
    isDark = true,

    background = Color(0xFF080F1E), // --canvas
    surface = Color(0xFF121C30), // --surface
    surfaceElevated = Color(0xFF16223A), // --surface-2
    surfaceHighest = Color(0xFF1B2942), // --surface-3

    primary = Color(0xFF4B7CFF), // --brand
    primaryPressed = Color(0xFF3F6EF0), // pressed step of --brand
    onPrimary = Color(0xFFFFFFFF), // --on-brand-grad
    primarySoft = Color(0xFF101C34), // --brand-soft
    brandInk = Color(0xFFA9C4FF), // --brand-ink
    brand2 = Color(0xFF2CC6E6), // --brand-2
    gradientEnd = Color(0xFF1F7BB2), // --brand-grad stop 2
    onAccent = Color(0xFF06101F), // --on-accent

    textPrimary = Color(0xFFEEF4FF), // --ink
    textSecondary = Color(0xFFA7B6CF), // --ink-2
    textMuted = Color(0xFF8492AB), // --ink-3

    border = Color(0xFF2F4160), // --line-2
    borderSubtle = Color(0xFF22314C), // --line

    success = Color(0xFF34C9B4), // --ok
    successSoft = Color(0xFF102E2C), // --ok-soft
    warning = Color(0xFFF0A03C), // --warn
    warningSoft = Color(0xFF33240F), // --warn-soft
    error = Color(0xFFFF7B8A), // --danger
    errorSoft = Color(0xFF351A20), // --danger-soft
    info = Color(0xFF6CB0FF), // --info
    infoSoft = Color(0xFF14243D), // --info-soft
    neutral = Color(0xFF93A4BD), // --neutral
    neutralSoft = Color(0xFF1B2740), // --neutral-soft

    glassNav = Color(0xEB060D1C), // --nav-bg   rgba(6, 13, 28, .92)
    glassBorder = Color(0x14FFFFFF), // --nav-line rgba(255, 255, 255, .08)
    navInk = Color(0xFF9DB0CD) // --nav-ink
)

/**
 * Material's own scheme, derived from the same tokens so the two can never drift: any component
 * that still reads `MaterialTheme.colorScheme` lands on the identical palette.
 */
private fun darkMaterialScheme(wv: WvColors = darkWvColors()) = darkColorScheme(
    primary = wv.primary,
    onPrimary = wv.onPrimary,
    secondary = wv.info,
    onSecondary = wv.onAccent,
    tertiary = wv.success,
    error = wv.error,
    onError = wv.onAccent,
    background = wv.background,
    onBackground = wv.textPrimary,
    surface = wv.surface,
    onSurface = wv.textPrimary,
    surfaceVariant = wv.surfaceElevated,
    onSurfaceVariant = wv.textSecondary,
    surfaceContainer = wv.surfaceElevated,
    surfaceContainerHigh = wv.surfaceHighest,
    surfaceContainerHighest = wv.surfaceHighest,
    outline = wv.border,
    outlineVariant = wv.borderSubtle,
    primaryContainer = wv.primarySoft,
    onPrimaryContainer = wv.brandInk,
    secondaryContainer = wv.infoSoft,
    onSecondaryContainer = wv.info,
    tertiaryContainer = wv.warningSoft
)

private fun lightMaterialScheme(wv: WvColors = lightWvColors()) = lightColorScheme(
    primary = wv.primary,
    onPrimary = wv.onPrimary,
    secondary = wv.info,
    onSecondary = wv.onAccent,
    tertiary = wv.success,
    error = wv.error,
    onError = wv.onAccent,
    background = wv.background,
    onBackground = wv.textPrimary,
    surface = wv.surface,
    onSurface = wv.textPrimary,
    surfaceVariant = wv.surfaceElevated,
    onSurfaceVariant = wv.textSecondary,
    surfaceContainer = wv.surfaceElevated,
    surfaceContainerHigh = wv.surfaceHighest,
    surfaceContainerHighest = wv.surfaceHighest,
    outline = wv.border,
    outlineVariant = wv.borderSubtle,
    primaryContainer = wv.primarySoft,
    onPrimaryContainer = wv.brandInk,
    secondaryContainer = wv.infoSoft,
    onSecondaryContainer = wv.info,
    tertiaryContainer = wv.warningSoft
)

val LocalWvColors = staticCompositionLocalOf { darkWvColors() }

/** Whether the *resolved* theme is dark — not what the OS reports. */
val LocalWvDark = staticCompositionLocalOf { true }

/** The user's stored choice (system / light / dark). */
val LocalThemeMode = staticCompositionLocalOf { ThemeMode.System }

/** Convenience accessors. Prefer these over resolving colours by hand in a screen. */
object WvTheme {
    /** The active palette. Screens should do `val wv = WvTheme.colors`. */
    val colors: WvColors
        @Composable get() = LocalWvColors.current

    /** Resolved darkness, for the few places that need to branch (shadows, system bars). */
    val isDark: Boolean
        @Composable get() = LocalWvDark.current

    /** The stored user preference, for the settings selector. */
    val mode: ThemeMode
        @Composable get() = LocalThemeMode.current
}

/**
 * @param mode the user's stored preference. `ThemeMode.System` follows the device; Light/Dark
 *   override it. Resolution is pure ([ThemeMode.isDark]) so it is unit-tested without a device.
 */
@Composable
fun WarrantyVaultTheme(
    mode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit
) {
    val dark = mode.isDark(isSystemInDarkTheme())
    val wv = if (dark) darkWvColors() else lightWvColors()

    SystemBarAppearance(dark)

    CompositionLocalProvider(
        LocalWvColors provides wv,
        LocalWvDark provides dark,
        LocalThemeMode provides mode
    ) {
        MaterialTheme(
            colorScheme = if (dark) darkMaterialScheme(wv) else lightMaterialScheme(wv),
            typography = WarrantyVaultTypography,
            content = content
        )
    }
}

/**
 * Keeps system-bar icons legible against the resolved palette: dark icons on the light canvas,
 * light icons on the dark one.
 *
 * Driven from the theme instead of XML `windowLightStatusBar`, because that attribute can only
 * describe the *device* setting — with an in-app Light/Dark override the bars must follow the
 * user's choice, otherwise light mode shows white icons on a near-white canvas.
 */
@Composable
private fun SystemBarAppearance(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = view.context.findActivity()?.window ?: return

    SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
