package io.github.currencortex.music

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.feature.settings.SettingsScreen
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SettingsHeaderTest {
    @get:Rule val compose=createComposeRule()
    private var underClicks=0
    private var backs=0
    private fun show(dark:Boolean=false,blur:Boolean=true,glass:Boolean=true) {
        compose.setContent {LeiTheme(AppearanceSettings(themeMode=if(dark)ThemeMode.DARK else ThemeMode.LIGHT,blur=blur,liquidGlass=glass)) {
            SettingsPage("顶栏测试","header_fixture",onBack={backs++}) {
                item {Canvas(Modifier.fillMaxWidth().height(600.dp).testTag("header_pattern").clickable {underClicks++}) {
                    val stripe=4.dp.toPx()
                    for(i in 0..(size.width/stripe).toInt())drawRect(if(i%2==0)Color.Black else Color.White,
                        Offset(i*stripe,0f),Size(stripe,size.height))
                }}
                items(20) {SettingsGroup {SettingsPreference("设置项目 $it")}}
            }
        }}
        compose.waitForIdle()
    }
    private fun scrollUnderHeader() {
        val density=InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        compose.onNodeWithTag("header_fixture").performSemanticsAction(SemanticsActions.ScrollBy) {it(0f,200*density)}
        compose.waitForIdle()
    }
    private fun capture(name:String) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        File(context.externalCacheDir,name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }
    private fun checkScrim(dark:Boolean,blur:Boolean=true,glass:Boolean=true) {
        show(dark=dark,blur=blur,glass=glass)
        val header=compose.onNodeWithTag("settings_floating_header")
        val bounds=header.fetchSemanticsNode().boundsInRoot
        assertTrue("The first item must clear both status bar and toolbar",
            compose.onNodeWithTag("header_pattern").fetchSemanticsNode().boundsInRoot.top>=bounds.bottom)
        scrollUnderHeader()
        capture(if(dark)"settings-header-dark-scrolled.png"else "settings-header-light-scrolled.png")
        assertTrue("Content must actually scroll underneath the header",
            compose.onNodeWithTag("header_pattern").fetchSemanticsNode().boundsInRoot.top<=bounds.top+1f)
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        fun contrast(y:Int):Int {
            val samples=((bitmap.width*.8f).toInt() until (bitmap.width*.95f).toInt()).map {x->(bitmap.getPixel(x,y) shr 16)and 255}
            return samples.max()-samples.min()
        }
        val covered=contrast((bounds.top+bounds.height*.1f).toInt())
        val middle=contrast((bounds.top+bounds.height*.7f).toInt())
        val uncovered=contrast((bounds.bottom+100).toInt())
        assertTrue("The fixture must have sharp stripes below the header",uncovered>200)
        assertTrue("The original .9-alpha scrim retains 10% of the stripe contrast",covered in 23..29)
        assertTrue("The scrim must fade toward the toolbar foot",middle>covered*2)
        bitmap.recycle()
        capture(if(dark)"settings-header-dark-scrolled.png"else "settings-header-light-scrolled.png")
        header.performTouchInput {click(Offset(width*.85f,height*.5f))}
        assertEquals("A transparent header cannot activate the preference underneath",0,underClicks)
        compose.onNodeWithTag("navigate_back").performClick();assertEquals(1,backs)
    }
    @Test fun lightHeaderMatchesTheOriginalScrimAndKeepsControlsInteractive()=checkScrim(false)
    @Test fun darkHeaderUsesTheSameScrimWithLiquidGlassDisabled()=checkScrim(true,glass=false)
    @Test fun disablingBlurKeepsTheOriginalScrimInsteadOfAddingAnOpaqueFallback()=checkScrim(false,blur=false,glass=false)

    private fun checkRoot(dark:Boolean,scale:Float) {
        var opened=0
        compose.setContent {LeiTheme(AppearanceSettings(themeMode=if(dark)ThemeMode.DARK else ThemeMode.LIGHT,
            customTextScale=true,textScale=scale)) {
            top.yukonga.miuix.kmp.basic.Scaffold {
            SettingsScreen(onAppearance={opened++},onNetwork={opened++},onStorage={opened++},onLogs={opened++},
                onAbout={opened++},onUpdates={opened++},onDesktopLyrics={opened++},onPlayback={opened++},
                onEffects={opened++},onAudioOutput={opened++})
            }
        }}
        val header=compose.onNodeWithTag("settings_root_toolbar").fetchSemanticsNode().boundsInRoot
        val first=compose.onNodeWithTag("open_updates").fetchSemanticsNode().boundsInRoot
        assertTrue("The first row must start below the floating toolbar",first.top>=header.bottom)
        val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("设置").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        assertEquals("Root title follows pageTitle",24f,layouts.single().layoutInput.style.fontSize.value,.001f)
        val ink=layouts.single().layoutInput.style.color
        assertTrue("The fixed title must stay readable in either theme",if(dark)ink.red>.7f else ink.red<.3f)
        val sectionLayouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("个性化").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(sectionLayouts)}
        assertEquals("Plain section title follows headline",17f,sectionLayouts.single().layoutInput.style.fontSize.value,.001f)
        capture(if(dark)"settings-root-nativeui-dark-large.png"else "settings-root-nativeui-light.png")
        val density=InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        compose.onNodeWithTag("settings_screen").performSemanticsAction(SemanticsActions.ScrollBy) {it(0f,150*density)}
        compose.waitForIdle()
        compose.onNodeWithText("设置").assertIsDisplayed()
        assertEquals("Toolbar must stay fixed while the list scrolls",header,
            compose.onNodeWithTag("settings_root_toolbar").fetchSemanticsNode().boundsInRoot)
        assertTrue("Rows must travel behind the header",compose.onNodeWithTag("open_updates")
            .fetchSemanticsNode().boundsInRoot.top<header.bottom)
        capture(if(dark)"settings-root-nativeui-dark-scrolled.png"else "settings-root-nativeui-light-scrolled.png")
        compose.onNodeWithTag("settings_floating_header").performTouchInput {click(Offset(width*.85f,height*.6f))}
        assertEquals("Covered rows cannot be activated through the title",0,opened)
        compose.onNodeWithTag("settings_screen").performScrollToNode(hasTestTag("open_about"))
        compose.onNodeWithTag("open_about").performScrollTo().performClick()
        assertEquals(1,opened)
    }
    @Test fun rootPageUsesFixedPageTitleAndPlainListHeaders()=checkRoot(false,1f)
    @Test fun darkRootKeepsHeaderFixedWithLargeText()=checkRoot(true,1.4f)
}
