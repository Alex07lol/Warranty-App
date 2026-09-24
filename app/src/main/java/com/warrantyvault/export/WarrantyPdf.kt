package com.warrantyvault.export

/**
 * Pure-Kotlin PDF 1.4 builder for the WarrantyVault "Product Passport & Warranty Record".
 *
 * Mirrors the layout of the web export (API/backend/src/services/export.service.js → buildPdf):
 * one page per product inside an explicit frame, sections for product/purchase specs,
 * warranty coverage, tags & notes, and service history. No Android or third-party
 * dependencies, so it is unit-testable on the JVM and safe to call from any layer.
 *
 * The builder consumes pre-formatted view models ([PassportProduct]) — date and price
 * formatting stay with the caller so this file never touches locales or Room.
 */
object WarrantyPdf {

    // ─── View models (filled by PdfExportService from Room entities) ────────────

    data class PassportService(
        val date: String?,          // pre-formatted yyyy-MM-dd or null
        val type: String?,
        val provider: String?,
        val cost: Double?,
        val currency: String?,
        val nextDate: String?
    )

    data class PassportProduct(
        val productName: String,
        val brand: String? = null,
        val model: String? = null,
        val category: String? = null,
        val serialNumber: String? = null,
        val imei: String? = null,
        val purchaseDate: String? = null,
        val purchasePrice: Double? = null,
        val currency: String? = null,
        val purchaseStore: String? = null,
        /** active | expiring_soon | expired | not_started | unknown */
        val warrantyStatus: String = "unknown",
        val warrantyExpiryDate: String? = null,
        val warrantyPeriodMonths: Int? = null,
        val warrantyProvider: String? = null,
        val warrantyProviderType: String? = null,
        val warrantyContact: String? = null,
        val lifecycleStatus: String = "owned",
        val tags: List<String> = emptyList(),
        val notes: String? = null,
        val services: List<PassportService> = emptyList()
    )

    // ─── Page geometry (A4 portrait, PDF points, origin bottom-left) ────────────

    private const val PAGE_W = 595.28
    private const val PAGE_H = 841.89

    // Ink & accent colors (RGB 0..1)
    private const val INK = "0.06 0.09 0.16"
    private const val MUTED = "0.40 0.46 0.55"
    private const val FAINT = "0.52 0.58 0.66"
    private const val NAVY = "0.08 0.12 0.22"
    private const val SLATE = "0.20 0.28 0.42"
    private const val PANEL = "0.97 0.98 0.99"
    private const val WHITE = "1 1 1"
    private const val CARD = "1 1 1"
    private const val HEADER_BG = "0.94 0.96 0.98"
    private const val CARD_BORDER = "0.85 0.88 0.92"
    private const val RULE = "0.92 0.94 0.96"

    // ─── Public entry point ─────────────────────────────────────────────────────

    /**
     * Builds a PDF with one passport page per product. An empty list produces a
     * single "vault is empty" page. Returns the complete PDF file bytes.
     */
    fun buildPassports(products: List<PassportProduct>, generatedAt: String): ByteArray {
        val streams = if (products.isEmpty()) {
            listOf(emptyVaultPage(generatedAt))
        } else {
            products.mapIndexed { index, p -> passportPage(p, index + 1, products.size, generatedAt) }
        }
        return assemble(streams)
    }

    // ─── Content stream builders ────────────────────────────────────────────────

    private class Ops {
        val sb = StringBuilder()

        fun rect(x: Double, y: Double, w: Double, h: Double, fill: String? = null, stroke: String? = null, lineW: Double = 1.0) {
            if (fill != null) sb.append("$fill rg $x $y $w $h re f\n")
            if (stroke != null) sb.append("$stroke RG $lineW w $x $y $w $h re S\n")
        }

        fun text(x: Double, y: Double, size: Double, font: String, color: String, s: String) {
            sb.append("BT /$font ${trim(size)} Tf $color rg $x $y Td (${escape(s)}) Tj ET\n")
        }

        fun build(): ByteArray = sb.toString().toByteArray(Charsets.ISO_8859_1)
    }

