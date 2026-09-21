package com.warrantyvault.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["productId"]),
        Index(value = ["userId", "documentType"]),
        Index(value = ["tags"])
    ]
)
@Serializable
data class Document(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val productId: Long? = null,
    val userId: Long,
    val documentType: String, // receipt, warranty_card, product_photo, manual, other
    val fileName: String,
    val filePath: String, // local file path
    val fileSize: Long,
    val mimeType: String,
    val uploadedAt: Long = System.currentTimeMillis(),
    val docState: String = "unreviewed", // unreviewed, reviewed, important, archived
    val verified: Boolean = false,
    val tags: String = "[]", // JSON array
    val notes: String? = null,
    val ocrStatus: String = "pending", // pending, processing, done, failed, skipped
    val ocrText: String? = null,
    val parsedData: String? = null, // JSON string
    val ocrError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)