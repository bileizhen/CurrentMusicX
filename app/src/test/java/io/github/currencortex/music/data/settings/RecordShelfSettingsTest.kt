package io.github.currencortex.music.data.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.*
import org.junit.Test

class RecordShelfSettingsTest {
    @Test fun existingInstallKeepsItsModeAndTheShelfPersistsWithoutChangingOtherPreferences() {
        val preferences = mutablePreferencesOf()
        assertFalse(SettingsPreferences.read(preferences).playlistRecordShelf)
        val original = AppearanceSettings(themeMode = ThemeMode.DARK, playlistDepthRibbon = true, uiScale = 1.1f)
        SettingsPreferences.write(preferences, original)
        assertEquals(original, SettingsPreferences.read(preferences))
        val shelf = original.copy(playlistRecordShelf = true, playlistDepthRibbon = false)
        SettingsPreferences.write(preferences, shelf)
        assertEquals(shelf, SettingsPreferences.read(preferences))
    }

    @Test fun previewModesPersistAndUnknownValuesFallBackToOnce() {
        val preferences = mutablePreferencesOf()
        assertEquals(RecordShelfPreviewMode.ONCE, SettingsPreferences.read(preferences).recordShelfPreviewMode)
        assertFalse(SettingsPreferences.read(preferences).recordShelfFullPreview)
        for (mode in RecordShelfPreviewMode.entries) {
            val value = AppearanceSettings(playlistRecordShelf = true, recordShelfPreviewMode = mode, recordShelfFullPreview = true)
            SettingsPreferences.write(preferences, value)
            assertEquals(value, SettingsPreferences.read(preferences))
        }
        preferences[stringPreferencesKey("record_shelf_preview_mode")] = "unknown"
        assertEquals(RecordShelfPreviewMode.ONCE, SettingsPreferences.read(preferences).recordShelfPreviewMode)
    }
}
