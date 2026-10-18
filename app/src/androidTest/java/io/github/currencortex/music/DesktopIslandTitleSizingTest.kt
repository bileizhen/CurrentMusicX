package io.github.currencortex.music

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.PlaybackLyricsState
import io.github.currencortex.music.feature.lyrics.desktop.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DesktopIslandTitleSizingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun headingFitsTrackNamesAndUsesOnlyTheirNaturalWidth() {
        val player = MutableStateFlow(PlayerState(song = Song(42, "Ravara (Her)", artists = "BeMax")))
        val lyrics = MutableStateFlow(PlaybackLyricsState(loading = true))
        val settings = MutableStateFlow(DesktopLyricsSettings(locked = true, appearance = DesktopAppearance.ISLAND))
        compose.mainClock.autoAdvance = false
        compose.setContent { Box(Modifier.width(340.dp)) {
            DesktopLyricsSurface(lyrics, player, settings, {}, {}, {}, maxIslandWidth = { 340f })
        } }
        fun settleHeading() { compose.mainClock.advanceTimeBy(1000) }
        fun width() = compose.onNodeWithTag("desktop_island").getUnclippedBoundsInRoot().let { (it.right - it.left).value }
        fun assertFits(tag: String) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag(tag).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse("$tag must fit its measured text without an early ellipsis", layouts.single().isLineEllipsized(0))
            val bounds = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            val pill = compose.onNodeWithTag("desktop_island").getUnclippedBoundsInRoot()
            assertTrue("$tag stays inside the capsule", bounds.right <= pill.right)
        }
        settleHeading()
        assertFits("desktop_island_title")
        assertFits("desktop_island_artist")
        val regular = width()
        snapshot("regular")
        compose.runOnIdle { player.value = player.value.copy(song = Song(43, "光", artists = "A")) }
        settleHeading()
        assertFits("desktop_island_title")
        val short = width()
        snapshot("short")
        assertTrue("Short names shrink the capsule", short < regular - 20f)
        compose.runOnIdle { player.value = player.value.copy(song = Song(44, "音乐与你 Hello 👩🏽‍🚀", artists = "Singer")) }
        settleHeading()
        assertFits("desktop_island_title")
        snapshot("mixed")
        assertTrue("Longer names expand the capsule", width() > short + 30f)
        compose.runOnIdle { player.value = player.value.copy(song = Song(46, "光", artists = "A longer artist collaboration")) }
        settleHeading()
        assertFits("desktop_island_artist")
        compose.runOnIdle { player.value = player.value.copy(song = Song(45, "A very long song title 音乐与你 ".repeat(8), artists = "Singer")) }
        settleHeading()
        assertTrue("Very long titles stay inside the available screen width", width() <= 340f)
        compose.onNodeWithTag("desktop_island_title").assertIsDisplayed()
        snapshot("long")
    }

    @Test fun largerSystemTextKeepsTheCoverAndBothLabelsInsideThePill() {
        val player = MutableStateFlow(PlayerState(song = Song(42, "Ravara (Her)", artists = "BeMax")))
        val lyrics = MutableStateFlow(PlaybackLyricsState(loading = true))
        val settings = MutableStateFlow(DesktopLyricsSettings(locked = true, appearance = DesktopAppearance.ISLAND))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                Box(Modifier.width(220.dp)) {
                    DesktopLyricsSurface(lyrics, player, settings,
                        {}, {}, {}, maxIslandWidth = { 220f })
                }
            }
        }
        compose.mainClock.advanceTimeBy(1000)
        val pill = compose.onNodeWithTag("desktop_island").getUnclippedBoundsInRoot()
        val cover = compose.onNodeWithTag("desktop_island_cover").getUnclippedBoundsInRoot()
        for (tag in listOf("desktop_island_title", "desktop_island_artist")) {
            val layouts = mutableListOf<TextLayoutResult>()
            val node = compose.onNodeWithTag(tag)
            node.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse("Large-font $tag must fit", layouts.single().isLineEllipsized(0))
            val label = node.getUnclippedBoundsInRoot()
            assertTrue(label.left >= cover.right && label.right <= pill.right)
        }
        snapshot("large")
    }

    private fun snapshot(name: String) {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        java.io.File(context.getExternalFilesDir(null), "island-title-$name.png").outputStream().use {
            compose.onNodeWithTag("desktop_island").captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
