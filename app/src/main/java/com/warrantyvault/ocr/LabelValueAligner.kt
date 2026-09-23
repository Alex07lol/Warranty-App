package com.warrantyvault.ocr

import java.util.Locale

/**
 * Repairs a very common OCR reading order for two-column "label : value" cards.
 *
 * Text recognition frequently returns a block of labels followed by a block of values — it
 * reads column by column, or groups lines by similar geometry — for example:
 *
 *     SERIAL NUMBER          <- labels, one per line
 *     PURCHASE DATE
 *     VALID TILL
 *     TERMS & CONDITIONS
 *     N8NRCV012345678        <- values, in the same order
 *     05 SEP 2025
 *     04 SEP 2027
 *
 * Every downstream extractor expects "LABEL : value" on a single line, so without this step
 * the Serial Number, Purchase Date and Valid Till silently disappear from the parse (and the
 * document looks empty even though the OCR read it perfectly).
 *
 * Values are matched by *kind* — a date label can only take a date-shaped line — in document
 * order, and each candidate line is consumed once. That keeps "05 SEP 2025" with the purchase
 * date and "04 SEP 2027" with the expiry even though both labels precede both values.
 */
object LabelValueAligner {

    /** The kind of value a label expects. Same-kind labels are filled in document order. */
    enum class Kind { NAME, TYPE, COLOUR, MODEL, PRODUCT_CODE, SERIAL, IMEI, DATE, DURATION, INVOICE, MERCHANT }

    private data class Field(val index: Int, val kind: Kind)

    // Only identity/date/warranty labels are aligned. Money labels ("Total", "Amount") are
    // deliberately excluded: price extraction already handles a label followed by a value.

    private val LABEL_RULES: List<Pair<Regex, Kind>> = listOf(
        Regex("(?i)^PRODUCT\\s*NAME$") to Kind.NAME,
        Regex("(?i)^PRODUCT\\s*(TYPE|CATEGORY)$") to Kind.TYPE,
        Regex("(?i)^COLOU?R$") to Kind.COLOUR,
        Regex("(?i)^(MODEL|MODEL\\s*(NO|NUMBER|#)|M/N|M#)$") to Kind.MODEL,
        Regex("(?i)^(PRODUCT\\s*(NO|NUMBER|CODE|ID)|ITEM\\s*(NO|NUMBER|CODE)|SKU)$") to Kind.PRODUCT_CODE,
        Regex("(?i)^(SERIAL\\s*(NO|NUMBER|#)?|S/N|S\\.N|SN|SERVICE\\s*TAG|ASSET\\s*TAG)$") to Kind.SERIAL,
        Regex("(?i)^(IMEI\\d?|IMEI\\s*[12])$") to Kind.IMEI,
        Regex(
            "(?i)^(PURCHASE\\s*DATE|DATE\\s*OF\\s*PURCHASE|PURCHASED\\s*ON|INVOICE\\s*DATE|ORDER\\s*DATE|" +
                "SALE\\s*DATE|WARRANTY\\s*(START|START\\s*DATE|BEGINS|FROM)|COVERAGE\\s*START|" +
                "VALID\\s*(FROM|TILL|UNTIL|UPTO|UP\\s*TO|THRU|THROUGH)|" +
                "WARRANTY\\s*(EXPIRY|EXPIRATION|END|VALID\\s*(TILL|UNTIL|UPTO|UP\\s*TO))|" +
                "EXPIRY\\s*DATE|EXPIRATION\\s*DATE|EXP\\s*DATE|END\\s*DATE|COVERAGE\\s*(END|UNTIL|TILL))$"
        ) to Kind.DATE,
        Regex("(?i)^WARRANTY\\s*(PERIOD|DURATION|TERM)$") to Kind.DURATION,
        Regex("(?i)^(INVOICE\\s*(NO|NUMBER|#)|BILL\\s*(NO|NUMBER)|INVOICE)$") to Kind.INVOICE,
        Regex(
            "(?i)^(PURCHASED\\s*FROM|SOLD\\s*BY|SOLD\\s*AT|BOUGHT\\s*FROM|RETAILER|DEALER|SELLER|VENDOR|" +
                "STORE\\s*NAME|PLACE\\s*OF\\s*PURCHASE)$"
        ) to Kind.MERCHANT
    )

