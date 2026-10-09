package io.github.currencortex.music

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.core.visualizer.CaptureStatus
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.player.PlayerScreen
import io.github.currencortex.music.feature.player.PlayerViewModel
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

class VisualizerDebugUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var vm: PlayerViewModel
    @Before fun setup() = runBlocking {
        container = AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(), "visualizer-ui-${UUID.randomUUID()}")
        container.ready.await(); container.sessionRestored.await()
        val song = Song(-93001, "音频调试 UI", durationMs = 10000)
        container.playbackQueue.replace(listOf(song), 0)
        container.playerController.state.value = PlayerState(song = song)
        vm = PlayerViewModel(container)
    }
    @After fun cleanup() { vm.audioAnalysis.close(); container.close() }
    @Test fun playerMenuOpensRealDiagnosticsWithCaptureOffAndLeavesArtworkIntact() {
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            top.yukonga.miuix.kmp.basic.Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) {
                PlayerScreen(vm, {}, {})
            }
        } }
        compose.onNodeWithTag("lyrics_options").performClick()
        compose.onNodeWithTag("open_visualizer").performScrollTo().performClick()
        compose.onNodeWithTag("visualizer_status").assertTextEquals(CaptureStatus.DISABLED.label)
        compose.onNodeWithTag("visualizer_toggle").assertTextEquals("开启音频采集")
        compose.onNodeWithTag("visualizer_spectrum").assertExists()
        compose.onNodeWithTag("visualizer_fps_FPS_120").performClick()
        compose.waitUntil(5000) { vm.settings.value.visualizerRender.frameRate == io.github.currencortex.music.data.visualizer.VisualizerFrameRate.FPS_120 }
        assertFalse(runBlocking { container.musicSettings.snapshot().visualizerEnabled })
        assertEquals(0, vm.audioAnalysis.frames.value.sampleRateHz)
        compose.onNodeWithText("关闭", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("visualizer_spectrum").assertDoesNotExist()
        assertFalse(runBlocking { container.musicSettings.snapshot().visualizerEnabled })
        assertEquals("音频调试 UI", container.playbackQueue.state.value.current?.name)
    }
    @Test fun previewSelectorAndParametersPersistWithoutImplicitAudioOptIn() {
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            top.yukonga.miuix.kmp.basic.Scaffold(contentWindowInsets = WindowInsets(0,0,0,0)) {
                io.github.currencortex.music.feature.visualizer.AudioAnalysisDebugDialog(vm, true, {})
            }
        } }
        compose.onNodeWithTag("visualizer_preview_toggle").performScrollTo().performClick()
        for (preset in io.github.currencortex.music.core.visualizer.VisualizerPreset.entries) {
            compose.onNodeWithTag("visualizer_preset_${preset.name}").performScrollTo().performClick()
            compose.waitUntil(5000) { vm.settings.value.visualizerEffects.presetId == preset }
            compose.onNodeWithTag("visualizer_preset_stage").assertExists()
            assertFalse(runBlocking { container.musicSettings.snapshot().visualizerEnabled })
        }
        compose.onNodeWithTag("visualizer_details").performScrollTo().performClick()
        compose.onNodeWithTag("visualizer_reduce_motion").performScrollTo().performClick()
        compose.waitUntil(5000) { vm.settings.value.visualizerEffects.reduceMotion }
        compose.onNodeWithTag("visualizer_parameter_intensity").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(1.6f) }
        compose.waitUntil(5000) { vm.settings.value.visualizerEffects.globalIntensity == 1.6f }
        assertEquals(0, vm.audioAnalysis.frames.value.sampleRateHz)
        assertEquals("音频调试 UI", container.playbackQueue.state.value.current?.name)
    }
}
