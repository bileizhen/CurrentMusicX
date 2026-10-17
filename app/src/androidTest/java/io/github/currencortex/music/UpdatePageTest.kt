package io.github.currencortex.music

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.update.*
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.update.UpdatePageContent
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

    @Test fun checkingAndDetailsMorphContinuouslyAndCanBeReversed() {
        val state = mutableStateOf<UpdateState>(UpdateState.Checking)
        compose.setContent { TestTheme {
            UpdatePageContent(state.value)
        } }
        compose.mainClock.autoAdvance = false
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

    @Test fun completedDownloadNeedsInstallTapAndExperienceCanRedownload() {
        var installs = 0; var downloads = 0
        compose.setContent { TestTheme {
            UpdatePageContent(UpdateState.Available(release), UpdateTransferState(UpdateDownloadState.Ready(File("fixture.apk"))),
                onInstall = { installs++ }, onDownload = { downloads++ }, downloadExperience = true)
        } }
        compose.runOnIdle { assertEquals(0, installs) }
        compose.onNodeWithTag("update_download").assertIsDisplayed().assertTextContains("点击安装").performClick()
        compose.onNodeWithTag("update_redownload").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, installs); assertEquals(1, downloads) }
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
        compose.onNodeWithTag("update_back").performClick()
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
        compose.onNodeWithText("下载源 · ${UpdateSource.GITHUB.label}").performClick()
        compose.onNodeWithText(UpdateSource.DPIK.label).performClick()
        compose.runOnIdle { assertEquals(UpdateSource.DPIK, selected) }
        compose.onNodeWithTag("update_menu").performClick()
        compose.onNodeWithText("更新设置").performClick()
        compose.onNodeWithText("预发布版").performClick()
        compose.runOnIdle { assertEquals(UpdateChannel.PRERELEASE, channel); state.value = UpdateTransferState() }
        compose.onNodeWithTag("update_menu").performClick()
        compose.onNodeWithText("忽略此版本").performClick()
        compose.runOnIdle { assertTrue(ignored) }
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
