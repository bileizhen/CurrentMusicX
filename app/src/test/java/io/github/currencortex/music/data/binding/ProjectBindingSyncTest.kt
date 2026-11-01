package io.github.currencortex.music.data.binding

import io.github.currencortex.music.core.network.RequestSession
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ProjectBindingSyncTest {
    private fun owner(server: String = "https://music.example/cm/", token: String? = "project-token", revision: Long = 1) =
        RequestSession(server, token, revision)

    @Test fun successfulSyncBelongsToCapturedAccountAndRedactsCredentials() = runBlocking {
        val captured = owner()
        var uploads = 0
        val sync = ProjectBindingSync({ captured }, { BindingCredential(42, "MUSIC_U=device-login") }, { login, expected ->
            assertEquals(42L, login.uid)
            assertEquals(captured, expected)
            assertFalse(login.toString().contains("device-login"))
            uploads++
        })
        sync.sync()
        assertEquals(1, uploads)
        assertTrue(sync.state.value.synced)
    }

    @Test fun cleartextOrCredentialBearingServersNeverReceiveLogin() = runBlocking {
        for (server in listOf("http://music.example/cm/", "https://user:password@music.example/cm/")) {
            val sync = ProjectBindingSync({ owner(server) }, { fail("Must not read credentials"); BindingCredential(0, "") }, { _, _ -> fail("Must not upload") })
            sync.sync()
            assertNotNull(sync.state.value.error)
            assertFalse(sync.state.value.syncing)
        }
    }

    @Test fun missingProjectLoginNeverReceivesCredentials() = runBlocking {
        val sync = ProjectBindingSync({ owner(token = null) }, { fail("No account"); BindingCredential(0, "") }, { _, _ -> fail("No upload") })
        sync.sync()
        assertNotNull(sync.state.value.error)
    }

    @Test fun changedOwnerBeforeUploadCancelsSharing() = runBlocking {
        var active = owner()
        lateinit var sync: ProjectBindingSync
        sync = ProjectBindingSync({ active }, {
            active = owner(token = "another-user"); sync.reset()
            BindingCredential(42, "MUSIC_U=device-login")
        }, { _, _ -> fail("Old login must not be uploaded to the new account") })
        sync.sync()
        assertEquals(ProjectBindingSyncState(), sync.state.value)
    }

    @Test fun accountSwitchDuringUploadNeverMarksNewAccountSynced() = runBlocking {
        var active = owner()
        lateinit var sync: ProjectBindingSync
        sync = ProjectBindingSync({ active }, { BindingCredential(42, "MUSIC_U=device-login") }, { _, _ ->
            active = owner(token = "another-user"); sync.reset()
        })
        sync.sync()
        assertEquals(ProjectBindingSyncState(), sync.state.value)
    }

    @Test fun failurePreservesLoginAndExplicitRetrySucceedsWithoutLeakingError() = runBlocking {
        var attempts = 0
        val sync = ProjectBindingSync({ owner() }, { BindingCredential(42, "MUSIC_U=device-login") }, { _, _ ->
            attempts++; if (attempts == 1) error("device-login")
        })
        sync.sync()
        assertNotNull(sync.state.value.error)
        assertFalse(sync.state.value.error!!.contains("device-login"))
        assertEquals(1, attempts)
        sync.sync()
        assertTrue(sync.state.value.synced)
        assertEquals(2, attempts)
    }

    @Test fun ordinaryCacheRefreshDoesNotEraseInFlightSync() = runBlocking {
        lateinit var sync: ProjectBindingSync
        sync = ProjectBindingSync({ owner() }, { BindingCredential(42, "MUSIC_U=device-login") }, { _, _ -> sync.reset() })
        sync.sync()
        assertTrue(sync.state.value.synced)
    }

    @Test fun restoredLoginRetriesAutomaticallyUntilSuccessful() = runBlocking {
        var attempts = 0
        val sync = ProjectBindingSync({ owner() }, { BindingCredential(42, "MUSIC_U=restored-login") }, { _, _ ->
            attempts++; if (attempts < 3) error("temporarily offline")
        })
        sync.syncAutomatically(owner(), listOf(0L, 0L, 0L, 0L))
        assertEquals(3, attempts)
        assertTrue(sync.state.value.synced)
    }

    @Test fun automaticRetriesAreBoundedAndKeepLocalCredential() = runBlocking {
        var attempts = 0
        val login = BindingCredential(42, "MUSIC_U=restored-login")
        val sync = ProjectBindingSync({ owner() }, { login }, { _, _ -> attempts++; error("offline") })
        sync.syncAutomatically(owner(), listOf(0L, 0L, 0L, 0L))
        assertEquals(4, attempts)
        assertNotNull(sync.state.value.error)
        assertEquals("MUSIC_U=restored-login", login.cookie)
    }

    @Test fun automaticAndLoginTriggeredSyncDoNotDoubleUpload() = runBlocking {
        var uploads = 0
        val sync = ProjectBindingSync({ owner() }, { BindingCredential(42, "MUSIC_U=restored-login") }, { _, _ -> uploads++; yield() })
        listOf(async { sync.syncAutomatically(owner(), listOf(0L)) }, async { sync.sync() }).awaitAll()
        sync.syncAutomatically(owner(), listOf(0L))
        assertEquals(1, uploads)
    }

    @Test fun ownerChangeStopsAutomaticRetry() = runBlocking {
        var current = owner()
        var uploads = 0
        lateinit var sync: ProjectBindingSync
        sync = ProjectBindingSync({ current }, { BindingCredential(42, "MUSIC_U=restored-login") }, { _, _ ->
            uploads++; current = owner(token = "another-user"); sync.reset(); error("offline")
        })
        sync.syncAutomatically(owner(), listOf(0L, 0L, 0L))
        assertEquals(1, uploads)
        assertEquals(ProjectBindingSyncState(), sync.state.value)
    }

    @Test fun cancellationClearsProgressAndPropagates() = runBlocking {
        val sync = ProjectBindingSync({ owner() }, { throw CancellationException() }, { _, _ -> fail() })
        try { sync.sync(); fail("Cancellation must propagate") } catch (_: CancellationException) { }
        assertEquals(ProjectBindingSyncState(), sync.state.value)
    }
}
