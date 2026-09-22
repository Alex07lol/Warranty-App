package com.warrantyvault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.warrantyvault.WarrantyVaultApplication
import com.warrantyvault.ui.components.*
import com.warrantyvault.ui.theme.WvDimens
import com.warrantyvault.ui.theme.darkWvColors
import com.warrantyvault.ui.theme.lightWvColors
import java.util.Locale

@Composable
fun ProductsScreen(
    onProductClick: (Long) -> Unit,
    onAddProductClick: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as WarrantyVaultApplication
    val productDao = app.database.productDao()
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()

    var searchQuery by remember { mutableStateOf("") }
    val products by productDao.getAllProducts(app.currentUserId).collectAsState(initial = emptyList())

    val filteredProducts = remember(products, searchQuery) {
        if (searchQuery.isBlank()) products else products.filter { product ->
            val query = searchQuery.lowercase(Locale.getDefault())
            product.productName.lowercase(Locale.getDefault()).contains(query) ||
            product.brand?.lowercase(Locale.getDefault())?.contains(query) == true ||
            product.model?.lowercase(Locale.getDefault())?.contains(query) == true ||
            product.category?.lowercase(Locale.getDefault())?.contains(query) == true ||
            product.serialNumber?.lowercase(Locale.getDefault())?.contains(query) == true
        }
    }

    WarrantyBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = WvDimens.ScreenGutter)
        ) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Products",
                        style = MaterialTheme.typography.headlineMedium,
                        color = wv.textPrimary
                    )
                    Text(
                        "${products.size} devices tracked",
                        style = MaterialTheme.typography.bodySmall,
                        color = wv.textSecondary
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = wv.primary,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(onClick = onAddProductClick)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add Product",
                            tint = wv.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(WvDimens.Space4))

            // Search Bar
            WarrantySearchBar(
                value = searchQuery,
                onValueChange = { searchQuery = it }
            )

            Spacer(Modifier.height(WvDimens.Space3))

            // Product List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp)
            ) {
                if (filteredProducts.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = if (products.isEmpty()) "No products yet" else "No matches found",
                            body = if (products.isEmpty())
                                "Add your first product to start tracking warranties"
                            else "Try a different search term",
                            actionText = if (products.isEmpty()) "Add Product" else null,
                            onAction = if (products.isEmpty()) onAddProductClick else null
                        )
                    }
                } else {
                    items(filteredProducts, key = { it.id }) { product ->
                        WarrantyProductCard(product = product, onClick = { onProductClick(product.id) })
                    }
                }
            }
        }
    }
}