package com.warrantyvault.backup

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The banner's "changes not backed up" line is only trustworthy if one rule holds: a *successful*
 * upload is what clears pending work. A refused or failed sync must keep the signal, otherwise the
 * UI would claim the vault is safe when it is not.
 *
 * These run against real SharedPreferences (Robolectric), which is where that state actually lives.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DrivePendingStateTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun service() = DriveBackupService(context)

    @Test
    fun `a fresh install has nothing linked and no pending work`() {
        val state = service().syncState()

        assertFalse(state.linked)
        assertNull(state.email)
        assertFalse(state.hasPendingWork)
        assertEquals(0, state.lastSyncMillis)
        // Sensible defaults for someone who links later.
        assertTrue(state.autoSyncEnabled)
        assertTrue(state.visibleCopyEnabled)
        assertFalse(state.paused)
    }

    @Test
    fun `changes are counted and the first change time is remembered`() {
        val service = service()
        service.markPendingChanges()
        val afterFirst = service.syncState()

        service.markPendingChanges()
        val afterSecond = service.syncState()

        assertEquals(1, afterFirst.pendingChanges)
        assertEquals(2, afterSecond.pendingChanges)
        assertTrue(afterFirst.pendingSinceMillis > 0L)
        assertEquals(
            "the pending window should start at the first change",
            afterFirst.pendingSinceMillis,
            afterSecond.pendingSinceMillis
        )
    }

    @Test
    fun `a successful upload clears pending work`() {
        val service = service()
        service.markPendingChanges()
        service.markPendingChanges()
        assertTrue(service.syncState().hasPendingWork)

        service.recordSyncSuccess(productCount = 7)

        val state = service.syncState()
        assertEquals(0, state.pendingChanges)
        assertEquals(0L, state.pendingSinceMillis)
        assertNull(state.message)
        assertEquals(7, state.lastSyncCount)
        assertTrue(state.lastSyncMillis > 0L)
    }

    @Test
    fun `a refusal keeps the changes flagged and explains why`() {
        val service = service()
        service.markPendingChanges()

        service.recordSyncNote("Drive holds 11 product(s), this device has 3.")

        val state = service.syncState()
        assertTrue("a refused sync must not look like a successful one", state.hasPendingWork)
        assertEquals(1, state.pendingChanges)
        assertEquals("Drive holds 11 product(s), this device has 3.", state.message)
    }

    @Test
    fun `pausing keeps the reason and clears on the next success`() {
        val service = service()
        service.pauseAutoSync("Google Drive needs permission again.")

        assertTrue(service.syncState().paused)
        assertFalse("a paused sync is not armed", service.autoSyncArmed())

        service.clearAutoSyncPause()
        assertFalse(service.syncState().paused)
    }

    @Test
    fun `automatic backup is only armed when linked enabled and not paused`() {
        val service = service()

        // Not linked yet, so nothing is armed regardless of the toggle.
        assertFalse(service.autoSyncArmed())

        service.setAutoSyncEnabled(false)
        assertFalse(service.syncState().autoSyncEnabled)
        service.setAutoSyncEnabled(true)
        assertTrue(service.syncState().autoSyncEnabled)
    }

    @Test
    fun `the observed flow republishes on every mutation`() {
        val service = service()
        val observed = DriveBackupService.observe(context)
        assertFalse(observed.value.hasPendingWork)

        service.markPendingChanges()
        assertTrue("the dashboard banner reads this flow", observed.value.hasPendingWork)

        service.recordSyncSuccess(productCount = 2)
        assertFalse(observed.value.hasPendingWork)
        assertEquals(2, observed.value.lastSyncCount)
    }
}
