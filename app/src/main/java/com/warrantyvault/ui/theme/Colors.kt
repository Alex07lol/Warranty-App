package com.warrantyvault.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Legacy color constants, kept as thin aliases over the new token system so existing
 * references compile while screens migrate to WvTheme.colors. New code should not use
 * these; use the semantic tokens from Theme.kt.
 */
val BrandPrimary: Color get() = darkWvColors().primary
val BrandAccent: Color get() = darkWvColors().info
val BrandInk: Color get() = darkWvColors().primaryPressed

val Ok: Color get() = lightWvColors().success
val OkSoft: Color get() = lightWvColors().successSoft
val Warn: Color get() = lightWvColors().warning
val WarnSoft: Color get() = lightWvColors().warningSoft
val Danger: Color get() = lightWvColors().error
val DangerSoft: Color get() = lightWvColors().errorSoft
val Neutral: Color get() = lightWvColors().textMuted
val NeutralSoft: Color get() = lightWvColors().borderSubtle
val Info: Color get() = lightWvColors().info
val InfoSoft: Color get() = lightWvColors().infoSoft

val OkDark: Color get() = darkWvColors().success
val OkSoftDark: Color get() = darkWvColors().successSoft
val WarnDark: Color get() = darkWvColors().warning
val WarnSoftDark: Color get() = darkWvColors().warningSoft
val DangerDark: Color get() = darkWvColors().error
val DangerSoftDark: Color get() = darkWvColors().errorSoft
val NeutralDark: Color get() = darkWvColors().textMuted
val NeutralSoftDark: Color get() = darkWvColors().borderSubtle
val InfoDark: Color get() = darkWvColors().info
val InfoSoftDark: Color get() = darkWvColors().infoSoft

val CanvasLight: Color get() = lightWvColors().background
val SurfaceLight: Color get() = lightWvColors().surface
val Surface2Light: Color get() = lightWvColors().surfaceElevated
val Surface3Light: Color get() = lightWvColors().surfaceHighest
val InkLight: Color get() = lightWvColors().textPrimary
val Ink2Light: Color get() = lightWvColors().textSecondary
val Ink3Light: Color get() = lightWvColors().textMuted
val LineLight: Color get() = lightWvColors().border
val Line2Light: Color get() = lightWvColors().border

val CanvasDark: Color get() = darkWvColors().background
val SurfaceDark: Color get() = darkWvColors().surface
val Surface2Dark: Color get() = darkWvColors().surfaceElevated
val Surface3Dark: Color get() = darkWvColors().surfaceHighest
val InkDark: Color get() = darkWvColors().textPrimary
val Ink2Dark: Color get() = darkWvColors().textSecondary
val Ink3Dark: Color get() = darkWvColors().textMuted
val LineDark: Color get() = darkWvColors().border
val Line2Dark: Color get() = darkWvColors().border

val NavBgLight: Color get() = lightWvColors().glassNav
val NavBgDark: Color get() = darkWvColors().glassNav
val NavInkLight: Color get() = lightWvColors().textSecondary
val NavInkDark: Color get() = darkWvColors().textSecondary
val NavLineLight: Color get() = lightWvColors().glassBorder
val NavLineDark: Color get() = darkWvColors().glassBorder

// Geometry aliases (old names)
val RadiusXS = WvDimens.RadiusSmall
val RadiusSM = WvDimens.RadiusSmall
val RadiusMD = WvDimens.RadiusMedium
val RadiusLG = WvDimens.RadiusMedium
val RadiusXL = WvDimens.RadiusLarge
val RadiusPill = WvDimens.RadiusPill
val Gutter = WvDimens.ScreenGutter

// Status helpers (token-based)
fun statusColor(status: String, dark: Boolean): Color =
    (if (dark) darkWvColors() else lightWvColors()).statusColors(status).first

fun statusSoftColor(status: String, dark: Boolean): Color =
    (if (dark) darkWvColors() else lightWvColors()).statusColors(status).second

/** Distance label helper for UI (delegates to the repair domain formatter). */
fun formatDistanceLabel(meters: Double): String =
    com.warrantyvault.repair.formatDistance(meters)
