package io.github.currencortex.music

import android.Manifest
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import android.util.Log
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.core.visualizer.*
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer

/** Real PCM fixture played by MusicService's existing ExoPlayer, never a test-side player. */
class VisualizerCaptureTest {
    @get:Rule val compose = createComposeRule()
    private val controls = object : ExternalPlayback {
        override fun play(playing: Boolean) {}
        override fun seek(position: Long) {}
        override fun next() {}
        override fun previous() {}
        override fun request(song: Song) {}
        override fun stop() {}
    }

    @Test fun serviceSessionHasRealFrequencyResponseAndCaptureReleaseKeepsPlayback() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        // Run with -PisolatedDebug=true: never grant or change production app permissions/data.
        assertTrue("Use the isolated debug package for real playback tests", context.packageName.endsWith(".verification"))
        var activity: android.app.Activity? = null
        var debugEngine by mutableStateOf<AudioAnalysisEngine?>(null)
        compose.setContent {
            val current = LocalActivity.current
            SideEffect { activity = current }
            debugEngine?.let { engine ->
                io.github.currencortex.music.feature.visualizer.AudioDebugCanvas(engine,
                    Modifier.requiredSize(320.dp, 160.dp).testTag("real_spectrum"))
            }
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            compose.runOnIdle { activity!!.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 901) }
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            withTimeout(8_000) {
                while (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    val root = automation.rootInActiveWindow
                    val allow = root?.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/permission_allow_foreground_only_button")
                        ?.firstOrNull() ?: root?.findAccessibilityNodeInfosByViewId("com.google.android.permissioncontroller:id/permission_allow_foreground_only_button")?.firstOrNull()
                    allow?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    delay(100)
                }
            }
        }
        val container = context.container
        container.ready.await(); container.sessionRestored.await()
        val low = wav(File(context.cacheDir, "visualizer-low.wav"), 937.5, false)
        val high = wav(File(context.cacheDir, "visualizer-high.wav"), 6000.0, false)
        val beat = wav(File(context.cacheDir, "visualizer-beat.wav"), 93.75, true)
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val file = when (request.requestUrl?.encodedPath) {
                        "/low.wav" -> low; "/high.wav" -> high; "/beat.wav" -> beat
                        else -> return MockResponse().setResponseCode(404)
                    }
                    return MockResponse().setHeader("Content-Type", "audio/wav").setBody(Buffer().write(file.readBytes()))
                }
            }
            start()
        }
        val lowUrl = server.url("/low.wav").toString()
        val highUrl = server.url("/high.wav").toString()
        val beatUrl = server.url("/beat.wav").toString()
        val controller = container.playerController
        suspend fun awaitActualOutput() {
            // playRequested / a previously published session ID can become available before
            // the replacement AudioTrack starts. Attach only after actual service output.
            withTimeout(10_000) {
                while (!withContext(Dispatchers.Main) {
                    controller.connect().let {
                        it.isPlaying && it.currentPosition >= 300 &&
                            it.currentMediaItem?.mediaId == controller.state.value.song?.id?.toString()
                    }
                }) delay(50)
            }
        }
        try {
            withContext(Dispatchers.Main) {
                assertTrue(controller.beginExternal(PlayerMode.ROOM, controls))
                controller.roomTrack(Song(-91001, "M0 tone", durationMs = 12000), lowUrl, 0, true)
            }
            awaitActualOutput()
            val id = withTimeout(10_000) { container.audioSessionId.first { it > 0 } }
            withTimeout(10_000) { controller.state.first { it.playing } }
            val source = VisualizerCaptureSource()
            suspend fun measure(expectedHz: Float, count: Int): List<AudioAnalysisFrame> {
                val analyzer = AudioSignalAnalyzer()
                val packets = withTimeout(12_000) { source.frames(container.audioSessionId.value).take(count).toList() }
                val frames = packets.map { analyzer.analyze(it) }
                assertTrue("No waveform energy: check device capture support / playback volume", frames.any { it.rms > .005f })
                val peaks = frames.filter { it.rms > .005f }.map { it.peakFrequencyHz }.sorted()
                val median = peaks[peaks.size / 2]
                assertEquals(expectedHz, median, frames.last().sampleRateHz.toFloat() / frames.last().captureSize * 2)
                val elapsed = (packets.last().timestampNanos - packets.first().timestampNanos) / 1e9
                Log.i("VisualizerM0Test", "session=$id expectedHz=$expectedHz medianHz=$median rateHz=${(packets.size - 1) / elapsed} sampleRate=${frames.last().sampleRateHz} size=${frames.last().captureSize} maxRms=${frames.maxOf { it.rms }} kicks=${frames.count { it.kick }}")
                return frames
            }
            measure(937.5f, 45)
            val positionAfterRelease = withContext(Dispatchers.Main) { controller.connect().currentPosition }
            // Waiting for player state progression proves capture teardown does not pause the service.
            withTimeout(5_000) { controller.state.first { it.playing && it.positionMs > positionAfterRelease + 100 } }
            withContext(Dispatchers.Main) { controller.roomTrack(Song(-91002, "M0 treble", durationMs = 12000), highUrl, 0, true) }
            awaitActualOutput()
            measure(6000f, 30)
            withContext(Dispatchers.Main) { controller.roomTrack(Song(-91003, "M0 kick", durationMs = 12000), beatUrl, 0, true) }
            awaitActualOutput()
            val kicks = measure(93.75f, 90)
            assertTrue("Bass impacts must trigger after adaptive warm-up", kicks.count { it.kick } >= 2)
            assertTrue(kicks.any { it.beatPulse > .9f })
            val engine = AudioAnalysisEngine(this, source)
            try {
                engine.request(CaptureRequest(true, true, true, true, true, container.audioSessionId.value))
                withTimeout(5_000) { engine.status.first { it == CaptureStatus.CAPTURING } }
                // Compose's test frame clock is synthetic; use it only for pixel assertions.
                // Real FPS is measured in VisualizerPerformanceTest without a Compose test clock.
                compose.mainClock.autoAdvance = false
                compose.runOnIdle { debugEngine = engine }
                compose.mainClock.advanceTimeBy(240)
                compose.waitUntil(5_000) { engine.frames.value.spectrum.any { it > .02f } }
                val image = compose.onNodeWithTag("real_spectrum").captureToImage()
                val pixels = image.toPixelMap()
                var painted = 0
                for (y in 0 until pixels.height step 2) for (x in 0 until pixels.width step 2) {
                    val color = pixels[x, y]
                    if (color.blue > .5f && color.red < .7f) painted++
                }
                assertTrue("Canvas must paint the actual spectrum / waveform", painted > 20)
                File(context.getExternalFilesDir(null), "visualizer-m0-real-spectrum.png").outputStream().use {
                    image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                engine.request(CaptureRequest(true, false, true, true, true, container.audioSessionId.value))
                withTimeout(5_000) { engine.status.first { it == CaptureStatus.HIDDEN } }
                assertEquals(0, engine.frames.value.sampleRateHz)
                assertTrue(withContext(Dispatchers.Main) { controller.connect().isPlaying })
            } finally { engine.close(); compose.mainClock.autoAdvance = true }
        } finally {
            withContext(Dispatchers.Main) {
                controller.roomTrack(null, null, 0, false)
                controller.endExternal(controls)
                controller.pause()
            }
            server.shutdown(); low.delete(); high.delete(); beat.delete()
        }
    }

    private fun wav(file: File, frequency: Double, kicks: Boolean): File {
        val rate = 48000
        val samples = rate * 12
        val buffer = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()); buffer.putInt(36 + samples * 2); buffer.put("WAVEfmt ".toByteArray())
        buffer.putInt(16); buffer.putShort(1); buffer.putShort(1); buffer.putInt(rate); buffer.putInt(rate * 2)
        buffer.putShort(2); buffer.putShort(16); buffer.put("data".toByteArray()); buffer.putInt(samples * 2)
        repeat(samples) { i ->
            val time = i.toDouble() / rate
            val amplitude = if (kicks) .06 + .8 * exp(-(time % .5) / .08) else .85
            buffer.putShort((sin(2 * PI * frequency * time) * amplitude * Short.MAX_VALUE).toInt().toShort())
        }
        file.writeBytes(buffer.array())
        return file
    }
}
