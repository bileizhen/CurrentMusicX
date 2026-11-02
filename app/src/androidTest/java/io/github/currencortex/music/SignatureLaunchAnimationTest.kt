package io.github.currencortex.music

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.ui.component.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SignatureLaunchAnimationTest {
    private var reduced = false
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor get() = if (reduced) 0f else 1f
    })
    private val app get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()

    private fun renderer(name: String): SignatureLaunchRenderer {
        val (latin, chinese) = SignatureGlyphs.load(app.assets, name)
        return SignatureLaunchRenderer(name, latin, chinese, app.resources.getFont(R.font.lxgw_wenkai_regular))
    }
    private fun frame(renderer: SignatureLaunchRenderer, ms: Int, file: String,
        width: Int = 1080, height: Int = 1920): Int {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        renderer.draw(Canvas(bitmap), width.toFloat(), height.toFloat(), ms.toFloat())
        File(app.externalCacheDir, file).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val bright = pixels.count { Color.red(it) > 140 || Color.green(it) > 140 || Color.blue(it) > 180 }
        // Every luminous signature pixel must fit inside the viewport, including landscape/long names.
        for (x in 0 until width) {
            assertTrue(Color.red(pixels[x]) < 100)
            assertTrue(Color.red(pixels[(height - 1) * width + x]) < 100)
        }
        for (y in 0 until height) {
            assertTrue(Color.red(pixels[y * width]) < 100)
            assertTrue(Color.red(pixels[y * width + width - 1]) < 100)
        }
        bitmap.recycle()
        return bright
    }

    @Test fun centreLinesWriteProgressivelyAndSettleWithoutReplacingTheGlyphs() {
        val renderer = renderer("bileizhen")
        assertTrue(renderer.strokeCount > 8)
        assertEquals(0, renderer.fallbackCount)
        assertEquals(0, frame(renderer, 250, "signature-0250.png"))
        val half = frame(renderer, 700, "signature-0700.png")
        val complete = frame(renderer, 1200, "signature-1200.png")
        assertTrue(half > 100)
        assertTrue(complete > half * 1.2)
        frame(renderer, 1400, "signature-1400.png")
        assertEquals(complete, frame(renderer, 1700, "signature-1700.png"))
    }

    @Test fun chineseEmojiAndLongNamesKeepTheirGlyphsAndFitLandscape() {
        val chinese = renderer("测试用户")
        assertEquals(0, chinese.fallbackCount)
        assertTrue(chinese.strokeCount > 8)
        assertTrue(frame(chinese, 1500, "signature-chinese.png") > 100)
        val mixed = renderer("霖🎧测试用户bileizhen非常长的名字👩‍💻")
        assertTrue(mixed.fallbackCount > 0)
        assertTrue(frame(mixed, 1500, "signature-landscape.png", 1200, 540) > 100)
        assertEquals("欢迎聆听", signatureName("\n\t "))
    }

    @Test fun boundedTimelineBlocksTouchesAndCallsFinishOnce() {
        compose.mainClock.autoAdvance = false
        var done by mutableStateOf(false)
        var finished = 0
        var clicks = 0
        compose.setContent {
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White).clickable { clicks++ }) {
                if (!done) SignatureLaunchOverlay("bileizhen") { finished++; done = true }
            }
        }
        compose.onNodeWithTag("launch_animation", useUnmergedTree = true).performTouchInput { click() }
        assertEquals(0, clicks)
        compose.mainClock.advanceTimeBy(1700)
        assertFalse(done)
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("launch_animation", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(1, finished)
    }

    @Test fun hardwareFrameShowsThePreparedSignature() {
        var prepared = false
        compose.setContent { SignatureLaunchFrame("bileizhen", { 1400f }, onPrepared = { prepared = true }) }
        compose.waitUntil(10000) { prepared }
        val bitmap = compose.onNodeWithTag("signature_launch_frame").captureToImage().asAndroidBitmap()
        File(app.externalCacheDir, "signature-compose.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue(pixels.count { Color.red(it) > 140 || Color.blue(it) > 180 } > 1000)
    }

    @Test fun closedSystemSplashGateStillHasAHardDeadline() {
        compose.mainClock.autoAdvance = false
        var done by mutableStateOf(false)
        compose.setContent { if (!done) SignatureLaunchOverlay("bileizhen", windowReady = false) { done = true } }
        compose.mainClock.advanceTimeBy(200)
        assertFalse(done)
        compose.mainClock.advanceTimeBy(2500)
        assertTrue(done)
    }

    @Test fun reducedMotionExitsPromptly() {
        reduced = true
        var done by mutableStateOf(false)
        compose.setContent { if (!done) SignatureLaunchOverlay("测试用户") { done = true } }
        compose.waitUntil(3000) { done }
    }
}
