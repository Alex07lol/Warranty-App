package com.warrantyvault.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * End-to-end evidence that the scan pipeline works on three *real* warranty cards.
 *
 * `app/src/test/resources/ocr/win-scale1.txt` and `win-scale2.txt` are the untouched line
 * output of an OCR engine (Windows.Media.Ocr, en-GB) run over the three card photos, captured
 * at 1x and 2x. Nothing was reordered or retyped: the files still contain the engine's
 * mistakes (halved words like "nsi iS", logo and signature noise, a serial read as
 * "41 IN2584769" at 1x). Non-ASCII glyph-recognition garbage was deleted by byte filter; no
 * text was edited.
 *
 * These captures are the hard case. Real on-device OCR (ML Kit) reads the same cards with
 * better geometry, and its lines are already "LABEL : value" — see `RealWarrantyCardTest` for
 * that shape. If a card parses here, it parses on device.
 */
class WinOcrCaptureTest {

    /** Fixed "today" so expiry/warning assertions never depend on the wall clock. */
    private val today = LocalDate.of(2026, 9, 22)

    private data class Capture(val scale: Int, val cards: Map<String, String>)

    private fun capture(scale: Int): Capture {
        val text = javaClass.classLoader!!
            .getResourceAsStream("ocr/win-scale$scale.txt")!!
            .bufferedReader()
            .readText()
        val cards = LinkedHashMap<String, String>()
        var current: String? = null
        val buffer = StringBuilder()
        for (line in text.lines()) {
            when {
                line.startsWith("### FILE ") -> {
                    current?.let { cards[it] = buffer.toString() }
                    buffer.setLength(0)
                    current = line.removePrefix("### FILE ").trim()
                }
                line.startsWith("### ") || current == null -> Unit
                else -> buffer.appendLine(line)
            }
        }
        current?.let { cards[it] = buffer.toString() }
        assertEquals("capture $scale must contain all three cards", 3, cards.size)
        return Capture(scale, cards)
    }

    private fun card(scale: Int, fragment: String): Pair<String, OcrResult> {
        val entry = capture(scale).cards.entries.first { it.key.contains(fragment) }
        return entry.key to OcrParser.parse(entry.value, today)
    }

    // ---------------- evidence dump ----------------

    @Test
    fun `parsed fields for every real capture`() {
        val out = StringBuilder("\n\nOCR PIPELINE ON REAL CARD IMAGES\n")
        for (scale in listOf(1, 2)) {
            for ((file, text) in capture(scale).cards) {
                val r = OcrParser.parse(text, today)
                // Every card, at both resolutions, must yield identity and warranty dates.
                // Only the serial of the 1x boAt capture is allowed to be missing (the engine
                // split the token and review asks the user to type it).
                assertNotNull("$file @${scale}x product name", r.productName.value)
                assertNotNull("$file @${scale}x purchase date", r.purchaseDate.value)
                assertNotNull("$file @${scale}x expiry date", r.warrantyExpiryDate.value)
                assertNotNull("$file @${scale}x period", r.warrantyPeriodMonths.value)
                out.appendLine("-".repeat(72))
                out.appendLine("scale=${scale}x  file=$file")
                out.appendLine("  productName   : ${r.productName.value}")
                out.appendLine("  brand         : ${r.brand.value}")
                out.appendLine("  model         : ${r.model.value}")
                out.appendLine("  serialNumber  : ${r.serialNumber.value}")
                out.appendLine("  imei          : ${r.imei.value}")
                out.appendLine("  purchaseDate  : ${r.purchaseDate.value}")
                out.appendLine("  periodMonths  : ${r.warrantyPeriodMonths.value}")
                out.appendLine("  expiryDate    : ${r.warrantyExpiryDate.value}")
                out.appendLine("  purchaseStore : ${r.purchaseStore.value}")
                out.appendLine("  purchasePrice : ${r.purchasePrice.value}")
                out.appendLine("  confidence    : ${r.productName.confidence}/${r.serialNumber.confidence}")
                r.warnings.forEach { out.appendLine("  warning       : $it") }
            }
        }
        out.appendLine("-".repeat(72))
        println(out)
    }

    // ---------------- ASUS TUF Gaming F15 warranty certificate ----------------

