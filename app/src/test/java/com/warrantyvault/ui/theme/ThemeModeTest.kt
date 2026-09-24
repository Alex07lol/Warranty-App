package com.warrantyvault.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Light/Dark choice used to be impossible to make — the app followed the device and the Settings
 * row did nothing — so the resolution rule is pinned here rather than left to the UI: an override
 * must win in *both* directions, and a stored value must never be able to leave the app in an
 * unknown state.
 */
class ThemeModeTest {

    @Test
    fun `System follows the device while Light and Dark override it`() {
        assertTrue(ThemeMode.System.isDark(systemInDark = true))
        assertFalse(ThemeMode.System.isDark(systemInDark = false))

        assertFalse(
            "Light must stay light even on a dark device — this is the whole point of the override",
            ThemeMode.Light.isDark(systemInDark = true)
        )
        assertTrue(
            "Dark must stay dark even on a light device",
            ThemeMode.Dark.isDark(systemInDark = false)
        )
    }

    @Test
    fun `the default is to follow the device`() {
        assertEquals(ThemeMode.System, ThemeMode.Default)
    }

    @Test
    fun `every mode round-trips through storage`() {
        ThemeMode.values().forEach { mode ->
            assertEquals(mode, ThemeMode.fromStorage(mode.storageValue))
        }
    }

    @Test
    fun `stored values are matched loosely so a hand-edited value still works`() {
        assertEquals(ThemeMode.Dark, ThemeMode.fromStorage("DARK"))
        assertEquals(ThemeMode.Light, ThemeMode.fromStorage(" Light "))
    }

    @Test
    fun `a missing or unrecognised stored value falls back to System instead of crashing`() {
        assertEquals(ThemeMode.System, ThemeMode.fromStorage(null))
        assertEquals(ThemeMode.System, ThemeMode.fromStorage(""))
        assertEquals(ThemeMode.System, ThemeMode.fromStorage("midnight"))
    }

    @Test
    fun `the preference is observable and published immediately`() {
        ThemePreference.setInMemory(ThemeMode.System)
        assertEquals(ThemeMode.System, ThemePreference.mode.value)

        ThemePreference.setInMemory(ThemeMode.Dark)
        assertEquals(
            "a change in Settings must reach every screen without a restart",
            ThemeMode.Dark,
            ThemePreference.mode.value
        )

        ThemePreference.setInMemory(ThemeMode.System)
    }

    @Test
    fun `the stored key matches the web app's so both front-ends agree`() {
        assertEquals("wv_theme", ThemePreference.KEY)
    }

    @Test
    fun `the settings row explains what the current choice is doing`() {
        assertTrue(ThemeMode.System.summary(systemInDark = true).contains("dark"))
        assertTrue(ThemeMode.System.summary(systemInDark = false).contains("light"))

        assertTrue(
            "an override should say it ignores the device",
            ThemeMode.Light.summary(systemInDark = true).contains("always light")
        )
        assertTrue(ThemeMode.Dark.summary(systemInDark = false).contains("always dark"))
    }
}
