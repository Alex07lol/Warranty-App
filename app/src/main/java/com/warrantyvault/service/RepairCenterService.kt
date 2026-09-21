package com.warrantyvault.service

import com.warrantyvault.data.RepairCenter
import com.warrantyvault.data.RepairCenterDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URL
import java.net.URLEncoder

private const val NOMINATIM_SEARCH = "https://nominatim.openstreetmap.org/search"
private const val NOMINATIM_REVERSE = "https://nominatim.openstreetmap.org/reverse"
private const val OVERPASS_API = "https://overpass-api.de/api/interpreter"

class RepairCenterService(private val dao: RepairCenterDao) {

    private val json = Json { ignoreUnknownKeys = true }
    private val userAgent = "WarrantyVault/1.0 (https://github.com/Alex07lol/warranty-checker)"

    /**
     * Search for repair centers near a location using free APIs.
     * Uses Nominatim for geocoding + Overpass API for POI search.
     */
    suspend fun searchNearby(
        lat: Double,
        lng: Double,
        radiusKm: Double = 10.0,
        brand: String? = null,
        limit: Int = 20
    ): List<RepairCenter> = withContext(Dispatchers.IO) {
        val cached = searchCached(lat, lng, radiusKm, brand, limit)
        if (cached.isNotEmpty()) return@withContext cached
        
        val results = mutableListOf<RepairCenter>()
        
        // Try Overpass API for detailed POI data
        val overpassResults = searchOverpass(lat, lng, radiusKm, brand, limit)
        results.addAll(overpassResults)
        
        // Fallback to Nominatim if Overpass returns little
        if (results.size < limit / 2) {
            val nominatimResults = searchNominatim(lat, lng, radiusKm, brand, limit - results.size)
            results.addAll(nominatimResults)
        }
        
        // Cache results
        if (results.isNotEmpty()) {
            dao.insertAll(results)
        }
        
        results.take(limit)
    }

