package io.github.currencortex.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.feature.lyrics.ui.*
import io.github.currencortex.music.feature.player.LyricsDisplaySettings
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class LyricsParticlesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun driftingParticlesFadeBeforeTheViewportEdgeWithoutFadingSettledInk() {
        val source = android.graphics.Bitmap.createBitmap(320, 200, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(source).drawRect(0f, 100f, 320f, 160f,
            android.graphics.Paint().apply { color = android.graphics.Color.WHITE })
        val renderer = LyricsParticleInk(listOf(SmokeGlyph(Rect(0f, 100f, 320f, 160f), 1000, 1000)), source.asImageBitmap())
        fun render(now: Long, exitAt: Long? = null): android.graphics.Bitmap {
            val bitmap = android.graphics.Bitmap.createBitmap(320, 200, android.graphics.Bitmap.Config.ARGB_8888)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(320f, 200f)) {
                with(renderer) { draw(now, LyricsSmokeColors(Color(0xffa291db), Color(0xff7880d8)), Color.White, exitAt) }
            }
            return bitmap
        }
        fun opacity(bitmap: android.graphics.Bitmap, left: Int, right: Int): Double {
            var total = 0L
            for (y in 0 until bitmap.height) for (x in left until right)
                total += android.graphics.Color.alpha(bitmap.getPixel(x, y))
            return total.toDouble() / ((right - left) * bitmap.height)
        }
        val incoming = render(1500)
        val outgoing = render(5300, 5000)
        for (cloud in listOf(incoming, outgoing)) {
            assertEquals("Particles disappear before reaching the clipping boundary", 0.0, opacity(cloud, 0, 2), .01)
            val edge = opacity(cloud, 2, 10)
            val transition = opacity(cloud, 14, 22)
            val interior = opacity(cloud, 32, 48)
            assertTrue("Particle opacity ramps up through the edge band: $edge / $transition / $interior",
                edge > 0 && transition > edge * 2 && interior > transition)
        }
        assertTrue("Settled text keeps its exact ink even at the viewport edge", source.sameAs(render(2500)))
        assertTrue("A paused particle frame is deterministic", outgoing.sameAs(render(5300, 5000)))
    }

    @Test fun stageKeepsCurrentCenteredAndClearsOutgoingOnSeekAndModeSwitch() {
        val time = mutableLongStateOf(3000)
        val auxiliary = mutableStateOf(true)
        val particles = mutableStateOf(true)
        val document = LyricsDocument(listOf(LyricLine(1000, 5000, "星尘里的旋律", translation = "Melody in stardust"),
            LyricLine(5000, 9000, "让每个字慢慢凝聚", translation = "Let each letter form", romanization = "rang mei ge zi"),
            LyricLine(9000, 13000, "下一句预览")))
        compose.setContent {
            LyricsScreen(document, time, {}, Modifier.width(360.dp).height(600.dp).background(Color(0xff111722)),
                translation = auxiliary.value, romanization = auxiliary.value,
                display = LyricsDisplayOptions(particles = particles.value, smoke = !particles.value))
        }
        compose.runOnIdle { time.longValue = 5450 }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(600)
        val panel = compose.onNodeWithTag("lyrics_panel").getUnclippedBoundsInRoot()
        val current = compose.onNodeWithTag("lyric_line_1").getUnclippedBoundsInRoot()
        assertEquals((panel.top.value + panel.bottom.value) / 2, (current.top.value + current.bottom.value) / 2, 1f)
        val paused = compose.onNodeWithTag("lyrics_panel").captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(1200)
        assertTrue(paused.sameAs(compose.onNodeWithTag("lyrics_panel").captureToImage().asAndroidBitmap()))
        compose.runOnIdle { auxiliary.value = false }
        compose.mainClock.advanceTimeBy(600)
        assertEquals(current, compose.onNodeWithTag("lyric_line_1").getUnclippedBoundsInRoot())
        compose.runOnIdle { particles.value = false }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { particles.value = true; time.longValue = 3000 }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("lyric_line_1").assertDoesNotExist()
        val back = compose.onNodeWithTag("lyric_line_0").getUnclippedBoundsInRoot()
        assertEquals((panel.top.value + panel.bottom.value) / 2, (back.top.value + back.bottom.value) / 2, 1f)
        compose.runOnIdle { time.longValue = 10000 }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("lyric_line_2").assertExists()
        compose.onNodeWithTag("smoke_next_line").assertDoesNotExist()
    }

    @Test fun particlesAreAnIndependentPersistedChoiceAndSmokeCanBeRestored() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val container = AppContainer(context, "particles-${UUID.randomUUID()}")
        try {
            container.ready.await()
            container.musicSettings.editLyricsDisplay { it.copy(smoke = true) }
            val config = mutableStateOf(container.musicSettings.snapshot().lyricsDisplay)
            compose.setContent { LeiTheme(AppearanceSettings()) {
                LyricsDisplaySettings(30f, {}, LyricsWeight.CURRENT, {}, config.value,
                    { transform -> config.value = transform(config.value) }, {})
            } }
            compose.onNodeWithTag("lyrics_particles").performScrollTo().assertIsOff().performClick().assertIsOn()
            assertTrue(config.value.particles); assertFalse(config.value.smoke)
            assertTrue(config.value.centered); assertFalse(config.value.perspective)
            container.musicSettings.editLyricsDisplay { config.value }
            assertTrue(container.musicSettings.snapshot().lyricsDisplay.particles)
            compose.onNodeWithTag("lyrics_smoke").performScrollTo().performClick().assertIsOn()
            assertTrue(config.value.smoke); assertFalse(config.value.particles)
            container.musicSettings.editLyricsDisplay { config.value }
            assertFalse(container.musicSettings.snapshot().lyricsDisplay.particles)
        } finally { container.close() }
    }

    @Test fun grainFormsRealGlyphsDissolvesAndFreezesWithMediaTime() {
        val time = mutableLongStateOf(1000)
        val enabled = mutableStateOf(true)
        val palette = mutableStateOf(LyricsSmokeColors(Color(0xffa291db), Color(0xff7880d8)))
        val line = LyricLine(1000, 5000, "Inspire")
        val next = LyricLine(5000, 9000, "Imagine")
        compose.setContent {
            Box(Modifier.size(360.dp, 170.dp).background(Color(0xff080808)).testTag("particle_preview"), contentAlignment = Alignment.Center) {
                if (time.longValue < 6000) KaraokeText(line, time, false, Modifier.fillMaxWidth(), fontSize = 40f,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    particleColors = palette.value.takeIf { enabled.value }, particleExitAt = 5000L.takeIf { time.longValue >= 5000 })
                if (time.longValue >= 5000) KaraokeText(next, time, false, Modifier.fillMaxWidth(), fontSize = 40f,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, particleColors = palette.value)
            }
        }
        fun image() = compose.onNodeWithTag("particle_preview").captureToImage().asAndroidBitmap()
        fun lit(bitmap: android.graphics.Bitmap): Int {
            val px = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(px, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return px.count { android.graphics.Color.red(it) > 40 }
        }
        assertEquals(0, lit(image()))
        compose.runOnIdle { time.longValue = 1500 }
        val cloud = image()
        assertTrue("Particles must be visible before the phrase settles", lit(cloud) > 100)
        compose.mainClock.advanceTimeBy(1000)
        assertTrue("Paused media freezes particles", cloud.sameAs(image()))
        compose.runOnIdle { time.longValue = 3800 }
        val formed = image()
        compose.runOnIdle { enabled.value = false }
        assertTrue("Settled particles preserve the exact original glyph ink", formed.sameAs(image()))
        compose.runOnIdle { enabled.value = true; time.longValue = 1500 }
        assertTrue("Backward seeking deterministically restores the same cloud", cloud.sameAs(image()))
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val folder = File(context.externalCacheDir, "lyrics-particles").apply { mkdirs() }
        repeat(141) { frame ->
            compose.runOnIdle { time.longValue = 1000 + frame * 50L }
            image().let { bitmap -> File(folder, "frame-%03d.png".format(frame)).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            } }
            if (frame == 88) {
                val retiring = image()
                compose.mainClock.advanceTimeBy(600)
                assertTrue("Outgoing grain also freezes with media time", retiring.sameAs(image()))
            }
        }
        compose.runOnIdle { palette.value = LyricsSmokeColors(Color.Red, Color.Red); time.longValue = 5500 }
        assertFalse("Cover palette must recolor the particles", cloud.sameAs(image()))
    }
}
