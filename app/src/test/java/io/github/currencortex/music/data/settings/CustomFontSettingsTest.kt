package io.github.currencortex.music.data.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomFontSettingsTest {
    @Test fun customFontSurvivesOtherAppearanceChangesAndCanBeReset() {
        val preferences = mutablePreferencesOf()
        val name = "e4122c65-4a66-43dd-8c74-110c7f8e9c11.font"
        SettingsPreferences.write(preferences, AppearanceSettings(customFont = name, blur = false))
        val restored = SettingsPreferences.read(preferences)
        assertEquals(name, restored.customFont)
        SettingsPreferences.write(preferences, restored.copy(uiScale = 1.1f))
        assertEquals(name, SettingsPreferences.read(preferences).customFont)
        SettingsPreferences.write(preferences, SettingsPreferences.read(preferences).copy(customFont = ""))
        assertEquals("", SettingsPreferences.read(preferences).customFont)
        assertEquals(false, SettingsPreferences.read(preferences).blur)
    }
}
