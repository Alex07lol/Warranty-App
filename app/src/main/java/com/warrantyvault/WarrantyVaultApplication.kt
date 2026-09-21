package com.warrantyvault

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.warrantyvault.data.AppDatabase
import com.warrantyvault.data.User
import com.warrantyvault.worker.ExpiryCheckWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WarrantyVaultApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    var currentUserId: Long = 1L // Default guest/local user ID

    override fun onCreate() {
        super.onCreate()
        // Verify & schedule the periodic warranty expiry check at app start.
        ExpiryCheckWorker.schedule(this)

        // POST_NOTIFICATIONS runtime permission (API 33+). The worker silently skips
        // system notifications when this is denied; in-app alerts still work.
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                com.warrantyvault.notification.NotificationPermissionHelper.request(this)
            }
        }

        // Seed default demo user and products if empty
        CoroutineScope(Dispatchers.IO).launch {
            val userDao = database.userDao()
            val productDao = database.productDao()

            val defaultUser = userDao.getUserByIdSuspend(1L)
            if (defaultUser == null) {
                userDao.insert(
                    User(
                        id = 1L,
                        name = "Aakash (Local)",
                        email = "aakash@warrantyvault.local",
                        passwordHash = "local_secure",
                        isActive = true,
                        isEmailVerified = true
                    )
                )

                // Seed Demo Products
                val now = System.currentTimeMillis()
                val day = 86400000L

                productDao.insertProduct(
                    com.warrantyvault.data.Product(
                        userId = 1L,
                        productName = "Dell XPS 15 Laptop",
                        brand = "Dell",
                        model = "XPS 15 9530",
                        category = "Laptop",
                        serialNumber = "XPS15-7G2K9",
                        purchaseDate = now - (300L * day),
                        purchasePrice = 2099.0,
                        currency = "USD",
                        purchaseStore = "Best Buy",
                        warrantyPeriodMonths = 12,
                        warrantyExpiryDate = now - (35L * day),
                        tags = "[\"work\", \"high value\"]",
                        notes = "Primary work machine — extended support plan."
                    )
                )

                productDao.insertProduct(
                    com.warrantyvault.data.Product(
                        userId = 1L,
                        productName = "Samsung Galaxy S23",
                        brand = "Samsung",
                        model = "SM-S911",
                        category = "Smartphone",
                        serialNumber = "S23-84KD2",
                        purchaseDate = now - (200L * day),
                        purchasePrice = 899.0,
                        currency = "USD",
                        purchaseStore = "Amazon",
                        warrantyPeriodMonths = 24,
                        warrantyExpiryDate = now + (4L * day),
                        lifecycleStatus = "in_use",
                        tags = "[\"mobile\", \"daily\"]",
                        warrantyProvider = "Samsung Care+",
                        warrantyProviderType = "extended",
                        warrantyContact = "support@samsung.com",
                        notes = "Screen replaced once."
                    )
                )

                productDao.insertProduct(
                    com.warrantyvault.data.Product(
                        userId = 1L,
                        productName = "LG 4K OLED TV",
                        brand = "LG",
                        model = "OLED65C3",
                        category = "Television",
                        serialNumber = "LG65-29FL7",
                        purchaseDate = now - (150L * day),
                        purchasePrice = 1799.0,
                        currency = "USD",
                        purchaseStore = "Best Buy",
                        warrantyPeriodMonths = 12,
                        warrantyExpiryDate = now + (12L * day),
                        tags = "[\"home\", \"entertainment\"]",
                        notes = "Living room setup."
                    )
                )
            }
        }
    }
}
