package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ApplicationProvider
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.currencortex.music.core.security.SecureTokenStore
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.feature.settings.*
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

/** Independent namespace and empty queue: never starts the production music service. */
class AudioSourceSettingsTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    private val namespace = "source-test-${UUID.randomUUID()}"
    @Before fun prepare() = runBlocking {
        container = AppContainer(context, namespace)
        container.ready.await(); container.sessionRestored.await()
        assertTrue(container.playbackQueue.state.value.songs.isEmpty())
    }
    @After fun finish() { container.close() }
    @Test fun importedLxSourcesSelectWithScopedIdentityAndRemovalKeepsLxProvider(): Unit = runBlocking {
        val prefix = "/**\n * @name Source A\n * @version 1\n */\n"
        val script = prefix + "lx.on('request', () => Promise.resolve('https://audio.example.com/song.mp3'));" +
            "lx.send('inited',{sources:{wy:{type:'music',actions:['musicUrl'],qualitys:['128k','320k']}}});"
        val first = container.lxSources.importScript(script)
        val second = container.lxSources.importScript(script.replace("Source A", "Source B"))
        container.lxSources.health.value = mapOf(first.id to io.github.currencortex.music.data.lx.LxSourceHealth(
            io.github.currencortex.music.core.network.SourceAvailabilityStatus.AVAILABLE, android.os.SystemClock.elapsedRealtime()),
            second.id to io.github.currencortex.music.data.lx.LxSourceHealth(
                io.github.currencortex.music.core.network.SourceAvailabilityStatus.UNAVAILABLE, android.os.SystemClock.elapsedRealtime()))
        container.audioSettings.selectLxSource(first.id)
        val vm = MusicSettingsViewModel(container)
        compose.setContent { LeiTheme(AppearanceSettings()) { top.yukonga.miuix.kmp.basic.Scaffold { MusicSettingsScreen(vm) {} } } }
        compose.onNodeWithTag("network_settings").performScrollToNode(hasTestTag("audio_provider"))
        compose.onNodeWithTag("audio_provider").performClick()
        compose.onNodeWithTag("audio_provider_lx").performClick()
        compose.waitUntil(5000) { vm.audioProvider.value.provider == AudioProvider.LX && !vm.audioState.value.busy }
        val previous = container.audioSettings.access().identity
        compose.onNodeWithTag("network_settings").performScrollToNode(hasTestTag("lx_source_select"))
        compose.onNode(hasTestTag("lx_source_badge_${first.id}") and hasAnyAncestor(hasTestTag("lx_source_select")), true)
            .assertContentDescriptionEquals("音源：可用")
        compose.onNodeWithTag("lx_source_select").performClick()
        compose.onNodeWithTag("lx_source_${second.id}").assertExists()
        compose.onNodeWithTag("lx_source_${second.id}").performClick()
        compose.waitUntil(5000) { vm.audioProvider.value.lxSourceId == second.id && !vm.audioState.value.busy }
        assertNotEquals(previous, container.audioSettings.access().identity)
        compose.onNode(hasTestTag("lx_source_badge_${second.id}") and hasAnyAncestor(hasTestTag("lx_source_select")), true)
            .assertContentDescriptionEquals("音源：不可用")
        val encrypted = File(context.noBackupFilesDir, "session.lx-sources.$namespace.aes").readBytes()
        assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("Source A"))
        compose.onNodeWithTag("network_settings").performScrollToNode(hasTestTag("lx_import_url"))
        compose.onNodeWithTag("lx_import_url").performClick()
        compose.onNodeWithTag("lx_url_input").assertExists()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("network_settings").performScrollToNode(hasTestTag("lx_remove"))
        compose.onNodeWithTag("lx_remove").performClick()
        compose.onNodeWithTag("lx_remove_confirm").performClick()
        compose.waitUntil(5000) { vm.audioProvider.value.provider == AudioProvider.LX &&
            vm.audioProvider.value.lxSourceId.isEmpty() && !vm.audioState.value.busy }
        assertEquals(listOf(first), container.lxSources.state.value)
        // An empty LX selection must not silently request a URL from the project server.
        try {
            container.musicRepository.source(io.github.currencortex.music.data.song.Song(42, "Removed source fixture"),
                io.github.currencortex.music.core.media.AudioQuality.STANDARD)
            fail("An unselected LX source must require choosing a script")
        } catch (error: io.github.currencortex.music.core.network.ApiException) {
            assertEquals(io.github.currencortex.music.core.network.ErrorKind.Server, error.kind)
        }
        vm.selectLxSource(first.id).join()
        vm.removeLxSource(first.id).join()
        assertTrue(container.lxSources.state.value.isEmpty())
        assertEquals(AudioProvider.LX, container.audioSettings.access().provider)
        assertEquals("", container.audioSettings.access().lxSourceId)
        assertTrue(container.playbackQueue.state.value.songs.isEmpty())
    }
    @Test fun visibleLxSettingsAutomaticallyChecksAndShowsTheResultWithoutAButton(): Unit = runBlocking {
        val script = "/**\n * @name Automatic check fixture\n */\n" +
            "lx.on('request', async () => 'http://127.0.0.1/blocked-audio');" +
            "lx.send('inited',{sources:{wy:{type:'music',actions:['musicUrl'],qualitys:['128k']}}});"
        val source = container.lxSources.importScript(script)
        container.audioSettings.selectLxSource(source.id)
        container.audioSettings.select(AudioProvider.LX)
        assertNull(container.lxSources.health.value[source.id])
        val vm = MusicSettingsViewModel(container)
        compose.setContent { LeiTheme(AppearanceSettings()) { top.yukonga.miuix.kmp.basic.Scaffold { MusicSettingsScreen(vm) {} } } }
        compose.onNodeWithTag("network_settings").performScrollToNode(hasTestTag("lx_source_select"))
        compose.waitUntil(5000) {
            vm.lxHealth.value[source.id]?.status == io.github.currencortex.music.core.network.SourceAvailabilityStatus.UNAVAILABLE
        }
        compose.onNodeWithTag("lx_source_badge_${source.id}", true).assertContentDescriptionEquals("音源：不可用")
        compose.onNodeWithText("检测音源可用性").assertDoesNotExist()
        assertEquals(source.id, container.audioSettings.access().lxSourceId)
        assertTrue(container.playbackQueue.state.value.songs.isEmpty())
    }

    @Test fun nativeSourceIsSelectablePersistsAndScopesCacheToTheNeteaseAccount(): Unit = runBlocking {
        val vm = MusicSettingsViewModel(container)
        compose.setContent { LeiTheme(AppearanceSettings()) { MusicSettingsScreen(vm) {} } }
        compose.onNodeWithTag("network_settings").performScrollToNode(hasTestTag("audio_provider"))
        compose.onNodeWithTag("audio_provider").performClick()
        compose.onNodeWithTag("audio_provider_netease").performClick()
        compose.waitUntil(5000) { vm.audioProvider.value.provider == AudioProvider.NETEASE && !vm.audioState.value.busy }
        assertEquals("netease", container.audioSettings.access().identity)
        compose.onNodeWithTag("audio_key_input").assertDoesNotExist()
        val song = io.github.currencortex.music.data.song.Song(11, "Native fixture")
        container.neteaseSessions.login(mapOf("MUSIC_U" to "fixture-cookie"),
            io.github.currencortex.music.data.binding.BoundProfile(42, "Fixture"), container.neteaseSessions.snapshot())
        val first = container.audioRequest(song, io.github.currencortex.music.core.media.AudioQuality.STANDARD)
        assertEquals(42L, first.accountId)
        assertEquals("https://music.163.com/", first.session.server)
        assertNull(first.session.token)
        container.accountRepository.server = "https://unreachable.invalid/cm/"
        assertEquals("Changing a project server cannot change a native audio cache key", first.key,
            container.audioRequest(song, first.quality).key)
        container.neteaseSessions.login(mapOf("MUSIC_U" to "another-fixture-cookie"),
            io.github.currencortex.music.data.binding.BoundProfile(99, "Other fixture"), container.neteaseSessions.snapshot())
        val other = container.audioRequest(song, first.quality)
        assertNotEquals(first.key, other.key); assertNotEquals(first.session, other.session)
        val preferences = context.preferencesDataStoreFile("app.$namespace.preferences_pb").readBytes()
        assertTrue(preferences.toString(Charsets.ISO_8859_1).contains("NETEASE"))
        assertTrue(container.playbackQueue.state.value.songs.isEmpty())
    }
    @Test fun sourceControlsStoreOnlyEncryptedKeyAndKeepProjectAccountAndQueue() = runBlocking<Unit> {
        val vm = MusicSettingsViewModel(container)
        val account = container.accountRepository.state.value
        val server = container.musicSettings.snapshot().server
        compose.setContent { LeiTheme(AppearanceSettings()) { MusicSettingsScreen(vm) {} } }
        // The key card only exists once LeiZ is the selected source.
        compose.onNodeWithTag("audio_key_input").assertDoesNotExist()
        compose.onNodeWithTag("network_settings").performScrollToNode(hasTestTag("audio_provider"))
        compose.onNodeWithTag("audio_provider").performClick()
        compose.onNodeWithTag("audio_provider_leiz").performClick()
        compose.waitUntil(5000) { vm.audioProvider.value.provider == AudioProvider.LEIZ && !vm.audioState.value.busy }
        compose.onNodeWithTag("network_settings").performScrollToNode(hasTestTag("audio_key_input"))
        compose.onNodeWithTag("audio_key_input").performTextInput("lz_fixture_only_not_a_real_key")
        compose.onNodeWithTag("audio_key_save").performClick()
        compose.waitUntil(5000) { vm.audioProvider.value.keyConfigured && !vm.audioState.value.busy }
        assertEquals("", compose.onNodeWithTag("audio_key_input").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        val access = container.audioSettings.access()
        assertTrue(access.identity.startsWith("leiz:"))
        assertFalse(access.identity.contains("fixture")); assertFalse(access.toString().contains("fixture"))
        val vault = SecureTokenStore(context, ".leiz.$namespace")
        assertEquals("lz_fixture_only_not_a_real_key", vault.read())
        val encrypted = File(context.noBackupFilesDir, "session.leiz.$namespace.aes").readBytes()
        assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("lz_fixture"))
        val preferences = context.preferencesDataStoreFile("app.$namespace.preferences_pb").readBytes()
        assertFalse(preferences.toString(Charsets.ISO_8859_1).contains("lz_fixture"))
        assertTrue(preferences.toString(Charsets.ISO_8859_1).contains("LEIZ"))
        compose.onNodeWithTag("network_settings").performScrollToNode(hasTestTag("audio_key_clear"))
        compose.onNodeWithTag("audio_key_clear").performClick()
        compose.waitUntil(5000) { !vm.audioProvider.value.keyConfigured && !vm.audioState.value.busy }
        assertNull(vault.read())
        assertNotEquals(access.identity, container.audioSettings.access().identity)
        assertEquals(account, container.accountRepository.state.value)
        assertEquals(server, container.musicSettings.snapshot().server)
        assertTrue(container.playbackQueue.state.value.songs.isEmpty())
    }
}
