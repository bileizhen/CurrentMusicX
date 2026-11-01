package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.netease.*
import io.github.currencortex.music.core.security.SecureTokenStore
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.binding.BoundProfile
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.binding.*
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

class ProjectBindingStartupTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var server: MockWebServer
    private val models = ViewModelStore()

    @After fun close() {
        models.clear()
        if (::container.isInitialized) container.close()
        if (::server.isInitialized) server.shutdown()
    }

    @Test fun restoredLocalLoginAutomaticallySyncsAfterProjectLoginAndExposesRetryWithoutCleartextUpload() = runBlocking<Unit> {
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(app.packageName.endsWith(".verification"))
        val namespace = "project-binding-${UUID.randomUUID()}"
        // Seed only this test's Keystore-backed native session, then let AppContainer restore it.
        val seedJob = SupervisorJob()
        val seed = NeteaseSessionStore(SecureTokenStore(app, ".netease.$namespace"), CoroutineScope(seedJob + Dispatchers.IO))
        seed.ready.await()
        seed.login(mapOf("MUSIC_U" to "synthetic-restored-login"), BoundProfile(42, "Restored fixture"), seed.snapshot())
        seedJob.cancelAndJoin()
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().setHeader("Content-Type", "application/json")
                    .setBody("""{"code":200,"bound":true,"profile":{"uid":42,"nickname":"Restored fixture"},"data":[]}""")
            }
            start()
        }
        val endpoint = server.url("/").toString()
        container = AppContainer(app, namespace, useNativeNetease = true,
            neteaseEndpoints = NeteaseEndpoints(endpoint, endpoint, endpoint))
        container.ready.await(); container.sessionRestored.await(); container.neteaseSessions.ready.await()
        assertTrue(container.neteaseSessions.state.value.loggedIn)
        assertEquals(42L, container.neteaseSessions.state.value.profile!!.uid)
        val base = server.url("/cm/").toString()
        container.musicSettings.setServer(base); container.accountRepository.server = base
        container.updateSettings.setAutoCheck(false)
        container.accountRepository.save("synthetic-project-token", UserDto(7, nickname = "Project fixture"))
        withTimeout(10_000) { container.bindingRepository.projectBindingSync.first { it.error != null } }
        assertTrue(container.neteaseSessions.state.value.loggedIn)
        assertFalse(container.neteaseSessions.state.value.stale)
        assertFalse(container.bindingRepository.projectBindingSync.value.synced)
        lateinit var vm: BindingViewModel
        compose.runOnUiThread { vm = BindingViewModel(container); models.put("binding", vm) }
        compose.setContent { LeiTheme(AppearanceSettings()) { BindingScreen(vm, {}) } }
        compose.onNodeWithTag("binding_screen").performScrollToNode(hasTestTag("binding_project_sync_retry"))
        compose.onNodeWithTag("binding_project_sync_retry").assertIsDisplayed().performClick()
        compose.onNodeWithTag("binding_project_sync_status").assertExists()
        while (true) {
            val request = server.takeRequest(50, java.util.concurrent.TimeUnit.MILLISECONDS) ?: break
            assertNotEquals("/cm/ncmbind/import", request.requestUrl!!.encodedPath)
        }
    }
}