    /**
     * Search using Overpass API (OpenStreetMap) - free, no key needed
     */
    private suspend fun searchOverpass(
        lat: Double,
        lng: Double,
        radiusKm: Double,
        brand: String?,
        limit: Int
    ): List<RepairCenter> = withContext(Dispatchers.IO) {
        val radiusMeters = (radiusKm * 1000).toInt()
        
        // Build Overpass query for repair-related amenities
        val query = buildOverpassQuery(lat, lng, radiusMeters, brand)
        
        try {
            val url = URL(OVERPASS_API)
            val connection = url.openConnection().apply {
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                doOutput = true
                connectTimeout = 15000
                readTimeout = 30000
            }
            
            connection.outputStream.write("data=${URLEncoder.encode(query, "UTF-8")}".toByteArray())
            connection.outputStream.close()
            
            val response = connection.inputStream.reader().readText()
            parseOverpassResponse(response, brand)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun buildOverpassQuery(lat: Double, lng: Double, radiusMeters: Int, brand: String?): String {
        return """
            [out:json][timeout:25];
            (
              node["shop"~"electronics|computer|mobile_phone|appliance|hardware|doityourself"](${lat - radiusMeters/111000.0},${lng - radiusMeters/85000.0},${lat + radiusMeters/111000.0},${lng + radiusMeters/85000.0});
              node["craft"~"electronics|repair|watchmaker|clockmaker"](${lat - radiusMeters/111000.0},${lng - radiusMeters/85000.0},${lat + radiusMeters/111000.0},${lng + radiusMeters/85000.0});
              node["amenity"="repair_cafe"](${lat - radiusMeters/111000.0},${lng - radiusMeters/85000.0},${lat + radiusMeters/111000.0},${lng + radiusMeters/85000.0});
              way["shop"~"electronics|computer|mobile_phone|appliance|hardware|doityourself"](${lat - radiusMeters/111000.0},${lng - radiusMeters/85000.0},${lat + radiusMeters/111000.0},${lng + radiusMeters/85000.0});
              relation["shop"~"electronics|computer|mobile_phone|appliance|hardware|doityourself"](${lat - radiusMeters/111000.0},${lng - radiusMeters/85000.0},${lat + radiusMeters/111000.0},${lng + radiusMeters/85000.0});
            );
            out center tags;
        """.trimIndent()
    }

    private fun parseOverpassResponse(jsonText: String, brand: String?): List<RepairCenter> {
        return try {
            val obj = json.decodeFromString<JsonObject>(jsonText)
            val elements = obj["elements"]?.jsonArray ?: return emptyList()
            
            val results = mutableListOf<RepairCenter>()
            
            for (element in elements) {
                val elementObj = element.jsonObject
                val tags = elementObj["tags"]?.jsonObject ?: continue
                
                val name = tags["name"]?.jsonPrimitive?.content ?: continue
                val centerLat = elementObj["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() 
                    ?: elementObj["center"]?.jsonObject?.get("lat")?.jsonPrimitive?.content?.toDoubleOrNull() ?: continue
                val centerLng = elementObj["lon"]?.jsonPrimitive?.content?.toDoubleOrNull() 
                    ?: elementObj["center"]?.jsonObject?.get("lon")?.jsonPrimitive?.content?.toDoubleOrNull() ?: continue
                
                val center = RepairCenter(
                    name = name,
                    brand = tags["brand"]?.jsonPrimitive?.content,
                    address = buildAddress(tags),
                    city = tags["addr:city"]?.jsonPrimitive?.content,
                    state = tags["addr:state"]?.jsonPrimitive?.content,
                    countryCode = tags["addr:country"]?.jsonPrimitive?.content?.uppercase(),
                    postalCode = tags["addr:postcode"]?.jsonPrimitive?.content,
                    phone = tags["phone"]?.jsonPrimitive?.content ?: tags["contact:phone"]?.jsonPrimitive?.content,
                    website = tags["website"]?.jsonPrimitive?.content ?: tags["contact:website"]?.jsonPrimitive?.content,
                    lat = centerLat,
                    lng = centerLng,
                    types = json.encodeToString(tags["shop"]?.jsonPrimitive?.content?.let { listOf(it) } ?: listOf(tags["craft"]?.jsonPrimitive?.content ?: tags["amenity"]?.jsonPrimitive?.content ?: "repair")),
                    rating = null,
                    userRatingsTotal = null,
                    openingHours = tags["opening_hours"]?.jsonPrimitive?.content,
                    source = "overpass",
                    placeId = elementObj["id"]?.jsonPrimitive?.content
                )
                results.add(center)
            }
            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Search using Nominatim - free geocoding
     */
    private suspend fun searchNominatim(
        lat: Double,
        lng: Double,
        radiusKm: Double,
        brand: String?,
        limit: Int
    ): List<RepairCenter> = withContext(Dispatchers.IO) {
        val params = mutableMapOf(
            "format" to "jsonv2",
            "lat" to lat.toString(),
            "lon" to lng.toString(),
            "radius" to (radiusKm * 1000).toInt().toString(),
            "limit" to limit.toString(),
            "accept-language" to "en",
            "addressdetails" to "1",
            "extratags" to "1",
            "namedetails" to "1"
        )
        
        val q = listOf(
            "shop=electronics", "shop=computer", "shop=mobile_phone",
            "shop=appliance", "shop=hardware", "shop=doityourself",
            "craft=electronics", "craft=repair", "amenity=repair_cafe"
        )
        
        val queryParams = params.map { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }.joinToString("&")
        val qParam = URLEncoder.encode(q.joinToString(" OR "), "UTF-8")
        val url = URL("$NOMINATIM_SEARCH?$queryParams&q=$qParam")
        
        try {
            val connection = url.openConnection().apply {
                setRequestProperty("User-Agent", userAgent)
                connectTimeout = 10000
                readTimeout = 20000
            }
            
            val response = connection.inputStream.reader().readText()
            parseNominatimResponse(response, brand)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseNominatimResponse(jsonText: String, brand: String?): List<RepairCenter> {
        return try {
            val array = json.decodeFromString<JsonArray>(jsonText)
            val results = mutableListOf<RepairCenter>()
            
            for (element in array) {
                val obj = element.jsonObject
                val name = obj["display_name"]?.jsonPrimitive?.content ?: continue
                val lat = obj["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: continue
                val lng = obj["lon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: continue
                
                val address = obj["address"]?.jsonObject
                val extratags = obj["extratags"]?.jsonObject
                
                val center = RepairCenter(
                    name = name,
                    brand = brand ?: extratags?.get("brand")?.jsonPrimitive?.content,
                    address = obj["display_name"]?.jsonPrimitive?.content ?: "",
                    city = address?.get("city")?.jsonPrimitive?.content ?: address?.get("town")?.jsonPrimitive?.content,
                    state = address?.get("state")?.jsonPrimitive?.content,
                    countryCode = address?.get("country_code")?.jsonPrimitive?.content?.uppercase(),
                    postalCode = address?.get("postcode")?.jsonPrimitive?.content,
                    phone = extratags?.get("phone")?.jsonPrimitive?.content,
                    website = extratags?.get("website")?.jsonPrimitive?.content,
                    lat = lat,
                    lng = lng,
                    types = json.encodeToString(listOf(obj["type"]?.jsonPrimitive?.content ?: "shop")),
                    rating = null,
                    userRatingsTotal = null,
                    openingHours = extratags?.get("opening_hours")?.jsonPrimitive?.content,
                    source = "nominatim",
                    placeId = obj["place_id"]?.jsonPrimitive?.content
                )
                results.add(center)
            }
            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun buildAddress(tags: JsonObject): String {
        val parts = mutableListOf<String>()
        tags["addr:housenumber"]?.jsonPrimitive?.content?.let { parts.add(it) }
        tags["addr:street"]?.jsonPrimitive?.content?.let { parts.add(it) }
        tags["addr:city"]?.jsonPrimitive?.content?.let { parts.add(it) }
        tags["addr:state"]?.jsonPrimitive?.content?.let { parts.add(it) }
        tags["addr:postcode"]?.jsonPrimitive?.content?.let { parts.add(it) }
        tags["addr:country"]?.jsonPrimitive?.content?.let { parts.add(it.uppercase()) }
        return parts.joinToString(", ")
    }

    private suspend fun searchCached(
        lat: Double,
        lng: Double,
        radiusKm: Double,
        brand: String?,
        limit: Int
    ): List<RepairCenter> {
        val radiusDegLat = radiusKm / 111.0
        val radiusDegLng = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)))
        
        return dao.getNearby(
            lat - radiusDegLat, lat + radiusDegLat,
            lng - radiusDegLng, lng + radiusDegLng,
            brand, limit
        )
    }

    suspend fun getByBrandAndCountry(brand: String, countryCode: String, limit: Int = 20): List<RepairCenter> {
        return dao.getByBrandAndCountry(brand, countryCode.uppercase(), limit)
    }

    suspend fun reverseGeocode(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        val url = URL("$NOMINATIM_REVERSE?format=jsonv2&lat=$lat&lon=$lng&accept-language=en")
        try {
            val connection = url.openConnection().apply {
                setRequestProperty("User-Agent", userAgent)
                connectTimeout = 10000
                readTimeout = 15000
            }
            val response = connection.inputStream.reader().readText()
            val obj = json.decodeFromString<JsonObject>(response)
            obj["display_name"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }
}