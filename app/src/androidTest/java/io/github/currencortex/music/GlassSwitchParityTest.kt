package io.github.currencortex.music

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
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
import kotlin.math.abs

class GlassSwitchParityTest {
    @get:Rule val compose=createComposeRule()
    private val checked=mutableStateOf(false)
    private val enabled=mutableStateOf(true)
    private var changes=0
    private fun show(glass:Boolean=true,reject:Boolean=false) {
        compose.mainClock.autoAdvance=false
        compose.setContent {LeiTheme(AppearanceSettings(themeMode=ThemeMode.DARK,liquidGlass=glass)) {
            SettingsPage("原版开关交互","switch_parity") {
                item {SettingsGroup {Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.Center) {
                    GlassSwitch(checked.value,{changes++;if(!reject)checked.value=it},Modifier.testTag("switch"),enabled=enabled.value)
                }}}
                items(20) {SettingsGroup {SettingsPreference("滚动项目 $it")}}
            }
        }}
        advance(600)
    }
    private fun advance(ms:Long) {compose.waitForIdle();compose.mainClock.advanceTimeBy(ms);compose.waitForIdle()}
    private fun frame()=compose.onNodeWithTag("switch").fetchSemanticsNode().config[GlassSwitchGeometry]
    private fun capture(name:String) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        File(context.externalCacheDir,name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }
    @Test fun quickTapKeepsLensLiftedThroughCrossingAndRestoresCorrectOffThumb() {
        show()
        val node=compose.onNodeWithTag("switch")
        val off=node.captureToImage().asAndroidBitmap()
        val pixel=off.getPixel((off.width/3f).toInt(),off.height/2)
        assertTrue("Off thumb must be a readable gray foreground, not white or a dim border",((pixel shr 16)and 255) in 100..229)
        node.performTouchInput {down(center);advanceEventTime(8);up()}
        advance(160)
        assertTrue(checked.value);assertEquals(1,changes)
        assertTrue("The original keeps its tap lift for 200ms",frame().lift>.98f)
        assertTrue(frame().glass);assertTrue(frame().width>40f)
        capture("switch-parity-tap-midpoint.png")
        advance(350)
        assertEquals(0f,frame().lift,.001f);assertEquals(1f,frame().raw,.001f)
        node.performTouchInput {down(center);advanceEventTime(8);up()};advance(160)
        assertFalse(checked.value);assertEquals(2,changes);assertTrue(frame().lift>.98f)
        advance(350)
        assertEquals(0f,frame().raw,.001f);assertEquals(24f,frame().width,.001f)
        capture("switch-parity-off-restored.png")
    }
    @Test fun ownerChangesLiftWithoutTouchAndRapidReversalUsesAcceptedState() {
        show()
        val node=compose.onNodeWithTag("switch")
        node.performSemanticsAction(SemanticsActions.OnClick) {it()};advance(160)
        assertTrue(frame().lift>.98f);assertTrue(frame().position>frame().raw)
        node.performSemanticsAction(SemanticsActions.OnClick) {it()};advance(60)
        assertFalse(checked.value);assertEquals(2,changes)
        advance(500)
        assertEquals(0f,frame().raw,.001f);assertEquals(0f,frame().position,.001f)
        assertEquals(0f,frame().lift,.001f)
    }
    @Test fun draggingStretchesAtMidpointHandsOffWithoutJumpAndOnlyCommitsAChange() {
        show()
        val node=compose.onNodeWithTag("switch")
        val density=InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        node.performTouchInput {down(Offset(width/3f,centerY))};advance(180)
        node.performTouchInput {moveBy(Offset(10*density,0f),delayMillis=160)};advance(32)
        assertEquals(.5f,frame().raw,.04f)
        assertEquals(frame().raw,frame().position,.0001f)
        assertTrue(frame().width>49f);assertTrue(frame().height<39f);assertEquals(0,changes)
        capture("switch-parity-drag-stretched.png")
        node.performTouchInput {moveBy(Offset(3*density,0f));up()};advance(32)
        assertTrue(checked.value);assertEquals(1,changes)
        assertTrue("Drag handoff retains a linear position instead of jumping onto a tap curve",abs(frame().raw-frame().position)<.001f)
        advance(500)
        node.performTouchInput {down(center);moveBy(Offset(-9*density,0f),delayMillis=160);up()};advance(500)
        assertTrue(checked.value);assertEquals("Returning to the same half must not re-save",1,changes)
        node.performTouchInput {down(center);moveTo(Offset(0f,centerY),delayMillis=200);cancel()};advance(500)
        assertTrue(checked.value);assertEquals(1,changes);assertEquals(1f,frame().raw,.001f)
    }
    @Test fun rejectedChangeAndGlassOffReturnToTheOwnersState() {
        show(glass=false,reject=true)
        val node=compose.onNodeWithTag("switch")
        node.performTouchInput {down(center)};advance(180)
        assertTrue(frame().lift>.99f);assertFalse(frame().glass)
        node.performTouchInput {up()};advance(500)
        assertFalse(checked.value);assertEquals(1,changes);assertEquals(0f,frame().raw,.001f)
        assertEquals(0f,frame().lift,.001f)
        capture("switch-parity-glass-off.png")
    }
    @Test fun verticalScrollAndDisablingDuringAHoldNeverToggle() {
        show()
        val node=compose.onNodeWithTag("switch")
        node.performTouchInput {down(center)};advance(180)
        compose.runOnIdle {enabled.value=false};advance(180)
        node.performTouchInput {up()};advance(400)
        assertEquals(0,changes);assertEquals(0f,frame().lift,.001f)
        compose.runOnIdle {enabled.value=true};advance(100)
        node.performTouchInput {down(center);moveBy(Offset(0f,-180f),delayMillis=250);up()};advance(500)
        assertEquals(0,changes);assertFalse(checked.value)
    }
}
