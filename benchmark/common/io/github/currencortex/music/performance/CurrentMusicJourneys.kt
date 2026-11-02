package io.github.currencortex.music.performance

import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/** A strict local fixture contract. Missing controls fail instead of silently dropping coverage. */
object CurrentMusicJourneys {
    const val PACKAGE = "com.bileizhen.currentmusic.benchmark"
    private const val ACTIVITY = "io.github.currencortex.music.benchmark.BenchmarkActivity"
    private const val READY_TIMEOUT_MS = 15_000L

    val transitions = listOf("player", "play", "lyrics", "playlist", "settings", "output")

    fun openHome(scope: MacrobenchmarkScope) {
        check(scope.packageName == PACKAGE) { "Performance tests must use the isolated $PACKAGE app" }
        scope.startActivityAndWait(
            Intent().apply {
                component = ComponentName(PACKAGE, ACTIVITY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra("scene", "home")
            },
        )
        awaitReady(scope, "home")
    }

    fun openScene(scope: MacrobenchmarkScope, scene: String) {
        require(scene in transitions)
        val action = scope.device.wait(
            Until.findObject(By.desc("benchmark_action_$scene")),
            READY_TIMEOUT_MS,
        ) ?: error("Missing benchmark action: $scene")
        action.click()
        awaitReady(scope, scene)
        // Capture a full word/line animation interval, not just the page's initial composition.
        // This blocks the instrumentation thread only; playback and Compose continue normally.
        if (scene == "lyrics") SystemClock.sleep(2_000)
    }

    private fun awaitReady(scope: MacrobenchmarkScope, scene: String) {
        check(scope.device.wait(Until.hasObject(By.desc(Pattern.compile("benchmark_(ready|unsupported)_$scene"))), READY_TIMEOUT_MS)) {
            "Fixture did not complete scene: $scene"
        }
        check(!scope.device.hasObject(By.desc("benchmark_unsupported_$scene"))) {
            "Unsupported fixture scene $scene: ${scope.device.findObject(By.desc("benchmark_unsupported_$scene"))?.text}"
        }
    }
}
