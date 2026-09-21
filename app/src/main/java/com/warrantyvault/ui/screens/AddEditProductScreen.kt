package com.warrantyvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.warrantyvault.WarrantyVaultApplication
import com.warrantyvault.data.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProductScreen(
    productId: Long?,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as WarrantyVaultApplication
    val productDao = app.database.productDao()
    val scope = rememberCoroutineScope()

    var productName by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var serialNumber by remember { mutableStateOf("") }
    var store by remember { mutableStateOf("") }
    var priceStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var warrantyMonths by remember { mutableStateOf("12") }
    var warrantyType by remember { mutableStateOf("Manufacturer") }
    var warrantyProvider by remember { mutableStateOf("") }
    var warrantyContact by remember { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf("") }

    LaunchedEffect(productId) {
        if (productId != null) {
            withContext(Dispatchers.IO) {
                productDao.getProductById(productId).collect { p ->
                    if (p != null) {
                        productName = p.productName
                        brand = p.brand ?: ""
                        model = p.model ?: ""
                        category = p.category ?: ""
                        serialNumber = p.serialNumber ?: ""
                        store = p.purchaseStore ?: ""
                        priceStr = p.purchasePrice?.toString() ?: ""
                        notes = p.notes ?: ""
                        warrantyMonths = p.warrantyPeriodMonths?.toString() ?: "12"
                        warrantyType = p.warrantyProviderType ?: "Manufacturer"
                        warrantyProvider = p.warrantyProvider ?: ""
                        warrantyContact = p.warrantyContact ?: ""
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (productId == null) "Add Product" else "Edit Product", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Basic Info Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Basic Information", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Divider()

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = productName,
                            onValueChange = { productName = it },
                            label = { Text("Product Name *") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = brand,
                            onValueChange = { brand = it },
                            label = { Text("Brand") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it },
                            label = { Text("Model") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            label = { Text("Category (e.g. Laptop, Smartphone, Appliance)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = serialNumber,
                            onValueChange = { serialNumber = it },
                            label = { Text("Serial Number") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Purchase Info Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Purchase Details", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Divider()

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = store,
                            onValueChange = { store = it },
                            label = { Text("Purchase Store") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = priceStr,
                            onValueChange = { priceStr = it },
                            label = { Text("Purchase Price") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = warrantyMonths,
                            onValueChange = { warrantyMonths = it },
                            label = { Text("Warranty Period (months)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Warranty Provider Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Warranty Provider (Optional)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Divider()

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = warrantyType,
                            onValueChange = { warrantyType = it },
                            label = { Text("Type (Manufacturer, Extended, Retailer, etc.)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = warrantyProvider,
                            onValueChange = { warrantyProvider = it },
                            label = { Text("Provider Name") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = warrantyContact,
                            onValueChange = { warrantyContact = it },
                            label = { Text("Support Contact (Phone/Email)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Notes Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Notes", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Divider()

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Additional Notes") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        singleLine = false
                    )
                }
            }

            if (errorMessage.isNotBlank()) {
                Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    if (productName.isBlank()) {
                        errorMessage = "Product name is required."
                        return@Button
                    }
                    scope.launch(Dispatchers.IO) {
                        val now = System.currentTimeMillis()
                        val price = priceStr.toDoubleOrNull()
                        val months = warrantyMonths.toIntOrNull() ?: 12
                        val expiry = now + (months.toLong() * 30L * 86400000L)

                        if (productId == null) {
                            productDao.insert(
                                Product(
                                    userId = app.currentUserId,
                                    productName = productName.trim(),
                                    brand = brand.trim().ifBlank { null },
                                    model = model.trim().ifBlank { null },
                                    category = category.trim().ifBlank { null },
                                    serialNumber = serialNumber.trim().ifBlank { null },
                                    purchaseStore = store.trim().ifBlank { null },
                                    purchasePrice = price,
                                    purchaseDate = now,
                                    warrantyExpiryDate = expiry,
                                    warrantyPeriodMonths = months,
                                    warrantyProvider = warrantyProvider.trim().ifBlank { null },
                                    warrantyProviderType = warrantyType.trim().ifBlank { null },
                                    warrantyContact = warrantyContact.trim().ifBlank { null },
                                    notes = notes.trim().ifBlank { null }
                                )
                            )
                        } else {
                            productDao.insert(
                                Product(
                                    id = productId,
                                    userId = app.currentUserId,
                                    productName = productName.trim(),
                                    brand = brand.trim().ifBlank { null },
                                    model = model.trim().ifBlank { null },
                                    category = category.trim().ifBlank { null },
                                    serialNumber = serialNumber.trim().ifBlank { null },
                                    purchaseStore = store.trim().ifBlank { null },
                                    purchasePrice = price,
                                    purchaseDate = now,
                                    warrantyExpiryDate = expiry,
                                    warrantyPeriodMonths = months,
                                    warrantyProvider = warrantyProvider.trim().ifBlank { null },
                                    warrantyProviderType = warrantyType.trim().ifBlank { null },
                                    warrantyContact = warrantyContact.trim().ifBlank { null },
                                    notes = notes.trim().ifBlank { null },
                                    updatedAt = now
                                )
                            )
                        }
                        withContext(Dispatchers.Main) {
                            onBackClick()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(if (productId == null) "Save Product" else "Update Product", fontWeight = FontWeight.Medium, fontSize = 16.sp)
            }
        }
    }
}