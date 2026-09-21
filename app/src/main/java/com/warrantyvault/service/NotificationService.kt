package com.warrantyvault.service

import com.warrantyvault.data.Notification
import com.warrantyvault.data.NotificationDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationService(private val dao: NotificationDao) {

    private suspend fun createStaged(
        productId: Long,
        productName: String,
        stage: String,
        title: String,
        message: String
    ) = withContext(Dispatchers.IO) {
        // Dedupe: never insert the same product+type+stage twice.
        if (dao.existsForProductStage(1, productId, "warranty_expiry:$stage")) return@withContext
        dao.insert(
            Notification(
                userId = 1,
                notificationType = "warranty_expiry:$stage",
                title = title,
                message = message,
                productId = productId,
                isRead = false,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun createExpiringSoonNotification(productId: Long, productName: String, expiryDate: Long) =
        createStaged(
            productId, productName, "30_days",
            title = "Warranty Expiring Soon",
            message = "$productName warranty expires on ${
                SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(expiryDate))
            }"
        )

    suspend fun createExpiredNotification(productId: Long, productName: String) =
        createStaged(
            productId, productName, "expired",
            title = "Warranty Expired",
            message = "$productName warranty has expired"
        )
}
