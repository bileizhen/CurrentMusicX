package io.github.currencortex.music.data.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.*
import org.junit.Test

class LaunchStyleSettingsTest {
    @Test fun existingInstallsAndUnknownValuesKeepClassic() {
        val preferences = mutablePreferencesOf()
        assertEquals(LaunchStyle.CLASSIC, SettingsPreferences.read(preferences).launchStyle)
        preferences[stringPreferencesKey("launch_style")] = "future-style"
        assertEquals(LaunchStyle.CLASSIC, SettingsPreferences.read(preferences).launchStyle)
    }

    @Test fun selectionPersistsWithoutResettingOtherAppearancePreferences() {
        val preferences = mutablePreferencesOf()
        val before = AppearanceSettings(themeMode = ThemeMode.DARK, blur = false, uiScale = .9f,
            playerBackground = "content://background", playerBackgroundBlur = 66f)
        SettingsPreferences.write(preferences, before.copy(launchStyle = LaunchStyle.HALO))
        assertEquals(before.copy(launchStyle = LaunchStyle.HALO), SettingsPreferences.read(preferences))
        SettingsPreferences.write(preferences, before.copy(launchStyle = LaunchStyle.NONE))
        assertEquals(before.copy(launchStyle = LaunchStyle.NONE), SettingsPreferences.read(preferences))
        SettingsPreferences.write(preferences, SettingsPreferences.read(preferences).copy(launchStyle = LaunchStyle.CLASSIC))
        assertEquals(before, SettingsPreferences.read(preferences))
    }
}
