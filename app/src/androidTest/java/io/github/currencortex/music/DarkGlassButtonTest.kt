package io.github.currencortex.music

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Check the painted rim in the same dark settings backdrop as the theme buttons. */
class DarkGlassButtonTest {
    @get:Rule val compose=createComposeRule()
    private val selected=mutableIntStateOf(2)
    private fun show(glass:Boolean=true) {
        compose.mainClock.autoAdvance=false
        compose.setContent {LeiTheme(AppearanceSettings(themeMode=ThemeMode.DARK,liquidGlass=glass)) {
            SettingsPage("外观","dark_button_fixture") {
                item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("跟随系统","浅色","深色").forEachIndexed {index,label->
                        SettingsChoiceButton(label,selected.intValue==index,{selected.intValue=index},
                            Modifier.weight(1f).testTag("dark_theme_button_$index"))
                    }
                }}
            }
        }}
        advance()
    }
    private fun advance() {
        compose.waitForIdle();compose.mainClock.advanceTimeByFrame();compose.mainClock.advanceTimeBy(1000);compose.waitForIdle()
    }
    private fun checkRims(glass:Boolean=true) {
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        val density=InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        try {
            for(index in 0..2) {
                val bounds=compose.onNodeWithTag("dark_theme_button_$index").fetchSemanticsNode().boundsInRoot
                fun rgb(x:Int,y:Int):List<Int> {
                    val pixel=bitmap.getPixel(x,y)
                    return listOf(android.graphics.Color.red(pixel),android.graphics.Color.green(pixel),android.graphics.Color.blue(pixel))
                }
                // The first four dp contain the optical rim. Compare it to the blank
                // material above the label, excluding text and the curved endcaps.
                val x=bounds.center.x.toInt()
                if(glass) {
                    val backdrop=rgb(x,bounds.top.toInt()-2)
                    for(y in listOf(bounds.top.toInt(),bounds.top.toInt()+1,bounds.bottom.toInt()-2,bounds.bottom.toInt()-1)) {
                        val edge=rgb(x,y)
                        assertTrue("Straight rim must not protrude beyond the curved ends: button=$index edge=$edge backdrop=$backdrop",
                            (0..2).all {kotlin.math.abs(edge[it]-backdrop[it])<=8})
                    }
                    continue
                }
                val body=rgb(x,(bounds.top+9*density).toInt())
                for(y in bounds.top.toInt()..(bounds.top+4*density).toInt()) {
                    val edge=rgb(x,y)
                    val jump=(0..2).maxOf {edge[it]-body[it]}
                    assertTrue("Top rim becomes a bright strip: button=$index jump=$jump edge=$edge body=$body",jump<=18)
                }
                val bottomBody=rgb(x,(bounds.bottom-9*density).toInt())
                for(y in (bounds.bottom-4*density).toInt() until bounds.bottom.toInt()) {
                    val edge=rgb(x,y)
                    val jump=(0..2).maxOf {edge[it]-bottomBody[it]}
                    assertTrue("Bottom rim becomes a bright strip: button=$index jump=$jump edge=$edge body=$bottomBody",jump<=18)
                }
            }
        }finally{bitmap.recycle()}
    }
    @Test fun darkSelectedAndUnselectedPillsHaveNoBrightHorizontalStrips() {
        show();checkRims()
        for(index in 0..2) {
            compose.onNodeWithTag("dark_theme_button_$index").performClick();advance()
            assertEquals(index,selected.intValue)
            compose.onNodeWithTag("dark_theme_button_$index").assertIsSelected()
            checkRims()
        }
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.externalCacheDir,"settings-dark-buttons-fixed.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        bitmap.recycle()
    }
    @Test fun disablingGlassStillKeepsTheDarkSelectionAndReadablePills() {
        show(false);checkRims(false)
        compose.onNodeWithTag("dark_theme_button_0").performClick();advance()
        compose.onNodeWithTag("dark_theme_button_0").assertIsSelected()
        assertEquals(0,selected.intValue);checkRims(false)
    }
}
