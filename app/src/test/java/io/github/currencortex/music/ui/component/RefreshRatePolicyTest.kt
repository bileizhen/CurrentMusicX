package io.github.currencortex.music.ui.component

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.feature.settings.refreshRateSliderOptions
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
        listOf(0, 60, 75, 90, 120, 144, 165, 240).map(::RefreshRatePreference).forEach { rate ->
            val selected = original.copy(refreshRate = rate)
            SettingsPreferences.write(prefs, selected)
            assertEquals(selected, SettingsPreferences.read(prefs))
        }
        prefs[stringPreferencesKey("refresh_rate")] = "unsupported_future_value"
        assertEquals(original, SettingsPreferences.read(prefs))
        assertEquals(RefreshRatePreference.SYSTEM, SettingsPreferences.read(mutablePreferencesOf()).refreshRate)
    }
    @Test fun sliderIncludesAllReportedRatesInOrderAndNoInventedIntermediateModes() {
        assertEquals(listOf(0, 60, 90, 120, 144, 165),
            refreshRateSliderOptions(listOf(165, 60, 144, 90, 120, 60, 0, -1)).map { it.hz })
        assertEquals(listOf(0, 60, 75), refreshRateSliderOptions(listOf(75, 60)).map { it.hz })
        val current = RefreshMode(1, 1080, 2400, 60f)
        val modes = listOf(current, RefreshMode(2, 1080, 2400, 144f), RefreshMode(3, 1080, 2400, 165f))
        assertEquals(2, preferredRefreshMode(current, modes, 144))
        assertEquals(3, preferredRefreshMode(current, modes, 165))
        assertEquals(RefreshRatePreference.HZ120, RefreshRatePreference.valueOf("HZ120"))
        for (value in listOf("HZ0", "HZ-1", "HZ1001", "HZNaN")) {
            val prefs = mutablePreferencesOf(stringPreferencesKey("refresh_rate") to value)
            assertEquals(RefreshRatePreference.SYSTEM, SettingsPreferences.read(prefs).refreshRate)
        }
    }
}
