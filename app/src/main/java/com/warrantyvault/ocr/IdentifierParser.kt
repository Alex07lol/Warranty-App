package com.warrantyvault.ocr

import java.util.Locale

/**
 * Serial number, IMEI, model, brand and merchant extraction. Label-driven first, dictionary
 * assisted second, with strict exclusion rules so identifiers don't masquerade as other fields.
 */
object IdentifierParser {

    // ---------- Serial / IMEI ----------

    private val SERIAL_LABEL = Regex(
        // The value may break at a look-alike symbol (₹/ß for B, ? for 7): allow one break
        // and one more alnum run so "NP3ß25082?6" is captured whole instead of truncated.
        // The value may NOT start with the label's own tail ("NUMBER"): a bare "SERIAL
        // NUMBER" line must yield no match, so position-based recovery can take over.
        "(?i)\\b(s/n|s\\.n|sn|serial\\s*(number|no|#)?|service\\s*tag|asset\\s*tag)\\s*[:#=-]\\s*" +
            "([A-Za-z0-9][A-Za-z0-9\\-/?₹ß]{3,31}(?:[^A-Za-z0-9\\s][A-Za-z0-9\\-/?₹ß]{1,31})?)"
    )
    private val IMEI_LABEL = Regex(
        "(?i)\\b(imei\\d?|imei\\s*1|imei\\s*2)\\s*[:#=-]?\\s*(\\d{14,16})"
    )
    private val IMEI_BARE = Regex("\\b\\d{15}\\b")

    fun extractSerial(lines: List<String>): Pair<String, String>? {
        for (line in lines) {
            val m = SERIAL_LABEL.find(line) ?: continue
            var value = m.groupValues[3].trim().trim(',', ';')
            if (isJunkIdentifier(value)) continue
            if (!looksLikeIdentifierValue(value)) continue
            value = repairIdentifierLookalikes(value)
            return value to line.trim()
        }
        return null
    }

