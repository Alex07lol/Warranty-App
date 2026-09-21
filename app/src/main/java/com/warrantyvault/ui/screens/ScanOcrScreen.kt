@file:OptIn(ExperimentalMaterial3Api::class)

package com.warrantyvault.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.warrantyvault.WarrantyVaultApplication
import com.warrantyvault.ocr.ProductMatch
import com.warrantyvault.ocr.ReviewDraft
import com.warrantyvault.ocr.ScanStep
import com.warrantyvault.ocr.ScanUiState
import com.warrantyvault.ui.CameraCaptureActivity
import com.warrantyvault.ui.components.*
import com.warrantyvault.ui.theme.RadiusLG
import com.warrantyvault.ui.theme.RadiusMD
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScanOcrScreen(
    onDocumentSaved: (Long?) -> Unit
) {
    val context = LocalContext.current
    val vm: ScanWorkflowViewModelHolder = viewModel()
    val state by vm.state.collectAsState()

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val path = result.data?.getStringExtra(CameraCaptureActivity.EXTRA_OUTPUT_PATH)
        if (path != null) vm.onImageSelected(Uri.fromFile(java.io.File(path)))
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.onImageSelected(it) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Scan Document") }) }) { padding ->
        WarrantyBackground(modifier = Modifier.padding(padding)) {
            AnimatedContent(
                targetState = state.step,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) + slideInVertically(animationSpec = tween(220)) { it / 12 })
                        .togetherWith(fadeOut(animationSpec = tween(140)))
                },
                label = "scanFlow"
            ) { step ->
                when (step) {
                    ScanStep.IDLE, ScanStep.PROCESSING, ScanStep.PARSING, ScanStep.ERROR ->
                        ScanInputPane(
                            state = state,
                            onCamera = { cameraLauncher.launch(android.content.Intent(context, CameraCaptureActivity::class.java)) },
                            onPick = { pickImage.launch("image/*") },
                            onDismissError = { vm.dismissError() }
                        )
                    ScanStep.REVIEW_REQUIRED, ScanStep.SAVING ->
                        state.reviewDraft?.let { draft ->
                            ReviewPane(
                                draft = draft,
                                isSaving = step == ScanStep.SAVING,
                                onEdit = { field, value -> vm.updateField(field, value) },
                                onDateEdit = { field, millis -> vm.updateDateField(field, millis) },
                                onConfirm = { vm.confirmDraft() },
                                onCancel = { vm.cancelReview() },
                                onRescan = { vm.rescan() }
                            )
                        }
                    ScanStep.SUCCESS -> SuccessPane(
                        message = state.statusMessage,
                        onDone = { onDocumentSaved(state.savedProductId) },
                        onScanAnother = { vm.reset() }
                    )
                }
            }

            if (state.showMatchDialog) {
                MatchDialog(
                    candidates = state.matchCandidates,
                    onUseExisting = { vm.useExistingProduct(it) },
                    onUpdateExisting = { vm.updateExistingProduct(it) },
                    onCreateNew = { vm.createNewFromDraft() },
                    onCancel = { vm.cancelMatchDialog() }
                )
            }
        }
    }
}

