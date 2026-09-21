package com.warrantyvault.ocr

import java.util.Locale

/**
 * Serial number, IMEI, model, brand and merchant extraction. Label-driven first, dictionary
 * assisted second, with strict exclusion rules so identifiers don't masquerade as other fields.
 */
object IdentifierParser {

    // ---------- Serial / IMEI ----------

    private val SERIAL_LABEL = Regex(
        "(?i)\\b(s/n|s\\.n|sn|serial\\s*(number|no|#)?|service\\s*tag|asset\\s*tag)\\s*[:#=-]?\\s*([A-Za-z0-9][A-Za-z0-9\\-/]{3,31})"
    )
    private val IMEI_LABEL = Regex(
        "(?i)\\b(imei\\d?|imei\\s*1|imei\\s*2)\\s*[:#=-]?\\s*(\\d{14,16})"
    )
    private val IMEI_BARE = Regex("\\b\\d{15}\\b")

    fun extractSerial(lines: List<String>): Pair<String, String>? {
        for (line in lines) {
            val m = SERIAL_LABEL.find(line) ?: continue
            val value = m.groupValues[3].trim().trim(',', ';')
            if (isJunkIdentifier(value)) continue
            return value to line.trim()
        }
        return null
    }

    fun extractImei(lines: List<String>): Pair<String, String>? {
        for (line in lines) {
            IMEI_LABEL.find(line)?.let { m ->
                return m.groupValues[2] to line.trim()
            }
        }
        // A bare 15-digit run only counts when IMEI was mentioned somewhere on the page.
        val imeiMentioned = lines.any { Regex("(?i)\\bimei\\b").containsMatchIn(it) }
        if (imeiMentioned) {
            for (line in lines) {
                IMEI_BARE.find(line)?.let { return it.value to line.trim() }
            }
        }
        return null
    }

    private fun isJunkIdentifier(v: String): Boolean {
        if (v.length < 4) return true
        val lower = v.lowercase(Locale.US)
        val junk = listOf("imei", "serial", "invoice", "order", "sku", "model", "phone", "date", "total", "price")
        return junk.any { lower.startsWith(it) }
    }

    // ---------- Model ----------

    private val MODEL_LABEL = Regex(
        "(?i)\\b(model\\s*(no|number|#)?|m/n|m#|part\\s*(number|no|#)?|product\\s*code|item\\s*(no|number|#)?|sku)\\s*[:#=-]?\\s*([A-Za-z0-9][A-Za-z0-9\\-\\./_ ]{1,39})"
    )
    private val PRODUCT_LABEL = Regex(
        "(?i)\\b(product(?:\\s*name)?|item|description)\\s*[:=]?\\s*(.{3,60})"
    )

    // Strong product-line patterns (brand + series) that beat generic candidates.
    private val PRODUCT_LINE = listOf(
        Regex("(?i)(galaxy\\s*[sAnM]\\d{1,3}\\s*(ultra|\\+)?)"),
        Regex("(?i)(iphone\\s*\\d{1,2}\\s*(pro\\s*max|pro|plus|max)?)"),
        Regex("(?i)(macbook\\s*(air|pro)\\s*(\\d{1,2})?)"),
        Regex("(?i)(thinkpad\\s*\\w{1,4}\\d{1,3})"),
        Regex("(?i)(wh-?1000x\\w{1,3})"),
        Regex("(?i)(pix\\w{0,2}\\s*\\d[a-z]\\d{1,2})"),
        Regex("(?i)(surface\\s*(pro|laptop|go)\\s*\\d?)"),
        Regex("(?i)(airpods\\s*(pro|max)?)")
    )

    /** Product line → manufacturing brand, so "Galaxy S24 Ultra" alone still yields Samsung. */
    private val PRODUCT_LINE_BRAND = listOf(
        Regex("(?i)galaxy") to "Samsung",
        Regex("(?i)iphone|macbook|airpods|ipad") to "Apple",
        Regex("(?i)thinkpad|ideapad|legion|yoga") to "Lenovo",
        Regex("(?i)wh-?1000x|wf-?1000x|bravia|playstation") to "Sony",
        Regex("(?i)surface|xbox") to "Microsoft",
        Regex("(?i)pixel|nest") to "Google",
        Regex("(?i)latitude|inspiron|xps|alienware") to "Dell",
        Regex("(?i)pavilion|envy|elitebook|spectre|probook") to "HP",
        Regex("(?i)rog|vivobook|zenbook") to "Asus",
        Regex("(?i)predator|nitro|aspire|swift") to "Acer"
    )

    /** Infers the brand from a recognized product-line string ("Galaxy S24 Ultra" → Samsung). */
    fun brandFromProductLine(productLine: String): String? {
        for ((rx, brand) in PRODUCT_LINE_BRAND) {
            if (rx.containsMatchIn(productLine)) return brand
        }
        return null
    }

    fun extractModel(lines: List<String>): Pair<String, String>? {
        for (line in lines) {
            val m = MODEL_LABEL.find(line) ?: continue
            // Group 5 is the model value (group 4 is an inner label alternative).
            val v = m.groupValues[5].trim().trim(',', ';')
            if (v.isEmpty() || isJunkIdentifier(v)) continue
            return v to line.trim()
        }
        return null
    }

    fun extractProductLine(lines: List<String>): Pair<String, String>? {
        for (line in lines) {
            for (rx in PRODUCT_LINE) {
                rx.find(line)?.let { return it.value.trim() to line.trim() }
            }
        }
        return null
    }

