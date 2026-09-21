package com.warrantyvault.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.warrantyvault.MainActivity
import com.warrantyvault.R
import com.warrantyvault.data.AppDatabase
import com.warrantyvault.data.Notification
import com.warrantyvault.data.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object WarrantyNotifier {
    const val CHANNEL_ID = "warranty_expiry"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Warranty expiry alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Reminders when product warranties are about to expire"
        }
        manager.createNotificationChannel(channel)
    }

    /** Posts a system notification if POST_NOTIFICATIONS is granted (API 33+). */
    fun post(context: Context, id: Long, title: String, message: String) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return // No permission: in-app notifications list still works.
        }
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(id.toInt(), notification)
        } catch (e: Exception) {
            Log.e("WarrantyNotifier", "Failed to post notification", e)
        }
    }
}

/**
 * Periodic warranty expiry check. Creates one in-app Notification row per product per stage
 * (30 days / 7 days / 1 day / expired) and deduplicates per stage so repeated runs never spam.
 */
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

    companion object {
        /** One-time schedule helper used by the Application and the boot receiver. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(12, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "warranty_expiry_check",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    private suspend fun checkExpiringProducts() {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val context = applicationContext

        WarrantyNotifier.ensureChannel(context)

        val expiringSoon = db.productDao().getExpiringSoonProducts(now, now + 30 * day)
        for (product in expiringSoon) {
            val daysLeft = daysUntil(product.warrantyExpiryDate!!, now)
            val stage = when {
                daysLeft <= 1 -> "1_day"
                daysLeft <= 7 -> "7_days"
                else -> "30_days"
            }
            maybeNotify(
                product = product,
                stage = stage,
                title = "Warranty expiring soon",
                message = "${product.productName} warranty expires on ${formatDate(product.warrantyExpiryDate)} ($daysLeft day${if (daysLeft == 1L) "" else "s"} left)."
            )
        }

        val expired = db.productDao().getExpiredProducts(now)
        for (product in expired) {
            maybeNotify(
                product = product,
                stage = "expired",
                title = "Warranty expired",
                message = "${product.productName} warranty has expired."
            )
        }
    }

    /**
     * Dedupe key: userId + productId + notificationType + stage. A new row is created only
     * when this stage hasn't fired yet, so WorkManager reruns never spam the user.
     */
    private suspend fun maybeNotify(product: Product, stage: String, title: String, message: String) {
        val dao = db.notificationDao()
        if (dao.existsForProductStage(product.userId, product.id, "warranty_expiry:$stage")) {
            return
        }
        dao.insert(
            Notification(
                userId = product.userId,
                productId = product.id,
                notificationType = "warranty_expiry:$stage",
                title = title,
                message = message,
                isRead = false,
                scheduledAt = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis()
            )
        )
        WarrantyNotifier.post(applicationContext, product.id, title, message)
        Log.i("ExpiryCheckWorker", "Notification [$stage] for ${product.productName}")
    }

    private fun daysUntil(expiry: Long, now: Long): Long {
        val diff = expiry - now
        return Math.max(0, (diff + 86_399_999L) / 86_400_000L)
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
