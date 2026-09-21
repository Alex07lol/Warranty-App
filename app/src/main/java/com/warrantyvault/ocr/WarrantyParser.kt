package com.warrantyvault.ocr

/**
 * Parses warranty durations ("2 Year Warranty", "24 Months Warranty", "36-month warranty")
 * into a normalized month count.
 */
object WarrantyParser {

    data class DurationHit(val months: Int, val source: String)

    /**
     * Number-first ("2 Year Warranty", "36-month warranty", "24 Months Warranty").
     * A following warrant/coverage word within ~20 chars is accepted but not required —
     * bare phrases like "1 Year Limited Warranty" must also match.
     */
    private val NUMBER_FIRST = Regex(
        "(?i)\\b(\\d{1,3})\\s*[- ]?(years?|yrs?|y|months?|mo\\.?|m)\\b(?:[^\\n]{0,20}?(warranty|coverage|guarantee))?"
    )

    /** Warranty-word-first ("Warranty: 3 Years", "Coverage: 18 months", "Warranty 2yr"). */
    private val WARRANTY_FIRST = Regex(
        "(?i)\\b(warranty|coverage|guarantee)[a-z]*\\s*[:\\-]?\\s*(\\d{1,3})\\s*[- ]?(years?|yrs?|y|months?|mo\\.?|m)\\b"
    )

    /** Months to years display helper for review UI. */
    fun describe(months: Int): String = when {
        months % 12 == 0 -> "${months / 12} year${if (months == 12) "" else "s"}"
        else -> "$months months"
    }

    fun normalizeMonths(n: Int, unit: String): Int = when (unit.lowercase().trimEnd('.')) {
        "y", "yr", "yrs", "year", "years" -> n * 12
        else -> n
    }

    fun extractDuration(lines: List<String>): DurationHit? {
        for (line in lines) {
            val m = WARRANTY_FIRST.find(line)
            if (m != null) {
                val n = m.groupValues[2].toIntOrNull() ?: continue
                if (n in 1..240) return DurationHit(normalizeMonths(n, m.groupValues[3]), line.trim())
            }
            val n = NUMBER_FIRST.find(line)
            if (n != null) {
                val count = n.groupValues[1].toIntOrNull() ?: continue
                if (count in 1..240) return DurationHit(normalizeMonths(count, n.groupValues[2]), line.trim())
            }
        }
        return null
    }
}
