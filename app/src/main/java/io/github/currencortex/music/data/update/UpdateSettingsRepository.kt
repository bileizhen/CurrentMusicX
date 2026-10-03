package io.github.currencortex.music.data.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.currencortex.music.core.update.UpdateChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first

class UpdateSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) {
    private val autoCheck = booleanPreferencesKey("update_auto_check")
    private val channel = stringPreferencesKey("update_channel")
    private val ignoredStable = stringPreferencesKey("update_ignored_stable")
    private val ignoredPrerelease = stringPreferencesKey("update_ignored_prerelease")

    private fun read(preferences: Preferences) = UpdateSettings(
            autoCheckOnLaunch = preferences[autoCheck] ?: true,
            channel = runCatching { UpdateChannel.valueOf(preferences[channel] ?: UpdateChannel.STABLE.name) }
                .getOrDefault(UpdateChannel.STABLE),
            ignoredVersions = mapOf(UpdateChannel.STABLE to preferences[ignoredStable].orEmpty(),
                UpdateChannel.PRERELEASE to preferences[ignoredPrerelease].orEmpty()),
        )

    val state: StateFlow<UpdateSettings> = dataStore.data.map(::read)
        .stateIn(scope, SharingStarted.Eagerly, UpdateSettings(autoCheckOnLaunch = false))

    suspend fun snapshot(): UpdateSettings = read(dataStore.data.first())

    suspend fun setAutoCheck(value: Boolean) {
        dataStore.edit { it[autoCheck] = value }
    }

    suspend fun setChannel(value: UpdateChannel) {
        dataStore.edit { it[channel] = value.name }
    }

    suspend fun ignoreRelease(channel: UpdateChannel, version: String) {
        dataStore.edit { it[if (channel == UpdateChannel.STABLE) ignoredStable else ignoredPrerelease] = version }
    }
}
