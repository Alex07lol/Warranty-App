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
import kotlinx.serialization.json.putJsonObject
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

    /** Private app-data folder: only this app can see or read what is stored in it. */
    const val APPDATA_SCOPE_URL = "https://www.googleapis.com/auth/drive.appdata"

    /**
     * Narrow scope for the user-visible mirror. It grants access only to files this app creates (or
     * files the user explicitly shares with it) — never the rest of the user's Drive. Using this
     * instead of the full `drive` scope is what keeps the mirror honest and reviewable.
     */
    const val DRIVE_FILE_SCOPE_URL = "https://www.googleapis.com/auth/drive.file"

    const val APPDATA_FOLDER = "appDataFolder"
    const val ROOT_FOLDER = "root"
    const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"

    /** Folder name shown in the user's Drive app. */
    const val VISIBLE_FOLDER_NAME = "WarrantyVault"

    private const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
    private const val ABOUT_URL = "https://www.googleapis.com/drive/v3/about?fields=user(emailAddress,displayName)"

    /** File metadata requested from Drive, including app-private properties. */
    private const val FILE_FIELDS = "id,name,modifiedTime,size,appProperties"

    /**
     * App-private property holding the number of products in the backup. It lets the sync guard
     * compare local and remote vault sizes from a cheap file listing instead of downloading the
     * backup on every check.
     */
    const val PROP_PRODUCT_COUNT = "warrantyVaultProductCount"
    const val PROP_FORMAT = "warrantyVaultFormat"

    private const val BOUNDARY = "warrantyvault_backup_boundary_7f3a"

    const val MULTIPART_CONTENT_TYPE = "multipart/related; boundary=\"$BOUNDARY\""

    private val json = Json { ignoreUnknownKeys = true }

    data class BackupFileInfo(
        val id: String,
        val name: String,
        val modifiedTime: String?,
        val sizeBytes: Long?,
        val appProperties: Map<String, String> = emptyMap()
    ) {
        /** Products recorded in the backup at upload time, when the metadata is present. */
        val productCount: Int? get() = appProperties[PROP_PRODUCT_COUNT]?.toIntOrNull()
    }

    data class DriveUser(val email: String?, val displayName: String?)

    /** Lists WarrantyVault backups inside the app data folder (newest metadata first, as returned). */
    fun listUrl(fileName: String = BackupEnvelope.FILE_NAME): String {
        val query = "name = '$fileName' and trashed = false"
        return "$FILES_URL?spaces=$APPDATA_FOLDER&q=${encode(query)}&fields=${encode("files($FILE_FIELDS)")}"
    }

    /**
     * Finds the user-visible folder. The `drive.file` scope means this query can only ever return
     * folders this app created, so an unrelated folder with the same name is never picked up.
     */
    fun visibleFolderQueryUrl(folderName: String = VISIBLE_FOLDER_NAME): String {
        val query = "name = '$folderName' and mimeType = '$FOLDER_MIME_TYPE' " +
            "and trashed = false and '$ROOT_FOLDER' in parents"
        return "$FILES_URL?spaces=drive&q=${encode(query)}&fields=${encode("files($FILE_FIELDS)")}"
    }

    /** Lists the backup inside a specific folder of the user's Drive. */
    fun childrenQueryUrl(folderId: String, fileName: String = BackupEnvelope.FILE_NAME): String {
        val query = "name = '$fileName' and '${folderId}' in parents and trashed = false"
        return "$FILES_URL?spaces=drive&q=${encode(query)}&fields=${encode("files($FILE_FIELDS)")}"
    }

    fun downloadUrl(fileId: String): String = "$FILES_URL/${encode(fileId)}?alt=media"

    /**
     * Updates go through multipart as well, so the product-count property stays in step with the
     * content that was just uploaded (a plain media upload cannot set metadata).
     */
    fun updateUrl(fileId: String): String =
        "$UPLOAD_URL/${encode(fileId)}?uploadType=multipart&fields=${encode(FILE_FIELDS)}"

    fun createUrl(): String = "$UPLOAD_URL?uploadType=multipart&fields=${encode(FILE_FIELDS)}"

    /** Metadata-only create (no content), used for the visible folder itself. */
    fun metadataCreateUrl(): String = "$FILES_URL?fields=${encode(FILE_FIELDS)}"

    fun folderCreateBody(folderName: String = VISIBLE_FOLDER_NAME): ByteArray = buildJsonObject {
        put("name", folderName)
        put("mimeType", FOLDER_MIME_TYPE)
        putJsonArray("parents") { add(ROOT_FOLDER) }
    }.toString().toByteArray(Charsets.UTF_8)

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
    fun multipartBody(
        fileName: String,
        content: ByteArray,
        appProperties: Map<String, String> = emptyMap(),
        parents: List<String> = emptyList(),
        mimeType: String = "application/json"
    ): ByteArray {
        val metadata = buildJsonObject {
            put("name", fileName)
            put("mimeType", mimeType)
            // parents may only be set on create; updates keep the file where it is.
            if (parents.isNotEmpty()) {
                putJsonArray("parents") { parents.forEach { add(it) } }
            }
            if (appProperties.isNotEmpty()) {
                putJsonObject("appProperties") { appProperties.forEach { (key, value) -> put(key, value) } }
            }
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
        sizeBytes = obj["size"].textOrNull()?.toLongOrNull(),
        appProperties = (obj["appProperties"] as? JsonObject)
            ?.mapNotNull { (key, value) -> value.textOrNull()?.let { key to it } }
            ?.toMap()
            .orEmpty()
    )

    private fun JsonElement?.textOrNull(): String? = (this as? JsonPrimitive)?.content

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
