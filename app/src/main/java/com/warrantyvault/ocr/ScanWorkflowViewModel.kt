package com.warrantyvault.ocr

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
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
) {
    /** Human-readable explanation shown in the match dialog. */
    val reason: String
        get() = when (matchType) {
            "exact_serial" -> "Same serial number"
            "exact_imei" -> "Same IMEI"
            "brand_model" -> "Same brand and model line"
            else -> matchType
        }
}

data class ScanUiState(
    val step: ScanStep = ScanStep.IDLE,
    val statusMessage: String = "",
    val errorMessage: String? = null,
    val reviewDraft: ReviewDraft? = null,
    /** Field-level validation errors shown on the review screen, keyed by field name. */
    val validationErrors: Map<String, String> = emptyMap(),
    val capturedImageUri: Uri? = null,
    val savedProductId: Long? = null,
    val matchCandidates: List<ProductMatch> = emptyList(),
    val showMatchDialog: Boolean = false
)

/**
 * Orchestrates: input -> secure storage -> OCR -> parse -> review -> match -> confirm -> link.
 * The user MUST pass through review before any product is created or verified.
 * The ReviewDraft (user-edited) is the source of truth for saves; the original OcrResult is
 * retained only as evidence on the Document.
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
                    reviewDraft = buildDraft(result),
                    validationErrors = emptyMap()
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
            // Reuse an in-flight document row for this session (OCR retry) instead of
            // inserting a duplicate. documentId is only non-null when insert already happened.
            val existingId = documentId
            if (existingId != null && error == null) return@withContext existingId
            val stored = storedDocument
            val doc = Document(
                id = existingId ?: 0,
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
                "imei" -> d.copy(imei = value.filter { it.isDigit() }.take(16))
                "purchaseStore" -> d.copy(purchaseStore = value)
                "priceText" -> d.copy(priceText = value)
                "currency" -> d.copy(currency = value.uppercase().take(3))
                "warrantyMonthsText" -> d.copy(warrantyMonthsText = value.filter { it.isDigit() }.take(3))
                "warrantyProvider" -> d.copy(warrantyProvider = value)
                "warrantyType" -> d.copy(warrantyType = value)
                else -> d
            }
        }
        // Clear the field's validation error as soon as the user touches it again.
        val errors = _state.value.validationErrors - field
        if (errors != _state.value.validationErrors) {
            _state.value = _state.value.copy(validationErrors = errors)
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
        val errors = _state.value.validationErrors - field
        if (errors != _state.value.validationErrors) {
            _state.value = _state.value.copy(validationErrors = errors)
        }
    }

    fun cancelMatchDialog() {
        _state.value = _state.value.copy(showMatchDialog = false, matchCandidates = emptyList(), step = ScanStep.REVIEW_REQUIRED)
    }

    /**
     * Re-runs the parser on the retained raw OCR text (does NOT re-capture or re-OCR).
     * Fields the user explicitly edited keep their edited values; untouched fields get
     * whatever the fresh parse produces.
     */
    fun rescan() {
        val draft = _state.value.reviewDraft ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(step = ScanStep.PARSING, statusMessage = "Re-parsing…")
            val result = withContext(Dispatchers.Default) { OcrParser.parse(draft.result.rawText) }
            val fresh = buildDraft(result)
            val edited = draft.userEdits
            val merged = fresh.copy(
                result = draft.result.copy(rawText = result.rawText),
                productName = if ("productName" in edited) draft.productName else fresh.productName,
                brand = if ("brand" in edited) draft.brand else fresh.brand,
                model = if ("model" in edited) draft.model else fresh.model,
                serialNumber = if ("serialNumber" in edited) draft.serialNumber else fresh.serialNumber,
                imei = if ("imei" in edited) draft.imei else fresh.imei,
                purchaseStore = if ("purchaseStore" in edited) draft.purchaseStore else fresh.purchaseStore,
                priceText = if ("priceText" in edited) draft.priceText else fresh.priceText,
                currency = if ("currency" in edited || "priceText" in edited) draft.currency else fresh.currency,
                purchaseDate = if ("purchaseDate" in edited) draft.purchaseDate else fresh.purchaseDate,
                warrantyExpiryDate = if ("warrantyExpiryDate" in edited) draft.warrantyExpiryDate else fresh.warrantyExpiryDate,
                warrantyMonthsText = if ("warrantyMonthsText" in edited) draft.warrantyMonthsText else fresh.warrantyMonthsText,
                warrantyProvider = if ("warrantyProvider" in edited) draft.warrantyProvider else fresh.warrantyProvider,
                warrantyType = if ("warrantyType" in edited) draft.warrantyType else fresh.warrantyType,
                userEdits = draft.userEdits
            )
            _state.value = _state.value.copy(step = ScanStep.REVIEW_REQUIRED, reviewDraft = merged)
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

    // ---------------- Review validation ----------------

    /**
     * Validates the draft before Confirm. Returns field-level errors; saving is blocked
     * while any error exists. This is independent of parser warnings.
     */
    fun validateDraft(draft: ReviewDraft): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (draft.productName.isBlank()) {
            errors["productName"] = "Product name is required."
        }
        // Purchase date is optional by design (user may not know it), but if provided it must
        // not be in the far future.
        draft.purchaseDate?.let { pd ->
            if (pd > System.currentTimeMillis() + 7L * 86_400_000L) {
                errors["purchaseDate"] = "Purchase date is in the future."
            }
        }
        // Timeline: expiry >= purchase when both exist.
        if (draft.purchaseDate != null && draft.warrantyExpiryDate != null &&
            draft.warrantyExpiryDate < draft.purchaseDate
        ) {
            errors["warrantyExpiryDate"] = "Expiry is before the purchase date."
        }
        // Warranty months must be blank or a sane positive number.
        draft.warrantyMonthsText.toIntOrNull()?.let { months ->
            if (months !in 1..240) {
                errors["warrantyMonthsText"] = "Warranty must be 1–240 months."
            }
        }
        // Price must be numeric if entered.
        if (draft.priceText.isNotBlank() && draft.priceText.toDoubleOrNull() == null) {
            errors["priceText"] = "Price must be a number."
        }
        // Currency: exactly 3 letters if entered.
        if (draft.currency.isNotBlank() &&
            (draft.currency.length != 3 || draft.currency.any { !it.isLetter() })
        ) {
            errors["currency"] = "Use a 3-letter currency code (e.g. INR, USD)."
        }
        // Identifier sanity: serial at least 4 chars if entered; IMEI 14-16 digits.
        draft.serialNumber.trim().takeIf { it.isNotEmpty() }?.let { s ->
            if (s.length < 4) errors["serialNumber"] = "Serial looks too short."
        }
        draft.imei.trim().takeIf { it.isNotEmpty() }?.let { i ->
            if (!OcrParser.isPlausibleImei(i)) {
                errors["imei"] = "IMEI should be 14–16 digits (15 with a valid checksum)."
            }
        }
        return errors
    }

    // ---------------- Matching + save ----------------

    /** Normalizes an identifier for comparison: trim, collapse inner whitespace, uppercase. */
    private fun normalizeIdentifier(value: String): String =
        value.trim().replace(Regex("\\s+"), " ").uppercase()

    /** Searches for likely existing products using targeted DAO queries. */
    suspend fun findMatches(draft: ReviewDraft): List<ProductMatch> = withContext(Dispatchers.IO) {
        val userId = currentUserId()
        val candidates = mutableListOf<ProductMatch>()

        fun addCandidate(product: Product, type: String) {
            if (candidates.none { it.product.id == product.id }) candidates.add(ProductMatch(product, type))
        }

        // 1. Exact serial (normalized).
        val serial = draft.serialNumber.trim()
        if (serial.isNotEmpty()) {
            productDao.findByNormalizedSerial(userId, normalizeIdentifier(serial))?.let {
                addCandidate(it, "exact_serial")
            }
        }
        // 2. Exact IMEI (normalized; the draft field is digits-only by construction).
        val imei = draft.imei.trim()
        if (imei.isNotEmpty()) {
            productDao.findByNormalizedImei(userId, normalizeIdentifier(imei))?.let {
                addCandidate(it, "exact_imei")
            }
        }
        // 3. Brand + model token via targeted query (no full-table scan).
        val brand = draft.brand.trim()
        val model = draft.model.trim()
        if (brand.isNotEmpty() && model.isNotEmpty()) {
            val token = model.split(Regex("\\s+")).firstOrNull { it.length >= 3 } ?: model
            // Whole-word containment so "S24" doesn't match "S2400"-style junk; family
            // variants ("S24 Ultra" vs "S24+") still surface for the user to decide.
            val tokenRx = Regex("\\b${Regex.escape(token)}\\b", RegexOption.IGNORE_CASE)
            productDao.findByBrandAndModelToken(userId, brand, token).forEach { p ->
                val hay = "${p.model ?: ""} ${p.productName}"
                if (tokenRx.containsMatchIn(hay)) addCandidate(p, "brand_model")
            }
        }
        candidates
    }

    /** Called when the user taps Confirm on the review screen. Validates first. */
    fun confirmDraft() {
        val draft = _state.value.reviewDraft ?: return
        val errors = validateDraft(draft)
        if (errors.isNotEmpty()) {
            _state.value = _state.value.copy(validationErrors = errors, step = ScanStep.REVIEW_REQUIRED)
            return
        }
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

    /**
     * Update existing: applies reviewed values ONLY to fields the user explicitly edited,
     * and fills blanks from OCR where the existing product has nothing. The user's edits are
     * authoritative; OCR never overwrites a real value.
     */
    fun updateExistingProduct(existingId: Long) {
        val draft = _state.value.reviewDraft ?: return
        val errors = validateDraft(draft)
        if (errors.isNotEmpty()) {
            _state.value = _state.value.copy(validationErrors = errors, step = ScanStep.REVIEW_REQUIRED)
            return
        }
        _state.value = _state.value.copy(showMatchDialog = false)
        viewModelScope.launch {
            _state.value = _state.value.copy(step = ScanStep.SAVING)
            try {
                withContext(Dispatchers.IO) {
                    database.runInTransaction {
                        kotlinx.coroutines.runBlocking {
                            val existing = productDao.getProductByIdImmediate(existingId)
                                ?: throw IllegalStateException("Product no longer exists.")
                            val edited = draft.userEdits

                            val updated = existing.copy(
                                brand = if ("brand" in edited) draft.brand.ifBlank { null } else existing.brand ?: draft.brand.ifBlank { null },
                                model = if ("model" in edited) draft.model.ifBlank { null } else existing.model ?: draft.model.ifBlank { null },
                                serialNumber = if ("serialNumber" in edited) draft.serialNumber.ifBlank { null } else existing.serialNumber ?: draft.serialNumber.ifBlank { null },
                                imei = if ("imei" in edited) draft.imei.ifBlank { null } else existing.imei ?: draft.imei.ifBlank { null },
                                purchaseStore = if ("purchaseStore" in edited) draft.purchaseStore.ifBlank { null } else existing.purchaseStore ?: draft.purchaseStore.ifBlank { null },
                                purchasePrice = if ("priceText" in edited) draft.priceText.toDoubleOrNull() else existing.purchasePrice ?: draft.priceText.toDoubleOrNull(),
                                currency = if ("priceText" in edited || "currency" in edited) draft.currency.ifBlank { existing.currency } else if (existing.purchasePrice == null) draft.currency.ifBlank { existing.currency } else existing.currency,
                                purchaseDate = if ("purchaseDate" in edited) draft.purchaseDate else existing.purchaseDate ?: draft.purchaseDate,
                                warrantyExpiryDate = if ("warrantyExpiryDate" in edited) draft.warrantyExpiryDate else existing.warrantyExpiryDate ?: draft.warrantyExpiryDate,
                                warrantyPeriodMonths = if ("warrantyMonthsText" in edited) draft.warrantyMonthsText.toIntOrNull() else existing.warrantyPeriodMonths ?: draft.warrantyMonthsText.toIntOrNull(),
                                warrantyProvider = if ("warrantyProvider" in edited) draft.warrantyProvider.ifBlank { null } else existing.warrantyProvider ?: draft.warrantyProvider.ifBlank { null },
                                warrantyProviderType = if ("warrantyType" in edited) draft.warrantyType.ifBlank { null } else existing.warrantyProviderType ?: draft.warrantyType.ifBlank { null },
                                updatedAt = System.currentTimeMillis()
                            )
                            productDao.updateProduct(updated)
                            documentId?.let { docId ->
                                documentDao.linkDocument(docId, existingId, "reviewed", true, System.currentTimeMillis())
                            }
                        }
                    }
                }
                _state.value = _state.value.copy(
                    step = ScanStep.SUCCESS,
                    savedProductId = existingId,
                    statusMessage = "Document attached and product updated."
                )
            } catch (e: SQLiteConstraintException) {
                _state.value = _state.value.copy(step = ScanStep.ERROR, errorMessage = "That serial or IMEI already belongs to another product.")
            } catch (e: Exception) {
                _state.value = _state.value.copy(step = ScanStep.ERROR, errorMessage = e.message ?: "Update failed.")
            }
        }
    }

    /** Use existing: attach the document only. Product fields are never touched. */
    fun useExistingProduct(existingId: Long) {
        _state.value = _state.value.copy(showMatchDialog = false)
        viewModelScope.launch {
            _state.value = _state.value.copy(step = ScanStep.SAVING)
            try {
                withContext(Dispatchers.IO) {
                    documentId?.let { docId ->
                        documentDao.linkDocument(docId, existingId, "reviewed", true, System.currentTimeMillis())
                    }
                }
                _state.value = _state.value.copy(
                    step = ScanStep.SUCCESS,
                    savedProductId = existingId,
                    statusMessage = "Document attached to existing product."
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(step = ScanStep.ERROR, errorMessage = e.message ?: "Could not attach document.")
            }
        }
    }

    private suspend fun saveAsNewProduct(draft: ReviewDraft) {
        _state.value = _state.value.copy(step = ScanStep.SAVING, statusMessage = "Saving…")
        try {
            val productId = withContext(Dispatchers.IO) {
                database.runInTransaction<Long?> {
                    kotlinx.coroutines.runBlocking {
                        val serial = draft.serialNumber.trim().ifBlank { null }
                        val imei = draft.imei.trim().ifBlank { null }

                        // Duplicate guards (final authority is the DB constraint below).
                        if (serial != null) {
                            productDao.findByNormalizedSerial(currentUserId(), normalizeIdentifier(serial))?.let {
                                throw DuplicateProductException(it.id)
                            }
                        }
                        if (imei != null) {
                            productDao.findByNormalizedImei(currentUserId(), normalizeIdentifier(imei))?.let {
                                throw DuplicateProductException(it.id)
                            }
                        }

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
                                imei = imei,
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
                        // Atomic with the insert: if this fails the transaction rolls back
                        // and no orphan product row is left behind.
                        documentId?.let { docId ->
                            val linked = documentDao.linkDocument(docId, id, "reviewed", true, System.currentTimeMillis())
                            if (linked == 0) throw IllegalStateException("Could not link the document.")
                        }
                        id
                    }
                }
            }
            _state.value = _state.value.copy(
                step = ScanStep.SUCCESS,
                savedProductId = productId,
                statusMessage = if (documentId != null) "Product saved and document attached." else "Product saved."
            )
        } catch (e: DuplicateProductException) {
            // Convert the duplicate into the standard match dialog flow.
            val existing = withContext(Dispatchers.IO) { productDao.getProductByIdImmediate(e.existingId) }
            val matches = existing?.let { listOf(ProductMatch(it, "exact_serial")) } ?: emptyList()
            _state.value = _state.value.copy(matchCandidates = matches, showMatchDialog = true, step = ScanStep.REVIEW_REQUIRED)
        } catch (e: SQLiteConstraintException) {
            // Raced insert: the unique index caught what the check missed.
            _state.value = _state.value.copy(
                step = ScanStep.ERROR,
                errorMessage = "A product with this serial or IMEI already exists. Please review and try attaching the document to it instead."
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(step = ScanStep.ERROR, errorMessage = "Save failed: ${e.message ?: "unknown error"}")
        }
    }

    private class DuplicateProductException(val existingId: Long) : Exception("duplicate")

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
