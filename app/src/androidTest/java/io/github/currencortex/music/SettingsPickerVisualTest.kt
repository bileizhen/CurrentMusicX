package io.github.currencortex.music

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.theme.LeiTheme
import io.github.currencortex.music.feature.settings.MusicSettingsScreen
import io.github.currencortex.music.feature.settings.MusicSettingsViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.blur.layerBackdrop
import java.io.File
import java.util.UUID

/** Material/shape checks use painted pixels, not just semantics or callbacks. */
class SettingsPickerVisualTest {
    @get:Rule val compose=createComposeRule()
    private fun advance(ms:Long=750) {
        compose.waitForIdle();compose.mainClock.advanceTimeByFrame();compose.mainClock.advanceTimeBy(ms);compose.waitForIdle()
    }
    private fun fixture(glass:Boolean,blur:Boolean) {
        compose.mainClock.autoAdvance=false
        compose.setContent {LeiTheme(AppearanceSettings(themeMode=ThemeMode.LIGHT,liquidGlass=glass,blur=blur)) {
            SettingsPage("菜单材质","picker_visual_page") {
                item {SettingsGroup {SettingsOptionsRow("来源","A",(0..2).map {i->
                    SettingsOption("picker_visual_$i",('A'+i).toString(),i==0,onSelect={})
                },Modifier.testTag("picker_visual_open"))}}
                item {Canvas(Modifier.fillMaxWidth().height(360.dp)) {
                    val stripe=40.dp.toPx()
                    var x=0f;var n=0
                    while(x<size.width) {
                        drawRect(if(n%2==0)Color.White else Color.Black,Offset(x,0f),androidx.compose.ui.geometry.Size(stripe,size.height))
                        x+=stripe;n++
                    }
                }}
            }
        }}
        advance()
    }
    private fun image():android.graphics.Bitmap {
        // Capture the actual presented window, including all overlapping layers.
        compose.onRoot().captureToImage()
        val bounds=compose.onRoot().fetchSemanticsNode().boundsInWindow
        val screen=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val result=android.graphics.Bitmap.createBitmap(screen,bounds.left.toInt(),bounds.top.toInt(),bounds.width.toInt(),bounds.height.toInt())
        if(result!==screen)screen.recycle()
        return result
    }
    private fun save(name:String) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        val bitmap=image()
        File(context.externalCacheDir,name).outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
    @Test fun glassPickerRefractsTheSettingsContentRatherThanAnEmptyBackground() {
        fixture(true,true)
        compose.onNodeWithTag("picker_visual_open").performClick();advance()
        val rect=compose.onNodeWithTag("settings_choice_panel").fetchSemanticsNode().boundsInRoot
        val bitmap=image()
        // Stay away from the rim, the leading labels, and the selected checkmark.
        val samples=( (rect.left+rect.width*.35f).toInt()..(rect.right-rect.width*.12f).toInt()).map {x->
            android.graphics.Color.red(bitmap.getPixel(x,(rect.bottom-rect.height*.15f).toInt()))
        }
        bitmap.recycle()
        assertTrue("Real page stripes must remain visible through frosted glass: range=${samples.max()-samples.min()}",samples.max()-samples.min()>18)
        save("settings-picker-real-content.png")
    }
    private fun checkShadow(glass:Boolean,blur:Boolean,name:String) {
        fixture(glass,blur)
        val before=image()
        compose.onNodeWithTag("picker_visual_open").performClick();advance()
        val rect=compose.onNodeWithTag("settings_choice_panel").fetchSemanticsNode().boundsInRoot
        val after=image()
        val x=(rect.right+3).toInt()
        val differences=((rect.top+rect.height*.2f).toInt()..(rect.bottom-rect.height*.2f).toInt()).mapNotNull {y->
            val original=android.graphics.Color.red(before.getPixel(x,y))
            if(original>=245)original-android.graphics.Color.red(after.getPixel(x,y))else null
        }
        save(name)
        before.recycle();after.recycle()
        assertTrue("The floating pane needs a visible shadow in $name; rect=$rect max=${differences.maxOrNull()}",differences.any {it>5})
    }
    @Test fun disabledGlassStillHasAFloatingRoundedPanel()=checkShadow(false,true,"settings-picker-frosted.png")
    @Test fun disabledGlassAndBlurStillHaveAFloatingRoundedPanel()=checkShadow(false,false,"settings-picker-solid.png")

    @Test fun realLyricsPickerSelectsClosesAndKeepsItsRoundedFloatingMaterial()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(context.packageName.endsWith(".verification"))
        val container=AppContainer(context,"picker-real-${UUID.randomUUID()}")
        try {
            container.ready.await();container.sessionRestored.await()
            container.updateSettings.setAutoCheck(false)
            val viewModel=MusicSettingsViewModel(container)
            compose.setContent {LeiTheme(AppearanceSettings(themeMode=ThemeMode.LIGHT)) {
                MusicSettingsScreen(viewModel) {}
            }}
            compose.onNodeWithTag("network_settings").performScrollToNode(hasTestTag("lyrics_source"))
            compose.mainClock.autoAdvance=false
            compose.onNodeWithTag("lyrics_source").performClick();advance()
            save("settings-picker-real-lyrics.png")
            compose.onNodeWithTag("lyrics_source_current_music").performClick();advance()
            compose.onNodeWithTag("settings_choice_panel").assertDoesNotExist()
            compose.onNodeWithTag("lyrics_source").assertTextContains("CurrentMusic")
        }finally{container.close()}
    }

    @Test fun frostedFallbackDoesNotBlurPixelsOutsideItsRoundedCorners() {
        compose.setContent {LeiTheme(AppearanceSettings(themeMode=ThemeMode.LIGHT,liquidGlass=false,blur=true)) {
            val backdrop=rememberGlassBackdrop()
            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                Canvas(Modifier.matchParentSize().then(if(backdrop!=null)Modifier.layerBackdrop(backdrop)else Modifier)) {
                    val stripe=8.dp.toPx();var x=0f;var n=0
                    while(x<size.width) {
                        drawRect(if(n%2==0)Color.Red else Color.Green,Offset(x,0f),androidx.compose.ui.geometry.Size(stripe,size.height));x+=stripe;n++
                    }
                }
                Box(Modifier.size(220.dp,180.dp).testTag("fallback_pane")
                    .glassSurface(backdrop,RoundedCornerShape(30.dp),menu=true,clipToShape=false))
            }
        }}
        val pane=compose.onNodeWithTag("fallback_pane").fetchSemanticsNode().boundsInRoot
        val bitmap=image()
        val pixel=bitmap.getPixel((pane.left+2).toInt(),(pane.top+2).toInt())
        bitmap.recycle()
        assertTrue("Corner must expose an unblurred red or green stripe, pixel=$pixel",
            kotlin.math.abs(android.graphics.Color.red(pixel)-android.graphics.Color.green(pixel))>200)
    }
}
