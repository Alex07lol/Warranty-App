package com.warrantyvault.ui

import com.warrantyvault.backup.SyncState
import com.warrantyvault.ui.components.DriveSyncStatus
import com.warrantyvault.ui.components.DriveSyncStatus.Action
import com.warrantyvault.ui.components.DriveSyncStatus.Variant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The banner is the only place the user learns that their changes are not backed up, so the
 * precedence between its states is pinned here: a problem outranks unsynced changes, and unsynced
 * changes outrank the reassuring "backed up" state.
 */
class DriveSyncStatusTest {

    private val now = 1_800_000_000_000L
    private val minute = 60_000L

    private fun state(
        linked: Boolean = true,
        email: String? = "aakash@example.com",
        autoSyncEnabled: Boolean = true,
        paused: Boolean = false,
        message: String? = null,
        lastSyncMillis: Long = now - 5 * minute,
        lastSyncCount: Int = 4,
        pendingChanges: Int = 0,
        syncing: Boolean = false
    ) = SyncState(
        linked = linked,
        email = email,
        autoSyncEnabled = autoSyncEnabled,
        visibleCopyEnabled = true,
        paused = paused,
        message = message,
        lastSyncMillis = lastSyncMillis,
        lastSyncCount = lastSyncCount,
        pendingChanges = pendingChanges,
        pendingSinceMillis = if (pendingChanges > 0) now - minute else 0L,
        syncing = syncing
    )

    @Test
    fun `nothing is shown until Drive backup is set up`() {
        assertEquals(Variant.HIDDEN, DriveSyncStatus.present(state(linked = false), now).variant)
    }

    @Test
    fun `a completed sync reports the product count and how long ago`() {
        val presentation = DriveSyncStatus.present(state(), now)

        assertEquals(Variant.SYNCED, presentation.variant)
        assertEquals("Backed up to Drive", presentation.title)
        assertEquals("4 products · 5 min ago", presentation.detail)
    }

    @Test
    fun `unsynced changes are reported instead of the synced state`() {
        val presentation = DriveSyncStatus.present(state(pendingChanges = 3), now)

        assertEquals(Variant.PENDING, presentation.variant)
        assertEquals("3 changes not backed up", presentation.title)
        assertTrue("detail should still say when the last backup happened",
            presentation.detail.contains("5 min ago"))
    }

    @Test
    fun `a single unsynced change reads naturally`() {
        assertEquals("1 change not backed up", DriveSyncStatus.present(state(pendingChanges = 1), now).title)
    }

    @Test
    fun `pending work with automatic backup off says so`() {
        val presentation = DriveSyncStatus.present(state(pendingChanges = 2, autoSyncEnabled = false), now)

        assertEquals(Variant.PENDING, presentation.variant)
        assertTrue(presentation.detail.contains("off"))
    }

    @Test
    fun `linked but never backed up asks for a first backup`() {
        val presentation = DriveSyncStatus.present(state(lastSyncMillis = 0L, lastSyncCount = 0), now)

        assertEquals(Variant.PENDING, presentation.variant)
        assertEquals("No Drive backup yet", presentation.title)
    }

    @Test
    fun `a paused backup shows the reason and outranks pending changes`() {
        val presentation = DriveSyncStatus.present(
            state(paused = true, message = "Re-link the account.", pendingChanges = 5),
            now
        )

        assertEquals(Variant.ERROR, presentation.variant)
        assertEquals("Drive backup paused", presentation.title)
        assertEquals("Re-link the account.", presentation.detail)
    }

    @Test
    fun `a result message outranks pending changes`() {
        val presentation = DriveSyncStatus.present(
            state(message = "Drive holds 11 product(s), this device has 3.", pendingChanges = 2),
            now
        )

        assertEquals(Variant.ERROR, presentation.variant)
        assertEquals("Drive backup needs attention", presentation.title)
    }

    @Test
    fun `paused without a message still explains what to do`() {
        val presentation = DriveSyncStatus.present(state(paused = true, message = null), now)

        assertEquals(Variant.ERROR, presentation.variant)
        assertTrue(presentation.detail.contains("Re-link"))
    }

    @Test
    fun `the sync action is offered whenever another backup would help`() {
        assertEquals(Action.SYNC, DriveSyncStatus.present(state(), now).action)
        assertEquals(Action.SYNC, DriveSyncStatus.present(state(pendingChanges = 2), now).action)
        assertEquals(
            "first backup is exactly when the button matters most",
            Action.SYNC,
            DriveSyncStatus.present(state(lastSyncMillis = 0L, lastSyncCount = 0), now).action
        )
    }

    @Test
    fun `no sync action when the fix lives in Settings instead`() {
        assertEquals(Action.NONE, DriveSyncStatus.present(state(paused = true), now).action)
        assertEquals(
            "a refusal needs Restore, not another upload attempt",
            Action.NONE,
            DriveSyncStatus.present(state(message = "Drive holds 11 product(s), this device has 3."), now).action
        )
    }

    @Test
    fun `an unlinked vault offers no action`() {
        assertEquals(Action.NONE, DriveSyncStatus.present(state(linked = false), now).action)
    }

    @Test
    fun `an upload in flight is reported and cannot be started twice`() {
        val presentation = DriveSyncStatus.present(state(syncing = true, pendingChanges = 3), now)

        assertEquals("Syncing to Drive…", presentation.title)
        assertEquals("Uploading your vault now", presentation.detail)
        assertEquals(Action.SYNCING, presentation.action)
    }

    @Test
    fun `an in-flight upload outranks pending work and an older message`() {
        val presentation = DriveSyncStatus.present(
            state(syncing = true, pendingChanges = 3, message = "earlier note"),
            now
        )

        assertEquals(Variant.PENDING, presentation.variant)
        assertEquals(Action.SYNCING, presentation.action)
    }

    @Test
    fun `relative time covers the ranges the banner can show`() {
        assertEquals("never", DriveSyncStatus.relativeTime(0L, now))
        assertEquals("just now", DriveSyncStatus.relativeTime(now - 20_000L, now))
        assertEquals("1 min ago", DriveSyncStatus.relativeTime(now - minute, now))
        assertEquals("59 min ago", DriveSyncStatus.relativeTime(now - 59 * minute, now))
        assertEquals("1 hour ago", DriveSyncStatus.relativeTime(now - 60 * minute, now))
        assertEquals("5 hours ago", DriveSyncStatus.relativeTime(now - 5 * 3_600_000L, now))
        assertEquals("yesterday", DriveSyncStatus.relativeTime(now - 24 * 3_600_000L, now))
        assertEquals("3 days ago", DriveSyncStatus.relativeTime(now - 3 * 86_400_000L, now))
    }

    @Test
    fun `a clock skew never produces a negative age`() {
        assertEquals("just now", DriveSyncStatus.relativeTime(now + 60_000L, now))
    }
}
