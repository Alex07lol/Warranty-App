package com.warrantyvault.service

import com.warrantyvault.data.Notification
import com.warrantyvault.data.NotificationDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationService(private val dao: NotificationDao) {
    suspend fun createExpiringSoonNotification(productId: Long, productName: String, expiryDate: Long) = withContext(Dispatchers.IO) {
        val notif = Notification(
            userId = 1,
            notificationType = "warranty_expiry",
            title = "Warranty Expiring Soon",
            message = "$productName warranty expires on ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(expiryDate))}",
            productId = productId,
            isRead = false,
            createdAt = System.currentTimeMillis()
        )
        dao.insert(notif)
    }

    suspend fun createExpiredNotification(productId: Long, productName: String) = withContext(Dispatchers.IO) {
        val notif = Notification(
            userId = 1,
            notificationType = "warranty_expiry",
            title = "Warranty Expired",
            message = "$productName warranty has expired",
            productId = productId,
            isRead = false,
            createdAt = System.currentTimeMillis()
        )
        dao.insert(notif)
    }
}