package io.github.currencortex.music

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.data.settings.LyricsDisplayOptions
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.feature.lyrics.ui.LyricsScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class LyricsPerspectiveTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun projectedNearEdgeKeepsCenteredAndDuetParagraphsInsideTheViewportAtMaximumFontSize() {
        val centered = mutableStateOf(false)
        val position = mutableLongStateOf(1500)
        val line = LyricLine(1000, 10000, "MMMMM MMMMM MMMMM MMMMM", isDuet = true,
            translation = "长句的翻译也留在可见区域内")
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                LyricsScreen(LyricsDocument(listOf(line)), position, {},
                    Modifier.width(340.dp).height(360.dp), fontSize = 40f, effects = false,
                    minimal = true, perspective = true, display = LyricsDisplayOptions(centered = centered.value,
                        stagger = false, glow = false, wordLift = false))
            }
        }
        fun assertClearNearEdge() {
            val pixels = compose.onNodeWithTag("lyrics_panel").captureToImage().toPixelMap()
            var lastInk = 0
            for (x in 0 until pixels.width) for (y in 0 until pixels.height)
                if (pixels[x, y].red > .04f) lastInk = maxOf(lastInk, x)
            assertTrue("The enlarged near edge must not cut off a wrapped paragraph: ink=$lastInk width=${pixels.width}",
                lastInk > pixels.width / 2 && lastInk < pixels.width - 8)
        }
        compose.waitForIdle()
        assertClearNearEdge()
        compose.runOnIdle { centered.value = true }
        compose.waitForIdle()
        assertClearNearEdge()
    }

    @Test fun portraitActuallyProjectsOpposingSlopesAndTheSwitchRestoresFlatGlyphs() {
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(5000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        val perspective = mutableStateOf(true)
        val position = mutableLongStateOf(7500)
        val document = LyricsDocument((0..8).map { LyricLine(1000L + it * 2000, 3000L + it * 2000, "MMMMMMMM") })
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                LyricsScreen(document, position, {}, Modifier.width(360.dp).height(560.dp),
                    fontSize = 28f, effects = false, perspective = perspective.value,
                    display = LyricsDisplayOptions(stagger = false, glow = false, wordLift = false))
            }
        }
        compose.waitForIdle()
        fun slope(index: Int): Double {
            // Measure the actual rendered ink, not an angle reported by semantics.
            val pixels = compose.onNodeWithTag("lyric_vocal_$index", useUnmergedTree = true).captureToImage().toPixelMap()
            fun center(start: Int, end: Int): Double {
                var weight = 0.0; var sum = 0.0
                for (x in start until end) for (y in 0 until pixels.height) {
                    val ink = pixels[x, y].red.toDouble()
                    weight += ink; sum += ink * y
                }
                assertTrue("The projected lyric must have visible ink", weight > 1)
                return sum / weight
            }
            return center(pixels.width / 2, pixels.width) - center(0, pixels.width / 2)
        }
        fun nearToFarGlyphSize(): Double {
            val pixels = compose.onNodeWithTag("lyric_vocal_3", useUnmergedTree = true).captureToImage().toPixelMap()
            fun inkSpread(start: Int, end: Int): Double {
                var weight = 0.0; var sum = 0.0; var squares = 0.0
                for (x in start until end) for (y in 0 until pixels.height) {
                    val ink = pixels[x, y].red.toDouble()
                    weight += ink; sum += ink * y; squares += ink * y * y
                }
                assertTrue(weight > 1)
                return kotlin.math.sqrt((squares / weight - (sum / weight) * (sum / weight)).coerceAtLeast(0.0))
            }
            return inkSpread(pixels.width / 2, pixels.width) / inkSpread(0, pixels.width / 2)
        }
        val above = slope(2); val current = slope(3); val below = slope(4)
        android.util.Log.i("LyricsPerspectiveEvidence", "ink slopes above=$above current=$current below=$below; " +
            (2..4).joinToString { "$it: ${compose.onNodeWithTag("lyric_line_$it").getUnclippedBoundsInRoot()}" })
        File(compose.activity.filesDir, "lyrics-perspective-portrait.png").outputStream().use {
            compose.onNodeWithTag("lyrics_panel").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        assertTrue("The preceding lyric must slope upward: $above", above < -6)
        assertEquals("The reading line remains approximately level", 0.0, current, 5.0)
        assertTrue("The upcoming lyric must slope downward: $below", below > 6)
        assertTrue("A real perspective plane enlarges the nearer glyphs, rather than merely rolling each row",
            nearToFarGlyphSize() > 1.12)
        compose.runOnIdle { perspective.value = false }
        compose.waitForIdle()
        assertEquals("Disabling 3D flattens the preceding lyric", 0.0, slope(2), 3.0)
        assertEquals("Disabling 3D flattens the upcoming lyric", 0.0, slope(4), 3.0)
        assertEquals("Flat text has equally sized near and far glyphs", 1.0, nearToFarGlyphSize(), .08)
    }
}
