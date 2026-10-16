package io.github.currencortex.music.ui.component

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.currencortex.music.data.settings.*
import org.junit.Assert.*
import org.junit.Test

class RefreshRatePolicyTest {
    @Test fun fixedModePreservesResolutionAndUsesNearestSupportedRate() {
        val current = RefreshMode(1, 1080, 2400, 60f)
        val supported = listOf(current, RefreshMode(2, 1080, 2400, 120f), RefreshMode(3, 1440, 3200, 90f))
        assertEquals(2, preferredRefreshMode(current, supported, 120))
        assertEquals(1, preferredRefreshMode(current, supported, 90))
        assertEquals(0, preferredRefreshMode(current, supported, 0))
        assertEquals(0, preferredRefreshMode(current, listOf(supported[2]), 120))
    }

    @Test fun persistedSelectionPreservesOtherAppearanceSettingsAndRejectsUnknownValues() {
        val prefs = mutablePreferencesOf()
        val original = AppearanceSettings(themeMode = ThemeMode.DARK, uiScale = 1.1f, playlistDepthRibbon = true)
        RefreshRatePreference.entries.forEach { rate ->
            val selected = original.copy(refreshRate = rate)
            SettingsPreferences.write(prefs, selected)
            assertEquals(selected, SettingsPreferences.read(prefs))
        }
        prefs[stringPreferencesKey("refresh_rate")] = "unsupported_future_value"
        assertEquals(original, SettingsPreferences.read(prefs))
        assertEquals(RefreshRatePreference.SYSTEM, SettingsPreferences.read(mutablePreferencesOf()).refreshRate)
    }
}
