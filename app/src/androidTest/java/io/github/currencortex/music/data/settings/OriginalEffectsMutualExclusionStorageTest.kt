package io.github.currencortex.music.data.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.RunWith
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@RunWith(AndroidJUnit4::class)
class OriginalEffectsMutualExclusionStorageTest {
    @get:Rule val temporary = TemporaryFolder(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)

    private suspend fun <T> open(file: File, block: suspend (DataStore<Preferences>, CoroutineScope) -> T): T {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return try { block(PreferenceDataStoreFactory.create(scope = scope) { file }, scope) }
        finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun selectingSmartAtomicallyRemovesSavedSingleAndReopensOnlySmart() = runBlocking {
        val file = File(temporary.root, "smart-wins.preferences_pb")
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

    @Test fun selectingSingleAtomicallyRemovesSmartAndReopensOnlySingle() = runBlocking {
        val file = File(temporary.root, "single-wins.preferences_pb")
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
        val file = File(temporary.root, "old-mixed.preferences_pb")
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
        val file = File(temporary.root, "clear-other.preferences_pb")
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
        val file = File(temporary.root, "malformed-smart.preferences_pb")
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

    @Test fun everyExplicitModeAtomicallyClearsBothAlternativesAndSurvivesReopen() = runBlocking {
        val choice = SavedOriginalSoundEffect(1008, "single", "netease:10")
        for (from in 0..2) for (to in 0..2) {
            val file = File(temporary.root, "three-modes-$from-$to.preferences_pb")
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

    @Test fun legacyThreeWaySaveRestoresMoodBeforeSmartBeforeSingleForSameOwnerWithoutMutatingFile() = runBlocking {
        val file = File(temporary.root, "old-three-modes.preferences_pb")
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
        val file = File(temporary.root, "old-different-owners.preferences_pb")
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
        val file = File(temporary.root, "mood-clears.preferences_pb")
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
