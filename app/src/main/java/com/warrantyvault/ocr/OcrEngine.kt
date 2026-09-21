package com.warrantyvault.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.regex.Pattern

@Serializable
data class OcrResult(
    val rawText: String,
    val productName: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val serialNumber: String? = null,
    val purchasePrice: Double? = null,
    val purchaseStore: String? = null,
    val purchaseDate: String? = null,
    val warrantyExpiryDate: String? = null
) {
    @Transient
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }
    
    fun toJson(): String {
        return try {
            json.encodeToString(this)
        } catch (e: Exception) {
            "{}"
        }
    }
}

object OcrEngine {
    private val MONTH_INDEX = mapOf(
        "jan" to 0, "feb" to 1, "mar" to 2, "apr" to 3, "may" to 4, "jun" to 5,
        "jul" to 6, "aug" to 7, "sep" to 8, "oct" to 9, "nov" to 10, "dec" to 11,
        "january" to 0, "february" to 1, "march" to 2, "april" to 3, "june" to 5,
        "july" to 6, "august" to 7, "september" to 8, "october" to 9, "november" to 10, "december" to 11
    )

    fun parseReceiptText(text: String): OcrResult {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val rawText = text

        val price = extractPrice(lines)
        val store = extractStore(lines)
        val brand = extractBrand(lines)
        val model = extractModel(lines)
        val serialNumber = extractSerialNumber(lines)
        val dates = extractDates(lines)

        val purchaseDate = dates.getOrNull(0)
        val warrantyExpiryDate = dates.getOrNull(1) ?: inferWarrantyExpiry(purchaseDate, lines)

        val productName = listOfNotNull(brand, model).joinToString(" ").ifEmpty {
            lines.firstOrNull { l -> l.length > 3 && !l.contains("total", ignoreCase = true) }
        }

        return OcrResult(
            rawText = rawText,
            productName = productName,
            brand = brand,
            model = model,
            serialNumber = serialNumber,
            purchasePrice = price,
            purchaseStore = store,
            purchaseDate = purchaseDate,
            warrantyExpiryDate = warrantyExpiryDate
        )
    }

    private fun extractPrice(lines: List<String>): Double? {
        val priceRegex = Pattern.compile("(?:total|subtotal|amount|paid|sum|\\$|€|£)?\\s*[:=]?\\s*([$€£]?\\s*\\d+[.,]\\d{2})", Pattern.CASE_INSENSITIVE)
        for (line in lines.reversed()) {
            val matcher = priceRegex.matcher(line)
            if (matcher.find()) {
                val priceStr = matcher.group(1)?.replace("[^0-9.]".toRegex(), "")
                val price = priceStr?.toDoubleOrNull()
                if (price != null && price > 0) return price
            }
        }
        return null
    }

    private fun extractStore(lines: List<String>): String? {
        val knownStores = listOf("best buy", "walmart", "target", "amazon", "apple", "micro center", "b&h", "costco", "home depot", "lowe's", "staples", "office depot", "ebay", "ikea", "sears")
        for (line in lines.take(5)) {
            val lower = line.lowercase(Locale.US)
            for (store in knownStores) {
                if (lower.contains(store)) return line
            }
        }
        return lines.firstOrNull { it.length > 3 }
    }

    private fun extractBrand(lines: List<String>): String? {
        val knownBrands = listOf("apple", "samsung", "sony", "lg", "dell", "hp", "lenovo", "asus", "acer", "microsoft", "bose", "jbl", "canon", "nikon", "panasonic", "philips", "dyson", "ninja", "kitchenaid", "logitech", "corsair", "razer", "sennheiser", "garmin", "fitbit", "gopro", "sonos", "anker", "belkin")
        for (line in lines) {
            val lower = line.lowercase(Locale.US)
            for (brand in knownBrands) {
                if (lower.contains(brand)) return brand.replaceFirstChar { it.uppercase() }
            }
        }
        return null
    }

    private fun extractModel(lines: List<String>): String? {
        val modelRegex = Pattern.compile("(?:model|mod|m/n|p/n|sku|item)\\s*[:=]?\\s*([a-z0-9-]{3,20})", Pattern.CASE_INSENSITIVE)
        for (line in lines) {
            val matcher = modelRegex.matcher(line)
            if (matcher.find()) return matcher.group(1)
        }
        return null
    }

    private fun extractSerialNumber(lines: List<String>): String? {
        val snRegex = Pattern.compile("(?:s/n|sn|serial|serial no|serial number)\\s*[:=]?\\s*([a-z0-9-]{5,30})", Pattern.CASE_INSENSITIVE)
        for (line in lines) {
            val matcher = snRegex.matcher(line)
            if (matcher.find()) return matcher.group(1)
        }
        return null
    }

    private fun extractDates(lines: List<String>): List<String> {
        val dateRegex = Pattern.compile("(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{4}[/-]\\d{1,2}[/-]\\d{1,2})")
        val dates = mutableListOf<String>()
        for (line in lines) {
            val matcher = dateRegex.matcher(line)
            while (matcher.find()) {
                dates.add(matcher.group(1))
            }
        }
        return dates
    }

    private fun inferWarrantyExpiry(purchaseDate: String?, lines: List<String>): String? {
        val warrantyRegex = Pattern.compile("(\\d+)\\s*(year|yr|month|mo)\\s*warranty", Pattern.CASE_INSENSITIVE)
        for (line in lines) {
            val matcher = warrantyRegex.matcher(line)
            if (matcher.find()) {
                val num = matcher.group(1)?.toIntOrNull() ?: 1
                val unit = matcher.group(2)?.lowercase(Locale.US) ?: "year"
                val months = if (unit.startsWith("y")) num * 12 else num
                return "${months}m from purchase"
            }
        }
        return null
    }
}

class AndroidOcrEngine(private val context: Context) {
    private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognizeText(imageUri: Uri): OcrResult {
        val inputImage = InputImage.fromFilePath(context, imageUri)
        val visionText = Tasks.await(recognizer.process(inputImage))
        return OcrEngine.parseReceiptText(visionText.text)
    }

    suspend fun recognizeBitmap(bitmap: Bitmap): OcrResult {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val visionText = Tasks.await(recognizer.process(inputImage))
        return OcrEngine.parseReceiptText(visionText.text)
    }
}