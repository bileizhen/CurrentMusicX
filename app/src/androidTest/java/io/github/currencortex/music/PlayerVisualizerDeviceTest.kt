package io.github.currencortex.music

import android.Manifest
import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.*
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.network.AppResult
import io.github.currencortex.music.core.visualizer.VisualizerPreset
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.player.PlayerScreen
import io.github.currencortex.music.feature.player.PlayerViewModel
import io.github.currencortex.music.feature.visualizer.*
import io.github.currencortex.music.feature.visualizer.render.VisualizerPresetRenderer
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Real player page and native VSync, with This Feeling; no Compose test clock. */
class PlayerVisualizerDeviceTest {
    @Test fun realSongRendersInPlayerAndReleasesWhenClosed() = runBlocking<Unit> {
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        assertTrue(app.packageName.endsWith(".verification"))
        val instrument = InstrumentationRegistry.getInstrumentation()
        val container = app.container
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val activity = AtomicReference<MainActivity>()
        var originalKeepScreenOn = false
        scenario.onActivity {
            activity.set(it)
            originalKeepScreenOn = it.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        }
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: Activity, state: Bundle?) { if (a is MainActivity) activity.set(a) }
            override fun onActivityStarted(a: Activity) {} ; override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {} ; override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, state: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        val store = ViewModelStore()
        var shown by mutableStateOf(true)
        var renderer: VisualizerPresetRenderer? = null
        var render: VisualizerRenderState? = null
        val vm = ViewModelProvider(store, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST") return PlayerViewModel(container) as T
            }
        })[PlayerViewModel::class.java]
        suspend fun waitFor(label: String, predicate: () -> Boolean) {
            try { withTimeout(30_000) { while (!predicate()) delay(50) } }
            catch (failure: TimeoutCancellationException) { throw AssertionError(label, failure) }
        }
        fun install() = instrument.runOnMainSync {
            activity.get().window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.get().setContent { LeiTheme(AppearanceSettings(blur = false)) {
                CompositionLocalProvider(LocalPlayerVisualizerObserver provides { state, preset -> render = state; renderer = preset }) {
                    if (shown) PlayerScreen(vm, { shown = false }, { container.playerController.toggle() })
                }
            } }
        }
        fun screenshot(name: String) {
            instrument.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(app.getExternalFilesDir(null), "player-visualizer-$name.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
        }
        var original: io.github.currencortex.music.data.settings.MusicSettings? = null
        var queue: io.github.currencortex.music.core.media.QueueSnapshot? = null
        var player: io.github.currencortex.music.core.media.PlayerState? = null
        try {
            withTimeout(120_000) { container.ready.await(); container.sessionRestored.await() }
            original = container.musicSettings.snapshot()
            queue = container.playbackQueue.state.value
            player = container.playerController.state.value
            val search = withTimeout(30_000) { container.musicRepository.search("THIS FEELING") }
            val song = (search as? AppResult.Success)?.value?.songs?.firstOrNull {
                it.name.equals("THIS FEELING", true) && it.artists.contains("my!lane", true)
            } ?: throw AssertionError("This Feeling / my!lane required; no substitute audio")
            assertEquals("Enable capture once through the isolated app's permission UI before this native test",
                android.content.pm.PackageManager.PERMISSION_GRANTED,
                androidx.core.content.ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO))
            container.musicSettings.setVisualizerEnabled(false)
            install()
            withContext(Dispatchers.Main) { container.playerController.playList(listOf(song), 0) }
            waitFor("Real playback did not start") { container.playerController.state.value.playing && container.audioSessionId.value > 0 }
            delay(500)
            assertNull("Capture remains opt-in", renderer)
            assertEquals(0, vm.audioAnalysis.frames.value.sampleRateHz)
            container.musicSettings.setVisualizerEnabled(true)
            val session = container.audioSessionId.value
            for (preset in VisualizerPreset.entries) {
                container.musicSettings.editVisualizerEffects { it.copy(presetId = preset) }
                waitFor("Player preset $preset must use real audio") { renderer?.preset == preset && render?.running == true && vm.audioAnalysis.frames.value.sampleRateHz > 0 }
                delay(3000)
                assertEquals(session, container.audioSessionId.value)
                assertTrue(vm.audioAnalysis.frames.value.timestampNanos > 0)
                assertNull(container.playerController.state.value.error)
                screenshot(preset.name.lowercase())
                Log.i("PlayerVisualizerTest", "preset=$preset source=${vm.audioAnalysis.frames.value.sampleRateHz} canvas=${render?.statistics?.value?.canvasDrawRate}")
            }
            withContext(Dispatchers.Main) { container.playerController.pause() }
            waitFor("Pause must release graphics") { render?.running == false && render?.preferredFps == 0f }
            assertEquals(0, vm.audioAnalysis.frames.value.sampleRateHz)
            withContext(Dispatchers.Main) { container.playerController.toggle().join() }
            waitFor("Resume must restore actual capture") { render?.running == true }
            instrument.runOnMainSync { activity.get().requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            delay(1500); install()
            waitFor("Landscape must render") { render?.running == true }
            delay(500); screenshot("landscape")
            instrument.runOnMainSync { activity.get().requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            delay(1500); install()
            waitFor("Portrait must render") { render?.running == true }
            val finalRender = requireNotNull(render)
            instrument.runOnMainSync { shown = false }
            waitFor("Closing player must release capture and clock") { renderer == null && !finalRender.running && finalRender.preferredFps == 0f }
            assertEquals(0, vm.audioAnalysis.frames.value.sampleRateHz)
            assertTrue("Closing the player page must preserve playback", container.playerController.state.value.playing)
            Log.i("PlayerVisualizerTest", "COMPLETE real player page / This Feeling")
        } finally {
            instrument.runOnMainSync {
                shown = false; store.clear()
                if (!originalKeepScreenOn) activity.get().window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            vm.audioAnalysis.close()
            withContext(Dispatchers.Main) {
                queue?.let { savedQueue ->
                    container.playerController.pause(); container.playbackQueue.restore(savedQueue)
                    val savedPlayer = requireNotNull(player)
                    if (savedQueue.current != null) container.playerController.load(savedPlayer.playing, savedPlayer.positionMs).join() else container.playerController.clear()
                }
            }
            original?.let { saved ->
                container.musicSettings.setVisualizerEnabled(saved.visualizerEnabled)
                container.musicSettings.editVisualizerEffects { saved.visualizerEffects }
                container.musicSettings.editVisualizerRender { saved.visualizerRender }
            }
            app.unregisterActivityLifecycleCallbacks(callbacks); scenario.close()
        }
    }
}
