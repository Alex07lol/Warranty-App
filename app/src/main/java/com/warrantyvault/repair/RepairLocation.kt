package com.warrantyvault.repair

/**
 * Minimal, privacy-safe context passed to the location provider.
 *
 * NEVER include warranty data, serial numbers, IMEI, OCR text, or any document
 * contents here. This type only carries a coarse product CATEGORY so the provider
 * can bias results (e.g. phone repair shops vs. appliance service centres).
 */
data class ProductContext(
    /** Coarse category: phone, laptop, tv, appliance, camera, watch, other. */
    val category: String
)

/**
 * Normalized repair location result. The rest of the app depends only on this model,
 * never on Overpass/Nominatim payloads.
 */
data class RepairLocation(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val phone: String?,
    val website: String?,
    val category: String?,
    /** Metres from the search origin, when the provider or app computed it. */
    val distanceMeters: Double?,
    /** Only if the source actually returned it — never invented. */
    val openingHours: String?,
    val operator: String?,
    /** e.g. "OpenStreetMap". */
    val source: String
)

/** Abstraction over the location data source. Must stay replaceable. */
interface RepairLocationProvider {
    /**
     * Searches for repair centres near a coordinate.
     * @param radiusMeters bounded by the provider implementation (see [MAX_RADIUS]).
     */
    suspend fun searchNearby(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
        productContext: ProductContext?
    ): List<RepairLocation>

    companion object {
        const val MAX_RADIUS = 20_000
        const val DEFAULT_RADIUS = 5_000
    }
}

/** Haversine distance in metres. Used for ordering and display. */
fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
        Math.sin(dLon / 2) * Math.sin(dLon / 2)
    return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

/** Human-friendly distance label. */
fun formatDistance(meters: Double): String = when {
    meters < 1000 -> "${meters.toInt()} m"
    meters < 10_000 -> String.format(java.util.Locale.getDefault(), "%.1f km", meters / 1000)
    else -> "${(meters / 1000).toInt()} km"
}

/** Maps a coarse product category to Overpass shop/craft filters. Internal to providers. */
internal fun categoryToOsmFilters(category: String?): List<String> = when (category?.lowercase()) {
    "phone" -> listOf("shop=mobile_phone", "shop=electronics", "craft=electronics_repair")
    "laptop", "computer" -> listOf("shop=computer", "craft=electronics_repair")
    "tv", "television" -> listOf("shop=electronics", "craft=electronics_repair")
    "appliance" -> listOf("shop=appliance", "craft=electronics_repair")
    "camera" -> listOf("shop=camera", "craft=electronics_repair")
    "watch" -> listOf("shop=clock", "craft=watchmaker")
    else -> listOf("craft=electronics_repair", "shop=electronics", "shop=mobile_phone", "shop=computer")
}
