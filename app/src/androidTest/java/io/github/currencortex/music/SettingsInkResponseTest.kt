package io.github.currencortex.music

import android.graphics.Color
import android.graphics.RuntimeShader
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SettingsInkResponseTest {
    @get:Rule val compose = createComposeRule()
    private var clicks = 0
    private fun show(enabled: Boolean = true) {
        compose.mainClock.autoAdvance = false
        compose.setContent { LeiTheme(AppearanceSettings(themeMode = ThemeMode.LIGHT)) { SettingsUiScope {
            SettingsGroup(Modifier.fillMaxWidth()) {
                SettingsPreference("重复点击已选项", modifier = Modifier.testTag("ink_row"), enabled = enabled, onClick = { clicks++ })
            }
        } } }
    }
    private fun shade(x: Float): Float {
        val image = compose.onNodeWithTag("ink_row").captureToImage().asAndroidBitmap()
        val cx = (image.width * x).toInt(); val cy = (image.height * .8f).toInt()
        var sum = 0f
        for (dy in -4..4) for (dx in -4..4) sum += Color.red(image.getPixel(cx + dx, cy + dy))
        image.recycle()
        return sum / 81
    }
    private fun down(x: Float) = compose.onNodeWithTag("ink_row").performTouchInput { down(Offset(width * x, height * .8f)) }
    private fun up() = compose.onNodeWithTag("ink_row").performTouchInput { up() }
    private fun tap(x: Float) = compose.onNodeWithTag("ink_row").performTouchInput { click(Offset(width * x, height * .8f)) }
    private fun capture() {
        val image = compose.onNodeWithTag("ink_row").captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir, "nativeui-ink-response-64ms.png")
            .outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }

    @Test @SdkSuppress(minSdkVersion = 33) fun originalSparkleShaderCompilesOnTheActualAndroidRenderer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.runOnUiThread { RuntimeShader(SettingsInkSource.read(context.assets)) }
    }

    @Test fun splashStartsAtTheFingerAndDoesNotWashTheWholeRowAtOnce() {
        show()
        down(.15f)
        compose.mainClock.advanceTimeBy(64)
        assertTrue("Ink must spread from the left press, not fill the whole row", shade(.15f) < shade(.85f) - 4)
        capture()
        up(); compose.mainClock.advanceTimeBy(800)
        down(.85f)
        compose.mainClock.advanceTimeBy(64)
        assertTrue("Moving the press must move the splash origin", shade(.85f) < shade(.15f) - 4)
        up()
    }

    @Test fun quickRepeatedTapsFinishTheirOwnSplashesAfterRelease() {
        show()
        val idle = shade(.2f)
        repeat(2) {
            tap(.2f)
            compose.mainClock.advanceTimeBy(128)
            assertTrue("Release must not cancel InkSparkle", shade(.2f) < idle - 3)
            compose.mainClock.advanceTimeBy(700)
            assertEquals(idle, shade(.2f), .1f)
        }
        assertEquals(2, clicks)
    }

    @Test fun cancelledPressDoesNotClick() {
        show()
        val idle = shade(.2f)
        down(.2f)
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("ink_row").performTouchInput { moveTo(Offset(width * .2f, height * 3f)); up() }
        compose.mainClock.advanceTimeBy(700)
        assertEquals(0, clicks)
        assertEquals(idle, shade(.2f), .1f)
    }

    @Test fun disabledRowDoesNotAnimateOrClick() {
        show(enabled = false)
        val idle = shade(.2f)
        tap(.2f)
        compose.mainClock.advanceTimeBy(128)
        assertEquals(0, clicks)
        assertEquals(idle, shade(.2f), .1f)
    }
}
