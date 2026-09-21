package com.warrantyvault.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.warrantyvault.data.AppDatabase
import com.warrantyvault.data.Notification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExpiryCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "ExpiryCheckWorker"
    private val db = AppDatabase.getDatabase(applicationContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting expiry check worker")
            checkExpiringProducts()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in expiry check worker", e)
            Result.retry()
        }
    }

    private suspend fun checkExpiringProducts() {
        val now = System.currentTimeMillis()
        val thirtyDaysFromNow = now + (30L * 24 * 60 * 60 * 1000)
        
        // Get products expiring in the next 30 days
        val expiringProducts = db.productDao().getExpiringSoonProducts(now, thirtyDaysFromNow)
        
        for (product in expiringProducts) {
            val notification = Notification(
                userId = product.userId,
                productId = product.id,
                notificationType = "warranty_expiry",
                title = "Warranty Expiring Soon",
                message = "${product.productName} warranty expires on ${formatDate(product.warrantyExpiryDate)}",
                isRead = false,
                createdAt = System.currentTimeMillis()
            )
            
            val existingNotification = db.notificationDao()
                .getRecentNotificationForProduct(product.userId, product.id, now - (24 * 60 * 60 * 1000))
            
            if (existingNotification == null) {
                db.notificationDao().insert(notification)
                Log.i(TAG, "Created expiry notification for product: ${product.productName}")
            }
        }
        
        // Also check for expired products
        val expiredProducts = db.productDao().getExpiredProducts(now)
        
        for (product in expiredProducts) {
            val notification = Notification(
                userId = product.userId,
                productId = product.id,
                notificationType = "warranty_expiry",
                title = "Warranty Expired",
                message = "${product.productName} warranty has expired",
                isRead = false,
                createdAt = System.currentTimeMillis()
            )
            
            val existingNotification = db.notificationDao()
                .getRecentNotificationForProduct(product.userId, product.id, now - (24 * 60 * 60 * 1000))
            
            if (existingNotification == null) {
                db.notificationDao().insert(notification)
                Log.i(TAG, "Created expired notification for product: ${product.productName}")
            }
        }
    }

    private fun formatDate(timestamp: Long?): String {
        return if (timestamp != null) {
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
        } else {
            "Unknown date"
        }
    }
}

// Factory for creating the worker
class ExpiryCheckWorkerFactory(
    private val appContext: Context
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return if (workerClassName == ExpiryCheckWorker::class.java.name) {
            ExpiryCheckWorker(appContext, workerParameters)
        } else {
            null
        }
    }
}