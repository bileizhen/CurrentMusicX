package io.github.currencortex.music

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.core.visualizer.*
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.core.network.AppResult
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.data.visualizer.*
import io.github.currencortex.music.feature.player.PlayerViewModel
import io.github.currencortex.music.feature.visualizer.*
import io.github.currencortex.music.feature.visualizer.render.VisualizerPresetRenderer
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Actual Android frame clock: deliberately no Compose test rule or synthetic test clock. */
class VisualizerPresetDeviceTest {
    @Test fun orbitIntegration() = verify(10_000, presets = listOf(VisualizerPreset.ORBIT_SPECTRUM), modeMillis = 10_000)
    @Test fun allPresetsAndLifecycle() = verify(20_000, modeMillis = 30_000)
    @Test fun complexPresetFifteenMinuteStability() = verify(900_000, modeMillis = 30_000,
        longPreset = VisualizerPreset.from(InstrumentationRegistry.getArguments().getString("m2.longPreset") ?: "NEON_PULSE"))

    private fun verify(soakMillis: Long, presets: List<VisualizerPreset> = VisualizerPreset.entries, modeMillis: Long = 30_000, longPreset: VisualizerPreset = VisualizerPreset.NEON_PULSE) = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        assertTrue(context.packageName.endsWith(".verification"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val container = context.container
        container.ready.await(); container.sessionRestored.await()
        val original = container.musicSettings.snapshot()
        val originalQueue = container.playbackQueue.state.value
        val originalPlayer = container.playerController.state.value
        val query = InstrumentationRegistry.getArguments().getString("m2.song") ?: "THIS FEELING"
        val result = container.musicRepository.search(query)
        val matches = (result as? AppResult.Success)?.value?.songs
            ?: throw AssertionError("Song search failed: ${(result as? AppResult.Failure)?.kind}")
        // Phonk reference requested by the user; never substitute synthesized audio on failure.
        val artist = InstrumentationRegistry.getArguments().getString("m2.artist") ?: "MY!LANE"
        val selected = matches.firstOrNull { it.name.equals(query, true) && it.artists.contains(artist, true) }
            ?: throw AssertionError("No exact $query / $artist result; candidates: ${matches.map { it.name + " / " + it.artists }}")
        val song = selected // Search already contains the actual cover and track metadata.
        Log.i("VisualizerM2Test", "REAL SONG id=${song.id} title=${song.name} artists=${song.artists} duration=${song.durationMs}")
        val store = ViewModelStore()
        val render = VisualizerRenderState()
        var shown by mutableStateOf(true)
        var vm: PlayerViewModel? = null
        var renderer: VisualizerPresetRenderer? = null
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var initialWindowPreference = 0f
        val activityRef = AtomicReference<MainActivity>()
        scenario.onActivity { activityRef.set(it); initialWindowPreference = it.window.attributes.preferredRefreshRate }
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) { if (activity is MainActivity) activityRef.set(activity) }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        }
        context.registerActivityLifecycleCallbacks(callbacks)
        // ActivityScenario waits for main-loop idleness, which continuous VSync deliberately
        // does not provide. Post real Activity operations without waiting for drawing to idle.
        fun onActivity(action: (MainActivity) -> Unit) = instrumentation.runOnMainSync { action(activityRef.get()) }
        val directory = context.getExternalFilesDir(null)!!
        val records = File(directory, "visualizer-m2-performance.jsonl").apply { writeText("") }
        suspend fun waitFor(message: String, predicate: () -> Boolean) {
            try { withTimeout(30_000) { while (!predicate()) delay(50) } }
            catch (e: TimeoutCancellationException) { throw AssertionError(message, e) }
        }
        fun record(phase: String) {
            val s = render.statistics.value
            val memory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
            val record = JSONObject().apply {
                put("songId", song.id); put("songTitle", song.name); put("songArtists", song.artists);
                put("positionMs", container.playerController.state.value.positionMs);
                put("windowPreference", activityRef.get().window.attributes.preferredRefreshRate);
                put("interactive", context.getSystemService(android.os.PowerManager::class.java).isInteractive);
                put("preset", renderer?.preset?.name); put("shader", renderer?.shaderStatus);
                put("reduceMotion", renderer?.systemReduceMotion == true || renderer?.config?.reduceMotion == true);
                put("transientSequence", vm!!.audioAnalysis.frames.value.transientSequence);
                put("glitchEnvelope", renderer?.effects?.glitch);
                put("particles", renderer?.effects?.particleCount); put("effectEvents", renderer?.effects?.events);
                put("phase", phase); put("uptimeMs", SystemClock.uptimeMillis()); put("requested", s.requestedFps ?: "AUTO")
                put("preferenceFps", s.preferenceFps); put("effectiveTargetFps", s.effectiveTargetFps)
                put("displayHz", s.displayRefreshRate); put("clockHz", s.renderTickRate); put("canvasFps", s.canvasDrawRate)
                put("windowReportFps", s.measuredFrameRate ?: JSONObject.NULL); put("presentedFps", JSONObject.NULL)
                put("averageMs", s.frameIntervalAverageMs); put("p95Ms", s.frameIntervalP95Ms); put("p99Ms", s.frameIntervalP99Ms)
                put("windowJankPercent", s.jankPercentage); put("windowTotalMs", s.windowTotalAverageMs)
                put("renderJankPercent", s.renderJankPercentage); put("estimatedDroppedFrames", s.estimatedDroppedFrames)
                put("gpuMs", s.gpuAverageMs ?: JSONObject.NULL); put("gpuP95Ms", s.gpuP95Ms ?: JSONObject.NULL);
                put("canvasCpuMs", s.canvasCpuAverageMs); put("audioHz", s.audioCaptureRate)
                val audio = vm!!.audioAnalysis.frames.value
                put("rms", audio.rms); put("peakHz", audio.peakFrequencyHz); put("bass", audio.bass)
                put("spectrumMax", audio.spectrum.maxOrNull()); put("kickSequence", audio.kickSequence)
                put("audioAgeMs", s.audioFrameAgeMs ?: JSONObject.NULL); put("quality", s.qualityLevel.name)
                put("clockTicks", s.clockTicks); put("canvasDraws", s.canvasDraws); put("layerCompositions", s.compositions)
                put("windowFrames", s.windowFrames); put("windowReportsDropped", s.droppedWindowReports)
                put("limitation", s.limitation ?: JSONObject.NULL); put("running", s.running)
                put("cpuTimeMs", android.os.Process.getElapsedCpuTime()); put("pssKiB", memory.totalPss)
                put("javaHeapBytes", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())
                put("nativeHeapBytes", Debug.getNativeHeapAllocatedSize())
            }
            records.appendText(record.toString() + "\n")
            // Keep the instrumentation stream alive during native long-running rendering.
            instrumentation.sendStatus(2, Bundle().apply { putString("stream", "M2 $phase uptime=${SystemClock.uptimeMillis()}\n") })
            Log.i("VisualizerM2Test", record.toString())
        }
        fun shell(command: String) {
            instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
            }
        }
        fun screenshot(name: String) {
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(directory, "visualizer-m2-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        try {
            onActivity { activity ->
                vm = ViewModelProvider(store, object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST") return PlayerViewModel(container) as T
                    }
                })[PlayerViewModel::class.java]
                activity.setContent { LeiTheme(AppearanceSettings(blur = false)) {
                    top.yukonga.miuix.kmp.basic.Scaffold {
                        if (shown) AudioAnalysisDebugDialog(vm!!, true, { shown = false }, render, initialPreview = true, rendererObserver = { renderer = it })
                    }
                } }
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                    activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 902)
            }
            withTimeout(8_000) {
                while (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    val root = instrumentation.uiAutomation.rootInActiveWindow
                    val allow = root?.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/permission_allow_foreground_only_button")?.firstOrNull()
                        ?: root?.findAccessibilityNodeInfosByViewId("com.google.android.permissioncontroller:id/permission_allow_foreground_only_button")?.firstOrNull()
                    allow?.performAction(AccessibilityNodeInfo.ACTION_CLICK); delay(100)
                }
            }
            // Re-enter RESUMED after permission acquisition to refresh the production permission gate.
            scenario.moveToState(Lifecycle.State.CREATED); scenario.moveToState(Lifecycle.State.RESUMED)
            container.musicSettings.setVisualizerEnabled(true)
            container.musicSettings.editVisualizerRender { it.copy(automaticOptimization = false) }
            withContext(Dispatchers.Main) {
                assertEquals(PlayerMode.LOCAL, container.playerController.state.value.mode)
                container.playerController.playList(listOf(song), 0)
            }
            waitFor("Requested song did not play: check the existing source/account settings") {
                container.playerController.state.value.let { it.playing && it.song?.id == song.id }
            }
            waitFor("Real capture / VSync renderer did not start") { render.running && render.statistics.value.audioCaptureRate > 5 }
            record("song-ready")
            waitFor("The requested song must produce real waveform and spectrum energy") {
                vm!!.audioAnalysis.frames.value.let { it.rms > .005f && it.spectrum.any { value -> value > .02f } }
            }
            for (preset in presets) {
                Log.i("VisualizerM2Phase", "begin preset-${preset.name}")
                val previous = renderer
                val session = container.audioSessionId.value
                container.musicSettings.setVisualizerFrameRate(VisualizerFrameRate.FPS_120)
                container.musicSettings.editVisualizerEffects { it.copy(presetId = preset) }
                waitFor("Preset did not propagate") { renderer?.preset == preset && renderer?.coverLoaded == true }
                if (previous != null && previous.preset != preset) assertTrue("Old renderer must release resources", previous.closed)
                assertEquals("Preset switching must preserve the audio session", session, container.audioSessionId.value)
                withContext(Dispatchers.Main) { container.playerController.seek(10_000) }
                val phaseStart = SystemClock.elapsedRealtime()
                var lastRecord = phaseStart
                var realGlitchSeen = false
                while (SystemClock.elapsedRealtime() - phaseStart < modeMillis) {
                    // Observe the 75ms effect during playback, not after the song's transient section.
                    // This polls assertions only; production animation still uses withFrameNanos.
                    delay(10)
                    if (preset == VisualizerPreset.DARK_GLITCH && !realGlitchSeen && (renderer?.effects?.glitch ?: 0f) > 0f) {
                        realGlitchSeen = true
                        record("glitch-event"); screenshot("dark-glitch-event")
                    }
                    if (SystemClock.elapsedRealtime() - lastRecord >= 5_000) {
                        record("preset-${preset.name}"); lastRecord = SystemClock.elapsedRealtime()
                    }
                }
                record("preset-${preset.name}"); screenshot(preset.name.lowercase())
                if (preset == VisualizerPreset.DARK_GLITCH)
                    assertTrue("Real FFT transient must produce a bounded glitch during playback", realGlitchSeen)
                val stats = render.statistics.value
                assertTrue(stats.canvasDrawRate > 15)
                assertTrue(stats.canvasDrawRate <= stats.displayRefreshRate + 5)
                assertTrue(stats.audioCaptureRate in 5f..35f)
                if (!preset.circular && stats.qualityLevel.ordinal < 2) assertEquals("API 33+ enhancement must compile", "AGSL", renderer?.shaderStatus)
                assertNull("Window metrics cannot establish presentation FPS", stats.presentedFrameRate)
            }
            val beforeFallback = container.audioSessionId.value
            container.musicSettings.editVisualizerRender { it.copy(preferredQuality = VisualizerQuality.ECO) }
            waitFor("ECO must preserve basic Canvas") { render.statistics.value.qualityLevel == VisualizerQuality.ECO && renderer?.shaderStatus == "Canvas" }
            delay(5000); record("eco"); assertEquals(beforeFallback, container.audioSessionId.value)
            container.musicSettings.editVisualizerRender { it.copy(preferredQuality = VisualizerQuality.ULTRA) }
            assertTrue("The real song must generate kicks", vm!!.audioAnalysis.frames.value.kickSequence > 0)
            container.musicSettings.editVisualizerEffects { it.copy(presetId = longPreset) }
            waitFor("Long-run preset did not propagate") { renderer?.preset == longPreset }
            container.musicSettings.setVisualizerFrameRate(VisualizerFrameRate.FPS_120)
            container.musicSettings.editVisualizerRender { it.copy(automaticOptimization = true) }
            val soakStart = SystemClock.elapsedRealtime()
            Log.i("VisualizerM2Phase", "begin soak")
            val initialCompositions = render.statistics.value.compositions
            var energizedSamples = 0
            while (SystemClock.elapsedRealtime() - soakStart < soakMillis) {
                if (container.playerController.state.value.positionMs > song.durationMs - 10_000) {
                    withContext(Dispatchers.Main) { container.playerController.seek(10_000) }
                }
                delay(5_000); record("soak")
                if (vm!!.audioAnalysis.frames.value.rms > .005f) energizedSamples++
                assertTrue("Render loop unexpectedly stopped during playback", render.running)
                assertTrue("Audio analysis stalled", (render.statistics.value.audioFrameAgeMs ?: 10000f) < 500)
            }
            assertTrue("The soak must contain energetic audio, not silent callbacks", energizedSamples > 0)
            assertTrue("Drawing should not recompose the layer every frame",
                render.statistics.value.compositions - initialCompositions < 500)
            screenshot("soak-final")
            Log.i("VisualizerM2Phase", "begin lifecycle")
            val beforeSeek = vm!!.audioAnalysis.frames.value.generation
            withContext(Dispatchers.Main) { container.playerController.seek(5000) }
            waitFor("Seek must reset the capture generation") { vm!!.audioAnalysis.frames.value.generation > beforeSeek }
            record("seek")
            withContext(Dispatchers.Main) { container.playerController.pause() }
            waitFor("Pause must finish its decay and release preference") { !render.running && render.preferredFps == 0f }
            assertEquals("Paused GPU resources must be released", "Canvas", renderer?.shaderStatus)
            assertFalse(renderer?.active ?: true)
            record("paused")
            withContext(Dispatchers.Main) { container.playerController.toggle().join() }
            waitFor("Resume must restart the renderer") { render.running }
            onActivity { it.moveTaskToBack(true) }
            waitFor("Background must stop rendering") { !render.running && render.preferredFps == 0f }
            record("background")
            val stopped = render.statistics.value.clockTicks
            delay(1000); assertEquals(stopped, render.statistics.value.clockTicks)
            assertEquals(0, vm!!.audioAnalysis.frames.value.sampleRateHz)
            if (!context.getSystemService(android.app.KeyguardManager::class.java).isDeviceSecure) {
                try {
                    shell("input keyevent KEYCODE_POWER")
                    waitFor("Screen off must keep graphics released") { !context.getSystemService(android.os.PowerManager::class.java).isInteractive && !render.running && render.preferredFps == 0f }
                    delay(1000); record("screen-off")
                    assertTrue(withContext(Dispatchers.Main) { container.playerController.connect().isPlaying })
                } finally {
                    shell("input keyevent KEYCODE_WAKEUP")
                    shell("wm dismiss-keyguard")
                }
            } else record("secure-lock-not-executed")
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            waitFor("Returning foreground must restore capture") { render.running }
            onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            delay(1500)
            onActivity { activity -> activity.setContent { LeiTheme(AppearanceSettings(blur = false)) {
                top.yukonga.miuix.kmp.basic.Scaffold { if (shown) AudioAnalysisDebugDialog(vm!!, true, { shown = false }, render, initialPreview = true, rendererObserver = { renderer = it }) }
            } } }
            waitFor("Landscape must restore rendering") { render.running }
            screenshot("landscape")
            // Real recreation is exercised below; set the portrait layout back for a readable screenshot.
            onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            delay(1500)
            val oldActivity = activityRef.get()
            onActivity { it.recreate() }
            waitFor("Activity recreation did not complete") { activityRef.get() !== oldActivity && activityRef.get().lifecycle.currentState == Lifecycle.State.RESUMED }
            onActivity { activity -> activity.setContent { LeiTheme(AppearanceSettings(blur = false)) {
                top.yukonga.miuix.kmp.basic.Scaffold { if (shown) AudioAnalysisDebugDialog(vm!!, true, { shown = false }, render, initialPreview = true, rendererObserver = { renderer = it }) }
            } } }
            waitFor("Recreation must restore exactly one renderer") { render.running }
            screenshot("recreated")
            onActivity { shown = false }
            waitFor("Closing the diagnostics must release drawing and preference") { !render.running && render.preferredFps == 0f }
            assertNull(renderer)
            onActivity { assertEquals("Window preference must be restored", initialWindowPreference, it.window.attributes.preferredRefreshRate, 0f) }
            record("closed")
            val closedTicks = render.statistics.value.clockTicks
            delay(1000); assertEquals(closedTicks, render.statistics.value.clockTicks)
            assertTrue(withContext(Dispatchers.Main) { container.playerController.connect().isPlaying })
            assertEquals(0, vm!!.audioAnalysis.frames.value.sampleRateHz)
            Log.i("VisualizerM2Test", "COMPLETE durationMs=${SystemClock.elapsedRealtime() - soakStart} records=${records.name}")
        } finally {
            onActivity { shown = false; store.clear() }
            withContext(Dispatchers.Main) {
                container.playerController.pause()
                container.playbackQueue.restore(originalQueue)
                if (originalQueue.current != null) container.playerController.load(originalPlayer.playing, originalPlayer.positionMs).join()
                else container.playerController.clear()
            }
            container.musicSettings.setVisualizerEnabled(original.visualizerEnabled)
            container.musicSettings.editVisualizerEffects { original.visualizerEffects }
            container.musicSettings.editVisualizerRender { original.visualizerRender }
            context.unregisterActivityLifecycleCallbacks(callbacks)
            scenario.close()
        }
    }

}
