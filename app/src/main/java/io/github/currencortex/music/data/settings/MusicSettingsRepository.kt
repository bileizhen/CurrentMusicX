package io.github.currencortex.music.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import io.github.currencortex.music.core.config.ServerDefaults
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.core.network.ServerUrl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class MusicSettings(val server: String = ServerDefaults.URL, val quality: AudioQuality = AudioQuality.AUTO,
                         val warnHighSpec: Boolean = true, val restoreQueue: Boolean = true,
                         val nickname: String = "", val accountId: Long = 0)
class MusicSettingsRepository(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    private val server = stringPreferencesKey("music.server")
    private val quality = stringPreferencesKey("music.quality")
    private val warning = booleanPreferencesKey("music.warn")
    private val restore = booleanPreferencesKey("music.restore")
    private val nickname = stringPreferencesKey("account.nickname")
    private val account = longPreferencesKey("account.id")
    val state = store.data.map { p -> MusicSettings(p[server] ?: ServerDefaults.URL, AudioQuality.from(p[quality].orEmpty()),
        p[warning] ?: true, p[restore] ?: true, p[nickname].orEmpty(), p[account] ?: 0) }
        .stateIn(scope, SharingStarted.Eagerly, MusicSettings())
    suspend fun snapshot(): MusicSettings {
        val p = store.data.first()
        return MusicSettings(p[server] ?: ServerDefaults.URL, AudioQuality.from(p[quality].orEmpty()), p[warning] ?: true,
            p[restore] ?: true, p[nickname].orEmpty(), p[account] ?: 0)
    }
    suspend fun setServer(value: String) { val normalized = ServerUrl.normalize(value); store.edit { it[server] = normalized } }
    suspend fun setQuality(value: AudioQuality) { store.edit { it[quality] = value.value } }
    suspend fun setWarning(value: Boolean) { store.edit { it[warning] = value } }
    suspend fun setRestore(value: Boolean) { store.edit { it[restore] = value } }
    suspend fun setAccount(id: Long, name: String) { store.edit { it[account] = id; it[nickname] = name } }
}