    private val SECTION_HEADER = Regex(
        "(?i)^(terms\\s*(&|and|\\+)\\s*conditions?|terms\\s*of\\s*(sale|service|warranty)|" +
            "warranty\\s*(card|certificate|policy|terms|registration)|customer\\s*copy|original\\s*copy|" +
            "important|notes?|conditions?|declaration|support|for\\s*(assistance|support))$"
    )

    private val DATE_SHAPED = Regex("\\d{1,4}\\s*[./-]\\s*\\d{1,2}\\s*[./-]\\s*\\d{1,4}")
    private val DURATION_SHAPED = Regex("(?i)\\b\\d{1,3}\\s*[- ]?(years?|yrs?|y|months?|mos?|mo\\.?)\\b")
    // Fuzz set: OCR regularly swaps handwritten characters for look-alike symbols (₹/ß for B,
    // ? for 7, + for t). A serial may contain a few of these and must still be captured so
    // the review screen can correct it.
    private val SERIAL_SHAPED = Regex("^[A-Za-z0-9][A-Za-z0-9\\-./_+?₹ß*#]{4,31}$")
    private val SERIAL_ISH = Regex("^[A-Za-z0-9]{9,}$")
    private val MODEL_SHAPED = Regex("^[A-Za-z0-9][A-Za-z0-9\\-./_ ]{1,39}$")
    private val IMEI_SHAPED = Regex("^\\d{14,16}$")
    private val INVOICE_SHAPED = Regex("^[A-Za-z0-9]{1,12}([/\\-][A-Za-z0-9]{1,12}){2,}$")
    private val DOMAIN_SHAPED = Regex("(?i)^[a-z0-9][a-z0-9\\-]*\\.(com|in|net|org|co|io|uk|de|sg|ae)(/.*)?$")
    private val URL_SHAPED = Regex("(?i)(https?://|www\\.)")
    private val LEGAL_ENTITY = Regex(
        "(?i)\\b(pvt\\.?\\s*ltd|private\\s+limited|ltd\\.?|limited|llc|inc\\.?|corp\\.?|gmbh|pty\\.?\\s*ltd)\\b"
    )

    private val DEVICE_WORDS = setOf(
        "laptop", "notebook", "earbud", "earbuds", "headphone", "headphones", "headset", "smartwatch",
        "watch", "tablet", "monitor", "printer", "camera", "speaker", "mouse", "keyboard", "router",
        "console", "gaming", "phone", "smartphone", "mobile", "tv", "television", "refrigerator",
        "fridge", "washing", "microwave", "dishwasher", "air conditioner", "airdopes", "trimmer",
        "powerbank", "power bank", "drone", "projector", "hard disk", "ssd", "charger"
    )
    private val COLOUR_WORDS = setOf(
        "black", "white", "silver", "grey", "gray", "blue", "red", "gold", "green", "pink", "purple",
        "graphite", "midnight", "starlight", "titanium", "beige", "bronze", "copper", "cream", "navy",
        "maroon", "yellow", "orange", "brown", "transparent", "clear", "rose", "sky", "space"
    )

    private const val LOOKAHEAD_LINES = 60

