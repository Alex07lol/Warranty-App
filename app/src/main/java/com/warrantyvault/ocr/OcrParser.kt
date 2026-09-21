package com.warrantyvault.ocr

import java.time.LocalDate

/**
 * Deterministic receipt parser: raw OCR text -> structured [OcrResult].
 * Knows nothing about Room, Compose, or Product creation.
 */
object OcrParser {

    /** Small duration/expiry disagreement (<= this many days) is acceptable. */
    private const val DURATION_TOLERANCE_DAYS = 7L
    /** Larger-but-not-major disagreement still gets a mild warning. */
    private const val DURATION_MAJOR_DAYS = 45L

    fun parse(rawText: String, today: LocalDate = LocalDate.now()): OcrResult {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val warnings = mutableListOf<String>()

        // --- Store ---
        val merchantHit = IdentifierParser.extractMerchant(lines)
        val merchant = merchantHit?.first

        // --- Model / product identity (brand is resolved after the product line is known) ---
        val modelHit = IdentifierParser.extractModel(lines)
        val productLineHit = IdentifierParser.extractProductLine(lines)
        val labelledProduct = IdentifierParser.extractLabelledProduct(lines)
        val candidate = IdentifierParser.bestProductCandidate(lines, merchant)

        val productLine: String?
        val productSource: String?
        var productConfidence = 0.4f
        when {
            labelledProduct != null -> {
                productLine = labelledProduct.first; productSource = labelledProduct.second; productConfidence = 0.8f
            }
            productLineHit != null -> {
                productLine = productLineHit.first; productSource = productLineHit.second; productConfidence = 0.85f
            }
            candidate != null -> {
                productLine = candidate; productSource = null; productConfidence = 0.35f
            }
            else -> { productLine = null; productSource = null }
        }

        // Brand: dictionary hit first; if absent, infer from the recognized product line
        // ("Galaxy S24 Ultra" with no literal "Samsung" on the receipt → Samsung).
        val brandHit: String? = IdentifierParser.extractBrand(lines, merchant, productLine)

        val productName: String? = when {
            productLine == null -> null
            brandHit != null && productLineHit != null &&
                IdentifierParser.brandNearModel(brandHit, productLineHit.second) &&
                !productLine.lowercase().contains(productLineHit.first.lowercase()) ->
                "$brandHit $productLine"
            else -> productLine
        }
        if (productName == null) warnings.add("No product identity detected — please enter the product name.")
        if (productConfidence <= 0.4f) warnings.add("Product name is a low-confidence guess from receipt text.")

        // --- Identifiers ---
        val serialHit = IdentifierParser.extractSerial(lines)
        val imeiHit = IdentifierParser.extractImei(lines)
        if (serialHit == null && imeiHit == null) {
            warnings.add("No serial number or IMEI detected — duplicate detection will be weaker.")
        }
        // Validate detected IMEI shape. Shape failures drop the value; a shape-valid IMEI
        // that fails the Luhn checksum is kept but flagged — OCR often misreads one digit,
        // and silently discarding the IMEI would silently weaken duplicate detection.
        val imeiRaw = imeiHit?.first
        var imeiValue: String? = null
        if (imeiRaw != null) {
            val digits = imeiRaw.filter { it.isDigit() }
            when {
                digits.length !in 14..16 -> {
                    warnings.add("Detected IMEI looks malformed — please verify it.")
                }
                digits.length == 15 && !luhnValid(digits) -> {
                    imeiValue = digits
                    warnings.add("Detected IMEI may contain a misread digit (checksum failed) — please verify it.")
                }
                else -> imeiValue = digits
            }
        }

        // --- Price ---
        val priceHit = PriceParser.extractPrice(lines)
        val currencyHit = PriceParser.detectCurrency(lines)
        val priceCount = countPriceCandidates(lines)
        if (priceCount > 1) warnings.add("Multiple price-like amounts detected — please verify the purchase price.")
        if (priceHit == null) warnings.add("No purchase price detected.")

        // --- Dates ---
        // Context-aware: labelled purchase/start/expiry dates win. A bare date is used as the
        // purchase date ONLY when it is the only date on the receipt and no expiry label exists.
        val dateHits = DateParser.extractDatedLines(lines)
        val purchaseHit = dateHits.lastOrNull { it.context == "purchase" }
        val startHit = dateHits.lastOrNull { it.context == "start" }
        val expiryHit = dateHits.lastOrNull { it.context == "expiry" }
        val unlabelledHits = dateHits.filter { it.context == null }

        val purchaseHitFinal = purchaseHit
            ?: unlabelledHits.singleOrNull()?.takeIf { expiryHit == null && startHit == null }
        val purchaseDate: LocalDate? = purchaseHitFinal?.date
        val warrantyStart: LocalDate? = startHit?.date ?: purchaseDate
        val explicitExpiry: LocalDate? = expiryHit?.date

        if (purchaseDate == null) warnings.add("No purchase date detected — please set it manually.")
        if (dateHits.count { it.context == null } > 1 && purchaseDate == null) {
            warnings.add("Several unlabelled dates found — please confirm which applies.")
        }
        // Ambiguity (03/04/2026 could be DD/MM or MM/DD): say so instead of pretending certainty.
        val ambiguousHit = purchaseHitFinal?.takeIf { it.ambiguous }
            ?: startHit?.takeIf { it.ambiguous }
            ?: expiryHit?.takeIf { it.ambiguous }
        if (ambiguousHit != null) {
            warnings.add(
                "The date \"${ambiguousHit.source}\" could be read day-first or month-first — " +
                    "we assumed ${ambiguousHit.date}. Please confirm."
            )
        }

        // --- Warranty duration ---
        val durationHit = WarrantyParser.extractDuration(lines)
        val durationMonths = durationHit?.months

        // --- Resolve expiry: explicit date preferred; duration is fallback/cross-check ---
        var expiryDate: LocalDate? = explicitExpiry
        if (expiryDate == null && durationMonths != null && purchaseDate != null) {
            expiryDate = DateUtils.addMonths(purchaseDate, durationMonths)
        }
        if (expiryDate == null && durationMonths != null && warrantyStart != null) {
            expiryDate = DateUtils.addMonths(warrantyStart, durationMonths)
        }

        // Cross-check duration vs explicit expiry with tiered warnings: small drift is
        // acceptable, meaningful drift warns, major contradiction warns loudly.
        if (explicitExpiry != null && durationMonths != null && purchaseDate != null) {
            val implied = DateUtils.addMonths(purchaseDate, durationMonths)
            val diffDays = Math.abs(DateUtils.daysBetween(implied, explicitExpiry))
            when {
                diffDays > DURATION_MAJOR_DAYS -> warnings.add(
                    "Warranty duration (${WarrantyParser.describe(durationMonths)}) and expiry date " +
                        "(${explicitExpiry}) disagree strongly — the explicit date was kept. Please verify."
                )
                diffDays > DURATION_TOLERANCE_DAYS -> warnings.add(
                    "Warranty duration and expiry date differ by about $diffDays days — the explicit date was kept."
                )
            }
        }

        // --- Timeline validation: do not silently "fix" user data, but warn ---
        val effectiveStart = purchaseDate ?: warrantyStart
        if (explicitExpiry != null && warrantyStart != null && startHit != null && explicitExpiry.isBefore(warrantyStart)) {
            warnings.add("Warranty expiry is before the warranty start date — please correct the dates.")
        }
        if (explicitExpiry != null && effectiveStart != null && explicitExpiry.isBefore(effectiveStart)) {
            warnings.add("Warranty expiry is before the purchase date — please correct the dates.")
            // An expiry before purchase cannot be right; keep it visible via warning but don't
            // use it to compute warranty status silently.
            expiryDate = null
        }
        if (warrantyStart != null && purchaseDate != null && startHit != null && warrantyStart.isBefore(purchaseDate)) {
            warnings.add("Warranty start is before the purchase date — please verify.")
        }
        if (expiryDate == null) warnings.add("No warranty end date detected — coverage end is unknown.")

        // Sanity: purchase date in the future is suspicious.
        if (purchaseDate != null && purchaseDate.isAfter(today.plusDays(7))) {
            warnings.add("Purchase date appears to be in the future — please verify.")
        }

        // --- Provider / type ---
        val providerHit = extractWarrantyProvider(lines)

        return OcrResult(
            rawText = rawText,
            productName = OcrFieldValue(productName, productConfidence, productSource),
            brand = OcrFieldValue(brandHit, 0.7f, brandHit?.let { "dictionary" }),
            model = OcrFieldValue(modelHit?.first, 0.8f, modelHit?.second),
            serialNumber = OcrFieldValue(serialHit?.first, 0.9f, serialHit?.second),
            imei = OcrFieldValue(imeiValue, 0.9f, imeiHit?.second),
            purchaseStore = OcrFieldValue(merchant, if (merchantHit != null) 0.85f else 0.3f, merchantHit?.second),
            purchasePrice = OcrFieldValue(priceHit?.amount, if (priceHit != null) 0.8f else 0f, priceHit?.source),
            currency = OcrFieldValue(priceHit?.currency ?: currencyHit, 0.7f, null),
            purchaseDate = OcrFieldValue(purchaseDate?.toString(), purchaseHitFinal?.confidence ?: 0f, purchaseHitFinal?.source),
            warrantyStartDate = OcrFieldValue(warrantyStart?.toString(), startHit?.confidence ?: 0.3f, startHit?.source),
            warrantyExpiryDate = OcrFieldValue(expiryDate?.toString(), if (explicitExpiry != null) 0.9f else 0.5f, expiryHit?.source ?: durationHit?.source),
            warrantyPeriodMonths = OcrFieldValue(durationMonths, if (durationHit != null) 0.85f else 0f, durationHit?.source),
            warrantyProvider = OcrFieldValue(providerHit?.first, 0.6f, providerHit?.second),
            warrantyType = OcrFieldValue(null, 0f, null),
            warnings = warnings
        )
    }

