package com.warrantyvault.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.warrantyvault.WarrantyEngine
import com.warrantyvault.data.AppDatabase
import com.warrantyvault.data.Product
import com.warrantyvault.data.ServiceHistory
import com.warrantyvault.ocr.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds WarrantyVault PDF exports ("Product Passport & Warranty Record") and saves
 * them to the public Downloads folder, mirroring the web export service:
 *
 *  - one clean page per product, each with an explicit frame, product/purchase
 *    specs, warranty coverage, tags & notes, and service history
 *  - [exportPdf]      → whole vault, one page per product
 *  - [exportProductPdf] → a single product's passport
 *
 * CSV/JSON exports of the entire list already live in ExportImportService; this
 * class deliberately adds only the PDF side.
 */
class PdfExportService(private val context: Context, private val db: AppDatabase) {

    // ─── Public API ─────────────────────────────────────────────────────────────

    /** Whole-vault export: one passport page per live product. */
    suspend fun exportPdf(): Uri = withContext(Dispatchers.IO) {
        val products = db.productDao().getAllForUser(currentUserId())
        val bytes = buildBytes(products)
        saveToDownloads(bytes, "warrantyvault-export-${fileStamp()}.pdf")
    }

    /** Single-product passport (e.g. shared with a service centre for a claim). */
    suspend fun exportProductPdf(productId: Long): Uri = withContext(Dispatchers.IO) {
        val product = db.productDao().getProductByIdImmediate(productId)
            ?: throw IllegalArgumentException("Product $productId not found")
        val bytes = buildBytes(listOf(product))
        saveToDownloads(bytes, "warranty-${slug(product.productName)}-${fileStamp()}.pdf")
    }

    /** Builds the PDF in memory and returns a shareable FileProvider URI (no file in Downloads). */
    suspend fun shareProductPdf(productId: Long): Uri = withContext(Dispatchers.IO) {
        val product = db.productDao().getProductByIdImmediate(productId)
            ?: throw IllegalArgumentException("Product $productId not found")
        val bytes = buildBytes(listOf(product))
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "warranty-${slug(product.productName)}-${fileStamp()}.pdf")
        file.writeBytes(bytes)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Standard ACTION_SEND chooser for a share URI. Call from the UI layer. */
    fun shareIntent(uri: Uri): Intent =
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Share warranty record"
        )

    // ─── Mapping & assembly ─────────────────────────────────────────────────────

    private suspend fun buildBytes(products: List<Product>): ByteArray {
        val serviceByProduct = if (products.isEmpty()) {
            emptyMap()
        } else {
            db.serviceHistoryDao().getByProductIds(products.map { it.id }).groupBy { it.productId }
        }
        val passports = products.map { it.toPassport(serviceByProduct[it.id].orEmpty()) }
        return WarrantyPdf.buildPassports(passports, timestamp())
    }

    private fun Product.toPassport(services: List<ServiceHistory>): WarrantyPdf.PassportProduct {
        val status = WarrantyEngine.warrantyStatusOf(purchaseDate, warrantyExpiryDate)
        return WarrantyPdf.PassportProduct(
            productName = productName,
            brand = brand,
            model = model,
            category = category,
            serialNumber = serialNumber,
            imei = imei,
            purchaseDate = purchaseDate?.let { epochToDate(it) },
            purchasePrice = purchasePrice,
            currency = currency,
            purchaseStore = purchaseStore,
            warrantyStatus = status.status,
            warrantyExpiryDate = warrantyExpiryDate?.let { epochToDate(it) },
            warrantyPeriodMonths = warrantyPeriodMonths,
            warrantyProvider = warrantyProvider,
            warrantyProviderType = warrantyProviderType,
            warrantyContact = warrantyContact,
            lifecycleStatus = lifecycleStatus,
            tags = parseTags(tags),
            notes = notes,
            services = services
                .sortedByDescending { it.serviceDate }
                .map {
                    WarrantyPdf.PassportService(
                        date = epochToDate(it.serviceDate),
                        type = it.serviceType,
                        provider = it.serviceProvider,
                        cost = it.cost,
                        currency = it.currency,
                        nextDate = it.nextServiceDate?.let { n -> epochToDate(n) }
                    )
                }
        )
    }

    private fun parseTags(raw: String?): List<String> = runCatching {
        val s = raw?.trim().orEmpty()
        if (!s.startsWith("[")) return emptyList()
        s.removePrefix("[").removeSuffix("]")
            .split(',')
            .map { it.trim().trim('"', '\'') }
            .filter { it.isNotEmpty() }
    }.getOrDefault(emptyList())

    private fun epochToDate(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    private fun fileStamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    private fun slug(name: String): String =
        name.trim()
            .replace(Regex("[^A-Za-z0-9]+"), "-")
            .trim('-')
            .lowercase()
            .take(40)
            .ifBlank { "product" }

    private fun currentUserId(): Long =
        (context.applicationContext as com.warrantyvault.WarrantyVaultApplication).currentUserId

    private fun saveToDownloads(bytes: ByteArray, fileName: String): Uri {
        val resolver = context.contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IllegalStateException("Could not create Downloads entry")
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: throw IllegalStateException("Could not open output stream")
        return uri
    }
}
