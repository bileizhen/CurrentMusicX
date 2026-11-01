package io.github.currencortex.music

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.component.LocalMusicBottomInset
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** CommonPopupBox / CommonPopupMenu parity: measured width, carried press and modal hit testing. */
class SettingsPopupParityTest {
    @get:Rule val compose=createComposeRule()
    private var picks=0
    private var toggles=0
    private val selected=mutableIntStateOf(0)
    private val values=listOf("500 ms","1000 ms","2000 ms","5000 ms","8000 ms")
    private val summary="说明文字应使用与 Native UI 一样的字号，在菜单宽度不足时换成两行，超过两行才省略。"
    private fun show(dark:Boolean=false,long:Boolean=false,count:Int=5) {
        compose.mainClock.autoAdvance=false
        compose.setContent {LeiTheme(AppearanceSettings(themeMode=if(dark)ThemeMode.DARK else ThemeMode.LIGHT)) {
            SettingsPage("选项菜单","parity_page") {
                item {SettingsGroup {
                    SettingsOptionsRow("延迟测试超时",if(long)"自动选择"else if(count==5)values[selected.intValue]else "选项 ${selected.intValue}",
                        (0 until count).map {i->SettingsOption("parity_option_$i",
                            if(long)"来源 $i"else if(count==5)values[i]else "选项 $i",i==selected.intValue,
                            summary=if(long)summary else null,onSelect={selected.intValue=i;picks++})},
                        Modifier.testTag("parity_source"))
                    SettingsToggle("保持关闭",false,{toggles++},Modifier.testTag("parity_underlying"))
                }}
            }
        }}
        advance()
    }
    private fun advance(ms:Long=800) {
        compose.waitForIdle();compose.mainClock.advanceTimeByFrame();compose.mainClock.advanceTimeBy(ms);compose.waitForIdle()
    }
    private fun density()=InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
    private fun save(name:String) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.externalCacheDir,name).outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
    @Test fun numericPickerUsesIntrinsicMinimumWidthAndRowTapKeepsItsValueAnchor() {
        show()
        val value=compose.onNodeWithText(values[0],useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("parity_source").performClick();advance()
        val panel=compose.onNodeWithTag("settings_choice_panel").fetchSemanticsNode().boundsInRoot
        assertEquals(160f,panel.width/density(),1f)
        // The value button has 8dp horizontal padding, like _OptionsValueButton.
        assertEquals(value.top-8*density()-((40-22)/2)*density(),panel.top,2*density())
        save("settings-parity-numeric-light.png")
    }
    @Test fun valueOpensOnDownAndSamePointerCanSlideIntoAChoiceExactlyOnce() {
        show()
        val source=compose.onNodeWithTag("parity_source")
        val rest=source.fetchSemanticsNode().boundsInRoot
        val value=compose.onNodeWithText(values[0],useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        source.performTouchInput {down(value.center-rest.topLeft)};advance()
        val panel=compose.onNodeWithTag("settings_choice_panel").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(160f,panel.width/density(),1f)
        val target=compose.onNodeWithTag("parity_option_3").fetchSemanticsNode().boundsInRoot.center
        source.performTouchInput {moveTo(target-rest.topLeft,delayMillis=160);up()};advance()
        assertEquals(3,selected.intValue);assertEquals(1,picks)
        compose.onNodeWithTag("settings_choice_panel").assertDoesNotExist()
    }
    @Test fun stationaryOpeningReleaseKeepsMenuOpenAndOutsideTapCannotToggleThePage() {
        show()
        val source=compose.onNodeWithTag("parity_source")
        val rest=source.fetchSemanticsNode().boundsInRoot
        val value=compose.onNodeWithText(values[0],useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        source.performTouchInput {down(value.center-rest.topLeft)};advance()
        source.performTouchInput {up()};advance()
        compose.onNodeWithTag("settings_choice_panel").assertIsDisplayed()
        assertEquals(0,picks)
        compose.onNodeWithTag("parity_underlying").performTouchInput {click(Offset(10f,height/2f))};advance()
        assertEquals(0,toggles)
        compose.onNodeWithTag("settings_choice_panel").assertDoesNotExist()
    }
    @Test fun longSubtitlesUseThirteenSpWrapToTwoLinesAndRespectTheMaximumWidth() {
        show(long=true,count=3)
        compose.onNodeWithTag("parity_source").performClick();advance()
        val panel=compose.onNodeWithTag("settings_choice_panel").fetchSemanticsNode().boundsInRoot
        assertEquals(280f,panel.width/density(),1f)
        val layouts=mutableListOf<TextLayoutResult>()
        compose.onAllNodesWithText(summary,useUnmergedTree=true)[0]
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
        assertEquals(13.sp,layouts.single().layoutInput.style.fontSize)
        assertEquals(2,layouts.single().lineCount)
        save("settings-parity-subtitles-light.png")
    }
    @Test fun darkPickerCapsItsViewportAndRevealsTheCheckedOption() {
        selected.intValue=10
        show(dark=true,count=12)
        compose.onNodeWithTag("parity_source").performClick();advance()
        compose.onNodeWithTag("parity_option_10").assertIsDisplayed().assertIsSelected()
        val panel=compose.onNodeWithTag("settings_choice_panel").fetchSemanticsNode().boundsInRoot
        assertEquals(160f,panel.width/density(),1f)
        assertEquals(16f+7.5f*44f,panel.height/density(),2f)
        save("settings-parity-numeric-dark.png")
    }
    @Test fun bottomOverlayCannotCoverTheLastChoiceOfALowAnchoredMenu() {
        compose.mainClock.autoAdvance=false
        compose.setContent {LeiTheme(AppearanceSettings(themeMode=ThemeMode.LIGHT)) {
            Box(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalMusicBottomInset provides 100.dp) {
                    SettingsPage("底部浮层避让","overlay_parity") {
                        item {Spacer(Modifier.height(340.dp))}
                        item {SettingsGroup {SettingsOptionsRow("来源","自动",(0..3).map {i->
                            SettingsOption("overlay_option_$i","来源 $i",i==0,summary=summary,
                                onSelect={selected.intValue=i;picks++})},Modifier.testTag("overlay_source"))}}
                    }
                }
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(100.dp).testTag("bottom_overlay"))
            }
        }}
        advance()
        compose.onNodeWithTag("overlay_source").performClick();advance()
        val panel=compose.onNodeWithTag("settings_choice_panel").fetchSemanticsNode().boundsInRoot
        val overlay=compose.onNodeWithTag("bottom_overlay").fetchSemanticsNode().boundsInRoot
        assertTrue("The complete pane must clear the overlay, panel=$panel overlay=$overlay",panel.bottom<=overlay.top)
        compose.onNodeWithTag("overlay_option_3").assertIsDisplayed().performClick();advance()
        assertEquals(3,selected.intValue);assertEquals(1,picks)
    }
}
