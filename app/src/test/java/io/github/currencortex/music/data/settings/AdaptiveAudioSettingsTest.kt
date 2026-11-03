package io.github.currencortex.music.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdaptiveAudioSettingsTest {
    private class MemoryStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        private val mutex = Mutex()
        var writes = 0
        var beforeWrite: suspend () -> Unit = {}
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            beforeWrite()
            transform(data.value).also { writes++; data.value = it }
        }
    }
    @Test fun dragIsAudibleWithoutDiskWritesAndCancellationRestoresSavedSettings() = runTest {
        val store = MemoryStore()
        val repo = AdaptiveAudioSettingsRepository(store, backgroundScope)
        repo.edit { it.copy(enabled = true, virtualBass = true) }; runCurrent()
        repeat(30) { step -> repo.preview { it.copy(bassStrength = step) }; runCurrent() }
        assertEquals(29, repo.effective.value.bassStrength)
        assertEquals(50, repo.snapshot().bassStrength)
        assertEquals(1, store.writes)
        repo.clearPreview(); runCurrent()
        assertEquals(repo.state.value, repo.effective.value)
    }
    @Test fun committingOneFieldPreservesOthersAndCannotClearTheNextGesture() = runTest {
        val store = MemoryStore()
        val repo = AdaptiveAudioSettingsRepository(store, backgroundScope)
        repo.edit { it.copy(enabled = true) }; runCurrent()
        val gate = CompletableDeferred<Unit>()
        store.beforeWrite = { gate.await() }
        val first: (AdaptiveAudioSettings) -> AdaptiveAudioSettings = { it.copy(bassStrength = 63) }
        repo.preview(first); runCurrent()
        val saving = launch { repo.commitPreview(first) }; runCurrent()
        repo.preview { it.copy(hapticStrength = 77) }; runCurrent()
        gate.complete(Unit); runCurrent(); saving.join()
        assertEquals(63, repo.snapshot().bassStrength)
        assertEquals(77, repo.effective.value.hapticStrength)
        assertEquals(50, repo.snapshot().hapticStrength)
        val second = launch { repo.commitPreview { it.copy(hapticStrength = 77) } }
        runCurrent(); second.join()
        assertEquals(repo.state.value, repo.effective.value)
        assertEquals(3, store.writes)
        assertTrue(repo.snapshot().enabled)
    }
    @Test fun leavingDuringSaveDoesNotUndoACommittedValueOrLeaveAnOverride() = runTest {
        val store = MemoryStore()
        val repo = AdaptiveAudioSettingsRepository(store, backgroundScope)
        val gate = CompletableDeferred<Unit>()
        store.beforeWrite = { gate.await() }
        val edit: (AdaptiveAudioSettings) -> AdaptiveAudioSettings = { it.copy(strength = 4) }
        repo.preview(edit); runCurrent()
        val saving = launch { repo.commitPreview(edit) }; runCurrent()
        repo.clearPreview(); runCurrent()
        gate.complete(Unit); runCurrent(); saving.join()
        assertEquals(4, repo.snapshot().strength)
        assertEquals(repo.state.value, repo.effective.value)
    }
}
