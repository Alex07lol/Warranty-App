package com.warrantyvault.ocr

import kotlinx.serialization.Serializable

/** How a field's value was obtained. Determines display and trust semantics. */
enum class OcrFieldSource {
    /** Detected by the parser from the OCR text. Always requires user confirmation. */
    DETECTED,
    /** Explicitly entered/cleared by the user during review. */
    USER_CONFIRMED
}

@Serializable
data class OcrFieldValue<T>(
    val value: T?,
    /** 0..1 rough confidence in the extraction; 1.0 means user-entered. */
    val confidence: Float = 0f,
    /** The raw receipt line(s) the value came from, for auditability. */
    val source: String? = null
)

/**
 * Structured intermediate result of parsing raw OCR text.
 *
 * This type is deliberately decoupled from Room entities: the engine produces it,
 * the user reviews/edits it, and only after confirmation is it projected onto Product/Document.
 */
@Serializable
data class OcrResult(
    val rawText: String,
    val productName: OcrFieldValue<String> = OcrFieldValue(null),
    val brand: OcrFieldValue<String> = OcrFieldValue(null),
    val model: OcrFieldValue<String> = OcrFieldValue(null),
    val serialNumber: OcrFieldValue<String> = OcrFieldValue(null),
    val imei: OcrFieldValue<String> = OcrFieldValue(null),
    val purchaseStore: OcrFieldValue<String> = OcrFieldValue(null),
    val purchasePrice: OcrFieldValue<Double> = OcrFieldValue(null),
    val currency: OcrFieldValue<String> = OcrFieldValue(null),
    val purchaseDate: OcrFieldValue<String> = OcrFieldValue(null),
    val warrantyStartDate: OcrFieldValue<String> = OcrFieldValue(null),
    val warrantyExpiryDate: OcrFieldValue<String> = OcrFieldValue(null),
    val warrantyPeriodMonths: OcrFieldValue<Int> = OcrFieldValue(null),
    val warrantyProvider: OcrFieldValue<String> = OcrFieldValue(null),
    val warrantyType: OcrFieldValue<String> = OcrFieldValue(null),
    val warnings: List<String> = emptyList()
) {
    /** True when at least one core identity field was detected. */
    fun hasAnyIdentity(): Boolean =
        productName.value != null || brand.value != null || model.value != null ||
            serialNumber.value != null || imei.value != null
}

/** High-level status of the scan workflow. */
enum class ScanStep {
    IDLE,
    PROCESSING,
    PARSING,
    REVIEW_REQUIRED,
    SAVING,
    SUCCESS,
    ERROR
}

/**
 * A reviewable product candidate built from an OcrResult. All values are editable by the
 * user; nothing here is trusted product data until the user confirms.
 */
data class ReviewDraft(
    val result: OcrResult,
    val productName: String,
    val brand: String,
    val model: String,
    val serialNumber: String,
    val imei: String,
    val purchaseStore: String,
    val priceText: String,
    val currency: String,
    val purchaseDate: Long?,
    val warrantyExpiryDate: Long?,
    val warrantyMonthsText: String,
    val warrantyProvider: String,
    val warrantyType: String,
    val userEdits: MutableSet<String> = mutableSetOf()
) {
    val hasUserEdits: Boolean get() = userEdits.isNotEmpty()
}
