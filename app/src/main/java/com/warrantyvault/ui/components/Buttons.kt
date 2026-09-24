package com.warrantyvault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.warrantyvault.ui.theme.WvDimens
import com.warrantyvault.ui.theme.WvTheme

/**
 * Primary CTA — the web's `.btn-primary`: brand gradient fill, white ink, and a blue glow that
 * reads as "this is the action". Material's `Button` cannot take a gradient container, so this is a
 * small Box-based button with the same semantics and touch target.
 */
@Composable
fun WarrantyPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val wv = WvTheme.colors
    val shape = RoundedCornerShape(WvDimens.RadiusMedium)

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = WvDimens.TouchTarget)
            .shadow(
                elevation = 8.dp,
                shape = shape,
                ambientColor = wv.primary,
                spotColor = wv.primary
            )
            .clip(shape)
            .background(brush = wv.brandBrush)
            .alpha(if (enabled) 1f else 0.55f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = WvDimens.Space4, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = wv.onPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(WvDimens.Space2))
        }
        Text(
            text,
            color = wv.onPrimary,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/** Ghost button — web `.btn-ghost`: surface fill, hairline line, ink-2 label. */
@Composable
fun WarrantyGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val wv = WvTheme.colors
    val shape = RoundedCornerShape(WvDimens.RadiusMedium)

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = WvDimens.TouchTarget)
            .shadow(elevation = 1.dp, shape = shape, ambientColor = Color.Black, spotColor = Color.Black.copy(alpha = 0.08f))
            .clip(shape)
            .background(wv.surface)
            .border(width = 1.dp, color = wv.borderSubtle, shape = shape)
            .alpha(if (enabled) 1f else 0.55f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = WvDimens.Space4, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = wv.textSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(WvDimens.Space2))
        }
        Text(
            text,
            color = wv.textSecondary,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/** Compact action — web `.btn-small`: same gradient fill, tighter padding and radius. */
@Composable
fun WarrantySmallButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wv = WvTheme.colors
    val shape = RoundedCornerShape(WvDimens.RadiusSmall)

    Row(
        modifier = modifier
            .clip(shape)
            .background(brush = wv.brandBrush)
            .clickable(onClick = onClick)
            .padding(horizontal = WvDimens.Space3, vertical = WvDimens.Space2),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            color = wv.onPrimary,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium
        )
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
    val wv = WvTheme.colors
    IconButton(onClick = onClick, modifier = modifier.size(42.dp)) {
        Icon(icon, contentDescription = contentDescription, tint = tint ?: wv.textSecondary, modifier = Modifier.size(20.dp))
    }
}
