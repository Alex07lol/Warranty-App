package com.warrantyvault.service

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.warrantyvault.data.AppDatabase
import com.warrantyvault.data.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportImportService(private val context: Context, private val db: AppDatabase) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    // CSV Headers matching web version
    private val CSV_HEADERS = listOf(
        "productName", "brand", "model", "category", "serialNumber", "purchaseDate",
        "purchasePrice", "currency", "purchaseStore",
        "warrantyExpiryDate", "lifecycleStatus", "serviceHistory"
    )

    // Export all products to JSON
    suspend fun exportJson(): Uri = withContext(Dispatchers.IO) {
        val products = db.productDao().getAllForUser(1) // Single local user
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
                    "purchaseDate" to product.purchaseDate?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it)) },
                    "purchasePrice" to product.purchasePrice,
                    "currency" to product.currency,
                    "purchaseStore" to product.purchaseStore,
                    "warrantyExpiryDate" to product.warrantyExpiryDate?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it)) },
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
                            "startDate" to w.startDate?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it)) },
                            "expiryDate" to w.expiryDate?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it)) }
                        )
                    },
                    "serviceHistory" to productServices.map { s ->
                        mapOf(
                            "serviceDate" to SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(s.serviceDate)),
                            "serviceType" to s.serviceType,
                            "serviceProvider" to s.serviceProvider,
                            "cost" to s.cost,
                            "currency" to s.currency,
                            "description" to s.description,
                            "nextServiceDate" to s.nextServiceDate?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it)) }
                        )
                    }
                )
            }
        )

        val jsonString = json.encodeToString(exportData)
        return@withContext saveToDownloads(jsonString, "warrantyvault-export-${System.currentTimeMillis()}.json", "application/json")
    }

    // Export all products to CSV (RFC 4180)
    suspend fun exportCsv(): Uri = withContext(Dispatchers.IO) {
        val products = db.productDao().getAllForUser(1)
        val productIds = products.map { it.id }
        val services = db.serviceHistoryDao().getByProductIds(productIds)
        val serviceByProduct = services.groupBy { it.productId }

        val rows = mutableListOf<String>()
        rows.add(CSV_HEADERS.joinToString(","))

        for (product in products) {
            val productServices = serviceByProduct[product.id] ?: emptyList()
            val serviceStr = productServices.map { s ->
                listOf(
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(s.serviceDate)),
                    s.serviceType,
                    s.serviceProvider ?: "",
                    s.cost?.toString() ?: "",
                    s.nextServiceDate?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it)) } ?: ""
                ).filter { it.isNotBlank() }.joinToString(" | ")
            }.joinToString(" ;; ")

            val row = listOf(
                escapeCsv(product.productName),
                escapeCsv(product.brand ?: ""),
                escapeCsv(product.model ?: ""),
                escapeCsv(product.category ?: ""),
                escapeCsv(product.serialNumber ?: ""),
                escapeCsv(product.purchaseDate?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it)) } ?: ""),
                escapeCsv(product.purchasePrice?.toString() ?: ""),
                escapeCsv(product.currency),
                escapeCsv(product.purchaseStore ?: ""),
                escapeCsv(product.warrantyExpiryDate?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it)) } ?: ""),
                escapeCsv(product.lifecycleStatus),
                escapeCsv(serviceStr)
            )
            rows.add(row.joinToString(","))
        }

        val csvString = rows.joinToString("\n")
        return@withContext saveToDownloads(csvString, "warrantyvault-export-${System.currentTimeMillis()}.csv", "text/csv")
    }

    private fun escapeCsv(value: String): String {
        var s = value
        // Formula injection guard
        if (s.startsWith("=") || s.startsWith("+") || s.startsWith("-") || s.startsWith("@") || s.startsWith("\t")) {
            s = "'$s"
        }
        // RFC 4180: quote if contains comma, quote, or newline
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"${s.replace("\"", "\"\"")}\""
        }
        return s
    }

    // Save file to Downloads using MediaStore
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

    // Import from JSON
    suspend fun importJson(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext ImportResult(0, 0, listOf("Failed to open file"))
        val jsonString = inputStream.reader().readText()
        
        val parsed = try {
            json.decodeFromString<JsonElement>(jsonString)
        } catch (e: Exception) {
            return@withContext ImportResult(0, 0, listOf("Invalid JSON: ${e.message}"))
        }

        val productsArray = when {
            parsed is kotlinx.serialization.json.JsonObject && parsed.containsKey("products") -> parsed["products"]?.jsonArray
            parsed is kotlinx.serialization.json.JsonArray -> parsed
            else -> null
        } ?: return@withContext ImportResult(0, 0, listOf("No products array found"))

        var imported = 0
        var failed = 0
        val errors = mutableListOf<String>()

        for ((index, element) in productsArray.withIndex()) {
            try {
                val productMap = element.jsonObject
                val name = productMap["productName"]?.jsonPrimitive?.content
                if (name == null) {
                    failed++
                    errors.add("productName required at index $index")
                    continue
                }
                
                val product = Product(
                    userId = 1,
                    productName = name,
                    brand = productMap["brand"]?.jsonPrimitive?.content,
                    model = productMap["model"]?.jsonPrimitive?.content,
                    category = productMap["category"]?.jsonPrimitive?.content,
                    serialNumber = productMap["serialNumber"]?.jsonPrimitive?.content,
                    purchaseDate = productMap["purchaseDate"]?.jsonPrimitive?.content?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it)?.time },
                    purchasePrice = productMap["purchasePrice"]?.jsonPrimitive?.content?.toDoubleOrNull(),
                    currency = productMap["currency"]?.jsonPrimitive?.content ?: "USD",
                    purchaseStore = productMap["purchaseStore"]?.jsonPrimitive?.content,
                    warrantyExpiryDate = productMap["warrantyExpiryDate"]?.jsonPrimitive?.content?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it)?.time },
                    warrantyPeriodMonths = productMap["warrantyPeriodMonths"]?.jsonPrimitive?.content?.toIntOrNull(),
                    warrantyProvider = productMap["warrantyProvider"]?.jsonPrimitive?.content,
                    warrantyProviderType = productMap["warrantyProviderType"]?.jsonPrimitive?.content,
                    warrantyContact = productMap["warrantyContact"]?.jsonPrimitive?.content,
                    warrantyWebsite = productMap["warrantyWebsite"]?.jsonPrimitive?.content,
                    lifecycleStatus = productMap["lifecycleStatus"]?.jsonPrimitive?.content ?: "owned",
                    tags = productMap["tags"]?.jsonPrimitive?.content ?: "[]",
                    notes = productMap["notes"]?.jsonPrimitive?.content
                )
                
                val productId = db.productDao().insert(product)
                imported++
                
                // Import warranties
                productMap["warranties"]?.jsonArray?.forEach { w ->
                    val wObj = w.jsonObject
                    db.warrantyPeriodDao().insert(com.warrantyvault.data.WarrantyPeriod(
                        productId = productId,
                        type = wObj["type"]?.jsonPrimitive?.content,
                        provider = wObj["provider"]?.jsonPrimitive?.content,
                        coverage = wObj["coverage"]?.jsonPrimitive?.content,
                        startDate = wObj["startDate"]?.jsonPrimitive?.content?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it)?.time },
                        expiryDate = wObj["expiryDate"]?.jsonPrimitive?.content?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it)?.time }
                    ))
                }
                
                // Import service history
                productMap["serviceHistory"]?.jsonArray?.forEach { s ->
                    val sObj = s.jsonObject
                    val sDate = sObj["serviceDate"]?.jsonPrimitive?.content?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it)?.time } ?: System.currentTimeMillis()
                    db.serviceHistoryDao().insert(com.warrantyvault.data.ServiceHistory(
                        productId = productId,
                        userId = 1,
                        serviceDate = sDate,
                        serviceType = sObj["serviceType"]?.jsonPrimitive?.content ?: "repair",
                        serviceProvider = sObj["serviceProvider"]?.jsonPrimitive?.content,
                        cost = sObj["cost"]?.jsonPrimitive?.content?.toDoubleOrNull(),
                        currency = sObj["currency"]?.jsonPrimitive?.content ?: "USD",
                        description = sObj["description"]?.jsonPrimitive?.content,
                        nextServiceDate = sObj["nextServiceDate"]?.jsonPrimitive?.content?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it)?.time }
                    ))
                }
            } catch (e: Exception) {
                failed++
                errors.add("Row $index: ${e.message}")
            }
        }

        ImportResult(imported, failed, errors)
    }

    // Import from CSV
    suspend fun importCsv(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext ImportResult(0, 0, listOf("Failed to open file"))
        val csvText = inputStream.reader().readText()
        
        val records = parseCsv(csvText)
        if (records.isEmpty()) return@withContext ImportResult(0, 0, listOf("No data rows found"))

        var imported = 0
        var failed = 0
        val errors = mutableListOf<String>()

        for ((index, record) in records.withIndex()) {
            try {
                val productName = record["productName"]
                if (productName.isNullOrEmpty()) {
                    failed++
                    errors.add("productName required at row $index")
                    continue
                }
                val product = Product(
                    userId = 1,
                    productName = productName,
                    brand = record["brand"]?.ifBlank { null },
                    model = record["model"]?.ifBlank { null },
                    category = record["category"]?.ifBlank { null },
                    serialNumber = record["serialNumber"]?.ifBlank { null },
                    purchaseDate = record["purchaseDate"]?.ifBlank { null }?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it)?.time },
                    purchasePrice = record["purchasePrice"]?.toDoubleOrNull(),
                    currency = record["currency"]?.ifBlank { null } ?: "USD",
                    purchaseStore = record["purchaseStore"]?.ifBlank { null },
                    warrantyExpiryDate = record["warrantyExpiryDate"]?.ifBlank { null }?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it)?.time },
                    lifecycleStatus = record["lifecycleStatus"]?.ifBlank { null } ?: "owned"
                )
                
                db.productDao().insert(product)
                imported++
            } catch (e: Exception) {
                failed++
                errors.add("Row $index: ${e.message}")
            }
        }

        ImportResult(imported, failed, errors)
    }

    // RFC 4180 CSV parser
    private fun parseCsv(text: String): List<Map<String, String>> {
        val s = text.replace("\uFEFF", "") // Remove BOM
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

        // Final record
        row.add(field.toString())
        if (row.size > 1 || row.firstOrNull()?.isNotBlank() == true) rawRows.add(row.toList())

        if (rawRows.isEmpty()) return emptyList()

        val headers = rawRows[0].map { it.trim() }
        return rawRows.drop(1).map { cells ->
            val map = mutableMapOf<String, String>()
            headers.forEachIndexed { idx, header ->
                val value = if (idx < cells.size) cells[idx] else ""
                // Remove formula injection guard
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