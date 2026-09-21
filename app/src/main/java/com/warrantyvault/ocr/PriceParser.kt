package com.warrantyvault.ocr

import java.util.Locale

/**
 * Parses money amounts from receipt text. Handles international formats (decimal comma vs
 * decimal point), currency symbols and codes, and thousands separators. Crucially, it never
 * mistakes long digit runs (IMEI, serial, invoice numbers), dates, phone numbers, or model
 * numbers for money.
 */
object PriceParser {

    private val CURRENCY_DECIMALS = mapOf(
        "JPY" to 0, "KRW" to 0, "VND" to 0
    )

    private val SYMBOL_TO_CODE = mapOf(
        "$" to "USD", "US$" to "USD", "USD" to "USD",
        "€" to "EUR", "EUR" to "EUR",
        "£" to "GBP", "GBP" to "GBP",
        "₹" to "INR", "Rs." to "INR", "Rs" to "INR", "INR" to "INR",
        "A$" to "AUD", "AUD" to "AUD",
        "C$" to "CAD", "CAD" to "CAD",
        "¥" to "JPY", "JPY" to "JPY",
        "CHF" to "CHF", "Fr." to "CHF",
        "SEK" to "SEK", "kr" to "SEK",
        "NOK" to "NOK", "DKK" to "DKK",
        "PLN" to "PLN", "zł" to "PLN",
        "R" to "ZAR", "ZAR" to "ZAR",
        "AED" to "AED", "د.إ" to "AED",
        "S$" to "SGD", "SGD" to "SGD"
    )

    // Long numeric identifiers that must never be read as money: IMEI (15), EAN (8/13),
    // invoice/order numbers, phone numbers. "date" and "model" lines are filtered by
    // isDateLine/isModelLine below.
    private val IDENTIFIER_LINE = Regex(
        "(?i)\\b(imei\\d?|serial|s/n|s\\.n|invoice|order|tracking|product\\s*code|sku|phone|tel)\\b"
    )

    /** Any line carrying a date label is never a price line. */
    private val DATE_LINE = Regex(
        "(?i)\\b(date|purchased|invoice\\s+date|warranty|valid|expires?|coverage|start(ed)?|bought)\\b"
    )

    /** Model/serial-looking alphanumeric tokens on a line disqualify it as a price line. */
    private val MODEL_LINE = Regex("(?i)\\b(model|m/n|part|p/n)\\b")

    /** A money-shaped token: optional symbol/code, digits with separators, optional decimals. */
    private val MONEY_TOKEN = Regex(
        "(?i)(USD|EUR|GBP|INR|AUD|CAD|JPY|CHF|SEK|NOK|DKK|PLN|ZAR|AED|SGD|US\\$|A\\$|C\\$|S\\$|Rs\\.?|[$€£₹¥]|zł|Fr\\.)?\\s?" +
            "(\\d{1,3}(?:[ .,]\\d{3})*(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)"
    )

    /** Currency code found anywhere on the line (e.g. "49,999.00 INR"). */
    private val TRAILING_CODE = Regex(
        "(?i)\\b(USD|EUR|GBP|INR|AUD|CAD|JPY|CHF|SEK|NOK|DKK|PLN|ZAR|AED|SGD)\\b"
    )

    data class PriceMatch(
        val amount: Double,
        val currency: String?,
        val label: String?,
        val source: String
    )

    /**
     * Label ranking, best first. A "Grand Total" always beats a bare "Total"; the position
     * in this list is the confidence tier.
     */
    private val LABEL_PRIORITY = listOf(
        "grand total", "net total", "order total", "total amount", "amount paid",
        "total paid", "final amount", "total due", "paid", "total",
        "purchase price", "price", "amount", "sum"
    )

    fun lineLabel(line: String): String? {
        val lower = line.lowercase(Locale.US)
        return LABEL_PRIORITY.firstOrNull { lower.contains(it) }
    }

    private fun looksLikeIdentifier(line: String): Boolean =
        IDENTIFIER_LINE.containsMatchIn(line)

    private fun looksLikeDateLine(line: String): Boolean = DATE_LINE.containsMatchIn(line)

    private fun looksLikeModelLine(line: String): Boolean = MODEL_LINE.containsMatchIn(line)

    /** A date-shaped token like 21/09/2026, 2026-09-21, 21.09.26. */
    private fun isDateShaped(token: String): Boolean =
        Regex("^\\d{1,4}[./-]\\d{1,2}[./-]\\d{1,4}$").matches(token)

    private fun phoneShaped(line: String, token: String): Boolean {
        if (Regex("(?i)\\b(phone|tel|mob|call)\\b").containsMatchIn(line)) return true
        return Regex("\\d{3}[- ]\\d{3}[- ]\\d{4}").matches(token.replace(" ", ""))
    }

