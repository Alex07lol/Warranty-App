package com.warrantyvault.ocr

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.warrantyvault.data.Document
import com.warrantyvault.data.DocumentStorage
import com.warrantyvault.data.Product
import com.warrantyvault.data.StoredDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

data class ProductMatch(
    val product: Product,
    /** What made this a candidate: exact_serial / exact_imei / brand_model. */
    val matchType: String
)

data class ScanUiState(
    val step: ScanStep = ScanStep.IDLE,
    val statusMessage: String = "",
    val errorMessage: String? = null,
    val reviewDraft: ReviewDraft? = null,
    val capturedImageUri: Uri? = null,
    val savedProductId: Long? = null,
    val matchCandidates: List<ProductMatch> = emptyList(),
    val showMatchDialog: Boolean = false
)

/**
 * Orchestrates: input -> secure storage -> OCR -> parse -> review -> match -> confirm -> link.
 * The user MUST pass through review before any product is created or verified.
 */
class ScanWorkflowViewModel(app: Application) : AndroidViewModel(app) {

    private val database = com.warrantyvault.data.AppDatabase.getDatabase(app)
    private val documentDao = database.documentDao()
    private val productDao = database.productDao()
    private val storage = DocumentStorage(app)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state

    private val ocrEngine = OcrEngine(app)

    private var storedDocument: StoredDocument? = null
    private var documentId: Long? = null

    // ---------------- Input ----------------

    /** Copies the image into app-private storage, runs OCR, parses, then requires review. */
    fun onImageSelected(uri: Uri) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(
                    step = ScanStep.PROCESSING,
                    statusMessage = "Securing document…",
                    errorMessage = null,
                    reviewDraft = null,
                    savedProductId = null
                )

                val stored = withContext(Dispatchers.IO) { storage.importFromUri(uri) }
                storedDocument = stored
                _state.value = _state.value.copy(capturedImageUri = Uri.fromFile(stored.file))

                _state.value = _state.value.copy(statusMessage = "Reading text…")
                val ocrText = try {
                    ocrEngine.recognizeUri(uri).text
                } catch (e: Exception) {
                    persistDocument(ocrText = null, error = "OCR failed: ${e.message}")
                    _state.value = _state.value.copy(
                        step = ScanStep.ERROR,
                        errorMessage = "We couldn't read text from this image.\n\nTry:\n• Better lighting\n• Keeping the document flat\n• Capturing the entire receipt\n• Taking the photo closer"
                    )
                    return@launch
                }

                if (ocrText.isBlank() || ocrText.lines().count { it.isNotBlank() } < 2) {
                    persistDocument(ocrText = null, error = "OCR produced no readable text")
                    _state.value = _state.value.copy(
                        step = ScanStep.ERROR,
                        errorMessage = "We couldn't confidently read this document.\n\nTry:\n• Better lighting\n• Keeping the document flat\n• Capturing the entire receipt\n• Taking the photo closer"
                    )
                    return@launch
                }

                _state.value = _state.value.copy(step = ScanStep.PARSING, statusMessage = "Extracting details…")
                val result = withContext(Dispatchers.Default) { OcrParser.parse(ocrText) }

                val docId = persistDocument(ocrText = ocrText, parsed = result)
                documentId = docId

