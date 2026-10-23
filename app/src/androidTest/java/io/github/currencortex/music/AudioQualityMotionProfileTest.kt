package io.github.currencortex.music

import android.graphics.*
import android.os.*
import android.view.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.player.*
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.theme.LeiTheme
import io.github.currencortex.music.ui.util.viewModelFactory
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

/** Real player, native display clock and optional screen recording; no Compose test clock. */
class AudioQualityMotionProfileTest {
    @Test fun recordQualityPanelOpening() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("qualityMotionProfile") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val container = AppContainer(context, "quality-motion-${UUID.randomUUID()}")
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = if (request.path?.startsWith("/cm/ncm/lyric") == true)
                    MockResponse().setBody("""{"lines":[{"t":0,"txt":"Close your eyes"},{"t":70000,"txt":"Only got one night"},{"t":80000,"txt":"Just put your trust in me"}]}""")
                else MockResponse().setResponseCode(404)
            }; start()
        }
        val cover = File(context.cacheDir, "quality-motion-cover.png")
        Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            canvas.drawColor(Color.DKGRAY)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 32f }
            repeat(20) { i ->
                paint.color = if (i % 2 == 0) Color.LTGRAY else Color.BLACK
                canvas.drawLine(-300f + i * 50f, 0f, 100f + i * 50f, 512f, paint)
            }
            cover.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }; recycle()
        }
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        val thread = HandlerThread("quality-frame-metrics").apply { start() }
        val phase = AtomicReference("idle")
        val frames = Collections.synchronizedList(mutableListOf<String>())
        val ids = listOf(FrameMetrics.TOTAL_DURATION, FrameMetrics.DRAW_DURATION,
            FrameMetrics.LAYOUT_MEASURE_DURATION, FrameMetrics.GPU_DURATION)
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
            val current = phase.get()
            if (current != "idle") frames += "$current,${SystemClock.elapsedRealtime()},$dropped," +
                ids.joinToString(",") { metrics.getMetric(it).toString() }
        }
        val panelOpen = AtomicBoolean(false)
        var width = 0; var height = 0
        try {
            container.ready.await(); container.sessionRestored.await()
            val url = server.url("/cm/").toString()
            container.musicSettings.setServer(url); container.accountRepository.server = url
            container.accountRepository.save("isolated-motion-token", UserDto(7, nickname = "Fixture"))
            container.musicSettings.setQuality(AudioQuality.STANDARD)
            val song = Song(55, "Hold On", artists = "Fadi", cover = cover.toURI().toString(), durationMs = 184000)
            container.playbackQueue.replace(listOf(song), 0)
            container.playerController.state.value = PlayerState(song = song, positionMs = 73000, durationMs = 184000)
            scenario.onActivity { activity ->
                width = activity.resources.displayMetrics.widthPixels; height = activity.resources.displayMetrics.heightPixels
                activity.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper))
                val vm = ViewModelProvider(activity, viewModelFactory { PlayerViewModel(container) })[PlayerViewModel::class.java]
                activity.setContent { LeiTheme(AppearanceSettings(themeMode = ThemeMode.DARK)) {
                    val backdrop = rememberMusicDialogBackdrop()
                    CompositionLocalProvider(LocalMusicDialogBackdrop provides backdrop) {
                        top.yukonga.miuix.kmp.basic.Scaffold {
                            Box(Modifier.fillMaxSize().musicDialogBackdropSource()) {
                                PlayerScreen(vm, {}, {}, onDialogActive = { panelOpen.set(it) })
                            }
                        }
                    }
                } }
            }
            delay(3500)
            repeat(3) { index ->
                // Verified against the actual phone screenshot: center label under the seek bar.
                val tapX = width * .5f; val tapY = height * .843f
                phase.set("open-$index")
                val down = SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                        tapX, tapY, 0)
                    instrumentation.uiAutomation.injectInputEvent(event, true); event.recycle()
                    delay(30)
                }
                delay(1400)
                check(panelOpen.get()) { "The physical tap did not open the quality panel" }
                phase.set("close-$index")
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                delay(1200)
                check(!panelOpen.get()) { "Back did not close the quality panel" }
                phase.set("idle"); delay(500)
            }
            val captured = synchronized(frames) { frames.toList() }
            File(context.externalCacheDir, "quality-panel-frames.csv").writeText(
                "phase,elapsedMs,dropped,totalNs,drawNs,layoutNs,gpuNs\n" + captured.joinToString("\n"))
            check(captured.isNotEmpty())
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nQUALITY_MOTION frames=${captured.size}\n") })
        } finally {
            scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
            scenario.close(); thread.quitSafely(); container.close(); server.shutdown(); cover.delete()
        }
    }
}