    private fun trim(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    private fun passportPage(p: PassportProduct, pageNum: Int, totalPages: Int, timestamp: String): ByteArray {
        val o = Ops()

        // Canvas + outer frame
        o.rect(0.0, 0.0, PAGE_W, PAGE_H, fill = WHITE)
        o.rect(36.0, 36.0, 523.28, 769.89, fill = PANEL, stroke = "0.58 0.64 0.72", lineW = 1.5)

        // Header band
        o.rect(36.0, 760.0, 523.28, 45.89, fill = NAVY)
        o.text(52.0, 778.0, 13.0, "F2", WHITE, "WARRANTYVAULT")
        o.text(180.0, 778.0, 9.0, "F1", "0.65 0.8 0.95", "PRODUCT PASSPORT & WARRANTY RECORD")
        o.text(480.0, 778.0, 8.5, "F1", "0.8 0.85 0.95", "Page $pageNum of $totalPages")

        // Title + subtitle
        o.text(52.0, 732.0, 16.0, "F2", INK, truncate(p.productName.ifBlank { "Unnamed Product" }, 16.0, "F2", 360.0))
        val subtitle = listOfNotNull(p.brand, p.model, p.category).joinToString("  |  ").ifBlank { "Registered Asset" }
        o.text(52.0, 715.0, 9.5, "F1", MUTED, truncate(subtitle, 9.5, "F1", 370.0))

        // Status pill (top right)
        val (statusLabel, statusColor) = statusDisplay(p.warrantyStatus)
        o.rect(430.0, 722.0, 115.0, 20.0, fill = "0.9 0.92 0.95", stroke = "0.75 0.8 0.88")
        o.text(438.0, 728.0, 8.0, "F2", statusColor, truncate("STATUS: $statusLabel", 8.0, "F2", 100.0))

        // ── Section 1: product & purchase specifications ──
        section(o, 50.0, 575.0, 495.0, 122.0, "PRODUCT & PURCHASE SPECIFICATIONS", "0.15 0.25 0.45")
        kv(o, 60.0, 652.0, 150.0, "Serial Number:", p.serialNumber ?: "Not recorded", mono = p.serialNumber != null)
        kv(o, 60.0, 632.0, 150.0, "IMEI:", p.imei ?: "Not recorded", mono = p.imei != null)
        kv(o, 60.0, 612.0, 150.0, "Category:", p.category ?: "General")
        kv(o, 60.0, 592.0, 150.0, "Store:", p.purchaseStore ?: "Not specified")
        kv(o, 310.0, 652.0, 405.0, "Purchase Date:", p.purchaseDate ?: "N/A")
        kv(o, 310.0, 632.0, 405.0, "Purchase Price:", formatPrice(p.purchasePrice, p.currency), bold = true)
        kv(o, 310.0, 612.0, 405.0, "Lifecycle:", lifecycleLabel(p.lifecycleStatus))

        // ── Section 2: warranty & coverage ──
        section(o, 50.0, 440.0, 495.0, 120.0, "WARRANTY & COVERAGE PROTECTION", "0.01 0.45 0.72")
        kv(o, 60.0, 512.0, 150.0, "Provider:", p.warrantyProvider ?: "Direct Manufacturer", bold = true)
        kv(o, 60.0, 492.0, 150.0, "Provider Type:", p.warrantyProviderType ?: "Standard")
        kv(o, 60.0, 472.0, 150.0, "Support Contact:", p.warrantyContact ?: "Refer to store receipt")
        kv(o, 310.0, 512.0, 400.0, "Warranty Expiry:", p.warrantyExpiryDate ?: "No date recorded", bold = true)
        o.text(400.0, 492.0, 9.0, "F2", statusColor, statusLabel)
        kv(o, 310.0, 472.0, 400.0, "Coverage Period:", coverageLabel(p))

        // ── Section 3: tags & notes ──
        section(o, 50.0, 345.0, 495.0, 80.0, "TAGS & NOTES", "0.25 0.3 0.4")
        kv(o, 60.0, 380.0, 110.0, "Tags:", p.tags.joinToString(", ").ifBlank { "None" })
        val notes = p.notes?.replace("\n", " ")?.trim().orEmpty()
        o.text(60.0, 360.0, 8.5, "F1", MUTED, "Notes:")
        o.text(110.0, 360.0, 9.0, "F1", INK, truncate(notes.ifBlank { "None recorded" }, 9.0, "F1", 415.0))

        // ── Section 4: service history ──
        section(o, 50.0, 105.0, 495.0, 230.0, "SERVICE & MAINTENANCE HISTORY", "0.25 0.3 0.4")
        o.text(60.0, 296.0, 8.0, "F2", "0.3 0.35 0.45", "DATE")
        o.text(150.0, 296.0, 8.0, "F2", "0.3 0.35 0.45", "SERVICE TYPE")
        o.text(250.0, 296.0, 8.0, "F2", "0.3 0.35 0.45", "PROVIDER")
        o.text(370.0, 296.0, 8.0, "F2", "0.3 0.35 0.45", "COST")
        o.text(460.0, 296.0, 8.0, "F2", "0.3 0.35 0.45", "NEXT SERVICE")
        o.rect(60.0, 290.0, 475.0, 0.5, fill = CARD_BORDER)

        if (p.services.isEmpty()) {
            o.text(60.0, 270.0, 9.0, "F3", FAINT, "No maintenance or service history recorded for this product.")
        } else {
            val rows = p.services.take(8)
            rows.forEachIndexed { i, s ->
                val y = 272.0 - i * 21.0
                o.text(60.0, y, 8.5, "F1", INK, truncate(s.date ?: "-", 8.5, "F1", 80.0))
                o.text(150.0, y, 8.5, "F1", INK, truncate(s.type ?: "Service", 8.5, "F1", 90.0))
                o.text(250.0, y, 8.5, "F1", INK, truncate(s.provider ?: "-", 8.5, "F1", 110.0))
                o.text(370.0, y, 8.5, "F1", INK, formatPrice(s.cost, s.currency))
                o.text(460.0, y, 8.5, "F1", INK, truncate(s.nextDate ?: "-", 8.5, "F1", 75.0))
                if (i < rows.size - 1) o.rect(60.0, y - 6.0, 475.0, 0.25, fill = RULE)
            }
            if (p.services.size > rows.size) {
                o.text(60.0, 272.0 - rows.size * 21.0, 8.5, "F3", FAINT,
                    "+ ${p.services.size - rows.size} more service record(s) — see the full export.")
            }
        }

        footer(o, timestamp, pageNum, totalPages)
        return o.build()
    }

    private fun emptyVaultPage(timestamp: String): ByteArray {
        val o = Ops()
        o.rect(0.0, 0.0, PAGE_W, PAGE_H, fill = WHITE)
        o.rect(36.0, 36.0, 523.28, 769.89, fill = PANEL, stroke = "0.58 0.64 0.72", lineW = 1.5)
        o.rect(36.0, 760.0, 523.28, 45.89, fill = NAVY)
        o.text(52.0, 778.0, 13.0, "F2", WHITE, "WARRANTYVAULT")
        o.text(180.0, 778.0, 9.0, "F1", "0.65 0.8 0.95", "PRODUCT PASSPORT & WARRANTY RECORD")
        o.text(480.0, 778.0, 8.5, "F1", "0.8 0.85 0.95", "Page 1 of 1")

        o.rect(100.0, 370.0, 395.0, 120.0, fill = CARD, stroke = "0.8 0.85 0.9")
        o.text(130.0, 445.0, 13.0, "F2", "0.15 0.2 0.3", "NO PRODUCTS IN VAULT")
        o.text(130.0, 420.0, 10.0, "F1", "0.4 0.45 0.55", "Your WarrantyVault library is currently empty.")
        o.text(130.0, 398.0, 9.0, "F1", FAINT, "Add products to generate warranty passports.")

        footer(o, timestamp, 1, 1)
        return o.build()
    }

    private fun footer(o: Ops, timestamp: String, pageNum: Int, totalPages: Int) {
        o.rect(50.0, 78.0, 495.0, 0.5, fill = CARD_BORDER)
        o.text(52.0, 64.0, 7.5, "F1", MUTED, "Generated by WarrantyVault (Android) on $timestamp")
        o.text(52.0, 50.0, 7.5, "F1", FAINT, "Confidential document | Authentic vault export | Page $pageNum of $totalPages")
    }

    // ─── Small layout helpers ───────────────────────────────────────────────────

    private fun section(o: Ops, x: Double, y: Double, w: Double, h: Double, title: String, titleColor: String) {
        o.rect(x, y, w, h, fill = CARD, stroke = CARD_BORDER)
        o.rect(x, y + h - 24.0, w, 24.0, fill = HEADER_BG)
        o.text(x + 10.0, y + h - 16.0, 9.0, "F2", titleColor, title)
    }

    private fun kv(o: Ops, labelX: Double, y: Double, valueX: Double, label: String, value: String, bold: Boolean = false, mono: Boolean = false) {
        o.text(labelX, y, 8.5, "F1", MUTED, label)
        o.text(valueX, y, 9.0, if (mono) "F5" else if (bold) "F2" else "F1", INK, value)
    }

    private fun statusDisplay(status: String): Pair<String, String> = when (status) {
        "active" -> "ACTIVE" to "0.09 0.64 0.29"
        "expiring_soon" -> "EXPIRING SOON" to "0.85 0.55 0.05"
        "expired" -> "EXPIRED" to "0.86 0.15 0.15"
        "not_started" -> "NOT STARTED" to "0.15 0.35 0.75"
        else -> "UNSPECIFIED" to "0.4 0.45 0.5"
    }

    private fun lifecycleLabel(status: String): String =
        status.replace('_', ' ').uppercase().ifBlank { "OWNED" }

    private fun coverageLabel(p: PassportProduct): String =
        p.warrantyPeriodMonths?.let { "$it Months" } ?: "Standard Term"

    private fun formatPrice(price: Double?, currency: String?): String {
        if (price == null) return "N/A"
        val formatted = if (price % 1.0 == 0.0) "%,.0f" else "%,.2f"
        val num = formatted.format(java.util.Locale.US, price)
        return if (currency.isNullOrBlank()) num else "$currency $num"
    }

    // ─── Text safety: WinAnsi + escaping + width-aware truncation ───────────────

    /** Maps typographic/non-Latin chars to their WinAnsi-safe equivalents. Shared by escape and truncate so folding happens exactly once, before width math. */
    private fun asciiFold(s: String): String = s
        .replace("₹", "INR ")
        .replace("[•●]".toRegex(), "-")
        .replace("[\u2018\u2019]".toRegex(), "'")
        .replace("[\u201C\u201D]".toRegex(), "\"")
        .replace("[\u2013\u2014]".toRegex(), "-")

    /** Normalizes to WinAnsi-safe ASCII and escapes PDF string syntax. */
    fun escape(s: String): String = asciiFold(s)
        .replace(Regex("[^\\x20-\\x7E]"), "?")
        .replace("\\", "\\\\")
        .replace("(", "\\(")
        .replace(")", "\\)")

    /** Average glyph width as a fraction of font size (good enough for truncation). */
    private fun widthFactor(font: String): Double = when (font) {
        "F2", "F5" -> 0.56   // bold
        "F3" -> 0.52         // oblique
        "F4" -> 0.60         // courier
        else -> 0.52         // helvetica
    }

    /** Ellipsizes text so it fits `maxWidth` points at `size` in `font`. */
    fun truncate(text: String, size: Double, font: String, maxWidth: Double): String {
        val s = asciiFold(text).replace(Regex("[^\\x20-\\x7E]"), "?")
        if (s.isEmpty()) return s
        val maxChars = (maxWidth / (size * widthFactor(font))).toInt()
        if (s.length <= maxChars) return s
        if (maxChars <= 3) return s.take(maxChars.coerceAtLeast(1))
        return s.take(maxChars - 3) + "..."
    }

    // ─── PDF file assembly (xref table, 5 fonts, one content stream per page) ──

    private fun assemble(pageStreams: List<ByteArray>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val offsets = mutableListOf<Int>()

        fun write(bytes: ByteArray) {
            offsets.add(out.size())
            out.write(bytes)
        }

        fun obj(content: String) = write(content.toByteArray(Charsets.ISO_8859_1))
        fun streamObj(dict: String, stream: ByteArray) {
            val head = (dict.replace("%%LENGTH%%", stream.size.toString()) + "\nstream\n").toByteArray(Charsets.ISO_8859_1)
            val tail = "\nendstream\nendobj\n".toByteArray(Charsets.ISO_8859_1)
            val total = head.size + stream.size + tail.size
            val buf = ByteArray(total)
            head.copyInto(buf); stream.copyInto(buf, head.size); tail.copyInto(buf, head.size + stream.size)
            write(buf)
        }

        // Header + binary comment line
        out.write(byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 0x0A)) // %PDF-1.4\n
        out.write(byteArrayOf(0x25, 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), 0x0A))
        offsets.clear()
        offsets.add(0)

