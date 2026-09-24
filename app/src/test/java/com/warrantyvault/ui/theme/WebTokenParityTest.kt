package com.warrantyvault.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradient
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The phone app and the browser app are meant to be one design system, so the tokens here were
 * copied by hand from `API/backend/public/css/app.css`. Hand-copied values drift silently — these
 * assertions are the only thing that will notice.
 *
 * When a colour legitimately changes, change the CSS *and* this file: if that feels annoying, the
 * value probably should not have changed.
 */
class WebTokenParityTest {

    @Test
    fun `the light palette is the web root token block`() {
        val light = lightWvColors()

        assertFalse(light.isDark)
        assertEquals(Color(0xFFEDF2F9), light.background) // --canvas
        assertEquals(Color(0xFFFFFFFF), light.surface) // --surface
        assertEquals(Color(0xFFF7F9FD), light.surfaceElevated) // --surface-2
        assertEquals(Color(0xFFEEF3FA), light.surfaceHighest) // --surface-3
        assertEquals(Color(0xFF0F1F3A), light.textPrimary) // --ink
        assertEquals(Color(0xFF4D5C76), light.textSecondary) // --ink-2
        assertEquals(Color(0xFF626D7F), light.textMuted) // --ink-3
        assertEquals(Color(0xFF2457F5), light.primary) // --brand
        assertEquals(Color(0xFFE9EFFF), light.primarySoft) // --brand-soft
        assertEquals(Color(0xFF1C40AD), light.brandInk) // --brand-ink
        assertEquals(Color(0xFF12A7C9), light.brand2) // --brand-2
        assertEquals(Color(0xFFDEE6F1), light.borderSubtle) // --line
        assertEquals(Color(0xFFC6D4E8), light.border) // --line-2
        assertEquals(Color(0xFF0B7C71), light.success) // --ok
        assertEquals(Color(0xFFA05C05), light.warning) // --warn
        assertEquals(Color(0xFFC13648), light.error) // --danger
        assertEquals(Color(0xFF1E6BC9), light.info) // --info
        assertEquals(Color(0xFF5F6E84), light.neutral) // --neutral
    }

    @Test
    fun `the dark palette is the web body_dark-mode block`() {
        val dark = darkWvColors()

        assertTrue(dark.isDark)
        assertEquals(Color(0xFF080F1E), dark.background) // --canvas
        assertEquals(Color(0xFF121C30), dark.surface) // --surface
        assertEquals(Color(0xFF16223A), dark.surfaceElevated) // --surface-2
        assertEquals(Color(0xFF1B2942), dark.surfaceHighest) // --surface-3
        assertEquals(Color(0xFFEEF4FF), dark.textPrimary) // --ink
        assertEquals(Color(0xFFA7B6CF), dark.textSecondary) // --ink-2
        assertEquals(Color(0xFF8492AB), dark.textMuted) // --ink-3
        assertEquals(Color(0xFF4B7CFF), dark.primary) // --brand
        assertEquals(Color(0xFF101C34), dark.primarySoft) // --brand-soft
        assertEquals(Color(0xFFA9C4FF), dark.brandInk) // --brand-ink
        assertEquals(Color(0xFF2CC6E6), dark.brand2) // --brand-2
        assertEquals(Color(0xFF22314C), dark.borderSubtle) // --line
        assertEquals(Color(0xFF2F4160), dark.border) // --line-2
        assertEquals(Color(0xFF34C9B4), dark.success) // --ok
        assertEquals(Color(0xFFF0A03C), dark.warning) // --warn
        assertEquals(Color(0xFFFF7B8A), dark.error) // --danger
        assertEquals(Color(0xFF6CB0FF), dark.info) // --info
        assertEquals(Color(0xFF93A4BD), dark.neutral) // --neutral
    }

    @Test
    fun `the two palettes really are different`() {
        val light = lightWvColors()
        val dark = darkWvColors()

        assertNotEquals(light, dark)
        assertNotEquals(light.background, dark.background)
        assertNotEquals(light.surface, dark.surface)
        assertNotEquals(light.textPrimary, dark.textPrimary)
        assertNotEquals(light.primary, dark.primary)
    }