                _state.value = _state.value.copy(
                    step = ScanStep.REVIEW_REQUIRED,
                    reviewDraft = buildDraft(result)
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    step = ScanStep.ERROR,
                    errorMessage = e.message ?: "Something went wrong while processing the document."
                )
            }
        }
    }

    private suspend fun persistDocument(ocrText: String?, parsed: OcrResult? = null, error: String? = null): Long =
        withContext(Dispatchers.IO) {
            val stored = storedDocument
            val doc = Document(
                id = documentId ?: 0,
                userId = currentUserId(),
                documentType = "receipt",
                fileName = stored?.file?.name ?: "unknown",
                filePath = stored?.file?.path ?: "",
                fileSize = stored?.sizeBytes ?: 0L,
                mimeType = stored?.mimeType ?: "application/octet-stream",
                docState = "unreviewed",
                verified = false, // OCR is NOT verification.
                ocrStatus = when {
                    error != null -> "failed"
                    ocrText != null -> "done"
                    else -> "processing"
                },
                ocrText = ocrText,
                parsedData = parsed?.let { json.encodeToString(it) },
                ocrError = error
            )
            documentDao.insert(doc)
        }

    private fun currentUserId(): Long =
        (getApplication<Application>() as com.warrantyvault.WarrantyVaultApplication).currentUserId

    private fun buildDraft(result: OcrResult): ReviewDraft = ReviewDraft(
        result = result,
        productName = result.productName.value ?: "",
        brand = result.brand.value ?: "",
        model = result.model.value ?: "",
        serialNumber = result.serialNumber.value ?: "",
        imei = result.imei.value ?: "",
        purchaseStore = result.purchaseStore.value ?: "",
        priceText = result.purchasePrice.value?.let { formatPrice(it) } ?: "",
        currency = result.currency.value ?: "USD",
        purchaseDate = result.purchaseDate.value?.let { parseIsoToMillis(it) },
        warrantyExpiryDate = result.warrantyExpiryDate.value?.let { parseIsoToMillis(it) },
        warrantyMonthsText = result.warrantyPeriodMonths.value?.toString() ?: "",
        warrantyProvider = result.warrantyProvider.value ?: "",
        warrantyType = result.warrantyType.value ?: ""
    )

    private fun parseIsoToMillis(iso: String): Long? =
        runCatching { DateUtils.toEpochMillis(LocalDate.parse(iso)) }.getOrNull()

    private fun formatPrice(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    // ---------------- Review edits ----------------

    fun updateDraft(transform: (ReviewDraft) -> ReviewDraft) {
        val current = _state.value.reviewDraft ?: return
        _state.value = _state.value.copy(reviewDraft = transform(current))
    }

    fun markEdited(field: String) {
        val current = _state.value.reviewDraft ?: return
        current.userEdits.add(field)
    }

    fun updateField(field: String, value: String) {
        markEdited(field)
        updateDraft { d ->
            when (field) {
                "productName" -> d.copy(productName = value)
                "brand" -> d.copy(brand = value)
                "model" -> d.copy(model = value)
                "serialNumber" -> d.copy(serialNumber = value)
                "imei" -> d.copy(imei = value)
                "purchaseStore" -> d.copy(purchaseStore = value)
                "priceText" -> d.copy(priceText = value)
                "currency" -> d.copy(currency = value.uppercase().take(3))
                "warrantyMonthsText" -> d.copy(warrantyMonthsText = value.filter { it.isDigit() }.take(3))
                "warrantyProvider" -> d.copy(warrantyProvider = value)
                "warrantyType" -> d.copy(warrantyType = value)
                else -> d
            }
        }
    }

    fun updateDateField(field: String, millis: Long?) {
        markEdited(field)
        updateDraft { d ->
            when (field) {
                "purchaseDate" -> d.copy(purchaseDate = millis)
                "warrantyExpiryDate" -> d.copy(warrantyExpiryDate = millis)
                else -> d
            }
        }
    }

    fun cancelMatchDialog() {
        _state.value = _state.value.copy(showMatchDialog = false, matchCandidates = emptyList(), step = ScanStep.REVIEW_REQUIRED)
    }

    /** Re-runs parsing on the retained raw text without redoing OCR. */
    fun rescan() {
        val draft = _state.value.reviewDraft ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(step = ScanStep.PARSING, statusMessage = "Re-parsing…")
            val result = withContext(Dispatchers.Default) { OcrParser.parse(draft.result.rawText) }
            _state.value = _state.value.copy(step = ScanStep.REVIEW_REQUIRED, reviewDraft = buildDraft(result))
        }
    }

    fun cancelReview() {
        // Keep the stored document as unreviewed evidence; nothing else changes.
        _state.value = _state.value.copy(
            step = ScanStep.IDLE,
            reviewDraft = null,
            statusMessage = if (documentId != null) "Document saved as unreviewed." else ""
        )
    }

    // ---------------- Matching + save ----------------

    suspend fun findMatches(draft: ReviewDraft): List<ProductMatch> = withContext(Dispatchers.IO) {
        val userId = currentUserId()
        val candidates = mutableListOf<ProductMatch>()
        val serial = draft.serialNumber.trim()
        val imei = draft.imei.trim()
        val brand = draft.brand.trim()
        val model = draft.model.trim()

        val all = productDao.getAllForUser(userId)
        if (serial.isNotEmpty()) {
            all.filter { it.serialNumber?.trim().equals(serial, ignoreCase = true) }
                .forEach { candidates.add(ProductMatch(it, "exact_serial")) }
        }
        if (imei.isNotEmpty()) {
            all.filter { it.serialNumber?.trim().equals(imei, ignoreCase = true) }
                .forEach { p ->
                    if (candidates.none { it.product.id == p.id }) candidates.add(ProductMatch(p, "exact_imei"))
                }
        }
        if (brand.isNotEmpty() && model.isNotEmpty()) {
            all.filter { p ->
                p.brand?.equals(brand, ignoreCase = true) == true &&
                    (p.model?.contains(model, ignoreCase = true) == true ||
                        p.productName.contains(model, ignoreCase = true))
            }.forEach { p ->
                if (candidates.none { it.product.id == p.id }) candidates.add(ProductMatch(p, "brand_model"))
            }
        }
        candidates
    }

    /** Called when the user taps Confirm on the review screen. */
    fun confirmDraft() {
        val draft = _state.value.reviewDraft ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(step = ScanStep.SAVING, statusMessage = "Checking for existing products…")
            val matches = findMatches(draft)
            if (matches.isNotEmpty()) {
                _state.value = _state.value.copy(matchCandidates = matches, showMatchDialog = true, step = ScanStep.REVIEW_REQUIRED)
            } else {
                saveAsNewProduct(draft)
            }
        }
    }

    fun createNewFromDraft() {
        _state.value = _state.value.copy(showMatchDialog = false)
        val draft = _state.value.reviewDraft ?: return
        viewModelScope.launch { saveAsNewProduct(draft) }
    }

    fun updateExistingProduct(existingId: Long) {
        val draft = _state.value.reviewDraft ?: return
        _state.value = _state.value.copy(showMatchDialog = false)
        viewModelScope.launch {
            _state.value = _state.value.copy(step = ScanStep.SAVING)
            withContext(Dispatchers.IO) {
                val existing = productDao.getProductByIdImmediate(existingId) ?: return@withContext
                // Fill blanks only; never clobber existing user data.
                val updated = existing.copy(
                    brand = existing.brand ?: draft.brand.ifBlank { null },
                    model = existing.model ?: draft.model.ifBlank { null },
                    serialNumber = existing.serialNumber ?: draft.serialNumber.ifBlank { null },
                    purchaseStore = existing.purchaseStore ?: draft.purchaseStore.ifBlank { null },
                    purchasePrice = existing.purchasePrice ?: draft.priceText.toDoubleOrNull(),
                    currency = if (existing.purchasePrice == null) draft.currency.ifBlank { existing.currency } else existing.currency,
                    purchaseDate = existing.purchaseDate ?: draft.purchaseDate,
                    warrantyExpiryDate = existing.warrantyExpiryDate ?: draft.warrantyExpiryDate,
                    warrantyPeriodMonths = existing.warrantyPeriodMonths ?: draft.warrantyMonthsText.toIntOrNull(),
                    updatedAt = System.currentTimeMillis()
                )
                productDao.updateProduct(updated)
                documentId?.let { docId -> linkDocument(docId, existingId, reviewed = true) }
            }
            _state.value = _state.value.copy(step = ScanStep.SUCCESS, savedProductId = existingId, statusMessage = "Document attached to existing product.")
        }
    }

    fun useExistingProduct(existingId: Long) {
        _state.value = _state.value.copy(showMatchDialog = false)
        viewModelScope.launch {
            _state.value = _state.value.copy(step = ScanStep.SAVING)
            documentId?.let { docId -> linkDocument(docId, existingId, reviewed = true) }
            _state.value = _state.value.copy(step = ScanStep.SUCCESS, savedProductId = existingId, statusMessage = "Document attached to existing product.")
        }
    }

    private suspend fun saveAsNewProduct(draft: ReviewDraft) {
        _state.value = _state.value.copy(step = ScanStep.SAVING, statusMessage = "Saving…")
        try {
            val duplicateId = withContext(Dispatchers.IO) {
                val serial = draft.serialNumber.trim().ifBlank { null }
                serial?.let { productDao.getProductBySerialNumber(currentUserId(), it)?.id }
            }
            if (duplicateId != null) {
                val existing = productDao.getProductBySerialNumber(currentUserId(), draft.serialNumber.trim())
                val matches = existing?.let { listOf(ProductMatch(it, "exact_serial")) } ?: emptyList()
                _state.value = _state.value.copy(matchCandidates = matches, showMatchDialog = true, step = ScanStep.REVIEW_REQUIRED)
                return
            }
            val productId = withContext(Dispatchers.IO) {
                val serial = draft.serialNumber.trim().ifBlank { null }
                val expiry = draft.warrantyExpiryDate ?: draft.purchaseDate?.let { pd ->
                    draft.warrantyMonthsText.toIntOrNull()?.let { months ->
                        DateUtils.toEpochMillis(DateUtils.addMonths(DateUtils.fromEpochMillis(pd), months))
                    }
                }
                val id = productDao.insertProduct(
                    Product(
                        userId = currentUserId(),
                        productName = draft.productName.ifBlank { "Unnamed product" },
                        brand = draft.brand.ifBlank { null },
                        model = draft.model.ifBlank { null },
                        serialNumber = serial,
                        purchaseStore = draft.purchaseStore.ifBlank { null },
                        purchasePrice = draft.priceText.toDoubleOrNull(),
                        currency = draft.currency.ifBlank { "USD" },
                        purchaseDate = draft.purchaseDate,
                        warrantyExpiryDate = expiry,
                        warrantyPeriodMonths = draft.warrantyMonthsText.toIntOrNull(),
                        warrantyProvider = draft.warrantyProvider.ifBlank { null },
                        warrantyProviderType = draft.warrantyType.ifBlank { null }
                    )
                )
                documentId?.let { docId -> linkDocument(docId, id, reviewed = true) }
                id
            }
            _state.value = _state.value.copy(
                step = ScanStep.SUCCESS,
                savedProductId = productId,
                statusMessage = "Product saved and document attached."
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(step = ScanStep.ERROR, errorMessage = "Save failed: ${e.message}")
        }
    }

    private suspend fun linkDocument(docId: Long, productId: Long, reviewed: Boolean) = withContext(Dispatchers.IO) {
        val doc = documentDao.getDocumentByIdImmediate(docId) ?: return@withContext
        documentDao.updateDocument(
            doc.copy(
                productId = productId,
                docState = if (reviewed) "reviewed" else doc.docState,
                verified = if (reviewed) true else doc.verified,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun dismissError() {
        _state.value = _state.value.copy(step = ScanStep.IDLE, errorMessage = null)
    }

    fun reset() {
        storedDocument = null
        documentId = null
        _state.value = ScanUiState()
    }

    override fun onCleared() {
        ocrEngine.close()
        super.onCleared()
    }
}
