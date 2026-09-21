package com.warrantyvault.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = BrandPrimary,
    secondary = BrandAccent,
    tertiary = OkDark,
    error = DangerDark,
    background = CanvasDark,
    surface = SurfaceDark,
    surfaceVariant = Surface3Dark,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color(0xFF06101F),
    onError = Color(0xFF06101F),
    onBackground = InkDark,
    onSurface = InkDark,
    onSurfaceVariant = Ink2Dark,
    outline = LineDark,
    outlineVariant = Line2Dark,
    inverseSurface = InkDark,
    inverseOnSurface = SurfaceDark,
    inversePrimary = BrandAccent
)

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    secondary = BrandAccent,
    tertiary = Ok,
    error = Danger,
    background = CanvasLight,
    surface = SurfaceLight,
    surfaceVariant = Surface3Light,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color(0xFF06101F),
    onError = Color(0xFF06101F),
    onBackground = InkLight,
    onSurface = InkLight,
    onSurfaceVariant = Ink2Light,
    outline = LineLight,
    outlineVariant = Line2Light,
    inverseSurface = InkLight,
    inverseOnSurface = SurfaceLight,
    inversePrimary = BrandAccent
)

@Composable
fun WarrantyVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = WarrantyVaultTypography,
        content = content
    )
}