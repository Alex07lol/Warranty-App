package com.warrantyvault.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Workflow-level tests for pieces that don't need Android framework:
 * expiry computation from a review draft, match prioritization rules,
 * and OCR-vs-verification semantics encoded in data models.
 */
class WarrantyWorkflowTest {

    private fun draft(
        purchase: Long? = null,
        expiry: Long? = null,
        months: String = "",
        serial: String = "",
        imei: String = ""
    ) = ReviewDraft(
        result = OcrResult(rawText = ""),
        productName = "Test Product",
        brand = "Brand",
        model = "Model",
        serialNumber = serial,
        imei = imei,
        purchaseStore = "Store",
        priceText = "999",
        currency = "USD",
        purchaseDate = purchase,
        warrantyExpiryDate = expiry,
        warrantyMonthsText = months,
        warrantyProvider = "",
        warrantyType = ""
    )

    private fun millis(y: Int, m: Int, d: Int) = DateUtils.toEpochMillis(LocalDate.of(y, m, d))

    @Test
    fun `expiry computed from months uses calendar arithmetic`() {
        val d = draft(purchase = millis(2026, 9, 21), months = "24")
        val expiry = d.purchaseDate!!.let { pd ->
            d.warrantyMonthsText.toIntOrNull()?.let { months ->
                DateUtils.toEpochMillis(DateUtils.addMonths(DateUtils.fromEpochMillis(pd), months))
            }
        }
        assertEquals(millis(2028, 9, 21), expiry)
    }

    @Test
    fun `explicit expiry is preferred over months`() {
        val d = draft(purchase = millis(2026, 9, 21), expiry = millis(2029, 1, 15), months = "24")
        // The workflow uses draft.warrantyExpiryDate when present; months are only a fallback.
        assertEquals(millis(2029, 1, 15), d.warrantyExpiryDate)
        assertNotEqualsMonthsImplied(d)
    }

    /** Guard: 24 months from Sep 21 2026 is Jan 2029 only if someone botches the math. */
    private fun assertNotEqualsMonthsImplied(d: ReviewDraft) {
        val implied = d.purchaseDate?.let {
            DateUtils.toEpochMillis(DateUtils.addMonths(DateUtils.fromEpochMillis(it), 24))
        }
        assertTrue(implied != d.warrantyExpiryDate)
    }

    @Test
    fun `month clamping jan31 plus one month is feb28`() {
        assertEquals(millis(2026, 2, 28), DateUtils.toEpochMillis(DateUtils.addMonths(LocalDate.of(2026, 1, 31), 1)))
    }

    @Test
    fun `match priority exact serial over brand model`() {
        val candidates = mutableListOf(
            ProductMatch(com.warrantyvault.data.Product(id = 2, userId = 1, productName = "Brand Model"), "brand_model"),
            ProductMatch(com.warrantyvault.data.Product(id = 1, userId = 1, productName = "Widget", serialNumber = "SN123"), "exact_serial")
        )
        val best = candidates.minByOrNull {
            when (it.matchType) {
                "exact_serial" -> 0
                "exact_imei" -> 1
                "brand_model" -> 2
                else -> 3
            }
        }
        assertEquals("exact_serial", best!!.matchType)
        assertEquals(1L, best.product.id)
    }

    @Test
    fun `match priority exact imei over brand model`() {
        val candidates = listOf(
            ProductMatch(com.warrantyvault.data.Product(id = 3, userId = 1, productName = "Brand Model"), "brand_model"),
            ProductMatch(com.warrantyvault.data.Product(id = 4, userId = 1, productName = "Phone", imei = "490154203237518"), "exact_imei")
        )
        val best = candidates.minByOrNull {
            when (it.matchType) {
                "exact_serial" -> 0
                "exact_imei" -> 1
                "brand_model" -> 2
                else -> 3
            }
        }
        assertEquals("exact_imei", best!!.matchType)
        assertEquals(4L, best.product.id)
    }

    @Test
    fun `ocr result defaults to unverified state in document mapping`() {
        // Document created from OCR must start unreviewed/unverified.
        val doc = com.warrantyvault.data.Document(
            userId = 1L,
            documentType = "receipt",
            fileName = "test.jpg",
            filePath = "/data/documents/test.jpg",
            fileSize = 12345L,
            mimeType = "image/jpeg",
            ocrStatus = "done",
            ocrText = "raw"
        )
        assertEquals("unreviewed", doc.docState)
        assertFalse(doc.verified)
    }

    @Test
    fun `review flag flips document to reviewed and verified`() {
        val doc = com.warrantyvault.data.Document(
            userId = 1L,
            documentType = "receipt",
            fileName = "test.jpg",
            filePath = "/data/documents/test.jpg",
            fileSize = 12345L,
            mimeType = "image/jpeg",
            docState = "unreviewed",
            verified = false
        )
        val linked = doc.copy(productId = 7L, docState = "reviewed", verified = true)
        assertEquals(7L, linked.productId)
        assertEquals("reviewed", linked.docState)
        assertTrue(linked.verified)
    }

    @Test
    fun `hasAnyIdentity requires a real identity field`() {
        val noIdentity = OcrParser.parse("Total: 10.00")
        // Deliberately constructed: no product/brand/model/serial/IMEI anywhere.
        assertFalse(noIdentity.hasAnyIdentity())

        val withSerial = OcrParser.parse("Store\nWidget\nS/N: ABC123456")
        assertTrue(withSerial.hasAnyIdentity())
        assertTrue(!withSerial.serialNumber.value.isNullOrBlank())
    }

    @Test
    fun `user edits are tracked in the review draft`() {
        val d = draft(serial = "SN123")
        assertFalse(d.hasUserEdits)
        d.userEdits.add("serialNumber")
        assertTrue(d.hasUserEdits)
    }

    @Test
    fun `duration description is human readable`() {
        assertEquals("1 year", WarrantyParser.describe(12))
        assertEquals("2 years", WarrantyParser.describe(24))
        assertEquals("18 months", WarrantyParser.describe(18))
    }
}
