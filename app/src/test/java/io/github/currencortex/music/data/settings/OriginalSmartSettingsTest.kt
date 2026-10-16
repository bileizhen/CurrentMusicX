package io.github.currencortex.music.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OriginalSmartSettingsTest {
    @get:Rule val temporary = TemporaryFolder()
    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        private val mutex = Mutex()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            transform(data.value).also { data.value = it }
        }
    }

    @Test fun defaultOffAndMalformedOwnerCannotCreateEnabledIntent() = runTest {
        val store = MemoryPreferences()
        val settings = OriginalSmartSettingsRepository(store, backgroundScope)
        assertEquals(OriginalSmartPreferences(), settings.snapshot())
        store.edit { it[booleanPreferencesKey("original_smart.enabled")] = true }
        assertFalse(settings.snapshot().enabled)
        store.edit { it[longPreferencesKey("original_smart.owner_id")] = -10 }
        assertFalse(settings.snapshot().enabled)
        assertTrue(runCatching { settings.set(true, 0) }.isFailure)
    }

    @Test fun disableClearsOwnerAndPreservesOtherAudioSettings() = runTest {
        val store = MemoryPreferences()
        val settings = OriginalSmartSettingsRepository(store, backgroundScope)
        val manual = booleanPreferencesKey("manual.audio.enabled")
        store.edit { it[manual] = true }
        settings.set(true, 10)
        assertEquals(OriginalSmartPreferences(true, 10), settings.snapshot())
        assertEquals(setOf("original_smart.enabled", "original_smart.owner_id", "manual.audio.enabled"),
            store.data.first().asMap().keys.map { it.name }.toSet())
        settings.set(false, 10)
        assertEquals(OriginalSmartPreferences(), settings.snapshot())
        assertEquals(mapOf<Preferences.Key<*>, Any>(kotlin.Pair(manual, true)), store.data.first().asMap())
    }

    @Test fun persistedIntentSurvivesActualDatastoreReopenWithoutStoringEntitlement() = runBlocking {
        val file = File(temporary.root, "original-smart.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var store = PreferenceDataStoreFactory.create(scope = scope) { file }
        OriginalSmartSettingsRepository(store, scope).set(true, 12345)
        scope.coroutineContext[Job]!!.cancelAndJoin()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            store = PreferenceDataStoreFactory.create(scope = scope) { file }
            assertEquals(OriginalSmartPreferences(true, 12345), OriginalSmartSettingsRepository(store, scope).snapshot())
            assertEquals(setOf("original_smart.enabled", "original_smart.owner_id"), store.data.first().asMap().keys.map { it.name }.toSet())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }
}
