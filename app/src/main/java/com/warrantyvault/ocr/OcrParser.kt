package com.warrantyvault.ocr

import java.time.LocalDate

/**
 * Deterministic receipt parser: raw OCR text -> structured [OcrResult].
 * Knows nothing about Room, Compose, or Product creation.
 */
object OcrParser {

    fun parse(rawText: String, today: LocalDate = LocalDate.now()): OcrResult {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val warnings = mutableListOf<String>()

        // --- Store ---
        val merchantHit = IdentifierParser.extractMerchant(lines)
        val merchant = merchantHit?.first

        // --- Brand (never the merchant) ---
        val brandHit = IdentifierParser.extractBrand(lines, merchant)

        // --- Model / product identity ---
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

        // A brand+model combo beats a bare candidate.
        val productName: String? = when {
            productLine == null -> null
            brandHit != null && productLineHit != null &&
                IdentifierParser.brandNearModel(brandHit, productLineHit.second) &&
                !productLine.lowercase().contains(productLineHit.first.lowercase()) ->
                "${brandHit} $productLine"
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

        // --- Price ---
        val priceHit = PriceParser.extractPrice(lines)
        val currencyHit = PriceParser.detectCurrency(lines)
        val priceCount = countPriceCandidates(lines)
        if (priceCount > 1) warnings.add("Multiple price-like amounts detected — please verify the purchase price.")
        if (priceHit == null) warnings.add("No purchase price detected.")

        // --- Dates ---
        // Context-aware: labelled purchase/start/expiry dates win. A bare date is used as the
        // purchase date ONLY when it is the only date on the receipt and no expiry label exists —
        // never "first date = purchase, second date = expiry".
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

        // Cross-check duration vs explicit expiry (absolute difference in days).
        if (explicitExpiry != null && durationMonths != null && purchaseDate != null) {
            val implied = DateUtils.addMonths(purchaseDate, durationMonths)
            if (Math.abs(DateUtils.daysBetween(implied, explicitExpiry)) > 45) {
                warnings.add(
                    "Warranty duration (${WarrantyParser.describe(durationMonths)}) and expiry date " +
                        "(${explicitExpiry}) disagree — the explicit date was kept."
                )
            }
        }

        // Invalid: expiry before purchase.
        val effectiveStart = purchaseDate ?: warrantyStart
        if (explicitExpiry != null && effectiveStart != null && explicitExpiry.isBefore(effectiveStart)) {
            warnings.add("Warranty expiry is before the purchase date — please correct the dates.")
            expiryDate = null
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
            imei = OcrFieldValue(imeiHit?.first, 0.9f, imeiHit?.second),
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
