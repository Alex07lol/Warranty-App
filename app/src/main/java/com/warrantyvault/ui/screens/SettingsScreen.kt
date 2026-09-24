package com.warrantyvault.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
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
import android.app.Activity
import android.widget.Toast
import com.warrantyvault.WarrantyVaultApplication
import com.warrantyvault.backup.DriveBackupService
import com.warrantyvault.backup.DriveRest
import com.warrantyvault.backup.DriveSyncWorker
import com.warrantyvault.export.PdfExportService
import com.warrantyvault.service.ExportImportService
import com.warrantyvault.ui.theme.ThemeMode
import com.warrantyvault.ui.theme.ThemePreference
import com.warrantyvault.ui.theme.WvDimens
import com.warrantyvault.ui.theme.WvTheme
import com.warrantyvault.ui.theme.summary
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as WarrantyVaultApplication
    val exportImportService = remember { ExportImportService(context, app.database) }
    val scope = rememberCoroutineScope()
    val wv = WvTheme.colors
    // The stored Light/Dark choice, so the selector below reflects and updates the whole app.
    val themeMode by ThemePreference.mode.collectAsState()

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

    // ---- Google Drive backup ----
    // Permission-gated: nothing leaves the device until the user completes Google's consent
    // screen, and the uploaded file lives in the app-private Drive folder.
    val driveService = remember { DriveBackupService(context) }
    var driveBusy by remember { mutableStateOf<String?>(null) }
    var needRelink by remember { mutableStateOf(false) }

    // Observed rather than polled: the service publishes on every change, including ones made by
    // the background sync worker.
    val driveState by DriveBackupService.observe(context).collectAsState()

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    fun describeDriveError(e: Throwable): String {
        if (e is DriveBackupService.NeedsRelink) needRelink = true
        return e.message ?: "Google Drive error"
    }

    fun backupNow(label: String = "Backing up…", force: Boolean = true) {
        if (driveBusy != null) return
        scope.launch {
            driveBusy = label
            try {
                val count = app.database.productDao().getProductCount(app.currentUserId)
                val payload = exportImportService.buildExportJson()
                driveService.uploadBackup(payload, driveService.appVersion(), count, force = force)
                    .onSuccess { outcome ->
                        needRelink = false
                        when (outcome) {
                            is DriveBackupService.UploadOutcome.Uploaded -> {
                                toast("Backed up ${outcome.productCount} product(s) to Google Drive")
                                // The private backup is the one that matters; the mirror is best-effort.
                                val copy = outcome.visibleCopy
                                if (copy is DriveBackupService.VisibleCopy.Failed) toast(copy.reason)
                            }
                            // Refused by the overwrite guard: Drive holds more than this device.
                            is DriveBackupService.UploadOutcome.Refused -> toast(outcome.reason)
                        }
                    }
                    .onFailure { toast("Backup failed: ${describeDriveError(it)}") }
            } catch (t: Throwable) {
                toast("Backup failed: ${describeDriveError(t)}")
            } finally {
                driveBusy = null
            }
        }
    }

    fun restoreFromDrive() {
        if (driveBusy != null) return
        scope.launch {
            driveBusy = "Contacting Drive…"
            try {
                driveService.fetchBackup()
                    .onSuccess { backup ->
                        // Unwrapped payload goes through the same validated preview as a file import,
                        // so the user confirms before anything is written.
                        importPreview = exportImportService.buildPreviewFromText(backup.dataJson, "application/json")
                    }
                    .onFailure { toast("Restore failed: ${describeDriveError(it)}") }
            } catch (t: Throwable) {
                toast("Restore failed: ${describeDriveError(t)}")
            } finally {
                driveBusy = null
            }
        }
    }

    // Runs after Google's consent UI returns (and directly when consent already stands).
    val completeDriveLink: () -> Unit = {
        scope.launch {
            driveBusy = "Linking…"
            val outcome = driveService.finishAuthorization()
            driveBusy = null
            outcome
                .onSuccess { account ->
                    needRelink = false
                    toast("Google Drive linked as ${account.email}")
                    // Permission granted: take the first backup straight away.
                    // Guarded upload: if Drive already holds a richer backup, the guard refuses and
                    // tells the user to restore instead of overwriting it with this device's data.
                    backupNow("Uploading first backup…", force = false)
                }
                .onFailure { toast("Couldn't link Google Drive: ${it.message ?: "permission not granted"}") }
        }
    }

    val driveConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) completeDriveLink()
        else toast("Google Drive permission was declined")
    }

    fun beginDriveLink() {
        if (driveBusy != null) return
        scope.launch {
            driveBusy = "Checking Google…"
            val started = driveService.startAuthorization()
            driveBusy = null
            started
                .onSuccess { auth ->
                    when (auth) {
                        // Consent still stands: link silently and take a backup.
                        is DriveBackupService.Authorization.Granted -> completeDriveLink()
                        is DriveBackupService.Authorization.UserActionRequired ->
                            driveConsentLauncher.launch(IntentSenderRequest.Builder(auth.intentSender).build())
                    }
                }
                .onFailure { toast("Google Drive unavailable: ${describeDriveError(it)}") }
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
                subtitle = themeMode.summary(isSystemInDarkTheme()),
                tint = wv.primary
            )
            ThemeModeSelector(
                selected = themeMode,
                onSelect = { ThemePreference.set(context, it) }
            )
        }
        Spacer(Modifier.height(WvDimens.Space4))

        // ---- Data & Privacy ----
        SettingsGroup("Data & Privacy") {
            SettingsRow(
                icon = Icons.Default.Shield,
                title = "Private by design",
                subtitle = "Stays on this device unless you turn on Google Drive backup.",
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
        var exporting by remember { mutableStateOf(false) }
        SettingsGroup("Export") {
            SettingsRow(
                icon = Icons.Default.PictureAsPdf,
                title = if (exporting) "Building PDF…" else "Export PDF (all products)",
                subtitle = "One passport page per product, saved to Downloads",
                tint = wv.error,
                onClick = {
                    if (!exporting) scope.launch {
                        exporting = true
                        runCatching { PdfExportService(context, app.database).exportPdf() }
                            .onSuccess {
                                Toast.makeText(context, "PDF saved to Downloads", Toast.LENGTH_LONG).show()
                            }
                            .onFailure {
                                Toast.makeText(context, "Couldn't create PDF: ${it.message}", Toast.LENGTH_LONG).show()
                            }
                        exporting = false
                    }
                }
            )
            HorizontalDivider(color = wv.borderSubtle)
            SettingsRow(
                icon = Icons.Default.TableChart,
                title = "Export CSV (entire list)",
                subtitle = "One row per product for spreadsheets",
                tint = wv.success,
                onClick = {
                    if (!exporting) scope.launch {
                        exporting = true
                        runCatching { exportImportService.exportCsv() }
                            .onSuccess { Toast.makeText(context, "CSV saved to Downloads", Toast.LENGTH_LONG).show() }
                            .onFailure { Toast.makeText(context, "Couldn't create CSV: ${it.message}", Toast.LENGTH_LONG).show() }
                        exporting = false
                    }
                }
            )
            HorizontalDivider(color = wv.borderSubtle)
            SettingsRow(
                icon = Icons.Default.UploadFile,
                title = "Export JSON (full backup)",
                subtitle = "Everything, including service history",
                tint = wv.primary,
                onClick = {
                    if (!exporting) scope.launch {
                        exporting = true
                        runCatching { exportImportService.exportJson() }
                            .onSuccess { Toast.makeText(context, "JSON saved to Downloads", Toast.LENGTH_LONG).show() }
                            .onFailure { Toast.makeText(context, "Couldn't create JSON: ${it.message}", Toast.LENGTH_LONG).show() }
                        exporting = false
                    }
                }
            )
        }
        Spacer(Modifier.height(WvDimens.Space4))

        SettingsGroup("Data Management") {
            SettingsRow(
                icon = Icons.Default.UploadFile,
                title = if (importing) "Reading file…" else "Import data (JSON)",
                subtitle = "Validated preview before anything is written",
                tint = wv.primary,
                onClick = { if (!importing) importLauncher.launch("application/json") }
            )
        }
        Spacer(Modifier.height(WvDimens.Space4))

        SettingsGroup("Google Drive backup") {
            if (driveState.email == null) {
                SettingsRow(
                    icon = Icons.Default.CloudUpload,
                    title = driveBusy ?: "Back up to Google Drive",
                    subtitle = "Asks for Drive permission, then keeps a private backup file",
                    tint = wv.primary,
                    onClick = { beginDriveLink() }
                )
            } else {
                if (needRelink || driveState.paused) {
                    SettingsRow(
                        icon = Icons.Default.LinkOff,
                        title = "Re-link Google Drive",
                        subtitle = "Permission expired — tap to sign in again",
                        tint = wv.warning,
                        onClick = { beginDriveLink() }
                    )
                    HorizontalDivider(color = wv.borderSubtle)
                }
                SettingsRow(
                    icon = Icons.Default.CloudUpload,
                    title = driveBusy ?: "Back up now",
                    subtitle = buildString {
                        append(driveState.email)
                        append(" · ")
                        append(
                            if (driveState.lastSyncMillis > 0) {
                                "last backup ${formatBackupTime(driveState.lastSyncMillis)} (${driveState.lastSyncCount} product(s))"
                            } else {
                                "no backup yet"
                            }
                        )
                    },
                    tint = wv.primary,
                    onClick = { backupNow() }
                )
                HorizontalDivider(color = wv.borderSubtle)
                SettingsRow(
                    icon = Icons.Default.CloudSync,
                    title = "Auto-backup after changes",
                    subtitle = when {
                        driveState.paused -> "Paused until you re-link Google Drive"
                        !driveState.autoSyncEnabled -> "Off — back up manually"
                        else -> "On — uploads about 10s after a change"
                    },
                    tint = wv.success,
                    trailing = {
                        Switch(
                            checked = driveState.autoSyncEnabled && !driveState.paused,
                            enabled = !driveState.paused && driveBusy == null,
                            onCheckedChange = { checked ->
                                driveService.setAutoSyncEnabled(checked)
                                // Catch the vault up right away instead of waiting for the next edit.
                                if (checked) DriveSyncWorker.enqueue(context)
                            }
                        )
                    }
                )
                HorizontalDivider(color = wv.borderSubtle)
                SettingsRow(
                    icon = Icons.Default.Folder,
                    title = "Visible copy in My Drive",
                    subtitle = if (driveState.visibleCopyEnabled) {
                        "My Drive / ${DriveRest.VISIBLE_FOLDER_NAME} — openable from the Drive app"
                    } else {
                        "Off — the backup stays private to this app"
                    },
                    tint = wv.primary,
                    trailing = {
                        Switch(
                            checked = driveState.visibleCopyEnabled,
                            enabled = driveBusy == null,
                            onCheckedChange = { checked ->
                                driveService.setVisibleCopyEnabled(checked)
                                // Write (or refresh) the mirror straight away. Guarded, so it can
                                // never replace a richer backup with this device's data.
                                if (checked) backupNow("Writing visible copy…", force = false)
                            }
                        )
                    }
                )
                driveState.message?.let { message ->
                    HorizontalDivider(color = wv.borderSubtle)
                    SettingsRow(
                        icon = Icons.Default.CloudSync,
                        title = "Last sync result",
                        subtitle = message,
                        tint = wv.warning
                    )
                }
                HorizontalDivider(color = wv.borderSubtle)
                SettingsRow(
                    icon = Icons.Default.CloudDownload,
                    title = "Restore from Google Drive",
                    subtitle = "Previewed and validated before anything is written",
                    tint = wv.success,
                    onClick = { restoreFromDrive() }
                )
                HorizontalDivider(color = wv.borderSubtle)
                SettingsRow(
                    icon = Icons.Default.CloudOff,
                    title = "Unlink Google account",
                    subtitle = "Forgets the permission here; the Drive backup stays",
                    tint = wv.textSecondary,
                    onClick = {
                        if (driveBusy == null) scope.launch {
                            driveBusy = "Unlinking…"
                            driveService.unlink()
                            needRelink = false
                            driveBusy = null
                            toast("Google Drive unlinked")
                        }
                    }
                )
            }
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
    val wv = WvTheme.colors
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
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val wv = WvTheme.colors
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
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null, tint = wv.textMuted, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * Three-way Light / System / Dark selector. Mirrors the web app's theme toggle, but makes the
 * "follow the device" option explicit instead of hiding it behind a two-state switch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    val options = ThemeMode.values()
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = WvDimens.Space4, end = WvDimens.Space4, bottom = WvDimens.Space3)
    ) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(option.label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private fun formatBackupTime(millis: Long): String =
    java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(millis))
