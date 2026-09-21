package com.warrantyvault.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "repair_centers",
    indices = [
        Index(value = ["brand", "countryCode"]),
        Index(value = ["lat", "lng"]),
        Index(value = ["source"])
    ]
)
@Serializable
data class RepairCenter(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val brand: String? = null,
    val address: String,
    val city: String? = null,
    val state: String? = null,
    val countryCode: String? = null,
    val postalCode: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val lat: Double,
    val lng: Double,
    val types: String = "[]", // JSON array
    val rating: Float? = null,
    val userRatingsTotal: Int? = null,
    val openingHours: String? = null,
    val source: String = "nominatim", // nominatim, overpass, manual
    val placeId: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)