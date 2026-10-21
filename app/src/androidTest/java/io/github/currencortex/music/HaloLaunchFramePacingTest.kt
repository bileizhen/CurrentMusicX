package io.github.currencortex.music

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.ui.component.HaloLaunchOverlay
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real display-clock evidence; no emulator-specific timing threshold and no screenshot stalls. */
class HaloLaunchFramePacingTest {
    @Test fun profileWholeAnimation() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("haloFrameProfile") == "true")
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        val worker = HandlerThread("halo-frame-metrics").apply { start() }
        val frames = Collections.synchronizedList(mutableListOf<List<Long>>())
        val done = CountDownLatch(1)
        val started = SystemClock.elapsedRealtime()
        val ids = listOf(FrameMetrics.TOTAL_DURATION, FrameMetrics.DRAW_DURATION,
            FrameMetrics.LAYOUT_MEASURE_DURATION, FrameMetrics.GPU_DURATION)
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
            frames += listOf(SystemClock.elapsedRealtime() - started, dropped.toLong()) +
                ids.map { metrics.getMetric(it) }
        }
        var refresh = 60f
        scenario.onActivity {
            refresh = it.display?.refreshRate ?: 60f
            it.window.addOnFrameMetricsAvailableListener(listener, Handler(worker.looper))
            it.setContent { HaloLaunchOverlay("bileizhen", ready = true) { done.countDown() } }
        }
        try {
            check(done.await(15, TimeUnit.SECONDS)) { "Animation did not complete" }
            val captured = synchronized(frames) { frames.toList() }
            val active = captured.filter { it[0] in 350..8400 }
            val samples = active.map { it[2] }.sorted()
            check(samples.size > 100) { "No continuous rendering sample" }
            fun percentile(p: Float) = samples[((samples.size - 1) * p).toInt()] / 1_000_000.0
            val summary = "frames=${samples.size}, displayHz=$refresh, p50Ms=${percentile(.5f)}, " +
                "p95Ms=${percentile(.95f)}, p99Ms=${percentile(.99f)}, " +
                "overBudget=${samples.count { it > 1_000_000_000.0 / refresh }}, " +
                "over33Ms=${samples.count { it > 33_333_333 }}, droppedReports=${captured.sumOf { it[1] }}"
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            java.io.File(context.externalCacheDir, "halo-frame-pacing.csv").writeText(
                "elapsedMs,dropped,totalNs,drawNs,layoutNs,gpuNs\n" + captured.joinToString("\n") { it.joinToString(",") })
            InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                putString("stream", "\nHALO_FRAME_PACING $summary\n")
            })
        } finally {
            scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
            scenario.close(); worker.quitSafely()
        }
    }
}
