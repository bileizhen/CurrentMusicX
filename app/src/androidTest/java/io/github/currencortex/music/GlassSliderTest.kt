package io.github.currencortex.music

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
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
import top.yukonga.miuix.kmp.basic.Slider as OriginalSlider
import kotlin.math.abs

class GlassSliderTest {
    @get:Rule val compose=createComposeRule()
    private val value=mutableFloatStateOf(.5f)
    private var commits=0
    private fun show(dark:Boolean=false,glass:Boolean=true,enabled:Boolean=true,rtl:Boolean=false,steps:Int=0,reference:Boolean=false) {
        compose.mainClock.autoAdvance=false
        compose.setContent {LeiTheme(AppearanceSettings(themeMode=if(dark)ThemeMode.DARK else ThemeMode.LIGHT,liquidGlass=glass)) {
            CompositionLocalProvider(LocalLayoutDirection provides if(rtl)LayoutDirection.Rtl else LayoutDirection.Ltr) {
                SettingsPage("玻璃滑块","slider_fixture") {
                    item {SettingsGroup {Column(Modifier.padding(16.dp)) {
                        GlassSlider(value.floatValue,{value.floatValue=it},Modifier.testTag("slider"),enabled=enabled,
                            steps=steps,onValueChangeFinished={commits++})
                        if(reference)Box(Modifier.fillMaxWidth().height(48.dp).testTag("original_slider"),contentAlignment=Alignment.Center) {
                            OriginalSlider(value.floatValue,{},enabled=enabled)
                        }
                    }}}
                    items(20) {SettingsGroup {SettingsPreference("滚动项目 $it")}}
                }
            }
        }}
        advance()
    }
    private fun advance(ms:Long=1000) {
        compose.waitForIdle();compose.mainClock.advanceTimeBy(ms);compose.waitForIdle()
    }
    private fun frame()=compose.onNodeWithTag("slider").fetchSemanticsNode().config[GlassSliderGeometry]
    private fun capture(name:String) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        File(context.externalCacheDir,name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }
    @Test fun dragStretchesGlassThumbClampsValueAndCommitsOnlyOnRelease() {
        show()
        val node=compose.onNodeWithTag("slider")
        capture("glass-slider-light.png")
        assertEquals(0f,frame().glass,.0001f)
        node.performTouchInput {down(center)};advance(300)
        assertTrue(frame().held);assertTrue(frame().thumb.scaleX>1.15f)
        assertTrue(frame().glass>.95f)
        node.performTouchInput {moveTo(Offset(width+80f,centerY+35f),delayMillis=240)};advance(160)
        assertEquals(1f,value.floatValue,.0001f);assertEquals(0,commits)
        assertTrue(frame().thumb.pull.x>0f);assertTrue(frame().thumb.pull.y>0f)
        capture("glass-slider-held.png")
        node.performTouchInput {up()};advance(1400)
        assertEquals(1,commits);assertFalse(frame().held)
        assertEquals(0f,frame().thumb.pull.x,.1f);assertEquals(1f,frame().thumb.scaleX,.01f)
        assertEquals(0f,frame().glass,.0001f)
    }
    @Test fun cancellingRestoresPreviewAndDoesNotSave() {
        show(dark=true)
        val node=compose.onNodeWithTag("slider")
        node.performTouchInput {down(center);moveTo(Offset(width*.8f,centerY),delayMillis=240)};advance(100)
        assertTrue(value.floatValue>.7f)
        node.performTouchInput {cancel()};advance(1400)
        assertEquals(.5f,value.floatValue,.0001f);assertEquals(0,commits);assertFalse(frame().held)
        capture("glass-slider-dark.png")
    }
    @Test fun verticalSwipeScrollsPageWithoutEditingOrSaving() {
        show()
        val first=compose.onNodeWithText("滚动项目 0").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("slider").performTouchInput {
            down(center);moveBy(Offset(0f,-180f),delayMillis=250);up()
        };advance()
        assertEquals(.5f,value.floatValue,.0001f);assertEquals(0,commits)
        assertTrue(compose.onNodeWithText("滚动项目 0").fetchSemanticsNode().boundsInRoot.top<first)
    }
    @Test fun rtlTapDiscreteAccessibilityAndInvalidTargetsKeepTheirContracts() {
        show(rtl=true,steps=3)
        val node=compose.onNodeWithTag("slider")
        node.performTouchInput {click(Offset(width*.1f,centerY))};advance()
        assertEquals(1f,value.floatValue,.0001f);assertEquals(1,commits)
        node.performSemanticsAction(SemanticsActions.SetProgress) {assertTrue(it(.62f))};advance()
        assertEquals(.5f,value.floatValue,.0001f);assertEquals(2,commits)
        node.performSemanticsAction(SemanticsActions.SetProgress) {assertFalse(it(Float.NaN))}
        assertEquals(2,commits)
        node.performSemanticsAction(SemanticsActions.SetProgress) {assertTrue(it(-10f))};advance()
        assertEquals(0f,value.floatValue,.0001f);assertEquals(3,commits)
    }
    @Test fun disabledAndGlassOffHaveUsableFallbackWithoutOpticalLayer() {
        show(dark=true,glass=false,enabled=false)
        val node=compose.onNodeWithTag("slider").assertIsNotEnabled()
        node.performTouchInput {click(Offset(width*.9f,centerY))};advance()
        node.performSemanticsAction(SemanticsActions.SetProgress) {assertFalse(it(1f))}
        assertEquals(.5f,value.floatValue,.0001f);assertEquals(0,commits)
        compose.onAllNodes(SemanticsMatcher.expectValue(LiquidGlassOpticsKey,true),useUnmergedTree=true).assertCountEquals(0)
        capture("glass-slider-disabled-fallback.png")
    }
    private fun assertRestingAppearanceMatchesOriginal(dark:Boolean) {
        show(dark=dark,reference=true)
        fun compare() {
            val actual=compose.onNodeWithTag("slider").captureToImage().asAndroidBitmap()
            val original=compose.onNodeWithTag("original_slider").captureToImage().asAndroidBitmap()
            assertEquals(original.width,actual.width);assertEquals(original.height,actual.height)
            var difference=0L
            for(y in 0 until actual.height)for(x in 0 until actual.width) {
                val a=actual.getPixel(x,y);val b=original.getPixel(x,y)
                for(shift in listOf(0,8,16))difference+=abs(((a shr shift)and 255)-((b shr shift)and 255))
            }
            assertTrue("The resting slider must retain the original flat track and opaque knob",
                difference.toDouble()/(actual.width*actual.height*3)<1.5)
            actual.recycle();original.recycle()
        }
        compare()
        val node=compose.onNodeWithTag("slider")
        node.performTouchInput {down(center)};advance(300)
        assertTrue(frame().glass>.95f)
        capture(if(dark)"glass-slider-dark-pressed.png"else "glass-slider-light-pressed.png")
        node.performTouchInput {up()};advance(1400)
        assertEquals(0f,frame().glass,.0001f);compare()
        capture(if(dark)"glass-slider-dark-restored.png"else "glass-slider-light-restored.png")
    }
    @Test fun lightRestingAndReleasedSliderMatchOriginalAppearance()=assertRestingAppearanceMatchesOriginal(false)
    @Test fun darkRestingAndReleasedSliderMatchOriginalAppearance()=assertRestingAppearanceMatchesOriginal(true)
}
