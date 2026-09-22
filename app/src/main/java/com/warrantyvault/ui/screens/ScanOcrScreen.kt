@file:OptIn(ExperimentalMaterial3Api::class)

package com.warrantyvault.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
    // OpenDocument: MIME-filtered documents contract; no gallery/storage permission needed and
    // no persistent grant required because the bytes are copied into private storage at once.
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.onImageSelected(it) }
    }
    val pickerMimes = arrayOf("image/*", "application/pdf")
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()

    WarrantyBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = WvDimens.ScreenGutter)
        ) {
            Spacer(Modifier.height(8.dp))
            Text("Scan Document", style = MaterialTheme.typography.headlineMedium, color = wv.textPrimary)
            Text("OCR → review → confirm. Nothing is saved without you.", style = MaterialTheme.typography.bodySmall, color = wv.textSecondary)
            Spacer(Modifier.height(WvDimens.Space4))

            AnimatedContent(
                targetState = state.step,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) + slideInVertically(animationSpec = tween(220)) { it / 12 })
                        .togetherWith(fadeOut(animationSpec = tween(140)))
                },
                label = "scanFlow",
                modifier = Modifier.weight(1f)
            ) { step ->
                when (step) {
                    ScanStep.IDLE, ScanStep.PROCESSING, ScanStep.PARSING, ScanStep.ERROR ->
                        ScanInputPane(
                            state = state,
                            onCamera = { cameraLauncher.launch(android.content.Intent(context, CameraCaptureActivity::class.java)) },
                            onPick = { pickImage.launch(pickerMimes) },
                            onDismissError = { vm.dismissError() }
                        )
                    ScanStep.REVIEW_REQUIRED, ScanStep.SAVING ->
                        state.reviewDraft?.let { draft ->
                            ReviewPane(
                                draft = draft,
                                state = state,
                                isSaving = step == ScanStep.SAVING,
                                onEdit = { field, value -> vm.updateField(field, value) },
                                onDateEdit = { field, millis -> vm.updateDateField(field, millis) },
                                onConfirm = { vm.confirmDraft() },
                                onCancel = { vm.cancelReview() },
                                onReparse = { vm.rescan() },
                                onRetake = { vm.cancelReview() }
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
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (state.step == ScanStep.PROCESSING || state.step == ScanStep.PARSING) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(46.dp),
                    color = wv.primary,
                    strokeWidth = 3.5.dp
                )
                Text(
                    text = if (state.step == ScanStep.PROCESSING) "Securing document…" else "Reading text…",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = wv.textPrimary
                )
                Text(
                    text = "private storage → OCR → parse",
                    style = MaterialTheme.typography.labelSmall,
                    color = wv.textMuted
                )
            }
        } else {
            Spacer(Modifier.height(8.dp))
            WarrantyPrimaryButton(
                text = "Take Photo",
                onClick = onCamera,
                icon = Icons.Default.PhotoCamera,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            )
            WarrantyGhostButton(
                text = "Choose Existing Image",
                onClick = onPick,
                icon = Icons.Default.PhotoLibrary,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            )
        }

        state.errorMessage?.let { err ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(WvDimens.RadiusMedium),
                color = wv.errorSoft,
                border = androidx.compose.foundation.BorderStroke(1.dp, wv.error.copy(alpha = 0.3f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Couldn't read this document", fontWeight = FontWeight.Bold, color = wv.error)
                    Text(err, style = MaterialTheme.typography.bodySmall, color = wv.textPrimary)
                    TextButton(onClick = onDismissError) { Text("OK", color = wv.primary) }
                }
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun ReviewPane(
    draft: ReviewDraft,
    state: ScanUiState,
    isSaving: Boolean,
    onEdit: (String, String) -> Unit,
    onDateEdit: (String, Long?) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onReparse: () -> Unit,
    onRetake: () -> Unit
) {
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Review Detected Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = wv.textPrimary)
            Spacer(Modifier.weight(1f))
            Surface(
                shape = CircleShape,
                color = wv.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, wv.borderSubtle),
                modifier = Modifier.size(36.dp)
            ) {
                IconButton(onClick = onReparse) {
                    Icon(Icons.Default.Replay, contentDescription = "Re-parse the captured text", tint = wv.textSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
        Text(
            "The ↻ icon re-reads the captured text. Edited fields are marked ✓.",
            style = MaterialTheme.typography.labelSmall,
            color = wv.textMuted
        )

        if (draft.result.warnings.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(WvDimens.RadiusMedium),
                color = wv.warningSoft
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Please double-check", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = wv.warning)
                    draft.result.warnings.forEach { warning ->
                        Text("• $warning", fontSize = 12.sp, color = wv.textSecondary, lineHeight = 18.sp)
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(WvDimens.RadiusMedium),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, wv.borderSubtle)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReviewTextField("Product Name", draft.productName, draft, "productName", onEdit, state.validationErrors["productName"], required = true)
                ReviewTextField("Brand", draft.brand, draft, "brand", onEdit, state.validationErrors["brand"])
                ReviewTextField("Model", draft.model, draft, "model", onEdit, state.validationErrors["model"])
                ReviewTextField("Serial Number", draft.serialNumber, draft, "serialNumber", onEdit, state.validationErrors["serialNumber"])
                ReviewTextField("IMEI", draft.imei, draft, "imei", onEdit, state.validationErrors["imei"])
                ReviewTextField("Store", draft.purchaseStore, draft, "purchaseStore", onEdit, state.validationErrors["purchaseStore"])
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1.4f)) {
                        ReviewTextField("Price", draft.priceText, draft, "priceText", onEdit, state.validationErrors["priceText"])
                    }
                    Box(Modifier.weight(1f)) {
                        ReviewTextField("Currency", draft.currency, draft, "currency", onEdit, state.validationErrors["currency"])
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(WvDimens.RadiusMedium),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, wv.borderSubtle)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReviewDateField("Purchase Date", draft.purchaseDate, draft, "purchaseDate", dateFormat, onDateEdit, state.validationErrors["purchaseDate"])
                ReviewDateField("Warranty Expiry", draft.warrantyExpiryDate, draft, "warrantyExpiryDate", dateFormat, onDateEdit, state.validationErrors["warrantyExpiryDate"])
                ReviewTextField("Warranty (months)", draft.warrantyMonthsText, draft, "warrantyMonthsText", onEdit, state.validationErrors["warrantyMonthsText"])
                ReviewTextField("Warranty Provider", draft.warrantyProvider, draft, "warrantyProvider", onEdit, state.validationErrors["warrantyProvider"])
                ReviewTextField("Warranty Type", draft.warrantyType, draft, "warrantyType", onEdit, state.validationErrors["warrantyType"])
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WarrantyPrimaryButton(
                text = if (isSaving) "Saving…" else "Confirm & Save",
                onClick = onConfirm,
                enabled = !isSaving && draft.productName.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            )
            WarrantyGhostButton(
                text = "Cancel — keep as unreviewed",
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().height(44.dp)
            )
        }
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun ReviewTextField(
    label: String,
    value: String,
    draft: ReviewDraft,
    field: String,
    onEdit: (String, String) -> Unit,
    errorText: String?,
    required: Boolean = false
) {
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (label + if (required) " *" else "").uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = wv.textMuted
            )
            if (draft.userEdits.contains(field)) {
                Spacer(Modifier.width(6.dp))
                Text("✓ edited", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = wv.success)
            } else if (value.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text("OCR", fontSize = 10.sp, color = wv.textMuted)
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = { onEdit(field, it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = errorText != null,
            shape = RoundedCornerShape(WvDimens.RadiusSmall),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = wv.primary,
                unfocusedBorderColor = wv.border,
                focusedTextColor = wv.textPrimary,
                unfocusedTextColor = wv.textPrimary,
                cursorColor = wv.primary,
                errorBorderColor = wv.error
            )
        )
        AnimatedVisibility(visible = errorText != null) {
            Text(errorText.orEmpty(), fontSize = 11.sp, color = wv.error)
        }
    }
}

@Composable
private fun ReviewDateField(
    label: String,
    value: Long?,
    draft: ReviewDraft,
    field: String,
    dateFormat: SimpleDateFormat,
    onDateEdit: (String, Long?) -> Unit,
    errorText: String?
) {
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    var showPicker by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = wv.textMuted)
            if (draft.userEdits.contains(field)) {
                Spacer(Modifier.width(6.dp))
                Text("✓ edited", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = wv.success)
            } else if (value != null) {
                Spacer(Modifier.width(6.dp))
                Text("OCR", fontSize = 10.sp, color = wv.textMuted)
            }
        }
        OutlinedTextField(
            value = value?.let { dateFormat.format(Date(it)) } ?: "",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            isError = errorText != null,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showPicker = true },
            shape = RoundedCornerShape(WvDimens.RadiusSmall),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = wv.primary,
                unfocusedBorderColor = wv.border,
                focusedTextColor = wv.textPrimary,
                unfocusedTextColor = wv.textPrimary,
                cursorColor = wv.primary,
                errorBorderColor = wv.error
            ),
            trailingIcon = {
                Row {
                    if (value != null) {
                        IconButton(onClick = { onDateEdit(field, null) }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear $label", tint = wv.textMuted)
                        }
                    }
                    IconButton(onClick = { showPicker = true }) {
                        Icon(Icons.Default.CalendarToday, contentDescription = "Pick $label", tint = wv.textMuted)
                    }
                }
            }
        )
        AnimatedVisibility(visible = errorText != null) {
            Text(errorText.orEmpty(), fontSize = 11.sp, color = wv.error)
        }
    }
    if (showPicker) {
        val initialMillis = value ?: System.currentTimeMillis()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                        ?: initialMillis
                    onDateEdit(field, millis)
                    showPicker = false
                }) { Text("OK", color = wv.primary) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel", color = wv.textSecondary) } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun SuccessPane(message: String, onDone: () -> Unit, onScanAnother: () -> Unit) {
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
    ) {
        Surface(
            shape = CircleShape,
            color = wv.successSoft,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = wv.success,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Text("Product saved", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = wv.textPrimary)
        Text(
            if (message.isNotBlank()) message else "Document attached · marked reviewed ✓",
            style = MaterialTheme.typography.bodySmall,
            color = wv.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        WarrantyPrimaryButton(text = "View products", onClick = onDone, modifier = Modifier.fillMaxWidth().height(48.dp))
        WarrantyGhostButton(text = "Scan Another Document", onClick = onScanAnother, modifier = Modifier.fillMaxWidth().height(44.dp))
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
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    val topMatch = candidates.firstOrNull()

    AlertDialog(
        onDismissRequest = onCancel,
        shape = RoundedCornerShape(WvDimens.RadiusLarge),
        containerColor = wv.surfaceElevated,
        title = {
            Column {
                Text(
                    "Existing product found",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = wv.textPrimary
                )
                if (topMatch != null) {
                    Text(
                        "${topMatch.reason} for ${topMatch.product.productName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = wv.textSecondary
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                candidates.take(2).forEach { m ->
                    Surface(
                        shape = RoundedCornerShape(WvDimens.RadiusMedium),
                        color = wv.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, wv.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Product", style = MaterialTheme.typography.labelSmall, color = wv.textMuted)
                                Text(m.product.productName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = wv.textPrimary)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Reason", style = MaterialTheme.typography.labelSmall, color = wv.textMuted)
                                Text(m.reason, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = wv.success)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                topMatch?.let { match ->
                    WarrantyPrimaryButton(
                        text = "Use existing",
                        onClick = { onUseExisting(match.product.id) },
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    )
                    Button(
                        onClick = { onUpdateExisting(match.product.id) },
                        shape = RoundedCornerShape(WvDimens.RadiusSmall),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = wv.primarySoft,
                            contentColor = wv.primary
                        ),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Text("Update existing (fills blanks only)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
                WarrantyGhostButton(
                    text = "Create new product",
                    onClick = onCreateNew,
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                )
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel", color = wv.textSecondary)
                }
            }
        }
    )
}
