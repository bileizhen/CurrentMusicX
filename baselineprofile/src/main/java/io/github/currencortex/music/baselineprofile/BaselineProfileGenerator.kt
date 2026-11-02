package io.github.currencortex.music.baselineprofile

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.Configurator
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.performance.CurrentMusicJourneys
import io.github.currencortex.music.performance.BenchmarkKeepAliveService
import org.junit.Rule
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    private var previousIdleTimeout: Long? = null
    private var progressSequence = 0
    private var measureIteration = 0
    private val progressStarted = SystemClock.elapsedRealtime()
    private val progressFileLock = Any()
    private var diagnosticWatchdog: Thread? = null
    private val progressFile: File by lazy {
        val directory = checkNotNull(InstrumentationRegistry.getInstrumentation().context.externalMediaDirs.firstOrNull()) {
            "No external media directory for profile progress"
        }
        check(directory.isDirectory || directory.mkdirs()) { "Unable to create profile progress directory" }
        File(directory, "cm-profile-progress.txt")
    }

    private fun progress(phase: String) {
        val message = "CM_PROFILE step=${++progressSequence} iteration=$measureIteration " +
            "elapsedMs=${SystemClock.elapsedRealtime() - progressStarted} phase=$phase"
        Log.i("CurrentMusicProfileProgress", message)
        // Open/append/close every phase so adb can read it even while another Binder call blocks.
        synchronized(progressFileLock) { progressFile.appendText("$message\n", Charsets.UTF_8) }
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            putString("stream", "\n$message\n")
        })
    }

    @Before
    fun useExplicitSceneReadiness() {
        progressSequence = 0
        measureIteration = 0
        synchronized(progressFileLock) {
            progressFile.writeText("CM_PROFILE_SESSION startedElapsedMs=${SystemClock.elapsedRealtime()}\n", Charsets.UTF_8)
        }
        progress("beforeEntry")
        progress("beforeHarnessService")
        BenchmarkKeepAliveService.start(InstrumentationRegistry.getInstrumentation().context)
        progress("afterHarnessService")
        val configuration = Configurator.getInstance()
        previousIdleTimeout = configuration.waitForIdleTimeout
        configuration.setWaitForIdleTimeout(100L)
        progress("afterConfigurator")
        startDiagnosticWatchdog()
    }

    private fun startDiagnosticWatchdog() {
        if (InstrumentationRegistry.getArguments().getString("traceWatchdog") != "true") return
        diagnosticWatchdog = Thread({
            try {
                Thread.sleep(15_000)
                val threads = Thread.getAllStackTraces().entries.filter { (thread, stack) ->
                    thread.name.equals("main", ignoreCase = true) ||
                        thread.name.contains("instrumentation", ignoreCase = true) ||
                        thread.name.startsWith("Instr:", ignoreCase = true) ||
                        thread.name.contains("junit", ignoreCase = true) ||
                        stack.any { it.className.startsWith("androidx.benchmark.") || it.className.contains("Shell") }
                }.sortedBy { it.key.id }
                val dump = buildString {
                    appendLine("CM_PROFILE_WATCHDOG elapsedMs=${SystemClock.elapsedRealtime() - progressStarted}")
                    for ((thread, stack) in threads) {
                        appendLine("THREAD name=${thread.name} id=${thread.id} state=${thread.state}")
                        for (frame in stack) appendLine("    at $frame")
                    }
                    appendLine("CM_PROFILE_WATCHDOG_END")
                }
                synchronized(progressFileLock) { progressFile.appendText(dump, Charsets.UTF_8) }
                Log.i("CurrentMusicProfileProgress", "WATCHDOG_DUMPED")
                InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                    putString("stream", "\nWATCHDOG_DUMPED\n")
                })
            } catch (_: InterruptedException) {
                // Test finished before the optional diagnostic snapshot.
            } catch (error: Exception) {
                // A diagnostic-only failure must not crash the test/target processes.
                Log.w("CurrentMusicProfileProgress", "Watchdog failed: ${error.javaClass.simpleName}")
            }
        }, "cm-profile-watchdog").apply { isDaemon = true; start() }
    }

    @After
    fun restoreAutomationIdleTimeout() {
        diagnosticWatchdog?.interrupt()
        diagnosticWatchdog = null
        previousIdleTimeout?.let { Configurator.getInstance().setWaitForIdleTimeout(it) }
        previousIdleTimeout = null
        BenchmarkKeepAliveService.stop(InstrumentationRegistry.getInstrumentation().context)
    }

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun productionColdStart() {
        progress("beforeCollect:productionColdStart")
        rule.collect(
        packageName = CurrentMusicJourneys.PACKAGE,
        maxIterations = 5,
        stableIterations = 2,
        strictStability = true,
        filterPredicate = { !it.contains("Lio/github/currencortex/music/benchmark/") },
        includeInStartupProfile = true,
    ) {
        measureIteration++
        progress("measureBegin:productionColdStart")
        progress("beforeHome")
        pressHome()
        progress("afterHome")
        progress("beforeStart")
        startActivityAndWait()
        progress("afterStart")
    }
        progress("afterCollect:productionColdStart")
    }

    @Test
    fun localAudioJourneys() {
        progress("beforeCollect:localAudioJourneys")
        rule.collect(
        packageName = CurrentMusicJourneys.PACKAGE,
        maxIterations = 5,
        stableIterations = 2,
        strictStability = true,
        filterPredicate = { !it.contains("Lio/github/currencortex/music/benchmark/") },
        includeInStartupProfile = false,
    ) {
        measureIteration++
        progress("measureBegin:localAudioJourneys")
        progress("beforeStart:home")
        CurrentMusicJourneys.openHome(this)
        progress("afterStart:home")
        for (scene in CurrentMusicJourneys.transitions) {
            progress("beforeScene:$scene")
            CurrentMusicJourneys.openScene(this, scene)
            progress("afterScene:$scene")
            progress("beforeStart:home")
            CurrentMusicJourneys.openHome(this)
            progress("afterStart:home")
        }
    }
        progress("afterCollect:localAudioJourneys")
    }
}
