package com.warrantyvault.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.URLEncoder

/**
 * Pure request/response plumbing for the handful of Google Drive v3 endpoints the backup uses.
 *
 * Kept free of Android and network types so the URL building, multipart body and JSON parsing can
 * be unit-tested on the JVM; [DriveBackupService] supplies the actual HTTP calls and OAuth token.
 *
 * Scope: `drive.appdata` — the app-data folder is private to this app. Files there are created by
 * WarrantyVault, are not visible in the user's Drive file list, and cannot be read by other apps.
 */
object DriveRest {

    const val SCOPE_URL = "https://www.googleapis.com/auth/drive.appdata"

    /** The scope string GoogleAuthUtil wants (it expects the `oauth2:` prefix). */
    const val OAUTH_SCOPE = "oauth2:$SCOPE_URL"

    const val APPDATA_FOLDER = "appDataFolder"

    private const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
    private const val ABOUT_URL = "https://www.googleapis.com/drive/v3/about?fields=user(emailAddress,displayName)"

    private const val BOUNDARY = "warrantyvault_backup_boundary_7f3a"

    const val MULTIPART_CONTENT_TYPE = "multipart/related; boundary=\"$BOUNDARY\""

    private val json = Json { ignoreUnknownKeys = true }

    data class BackupFileInfo(
        val id: String,
        val name: String,
        val modifiedTime: String?,
        val sizeBytes: Long?
    )

    data class DriveUser(val email: String?, val displayName: String?)

    /** Lists WarrantyVault backups inside the app data folder (newest metadata first, as returned). */
    fun listUrl(fileName: String = BackupEnvelope.FILE_NAME): String {
        val query = "name = '$fileName' and trashed = false"
        return "$FILES_URL?spaces=$APPDATA_FOLDER&q=${encode(query)}&fields=${encode("files(id,name,modifiedTime,size)")}"
    }

    fun downloadUrl(fileId: String): String = "$FILES_URL/${encode(fileId)}?alt=media"

    fun updateUrl(fileId: String): String =
        "$UPLOAD_URL/${encode(fileId)}?uploadType=media&fields=${encode("id,name,modifiedTime,size")}"

    fun createUrl(): String = "$UPLOAD_URL?uploadType=multipart&fields=${encode("id,name,modifiedTime,size")}"

    fun aboutUrl(): String = ABOUT_URL

    /**
     * OAuth token-revocation endpoint. Unlinking goes through here because the Play Services
     * authorization client exposes no revoke call, and revoking the grant also kills the token.
     */
    fun revokeUrl(token: String): String = "https://oauth2.googleapis.com/revoke?token=${encode(token)}"

    /**
     * `multipart/related` body for creating a file with both metadata and content in one request.
     * (Simple `uploadType=media` cannot set the name or the app-data parent, so first uploads use
     * multipart; later updates reuse the existing file id with a plain media upload.)
     */
    fun multipartCreateBody(fileName: String, content: ByteArray): ByteArray {
        val metadata = buildJsonObject {
            put("name", fileName)
            put("mimeType", "application/json")
            putJsonArray("parents") { add(APPDATA_FOLDER) }
        }.toString()

        val head = buildString {
            append("--$BOUNDARY\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata).append("\r\n")
            append("--$BOUNDARY\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        }.toByteArray(Charsets.UTF_8)

        val tail = "\r\n--$BOUNDARY--\r\n".toByteArray(Charsets.UTF_8)
        return head + content + tail
    }

    fun parseFileList(body: String): List<BackupFileInfo> {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return emptyList()
        val files = root["files"] as? JsonArray ?: return emptyList()
        return files.mapNotNull { it as? JsonObject }.map { toFileInfo(it) }
    }

    fun parseFile(body: String): BackupFileInfo =
        toFileInfo(json.parseToJsonElement(body) as? JsonObject ?: JsonObject(emptyMap()))

    fun parseUser(body: String): DriveUser {
        val user = runCatching {
            (json.parseToJsonElement(body) as? JsonObject)?.get("user") as? JsonObject
        }.getOrNull() ?: return DriveUser(null, null)
        return DriveUser(
            email = user["emailAddress"].textOrNull(),
            displayName = user["displayName"].textOrNull()
        )
    }

    private fun toFileInfo(obj: JsonObject): BackupFileInfo = BackupFileInfo(
        id = obj["id"].textOrNull().orEmpty(),
        name = obj["name"].textOrNull().orEmpty(),
        modifiedTime = obj["modifiedTime"].textOrNull(),
        sizeBytes = obj["size"].textOrNull()?.toLongOrNull()
    )

    private fun JsonElement?.textOrNull(): String? = (this as? JsonPrimitive)?.content

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
