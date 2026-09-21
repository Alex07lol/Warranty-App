package com.warrantyvault.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.warrantyvault.WarrantyVaultApplication
import com.warrantyvault.service.ExportImportService
import com.warrantyvault.ui.components.*
import com.warrantyvault.ui.theme.RadiusLG
import com.warrantyvault.ui.theme.RadiusMD
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as WarrantyVaultApplication
    val exportImportService = remember { ExportImportService(context, app.database) }
    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = exportImportService.importJson(uri)
                val message = if (result.errors.isEmpty()) {
                    "Imported ${result.imported} items"
                } else {
                    "Import completed with ${result.failed} failures: ${result.errors.joinToString(", ")}"
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings & Privacy", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        WarrantyBackground(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Appearance Section
                WarrantySectionHeader(title = "Appearance")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(RadiusLG),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp).padding(end = 12.dp))
                            Column {
                                Text("Theme", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Light / Dark / System", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Data & Privacy Section
                WarrantySectionHeader(title = "Data & Privacy")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(RadiusLG),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(RadiusMD))
                            ) {
                                Icon(
                                    Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Local-First Architecture", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("100% Offline", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Text(
                            text = "All your products, receipts, warranty cards, service records, and OCR data are stored securely and privately on your device using Android Room Database. No cloud account, no tracking, no ads.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(RadiusLG),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(RadiusMD))
                            ) {
                                Icon(
                                    Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Data Privacy", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Text(
                            text = "Your warranty data never leaves your device. No analytics, no telemetry, no third-party access. Export your data anytime via JSON/CSV.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Data Management Section
                WarrantySectionHeader(title = "Data Management")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(RadiusLG),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            WarrantyPrimaryButton(
                                text = "Export All Data",
                                onClick = {
                                    scope.launch {
                                        exportImportService.exportJson()
                                        exportImportService.exportCsv()
                                        Toast.makeText(context, "Data exported (JSON & CSV) to Downloads", Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            WarrantyGhostButton(
                                text = "Import Data",
                                onClick = { importLauncher.launch("*/*") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // About Section
                WarrantySectionHeader(title = "About")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(RadiusLG),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(RadiusMD))
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("About WarrantyVault", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Text(
                            text = "Version 1.0.0 (Android Native)\nConverted from WarrantyVault web platform for 100% offline local storage and management.\n\nBuilt with: Kotlin, Jetpack Compose, Room, ML Kit, WorkManager",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}