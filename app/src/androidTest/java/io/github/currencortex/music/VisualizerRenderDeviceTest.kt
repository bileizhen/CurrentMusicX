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
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.data.visualizer.*
import io.github.currencortex.music.feature.player.PlayerViewModel
import io.github.currencortex.music.feature.visualizer.*
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.*

/** Actual Android frame clock: deliberately no Compose test rule or synthetic test clock. */
class VisualizerRenderDeviceTest {
    private val controls = object : ExternalPlayback {
        override fun play(playing: Boolean) {}; override fun seek(position: Long) {}
        override fun next() {}; override fun previous() {}; override fun request(song: Song) {}; override fun stop() {}
    }
    @Test fun frameRatesAndLifecycleSmoke() = verify(20_000, modeMillis = 20_000)
    @Test fun realAudioFrameRatesLifecycleAndFifteenMinuteStability() = verify(900_000)

    private fun verify(soakMillis: Long, modeMillis: Long = 10_000) = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        assertTrue(context.packageName.endsWith(".verification"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val container = context.container
        container.ready.await(); container.sessionRestored.await()
        val original = container.musicSettings.snapshot()
        val store = ViewModelStore()
        val render = VisualizerRenderState()
        var shown by mutableStateOf(true)
        var vm: PlayerViewModel? = null
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val activityRef = AtomicReference<MainActivity>()
        scenario.onActivity { activityRef.set(it) }
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
        val records = File(directory, "visualizer-m1-performance.jsonl").apply { writeText("") }
        val audioFile = wav(File(context.cacheDir, "visualizer-m1-stability.wav"))
        val server = FixtureServer(audioFile)
        val url = server.url
        suspend fun waitFor(message: String, predicate: () -> Boolean) {
            try { withTimeout(10_000) { while (!predicate()) delay(50) } }
            catch (e: TimeoutCancellationException) { throw AssertionError(message, e) }
        }
        fun record(phase: String) {
            val s = render.statistics.value
            val memory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
            val record = JSONObject().apply {
                put("phase", phase); put("uptimeMs", SystemClock.uptimeMillis()); put("requested", s.requestedFps ?: "AUTO")
                put("preferenceFps", s.preferenceFps); put("effectiveTargetFps", s.effectiveTargetFps)
                put("displayHz", s.displayRefreshRate); put("clockHz", s.renderTickRate); put("canvasFps", s.canvasDrawRate)
                put("windowReportFps", s.measuredFrameRate ?: JSONObject.NULL); put("presentedFps", JSONObject.NULL)
                put("averageMs", s.frameIntervalAverageMs); put("p95Ms", s.frameIntervalP95Ms); put("p99Ms", s.frameIntervalP99Ms)
                put("windowJankPercent", s.jankPercentage); put("windowTotalMs", s.windowTotalAverageMs)
                put("renderJankPercent", s.renderJankPercentage); put("estimatedDroppedFrames", s.estimatedDroppedFrames)
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
            Log.i("VisualizerM1Test", record.toString())
        }
        fun screenshot(name: String) {
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(directory, "visualizer-m1-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
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
                        if (shown) AudioAnalysisDebugDialog(vm!!, true, { shown = false }, render)
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
                assertTrue(container.playerController.beginExternal(PlayerMode.ROOM, controls))
                container.playerController.roomTrack(Song(-95001, "M1 实时频谱稳定性", durationMs = 1_080_000), url, 0, true)
            }
            waitFor("Real capture / VSync renderer did not start") { render.running && render.statistics.value.audioCaptureRate > 5 }
            record("fixture-ready")
            waitFor("The performance fixture must produce real waveform and spectrum energy") {
                vm!!.audioAnalysis.frames.value.let { it.rms > .005f && it.spectrum.any { value -> value > .02f } }
            }
            for (fps in VisualizerFrameRate.entries) {
                Log.i("VisualizerM1Phase", "begin mode-${fps.name}")
                container.musicSettings.setVisualizerFrameRate(fps)
                waitFor("FPS preference did not propagate") { render.statistics.value.requestedFps == fps.targetFps }
                delay(modeMillis)
                record("mode-${fps.name}"); screenshot(fps.name.lowercase())
                val stats = render.statistics.value
                assertTrue(stats.canvasDrawRate > 15)
                assertTrue(stats.canvasDrawRate <= stats.displayRefreshRate + 5)
                assertTrue(stats.audioCaptureRate in 5f..35f)
                assertNull("Window metrics cannot establish presentation FPS", stats.presentedFrameRate)
            }
            assertTrue("The real fixture must generate kicks", vm!!.audioAnalysis.frames.value.kickSequence > 0)
            container.musicSettings.setVisualizerFrameRate(VisualizerFrameRate.AUTO)
            container.musicSettings.editVisualizerRender { it.copy(automaticOptimization = true) }
            val soakStart = SystemClock.elapsedRealtime()
            Log.i("VisualizerM1Phase", "begin soak")
            val initialCompositions = render.statistics.value.compositions
            var energizedSamples = 0
            while (SystemClock.elapsedRealtime() - soakStart < soakMillis) {
                delay(5_000); record("soak")
                if (vm!!.audioAnalysis.frames.value.rms > .005f) energizedSamples++
                assertTrue("Render loop unexpectedly stopped during playback", render.running)
                assertTrue("Audio analysis stalled", (render.statistics.value.audioFrameAgeMs ?: 10000f) < 500)
            }
            assertTrue("The soak must contain energetic audio, not silent callbacks", energizedSamples > 0)
            assertTrue("Drawing should not recompose the layer every frame",
                render.statistics.value.compositions - initialCompositions < 500)
            screenshot("soak-final")
            Log.i("VisualizerM1Phase", "begin lifecycle")
            val beforeSeek = vm!!.audioAnalysis.frames.value.generation
            withContext(Dispatchers.Main) { container.playerController.roomCorrection(5000, true, 1f, true) }
            waitFor("Seek must reset the capture generation") { vm!!.audioAnalysis.frames.value.generation > beforeSeek }
            record("seek")
            val beforeTrack = vm!!.audioAnalysis.frames.value.generation
            withContext(Dispatchers.Main) { container.playerController.roomTrack(Song(-95002, "M1 切歌", durationMs = 1_080_000), url, 0, true) }
            waitFor("Track change must clear analysis history") { vm!!.audioAnalysis.frames.value.generation > beforeTrack }
            record("track")
            withContext(Dispatchers.Main) { container.playerController.roomCorrection(0, false, 1f, false) }
            waitFor("Pause must finish its decay and release preference") { !render.running && render.preferredFps == 0f }
            record("paused")
            withContext(Dispatchers.Main) { container.playerController.roomCorrection(0, false, 1f, true) }
            waitFor("Resume must restart the renderer") { render.running }
            onActivity { it.moveTaskToBack(true) }
            waitFor("Background must stop rendering") { !render.running && render.preferredFps == 0f }
            record("background")
            val stopped = render.statistics.value.clockTicks
            delay(1000); assertEquals(stopped, render.statistics.value.clockTicks)
            assertEquals(0, vm!!.audioAnalysis.frames.value.sampleRateHz)
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            waitFor("Returning foreground must restore capture") { render.running }
            onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            delay(1500)
            onActivity { activity -> activity.setContent { LeiTheme(AppearanceSettings(blur = false)) {
                top.yukonga.miuix.kmp.basic.Scaffold { if (shown) AudioAnalysisDebugDialog(vm!!, true, { shown = false }, render) }
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
                top.yukonga.miuix.kmp.basic.Scaffold { if (shown) AudioAnalysisDebugDialog(vm!!, true, { shown = false }, render) }
            } } }
            waitFor("Recreation must restore exactly one renderer") { render.running }
            screenshot("recreated")
            onActivity { shown = false }
            waitFor("Closing the diagnostics must release drawing and preference") { !render.running && render.preferredFps == 0f }
            record("closed")
            val closedTicks = render.statistics.value.clockTicks
            delay(1000); assertEquals(closedTicks, render.statistics.value.clockTicks)
            assertTrue(withContext(Dispatchers.Main) { container.playerController.connect().isPlaying })
            assertEquals(0, vm!!.audioAnalysis.frames.value.sampleRateHz)
            Log.i("VisualizerM1Test", "COMPLETE durationMs=${SystemClock.elapsedRealtime() - soakStart} records=${records.name}")
        } finally {
            onActivity { shown = false; store.clear() }
            withContext(Dispatchers.Main) {
                container.playerController.roomTrack(null, null, 0, false)
                container.playerController.endExternal(controls); container.playerController.pause()
            }
            container.musicSettings.setVisualizerEnabled(original.visualizerEnabled)
            container.musicSettings.editVisualizerRender { original.visualizerRender }
            context.unregisterActivityLifecycleCallbacks(callbacks)
            scenario.close(); server.close(); audioFile.delete()
        }
    }

    /** 18 minutes at the same 48 kHz as M0, served with bounded buffers instead of a huge response copy. */
    private fun wav(file: File): File {
        val rate = 48000; val samples = rate * 1080
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16).put("data".toByteArray()).putInt(samples * 2)
        // All oscillator/envelope phases repeat every four seconds. Calculate that cycle once,
        // then stream copies; generation must not spend minutes computing millions of trig calls.
        val cycle = ByteBuffer.allocate(rate * 4 * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(rate * 4) { i ->
            val t = i.toDouble() / rate
            val kick = exp(-(t % .5) / .08)
            // Use M0's proven amplitudes: AS_PLAYED capture is volume dependent and a quiet
            // mixed carrier can quantize below the 8-bit noise floor at this device's volume.
            val value = if (t % 4 < 2) .85 * sin(2 * PI * 937.5 * t) * Short.MAX_VALUE
                else (.06 + .8 * kick) * sin(2 * PI * 93.75 * t) * Short.MAX_VALUE
            cycle.putShort(value.toInt().toShort())
        }
        file.outputStream().buffered().use { stream ->
            stream.write(header.array())
            repeat(1080 / 4) { stream.write(cycle.array()) }
        }
        return file
    }

    /** Test-only localhost WAV server; range support also exercises real Media3 seeking. */
    private class FixtureServer(private val file: File) : AutoCloseable {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val workers = Executors.newCachedThreadPool { task -> Thread(task, "M1FixtureHttp").apply { isDaemon = true } }
        val url = "http://127.0.0.1:${socket.localPort}/fixture.wav"
        init {
            workers.execute {
                try { while (!socket.isClosed) {
                    val client = socket.accept()
                    workers.execute { client.use {
                        try {
                            it.soTimeout = 10_000
                            val reader = it.getInputStream().bufferedReader(Charsets.US_ASCII)
                            val request = reader.readLine() ?: return@use
                            if (!request.startsWith("GET /fixture.wav ")) return@use
                            var start = 0L
                            var range = false
                            while (true) {
                                val line = reader.readLine() ?: return@use
                                if (line.isEmpty()) break
                                if (line.startsWith("Range: bytes=", ignoreCase = true)) {
                                    start = line.substringAfter('=').substringBefore('-').toLongOrNull() ?: 0L
                                    range = true
                                }
                            }
                            start = start.coerceIn(0, file.length() - 1)
                            val output = it.getOutputStream().buffered(16 * 1024)
                            val headers = buildString {
                                append("HTTP/1.1 ${if (range) "206 Partial Content" else "200 OK"}\r\n")
                                append("Content-Type: audio/wav\r\nContent-Length: ${file.length() - start}\r\nAccept-Ranges: bytes\r\n")
                                if (range) append("Content-Range: bytes $start-${file.length() - 1}/${file.length()}\r\n")
                                append("Connection: close\r\n\r\n")
                            }
                            output.write(headers.toByteArray(Charsets.US_ASCII))
                            RandomAccessFile(file, "r").use { audio ->
                                audio.seek(start)
                                val bytes = ByteArray(16 * 1024)
                                while (!Thread.currentThread().isInterrupted) {
                                    val count = audio.read(bytes)
                                    if (count < 0) break
                                    output.write(bytes, 0, count)
                                }
                            }
                            output.flush()
                        } catch (_: IOException) { /* Expected disconnect when the player seeks or closes. */ }
                    } }
                } } catch (_: IOException) { /* Closing the listener ends accept. */ }
            }
        }
        override fun close() { socket.close(); workers.shutdownNow() }
    }
}