@Composable
private fun ScanInputPane(
    state: ScanUiState,
    onCamera: () -> Unit,
    onPick: () -> Unit,
    onDismissError: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "Add a receipt or warranty document",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "We extract the details, then you review them before anything is saved.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        if (state.step == ScanStep.PROCESSING || state.step == ScanStep.PARSING) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            Text(state.statusMessage, style = MaterialTheme.typography.bodyMedium)
        } else {
            WarrantyPrimaryButton(
                text = "Take Photo",
                onClick = onCamera,
                icon = { Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            )
            WarrantyGhostButton(
                text = "Choose Existing Image",
                onClick = onPick,
                icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            )
        }

        state.errorMessage?.let { err ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(RadiusLG),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Couldn't read this document", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(err, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = onDismissError) { Text("OK") }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ReviewPane(
    draft: ReviewDraft,
    isSaving: Boolean,
    onEdit: (String, String) -> Unit,
    onDateEdit: (String, Long?) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onRescan: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Review Detected Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRescan) {
                Icon(Icons.Default.Replay, contentDescription = "Re-parse text")
            }
        }

        if (draft.result.warnings.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(RadiusLG),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Please double-check", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    draft.result.warnings.forEach { warning ->
                        Text("• $warning", fontSize = 13.sp, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (draft.hasUserEdits) {
            Text("Edited fields are marked ✓", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(RadiusLG),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReviewTextField("Product Name", draft.productName, draft, "productName", onEdit, required = true)
                ReviewTextField("Brand", draft.brand, draft, "brand", onEdit)
                ReviewTextField("Model", draft.model, draft, "model", onEdit)
                ReviewTextField("Serial Number", draft.serialNumber, draft, "serialNumber", onEdit)
                ReviewTextField("IMEI", draft.imei, draft, "imei", onEdit)
                ReviewTextField("Store", draft.purchaseStore, draft, "purchaseStore", onEdit)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1.4f)) {
                        ReviewTextField("Price", draft.priceText, draft, "priceText", onEdit)
                    }
                    Box(Modifier.weight(1f)) {
                        ReviewTextField("Currency", draft.currency, draft, "currency", onEdit)
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(RadiusLG),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReviewDateField("Purchase Date", draft.purchaseDate, draft, "purchaseDate", dateFormat, onDateEdit)
                ReviewDateField("Warranty Expiry", draft.warrantyExpiryDate, draft, "warrantyExpiryDate", dateFormat, onDateEdit)
                ReviewTextField("Warranty (months)", draft.warrantyMonthsText, draft, "warrantyMonthsText", onEdit)
                ReviewTextField("Warranty Provider", draft.warrantyProvider, draft, "warrantyProvider", onEdit)
                ReviewTextField("Warranty Type", draft.warrantyType, draft, "warrantyType", onEdit)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WarrantyGhostButton(text = "Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
            WarrantyPrimaryButton(
                text = if (isSaving) "Saving…" else "Confirm & Save",
                onClick = onConfirm,
                enabled = !isSaving && draft.productName.isNotBlank(),
                modifier = Modifier.weight(1.4f)
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ReviewTextField(
    label: String,
    value: String,
    draft: ReviewDraft,
    field: String,
    onEdit: (String, String) -> Unit,
    required: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label + if (required) " *" else "",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (draft.userEdits.contains(field)) {
                Spacer(Modifier.width(6.dp))
                Text("✓ edited", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
            } else if (value.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text("OCR detected", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = { onEdit(field, it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(RadiusMD)
        )
    }
}

@Composable
private fun ReviewDateField(
    label: String,
    value: Long?,
    draft: ReviewDraft,
    field: String,
    dateFormat: SimpleDateFormat,
    onDateEdit: (String, Long?) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (draft.userEdits.contains(field)) {
                Spacer(Modifier.width(6.dp))
                Text("✓ edited", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
            } else if (value != null) {
                Spacer(Modifier.width(6.dp))
                Text("OCR detected", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedTextField(
            value = value?.let { dateFormat.format(Date(it)) } ?: "",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showPicker = true },
            shape = RoundedCornerShape(RadiusMD),
            trailingIcon = {
                Row {
                    if (value != null) {
                        IconButton(onClick = { onDateEdit(field, null) }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear $label")
                        }
                    }
                    IconButton(onClick = { showPicker = true }) {
                        Icon(Icons.Default.CalendarToday, contentDescription = "Pick $label")
                    }
                }
            }
        )
    }
    if (showPicker) {
        val initialMillis = value ?: System.currentTimeMillis()
        val initialDate = java.time.Instant.ofEpochMilli(initialMillis)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                        ?: initialMillis
                    onDateEdit(field, millis)
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun SuccessPane(message: String, onDone: () -> Unit, onScanAnother: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Text("Done!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(message, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        WarrantyPrimaryButton(text = "View Products", onClick = onDone, modifier = Modifier.fillMaxWidth())
        WarrantyGhostButton(text = "Scan Another Document", onClick = onScanAnother, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun MatchDialog(
    candidates: List<ProductMatch>,
    onUseExisting: (Long) -> Unit,
    onUpdateExisting: (Long) -> Unit,
    onCreateNew: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Existing product found") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                candidates.take(3).forEach { m ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(m.product.productName, fontWeight = FontWeight.Bold)
                            Text(
                                listOfNotNull(m.product.brand, m.product.model, m.product.serialNumber).joinToString(" · "),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("Match: ${m.matchType.replace('_', ' ')}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { candidates.firstOrNull()?.let { onUseExisting(it.product.id) } }) { Text("Use existing") }
                TextButton(onClick = { candidates.firstOrNull()?.let { onUpdateExisting(it.product.id) } }) { Text("Update existing (fill blanks)") }
                TextButton(onClick = onCreateNew) { Text("Create new product") }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    )
}


