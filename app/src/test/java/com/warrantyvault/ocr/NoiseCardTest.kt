package com.warrantyvault.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The same capture harness as [WinOcrCaptureTest] — untouched OCR output of the actual card
 * image — run against the pipeline. This card is the hard case: handwritten values in blue
 * ink on crumpled paper, no "Valid Till" (the expiry must be computed from the period), a
 * spaced numeric date, and an invoice/phone number that must not become a price.
 */
class NoiseCardTest {

    /** Fixed "today" so computed expiries never depend on the wall clock. */
    private val today = LocalDate.of(2026, 9, 22)

    private fun capture(scale: Int): Map<Int, String> {
        val text = javaClass.classLoader!!
            .getResourceAsStream("ocr/win-noise$scale.txt")!!
            .bufferedReader()
            .readText()
        val cards = LinkedHashMap<Int, String>()
        var current: Int? = null
        val buffer = StringBuilder()
        for (line in text.lines()) {
            when {
                line.startsWith("### FILE ") -> {
                    current?.let { cards[it] = buffer.toString() }
                    buffer.setLength(0)
                    current = scale
                }
                line.startsWith("### ") || current == null -> Unit
                else -> buffer.appendLine(line)
            }
        }
        current?.let { cards[it] = buffer.toString() }
        return cards
    }

    private fun parse(scale: Int): OcrResult = OcrParser.parse(capture(scale).values.first(), today)

    @Test
    fun `evidence dump`() {
        for (scale in listOf(1, 2)) {
            val r = parse(scale)
            println(
                """\nNOISE ${scale}x
                    |  name    : ${r.productName.value}
                    |  brand   : ${r.brand.value}
                    |  model   : ${r.model.value}
                    |  serial  : ${r.serialNumber.value}
                    |  purchase: ${r.purchaseDate.value}
                    |  period  : ${r.warrantyPeriodMonths.value}
                    |  expiry  : ${r.warrantyExpiryDate.value}
                    |  store   : ${r.purchaseStore.value}
                    |  price   : ${r.purchasePrice.value}
                    |  aligned :
                """.trimMargin() + LabelValueAligner.align(capture(scale).values.first().lines()).joinToString("\n           ") { it }
            )
        }
    }

    // ---------------- what the card is ----------------

    @Test
    fun `noise card parses identity dates and computed expiry at 1x`() {
        val r = parse(1)
        // Handwriting read as "Noise ColorFi+ pulse": brand + model + colour word — a
        // corrected-in-review product name, not a parse failure.
        assertNotNull(r.productName.value)
        assertEquals("Noise", r.brand.value)
        assertEquals("NS-WB-PULSE3-BLK", r.model.value)
        assertEquals("NP3B2508276", r.serialNumber.value)
        assertEquals(LocalDate.of(2025, 8, 27), LocalDate.parse(r.purchaseDate.value))
        assertEquals(12, r.warrantyPeriodMonths.value)
        // No "Valid Till" on the card — the expiry is purchase + 12 months.
        assertEquals(LocalDate.of(2026, 8, 27), LocalDate.parse(r.warrantyExpiryDate.value))
        // The 1800 phone number and BLR/25-26/118942 invoice are not prices.
        assertNull(r.purchasePrice.value)
    }

    @Test
    fun `noise card parses at 2x with computed expiry`() {
        val r = parse(2)
        assertNotNull(r.productName.value)
        assertEquals("Noise", r.brand.value)
        // At 2x the model lost its middle ("NS- BLK") — a partial model must not be stored.
        assertNull(r.model.value)
        assertEquals("NP3B2508276", r.serialNumber.value)
        assertEquals(LocalDate.of(2025, 8, 27), LocalDate.parse(r.purchaseDate.value))
        assertEquals(12, r.warrantyPeriodMonths.value)
        assertEquals(LocalDate.of(2026, 8, 27), LocalDate.parse(r.warrantyExpiryDate.value))
        assertNull(r.purchasePrice.value)
    }

    @Test
    fun `review is told what to verify on the noise card`() {
        val r = parse(1)
        val w = r.warnings.joinToString(" ").lowercase()
        assertTrue("expect a price warning, got: $w", w.contains("price"))
        assertTrue(
            "handwritten card should surface at least one verification warning, got: $w",
            r.warnings.isNotEmpty()
        )
    }

    // ---------------- unit level: the handwriting failure modes ----------------

    @Test
    fun `spaced numeric separators parse`() {
        assertEquals(
            LocalDate.of(2025, 8, 27),
            DateParser.parseNumeric("27 / 08 / 2025")
        )
        assertTrue(DateParser.parseNumericDetailed("27 / 08 / 2025")!!.ambiguous.not())
    }

    @Test
    fun `i-for-1 duration reads as twelve months`() {
        val r = WarrantyParser.extractDuration(listOf("I year (12 Mon+hs)"))
        assertNotNull(r)
        assertEquals(12, r!!.months)
    }

    @Test
    fun `currency lookalikes are repaired inside identifiers`() {
        assertEquals("NP3B2508276", IdentifierParser.repairIdentifierLookalikes("NP3₹25082?6"))
    }

    @Test
    fun `a serial with lookalike breaks is captured whole`() {
        // The regex may break at the ₹/?; the value must be captured across the break and
        // repaired, never truncated at the symbol.
        val hit = IdentifierParser.extractSerial(listOf("Serial Number : NP3₹25082?6"))
        assertNotNull(hit)
        assertEquals("NP3B2508276", hit!!.first)
    }

    @Test
    fun `noise brand is canonicalized`() {
        assertEquals(
            "Noise",
            IdentifierParser.extractBrand(listOf("Noise ColorFit Pulse 3"), null, null, null)
        )
    }
}
