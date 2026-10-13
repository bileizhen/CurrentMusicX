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
    @Test fun realIslandTrackChangesKeepIntermediateHeightsWithoutMovingTheNativeWindow(): Unit = runBlocking {
        check(context.packageName.endsWith(".verification"))
        assumeTrue("Grant the isolated app overlay permission in system settings first", Settings.canDrawOverlays(context))
        val container = AppContainer(context, "island-arc-${UUID.randomUUID()}", useNativeNetease = false)
        var host: DesktopLyricsHost? = null
        try {
            container.ready.await(); container.desktopLyrics.ready.await()
            fun cover(name: String, color: Int): String {
                val file = java.io.File(context.cacheDir, name)
                val bitmap = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(color)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawCircle(22f, 22f, 10f, android.graphics.Paint().apply { this.color = android.graphics.Color.WHITE })
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle(); return android.net.Uri.fromFile(file).toString()
            }
            val red = cover("island-live-red.png", android.graphics.Color.RED)
            val blue = cover("island-live-blue.png", android.graphics.Color.BLUE)
            fun song(id: Long) = Song(id, if (id % 2L == 0L) "圆形封面沿弧线翻转" else "连续切歌", artists = "CurrentMusic", cover = if (id % 2L == 0L) blue else red)
            fun document(id: Long) = PlaybackLyricsState(LyricsDocument(listOf(LyricLine(0, 10000,
                "圆形封面与双行歌词", translation = "The circular cover follows an arc"))), songId = id)
            val player = MutableStateFlow(PlayerState(song = song(1), positionMs = 1500))
            val lyrics = MutableStateFlow(document(1))
            withContext(Dispatchers.Main) { host = DesktopLyricsHost(context, lyrics, player, container.desktopLyrics,
                container.desktopLyricsStatus, container.desktopLyricsSettingsVisible) }
            container.desktopLyrics.edit { it.copy(enabled = true, locked = true, appearance = DesktopAppearance.ISLAND,
                islandFontSize = 24f, islandY = .25f) }
            shell("input keyevent 3")
            val density = context.resources.displayMetrics.density
            withTimeout(10000) { while (!container.desktopLyricsStatus.value.visible || container.desktopLyricsStatus.value.contentHeight < 48 * density) delay(50) }
            delay(600)
            val samples = StringBuilder("track,elapsed_ms,height_px,native_height_px\n")
            for (id in 2L..4L) {
                val before = container.desktopLyricsStatus.value
                val heights = mutableSetOf<Int>()
                val started = SystemClock.elapsedRealtime()
                withContext(Dispatchers.Main) {
                    player.value = player.value.copy(song = song(id))
                    lyrics.value = PlaybackLyricsState(songId = id, loading = true)
                }
                var firstChanged: Int? = null
                while (SystemClock.elapsedRealtime() - started < 700) {
                    val s = container.desktopLyricsStatus.value
                    heights += s.contentHeight
                    val elapsed = SystemClock.elapsedRealtime() - started
                    samples.append("$id,$elapsed,${s.contentHeight},${s.height}\n")
                    assertEquals("The arc never moves the native window", before.x to before.y, s.x to s.y)
                    assertEquals("The arc never resizes the native width", before.width, s.width)
                    if (elapsed < 350) assertEquals("The flight holds the native buffer height", before.height, s.height)
                    if (firstChanged == null && s.contentHeight != before.contentHeight) firstChanged = s.contentHeight
                    delay(16)
                }
                assertTrue("The first heading frame cannot jump directly to the small cover", firstChanged != null && firstChanged!! > before.contentHeight - 3 * density)
                assertTrue("The live size transition has multiple intermediate frames", heights.size >= 5)
                lyrics.value = document(id)
                delay(2500)
            }
            java.io.File(context.getExternalFilesDir(null), "island-arc-live-heights.csv").writeText(samples.toString())
        } finally { withContext(Dispatchers.Main) { host?.close() }; container.close() }
    }
    @Test fun idleAndLockedIslandAcceptRealTapsAndSwipesAndRemoveInputWhenHiddenOrClosed(): Unit = runBlocking {
        check(context.packageName.endsWith(".verification"))
        assumeTrue("Grant the isolated app overlay permission in system settings first", Settings.canDrawOverlays(context))
        val container = AppContainer(context, "island-tap-${UUID.randomUUID()}", useNativeNetease = false)
        var host: DesktopLyricsHost? = null
        try {
            container.ready.await(); container.desktopLyrics.ready.await()
            val player = MutableStateFlow(PlayerState(song = Song(100, "点击验证"), positionMs = 1500))
            val lyrics = MutableStateFlow(PlaybackLyricsState(LyricsDocument(listOf(LyricLine(0, 10000, "点击切换歌词与帧率"))), songId = 100))
            val directions = mutableListOf<Int>()
            val playToggles = java.util.concurrent.atomic.AtomicInteger()
            withContext(Dispatchers.Main) { host = DesktopLyricsHost(context, lyrics, player, container.desktopLyrics,
                container.desktopLyricsStatus, container.desktopLyricsSettingsVisible, changeSong = { directions += it },
                togglePlayback = { playToggles.incrementAndGet(); player.value = player.value.copy(playing = !player.value.playing) }) }
            container.desktopLyrics.edit { it.copy(enabled = true, locked = false, appearance = DesktopAppearance.ISLAND, islandY = .25f) }
            shell("input keyevent 3")
            suspend fun await(label: String, condition: (DesktopWindowStatus) -> Boolean) {
                try { withTimeout(10000) { while (!condition(container.desktopLyricsStatus.value)) delay(50) } }
                catch (failure: TimeoutCancellationException) { throw AssertionError("$label: ${container.desktopLyricsStatus.value}", failure) }
            }
            fun tap() {
                val s = container.desktopLyricsStatus.value
                shell("input tap ${s.x + s.width / 2} ${s.y + s.height - s.contentHeight / 2}")
            }
            fun tapCover() {
                val s = container.desktopLyricsStatus.value
                val x = s.x + (s.width - s.contentWidth) / 2 + s.contentHeight / 2 - 4 * context.resources.displayMetrics.density
                shell("input tap ${x.toInt()} ${s.y + s.height - s.contentHeight / 2}")
            }
            suspend fun awaitInputWindowRemoved() {
                withTimeout(5000) {
                    while (shell("dumpsys window windows").lineSequence().any {
                        it.trimStart().startsWith("Window #") && it.contains("CurrentMusic 灵动岛点击")
                    }) delay(100)
                }
            }
            fun image(name: String) {
                val s = container.desktopLyricsStatus.value
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    val cropped = android.graphics.Bitmap.createBitmap(bitmap,
                        s.x.coerceAtLeast(0), s.y.coerceAtLeast(0), s.width.coerceAtMost(bitmap.width - s.x), s.height.coerceAtMost(bitmap.height - s.y))
                    java.io.FileOutputStream(java.io.File(context.getExternalFilesDir(null), name)).use {
                        cropped.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    cropped.recycle(); bitmap.recycle()
                }
            }
            await("Initial idle input") { it.visible && it.islandTouchable && !it.editing && it.contentHeight > 0 }
            assertFalse(container.desktopLyricsStatus.value.islandFps)
            val fixed = container.desktopLyricsStatus.value.let { it.x to it.width }
            delay(500) // Let the native buffer settle after removing the editing row.
            image("island-tap-lyrics.png")
            tapCover(); await("Idle cover plays without FPS") { playToggles.get() == 1 && !it.islandFps }
            assertTrue(player.value.playing)
            tapCover(); await("Idle cover pauses without FPS") { playToggles.get() == 2 && !it.islandFps }
            assertFalse(player.value.playing)
            tap(); await("Idle tap FPS") { it.islandFps && it.islandTouchable }
            delay(700)
            image("island-tap-fps.png")
            tap(); await("Idle tap lyrics") { !it.islandFps && it.islandTouchable }
            assertEquals("Toggling never moves the visual window", fixed, container.desktopLyricsStatus.value.let { it.x to it.width })
            lyrics.value = PlaybackLyricsState(LyricsDocument(listOf(LyricLine(0, 10000, "纯音乐，请欣赏"))), songId = 100)
            await("Instrumental FPS") { it.islandFps }
            repeat(2) { tap(); delay(200); assertTrue(container.desktopLyricsStatus.value.islandFps) }
            lyrics.value = PlaybackLyricsState(LyricsDocument(), songId = 100)
            tap(); delay(200); assertTrue(container.desktopLyricsStatus.value.islandFps)
            container.desktopLyrics.edit { it.copy(locked = true) }
            await("Locked input remains") { it.islandTouchable && !it.editing }
            lyrics.value = PlaybackLyricsState(LyricsDocument(listOf(LyricLine(0, 10000, "锁定仍可切换"))), songId = 100)
            await("Lyrics return while locked") { !it.islandFps }
            tap(); await("Locked tap FPS") { it.islandFps }
            delay(500)
            tapCover(); await("Locked cover preserves FPS mode") { playToggles.get() == 3 && it.islandFps }
            tapCover(); await("Locked cover pauses") { playToggles.get() == 4 && it.islandFps }
            tap(); await("Locked tap lyrics") { !it.islandFps }
            fun swipe(left: Boolean) {
                val s = container.desktopLyricsStatus.value
                val distance = (s.contentWidth * .35f).toInt()
                val x = s.x + s.width / 2; val y = s.y + s.height - s.contentHeight / 2
                shell("input swipe ${x + if (left) distance else -distance} $y ${x + if (left) -distance else distance} $y 250")
            }
            swipe(true); swipe(false)
            withContext(Dispatchers.Main) { assertEquals(listOf(1, -1), directions) }
            player.value = player.value.copy(canControlPlayback = false)
            tapCover(); delay(200); assertEquals("Unavailable playback is ignored", 4, playToggles.get())
            assertFalse(container.desktopLyricsStatus.value.islandFps)
            swipe(true)
            withContext(Dispatchers.Main) { assertEquals(2, directions.size) }
            assertEquals(fixed, container.desktopLyricsStatus.value.let { it.x to it.width })
            container.desktopLyrics.edit { it.copy(locked = false) }
            await("Unlock editor") { it.editing }
            await("Unlock idle input") { it.islandTouchable }
            container.desktopLyricsSettingsVisible.value = true
            await("Settings hide") { !it.visible && !it.islandTouchable }
            container.desktopLyricsSettingsVisible.value = false
            await("Settings restore") { it.visible && it.islandTouchable }
            container.desktopLyrics.edit { it.copy(enabled = false) }
            await("Close removes input") { !it.attached && !it.islandTouchable }
            awaitInputWindowRemoved()
        } finally { withContext(Dispatchers.Main) { host?.close() }; container.close() }
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
            assertFalse("Idle island hides editing without changing the saved lock", fixedWindow.editing)
            assertTrue("The compact pill retains its tap target", fixedWindow.islandTouchable)
            assertFalse(container.desktopLyrics.state.value.locked)
            assertTrue(fixedWindow.alpha <= .8f)
            container.desktopLyrics.edit { it.copy(islandOpacity = .62f) }
            withTimeout(10000) { while (kotlin.math.abs(container.desktopLyricsStatus.value.alpha - .62f) > .001f) delay(50) }
            assertEquals(.85f, container.desktopLyrics.state.value.opacity, .001f)
            container.desktopLyrics.edit { it.copy(islandOpacity = .85f) }
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
