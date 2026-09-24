package com.warrantyvault.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WarrantyPdfTest {

    private fun sample(mods: (WarrantyPdf.PassportProduct) -> WarrantyPdf.PassportProduct = { it }): WarrantyPdf.PassportProduct {
        val base = WarrantyPdf.PassportProduct(
            productName = "ASUS TUF Gaming F15",
            brand = "ASUS",
            model = "FX506LH-HN258W",
            category = "laptop",
            serialNumber = "N8NRCV012345678",
            imei = null,
            purchaseDate = "2025-09-05",
            purchasePrice = 74999.0,
            currency = "INR",
            purchaseStore = "ASUS India Private Limited",
            warrantyStatus = "active",
            warrantyExpiryDate = "2027-09-04",
            warrantyPeriodMonths = 24,
            warrantyProvider = "ASUS India",
            warrantyProviderType = "manufacturer",
            warrantyContact = "1800 572 6005",
            lifecycleStatus = "in_use",
            tags = listOf("gaming", "primary"),
            notes = "Bought during festive sale.",
            services = listOf(
                WarrantyPdf.PassportService(
                    date = "2026-05-10",
                    type = "repair",
                    provider = "Noise Service Centre",
                    cost = 1250.0,
                    currency = "INR",
                    nextDate = "2026-11-10"
                )
            )
        )
        return mods(base)
    }

    private fun pdfString(bytes: ByteArray): String = String(bytes, Charsets.ISO_8859_1)

    @Test
    fun `header is valid pdf 1_4 with binary marker`() {
        val pdf = pdfString(WarrantyPdf.buildPassports(listOf(sample()), "2026-09-23 10:00:00"))
        assertTrue(pdf.startsWith("%PDF-1.4\n"))
        assertTrue(pdf.contains("%%EOF"))
        assertTrue(pdf.contains("startxref"))
    }

    @Test
    fun `one page per product with passport sections`() {
        val pdf = pdfString(
            WarrantyPdf.buildPassports(listOf(sample(), sample { it.copy(productName = "boAt Airdopes 141") }), "t")
        )
        assertEquals(2, Regex("/MediaBox \\[0 0 595\\.28 841\\.89\\]").findAll(pdf).count())
        assertTrue(pdf.contains("WARRANTYVAULT"))
        assertTrue(pdf.contains("PRODUCT PASSPORT & WARRANTY RECORD"))
        assertTrue(pdf.contains("Page 1 of 2"))
        assertTrue(pdf.contains("Page 2 of 2"))
        assertTrue(pdf.contains("ASUS TUF Gaming F15"))
        assertTrue(pdf.contains("boAt Airdopes 141"))
    }

    @Test
    fun `all fields of one product land on its page`() {
        val pdf = pdfString(WarrantyPdf.buildPassports(listOf(sample()), "t"))
        listOf(
            "N8NRCV012345678", "FX506LH-HN258W", "2025-09-05", "2027-09-04",
            "INR 74,999", "ASUS India Private Limited", "PRODUCT & PURCHASE SPECIFICATIONS",
            "WARRANTY & COVERAGE PROTECTION", "TAGS & NOTES", "SERVICE & MAINTENANCE HISTORY",
            "ACTIVE", "24 Months", "1800 572 6005", "2026-05-10", "1,250"
        ).forEach { field -> assertTrue("missing: $field", pdf.contains(field)) }
    }

    @Test
    fun `xref offsets point at every object`() {
        val bytes = WarrantyPdf.buildPassports(listOf(sample()), "t")
        val pdf = pdfString(bytes)
        val xrefStart = pdf.lastIndexOf("xref\n")
        assertTrue(xrefStart > 0)
        // Every "n 0 obj" must start exactly at the offset the xref table claims.
        Regex("(\\d{10}) 00000 n ").findAll(pdf.substring(xrefStart)).forEach { m ->
            val offset = m.groupValues[1].toInt()
            val at = pdf.substring(offset, offset + 20)
            assertTrue("offset $offset points at '$at'", Regex("^\\d+ 0 obj").containsMatchIn(at))
        }
        // startxref must point at the xref keyword.
        val startXref = Regex("\\d+").find(pdf.substringAfterLast("startxref\n"))!!.value.toInt()
        assertEquals("xref", pdf.substring(startXref, startXref + 4))
    }

    @Test
    fun `empty vault produces a friendly single page`() {
        val pdf = pdfString(WarrantyPdf.buildPassports(emptyList(), "t"))
        assertTrue(pdf.contains("NO PRODUCTS IN VAULT"))
        assertTrue(pdf.contains("Page 1 of 1"))
        assertFalse(pdf.contains("Page 2"))
    }

    @Test
    fun `special characters are escaped and ascii-folded`() {
        // Only backslash, parens need PDF escaping — an apostrophe is a normal char.
        assertEquals("It's \\(ok\\)", WarrantyPdf.escape("It's (ok)"))
        val pdf = pdfString(
            WarrantyPdf.buildPassports(
                listOf(sample { it.copy(productName = "Rupee ₹ product", notes = "a(b)c \\ back") }),
                "t"
            )
        )
        assertTrue(pdf.contains("Rupee INR  product"))
        assertTrue(pdf.contains("a\\(b\\)c \\\\ back"))
        assertFalse(pdf.contains("₹"))
    }

    @Test
    fun `truncate keeps text inside the column width`() {
        val long = "A very long product name that will not fit the title column at all"
        val cut = WarrantyPdf.truncate(long, 16.0, "F2", 360.0)
        assertTrue(cut.endsWith("..."))
        // 0.56 avg glyph factor for bold: maxChars = 360 / (16*0.56) = 40
        assertEquals(40, cut.length)
        assertEquals("short", WarrantyPdf.truncate("short", 9.0, "F1", 300.0))
        assertEquals("", WarrantyPdf.truncate("", 9.0, "F1", 300.0))
    }

    @Test
    fun `expiring and expired statuses get their own labels`() {
        val expiring = pdfString(
            WarrantyPdf.buildPassports(listOf(sample { it.copy(warrantyStatus = "expiring_soon") }), "t")
        )
        assertTrue(expiring.contains("EXPIRING SOON"))
        val expired = pdfString(
            WarrantyPdf.buildPassports(listOf(sample { it.copy(warrantyStatus = "expired") }), "t")
        )
        assertTrue(expired.contains("EXPIRED"))
        val unknown = pdfString(
            WarrantyPdf.buildPassports(listOf(sample { it.copy(warrantyStatus = "unknown", warrantyExpiryDate = null) }), "t")
        )
        assertTrue(unknown.contains("UNSPECIFIED"))
        assertTrue(unknown.contains("No date recorded"))
    }

    @Test
    fun `more than eight services collapse with a note`() {
        val services = (1..12).map {
            WarrantyPdf.PassportService("2026-01-%02d".format(it), "maintenance", "Shop", 100.0 * it, "INR", null)
        }
        val pdf = pdfString(WarrantyPdf.buildPassports(listOf(sample { it.copy(services = services) }), "t"))
        assertTrue(pdf.contains("+ 4 more service record"))
        assertTrue(pdf.contains("2026-01-08")) // 8th row visible
        assertFalse(pdf.contains("2026-01-09")) // 9th collapsed
    }

    @Test
    fun `price formatting keeps cents and thousands separators`() {
        val withCents = pdfString(
            WarrantyPdf.buildPassports(listOf(sample { it.copy(purchasePrice = 1234.5) }), "t")
        )
        assertTrue(withCents.contains("INR 1,234.50"))
        val round = pdfString(
            WarrantyPdf.buildPassports(listOf(sample { it.copy(purchasePrice = 74999.0) }), "t")
        )
        assertTrue(round.contains("INR 74,999") && !round.contains("INR 74,999.00"))
        val noPrice = pdfString(
            WarrantyPdf.buildPassports(listOf(sample { it.copy(purchasePrice = null) }), "t")
        )
        assertTrue(noPrice.contains("N/A"))
    }
}
