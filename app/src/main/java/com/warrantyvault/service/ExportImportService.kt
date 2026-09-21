package com.warrantyvault.service

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.warrantyvault.data.AppDatabase
import com.warrantyvault.data.Product
import com.warrantyvault.ocr.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

class ExportImportService(private val context: Context, private val db: AppDatabase) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val CSV_HEADERS = listOf(
        "productName", "brand", "model", "category", "serialNumber", "purchaseDate",
        "purchasePrice", "currency", "purchaseStore",
        "warrantyExpiryDate", "lifecycleStatus", "serviceHistory"
    )

    // ---------------- Export (unchanged behavior, preserved) ----------------

    suspend fun exportJson(): Uri = withContext(Dispatchers.IO) {
        val products = db.productDao().getAllForUser(currentUserId())
        val productIds = products.map { it.id }
        val services = db.serviceHistoryDao().getByProductIds(productIds)
        val warranties = db.warrantyPeriodDao().getByProductIds(productIds)

        val serviceByProduct = services.groupBy { it.productId }
        val warrantyByProduct = warranties.groupBy { it.productId }

        val exportData = mapOf(
            "exportedAt" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()),
            "count" to products.size,
            "products" to products.map { product ->
                val productServices = serviceByProduct[product.id] ?: emptyList()
                val productWarranties = warrantyByProduct[product.id] ?: emptyList()

                mapOf(
                    "id" to product.id,
                    "productName" to product.productName,
                    "brand" to product.brand,
                    "model" to product.model,
                    "category" to product.category,
                    "serialNumber" to product.serialNumber,
                    "imei" to product.imei,
                    "purchaseDate" to product.purchaseDate?.let { epochToDate(it) },
                    "purchasePrice" to product.purchasePrice,
                    "currency" to product.currency,
                    "purchaseStore" to product.purchaseStore,
                    "warrantyExpiryDate" to product.warrantyExpiryDate?.let { epochToDate(it) },
                    "warrantyPeriodMonths" to product.warrantyPeriodMonths,
                    "warrantyProvider" to product.warrantyProvider,
                    "warrantyProviderType" to product.warrantyProviderType,
                    "warrantyContact" to product.warrantyContact,
                    "warrantyWebsite" to product.warrantyWebsite,
                    "lifecycleStatus" to product.lifecycleStatus,
                    "tags" to product.tags,
                    "notes" to product.notes,
                    "warranties" to productWarranties.map { w ->
                        mapOf(
                            "type" to w.type,
                            "provider" to w.provider,
                            "coverage" to w.coverage,
                            "startDate" to w.startDate?.let { epochToDate(it) },
                            "expiryDate" to w.expiryDate?.let { epochToDate(it) }
                        )
                    },
                    "serviceHistory" to productServices.map { s ->
                        mapOf(
                            "serviceDate" to epochToDate(s.serviceDate),
                            "serviceType" to s.serviceType,
                            "serviceProvider" to s.serviceProvider,
                            "cost" to s.cost,
                            "currency" to s.currency,
                            "description" to s.description,
                            "nextServiceDate" to s.nextServiceDate?.let { epochToDate(it) }
                        )
                    }
                )
            }
        )

        val jsonString = json.encodeToString(exportData)
        return@withContext saveToDownloads(jsonString, "warrantyvault-export-${System.currentTimeMillis()}.json", "application/json")
    }

    suspend fun exportCsv(): Uri = withContext(Dispatchers.IO) {
        val products = db.productDao().getAllForUser(currentUserId())
        val productIds = products.map { it.id }
        val services = db.serviceHistoryDao().getByProductIds(productIds)
        val serviceByProduct = services.groupBy { it.productId }

        val rows = mutableListOf<String>()
        rows.add(CSV_HEADERS.joinToString(","))

        for (product in products) {
            val productServices = serviceByProduct[product.id] ?: emptyList()
            val serviceStr = productServices.map { s ->
                listOf(
                    epochToDate(s.serviceDate),
                    s.serviceType,
                    s.serviceProvider ?: "",
                    s.cost?.toString() ?: "",
                    s.nextServiceDate?.let { epochToDate(it) } ?: ""
                ).filter { it.isNotBlank() }.joinToString(" | ")
            }.joinToString(" ;; ")

            val row = listOf(
                escapeCsv(product.productName),
                escapeCsv(product.brand ?: ""),
                escapeCsv(product.model ?: ""),
                escapeCsv(product.category ?: ""),
                escapeCsv(product.serialNumber ?: ""),
                escapeCsv(product.purchaseDate?.let { epochToDate(it) } ?: ""),
                escapeCsv(product.purchasePrice?.toString() ?: ""),
                escapeCsv(product.currency),
                escapeCsv(product.purchaseStore ?: ""),
                escapeCsv(product.warrantyExpiryDate?.let { epochToDate(it) } ?: ""),
                escapeCsv(product.lifecycleStatus),
                escapeCsv(serviceStr)
            )
            rows.add(row.joinToString(","))
        }

        val csvString = rows.joinToString("\n")
        return@withContext saveToDownloads(csvString, "warrantyvault-export-${System.currentTimeMillis()}.csv", "text/csv")
    }

    private fun epochToDate(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

    private fun currentUserId(): Long =
        (context.applicationContext as com.warrantyvault.WarrantyVaultApplication).currentUserId

    private fun escapeCsv(value: String): String {
        var s = value
        if (s.startsWith("=") || s.startsWith("+") || s.startsWith("-") || s.startsWith("@") || s.startsWith("\t")) {
            s = "'$s"
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"${s.replace("\"", "\"\"")}\""
        }
        return s
    }

    private fun saveToDownloads(content: String, fileName: String, mimeType: String): Uri {
        val resolver = context.contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)!!
        resolver.openOutputStream(uri).use { outputStream ->
            outputStream?.write(content.toByteArray())
        }
        return uri
    }

    // ---------------- Import: parse -> normalize -> validate -> preview -> commit ----------------

    /** Result of parsing a file into validated candidates (no DB writes). */
    data class ImportPreview(
        val valid: List<ParsedProduct>,
        val rejected: List<ImportIssue>,
        val duplicatesInFile: Int
    )

    data class ParsedProduct(
        val productName: String,
        val brand: String? = null,
        val model: String? = null,
        val category: String? = null,
        val serialNumber: String? = null,
        val imei: String? = null,
        val purchaseDate: Long? = null,
        val purchasePrice: Double? = null,
        val currency: String = "USD",
        val purchaseStore: String? = null,
        val warrantyExpiryDate: Long? = null,
        val warrantyPeriodMonths: Int? = null,
        val warrantyProvider: String? = null,
        val warrantyProviderType: String? = null,
        val warrantyContact: String? = null,
        val warrantyWebsite: String? = null,
        val lifecycleStatus: String = "owned",
        val tags: String = "[]",
        val notes: String? = null
    )

    data class ImportIssue(val rowIndex: Int, val reason: String)

    @Serializable
    data class ImportSummary(
        val imported: Int,
        val rejected: Int,
        val duplicatesSkipped: Int,
        val warnings: List<String>
    )

    suspend fun buildPreview(uri: Uri): ImportPreview = withContext(Dispatchers.IO) {
        val text = readText(uri)
            ?: return@withContext ImportPreview(emptyList(), listOf(ImportIssue(-1, "Failed to open file")), 0)

        val mime = context.contentResolver.getType(uri) ?: ""
        val rows: List<Map<String, String?>> = when {
            mime.contains("json") || text.trimStart().startsWith("{") || text.trimStart().startsWith("[") ->
                parseJsonRows(text).fold(
                    onSuccess = { it },
                    onFailure = { return@withContext ImportPreview(emptyList(), listOf(ImportIssue(-1, "Invalid JSON: ${it.message}")), 0) }
                )
            mime.contains("csv") || mime.contains("comma") -> parseCsv(text).map { r -> r.mapValues { it.value as String? } }
            else -> return@withContext ImportPreview(
                emptyList(),
                listOf(ImportIssue(-1, "Unsupported file type. Please choose a JSON export.")),
                0
            )
        }

        val valid = mutableListOf<ParsedProduct>()
        val rejected = mutableListOf<ImportIssue>()
        val seenSerials = mutableSetOf<String>()
        val seenImeis = mutableSetOf<String>()
        var inFileDupes = 0

        for ((index, row) in rows.withIndex()) {
            val name = row["productName"]?.trim()
            if (name.isNullOrEmpty()) {
                rejected.add(ImportIssue(index, "productName is required"))
                continue
            }
            if (row["purchaseDate"].isNullOrEmpty() && row["warrantyExpiryDate"].isNullOrEmpty() && row["warrantyPeriodMonths"].isNullOrEmpty()) {
                // Not fatal, but flagged later as a warning in the summary.
            }
            val serial = row["serialNumber"]?.trim()?.ifBlank { null }
            if (serial != null && !seenSerials.add(serial.uppercase())) {
                inFileDupes++
                rejected.add(ImportIssue(index, "Duplicate serial in file: $serial"))
                continue
            }
            val imei = row["imei"]?.trim()?.ifBlank { null }
            if (imei != null) {
                val digits = imei.filter { it.isDigit() }
                if (digits.length !in 14..16) {
                    rejected.add(ImportIssue(index, "Invalid IMEI: '$imei' (expected 14-16 digits)"))
                    continue
                }
                if (!seenImeis.add(digits)) {
                    inFileDupes++
                    rejected.add(ImportIssue(index, "Duplicate IMEI in file: $imei"))
                    continue
                }
            }
            val price = row["purchasePrice"]?.let { parseFlexibleDouble(it) }
            if (row["purchasePrice"] != null && row["purchasePrice"]!!.isNotBlank() && price == null) {
                rejected.add(ImportIssue(index, "Invalid purchasePrice: '${row["purchasePrice"]}'"))
                continue
            }
            val purchaseDate = row["purchaseDate"]?.let { parseFlexibleDate(it) }
            if (row["purchaseDate"]?.isNotBlank() == true && purchaseDate == null) {
                rejected.add(ImportIssue(index, "Invalid purchaseDate: '${row["purchaseDate"]}' (expected yyyy-MM-dd)"))
                continue
            }
            val expiryDate = row["warrantyExpiryDate"]?.let { parseFlexibleDate(it) }
            if (row["warrantyExpiryDate"]?.isNotBlank() == true && expiryDate == null) {
                rejected.add(ImportIssue(index, "Invalid warrantyExpiryDate: '${row["warrantyExpiryDate"]}'"))
                continue
            }
            if (purchaseDate != null && expiryDate != null && expiryDate < purchaseDate) {
                rejected.add(ImportIssue(index, "Warranty expiry is before purchase date"))
                continue
            }
            val months = row["warrantyPeriodMonths"]?.let { parseFlexibleInt(it) }
            if (row["warrantyPeriodMonths"]?.isNotBlank() == true && months == null) {
                rejected.add(ImportIssue(index, "Invalid warrantyPeriodMonths: '${row["warrantyPeriodMonths"]}'"))
                continue
            }

            valid.add(
                ParsedProduct(
                    productName = name,
                    brand = row["brand"]?.ifBlank { null },
                    model = row["model"]?.ifBlank { null },
                    category = row["category"]?.ifBlank { null },
                    serialNumber = serial,
                    imei = imei,
                    purchaseDate = purchaseDate,
                    purchasePrice = price,
                    currency = row["currency"]?.ifBlank { null } ?: "USD",
                    purchaseStore = row["purchaseStore"]?.ifBlank { null },
                    warrantyExpiryDate = expiryDate,
                    warrantyPeriodMonths = months,
                    warrantyProvider = row["warrantyProvider"]?.ifBlank { null },
                    warrantyProviderType = row["warrantyProviderType"]?.ifBlank { null },
                    warrantyContact = row["warrantyContact"]?.ifBlank { null },
                    warrantyWebsite = row["warrantyWebsite"]?.ifBlank { null },
                    lifecycleStatus = row["lifecycleStatus"]?.ifBlank { null } ?: "owned",
                    tags = row["tags"]?.ifBlank { null } ?: "[]",
                    notes = row["notes"]?.ifBlank { null }
                )
            )
        }

        ImportPreview(valid, rejected, inFileDupes)
    }

    /**
     * Commits a validated preview. Existing records are never modified or destroyed:
     * rows whose serial matches an existing product are skipped and counted as duplicates.
     */
    suspend fun commitImport(preview: ImportPreview): ImportSummary = withContext(Dispatchers.IO) {
        val userId = currentUserId()
        var imported = 0
        var duplicatesSkipped = 0
        val warnings = mutableListOf<String>()
        var noDates = 0

        for (p in preview.valid) {
            if (p.serialNumber != null) {
                val existing = db.productDao().getProductBySerialNumber(userId, p.serialNumber)
                if (existing != null) {
                    duplicatesSkipped++
                    continue
                }
            }
            if (p.imei != null) {
                val existingByImei = db.productDao().getProductByImei(userId, p.imei)
                if (existingByImei != null) {
                    duplicatesSkipped++
                    continue
                }
            }
            if (p.purchaseDate == null && p.warrantyExpiryDate == null) noDates++
            db.productDao().insertProduct(
                Product(
                    userId = userId,
                    productName = p.productName,
                    brand = p.brand,
                    model = p.model,
                    category = p.category,
                    serialNumber = p.serialNumber,
                    imei = p.imei,
                    purchaseDate = p.purchaseDate,
                    purchasePrice = p.purchasePrice,
                    currency = p.currency,
                    purchaseStore = p.purchaseStore,
                    warrantyExpiryDate = p.warrantyExpiryDate,
                    warrantyPeriodMonths = p.warrantyPeriodMonths,
                    warrantyProvider = p.warrantyProvider,
                    warrantyProviderType = p.warrantyProviderType,
                    warrantyContact = p.warrantyContact,
                    warrantyWebsite = p.warrantyWebsite,
                    lifecycleStatus = p.lifecycleStatus,
                    tags = p.tags,
                    notes = p.notes
                )
            )
            imported++
        }

        if (noDates > 0) warnings.add("$noDates record(s) have no purchase or warranty dates.")
        ImportSummary(imported, preview.rejected.size, duplicatesSkipped, warnings)
    }

    /** Convenience for callers that don't need the interactive preview step. */
    suspend fun importJsonValidated(uri: Uri): ImportSummary {
        val preview = buildPreview(uri)
        return commitImport(preview)
    }

    private fun readText(uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { it.reader(Charsets.UTF_8).readText() }
    } catch (e: Exception) {
        null
    }

    private fun parseJsonRows(text: String): Result<List<Map<String, String?>>> = runCatching {
        val parsed = json.decodeFromString<JsonElement>(text)
        val productsArray: kotlinx.serialization.json.JsonArray = when {
            parsed is kotlinx.serialization.json.JsonObject && parsed.containsKey("products") ->
                parsed["products"]?.jsonArray ?: throw IllegalArgumentException("No products array found")
            parsed is kotlinx.serialization.json.JsonArray -> parsed
            else -> throw IllegalArgumentException("No products array found")
        }
        productsArray.map { el ->
            val obj = el.jsonObject
            obj.mapValues { (_, v) ->
                runCatching { v.jsonPrimitive.content }.getOrNull()
            }
        }
    }

    private fun parseFlexibleDouble(raw: String): Double? =
        raw.trim().replace(Regex("^[^0-9\\-]+"), "").replace(",", "").toDoubleOrNull()

    private fun parseFlexibleInt(raw: String): Int? = raw.trim().toIntOrNull()

    private fun parseFlexibleDate(raw: String): Long? {
        val s = raw.trim()
        if (s.isBlank()) return null
        // ISO first (export format), then common alternatives.
        runCatching { return DateUtils.toEpochMillis(LocalDate.parse(s)) }
        for (fmt in listOf("yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", "dd-MM-yyyy", "dd.MM.yyyy")) {
            runCatching {
                val df = SimpleDateFormat(fmt, Locale.US).apply { isLenient = false }
                val d = df.parse(s) ?: return@runCatching
                return d.time
            }
        }
        return null
    }

    // ---------------- Legacy CSV parser (kept for completeness) ----------------

    fun parseCsv(text: String): List<Map<String, String>> {
        val s = text.replace("\uFEFF", "")
        val rawRows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        var field = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < s.length) {
            val ch = s[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') {
                        field.append('"')
                        i += 2
                        continue
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(ch)
                }
            } else if (ch == '"') {
                inQuotes = true
            } else if (ch == ',') {
                row.add(field.toString())
                field = StringBuilder()
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < s.length && s[i + 1] == '\n') i++
                row.add(field.toString())
                field = StringBuilder()
                if (row.size > 1 || row.firstOrNull()?.isNotBlank() == true) rawRows.add(row.toList())
                row.clear()
            } else {
                field.append(ch)
            }
            i++
        }

        row.add(field.toString())
        if (row.size > 1 || row.firstOrNull()?.isNotBlank() == true) rawRows.add(row.toList())

        if (rawRows.isEmpty()) return emptyList()

        val headers = rawRows[0].map { it.trim() }
        return rawRows.drop(1).map { cells ->
            val map = mutableMapOf<String, String>()
            headers.forEachIndexed { idx, header ->
                val value = if (idx < cells.size) cells[idx] else ""
                map[header] = if (value.startsWith("'")) value.substring(1) else value
            }
            map
        }
    }

    data class ImportResult(
        val imported: Int,
        val failed: Int,
        val errors: List<String>
    )
}
