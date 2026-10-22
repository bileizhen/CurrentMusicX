package io.github.currencortex.music

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.logging.LogSink
import io.github.currencortex.music.core.update.*
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.update.UpdateSettingsRepository
import io.github.currencortex.music.feature.update.UpdatePage
import io.github.currencortex.music.ui.theme.LeiTheme
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

/** Automatic detection uses the real service and real isolated preference writes. No production downloads. */
class UpdateAvailablePromptTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var settings: UpdateSettingsRepository
    private lateinit var service: UpdateService
    private lateinit var transfer: UpdateTransfer
    private var downloads = 0
    private var installs = 0
    private val release = AppRelease("99.0.0", "### 更新内容\n- 优化播放体验",
        "https://github.com/bileizhen/CurrentMusicX/releases/tag/v99.0.0",
        "https://github.com/bileizhen/CurrentMusicX/releases/download/v99.0.0/fixture.apk", "fixture.apk", false, 100, "0".repeat(64))

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        val preferenceFile = File(context.cacheDir, "update-prompt-${UUID.randomUUID()}.preferences_pb")
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { preferenceFile })
        settings = UpdateSettingsRepository(store, scope)
        val logger = object : LogSink {
            override fun info(tag: String, message: String) {}
            override fun warn(tag: String, message: String, error: Throwable?) {}
        }
        service = UpdateService(ReleaseChecker { release }, settings, logger)
        transfer = UpdateTransfer(UpdateDownload { _, _, _ -> downloads++; error("No download expected") },
            object : UpdateInstall {
                override fun canInstall() = true
                override suspend fun request(file: File, release: AppRelease): UpdateInstallResult {
                    installs++; error("No installation expected")
                }
            }, scope)
    }

    @After fun close() { scope.cancel() }

    @Test fun automaticDiscoveryShowsTwoButtonsAndIgnoreKeepsThePageClosed() {
        runBlocking { service.checkOnLaunch() }
        showHost()
        compose.onNodeWithTag("update_available_prompt").assertIsDisplayed()
        compose.onNodeWithTag("update_page").assertDoesNotExist()
        val ignore = compose.onNodeWithTag("update_prompt_ignore").assertTextContains("忽略更新")
        val view = compose.onNodeWithTag("update_prompt_view").assertTextContains("查看")
        assertTrue(ignore.getUnclippedBoundsInRoot().right < view.getUnclippedBoundsInRoot().left)
        capture("update-available-prompt.png")
        ignore.performClick()
        compose.waitUntil(5000) { service.prompt.value == null }
        compose.onNodeWithTag("update_available_prompt").assertDoesNotExist()
        compose.onNodeWithTag("update_page").assertDoesNotExist()
        runBlocking {
            assertEquals(release.version, settings.snapshot().ignoredVersions[UpdateChannel.STABLE])
            service.check()
        }
        compose.onNodeWithTag("update_available_prompt").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, downloads); assertEquals(0, installs) }
    }

    @Test fun viewOpensTheUpdatePageWithoutStartingADownload() {
        runBlocking { service.checkOnLaunch() }
        showHost()
        compose.onNodeWithTag("update_prompt_view").performClick()
        compose.onNodeWithTag("update_page").assertIsDisplayed()
        compose.onNodeWithTag("update_available_prompt").assertDoesNotExist()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("update_download").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("update_download").assertIsDisplayed()
        compose.runOnIdle { assertTrue(service.dialogVisible.value); assertEquals(0, downloads); assertEquals(0, installs) }
    }

    @Test fun manualCheckOpensThePageDirectly() {
        runBlocking { service.present() }
        showHost()
        compose.onNodeWithTag("update_page").assertIsDisplayed()
        compose.onNodeWithTag("update_available_prompt").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, downloads); assertEquals(0, installs) }
    }

    private fun showHost() {
        compose.setContent { TestTheme { UpdatePage(service, transfer) } }
    }

    @Composable private fun TestTheme(content: @Composable () -> Unit) {
        LeiTheme(AppearanceSettings(blur = false)) { top.yukonga.miuix.kmp.basic.Scaffold { content() } }
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
