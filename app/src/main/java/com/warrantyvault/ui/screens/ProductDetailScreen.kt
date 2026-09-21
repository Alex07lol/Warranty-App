package com.warrantyvault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.warrantyvault.ui.theme.CanvasDark
import com.warrantyvault.ui.theme.statusColor
import com.warrantyvault.ui.theme.statusSoftColor
import com.warrantyvault.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    productId: Long,
    onBackClick: () -> Unit,
    onEditClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as WarrantyVaultApplication
    val database = app.database
    val productDao = database.productDao()
    val scope = rememberCoroutineScope()

    var product by remember { mutableStateOf<Product?>(null) }

    LaunchedEffect(productId) {
        withContext(Dispatchers.IO) {
            productDao.getProductById(productId).collect { p ->
                product = p
            }
        }
    }

    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(product?.productName ?: "Product Details") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (product != null) {
                        IconButton(onClick = { onEditClick(product!!.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                productDao.softDelete(product!!.id, System.currentTimeMillis())
                                withContext(Dispatchers.Main) {
                                    onBackClick()
                                }
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (product == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val p = product!!
            val info = WarrantyEngine.warrantyStatusOf(p.purchaseDate, p.warrantyExpiryDate)
            val isDark = MaterialTheme.colorScheme.background == CanvasDark

            WarrantyBackground(modifier = Modifier.padding(padding)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = p.productName, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                                    WarrantyStatusBadge(status = info.status, label = info.label)
                                }

                                val brandModel = listOfNotNull(p.brand, p.model).joinToString(" · ")
                                if (brandModel.isNotEmpty()) {
                                    Text(text = brandModel, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    item {
                        WarrantySectionHeader(title = "Warranty Status")
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                DetailRow("Status", info.label, statusColor(info.status, isDark))
                                DetailRow("Days Remaining", info.daysRemaining?.toString() ?: "Unknown")
                                DetailRow("Warranty Period", p.warrantyPeriodMonths?.let { "$it months" })
                                DetailRow("Warranty Expiry", p.warrantyExpiryDate?.let { dateFormat.format(Date(it)) })
                                DetailRow("Warranty Provider", p.warrantyProvider)
                                DetailRow("Warranty Type", p.warrantyProviderType)
                                DetailRow("Support Contact", p.warrantyContact)
                                DetailRow("Website", p.warrantyWebsite)
                            }
                        }
                    }

                    item {
                        WarrantySectionHeader(title = "Purchase Information")
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                DetailRow("Purchase Date", p.purchaseDate?.let { dateFormat.format(Date(it)) })
                                DetailRow("Purchase Price", p.purchasePrice?.let { "${p.currency} $it" })
                                DetailRow("Store", p.purchaseStore)
                            }
                        }
                    }

                    item {
                        WarrantySectionHeader(title = "Product Identifiers")
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                DetailRow("Category", p.category)
                                DetailRow("Serial Number", p.serialNumber)
                                DetailRow("Lifecycle Status", p.lifecycleStatus.replaceFirstChar { it.uppercase() })
                            }
                        }
                    }

                    item {
                        WarrantySectionHeader(title = "Notes")
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                DetailRow("Notes", p.notes)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String?, statusColor: Color? = null) {
    if (!value.isNullOrBlank()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Text(
                text = value,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = statusColor ?: MaterialTheme.colorScheme.onSurface
            )
        }
    }
}