    /** Rewrites label-only lines into "LABEL : value" where a matching value can be found. */
    fun align(lines: List<String>): List<String> {
        if (lines.size < 2) return lines
        val fields = lines.mapIndexedNotNull { i, l -> labelKind(l)?.let { Field(i, it) } }
        if (fields.size < 2) return lines

        val labelIndices = fields.map { it.index }.toSet()
        val consumed = mutableSetOf<Int>()
        val pairs = mutableMapOf<Int, String>()

        // Document order matters: NAME claims the product line before MODEL looks for a model,
        // and earlier date labels claim earlier date values.
        for (field in fields) {
            val hit = findValue(lines, field, labelIndices, consumed) ?: continue
            consumed.add(hit.first)
            pairs[field.index] = hit.second
        }
        if (pairs.isEmpty()) return lines

        val out = ArrayList<String>(lines.size)
        for (i in lines.indices) {
            if (i in consumed) continue
            val value = pairs[i]
            if (value == null) {
                out.add(lines[i])
            } else {
                val label = lines[i].trim().trimEnd(':', '.', '#', ' ').trim()
                out.add("$label : $value")
            }
        }
        return out
    }

    /** The kind of value a label-only line expects, or null when the line is not a label. */
    fun labelKind(line: String): Kind? {
        val t = line.trim().trimEnd(':', '.', '#', ' ').trim()
        if (t.isEmpty() || t.length > 40) return null
        if (t.any { it.isDigit() }) return null
        return LABEL_RULES.firstOrNull { it.first.matches(t) }?.second
    }

    private fun findValue(
        lines: List<String>,
        field: Field,
        labelIndices: Set<Int>,
        consumed: Set<Int>
    ): Pair<Int, String>? {
        val end = minOf(lines.size, field.index + LOOKAHEAD_LINES)
        for (i in (field.index + 1) until end) {
            if (i in consumed || i in labelIndices) continue
            val candidate = lines[i].trim()
            if (candidate.isEmpty() || candidate.length > 80) continue
            if (isValueFor(field.kind, candidate)) return i to candidate
        }
        return null
    }

    /** Typed predicate: can this line be the value of that label? */
    fun isValueFor(kind: Kind, line: String): Boolean = when (kind) {
        Kind.NAME -> isNameValue(line)
        Kind.TYPE -> isTypeValue(line)
        Kind.COLOUR -> isColourValue(line)
        Kind.MODEL -> isModelValue(line)
        Kind.PRODUCT_CODE -> isProductCodeValue(line)
        Kind.SERIAL -> isSerialValue(line)
        Kind.IMEI -> isImeiValue(line)
        Kind.DATE -> isDateValue(line)
        Kind.DURATION -> isDurationValue(line)
        Kind.INVOICE -> isInvoiceValue(line)
        Kind.MERCHANT -> isMerchantValue(line)
    }

    fun isDateValue(line: String): Boolean {
        val t = line.trim()
        if (DATE_SHAPED.containsMatchIn(t)) return true
        if (fuzzyNumericDateGroups(t) != null) return true
        return DateParser.parseTextual(t) != null
    }

