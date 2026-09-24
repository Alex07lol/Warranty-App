package com.warrantyvault.backup

/**
 * Everything the UI needs to describe the Google Drive backup, in one immutable snapshot.
 *
 * Deliberately a plain data class rather than something the service owns: the presentation logic
 * that turns it into user-facing text is pure and unit-testable, and screens observe it as a flow
 * so a sync performed by the background worker shows up without any polling.
 */
data class SyncState(
    val linked: Boolean = false,
    val email: String? = null,
    val autoSyncEnabled: Boolean = true,
    val visibleCopyEnabled: Boolean = true,
    /** Set when syncing has stopped until the user re-links (lost or revoked grant). */
    val paused: Boolean = false,
    /** Last result worth telling the user: a skip reason, a warning, or an error. */
    val message: String? = null,
    val lastSyncMillis: Long = 0L,
    val lastSyncCount: Int = 0,
    /**
     * Vault changes recorded since the last successful upload. They are counted, not queued: the
     * upload always sends the whole vault, so this is a "you have unsynced work" signal.
     */
    val pendingChanges: Int = 0,
    val pendingSinceMillis: Long = 0L,
    /** An upload is in flight right now (in-memory only; always false after a process restart). */
    val syncing: Boolean = false
) {
    val hasPendingWork: Boolean get() = pendingChanges > 0
}
