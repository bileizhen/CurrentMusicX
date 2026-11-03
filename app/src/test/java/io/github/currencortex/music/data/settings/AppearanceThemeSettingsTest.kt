package io.github.currencortex.music.data.settings

import androidx.datastore.preferences.core.*
import org.junit.Assert.*
import org.junit.Test

class AppearanceThemeSettingsTest {
    @Test fun existingPreferencesKeepTheirValuesAndNewControlsStartWithSafeDefaults() {
        val p = mutablePreferencesOf(stringPreferencesKey("theme_mode") to "DARK", booleanPreferencesKey("monet") to true,
            floatPreferencesKey("ui_scale") to 1.1f, booleanPreferencesKey("liquid_glass") to false)
        val s = SettingsPreferences.read(p)
        assertEquals(ThemeMode.DARK, s.themeMode); assertTrue(s.monet); assertEquals(1.1f, s.uiScale)
        assertFalse(s.liquidGlass); assertFalse(s.pureBlack); assertFalse(s.customTextScale)
        assertEquals(DefaultThemeColors, s.primaryColors)
        assertEquals(PlayerCoverStyle.CIRCLE, s.playerCoverStyle)
    }
    @Test fun themePaletteAndTextScaleRoundTripWithoutChangingPlaybackAppearance() {
        val p = mutablePreferencesOf()
        val settings = AppearanceSettings(themeMode = ThemeMode.DARK, uiScale = 1.15f, customFont = "existing.font",
            playerBackground = "art.jpg", playerDynamicBackground = false, launchStyle = LaunchStyle.HALO,
            playlistRecordShelf = true, refreshRate = RefreshRatePreference(144), pureBlack = true,
            primaryColor = 0xFF123456, primaryColors = DefaultThemeColors + 0xFF123456,
            colorStyle = ThemeColorStyle.TINTED, customTextScale = true, textScale = 1.25f,
            playerCoverStyle = PlayerCoverStyle.IMMERSIVE)
        SettingsPreferences.write(p, settings)
        assertEquals(settings, SettingsPreferences.read(p))
        SettingsPreferences.write(p, SettingsPreferences.read(p).copy(monet = true, pureBlack = false))
        assertEquals(settings.copy(monet = true, pureBlack = false), SettingsPreferences.read(p))
    }
    @Test fun legacyFadePreferenceIsIgnoredAndRemovedOnWrite() {
        val key = stringPreferencesKey("tab_animation")
        val p = mutablePreferencesOf(key to "FADE")
        assertEquals(AppearanceSettings(), SettingsPreferences.read(p))
        SettingsPreferences.write(p, SettingsPreferences.read(p))
        assertNull(p[key])
    }
    @Test fun invalidScaleAndPaletteCannotBreakThemeRendering() {
        val p = mutablePreferencesOf(floatPreferencesKey("text_scale") to Float.NaN,
            stringPreferencesKey("primary_colors") to "not-a-color", stringPreferencesKey("theme_color_style") to "invalid")
        assertEquals(1f, SettingsPreferences.read(p).textScale)
        assertEquals(DefaultThemeColors, SettingsPreferences.read(p).primaryColors)
        assertEquals(ThemeColorStyle.VIVID, SettingsPreferences.read(p).colorStyle)
        p[stringPreferencesKey("player_cover_style")] = "unknown-future-style"
        assertEquals(PlayerCoverStyle.CIRCLE, SettingsPreferences.read(p).playerCoverStyle)
        val normalized = AppearanceSettings(primaryColor = 0x123456, primaryColors = listOf(0x123456, 0xFF123456), textScale = 100f).normalized()
        assertEquals(0xFF123456, normalized.primaryColor); assertEquals(listOf(0xFF123456), normalized.primaryColors)
        assertEquals(1.4f, normalized.textScale)
    }
    @Test fun previouslyDockedInstallsMigrateToFloatingAndCannotPersistDockedAgain() {
        val p = mutablePreferencesOf(booleanPreferencesKey("floating_bar") to false)
        assertTrue(SettingsPreferences.read(p).floatingBar)
        SettingsPreferences.write(p, AppearanceSettings(floatingBar = false, themeMode = ThemeMode.DARK))
        assertTrue(p[booleanPreferencesKey("floating_bar")]!!)
        assertTrue(SettingsPreferences.read(p).floatingBar)
        assertEquals(ThemeMode.DARK, SettingsPreferences.read(p).themeMode)
    }
}
