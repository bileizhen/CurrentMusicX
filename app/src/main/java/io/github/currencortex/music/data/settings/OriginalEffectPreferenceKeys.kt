package io.github.currencortex.music.data.settings

import androidx.datastore.preferences.core.*

/** All original effect modes share one atomic saved choice. */
internal object OriginalEffectPreferenceKeys {
    val singleId = longPreferencesKey("original_effects.id")
    val singleName = stringPreferencesKey("original_effects.name")
    val singleOwner = stringPreferencesKey("original_effects.owner")
    val smartEnabled = booleanPreferencesKey("original_smart.enabled")
    val smartOwner = longPreferencesKey("original_smart.owner_id")
    val moodEnabled = booleanPreferencesKey("original_mood.enabled")
    val masterEnabled = booleanPreferencesKey("original_ai_master.enabled")
    val masterOwner = longPreferencesKey("original_ai_master.owner_id")
    fun masterRequestedFor(p: Preferences, uid: Long) = p[masterEnabled] == true && uid > 0 && p[masterOwner] == uid
    fun clearMaster(p: MutablePreferences) { p.remove(masterEnabled); p.remove(masterOwner) }
    val moodOwner = longPreferencesKey("original_mood.owner_id")

    fun smartRequested(p: Preferences): Boolean = p[smartEnabled] == true && (p[smartOwner] ?: 0) > 0
    fun moodRequested(p: Preferences): Boolean = p[moodEnabled] == true && (p[moodOwner] ?: 0) > 0
    fun moodRequestedFor(p: Preferences, ownerId: Long): Boolean = moodRequested(p) && p[moodOwner] == ownerId
    fun higherPriorityThanSingle(p: Preferences, ownerKey: String): Boolean =
        (p[masterEnabled] == true && (p[masterOwner] ?: 0) > 0 && ownerKey == "netease:${p[masterOwner]}") ||
            (moodRequested(p) && ownerKey == "netease:${p[moodOwner]}") ||
            (smartRequested(p) && ownerKey == "netease:${p[smartOwner]}")
    fun clearSingle(p: MutablePreferences) { p.remove(singleId); p.remove(singleName); p.remove(singleOwner) }
    fun clearSmart(p: MutablePreferences) { p.remove(smartEnabled); p.remove(smartOwner) }
    fun clearMood(p: MutablePreferences) { p.remove(moodEnabled); p.remove(moodOwner) }
}