    fun extractLabelledProduct(lines: List<String>): Pair<String, String>? {
        for (line in lines) {
            val m = PRODUCT_LABEL.find(line) ?: continue
            val v = m.groupValues[2].trim()
            if (v.length < 3) continue
            return v to line.trim()
        }
        return null
    }

    /** Fallback: a plausible product-name line (title-cased, not a label/metadata line). */
    fun bestProductCandidate(lines: List<String>, merchant: String?): String? {
        val noise = listOf(
            "invoice", "receipt", "tax", "total", "subtotal", "amount", "paid", "date", "cashier",
            "store", "order", "customer", "thank", "warranty", "return", "policy", "payment",
            "card", "visa", "mastercard", "change", "item", "qty", "price", "unit", "sale",
            "gst", "vat", "shipping", "billing", "purchase", "balance", "due", "tender",
            "transaction", "auth", "approval", "terminal", "coupon", "discount", "savings"
        )
        for (line in lines.take(12)) {
            val l = line.lowercase(Locale.US)
            if (line.length !in 4..60) continue
            if (Regex("\\d").containsMatchIn(line)) continue
            if (noise.any { l.contains(it) }) continue
            if (merchant != null && l.contains(merchant.lowercase(Locale.US))) continue
            if (l == merchant?.lowercase(Locale.US)) continue
            return line.trim()
        }
        return null
    }

    // ---------- Brands & merchants ----------

    val KNOWN_BRANDS = setOf(
        "apple", "samsung", "sony", "lg", "dell", "hp", "lenovo", "asus", "acer", "msi",
        "oneplus", "xiaomi", "google", "realme", "motorola", "moto", "bose", "jbl", "logitech",
        "canon", "nikon", "philips", "dyson", "microsoft", "panasonic", "ninja", "kitchenaid",
        "corsair", "razer", "sennheiser", "garmin", "fitbit", "gopro", "sonos", "anker",
        "belkin", "honor", "vivo", "oppo", "nothing", "bfrigidaire", "whirlpool", "maytag",
        "bosch", "siemens", "electrolux", "miele", "fujifilm", "olympus", "dji", "instax"
    )

    /** Retailers — never treated as product brand. */
    val KNOWN_MERCHANTS = setOf(
        "amazon", "flipkart", "walmart", "best buy", "target", "costco", "ikea", "apple store",
        "samsung store", "micro center", "b&h", "home depot", "lowes", "lowe's", "staples",
        "office depot", "ebay", "sears", "reliance digital", "croma", "vijay sales", "poorvika",
        "sangeetha", "lot", "trader joes", "aldi", "lidl", "kroger", "safeway", "publix",
        "walgreens", "cvs", "rite aid", "dell direct", "jbl store", "myntra", "ajio", "tata cliq",
        "mahindra", "paytm", "snapdeal", "shopclues", "decathlon", "dunelm", "argos", "currys",
        "john lewis", "media markt", "saturn", "elgiganten", "elkjop", "power", "gigantti"
    )

    fun extractMerchant(lines: List<String>): Pair<String, String>? {
        // Dictionary match anywhere in the first 6 lines wins (receipts put store at top).
        for (line in lines.take(6)) {
            val lower = line.lowercase(Locale.US)
            for (m in KNOWN_MERCHANTS) {
                if (lower.contains(m)) {
                    return canonicalMerchant(m) to line.trim()
                }
            }
        }
        // Legal-entity style names: "Amazon Seller Services Pvt Ltd", "ACME Electronics GmbH".
        val legalEntity = Regex(
            "(?i)^([A-Z][A-Za-z&'\\. ]{2,40})\\s+(Pvt\\.?\\s*Ltd|Private\\s+Limited|Ltd\\.?|LLC|Inc\\.?|Corp\\.?|GmbH|AG|BV|NV|Oy|AB|AS|Pty\\.?\\s*Ltd)"
        )
        for (line in lines.take(8)) {
            legalEntity.find(line.trim())?.let { m ->
                return m.value.trim() to line.trim()
            }
        }
        // Fallback: first substantial header line.
        for (line in lines.take(5)) {
            val t = line.trim()
            if (t.length in 3..40 && !Regex("[€$₹£]|\\d{4}").containsMatchIn(t)) {
                return t to line.trim()
            }
        }
        return null
    }

    fun canonicalMerchant(matched: String): String = when (matched) {
        "lowes", "lowe's" -> "Lowe's"
        "apple store" -> "Apple Store"
        "samsung store" -> "Samsung Store"
        else -> matched.split(" ").joinToString(" ") { w ->
            w.replaceFirstChar { it.uppercase(Locale.US) }
        }
    }

    fun extractBrand(lines: List<String>, merchant: String?, productLine: String? = null): String? {
        for (line in lines) {
            val lower = line.lowercase(Locale.US)
            for (b in KNOWN_BRANDS) {
                if (lower.contains(b) && merchant?.lowercase(Locale.US)?.contains(b) != true) {
                    return b.replaceFirstChar { it.uppercase(Locale.US) }
                }
            }
        }
        // No dictionary hit: infer from a recognized product line ("Galaxy S24" → Samsung).
        if (productLine != null) {
            val inferred = brandFromProductLine(productLine)
            if (inferred != null) return inferred
        }
        return null
    }

    fun brandNearModel(brand: String, modelLine: String): Boolean =
        modelLine.lowercase(Locale.US).contains(brand.lowercase(Locale.US))
}
