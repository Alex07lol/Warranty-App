package com.warrantyvault.backup

import android.content.Context
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Google Drive backup/restore for the vault.
 *
 * Design notes:
 *  - **Permission first.** Nothing leaves the device until the user answers Google's consent
 *    screen for the Drive `appdata` scope. Consent is remembered by Google, so later backups are
 *    silent; if the grant is gone the API reports [NeedsRelink] and the UI asks the user to
 *    re-link instead of failing obscurely.
 *  - **Private by default.** The file lives in the Drive *app data folder*: owned by the user's
 *    Drive, readable and writable only by this app, invisible in the normal Drive file list.
 *  - **Same document as the JSON export.** The upload is a [BackupEnvelope] around
 *    `ExportImportService.buildExportJson()`, so restore reuses the validated import pipeline
 *    (duplicate detection, IMEI checks, rejected-row reporting) and never destroys existing data.
 *  - **No heavyweight Google API client.** Auth uses the current Google Identity Services
 *    `AuthorizationClient`; Drive v3 is called over `HttpURLConnection` with the pure request and
 *    response plumbing living in [DriveRest].
 *
 * One-time developer setup (OAuth client for this package name + signing SHA-1) is documented in
 * `docs/DRIVE_BACKUP_SETUP.md`. Without it Google rejects the consent request.
 */
