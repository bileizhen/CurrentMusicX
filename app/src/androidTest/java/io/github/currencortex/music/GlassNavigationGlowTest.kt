package io.github.currencortex.music

import android.graphics.Color
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.core.media.DockAudioLevel
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported

/** Exercise the real backdrop-sampling indicator, rather than the plain navigation fallback. */
@android.annotation.TargetApi(33)
class GlassNavigationGlowTest {
    @get:Rule val compose = createComposeRule()
    private var selected by mutableIntStateOf(0)
    private var enabled by mutableStateOf(true)
    private var expanded by mutableStateOf(true)
    private val meter = DockAudioLevel()

    @Composable private fun Fixture() {
        LeiTheme(AppearanceSettings(blur = true, liquidGlass = false)) {
            HighApiFloatingNavigation(selected, listOf("首页", "发现", "我的", "设置"),
                List(4) { Icons.Default.Home }, { selected = it }, blur = true, glass = false,
                content = { Box(Modifier.fillMaxSize().background(ComposeColor.White)) },
                overlay = {
                    val navigation = LocalMusicDockNavigation.current!!
                    UnifiedMusicDock(expanded, true, true, expanded, Modifier.align(Alignment.BottomCenter),
                        mini = { Box(Modifier.fillMaxSize().testTag("mini_content")) }, navigation = navigation, glow = {
                            if (enabled) DockGlowLight(ArtworkGlowPalette(0xffe57728.toInt(), 0xffe57728.toInt()),
                                false, meter, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(88.dp))
                        })
                })
        }
    }

    private fun selectedPixel(index: Int): Int {
        val bitmap = compose.onNodeWithTag("music_dock").captureToImage().asAndroidBitmap()
        val x = (bitmap.width * ((index + .5f) / 4)).toInt()
        val y = (bitmap.height * (1f - 8f / 122f)).toInt()
        return bitmap.getPixel(x, y)
    }

    @Test fun selectedGlassIndicatorRetainsLightOnEitherSideAndUpdatesWhenDisabled() {
        assumeTrue(Build.VERSION.SDK_INT >= 33 && isRuntimeShaderSupported())
        compose.mainClock.autoAdvance = false
        compose.setContent { Fixture() }
        compose.mainClock.advanceTimeBy(1000)
        val left = selectedPixel(0)
        assertTrue("Selected Home indicator must sample the warm light", Color.red(left) - Color.blue(left) > 20)
        compose.onNodeWithTag("tab_3").performClick()
        compose.mainClock.advanceTimeBy(1600)
        compose.runOnIdle { assertEquals(3, selected) }
        val right = selectedPixel(3)
        assertTrue("Selected Settings indicator must also sample the warm light", Color.red(right) - Color.blue(right) > 20)
        compose.runOnIdle { enabled = false }
        compose.mainClock.advanceTimeBy(1000)
        val disabled = selectedPixel(3)
        assertTrue("Disabling the light must update the sampled indicator", kotlin.math.abs(Color.red(disabled) - Color.blue(disabled)) < 10)
        assertFalse(meter.enabled)
    }

    @Test fun pausedMiniRetainsLightAfterNavigationCollapsesAndExpandsAgain() {
        assumeTrue(Build.VERSION.SDK_INT >= 33 && isRuntimeShaderSupported())
        compose.mainClock.autoAdvance = false
        compose.setContent { Fixture() }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { expanded = false }
        compose.mainClock.advanceTimeBy(1000)
        val mini = compose.onNodeWithTag("mini_content").captureToImage().asAndroidBitmap()
        val pixel = mini.getPixel(mini.width / 2, mini.height - 2)
        assertTrue("Paused secondary-page mini must retain the light at its own bottom edge",
            Color.red(pixel) - Color.blue(pixel) > 20)
        compose.runOnIdle { enabled = false }
        compose.mainClock.advanceTimeBy(1000)
        val unlit = compose.onNodeWithTag("mini_content").captureToImage().asAndroidBitmap()
        val disabled = unlit.getPixel(unlit.width / 2, unlit.height - 2)
        assertTrue("The appearance toggle must also clear light from the collapsed mini",
            kotlin.math.abs(Color.red(disabled) - Color.blue(disabled)) < 10)
        compose.runOnIdle { enabled = true; expanded = true }
        compose.mainClock.advanceTimeBy(1600)
        val restored = selectedPixel(0)
        assertTrue("Expanded navigation must recover its illuminated indicator",
            Color.red(restored) - Color.blue(restored) > 20)
    }
}
