package com.warrantyvault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.warrantyvault.data.Product
import com.warrantyvault.WarrantyEngine
import com.warrantyvault.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun WarrantyProductCard(
    product: Product,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val info = WarrantyEngine.warrantyStatusOf(product.purchaseDate, product.warrantyExpiryDate)
    val isDark = MaterialTheme.colorScheme.background == CanvasDark
    val statusColor = statusColor(info.status, isDark)
    val softColor = statusSoftColor(info.status, isDark)

    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(RadiusLG),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail / placeholder
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(BrandPrimary.copy(alpha = 0.15f), BrandAccent.copy(alpha = 0.15f))
                        ),
                        shape = RoundedCornerShape(RadiusMD)
                    ),
                contentAlignment = Alignment.Center
            ) {
                val initial = product.productName.firstOrNull()?.uppercase() ?: "?"
                Text(
                    text = initial.toString(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = BrandPrimary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Product info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = product.productName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                val brandModel = listOfNotNull(product.brand, product.model).joinToString(" · ")
                if (brandModel.isNotEmpty()) {
                    Text(
                        text = brandModel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                if (product.warrantyExpiryDate != null) {
                    Text(
                        text = "Expires: ${dateFormat.format(Date(product.warrantyExpiryDate!!))}",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right side - status badge + chevron
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
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

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}