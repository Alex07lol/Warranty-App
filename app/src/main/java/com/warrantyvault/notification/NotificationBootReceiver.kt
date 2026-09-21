package com.warrantyvault.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.warrantyvault.worker.ExpiryCheckWorker

class NotificationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Periodic work is persisted by WorkManager across reboots, but KEEP-scheduling
            // here is cheap insurance for devices that clear app data or force-stop the app.
            ExpiryCheckWorker.schedule(context)
        }
    }
}
