package com.warrantyvault.ocr

import java.time.LocalDate
import java.util.Locale

/**
 * Context-aware date parsing. Dates are returned together with the label that preceded them
 * so the parser can tell purchase dates from expiry dates instead of guessing by order.
 * Genuinely ambiguous numeric dates (e.g. 03/04/2026 where both DD/MM and MM/DD are valid)
 * are flagged so the review flow can surface a warning instead of silently picking one.
 */
object DateParser {

    data class DateHit(
        val date: LocalDate,
        /** "purchase", "start", "expiry", or null for unlabelled dates. */
        val context: String?,
        val source: String,
        /** 0..1 heuristic confidence. */
        val confidence: Float,
        /**
         * True when both day-first and month-first interpretations were plausible and we
         * picked one (day-first). The review screen should ask the user to confirm.
         */
        val ambiguous: Boolean = false
    )

    private val PURCHASE_LABELS = listOf(
        "purchase date", "date of purchase", "purchased on", "purchased", "bought on",
        "invoice date", "order date", "sale date", "date of sale", "receipt date"
    )
    private val EXPIRY_LABELS = listOf(
        "warranty valid until", "warranty valid till", "warranty valid upto", "warranty until",
        // "Valid Till" is the single most common expiry label on printed warranty cards.
        "valid till", "valid until", "valid thru", "valid through", "valid upto", "valid up to",
        "warranty upto", "warranty up to",
        "warranty expiry", "warranty expiration", "warranty expires", "warranty end",
        "coverage until", "coverage till", "coverage upto", "coverage end",
        "expiration date", "expiry date", "expires on",
        "expires", "exp date", "end date", "warranty ends"
    )
    private val START_LABELS = listOf(
        "warranty start", "coverage start", "coverage begins", "start date", "warranty begins",
        "coverage start date", "warranty valid from", "valid from"
    )

