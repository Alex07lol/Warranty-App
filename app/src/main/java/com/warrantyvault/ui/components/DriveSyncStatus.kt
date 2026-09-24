package com.warrantyvault.ui.components

import com.warrantyvault.backup.SyncState

/**
 * Decides what the Drive sync banner says.
 *
 * Kept free of Compose and Android types so the wording and the precedence between states are
 * unit-testable. The precedence matters: a problem must outrank unsynced changes, and unsynced
 * changes must outrank the reassuring "backed up" state — otherwise the banner would quietly hide
 * the one thing the user needs to know.
 */
object DriveSyncStatus {

    enum class Variant { HIDDEN, SYNCED, PENDING, ERROR }

    data class Presentation(val variant: Variant, val title: String, val detail: String)

    fun present(state: SyncState, now: Long): Presentation {
        // Nothing to say until Drive backup has actually been set up.
        if (!state.linked) return Presentation(Variant.HIDDEN, "", "")

        if (state.paused) {
            return Presentation(
                Variant.ERROR,
                "Drive backup paused",
                state.message ?: "Re-link your Google account to continue."
            )
        }

        state.message?.let { message ->
            return Presentation(Variant.ERROR, "Drive backup needs attention", message)
        }

        if (state.pendingChanges > 0) {
            val title = if (state.pendingChanges == 1) {
                "1 change not backed up"
            } else {
                "${state.pendingChanges} changes not backed up"
            }
            return Presentation(Variant.PENDING, title, pendingDetail(state, now))
        }

        if (state.lastSyncMillis <= 0L) {
            return Presentation(Variant.PENDING, "No Drive backup yet", "Tap to back up your vault")
        }

        return Presentation(
            Variant.SYNCED,
            "Backed up to Drive",
            "${productLabel(state.lastSyncCount)} · ${relativeTime(state.lastSyncMillis, now)}"
        )
    }

    private fun pendingDetail(state: SyncState, now: Long): String = when {
        !state.autoSyncEnabled -> "Automatic backup is off — tap to back up"
        state.lastSyncMillis <= 0L -> "No backup uploaded yet"
        else -> "Last backup ${relativeTime(state.lastSyncMillis, now)}"
    }

    private fun productLabel(count: Int): String = if (count == 1) "1 product" else "$count products"

    /** Compact relative time. [now] is a parameter so tests are deterministic. */
    fun relativeTime(epochMillis: Long, now: Long): String {
        if (epochMillis <= 0L) return "never"
        val delta = now - epochMillis
        return when {
            delta < MINUTE -> "just now"
            delta < HOUR -> "${delta / MINUTE} min ago"
            delta < DAY -> {
                val hours = delta / HOUR
                if (hours == 1L) "1 hour ago" else "$hours hours ago"
            }
            else -> {
                val days = delta / DAY
                if (days == 1L) "yesterday" else "$days days ago"
            }
        }
    }

    private const val MINUTE = 60_000L
    private const val HOUR = 3_600_000L
    private const val DAY = 86_400_000L
}
