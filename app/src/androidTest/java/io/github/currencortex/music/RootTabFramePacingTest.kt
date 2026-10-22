package io.github.currencortex.music

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.auth.AuthViewModel
import io.github.currencortex.music.feature.profile.*
import io.github.currencortex.music.feature.settings.SettingsScreen
import io.github.currencortex.music.ui.component.RootTabTransition
import io.github.currencortex.music.ui.theme.LeiTheme
import io.github.currencortex.music.ui.util.viewModelFactory
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Collections
import java.util.UUID

/** Opt-in real display-clock comparison, including the first return from Settings to Me. */
class RootTabFramePacingTest {
    @Test fun settingsToMe() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("rootFrameProfile") == "true")
        val label = args.getString("profileLabel") ?: "sample"
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = when (request.requestUrl!!.encodedPath) {
                        "/cm/auth/me" -> """{"id":7,"nickname":"Profile fixture","bio":"Profile and navigation performance","stat":{"likes":23,"playlists":12}}"""
                        "/cm/playlists" -> """{"playlists":[]}"""
                        "/cm/plays/recent", "/cm/likes/mine" -> """{"songs":[]}"""
                        "/cm/decorations/scales" -> """{"scales":{}}"""
                        else -> return MockResponse().setResponseCode(404)
                    }
                    return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
                }
            }
            start()
        }
        val container = AppContainer(instrumentation.targetContext, "root-frames-${UUID.randomUUID()}")
        container.ready.await(); container.sessionRestored.await()
        container.musicSettings.setServer(server.url("/cm/").toString())
        container.accountRepository.server = server.url("/cm/").toString()
        container.accountRepository.save("fixture", UserDto(7, nickname = "Profile fixture"))
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        val worker = HandlerThread("root-frame-metrics").apply { start() }
        val frames = Collections.synchronizedList(mutableListOf<String>())
        val compositions = Collections.synchronizedList(mutableListOf<String>())
        val selected = mutableIntStateOf(3)
        var phase = "setup"
        val started = SystemClock.elapsedRealtime()
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
            frames += "$phase,${SystemClock.elapsedRealtime() - started},$dropped," +
                listOf(FrameMetrics.TOTAL_DURATION, FrameMetrics.LAYOUT_MEASURE_DURATION,
                    FrameMetrics.DRAW_DURATION, FrameMetrics.GPU_DURATION).joinToString(",") { metrics.getMetric(it).toString() }
        }
        scenario.onActivity { activity ->
            activity.window.addOnFrameMetricsAvailableListener(listener, Handler(worker.looper))
            activity.setContent {
                val profile: ProfileViewModel = viewModel(factory = viewModelFactory { ProfileViewModel(container) })
                val auth: AuthViewModel = viewModel(factory = viewModelFactory { AuthViewModel(container) })
                val discover: DiscoverViewModel = viewModel(factory = viewModelFactory { DiscoverViewModel(container) })
                LeiTheme(AppearanceSettings()) {
                    RootTabTransition(selected.intValue, rememberSaveableStateHolder(), { selected.intValue = it }) { tab ->
                        DisposableEffect(tab) {
                            compositions += "$phase,enter,$tab"
                            onDispose { compositions += "$phase,exit,$tab" }
                        }
                        when (tab) {
                            1 -> DiscoverScreen(discover) {}
                            2 -> MeScreen(profile, auth, {}, { selected.intValue = 3 }, { _, _ -> })
                            3 -> SettingsScreen({}, {}, {}, {}, {}, {})
                            else -> Box(Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
        try {
            SystemClock.sleep(1800)
            repeat(6) { pass ->
                scenario.onActivity { phase = "settings_to_me_$pass"; selected.intValue = 2 }
                SystemClock.sleep(550)
                scenario.onActivity { phase = "me_to_settings_$pass"; selected.intValue = 3 }
                SystemClock.sleep(550)
            }
            val captured = synchronized(frames) { frames.toList() }
            val target = captured.filter { it.startsWith("settings_to_me_") }.map { it.split(',')[3].toLong() }.sorted()
            check(target.size > 30) { "No continuous tab animation sample" }
            fun percentile(p: Float) = target[((target.size - 1) * p).toInt()] / 1_000_000.0
            val summary = "$label frames=${target.size},p50Ms=${percentile(.5f)},p95Ms=${percentile(.95f)}," +
                "maxMs=${target.last() / 1_000_000.0},over33Ms=${target.count { it > 33_333_333 }}"
            File(instrumentation.targetContext.externalCacheDir, "root-tab-$label.csv").writeText(
                "phase,elapsedMs,dropped,totalNs,layoutNs,drawNs,gpuNs\n" + captured.joinToString("\n"))
            File(instrumentation.targetContext.externalCacheDir, "root-tab-$label-composition.csv").writeText(compositions.joinToString("\n"))
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nROOT_TAB_FRAMES $summary\n") })
        } finally {
            scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
            scenario.close(); worker.quitSafely(); container.close(); server.shutdown()
        }
    }
}
