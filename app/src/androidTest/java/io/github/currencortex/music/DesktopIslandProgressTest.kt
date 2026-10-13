package io.github.currencortex.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.PlaybackLyricsState
import io.github.currencortex.music.feature.lyrics.desktop.*
import io.github.currencortex.music.feature.lyrics.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class DesktopIslandProgressTest {
    @get:Rule val compose = createComposeRule()

    @Test fun islandProgressUsesPlaybackClockRatherThanLyricOffsetAndFreezesWhenPaused() {
        val lyrics = MutableStateFlow(PlaybackLyricsState(LyricsDocument(listOf(
            LyricLine(0, 300000, "Poured all my heart to you", translation = "我把一切的心都付诸于你"))), songId = 42))
        val player = MutableStateFlow(PlayerState(song = Song(42, "Progress", durationMs = 240000),
            positionMs = 120000, durationMs = 240000))
        val options = MutableStateFlow(DesktopLyricsSettings(locked = true, offsetMs = 60000))
        compose.setContent { Box(Modifier.width(360.dp)) { DesktopLyricsSurface(lyrics, player, options, {}, {}, {}) } }
        compose.mainClock.advanceTimeBy(2400)
        compose.waitUntil(5000) { compose.onAllNodesWithTag("desktop_island_lyrics").fetchSemanticsNodes()
            .any { it.config.getOrElse(DesktopIslandReveal) { 0f } == 1f } }
        fun progress() = compose.onNodeWithTag("desktop_island_progress")
        progress().assertContentDescriptionEquals("已播放 2:00")
        progress().assertRangeInfoEquals(ProgressBarRangeInfo(.5f, 0f..1f))
        val bounds = compose.onNodeWithTag("desktop_island").getUnclippedBoundsInRoot()
        val image = progress().captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(1000)
        val paused = progress().captureToImage().asAndroidBitmap()
        assertTrue("A paused clock freezes the progress and timestamp", image.sameAs(paused))
        image.recycle(); paused.recycle()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.onNodeWithTag("desktop_island").captureToImage().asAndroidBitmap().let { bitmap ->
            File(context.externalCacheDir, "island-progress.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        compose.runOnIdle { player.value = player.value.copy(positionMs = 180000, timelineRevision = 1) }
        progress().assertContentDescriptionEquals("已播放 3:00")
        progress().assertRangeInfoEquals(ProgressBarRangeInfo(.75f, 0f..1f))
        compose.runOnIdle { player.value = player.value.copy(positionMs = 60000, timelineRevision = 2) }
        progress().assertRangeInfoEquals(ProgressBarRangeInfo(.25f, 0f..1f))
        assertEquals("Time changes do not resize the island", bounds,
            compose.onNodeWithTag("desktop_island").getUnclippedBoundsInRoot())
        compose.runOnIdle { player.value = player.value.copy(song = Song(43, "Next", durationMs = 100000),
            positionMs = 0, durationMs = 100000, timelineRevision = 3) }
        progress().assertContentDescriptionEquals("已播放 0:00")
        progress().assertRangeInfoEquals(ProgressBarRangeInfo(0f, 0f..1f))
    }

    @Test fun progressFillsFromBottomAndHandlesMissingOrOutOfRangeTimes() {
        val position = mutableLongStateOf(25000)
        val duration = mutableLongStateOf(100000)
        compose.setContent { DesktopIslandProgress(position, duration.longValue,
            Modifier.width(desktopIslandProgressWidth).height(22.dp).background(Color.Black)) }
        fun node() = compose.onNodeWithTag("desktop_island_progress")
        fun bright(top: Boolean): Int {
            val pixels = node().captureToImage().toPixelMap()
            var count = 0
            val range = if (top) 0 until pixels.height / 2 else pixels.height / 2 until pixels.height
            for (y in range) for (x in 0 until (pixels.width * 3 / desktopIslandProgressWidth.value).toInt())
                if (pixels[x, y].red > .7f) count++
            return count
        }
        assertEquals("Unplayed upper track stays dim", 0, bright(true))
        assertTrue("Played segment begins at the bottom", bright(false) > 5)
        compose.runOnIdle { position.longValue = 75000 }
        assertTrue("Fill grows upward", bright(true) > 5)
        compose.runOnIdle { position.longValue = -1000 }
        node().assertContentDescriptionEquals("已播放 0:00")
        node().assertRangeInfoEquals(ProgressBarRangeInfo(0f, 0f..1f))
        assertEquals(0, bright(false))
        compose.runOnIdle { position.longValue = Long.MAX_VALUE }
        node().assertContentDescriptionEquals("已播放 1:40")
        node().assertRangeInfoEquals(ProgressBarRangeInfo(1f, 0f..1f))
        compose.runOnIdle { duration.longValue = 0; position.longValue = 120000 }
        node().assertContentDescriptionEquals("已播放 2:00")
        node().assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ProgressBarRangeInfo))
        assertEquals("Unknown duration has no fabricated fill", 0, bright(false))
    }
}
