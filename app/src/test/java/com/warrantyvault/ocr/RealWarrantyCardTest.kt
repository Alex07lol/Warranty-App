package com.warrantyvault.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Regression tests built from *real* OCR output of three physical warranty cards.
 *
 * The text below is the verbatim line output of an OCR engine (Windows.Media.Ocr) run against
 * the card images — including its mistakes: it reads the two-column "label : value" table as a
 * block of labels followed by a block of values, it misreads one digit of the boAt serial, and
 * it emits logo/QR garbage. ML Kit on Android produces cleaner text for the same cards, so
 * anything that parses correctly here parses correctly there too.
 *
 * These fixtures previously parsed to almost nothing: with the label/value split, Serial
 * Number, Purchase Date and Valid Till were never even seen.
 */
class RealWarrantyCardTest {

    private val asusTufF15Card = """
        IN SEARCH OF INCREDIBLE
        CREATE
        WORK
        BEYOND
        WARRANTY CERTIFICATE
        GENUINE PRODUCTS.
        GREATER POSSIBILITIES.
        PRODUCT NAME
        MODEL NUMBER
        SERIAL NUMBER
        PRODUCT TYPE
        PURCHASE DATE
        WARRANTY PERIOD
        VALID TILL
        INVOICE NUMBER
        TERMS & CONDITIONS
        ASUS TUF Gaming F15
        FX506LH-HN258W
        N8NRCV012345678
        Gaming Laptop
        05 SEP 2025
        2 Years (24 Months)
        04 SEP 2027
        INV/BLR/2025/09304
        BUILT
        WHAT'S
        NEXT
        ASUS India Private Limited
        402, Supreme Chambers,
        17/18, Shah Industrial Estate,
        Veera Desai Road, Andheri (W),
        Mumbai - 400053, India
        www.asus.com/in
        GENUINE
        PRODUCT
        A123456789
        Scan to validate
        warranty status
        or visit
        https://www.asus.com/in/support/
        warranty-status
        *OIA
        < MUMBAI
        o
        z
        c
        a
        1.
        2.
        3.
        4.
        5.
        ASUS
        This warranty covers manufacturing defects in materials and workmanship under normal use.
        The warranty is valid only in India and is non-transferable.
        Accidental damage, liquid damage, misuse, or unauthorized repairs are not covered,
        Please retain this certificate and proof of purchase for warranty claims.
        For detailed terms and conditions, visit www.asus.com/in/support.
        INNOVATION FOR A INCREDIBLE TOMORROW
        S. Kapoor
        Authorized Signatory
        ASUS India Private Limited
        SERVICE I SUPPORT I COMMUNITY
    """.trimIndent()

    private val hpLaptop15sCard = """
        Inventing
        brighter
        tomorrows
        WARRANTY CARD
        CUSTOMER COPY
        HP India Sales Pvt. Ltd.
        24, Salarpuria Arena, Hosur Road
        Adugodi, Bengaluru - 560030
        Karnataka, India
        www.hp.com/in
        Product Name
        Model Number
        Serial Number
        Product Number
        Purchase Date
        Warranty Period
        Valid Till
        Terms & Conditions
        HP Laptop 15s
        15s-eq2143AU
        5CD2458XYZ
        6F8L6PA#ACJ
        12 AUG 2025
        1 Year (12 Months)
        11 AUG 2026
        1.
        2.
        3.
        4.
        5.
        This warranty covers manufacturing defects in materials and workmanship under normal use.
        The warranty is valid only in India and is non-transferable.
        Accidental damage, liquid damage, unauthorized repairs or modifications are not covered.
        Please retain this card and proof of purchase for warranty claims.
        For full warranty terms, visit www.hp.com/in/warranty.
        5CD2458XYZ
        HP Support
        For assistance, visit:
        www.hp.com/in/support
        or call 1800 108 4747
        (Toll Free)
        Authorized Signatory
        HP India Sales Pvt. Ltd.
        Scan for Support
        SALES
        - BENGALURU
        RELIABLE
        INNOVATIVE
        TOGETHER FOR A BETTER TOMORROW
        HP Keep Reinventing
    """.trimIndent()

