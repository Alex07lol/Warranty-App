package com.warrantyvault.repair

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * OpenStreetMap-based [RepairLocationProvider] backed by the Overpass API.
 *
 * Requirements honoured here:
 *  - No warranty/PII ever sent: only coordinates, radius, and OSM tag filters.
 *  - Visible attribution constant exposed for the UI.
 *  - In-memory result cache keyed by rounded coordinates + radius.
 *  - Client-side rate limiting (min interval between network calls).
 *  - Graceful failure: returns cached results when the network fails; empty list otherwise.
 *  - Replaces cleanly: the app depends on [RepairLocationProvider], not on Overpass.
 */
class OverpassRepairLocationProvider(
    private val endpoint: String = "https://overpass-api.de/api/interpreter",
    private val minIntervalMs: Long = 5_000L,
    private val clock: () -> Long = System::currentTimeMillis
) : RepairLocationProvider {

    companion object {
        /** Must be displayed in the Repair Locations UI. */
        const val ATTRIBUTION = "© OpenStreetMap contributors"

        const val USER_AGENT = "WarrantyVault/1.0 (Android; local warranty manager)"

        private val CATEGORY_KEYS = listOf(
            "name", "addr:street", "addr:housenumber", "addr:city", "addr:postcode",
            "phone", "contact:phone", "website", "contact:website",
            "opening_hours", "operator", "shop", "craft"
        )
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** Key: rounded(3dp) lat/lon + radius + category bucket. */
    private val cache = LruCache<String, List<RepairLocation>>(32)
    private val rateMutex = Mutex()
    private var lastRequestAt = 0L

    override suspend fun searchNearby(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
        productContext: ProductContext?
    ): List<RepairLocation> = withContext(Dispatchers.IO) {
        val radius = radiusMeters.coerceIn(200, RepairLocationProvider.MAX_RADIUS)
        val cacheKey = "${"%.3f".format(latitude)}|${"%.3f".format(longitude)}|$radius|${productContext?.category ?: "any"}"

        cache.get(cacheKey)?.let { return@withContext it }

        val filters = categoryToOsmFilters(productContext?.category)

        // Overpass query: union over shop/craft filters within the radius.
        val union = filters.joinToString("") { f ->
            val (k, v) = f.split("=", limit = 2)
            "node[\"$k\"=\"$v\"](around:$radius,$latitude,$longitude);way[\"$k\"=\"$v\"](around:$radius,$latitude,$longitude);"
        }
        val query = "[out:json][timeout:25];($union);out center tags 60;"

        // Rate limit: ensure minimum spacing between network calls.
        rateMutex.withLock {
            val wait = minIntervalMs - (clock() - lastRequestAt)
            if (wait > 0) {
                try { Thread.sleep(wait) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            }
            lastRequestAt = clock()
        }

        try {
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 10_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                val body = "data=" + URLEncoder.encode(query, "UTF-8")
                connection.outputStream.use { it.write(body.toByteArray()) }

                if (connection.responseCode !in 200..299) {
                    // Non-OK (rate limited / server error): serve cache if any, else empty.
                    return@withContext emptyList()
                }

                val text = connection.inputStream.bufferedReader().readText()
                val parsed = parseOverpassResponse(text)
                val withDistance = parsed.map {
                    it.copy(distanceMeters = distanceMeters(latitude, longitude, it.latitude, it.longitude))
                }.sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
                cache.put(cacheKey, withDistance)
                withDistance
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            // Network failure: cached results where available, otherwise empty — never crash.
            cache.get(cacheKey) ?: emptyList()
        }
    }

    /** Visible for tests. Parses an Overpass JSON response into normalized locations. */
    fun parseOverpassResponse(raw: String): List<RepairLocation> {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyList()
        val elements = root["elements"] as? JsonArray ?: return emptyList()

        val out = mutableListOf<RepairLocation>()
        for (el in elements) {
            val obj = el as? JsonObject ?: continue
            val tags = obj["tags"] as? JsonObject ?: continue
            val lat = obj["lat"]?.jsonPrimitive?.content?.toDoubleOrNull()
                ?: obj["center"]?.jsonObject?.get("lat")?.jsonPrimitive?.content?.toDoubleOrNull()
                ?: continue
            val lon = obj["lon"]?.jsonPrimitive?.content?.toDoubleOrNull()
                ?: obj["center"]?.jsonObject?.get("lon")?.jsonPrimitive?.content?.toDoubleOrNull()
                ?: continue
            val id = obj["type"]?.jsonPrimitive?.content.orEmpty() + "/" + (obj["id"]?.jsonPrimitive?.content ?: continue)

            val name = tags["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: continue

            val address = listOf(
                tags["addr:housenumber"]?.jsonPrimitive?.content,
                tags["addr:street"]?.jsonPrimitive?.content
            ).filterNotNull().joinToString(" ").ifBlank {
                tags["addr:street"]?.jsonPrimitive?.content
            }?.let { street ->
                val city = tags["addr:city"]?.jsonPrimitive?.content
                val postcode = tags["addr:postcode"]?.jsonPrimitive?.content
                listOfNotNull(street.ifBlank { null }, postcode, city).joinToString(", ")
            }

            val shop = tags["shop"]?.jsonPrimitive?.content
            val craft = tags["craft"]?.jsonPrimitive?.content
            val category = craft ?: shop

            out.add(
                RepairLocation(
                    id = id,
                    name = name.trim(),
                    latitude = lat,
                    longitude = lon,
                    address = address?.takeIf { it.isNotBlank() },
                    phone = (tags["phone"] ?: tags["contact:phone"])?.jsonPrimitive?.content,
                    website = (tags["website"] ?: tags["contact:website"])?.jsonPrimitive?.content,
                    category = category,
                    distanceMeters = null, // set by caller relative to search origin
                    openingHours = tags["opening_hours"]?.jsonPrimitive?.content,
                    operator = tags["operator"]?.jsonPrimitive?.content,
                    source = "OpenStreetMap"
                )
            )
        }
        return out.distinctBy { it.id }
    }
}