        val kids = pageStreams.indices.joinToString(" ") { "${8 + 2 * it} 0 R" }

        obj("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        obj("2 0 obj\n<< /Type /Pages /Kids [$kids] /Count ${pageStreams.size} >>\nendobj\n")
        obj("3 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\n")
        obj("4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>\nendobj\n")
        obj("5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Oblique /Encoding /WinAnsiEncoding >>\nendobj\n")
        obj("6 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>\nendobj\n")
        obj("7 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Courier-Bold /Encoding /WinAnsiEncoding >>\nendobj\n")

        pageStreams.forEachIndexed { i, stream ->
            val pageObj = 8 + 2 * i
            obj(
                "$pageObj 0 obj\n<<\n  /Type /Page\n  /Parent 2 0 R\n  /MediaBox [0 0 $PAGE_W $PAGE_H]\n" +
                    "  /Resources << /Font << /F1 3 0 R /F2 4 0 R /F3 5 0 R /F4 6 0 R /F5 7 0 R >> >>\n" +
                    "  /Contents ${pageObj + 1} 0 R\n>>\nendobj\n"
            )
            streamObj("${pageObj + 1} 0 obj\n<< /Length %%LENGTH%% >>", stream)
        }

        val startxref = out.size()
        val xref = buildString {
            append("xref\n0 ${offsets.size}\n")
            append("0000000000 65535 f \n")
            for (i in 1 until offsets.size) append(String.format("%010d 00000 n \n", offsets[i]))
            append("trailer\n<< /Size ${offsets.size} /Root 1 0 R >>\nstartxref\n$startxref\n%%EOF\n")
        }
        out.write(xref.toByteArray(Charsets.ISO_8859_1))
        return out.toByteArray()
    }
}
