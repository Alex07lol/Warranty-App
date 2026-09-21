package com.warrantyvault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.warrantyvault.WarrantyVaultApplication
import com.warrantyvault.data.Product
import com.warrantyvault.ui.components.*
import com.warrantyvault.ui.theme.RadiusLG
import com.warrantyvault.ui.theme.RadiusMD
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
            WarrantySectionHeader(title = "Product Information")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(RadiusLG),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    WarrantyFormField(
                        value = productName,
                        onValueChange = { productName = it },
                        label = "Product Name *",
                        placeholder = "e.g., MacBook Pro 14"
                    )

                    WarrantyFormField(
                        value = brand,
                        onValueChange = { brand = it },
                        label = "Brand",
                        placeholder = "e.g., Apple"
                    )

                    WarrantyFormField(
                        value = model,
                        onValueChange = { model = it },
                        label = "Model",
                        placeholder = "e.g., M3 Pro"
                    )

                    WarrantyFormField(
                        value = category,
                        onValueChange = { category = it },
                        label = "Category",
                        placeholder = "e.g., Laptop, Smartphone, Appliance"
                    )

                    WarrantyFormField(
                        value = serialNumber,
                        onValueChange = { serialNumber = it },
                        label = "Serial Number",
                        placeholder = "Optional"
                    )
                }
            }

            // Purchase Info Section
            WarrantySectionHeader(title = "Purchase Details")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(RadiusLG),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    WarrantyFormField(
                        value = store,
                        onValueChange = { store = it },
                        label = "Purchase Store",
                        placeholder = "e.g., Apple Store"
                    )

                    WarrantyFormField(
                        value = priceStr,
                        onValueChange = { priceStr = it },
                        label = "Purchase Price",
                        placeholder = "e.g., 1999.99"
                    )

                    WarrantyFormField(
                        value = warrantyMonths,
                        onValueChange = { warrantyMonths = it },
                        label = "Warranty Period (months)",
                        placeholder = "12"
                    )
                }
            }

            // Warranty Provider Section
            WarrantySectionHeader(title = "Warranty Provider (Optional)")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(RadiusLG),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    WarrantyFormField(
                        value = warrantyType,
                        onValueChange = { warrantyType = it },
                        label = "Type",
                        placeholder = "Manufacturer, Extended, Retailer, etc."
                    )

                    WarrantyFormField(
                        value = warrantyProvider,
                        onValueChange = { warrantyProvider = it },
                        label = "Provider Name",
                        placeholder = "e.g., AppleCare+"
                    )

                    WarrantyFormField(
                        value = warrantyContact,
                        onValueChange = { warrantyContact = it },
                        label = "Support Contact",
                        placeholder = "Phone / Email / Website"
                    )
                }
            }

            // Notes Section
            WarrantySectionHeader(title = "Notes")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(RadiusLG),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    WarrantyFormField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = "Additional Notes",
                        placeholder = "Any additional information...",
                        minLines = 3,
                        maxLines = 5
                    )
                }
            }

            if (errorMessage.isNotBlank()) {
                Text(text = errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            WarrantyPrimaryButton(
                text = if (productId == null) "Save Product" else "Update Product",
                onClick = {
                    if (productName.isBlank()) {
                        errorMessage = "Product name is required."
                        return@WarrantyPrimaryButton
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
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}