package io.github.currencortex.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.data.announcement.Announcement
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.feature.announcement.AnnouncementDialog
import io.github.currencortex.music.feature.player.AudioQualitySheet
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.basic.*
import java.io.File

/** Uses callbacks with no account/network side effects and a real captured page backdrop. */
class UnifiedDialogStyleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fullWidthChoicePressScrollCancelAndThemeRemainConsistent() {
        val theme = mutableStateOf(ThemeMode.LIGHT)
        var selected by mutableIntStateOf(0)
        var taps = 0
        val shown = mutableStateOf(true)
        compose.setContent { LeiTheme(AppearanceSettings(themeMode = theme.value)) {
            val backdrop = rememberMusicDialogBackdrop()
            CompositionLocalProvider(LocalMusicDialogBackdrop provides backdrop) {
                Scaffold {
                    Box(Modifier.fillMaxSize().musicDialogBackdropSource().background(
                        Brush.horizontalGradient(listOf(Color(0xFFD37258), Color(0xFF397CB1)))))
                    if (shown.value) MusicChoiceDialog("选择播放方式", { shown.value = false }) {
                        Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()).testTag("choices")) {
                            repeat(10) { index -> MusicDialogChoiceRow("选项 $index", selected == index,
                                { selected = index; taps++ }, Modifier.testTag("choice_$index")) }
                        }
                    }
                }
            }
        } }
        val panel = compose.onNodeWithTag("music_dialog_surface").fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithTag("choice_0").fetchSemanticsNode().boundsInRoot
        assertEquals(panel.left, row.left, 1f); assertEquals(panel.right, row.right, 1f)
        capture("common-choice-light.png")
        compose.onNodeWithTag("choice_1").performTouchInput { click(Offset(2f, centerY)) }
        compose.runOnIdle { assertEquals(1, selected); assertEquals(1, taps) }
        // A vertical drag belongs to scrolling, and must not activate a row on release.
        compose.onNodeWithTag("choices").performTouchInput { swipeUp() }
        compose.runOnIdle { assertEquals(1, taps); theme.value = ThemeMode.DARK }
        capture("common-choice-dark.png")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("music_dialog_surface").assertDoesNotExist()
    }

    @Test fun formConfirmationAnnouncementAndQualityUseSharedSurfaceInBothThemes() {
        val theme = mutableStateOf(ThemeMode.LIGHT)
        val kind = mutableIntStateOf(0)
        compose.setContent { LeiTheme(AppearanceSettings(themeMode = theme.value)) {
            val backdrop = rememberMusicDialogBackdrop()
            CompositionLocalProvider(LocalMusicDialogBackdrop provides backdrop) { Scaffold {
            Box(Modifier.fillMaxSize().musicDialogBackdropSource().background(
                Brush.horizontalGradient(listOf(Color(0xFFD37258), Color(0xFF397CB1)))))
            when (kind.intValue) {
                0 -> MusicDialog("修改歌单名称", {}, footer = {
                    MusicDialogActions { MusicDialogButton("取消", {}, Modifier.weight(1f)); MusicDialogButton("保存", {}, Modifier.weight(1f)) }
                }) { TextField("我的收藏", {}, Modifier.fillMaxWidth(), label = "名称") }
                1 -> MusicDialog("清除播放队列？", {}, footer = {
                    MusicDialogActions { MusicDialogButton("取消", {}, Modifier.weight(1f)); MusicDialogButton("清除", {}, Modifier.weight(1f), destructive = true) }
                }) { Text("清除后将停止当前播放。") }
                2 -> AnnouncementDialog(listOf(Announcement(id = 1, title = "更新公告", body = "播放体验和界面交互已更新。")), onDismiss = {})
                else -> AudioQualitySheet(AudioQuality.LOSSLESS, {}, {})
            }
        } } } }
        for (dark in listOf(false, true)) {
            compose.runOnIdle { theme.value = if (dark) ThemeMode.DARK else ThemeMode.LIGHT }
            for (index in 0..3) {
                compose.runOnIdle { kind.intValue = index }
                compose.onNodeWithTag(if (index == 3) "bottom_sheet_material" else "music_dialog_surface").assertIsDisplayed()
                capture("common-${listOf("form", "confirmation", "announcement", "quality")[index]}-${if (dark) "dark" else "light"}.png")
            }
        }
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        File(context.getExternalFilesDir(null), name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
