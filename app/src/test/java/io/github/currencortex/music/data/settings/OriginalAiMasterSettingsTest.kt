package io.github.currencortex.music.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test

class OriginalAiMasterSettingsTest {
    private class MemoryPreferences(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)
        private val mutex = Mutex()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            transform(data.value).also { data.value = it }
        }
    }

    // Real file rewrite/reopen runs in OriginalAiMasterSettingsStorageTest on Android.
    @Test fun perAccountDiyPersistsAcrossRecreationAndEffectOff() = runBlocking {
        var persisted: Preferences = emptyPreferences()
        suspend fun open(block: suspend (OriginalAiMasterSettingsRepository) -> Unit) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val store = MemoryPreferences(persisted)
            try {
                block(OriginalAiMasterSettingsRepository(store, scope))
            } finally {
                persisted = store.data.value
                scope.coroutineContext[Job]!!.cancelAndJoin()
            }
        }
        open { settings ->
                settings.setDiy(10, setOf("en_bass", "en_live"))
                settings.setDiy(20, setOf("en_vocal"))
                settings.set(OriginalSmartPreferences(true, 10))
                settings.set(OriginalSmartPreferences())
                assertEquals(setOf("en_bass", "en_live"), settings.diyFor(10))
                assertEquals(setOf("en_vocal"), settings.diyFor(20))
                assertFalse(settings.snapshot().enabled)
                assertTrue(settings.diyFor(30).isEmpty())
        }
        open { settings ->
                assertEquals(setOf("en_bass", "en_live"), settings.diyFor(10))
                assertEquals(setOf("en_vocal"), settings.diyFor(20))
                assertTrue(runCatching { settings.setDiy(10, setOf("fake")) }.isFailure)
                assertTrue(runCatching { settings.setDiy(0, setOf("en_bass")) }.isFailure)
        }
    }
}