    private val boatAirdopes141Card = """
        borst
        WARRANTY CARD
        LISTEN BOLDER.
        LIVE
        INDIAN
        BRAND
        FOR A
        BOLDER YOU
        Plug Into Nirvana
        PRODUCT NAME
        PRODUCT TYPE
        MODEL NUMBER
        SERIAL NUMBER
        COLOUR
        PURCHASE DATE
        WARRANTY PERIOD
        VALID TILL
        INVOICE NUMBER
        PURCHASED FROM
        TERMS & CONDITIONS
        LOUDER.
        1 YEAR
        MANUFACTURER
        WARRANTY
        DEDICATED
        CUSTOMER SUPPORT
        HASSLE-FREE
        SERVICE
        souv
        boAt Airdopes 141
        True Wireless Earbuds
        Airdopes 141
        BT1411N2584769
        Active Black
        17 JUL 2025
        1 Year (12 Months)
        16 JUL 2026
        AMZ/2025/0717/45823
        Amazon.in
        49
        Scan for
        warranty registration,
        product support
        and more.
        https://support.boat-lifestyle.com
        1.
        2.
        3.
        4.
        5.
        This warranty covers manufacturing defects in materials and workmanship under normal use.
        The warranty is valid only in India and is non-transferable.
        Physical damage, water damage, misuse, unauthorized repairs or modifications are not covered.
        Please retain this card and proof of purchase for warranty claims.
        For full terms and conditions, visit www.boat-lifestyle.com/pages/warranty.
        Authorized Signatory
        Imagine Marketing Limited
        (boAt)
        AUDIO
        WEARABLES
        ACCESSORIES
        LIFESTYLE
        #DoWhatFloatsYourboAt
    """.trimIndent()

    // ---------- ASUS TUF Gaming F15 warranty certificate ----------

    @Test
    fun `asus warranty certificate parses every field`() {
        val r = OcrParser.parse(asusTufF15Card)

        assertEquals("ASUS TUF Gaming F15", r.productName.value)
        assertEquals("ASUS", r.brand.value)
        assertEquals("FX506LH-HN258W", r.model.value)
        assertEquals("N8NRCV012345678", r.serialNumber.value)
        assertNull(r.imei.value)
        assertEquals(LocalDate.of(2025, 9, 5), LocalDate.parse(r.purchaseDate.value!!))
        assertEquals(24, r.warrantyPeriodMonths.value)
        assertEquals(LocalDate.of(2027, 9, 4), LocalDate.parse(r.warrantyExpiryDate.value!!))
        assertEquals("ASUS India Private Limited", r.purchaseStore.value)
        assertNull("A warranty certificate carries no price", r.purchasePrice.value)
    }

    @Test
    fun `asus card address and hologram digits are not prices`() {
        // 402 (house number), 400053 (postal code), A123456789 (hologram) must stay out.
        val r = OcrParser.parse(asusTufF15Card)
        assertNull(r.purchasePrice.value)
        assertTrue("Price absence should be reported for review", r.warnings.any { it.contains("price", ignoreCase = true) })
    }

    // ---------- HP Laptop 15s warranty card ----------

    @Test
    fun `hp warranty card parses every field`() {
        val r = OcrParser.parse(hpLaptop15sCard)

        assertEquals("HP Laptop 15s", r.productName.value)
        assertEquals("HP", r.brand.value)
        assertEquals("15s-eq2143AU", r.model.value)
        assertEquals("5CD2458XYZ", r.serialNumber.value)
        assertEquals(LocalDate.of(2025, 8, 12), LocalDate.parse(r.purchaseDate.value!!))
        assertEquals(12, r.warrantyPeriodMonths.value)
        assertEquals(LocalDate.of(2026, 8, 11), LocalDate.parse(r.warrantyExpiryDate.value!!))
        assertEquals("HP India Sales Pvt. Ltd.", r.purchaseStore.value)
        assertNull(r.purchasePrice.value)
    }

    @Test
    fun `hp support phone number and postal code are not prices`() {
        // 1800 108 4747 and 560030 must never be read as money.
        val lines = listOf("24, Salarpuria Arena, Hosur Road", "Adugodi, Bengaluru - 560030", "or call 1800 108 4747")
        assertNull(PriceParser.extractPrice(lines))
    }

    // ---------- boAt Airdopes 141 warranty card ----------

