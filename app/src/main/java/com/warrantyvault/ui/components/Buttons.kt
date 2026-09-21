package com.warrantyvault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.warrantyvault.ui.theme.WvDimens
import com.warrantyvault.ui.theme.darkWvColors
import com.warrantyvault.ui.theme.lightWvColors

@Composable
fun WarrantyPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(WvDimens.RadiusSmall),
        colors = ButtonDefaults.buttonColors(
            containerColor = wv.primary,
            contentColor = wv.onPrimary,
            disabledContainerColor = wv.primary.copy(alpha = 0.4f)
        )
    ) {
        icon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(WvDimens.Space2))
        }
        Text(text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun WarrantyGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(WvDimens.RadiusSmall),
        border = androidx.compose.foundation.BorderStroke(1.dp, wv.border),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = wv.textPrimary,
            containerColor = androidx.compose.ui.graphics.Color.Transparent
        )
    ) {
        icon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(16.dp), tint = wv.textSecondary)
            Spacer(Modifier.width(WvDimens.Space2))
        }
        Text(text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun WarrantySmallButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(WvDimens.RadiusSmall),
        colors = ButtonDefaults.buttonColors(containerColor = wv.primary, contentColor = wv.onPrimary),
        contentPadding = PaddingValues(horizontal = WvDimens.Space3, vertical = WvDimens.Space2)
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
    }
}

/** Neutral circular icon button used in headers. */
@Composable
fun WarrantyIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color? = null
) {
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    IconButton(onClick = onClick, modifier = modifier.size(42.dp)) {
        Icon(icon, contentDescription = contentDescription, tint = tint ?: wv.textSecondary, modifier = Modifier.size(20.dp))
    }
}