    private fun isNameValue(line: String): Boolean {
        val t = line.trim()
        if (t.length !in 5..60) return false
        if (t.contains(':') || t.contains('@')) return false
        if (URL_SHAPED.containsMatchIn(t)) return false
        if (SECTION_HEADER.matches(t)) return false
        if (isGreetingLine(t)) return false
        if (t == t.uppercase(Locale.US)) return false // all-caps banner, not a product name
        if (t.endsWith(".")) return false
        if (t.startsWith("+")) return false // OCR garble continuing a terms line ("+uring…")
        val words = t.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size < 2) return false
        if (!t.any { it.isLetter() }) return false
        return true
    }

    private fun isTypeValue(line: String): Boolean {
        val lower = line.trim().lowercase(Locale.US)
        return DEVICE_WORDS.any { lower.contains(it) }
    }

    private fun isColourValue(line: String): Boolean {
        val t = line.trim()
        if (t.length > 30) return false
        val lower = t.lowercase(Locale.US)
        return COLOUR_WORDS.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(lower) }
    }

    private fun isModelValue(line: String): Boolean {
        val t = line.trim()
        if (!MODEL_SHAPED.matches(t)) return false
        if (!t.any { it.isDigit() }) return false
        if (isDateValue(t) || isDurationValue(t)) return false
        if (fuzzyNumericDateGroups(t) != null) return false
        // A long unseparated alphanumeric run is a serial, not a model.
        if (SERIAL_ISH.matches(t) && t.any { it.isLetter() } && t.count { it.isDigit() } >= 3) return false
        return true
    }

    private fun isProductCodeValue(line: String): Boolean {
        val t = line.trim()
        if (t.length !in 3..40) return false
        return t.all { it.isLetterOrDigit() || it in "-./_# " }
    }

    private fun isSerialValue(line: String): Boolean {
        val t = line.trim()
        if (!SERIAL_SHAPED.matches(t)) return false
        if (!t.any { it.isDigit() }) return false
        // Symbol fuzz (₹, ?) substitutes letters, so an all-symbol run is not a serial.
        if (!t.any { it.isLetter() }) return false
        // Slash-separated runs ("AMZ/2025/0717/45823", QR noise "501/NP") are invoice or
        // document numbers, not serials — same rule as IdentifierParser.looksLikeSerialValue,
        // so both routes agree. A missing serial goes to review; a wrong one prints on a claim.
        if ('/' in t) return false
        return !isDateValue(t)
    }

    /**
     * Fuzzy date read: day/year runs stayed digits but the month was mangled ("og" for 08).
     * The shape must be intact — exactly three groups with digits in the day and year slots —
     * so ordinary text never matches. Returns the groups as written.
     */
    fun fuzzyNumericDateGroups(line: String): Triple<String, String, String>? {
        // Day/year slots stay digit-shaped but may carry look-alike symbols ("2?" for 27);
        // the month slot may be letters ("og" for 08).
        val m = Regex("^([0-9?]{1,2})\\s*[./-]\\s*([A-Za-z]{1,4}|[0-9?]{1,2})\\s*[./-]\\s*([0-9?]{2,4})$")
            .find(line.trim()) ?: return null
        val (d, mo, y) = m.destructured
        if (mo.length == 1 && mo[0].isLetter()) return null // "27 / a / 2025": too little signal
        if (d.count { it.isDigit() } < 1 || y.count { it.isDigit() } < 2) return null
        return Triple(d, mo, y)
    }

    private fun isImeiValue(line: String): Boolean = IMEI_SHAPED.matches(line.trim())

    private fun isDurationValue(line: String): Boolean {
        val t = line.trim()
        if (isDateValue(t)) return false
        return DURATION_SHAPED.containsMatchIn(t)
    }

    private fun isInvoiceValue(line: String): Boolean {
        val t = line.trim()
        if (isDateValue(t)) return false
        if (fuzzyNumericDateGroups(t) != null) return false
        return INVOICE_SHAPED.matches(t)
    }

    private fun isMerchantValue(line: String): Boolean {
        val t = line.trim()
        if (t.length !in 3..60) return false
        if (URL_SHAPED.containsMatchIn(t)) return false
        if (SECTION_HEADER.matches(t)) return false
        // Greeting/notes lines are common on greeting-style cards, never merchants.
        if (Regex("(?i)^(good|thank|thanks|keep|more|dear|hi|hello|happy)\\b").containsMatchIn(t)) return false
        val lower = t.lowercase(Locale.US)
        if (IdentifierParser.KNOWN_MERCHANTS.any { lower.contains(it) }) return true
        if (DOMAIN_SHAPED.matches(lower)) return true
        return LEGAL_ENTITY.containsMatchIn(t)
    }

    /** A "good ..." line: greeting text, never a product name. */
    private fun isGreetingLine(t: String): Boolean =
        Regex("(?i)^(good|thank|thanks|keep|more|happy|dear|hi|hello|welcome|congrat)\\b").containsMatchIn(t)
}
