package com.warrantyvault.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "isDeleted"]),
        Index(value = ["warrantyExpiryDate"]),
        Index(value = ["userId", "serialNumber"], unique = true)
    ]
)
@Serializable
data class Product(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val productName: String,
    val brand: String? = null,
    val model: String? = null,
    val category: String? = null,
    val purchaseDate: Long? = null,
    val purchasePrice: Double? = null,
    val currency: String = "USD",
    val purchaseStore: String? = null,
    val serialNumber: String? = null,
    val warrantyExpiryDate: Long? = null,
    val warrantyPeriodMonths: Int? = null,
    val warrantyProvider: String? = null,
    val warrantyProviderType: String? = null,
    val warrantyContact: String? = null,
    val warrantyWebsite: String? = null,
    val lifecycleStatus: String = "owned",
    val tags: String = "[]", // JSON array of tags
    val notes: String? = null,
    val isDeleted: Boolean = false,
    val thumbnailUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)