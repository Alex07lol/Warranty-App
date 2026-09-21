package com.warrantyvault.notification

/**
 * Constants for the POST_NOTIFICATIONS runtime permission on API 33+.
 * Requesting is a UI/Activity concern (MainActivity); this object only centralizes the
 * permission string so worker and UI agree on it.
 */
object NotificationPermissionHelper {
    // Inlined constant; every caller guards with Build.VERSION.SDK_INT >= 33.
    const val PERMISSION = "android.permission.POST_NOTIFICATIONS"
    const val REQUEST_CODE = 4242
}
