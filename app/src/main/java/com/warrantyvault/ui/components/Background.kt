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
import com.warrantyvault.ui.theme.BrandAccent
import com.warrantyvault.ui.theme.BrandPrimary
import com.warrantyvault.ui.theme.CanvasDark
import com.warrantyvault.ui.theme.CanvasLight
import kotlin.math.max

@Composable
fun WarrantyBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == CanvasDark

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Base canvas color
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (isDark) CanvasDark else CanvasLight
        ) {}

        // Radial gradient 1 - top left
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val centerX = size.width * 0.06f
            val centerY = size.height * -0.12f
            val radius = max(size.width, size.height) * 0.45f

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BrandPrimary.copy(alpha = 0.1f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = radius
                ),
                topLeft = Offset(0f, 0f),
                size = size
            )
        }

        // Radial gradient 2 - bottom right
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val centerX = size.width * 1.04f
            val centerY = size.height * 1.08f
            val radius = max(size.width, size.height) * 0.42f

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BrandAccent.copy(alpha = 0.1f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = radius
                ),
                topLeft = Offset(0f, 0f),
                size = size
            )
        }

        // Content
        content()
    }
}