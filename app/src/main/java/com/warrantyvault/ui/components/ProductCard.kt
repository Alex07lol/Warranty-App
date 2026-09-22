package com.warrantyvault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.warrantyvault.WarrantyEngine
import com.warrantyvault.data.Product
import com.warrantyvault.ui.theme.WvDimens
import com.warrantyvault.ui.theme.darkWvColors
import com.warrantyvault.ui.theme.lightWvColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Compact product card. Neutral surface; only the status indicator carries
 * semantic colour. Shows real thumbnail (from the product's document) when available.
 */
@Composable
fun WarrantyProductCard(
    product: Product,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnailPath: String? = null
) {
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    val info = WarrantyEngine.warrantyStatusOf(product.purchaseDate, product.warrantyExpiryDate)
    val (statusColor, statusSoft) = wv.statusColors(info.status)

    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WvDimens.RadiusMedium))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(WvDimens.RadiusMedium),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, wv.borderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(WvDimens.Space3),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail / initial placeholder
            Box(
                modifier = Modifier
                    .size(WvDimens.Thumb)
                    .background(wv.surfaceHighest, RoundedCornerShape(WvDimens.RadiusSmall)),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailPath != null && File(thumbnailPath).exists()) {
                    AsyncImage(
                        model = File(thumbnailPath),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Devices,
                        contentDescription = null,
                        tint = wv.textMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.width(WvDimens.Space3))

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = product.productName,
                    style = MaterialTheme.typography.titleMedium,
                    color = wv.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val brandModel = listOfNotNull(product.brand, product.model).joinToString(" · ")
                if (brandModel.isNotEmpty()) {
                    Text(
                        text = brandModel,
                        style = MaterialTheme.typography.bodySmall,
                        color = wv.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (product.warrantyExpiryDate != null) {
                    Text(
                        text = "Expires ${dateFormat.format(Date(product.warrantyExpiryDate!!))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = wv.textMuted
                    )
                }
            }

            Spacer(Modifier.width(WvDimens.Space2))

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusBadge(status = info.status, label = info.label)
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = wv.textMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
