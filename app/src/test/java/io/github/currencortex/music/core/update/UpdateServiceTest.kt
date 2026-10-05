package io.github.currencortex.music.core.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.github.currencortex.music.core.logging.LogSink
import io.github.currencortex.music.core.update.UpdateChannel
import io.github.currencortex.music.data.update.UpdateSettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test

class UpdateServiceTest {
    private val logger = object : LogSink {
        override fun info(tag: String, message: String) {}
        override fun warn(tag: String, message: String, error: Throwable?) {}
    }
    private val release = AppRelease("1.2.0", "Notes", "https://github.com/example/app/releases/tag/v1.2.0", null, null, false)

    private suspend fun scenario(block: suspend (UpdateSettingsRepository) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        // State-machine tests use the DataStore contract; Android instrumentation
        // verifies real disk writes without Windows File.renameTo limitations.
        val store = object : DataStore<Preferences> {
            override val data = MutableStateFlow<Preferences>(emptyPreferences())
            private val mutex = Mutex()
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                mutex.withLock { transform(data.value).also { data.value = it } }
        }
        try { block(UpdateSettingsRepository(store, scope)) } finally { scope.cancel() }
    }

    @Test fun defaultsAutoCheckOnStableChannel() = runBlocking {
        scenario { settings ->
            val defaults = settings.snapshot()
            assertTrue(defaults.autoCheckOnLaunch)
            assertEquals(UpdateChannel.STABLE, defaults.channel)
        }
    }

    @Test fun explicitlySelectedPrereleaseChannelIsPreserved() = runBlocking {
        scenario { settings ->
            settings.setChannel(UpdateChannel.PRERELEASE)
            assertEquals(UpdateChannel.PRERELEASE, settings.snapshot().channel)
        }
    }

    @Test fun automaticPromptsRespectIgnoreSettingsAndManualChecksOverrideIt() = runBlocking {
        scenario { settings ->
            settings.setChannel(UpdateChannel.STABLE)
            val service = UpdateService(ReleaseChecker { release }, settings, logger)
            service.check()
            assertTrue(service.dialogVisible.value)
            service.ignoreCurrentRelease()
            service.check()
            assertFalse(service.dialogVisible.value)
            settings.ignoreRelease(UpdateChannel.PRERELEASE, "2.0.0-rc.1")
            assertEquals("1.2.0", settings.snapshot().ignoredVersions[UpdateChannel.STABLE])
            assertEquals("2.0.0-rc.1", settings.snapshot().ignoredVersions[UpdateChannel.PRERELEASE])
            service.check(manual = true)
            assertTrue(service.dialogVisible.value)
            assertTrue(service.state.value is UpdateState.Available)
        }
    }

    @Test fun quietAutomaticChecksAndManualFailureRetry() = runBlocking {
        scenario { settings ->
            var fails = false
            val service = UpdateService(ReleaseChecker {
                if (fails) throw java.io.IOException("offline") else null
            }, settings, logger)
            service.check()
            assertEquals(UpdateState.UpToDate, service.state.value)
            assertFalse(service.dialogVisible.value)
            fails = true
            service.check(manual = true)
            assertTrue(service.state.value is UpdateState.Failed)
            assertTrue(service.dialogVisible.value)
            fails = false
            service.check(manual = true)
            assertEquals(UpdateState.UpToDate, service.state.value)
        }
    }

    @Test fun concurrentChecksAreDeduplicatedAndClosingTheDialogStaysClosed() = runBlocking {
        scenario { settings ->
            val started = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            var calls = 0
            val service = UpdateService(ReleaseChecker {
                calls++
                started.complete(Unit)
                runBlocking { finish.await() }
                release
            }, settings, logger)
            val check = async { service.check(manual = true) }
            withTimeout(5000) { started.await() }
            service.check(manual = true)
            service.dismissDialog()
            finish.complete(Unit)
            check.await()
            assertEquals(1, calls)
            assertFalse(service.dialogVisible.value)
        }
    }
}
