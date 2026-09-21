package com.warrantyvault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.warrantyvault.WarrantyEngine
import com.warrantyvault.WarrantyVaultApplication
import com.warrantyvault.data.Product
import com.warrantyvault.ui.theme.*
import com.warrantyvault.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToProducts: () -> Unit,
    onNavigateToProductDetail: (Long) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as WarrantyVaultApplication
    val database = app.database
    val productDao = database.productDao()

    val products by productDao.getAllProducts(app.currentUserId).collectAsState(initial = emptyList())

    val activeCount = products.count {
        val info = WarrantyEngine.warrantyStatusOf(it.purchaseDate, it.warrantyExpiryDate)
        info.status == "active" || info.status == "expiring_soon"
    }

    val expiringSoonList = products.filter {
        val info = WarrantyEngine.warrantyStatusOf(it.purchaseDate, it.warrantyExpiryDate)
        info.status == "expiring_soon"
    }

    val expiredCount = products.count {
        val info = WarrantyEngine.warrantyStatusOf(it.purchaseDate, it.warrantyExpiryDate)
        info.status == "expired"
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "WarrantyVault",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Dashboard",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = { /* TODO: toggle theme */ }) {
                        Icon(
                            imageVector = Icons.Default.Brightness4,
                            contentDescription = "Toggle Theme",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { /* TODO: logout */ }) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Logout",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        content = { padding ->
            WarrantyBackground(modifier = Modifier.padding(padding)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Stat cards grid
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item { ActiveWarrantiesStat(value = activeCount.toString()) }
                        item { ExpiringSoonStat(value = expiringSoonList.size.toString()) }
                        item { ExpiredWarrantiesStat(value = expiredCount.toString()) }
                        item { DocumentsStat(value = products.size.toString()) }
                    }

                    // Action banner
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        WarrantyPrimaryButton(
                            text = "Scan Document",
                            onClick = { /* TODO: navigate to Scan */ },
                            modifier = Modifier.weight(1f)
                        )
                        WarrantyGhostButton(
                            text = "Add Warranty",
                            onClick = { onNavigateToProducts() },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Expiring soon section
                    if (expiringSoonList.isNotEmpty()) {
                        WarrantySectionHeader(title = "Expiring Soon")
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            expiringSoonList.forEach { product ->
                                WarrantyProductCard(product = product, onClick = { onNavigateToProductDetail(product.id) })
                            }
                        }
                    }

                    // Recent products section
                    WarrantySectionHeader(title = "Recent Products")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        products.take(6).forEach { product ->
                            WarrantyProductCard(product = product, onClick = { onNavigateToProductDetail(product.id) })
                        }
                    }
                }
            }
        }
    )
}