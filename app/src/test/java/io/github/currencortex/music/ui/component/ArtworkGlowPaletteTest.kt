package io.github.currencortex.music.ui.component

import io.github.currencortex.music.core.media.smoothDockEnergy
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.SettingsPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.*
import org.junit.Test

class ArtworkGlowPaletteTest {
    @Test fun meaningfulArtworkColourBecomesTheLightSourceInsteadOfGreyBackground() {
        val pixels = IntArray(1000) { if (it < 850) 0xff999999.toInt() else 0xffea762b.toInt() }
        val palette = ArtworkGlowPalette.fromPixels(pixels)
        assertTrue((palette.primary ushr 16 and 255) > (palette.primary and 255) + 100)
        assertEquals(palette.primary, palette.secondary)
    }

    @Test fun faintArtworkTintDoesNotWashOutHalfOfAStronglyColouredCover() {
        val pixels = IntArray(1000) { when { it < 500 -> 0xff207bd8.toInt(); it < 850 -> 0xff999999.toInt(); else -> 0xffd9d7bb.toInt() } }
        val palette = ArtworkGlowPalette.fromPixels(pixels)
        assertEquals(palette.primary, palette.secondary)
    }

    @Test fun monochromeCoverKeepsColourOnBothSidesInsteadOfChoosingGreyOrNoise() {
        val green = 0xff88c900.toInt()
        val pixels = IntArray(1000) { when { it < 600 -> green; it < 999 -> 0xff999999.toInt(); else -> 0xffeb2088.toInt() } }
        val palette = ArtworkGlowPalette.fromPixels(pixels)
        assertEquals(palette.primary, palette.secondary)
        assertTrue((palette.secondary ushr 8 and 255) > (palette.secondary and 255) + 100)
    }

    @Test fun genuineSecondaryColourSurvivesNeutralCoverDetails() {
        val pixels = IntArray(1000) { when { it < 600 -> 0xff88c900.toInt(); it < 900 -> 0xff999999.toInt(); else -> 0xff207bd8.toInt() } }
        val palette = ArtworkGlowPalette.fromPixels(pixels)
        assertTrue((palette.primary ushr 8 and 255) > (palette.primary and 255) + 100)
        assertTrue((palette.secondary and 255) > (palette.secondary ushr 16 and 255) + 100)
    }

    @Test fun coverColoursBeatBlackBordersAndWhiteText() {
        val blue = 0xff207bd8.toInt(); val orange = 0xffea762b.toInt()
        val pixels = IntArray(1000) { when { it < 450 -> 0xff000000.toInt(); it < 700 -> -1; it < 900 -> blue; else -> orange } }
        val palette = ArtworkGlowPalette.fromPixels(pixels)
        assertTrue((palette.primary and 255) > (palette.primary ushr 16 and 255) + 100)
        assertTrue((palette.secondary ushr 16 and 255) > (palette.secondary and 255) + 100)
        assertEquals(ArtworkGlowPalette.Neutral, ArtworkGlowPalette.fromPixels(intArrayOf(0, -1, 0xff000000.toInt())))
    }
    @Test fun energyHasQuickAttackGentleReleaseAndFrameRateIndependentMotion() {
        val attack = smoothDockEnergy(0f, 1f, .05f)
        val release = smoothDockEnergy(1f, 0f, .05f)
        assertTrue(attack > 1f - release)
        var sixty = 0f; repeat(60) { sixty = smoothDockEnergy(sixty, .8f, 1f/60) }
        var high = 0f; repeat(120) { high = smoothDockEnergy(high, .8f, 1f/120) }
        assertEquals(sixty, high, .001f)
    }
    @Test fun glowChoicePersistsWithoutChangingOtherAppearanceChoices() {
        val p = mutablePreferencesOf()
        SettingsPreferences.write(p, AppearanceSettings(navigationGlow = false, blur = false, uiScale = 1.1f))
        assertFalse(SettingsPreferences.read(p).navigationGlow)
        assertFalse(SettingsPreferences.read(p).blur)
        assertEquals(1.1f, SettingsPreferences.read(p).uiScale, 0f)
        SettingsPreferences.write(p, SettingsPreferences.read(p).copy(navigationGlow = true))
        assertTrue(SettingsPreferences.read(p).navigationGlow)
        assertFalse(SettingsPreferences.read(p).blur)
    }
}
