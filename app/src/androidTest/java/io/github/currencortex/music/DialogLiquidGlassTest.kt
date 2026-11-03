package io.github.currencortex.music

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.LiquidGlassOpticsKey
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import java.io.File

class DialogLiquidGlassTest {
    @get:Rule val compose = createComposeRule()
    private var settings by mutableStateOf(AppearanceSettings(themeMode = ThemeMode.LIGHT, liquidGlass = true, blur = true))
    private var warm by mutableStateOf(true)
    private var visible by mutableStateOf(true)

    private fun show(capture: Boolean = true) {
        compose.setContent { LeiTheme(settings) {
            val backdrop = if (capture) rememberMusicDialogBackdrop() else null
            CompositionLocalProvider(LocalMusicDialogBackdrop provides backdrop) { Scaffold {
                Canvas(Modifier.fillMaxSize().musicDialogBackdropSource()) {
                    drawRect(if (warm) Color(0xFFE98944) else Color(0xFF3F80DA))
                    val gap = 14.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        drawRect(Color.White.copy(alpha = .7f), Offset(x, 0f), Size(gap * .22f, size.height))
                        x += gap
                    }
                }
                if (visible) MusicDialog("液态玻璃弹窗", { visible = false }) {
                    Text("背景颜色应透过玻璃，文字保持清晰。")
                    Spacer(Modifier.height(120.dp))
                    MusicDialogButton("完成", { visible = false })
                }
            } }
        } }
    }

    private fun optics(enabled: Boolean) = compose.onNodeWithTag("music_dialog_surface")
        .assert(SemanticsMatcher.expectValue(LiquidGlassOpticsKey, enabled))

    private fun image(name: String): Bitmap {
        val bitmap = compose.onNodeWithTag("music_dialog_surface").captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.externalCacheDir, "dialog-glass-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return bitmap
    }

    @Test fun liveBackdropAndThemeChangesReachRetainedDialog() {
        show()
        optics(true)
        val lightWarm = image("light-warm")
        compose.runOnIdle { warm = false }
        val lightCool = image("light-cool")
        fun redBlue(bitmap: Bitmap): Int {
            // Blank content area: exclude text/actions and the refracting boundary.
            var sum = 0
            for (y in bitmap.height * 45 / 100 until bitmap.height * 65 / 100 step 4)
                for (x in bitmap.width / 4 until bitmap.width * 3 / 4 step 4) {
                    val pixel = bitmap.getPixel(x, y)
                    sum += android.graphics.Color.red(pixel) - android.graphics.Color.blue(pixel)
                }
            return sum
        }
        assertTrue("The retained panel must resample the changed page, rather than paint an opaque fill",
            redBlue(lightWarm) > redBlue(lightCool) + 1000)
        compose.runOnIdle { settings = settings.copy(themeMode = ThemeMode.DARK) }
        optics(true)
        val dark = image("dark-cool")
        assertFalse("Dark mode must update the retained material", dark.sameAs(lightCool))
        compose.onNodeWithText("完成").assertIsDisplayed().performClick()
        compose.onNodeWithTag("music_dialog_surface").assertDoesNotExist()
    }

    @Test fun appearanceSwitchesUpdateAnAlreadyOpenPanel() {
        show()
        optics(true)
        val frosted = image("blur")
        compose.runOnIdle { settings = settings.copy(blur = false) }
        optics(true)
        val clear = image("no-blur")
        assertFalse("Disabling blur must change the sampled material while retaining refraction", clear.sameAs(frosted))
        compose.runOnIdle { settings = settings.copy(liquidGlass = false, blur = true) }
        optics(false)
        image("standard")
        compose.onNodeWithText("完成").assertIsDisplayed().performClick()
        compose.onNodeWithTag("music_dialog_surface").assertDoesNotExist()
    }

    @Test fun missingBackdropFallsBackToReadablePanel() {
        show(capture = false)
        optics(false)
        compose.onNodeWithText("液态玻璃弹窗").assertIsDisplayed()
        image("fallback")
        compose.onNodeWithText("完成").assertIsDisplayed().performClick()
        compose.onNodeWithTag("music_dialog_surface").assertDoesNotExist()
    }
}
