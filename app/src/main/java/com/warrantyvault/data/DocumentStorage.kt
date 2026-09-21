package com.warrantyvault.data

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

data class StoredDocument(
    val file: File,
    val mimeType: String,
    val sizeBytes: Long
)

/**
 * Copies externally picked/captured content into app-private storage
 * (filesDir/documents) and returns stable metadata. The original content:// URI is never
 * persisted; permissions to it lapse and they are not stable references.
 *
 * Hardening: MIME allowlist, size cap, partial-file cleanup on failure, and generated
 * filenames that cannot escape the documents directory.
 */
class DocumentStorage(private val context: Context) {

    companion object {
        /** Document types this app accepts. Anything else is rejected with a clear error. */
        private val ALLOWED_MIME_PREFIXES = listOf("image/")
        private val ALLOWED_MIME_EXACT = setOf("application/pdf")

        /** Refuse to ingest absurd files; receipts/photos are far below this. */
        private const val MAX_BYTES = 40L * 1024 * 1024
    }

    private val documentsDir: File
        get() = File(context.filesDir, "documents").apply { if (!exists()) mkdirs() }

    @Throws(IOException::class)
    suspend fun importFromUri(uri: Uri, suggestedName: String? = null): StoredDocument = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val realMime = resolver.getType(uri) ?: guessMimeFromUri(uri) ?: "application/octet-stream"

        if (!isAllowed(realMime)) {
            throw IOException("Unsupported file type: $realMime. Please choose an image or PDF.")
        }

        // Generated name only — user input never touches the path.
        val extension = sanitizeExtension(buildExtension(realMime, uri))
        val fileName = "doc_${System.currentTimeMillis()}_${(1000..9999).random()}$extension"
        val destination = File(documentsDir, fileName)

        // Defense in depth: the destination must stay inside documentsDir.
        if (destination.canonicalFile.parentFile != documentsDir.canonicalFile) {
            throw IOException("Invalid destination path.")
        }

        var size = 0L
        try {
            val input = resolver.openInputStream(uri) ?: throw IOException("Unable to open the selected document.")
            input.use { stream ->
                java.io.FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read == -1) break
                        size += read
                        if (size > MAX_BYTES) throw IOException("The selected document is too large.")
                        output.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: Exception) {
            // Never leave a partial file behind.
            destination.delete()
            throw e
        }

        if (size == 0L) {
            destination.delete()
            throw IOException("The selected document is empty.")
        }

        StoredDocument(destination, realMime, size)
    }

    suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        val f = File(path)
        // Only ever delete inside our own documents dir.
        if (f.isFile && f.canonicalFile.parentFile == documentsDir.canonicalFile) f.delete()
        Unit
    }

    private fun isAllowed(mime: String): Boolean =
        ALLOWED_MIME_PREFIXES.any { mime.startsWith(it) } || mime in ALLOWED_MIME_EXACT

    private fun sanitizeExtension(ext: String): String {
        val clean = ext.removePrefix(".").filter { it.isLetterOrDigit() }.lowercase().take(8)
        return ".$clean"
    }

    private fun guessMimeFromUri(uri: Uri): String? {
        val ext = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic", "heif" -> "image/heic"
            "pdf" -> "application/pdf"
            else -> null
        }
    }

    private fun buildExtension(mime: String, uri: Uri): String {
        // Prefer the extension implied by the real MIME type.
        val fromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        val fromUri = uri.lastPathSegment?.substringAfterLast('.', "")
            ?.takeIf { it.length in 1..8 && it.none { c -> !c.isLetterOrDigit() } }
        val ext = fromMime ?: fromUri ?: "bin"
        return if (ext.startsWith('.')) ext else ".$ext"
    }
}
