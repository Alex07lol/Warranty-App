package com.warrantyvault.backup

/**
 * Decision logic for background auto-sync, kept pure so the rules can be unit-tested without a
 * device, a database or a network.
 *
 * The two questions it answers are deliberately separate:
 *
 *  1. **May auto-sync run at all?** Only when an account is linked, the user has left auto-backup
 *     on, and a previous grant failure has not paused it (a background worker must never pop a
 *     consent screen at the user, so a lost grant pauses syncing until they re-link).
 *  2. **May this device replace the backup on Drive?** Never with *less* data than Drive already
 *     holds. A fresh install seeds demo products, and a half-restored device has a partial vault;
 *     without this rule the first background sync would silently overwrite a richer backup with
 *     seed data. Equal or larger local vaults upload normally, and an explicit user-initiated
 *     backup always wins.
 */
object AutoSyncPolicy {

    fun shouldSync(linked: Boolean, enabled: Boolean, paused: Boolean): Boolean =
        linked && enabled && !paused

    /** What to do with an upload request: send it, or refuse and explain. */
    sealed class UploadDecision {
        object Upload : UploadDecision()
        data class Refuse(val reason: String) : UploadDecision()
    }

    /**
     * @param force true for a user-initiated "Back up now" (always uploads).
     * @param localCount products on this device.
     * @param remoteProductCount products recorded in the backup already on Drive, when known.
     */
    fun decideUpload(force: Boolean, localCount: Int, remoteProductCount: Int?): UploadDecision {
        if (force) return UploadDecision.Upload
        // No backup yet, or one uploaded before the property existed: nothing to protect.
        if (remoteProductCount == null) return UploadDecision.Upload
        if (localCount >= remoteProductCount) return UploadDecision.Upload

        return UploadDecision.Refuse(
            "Drive holds $remoteProductCount product(s), this device has $localCount. Restore that " +
                "backup — or, if you removed products on purpose, use Back up now to update Drive."
        )
    }
}
