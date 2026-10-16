package io.github.currencortex.music

import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/** Explicit opt-in: sample the actual display around one user-authorized use-button click.
 * Uses screenshot permission already supplied by Android instrumentation; no recording permission
 * or display/settings preference is changed. Navigation is restricted to the target app's nodes.
 */
class IslandTransitionRecordingTest {
    @Test fun captureActualUseButtonTransition() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recordIslandClick") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val context = instrumentation.targetContext
        fun nodes(): List<AccessibilityNodeInfo> {
            val found = mutableListOf<AccessibilityNodeInfo>()
            fun visit(node: AccessibilityNodeInfo?) {
                if (node == null) return
                if (node.packageName?.toString() == context.packageName && node.isVisibleToUser) found += node
                for (i in 0 until node.childCount) visit(node.getChild(i))
            }
            visit(automation.rootInActiveWindow)
            return found
        }
        fun tap(x: Float, y: Float) {
            val down = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0).also {
                    automation.injectInputEvent(it, true); it.recycle()
                }
            }
        }
        fun clickText(label: String) {
            val limit = SystemClock.elapsedRealtime() + 15_000
            while (SystemClock.elapsedRealtime() < limit) {
                val node = nodes().firstOrNull { it.text?.toString() == label }
                if (node != null && node.refresh()) {
                    var target: AccessibilityNodeInfo? = node
                    repeat(5) {
                        if (target?.isClickable == true && target!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            Thread.sleep(500); return
                        }
                        target = target?.parent
                    }
                    val bounds = Rect().also(node::getBoundsInScreen)
                    tap(bounds.exactCenterX(), bounds.exactCenterY()); Thread.sleep(500); return
                }
                Thread.sleep(200)
            }
            error("Missing active UI text: $label")
        }
        automation.executeShellCommand("am start -W -n ${context.packageName}/${MainActivity::class.java.name}").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
        }
        val launchLimit = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < launchLimit) {
            val current = nodes()
            if (current.any { it.text?.toString() == "知道了" }) { clickText("知道了"); break }
            if (current.any { it.text?.toString() == "设置" }) break
            Thread.sleep(200)
        }
        // Reconnect the existing enabled desktop island after instrumentation restarted the app.
        // This does not enable an overlay preference or send any playback command.
        kotlinx.coroutines.runBlocking {
            val container = (context.applicationContext as CurrentMusicApplication).awaitContainer()
            container.desktopLyrics.ready.await()
            if (container.desktopLyrics.state.value.enabled) container.playerController.connect()
        }
        clickText("设置"); clickText("网络与播放")
        repeat(8) {
            if (nodes().any { it.text?.toString() == "音效" }) return@repeat
            val bounds = Rect().also { automation.rootInActiveWindow.getBoundsInScreen(it) }
            val down = SystemClock.uptimeMillis()
            for (step in 0..12) {
                val action = when (step) { 0 -> MotionEvent.ACTION_DOWN; 12 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
                val y = bounds.bottom - bounds.height() * (.15f + .5f * step / 12)
                MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, bounds.left + bounds.width() * .85f, y, 0).also {
                    automation.injectInputEvent(it, true); it.recycle()
                }
                Thread.sleep(30)
            }
            Thread.sleep(400)
        }
        clickText("音效")
        val folder = File(context.getExternalFilesDir(null), "island-live-morph-${System.currentTimeMillis()}").apply { mkdirs() }
        var button: Rect? = null
        val deadline = SystemClock.elapsedRealtime() + 60_000
        while (button == null && SystemClock.elapsedRealtime() < deadline) {
            val current = nodes()
            val title = current.firstOrNull { it.text?.toString() == "原声黑胶" }
            if (title != null) {
                val bounds = Rect().also(title::getBoundsInScreen)
                button = current.filter { it.text?.toString() == "使用" }.map { Rect().also(it::getBoundsInScreen) }
                    .filter { it.top > bounds.bottom && it.top < bounds.bottom + 450 && kotlin.math.abs(it.centerX() - bounds.centerX()) < 160 }
                    .minByOrNull { it.top }
            }
            if (button == null) Thread.sleep(250)
        }
        checkNotNull(button) { "Open the real sound-effects page with the 原声黑胶 use button visible" }
        Thread.sleep(700)
        val start = SystemClock.elapsedRealtime()
        var clicked = false
        var index = 0
        File(folder, "frames.csv").bufferedWriter().use { log ->
            while (SystemClock.elapsedRealtime() - start < 8_500) {
                check(automation.rootInActiveWindow?.packageName?.toString() == context.packageName) {
                    "Target app left the foreground; recording stopped"
                }
                val elapsed = SystemClock.elapsedRealtime() - start
                if (!clicked && elapsed >= 900) {
                    val target = checkNotNull(button)
                    val down = SystemClock.uptimeMillis()
                    for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                        MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, target.centerX().toFloat(), target.centerY().toFloat(), 0).also {
                            automation.injectInputEvent(it, true); it.recycle()
                        }
                    }
                    clicked = true
                    log.write("click,$elapsed\n")
                }
                automation.takeScreenshot()?.let { bitmap ->
                    val name = String.format(Locale.ROOT, "%04d.jpg", index++)
                    File(folder, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, it) }
                    bitmap.recycle()
                    log.write("$name,$elapsed\n")
                }
                Thread.sleep(20)
            }
        }
        println("ISLAND_RECORDING=${folder.absolutePath};frames=$index")
    }
}
