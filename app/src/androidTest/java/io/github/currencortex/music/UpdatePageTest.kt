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
        var selected: UpdateSource? = null
        var channel: UpdateChannel? = null
        var ignored = false
        val state = mutableStateOf(UpdateTransferState(UpdateDownloadState.Downloading(42, 100), activeSource = UpdateSource.GITHUB))
        compose.setContent { TestTheme {
            UpdatePageContent(UpdateState.Available(release), state.value, onChangeSource = { selected = it },
                onChannel = { channel = it }, onIgnore = { ignored = true })
        } }
        compose.onNodeWithTag("update_source").assertDoesNotExist()
        compose.onNodeWithTag("update_menu").performClick()
        compose.onNodeWithText("下载源").performClick()
        compose.onNodeWithText(UpdateSource.GITHUB.label).assertIsSelected()
        capture("update-source-sheet.png")
        compose.onNodeWithTag("update_source_confirm").assertDoesNotExist()
        compose.onNodeWithText(UpdateSource.DPIK.label).performClick()
        compose.runOnIdle { assertEquals(UpdateSource.DPIK, selected) }
        compose.onNodeWithTag("update_options_sheet").assertDoesNotExist()
        compose.onNodeWithTag("update_menu").performClick()
        compose.onNodeWithText("更新设置").performClick()
        compose.onNode(hasText("正式版") and hasAnyAncestor(hasTestTag("update_options_sheet"))).assertIsSelected()
        compose.onNodeWithContentDescription("关闭更新设置").assertIsDisplayed()
        capture("update-settings-sheet.png")
        compose.onNodeWithText("预发布版").performClick()
        compose.runOnIdle { assertNull(channel) }
        compose.onNodeWithTag("update_settings_save").performClick()
        compose.runOnIdle { assertEquals(UpdateChannel.PRERELEASE, channel); state.value = UpdateTransferState() }
        compose.onNodeWithTag("update_menu").performClick()
        compose.onNodeWithText("忽略此版本").performClick()
        compose.runOnIdle { assertTrue(ignored) }
    }

    @Test fun selectingCurrentSourceAndCancellingSettingsKeepsTheActiveDownloadAndPreferences() {
        var sourceChanges = 0; var channelChanges = 0; var checkChanges = 0
        compose.setContent { TestTheme {
            UpdatePageContent(UpdateState.Available(release),
                UpdateTransferState(UpdateDownloadState.Downloading(42, 100), activeSource = UpdateSource.GITHUB),
                onChangeSource = { sourceChanges++ }, onChannel = { channelChanges++ }, onAutoCheck = { checkChanges++ })
        } }
        compose.onNodeWithTag("update_menu").performClick()
        compose.onNodeWithText("下载源").performClick()
        compose.onNodeWithText(UpdateSource.GITHUB.label).performClick()
        compose.onNodeWithTag("update_options_sheet").assertDoesNotExist()
        compose.onNodeWithTag("update_progress").assertTextContains("42%", substring = true)
        compose.onNodeWithTag("update_menu").performClick()
        compose.onNodeWithText("下载源").performClick()
        compose.onNodeWithText(UpdateSource.GITHUB.label).assertIsSelected()
        compose.onNodeWithTag("update_source_cancel").performClick()
        compose.onNodeWithTag("update_menu").performClick()
        compose.onNodeWithText("更新设置").performClick()
        compose.onNodeWithTag("update_auto_check").performClick()
        compose.onNodeWithText("预发布版").performClick()
        compose.onNodeWithTag("update_settings_cancel").performClick()
        compose.onNodeWithTag("update_menu").performClick()
        compose.onNodeWithText("更新设置").performClick()
        compose.onNode(hasText("正式版") and hasAnyAncestor(hasTestTag("update_options_sheet"))).assertIsSelected()
        compose.onNodeWithTag("update_auto_check").assertIsOn()
        compose.runOnIdle { assertEquals(0, sourceChanges); assertEquals(0, channelChanges); assertEquals(0, checkChanges) }
    }

    @Test fun settingsDialogSupportsBothThemesAndAlignedActions() {
        val theme = mutableStateOf(io.github.currencortex.music.data.settings.ThemeMode.LIGHT)
        compose.setContent { LeiTheme(AppearanceSettings(themeMode = theme.value, blur = false)) {
            top.yukonga.miuix.kmp.basic.Scaffold { UpdatePageContent(UpdateState.UpToDate) }
        } }
        compose.onNodeWithTag("update_menu").performClick()
        compose.onNodeWithText("更新设置").performClick()
        val cancel = compose.onNodeWithTag("update_settings_cancel").getUnclippedBoundsInRoot()
        val save = compose.onNodeWithTag("update_settings_save").getUnclippedBoundsInRoot()
        assertEquals(cancel.top.value, save.top.value, .5f)
        assertEquals((cancel.right - cancel.left).value, (save.right - save.left).value, .5f)
        capture("update-settings-light.png")
        compose.runOnIdle { theme.value = io.github.currencortex.music.data.settings.ThemeMode.DARK }
        compose.waitForIdle()
        compose.onNodeWithTag("update_settings_save").assertIsDisplayed()
        capture("update-settings-dark.png")
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

    @Test fun menuUsesSeparateWavesAndReversesWithoutRestartingOrDroppingShield() {
        compose.mainClock.autoAdvance = false
        compose.setContent { TestTheme { UpdatePageContent(UpdateState.Available(release)) } }
        compose.mainClock.advanceTimeBy(1000)
        val button = compose.onNodeWithTag("update_menu")
        val panel = compose.onNodeWithTag("update_overflow_panel")
        button.performClick()
        compose.mainClock.advanceTimeBy(96)
        val opening = panel.fetchSemanticsNode().config[UpdateMenuWavesKey]
        assertTrue("Scale and path must not share a single tween", kotlin.math.abs(opening.path - opening.scale) > .02f)
        assertTrue(opening.opacity > .1f && opening.scale > .1f)
        button.performClick()
        compose.mainClock.advanceTimeBy(16)
        val turning = panel.fetchSemanticsNode().config[UpdateMenuWavesKey]
        // Semantics clicks can pump recomposition frames; exact continuity is covered with
        // a controlled frame clock in UpdateMenuMotionTest, rather than a wall-clock delta here.
        assertTrue("Retargeting must not hide the panel: $opening -> $turning", turning.scale > .01f)
        compose.mainClock.advanceTimeBy(112)
        val closing = panel.fetchSemanticsNode().config[UpdateMenuWavesKey]
        assertTrue("The reversed wave should now be closing: $opening -> $closing",
            closing.scale > .001f && closing.scale < opening.scale)
        compose.onNodeWithTag("update_menu_dismiss").assertExists()
        capture("update-menu-spring-closing.png")
        button.performClick()
        compose.mainClock.advanceTimeBy(16)
        val reversed = panel.fetchSemanticsNode().config[UpdateMenuWavesKey]
        assertTrue("Reopening keeps the current scale instead of snapping to zero", reversed.scale > .01f)
        compose.mainClock.advanceTimeBy(800)
        val settled = panel.fetchSemanticsNode().config[UpdateMenuWavesKey]
        assertEquals(1f, settled.scale, .002f)
        capture("update-menu-spring-open.png")
        button.performClick()
        compose.mainClock.advanceTimeBy(32)
        panel.assertExists()
        compose.onNodeWithTag("update_menu_dismiss").assertExists()
        compose.mainClock.advanceTimeBy(800)
        panel.assertDoesNotExist()
        compose.onNodeWithTag("update_menu_dismiss").assertDoesNotExist()
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
        assertTrue("The glass panel should respond subtly to the finger", drag.displacement.getDistance() > .1f)
        compose.runOnIdle { assertEquals(0, retries); assertEquals(0, releases) }
        capture("update-menu-drag-highlight.png")
        panel.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { assertEquals(0, retries); assertEquals(1, releases) }
        panel.assertDoesNotExist()

        compose.onNodeWithTag("update_menu").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(400)
        val selected = point(2)
        panel.performTouchInput { down(selected); moveTo(Offset(-60f, selected.y), delayMillis = 150); up() }
        compose.mainClock.advanceTimeBy(400)
        panel.assertIsDisplayed()
        assertEquals(-1, panel.fetchSemanticsNode().config[UpdateMenuInteractionKey].selected)
        compose.runOnIdle { assertEquals("Dragging outside must cancel the option", 1, releases) }

        panel.performTouchInput { down(point(2)); cancel() }
        compose.mainClock.advanceTimeBy(400)
        panel.assertIsDisplayed()
        assertEquals(-1, panel.fetchSemanticsNode().config[UpdateMenuInteractionKey].selected)
        compose.runOnIdle { assertEquals("A cancelled gesture must not run the option", 1, releases) }
    }

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
