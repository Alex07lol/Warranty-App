package com.warrantyvault.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// WarrantyVault brand colors (from web CSS tokens)
val BrandPrimary = Color(0xFF2457F5)
val BrandAccent = Color(0xFF12A7C9)
val BrandInk = Color(0xFF1C40AD)

// Semantic colors (light theme)
val Ok = Color(0xFF0B7C71)
val OkSoft = Color(0xFFE1F7F3)
val Warn = Color(0xFFA05C05)
val WarnSoft = Color(0xFFFDf0D9)
val Danger = Color(0xFFC13648)
val DangerSoft = Color(0xFFffe9Ec)
val Neutral = Color(0xFF5F6E84)
val NeutralSoft = Color(0xFFEEF2F8)
val Info = Color(0xFF1E6BC9)
val InfoSoft = Color(0xFFE7F0FD)

// Semantic colors (dark theme)
val OkDark = Color(0xFF34C9B4)
val OkSoftDark = Color(0xFF102E2C)
val WarnDark = Color(0xFFf0a03c)
val WarnSoftDark = Color(0xFF33240f)
val DangerDark = Color(0xFFff7b8a)
val DangerSoftDark = Color(0xFF351A20)
val NeutralDark = Color(0xFF93A4BD)
val NeutralSoftDark = Color(0xFF1B2740)
val InfoDark = Color(0xFF6cb0ff)
val InfoSoftDark = Color(0xFF14243D)

// Surface palette (light)
val CanvasLight = Color(0xFFEDF2F9)
val SurfaceLight = Color(0xFFFFFFFF)
val Surface2Light = Color(0xFFF7F9FD)
val Surface3Light = Color(0xFFEEF3FA)
val InkLight = Color(0xFF0F1F3A)
val Ink2Light = Color(0xFF4D5C76)
val Ink3Light = Color(0xFF626D7F)
val LineLight = Color(0xFFDEE6F1)
val Line2Light = Color(0xFFC6D4E8)

// Surface palette (dark)
val CanvasDark = Color(0xFF080F1E)
val SurfaceDark = Color(0xFF121C30)
val Surface2Dark = Color(0xFF16223A)
val Surface3Dark = Color(0xFF1B2942)
val InkDark = Color(0xFFEEF4FF)
val Ink2Dark = Color(0xFFA7B6CF)
val Ink3Dark = Color(0xFF8492AB)
val LineDark = Color(0xFF22314C)
val Line2Dark = Color(0xFF2F4160)

// Nav glass (dark in both themes)
val NavBgLight = Color(0xFF091630).copy(alpha = 0.9f)
val NavBgDark = Color(0xFF060D1C).copy(alpha = 0.92f)
val NavInkLight = Color(0xFFA9BAD4)
val NavInkDark = Color(0xFF9DB0CD)
val NavLineLight = Color(0xFFFFFFFF).copy(alpha = 0.1f)
val NavLineDark = Color(0xFFFFFFFF).copy(alpha = 0.08f)

// Geometry tokens
val RadiusXS = 8.dp
val RadiusSM = 10.dp
val RadiusMD = 14.dp
val RadiusLG = 18.dp
val RadiusXL = 24.dp
val RadiusPill = 999.dp

// Spacing
val Gutter = 18.dp

// Status color helpers
fun statusColor(status: String, dark: Boolean): Color = when (status) {
    "active" -> if (dark) OkDark else Ok
    "expiring_soon" -> if (dark) WarnDark else Warn
    "expired" -> if (dark) DangerDark else Danger
    else -> if (dark) NeutralDark else Neutral
}

fun statusSoftColor(status: String, dark: Boolean): Color = when (status) {
    "active" -> if (dark) OkSoftDark else OkSoft
    "expiring_soon" -> if (dark) WarnSoftDark else WarnSoft
    "expired" -> if (dark) DangerSoftDark else DangerSoft
    else -> if (dark) NeutralSoftDark else NeutralSoft
}