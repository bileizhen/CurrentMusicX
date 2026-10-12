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
        val autoRotation = Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1)
        val originalRotation = context.getSystemService(android.hardware.display.DisplayManager::class.java)
            .getDisplay(android.view.Display.DEFAULT_DISPLAY).rotation
        var host: DesktopLyricsHost? = null
        try {
            withTimeout(15000) { container.ready.await(); container.desktopLyrics.ready.await() }
            android.util.Log.i("DesktopEvidence", "preferences ready")
            val song = Song(100, "悬浮歌词验证")
            val player = MutableStateFlow(PlayerState(song = song, positionMs = 1500, anchorRealtimeMs = SystemClock.elapsedRealtime()))
            val lyrics = MutableStateFlow(PlaybackLyricsState(LyricsDocument(listOf(LyricLine(0, 4000, "共享歌词数据"))), songId = 100))
            withContext(Dispatchers.Main) { host = DesktopLyricsHost(context, lyrics, player, container.desktopLyrics, container.desktopLyricsStatus, container.desktopLyricsSettingsVisible) }
            container.desktopLyrics.edit { it.copy(enabled = true, appearance = DesktopAppearance.ISLAND) }
            android.util.Log.i("DesktopEvidence", "enable requested")
            try { withTimeout(10000) { while (!container.desktopLyricsStatus.value.visible) delay(50) } }
            catch (e: Exception) { throw AssertionError("Window ${container.desktopLyricsStatus.value}; prefs=${container.desktopLyrics.state.value}; permission=${Settings.canDrawOverlays(context)}", e) }
            android.util.Log.i("DesktopEvidence", "overlay visible")
            shell("input keyevent 3")
            delay(300)
            assertTrue(container.desktopLyricsStatus.value.attached)
            assertTrue(container.desktopLyricsStatus.value.visible)
            assertTrue(container.desktopLyricsStatus.value.width > 1)
            withTimeout(10000) { while (container.desktopLyricsStatus.value.height <= 0) delay(50) }
            val editHeight = container.desktopLyricsStatus.value.height
            val density = context.resources.displayMetrics.density
            assertTrue("Island width remains bounded", container.desktopLyricsStatus.value.width >= 160 * density)
            withTimeout(10000) { while (container.desktopLyricsStatus.value.height >= editHeight - 30 * density) delay(50) }
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.FileOutputStream(java.io.File(context.getExternalFilesDir(null), "desktop-island-portrait.png")).use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            val shortWidth = container.desktopLyricsStatus.value.contentWidth
            val fixedWindow = container.desktopLyricsStatus.value
            assertFalse("Idle island passes touches without changing the saved lock", fixedWindow.editing)
            assertFalse(container.desktopLyrics.state.value.locked)
            assertTrue(fixedWindow.alpha <= .8f)
            context.sendBroadcast(Intent(DesktopLyricsHost.UNLOCK).setPackage(context.packageName))
            withTimeout(10000) { while (!container.desktopLyricsStatus.value.editing) delay(50) }
            withTimeout(10000) { while (container.desktopLyricsStatus.value.editing) delay(50) }
            assertEquals(fixedWindow.x, container.desktopLyricsStatus.value.x)
            assertEquals(fixedWindow.width, container.desktopLyricsStatus.value.width)
            lyrics.value = PlaybackLyricsState(LyricsDocument(listOf(LyricLine(0, 4000, "共享歌词数据".repeat(8)))), songId = 100)
            withTimeout(10000) { while (container.desktopLyricsStatus.value.contentWidth <= shortWidth) delay(50) }
            delay(350) // Wait for the finite width transition before saving the rotation baseline.
            val portraitWidth = container.desktopLyricsStatus.value.width
            // The vendor launcher fixes its orientation. Rotate over the app instead.
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            delay(400)
            assertTrue(instrumentation.uiAutomation.setRotation(android.app.UiAutomation.ROTATION_FREEZE_90))
            withTimeout(10000) { while (container.desktopLyricsStatus.value.width <= portraitWidth) delay(50) }
            delay(600)
            val wide = container.desktopLyricsStatus.value
            assertTrue(wide.width <= (640 * context.resources.displayMetrics.density).toInt())
            val screenCenter = context.getSystemService(android.view.WindowManager::class.java).maximumWindowMetrics.bounds.centerX()
            assertTrue("Landscape island uses physical screen center despite a side cutout", kotlin.math.abs(wide.x + wide.width / 2 - screenCenter) <= 1)
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.FileOutputStream(java.io.File(context.getExternalFilesDir(null), "desktop-island-landscape.png")).use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            assertTrue(instrumentation.uiAutomation.setRotation(android.app.UiAutomation.ROTATION_FREEZE_0))
            withTimeout(10000) { while (container.desktopLyricsStatus.value.width > portraitWidth) delay(50) }
            android.util.Log.i("DesktopEvidence", "portrait restored: ${container.desktopLyricsStatus.value}")
            val centeredX = container.desktopLyricsStatus.value.x
            container.desktopLyrics.edit { it.copy(x = .01f, y = .9f, islandY = .35f) }
            withTimeout(10000) { while (container.desktopLyricsStatus.value.y <= 200 * density) delay(50) }
            assertEquals("Island ignores normal horizontal placement", centeredX, container.desktopLyricsStatus.value.x)
            android.util.Log.i("DesktopEvidence", "height updated: ${container.desktopLyricsStatus.value}")
            val wideWidth = container.desktopLyricsStatus.value.contentWidth
            lyrics.value = PlaybackLyricsState(LyricsDocument(listOf(LyricLine(0, 4000, "音乐"))), songId = 100)
            withTimeout(10000) { while (container.desktopLyricsStatus.value.contentWidth >= wideWidth) delay(50) }
            delay(350)
            val beforeSwitch = container.desktopLyricsStatus.value
            shell("dumpsys gfxinfo ${context.packageName} reset")
            player.value = player.value.copy(song = Song(101, "新歌曲"))
            delay(700) // Inspect the heading after its velocity-preserving resize settles.
            assertTrue("Heading-only width follows metadata rather than the previous lyric", container.desktopLyricsStatus.value.contentWidth >= 48 * density && container.desktopLyricsStatus.value.contentWidth < beforeSwitch.contentWidth)
            assertEquals("Loading does not move the top anchor", beforeSwitch.y, container.desktopLyricsStatus.value.y)
            assertTrue("Loading follows compact metadata height", container.desktopLyricsStatus.value.contentHeight < 45 * density)
            lyrics.value = PlaybackLyricsState(LyricsDocument(listOf(LyricLine(0, 8000, "音乐与你同在", translation = "The music stays with you"))), songId = 101)
            repeat(50) {
                delay(50)
                val frame = container.desktopLyricsStatus.value
                assertEquals("Cue changes never reposition the native surface", beforeSwitch.x, frame.x)
                assertEquals("Cue changes never resize the native surface width", beforeSwitch.width, frame.width)
                assertEquals("No vertical bounce while new lyrics resize", beforeSwitch.y, frame.y)
                assertTrue("Adaptive shell remains bounded", frame.height in (30 * density).toInt()..(70 * density).toInt())
                assertTrue("Every width transition stays centered", kotlin.math.abs(frame.x + frame.width / 2 - context.getSystemService(android.view.WindowManager::class.java).maximumWindowMetrics.bounds.centerX()) <= 2)
            }
            assertTrue("Translation grows the shell only when visible", container.desktopLyricsStatus.value.height > beforeSwitch.height + 5 * density)
            container.desktopLyrics.edit { it.copy(islandY = DESKTOP_ISLAND_HEIGHT) }
            shell("input keyevent 3")
            delay(350)
            java.io.File(context.getExternalFilesDir(null), "desktop-island-switch-framestats.txt")
                .writeText(shell("dumpsys gfxinfo ${context.packageName} framestats"))
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.FileOutputStream(java.io.File(context.getExternalFilesDir(null), "desktop-island-centered-bilingual.png")).use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            android.util.Log.i("DesktopEvidence", "short cue: ${container.desktopLyricsStatus.value}")
            val saved = container.desktopLyrics.state.value
            val heldPlayer = player.value; val heldLyrics = lyrics.value
            player.value = player.value.copy(song = null)
            delay(900)
            assertTrue("No-song FPS island remains visible over Home", container.desktopLyricsStatus.value.visible)
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.FileOutputStream(java.io.File(context.getExternalFilesDir(null), "desktop-island-fps.png")).use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }; bitmap.recycle()
            }
            player.value = heldPlayer; lyrics.value = heldLyrics
            container.desktopLyricsSettingsVisible.value = true
            withTimeout(10000) { while (container.desktopLyricsStatus.value.visible) delay(50) }
            assertTrue(container.desktopLyricsStatus.value.attached)
            assertEquals(saved, container.desktopLyrics.state.value)
            container.desktopLyricsSettingsVisible.value = false
            withTimeout(10000) { while (!container.desktopLyricsStatus.value.visible) delay(50) }
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
            instrumentation.uiAutomation.setRotation(if (autoRotation == 1) android.app.UiAutomation.ROTATION_UNFREEZE else originalRotation)

        }
    }
}
