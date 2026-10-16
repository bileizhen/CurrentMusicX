package io.github.currencortex.music.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SoundEffectsPreferences(val smartEnabled: Boolean = false)

/** Smart tuning is independent of the user's manually saved equalizer curve. */
class SoundEffectsSettingsRepository(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    private val smartEnabled = booleanPreferencesKey("sound_effects.smart_enabled")
    private fun decode(p: Preferences) = SoundEffectsPreferences(p[smartEnabled] ?: false)
    val state = store.data.map(::decode).stateIn(scope, SharingStarted.Eagerly, SoundEffectsPreferences())
    suspend fun snapshot() = decode(store.data.first())
    suspend fun setSmartEnabled(value: Boolean) { store.edit { it[smartEnabled] = value } }
}
