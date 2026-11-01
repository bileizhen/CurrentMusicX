package io.github.currencortex.music

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.feature.recognition.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Opt-in real playback -> system mix -> local fingerprint -> official endpoint tests. */
class RecognitionStreamingDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>().also {
        check(it.packageName.endsWith(".verification"))
    }

    @Test fun controlledPlaybackMatchesBeforeCaptureFinishesInForeground(): Unit = runBlocking {
        runPlayback("foreground", background = false)
    }

    @Test fun controlledPlaybackMatchesBeforeCaptureFinishesAfterReturningHome(): Unit = runBlocking {
        runPlayback("background", background = true)
    }

    private suspend fun runPlayback(mode: String, background: Boolean) {
        assumeTrue("Enable recognitionStreamingLive explicitly to play the known fixture and call NetEase",
            InstrumentationRegistry.getArguments().getString("recognitionStreamingLive") == "true")
        val directory = checkNotNull(context.getExternalFilesDir(null)).canonicalFile
        val output = File(directory, "recognition-stream-$mode.json")
        val permissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!permissionGranted) output.writeText(buildJsonObject {
            put("status", "SKIPPED"); put("mode", mode)
            put("message", "RECORD_AUDIO must already be granted; the test does not grant permissions")
        }.toString())
        assumeTrue("RECORD_AUDIO must already be granted", permissionGranted)

        val input = File(directory, "recognition-test.pcm").canonicalFile
        check(input.parentFile == directory && input.length() == 96_000L) {
            "Push the known six-second 8 kHz mono PCM16 fixture as recognition-test.pcm"
        }
        val bytes = input.readBytes()
        val pcm = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm)
        bytes.fill(0)

        lateinit var activity: Activity
        compose.setContent { val current = LocalActivity.current!!; SideEffect { activity = current } }
        compose.waitForIdle()
        withTimeout(5000) {
            while (!activity.hasWindowFocus() || withContext(Dispatchers.Main) {
                    !(activity as LifecycleOwner).lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                }) delay(25)
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val encoder = NeteaseFingerprint(context)
        val repository = NeteaseRecognitionRepository()
        val realRecorder = SystemMixRecorder()
        val capturedWindows = CopyOnWriteArrayList<Int>()
        val progressSeconds = CopyOnWriteArrayList<Int>()
        val requests = CopyOnWriteArrayList<Pair<Int, Double>>()
        val sessions = mutableSetOf<String>()
        val captureReleased = AtomicBoolean(false)
        val captures = AtomicInteger(0)
        val recorder = object : RecognitionRecorder {
            override suspend fun record(stop: AtomicBoolean, progress: (Int, Float) -> Unit) =
                recordStreaming(stop, progress) { }

            override suspend fun recordStreaming(stop: AtomicBoolean, progress: (Int, Float) -> Unit,
                onWindow: (ShortArray) -> Unit): ShortArray {
                captures.incrementAndGet()
                try {
                    return realRecorder.recordStreaming(stop, { seconds, level ->
                        if (progressSeconds.lastOrNull() != seconds) progressSeconds += seconds
                        progress(seconds, level)
                    }, { window ->
                        capturedWindows += window.size
                        onWindow(window)
                    })
                } finally { captureReleased.set(true) }
            }
        }
        val controller = RecognitionController(scope, recorder, encoder::encode,
            { _, _ -> error("Streaming recognition must use its shared session") }, RecognitionAudioSource.SYSTEM,
            recognizeInSession = { fingerprint, duration, session, round ->
                sessions += session
                requests += round to duration
                repository.recognize(fingerprint, duration, session, round)
            })
        val started = SystemClock.elapsedRealtime()
        val transitions = mutableListOf<JsonObject>()
        var maximumLevel = 0f
        var lastKey: Triple<RecognitionPhase, RecognitionStep, Int>? = null
        val observer = scope.launch {
            controller.state.collect { state ->
                maximumLevel = maxOf(maximumLevel, state.level)
                val key = Triple(state.phase, state.step, state.round)
                if (key != lastKey) {
                    lastKey = key
                    transitions += buildJsonObject {
                        put("afterMillis", SystemClock.elapsedRealtime() - started)
                        put("phase", state.phase.name); put("step", state.step.name)
                        put("round", state.round); put("seconds", state.seconds)
                    }
                }
            }
        }
        var player: AudioTrack? = null
        var failureClass = ""
        var failureMessage = ""
        var activityState = "UNKNOWN"
        try {
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).apply {
                    if (Build.VERSION.SDK_INT >= 29) setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL)
                }.build()
            player = AudioTrack.Builder().setAudioAttributes(attributes)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(8000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
            check(player.state == AudioTrack.STATE_NO_STATIC_DATA)
            check(player.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING) == pcm.size)
            check(player.setLoopPoints(0, pcm.size, -1) == AudioTrack.SUCCESS)
            player.play()
            delay(500)
            withContext(Dispatchers.Main) { controller.start() }
            // Start capture visibly, then exercise encoding and network matching while at HOME.
            if (background) {
                withTimeout(5000) { while (progressSeconds.isEmpty() && controller.state.value.busy) delay(10) }
                withContext(Dispatchers.Main) {
                    activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                withTimeout(5000) {
                    while (activity.hasWindowFocus() || withContext(Dispatchers.Main) {
                            (activity as LifecycleOwner).lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                        }) delay(25)
                }
            }
            activityState = withContext(Dispatchers.Main) { (activity as LifecycleOwner).lifecycle.currentState.name }
            withTimeout(50_000) { while (controller.state.value.busy) delay(25) }
            val result = controller.state.value
            assertEquals("$mode recognition failed at ${result.step}, round ${result.round}: ${result.message}",
                RecognitionPhase.MATCHED, result.phase)
            assertTrue("Real captured fixture must match Rise to the Dark", result.songs.any { it.id == 29755418L })
            assertTrue("Recognition should stop capture before its 15-second limit", result.seconds in 2..14)
            assertEquals("Recognition windows must share one continuous recording", 1, captures.get())
            assertTrue("Capture must be released before publishing a match", captureReleased.get())
            assertTrue("Actual nonzero waveform must have been captured", maximumLevel > 0f)
            assertTrue("At least one real cumulative window is required", capturedWindows.isNotEmpty())
            assertEquals(16_000, capturedWindows.first())
            assertTrue(progressSeconds.zipWithNext().all { (previous, next) -> next >= previous })
            assertTrue(capturedWindows.zipWithNext().all { (previous, next) -> next > previous })
            assertEquals("All network requests must use the attempt's shared session", 1, sessions.size)
            assertEquals((1..requests.size).toList(), requests.map { it.first })
            if (background) assertFalse(activity.hasWindowFocus()) else assertTrue(activity.hasWindowFocus())
        } catch (failure: Throwable) {
            failureClass = failure.javaClass.simpleName
            failureMessage = failure.message.orEmpty().take(300)
            throw failure
        } finally {
            withContext(NonCancellable + Dispatchers.Main) {
                val result = controller.state.value
                observer.cancelAndJoin()
                val audio = context.getSystemService(AudioManager::class.java)
                try { output.writeText(buildJsonObject {
                    put("status", if (failureClass.isEmpty() && result.phase == RecognitionPhase.MATCHED) "MATCHED" else "FAILED")
                    put("mode", mode); put("activityLifecycle", activityState)
                    put("fixtureRateHz", 8000); put("fingerprintRateHz", 8000)
                    put("reportedOutputRateHz", audio.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE).orEmpty())
                    put("nativeOutputRateHz", AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC))
                    put("phase", result.phase.name); put("step", result.step.name)
                    put("round", result.round); put("capturedSeconds", result.seconds)
                    put("captureStarts", captures.get()); put("captureReleased", captureReleased.get())
                    put("maximumLevel", maximumLevel); put("sessionCount", sessions.size)
                    put("totalMillis", SystemClock.elapsedRealtime() - started)
                    put("capturedWindowSamples", JsonArray(capturedWindows.map(::JsonPrimitive)))
                    put("requests", JsonArray(requests.map { (round, duration) -> buildJsonObject {
                        put("round", round); put("duration", duration)
                    } }))
                    put("transitions", JsonArray(transitions))
                    put("songs", JsonArray(result.songs.map { song -> buildJsonObject {
                        put("id", song.id); put("name", song.name)
                    } }))
                    put("message", result.message.orEmpty())
                    put("failureClass", failureClass); put("failureMessage", failureMessage)
                }.toString()) } finally {
                    controller.cancel()
                    scope.coroutineContext[Job]?.cancelAndJoin()
                    runCatching { player?.stop() }
                    try { player?.release() } finally { pcm.fill(0) }
                }
            }
        }
    }
}
