package com.warrantyvault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.warrantyvault.WarrantyVaultApplication
import com.warrantyvault.data.Product
import com.warrantyvault.ocr.DateUtils
import com.warrantyvault.ui.components.*
import com.warrantyvault.ui.theme.RadiusLG
import com.warrantyvault.ui.theme.RadiusMD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var currency by remember { mutableStateOf("USD") }
    var notes by remember { mutableStateOf("") }
    var warrantyMonths by remember { mutableStateOf("") }
    var warrantyType by remember { mutableStateOf("Manufacturer") }
    var warrantyProvider by remember { mutableStateOf("") }
    var warrantyContact by remember { mutableStateOf("") }

    // Dates start null — never silently "now". The user picks them explicitly.
    var purchaseDate by remember { mutableStateOf<Long?>(null) }
    var warrantyExpiry by remember { mutableStateOf<Long?>(null) }
    val expiryExplicitlyEdited = remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(productId == null) }
    var existingProduct by remember { mutableStateOf<Product?>(null) }
    var duplicateOf by remember { mutableStateOf<Product?>(null) }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    LaunchedEffect(productId) {
        if (productId != null) {
            withContext(Dispatchers.IO) {
                val p = productDao.getProductByIdImmediate(productId)
                if (p != null) {
                    existingProduct = p
                    productName = p.productName
                    brand = p.brand ?: ""
                    model = p.model ?: ""
                    category = p.category ?: ""
                    serialNumber = p.serialNumber ?: ""
                    store = p.purchaseStore ?: ""
                    priceStr = p.purchasePrice?.toString() ?: ""
                    currency = p.currency
                    notes = p.notes ?: ""
                    warrantyMonths = p.warrantyPeriodMonths?.toString() ?: ""
                    warrantyType = p.warrantyProviderType ?: "Manufacturer"
                    warrantyProvider = p.warrantyProvider ?: ""
                    warrantyContact = p.warrantyContact ?: ""
                    purchaseDate = p.purchaseDate
                    warrantyExpiry = p.warrantyExpiryDate
                    expiryExplicitlyEdited.value = p.warrantyExpiryDate != null
                }
                loaded = true
            }
        }
    }

    fun computeExpiry(): Long? {
        if (expiryExplicitlyEdited.value) return warrantyExpiry
        val pd = purchaseDate ?: return warrantyExpiry
        val months = warrantyMonths.toIntOrNull() ?: return warrantyExpiry
        return DateUtils.toEpochMillis(DateUtils.addMonths(DateUtils.fromEpochMillis(pd), months))
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
        if (!loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

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
                    DatePickerField(
                        label = "Purchase Date",
                        value = purchaseDate,
                        dateFormat = dateFormat,
                        onPick = {
                            purchaseDate = it
                            expiryExplicitlyEdited.value = false
                        },
                        onClear = { purchaseDate = null }
                    )
                    WarrantyFormField(
                        value = store,
                        onValueChange = { store = it },
                        label = "Purchase Store",
                        placeholder = "e.g., Apple Store"
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1.5f)) {
                            WarrantyFormField(
                                value = priceStr,
                                onValueChange = { priceStr = it },
                                label = "Purchase Price",
                                placeholder = "e.g., 1999.99"
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            WarrantyFormField(
                                value = currency,
                                onValueChange = { currency = it.uppercase().take(3) },
                                label = "Currency",
                                placeholder = "USD"
                            )
                        }
                    }
                }
            }

            // Warranty Section
            WarrantySectionHeader(title = "Warranty")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(RadiusLG),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    WarrantyFormField(
                        value = warrantyMonths,
                        onValueChange = { v ->
                            warrantyMonths = v.filter { it.isDigit() }.take(3)
                            expiryExplicitlyEdited.value = false
                        },
                        label = "Warranty Period (months)",
                        placeholder = "e.g., 24"
                    )
                    DatePickerField(
                        label = "Warranty Expiry (auto from months, or pick)",
                        value = computeExpiry(),
                        dateFormat = dateFormat,
                        onPick = {
                            warrantyExpiry = it
                            expiryExplicitlyEdited.value = true
                        },
                        onClear = {
                            warrantyExpiry = null
                            expiryExplicitlyEdited.value = false
                        }
                    )
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
                    val months = warrantyMonths.toIntOrNull()
                    if (warrantyMonths.isNotBlank() && (months == null || months <= 0 || months > 240)) {
                        errorMessage = "Warranty period must be between 1 and 240 months."
                        return@WarrantyPrimaryButton
                    }
                    errorMessage = ""
                    val pd = purchaseDate
                    val expiry = computeExpiry()
                    val snapshot = ProductFormSnapshot(
                        productName = productName.trim(),
                        brand = brand.trim().ifBlank { null },
                        model = model.trim().ifBlank { null },
                        category = category.trim().ifBlank { null },
                        serialNumber = serialNumber.trim().ifBlank { null },
                        purchaseStore = store.trim().ifBlank { null },
                        purchasePrice = priceStr.toDoubleOrNull(),
                        currency = currency.ifBlank { "USD" },
                        purchaseDate = pd,
                        warrantyExpiryDate = expiry,
                        warrantyPeriodMonths = months,
                        warrantyProvider = warrantyProvider.trim().ifBlank { null },
                        warrantyProviderType = warrantyType.trim().ifBlank { null },
                        warrantyContact = warrantyContact.trim().ifBlank { null },
                        notes = notes.trim().ifBlank { null }
                    )
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            if (productId == null) {
                                insertNew(productDao, app.currentUserId, snapshot)
                            } else {
                                updateExisting(productDao, app.currentUserId, productId, snapshot)
                            }
                        }
                        when (result) {
                            is SaveResult.OK -> onBackClick()
                            is SaveResult.DuplicateSerial -> duplicateOf = result.existing
                            is SaveResult.Error -> errorMessage = result.message
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    duplicateOf?.let { dup ->
        AlertDialog(
            onDismissRequest = { duplicateOf = null },
            title = { Text("Duplicate serial number") },
            text = {
                Text(
                    "A product with this serial already exists:\n\n${dup.productName}" +
                        (dup.brand?.let { "\n$it" } ?: "") +
                        "\n\nClear the serial to save anyway, or go back and edit."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    serialNumber = ""
                    duplicateOf = null
                }) { Text("Clear serial & save") }
            },
            dismissButton = {
                TextButton(onClick = { duplicateOf = null }) { Text("Back to edit") }
            }
        )
    }
}

