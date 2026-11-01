package io.github.currencortex.music

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.update.*
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.update.UpdatePageContent
import io.github.currencortex.music.feature.update.UpdatePageHost
import io.github.currencortex.music.feature.update.UpdateMenuInteractionKey
import io.github.currencortex.music.feature.update.UpdateMenuWavesKey
import androidx.compose.ui.geometry.Offset
import io.github.currencortex.music.data.update.UpdateSettings
import io.github.currencortex.music.ui.theme.LeiTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Native layout and action fixtures only; never downloads or installs a real APK. */
class UpdatePageTest {
    @get:Rule val compose = createComposeRule()
    private val release = AppRelease("1.2.0", "## 1.2.0\n### 体验优化\n" +
        (1..25).joinToString("\n") { "- 第 $it 项更新：优化播放与操作体验" },
        "https://github.com/bileizhen/CurrentMusicX/releases/tag/v1.2.0",
        "https://github.com/bileizhen/CurrentMusicX/releases/download/v1.2.0/app.apk", "fixture.apk", false, 100, "0".repeat(64))

    @Test fun everyEntryHoldsCheckingForTwoSecondsEvenWhenResultArrivesEarly() {
        val visible = mutableStateOf(true)
        val result = mutableStateOf<UpdateState>(UpdateState.UpToDate)
        compose.mainClock.autoAdvance = false
        compose.setContent { TestTheme {
            UpdatePageHost(visible.value, result.value, {}, {}, {}, {}, UpdateTransferState(), {}, {}, {}, {},
                false, UpdateSettings(), {}, {}, "")
        } }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("正在检查更新").assertIsDisplayed()
        compose.runOnIdle { result.value = UpdateState.Failed("测试网络失败") }
        compose.mainClock.advanceTimeBy(900)
        compose.onNodeWithText("正在检查更新").assertIsDisplayed()
        compose.onNodeWithText("测试网络失败").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("测试网络失败").assertIsDisplayed()
        compose.runOnIdle { visible.value = false }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { result.value = UpdateState.Checking; visible.value = true }
        compose.mainClock.advanceTimeBy(3000)
        compose.onNodeWithText("正在检查更新").assertIsDisplayed()
        compose.runOnIdle { result.value = UpdateState.UpToDate }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("已是最新版本").assertIsDisplayed()
    }

    @Test fun checkingAndDetailsMorphContinuouslyAndCanBeReversed() {
        val state = mutableStateOf<UpdateState>(UpdateState.Checking)
        compose.setContent { TestTheme {
            UpdatePageContent(state.value)
        } }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("update_check_spinner").assertIsDisplayed()
        capture("update-checking-refined.png")
        val initial = compose.onNodeWithTag("update_status_card").getUnclippedBoundsInRoot().top
        compose.runOnIdle { state.value = UpdateState.Available(release) }
        compose.mainClock.advanceTimeBy(300)
        val moving = compose.onNodeWithTag("update_status_card").getUnclippedBoundsInRoot().top
        compose.mainClock.advanceTimeBy(500)
        val settled = compose.onNodeWithTag("update_status_card").getUnclippedBoundsInRoot().top
        assertTrue("Status card should move through intermediate positions", moving < initial && moving > settled)
        compose.onNodeWithTag("update_details").performClick()
        compose.mainClock.advanceTimeBy(250)
        val expanding = compose.onNodeWithTag("update_status_card").getUnclippedBoundsInRoot().top
        assertTrue(expanding < settled)
        compose.onNodeWithTag("update_back").performClick()
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("update_details").assertIsDisplayed()
        compose.onNodeWithTag("update_details").performClick()
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("update_message").assertIsDisplayed().performTouchInput { swipeUp() }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("update_download").assertIsDisplayed()
        capture("update-page-details.png")
    }

