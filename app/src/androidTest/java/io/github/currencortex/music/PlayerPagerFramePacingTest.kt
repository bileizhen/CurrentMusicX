package io.github.currencortex.music

import android.os.*
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.player.*
import io.github.currencortex.music.ui.theme.LeiTheme
import io.github.currencortex.music.ui.util.viewModelFactory
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Opt-in native-clock swipe sample: reports device timings, without a flaky timing assertion. */
class PlayerPagerFramePacingTest {
    @Test fun profileCoverLyricsSwipes() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("playerPagerProfile") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val container = AppContainer(context, "pager-profile-${UUID.randomUUID()}")
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = if (request.path?.startsWith("/cm/ncm/lyric") == true)
                    MockResponse().setHeader("Content-Type", "application/json").setBody("{\"lines\":[" +
                        (0..79).joinToString { "{\"t\":${it * 3000},\"txt\":\"沿着星光缓缓前行，让音乐一直陪伴你 $it\"}" } + "]}")
                else MockResponse().setResponseCode(404)
            }
            start()
        }
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        val thread = HandlerThread("pager-frame-metrics").apply { start() }
        val recording = AtomicBoolean(false)
        val frames = Collections.synchronizedList(mutableListOf<List<Long>>())
        val ids = listOf(FrameMetrics.TOTAL_DURATION, FrameMetrics.LAYOUT_MEASURE_DURATION,
            FrameMetrics.DRAW_DURATION, FrameMetrics.GPU_DURATION)
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            if (recording.get()) frames += ids.map { metrics.getMetric(it) }
        }
        var width = 0; var height = 0; var refresh = 60f
        try {
            container.ready.await(); container.sessionRestored.await()
            val url = server.url("/cm/").toString()
            container.musicSettings.setServer(url); container.accountRepository.server = url
            container.accountRepository.save("isolated-profile-token", UserDto(7, nickname = "Pager fixture"))
            container.musicSettings.editLyricsDisplay { it.copy(hideControls = true) }
            val song = Song(55, "星光", artists = "左右切页性能样本", durationMs = 240000)
            container.playerController.queue.replace(listOf(song), 0)
            container.playerController.state.value = PlayerState(song = song, positionMs = 90000, durationMs = 240000)
            withTimeout(10000) { while (container.playbackLyrics.state.value.document.lines.size < 80) delay(50) }
            scenario.onActivity { activity ->
                width = activity.resources.displayMetrics.widthPixels; height = activity.resources.displayMetrics.heightPixels
                refresh = activity.display?.refreshRate ?: 60f
                activity.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper))
                val vm = ViewModelProvider(activity, viewModelFactory { PlayerViewModel(container) })[PlayerViewModel::class.java]
                activity.setContent { LeiTheme(AppearanceSettings(blur = false)) {
                    top.yukonga.miuix.kmp.basic.Scaffold { PlayerScreen(vm, {}, {}) }
                } }
            }
            delay(3000)
            fun swipe(left: Boolean) {
                val down = SystemClock.uptimeMillis()
                val start = width * if (left) .86f else .14f
                val end = width - start
                for (step in 0..32) {
                    val due = down + step * 12
                    SystemClock.sleep((due - SystemClock.uptimeMillis()).coerceAtLeast(0))
                    val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(),
                        if (step == 0) MotionEvent.ACTION_DOWN else if (step == 32) MotionEvent.ACTION_UP else MotionEvent.ACTION_MOVE,
                        start + (end - start) * step / 32, height * .42f, 0)
                    instrumentation.uiAutomation.injectInputEvent(event, true); event.recycle()
                }
            }
            repeat(2) { swipe(true); delay(800); swipe(false); delay(800) }
            repeat(12) { index ->
                recording.set(true); swipe(index % 2 == 0); delay(420); recording.set(false); delay(180)
            }
            val captured = synchronized(frames) { frames.toList() }
            check(captured.isNotEmpty()) { "No native swipe frames captured" }
            val total = captured.map { it[0] }.sorted()
            val budget = 1_000_000_000.0 / refresh
            fun percentile(values: List<Long>, p: Float) = values[((values.size - 1) * p).toInt()] / 1_000_000.0
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nPLAYER_PAGER_FRAMES " +
                "frames=${total.size}, displayHz=$refresh, overBudget=${total.count { it > budget }}, over33Ms=${total.count { it > 33_333_333 }}, " +
                "p50Ms=${percentile(total, .5f)}, p95Ms=${percentile(total, .95f)}, " +
                listOf("total", "layout", "draw", "gpu").mapIndexed { i, name ->
                    "$name.p95Ms=${percentile(captured.map { it[i] }.sorted(), .95f)}"
                }.joinToString() + "\n") })
        } finally {
            scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
            scenario.close(); thread.quitSafely(); container.close(); server.shutdown()
        }
    }
}
