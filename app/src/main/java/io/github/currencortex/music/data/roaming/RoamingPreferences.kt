package io.github.currencortex.music.data.roaming

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.first

data class RoamingSettings(val selection: String = "DEFAULT", val preload: Boolean = true, val restoreQueue: Boolean = true)
class RoamingPreferences(private val store: DataStore<Preferences>) {
    private fun key(uid: Long, field: String) = "roaming.$uid.$field"
    suspend fun load(uid: Long): RoamingSettings {
        val p = store.data.first()
        return RoamingSettings(p[stringPreferencesKey(key(uid, "choice"))] ?: "DEFAULT",
            p[booleanPreferencesKey(key(uid, "preload"))] ?: true,
            p[booleanPreferencesKey(key(uid, "restore"))] ?: true)
    }
    suspend fun save(uid: Long, settings: RoamingSettings) {
        if (uid <= 0) return
        store.edit { p ->
            p[stringPreferencesKey(key(uid, "choice"))] = settings.selection
            p[booleanPreferencesKey(key(uid, "preload"))] = settings.preload
            p[booleanPreferencesKey(key(uid, "restore"))] = settings.restoreQueue
        }
    }
}
