package com.warrantyvault.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.warrantyvault.WarrantyInfo
import com.warrantyvault.ui.theme.*

@Composable
fun WarrantyStatusBadge(
    info: WarrantyInfo,
    modifier: Modifier = Modifier
) {
    val dark = MaterialTheme.colorScheme.background == CanvasDark
    val statusColor = statusColor(info.status, dark)
    val softColor = statusSoftColor(info.status, dark)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(RadiusPill),
        color = softColor,
        contentColor = statusColor
    ) {
        Text(
            text = info.label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp
        )
    }
}

@Composable
fun WarrantyStatusBadge(
    status: String,
    label: String,
    modifier: Modifier = Modifier
) {
    val dark = MaterialTheme.colorScheme.background == CanvasDark
    val statusColor = statusColor(status, dark)
    val softColor = statusSoftColor(status, dark)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(RadiusPill),
        color = softColor,
        contentColor = statusColor
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp
        )
    }
}