package io.github.currencortex.music

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.update.*
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.update.MarkdownText
import io.github.currencortex.music.feature.update.UpdatePageContent
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class UpdateMarkdownRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun generatedReleaseNotesHideMetadataAndRenderReadableLinks() {
        val notes = """
            <!-- Release notes generated using configuration in .github/release.yml at v1.3.3 -->
            ## What's Changed
            ### New features
            * 支持 CI 版应用内下载与安装 by @bileizhen in https://github.com/bileizhen/CurrentMusicX/pull/14
            * 歌词搜索展示命中句及前后文 by @author in https://github.com/bileizhen/CurrentMusicX/pull/13
            **Full Changelog**: https://github.com/bileizhen/CurrentMusicX/compare/v1.3.2...v1.3.3
        """.trimIndent()
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            top.yukonga.miuix.kmp.basic.Scaffold {
                UpdatePageContent(UpdateState.Available(AppRelease("1.3.3", notes,
                    "https://github.com/bileizhen/CurrentMusicX/releases/tag/v1.3.3", null, null, false)))
            }
        } }
        compose.onNodeWithTag("update_details").performClick()
        compose.onNodeWithText("What's Changed").assertIsDisplayed()
        compose.onNodeWithText("New features").assertIsDisplayed()
        compose.onNodeWithText("#14", substring = true).assertIsDisplayed()
        compose.onNodeWithText("完整改动", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Release notes generated", substring = true).assertDoesNotExist()
        compose.onNodeWithText("https://github.com", substring = true).assertDoesNotExist()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), "update-markdown-rendering.png").outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun compactLinkOpensTheOriginalPullRequest() {
        var clicked = ""
        val url = "https://github.com/bileizhen/CurrentMusicX/pull/14"
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            MarkdownText(url, onLinkClick = { clicked = it })
        } }
        compose.onNodeWithText("#14").performClick()
        compose.runOnIdle { assertEquals(url, clicked) }
    }
}