    /**
     * Repairs handwriting-OCR look-alikes inside identifiers: ₹/ß/¥ become B, ? becomes 7,
     * superscript digits become their ASCII digit. Lowercase letters are preserved — serials
     * and models are case-sensitive.
     */
    fun repairIdentifierLookalikes(value: String): String = value.map { c ->
        when (c) {
            '₹', '¥', 'ß' -> 'B'
            '?' -> '7'
            '²' -> '2'
            '³' -> '3'
            '¹' -> '1'
            else -> c
        }
    }.joinToString("")

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
        val junk = listOf(
            "imei", "serial", "invoice", "order", "sku", "model", "phone", "date", "total", "price",
            // Words that are part of the *label* itself, not a value. Without these, a line
            // whose value block did not line up ("SERIAL NUMBER" on its own) yields the
            // serial "NUMBER".
            "number", "name", "type", "colour", "color", "brand", "period"
        )
        return junk.any { lower.startsWith(it) }
    }

    /**
     * A real identifier value: compact (OCR may not split it) and containing a digit. This is
     * what keeps a stray label word ("NUMBER") from being stored as a serial, while still
     * accepting all-digit serials.
     */
    private fun looksLikeIdentifierValue(v: String): Boolean =
        v.length in 5..32 && !v.contains(' ') && v.any { it.isDigit() }

    // ---------- Model ----------

    private val MODEL_LABEL = Regex(
        "(?i)\\b(?:model\\s*(?:no|number|#)?|m/n|m#|part\\s*(?:number|no|#)?|product\\s*code|item\\s*(?:no|number|#)?|sku)\\s*[:#=-]\\s*" +
            "([A-Za-z0-9][A-Za-z0-9\\-\\./_ ]{1,39})"
    )
    /** Verb leads that mark a captured "model" as alignment garbage ("call 1800 572 6005"). */
    private val PHONE_VERB_LEADS = setOf("call", "tel", "phone", "visit", "toll", "dial")

    // "Product Name"/"Item"/"Description" only. "Product Number/Code/ID/Type" is an
    // identifier or a category, never the product name.
    private val PRODUCT_LABEL = Regex(
        "(?i)\\b(product\\s*name|product(?!\\s*(?:no|number|code|id|#|type))|item\\s*name|item|description)\\s*[:=]?\\s*(.{3,60})"
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

    /** Value-shaped model token, for position-based recovery. */
    fun looksLikeModelValue(t: String): Boolean {
        if (t.length !in 3..24) return false
        if (!t.any { it.isDigit() }) return false
        if (!t.any { it.isLetter() }) return false
        if (t.contains(' ') && !t.contains('-')) return false // "call 1800 572 6005"
        if (t.count { it == '/' } >= 2) return false // "LR/25-2C/fl8942" is an invoice number
        // A 9+ char unseparated run with a letter/digit mix is a serial, not a model —
        // the model must not steal the serial's token when both are recovered by position.
        if (t.length >= 9 && !t.contains(' ') && !t.contains('-') &&
            t.any { it.isDigit() } && t.any { it.isLetter() }
        ) return false
        if (t.any { it !in "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-./_# " }) return false
        return true
    }

    /** Value-shaped serial token, for position-based recovery. */
    fun looksLikeSerialValue(t: String): Boolean {
        if (t.length !in 6..24) return false
        if (!t.any { it.isDigit() } || !t.any { it.isLetter() }) return false
        if (t.contains(' ')) return false
        if (t.any { it !in "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-./_#?₹ß" }) return false
        // A date-shaped token is never a serial.
        if (Regex("^\\d{1,2}\\s*[./-]").containsMatchIn(t)) return false
        // Slash-carrying tokens ("AMZ/2025/0717/45823", "501/NP") are invoice/document
        // numbers or QR noise, never serials.
        if ('/' in t) return false
        return true
    }

    fun extractModel(lines: List<String>): Pair<String, String>? {
        for (line in lines) {
            val m = MODEL_LABEL.find(line) ?: continue
            // Group 1 is the model value (the label alternatives are non-capturing).
            val v = m.groupValues[1].trim().trim(',', ';')
            if (v.isEmpty() || isJunkIdentifier(v)) continue
            // Models carry digits ("15s-eq2143AU", "Airdopes 141"); a digitless capture is
            // the label's own second word ("MODEL NUMBER" alone → "NUMBER").
            if (!v.any { it.isDigit() }) continue
            // A phone/support number captured as the model value ("call 1800 572 6005") is
            // mis-alignment garbage, not a model: 3+ space-separated groups or a verb-first
            // line is never a model code, while genuine two-word models ("Airdopes 141") pass.
            val words = v.split(Regex("\\s+"))
            if (words.size >= 3 && !v.contains('-')) continue
            if (words.first().lowercase(Locale.US) in PHONE_VERB_LEADS) continue
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

    /** A plausible product-name value: digit or model-ish ("ColorFi+ pulse"), not logo noise. */
    fun looksLikeProductName(v: String): Boolean {
        if (v.contains('+') || v.any { it.isDigit() }) return true
        if (Regex("[^A-Za-z0-9+ .'-]").containsMatchIn(v)) return false // "Q) nase" — punctuation noise
        // Slogans and taglines are not product names ("Listen to more", "Good things").
        if (Regex("(?i)^(listen|good|thank|keep|more|stronger|plug|sound|inventing|reliable|innovative)").containsMatchIn(v)) return false
        val words = v.split(Regex("\\s+"))
        if (words.size >= 2 && words.any { it[0].isUpperCase() } && v.length in 9..48) return true
        return false
    }

    fun extractLabelledProduct(lines: List<String>): Pair<String, String>? {
        for (line in lines) {
            val m = PRODUCT_LABEL.find(line) ?: continue
            val v = m.groupValues[2].trim()
            if (v.length < 3) continue
            // Sentence fragment from a mis-aligned value column ("Against manufac+uring") is
            // not a product name: it reads as prose, not as a name-ish token.
            if (Regex("(?i)^(against|under|are not|is not|the |this |and |or |of |for )").containsMatchIn(v)) continue
            // Logo garbage ("Q) nase", "nase"): the product name must carry a digit, a
            // capitalised word pair, or a known product-type word. "Noise ColorFi+ pulse"
            // qualifies; logo noise does not.
            if (!looksLikeProductName(v)) continue
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
            if (!looksLikeProductName(line.trim())) continue
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
        "bosch", "siemens", "electrolux", "miele", "fujifilm", "olympus", "dji", "instax",
        // Indian consumer brands that appear on warranty cards (boAt Airdopes, Boult, etc.).
        "boat", "boult", "ambrane", "portronics", "micromax", "lava", "infinix", "tecno",
        "redmi", "poco", "iqoo", "haier", "voltas", "godrej", "ifb", "tcl", "noise"
    )

    /** Brands whose display name differs from their dictionary key only by case. */
    private val BRAND_CANONICAL = mapOf(
        "boat" to "boAt", "hp" to "HP", "lg" to "LG", "jbl" to "JBL", "msi" to "MSI",
        "dji" to "DJI", "asus" to "ASUS", "oppo" to "OPPO", "tcl" to "TCL", "ifb" to "IFB",
        "noise" to "Noise"
    )

    /** Retailers — never treated as product brand. */
    val KNOWN_MERCHANTS = setOf(
        "amazon", "flipkart", "walmart", "best buy", "target", "costco", "ikea", "apple store",
        // Manufacturer support sites that appear as the "or visit" footer on warranty cards.
        "gonoise.com", "boat-lifestyle.com", "asus.com", "hp.com",
        "samsung store", "micro center", "b&h", "home depot", "lowes", "lowe's", "staples",
        "office depot", "ebay", "sears", "reliance digital", "croma", "vijay sales", "poorvika",
        "sangeetha", "lot", "trader joes", "aldi", "lidl", "kroger", "safeway", "publix",
        "walgreens", "cvs", "rite aid", "dell direct", "jbl store", "myntra", "ajio", "tata cliq",
        "mahindra", "paytm", "snapdeal", "shopclues", "decathlon", "dunelm", "argos", "currys",
        "john lewis", "media markt", "saturn", "elgiganten", "elkjop", "power", "gigantti"
    )

    /**
     * Explicit seller label. Anchored to the start of the line so boilerplate that merely
     * mentions "authorised dealer" further down a terms paragraph is not mistaken for it.
     */
    private val MERCHANT_LABEL = Regex(
        "(?i)^[\\s\\d.\\-)\\'\"*]*(purchased\\s*from|sold\\s*by|sold\\s*at|bought\\s*from|retailer|" +
            "dealer|seller|vendor|store\\s*name|place\\s*of\\s*purchase)\\b\\s*[:\\-]?\\s*(.{2,60})$"
    )

    fun extractMerchant(lines: List<String>): Pair<String, String>? {
        // 1. Explicit label on the document ("PURCHASED FROM : Amazon.in") — most reliable.
        for (line in lines) {
            MERCHANT_LABEL.find(line.trim())?.let { m ->
                val v = m.groupValues[2].trim().trim('.', ',', ';').trim()
                if (v.length >= 2 && !v.all { it.isDigit() }) return v to line.trim()
            }
        }
        // 2. Dictionary match in the first 6 lines (receipts put the store at the top).
        for (line in lines.take(6)) {
            val lower = line.lowercase(Locale.US)
            for (m in KNOWN_MERCHANTS) {
                if (lower.contains(m)) {
                    return canonicalMerchant(m) to line.trim()
                }
            }
        }
        // 3. Legal-entity style names anywhere: "HP India Sales Pvt. Ltd.", "... Private Limited".
        val legalEntity = Regex(
            // The entity suffix must end the name ("... Pvt. Ltd.") — without the trailing
            // guard, the "Inc" of a banner like "IN SEARCH OF INCREDIBLE" matches and the
            // real vendor line further down is never reached.
            "(?i)^([A-Z][A-Za-z&'\\. ]{2,40})\\s+(Pvt\\.?\\s*Ltd|Private\\s+Limited|Ltd\\.?|Limited|LLC|Inc\\.?|Corp\\.?|GmbH|AG|BV|NV|Oy|AB|AS|Pty\\.?\\s*Ltd)(?![A-Za-z])"
        )
        for (line in lines) {
            legalEntity.find(line.trim())?.let { m ->
                return m.value.trim() to line.trim()
            }
        }
        // 4. Fallback: first substantial header line, rejecting OCR garbage ("nsi iS").
        for (line in lines.take(5)) {
            val t = line.trim()
            if (t.length !in 5..40) continue
            if (Regex("[€$₹£]|\\d{4}").containsMatchIn(t)) continue
            if (t.split(Regex("\\s+")).none { it.length >= 4 }) continue
            return t to line.trim()
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

    /**
     * Brand extraction in two passes.
     *
     * Pass 1 looks only at product-identity lines (labelled fields and known product-line
     * patterns) so the product's own brand beats a store or manufacturer address that happens
     * to appear earlier — e.g. an HP card whose header reads "HP India Sales Pvt. Ltd.".
     * Pass 2 scans anything else, still skipping the merchant's own line, so a store name never
     * becomes the product brand. Pass 3 infers from the recognised product line
     * ("Galaxy S24" → Samsung).
     */
    fun extractBrand(
        lines: List<String>,
        merchant: String?,
        productLine: String? = null,
        merchantLine: String? = null
    ): String? {
        for (line in lines) {
            if (merchantLine != null && line == merchantLine) continue
            if (!isIdentityLine(line)) continue
            brandIn(line)?.let { return it }
        }
        for (line in lines) {
            if (merchantLine != null && line == merchantLine) continue
            brandIn(line)?.let { return it }
        }
        if (productLine != null) return brandFromProductLine(productLine)
        return null
    }

    private fun isIdentityLine(line: String): Boolean =
        line.contains(':') || PRODUCT_LINE.any { it.containsMatchIn(line) }

    private fun brandIn(line: String): String? {
        val lower = line.lowercase(Locale.US)
        val hit = KNOWN_BRANDS.firstOrNull { lower.contains(it) } ?: return null
        return BRAND_CANONICAL[hit] ?: hit.replaceFirstChar { it.uppercase(Locale.US) }
    }

    /**
     * Builds a display name for a label-captured product line: prefix the brand when the
     * captured value does not already start with it ("ColorFit Pulse 3" + Noise). The raw
     * value is kept verbatim otherwise — no invented models or spellings.
     */
    fun withBrandPrefix(value: String?, brand: String?): String? {
        if (value == null) return null
        if (brand == null) return value
        return if (value.lowercase(Locale.US).contains(brand.lowercase(Locale.US))) value
        else "$brand $value"
    }

    fun brandNearModel(brand: String, modelLine: String): Boolean =
        modelLine.lowercase(Locale.US).contains(brand.lowercase(Locale.US))
}
