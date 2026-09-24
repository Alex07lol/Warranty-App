package com.warrantyvault.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Versioned wrapper around the export document that is stored in Google Drive.
 *
 * The inner `data` payload is exactly what
 * [com.warrantyvault.service.ExportImportService.buildExportJson] produces, so a Drive backup can
 * always be unwrapped and fed back through the normal validated import pipeline — and a backup
 * file taken from Drive is also a perfectly good manual JSON import.
 *
 * Only the wrapper adds meaning: the format marker lets us reject files that merely happen to be
 * JSON, and the version lets a future app release migrate older backups instead of guessing.
 *
 * Pure Kotlin (no Android or network dependency) so the format is unit-testable on the JVM.
 */
object BackupEnvelope {

    const val FORMAT = "warrantyvault-backup"
    const val CURRENT_VERSION = 1

    /** Name of the backup file inside the Drive app data folder. */
    const val FILE_NAME = "warrantyvault-backup.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    private data class Envelope(
        val format: String,
        val version: Int,
        val exportedAt: String,
        val appVersion: String,
        val count: Int,
        val data: JsonElement
    )

    /** A validated backup ready to be handed to the import pipeline. */
    data class Unwrapped(
        val version: Int,
        val exportedAt: String,
        val count: Int,
        val appVersion: String,
        /** The inner export document as JSON text. */
        val dataJson: String
    )

    /** Wraps [exportJson] with format/version metadata. [count] is the number of products inside. */
    fun wrap(exportJson: String, appVersion: String, exportedAt: String, count: Int): String {
        val data = json.parseToJsonElement(exportJson)
        return json.encodeToString(
            Envelope.serializer(),
            Envelope(
                format = FORMAT,
                version = CURRENT_VERSION,
                exportedAt = exportedAt,
                appVersion = appVersion,
                count = count,
                data = data
            )
        )
    }

    /**
     * Validates and unwraps a backup document.
     *
     * Anything that is not a WarrantyVault backup fails here with a human-readable reason —
     * wrong format marker, a version from a newer app, a missing payload, or a payload with no
     * products list — instead of letting garbage reach the database importer.
     */
    fun unwrap(backupJson: String): Result<Unwrapped> = runCatching {
        val root = runCatching { json.parseToJsonElement(backupJson) }
            .getOrElse { throw IllegalArgumentException("Backup file is not valid JSON") }
        val obj = root as? JsonObject
            ?: throw IllegalArgumentException("Backup file is not a WarrantyVault backup")

        val format = obj["format"].textOrNull()
            ?: throw IllegalArgumentException("Backup file is not a WarrantyVault backup (no format marker)")
        if (format != FORMAT) {
            throw IllegalArgumentException("Backup file is not a WarrantyVault backup (found '$format')")
        }

        val version = obj["version"].textOrNull()?.toIntOrNull()
            ?: throw IllegalArgumentException("Backup file has no version number")
        if (version > CURRENT_VERSION) {
            throw IllegalArgumentException(
                "This backup was made by a newer version of the app (backup v$version, app supports v$CURRENT_VERSION)."
            )
        }
        if (version < 1) throw IllegalArgumentException("Unsupported backup version $version")

        val data = obj["data"] as? JsonObject
            ?: throw IllegalArgumentException("Backup file has no data payload")
        val products: JsonArray = (data["products"] as? JsonArray)
            ?: throw IllegalArgumentException("Backup payload has no products list")

        Unwrapped(
            version = version,
            exportedAt = obj["exportedAt"].textOrNull().orEmpty(),
            count = obj["count"].textOrNull()?.toIntOrNull() ?: products.size,
            appVersion = obj["appVersion"].textOrNull().orEmpty(),
            dataJson = data.toString()
        )
    }

    /** True when [backupJson] looks like a WarrantyVault backup wrapper (used for quick checks). */
    fun looksLikeBackup(backupJson: String): Boolean = unwrap(backupJson).isSuccess

    private fun JsonElement?.textOrNull(): String? = (this as? JsonPrimitive)?.content
}
