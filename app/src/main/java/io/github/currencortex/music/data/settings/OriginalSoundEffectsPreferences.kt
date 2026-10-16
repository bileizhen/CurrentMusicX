package io.github.currencortex.music.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

/** Identity and label only. Presets, CDN addresses and credentials are never persisted here. */
data class SavedOriginalSoundEffect(val effectId: Long, val name: String, val ownerKey: String)

class OriginalSoundEffectsSettingsRepository(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    private val id = OriginalEffectPreferenceKeys.singleId
    private val name = OriginalEffectPreferenceKeys.singleName
    private val owner = OriginalEffectPreferenceKeys.singleOwner
    private fun decode(p: Preferences): SavedOriginalSoundEffect? {
        val effectId = p[id]?.takeIf { it > 0 } ?: return null
        val ownerKey = p[owner]?.takeIf { it == "anonymous" || it.matches(Regex("netease:[1-9][0-9]*")) } ?: return null
        // Resolve old mixed saves only within an account. Controllers validate other owners.
        if (OriginalEffectPreferenceKeys.higherPriorityThanSingle(p, ownerKey)) return null
        return SavedOriginalSoundEffect(effectId, p[name].orEmpty().take(128), ownerKey)
    }
    val state: StateFlow<SavedOriginalSoundEffect?> = store.data.map(::decode)
        .stateIn(scope, SharingStarted.Eagerly, null)
    suspend fun snapshot() = decode(store.data.first())
    suspend fun set(value: SavedOriginalSoundEffect?) {
        require(value == null || value.effectId > 0 && (value.ownerKey == "anonymous" ||
            value.ownerKey.matches(Regex("netease:[1-9][0-9]*"))))
        store.edit { p ->
            if (value == null) OriginalEffectPreferenceKeys.clearSingle(p)
            else {
                OriginalEffectPreferenceKeys.clearMaster(p)
                OriginalEffectPreferenceKeys.clearSmart(p)
                OriginalEffectPreferenceKeys.clearMood(p)
                p[id] = value.effectId; p[name] = value.name.take(128); p[owner] = value.ownerKey
            }
        }
    }
}
