package io.github.currencortex.music.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.core.DataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OriginalSoundEffectsSettingsTest {
    @get:Rule val temporary = TemporaryFolder()
    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        private val mutex = Mutex()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            transform(data.value).also { data.value = it }
        }
    }

    @Test fun choiceSurvivesReopeningWithoutPersistingResourcesOrCredentials() = runBlocking {
        val file = File(temporary.root, "settings.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var store = PreferenceDataStoreFactory.create(scope = scope) { file }
        var repository = OriginalSoundEffectsSettingsRepository(store, scope)
        assertNull(repository.snapshot())
        val saved = SavedOriginalSoundEffect(1001, "3D环绕", "netease:10")
        repository.set(saved)
        val raw = store.data.first().asMap()
        assertEquals(setOf("original_effects.id", "original_effects.name", "original_effects.owner"), raw.keys.map { it.name }.toSet())
        scope.coroutineContext[Job]!!.cancelAndJoin()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            store = PreferenceDataStoreFactory.create(scope = scope) { file }
            repository = OriginalSoundEffectsSettingsRepository(store, scope)
            assertEquals(saved, repository.snapshot())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun clearingOriginalChoicePreservesManualEqAndSmartPreferences() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = MemoryPreferences()
            val manual = EqualizerSettingsRepository(store, scope)
            val smart = SoundEffectsSettingsRepository(store, scope)
            val original = OriginalSoundEffectsSettingsRepository(store, scope)
            manual.select(EqualizerPreset.ROCK)
            manual.setEnabled(true)
            smart.setSmartEnabled(true)
            val before = manual.snapshot()
            original.set(SavedOriginalSoundEffect(1001, "3D环绕", "anonymous"))
            original.set(null)
            assertNull(original.snapshot())
            assertEquals(before, manual.snapshot())
            assertTrue(smart.snapshot().smartEnabled)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun malformedOrMissingOwnerDoesNotRestoreAnUnscopedPreset() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = MemoryPreferences()
            val repository = OriginalSoundEffectsSettingsRepository(store, scope)
            store.edit {
                it[longPreferencesKey("original_effects.id")] = 1001
                it[stringPreferencesKey("original_effects.name")] = "3D环绕"
            }
            assertNull(repository.snapshot())
            store.edit { it[stringPreferencesKey("original_effects.owner")] = "netease:0" }
            assertNull(repository.snapshot())
            assertTrue(runCatching { repository.set(SavedOriginalSoundEffect(1001, "bad", "cookie=value")) }.isFailure)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }
}
