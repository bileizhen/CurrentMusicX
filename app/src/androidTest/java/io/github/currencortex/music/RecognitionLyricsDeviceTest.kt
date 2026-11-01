package io.github.currencortex.music

import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.feature.recognition.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/** Explicitly supplied user sample, replayed at wall-clock speed; this is not a microphone test. */
class RecognitionLyricsDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun suppliedSingingMatchesThroughRealEncoderAndBothNetworkBranches(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recognitionLyricsLive") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName.endsWith(".verification"))
        val directory = checkNotNull(context.getExternalFilesDir(null))
        val input = File(directory, "recognition-user-singing.pcm")
        val bytes = input.readBytes()
        check(bytes.size >= 240_000 && bytes.size <= 400_000)
        val pcm = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm)
        bytes.fill(0)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val encoder = NeteaseFingerprint(context)
        val aacEncoder = RecognitionAacEncoder()
        val repository = NeteaseRecognitionRepository()
        var lyricCalls = 0
        var lyricStartedMs = 0L
        var aacBytes = 0
        var released = false
        val sessions = mutableSetOf<String>()
        val started = SystemClock.elapsedRealtime()
        val recorder = object : RecognitionRecorder {
            override suspend fun record(stop: AtomicBoolean, progress: (Int, Float) -> Unit) =
                recordStreaming(stop, progress) { }
            override suspend fun recordStreaming(stop: AtomicBoolean, progress: (Int, Float) -> Unit,
                onWindow: (ShortArray) -> Unit): ShortArray {
                var size = 0
                val windows = RecognitionWindows()
                try {
                    while (size < 120_000 && !stop.get()) {
                        delay(100)
                        size += 800
                        progress(size / 8000, 0.5f)
                        windows.emitAvailable(size, { pcm.copyOf(it) }, onWindow)
                    }
                    return pcm.copyOf(size)
                } finally { released = true }
            }
        }
        val controller = RecognitionController(scope, recorder, encoder::encode, repository::recognize,
            recognizeInSession = { fp, duration, session, round ->
                sessions += session
                repository.recognize(fp, duration, session, round)
            }, recognizeLyrics = { samples, session ->
                sessions += session; lyricCalls++; lyricStartedMs = SystemClock.elapsedRealtime() - started
                val audio = aacEncoder.encode(samples)
                aacBytes = audio.size
                try { repository.recognizeLyrics(audio, session) } finally { audio.fill(0) }
            })
        try {
            withContext(Dispatchers.Main) { controller.start() }
            val result = withTimeout(45_000) { controller.state.first { !it.busy && it.phase != RecognitionPhase.IDLE } }
            File(directory, "recognition-user-singing.json").writeText(buildJsonObject {
                put("phase", result.phase.name); put("source", "provided-file-paced-replay")
                put("totalMs", SystemClock.elapsedRealtime() - started); put("lyricStartedMs", lyricStartedMs)
                put("lyricCalls", lyricCalls); put("aacBytes", aacBytes); put("sessionCount", sessions.size)
                put("message", result.message.orEmpty())
                put("songs", JsonArray(result.songs.map { buildJsonObject { put("id", it.id); put("name", it.name) } }))
            }.toString())
            assertEquals(result.message, RecognitionPhase.MATCHED, result.phase)
            assertTrue("The supplied singing must identify 爱情讯息", result.songs.any { it.id == 233888L })
            assertEquals(1, lyricCalls); assertEquals(1, sessions.size)
            assertTrue(lyricStartedMs >= 10_000); assertTrue(aacBytes > 7)
            assertTrue("Capture must be released before showing a match", released)
        } finally {
            withContext(Dispatchers.Main) { controller.cancel() }
            scope.cancel(); pcm.fill(0)
        }
    }
}
