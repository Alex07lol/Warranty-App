package com.warrantyvault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.warrantyvault.ui.theme.WvDimens
import com.warrantyvault.ui.theme.darkWvColors
import com.warrantyvault.ui.theme.lightWvColors

/**
 * Neutral stat card with a semantic accent dot + tinted icon. Never a giant colored card.
 */
@Composable
fun WarrantyStatCard(
    label: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null
) {
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    val accentColor = accent ?: wv.textSecondary
    val container = MaterialTheme.colorScheme.surface

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(WvDimens.RadiusMedium),
        color = container,
        border = androidx.compose.foundation.BorderStroke(1.dp, wv.borderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(WvDimens.Space3),
            verticalArrangement = Arrangement.spacedBy(WvDimens.Space1)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(accentColor.copy(alpha = 0.14f), RoundedCornerShape(WvDimens.RadiusSmall)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(6.dp))
                // Semantic dot — colour is not the only indicator.
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(accentColor, androidx.compose.foundation.shape.CircleShape)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = wv.textPrimary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = wv.textSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
fun ActiveWarrantiesStat(value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    WarrantyStatCard("Active", value, "", Icons.Default.CheckCircle, modifier, wv.success, onClick)
}

@Composable
fun ExpiringSoonStat(value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    WarrantyStatCard("Expiring", value, "", Icons.Default.Warning, modifier, wv.warning, onClick)
}

@Composable
fun ExpiredWarrantiesStat(value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    WarrantyStatCard("Expired", value, "", Icons.Default.Close, modifier, wv.error, onClick)
}