class DriveBackupService(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val authClient by lazy { Identity.getAuthorizationClient(appContext) }

    /** Thrown when Google no longer accepts the stored grant and the user must re-link. */
    class NeedsRelink(message: String, cause: Throwable? = null) : IOException(message, cause)

    data class LinkedAccount(val email: String)

    data class BackupUpload(
        val modifiedTime: String?,
        val sizeBytes: Long?,
        val productCount: Int
    )

    /** Outcome of asking Google for Drive permission. */
    sealed class Authorization {
        /** Permission already granted — carry on, no UI needed. */
        object Granted : Authorization()

        /** Google needs the user's answer; the host launches this with StartIntentSenderForResult. */
        data class UserActionRequired(val intentSender: IntentSender) : Authorization()
    }

    // ---------------------------------------------------------------- account linking

    fun linkedEmail(): String? = prefs.getString(KEY_EMAIL, null)

    fun lastBackupMillis(): Long = prefs.getLong(KEY_LAST_BACKUP, 0L)

    fun lastBackupCount(): Int = prefs.getInt(KEY_LAST_COUNT, 0)

    /**
     * Asks for the Drive app-data scope. Returns [Authorization.Granted] when the grant already
     * stands (no UI), otherwise the [IntentSender] that shows Google's consent screen.
     */
    suspend fun startAuthorization(): Result<Authorization> = withContext(Dispatchers.IO) {
        runCatching {
            when (val grant = requestGrant()) {
                is Grant.Ok -> Authorization.Granted
                is Grant.NeedsUser -> Authorization.UserActionRequired(grant.intentSender)
            }
        }
    }

    /**
     * Call after Google's consent UI returns. Completes the link, remembers which account was
     * chosen, and is what makes the very first backup possible.
     */
    suspend fun finishAuthorization(): Result<LinkedAccount> = withContext(Dispatchers.IO) {
        runCatching {
            when (val grant = requestGrant()) {
                is Grant.Ok -> {
                    val email = DriveRest.parseUser(request(DriveRest.aboutUrl(), "GET", grant.token)).email
                        ?: throw IOException("Linked with Google, but no account email came back")
                    prefs.edit().putString(KEY_EMAIL, email).apply()
                    LinkedAccount(email)
                }
                is Grant.NeedsUser -> throw IOException("Google Drive permission was not granted")
            }
        }
    }

    /**
     * Unlinks this device: the app's OAuth grant is revoked (which also invalidates the cached
     * access token) and local prefs are cleared. The Drive backup file itself is left alone, so
     * re-linking later restores from it.
     */
    suspend fun unlink(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            runCatching {
                val token = requireToken()
                request(DriveRest.revokeUrl(token), "POST", token, ByteArray(0))
            }
            prefs.edit().clear().apply()
        }
    }

    // ---------------------------------------------------------------- backup

    /**
     * Uploads the current vault as a [BackupEnvelope] into the Drive app data folder, creating the
     * file on first run and replacing its contents on every later run.
     */
    suspend fun uploadBackup(
        exportJson: String,
        appVersion: String,
        productCount: Int
    ): Result<BackupUpload> = withContext(Dispatchers.IO) {
        runCatching {
            val token = requireToken()
            val envelope = BackupEnvelope.wrap(exportJson, appVersion, nowIso(), productCount)
            val body = envelope.toByteArray(Charsets.UTF_8)

            val existing = listFiles(token).firstOrNull()
            val response = if (existing != null) {
                request(DriveRest.updateUrl(existing.id), "PATCH", token, body)
            } else {
                request(
                    DriveRest.createUrl(),
                    "POST",
                    token,
                    DriveRest.multipartCreateBody(BackupEnvelope.FILE_NAME, body),
                    DriveRest.MULTIPART_CONTENT_TYPE
                )
            }
            val info = DriveRest.parseFile(response)

            prefs.edit()
                .putLong(KEY_LAST_BACKUP, System.currentTimeMillis())
                .putInt(KEY_LAST_COUNT, productCount)
                .apply()

            BackupUpload(info.modifiedTime, info.sizeBytes, productCount)
        }
    }

    /** Downloads and validates the backup on Drive, ready for the import pipeline. */
    suspend fun fetchBackup(): Result<BackupEnvelope.Unwrapped> = withContext(Dispatchers.IO) {
        runCatching {
            val token = requireToken()
            val file = listFiles(token).firstOrNull()
                ?: throw IOException("No WarrantyVault backup found in this Google Drive yet.")
            val raw = request(DriveRest.downloadUrl(file.id), "GET", token)
            BackupEnvelope.unwrap(raw).getOrThrow()
        }
    }

    fun appVersion(): String = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
    }.getOrNull() ?: "unknown"

    // ---------------------------------------------------------------- internals

    private sealed class Grant {
        class Ok(val token: String) : Grant()
        class NeedsUser(val intentSender: IntentSender) : Grant()
    }

    /** Blocking; always called from an IO dispatcher. */
    private fun requestGrant(): Grant {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DriveRest.SCOPE_URL)))
            .build()
        return try {
            val result = Tasks.await(authClient.authorize(request))
            if (result.hasResolution()) {
                // Google wants the user's answer: first grant, a different account, or a new scope.
                val sender = result.pendingIntent?.intentSender
                    ?: throw NeedsRelink("Google Drive permission is needed again — re-link the account.")
                Grant.NeedsUser(sender)
            } else {
                Grant.Ok(result.accessToken ?: throw IOException("Google returned no access token"))
            }
        } catch (e: ApiException) {
            // Cancelled prompts, revoked grants and misconfiguration all land here; the caller
            // turns this into a "re-link" prompt with Google's own message attached.
            throw NeedsRelink(e.message ?: "Google Drive authorization failed (${e.statusCode})", e)
        }
    }

    private fun requireToken(): String {
        if (linkedEmail() == null) throw NeedsRelink("No Google account is linked yet.")
        return when (val grant = requestGrant()) {
            is Grant.Ok -> grant.token
            is Grant.NeedsUser ->
                throw NeedsRelink("Google Drive permission expired — re-link the account to continue.")
        }
    }

    private fun listFiles(token: String): List<DriveRest.BackupFileInfo> =
        DriveRest.parseFileList(request(DriveRest.listUrl(), "GET", token))

    private fun request(
        url: String,
        method: String,
        token: String,
        body: ByteArray? = null,
        contentType: String = "application/json; charset=UTF-8"
    ): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType)
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                if (code == 401) {
                    throw NeedsRelink("Google Drive permission expired — re-link the account to continue.")
                }
                // 403 is usually "Drive API not enabled for this project" or a scope problem, so the
                // Google message is passed through rather than flattened into a generic error.
                throw IOException("Google Drive request failed ($code): ${text.take(300)}")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun nowIso(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

    companion object {
        private const val PREFS = "warrantyvault_drive_backup"
        private const val KEY_EMAIL = "drive_account_email"
        private const val KEY_LAST_BACKUP = "drive_last_backup_ms"
        private const val KEY_LAST_COUNT = "drive_last_backup_count"

        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 20_000
    }
}
