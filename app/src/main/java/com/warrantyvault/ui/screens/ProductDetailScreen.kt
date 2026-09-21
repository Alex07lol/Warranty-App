package com.warrantyvault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
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
            val badgeColor = when (info.status) {
                "active" -> Color(0xFF10B981)
                "expiring_soon" -> Color(0xFFF59E0B)
                "expired" -> Color(0xFFEF4444)
                else -> Color.Gray
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
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
                                Text(text = p.productName, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Surface(
                                    color = badgeColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(
                                        text = info.label,
                                        color = badgeColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            val brandModel = listOfNotNull(p.brand, p.model).joinToString(" · ")
                            if (brandModel.isNotEmpty()) {
                                Text(text = brandModel, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Specifications & Details", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                DetailRow("Category", p.category)
                                DetailRow("Serial Number", p.serialNumber)
                                DetailRow("Purchase Date", p.purchaseDate?.let { dateFormat.format(Date(it)) })
                                DetailRow("Purchase Price", p.purchasePrice?.let { "${p.currency} $it" })
                                DetailRow("Store", p.purchaseStore)
                                DetailRow("Warranty Period", p.warrantyPeriodMonths?.let { "$it months" })
                                DetailRow("Warranty Expiry", p.warrantyExpiryDate?.let { dateFormat.format(Date(it)) })
                                DetailRow("Lifecycle Status", p.lifecycleStatus.replaceFirstChar { it.uppercase() })
                                DetailRow("Warranty Provider", p.warrantyProvider)
                                DetailRow("Warranty Type", p.warrantyProviderType)
                                DetailRow("Support Contact", p.warrantyContact)
                                DetailRow("Website", p.warrantyWebsite)
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
fun DetailRow(label: String, value: String?) {
    if (!value.isNullOrBlank()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Text(text = value, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}