    /** IMEI shape check: 14-16 digits; Luhn checksum reported separately via [imeiChecksumValid]. */
    fun isPlausibleImei(value: String): Boolean {
        val digits = value.filter { it.isDigit() }
        return digits.length in 14..16
    }

    /** Luhn checksum validity for 15-digit IMEIs; always true for other lengths. */
    fun imeiChecksumValid(value: String): Boolean {
        val digits = value.filter { it.isDigit() }
        if (digits.length != 15) return true
        return luhnValid(digits)
    }

    private fun luhnValid(digits: String): Boolean {
        var sum = 0
        var double = false
        for (i in digits.length - 1 downTo 0) {
            var d = digits[i] - '0'
            if (double) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
            double = !double
        }
        return sum % 10 == 0
    }

    private fun countPriceCandidates(lines: List<String>): Int {
        var count = 0
        for (line in lines) {
            if (line.lowercase().contains(Regex("(?i)\\b(imei|serial|invoice|order|sku)\\b"))) continue
            if (PriceParser.lineLabel(line) != null) count++
        }
        return count
    }

    private fun extractWarrantyProvider(lines: List<String>): Pair<String, String>? {
        val rx = Regex("(?i)\\b(warranty\\s*(provider|by)|provided by|coverage by|care\\+?|applecare|extended warranty)\\s*[:\\-]?\\s*([A-Za-z0-9 .&+]{2,40})")
        for (line in lines) {
            rx.find(line)?.let {
                val v = it.groupValues[3].trim()
                if (v.length >= 2) return v to line.trim()
            }
        }
        return null
    }
}
