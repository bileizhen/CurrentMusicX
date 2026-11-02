package io.github.currencortex.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.update.*
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.update.UpdatePageContent
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import java.io.File
import kotlinx.coroutines.CompletableDeferred

/** Native input, paint geometry and callbacks. No real APK is downloaded or installed. */
class NativeUiComponentsTest {
    @get:Rule val compose=createComposeRule()
    private val release=AppRelease("1.2.6","- 改进按钮和菜单交互", "https://github.com/bileizhen/CurrentMusicX/releases/tag/v1.2.6",
        "https://github.com/bileizhen/CurrentMusicX/releases/download/v1.2.6/app.apk","fixture.apk",false,1024,"0".repeat(64))
    private val check=UpdateSourceCheck {_,_->UpdateSourceHealth(UpdateSourceStatus.AVAILABLE,125)}
    private fun showUpdate(onRelease:()->Unit={},onChannel:(UpdateChannel)->Unit={},onCancel:()->Unit={}) {
        compose.mainClock.autoAdvance=false
        compose.setContentWithPressDiagnostics {LeiTheme(AppearanceSettings()) {Scaffold {
            UpdatePageContent(UpdateState.Available(release),onOpenRelease={onRelease()},onChannel=onChannel,
                onCancel=onCancel,sourceCheck=check)
        }}}
        compose.mainClock.advanceTimeBy(1000);compose.waitForIdle()
    }
    private fun advance(ms:Long=600){
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame();compose.waitForIdle()
        compose.mainClock.advanceTimeBy(ms);compose.waitForIdle()
    }
    private fun capture(name:String) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(context.externalCacheDir,name).outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
    }
    @Test fun elasticButtonStretchesInTheDraggedAxisKeepsItsHitBoxAndCancelsOutside() {
        var clicks=0
        compose.mainClock.autoAdvance=false
        compose.setContentWithPressDiagnostics {LeiTheme(AppearanceSettings()) {Scaffold {Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
            GlassActionButton("可复用按钮",{clicks++},Modifier.width(200.dp).testTag("reused_button"))
        }}}}
        advance()
        val button=compose.onNodeWithTag("reused_button")
        val rest=button.fetchSemanticsNode().boundsInRoot
        button.performTouchInput{down(center)};advance(180)
        val held=button.fetchSemanticsNode().config[ElasticPressGeometry]
        assertTrue("A Native UI press swells rather than shrinking",held.scaleX>1f&&held.scaleY>1f)
        button.performTouchInput{moveBy(Offset(90f,0f))};advance(150)
        val stretched=button.fetchSemanticsNode().config[ElasticPressGeometry]
        assertTrue("Horizontal dragging stretches X further",stretched.scaleX>stretched.scaleY+.005f)
        assertTrue(stretched.pull.x>0f)
        assertEquals("Paint deformation must not move the target",rest,button.fetchSemanticsNode().boundsInRoot)
        capture("nativeui-reusable-button-stretch.png")
        button.performTouchInput{moveTo(Offset(width+200f,-200f));up()};advance(1000)
        assertEquals(0,clicks)
        val returned=button.fetchSemanticsNode().config[ElasticPressGeometry]
        assertEquals(1f,returned.scaleX,.002f);assertEquals(0f,returned.pull.getDistance(),.1f)
        button.performTouchInput{down(center);advanceEventTime(8);up()};advance(100)
        assertTrue("A quick tap still completes its lift",button.fetchSemanticsNode().config[ElasticPressGeometry].lift>.5f)
        advance(1200);assertEquals(1,clicks)
    }
    @Test fun normalElasticButtonKeepsItsClickWithoutPublishingPressFrames() {
        var clicks = 0
        compose.setContent {
            LeiTheme(AppearanceSettings()) {
                GlassActionButton("按钮", { clicks++ }, Modifier.testTag("normal_button"))
            }
        }
        val button = compose.onNodeWithTag("normal_button")
        val semantics = button.fetchSemanticsNode().config
        assertFalse(semantics.contains(ElasticPressGeometry))
        assertFalse(semantics.contains(GlassPressLight))
        button.performClick()
        assertEquals(1, clicks)
    }
    @Test fun popupOpensOnDownAndCarriesTheSamePointerIntoItsReleasedAction() {
        var releases=0
        showUpdate(onRelease={releases++})
        val button=compose.onNodeWithTag("update_menu")
        button.performTouchInput{down(center)};advance(80)
        compose.onNodeWithTag("update_overflow_panel").assertExists()
        val opening=compose.onNodeWithTag("update_overflow_panel").fetchSemanticsNode().config[GlassMenuFrameKey]
        assertTrue(opening.progress>0f&&opening.progress<1.2f)
        capture("nativeui-popup-opening-droplet.png")
        advance(500)
        val source=button.fetchSemanticsNode().boundsInRoot
        val target=compose.onNodeWithTag("update_menu_action_2").fetchSemanticsNode().boundsInRoot.center
        button.performTouchInput{moveTo(target-source.topLeft)};advance(80)
        button.performTouchInput{up()};advance(700)
        assertEquals(1,releases)
        compose.onNodeWithTag("update_overflow_panel").assertDoesNotExist()
        button.assertExists()
    }
    @Test fun popupPanelDragStretchesAndCancelNeverChoosesARow() {
        var releases=0
        showUpdate(onRelease={releases++})
        compose.onNodeWithTag("update_menu").performClick();advance()
        val panel=compose.onNodeWithTag("update_overflow_panel")
        val base=panel.fetchSemanticsNode().boundsInRoot
        val row=compose.onNodeWithTag("update_menu_action_2").fetchSemanticsNode().boundsInRoot.center-base.topLeft
        panel.performTouchInput{down(row)};advance(180)
        panel.performTouchInput{moveTo(Offset(width+24f,row.y))};advance(150)
        val geometry=panel.fetchSemanticsNode().config[ElasticPressGeometry]
        assertTrue(geometry.pull.x>1f)
        assertTrue(geometry.scaleX>geometry.scaleY+.003f)
        assertEquals(base,panel.fetchSemanticsNode().boundsInRoot)
        capture("nativeui-popup-drag-stretch.png")
        panel.performTouchInput{cancel()};advance(1000)
        assertEquals(0,releases)
        assertEquals(0f,panel.fetchSemanticsNode().config[ElasticPressGeometry].pull.getDistance(),.1f)
    }
    @Test fun nestedSettingsApplyImmediatelyWithoutSaveOrCancelActions() {
        val channels=mutableListOf<UpdateChannel>()
        showUpdate(onChannel={channels+=it})
        compose.onNodeWithTag("update_menu").performClick();advance()
        compose.onNodeWithTag("update_menu_action_0").performClick();advance()
        assertEquals("Settings must finish unfolding before the next tap",1f,
            compose.onNodeWithTag("update_overflow_panel").fetchSemanticsNode().config[GlassMenuUnfoldKey],.01f)
        capture("nativeui-popup-settings-before-channel.png")
        compose.onNodeWithTag("update_channel").performClick();advance()
        capture("nativeui-popup-settings-channel.png")
        val node=compose.onNodeWithTag("update_overflow_panel").fetchSemanticsNode()
        assertEquals("Path: ${node.config[GlassMenuPathKey]}, unfold=${node.config[GlassMenuUnfoldKey]}",2,node.config[GlassMenuDepthKey])
        compose.onNodeWithTag("update_channel_prerelease").performClick();advance()
        assertEquals(listOf(UpdateChannel.PRERELEASE),channels)
        compose.onNodeWithTag("update_settings_save").assertDoesNotExist()
        compose.onNodeWithTag("update_settings_cancel").assertDoesNotExist()
        capture("nativeui-popup-nested-settings.png")
        compose.onNodeWithTag("update_channel").performClick();advance()
        compose.onNodeWithTag("update_channel_prerelease").performClick();advance()
        assertEquals("Selecting the current value must not dispatch again",1,channels.size)
    }
    @Test fun genericMenuWorksOutsideUpdateAndDwellOpensItsSubmenuWithoutSelecting() {
        var picked=0
        compose.mainClock.autoAdvance=false
        compose.setContent {LeiTheme(AppearanceSettings()) {Scaffold {
            var open by remember{mutableStateOf(false)}
            var anchor by remember{mutableStateOf<Rect?>(null)}
            Box(Modifier.fillMaxSize().background(Color(0xFF18191D))) {
                GlassMenu(open,{open=it},listOf(GlassMenuItem("file_sort","排序",children=listOf(
                    GlassMenuItem("file_name","按名称",checked=true,onClick={picked++}),
                    GlassMenuItem("file_time","按时间",onClick={picked++})
                ))),anchor,null,toolbar={
                    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),contentAlignment=Alignment.TopEnd) {
                        GlassIconButton({open=!open},"文件菜单",Modifier.testTag("file_menu"),opensMenu=true,onBoundsChanged={anchor=it}){Text("⋮")}
                    }
                })
            }
        }}}
        advance()
        compose.onNodeWithTag("file_menu").performClick();advance()
        val panel=compose.onNodeWithTag("glass_menu_panel")
        panel.performTouchInput{down(center)};advance(600)
        assertEquals(1,panel.fetchSemanticsNode().config[GlassMenuDepthKey])
        panel.performTouchInput{up()};advance()
        assertEquals("Letting go on the landed header must not pick a child",0,picked)
        compose.onNodeWithTag("file_time").performClick();advance(800)
        assertEquals(1,picked)
        panel.assertDoesNotExist()
    }

    @Test fun existingAppearanceSwitchDisablesRealRimRefractionAndRestoresItLive() {
        val enabled=mutableStateOf(true)
        compose.setContent {LeiTheme(AppearanceSettings(themeMode=io.github.currencortex.music.data.settings.ThemeMode.DARK,
            liquidGlass=enabled.value,blur=false)) {Scaffold {
            val backdrop=rememberGlassBackdrop()
            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                Canvas(Modifier.size(220.dp,120.dp).then(if(backdrop!=null)Modifier.layerBackdrop(backdrop)else Modifier)) {
                    val stripe=16.dp.toPx()
                    repeat(15){i->drawRect(if(i%2==0)Color.Red else Color.Green,
                        Offset(i*stripe,0f),androidx.compose.ui.geometry.Size(stripe,size.height))}
                }
                Box(Modifier.size(220.dp,120.dp).testTag("optical_pane")
                    .glassSurface(backdrop,RoundedCornerShape(24.dp)))
            }
        }}}
        compose.waitForIdle()
        val pane=compose.onNodeWithTag("optical_pane")
        fun rimChannels():Pair<Int,Int> {
            val bitmap=pane.captureToImage().asAndroidBitmap()
            val pixel=bitmap.getPixel((bitmap.width*5f/220f).toInt(),bitmap.height/2)
            bitmap.recycle()
            return android.graphics.Color.red(pixel) to android.graphics.Color.green(pixel)
        }
        assertTrue(pane.fetchSemanticsNode().config[LiquidGlassOpticsKey])
        val refracted=rimChannels()
        capture("nativeui-ultra-refracted-stripes.png")
        assertTrue("At 5dp the circular bevel must read the green stripe ~25dp inside: $refracted",refracted.second>refracted.first+15)
        compose.runOnIdle{enabled.value=false};compose.waitForIdle()
        assertFalse(pane.fetchSemanticsNode().config[LiquidGlassOpticsKey])
        val flat=rimChannels()
        assertTrue("Disabled glass must read the unshifted red stripe: $flat",flat.first>flat.second+15)
        compose.runOnIdle{enabled.value=true};compose.waitForIdle()
        val restored=rimChannels()
        assertTrue(restored.second>restored.first+15)
    }

    @Test fun submenuStartsAtItsParentWidthExpandsAndCanReverseItsFold() {
        showUpdate()
        compose.onNodeWithTag("update_menu").performClick();advance()
        val panel=compose.onNodeWithTag("update_overflow_panel")
        val parent=panel.fetchSemanticsNode().boundsInRoot
        val parentRow=compose.onNodeWithTag("update_menu_action_1").fetchSemanticsNode().boundsInRoot
        val parentTextLeft=compose.onNode(hasText("下载源") and hasAnyAncestor(hasTestTag("update_menu_action_1")),useUnmergedTree=true)
            .fetchSemanticsNode().boundsInRoot.left
        compose.onNodeWithTag("update_menu_action_1").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()}
        compose.mainClock.advanceTimeByFrame();compose.waitForIdle()
        val initial=panel.fetchSemanticsNode()
        assertEquals(1,initial.config[GlassMenuDepthKey])
        assertEquals("The child header must start on the original row",parentTextLeft,
            compose.onNode(hasText("下载源") and hasAnyAncestor(hasTestTag("update_menu_action_back")),useUnmergedTree=true)
                .fetchSemanticsNode().boundsInRoot.left,1.5f)
        assertEquals(0f,initial.config[GlassMenuUnfoldKey],.001f)
        capture("nativeui-submenu-origin.png")
        advance(48)
        val growing=panel.fetchSemanticsNode()
        assertTrue(compose.onNode(hasText("下载源") and hasAnyAncestor(hasTestTag("update_menu_action_back")),useUnmergedTree=true)
            .fetchSemanticsNode().boundsInRoot.left<parentTextLeft-1f)
        assertTrue(growing.config[GlassMenuUnfoldKey] in .01f.. .99f)
        capture("nativeui-submenu-unfolding.png")
        advance()
        compose.onNodeWithTag("update_menu_action_back").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()}
        advance(48)
        compose.onNodeWithTag("update_menu_action_back").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()}
        advance()
        assertEquals(1,panel.fetchSemanticsNode().config[GlassMenuDepthKey])
        assertEquals(1f,panel.fetchSemanticsNode().config[GlassMenuUnfoldKey],.001f)
        capture("nativeui-submenu-restored.png")
    }

    @Test fun recededParentShadeCannotPaintOutsideItsRoundedCorner() {
        compose.mainClock.autoAdvance=false
        compose.setContent {LeiTheme(AppearanceSettings(themeMode=io.github.currencortex.music.data.settings.ThemeMode.DARK)) {Scaffold {
            var open by remember{mutableStateOf(false)}
            var anchor by remember{mutableStateOf<Rect?>(null)}
            val backdrop=rememberGlassBackdrop()
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.matchParentSize().then(if(backdrop!=null)Modifier.layerBackdrop(backdrop)else Modifier).background(Color.White))
                GlassMenu(open,{open=it},listOf(GlassMenuItem("corner_first","更新设置"),
                    GlassMenuItem("corner_child","下载源",children=listOf(GlassMenuItem("corner_leaf","镜像")))),anchor,backdrop,toolbar={
                    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),contentAlignment=Alignment.TopEnd) {
                        GlassIconButton({open=!open},"菜单",Modifier.testTag("corner_trigger"),backdrop,
                            opensMenu=true,onBoundsChanged={anchor=it}){Text("⋮")}
                    }
                })
            }
        }}}
        advance()
        compose.onNodeWithTag("corner_trigger").performClick();advance()
        val parent=compose.onNodeWithTag("glass_menu_panel").fetchSemanticsNode().boundsInRoot
        val row=compose.onNodeWithTag("corner_child").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("corner_child").performClick();advance()
        // The covered card scales 0.95 around the selected row's top-right corner.
        val corner=Offset(parent.right-parent.width*.95f,row.top+(parent.top-row.top)*.95f)+Offset(2f,2f)
        val image=compose.onRoot().captureToImage().asAndroidBitmap()
        val pixel=image.getPixel(corner.x.toInt(),corner.y.toInt())
        image.recycle()
        assertTrue("Rounded parent corner must expose the white scene, pixel=$pixel",
            minOf(android.graphics.Color.red(pixel),android.graphics.Color.green(pixel),android.graphics.Color.blue(pixel))>=250)
        capture("nativeui-submenu-rounded-parent.png")
    }

    @Test fun sourceMenuEndsAfterItsLastChoiceWithoutCancelOrAnEmptyTail() {
        var inset=0f
        compose.mainClock.autoAdvance=false
        compose.setContent {LeiTheme(AppearanceSettings()) {Scaffold {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.4f)) {
                inset=with(LocalDensity.current){8.dp.toPx()}
                UpdatePageContent(UpdateState.Available(release),sourceCheck=check)
            }
        }}}
        advance(1000)
        compose.onNodeWithTag("update_menu").performClick();advance()
        compose.onNodeWithTag("update_menu_action_1").performClick();advance()
        compose.onNodeWithTag("update_source_cancel").assertDoesNotExist()
        val panel=compose.onNodeWithTag("update_overflow_panel").fetchSemanticsNode().boundsInRoot
        val lastChoice=UpdateSource.available.maxOf {
            compose.onNodeWithTag("update_source_${it.name}").fetchSemanticsNode().boundsInRoot.bottom
        }
        assertEquals("Only the normal card inset may follow the last source",inset,panel.bottom-lastChoice,1.5f)
        capture("nativeui-sources-fit-content.png")
        compose.onNodeWithTag("update_menu_action_back").performClick();advance()
        compose.onNodeWithTag("update_menu_action_0").performClick();advance()
        val settings=compose.onNodeWithTag("update_overflow_panel").fetchSemanticsNode().boundsInRoot
        val lastSetting=compose.onNodeWithTag("update_auto_check").fetchSemanticsNode().boundsInRoot
        assertEquals("Settings must measure independently of the longer source list",inset,settings.bottom-lastSetting.bottom,1.5f)
    }

    @Test fun sourceMenuKeepsStatusBadgesAndSortsByHiddenLatencyWithCurrentMusicPinned() {
        val results=mapOf(
            UpdateSource.CURRENTMUSIC to UpdateSourceHealth(UpdateSourceStatus.AVAILABLE,1892),
            UpdateSource.GH_PROXY to UpdateSourceHealth(UpdateSourceStatus.AVAILABLE,231),
            UpdateSource.TBAP to UpdateSourceHealth(UpdateSourceStatus.AVAILABLE,1192),
            UpdateSource.GITHUB to UpdateSourceHealth(UpdateSourceStatus.TIMEOUT,8000),
            UpdateSource.GEEKERTAO to UpdateSourceHealth(UpdateSourceStatus.LIMITED,429),
            UpdateSource.DPIK to UpdateSourceHealth(UpdateSourceStatus.LIMITED,500))
        compose.mainClock.autoAdvance=false
        compose.setContent {LeiTheme(AppearanceSettings()) {Scaffold {
            UpdatePageContent(UpdateState.Available(release),sourceCheck=UpdateSourceCheck{_,source->results.getValue(source)})
        }}}
        advance(1000)
        compose.onNodeWithTag("update_menu").performClick();advance()
        compose.onNodeWithTag("update_menu_action_1").performClick();advance()
        val ordered=listOf(UpdateSource.CURRENTMUSIC,UpdateSource.GH_PROXY,UpdateSource.TBAP,
            UpdateSource.GITHUB,UpdateSource.GEEKERTAO,UpdateSource.DPIK)
        ordered.zipWithNext().forEach{(above,below)->
            assertTrue(compose.onNodeWithTag("update_source_${above.name}").fetchSemanticsNode().boundsInRoot.top<
                compose.onNodeWithTag("update_source_${below.name}").fetchSemanticsNode().boundsInRoot.top)
        }
        ordered.forEach{source->
            val badge=compose.onNodeWithTag("update_source_badge_${source.name}",useUnmergedTree=true)
            badge.assertIsDisplayed()
            val label=when(source){UpdateSource.CURRENTMUSIC->"CurrentMusic";UpdateSource.GITHUB->"github.com";else->source.label}
            val name=compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("update_source_${source.name}")),useUnmergedTree=true)
            assertTrue("The status badge must lead the source name",badge.fetchSemanticsNode().boundsInRoot.right<=name.fetchSemanticsNode().boundsInRoot.left)
            val status=when(results.getValue(source).status){UpdateSourceStatus.AVAILABLE->"可用";UpdateSourceStatus.TIMEOUT->"超时";else->"限流"}
            compose.onNode(hasText(status) and hasAnyAncestor(hasTestTag("update_source_badge_${source.name}")),useUnmergedTree=true).assertIsDisplayed()
        }
        compose.onAllNodes(hasText("ms",substring=true),useUnmergedTree=true).assertCountEquals(0)
        compose.onNodeWithTag("update_source_cancel").assertDoesNotExist()
        capture("nativeui-source-status-only.png")
    }

    @Test fun sourceSortingMovesThroughIntermediatePositionsAndKeepsDedicatedSourceStill() {
        val pending=UpdateSource.available.associateWith{CompletableDeferred<UpdateSourceHealth>()}
        val probe=UpdateSourceCheck{_,source->pending.getValue(source).await()}
        compose.mainClock.autoAdvance=false
        compose.setContent {LeiTheme(AppearanceSettings()) {Scaffold {
            UpdatePageContent(UpdateState.Available(release),sourceCheck=probe)
        }}}
        advance(1000)
        compose.onNodeWithTag("update_menu").performClick();advance()
        compose.onNodeWithTag("update_menu_action_1").performClick();advance()
        fun top(source:UpdateSource)=compose.onNodeWithTag("update_source_${source.name}").fetchSemanticsNode().boundsInRoot.top
        val pinned=top(UpdateSource.CURRENTMUSIC)
        val start=top(UpdateSource.GH_PROXY)
        val target=top(UpdateSource.GITHUB)
        compose.runOnIdle {
            pending.forEach{(source,result)->result.complete(when(source){
                UpdateSource.CURRENTMUSIC->UpdateSourceHealth(UpdateSourceStatus.AVAILABLE,1892)
                UpdateSource.GH_PROXY->UpdateSourceHealth(UpdateSourceStatus.AVAILABLE,231)
                UpdateSource.TBAP->UpdateSourceHealth(UpdateSourceStatus.AVAILABLE,1192)
                UpdateSource.GITHUB->UpdateSourceHealth(UpdateSourceStatus.TIMEOUT,8000)
                else->UpdateSourceHealth(UpdateSourceStatus.LIMITED,500)
            })}
        }
        advance(96)
        val middle=top(UpdateSource.GH_PROXY)
        assertTrue("The row must move rather than jump: $start -> $middle -> $target",middle<start-.5f&&middle>target+.5f)
        assertEquals(pinned,top(UpdateSource.CURRENTMUSIC),.5f)
        capture("nativeui-source-sorting-midway.png")
        advance(1200)
        assertEquals(target,top(UpdateSource.GH_PROXY),.5f)
        assertEquals(pinned,top(UpdateSource.CURRENTMUSIC),.5f)
    }

    @Test fun reusableSwitchDragsAcrossItsTrackAndCancelledDragDoesNotCommit() {
        val checked=mutableStateOf(false)
        var changes=0
        compose.mainClock.autoAdvance=false
        compose.setContent {LeiTheme(AppearanceSettings()) {Scaffold {Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
            GlassSwitch(checked.value,{checked.value=it;changes++},Modifier.testTag("reused_switch"))
        }}}}
        advance()
        val toggle=compose.onNodeWithTag("reused_switch")
        toggle.performTouchInput{down(Offset(width*.2f,centerY));moveTo(Offset(width*.85f,centerY),180)}
        advance(180);capture("nativeui-ultra-switch-stretch.png")
        assertTrue(toggle.fetchSemanticsNode().config[GlassSwitchPosition]>.5f)
        toggle.performTouchInput{up()};advance()
        toggle.assertIsOn();assertEquals(1,changes)
        toggle.performTouchInput{down(center);moveTo(Offset(0f,centerY),180);cancel()};advance()
        toggle.assertIsOn();assertEquals(1,changes)
    }

    @Test fun openingAndReturningGlassNeverIntroduceABlackPlateOverItsCapture() {
        val open=mutableStateOf(false)
        compose.mainClock.autoAdvance=false
        compose.setContent {LeiTheme(AppearanceSettings(themeMode=io.github.currencortex.music.data.settings.ThemeMode.DARK)) {Scaffold {
            val backdrop=rememberGlassBackdrop()
            var anchor by remember{mutableStateOf<Rect?>(null)}
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.matchParentSize().then(if(backdrop!=null)Modifier.layerBackdrop(backdrop)else Modifier).background(Color.White))
                GlassMenu(open.value,{open.value=it},listOf(GlassMenuItem("single_leaf","操作")),anchor,backdrop,toolbar={
                    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),contentAlignment=Alignment.TopEnd) {
                        GlassIconButton({open.value=!open.value},"菜单",Modifier.testTag("matte_trigger"),backdrop,
                            opensMenu=true,onBoundsChanged={anchor=it}) {Text("⋮",color=Color.White)}
                    }
                })
            }
        }}}
        advance()
        val baselineImage=compose.onNodeWithTag("matte_trigger").captureToImage().asAndroidBitmap()
        val baselinePixel=baselineImage.getPixel((baselineImage.width*.3f).toInt(),(baselineImage.height*.65f).toInt())
        val baseline=minOf(android.graphics.Color.red(baselinePixel),android.graphics.Color.green(baselinePixel),android.graphics.Color.blue(baselinePixel))
        baselineImage.recycle()
        assertTrue("The source capture itself must be valid",baseline>60)
        fun checkPlate(phase:String) {
            val frame=compose.onNodeWithTag("glass_menu_panel").fetchSemanticsNode().config[GlassMenuFrameKey]
            val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
            val point=frame.bounds.topLeft+Offset(frame.bounds.width*.3f,frame.bounds.height*.65f)
            val color=bitmap.getPixel(point.x.toInt().coerceIn(0,bitmap.width-1),point.y.toInt().coerceIn(0,bitmap.height-1))
            bitmap.recycle()
            val darkest=minOf(android.graphics.Color.red(color),android.graphics.Color.green(color),android.graphics.Color.blue(color))
            assertTrue("$phase introduced a dark matte: min channel=$darkest, source=$baseline, frame=$frame",darkest>=baseline-10)
        }
        compose.onNodeWithTag("matte_trigger").performTouchInput{down(center)}
        repeat(12){advance(32);checkPlate("opening $it")}
        compose.onNodeWithTag("matte_trigger").performTouchInput{up()};advance()
        val settled=compose.onNodeWithTag("glass_menu_panel").fetchSemanticsNode().config[GlassMenuFrameKey].bounds
        val edges=compose.onRoot().captureToImage().asAndroidBitmap()
        val rimPixels=kotlin.math.ceil(.67f*compose.density.density).toInt()
        val outside=android.graphics.Color.red(edges.getPixel(kotlin.math.floor(settled.left).toInt()-rimPixels-1,settled.center.y.toInt()))
        val inside=android.graphics.Color.red(edges.getPixel(kotlin.math.ceil(settled.left).toInt()+2,settled.center.y.toInt()))
        val beyondShadow=android.graphics.Color.red(edges.getPixel((settled.left-48*compose.density.density).toInt().coerceAtLeast(0),settled.center.y.toInt()))
        edges.recycle()
        // A soft exterior shadow now separates light panes from white settings cards.
        // Sample beyond the .67dp outer hairline and beyond the wide 29dp shadow.
        // Neither layer should soften the optical edge or tint the far background.
        assertTrue("The pane boundary must resolve within physical pixels, outside=$outside inside=$inside far=$beyondShadow",
            outside>=220&&outside-inside>80&&inside<180&&beyondShadow>=252)
        compose.runOnIdle{open.value=false}
        repeat(8){advance(32);checkPlate("return $it")}
        val panel=compose.onNodeWithTag("glass_menu_panel")
        var ticks=0
        while(panel.fetchSemanticsNode().config[GlassMenuFrameKey].glyph<1f&&ticks++<12) {
            compose.mainClock.advanceTimeByFrame();compose.waitForIdle()
        }
        val handoff=panel.fetchSemanticsNode().config[GlassMenuFrameKey]
        assertEquals(0f,handoff.blend,.001f);assertEquals(2.5f,handoff.frost,.001f)
        val beforeHandoff=compose.onRoot().captureToImage().asAndroidBitmap()
        val buttonBounds=compose.onNodeWithTag("matte_trigger").fetchSemanticsNode().boundsInRoot
        advance();panel.assertDoesNotExist()
        val afterHandoff=compose.onRoot().captureToImage().asAndroidBitmap()
        var difference=0L;var samples=0
        for(y in buttonBounds.top.toInt()..buttonBounds.bottom.toInt())for(x in buttonBounds.left.toInt()..buttonBounds.right.toInt()) {
            // Exclude antialiased outermost pixels; compare the material and source glyph.
            if((Offset(x.toFloat(),y.toFloat())-buttonBounds.center).getDistance()<buttonBounds.width*.35f) {
                val a=beforeHandoff.getPixel(x,y);val b=afterHandoff.getPixel(x,y)
                difference+=kotlin.math.abs(android.graphics.Color.red(a)-android.graphics.Color.red(b))+
                    kotlin.math.abs(android.graphics.Color.green(a)-android.graphics.Color.green(b))+
                    kotlin.math.abs(android.graphics.Color.blue(a)-android.graphics.Color.blue(b))
                samples+=3
            }
        }
        beforeHandoff.recycle();afterHandoff.recycle()
        assertTrue("The last droplet and real button must hand off without a material/glyph snap: ${difference.toFloat()/samples}",difference.toFloat()/samples<4f)
    }
}
