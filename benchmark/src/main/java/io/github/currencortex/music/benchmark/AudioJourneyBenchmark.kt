package io.github.currencortex.music.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Configurator
import io.github.currencortex.music.performance.CurrentMusicJourneys
import io.github.currencortex.music.performance.BenchmarkKeepAliveService
import org.junit.Rule
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class AudioJourneyBenchmark {
    private var previousIdleTimeout: Long? = null

    @Before
    fun useExplicitSceneReadiness() {
        BenchmarkKeepAliveService.start(InstrumentationRegistry.getInstrumentation().context)
        val configuration = Configurator.getInstance()
        previousIdleTimeout = configuration.waitForIdleTimeout
        configuration.setWaitForIdleTimeout(100L)
    }

    @After
    fun restoreAutomationIdleTimeout() {
        previousIdleTimeout?.let { Configurator.getInstance().setWaitForIdleTimeout(it) }
        previousIdleTimeout = null
        BenchmarkKeepAliveService.stop(InstrumentationRegistry.getInstrumentation().context)
    }

    @get:Rule
    val rule = MacrobenchmarkRule()

    private fun compilationMode(): CompilationMode =
        when (val mode = InstrumentationRegistry.getArguments().getString("compilation", "none")) {
            "none" -> CompilationMode.None()
            "baseline" -> CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require)
            else -> error("Unknown compilation=$mode; use none or baseline")
        }

    @Test
    fun productionColdStart() = rule.measureRepeated(
        packageName = CurrentMusicJourneys.PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode(),
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = { pressHome() },
    ) {
        // Real launcher/MainActivity, distinct from the deterministic fixture below.
        startActivityAndWait()
    }

    @Test
    fun localHomeFirstFrame() = rule.measureRepeated(
        packageName = CurrentMusicJourneys.PACKAGE,
        metrics = listOf(
            StartupTimingMetric(),
            FrameTimingMetric(),
            TraceSectionMetric("cm_benchmark_home", TraceSectionMetric.Mode.First),
        ),
        compilationMode = compilationMode(),
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = { pressHome() },
    ) {
        CurrentMusicJourneys.openHome(this)
    }

    @Test fun openPlayer() = transition("player")
    @Test fun startPlayback() = transition("play")
    @Test fun enterWordLyrics() = transition("lyrics")
    @Test fun switchPlaylist() = transition("playlist")
    @Test fun openSettings() = transition("settings")
    @Test fun switchOutputMode() = transition("output")

    private fun transition(scene: String) = rule.measureRepeated(
        packageName = CurrentMusicJourneys.PACKAGE,
        metrics = listOf(
            FrameTimingMetric(),
            TraceSectionMetric("cm_benchmark_$scene", TraceSectionMetric.Mode.First),
        ),
        compilationMode = compilationMode(),
        iterations = 5,
        setupBlock = { CurrentMusicJourneys.openHome(this) },
    ) {
        CurrentMusicJourneys.openScene(this, scene)
    }
}
