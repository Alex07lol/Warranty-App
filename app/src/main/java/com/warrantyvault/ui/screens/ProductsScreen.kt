package com.warrantyvault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.warrantyvault.WarrantyEngine
import com.warrantyvault.WarrantyVaultApplication
import com.warrantyvault.data.Product
import com.warrantyvault.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    onProductClick: (Long) -> Unit,
    onAddProductClick: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as WarrantyVaultApplication
    val database = app.database
    val productDao = database.productDao()

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Products", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { /* handled by navigation */ }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = onAddProductClick) {
                        Icon(Icons.Default.Add, contentDescription = "Add Product")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search products...") },
                modifier = Modifier.padding(16.dp),
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                }
            )

            // Product List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 100.dp)
            ) {
                if (filteredProducts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    if (products.isEmpty()) "No products yet" else "No matches found",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    if (products.isEmpty())
                                        "Add your first product to start tracking warranties"
                                    else "Try a different search term",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (products.isEmpty()) {
                                    Button(onClick = onAddProductClick) {
                                        Text("Add Product")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(filteredProducts) { product ->
                        ProductCardItem(product = product, onClick = { onProductClick(product.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun ProductCardItem(product: Product, onClick: () -> Unit) {
    val info = WarrantyEngine.warrantyStatusOf(product.purchaseDate, product.warrantyExpiryDate)
    val badgeColor = when (info.status) {
        "active" -> Color(0xFF10B981)
        "expiring_soon" -> Color(0xFFF59E0B)
        "expired" -> Color(0xFFEF4444)
        else -> Color.Gray
    }

    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.productName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val subtitle = listOfNotNull(product.brand, product.model).joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(text = subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(4.dp))
                if (product.warrantyExpiryDate != null) {
                    Text(
                        text = "Expires: ${dateFormat.format(Date(product.warrantyExpiryDate))}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                color = badgeColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = info.label,
                    color = badgeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}