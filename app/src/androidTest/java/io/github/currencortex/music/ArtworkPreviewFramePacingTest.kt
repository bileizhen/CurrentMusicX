package io.github.currencortex.music

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assume.assumeTrue
import org.junit.Test
import top.yukonga.miuix.kmp.basic.Scaffold
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Native frame metrics on the real display clock, independent of Compose's test clock. */
class ArtworkPreviewFramePacingTest {
    @Test fun profileClosingCoverOnRealDisplayClock() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("artworkFrameProfile") == "true")
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".verification"))
        val label = args.getString("artworkProfileLabel") ?: "current"
        val image = File(context.cacheDir, "artwork-frame-cover.png")
        Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(67, 125, 184))
            image.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
            recycle()
        }
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        val worker = HandlerThread("artwork-return-metrics").apply { start() }
        val cycle = AtomicInteger(-1)
        val closedAt = AtomicLong(0)
        val frames = Collections.synchronizedList(mutableListOf<List<Long>>())
        var showing by mutableStateOf(false)
        var anchor = Rect.Zero
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
            val index = cycle.get()
            val elapsed = (metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP) - closedAt.get()) / 1_000_000
            if (index >= 0 && elapsed in 0..450) frames += listOf(index.toLong(), elapsed, dropped.toLong(),
                metrics.getMetric(FrameMetrics.TOTAL_DURATION), metrics.getMetric(FrameMetrics.DRAW_DURATION),
                metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION), metrics.getMetric(FrameMetrics.GPU_DURATION),
                metrics.getMetric(FrameMetrics.DEADLINE))
        }
        try {
            scenario.onActivity { activity ->
                activity.window.addOnFrameMetricsAvailableListener(listener, Handler(worker.looper))
                activity.setContent { LeiTheme(AppearanceSettings(themeMode = ThemeMode.LIGHT)) {
                    val backdrop = rememberMusicDialogBackdrop()
                    CompositionLocalProvider(LocalMusicDialogBackdrop provides backdrop) {
                        Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) {
                            Box(Modifier.fillMaxSize().musicDialogBackdropSource()) {
                                Canvas(Modifier.fillMaxSize()) {
                                    drawRect(Color(0xFFEAD8C5))
                                    val side = 36.dp.toPx()
                                    for (y in 0..(size.height / side).toInt()) for (x in 0..(size.width / side).toInt()) {
                                        if ((x + y) % 2 == 0) drawRect(Color(0xFF6688AA),
                                            Offset(x * side, y * side), Size(side, side))
                                    }
                                }
                                MusicCover(image.toURI().toString(), Modifier.align(Alignment.BottomEnd)
                                    .padding(end = 24.dp, bottom = 120.dp).size(110.dp)
                                    .onGloballyPositioned { anchor = it.boundsInWindow() }, pixels = 320, cornerRadius = 12.dp)
                            }
                            if (showing) MusicArtworkPreview(image.toURI().toString(), "帧时间验证", anchor,
                                listOf(GlassMenuItem("play", "播放", MusicIcons.Play),
                                    GlassMenuItem("next", "下一首", MusicIcons.NextTrack),
                                    GlassMenuItem("queue", "加入队列", MusicIcons.List)),
                                listOf(GlassMenuItem("artist", "查看歌手"), GlassMenuItem("album", "查看专辑"),
                                    GlassMenuItem("more", "更多操作")), onDismiss = { showing = false })
                        }
                    }
                } }
            }
            Thread.sleep(1200)
            repeat(10) { index ->
                cycle.set(-1)
                scenario.onActivity { check(!anchor.isEmpty); showing = true }
                Thread.sleep(700)
                scenario.onActivity {
                    closedAt.set(System.nanoTime())
                    cycle.set(index)
                    it.onBackPressedDispatcher.onBackPressed()
                }
                Thread.sleep(550)
                scenario.onActivity { check(!showing) { "Preview did not close" } }
            }
            cycle.set(-1)
            val captured = synchronized(frames) { frames.toList() }
            val active = captured.filter { it[0] >= 2 && it[1] <= 250 }
            check(active.size >= 32) { "Insufficient display-clock samples: ${active.size}" }
            val times = active.map { it[3] / 1_000_000.0 }.sorted()
            val summary = "frames=${times.size}, p50Ms=${times[times.size / 2]}, p95Ms=${times[((times.size - 1) * .95).toInt()]}, " +
                "maxMs=${times.last()}, over16Ms=${times.count { it > 16.667 }}, over33Ms=${times.count { it > 33.333 }}"
            File(context.externalCacheDir, "artwork-return-$label.csv").writeText(
                "cycle,elapsedMs,dropped,totalNs,drawNs,layoutNs,gpuNs,deadlineNs\n" + captured.joinToString("\n") { it.joinToString(",") })
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nARTWORK_RETURN $label $summary\n") })
        } finally {
            scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
            scenario.close()
            worker.quitSafely()
        }
    }
}
