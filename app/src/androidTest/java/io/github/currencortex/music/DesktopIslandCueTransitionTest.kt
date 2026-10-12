package io.github.currencortex.music

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
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Actual overlay/frame clock with explicit fixture word timestamps, not a Compose virtual clock. */
class DesktopIslandCueTransitionTest {
    @Test fun upcomingCuesKeepTheNativeWindowCenteredAndWithinItsHeightClass(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(context.packageName.endsWith(".verification"))
        assumeTrue(Settings.canDrawOverlays(context))
        fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use { p ->
            java.io.FileInputStream(p.fileDescriptor).bufferedReader().use { it.readText() }
        }
        val container = AppContainer(context, "island-cues-${UUID.randomUUID()}", useNativeNetease = false)
        val coverFile = java.io.File(context.cacheDir, "island-cue-cover.png")
        val bitmap = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bitmap).apply {
            drawColor(android.graphics.Color.BLUE)
            drawRect(0f, 0f, 48f, 96f, android.graphics.Paint().apply { color = android.graphics.Color.RED })
        }
        coverFile.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        var host: DesktopLyricsHost? = null
        var sampling: Job? = null
        try {
            withTimeout(15000) { container.ready.await(); container.desktopLyrics.ready.await() }
            val texts = listOf("音乐与你", "The music stays with you through every tomorrow", "風に乗って光の中へ", "光", "Hello 👩🏽‍🚀 e\u0301 音乐", "走过世界每一个角落")
            val document = LyricsDocument(texts.mapIndexed { i, text ->
                val start = if (i == 0) 0L else 4000L + (i - 1) * 2500L
                LyricLine(start, start + 2500, text, words = listOf(LyricWord(text, start, start + 2200, 0, text.length)),
                    translation = if (i % 2 == 0) "The music stays with you" else "音乐一直陪着你")
            })
            val anchor = SystemClock.elapsedRealtime()
            val player = MutableStateFlow(PlayerState(song = Song(700, "Continuous cues", cover = android.net.Uri.fromFile(coverFile).toString()), playing = true,
                durationMs = 17000, anchorRealtimeMs = anchor))
            // Match the real service's fresh playback anchors. A single stale
            // anchor deliberately stops extrapolating after 3s and cannot test six cues.
            sampling = launch {
                while (isActive) {
                    val now = SystemClock.elapsedRealtime()
                    player.value = player.value.copy(positionMs = now - anchor, anchorRealtimeMs = now)
                    delay(250)
                }
            }
            val lyrics = MutableStateFlow(PlaybackLyricsState(document, songId = 700))
            withContext(Dispatchers.Main) { host = DesktopLyricsHost(context, lyrics, player, container.desktopLyrics, container.desktopLyricsStatus) }
            container.desktopLyrics.edit { it.copy(enabled = true, locked = true, appearance = DesktopAppearance.ISLAND, effect = DesktopEffect.STAGE) }
            withTimeout(10000) { while (!container.desktopLyricsStatus.value.visible) delay(20) }
            shell("input keyevent 3")
            // The heading entrance ends before profiling successive timed cues.
            while (SystemClock.elapsedRealtime() - anchor < 3500) delay(20)
            shell("dumpsys gfxinfo ${context.packageName} reset")
            val density = context.resources.displayMetrics.density
            val samples = mutableListOf<String>()
            val top = container.desktopLyricsStatus.value.y
            val label = InstrumentationRegistry.getArguments().getString("island_frame_label") ?: "fixed"
            val fixedSurface = container.desktopLyricsStatus.value
            val contentWidthGetter = fixedSurface.javaClass.methods.firstOrNull { it.name == "getContentWidth" }
            val observedWidths = mutableSetOf<Int>()
            var largestCenterError = 0
            var smallestHeight = Int.MAX_VALUE; var largestHeight = 0
            while (SystemClock.elapsedRealtime() - anchor < 16500) {
                val frame = container.desktopLyricsStatus.value
                val center = context.getSystemService(android.view.WindowManager::class.java).maximumWindowMetrics.bounds.centerX()
                largestCenterError = maxOf(largestCenterError, kotlin.math.abs(frame.x + frame.width / 2 - center))
                smallestHeight = minOf(smallestHeight, frame.height); largestHeight = maxOf(largestHeight, frame.height)
                assertEquals("Each cue keeps its top anchor", top, frame.y)
                if (label != "baseline") {
                    assertEquals("Cue transitions retain the native surface origin", fixedSurface.x, frame.x)
                    assertEquals("Cue transitions retain the native surface width", fixedSurface.width, frame.width)
                }
                val contentWidth = (contentWidthGetter?.invoke(frame) as? Int) ?: frame.width
                observedWidths += contentWidth
                samples += "${SystemClock.elapsedRealtime() - anchor},${frame.x},${frame.y},${frame.width},${frame.height},$contentWidth"
                delay(20)
            }
            java.io.File(context.getExternalFilesDir(null), "island-cues-$label-frames.txt").writeText(shell("dumpsys gfxinfo ${context.packageName} framestats"))
            java.io.File(context.getExternalFilesDir(null), "island-cues-$label-placement.csv").writeText("timeMs,x,y,width,height,contentWidth\n" + samples.joinToString("\n"))
            assertTrue("The fixture must actually render multiple cue widths", observedWidths.size >= 3)
            assertTrue("Every timed cue remains horizontally centered", largestCenterError <= 2)
            assertTrue("Bilingual cues remain compact", largestHeight <= 70 * density)
            assertTrue("Changing language must not produce a large vertical jump", largestHeight - smallestHeight < 8 * density)
        } finally {
            sampling?.cancelAndJoin()
            withContext(Dispatchers.Main) { host?.close() }
            container.close()
            coverFile.delete()
        }
    }
}
