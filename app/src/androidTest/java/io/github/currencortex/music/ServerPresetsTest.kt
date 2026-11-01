package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.config.ServerDefaults
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.feature.settings.MusicSettingsScreen
import io.github.currencortex.music.feature.settings.MusicSettingsViewModel
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.io.File
import top.yukonga.miuix.kmp.basic.Scaffold

/** Real settings/dialog UI and persistence, with deterministic anonymous checks in an isolated package. */
class ServerPresetsTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private val theme = mutableStateOf(ThemeMode.LIGHT)
    @Before fun setup(): Unit = runBlocking {
        container = AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(),
            "server-presets-${UUID.randomUUID()}", serverCheck = ServerCheck {
                delay(100)
                ServerHealth(ServerStatus.AVAILABLE, when (it) {
                    ServerDefaults.CDN -> 30
                    ServerDefaults.CDN_GEEKERTAO -> 80
                    ServerDefaults.CDN_CTCEHS -> 120
                    else -> 500
                })
            })
        container.ready.await(); container.sessionRestored.await()
        container.musicSettings.setServerSelection(CurrentMusicServer.MAIN, ServerDefaults.URL)
        container.accountRepository.server = ServerDefaults.URL
        container.accountRepository.save("isolated-server-test", UserDto(123, nickname = "线路验证"))
        container.updateSettings.setAutoCheck(false)
        val vm = MusicSettingsViewModel(container)
        compose.setContent { LeiTheme(AppearanceSettings(blur = false, themeMode = theme.value)) {
            Scaffold { MusicSettingsScreen(vm) }
        } }
        compose.onNodeWithTag("network_settings").performScrollToNode(hasTestTag("server_source"))
    }
    @After fun finish() { if (::container.isInitialized) container.close() }
    private fun openSources() {
        compose.onNodeWithTag("server_source").performClick()
        compose.waitUntil(8000) {
            compose.onAllNodesWithTag("server_source_badge_CDN", useUnmergedTree = true)
                .fetchSemanticsNodes().any { it.config.toString().contains("30毫秒") }
        }
    }

    @Test fun fixedOrderAndLatenciesAndPresetChangesKeepLogin() {
        openSources()
        val tops = listOf("AUTO", "MAIN", "CDN", "CDN_GEEKERTAO", "CDN_CTCEHS", "CUSTOM").map {
            compose.onNodeWithTag("server_source_$it").fetchSemanticsNode().boundsInRoot.top
        }
        assertEquals(tops.sorted(), tops)
        compose.onNodeWithText("500 ms").assertExists()
        compose.onNodeWithText("80 ms").assertExists()
        compose.onNodeWithText("120 ms").assertExists()
        compose.onAllNodesWithText("30 ms").assertCountEquals(2)
        capture("server-presets-light.png")
        compose.runOnIdle { theme.value = ThemeMode.DARK }
        compose.waitForIdle()
        capture("server-presets-dark.png")
        compose.onNodeWithTag("server_source_CDN").performClick()
        compose.waitUntil(5000) { runBlocking { container.musicSettings.snapshot().serverMode == CurrentMusicServer.CDN } &&
            container.serverRouter.resolve(ServerDefaults.URL) == ServerDefaults.CDN }
        assertUpdateRoute(ServerDefaults.CDN)
        assertEquals("isolated-server-test", container.accountRepository.token)
        assertEquals(ServerDefaults.URL, container.accountRepository.server)
        assertEquals(ServerDefaults.CDN, container.serverRouter.resolve(ServerDefaults.URL))
        openSources()
        compose.onNodeWithTag("server_source_CDN_GEEKERTAO").performClick()
        compose.waitUntil(5000) { runBlocking { container.musicSettings.snapshot().serverMode == CurrentMusicServer.CDN_GEEKERTAO } &&
            container.serverRouter.resolve(ServerDefaults.URL) == ServerDefaults.CDN_GEEKERTAO }
        assertUpdateRoute(ServerDefaults.CDN_GEEKERTAO)
        assertEquals("isolated-server-test", container.accountRepository.token)
        assertEquals(ServerDefaults.URL, container.accountRepository.server)
        openSources()
        compose.onNodeWithTag("server_source_CDN_CTCEHS").performClick()
        compose.waitUntil(5000) {runBlocking {container.musicSettings.snapshot().serverMode==CurrentMusicServer.CDN_CTCEHS}&&
            container.serverRouter.resolve(ServerDefaults.URL)==ServerDefaults.CDN_CTCEHS}
        assertUpdateRoute(ServerDefaults.CDN_CTCEHS)
        assertEquals("isolated-server-test",container.accountRepository.token)
        assertEquals(ServerDefaults.URL,container.accountRepository.server)
        openSources()
        compose.onNodeWithTag("server_source_AUTO").performClick()
        compose.waitUntil(5000) { runBlocking { container.musicSettings.snapshot().serverMode == CurrentMusicServer.AUTO } &&
            container.serverRouter.resolve(ServerDefaults.URL) == ServerDefaults.CDN }
        assertUpdateRoute(ServerDefaults.CDN)
        assertEquals("isolated-server-test", container.accountRepository.token)
        assertEquals(ServerDefaults.CDN, container.serverRouter.resolve(ServerDefaults.URL))
    }
    private fun assertUpdateRoute(server: String) {
        val release = io.github.currencortex.music.core.update.AppRelease("1.3.0", "", "",
            "https://github.com/bileizhen/CurrentMusicX/releases/download/v1.3.0/app.apk", "app.apk", false)
        assertEquals(server + "x/download/latest", container.updateAssetUrl(release,
            io.github.currencortex.music.core.update.UpdateSource.CURRENTMUSIC))
        assertEquals(release.apkUrl, container.updateAssetUrl(release,
            io.github.currencortex.music.core.update.UpdateSource.GITHUB))
    }

    private fun capture(name: String) {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val bitmap = compose.onNodeWithTag("music_dialog_surface").captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun customInputValidatesAndPersistsAndClearsOnlyTheChangedAccount() {
        openSources()
        compose.onNodeWithTag("server_source_CUSTOM").performClick()
        compose.onNodeWithTag("server_input").performTextInput("invalid")
        compose.onNodeWithTag("save_server").performClick()
        compose.onNodeWithTag("server_input").assertExists()
        assertEquals("isolated-server-test", container.accountRepository.token)
        compose.onNodeWithTag("server_input").performTextReplacement("https://custom.example/api/")
        compose.onNodeWithTag("save_server").performClick()
        compose.waitUntil(5000) { runBlocking { container.musicSettings.snapshot().customServer == "https://custom.example/api/" } }
        assertNull(container.accountRepository.token)
        assertEquals("https://custom.example/api/", container.accountRepository.server)
        openSources()
        compose.onNodeWithTag("server_source_MAIN").performClick()
        compose.waitUntil(5000) { runBlocking { container.musicSettings.snapshot().serverMode == CurrentMusicServer.MAIN } }
        assertEquals("https://custom.example/api/", runBlocking { container.musicSettings.snapshot().customServer })
    }

    @Test fun liveCtcehsPresetFromThePhone() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveCtcehsServer")=="true")
        val health=CurrentMusicServerProbe().check(ServerDefaults.CDN_CTCEHS)
        val context=ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        File(context.getExternalFilesDir(null),"server-ctcehs-live.txt").writeText("${ServerDefaults.CDN_CTCEHS}: $health")
        assertEquals("The new CDN must serve the anonymous announcement API on this phone",ServerStatus.AVAILABLE,health.status)
    }

    @Test fun livePublicPresetsFromThePhone() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveServerPresets") == "true")
        val probe = CurrentMusicServerProbe()
        val results = coroutineScope {
            CurrentMusicServer.presets.map { source -> async { requireNotNull(source.url) to probe.check(requireNotNull(source.url)) } }
                .awaitAll().toMap()
        }
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        File(context.getExternalFilesDir(null), "server-presets-live.txt").writeText(results.entries.joinToString("\n") { "${it.key}: ${it.value}" })
        assertEquals("The CDN must serve the real anonymous API on this phone", ServerStatus.AVAILABLE, results[ServerDefaults.CDN]?.status)
        assertEquals("The new CDN must serve the real anonymous API on this phone", ServerStatus.AVAILABLE, results[ServerDefaults.CDN_GEEKERTAO]?.status)
    }
}