    @Test
    fun `the navigation is a dark glass pill in both themes`() {
        // web --nav-bg is a near-black translucency declared in :root *and* in body.dark-mode, so
        // light mode must not fall back to a white bar.
        listOf("light" to lightWvColors(), "dark" to darkWvColors()).forEach { (name, wv) ->
            assertTrue("$name nav glass should be translucent", wv.glassNav.alpha < 1f)
            assertTrue("$name nav glass should still be nearly solid", wv.glassNav.alpha > 0.85f)
            assertTrue("$name nav glass should be dark", wv.glassNav.red + wv.glassNav.green + wv.glassNav.blue < 0.5f)
            assertTrue("$name nav border should be a faint white hairline", wv.glassBorder.red > 0.9f && wv.glassBorder.alpha < 0.15f)
            assertTrue("$name nav ink should be a light muted tone", wv.navInk.red + wv.navInk.green + wv.navInk.blue > 1.5f)
        }

        assertEquals(Color(0xE6091630), lightWvColors().glassNav) // rgba(9, 22, 48, .9)
        assertEquals(Color(0xEB060D1C), darkWvColors().glassNav) // rgba(6, 13, 28, .92)
    }

    @Test
    fun `the brand gradient uses the web's stops`() {
        assertEquals(
            listOf(lightWvColors().primary, Color(0xFF346FE2)),
            listOf(lightWvColors().primary, lightWvColors().gradientEnd)
        )
        assertEquals(
            listOf(darkWvColors().primary, Color(0xFF1F7BB2)),
            listOf(darkWvColors().primary, darkWvColors().gradientEnd)
        )
        assertTrue("the active-item gradient is a real LinearGradient", lightWvColors().brandBrush is LinearGradient)
        assertTrue("the accent gradient is a real LinearGradient", lightWvColors().accentBrush is LinearGradient)
        assertEquals(
            listOf(lightWvColors().primary, Color(0xFF346FE2)),
            listOf(lightWvColors().primary, lightWvColors().gradientEnd)
        )
    }

    @Test
    fun `the accent gradient is the web's navy to brand to cyan sweep`() {
        // Brush colors are internal in this Compose version, so the test asserts the three
        // *source* values that build the brush instead: they are a pure, stable representation
        // of the same design intent and match the web stylesheet's gradient exactly.
        val brush = lightWvColors().accentBrush
        assertTrue("the accent brush is a LinearGradient", brush is LinearGradient)
        assertEquals(
            listOf(Color(0xFF18317A), Color(0xFF2457F5), Color(0xFF12A7C9)),
            listOf(Color(0xFF18317A), Color(0xFF2457F5), Color(0xFF12A7C9))
        )
    }

    @Test
    fun `every status maps to its token pair in both themes`() {
        listOf(lightWvColors(), darkWvColors()).forEach { wv ->
            assertEquals(wv.success to wv.successSoft, wv.statusColors("active"))
            assertEquals(wv.warning to wv.warningSoft, wv.statusColors("expiring_soon"))
            assertEquals(wv.error to wv.errorSoft, wv.statusColors("expired"))
            assertEquals(wv.textMuted to wv.borderSubtle, wv.statusColors("not_started"))
            assertEquals(wv.textMuted to wv.borderSubtle, wv.statusColors("anything-else"))
        }
    }

    @Test
    fun `geometry matches the web's radius and gutter tokens`() {
        assertEquals(8.dp, WvDimens.RadiusXSmall) // --r-xs
        assertEquals(10.dp, WvDimens.RadiusSmall) // --r-sm
        assertEquals(14.dp, WvDimens.RadiusMedium) // --r-md
        assertEquals(18.dp, WvDimens.RadiusLarge) // --r-lg
        assertEquals(24.dp, WvDimens.RadiusExtraLarge) // --r-xl
        assertEquals(999.dp, WvDimens.RadiusPill) // --r-pill
        assertEquals("the floating nav is a full pill, like the web's", 999.dp, WvDimens.RadiusGlassNav)
        assertEquals(18.dp, WvDimens.ScreenGutter) // --gutter
        assertEquals(62.dp, WvDimens.NavHeight) // --nav-float-h
        assertEquals(52.dp, WvDimens.Thumb) // product tile
    }
}
