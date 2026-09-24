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
import androidx.work.workDataOf
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
        val userInitiated = inputData.getBoolean(KEY_USER_INITIATED, false)

        if (!AutoSyncPolicy.shouldRun(userInitiated, state.linked, state.autoSyncEnabled, state.paused)) {
            // Nothing to do: no account, automatic backup off, or waiting for the user to re-link.
            return@withContext Result.success()
        }

        service.markSyncStarted()
        return@withContext try {
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
        } finally {
            service.markSyncFinished()
        }
    }

    companion object {
        private const val TAG = "DriveSyncWorker"
        private const val WORK_NAME = "warrantyvault_drive_autosync"

        /** Long enough to coalesce a burst of edits (scan confirm writes rows, then history). */
        private const val DEBOUNCE_SECONDS = 10L
        private const val MAX_ATTEMPTS = 3
        private const val KEY_USER_INITIATED = "user_initiated"

        /** Schedules a debounced sync. Does nothing when auto-sync is not currently allowed. */
        fun enqueue(context: Context) {
            if (!DriveBackupService(context).autoSyncArmed()) return
            enqueueRequest(context, initialDelaySeconds = DEBOUNCE_SECONDS, userInitiated = false)
        }

        /**
         * Schedules an upload straight away for an explicit tap on the dashboard banner.
         *
         * It replaces any debounced sync already waiting (the tap supersedes it) and deliberately
         * ignores the automatic-backup toggle — the user just asked for this one. It goes through
         * the same worker, so it is still guarded against replacing a richer backup.
         */
        fun enqueueNow(context: Context) {
            enqueueRequest(context, initialDelaySeconds = 0L, userInitiated = true)
        }

        private fun enqueueRequest(context: Context, initialDelaySeconds: Long, userInitiated: Boolean) {
            val request = OneTimeWorkRequestBuilder<DriveSyncWorker>()
                .setInitialDelay(initialDelaySeconds, TimeUnit.SECONDS)
                .setInputData(workDataOf(KEY_USER_INITIATED to userInitiated))
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
