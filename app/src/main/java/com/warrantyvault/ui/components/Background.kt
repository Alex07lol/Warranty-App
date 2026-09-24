package com.warrantyvault.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.warrantyvault.ui.theme.WvTheme
import kotlin.math.max

/**
 * App background: flat neutral canvas with one very restrained radial tint so the
 * screen doesn't feel flat. No neon gradients, no glow.
 */
@Composable
fun WarrantyBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val wv = WvTheme.colors

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = wv.background
        ) {}

        // Single, very subtle radial tint from the top edge.
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val radius = max(size.width, size.height) * 0.55f
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        wv.primary.copy(alpha = 0.045f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, -size.height * 0.18f),
                    radius = radius
                ),
                topLeft = Offset.Zero,
                size = size
            )
        }

        content()
    }
}
