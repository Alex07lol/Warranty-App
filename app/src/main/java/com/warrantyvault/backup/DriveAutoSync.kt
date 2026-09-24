package com.warrantyvault.backup

import android.content.Context
import android.util.Log
import com.warrantyvault.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Watches the vault and schedules an upload whenever it changes.
 *
 * The hook is Room's own change notification — the same mechanism that refreshes UI queries — rather
 * than calls sprinkled through individual screens. Any current or future write path (scan confirm,
 * manual add/edit, bulk import, service history) is therefore covered without being wired up
 * individually.
 *
 * `drop(1)` on each query discards its initial snapshot, so only an actual change schedules work:
 * app start must not trigger an upload, which on a fresh install would push seed data to Drive
 * before the user has had a chance to restore.
 *
 * Coverage note: products and service history are watched. A warranty-period row changed entirely
 * on its own does not emit, so that edit rides along with the next product change or manual backup.
 */
class DriveAutoSync(
    private val context: Context,
    private val db: AppDatabase,
    private val userId: Long
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            try {
                combine(
                    db.productDao().getAllProducts(userId).drop(1),
                    db.serviceHistoryDao().getAllServiceHistory(userId).drop(1)
                ) { _, _ -> Unit }.collect {
                    DriveSyncWorker.enqueue(context)
                }
            } catch (t: Throwable) {
                // A broken observer must never take the app down; sync simply stops until restart.
                Log.w(TAG, "Auto-sync watcher stopped: ${t.message}")
            }
        }
    }

    companion object {
        private const val TAG = "DriveAutoSync"
    }
}
