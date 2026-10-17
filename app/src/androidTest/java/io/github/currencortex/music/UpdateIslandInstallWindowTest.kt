package io.github.currencortex.music

import android.graphics.Bitmap
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.core.update.UpdateDownloadState
import io.github.currencortex.music.core.update.UpdateIslandNotice
import io.github.currencortex.music.feature.lyrics.data.PlaybackLyricsState
import io.github.currencortex.music.feature.lyrics.desktop.DesktopLyricsHost
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Native overlay input routing only; the install callback records taps without opening an installer. */
class UpdateIslandInstallWindowTest {
    @Test fun persistentReadyOverlayAcceptsInstallTap(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(app.packageName.endsWith(".verification"))
        assumeTrue(Settings.canDrawOverlays(app))
        val container = AppContainer(app, "update-install-${UUID.randomUUID()}", useNativeNetease = false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val notice = UpdateIslandNotice(MutableStateFlow(UpdateDownloadState.Ready(File("verified-fixture.apk"))),
            MutableStateFlow(true), scope)
        val installs = AtomicInteger()
        var host: DesktopLyricsHost? = null
        try {
            container.desktopLyrics.ready.await()
            withContext(Dispatchers.Main) {
                host = DesktopLyricsHost(app, MutableStateFlow(PlaybackLyricsState()), MutableStateFlow(PlayerState()),
                    container.desktopLyrics, container.desktopLyricsStatus, effectNotices = notice.state,
                    installUpdate = { installs.incrementAndGet() })
            }
            container.desktopLyrics.edit { it.copy(enabled = true, locked = true, islandY = .15f) }
            withTimeout(8_000) { while (!container.desktopLyricsStatus.value.islandTouchable) delay(25) }
            delay(5_000)
            val status = container.desktopLyricsStatus.value
            assertTrue(status.visible && status.islandTouchable)
            assertEquals("下载完成", notice.state.value?.name)
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(app.getExternalFilesDir(null), "update-ready-native.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }; bitmap.recycle()
            }
            val x = status.x + status.width / 2f
            val y = status.y + status.contentTop + status.contentHeight / 2f
            val start = SystemClock.uptimeMillis()
            for ((action, time) in listOf(MotionEvent.ACTION_DOWN to start, MotionEvent.ACTION_UP to start + 80)) {
                val event = MotionEvent.obtain(start, time, action, x, y, 0)
                assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)); event.recycle()
            }
            withTimeout(3_000) { while (installs.get() == 0) delay(25) }
            assertEquals(1, installs.get())
        } finally {
            withContext(Dispatchers.Main) { host?.close() }
            scope.cancel(); container.close()
        }
    }
}
