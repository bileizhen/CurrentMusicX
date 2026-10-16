package io.github.currencortex.music.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

internal val ORIGINAL_AI_MASTER_DIY_KEYS = setOf("en_bass", "en_treble", "en_vocal", "en_vibe", "en_live")

/** User intent only. SVIP authorization is always obtained separately from the current session. */
class OriginalAiMasterSettingsRepository(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    private val enabled = booleanPreferencesKey("original_ai_master.enabled")
    private val owner = longPreferencesKey("original_ai_master.owner_id")
    private fun decode(p: Preferences): OriginalSmartPreferences =
        if (p[enabled] == true && (p[owner] ?: 0) > 0) OriginalSmartPreferences(true, p[owner]!!)
        else OriginalSmartPreferences()
    val state: StateFlow<OriginalSmartPreferences> = store.data.map(::decode)
        .stateIn(scope, SharingStarted.Eagerly, OriginalSmartPreferences())
    suspend fun snapshot() = decode(store.data.first())
    /** Preferences are isolated by actual UID, including when the effect is switched off. */
    suspend fun diyFor(ownerId: Long): Set<String> {
        if (ownerId <= 0) return emptySet()
        return store.data.first()[diyKey(ownerId)].orEmpty().filterTo(linkedSetOf()) {
            it in ORIGINAL_AI_MASTER_DIY_KEYS
        }
    }
    suspend fun setDiy(ownerId: Long, selected: Set<String>) {
        require(ownerId > 0 && ORIGINAL_AI_MASTER_DIY_KEYS.containsAll(selected))
        store.edit { p -> p[diyKey(ownerId)] = selected.toSet() }
    }
    private fun diyKey(ownerId: Long) = stringSetPreferencesKey("original_ai_master.diy.$ownerId")
    suspend fun set(value: OriginalSmartPreferences) {
        require(!value.enabled || value.ownerId > 0)
        store.edit { p ->
            if (value.enabled) {
                OriginalEffectPreferenceKeys.clearSingle(p); OriginalEffectPreferenceKeys.clearSmart(p)
                OriginalEffectPreferenceKeys.clearMood(p)
                p[enabled] = true; p[owner] = value.ownerId
            } else { p.remove(enabled); p.remove(owner) }
        }
    }
}