    @Test
    fun `asus card parses from both captures`() {
        for (scale in listOf(1, 2)) {
            val (file, r) = card(scale, "31a880e5")
            val where = "scale=${scale}x ($file)"
            assertEquals("$where name", "ASUS TUF Gaming F15", r.productName.value)
            assertEquals("$where brand", "ASUS", r.brand.value)
            assertEquals("$where model", "FX506LH-HN258W", r.model.value)
            assertEquals("$where serial", "N8NRCV012345678", r.serialNumber.value)
            assertNull("$where has no IMEI", r.imei.value)
            assertEquals("$where purchase", LocalDate.of(2025, 9, 5), LocalDate.parse(r.purchaseDate.value))
            assertEquals("$where period", 24, r.warrantyPeriodMonths.value)
            assertEquals("$where expiry", LocalDate.of(2027, 9, 4), LocalDate.parse(r.warrantyExpiryDate.value))
            assertEquals("$where store", "ASUS India Private Limited", r.purchaseStore.value)
            assertNull("$where price: a warranty certificate carries no price", r.purchasePrice.value)
        }
    }

    @Test
    fun `asus card leaves the address hologram and invoice alone`() {
        for (scale in listOf(1, 2)) {
            val (_, r) = card(scale, "31a880e5")
            // 402 (house number), 400053 (postcode) and A123456789 (hologram) are not money.
            assertNull("scale=${scale}x price", r.purchasePrice.value)
            assertTrue(
                "scale=${scale}x should ask the user for the price",
                r.warnings.any { it.contains("price", ignoreCase = true) }
            )
        }
    }

    // ---------------- HP Laptop 15s warranty card ----------------

    @Test
    fun `hp card parses from both captures`() {
        for (scale in listOf(1, 2)) {
            val (file, r) = card(scale, "ChatGPT Image Sep 21")
            val where = "scale=${scale}x ($file)"
            assertEquals("$where name", "HP Laptop 15s", r.productName.value)
            assertEquals("$where brand", "HP", r.brand.value)
            assertEquals("$where model", "15s-eq2143AU", r.model.value)
            assertEquals("$where serial", "5CD2458XYZ", r.serialNumber.value)
            assertEquals("$where purchase", LocalDate.of(2025, 8, 12), LocalDate.parse(r.purchaseDate.value))
            assertEquals("$where period", 12, r.warrantyPeriodMonths.value)
            assertEquals("$where expiry", LocalDate.of(2026, 8, 11), LocalDate.parse(r.warrantyExpiryDate.value))
            assertEquals("$where store", "HP India Sales Pvt. Ltd.", r.purchaseStore.value)
            // The support number 1800 108 4747 and postcode 560030 must never be the price.
            assertNull("$where price", r.purchasePrice.value)
        }
    }

    // ---------------- boAt Airdopes 141 warranty card ----------------

    @Test
    fun `boat card parses from the 2x capture`() {
        val (_, r) = card(2, "16e44cf0")
        assertEquals("boAt Airdopes 141", r.productName.value)
        assertEquals("boAt", r.brand.value)
        assertEquals("Airdopes 141", r.model.value)
        assertEquals("BT1411N2584769", r.serialNumber.value)
        assertEquals(LocalDate.of(2025, 7, 17), LocalDate.parse(r.purchaseDate.value))
        assertEquals(12, r.warrantyPeriodMonths.value)
        assertEquals(LocalDate.of(2026, 7, 16), LocalDate.parse(r.warrantyExpiryDate.value))
        assertEquals("Amazon.in", r.purchaseStore.value)
        assertNull("the AMZ invoice number is not a price", r.purchasePrice.value)
    }

    @Test
    fun `boat card keeps every readable field at 1x`() {
        val (_, r) = card(1, "16e44cf0")
        // At 1x the engine splits the serial ("41 IN2584769"), so it cannot be trusted; the
        // rest of the card is still read, and review asks the user to fill the serial in.
        assertEquals("boAt Airdopes 141", r.productName.value)
        assertEquals("boAt", r.brand.value)
        assertEquals("Airdopes 141", r.model.value)
        assertEquals(LocalDate.of(2025, 7, 17), LocalDate.parse(r.purchaseDate.value))
        assertEquals(12, r.warrantyPeriodMonths.value)
        assertEquals(LocalDate.of(2026, 7, 16), LocalDate.parse(r.warrantyExpiryDate.value))
        assertEquals("Amazon.in", r.purchaseStore.value)
        assertNull("a split serial must not be invented", r.purchasePrice.value)
        // The engine read the serial as "41 IN2584769" — two characters short. A partial or
        // space-broken serial is worse than none (it would be printed on a claim), and the
        // label word "NUMBER" must never stand in for the missing value.
        assertNull("serial must stay empty rather than hold a misread value", r.serialNumber.value)
        assertTrue(
            "review must be told the serial is missing",
            r.warnings.any { it.contains("serial", ignoreCase = true) }
        )
    }

    @Test
    fun `boat card price stays empty while the invoice number is present`() {
        val (_, r) = card(2, "16e44cf0")
        assertNull(r.purchasePrice.value)
        assertTrue(r.warnings.any { it.contains("price", ignoreCase = true) })
    }
}
