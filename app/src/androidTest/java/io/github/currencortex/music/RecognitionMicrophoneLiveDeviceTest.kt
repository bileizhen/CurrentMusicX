package io.github.currencortex.music

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
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
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** An external speaker supplies the known song; this test never starts or changes phone playback. */
class RecognitionMicrophoneLiveDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>().also {
        check(it.packageName.endsWith(".verification"))
    }

    @Test fun externalKnownSongMatchesThroughTheRealMicrophone(): Unit = runBlocking {
        assumeTrue("An external speaker must play the known fixture; opt in with recognitionMicrophoneLive=true",
            InstrumentationRegistry.getArguments().getString("recognitionMicrophoneLive") == "true")
        val directory = checkNotNull(context.getExternalFilesDir(null)).canonicalFile
        val output = File(directory, "recognition-microphone-live.json")
        val permissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!permissionGranted) output.writeText(buildJsonObject {
            put("status", "SKIPPED"); put("source", "microphone-acoustic")
            put("message", "RECORD_AUDIO must already be granted; the test does not grant permissions")
        }.toString())
        assumeTrue("RECORD_AUDIO must already be granted", permissionGranted)

        lateinit var activity: Activity
        compose.setContent { val current = LocalActivity.current!!; SideEffect { activity = current } }
        compose.waitForIdle()
        withTimeout(5000) {
            while (!activity.hasWindowFocus() || withContext(Dispatchers.Main) {
                    !(activity as LifecycleOwner).lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                }) delay(25)
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val realRecorder = MicrophoneRecorder()
        val encoder = NeteaseFingerprint(context)
        val repository = NeteaseRecognitionRepository()
        val maximumLevel = AtomicReference(0f)
        val captureReleased = AtomicBoolean(false)
        val captureStarts = AtomicInteger(0)
        val capturedWindows = CopyOnWriteArrayList<Int>()
        val progressSeconds = CopyOnWriteArrayList<Int>()
        val requests = mutableListOf<JsonObject>()
        val sessions = mutableSetOf<String>()
        val started = SystemClock.elapsedRealtime()
        val recorder = object : RecognitionRecorder {
            override suspend fun record(stop: AtomicBoolean, progress: (Int, Float) -> Unit) =
                recordStreaming(stop, progress) { }

            override suspend fun recordStreaming(stop: AtomicBoolean, progress: (Int, Float) -> Unit,
                onWindow: (ShortArray) -> Unit): ShortArray {
                captureStarts.incrementAndGet()
                try {
                    return realRecorder.recordStreaming(stop, { seconds, level ->
                        maximumLevel.set(maxOf(maximumLevel.get(), level))
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
            { _, _ -> error("The microphone pipeline must use its shared recognition session") },
            RecognitionAudioSource.MICROPHONE,
            recognizeInSession = { fingerprint, duration, session, round ->
                sessions += session
                val requestStarted = SystemClock.elapsedRealtime()
                var outcome = "ERROR"
                var failure = ""
                try {
                    repository.recognize(fingerprint, duration, session, round).also {
                        outcome = if (it.isEmpty()) "NO_MATCH" else "MATCHED"
                    }
                } catch (error: Exception) {
                    failure = error.javaClass.simpleName
                    throw error
                } finally {
                    requests += buildJsonObject {
                        put("round", round); put("duration", duration)
                        put("afterMillis", requestStarted - started)
                        put("requestMillis", SystemClock.elapsedRealtime() - requestStarted)
                        put("outcome", outcome); put("failureClass", failure)
                    }
                }
            })
        var lastObserved = RecognitionState()
        var lastKey: Triple<RecognitionPhase, RecognitionStep, Int>? = null
        val transitions = mutableListOf<JsonObject>()
        fun observe() {
            val state = controller.state.value
            val key = Triple(state.phase, state.step, state.round)
            if (key != lastKey) {
                lastKey = key
                transitions += buildJsonObject {
                    put("afterMillis", SystemClock.elapsedRealtime() - started)
                    put("phase", state.phase.name); put("step", state.step.name)
                    put("round", state.round); put("seconds", state.seconds)
                }
            }
            lastObserved = state
        }
        var failureClass = ""
        var failureMessage = ""
        try {
            withContext(Dispatchers.Main) { controller.start() }
            withTimeout(42_000) {
                while (true) {
                    observe()
                    if (!lastObserved.busy) break
                    delay(20)
                }
            }
            assertEquals("Acoustic microphone recognition failed at ${lastObserved.step}, round ${lastObserved.round}: ${lastObserved.message}",
                RecognitionPhase.MATCHED, lastObserved.phase)
            assertTrue("The external known song must be identified as Rise to the Dark", lastObserved.songs.any { it.id == 29755418L })
            assertTrue("The physical microphone must capture nonzero audio", maximumLevel.get() > 0f)
            assertTrue("The physical microphone must emit a real recognition window", capturedWindows.isNotEmpty())
            assertEquals(1, captureStarts.get())
            assertTrue("Capture must have been released before publishing its final result", captureReleased.get())
            assertEquals(1, sessions.size)
            assertTrue("The microphone test must remain visible", activity.hasWindowFocus())
        } catch (failure: Throwable) {
            observe()
            failureClass = failure.javaClass.simpleName
            failureMessage = failure.message.orEmpty().take(300)
            throw failure
        } finally {
            withContext(NonCancellable + Dispatchers.Main) {
                val activityState = (activity as LifecycleOwner).lifecycle.currentState.name
                val captureReleasedAtResult = captureReleased.get()
                controller.cancel()
                scope.coroutineContext[Job]?.cancelAndJoin()
                output.writeText(buildJsonObject {
                    put("status", if (failureClass.isEmpty() && lastObserved.phase == RecognitionPhase.MATCHED) "MATCHED" else "FAILED")
                    put("source", "microphone-acoustic"); put("productService", false)
                    put("expectedSongId", 29755418L)
                    put("externalAudioReachability", "Not assumed; assessed by the actual capture and recognition result")
                    put("activityLifecycle", activityState)
                    put("phase", lastObserved.phase.name); put("step", lastObserved.step.name)
                    put("round", lastObserved.round); put("seconds", lastObserved.seconds)
                    put("fingerprintRateHz", 8000)
                    put("maximumLevel", maximumLevel.get())
                    put("captureStarts", captureStarts.get())
                    put("captureReleasedAtResult", captureReleasedAtResult)
                    put("captureReleasedAfterCleanup", captureReleased.get())
                    put("sessionCount", sessions.size)
                    put("totalMillis", SystemClock.elapsedRealtime() - started)
                    put("capturedWindowSamples", JsonArray(capturedWindows.map(::JsonPrimitive)))
                    put("progressSeconds", JsonArray(progressSeconds.map(::JsonPrimitive)))
                    put("requests", JsonArray(requests)); put("transitions", JsonArray(transitions))
                    put("songs", JsonArray(lastObserved.songs.map { song -> buildJsonObject {
                        put("id", song.id); put("name", song.name)
                    } }))
                    put("message", lastObserved.message.orEmpty())
                    put("failureClass", failureClass); put("failureMessage", failureMessage)
                }.toString())
            }
        }
    }
}
