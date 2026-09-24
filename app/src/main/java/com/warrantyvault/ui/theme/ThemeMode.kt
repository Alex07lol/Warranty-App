package com.warrantyvault.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which palette the app should render with. The web app keeps the equivalent choice in
 * `localStorage['wv_theme']`; we keep ours in SharedPreferences under the same key so the two
 * front-ends agree on vocabulary.
 */
enum class ThemeMode(val storageValue: String, val label: String) {
    /** Follow the device's dark-mode setting (the historical behaviour). */
    System("system", "System"),

    /** Always the light palette, even when the device is dark. */
    Light("light", "Light"),

    /** Always the dark palette, even when the device is light. */
    Dark("dark", "Dark");

    /**
     * Pure resolution of the preference against what the OS reports. Kept free of Android types so
     * it can be unit-tested on the JVM.
     */
    fun isDark(systemInDark: Boolean): Boolean = when (this) {
        System -> systemInDark
        Light -> false
        Dark -> true
    }

    companion object {
        val Default = System

        /** Never throws: an unknown or missing stored value falls back to [Default]. */
        fun fromStorage(raw: String?): ThemeMode =
            values().firstOrNull { it.storageValue.equals(raw?.trim(), ignoreCase = true) } ?: Default
    }
}

/**
 * One-line, user-facing explanation of what this preference is doing right now — pure, so the
 * wording is unit-tested rather than eyeballed in the settings row.
 */
fun ThemeMode.summary(systemInDark: Boolean): String = when (this) {
    ThemeMode.System -> "System · your device is ${if (systemInDark) "dark" else "light"} right now"
    ThemeMode.Light -> "Light · always light, whatever the device is set to"
    ThemeMode.Dark -> "Dark · always dark, whatever the device is set to"
}

/**
 * The stored theme preference, held in memory as a flow so Compose recomposes immediately when it
 * changes and so the choice survives a restart.
 *
 * [load] runs once from `Application.onCreate` — before the first frame — so launching the app can
 * never flash the wrong palette.
 */
object ThemePreference {

    const val PREFS = "wv_prefs"
    const val KEY = "wv_theme"

    private val _mode = MutableStateFlow(ThemeMode.Default)

    /** The active preference. Observing screens recompose when it changes. */
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    /** Reads the persisted choice into memory. Safe to call more than once. */
    fun load(context: Context) {
        _mode.value = ThemeMode.fromStorage(prefs(context).getString(KEY, null))
    }

    /** Persists and publishes a new choice. */
    fun set(context: Context, mode: ThemeMode) {
        prefs(context).edit().putString(KEY, mode.storageValue).apply()
        _mode.value = mode
    }

    /** Test seam: set the in-memory value without touching disk. */
    fun setInMemory(mode: ThemeMode) {
        _mode.value = mode
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
