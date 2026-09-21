package com.warrantyvault.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.warrantyvault.WarrantyVaultApplication
import com.warrantyvault.service.ExportImportService
import com.warrantyvault.ui.theme.WvDimens
import com.warrantyvault.ui.theme.darkWvColors
import com.warrantyvault.ui.theme.lightWvColors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as WarrantyVaultApplication
    val exportImportService = remember { ExportImportService(context, app.database) }
    val scope = rememberCoroutineScope()
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()

    var importPreview by remember { mutableStateOf<com.warrantyvault.service.ExportImportService.ImportPreview?>(null) }
    var importing by remember { mutableStateOf(false) }
    var importSummary by remember { mutableStateOf<com.warrantyvault.service.ExportImportService.ImportSummary?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                importing = true
                importSummary = null
                importPreview = exportImportService.buildPreview(uri)
                importing = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(wv.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = WvDimens.ScreenGutter)
    ) {
        Spacer(Modifier.height(WvDimens.Space4))
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = wv.textPrimary)
        Spacer(Modifier.height(WvDimens.Space5))

        // ---- Profile ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = wv.primarySoft, modifier = Modifier.size(56.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("A", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = wv.primary)
                }
            }
            Spacer(Modifier.width(WvDimens.Space3))
            Column {
                Text("Aakash (Local)", style = MaterialTheme.typography.titleMedium, color = wv.textPrimary)
                Text("Local Device", style = MaterialTheme.typography.bodySmall, color = wv.textSecondary)
            }
        }
        Spacer(Modifier.height(WvDimens.Space6))

        // ---- Appearance ----
        SettingsGroup("Appearance") {
            SettingsRow(
                icon = Icons.Default.Palette,
                title = "Theme",
                subtitle = "Follows system (dark & light designed)",
                tint = wv.primary
            )
        }
        Spacer(Modifier.height(WvDimens.Space4))

        // ---- Data & Privacy ----
        SettingsGroup("Data & Privacy") {
            SettingsRow(
                icon = Icons.Default.Shield,
                title = "Private by design",
                subtitle = "All data stays on this device. No cloud, no tracking.",
                tint = wv.success
            )
            HorizontalDivider(color = wv.borderSubtle)
            SettingsRow(
                icon = Icons.Default.Storage,
                title = "Local-first architecture",
                subtitle = "Room database in app-private storage",
                tint = wv.primary
            )
        }
        Spacer(Modifier.height(WvDimens.Space4))

        // ---- Notifications ----
        SettingsGroup("Notifications") {
            SettingsRow(
                icon = Icons.Default.Notifications,
                title = "Warranty reminders",
                subtitle = "Alerts at 30, 7 and 1 day before expiry",
                tint = wv.warning
            )
        }
        Spacer(Modifier.height(WvDimens.Space4))

        // ---- Data Management ----
        SettingsGroup("Data Management") {
            SettingsRow(
                icon = Icons.Default.UploadFile,
                title = "Export data",
                subtitle = "Save a JSON + CSV backup to Downloads",
                tint = wv.primary,
                onClick = {
                    scope.launch {
                        exportImportService.exportJson()
                        exportImportService.exportCsv()
                        Toast.makeText(context, "Data exported (JSON & CSV) to Downloads", Toast.LENGTH_LONG).show()
                    }
                }
            )
            HorizontalDivider(color = wv.borderSubtle)
            SettingsRow(
                icon = Icons.Default.UploadFile,
                title = if (importing) "Reading file…" else "Import data (JSON)",
                subtitle = "Validated preview before anything is written",
                tint = wv.primary,
                onClick = { if (!importing) importLauncher.launch("application/json") }
            )
        }
        Spacer(Modifier.height(WvDimens.Space4))

        // ---- App ----
        SettingsGroup("App") {
            SettingsRow(
                icon = Icons.Default.Info,
                title = "About WarrantyVault",
                subtitle = "Version 1.0.0 · Kotlin · Compose · Room · ML Kit",
                tint = wv.textSecondary
            )
        }

        Spacer(Modifier.height(WvDimens.Space8))

        // Import preview dialog + summary
        importPreview?.let { preview ->
            AlertDialog(
                onDismissRequest = { importPreview = null },
                title = { Text("Confirm import") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Ready to import: ${preview.valid.size} record(s)")
                        if (preview.duplicatesInFile > 0) {
                            Text("Duplicates within file: ${preview.duplicatesInFile} (will be skipped)", color = wv.warning)
                        }
                        if (preview.rejected.isNotEmpty()) {
                            Text("Rejected: ${preview.rejected.size}", color = wv.error, fontWeight = FontWeight.Bold)
                            preview.rejected.take(5).forEach {
                                Text("Row ${it.rowIndex + 1}: ${it.reason}", style = MaterialTheme.typography.labelSmall, color = wv.error)
                            }
                            if (preview.rejected.size > 5) Text("…and ${preview.rejected.size - 5} more", style = MaterialTheme.typography.labelSmall)
                        }
                        if (preview.valid.isEmpty()) {
                            Text("Nothing can be imported from this file.", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val p = preview
                            importPreview = null
                            scope.launch {
                                val summary = exportImportService.commitImport(p)
                                importSummary = summary
                                Toast.makeText(
                                    context,
                                    "Imported: ${summary.imported} · Rejected: ${summary.rejected} · Duplicates: ${summary.duplicatesSkipped}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        enabled = preview.valid.isNotEmpty()
                    ) { Text("Import ${preview.valid.size}") }
                },
                dismissButton = { TextButton(onClick = { importPreview = null }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = wv.textMuted,
            modifier = Modifier.padding(bottom = WvDimens.Space2, start = WvDimens.Space1)
        )
        Surface(
            shape = RoundedCornerShape(WvDimens.RadiusMedium),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, wv.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: (() -> Unit)? = null
) {
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = WvDimens.Space4, vertical = WvDimens.Space3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Consistent neutral icon container — semantic tint only.
        Surface(
            shape = RoundedCornerShape(WvDimens.RadiusSmall),
            color = tint.copy(alpha = 0.12f),
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(WvDimens.Space3))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = wv.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = wv.textSecondary)
        }
        if (onClick != null) {
            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null, tint = wv.textMuted, modifier = Modifier.size(18.dp))
        }
    }
}
