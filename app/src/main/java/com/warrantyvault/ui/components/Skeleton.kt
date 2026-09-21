package com.warrantyvault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.warrantyvault.ui.theme.*

@Composable
fun WarrantySkeleton(
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Unspecified,
    height: androidx.compose.ui.unit.Dp = 16.dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(RadiusXS)
) {
    Surface(
        modifier = modifier
            .width(width)
            .height(height),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = shape
    ) {}
}

@Composable
fun WarrantyProductCardSkeleton(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(RadiusLG),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            WarrantySkeleton(
                width = 52.dp,
                height = 52.dp,
                shape = RoundedCornerShape(RadiusMD)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WarrantySkeleton(width = 120.dp)
                WarrantySkeleton(width = 80.dp)
                WarrantySkeleton(width = 100.dp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            WarrantySkeleton(width = 60.dp, height = 24.dp, shape = RoundedCornerShape(RadiusPill))
        }
    }
}

@Composable
fun WarrantyStatCardSkeleton(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(RadiusLG),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WarrantySkeleton(width = 36.dp, height = 36.dp, shape = RoundedCornerShape(RadiusSM))
            WarrantySkeleton(width = 80.dp, height = 32.dp)
            WarrantySkeleton(width = 100.dp, height = 14.dp)
            WarrantySkeleton(width = 70.dp, height = 12.dp)
        }
    }
}