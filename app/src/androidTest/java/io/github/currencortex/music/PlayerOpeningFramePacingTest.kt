package io.github.currencortex.music

import android.os.*
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.CurrentMusicApp
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Opt-in, silent full-app fixture. Uses the real display clock, never the Compose test clock. */
class PlayerOpeningFramePacingTest {
    @Test fun profileMiniToFullPlayer() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("playerOpeningProfile") == "true")
        val traceMethods = InstrumentationRegistry.getArguments().getString("playerOpeningMethods") == "true"
        var tracing = false
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val container = AppContainer(context, "player-opening-${UUID.randomUUID()}")
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = when (request.requestUrl!!.encodedPath) {
                        "/cm/daily" -> "{\"daily\":[],\"forYou\":[],\"artists\":[]}"
                        "/cm/plays/recent", "/cm/likes/mine" -> "{\"songs\":[]}"
                        "/cm/playlists" -> "{\"playlists\":[]}"
                        "/cm/ncm/lyric" -> "{\"lines\":[" + (0..79).joinToString {
                            "{\"t\":${it * 3000},\"txt\":\"沿着星光缓缓前行，让音乐一直陪伴你 $it\"}"
                        } + "]}"
                        else -> return MockResponse().setResponseCode(404)
                    }
                    return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
                }
            }
            start()
        }
        val cover = java.io.File(context.cacheDir, "opening-cover-${UUID.randomUUID()}.png")
        android.graphics.Bitmap.createBitmap(800, 800, android.graphics.Bitmap.Config.ARGB_8888).apply {
            val paint = android.graphics.Paint().apply { shader = android.graphics.LinearGradient(0f, 0f, 800f, 800f,
                intArrayOf(0xFFF3B282.toInt(), 0xFF845D78.toInt(), 0xFF263A5B.toInt()), null, android.graphics.Shader.TileMode.CLAMP) }
            android.graphics.Canvas(this).drawRect(0f, 0f, 800f, 800f, paint)
            cover.outputStream().use { compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; recycle()
        }
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        val worker = HandlerThread("player-opening-metrics").apply { start() }
        val cycle = AtomicInteger(-1)
        val started = AtomicLong()
        val frames = Collections.synchronizedList(mutableListOf<List<Long>>())
        val ids = listOf(FrameMetrics.TOTAL_DURATION, FrameMetrics.LAYOUT_MEASURE_DURATION,
            FrameMetrics.DRAW_DURATION, FrameMetrics.GPU_DURATION, FrameMetrics.UNKNOWN_DELAY_DURATION)
        var refresh = 60f
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
            val index = cycle.get()
            val elapsed = (metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP) - started.get()) / 1_000_000
            if (index >= 0) frames += listOf(index.toLong(), elapsed, dropped.toLong()) + ids.map { metrics.getMetric(it) }
        }
        fun find(node: AccessibilityNodeInfo?, label: String): android.graphics.Rect? {
            if (node == null) return null
            if (node.text?.toString()?.contains(label) == true || node.contentDescription?.toString()?.contains(label) == true)
                return android.graphics.Rect().also { node.getBoundsInScreen(it) }
            repeat(node.childCount) { find(node.getChild(it), label)?.let { return it } }
            return null
        }
        try {
            container.ready.await(); container.sessionRestored.await()
            container.desktopLyrics.markRecommendationSeen()
            container.updateSettings.setAutoCheck(false)
            container.musicSettings.setLyricsSource(io.github.currencortex.music.data.settings.LyricsSource.CURRENT_MUSIC)
            container.settings.edit { it.copy(themeMode = ThemeMode.LIGHT, launchStyle = io.github.currencortex.music.data.settings.LaunchStyle.NONE) }
            val url = server.url("/cm/").toString()
            container.musicSettings.setServer(url); container.accountRepository.server = url
            container.accountRepository.save("isolated-opening-token", UserDto(7, nickname = "Opening fixture"))
            val song = Song(55, "展开性能样本", artists = "真实显示时钟", cover = cover.toURI().toString(), durationMs = 240000)
            container.playerController.queue.replace(listOf(song), 0)
            container.playerController.state.value = PlayerState(song = song, positionMs = 90000, durationMs = 240000)
            withTimeout(10000) { while (container.playbackLyrics.state.value.document.lines.size < 80) delay(50) }
            scenario.onActivity { activity ->
                refresh = activity.display?.refreshRate ?: 60f
                activity.window.addOnFrameMetricsAvailableListener(listener, Handler(worker.looper))
                activity.setContent { CurrentMusicApp(container) }
            }
            delay(3500)
            val automation = instrumentation.uiAutomation
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            var mini: android.graphics.Rect? = null
            withTimeout(10000) {
                while (mini == null) {
                    if (android.os.Build.VERSION.SDK_INT >= 33) automation.clearCache()
                    mini = find(automation.rootInActiveWindow, song.name)
                    if (mini == null) delay(100)
                }
            }
            val rect = checkNotNull(mini)
            repeat(1) { index ->
                if (traceMethods) {
                    Debug.startMethodTracingSampling(java.io.File(context.externalCacheDir, "opening-methods.trace").absolutePath, 32 * 1024 * 1024, 1000)
                    tracing = true
                }
                started.set(System.nanoTime()); cycle.set(index)
                val down = SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, rect.exactCenterX(), rect.exactCenterY(), 0).let {
                        automation.injectInputEvent(it, true); it.recycle()
                    }
                    delay(20)
                }
                delay(1300)
                if (android.os.Build.VERSION.SDK_INT >= 33) automation.clearCache()
                check(find(automation.rootInActiveWindow, "收起播放器") != null) { "Tap did not open the actual player" }
                cycle.set(-1)
                if (tracing) { Debug.stopMethodTracing(); tracing = false }
                if (index == 0) automation.takeScreenshot().let { image ->
                    java.io.File(context.externalCacheDir, "opening-settled.png").outputStream().use {
                        image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }; image.recycle()
                }
            }
            val captured = synchronized(frames) { frames.toList() }
            fun report(label: String, values: List<List<Long>>) {
                fun percentile(column: Int, p: Double): Double = values.map { it[column] }.sorted().let { it[((it.size - 1) * p).toInt()] / 1_000_000.0 }
                instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nPLAYER_OPENING $label frames=${values.size} displayHz=$refresh " +
                    "overBudget=${values.count { it[3] > 1_000_000_000.0 / refresh }} over33Ms=${values.count { it[3] > 33_333_333 }} " +
                    "p50Ms=${percentile(3, .5)} p95Ms=${percentile(3, .95)} maxMs=${percentile(3, 1.0)} " +
                    "layoutP95Ms=${percentile(4, .95)} drawP95Ms=${percentile(5, .95)} gpuP95Ms=${percentile(6, .95)} " +
                    "callbacksDropped=${values.sumOf { it[2] }}\n") })
            }
            check(captured.isNotEmpty()) { "No native frames captured" }
            report("first", captured.filter { it[0] == 0L })
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nOPENING_CSV_BEGIN\ncycle,elapsedMs,dropped,totalNs,layoutNs,drawNs,gpuNs,unknownNs\n" +
                captured.joinToString("\n") { it.joinToString(",") } + "\nOPENING_CSV_END\n") })
        } finally {
            if (tracing) Debug.stopMethodTracing()
            runCatching { scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) } }
            scenario.close(); worker.quitSafely(); container.close(); server.shutdown(); cover.delete()
        }
    }
}
