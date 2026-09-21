package com.warrantyvault.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.warrantyvault.WarrantyVaultApplication
import com.warrantyvault.data.Document
import com.warrantyvault.ocr.AndroidOcrEngine
import com.warrantyvault.ocr.OcrResult
import com.warrantyvault.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanOcrScreen(
    onDocumentSaved: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as WarrantyVaultApplication
    val documentDao = app.database.documentDao()
    val scope = rememberCoroutineScope()
    val ocrEngine = remember { AndroidOcrEngine(context) }

    var isProcessing by remember { mutableStateOf(false) }
    var scanStatus by remember { mutableStateOf("Take a photo of a receipt or warranty card to extract warranty details.") }
    var extractedData by remember { mutableStateOf<OcrResult?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            isProcessing = true
            scanStatus = "Processing image with ML Kit OCR..."
            scope.launch(Dispatchers.IO) {
                try {
                    val result = ocrEngine.recognizeText(it)
                    val now = System.currentTimeMillis()
                    documentDao.insert(
                        Document(
                            userId = app.currentUserId,
                            documentType = "receipt",
                            fileName = "scanned_receipt_$now.jpg",
                            filePath = it.toString(),
                            fileSize = 102400L,
                            mimeType = "image/jpeg",
                            docState = "unreviewed",
                            verified = true,
                            ocrStatus = "done",
                            ocrText = result.rawText,
                            parsedData = result.toJson()
                        )
                    )
                    withContext(Dispatchers.Main) {
                        extractedData = result
                        isProcessing = false
                        scanStatus = "OCR Complete! Extracted: ${result.productName ?: "Unknown"}"
                        onDocumentSaved()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isProcessing = false
                        scanStatus = "Error: ${e.message}"
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(title = { Text("Scan OCR") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "OCR",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Light
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = scanStatus,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (isProcessing) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Extracting text and parsing warranty details...")
            } else {
                Button(
                    onClick = { pickImage.launch("image/*") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Capture & Run ML Kit OCR", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }

            extractedData?.let { result ->
                Spacer(modifier = Modifier.height(32.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Extracted Data", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            result.productName?.let { DetailRow("Product", it) }
                            result.brand?.let { DetailRow("Brand", it) }
                            result.model?.let { DetailRow("Model", it) }
                            result.serialNumber?.let { DetailRow("Serial", it) }
                            result.purchasePrice?.let { DetailRow("Price", it.toString()) }
                            result.purchaseStore?.let { DetailRow("Store", it) }
                            result.purchaseDate?.let { DetailRow("Date", it) }
                            result.warrantyExpiryDate?.let { DetailRow("Warranty Expiry", it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}