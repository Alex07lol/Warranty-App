package com.warrantyvault.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The overwrite guard is the part of auto-sync that protects user data, so its rules are pinned
 * here: a device holding less than the Drive backup must never replace it automatically, while an
 * explicit backup by the user always goes through.
 */
class AutoSyncPolicyTest {

    // ---------------------------------------------------------------- shouldSync

    @Test
    fun `sync runs only when linked enabled and not paused`() {
        assertTrue(AutoSyncPolicy.shouldSync(linked = true, enabled = true, paused = false))
    }

    @Test
    fun `sync is off when any precondition fails`() {
        assertFalse("unlinked", AutoSyncPolicy.shouldSync(linked = false, enabled = true, paused = false))
        assertFalse("auto-backup off", AutoSyncPolicy.shouldSync(linked = true, enabled = false, paused = false))
        assertFalse("paused after a lost grant", AutoSyncPolicy.shouldSync(linked = true, enabled = true, paused = true))
        assertFalse("nothing satisfied", AutoSyncPolicy.shouldSync(linked = false, enabled = false, paused = true))
    }

    // ---------------------------------------------------------------- shouldRun

    @Test
    fun `an automatic run obeys the toggle and the paused state`() {
        assertTrue(AutoSyncPolicy.shouldRun(userInitiated = false, linked = true, enabled = true, paused = false))
        assertFalse("auto-backup off", AutoSyncPolicy.shouldRun(userInitiated = false, linked = true, enabled = false, paused = false))
        assertFalse("paused", AutoSyncPolicy.shouldRun(userInitiated = false, linked = true, enabled = true, paused = true))
        assertFalse("unlinked", AutoSyncPolicy.shouldRun(userInitiated = false, linked = false, enabled = true, paused = false))
    }

    @Test
    fun `a tap on the banner syncs even when automatic backup is off`() {
        // The whole point of the one-tap action: it must work for someone who keeps auto-backup off.
        assertTrue(AutoSyncPolicy.shouldRun(userInitiated = true, linked = true, enabled = false, paused = false))
    }

    @Test
    fun `a tap still needs an account`() {
        assertFalse(AutoSyncPolicy.shouldRun(userInitiated = true, linked = false, enabled = true, paused = false))
    }

    // ---------------------------------------------------------------- decideUpload

    @Test
    fun `a user-initiated backup always uploads even when it would shrink the backup`() {
        val decision = AutoSyncPolicy.decideUpload(force = true, localCount = 1, remoteProductCount = 12)
        assertEquals(AutoSyncPolicy.UploadDecision.Upload, decision)
    }

    @Test
    fun `first ever backup uploads even from an empty vault`() {
        val decision = AutoSyncPolicy.decideUpload(force = false, localCount = 0, remoteProductCount = null)
        assertEquals(AutoSyncPolicy.UploadDecision.Upload, decision)
    }

    @Test
    fun `backup without a recorded product count is treated as unknown and not blocked`() {
        val decision = AutoSyncPolicy.decideUpload(force = false, localCount = 0, remoteProductCount = null)
        assertEquals(AutoSyncPolicy.UploadDecision.Upload, decision)
    }

    @Test
    fun `normal growth and equal states upload`() {
        assertEquals(
            AutoSyncPolicy.UploadDecision.Upload,
            AutoSyncPolicy.decideUpload(force = false, localCount = 5, remoteProductCount = 3)
        )
        assertEquals(
            AutoSyncPolicy.UploadDecision.Upload,
            AutoSyncPolicy.decideUpload(force = false, localCount = 3, remoteProductCount = 3)
        )
    }

    @Test
    fun `a fresh install cannot overwrite a richer backup with seed data`() {
        // The app seeds three demo products on a first run; a real backup may hold many more.
        val decision = AutoSyncPolicy.decideUpload(force = false, localCount = 3, remoteProductCount = 11)

        assertTrue(decision is AutoSyncPolicy.UploadDecision.Refuse)
        val reason = (decision as AutoSyncPolicy.UploadDecision.Refuse).reason
        assertTrue("reason names the remote count", reason.contains("11"))
        assertTrue("reason names the local count", reason.contains("3"))
        assertTrue("reason suggests restoring", reason.contains("Restore"))
    }

    @Test
    fun `a half-restored device is also refused`() {
        val decision = AutoSyncPolicy.decideUpload(force = false, localCount = 4, remoteProductCount = 5)
        assertTrue(decision is AutoSyncPolicy.UploadDecision.Refuse)
    }
}
