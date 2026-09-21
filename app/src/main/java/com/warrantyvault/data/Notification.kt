package com.warrantyvault.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "notifications",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Document::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["userId", "isRead"]),
        Index(value = ["productId", "notificationType", "scheduledAt"])
    ]
)
@Serializable
data class Notification(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val productId: Long? = null,
    val documentId: Long? = null,
    val notificationType: String, // warranty_expiry, service_reminder, document_processing, shared_access, system
    val title: String,
    val message: String,
    val isRead: Boolean = false,
    val isSent: Boolean = false,
    val scheduledAt: Long? = null,
    val sentAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)