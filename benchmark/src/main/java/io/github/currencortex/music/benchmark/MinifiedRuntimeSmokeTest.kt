package io.github.currencortex.music.benchmark

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.currencortex.music.performance.CurrentMusicJourneys
import io.github.currencortex.music.performance.BenchmarkKeepAliveService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import java.util.regex.Pattern

/** Run separately from performance measurements, against the minified performance application. */
@RunWith(AndroidJUnit4::class)
class MinifiedRuntimeSmokeTest {
    private var previousIdleTimeout: Long? = null
    private var progressSequence = 0
    private val progressStarted = SystemClock.elapsedRealtime()

    private fun progress(phase: String) {
        val message = "CM_SMOKE step=${++progressSequence} " +
            "elapsedMs=${SystemClock.elapsedRealtime() - progressStarted} phase=$phase"
        Log.i("CurrentMusicSmokeProgress", message)
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            putString("stream", "\n$message\n")
        })
    }

    @Before
    fun useExplicitSceneReadiness() {
        progress("entry")
        progress("beforeHarnessService")
        BenchmarkKeepAliveService.start(InstrumentationRegistry.getInstrumentation().context)
        progress("afterHarnessService")
        val configuration = Configurator.getInstance()
        previousIdleTimeout = configuration.waitForIdleTimeout
        configuration.setWaitForIdleTimeout(100L)
        progress("afterConfig")
    }

    @After
    fun restoreAutomationIdleTimeout() {
        previousIdleTimeout?.let { Configurator.getInstance().setWaitForIdleTimeout(it) }
        previousIdleTimeout = null
        BenchmarkKeepAliveService.stop(InstrumentationRegistry.getInstrumentation().context)
    }

    @Test
    fun localRuntimeSurvivesR8() {
        progress("beforeContext")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.context
        progress("beforeUiDevice")
        val device = UiDevice.getInstance(instrumentation)
        progress("afterUiDevice")
        val target = CurrentMusicJourneys.PACKAGE
        progress("beforeTargetInfo")
        val info = context.packageManager.getApplicationInfo(target, 0)
        assertEquals("Smoke must use the non-debuggable target", 0, info.flags and ApplicationInfo.FLAG_DEBUGGABLE)
        val permissions = context.packageManager.getPackageInfo(target, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse("Fixture must not have Internet permission", "android.permission.INTERNET" in permissions)
        progress("afterTargetInfo")
        try {
            progress("beforeStart")
            context.startActivity(Intent().apply {
                component = ComponentName(target, "io.github.currencortex.music.benchmark.BenchmarkActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra("scene", "smoke")
            })
            progress("afterStart")
            progress("beforeWait")
            val completed = device.wait(
                Until.hasObject(By.desc(Pattern.compile("benchmark_(ready|unsupported)_smoke"))), 45_000,
            )
            progress("afterWait")
            assertTrue("Runtime smoke did not complete", completed)
            progress("beforeUnsupportedLookup")
            val unsupportedNode = device.findObject(By.desc("benchmark_unsupported_smoke"))
            progress("afterUnsupportedLookup")
            val unsupportedText = unsupportedNode?.text
            progress("afterUnsupportedText")
            val unsupported = device.hasObject(By.desc("benchmark_unsupported_smoke"))
            progress("afterUnsupportedCheck")
            assertFalse("Runtime smoke failed: $unsupportedText", unsupported)
            progress("beforeSummary")
            val summaryNode = device.findObject(By.desc("benchmark_result_smoke"))
            progress("afterSummaryLookup")
            val summary = summaryNode?.text.orEmpty()
            progress("afterSummary")
            assertTrue("Runtime smoke must expose a result summary", summary.isNotBlank())
            Log.i("CurrentMusicRuntimeSmoke", summary)
        } finally {
            progress("beforeHome")
            device.pressHome()
            progress("afterHome")
        }
    }
}
