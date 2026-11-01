package io.github.currencortex.music

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.ui.component.SettingsSwitch
import io.github.currencortex.music.ui.component.MusicTextAction
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class UnifiedSettingsComponentsTest {
    @get:Rule val compose=createComposeRule()
    private val checked=mutableStateOf(false)
    private val selected=mutableIntStateOf(0)
    private var toggles=0
    private var picks=0
    private fun show(dark:Boolean=false,large:Boolean=false) {
        compose.mainClock.autoAdvance=false
        compose.setContent {LeiTheme(AppearanceSettings(themeMode=if(dark)ThemeMode.DARK else ThemeMode.LIGHT)) {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,if(large)1.4f else density.fontScale)) {
                SettingsPage("设置组件","settings_fixture") {
                    item {SettingsGroup {
                        SettingsSwitch("预加载下一首",checked.value,{checked.value=it;toggles++},Modifier.testTag("fixture_toggle"),summary="拖动或点击均只保存一次")
                        SettingsSwitch("不可用选项",false,{},Modifier.testTag("fixture_disabled"),enabled=false)
                        SettingsOptionsRow("歌词来源",listOf("自动选择","网易云","CurrentMusic")[selected.intValue],
                            (0..2).map {i->SettingsOption("fixture_option_$i",listOf("自动选择","网易云","CurrentMusic")[i],selected.intValue==i,
                                onSelect={selected.intValue=i;picks++})},Modifier.testTag("fixture_options"))
                    }}
                    item {SettingsGroup {Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        MusicTextAction("归零",{},Modifier.testTag("fixture_action_one"))
                        MusicTextAction("恢复默认",{},Modifier.testTag("fixture_action_two"))
                    }}}
                }
            }
        }}
        advance()
    }
    private fun advance(ms:Long=750) {
        compose.waitForIdle();compose.mainClock.advanceTimeByFrame();compose.mainClock.advanceTimeBy(ms);compose.waitForIdle()
    }
    private fun capture(name:String) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.externalCacheDir,name).outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
    @Test fun rowTapThumbDragAndCancelledDragHaveOneOwnerAndDisabledStaysDisabled() {
        show()
        val row=compose.onNodeWithTag("fixture_toggle")
        row.assertIsOff().performClick();advance()
        row.assertIsOn();assertEquals(1,toggles)
        val density=InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        row.performTouchInput {
            val start=Offset(width-38*density,height/2f)
            down(start);moveTo(start-Offset(38*density,0f),delayMillis=220);up()
        };advance()
        row.assertIsOff();assertEquals(2,toggles)
        row.performTouchInput {
            val start=Offset(width-48*density,height/2f)
            down(start);moveTo(start+Offset(40*density,0f),delayMillis=220);cancel()
        };advance()
        row.assertIsOff();assertEquals(2,toggles)
        compose.onNodeWithTag("fixture_disabled").assertIsNotEnabled()
        capture("settings-components-light.png")
    }
    @Test fun valueMenuSavesImmediatelyClosesAndShowsTheNewSelectionWhenReopened() {
        show(dark=true)
        compose.onNodeWithTag("fixture_options").performClick();advance()
        compose.onNodeWithTag("fixture_option_0").assertIsSelected()
        capture("settings-components-choice.png")
        compose.onNodeWithTag("fixture_option_2").performClick();advance()
        compose.onNodeWithTag("settings_choice_panel").assertDoesNotExist()
        assertEquals(2,selected.intValue);assertEquals(1,picks)
        compose.onNodeWithTag("fixture_options").assertTextContains("CurrentMusic").performClick();advance()
        compose.onNodeWithTag("fixture_option_2").assertIsSelected()
    }
    @Test fun largeTextKeepsToolbarAndAdjacentActionsVisibleWithoutOverflow() {
        show(large=true)
        compose.onNodeWithTag("navigate_back").assertIsDisplayed()
        val first=compose.onNodeWithTag("fixture_action_one").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val second=compose.onNodeWithTag("fixture_action_two").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(first.right<=second.left)
        capture("settings-components-large-text.png")
    }
}
