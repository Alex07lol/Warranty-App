package com.warrantyvault.backup

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.warrantyvault.WarrantyVaultApplication
import com.warrantyvault.data.AppDatabase
import com.warrantyvault.service.ExportImportService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Uploads the vault to Google Drive in the background after the vault changed.
 *
 * Runs as unique work with [ExistingWorkPolicy.REPLACE], so a burst of edits collapses into a
 * single upload (each new change restarts the short delay) and a scheduled sync survives the
 * process being killed. It requires network connectivity, so WorkManager holds the work until the
 * device is online rather than failing immediately.
 *
 * Safety: this worker never shows UI. If the Google grant is gone it pauses auto-sync and leaves
 * Settings to offer a re-link, and it uploads with `force = false` so a device holding less data
 * than the Drive backup can never overwrite it (see [AutoSyncPolicy]).
 */
class DriveSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val service = DriveBackupService(applicationContext)
        val state = service.syncState()

        if (!AutoSyncPolicy.shouldSync(state.linked, state.autoSyncEnabled, state.paused)) {
            // Nothing to do: not linked, auto-backup off, or waiting for the user to re-link.
            return@withContext Result.success()
        }

        try {
            val db = AppDatabase.getDatabase(applicationContext)
            val userId = (applicationContext as WarrantyVaultApplication).currentUserId
            val productCount = db.productDao().getProductCount(userId)
            val payload = ExportImportService(applicationContext, db).buildExportJson()

            val outcome = service.uploadBackup(
                exportJson = payload,
                appVersion = service.appVersion(),
                productCount = productCount,
                force = false
            ).getOrThrow()

            when (outcome) {
                is DriveBackupService.UploadOutcome.Uploaded ->
                    Log.i(TAG, "Auto-sync uploaded ${outcome.productCount} product(s)")
                is DriveBackupService.UploadOutcome.Refused ->
                    Log.w(TAG, "Auto-sync refused: the Drive backup holds more products than this device")
            }
            Result.success()
        } catch (e: DriveBackupService.NeedsRelink) {
            service.pauseAutoSync(e.message ?: "Google Drive needs permission again — re-link the account.")
            Result.success()
        } catch (e: Exception) {
            service.recordSyncNote(e.message ?: "Sync failed")
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "DriveSyncWorker"
        private const val WORK_NAME = "warrantyvault_drive_autosync"

        /** Long enough to coalesce a burst of edits (scan confirm writes rows, then history). */
        private const val DEBOUNCE_SECONDS = 10L
        private const val MAX_ATTEMPTS = 3

        /** Schedules a debounced sync. Does nothing when auto-sync is not currently allowed. */
        fun enqueue(context: Context) {
            if (!DriveBackupService(context).autoSyncArmed()) return

            val request = OneTimeWorkRequestBuilder<DriveSyncWorker>()
                .setInitialDelay(DEBOUNCE_SECONDS, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
