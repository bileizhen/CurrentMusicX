package io.github.currencortex.music

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.ui.component.nativeui.SettingsFloatingHeader
import io.github.currencortex.music.ui.component.nativeui.SettingsRootTopBar
import io.github.currencortex.music.ui.component.nativeui.SettingsToolbarHeight
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class HeaderScrimGradientTest {
    @get:Rule val compose = createComposeRule()
    private var dark by mutableStateOf(true)
    private var opacity by mutableFloatStateOf(.9f)
    private val pageColor get() = if (dark) Color(0xff252527) else Color(0xffe3e3e3)
    private val scrimColor get() = if (dark) Color(0xff121212) else Color(0xfffdfdfd)

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LeiTheme(AppearanceSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
                Box(Modifier.fillMaxWidth().height(180.dp).background(pageColor).testTag("header_gradient")) {
                    SettingsFloatingHeader(background = scrimColor, opacity = { opacity }) { SettingsRootTopBar("我的") }
                }
            }
        }
    }

    private fun frame(): Bitmap {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        return compose.onNodeWithTag("header_gradient").captureToImage().asAndroidBitmap()
    }

    private fun save(bitmap: Bitmap, name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        File(context.externalCacheDir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun checkFade(bitmap: Bitmap) {
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val header = compose.onNodeWithTag("settings_floating_header").fetchSemanticsNode().boundsInRoot
        val fixture = compose.onNodeWithTag("header_gradient").fetchSemanticsNode().boundsInRoot
        val start = header.bottom - fixture.top - SettingsToolbarHeight.value * density
        val fadeHeight = (SettingsToolbarHeight.value + 16f) * density
        val from = (start + fadeHeight * .15f).toInt()
        val end = (start + fadeHeight * .85f).toInt()
        val left = bitmap.width / 2
        val right = bitmap.width * 4 / 5
        val easing = CubicBezierEasing(.42f, 0f, .58f, 1f)
        val background = pageColor.toArgb()
        val foreground = scrimColor.toArgb()
        val channels = listOf(16, 8, 0)
        var previous: DoubleArray? = null
        var largestStep = 0.0
        for (y in from until end) {
            val mean = DoubleArray(3)
            for (x in left until right) {
                val pixel = bitmap.getPixel(x, y)
                channels.forEachIndexed { i, shift -> mean[i] += (pixel ushr shift) and 255 }
            }
            val alpha = (1 - easing.transform(((y + .5f - start) / fadeHeight).coerceIn(0f, 1f))) * opacity
            channels.forEachIndexed { i, shift ->
                mean[i] /= right - left
                val expected = ((background ushr shift) and 255) * (1 - alpha) +
                    ((foreground ushr shift) and 255) * alpha
                assertEquals("The fade must retain its original opacity and colour at row $y", expected.toDouble(), mean[i], 1.0)
                previous?.let { largestStep = maxOf(largestStep, kotlin.math.abs(mean[i] - it[i])) }
            }
            previous = mean
        }
        // Average across a text-free strip: isolated pixel noise is fine, coherent row jumps are not.
        if (dark) assertTrue("Visible horizontal bands: largest adjacent-row RGB step=$largestStep, density=$density",
            largestStep < .4 / density + .15)
        assertEquals("The scrim must leave the page below the overhang untouched", pageColor.toArgb(),
            bitmap.getPixel(bitmap.width / 2, bitmap.height - 1))
    }

    @Test fun darkHeaderFadeIsSmoothAndStableWithoutChangingItsStrength() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        show()
        val bitmap = frame()
        save(bitmap, "header-gradient-dark.png")
        checkFade(bitmap)
        val later = frame()
        assertTrue("A stationary header must not shimmer between frames", bitmap.sameAs(later))
        later.recycle()
        bitmap.recycle()
    }

    @Test fun themeAndOpacityChangesUpdateTheCachedScrimWithoutLeavingNoiseAtZeroOpacity() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        show()
        frame().recycle()
        compose.runOnIdle { opacity = .4f }
        frame().also { checkFade(it); it.recycle() }
        compose.runOnIdle { opacity = 0f }
        val clear = frame()
        for (y in 0 until clear.height) for (x in clear.width / 2 until clear.width * 4 / 5) {
            assertEquals("Zero opacity must not leave a tint or noise", pageColor.toArgb(), clear.getPixel(x, y))
        }
        clear.recycle()
        compose.runOnIdle { dark = false; opacity = .9f }
        frame().also { checkFade(it); save(it, "header-gradient-light.png"); it.recycle() }
        compose.runOnIdle { dark = true }
        frame().also { checkFade(it); it.recycle() }
    }
}
