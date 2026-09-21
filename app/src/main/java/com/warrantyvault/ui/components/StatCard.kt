package com.warrantyvault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.warrantyvault.ui.theme.*

@Composable
fun WarrantyStatCard(
    label: String,
    value: String,
    subtitle: String,
    icon: @Composable (() -> Unit),
    iconBgColor: Color,
    iconTintColor: Color,
    modifier: Modifier = Modifier,
    accent: Boolean = false
) {
    val isDark = MaterialTheme.colorScheme.background == CanvasDark

    Card(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(RadiusLG),
        colors = CardDefaults.cardColors(
            containerColor = if (accent) BrandPrimary else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (accent) 6.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Icon wrapper
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (accent) Color.White.copy(alpha = 0.18f) else iconBgColor,
                        shape = RoundedCornerShape(RadiusSM)
                    ),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }

            // Value
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                letterSpacing = (-1).sp,
                color = if (accent) Color.White else MaterialTheme.colorScheme.onSurface
            )

            // Label
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = if (accent) Color.White.copy(alpha = 0.82f) else MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Subtitle
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (accent) Color.White.copy(alpha = 0.68f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Predefined stat cards for Dashboard
@Composable
fun ActiveWarrantiesStat(value: String, modifier: Modifier = Modifier) {
    WarrantyStatCard(
        label = "Active Warranties",
        value = value,
        subtitle = "Coverage is active",
        icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp)) },
        iconBgColor = OkSoft,
        iconTintColor = Ok,
        modifier = modifier
    )
}

@Composable
fun ExpiringSoonStat(value: String, modifier: Modifier = Modifier) {
    WarrantyStatCard(
        label = "Expiring Soon",
        value = value,
        subtitle = "Action required soon",
        icon = { Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(20.dp)) },
        iconBgColor = WarnSoft,
        iconTintColor = Warn,
        modifier = modifier,
        accent = true
    )
}

@Composable
fun ExpiredWarrantiesStat(value: String, modifier: Modifier = Modifier) {
    WarrantyStatCard(
        label = "Expired Warranties",
        value = value,
        subtitle = "Coverage ended",
        icon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(20.dp)) },
        iconBgColor = DangerSoft,
        iconTintColor = Danger,
        modifier = modifier
    )
}

@Composable
fun DocumentsStat(value: String, modifier: Modifier = Modifier) {
    WarrantyStatCard(
        label = "Total Documents",
        value = value,
        subtitle = "Receipts & files",
        icon = { Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(20.dp)) },
        iconBgColor = OkSoft,
        iconTintColor = BrandPrimary,
        modifier = modifier
    )
}