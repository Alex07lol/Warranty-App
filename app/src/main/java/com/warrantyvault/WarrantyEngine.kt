package com.warrantyvault

import java.util.Calendar

data class WarrantyInfo(
    val status: String, // not_started, active, expiring_soon, expired, unknown
    val daysRemaining: Int?,
    val label: String,
    val badgeClass: String
)

object WarrantyEngine {
    private const val EXPIRING_SOON_DAYS = 30

    fun startOfDay(timeMillis: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    fun warrantyStatusOf(startDate: Long?, expiryDate: Long?, nowMillis: Long = System.currentTimeMillis()): WarrantyInfo {
        if (expiryDate == null) {
            return WarrantyInfo("unknown", null, "Unknown", "unknown")
        }
        val today = startOfDay(nowMillis)
        val expiry = startOfDay(expiryDate)
        val daysRemaining = Math.ceil((expiry - today).toDouble() / 86400000.0).toInt()

        if (startDate != null) {
            val start = startOfDay(startDate)
            if (start > today) {
                return WarrantyInfo("not_started", daysRemaining, "Not started", "not-started")
            }
        }

        if (daysRemaining <= 0) {
            return WarrantyInfo("expired", daysRemaining, "Expired", "expired")
        }
        if (daysRemaining <= EXPIRING_SOON_DAYS) {
            return WarrantyInfo("expiring_soon", daysRemaining, "$daysRemaining days left", "expiring-soon")
        }
        return WarrantyInfo("active", daysRemaining, "$daysRemaining days left", "active")
    }
}