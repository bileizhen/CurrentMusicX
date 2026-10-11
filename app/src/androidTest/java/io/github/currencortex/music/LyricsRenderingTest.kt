package io.github.currencortex.music

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.feature.lyrics.ui.*
import kotlinx.coroutines.delay
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.CopyOnWriteArrayList

/** Same real-device renderer/300 ms playback samples for before-and-after recordings. */
class LyricsRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun recordContinuousMixedLanguageWordTiming(): Unit {
        val metrics = CopyOnWriteArrayList<Long>()
        val worker = HandlerThread("lyrics-frame-metrics").apply { start() }
        val listener = android.view.Window.OnFrameMetricsAvailableListener { _, frame, _ ->
            metrics += frame.getMetric(FrameMetrics.TOTAL_DURATION)
        }
        compose.runOnUiThread { compose.activity.window.addOnFrameMetricsAvailableListener(listener, Handler(worker.looper)) }
        val arguments = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        val sustained = arguments.getString("lyrics_peaks") == "true"
        val silence = arguments.getString("lyrics_gaps") == "true"
        val perspective = arguments.getString("lyrics_perspective") == "true"
        val startPosition = arguments.getString("lyrics_start_ms")?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        val span = if (silence) 6500L else if (sustained) 5000L else 1600L
        val vocalSpan = if (silence) 1600L else span
        val intro = if (silence) 2500L else 0L
        val phrases = listOf("沿着星光慢慢向前走", "Hello world, the music carries on", "夜空に響くメロディー", "Stay with me 世界が輝く", "每个音符都在温柔流动")
        val document = LyricsDocument((0..24).map { index ->
            val text = phrases[index % phrases.size]
            val begin = index * span + intro
            val tail = if (text.contains(' ')) text.lastIndexOf(' ') + 1 else text.lastIndex
            val words = if (sustained) (0 until tail).map { offset ->
                LyricWord(text.substring(offset, offset + 1), begin + offset * 1800 / tail,
                    begin + (offset + 1) * 1800 / tail, offset, offset + 1)
            } + LyricWord(text.substring(tail), begin + 1800, begin + span, tail, text.length)
            else text.indices.map { offset ->
                LyricWord(text.substring(offset, offset + 1), begin + offset * vocalSpan / text.length,
                    begin + (offset + 1) * vocalSpan / text.length, offset, offset + 1)
            }
            LyricLine(begin, begin + vocalSpan, text, words, translation = "The music stays with you · 音乐一直陪着你")
        })
        try {
            compose.mainClock.autoAdvance = false
            compose.setContent {
                val song = remember { Song(7, "Rendering fixture", durationMs = span * 25 + intro) }
                var sample by remember { mutableStateOf(PlayerState(song = song, positionMs = startPosition,
                    playing = true, durationMs = span * 25 + intro)) }
                LaunchedEffect(Unit) {
                    val start = SystemClock.elapsedRealtime()
                    while (true) { sample = sample.copy(positionMs = startPosition + SystemClock.elapsedRealtime() - start); delay(300) }
                }
                val position = rememberLyricsPosition(sample)
                LyricsScreen(document, position, {}, Modifier.fillMaxSize().background(Color(0xFF171B22)).padding(horizontal = 20.dp),
                    fontSize = 36f, minimal = silence, perspective = perspective)
            }
            // Advance rendering frames alongside real wall-clock playback samples.
            val until = SystemClock.elapsedRealtime() + 15000
            while (SystemClock.elapsedRealtime() < until) {
                compose.mainClock.advanceTimeByFrame()
                Thread.sleep(16)
            }
            assertTrue("Real window frames must be observed", metrics.size > 60)
            val samples = metrics.drop(40).sorted()
            fun percentile(p: Float) = samples[((samples.size - 1) * p).toInt()] / 1_000_000f
            android.util.Log.i("LyricsRenderingEvidence", "frames=${samples.size} p50=${percentile(.5f)}ms p95=${percentile(.95f)}ms over32=${samples.count { it > 32_000_000 }}")
        } finally {
            compose.runOnUiThread { compose.activity.window.removeOnFrameMetricsAvailableListener(listener) }
            worker.quitSafely()
        }
    }
}
