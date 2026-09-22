package com.warrantyvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.warrantyvault.WarrantyEngine
import com.warrantyvault.WarrantyVaultApplication
import com.warrantyvault.data.Product
import com.warrantyvault.data.ServiceHistory
import com.warrantyvault.ui.components.StatusBadge
import com.warrantyvault.ui.theme.WvDimens
import com.warrantyvault.ui.theme.darkWvColors
import com.warrantyvault.ui.theme.lightWvColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val detailDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

@Composable
fun ProductDetailScreen(
    productId: Long,
    onBackClick: () -> Unit,
    onEditClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as WarrantyVaultApplication
    val productDao = app.database.productDao()
    val scope = rememberCoroutineScope()
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()

    var product by remember { mutableStateOf<Product?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Collect product updates on a background dispatcher via the DAO flow.
    LaunchedEffect(productId) {
        withContext(Dispatchers.IO) {
            productDao.getProductById(productId).collect { p -> product = p }
        }
    }

    WarrantyBackground {
        product?.let { p ->
            val info = WarrantyEngine.warrantyStatusOf(p.purchaseDate, p.warrantyExpiryDate)

            Column(
                Modifier
                    .fillMaxSize()
            ) {
                // Header: back, title, edit, delete
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = WvDimens.Space2, vertical = WvDimens.Space2),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = wv.textPrimary)
                    }
                    Text(
                        p.productName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = wv.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onEditClick(p.id) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit product", tint = wv.textSecondary)
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete product", tint = wv.error)
                    }
                }

                val tabs = listOf("Overview", "Documents", "Repairs")
                var selectedTab by remember(p.id) { mutableIntStateOf(0) }

                // Tab row
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = wv.primary,
                    divider = { HorizontalDivider(color = wv.borderSubtle) }
                ) {
                    tabs.forEachIndexed { i, title ->
                        Tab(
                            selected = selectedTab == i,
                            onClick = { selectedTab = i },
                            text = {
                                Text(
                                    title,
                                    fontWeight = if (selectedTab == i) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selectedTab == i) wv.primary else wv.textSecondary
                                )
                            }
                        )
                    }
                }

                // Content
                when (selectedTab) {
                    0 -> OverviewTab(p, info)
                    1 -> DocumentsTab(p.id)
                    2 -> RepairsTab(p.id)
                }
            }

            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text("Delete product?") },
                    text = { Text("This removes \"${p.productName}\" and its service history. Documents are kept as unlinked files.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showDeleteConfirm = false
                            scope.launch(Dispatchers.IO) {
                                productDao.softDelete(p.id, System.currentTimeMillis())
                                withContext(Dispatchers.Main) { onBackClick() }
                            }
                        }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
                )
            }
        } ?: run {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = wv.primary)
            }
        }
    }
}

