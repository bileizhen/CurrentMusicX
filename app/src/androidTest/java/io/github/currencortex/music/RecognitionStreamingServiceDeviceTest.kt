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
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Exercises the actual product foreground service and island, using controlled local playback. */
class RecognitionStreamingServiceDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>().also {
        check(it.packageName.endsWith(".verification"))
    }

    @Test fun controlledPlaybackMatchesThroughProductServiceAfterReturningHome(): Unit = runBlocking {
        assumeTrue("Enable recognitionStreamingLive explicitly to play the fixture and call NetEase",
            InstrumentationRegistry.getArguments().getString("recognitionStreamingLive") == "true")
        val directory = checkNotNull(context.getExternalFilesDir(null)).canonicalFile
        val output = File(directory, "recognition-stream-service.json")
        fun requireEnvironment(condition: Boolean, reason: String) {
            if (!condition) output.writeText(buildJsonObject {
                put("status", "SKIPPED"); put("productService", true); put("message", reason)
            }.toString())
            assumeTrue(reason, condition)
        }
        requireEnvironment(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED, "RECORD_AUDIO must already be granted")
        requireEnvironment(Settings.canDrawOverlays(context), "Desktop island overlay permission must already be granted")
        requireEnvironment(!RecognitionDesktopSession.running.value, "An existing recognition attempt must finish first")

        val args = InstrumentationRegistry.getArguments()
        val input = File(directory, args.getString("recognitionFixture") ?: "recognition-test.pcm").canonicalFile
        val expectedSong = args.getString("recognitionExpectedSong")?.toLong() ?: 29755418L
        check(input.parentFile == directory && input.length() in 32_000L..400_000L && input.length() % 2 == 0L) {
            "Push the explicitly selected 8 kHz mono PCM16 fixture into the isolated app's files directory"
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

        val started = SystemClock.elapsedRealtime()
        var ownedService = false
        var player: AudioTrack? = null
        var activityState = "RESUMED"
        var failureClass = ""
        var failureMessage = ""
        var maximumLevel = 0f
        var maximumSeconds = 0
        var lastObserved = RecognitionState()
        val transitions = mutableListOf<JsonObject>()
        fun observe() {
            val state = RecognitionDesktopSession.state.value
            maximumSeconds = maxOf(maximumSeconds, state.seconds)
            maximumLevel = maxOf(maximumLevel, state.level)
            if (state.phase != lastObserved.phase || state.step != lastObserved.step || state.round != lastObserved.round) {
                transitions += buildJsonObject {
                    put("afterMillis", SystemClock.elapsedRealtime() - started)
                    put("phase", state.phase.name); put("step", state.step.name)
                    put("round", state.round); put("seconds", state.seconds)
                }
            }
            lastObserved = state
        }
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
            withContext(Dispatchers.Main) {
                RecognitionDesktopSession.clearResult()
                RecognitionDesktopService.startSystemMix(context, desktop = true)
                ownedService = true
            }
            withTimeout(5000) {
                while (!RecognitionDesktopSession.running.value || !RecognitionDesktopSession.attached.value) {
                    observe()
                    check(lastObserved.phase != RecognitionPhase.ERROR) { "Product service failed to start: ${lastObserved.message}" }
                    delay(10)
                }
            }
            observe()
            withContext(Dispatchers.Main) {
                activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            withTimeout(5000) {
                while (activity.hasWindowFocus() || withContext(Dispatchers.Main) {
                        (activity as LifecycleOwner).lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                    }) {
                    observe()
                    delay(10)
                }
            }
            activityState = withContext(Dispatchers.Main) { (activity as LifecycleOwner).lifecycle.currentState.name }
            withTimeout(50_000) {
                while (true) {
                    observe()
                    if (lastObserved.phase in setOf(RecognitionPhase.MATCHED, RecognitionPhase.NO_MATCH, RecognitionPhase.ERROR)) break
                    check(RecognitionDesktopSession.running.value) { "Product service stopped before returning a result" }
                    delay(10)
                }
            }
            assertEquals("Product service failed at ${lastObserved.step}, round ${lastObserved.round}: ${lastObserved.message}",
                RecognitionPhase.MATCHED, lastObserved.phase)
            assertTrue("The actual product service must identify expected song $expectedSong", lastObserved.songs.any { it.id == expectedSong })
            assertEquals(RecognitionAudioSource.SYSTEM, lastObserved.source)
            assertTrue("The product service should match before its 15-second recording limit", lastObserved.seconds in 2..14)
            assertTrue("A network matching round must have completed", lastObserved.round >= 1)
            assertTrue("Real captured output must contain a nonzero waveform", maximumLevel > 0f)
            assertFalse("The activity must remain at HOME", activity.hasWindowFocus())
            assertFalse(withContext(Dispatchers.Main) {
                (activity as LifecycleOwner).lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            })
            assertTrue("The product island should still own the result", RecognitionDesktopSession.attached.value)
        } catch (failure: Throwable) {
            failureClass = failure.javaClass.simpleName
            failureMessage = failure.message.orEmpty().take(300)
            throw failure
        } finally {
            withContext(NonCancellable + Dispatchers.Main) {
                val audio = context.getSystemService(AudioManager::class.java)
                try { output.writeText(buildJsonObject {
                    put("status", if (failureClass.isEmpty() && lastObserved.phase == RecognitionPhase.MATCHED) "MATCHED" else "FAILED")
                    put("productService", true); put("desktopIsland", true)
                    put("activityLifecycle", activityState)
                    put("phase", lastObserved.phase.name); put("step", lastObserved.step.name)
                    put("round", lastObserved.round); put("capturedSeconds", lastObserved.seconds)
                    put("maximumObservedSeconds", maximumSeconds); put("maximumLevel", maximumLevel)
                    put("fixtureRateHz", 8000); put("fingerprintRateHz", 8000)
                    put("reportedOutputRateHz", audio.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE).orEmpty())
                    put("nativeOutputRateHz", AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC))
                    put("totalMillis", SystemClock.elapsedRealtime() - started)
                    put("transitions", JsonArray(transitions))
                    put("songs", JsonArray(lastObserved.songs.map { song -> buildJsonObject {
                        put("id", song.id); put("name", song.name)
                    } }))
                    put("message", lastObserved.message.orEmpty())
                    put("failureClass", failureClass); put("failureMessage", failureMessage)
                }.toString()) } finally {
                    try {
                        if (ownedService) {
                            RecognitionDesktopService.stop(context)
                            withTimeout(5000) { while (RecognitionDesktopSession.running.value) delay(10) }
                        }
                    } finally {
                        runCatching { player?.stop() }
                        try { player?.release() } finally { pcm.fill(0) }
                    }
                }
            }
        }
    }
}
