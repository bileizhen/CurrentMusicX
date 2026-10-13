package io.github.currencortex.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.feature.lyrics.ui.*
import io.github.currencortex.music.feature.player.LyricsDisplaySettings
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class LyricsSmokeTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    private fun cover(color: Int): File = File(context.cacheDir, "smoke-${UUID.randomUUID()}.png").also { file ->
        val bitmap = android.graphics.Bitmap.createBitmap(32, 32, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    @Test fun smokeUsesRealCoverColorsFreezesWithMediaTimeAndDisablesWithoutMovingText() {
        val red = cover(0xffeb523b.toInt()); val blue = cover(0xff368eea.toInt())
        val cover = mutableStateOf(red.absolutePath)
        val enabled = mutableStateOf(true)
        val position = mutableLongStateOf(1280)
        val text = "烟雾里的音乐 Hello"
        val line = LyricLine(1000, 5000, text, listOf(LyricWord(text, 1000, 2200, 0, text.length)))
        var palette: LyricsSmokeColors? = null
        try {
            compose.setContent {
                val colors = rememberLyricsSmokeColors(cover.value)
                SideEffect { palette = colors }
                Box(Modifier.fillMaxSize().background(Color(0xff15151a)).testTag("smoke_preview"), contentAlignment = Alignment.Center) {
                    KaraokeText(line, position, true, Modifier.width(310.dp).testTag("smoke_text"), fontSize = 36f,
                        smokeColors = colors.takeIf { enabled.value })
                }
            }
            compose.waitUntil(8000) { palette?.primary?.let { it.red > it.blue + .3f } == true }
            compose.waitForIdle()
            compose.onNodeWithText(text, useUnmergedTree = true).assertExists()
            val bounds = compose.onNodeWithTag("smoke_text").getUnclippedBoundsInRoot()
            fun pixels() = compose.onNodeWithTag("smoke_preview").captureToImage().asAndroidBitmap()
            fun colored(red: Boolean): Int {
                val image = compose.onNodeWithTag("smoke_preview").captureToImage().toPixelMap()
                var count = 0
                for (y in 0 until image.height) for (x in 0 until image.width) {
                    val c = image[x, y]
                    if (if (red) c.red > c.blue + .08f else c.blue > c.red + .08f) count++
                }
                return count
            }
            assertTrue("Smoke should visibly carry the red cover", colored(true) > 150)
            val paused = pixels()
            compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
            assertTrue("Paused media time freezes the smoke", paused.sameAs(pixels()))
            listOf(1100L, 1280L, 1450L, 1750L, 5100L, 5400L).forEach { time ->
                compose.runOnIdle { position.longValue = time }
                pixels().let { bitmap -> File(context.externalCacheDir, "smoke-$time.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                } }
            }
            compose.runOnIdle { position.longValue = 1280; cover.value = blue.absolutePath }
            compose.waitUntil(8000) { palette?.primary?.let { it.blue > it.red + .3f } == true }
            compose.waitForIdle()
            assertTrue("Changing covers recolors the same lyrics", colored(false) > 150)
            compose.runOnIdle { enabled.value = false }
            compose.waitForIdle()
            assertEquals(bounds, compose.onNodeWithTag("smoke_text").getUnclippedBoundsInRoot())
            assertEquals("Disabled smoke leaves ordinary monochrome karaoke", 0, colored(false))
            compose.runOnIdle { enabled.value = true; position.longValue = 800 }
            assertEquals("Intro has no smoke", 0, colored(false))
            compose.runOnIdle { position.longValue = 5800 }
            assertEquals("No perpetual animation after the exit", 0, colored(false))
        } finally { red.delete(); blue.delete() }
    }
    @Test fun smokeEnablesCenterAndLocksOutPerspective() {
        val settings = mutableStateOf(LyricsDisplayOptions())
        compose.setContent { LeiTheme(AppearanceSettings()) {
            LyricsDisplaySettings(30f, {}, LyricsWeight.CURRENT, {}, settings.value,
                { transform -> settings.value = transform(settings.value) }, {})
        } }
        compose.onNodeWithTag("lyrics_smoke").performScrollTo().assertIsOff().performClick()
        compose.onNodeWithTag("lyrics_smoke").assertIsOn()
        assertTrue(settings.value.smoke); assertTrue(settings.value.glow); assertTrue(settings.value.wordLift)
        assertTrue(settings.value.centered); assertFalse(settings.value.perspective)
        compose.onNodeWithTag("lyrics_perspective").performScrollTo().assertIsOff().assertIsNotEnabled()
        compose.onNodeWithTag("lyrics_centered").performScrollTo().assertIsOn().assertIsNotEnabled()
        compose.onNodeWithTag("lyrics_smoke").performScrollTo()
        compose.onNodeWithTag("lyrics_smoke").performClick().assertIsOff()
        compose.onNodeWithTag("lyrics_perspective").performScrollTo().assertIsEnabled()
    }

    @Test fun referencePhraseFormsSequentiallyThenKeepsItsOriginalShape() {
        val position = mutableLongStateOf(1000)
        val enabled = mutableStateOf(true)
        val line = LyricLine(1000, 5000, "Out of thin air", emptyList())
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xff080808)), contentAlignment = Alignment.Center) {
                Box(Modifier.width(360.dp).height(130.dp).testTag("smoke_reference"), contentAlignment = Alignment.Center) {
                    KaraokeText(line, position, true, Modifier.fillMaxWidth(), fontSize = 36f, fontWeight = FontWeight.Bold,
                        smokeColors = if (enabled.value) LyricsSmokeColors(Color(0xff9180bd), Color(0xffada0d2)) else null)
                }
            }
        }
        fun frame() = compose.onNodeWithTag("smoke_reference").captureToImage().asAndroidBitmap()
        fun litPixels(bitmap: android.graphics.Bitmap): Int {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return pixels.count { android.graphics.Color.red(it) > 40 }
        }
        assertEquals("The forming phrase starts from empty space", 0, litPixels(frame()))
        val folder = File(context.externalCacheDir, "smoke-reference").apply { mkdirs() }
        repeat(61) { index ->
            compose.runOnIdle { position.longValue = 1000 + index * 50L }
            frame().let { bitmap -> File(folder, "frame-%03d.png".format(index)).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            } }
        }
        val settled = litPixels(frame())
        compose.runOnIdle { position.longValue = 1250 }
        assertTrue("Only the leading letters exist early in the entrance", litPixels(frame()) < settled / 2)
        compose.runOnIdle { position.longValue = 4000; enabled.value = false }
        val ordinary = litPixels(frame())
        assertTrue("Settled ink matches the ordinary paragraph", kotlin.math.abs(settled - ordinary) < ordinary * .02)
    }
}
