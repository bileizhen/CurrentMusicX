package io.github.currencortex.music

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.feature.profile.ChatBackdropPanel
import io.github.currencortex.music.ui.util.rememberBlurBackdrop
import io.github.currencortex.music.ui.theme.LeiTheme
import io.github.currencortex.music.data.settings.AppearanceSettings
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.basic.Text
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class ChatBackdropPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fixedBarsBlurTheMovingBackdropWhileTheMessageCenterStaysSharp() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val ink = mutableStateOf(Color.Black)
        var density = 1f
        compose.setContent { LeiTheme(AppearanceSettings()) {
            density = LocalDensity.current.density
            val backdrop = rememberBlurBackdrop(true)
            Box(Modifier.size(300.dp, 240.dp).background(Color(0xFFF5F6F8)).testTag("backdrop_fixture")) {
                Canvas(Modifier.matchParentSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
                    drawRect(ink.value, Offset(130.dp.toPx(), 0f), Size(20.dp.toPx(), size.height))
                }
                ChatBackdropPanel(backdrop, true, Modifier.align(Alignment.TopCenter).fillMaxWidth().height(72.dp))
                ChatBackdropPanel(backdrop, false, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(72.dp))
                Text("公共聊天室", Modifier.padding(8.dp), fontSize = 14.sp)
            }
        } }
        val node = compose.onNodeWithTag("backdrop_fixture")
        fun pixel(bitmap: android.graphics.Bitmap, x: Int, y: Int) = bitmap.getPixel((x * density).toInt(), (y * density).toInt())
        val first = node.captureToImage().asAndroidBitmap()
        assertTrue(android.graphics.Color.red(pixel(first, 140, 120)) < 10)
        // Both bars show a blurred and tinted copy of the black source, not a flat cover.
        for (y in listOf(24, 216)) {
            val red = android.graphics.Color.red(pixel(first, 140, y))
            assertTrue("The fixed bar must sample the source behind it", red in 100..235)
            assertTrue("Gaussian blur must soften the stripe's outer edge",
                android.graphics.Color.red(pixel(first, 126, y)) < 244)
        }
        first.recycle()
        compose.runOnIdle { ink.value = Color(0xFF0060FF) }
        val second = node.captureToImage().asAndroidBitmap()
        val refreshed = pixel(second, 140, 24)
        assertTrue("The backdrop must refresh as messages change", android.graphics.Color.blue(refreshed) > android.graphics.Color.red(refreshed) + 20)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "chat-component-backdrop.png").outputStream().use {
            second.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        second.recycle()
    }
}
