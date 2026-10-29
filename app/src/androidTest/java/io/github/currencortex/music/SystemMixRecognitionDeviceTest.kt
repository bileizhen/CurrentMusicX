package io.github.currencortex.music

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
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

/** Live existing-output test of the actual FGS and original island. Never controls media playback. */
class SystemMixRecognitionDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>().also {
        check(it.packageName.endsWith(".verification"))
    }

    @Test fun existingSystemPlaybackMatchesThroughTheBackgroundRecognitionService(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recognitionSystemMixLive") == "true")
        val output = File(checkNotNull(context.getExternalFilesDir(null)), "recognition-system-mix-live.json")
        fun requireEnvironment(condition: Boolean, reason: String) {
            if (!condition) output.writeText(buildJsonObject {
                put("status", "SKIPPED"); put("source", "system-mix-service"); put("message", reason)
            }.toString())
            assumeTrue(reason, condition)
        }
        requireEnvironment(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
            "RECORD_AUDIO must already have been granted by the user")
        requireEnvironment(Settings.canDrawOverlays(context), "Desktop island permission must already be granted")
        requireEnvironment(!RecognitionDesktopSession.running.value, "An existing recognition attempt is active; the test does not replace it")
        val audio = context.getSystemService(AudioManager::class.java)
        // isMusicActive() can be false for an actively playing AAudio stream. The
        // explicit live opt-in, nonzero waveform and actual match below are the gates.
        lateinit var activity: Activity
        compose.setContent { val current = LocalActivity.current!!; SideEffect { activity = current } }
        compose.waitForIdle()
        withTimeout(5000) {
            while (!activity.hasWindowFocus() || withContext(Dispatchers.Main) {
                    !(activity as LifecycleOwner).lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                }) delay(25)
        }
        val started = SystemClock.elapsedRealtime()
        var ownedService = false
        var finishRequested = false
        var activityState = "RESUMED"
        var captureAt = -1L
        var analyzeAt = -1L
        var matchAt = -1L
        var terminalAt = -1L
        var maxSeconds = 0
        var maxLevel = 0f
        var lastObserved = RecognitionState()
        var failureClass = ""
        var failureMessage = ""
        val transitions = mutableListOf<JsonObject>()
        fun observe(state: RecognitionState) {
            val now = SystemClock.elapsedRealtime()
            maxSeconds = maxOf(maxSeconds, state.seconds)
            maxLevel = maxOf(maxLevel, state.level)
            if (state.phase == RecognitionPhase.RECORDING && captureAt < 0) captureAt = now
            if (state.phase == RecognitionPhase.RECOGNIZING && state.step == RecognitionStep.ANALYZE && analyzeAt < 0) analyzeAt = now
            if (state.phase == RecognitionPhase.RECOGNIZING && state.step == RecognitionStep.MATCH && matchAt < 0) matchAt = now
            if (state.phase != lastObserved.phase || state.step != lastObserved.step) transitions += buildJsonObject {
                put("afterMillis", now - started); put("phase", state.phase.name); put("step", state.step.name); put("seconds", state.seconds)
            }
            lastObserved = state
        }
        try {
            withTimeout(35_000) {
                withContext(Dispatchers.Main) {
                    RecognitionDesktopSession.clearResult()
                    RecognitionDesktopService.startSystemMix(context, desktop = true)
                    ownedService = true
                }
                withTimeout(5000) {
                    while (!RecognitionDesktopSession.running.value || !RecognitionDesktopSession.attached.value) {
                        observe(RecognitionDesktopSession.state.value)
                        check(lastObserved.phase != RecognitionPhase.ERROR) { "Recognition service failed to start: ${lastObserved.message}" }
                        compose.mainClock.advanceTimeBy(16)
                        delay(10)
                    }
                }
                observe(RecognitionDesktopSession.state.value)
                assertEquals(RecognitionAudioSource.SYSTEM, lastObserved.source)
                withContext(Dispatchers.Main) {
                    activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                withTimeout(5000) {
                    while (activity.hasWindowFocus() || withContext(Dispatchers.Main) {
                            (activity as LifecycleOwner).lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                        }) {
                        observe(RecognitionDesktopSession.state.value)
                        delay(10)
                    }
                }
                activityState = withContext(Dispatchers.Main) { (activity as LifecycleOwner).lifecycle.currentState.name }
                while (true) {
                    val state = RecognitionDesktopSession.state.value
                    observe(state)
                    if (!finishRequested && state.phase == RecognitionPhase.RECORDING && state.seconds >= 6) {
                        withContext(Dispatchers.Main) { RecognitionDesktopService.finish(context) }
                        finishRequested = true
                    }
                    if (state.phase in setOf(RecognitionPhase.MATCHED, RecognitionPhase.NO_MATCH, RecognitionPhase.ERROR)) {
                        terminalAt = SystemClock.elapsedRealtime()
                        break
                    }
                    check(RecognitionDesktopSession.running.value) { "Service stopped before producing a recognition result" }
                    delay(10)
                }
            }
            assertFalse(activity.hasWindowFocus())
            assertEquals("Real system mix failed at ${lastObserved.step}: ${lastObserved.message}", RecognitionPhase.MATCHED, lastObserved.phase)
            assertTrue("Only a nonempty real backend result counts as recognition", lastObserved.songs.isNotEmpty())
            assertTrue("A real nonzero waveform must have been observed", maxLevel > 0f)
            assertTrue("At least three seconds of accepted PCM are required", maxSeconds >= 3)
        } catch (failure: Throwable) {
            failureClass = failure.javaClass.simpleName
            failureMessage = failure.message.orEmpty().take(300)
            throw failure
        } finally {
            val end = if (terminalAt >= 0) terminalAt else SystemClock.elapsedRealtime()
            output.writeText(buildJsonObject {
                put("status", if (failureClass.isEmpty() && lastObserved.phase == RecognitionPhase.MATCHED) "MATCHED" else "FAILED")
                put("source", "system-mix-service")
                put("productService", true)
                put("activityLifecycle", activityState)
                put("phase", lastObserved.phase.name)
                put("step", lastObserved.step.name)
                put("observedCaptureMillis", if (captureAt < 0) -1 else (if (analyzeAt >= 0) analyzeAt else end) - captureAt)
                put("observedEncodeMillis", if (analyzeAt < 0) -1 else (if (matchAt >= 0) matchAt else end) - analyzeAt)
                put("observedRequestMillis", if (matchAt < 0) -1 else end - matchAt)
                put("totalMillis", end - started)
                put("maxObservedSeconds", maxSeconds)
                put("maxObservedLevel", maxLevel)
                put("finishRequested", finishRequested)
                put("inputSamples", JsonNull) // Native service intentionally exposes metadata, not PCM buffers.
                put("inputRateHz", 8000)
                put("sourceSampleRateHz", JsonNull)
                put("reportedOutputRateHz", audio.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE).orEmpty())
                put("message", lastObserved.message.orEmpty())
                put("failureClass", failureClass)
                put("failureMessage", failureMessage)
                put("transitions", JsonArray(transitions))
                put("songs", buildJsonArray {
                    lastObserved.songs.forEach { song -> add(buildJsonObject { put("id", song.id); put("name", song.name) }) }
                })
            }.toString())
            if (ownedService) withContext(NonCancellable + Dispatchers.Main) { RecognitionDesktopService.stop(context) }
        }
    }
}
