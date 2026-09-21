package com.warrantyvault.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Raw OCR text with engine metadata. */
data class OcrTextResult(
    val text: String,
    val lineCount: Int
)

/**
 * Async ML Kit OCR engine. [recognizeUri]/[recognizeBitmap] suspend until ML Kit completes;
 * they never block the calling thread (no Tasks.await on the UI thread). Runs on whatever
 * dispatcher the caller provides — the workflow calls it from a background coroutine.
 */
class OcrEngine(context: Context) {

    private val appContext = context.applicationContext
    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeUri(imageUri: Uri): OcrTextResult {
        // InputImage.fromFilePath does disk IO; caller already runs on IO dispatcher.
        val image = InputImage.fromFilePath(appContext, imageUri)
        return recognize(image)
    }

    suspend fun recognizeBitmap(bitmap: Bitmap): OcrTextResult {
        return recognize(InputImage.fromBitmap(bitmap, 0))
    }

    private suspend fun recognize(image: InputImage): OcrTextResult =
        suspendCancellableCoroutine { cont ->
            val task = recognizer.process(image)
            task.addOnSuccessListener { text ->
                val clean = text.text
                cont.resume(OcrTextResult(clean, clean.lines().count { it.isNotBlank() }))
            }
            task.addOnFailureListener { e -> cont.resumeWithException(e) }
        }

    fun close() {
        recognizer.close()
    }
}
