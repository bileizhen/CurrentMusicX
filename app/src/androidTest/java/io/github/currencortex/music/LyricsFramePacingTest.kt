package io.github.currencortex.music

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.feature.lyrics.ui.LyricsScreen
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.Collections

/** Opt-in real-clock rendering sample; timings are evidence, not a device-dependent pass threshold. */
class LyricsFramePacingTest {
    @Test fun profileTimedLyrics() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("lyricFrameProfile") == "true")
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        val metricIds = listOf(FrameMetrics.TOTAL_DURATION, FrameMetrics.LAYOUT_MEASURE_DURATION,
            FrameMetrics.DRAW_DURATION, FrameMetrics.SYNC_DURATION, FrameMetrics.COMMAND_ISSUE_DURATION,
            FrameMetrics.GPU_DURATION)
        val frames = Collections.synchronizedList(mutableListOf<List<Long>>())
        val thread = HandlerThread("lyric-frame-metrics").apply { start() }
        val started = SystemClock.elapsedRealtime()
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            // Exclude activity creation and initial shader/font warmup.
            if (SystemClock.elapsedRealtime() - started > 3000)
                frames += metricIds.map { metrics.getMetric(it) }
        }
        val text = "沿着星光缓缓前行让音乐一直陪伴你"
        val document = LyricsDocument((0..25).map { index ->
            val start = index * 1200L
            LyricLine(start, start + 1200, text + index, text.mapIndexed { offset, char ->
                LyricWord(char.toString(), start + offset * 60, start + (offset + 1) * 60, offset, offset + 1,
                    roles = if (offset % 5 == 0) listOf("emphasis") else emptyList())
            }, translation = "Walk beneath the stars, let music always stay with you.")
        })
        var refresh = 60f
        scenario.onActivity {
            refresh = it.display?.refreshRate ?: 60f
            it.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper))
            it.setContent {
                val position = remember { mutableLongStateOf(0) }
                LaunchedEffect(Unit) {
                    val origin = withFrameNanos { it }
                    while (true) withFrameNanos { position.longValue = (it - origin) / 1_000_000 }
                }
                LyricsScreen(document, position, {}, Modifier.fillMaxSize().background(Color(0xFF252B35)))
            }
        }
        try {
            // The production Choreographer drives both media time and animations. No Compose test clock.
            Thread.sleep(18000)
            val captured = synchronized(frames) { frames.toList() }
            val samples = captured.map { it[0] }.sorted()
            check(samples.isNotEmpty()) { "No native window frames recorded" }
            fun percentile(p: Float) = samples[((samples.size - 1) * p).toInt()] / 1_000_000.0
            val budget = 1_000_000_000.0 / refresh
            val summary = "frames=${samples.size}, displayHz=$refresh, p50Ms=${percentile(.5f)}, " +
                "p90Ms=${percentile(.9f)}, p95Ms=${percentile(.95f)}, " +
                "overBudget=${samples.count { it > budget }}, over33Ms=${samples.count { it > 33_333_333 }}, " +
                listOf("total", "layout", "draw", "sync", "command", "gpu").mapIndexed { index, name ->
                    val values = captured.map { it[index] }.sorted()
                    "$name.p95=${values[((values.size - 1) * .95f).toInt()] / 1_000_000.0}"
                }.joinToString()
            InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                putString("stream", "\nLYRICS_FRAME_PACING $summary\n")
            })
        } finally {
            scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
            scenario.close()
            thread.quitSafely()
        }
    }
}