    /**
     * Normalizes a money token to a double. Handles "1.299,99" (EUR style), "1,299.99" (US style),
     * "49,999" (Indian grouping), "129999", "999.99".
     */
    fun parseAmount(raw: String): Double? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')
        val normalized: String = when {
            // Both separators: the rightmost one is the decimal separator.
            lastComma >= 0 && lastDot >= 0 ->
                if (lastDot > lastComma) s.replace(",", "")   // 1,299.99
                else s.replace(".", "").replace(',', '.')     // 1.299,99
            // Comma only: thousands grouping (49,999 / 1,234,567) vs decimal comma (999,99).
            lastComma >= 0 -> {
                val parts = s.split(",")
                val last = parts.last()
                when {
                    parts.size > 2 || last.length == 3 -> s.replace(",", "") // grouping
                    last.length <= 2 -> s.replace(',', '.')                  // decimal
                    else -> s.replace(",", "")                               // ambiguous → grouping
                }
            }
            else -> s
        }
        return normalized.toDoubleOrNull()
    }

    fun decimalsFor(currencyCode: String?): Int = CURRENCY_DECIMALS[currencyCode?.uppercase(Locale.US)] ?: 2

    /**
     * Extracts the best price from receipt lines using a strict ranking:
     * 1. Label tier (Grand Total beats Total beats Price) among labelled candidates.
     * 2. Currency-carrying candidates over bare numbers.
     * 3. Largest plausible amount as the last tiebreaker.
     *
     * Date lines, identifier lines, and model lines are skipped entirely, so
     * "Purchase Date: 21/09/2026" can never yield 21, 09, or 2026 as money.
     */
    fun extractPrice(lines: List<String>): PriceMatch? {
        val candidates = mutableListOf<PriceMatch>()

        for ((index, line) in lines.withIndex()) {
            if (looksLikeIdentifier(line)) continue
            if (looksLikeDateLine(line)) continue
            if (looksLikeModelLine(line)) continue
            val label = lineLabel(line)
            // Look on this line and, for labels, also the next line ("Total\n₹129,999").
            // The next line must not itself be a date/identifier line.
            val next = if (label != null && index + 1 < lines.size) lines[index + 1] else null
            val scopes = buildList {
                add(line)
                if (next != null && !looksLikeIdentifier(next) && !looksLikeDateLine(next) && !looksLikeModelLine(next)) {
                    add(next)
                }
            }
            for (scope in scopes) {
                if (isDateShaped(scope.trim())) continue
                for (m in MONEY_TOKEN.findAll(scope)) {
                    val symbol = m.groupValues[1].trim()
                    val token = m.groupValues[2]
                    if (isDateShaped(token)) continue
                    if (phoneShaped(scope, token)) continue
                    val amount = parseAmount(token) ?: continue
                    if (amount <= 0) continue
                    // Refuse absurd amounts that are far more likely identifiers/typos (> 100M).
                    if (amount > 100_000_000) continue
                    val currency = resolveCurrency(symbol, scope)
                    candidates.add(
                        PriceMatch(
                            amount = amount,
                            currency = currency,
                            label = label,
                            source = scope.trim()
                        )
                    )
                }
            }
        }

        if (candidates.isEmpty()) return null

        // 1) Best label tier wins.
        val labelled = candidates.filter { it.label != null }
        if (labelled.isNotEmpty()) {
            val bestLabel = LABEL_PRIORITY.firstOrNull { l -> labelled.any { it.label == l } }
            val pool = labelled.filter { it.label == bestLabel }
            return pool.maxByOrNull { it.amount }
        }

        // 2) Unlabelled: currency-carrying candidate first, then largest amount.
        return candidates.sortedWith(
            compareByDescending<PriceMatch> { it.currency != null }.thenByDescending { it.amount }
        ).first()
    }

    private fun resolveCurrency(symbol: String, line: String): String? {
        if (symbol.isNotEmpty()) return SYMBOL_TO_CODE[symbol] ?: SYMBOL_TO_CODE[symbol.uppercase(Locale.US)]
        val code = TRAILING_CODE.find(line)?.value
        return code?.let { SYMBOL_TO_CODE[it.uppercase(Locale.US)] }
    }

    /** Ordered symbol probes: multi-char symbols must be checked before single-char ones. */
    private val SYMBOL_PROBES = listOf("US$", "A$", "C$", "S$", "Rs.", "Rs", "₹", "€", "£", "¥", "zł", "$")

    /** Detects the receipt's currency from explicit codes/symbols anywhere in the text. */
    fun detectCurrency(lines: List<String>): String? {
        for (line in lines) {
            if (looksLikeIdentifier(line)) continue
            TRAILING_CODE.find(line)?.let {
                return SYMBOL_TO_CODE[it.value.uppercase(Locale.US)]
            }
            for (sym in SYMBOL_PROBES) {
                if (line.contains(sym)) return SYMBOL_TO_CODE[sym]
            }
        }
        return null
    }
}
