package io.github.currencortex.music.data.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.floatPreferencesKey
import org.junit.Assert.*
import org.junit.Test

class PlayerBackgroundSettingsTest {
    @Test fun imageAndBlurPersistAlongsideExistingAppearanceAndResetIndependently() {
        val preferences = mutablePreferencesOf()
        val original = AppearanceSettings(themeMode = ThemeMode.DARK, uiScale = .9f,
            playerBackground = "example.image", playerBackgroundBlur = 64f)
        SettingsPreferences.write(preferences, original)
        assertEquals(original, SettingsPreferences.read(preferences))
        SettingsPreferences.write(preferences, original.copy(playerBackground = ""))
        val reset = SettingsPreferences.read(preferences)
        assertEquals("", reset.playerBackground)
        assertEquals(64f, reset.playerBackgroundBlur)
        assertEquals(ThemeMode.DARK, reset.themeMode)
        assertEquals(.9f, reset.uiScale)
    }
    @Test fun oldAndInvalidPreferencesHaveSafeDefaultsAndBoundedBlur() {
        val p = mutablePreferencesOf()
        assertEquals("", SettingsPreferences.read(p).playerBackground)
        assertEquals(35f, SettingsPreferences.read(p).playerBackgroundBlur)
        p[floatPreferencesKey("player_background_blur")] = Float.NaN
        assertEquals(35f, SettingsPreferences.read(p).playerBackgroundBlur)
        assertEquals(0f, AppearanceSettings(playerBackgroundBlur = -1f).normalized().playerBackgroundBlur)
        assertEquals(100f, AppearanceSettings(playerBackgroundBlur = 101f).normalized().playerBackgroundBlur)
    }
}
