package com.warrantyvault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Upload
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
                title = { Text("Library", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { /* handled by navigation */ }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = onAddProductClick) {
                        Icon(Icons.Default.Add, contentDescription = "Add Product")
                    }
                    IconButton(onClick = { /* TODO: show filter sheet */ }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filters")
                    }
                    IconButton(onClick = { /* TODO: export */ }) {
                        Icon(Icons.Default.Download, contentDescription = "Export")
                    }
                    IconButton(onClick = { /* TODO: import */ }) {
                        Icon(Icons.Default.Upload, contentDescription = "Import")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Search Bar
            WarrantySearchBar(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.padding(16.dp)
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
                                    WarrantyPrimaryButton(
                                        text = "Add Product",
                                        onClick = onAddProductClick
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(filteredProducts) { product ->
                        WarrantyProductCard(product = product, onClick = { onProductClick(product.id) })
                    }
                }
            }
        }
    }
}