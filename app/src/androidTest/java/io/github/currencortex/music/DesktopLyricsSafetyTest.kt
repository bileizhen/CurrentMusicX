package io.github.currencortex.music

import android.app.UiAutomation
import android.content.Intent
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.PowerManager
import android.provider.Settings
import android.view.Display
import android.view.WindowInsets
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.PlaybackLyricsState
import io.github.currencortex.music.feature.lyrics.desktop.*
import io.github.currencortex.music.feature.lyrics.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

@androidx.test.filters.SdkSuppress(minSdkVersion = 30)
class DesktopLyricsSafetyTest {
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private fun shell(command: String) = automation.executeShellCommand(command).use {
        java.io.FileInputStream(it.fileDescriptor).bufferedReader().use { r -> r.readText() }
    }
    private suspend fun await(condition: () -> Boolean) = withTimeout(10000) { while(!condition()) delay(50) }
    private suspend fun fixture(long: Boolean = false, body: suspend (AppContainer) -> Unit) {
        check(context.packageName.endsWith(".verification"))
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assumeTrue(Settings.canDrawOverlays(context))
        val container = AppContainer(context, "desktop-safety-${UUID.randomUUID()}", useNativeNetease = false)
        var host: DesktopLyricsHost? = null
        try {
            container.ready.await(); container.desktopLyrics.ready.await()
            val player = MutableStateFlow(PlayerState(song = Song(888, "窗口安全验证")))
            val lyrics = MutableStateFlow(PlaybackLyricsState(LyricsDocument(listOf(LyricLine(0, 4000,
                if(long) "保持字号换行的长歌词".repeat(40) else "触摸穿透验证"))), songId = 888))
            container.desktopLyrics.edit { it.copy(enabled = true, locked = true, opacity = .95f) }
            withContext(Dispatchers.Main) { host = DesktopLyricsHost(context, lyrics, player, container.desktopLyrics, container.desktopLyricsStatus) }
            await { container.desktopLyricsStatus.value.visible && container.desktopLyricsStatus.value.height > 0 }
            body(container)
        } finally { withContext(Dispatchers.Main) { host?.close() }; container.close() }
    }
    @Test fun rotatingLongWindowClampsToVisibleSafeArea(): Unit = runBlocking {
        val auto = Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1)
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val original = display.rotation
        val wm = context.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null).getSystemService(WindowManager::class.java)
        try { fixture(long = true) { container ->
            assertTrue(automation.setRotation(UiAutomation.ROTATION_FREEZE_90))
            await { wm.maximumWindowMetrics.bounds.width() > wm.maximumWindowMetrics.bounds.height() }
            delay(500)
            val metrics = wm.maximumWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val s = container.desktopLyricsStatus.value
            assertTrue(s.x >= metrics.bounds.left + insets.left && s.y >= metrics.bounds.top + insets.top)
            assertTrue(s.x + s.width <= metrics.bounds.right - insets.right)
            assertTrue(s.y + s.height <= metrics.bounds.bottom - insets.bottom)
        } } finally { automation.setRotation(if(auto == 1) UiAutomation.ROTATION_UNFREEZE else original) }
    }
    @Test fun lockedOverlayPassesInjectedTouchToTheForeignLauncher(): Unit = runBlocking {
        fixture { container ->
            shell("input keyevent 3"); delay(800)
            val root = automation.rootInActiveWindow
            assertTrue("Underlying focused window must be a foreign launcher", root.packageName != context.packageName)
            val target = root.findAccessibilityNodeInfosByText("CurrentMusic").lastOrNull()
            assumeTrue("Launcher must expose a CurrentMusic icon", target != null)
            val bounds = Rect(); target!!.getBoundsInScreen(bounds)
            val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            val wm = context.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null).getSystemService(WindowManager::class.java)
            val screen = wm.maximumWindowMetrics.bounds
            val x = bounds.centerX(); val y = bounds.centerY()
            container.desktopLyrics.edit { it.copy(x = x.toFloat()/screen.width(), y = y.toFloat()/screen.height()) }
            delay(400)
            val s = container.desktopLyricsStatus.value
            assertTrue(s.visible && s.alpha <= .8f)
            assertTrue("Touch must actually pass through overlay bounds", x in s.x..s.x+s.width && y in s.y..s.y+s.height)
            shell("input tap $x $y")
            await { automation.rootInActiveWindow?.packageName == context.packageName }
        }
    }
    @Test fun screenOffHidesWindowAndStopsItsVisibleLifecycle(): Unit = runBlocking {
        try { fixture { container ->
            shell("input keyevent 223")
            await { !context.getSystemService(PowerManager::class.java).isInteractive && !container.desktopLyricsStatus.value.visible }
            assertTrue(container.desktopLyricsStatus.value.attached)
        } } finally { shell("input keyevent 224") }
    }
}
