package io.github.currencortex.music.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) {
    val state: StateFlow<AppearanceSettings> = dataStore.data
        .map(SettingsPreferences::read)
        .stateIn(scope, SharingStarted.Eagerly, AppearanceSettings())

    suspend fun edit(change: (AppearanceSettings) -> AppearanceSettings) {
        dataStore.edit { preferences ->
            SettingsPreferences.write(preferences, change(SettingsPreferences.read(preferences)))
        }
    }
}