    @Test
    fun `boat warranty card parses every field`() {
        val r = OcrParser.parse(boatAirdopes141Card)

        assertEquals("boAt Airdopes 141", r.productName.value)
        assertEquals("boAt", r.brand.value)
        assertEquals("Airdopes 141", r.model.value)
        // The engine misread the printed "BT141IN2584769" (I -> 1); the parser must still
        // capture the serial token so review can correct it.
        assertEquals("BT1411N2584769", r.serialNumber.value)
        assertEquals(LocalDate.of(2025, 7, 17), LocalDate.parse(r.purchaseDate.value!!))
        assertEquals(12, r.warrantyPeriodMonths.value)
        assertEquals(LocalDate.of(2026, 7, 16), LocalDate.parse(r.warrantyExpiryDate.value!!))
        assertEquals("Amazon.in", r.purchaseStore.value)
        assertNull("Amazon invoice number is not a price", r.purchasePrice.value)
    }

    @Test
    fun `aligned lines expose the original values`() {
        val aligned = LabelValueAligner.align(boatAirdopes141Card.lines())
        assertTrue(aligned.contains("SERIAL NUMBER : BT1411N2584769"))
        assertTrue(aligned.contains("PURCHASE DATE : 17 JUL 2025"))
        assertTrue(aligned.contains("VALID TILL : 16 JUL 2026"))
        assertTrue(aligned.contains("INVOICE NUMBER : AMZ/2025/0717/45823"))
        assertTrue(aligned.contains("PURCHASED FROM : Amazon.in"))
        assertTrue(aligned.contains("MODEL NUMBER : Airdopes 141"))
    }

    // ---------- Aligner unit behaviour ----------

    @Test
    fun `column split label and value blocks are aligned in order`() {
        val lines = listOf(
            "SERIAL NUMBER", "PURCHASE DATE", "VALID TILL", "TERMS & CONDITIONS",
            "N8NRCV012345678", "05 SEP 2025", "04 SEP 2027"
        )
        val aligned = LabelValueAligner.align(lines)
        assertTrue(aligned.contains("SERIAL NUMBER : N8NRCV012345678"))
        assertTrue(aligned.contains("PURCHASE DATE : 05 SEP 2025"))
        assertTrue(aligned.contains("VALID TILL : 04 SEP 2027"))
        // Consumed value lines are folded into their labels, not duplicated.
        assertEquals(4, aligned.size)
    }

    @Test
    fun `already single line labels are left untouched`() {
        val lines = listOf("SERIAL NUMBER : N8NRCV012345678", "PURCHASE DATE : 05 SEP 2025")
        assertEquals(lines, LabelValueAligner.align(lines))
    }

    @Test
    fun `text without labelled fields is left untouched`() {
        val lines = listOf("Some Shop", "Coffee 4.50", "Total: 4.50")
        assertEquals(lines, LabelValueAligner.align(lines))
    }

    // ---------- Price hardening ----------

    @Test
    fun `invoice identifiers never become prices`() {
        assertNull(PriceParser.extractPrice(listOf("AMZ/2025/0717/45823")))
        assertNull(PriceParser.extractPrice(listOf("INV/BLR/2025/09304")))
    }

    @Test
    fun `standalone year and short numbers are not prices`() {
        assertNull(PriceParser.extractPrice(listOf("2026")))
        assertNull(PriceParser.extractPrice(listOf("49")))
        assertNull(PriceParser.extractPrice(listOf("1 YEAR")))
    }

    @Test
    fun `bare receipt total is still accepted`() {
        val m = PriceParser.extractPrice(listOf("129999"))
        assertNotNull(m)
        assertEquals(129999.0, m!!.amount, 0.001)
    }

    @Test
    fun `valid till is recognized as the expiry label`() {
        val r = OcrParser.parse("Store\nWidget\nPurchase Date: 05 SEP 2025\nValid Till: 04 SEP 2027")
        assertEquals(LocalDate.of(2025, 9, 5), LocalDate.parse(r.purchaseDate.value!!))
        assertEquals(LocalDate.of(2027, 9, 4), LocalDate.parse(r.warrantyExpiryDate.value!!))
        assertTrue(r.warnings.none { it.contains("No warranty end date", ignoreCase = true) })
    }

    @Test
    fun `labelled purchase store beats the fallback header`() {
        val r = OcrParser.parse("best store ever\nboAt Airdopes 141\nPurchased From: Amazon.in")
        assertEquals("Amazon.in", r.purchaseStore.value)
        assertEquals("boAt", r.brand.value)
    }

    @Test
    fun `product number is never the product name`() {
        val r = OcrParser.parse("Store\nProduct Number: 6F8L6PA#ACJ\nSerial Number: 5CD2458XYZ")
        assertTrue(
            "Product Number must not be read as the product name",
            r.productName.value == null || !r.productName.value!!.contains("6F8L6PA")
        )
    }
}
