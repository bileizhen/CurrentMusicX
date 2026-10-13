package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.feature.settings.*
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class PlaybackPreferencesTest {
    @get:Rule val compose = createComposeRule()
    @Test fun controlsAreExclusiveAndDurationAndConcurrentPolicySurviveReopening() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val container = AppContainer(context, "playback-preferences-${UUID.randomUUID()}")
        try {
            container.ready.await(); container.sessionRestored.await()
            val vm = MusicSettingsViewModel(container)
            assertEquals(5, container.musicSettings.snapshot().crossfadeSeconds)
            assertEquals(MusicTransitionMode.OFF, container.musicSettings.snapshot().transitionMode)
            assertFalse(container.musicSettings.snapshot().allowConcurrentPlayback)
            compose.setContent { LeiTheme(AppearanceSettings()) { MusicSettingsScreen(vm) {} } }
            fun click(tag: String) {
                compose.onNodeWithTag("network_settings").performScrollToNode(hasTestTag(tag))
                compose.onNodeWithTag(tag).performClick()
            }
            click("smart_transition")
            compose.waitUntil(5000) { vm.settings.value.transitionMode == MusicTransitionMode.SMART }
            click("crossfade")
            compose.waitUntil(5000) { vm.settings.value.transitionMode == MusicTransitionMode.CROSSFADE }
            compose.onNodeWithTag("network_settings").performScrollToNode(hasTestTag("crossfade_duration"))
            compose.onNodeWithText("过渡时长 · 5 秒").assertExists()
            compose.onNodeWithTag("crossfade_duration").performSemanticsAction(SemanticsActions.SetProgress) { it(8f) }
            compose.waitUntil(5000) { vm.settings.value.crossfadeSeconds == 8 }
            click("allow_concurrent_playback")
            compose.waitUntil(5000) { vm.settings.value.allowConcurrentPlayback }
            click("smart_transition")
            compose.waitUntil(5000) { vm.settings.value.transitionMode == MusicTransitionMode.SMART }
            compose.onNodeWithTag("crossfade_duration").assertDoesNotExist()
            click("crossfade")
            compose.waitUntil(5000) { vm.settings.value.transitionMode == MusicTransitionMode.CROSSFADE }
            compose.onNodeWithTag("network_settings").performScrollToNode(hasTestTag("crossfade_duration"))
            compose.onNodeWithText("过渡时长 · 8 秒").assertExists()
            assertTrue(container.playbackQueue.state.value.songs.isEmpty())
        } finally { container.close() }

        // Close the actual disk store, then recreate it to exercise process-restart persistence.
        val file = File(context.cacheDir, "playback-${UUID.randomUUID()}.preferences_pb")
        var owner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            var repo = MusicSettingsRepository(PreferenceDataStoreFactory.create(scope = owner) { file }, owner)
            repo.setConcurrentPlayback(true); repo.setTransitionMode(MusicTransitionMode.CROSSFADE); repo.setCrossfadeSeconds(8)
            owner.coroutineContext[Job]!!.cancelAndJoin()
            owner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            repo = MusicSettingsRepository(PreferenceDataStoreFactory.create(scope = owner) { file }, owner)
            assertTrue(repo.snapshot().allowConcurrentPlayback)
            assertEquals(MusicTransitionMode.CROSSFADE, repo.snapshot().transitionMode)
            assertEquals(8, repo.snapshot().crossfadeSeconds)
            repo.setCrossfadeSeconds(100); assertEquals(12, repo.snapshot().crossfadeSeconds)
            repo.setCrossfadeSeconds(-1); assertEquals(1, repo.snapshot().crossfadeSeconds)
        } finally { owner.coroutineContext[Job]!!.cancelAndJoin(); file.delete() }
    }
}