    private val MONTHS = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "sept" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )

    // Separators may be surrounded by spaces ("27 / 08 / 2025") — printed cards space them
    // and OCR often inserts spaces around them.
    private val NUMERIC_DATE = Regex(
        "(\\d{4})\\s*[./-]\\s*(\\d{1,2})\\s*[./-]\\s*(\\d{1,2})|(\\d{1,2})\\s*[./-]\\s*(\\d{1,2})\\s*[./-]\\s*(\\d{2,4})"
    )
    private val TEXTUAL_DATE = Regex(
        "(?i)(\\d{1,2})\\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*[.,]?\\s+(\\d{4})" +
            "|(?i)(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\s+(\\d{1,2})(?:st|nd|rd|th)?,?\\s+(\\d{4})"
    )

    private fun labelContext(line: String): String? {
        val lower = line.lowercase(Locale.US)
        if (EXPIRY_LABELS.any { lower.contains(it) }) return "expiry"
        if (START_LABELS.any { lower.contains(it) }) return "start"
        if (PURCHASE_LABELS.any { lower.contains(it) }) return "purchase"
        return null
    }

    /** Outcome of parsing a DD/MM-ordered numeric token. */
    data class NumericParse(val date: LocalDate, val ambiguous: Boolean)

    /**
     * Handwriting OCR substitutes look-alike letters for digits inside dates ("og" for 08,
     * "2?" for 27). Mapped only when a strict read of the token fails, so real text is never
     * reinterpreted: o/O→0, i/I/l/|→1, s/S→5, g/q→9, ?→7, b→6, z→2, +→t-style shapes.
     */
    private fun fuzzyDigits(raw: String): String = raw.map { c ->
        when (c) {
            'o', 'O' -> '0'
            'i', 'I', 'l', 'L', '|' -> '1'
            's', 'S' -> '5'
            // Handwritten 8 loops closed → OCR reads g ("og" for 08); open-topped 9 → q.
            'g' -> '8'
            'q' -> '9'
            '?' -> '7'
            'b' -> '6'
            'z', 'Z' -> '2'
            else -> c
        }
    }.joinToString("")

    /**
     * Parses "d/m/y" ordered tokens (the global norm). If both day-first and month-first
     * readings are possible (e.g. 03/04/2026) the day-first reading is returned with
     * [NumericParse.ambiguous] = true.
     */
    fun parseNumericDetailed(token: String): NumericParse? {
        parseNumericStrict(token)?.let { return it }
        val fuzzed = fuzzyDigits(token.trim())
        if (fuzzed != token.trim()) return parseNumericStrict(fuzzed)
        return null
    }

    private fun parseNumericStrict(token: String): NumericParse? {
        val m = NUMERIC_DATE.matchEntire(token.trim()) ?: return parseNumericLooseDetailed(token)
        val (y, mo, d, d2, mo2, y2) = m.destructured
        return if (y.isNotEmpty()) {
            // ISO order (2026-09-21): unambiguous by construction.
            makeDate(y.toInt(), mo.toInt(), d.toInt())?.let { NumericParse(it, ambiguous = false) }
        } else {
            val first = d2.toInt(); val second = mo2.toInt(); var year = y2.toInt()
            if (year < 100) year += if (year <= 69) 2000 else 1900
            val dayFirst = makeDate(year, second, first)
            val monthFirst = makeDate(year, first, second)
            when {
                dayFirst != null && monthFirst != null && first != second ->
                    // Both plausible: prefer day-first (global norm), flag ambiguity.
                    NumericParse(dayFirst, ambiguous = true)
                dayFirst != null -> NumericParse(dayFirst, ambiguous = false)
                monthFirst != null -> NumericParse(monthFirst, ambiguous = false)
                else -> null
            }
        }
    }

    private fun parseNumericLooseDetailed(token: String): NumericParse? {
        val parts = token.trim().split(Regex("\\s*[./-]\\s*"))
        if (parts.size != 3) return null
        if (!parts.all { it.isNotEmpty() && (it[0].isDigit() || it[0].lowercaseChar() in "oilsgqbz?") }) return null
        val a = parts[0].toIntOrNull() ?: return null
        val b = parts[1].toIntOrNull() ?: return null
        var c = parts[2].toIntOrNull() ?: return null
        if (c < 100) c += if (c <= 69) 2000 else 1900
        return if (parts[0].length == 4) {
            makeDate(a, b, c)?.let { NumericParse(it, ambiguous = false) }
        } else {
            parseNumericDetailed("$a/${b}/$c")
        }
    }

    /** Back-compat: parses a numeric token or null. */
    fun parseNumeric(token: String): LocalDate? = parseNumericDetailed(token)?.date

    fun parseTextual(line: String): LocalDate? {
        val m = TEXTUAL_DATE.find(line) ?: return null
        return try {
            if (m.groupValues[1].isNotEmpty()) {
                val day = m.groupValues[1].toInt()
                val mon = MONTHS[m.groupValues[2].lowercase(Locale.US)] ?: return null
                val year = m.groupValues[3].toInt()
                makeDate(year, mon, day)
            } else {
                val mon = MONTHS[m.groupValues[4].lowercase(Locale.US)] ?: return null
                val day = m.groupValues[5].toInt()
                val year = m.groupValues[6].toInt()
                makeDate(year, mon, day)
            }
        } catch (e: NumberFormatException) { null }
    }

    private fun makeDate(year: Int, month: Int, day: Int): LocalDate? {
        if (month !in 1..12 || day !in 1..31 || year !in 1900..2200) return null
        return try { LocalDate.of(year, month, day) } catch (e: Exception) { null }
    }

    /** Finds all dated values on a line. */
    fun findDates(line: String): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        NUMERIC_DATE.findAll(line).forEach { m ->
            parseNumeric(m.value)?.let { out.add(it) }
        }
        parseTextual(line)?.let { out.add(it) }
        return out.distinct()
    }

    /**
     * Scans lines for dates with their label context and ambiguity flags.
     */
    fun extractDatedLines(lines: List<String>): List<DateHit> {
        val hits = mutableListOf<DateHit>()
        for (line in lines) {
            val ctx = labelContext(line)
            val conf = when (ctx) {
                "purchase", "expiry", "start" -> 0.9f
                else -> 0.4f
            }
            // Numeric hits carry ambiguity info; textual dates are never ambiguous.
            NUMERIC_DATE.findAll(line).forEach { m ->
                parseNumericDetailed(m.value)?.let { np ->
                    hits.add(DateHit(np.date, ctx, line.trim(), conf, np.ambiguous))
                }
            }
            parseTextual(line)?.let { date ->
                hits.add(DateHit(date, ctx, line.trim(), conf, ambiguous = false))
            }
        }
        return hits.distinctBy { Triple(it.date, it.context, it.source) }
    }

    fun purchaseLabelPresent(line: String): Boolean {
        val lower = line.lowercase(Locale.US)
        return PURCHASE_LABELS.any { lower.contains(it) }
    }
}
