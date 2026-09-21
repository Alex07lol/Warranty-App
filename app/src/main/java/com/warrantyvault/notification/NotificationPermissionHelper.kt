package com.warrantyvault.notification

import android.app.Activity
import android.content.Context
import androidx.core.app.ActivityCompat

/**
 * Thin helper for the POST_NOTIFICATIONS runtime permission on API 33+.
 * Kept separate from the Application so the logic is testable and swappable.
 */
object NotificationPermissionHelper {
    // Inlined constant; every caller guards with Build.VERSION.SDK_INT >= 33.
    const val PERMISSION = "android.permission.POST_NOTIFICATIONS"
    const val REQUEST_CODE = 4242

    fun request(context: Context) {
        if (context is Activity) {
            ActivityCompat.requestPermissions(context, arrayOf(PERMISSION), REQUEST_CODE)
        }
        // Non-activity contexts cannot prompt; the worker degrades gracefully to in-app alerts.
    }
}
