package io.github.currencortex.music.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test

class OriginalEffectsMutualExclusionTest {
    private class MemoryPreferences(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)
        private val mutex = Mutex()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            transform(data.value).also { data.value = it }
        }
    }
    private val saved = mutableMapOf<String, Preferences>()

    // Storage/reopen equivalents run in OriginalEffectsMutualExclusionStorageTest on Android.
    private suspend fun <T> open(key: String, block: suspend (DataStore<Preferences>, CoroutineScope) -> T): T {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = MemoryPreferences(saved[key] ?: emptyPreferences())
        return try { block(store, scope) }
        finally { saved[key] = store.data.value; scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun selectingSmartAtomicallyRemovesSavedSingleAndRecreatesOnlySmart() = runBlocking {
        val file = "smart-wins.preferences_pb"
        open(file) { store, scope ->
            val single = OriginalSoundEffectsSettingsRepository(store, scope)
            val smart = OriginalSmartSettingsRepository(store, scope)
            single.set(SavedOriginalSoundEffect(1001, "3D环绕", "netease:10"))
            assertNotNull(single.snapshot())
            smart.set(true, 10)
            assertNull(single.snapshot())
            assertEquals(OriginalSmartPreferences(true, 10), smart.snapshot())
            assertEquals(setOf("original_smart.enabled", "original_smart.owner_id"), store.data.first().asMap().keys.map { it.name }.toSet())
        }
        open(file) { store, scope ->
            assertNull(OriginalSoundEffectsSettingsRepository(store, scope).snapshot())
            assertEquals(OriginalSmartPreferences(true, 10), OriginalSmartSettingsRepository(store, scope).snapshot())
        }
    }

    @Test fun selectingSingleAtomicallyRemovesSmartAndRecreatesOnlySingle() = runBlocking {
        val file = "single-wins.preferences_pb"
        val choice = SavedOriginalSoundEffect(1008, "LiveHouse", "netease:10")
        open(file) { store, scope ->
            val single = OriginalSoundEffectsSettingsRepository(store, scope)
            val smart = OriginalSmartSettingsRepository(store, scope)
            smart.set(true, 10)
            single.set(choice)
            assertEquals(choice, single.snapshot())
            assertEquals(OriginalSmartPreferences(), smart.snapshot())
            assertEquals(setOf("original_effects.id", "original_effects.name", "original_effects.owner"), store.data.first().asMap().keys.map { it.name }.toSet())
        }
        open(file) { store, scope ->
            assertEquals(choice, OriginalSoundEffectsSettingsRepository(store, scope).snapshot())
            assertEquals(OriginalSmartPreferences(), OriginalSmartSettingsRepository(store, scope).snapshot())
        }
    }

    @Test fun legacyMixedStateAlwaysRestoresSmartPriorityUntilUserSelectsSingle() = runBlocking {
        val file = "old-mixed.preferences_pb"
        open(file) { store, _ ->
            // Reproduce files from the older implementation, including both complete preferences.
            store.edit {
                it[longPreferencesKey("original_effects.id")] = 1001
                it[stringPreferencesKey("original_effects.name")] = "old single"
                it[stringPreferencesKey("original_effects.owner")] = "netease:10"
                it[booleanPreferencesKey("original_smart.enabled")] = true
                it[longPreferencesKey("original_smart.owner_id")] = 10
            }
        }
        val newChoice = SavedOriginalSoundEffect(1008, "new explicit single", "netease:10")
        open(file) { store, scope ->
            val single = OriginalSoundEffectsSettingsRepository(store, scope)
            val smart = OriginalSmartSettingsRepository(store, scope)
            assertNull(single.snapshot())
            assertEquals(OriginalSmartPreferences(true, 10), smart.snapshot())
            single.set(newChoice)
        }
        open(file) { store, scope ->
            assertEquals(newChoice, OriginalSoundEffectsSettingsRepository(store, scope).snapshot())
            assertEquals(OriginalSmartPreferences(), OriginalSmartSettingsRepository(store, scope).snapshot())
        }
    }

    @Test fun deactivatingOtherControllerDoesNotEraseActiveChoiceOrManualPreferences() = runBlocking {
        val file = "clear-other.preferences_pb"
        open(file) { store, scope ->
            val single = OriginalSoundEffectsSettingsRepository(store, scope)
            val smart = OriginalSmartSettingsRepository(store, scope)
            val manual = booleanPreferencesKey("manual.audio.enabled")
            store.edit { it[manual] = true }
            smart.set(true, 10)
            single.set(null)
            assertEquals(OriginalSmartPreferences(true, 10), smart.snapshot())
            val choice = SavedOriginalSoundEffect(1001, "single", "netease:10")
            single.set(choice)
            smart.set(false, 10)
            assertEquals(choice, single.snapshot())
            assertEquals(true, store.data.first()[manual])
        }
    }

    @Test fun malformedSmartOwnerDoesNotHideAnOtherwiseValidSingle() = runBlocking {
        val file = "malformed-smart.preferences_pb"
        open(file) { store, scope ->
            val single = OriginalSoundEffectsSettingsRepository(store, scope)
            val smart = OriginalSmartSettingsRepository(store, scope)
            val choice = SavedOriginalSoundEffect(1001, "single", "anonymous")
            single.set(choice)
            store.edit { it[booleanPreferencesKey("original_smart.enabled")] = true }
            assertEquals(choice, single.snapshot())
            assertEquals(OriginalSmartPreferences(), smart.snapshot())
            store.edit { it[longPreferencesKey("original_smart.owner_id")] = -1 }
            assertEquals(choice, single.snapshot())
            assertEquals(OriginalSmartPreferences(), smart.snapshot())
        }
    }

    @Test fun everyExplicitModeAtomicallyClearsBothAlternativesAcrossRepositoryRecreation() = runBlocking {
        val choice = SavedOriginalSoundEffect(1008, "single", "netease:10")
        for (from in 0..2) for (to in 0..2) {
            val file = "three-modes-$from-$to.preferences_pb"
            open(file) { store, scope ->
                val single = OriginalSoundEffectsSettingsRepository(store, scope)
                val smart = OriginalSmartSettingsRepository(store, scope)
                val mood = OriginalMoodSettingsRepository(store, scope)
                suspend fun choose(mode: Int) = when (mode) {
                    0 -> single.set(choice)
                    1 -> smart.set(true, 10)
                    else -> mood.set(true, 10)
                }
                choose(from)
                choose(to)
                val expectedKeys = when (to) {
                    0 -> setOf("original_effects.id", "original_effects.name", "original_effects.owner")
                    1 -> setOf("original_smart.enabled", "original_smart.owner_id")
                    else -> setOf("original_mood.enabled", "original_mood.owner_id")
                }
                assertEquals(expectedKeys, store.data.first().asMap().keys.map { it.name }.toSet())
            }
            open(file) { store, scope ->
                assertEquals(if (to == 0) choice else null, OriginalSoundEffectsSettingsRepository(store, scope).snapshot())
                assertEquals(OriginalSmartPreferences(to == 1, if (to == 1) 10 else 0), OriginalSmartSettingsRepository(store, scope).snapshot())
                assertEquals(OriginalSmartPreferences(to == 2, if (to == 2) 10 else 0), OriginalMoodSettingsRepository(store, scope).snapshot())
            }
        }
    }

    @Test fun legacyThreeWaySaveRestoresMoodBeforeSmartBeforeSingleForSameOwnerWithoutMutatingPreferences() = runBlocking {
        val file = "old-three-modes.preferences_pb"
        open(file) { store, _ -> seedMixedSave(store, singleOwner = "netease:10", smartOwner = 10, moodOwner = 10) }
        open(file) { store, scope ->
            val single = OriginalSoundEffectsSettingsRepository(store, scope)
            val smart = OriginalSmartSettingsRepository(store, scope)
            val mood = OriginalMoodSettingsRepository(store, scope)
            val original = store.data.first()
            assertNull(single.snapshot())
            assertEquals(OriginalSmartPreferences(), smart.snapshot())
            assertEquals(OriginalSmartPreferences(true, 10), mood.snapshot())
            assertEquals(original, store.data.first())
            mood.set(false, 10)
            assertNull(single.snapshot())
            assertEquals(OriginalSmartPreferences(true, 10), smart.snapshot())
            smart.set(false, 10)
            assertEquals(SavedOriginalSoundEffect(1001, "legacy", "netease:10"), single.snapshot())
        }
    }

    @Test fun oldDifferentOwnerChoicesRemainVisibleForControllersToValidate() = runBlocking {
        val file = "old-different-owners.preferences_pb"
        open(file) { store, _ -> seedMixedSave(store, singleOwner = "netease:10", smartOwner = 20, moodOwner = 30) }
        open(file) { store, scope ->
            val before = store.data.first()
            assertEquals(SavedOriginalSoundEffect(1001, "legacy", "netease:10"), OriginalSoundEffectsSettingsRepository(store, scope).snapshot())
            assertEquals(OriginalSmartPreferences(true, 20), OriginalSmartSettingsRepository(store, scope).snapshot())
            assertEquals(OriginalSmartPreferences(true, 30), OriginalMoodSettingsRepository(store, scope).snapshot())
            assertEquals(before, store.data.first())
        }
    }

    @Test fun moodRejectsMalformedOwnerAndClearingOtherModesKeepsMoodAndManualSettings() = runBlocking {
        val file = "mood-clears.preferences_pb"
        open(file) { store, scope ->
            val mood = OriginalMoodSettingsRepository(store, scope)
            val manual = booleanPreferencesKey("manual.audio.enabled")
            store.edit { it[manual] = true; it[OriginalEffectPreferenceKeys.moodEnabled] = true }
            assertEquals(OriginalSmartPreferences(), mood.snapshot())
            assertTrue(runCatching { mood.set(true, 0) }.isFailure)
            mood.set(true, 10)
            OriginalSoundEffectsSettingsRepository(store, scope).set(null)
            OriginalSmartSettingsRepository(store, scope).set(false, 10)
            assertEquals(OriginalSmartPreferences(true, 10), mood.snapshot())
            assertEquals(true, store.data.first()[manual])
            mood.set(false, 10)
            assertEquals(setOf("manual.audio.enabled"), store.data.first().asMap().keys.map { it.name }.toSet())
        }
    }

    @Test fun masterAndExistingModesRemainMutuallyExclusiveAcrossRecreation() = runBlocking {
        for (mode in 0..2) {
            val key = "master-mutual-$mode.preferences_pb"
            open(key) { store, scope ->
                val master = OriginalAiMasterSettingsRepository(store, scope)
                val smart = OriginalSmartSettingsRepository(store, scope)
                val mood = OriginalMoodSettingsRepository(store, scope)
                val single = OriginalSoundEffectsSettingsRepository(store, scope)
                master.set(OriginalSmartPreferences(true, 10))
                when (mode) {
                    0 -> smart.set(true, 10)
                    1 -> mood.set(true, 10)
                    else -> single.set(SavedOriginalSoundEffect(1001, "single", "netease:10"))
                }
                assertFalse(master.snapshot().enabled)
                master.set(OriginalSmartPreferences(true, 10))
                assertFalse(smart.snapshot().enabled)
                assertFalse(mood.snapshot().enabled)
                assertNull(single.snapshot())
            }
            open(key) { store, scope ->
                assertEquals(OriginalSmartPreferences(true, 10), OriginalAiMasterSettingsRepository(store, scope).snapshot())
                assertNull(OriginalSoundEffectsSettingsRepository(store, scope).snapshot())
            }
        }
    }

    private suspend fun seedMixedSave(store: DataStore<Preferences>, singleOwner: String, smartOwner: Long, moodOwner: Long) {
        store.edit {
            it[OriginalEffectPreferenceKeys.singleId] = 1001
            it[OriginalEffectPreferenceKeys.singleName] = "legacy"
            it[OriginalEffectPreferenceKeys.singleOwner] = singleOwner
            it[OriginalEffectPreferenceKeys.smartEnabled] = true
            it[OriginalEffectPreferenceKeys.smartOwner] = smartOwner
            it[OriginalEffectPreferenceKeys.moodEnabled] = true
            it[OriginalEffectPreferenceKeys.moodOwner] = moodOwner
        }
    }
}
