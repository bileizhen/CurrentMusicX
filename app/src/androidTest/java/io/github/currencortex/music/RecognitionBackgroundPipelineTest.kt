package io.github.currencortex.music

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import io.github.currencortex.music.feature.recognition.NeteaseFingerprint
import io.github.currencortex.music.feature.recognition.NeteaseRecognitionRepository
import io.github.currencortex.music.feature.recognition.RecognitionController
import io.github.currencortex.music.feature.recognition.RecognitionPhase
import io.github.currencortex.music.feature.recognition.RecognitionRecorder
import kotlinx.coroutines.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Explicit known-file input only. No microphone, playback or system audio capture is started. */
class RecognitionBackgroundPipelineTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = ApplicationProvider.getApplicationContext<Context>().also {
        check(it.packageName.endsWith(".verification"))
    }

    @Test fun knownClipMatchesWithAVisibleActivity(): Unit = runBlocking {
        requireOptIn()
        lateinit var activity: Activity
        compose.setContent { val current = LocalActivity.current!!; SideEffect { activity = current } }
        compose.waitForIdle()
        assertTrue(activity.hasWindowFocus())
        runKnownClip("foreground", activity)
    }

    @Test fun knownClipMatchesAfterReturningHome(): Unit = runBlocking {
        requireOptIn()
        lateinit var activity: Activity
        compose.setContent { val current = LocalActivity.current!!; SideEffect { activity = current } }
        compose.waitForIdle()
        withContext(Dispatchers.Main) {
            activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        withTimeout(5000) { while (activity.hasWindowFocus()) delay(25) }
        withTimeout(5000) {
            while (withContext(Dispatchers.Main) { (activity as LifecycleOwner).lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }) delay(25)
        }
        delay(300)
        runKnownClip("background", activity)
    }

    private fun requireOptIn() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recognitionBackgroundLive") == "true")
    }

    private suspend fun runKnownClip(mode: String, activity: Activity) {
        val lifecycleState = withContext(Dispatchers.Main) { (activity as LifecycleOwner).lifecycle.currentState }
        val directory = checkNotNull(context.getExternalFilesDir(null)).canonicalFile
        val input = File(directory, "recognition-test.pcm").canonicalFile
        check(input.parentFile == directory && input.length() in 48_000..160_000) {
            "Explicit 8 kHz mono PCM16 validation fixture is required"
        }
        val bytes = input.readBytes()
        val samples = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
        bytes.fill(0)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val encoder = NeteaseFingerprint(context)
        val repository = NeteaseRecognitionRepository()
        var stage = "capture-fixture"
        var encodeMillis = -1L
        var requestMillis = -1L
        var fingerprintLength = 0
        val start = SystemClock.elapsedRealtime()
        val controller = RecognitionController(scope, RecognitionRecorder { _, progress ->
            progress(samples.size / 8000, 0f)
            samples
        }, { pcm ->
            stage = "fingerprint"
            val encodeStart = SystemClock.elapsedRealtime()
            try { encoder.encode(pcm).also { fingerprintLength = it.length } }
            finally { encodeMillis = SystemClock.elapsedRealtime() - encodeStart }
        }, { fingerprint, duration ->
            stage = "request"
            val requestStart = SystemClock.elapsedRealtime()
            try { repository.recognize(fingerprint, duration) }
            finally { requestMillis = SystemClock.elapsedRealtime() - requestStart }
        })
        try {
            withContext(Dispatchers.Main) { controller.start() }
            withTimeout(30_000) { while (controller.state.value.busy) delay(25) }
            val result = controller.state.value
            assertEquals("$mode pipeline failed during $stage: ${result.message}; encode=${encodeMillis}ms request=${requestMillis}ms",
                RecognitionPhase.MATCHED, result.phase)
            assertTrue("Known fixture must identify Rise to the Dark", result.songs.any { it.id == 29755418L })
        } finally {
            val result = controller.state.value
            File(directory, "recognition-$mode-pipeline.json").writeText(buildJsonObject {
                put("mode", mode)
                put("activityLifecycle", lifecycleState.name)
                put("lastStage", stage)
                put("phase", result.phase.name)
                put("inputSamples", samples.size)
                put("encodeMillis", encodeMillis)
                put("requestMillis", requestMillis)
                put("totalMillis", SystemClock.elapsedRealtime() - start)
                put("fingerprintLength", fingerprintLength)
                put("matchedKnownSong", result.songs.any { it.id == 29755418L })
                put("message", result.message.orEmpty())
            }.toString())
            withContext(NonCancellable + Dispatchers.Main) {
                controller.cancel()
                scope.cancel()
                samples.fill(0)
            }
        }
    }
}
