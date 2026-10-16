package io.github.currencortex.music.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

/** An account-bound user preference, never a cached VIP entitlement. */
data class OriginalSmartPreferences(val enabled: Boolean = false, val ownerId: Long = 0)

class OriginalSmartSettingsRepository(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    private val enabled = OriginalEffectPreferenceKeys.smartEnabled
    private val owner = OriginalEffectPreferenceKeys.smartOwner
    private fun decode(p: Preferences): OriginalSmartPreferences {
        val uid = p[owner]?.takeIf { it > 0 } ?: return OriginalSmartPreferences()
        if (OriginalEffectPreferenceKeys.masterRequestedFor(p, uid)) return OriginalSmartPreferences()
        if (OriginalEffectPreferenceKeys.moodRequestedFor(p, uid)) return OriginalSmartPreferences()
        return if (p[enabled] == true) OriginalSmartPreferences(true, uid) else OriginalSmartPreferences()
    }
    val state: StateFlow<OriginalSmartPreferences> = store.data.map(::decode)
        .stateIn(scope, SharingStarted.Eagerly, OriginalSmartPreferences())
    suspend fun snapshot(): OriginalSmartPreferences = decode(store.data.first())
    suspend fun set(value: OriginalSmartPreferences) {
        require(!value.enabled || value.ownerId > 0) { "原版智能音效必须绑定真实网易云账号" }
        store.edit { p ->
            if (value.enabled) {
                OriginalEffectPreferenceKeys.clearMaster(p)
                OriginalEffectPreferenceKeys.clearSingle(p)
                OriginalEffectPreferenceKeys.clearMood(p)
                p[enabled] = true; p[owner] = value.ownerId
            } else OriginalEffectPreferenceKeys.clearSmart(p)
        }
    }
    suspend fun set(enabled: Boolean, ownerId: Long) = set(OriginalSmartPreferences(enabled, ownerId))
}
