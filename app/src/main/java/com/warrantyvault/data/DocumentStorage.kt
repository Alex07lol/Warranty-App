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
 * (filesdir/documents) and returns stable metadata. The original content:// URI is never
 * persisted; permissions to it lapse and they are not stable references.
 */
class DocumentStorage(private val context: Context) {

    private val documentsDir: File
        get() = File(context.filesDir, "documents").apply { if (!exists()) mkdirs() }

    @Throws(IOException::class)
    suspend fun importFromUri(uri: Uri, suggestedName: String? = null): StoredDocument = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val realMime = resolver.getType(uri) ?: guessMimeFromUri(uri) ?: "application/octet-stream"

        val extension = buildExtension(realMime, suggestedName, uri)
        val fileName = "doc_${System.currentTimeMillis()}_${(1000..9999).random()}$extension"
        val destination = File(documentsDir, fileName)

        val size: Long
        val input = resolver.openInputStream(uri) ?: throw IOException("Unable to open the selected document.")
        input.use { stream ->
            java.io.FileOutputStream(destination).use { output ->
                size = stream.copyTo(output)
            }
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

    private fun guessMimeFromUri(uri: Uri): String? {
        val ext = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(context.contentResolver.getType(uri) ?: "") ?: uri.lastPathSegment
            ?.substringAfterLast('.', "")?.lowercase()
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

    private fun buildExtension(mime: String, suggestedName: String?, uri: Uri): String {
        // Prefer the extension implied by the real MIME type.
        val fromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        val fromName = suggestedName?.substringAfterLast('.', "")?.takeIf { it.length in 1..8 && it.none { c -> !c.isLetterOrDigit() } }
        val fromUri = uri.lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.length in 1..8 && it.none { c -> !c.isLetterOrDigit() } }
        val ext = fromMime ?: fromName ?: fromUri ?: "bin"
        return if (ext.startsWith('.')) ext else ".$ext"
    }
}