@Composable
private fun OverviewTab(p: Product, info: com.warrantyvault.WarrantyInfo) {
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    val (statusColor, _) = wv.statusColors(info.status)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = WvDimens.ScreenGutter, end = WvDimens.ScreenGutter, top = WvDimens.Space3, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(WvDimens.Space4)
    ) {
        // ---- Hero card ----
        item {
            Surface(
                shape = RoundedCornerShape(WvDimens.RadiusLarge),
                color = androidx.compose.ui.graphics.Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(1.dp, wv.borderSubtle)
            ) {
                Box(
                    modifier = Modifier
                        .background(wv.heroBrush)
                        .padding(WvDimens.Space5)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(WvDimens.Space2)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                p.productName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = wv.textPrimary,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            StatusBadge(status = info.status, label = info.label)
                        }
                        listOfNotNull(p.brand, p.model).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = wv.textSecondary)
                        }
                        // Days remaining + progress
                        info.daysRemaining?.let { days ->
                            val total = p.warrantyPeriodMonths?.let { m -> m * 30.44 }?.toInt()
                            val progress = if (total != null && total > 0) {
                                (total - days).coerceIn(0, total) / total.toFloat()
                            } else null
                            Spacer(Modifier.height(WvDimens.Space1))
                            LinearProgressIndicator(
                                progress = { (1f - (progress ?: 0f)).coerceIn(0f, 1f) },
                                color = statusColor,
                                trackColor = wv.surfaceHighest,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .androidx.compose.ui.draw.clip(RoundedCornerShape(WvDimens.RadiusPill))
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Purchase date", style = MaterialTheme.typography.labelSmall, color = wv.textMuted)
                                Text(
                                    p.purchaseDate?.let { detailDateFormat.format(Date(it)) } ?: "—",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = wv.textPrimary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Expiry", style = MaterialTheme.typography.labelSmall, color = wv.textMuted)
                                Text(
                                    p.warrantyExpiryDate?.let { detailDateFormat.format(Date(it)) } ?: "—",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = wv.textPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- Warranty information ----
        item {
            InfoCard("Warranty Information") {
                KeyValue("Status", info.label, valueColor = statusColor)
                KeyValue("Days Remaining", info.daysRemaining?.toString() ?: "Unknown")
                KeyValue("Warranty Period", p.warrantyPeriodMonths?.let { "$it months" })
                KeyValue("Warranty Expiry", p.warrantyExpiryDate?.let { detailDateFormat.format(Date(it)) })
                KeyValue("Warranty Provider", p.warrantyProvider)
                KeyValue("Support Contact", p.warrantyContact)
            }
        }

        // ---- Purchase information ----
        item {
            InfoCard("Purchase Information") {
                KeyValue("Purchase Date", p.purchaseDate?.let { detailDateFormat.format(Date(it)) })
                KeyValue("Purchase Price", p.purchasePrice?.let { "${p.currency} ${it}" })
                KeyValue("Store", p.purchaseStore)
            }
        }

        // ---- Product identifiers ----
        item {
            InfoCard("Product Identifiers") {
                IdentifierRow("Serial Number", p.serialNumber)
                IdentifierRow("IMEI", p.imei, sensitive = true)
                IdentifierRow("Model", p.model)
                IdentifierRow("Category", p.category)
            }
        }

        if (!p.notes.isNullOrBlank()) {
            item {
                InfoCard("Notes") {
                    Text(p.notes, style = MaterialTheme.typography.bodyMedium, color = wv.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun DocumentsTab(productId: Long) {
    val app = LocalContext.current.applicationContext as WarrantyVaultApplication
    val documents by app.database.documentDao().getDocumentsByProductId(productId)
        .collectAsState(initial = emptyList())
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()

    if (documents.isEmpty()) {
        Column(Modifier.padding(horizontal = WvDimens.ScreenGutter, vertical = WvDimens.Space3)) {
            EmptyStateCard(
                title = "No documents yet",
                body = "Invoices, receipts and warranty cards you scan for this product will appear here.",
                actionText = "Scan Document"
            )
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = WvDimens.ScreenGutter, end = WvDimens.ScreenGutter, top = WvDimens.Space3, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(WvDimens.Space3)
        ) {
            items(documents.size) { i ->
                val d = documents[i]
                DocumentRow(
                    fileName = d.fileName,
                    addedAt = d.uploadedAt,
                    verified = d.verified
                )
            }
        }
    }
}

@Composable
private fun RepairsTab(productId: Long) {
    val app = LocalContext.current.applicationContext as WarrantyVaultApplication
    val repairs by app.database.serviceHistoryDao().getServiceHistoryByProductId(productId)
        .collectAsState(initial = emptyList())
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()

    if (repairs.isEmpty()) {
        Column(Modifier.padding(horizontal = WvDimens.ScreenGutter, vertical = WvDimens.Space3)) {
            EmptyStateCard(
                title = "No repair history yet",
                body = "Service events for this product — screen replacements, battery swaps, diagnostics — will appear here."
            )
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = WvDimens.ScreenGutter, end = WvDimens.ScreenGutter, top = WvDimens.Space3, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(WvDimens.Space3)
        ) {
            items(repairs.size) { i ->
                RepairEventRow(repairs[i])
            }
        }
    }
}

@Composable
private fun DocumentRow(fileName: String, addedAt: Long, verified: Boolean) {
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    Surface(
        shape = RoundedCornerShape(WvDimens.RadiusMedium),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, wv.borderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(WvDimens.Space3), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(WvDimens.RadiusSmall),
                color = wv.primarySoft,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = wv.primary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(WvDimens.Space3))
            Column(Modifier.weight(1f)) {
                Text(fileName, style = MaterialTheme.typography.titleSmall, color = wv.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Added ${detailDateFormat.format(Date(addedAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = wv.textMuted
                    )
                    if (verified) {
                        Text(" · ", style = MaterialTheme.typography.labelSmall, color = wv.textMuted)
                        Text(
                            "✓ reviewed",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = wv.success
                        )
                    }
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = wv.textMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun RepairEventRow(event: ServiceHistory) {
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    Surface(
        shape = RoundedCornerShape(WvDimens.RadiusMedium),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, wv.borderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(WvDimens.Space3), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    event.serviceType.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = wv.textPrimary
                )
                Text(
                    detailDateFormat.format(Date(event.serviceDate)),
                    style = MaterialTheme.typography.labelSmall,
                    color = wv.textMuted
                )
            }
            event.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = wv.textSecondary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    event.serviceProvider ?: "Service",
                    style = MaterialTheme.typography.labelSmall,
                    color = wv.textMuted
                )
                event.cost?.let {
                    Text(
                        "${event.currency} ${it}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = wv.textPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    Surface(
        shape = RoundedCornerShape(WvDimens.RadiusMedium),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, wv.borderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(WvDimens.Space4), verticalArrangement = Arrangement.spacedBy(WvDimens.Space3)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = wv.textPrimary)
            content()
        }
    }
}

@Composable
private fun KeyValue(label: String, value: String?, valueColor: androidx.compose.ui.graphics.Color? = null) {
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = wv.textMuted)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor ?: wv.textPrimary,
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

/** Identifier row with copy; sensitive values are masked until the reveal is pressed. */
@Composable
private fun IdentifierRow(label: String, value: String?, sensitive: Boolean = false) {
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    val clipboard = LocalClipboardManager.current
    var revealed by remember(label) { mutableStateOf(!sensitive) }
    if (value.isNullOrBlank()) return

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = wv.textMuted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (revealed) value else value.map { "•" }.joinToString(""),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = wv.textPrimary
            )
            IconButton(onClick = {
                clipboard.setText(AnnotatedString(value))
            }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy $label", tint = wv.textMuted, modifier = Modifier.size(15.dp))
            }
            if (sensitive) {
                IconButton(onClick = { revealed = !revealed }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (revealed) "Hide $label" else "Show $label",
                        tint = wv.textMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}
