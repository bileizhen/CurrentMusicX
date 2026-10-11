package io.github.currencortex.music

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.DockAudioLevel
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NavigationGlowTest {
    @get:Rule val compose = createComposeRule()
    private val meter = DockAudioLevel()
    private var enabled by mutableStateOf(true)
    private var playing by mutableStateOf(false)
    private var selected by mutableIntStateOf(0)
    private var palette by mutableStateOf(ArtworkGlowPalette(0xff227fdd.toInt(), 0xffad55dc.toInt()))

    @Composable private fun Fixture() {
        LeiTheme(AppearanceSettings(blur = false)) {
            Box(Modifier.size(360.dp, 130.dp).clip(RoundedCornerShape(30.dp)).background(ComposeColor.White).testTag("glow_panel")) {
                if (enabled) DockGlowLight(palette, playing, meter, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(88.dp))
                PlainFloatingBar(selected, listOf("首页", "发现", "我的", "设置"), List(4) { Icons.Default.Home },
                    { selected = it }, Modifier.align(Alignment.BottomCenter), embedded = true)
            }
        }
    }
    private fun image() = compose.onNodeWithTag("glow_panel").captureToImage().asAndroidBitmap()
    private fun edge() = image().let { it.getPixel((it.width * .28f).toInt(), (it.height * (1f - 18f / 130f)).toInt()) }

    @Test fun singleColourCoverIlluminatesBothSidesOfTheDock() {
        compose.mainClock.autoAdvance = false
        palette = ArtworkGlowPalette.fromPixels(IntArray(1000) { if (it < 600) 0xff88c900.toInt() else 0xff999999.toInt() })
        compose.setContent { Fixture() }
        compose.mainClock.advanceTimeBy(100)
        val bitmap = image()
        val y = (bitmap.height * (1f - 8f / 130f)).toInt()
        val left = bitmap.getPixel((bitmap.width * .30f).toInt(), y)
        val right = bitmap.getPixel((bitmap.width * .70f).toInt(), y)
        assertTrue("Right side must retain the cover colour", Color.green(right) > Color.blue(right) + 20)
        assertEquals("Both sides must have comparable light", Color.blue(left).toDouble(), Color.blue(right).toDouble(), 6.0)
    }

    @Test fun glowStaysAtBottomAndNeverTakesNavigationTouchesAndDisablesCleanly() {
        compose.mainClock.autoAdvance = false
        compose.setContent { Fixture() }
        compose.mainClock.advanceTimeBy(100)
        assertTrue(meter.enabled)
        val bitmap = image()
        assertEquals(Color.WHITE, bitmap.getPixel(bitmap.width / 2, 10))
        assertTrue("Cover blue must appear at the bottom", Color.blue(edge()) > Color.red(edge()))
        val upperFalloff = bitmap.getPixel((bitmap.width * .28f).toInt(), (bitmap.height * (1f - 55f / 130f)).toInt())
        assertTrue("Light must diffuse upwards, with less colour away from the source", Color.red(upperFalloff) > Color.red(edge()))
        val outside = bitmap.getPixel(0, bitmap.height - 1)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.getExternalFilesDir(null), "navigation-soft-light-preview.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithTag("tab_2").performClick()
        compose.runOnIdle { assertEquals(2, selected); enabled = false }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("navigation_glow").assertDoesNotExist()
        assertFalse(meter.enabled)
        assertEquals(Color.WHITE, edge())
        val disabled = image()
        assertEquals("Light must stay inside the rounded glass", outside, disabled.getPixel(0, disabled.height - 1))
        compose.onNodeWithTag("tab_3").performClick()
        compose.runOnIdle { assertEquals(3, selected) }
    }

    @Test fun audioPulseSettlesOnPauseAndCoverChangeCrossfades() {
        compose.mainClock.autoAdvance = false
        compose.setContent { Fixture() }; compose.mainClock.advanceTimeBy(32)
        val quiet = edge()
        compose.runOnIdle { playing = true; meter.publish(.9f, .9f) }
        compose.mainClock.advanceTimeBy(240)
        val loud = edge()
        assertTrue(Color.red(loud) < Color.red(quiet))
        compose.runOnIdle { playing = false }
        compose.mainClock.advanceTimeBy(2500)
        val settled = edge()
        assertTrue("Pause must lose the stronger audio pulse", Color.red(settled) > Color.red(loud))
        compose.runOnIdle { palette = ArtworkGlowPalette(0xffe57728.toInt(), 0xffe57728.toInt()) }
        compose.mainClock.advanceTimeBy(300)
        val middle = edge()
        compose.mainClock.advanceTimeBy(1000)
        val warm = edge()
        assertNotEquals(quiet, middle); assertNotEquals(middle, warm)
        assertTrue(Color.red(warm) > Color.blue(warm))
    }

    @Test fun louderAudioRaisesLightAndSilenceLowersItWhilePlaybackContinues() {
        compose.mainClock.autoAdvance = false
        compose.setContent { Fixture() }
        compose.mainClock.advanceTimeBy(32)
        fun upperLight() = image().let {
            it.getPixel((it.width * .28f).toInt(), (it.height * (1f - 55f / 130f)).toInt())
        }
        val quiet = upperLight()
        compose.runOnIdle { playing = true; meter.publish(1f, 1f) }
        compose.mainClock.advanceTimeBy(320)
        val loud = upperLight()
        assertTrue("Strong audio must raise colour into the previously unlit upper area",
            Color.red(loud) < Color.red(quiet) - 5)
        compose.runOnIdle { meter.publish(0f, 0f) }
        compose.mainClock.advanceTimeBy(80)
        val falling = upperLight()
        assertTrue("The light must return gradually instead of snapping straight down",
            Color.red(falling) > Color.red(loud) && Color.red(falling) < Color.red(quiet))
        compose.mainClock.advanceTimeBy(1600)
        val settled = upperLight()
        assertTrue("Silence must lower the light even while the player remains playing",
            kotlin.math.abs(Color.red(settled) - Color.red(quiet)) <= 2)
        compose.runOnIdle { assertTrue(playing) }
    }
}
