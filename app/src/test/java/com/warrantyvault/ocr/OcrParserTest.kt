package com.warrantyvault.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OcrParserTest {

    // ---------- Test 1: Indian receipt ----------

    @Test
    fun `indian receipt parses all key fields`() {
        val text = """
            AMAZON
            Samsung Galaxy S24 Ultra
            Model: SM-S928B
            S/N: R5CR123456
            Invoice Date: 21/09/2026
            Warranty: 24 Months
            Total: ₹129,999
        """.trimIndent()

        val r = OcrParser.parse(text)

        assertEquals("Amazon", r.purchaseStore.value)
        assertEquals("Samsung", r.brand.value)
        assertEquals("SM-S928B", r.model.value)
        assertEquals("R5CR123456", r.serialNumber.value)
        assertEquals(LocalDate.of(2026, 9, 21), LocalDate.parse(r.purchaseDate.value!!))
        assertEquals(24, r.warrantyPeriodMonths.value)
        assertEquals(129999.0, r.purchasePrice.value!!, 0.001)
        assertEquals("INR", r.currency.value)
        // purchase + 24 months via calendar arithmetic
        assertEquals(LocalDate.of(2028, 9, 21), LocalDate.parse(r.warrantyExpiryDate.value!!))
    }

    // ---------- Test 2: US receipt ----------

    @Test
    fun `us receipt parses price serial and duration`() {
        val text = """
            BEST BUY
            Apple MacBook Air
            Purchase Date: 09/21/2026
            Total ${'$'}999.99
            Serial Number: C02ABC123XYZ
            1 Year Limited Warranty
        """.trimIndent()

        val r = OcrParser.parse(text)

        assertEquals("Best Buy", r.purchaseStore.value)
        assertEquals("Apple", r.brand.value)
        assertEquals(LocalDate.of(2026, 9, 21), LocalDate.parse(r.purchaseDate.value!!))
        assertEquals(999.99, r.purchasePrice.value!!, 0.001)
        assertEquals("USD", r.currency.value)
        assertEquals("C02ABC123XYZ", r.serialNumber.value)
        assertEquals(12, r.warrantyPeriodMonths.value)
        assertEquals(LocalDate.of(2027, 9, 21), LocalDate.parse(r.warrantyExpiryDate.value!!))
    }

    // ---------- Test 3: European formats ----------

    @Test
    fun `european price and date formats`() {
        val text = """
            Media Markt
            Philips Hue Bridge
            21.09.2026
            €1.299,99
        """.trimIndent()

        val r = OcrParser.parse(text)
        assertEquals(1299.99, r.purchasePrice.value!!, 0.001)
        assertEquals("EUR", r.currency.value)
        assertEquals(LocalDate.of(2026, 9, 21), LocalDate.parse(r.purchaseDate.value!!))
    }

    @Test
    fun `price parser handles decimal comma formats`() {
        assertEquals(1.29, PriceParser.parseAmount("1,29")!!, 0.0001)
        assertEquals(1299.99, PriceParser.parseAmount("1.299,99")!!, 0.0001)
        assertEquals(1299.99, PriceParser.parseAmount("1,299.99")!!, 0.0001)
        assertEquals(49999.0, PriceParser.parseAmount("49,999")!!, 0.0001)
        assertEquals(999.99, PriceParser.parseAmount("999.99")!!, 0.0001)
    }

    @Test
    fun `currency codes recognized in all positions`() {
        assertEquals("INR", PriceParser.detectCurrency(listOf("Total: 49,999.00 INR")))
        assertEquals("INR", PriceParser.detectCurrency(listOf("Rs. 49,999")))
        assertEquals("USD", PriceParser.detectCurrency(listOf("USD 999.99")))
        assertEquals("EUR", PriceParser.detectCurrency(listOf("€999,99")))
        assertEquals("GBP", PriceParser.detectCurrency(listOf("£999.99")))
        assertEquals("AUD", PriceParser.detectCurrency(listOf("A$999.99")))
        assertEquals("CAD", PriceParser.detectCurrency(listOf("CAD 1299.99")))
        assertEquals("JPY", PriceParser.detectCurrency(listOf("¥150,000")))
        assertEquals("CHF", PriceParser.detectCurrency(listOf("CHF 999.90")))
        assertEquals("SEK", PriceParser.detectCurrency(listOf("12 990 SEK")))
        assertEquals("NOK", PriceParser.detectCurrency(listOf("NOK 1299")))
        assertEquals("DKK", PriceParser.detectCurrency(listOf("DKK 1299")))
        assertEquals("PLN", PriceParser.detectCurrency(listOf("4 999 PLN")))
        assertEquals("ZAR", PriceParser.detectCurrency(listOf("ZAR 12 999")))
        assertEquals("AED", PriceParser.detectCurrency(listOf("AED 3 699")))
        assertEquals("SGD", PriceParser.detectCurrency(listOf("S$1 299")))
    }

    // ---------- Test 4: identifiers must not become prices ----------

    @Test
    fun `imei invoice and serial are never parsed as prices`() {
        val text = """
            Store XYZ
            IMEI: 356789012345678
            Invoice No: 202609211234
            S/N: ABC123456
        """.trimIndent()

        val r = OcrParser.parse(text)
        assertNull("Identifier must not become a price", r.purchasePrice.value)
        assertEquals("356789012345678", r.imei.value)
        assertEquals("ABC123456", r.serialNumber.value)
    }

    // ---------- Regression: date lines never become prices ----------

    @Test
    fun `labelled date line never produces a price`() {
        val lines = listOf("Purchase Date: 21/09/2026")
        assertNull(PriceParser.extractPrice(lines))
        val lines2 = listOf("Invoice Date: 21/09/2026")
        assertNull(PriceParser.extractPrice(lines2))
        val lines3 = listOf("Warranty Expiry: 21/09/2028")
        assertNull(PriceParser.extractPrice(lines3))
        val lines4 = listOf("Date: 2026-09-21")
        assertNull(PriceParser.extractPrice(lines4))
    }

    @Test
    fun `date-only receipt yields date but no price`() {
        val text = """
            Some Store
            Purchase Date: 21/09/2026
        """.trimIndent()
        val r = OcrParser.parse(text)
        assertEquals(LocalDate.of(2026, 9, 21), LocalDate.parse(r.purchaseDate.value!!))
        assertNull("Date tokens must not become money", r.purchasePrice.value)
    }

    @Test
    fun `bare date line without label is not a price`() {
        val lines = listOf("21/09/2026")
        assertNull(PriceParser.extractPrice(lines))
    }

    @Test
    fun `model number line is not a price`() {
        val lines = listOf("Model: SM-S928B")
        assertNull(PriceParser.extractPrice(lines))
    }

    // ---------- Regression: multiple prices, label ranking ----------

    @Test
    fun `grand total outranks subtotal and tax`() {
        val text = """
            Store
            Widget Pro
            Subtotal: ₹115,000
            Tax: ₹14,500
            Discount: ₹3,500
            Grand Total: ₹126,000
        """.trimIndent()
        val r = OcrParser.parse(text)
        assertEquals(126000.0, r.purchasePrice.value!!, 0.001)
        assertTrue("Multiple amounts should raise a verification warning", r.warnings.any { it.contains("price", ignoreCase = true) })
    }

    @Test
    fun `labelled price beats larger unlabelled number`() {
        val lines = listOf("Widget 85000", "Total: 129.99")
        val m = PriceParser.extractPrice(lines)
        assertEquals(129.99, m!!.amount, 0.001)
    }

    @Test
    fun `amount paid outranks bare price`() {
        val lines = listOf("Price: 90.00", "Amount Paid: 100.00")
        val m = PriceParser.extractPrice(lines)
        assertEquals(100.0, m!!.amount, 0.001)
    }

    @Test
    fun `unlabelled price extraction prefers total labels`() {
        val lines = listOf("Subtotal: 90.00", "Tax: 10.00", "Grand Total: 100.00")
        val m = PriceParser.extractPrice(lines)
        assertNotNull(m)
        assertEquals(100.0, m!!.amount, 0.001)
    }

    @Test
    fun `price on next line after label is found`() {
        val lines = listOf("Total", "₹129,999")
        val m = PriceParser.extractPrice(lines)
        assertNotNull(m)
        assertEquals(129999.0, m!!.amount, 0.001)
    }

    @Test
    fun `next line after label that is a date is skipped`() {
        val lines = listOf("Total", "Date: 21/09/2026")
        // The date next-line must not be consumed as the total's value.
        val m = PriceParser.extractPrice(lines)
        assertNull(m)
    }

    // ---------- Test 5: explicit expiry ----------

    @Test
    fun `explicit warranty expiry is used verbatim`() {
        val text = """
            Store
            Widget Pro
            Purchase Date: 21 Sep 2026
            Warranty Valid Until: September 21, 2028
        """.trimIndent()

        val r = OcrParser.parse(text)
        assertEquals(LocalDate.of(2028, 9, 21), LocalDate.parse(r.warrantyExpiryDate.value!!))
    }

    // ---------- Test 6: duration arithmetic ----------

    @Test
    fun `two year warranty adds calendar months`() {
        val text = """
            Store
            Widget
            Purchase Date: 21 Sep 2026
            Warranty: 2 Years
        """.trimIndent()

        val r = OcrParser.parse(text)
        assertEquals(24, r.warrantyPeriodMonths.value)
        assertEquals(LocalDate.of(2028, 9, 21), LocalDate.parse(r.warrantyExpiryDate.value!!))
    }

    @Test
    fun `duration phrase variants normalize to months`() {
        assertEquals(12, WarrantyParser.extractDuration(listOf("1 Year Warranty"))?.months)
        assertEquals(24, WarrantyParser.extractDuration(listOf("24 Months Warranty"))?.months)
        assertEquals(36, WarrantyParser.extractDuration(listOf("Warranty: 3 Years"))?.months)
        assertEquals(18, WarrantyParser.extractDuration(listOf("Coverage: 18 months"))?.months)
        assertEquals(36, WarrantyParser.extractDuration(listOf("36-month warranty"))?.months)
        assertEquals(24, WarrantyParser.extractDuration(listOf("2 Year Warranty"))?.months)
    }

    @Test
    fun `calendar month arithmetic is exact`() {
        val jan31 = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 28), DateUtils.addMonths(jan31, 1))
        // Feb 28 2027 + 12 months stays Feb 28 (no day-clamping involved).
        assertEquals(LocalDate.of(2028, 2, 28), DateUtils.addMonths(LocalDate.of(2027, 2, 28), 12))
        assertEquals(LocalDate.of(2028, 2, 29), DateUtils.addMonths(LocalDate.of(2027, 8, 29), 6))
        assertEquals(LocalDate.of(2027, 3, 31), DateUtils.addMonths(jan31, 14))
        // A one-month period is not always 30 days.
        assertEquals(28, DateUtils.daysBetween(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1)))
    }

    // ---------- Test 7: invalid warranty period ----------

    @Test
    fun `expiry before purchase produces warning`() {
        val text = """
            Store
            Widget
            Purchase Date: 21 Sep 2026
            Warranty Expiry: 20 Sep 2025
        """.trimIndent()

        val r = OcrParser.parse(text)
        assertTrue(
            r.warnings.any { it.contains("before the purchase date", ignoreCase = true) }
        )
        assertNull("Invalid expiry must not be trusted", r.warrantyExpiryDate.value)
    }

    // ---------- Test 8: conflicting duration vs explicit expiry ----------

    @Test
    fun `conflicting duration and explicit expiry warns`() {
        val text = """
            Store
            Widget
            Purchase Date: 21 Sep 2026
            Warranty: 24 Months
            Warranty Expiry: 21 Sep 2027
        """.trimIndent()

        val r = OcrParser.parse(text)
        assertTrue(r.warnings.any { it.contains("disagree", ignoreCase = true) })
        // Explicit expiry wins, not the duration-implied 2028 date.
        assertEquals(LocalDate.of(2027, 9, 21), LocalDate.parse(r.warrantyExpiryDate.value!!))
    }

    @Test
    fun `small duration drift does not warn`() {
        // 12 months from Sep 21 2026 = Sep 21 2027; expiry a few days earlier is fine.
        val text = """
            Store
            Widget
            Purchase Date: 21 Sep 2026
            Warranty: 12 Months
            Warranty Expiry: 18 Sep 2027
        """.trimIndent()
        val r = OcrParser.parse(text)
        assertFalse(r.warnings.any { it.contains("differ", ignoreCase = true) || it.contains("disagree", ignoreCase = true) })
    }

    // ---------- Regression: ambiguous numeric dates ----------

    @Test
    fun `ambiguous numeric date warns`() {
        val text = """
            Store
            Widget
            Purchase Date: 03/04/2026
        """.trimIndent()
        val r = OcrParser.parse(text)
        assertTrue("Ambiguity must surface a warning", r.warnings.any { it.contains("day-first or month-first", ignoreCase = true) })
        assertEquals(LocalDate.of(2026, 4, 3), LocalDate.parse(r.purchaseDate.value!!))
    }

    @Test
    fun `unambiguous day-month pair does not warn`() {
        val text = """
            Store
            Widget
            Purchase Date: 21/09/2026
        """.trimIndent()
        val r = OcrParser.parse(text)
        assertFalse(r.warnings.any { it.contains("day-first or month-first", ignoreCase = true) })
        assertEquals(LocalDate.of(2026, 9, 21), LocalDate.parse(r.purchaseDate.value!!))
    }

    @Test
    fun `iso date never flagged ambiguous`() {
        val hit = DateParser.parseNumericDetailed("2026-09-21")!!
        assertFalse(hit.ambiguous)
    }

    @Test
    fun `second date component over twelve forces month-first`() {
        // 25/03/2026 can only be day-first (25 can't be a month).
        val hit = DateParser.parseNumericDetailed("25/03/2026")!!
        assertFalse(hit.ambiguous)
        assertEquals(LocalDate.of(2026, 3, 25), hit.date)
    }

    // ---------- Timeline validation ----------

    @Test
    fun `warranty start before purchase warns`() {
        val text = """
            Store
            Widget
            Purchase Date: 21 Sep 2026
            Warranty Start: 01 Sep 2026
            Warranty Expiry: 21 Sep 2028
        """.trimIndent()
        val r = OcrParser.parse(text)
        assertTrue(r.warnings.any { it.contains("before the purchase date", ignoreCase = true) })
    }

    // ---------- Merchant / brand / model ----------

    @Test
    fun `merchant is not treated as brand`() {
        val text = """
            BEST BUY
            Sony WH-1000XM5
            Model: WH-1000XM5
        """.trimIndent()
        val r = OcrParser.parse(text)
        assertEquals("Best Buy", r.purchaseStore.value)
        assertEquals("Sony", r.brand.value)
    }

    @Test
    fun `model label beats noise`() {
        val text = """
            Amazon
            Galaxy S24 Ultra
            Model No: SM-S928B
            IMEI1: 356789012345678
        """.trimIndent()
        val r = OcrParser.parse(text)
        assertEquals("SM-S928B", r.model.value)
        assertEquals("356789012345678", r.imei.value)
        assertEquals("Samsung", r.brand.value)
        assertEquals("Amazon", r.purchaseStore.value)
    }

    @Test
    fun `warnings emitted for missing core fields`() {
        val r = OcrParser.parse("Some shop\nA thing\nTotal: 10.00")
        assertTrue(r.warnings.any { it.contains("product", ignoreCase = true) })
        assertTrue(r.warnings.any { it.contains("purchase date", ignoreCase = true) })
    }

    @Test
    fun `raw text is always preserved`() {
        val raw = "Some\nRaw\nText\nTotal: ₹1,234"
        val r = OcrParser.parse(raw)
        assertEquals(raw, r.rawText)
    }

    // ---------- IMEI validation ----------

    @Test
    fun `valid luhn imei accepted`() {
        // 490154203237518 is a standard Luhn-valid 15-digit IMEI test vector.
        assertTrue(OcrParser.isPlausibleImei("490154203237518"))
    }

    @Test
    fun `invalid checksum imei is flagged by checksum helper`() {
        // Shape alone is plausible (OCR may misread a digit); the Luhn check reports it.
        assertTrue(OcrParser.isPlausibleImei("490154203237519"))
        assertFalse(OcrParser.imeiChecksumValid("490154203237519"))
        assertTrue(OcrParser.imeiChecksumValid("490154203237518"))
    }

    @Test
    fun `wrong length imei rejected`() {
        assertFalse(OcrParser.isPlausibleImei("12345"))
        assertFalse(OcrParser.isPlausibleImei("12345678901234567890"))
    }

    @Test
    fun `malformed detected imei produces warning and is dropped`() {
        val text = """
            Store
            Widget
            IMEI: 12345
        """.trimIndent()
        val r = OcrParser.parse(text)
        assertNull(r.imei.value)
        assertTrue(r.warnings.any { it.contains("IMEI", ignoreCase = true) })
    }
}
