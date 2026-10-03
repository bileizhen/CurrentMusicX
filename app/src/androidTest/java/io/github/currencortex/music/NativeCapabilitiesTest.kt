package io.github.currencortex.music

import android.content.ContentValues
import android.provider.MediaStore
import android.os.Build
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.logging.DiagnosticExporter
import io.github.currencortex.music.core.update.AppRelease
import io.github.currencortex.music.core.update.UpdateState
import io.github.currencortex.music.core.update.UpdateChannel
import io.github.currencortex.music.feature.update.UpdateDialogContent
import io.github.currencortex.music.ui.CurrentMusicApp
import io.github.currencortex.music.ui.theme.LeiTheme
import io.github.currencortex.music.data.settings.AppearanceSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NativeCapabilitiesTest {
    @get:Rule val compose = createComposeRule()
    private val application get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    private val container get() = application.container

    @Before fun prepare() = runBlocking {
        container.updateSettings.setAutoCheck(false)
        container.updateSettings.setChannel(UpdateChannel.STABLE)
        container.settings.edit { AppearanceSettings(blur = false) }
    }

    @Test fun navigationLegalDocumentsAndBackRemainAccessible() {
        compose.setContent { CurrentMusicApp(container) }
        compose.onNodeWithTag("tab_2").assertDoesNotExist()
        compose.onNodeWithTag("tab_1").performClick()
        compose.onNodeWithText("关于").performScrollTo().performClick()
        compose.onNodeWithText("检查更新").assertDoesNotExist()
        compose.onNodeWithText("导出日志").assertDoesNotExist()
        compose.onNodeWithTag("tab_1").assertDoesNotExist()
        compose.onNodeWithText("开源许可").performScrollTo().performClick()
        try {
            compose.waitUntil(5000) { compose.onAllNodesWithText("GNU GENERAL PUBLIC LICENSE", substring = true).fetchSemanticsNodes().isNotEmpty() }
        } catch (error: Throwable) {
            throw AssertionError(compose.onRoot(useUnmergedTree = true).printToString(), error)
        }
        compose.onNodeWithTag("navigate_back").performClick()
        compose.onNodeWithText("隐私").performScrollTo().performClick()
        try {
            compose.waitUntil(5000) { compose.onAllNodesWithText("本地数据").fetchSemanticsNodes().isNotEmpty() }
        } catch (error: Throwable) {
            throw AssertionError(compose.onRoot(useUnmergedTree = true).printToString(), error)
        }
        compose.onNodeWithTag("navigate_back").performClick()
        compose.onNodeWithText("第三方声明").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("MIUIX", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("navigate_back").performClick()
        compose.onNodeWithTag("navigate_back").performClick()
        compose.onNodeWithTag("tab_1").assertExists()
        compose.onNodeWithText("导出日志").assertExists()
    }

    @Test fun predictiveBackSeeksParentCancelsAndCommitsFromEitherEdge() {
        org.junit.Assume.assumeTrue(Build.VERSION.SDK_INT >= 34)
        lateinit var activity: ComponentActivity
        compose.setContent {
            activity = LocalContext.current.activity()
            CurrentMusicApp(container)
        }
        compose.onNodeWithTag("tab_1").performClick()
        compose.onNodeWithText("外观").performScrollTo().performClick()
        val originalLeft = compose.onNodeWithTag("appearance_screen").fetchSemanticsNode().boundsInRoot.left
        for (edge in listOf(BackEventCompat.EDGE_LEFT, BackEventCompat.EDGE_RIGHT)) {
            compose.mainClock.autoAdvance = false
            compose.runOnUiThread {
                activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 500f, 0f, edge))
                activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(300f, 500f, .55f, edge))
            }
            compose.mainClock.advanceTimeBy(240)
            compose.onNodeWithTag("settings_screen").assertExists()
            val seekingLeft = compose.onNodeWithTag("appearance_screen").fetchSemanticsNode().boundsInRoot.left
            assertTrue("Predictive back must translate the current page to reveal its parent", seekingLeft > originalLeft + 20f)
            compose.runOnUiThread { activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            compose.onNodeWithTag("appearance_screen").assertIsDisplayed()
            compose.onNodeWithTag("settings_screen").assertDoesNotExist()
            assertEquals(originalLeft, compose.onNodeWithTag("appearance_screen").fetchSemanticsNode().boundsInRoot.left, 1f)
        }
        compose.runOnUiThread {
            activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 500f, 0f, BackEventCompat.EDGE_LEFT))
            activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(500f, 500f, .8f, BackEventCompat.EDGE_LEFT))
            activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.onNodeWithTag("appearance_screen").assertDoesNotExist()
        compose.onNodeWithTag("settings_screen").assertIsDisplayed()
        compose.onNodeWithTag("tab_1").assertIsDisplayed()
    }

    @Test fun disabledPredictionKeepsPageStillAndBackDismissesPopupsFirst() {
        runBlocking { container.settings.edit { it.copy(predictiveBack = false) } }
        lateinit var activity: ComponentActivity
        compose.setContent {
            activity = LocalContext.current.activity()
            CurrentMusicApp(container)
        }
        compose.onNodeWithTag("tab_1").performClick()
        compose.onNodeWithText("外观").performScrollTo().performClick()
        val original = compose.onNodeWithTag("appearance_screen").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 500f, 0f, BackEventCompat.EDGE_LEFT))
            activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(300f, 500f, .55f, BackEventCompat.EDGE_LEFT))
        }
        compose.mainClock.advanceTimeBy(240)
        assertEquals(original, compose.onNodeWithTag("appearance_screen").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("settings_screen").assertDoesNotExist()
        compose.runOnUiThread { activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("显示缩放").performScrollTo().performClick()
        compose.onNodeWithText("80% - 120%").assertIsDisplayed()
        compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("80% - 120%").assertDoesNotExist()
        compose.onNodeWithTag("appearance_screen").assertIsDisplayed()
        compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("settings_screen").assertIsDisplayed()
        compose.onNodeWithText("关于").performScrollTo().performClick()
        compose.onNodeWithTag("about_screen").performScrollToNode(hasText("bileizhen"))
        compose.onNodeWithText("bileizhen").performClick()
        compose.onNodeWithText("成员信息").assertIsDisplayed()
        compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("成员信息").assertDoesNotExist()
        compose.onNodeWithTag("about_screen").assertIsDisplayed()
        compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("导出日志").performScrollTo().performClick()
        compose.onNodeWithTag("export_logs_save").assertExists()
        compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("export_logs_save").assertDoesNotExist()
        compose.onNodeWithText("检查更新").performScrollTo().performClick()
        compose.onNodeWithText("测试预览").assertExists()
        compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("测试预览").assertDoesNotExist()
        compose.onNodeWithTag("settings_screen").assertIsDisplayed()
    }

    @Test fun nestedDocumentStackAndAboutScrollSurviveRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { CurrentMusicApp(container) }
        compose.onNodeWithTag("tab_1").performClick()
        compose.onNodeWithText("关于").performScrollTo().performClick()
        compose.onNodeWithText("开源许可").performScrollTo().performClick()
        compose.onNodeWithText("Apache License 2.0").performScrollTo().performClick()
        compose.onNodeWithText("Apache License 2.0").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Apache License 2.0").assertExists()
        compose.onNodeWithTag("navigate_back").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("GNU GENERAL PUBLIC LICENSE", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("navigate_back").performClick()
        compose.onNodeWithText("开源许可").assertIsDisplayed()
        compose.onNodeWithTag("navigate_back").performClick()
        compose.onNodeWithTag("tab_1").assertIsDisplayed()
        compose.onNodeWithText("导出日志").assertExists()
    }

    @Test fun aboutAuthorCardAndMemberDetailsRemainAccessibleWhenScrolling() {
        compose.setContent { CurrentMusicApp(container) }
        compose.onNodeWithTag("tab_1").performClick()
        compose.onNodeWithText("关于").performScrollTo().performClick()
        compose.onNodeWithTag("about_screen").performScrollToNode(hasText("bileizhen"))
        compose.onNodeWithText("开发组").assertExists()
        compose.onNodeWithText("开发 · 设计 · 维护").assertIsDisplayed()
        compose.onNodeWithText("bileizhen").performClick()
        compose.onNodeWithText("成员信息").assertIsDisplayed()
        compose.onNodeWithText("分工").assertExists()
        compose.onNode(hasText("GitHub") and hasAnyAncestor(hasTestTag("member_dialog_content"))).assertDoesNotExist()
        compose.onNodeWithText("问题反馈").assertDoesNotExist()
        val close = compose.onNodeWithText("关闭").assertIsDisplayed().fetchSemanticsNode()
        assertTrue("Member close button must remain usable with long text", close.boundsInWindow.height >= 40f * application.resources.displayMetrics.density)
        compose.onNodeWithText("关闭").performClick()
        val configured = io.github.currencortex.music.core.config.AboutCredits.sections.flatMap { it.members }
        assertEquals(listOf(io.github.currencortex.music.core.config.AppMetadata.AUTHOR), configured.map { it.name })
        compose.onNodeWithText("贡献者 · 内测").assertDoesNotExist()
        compose.onNodeWithTag("about_screen").performScrollToNode(hasText("GitHub"))
        compose.onNodeWithTag("about_screen").performScrollToNode(hasText("bileizhen"))
        compose.onNodeWithText("bileizhen").assertIsDisplayed().performClick()
        compose.onNodeWithText("成员信息").assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("检查更新").assertDoesNotExist()
        compose.onNodeWithText("导出日志").assertDoesNotExist()
    }

    @Test fun floatingBlurAndGlassSwitchesSelectRenderingWithoutLosingTabs() {
        compose.setContent { CurrentMusicApp(container) }
        compose.onNodeWithTag("plain_floating_bar").assertExists()
        runBlocking { container.settings.edit { it.copy(blur = true, liquidGlass = true) } }
        if (Build.VERSION.SDK_INT >= 33) {
            compose.waitUntil(5000) { compose.onAllNodesWithTag("glass_floating_bar").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("tab_1").performClick()
            runBlocking { container.settings.edit { it.copy(liquidGlass = false) } }
            compose.waitUntil(5000) { compose.onAllNodesWithTag("blur_floating_bar").fetchSemanticsNodes().isNotEmpty() }
        } else compose.onNodeWithTag("plain_floating_bar").assertExists()
        runBlocking { container.settings.edit { it.copy(floatingBar = false) } }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("standard_navigation_bar").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab_2").assertDoesNotExist()
        compose.onNodeWithTag("tab_1").performClick()
        compose.onNodeWithText("关于").performScrollTo().performClick()
        compose.onNodeWithTag("about_logo").assertExists()
    }

    @Test fun updateNotesAndActionsAreUsable() {
        runBlocking {
            container.updateSettings.ignoreRelease(UpdateChannel.STABLE, "1.2.0")
            container.updateSettings.ignoreRelease(UpdateChannel.PRERELEASE, "1.3.0-rc.1")
            val stored = container.updateSettings.snapshot().ignoredVersions
            assertEquals("1.2.0", stored[UpdateChannel.STABLE])
            assertEquals("1.3.0-rc.1", stored[UpdateChannel.PRERELEASE])
        }
        var opened = ""
        var ignored = false
        val release = AppRelease("1.2.0-rc.1", "A test release", "https://github.com/example/app/releases/tag/v1.2.0-rc.1",
            null, null, true)
        compose.setContent {
            LeiTheme(AppearanceSettings()) {
                top.yukonga.miuix.kmp.basic.Scaffold {
                UpdateDialogContent(UpdateState.Available(release), {}, {}, { ignored = true }, { opened = it })
                }
            }
        }
        compose.onNodeWithText("A test release").assertExists()
        compose.onNodeWithText("查看发布").performClick()
        assertEquals(release.pageUrl, opened)
        compose.onNodeWithText("忽略此版本").performClick()
        assertTrue(ignored)
    }

    @Test fun debugUpdatePreviewCanBeOpenedDismissedAndReopened() {
        assertTrue(BuildConfig.UPDATE_DIALOG_PREVIEW)
        compose.setContent { CurrentMusicApp(container) }
        compose.onNodeWithTag("tab_1").performClick()
        compose.onNodeWithTag("settings_screen").performScrollToNode(hasText("检查更新"))
        compose.onNodeWithText("检查更新").performClick()
        compose.onNodeWithText("测试预览").assertExists()
        val minimumButtonHeight = 40f * application.resources.displayMetrics.density
        for (label in listOf("下载更新", "关闭")) {
            val button = compose.onNodeWithText(label).assertIsDisplayed().fetchSemanticsNode()
            assertTrue("$label must be fully usable without scrolling", button.boundsInWindow.height >= minimumButtonHeight)
        }
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("测试预览").assertDoesNotExist()
        compose.onNodeWithTag("settings_screen").performScrollToNode(hasText("检查更新"))
        compose.onNodeWithText("检查更新").performClick()
        compose.onNodeWithText("忽略此版本").performClick()
        compose.onNodeWithTag("settings_screen").performScrollToNode(hasText("检查更新"))
        compose.onNodeWithText("检查更新").performClick()
        compose.onNodeWithText("测试预览").assertExists()
    }

    @Test fun appearancePreviewAndChannelMenuMatchNativeControls() {
        compose.setContent { CurrentMusicApp(container) }
        compose.onNodeWithTag("tab_1").performClick()
        compose.onNodeWithTag("update_channel").performClick()
        compose.onNodeWithText("仅接收稳定发布").assertExists()
        compose.onNodeWithText("提前获取 rc 测试版本").performClick()
        compose.waitUntil(5000) { container.updateSettings.state.value.channel == UpdateChannel.PRERELEASE }
        compose.onNodeWithText("提前获取 rc 测试版本").assertDoesNotExist()
        compose.onNodeWithText("外观").performScrollTo().performClick()
        compose.onNodeWithTag("theme_preview").assertExists()
        compose.onNodeWithText("深色").performClick()
        compose.waitUntil(5000) { container.settings.state.value.themeMode == io.github.currencortex.music.data.settings.ThemeMode.DARK }
        compose.onNodeWithTag("setting_blur").performScrollTo().performClick()
        compose.waitUntil(5000) { container.settings.state.value.blur }
        compose.onNodeWithTag("navigate_back").performClick()
        compose.onNodeWithTag("tab_0").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("glass_floating_bar").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun logExportIncludesRedactedDiagnostics() {
        container.logger.error("Test", "sample failure token=fake-secret", java.io.IOException("offline"))
        compose.setContent { CurrentMusicApp(container) }
        compose.onNodeWithTag("tab_1").performClick()
        compose.onNodeWithText("导出日志").performScrollTo().performClick()
        compose.onNodeWithTag("export_logs_save").assertIsEnabled()
        compose.onNodeWithTag("export_logs_share").assertIsEnabled()
        compose.onNodeWithText("搜索日志").assertDoesNotExist()
        compose.onNodeWithText("清空日志").assertDoesNotExist()
        val file = runBlocking { DiagnosticExporter.create(application, container.logger) }
        val report = java.util.zip.ZipFile(file).use { zip ->
            assertNotNull(zip.getEntry("logs.txt"))
            zip.getInputStream(zip.getEntry("diagnostics.txt")).bufferedReader().use { it.readText() }
        }
        assertFalse(report.contains("fake-secret"))
        assertTrue(report.contains("[REDACTED]"))
        assertTrue(report.contains("java.io.IOException: offline"))
        assertTrue(report.contains("package=${BuildConfig.APPLICATION_ID}"))
        val intent = DiagnosticExporter.shareIntent(application, file)
        assertEquals("application/zip", intent.type)
        assertTrue(intent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertNotNull(intent.clipData)
        assertFalse(intent.hasExtra(android.content.Intent.EXTRA_TEXT))
        val shared = application.contentResolver.openInputStream(intent.clipData!!.getItemAt(0).uri)!!.use { it.readBytes() }
        assertArrayEquals(file.readBytes(), shared)
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = application.contentResolver
            val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "template-export-test-${System.nanoTime()}.zip")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }))
            try {
                runBlocking { DiagnosticExporter.save(application, file, uri) }
                val saved = resolver.openInputStream(uri)!!.use { it.readBytes() }
                assertArrayEquals(file.readBytes(), saved)
            } finally { resolver.delete(uri, null, null) }
        }
    }
}

private tailrec fun Context.activity(): ComponentActivity = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.activity()
    else -> error("Compose host must be a ComponentActivity")
}
