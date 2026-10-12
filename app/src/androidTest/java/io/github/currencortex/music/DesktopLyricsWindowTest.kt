package io.github.currencortex.music

import org.junit.Assume.assumeTrue
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
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
import org.junit.Test
import java.util.UUID

/** User grants the isolated package permission; this fixture never changes system app ops. */
class DesktopLyricsWindowTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use { p ->
        java.io.FileInputStream(p.fileDescriptor).bufferedReader().use { it.readText() }
    }
    @Test fun independentWindowSurvivesHomeAndRepeatedToggles(): Unit = runBlocking {
        check(context.packageName.endsWith(".verification"))
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        android.util.Log.i("DesktopEvidence", "window test started")
        assumeTrue("Grant the isolated app overlay permission in system settings first", Settings.canDrawOverlays(context))
        val container = AppContainer(context, "desktop-window-${UUID.randomUUID()}", useNativeNetease = false)
        var host: DesktopLyricsHost? = null
        try {
            withTimeout(15000) { container.ready.await(); container.desktopLyrics.ready.await() }
            android.util.Log.i("DesktopEvidence", "preferences ready")
            val song = Song(100, "悬浮歌词验证")
            val player = MutableStateFlow(PlayerState(song = song, positionMs = 1500, anchorRealtimeMs = SystemClock.elapsedRealtime()))
            val lyrics = MutableStateFlow(PlaybackLyricsState(LyricsDocument(listOf(LyricLine(0, 4000, "共享歌词数据"))), songId = 100))
            withContext(Dispatchers.Main) { host = DesktopLyricsHost(context, lyrics, player, container.desktopLyrics, container.desktopLyricsStatus) }
            container.desktopLyrics.edit { it.copy(enabled = true) }
            android.util.Log.i("DesktopEvidence", "enable requested")
            try { withTimeout(10000) { while (!container.desktopLyricsStatus.value.visible) delay(50) } }
            catch (e: Exception) { throw AssertionError("Window ${container.desktopLyricsStatus.value}; prefs=${container.desktopLyrics.state.value}; permission=${Settings.canDrawOverlays(context)}", e) }
            android.util.Log.i("DesktopEvidence", "overlay visible")
            shell("input keyevent 3")
            delay(300)
            assertTrue(container.desktopLyricsStatus.value.attached)
            assertTrue(container.desktopLyricsStatus.value.visible)
            assertTrue(container.desktopLyricsStatus.value.width > 1)
            container.desktopLyrics.edit { it.copy(locked = true, opacity = .95f) }
            withTimeout(10000) { while (container.desktopLyricsStatus.value.alpha > .8f) delay(50) }
            assertTrue(container.desktopLyricsStatus.value.alpha <= .8f)
            for (round in 0..2) {
                container.desktopLyrics.edit { it.copy(enabled = false) }
                withTimeout(10000) { while (container.desktopLyricsStatus.value.attached) delay(50) }
                container.desktopLyrics.edit { it.copy(enabled = true) }
                withTimeout(10000) { while (!container.desktopLyricsStatus.value.attached) delay(50) }
            }
            // Device-specific security settings can forbid adb/appops. Exercise the
            // real permission watcher with user-operated system UI when requested.
            if (InstrumentationRegistry.getArguments().getString("desktop_revoke_manual") == "true") {
                android.util.Log.i("DesktopEvidence", "Waiting for user permission revocation")
                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                withTimeout(120000) { while (Settings.canDrawOverlays(context)) delay(50) }
                withTimeout(10000) { while (container.desktopLyricsStatus.value.attached || container.desktopLyrics.state.value.enabled) delay(50) }
                android.util.Log.i("DesktopEvidence", "Revocation closed overlay; waiting for user to restore permission")
                withTimeout(120000) { while (!Settings.canDrawOverlays(context)) delay(50) }
                container.desktopLyrics.edit { it.copy(enabled = true) }
                withTimeout(10000) { while (!container.desktopLyricsStatus.value.attached) delay(50) }
            }
            withContext(Dispatchers.Main) { host?.close() }
            assertFalse(container.desktopLyricsStatus.value.attached)
            // A recreated host observes saved settings, without adding a duplicate window.
            withContext(Dispatchers.Main) { host = DesktopLyricsHost(context, lyrics, player, container.desktopLyrics, container.desktopLyricsStatus) }
            withTimeout(10000) { while (!container.desktopLyricsStatus.value.attached) delay(50) }

        } finally {
            withContext(Dispatchers.Main) { host?.close() }; container.close()

        }
    }
}
