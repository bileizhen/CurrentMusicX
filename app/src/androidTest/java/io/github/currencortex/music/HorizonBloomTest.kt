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
import io.github.currencortex.music.feature.update.HorizonBloomBackground
import io.github.currencortex.music.feature.update.HorizonBloomRenderer
import io.github.currencortex.music.feature.update.UpdatePageContent
import io.github.currencortex.music.ui.component.ArtworkGlowPalette
import io.github.currencortex.music.ui.theme.LeiTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class HorizonBloomTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun nativeShaderCompilesOnSupportedAndroid() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        HorizonBloomRenderer()
    }

    @Test fun updatePageUsesRealCoverColoursAndRetintsAfterCoverChanges() {
        val red = cover("horizon-red.png", 0xffed4055.toInt())
        val blue = cover("horizon-blue.png", 0xff3873ed.toInt())
        val selected = mutableStateOf(red.toURI().toString())
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            top.yukonga.miuix.kmp.basic.Scaffold {
                UpdatePageContent(UpdateState.UpToDate, cover = selected.value, backgroundActive = false)
            }
        } }
        compose.waitUntil(15000) { channels().let { it.first > it.second * 1.4 } }
        capture("horizon-cover-red.png")
        compose.runOnIdle { selected.value = blue.toURI().toString() }
        compose.waitUntil(15000) { channels().let { it.second > it.first * 1.4 } }
        capture("horizon-cover-blue.png")
        compose.onNodeWithTag("update_details").performClick()
        compose.onNodeWithTag("update_back").performClick()
        compose.onNodeWithTag("update_retry").assertIsDisplayed()
    }

    @Test fun motionStopsWhenPageIsInactiveAndResumesContinuously() {
        val active = mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent { HorizonBloomBackground(ArtworkGlowPalette(0xffff9ffc.toInt(), 0xff5227ff.toInt()),
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

    @Test fun canvasFallbackKeepsTheSameColouredHorizon() {
        compose.setContent { HorizonBloomBackground(ArtworkGlowPalette(0xffed4055.toInt(), 0xff3873ed.toInt()),
            Modifier.fillMaxSize(), active = false, preferShader = false) }
        val bitmap = image()
        val center = bitmap.getPixel(bitmap.width / 2, (bitmap.height * .66f).toInt())
        assertTrue((center ushr 16 and 255) > 20)
        capture("horizon-canvas-fallback.png")
    }

    @Test fun portraitHasShallowDimLimbAndAtmosphereAcrossTheSky() {
        compose.setContent { HorizonBloomBackground(ArtworkGlowPalette(0xffff9ffc.toInt(), 0xff5227ff.toInt()),
            Modifier.fillMaxSize(), active = false) }
        val bitmap = image()
        fun peak(x: Int): Int = ((bitmap.height * .62).toInt()..(bitmap.height * .73).toInt())
            .maxBy { bitmap.getPixel(x, it) ushr 16 and 255 }
        val middle = peak(bitmap.width / 2)
        val edge = peak((bitmap.width * .05).toInt())
        assertEquals("Limb should sit at two thirds of the portrait", .66f,
            middle.toFloat() / bitmap.height, .012f)
        assertTrue("Planet must have a shallow curve", edge > middle && edge - middle < bitmap.height * .04f)
        val highlight = bitmap.getPixel(bitmap.width / 2, middle) ushr 16 and 255
        val rim = bitmap.getPixel((bitmap.width * .05).toInt(), edge) ushr 16 and 255
        assertTrue("The sun must have a visible local highlight", highlight > 130)
        assertTrue("The rest of the rim must stay dim", rim in 20..85 && highlight > rim * 3)
        val sky = bitmap.getPixel(bitmap.width / 2, (bitmap.height * .3).toInt())
        assertTrue("Cool atmosphere must reach the upper sky", (sky and 255) > (sky ushr 16 and 255) + 4)
        capture("horizon-portrait-reference.png")
    }

    private fun image() = compose.onNodeWithTag("update_horizon_background").captureToImage().asAndroidBitmap()
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
