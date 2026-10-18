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
    @Test fun nativeSourceIsSelectablePersistsAndScopesCacheToTheNeteaseAccount(): Unit = runBlocking {
        val vm = MusicSettingsViewModel(container)
        compose.setContent { LeiTheme(AppearanceSettings()) { MusicSettingsScreen(vm) {} } }
        compose.onNodeWithTag("network_settings").performScrollToNode(hasTestTag("audio_provider_netease"))
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
        compose.onNodeWithTag("network_settings").performScrollToNode(hasTestTag("audio_provider_leiz"))
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