    @Test fun downloadStaysLiveOnBackAndCancelNeedsExplicitConfirmation() {
        var closes = 0; var cancels = 0; var downloads = 0
        val progress = mutableStateOf(UpdateTransferState(UpdateDownloadState.Downloading(42, 100), activeSource = UpdateSource.GITHUB))
        compose.setContent { TestTheme {
            UpdatePageContent(UpdateState.Available(release), progress.value,
                onDismiss = { closes++ }, onCancel = { cancels++ }, onDownload = { downloads++ })
        } }
        compose.onNodeWithTag("update_progress").assertTextContains("42%", substring = true)
        compose.onNodeWithTag("update_download").performClick()
        compose.runOnIdle { assertEquals(0, cancels) }
        compose.onNodeWithTag("update_keep_download").performClick()
        compose.onNodeWithTag("update_back").performClick()
        compose.runOnIdle { assertEquals(1, closes); assertEquals(0, cancels); assertEquals(0, downloads) }
        compose.onNodeWithTag("update_download").performClick()
        compose.onNodeWithTag("update_confirm_cancel").performClick()
        compose.runOnIdle { assertEquals(1, cancels) }
    }

    @Test fun completedDownloadNeedsInstallTapAndHasNoRepeatDownloadButton() {
        var installs = 0; var downloads = 0
        compose.setContent { TestTheme {
            UpdatePageContent(UpdateState.Available(release), UpdateTransferState(UpdateDownloadState.Ready(File("fixture.apk"))),
                onInstall = { installs++ }, onDownload = { downloads++ }, downloadExperience = true)
        } }
        compose.runOnIdle { assertEquals(0, installs) }
        compose.onNodeWithTag("update_download").assertIsDisplayed().assertTextContains("点击安装").performClick()
        compose.onNodeWithTag("update_redownload").assertDoesNotExist()
        compose.onNodeWithText("再次下载").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, installs); assertEquals(0, downloads) }
        capture("update-page-completed.png")
    }

    @Test fun upToDateShowsBundledNotesAndFailedCheckCanRetry() {
        val state = mutableStateOf<UpdateState>(UpdateState.UpToDate)
        var retries = 0
        compose.setContent { TestTheme {
            UpdatePageContent(state.value, installedNotes = "### 本机版本\n- 离线更新说明", onRetry = { retries++ })
        } }
        compose.onNodeWithText("已是最新版本").assertIsDisplayed()
        capture("update-page-latest.png")
        compose.onNodeWithTag("update_details").performClick()
        compose.onNodeWithText("离线更新说明", substring = true).assertIsDisplayed()
        compose.onNodeWithText("收起 ⌃").assertDoesNotExist()
        compose.onNodeWithContentDescription("收起更新说明").assertIsDisplayed().performClick()
        compose.onNodeWithTag("update_details").assertIsDisplayed()
        compose.runOnIdle { state.value = UpdateState.Failed("网络不可用") }
        compose.onNodeWithTag("update_retry").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun sourceSelectionAndChannelMenuUseNativeCallbacks() {
        var selected: UpdateSource? = null; var channel: UpdateChannel? = null; var ignored = false
        val state = mutableStateOf(UpdateTransferState(UpdateDownloadState.Downloading(42, 100), activeSource = UpdateSource.GITHUB))
        compose.setContent { TestTheme {
            UpdatePageContent(UpdateState.Available(release), state.value, onChangeSource = { selected = it },
                onChannel = { channel = it }, onIgnore = { ignored = true },sourceCheck=fixtureSourceCheck)
        } }
        compose.onNodeWithTag("update_menu").performClick(); settle()
        compose.onNodeWithTag("update_menu_action_1").performClick(); settle()
        compose.onNodeWithTag("update_source_GITHUB").assertIsSelected()
        capture("update-source-sheet.png")
        compose.onNodeWithTag("update_source_DPIK").performClick(); settle()
        compose.waitUntil(3000){selected==UpdateSource.DPIK}
        compose.onNodeWithTag("update_overflow_panel").assertDoesNotExist()
        compose.onNodeWithTag("update_menu").performClick(); settle()
        compose.onNodeWithTag("update_menu_action_0").performClick(); settle()
        compose.onNodeWithTag("update_channel").performClick(); settle()
        compose.onNodeWithTag("update_channel_stable").assertIsSelected()
        capture("update-settings-channel.png")
        compose.onNodeWithTag("update_channel_prerelease").performClick(); settle()
        compose.runOnIdle { assertEquals(UpdateChannel.PRERELEASE,channel);state.value = UpdateTransferState() }
        compose.onNodeWithTag("update_menu_action_back").performClick(); settle()
        compose.onNodeWithTag("update_menu_action_3").performClick(); settle()
        compose.waitUntil(3000){ignored}
    }

    @Test fun selectingCurrentSourceKeepsDownloadAndSettingsPersistWithoutSave() {
        var sourceChanges=0;var channelChanges=0;var checkChanges=0
        val preferences=mutableStateOf(UpdateSettings())
        compose.setContent {TestTheme {
            UpdatePageContent(UpdateState.Available(release),
                UpdateTransferState(UpdateDownloadState.Downloading(42,100),activeSource=UpdateSource.GITHUB),
                settings=preferences.value,onChangeSource={sourceChanges++},
                onChannel={channelChanges++;preferences.value=preferences.value.copy(channel=it)},
                onAutoCheck={checkChanges++;preferences.value=preferences.value.copy(autoCheckOnLaunch=it)},sourceCheck=fixtureSourceCheck)
        }}
        compose.onNodeWithTag("update_menu").performClick();settle()
        compose.onNodeWithTag("update_menu_action_1").performClick();settle()
        compose.onNodeWithTag("update_source_GITHUB").performClick();settle()
        compose.onNodeWithTag("update_overflow_panel").assertDoesNotExist()
        compose.onNodeWithTag("update_progress").assertTextContains("42%",substring=true)
        compose.onNodeWithTag("update_menu").performClick();settle()
        compose.onNodeWithTag("update_menu_action_0").performClick();settle()
        val auto=compose.onNodeWithTag("update_auto_check")
        auto.assertIsOn()
        auto.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role,
            androidx.compose.ui.semantics.Role.Checkbox))
        assertTrue("Update channel precedes automatic checking",
            compose.onNodeWithTag("update_channel").getUnclippedBoundsInRoot().bottom<=auto.getUnclippedBoundsInRoot().top)
        capture("update-settings-checkmark.png")
        auto.performClick();settle()
        compose.onNodeWithTag("update_overflow_panel").assertDoesNotExist()
        compose.runOnIdle {assertFalse(preferences.value.autoCheckOnLaunch);assertEquals(1,checkChanges)}
        compose.onNodeWithTag("update_menu").performClick();settle()
        compose.onNodeWithTag("update_menu_action_0").performClick();settle()
        compose.onNodeWithTag("update_auto_check").assertIsOff()
        compose.onNodeWithTag("update_channel").performClick();settle()
        compose.onNodeWithTag("update_channel_prerelease").performClick();settle()
        compose.onNodeWithTag("update_settings_cancel").assertDoesNotExist()
        compose.onNodeWithTag("update_settings_save").assertDoesNotExist()
        compose.onNodeWithTag("update_back").performClick();settle()
        compose.onNodeWithTag("update_menu").performClick();settle()
        compose.onNodeWithTag("update_menu_action_0").performClick();settle()
        compose.onNodeWithTag("update_auto_check").assertIsOff()
        compose.onNodeWithTag("update_channel").assertTextContains("预发布版",substring=true)
        compose.runOnIdle {assertEquals(0,sourceChanges);assertEquals(1,channelChanges);assertEquals(1,checkChanges)}
        compose.onNodeWithTag("update_auto_check").performClick();settle()
        compose.onNodeWithTag("update_overflow_panel").assertDoesNotExist()
        compose.runOnIdle {assertTrue(preferences.value.autoCheckOnLaunch);assertEquals(2,checkChanges)}
    }

    @Test fun settingsMenuHasConsistentRowWidthsAndHeaderReturnsToParent() {
        compose.setContent { TestTheme { UpdatePageContent(UpdateState.UpToDate,sourceCheck=fixtureSourceCheck) } }
        compose.onNodeWithTag("update_menu").performClick(); settle()
        compose.onNodeWithTag("update_menu_action_0").performClick(); settle()
        compose.onNodeWithTag("update_settings_cancel").assertDoesNotExist()
        compose.onNodeWithTag("update_settings_save").assertDoesNotExist()
        val auto=compose.onNodeWithTag("update_auto_check").getUnclippedBoundsInRoot()
        val option=compose.onNodeWithTag("update_channel").getUnclippedBoundsInRoot()
        assertEquals(auto.left.value,option.left.value,.5f)
        assertEquals((auto.right-auto.left).value,(option.right-option.left).value,.5f)
        assertTrue(option.bottom<=auto.top)
        compose.onNodeWithTag("update_channel").performClick(); settle()
        compose.onNodeWithTag("update_menu_action_back").performClick(); settle()
        compose.onNodeWithTag("update_auto_check").assertIsDisplayed()
        compose.onNodeWithTag("update_menu_action_back").performClick(); settle()
        compose.onNodeWithTag("update_menu_action_1").assertIsDisplayed()
        compose.onNodeWithTag("update_menu_action_1").performClick(); settle()
        compose.onNodeWithTag("update_source_cancel").assertDoesNotExist()
        compose.onNodeWithTag("update_source_confirm").assertDoesNotExist()
        capture("update-sources-glass.png")
    }

    @Test fun overflowMenuAnimatesFromButtonAndOutsideTapDoesNotActivatePage() {
        var downloads = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { TestTheme {
            UpdatePageContent(UpdateState.Available(release), onDownload = { downloads++ })
        } }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("update_menu").performClick()
        compose.mainClock.advanceTimeBy(90)
        compose.onNodeWithTag("update_overflow_panel").assertExists()
        capture("update-menu-opening.png")
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("更新设置").assertIsDisplayed()
        compose.onNodeWithText("重新检查").assertDoesNotExist()
        capture("update-menu-glass.png")
        compose.onNodeWithTag("update_menu_dismiss").performTouchInput {
            click(androidx.compose.ui.geometry.Offset(center.x, height * .85f))
        }
        compose.mainClock.advanceTimeBy(70)
        compose.onNodeWithTag("update_overflow_panel").assertExists()
        compose.runOnIdle { assertEquals("Dismissal must not trigger download underneath", 0, downloads) }
        // The button remains available while the closing panel retargets its animation.
        compose.onNodeWithTag("update_menu").performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("发布记录").assertIsDisplayed()
        compose.onNodeWithTag("update_back").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("update_overflow_panel").assertDoesNotExist()
        compose.onNodeWithTag("update_download").assertIsDisplayed()
    }

    @Test fun menuMorphReversesAndKeepsShieldUntilReturnCompletes() {
        compose.mainClock.autoAdvance=false
        compose.setContent { TestTheme { UpdatePageContent(UpdateState.Available(release)) } }
        compose.mainClock.advanceTimeBy(1000)
        val button=compose.onNodeWithTag("update_menu")
        val panel=compose.onNodeWithTag("update_overflow_panel")
        button.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()};compose.mainClock.advanceTimeBy(96)
        val opening=panel.fetchSemanticsNode().config[io.github.currencortex.music.ui.component.nativeui.GlassMenuFrameKey]
        assertTrue(opening.bounds.width>0f&&opening.content>0f&&opening.progress<1.1f)
        button.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()};compose.mainClock.advanceTimeBy(80)
        val returning=panel.fetchSemanticsNode().config[io.github.currencortex.music.ui.component.nativeui.GlassMenuFrameKey]
        assertTrue(returning.bounds.width>0f)
        compose.onNodeWithTag("update_menu_dismiss").assertExists()
        button.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()};compose.mainClock.advanceTimeBy(800)
        assertEquals(1f,panel.fetchSemanticsNode().config[UpdateMenuWavesKey].scale,.002f)
        button.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()};compose.mainClock.advanceTimeBy(32)
        panel.assertExists();compose.onNodeWithTag("update_menu_dismiss").assertExists()
        compose.mainClock.advanceTimeBy(800)
        panel.assertDoesNotExist();compose.onNodeWithTag("update_menu_dismiss").assertDoesNotExist()
    }

    @Test fun menuDragTracksOneCapsuleAndOnlyRunsTheReleasedOption() {
        var retries = 0
        var releases = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { TestTheme {
            UpdatePageContent(UpdateState.Available(release), onRetry = { retries++ }, onOpenRelease = { releases++ })
        } }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("update_menu").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(400)
        val panel = compose.onNodeWithTag("update_overflow_panel")
        fun point(index: Int): Offset = compose.onNodeWithTag("update_menu_action_$index")
            .fetchSemanticsNode().boundsInRoot.center - panel.fetchSemanticsNode().boundsInRoot.topLeft
        val first = point(0)
        val third = point(2)
        panel.performTouchInput { down(first) }
        compose.mainClock.advanceTimeBy(100)
        assertEquals(0, panel.fetchSemanticsNode().config[UpdateMenuInteractionKey].selected)
        panel.performTouchInput { moveTo(third, delayMillis = 180) }
        compose.mainClock.advanceTimeBy(300)
        val drag = panel.fetchSemanticsNode().config[UpdateMenuInteractionKey]
        assertEquals(2, drag.selected)
        assertTrue("The capsule follows the selected row", drag.highlight.top > first.y)
        assertEquals("Moving across rows must not pull the menu away from the finger",0f,drag.displacement.getDistance(),.1f)
        compose.runOnIdle { assertEquals(0, retries); assertEquals(0, releases) }
        capture("update-menu-drag-highlight.png")
        panel.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(600)
        compose.waitUntil(2000){releases==1}
        compose.runOnIdle { assertEquals(0, retries); assertEquals(1, releases) }
        panel.assertDoesNotExist()

        compose.onNodeWithTag("update_menu").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(400)
        val selected = point(2)
        panel.performTouchInput { down(selected); moveTo(Offset(-60f, selected.y), delayMillis = 150); up() }
        compose.mainClock.advanceTimeBy(400)
        panel.assertIsDisplayed()
        assertEquals(Int.MIN_VALUE, panel.fetchSemanticsNode().config[UpdateMenuInteractionKey].selected)
        compose.runOnIdle { assertEquals("Dragging outside must cancel the option", 1, releases) }

        panel.performTouchInput { down(point(2)); cancel() }
        compose.mainClock.advanceTimeBy(400)
        panel.assertIsDisplayed()
        assertEquals(Int.MIN_VALUE, panel.fetchSemanticsNode().config[UpdateMenuInteractionKey].selected)
        compose.runOnIdle { assertEquals("A cancelled gesture must not run the option", 1, releases) }
    }

    private val fixtureSourceCheck=UpdateSourceCheck {_,_->UpdateSourceHealth(UpdateSourceStatus.AVAILABLE,125)}
    private fun settle() { compose.mainClock.advanceTimeByFrame();compose.mainClock.advanceTimeBy(900);compose.waitForIdle() }

    @Composable private fun TestTheme(content: @Composable () -> Unit) {
        LeiTheme(AppearanceSettings(blur = false)) {
            top.yukonga.miuix.kmp.basic.Scaffold { content() }
        }
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