/** Snapshot of the form at save time. */
private data class ProductFormSnapshot(
    val productName: String,
    val brand: String?,
    val model: String?,
    val category: String?,
    val serialNumber: String?,
    val purchaseStore: String?,
    val purchasePrice: Double?,
    val currency: String,
    val purchaseDate: Long?,
    val warrantyExpiryDate: Long?,
    val warrantyPeriodMonths: Int?,
    val warrantyProvider: String?,
    val warrantyProviderType: String?,
    val warrantyContact: String?,
    val notes: String?
)

private sealed interface SaveResult {
    data object OK : SaveResult
    data class DuplicateSerial(val existing: Product) : SaveResult
    data class Error(val message: String) : SaveResult
}

private suspend fun insertNew(
    productDao: com.warrantyvault.data.ProductDao,
    userId: Long,
    s: ProductFormSnapshot
): SaveResult {
    s.serialNumber?.let { serial ->
        productDao.getProductBySerialNumber(userId, serial)?.let {
            return SaveResult.DuplicateSerial(it)
        }
    }
    return try {
        productDao.insertProduct(
            Product(
                userId = userId,
                productName = s.productName,
                brand = s.brand,
                model = s.model,
                category = s.category,
                serialNumber = s.serialNumber,
                purchaseStore = s.purchaseStore,
                purchasePrice = s.purchasePrice,
                currency = s.currency,
                purchaseDate = s.purchaseDate,
                warrantyExpiryDate = s.warrantyExpiryDate,
                warrantyPeriodMonths = s.warrantyPeriodMonths,
                warrantyProvider = s.warrantyProvider,
                warrantyProviderType = s.warrantyProviderType,
                warrantyContact = s.warrantyContact,
                notes = s.notes
            )
        )
        SaveResult.OK
    } catch (e: Exception) {
        SaveResult.Error(e.message ?: "Could not save the product.")
    }
}

private suspend fun updateExisting(
    productDao: com.warrantyvault.data.ProductDao,
    userId: Long,
    productId: Long,
    s: ProductFormSnapshot
): SaveResult {
    val original = productDao.getProductByIdImmediate(productId)
        ?: return SaveResult.Error("Product no longer exists.")
    s.serialNumber?.let { serial ->
        if (!serial.equals(original.serialNumber, ignoreCase = true)) {
            productDao.getProductBySerialNumber(userId, serial)?.let {
                if (it.id != original.id) return SaveResult.DuplicateSerial(it)
            }
        }
    }
    // Real UPDATE via @Update: preserves id, userId, createdAt, tags, documents (separate table),
    // and every field the form doesn't touch. purchaseDate changes only because the user picked it.
    val updated = original.copy(
        productName = s.productName,
        brand = s.brand,
        model = s.model,
        category = s.category,
        serialNumber = s.serialNumber,
        purchaseStore = s.purchaseStore,
        purchasePrice = s.purchasePrice,
        currency = s.currency,
        purchaseDate = s.purchaseDate,
        warrantyExpiryDate = s.warrantyExpiryDate,
        warrantyPeriodMonths = s.warrantyPeriodMonths,
        warrantyProvider = s.warrantyProvider,
        warrantyProviderType = s.warrantyProviderType,
        warrantyContact = s.warrantyContact,
        notes = s.notes,
        updatedAt = System.currentTimeMillis()
    )
    return try {
        val rows = productDao.updateProduct(updated)
        if (rows > 0) SaveResult.OK else SaveResult.Error("Product no longer exists.")
    } catch (e: Exception) {
        SaveResult.Error(e.message ?: "Could not update the product.")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    label: String,
    value: Long?,
    dateFormat: SimpleDateFormat,
    onPick: (Long) -> Unit,
    onClear: () -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value?.let { dateFormat.format(Date(it)) } ?: "",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            shape = RoundedCornerShape(RadiusMD),
            trailingIcon = {
                Row {
                    if (value != null) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Default.Close, contentDescription = "Clear date")
                        }
                    }
                    IconButton(onClick = { showPicker = true }) {
                        Icon(Icons.Default.CalendarToday, contentDescription = "Select date")
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )
    }
    if (showPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = value ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let(onPick)
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
