package com.warrantyvault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.warrantyvault.WarrantyInfo
import com.warrantyvault.ui.theme.WvDimens
import com.warrantyvault.ui.theme.darkWvColors
import com.warrantyvault.ui.theme.lightWvColors

@Composable
fun WarrantyStatusBadge(
    info: WarrantyInfo,
    modifier: Modifier = Modifier
) {
    StatusBadge(status = info.status, label = info.label, modifier = modifier)
}

@Composable
fun WarrantyStatusBadge(
    status: String,
    label: String,
    modifier: Modifier = Modifier
) {
    StatusBadge(status = status, label = label, modifier = modifier)
}

/** Semantic pill: soft container + colored text + small dot (colour is not the only cue). */
@Composable
fun StatusBadge(
    status: String,
    label: String,
    modifier: Modifier = Modifier
) {
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    val colors: Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> = wv.statusColors(status)
    val color = colors.first
    val soft = colors.second

    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(WvDimens.RadiusPill),
        color = soft,
        contentColor = color
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, CircleShape)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
