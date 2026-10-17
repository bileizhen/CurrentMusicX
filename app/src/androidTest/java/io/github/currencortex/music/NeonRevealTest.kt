package io.github.currencortex.music

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.update.UpdateState
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.update.NeonRevealBackground
import io.github.currencortex.music.feature.update.NeonRevealRenderer
import io.github.currencortex.music.feature.update.UpdatePageContent
import io.github.currencortex.music.ui.component.ArtworkGlowPalette
import io.github.currencortex.music.ui.theme.LeiTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class NeonRevealTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun nativeShaderCompilesOnSupportedAndroid() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        NeonRevealRenderer()
    }

    @Test fun updatePageUsesRealCoverColoursAndRetintsAfterCoverChanges() {
        val red = cover("neon-red.png", 0xffed4055.toInt())
        val blue = cover("neon-blue.png", 0xff3873ed.toInt())
        val selected = mutableStateOf(red.toURI().toString())
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            top.yukonga.miuix.kmp.basic.Scaffold {
                UpdatePageContent(UpdateState.UpToDate, cover = selected.value, backgroundActive = false)
            }
        } }
        compose.waitUntil(15000) { channels().let { it.first > it.second * 1.4 } }
        capture("neon-cover-red.png")
        compose.runOnIdle { selected.value = blue.toURI().toString() }
        compose.waitUntil(15000) { channels().let { it.second > it.first * 1.4 } }
        capture("neon-cover-blue.png")
        val beforeTap = image()
        compose.onNodeWithTag("update_neon_background").performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(160)
        assertTrue("Blank taps must not replay the lighting", beforeTap.sameAs(image()))
        compose.onNodeWithTag("update_details").performClick()
        compose.onNodeWithTag("update_back").performClick()
        compose.onNodeWithTag("update_retry").assertIsDisplayed()
    }

    @Test fun motionStopsWhenPageIsInactiveAndResumesContinuously() {
        val active = mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent { NeonRevealBackground(ArtworkGlowPalette(0xffff9ffc.toInt(), 0xff5227ff.toInt()),
            Modifier.fillMaxSize(), active = active.value) }
        compose.mainClock.advanceTimeBy(2500)
        val first = image()
        compose.mainClock.advanceTimeBy(2000)
        val moving = image()
        assertFalse(first.sameAs(moving))
        compose.runOnIdle { active.value = false }
        compose.mainClock.advanceTimeByFrame()
        val paused = image()
        compose.mainClock.advanceTimeBy(2000)
        assertTrue("An invisible page must not keep drawing motion", paused.sameAs(image()))
        compose.runOnIdle { active.value = true }
        compose.mainClock.advanceTimeBy(1000)
        assertFalse(paused.sameAs(image()))
    }

    @Test fun canvasFallbackDrawsMirroredTubes() {
        compose.setContent { NeonRevealBackground(ArtworkGlowPalette(0xffffd3a8.toInt(), 0xffff4d6d.toInt()),
            Modifier.fillMaxSize(), active = false, preferShader = false) }
        assertMirroredTubes(image())
        capture("neon-canvas-fallback.png")
    }

    @Test fun centreOutRevealLeavesBrightMirroredTubes() {
        compose.mainClock.autoAdvance = false
        compose.setContent { NeonRevealBackground(ArtworkGlowPalette(0xffffd3a8.toInt(), 0xffff4d6d.toInt()),
            Modifier.fillMaxSize()) }
        compose.mainClock.advanceTimeBy(200)
        val dark = image()
        compose.mainClock.advanceTimeBy(500)
        val opening = image()
        assertFalse(dark.sameAs(opening))
        capture("neon-opening.png")
        compose.mainClock.advanceTimeBy(2600)
        val lit = image()
        assertMirroredTubes(lit)
        // The horizontal lit span grows from the middle toward both ends.
        fun brightWidth(b: Bitmap): Int {
            val y = (0 until b.height / 8).maxBy { b.getPixel(b.width / 2, it) and 255 }
            return (0 until b.width).count { (b.getPixel(it, y) and 255) > 190 }
        }
        assertTrue(brightWidth(lit) > brightWidth(opening) * 1.3)
        capture("neon-revealed.png")
    }

    private fun assertMirroredTubes(bitmap: Bitmap) {
        fun peak(range: IntRange) = range.maxOf { bitmap.getPixel(bitmap.width / 2, it) and 255 }
        assertTrue("Top tube must have a bright core", peak(0..bitmap.height / 8) > 190)
        assertTrue("Bottom tube must have a bright core", peak(bitmap.height * 7 / 8 until bitmap.height) > 190)
        val center = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        assertTrue("Coloured light should reach the centre", (center ushr 16 and 255) > 50)
    }

    private fun image() = compose.onNodeWithTag("update_neon_background").captureToImage().asAndroidBitmap()
    private fun channels(): Pair<Double, Double> {
        val bitmap = image()
        var red = 0.0; var blue = 0.0
        for (y in (bitmap.height * .56).toInt() until (bitmap.height * .62).toInt() step 4)
            for (x in (bitmap.width * .43).toInt() until (bitmap.width * .57).toInt() step 4) {
                val pixel = bitmap.getPixel(x, y)
                red += pixel ushr 16 and 255; blue += pixel and 255
            }
        bitmap.recycle()
        return red to blue
    }
    private fun cover(name: String, color: Int): File {
        check(context.packageName.endsWith(".verification"))
        val file = File(context.cacheDir, name)
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }
    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
