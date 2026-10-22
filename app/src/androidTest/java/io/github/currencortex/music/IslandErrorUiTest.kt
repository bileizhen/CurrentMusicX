package io.github.currencortex.music

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.feature.lyrics.desktop.DesktopIslandNoticeTransition
import io.github.currencortex.music.feature.lyrics.desktop.DesktopLyricsSurface
import io.github.currencortex.music.feature.lyrics.desktop.DesktopLyricsSettings
import io.github.currencortex.music.feature.lyrics.data.PlaybackLyricsState
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.util.showMusicMessage
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class IslandErrorUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun contextFeedbackUsesTheInAppIslandWithoutDesktopOverlayPermission() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(app.packageName.endsWith(".verification"))
        val container = app.container
        kotlinx.coroutines.runBlocking { container.ready.await(); container.sessionRestored.await() }
        org.junit.Assume.assumeFalse(container.desktopLyricsStatus.value.attached)
        container.errorNotices.clear()
        try {
            compose.setContent {
                val context = androidx.compose.ui.platform.LocalContext.current
                Box(Modifier.width(360.dp)) {
                    io.github.currencortex.music.ui.component.MusicTextAction("提示测试", {
                        context.showMusicMessage("版本信息已复制")
                    })
                    io.github.currencortex.music.feature.lyrics.desktop.SoundEffectIslandFallback(container)
                }
            }
            compose.onNodeWithText("提示测试").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("island_message_notice").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("版本信息已复制").assertExists()
            compose.onNodeWithTag("island_error_notice").assertDoesNotExist()
            assertEquals(IslandNoticeKind.MESSAGE, container.islandNotices.value!!.kind)
            assertEquals(false, container.desktopLyricsStatus.value.attached)
        } finally { container.errorNotices.clear() }
    }

    @Test fun firstErrorAndErrorAfterDownloadRenderTheirOwnMessageWithoutSoundEffectLabels() {
        val notice = mutableStateOf<SoundEffectNotice?>(SoundEffectNotice(1, "提示", SoundEffectNoticeTier.STANDARD,
            SoundEffectNoticePhase.FAILED, "网络连接失败", kind = IslandNoticeKind.ERROR))
        var installs = 0
        compose.setContent { Box(Modifier.width(340.dp)) {
            DesktopIslandNoticeTransition(notice.value, 36.dp, 12f) { installs++ }
        } }
        compose.onNodeWithTag("island_error_notice").assertExists()
        compose.onNodeWithText("网络连接失败").assertExists()
        compose.onNodeWithText("未启用").assertDoesNotExist()
        compose.runOnIdle { notice.value = SoundEffectNotice(2, "下载中", SoundEffectNoticeTier.STANDARD,
            kind = IslandNoticeKind.UPDATE_DOWNLOAD, progress = 70) }
        compose.waitForIdle()
        compose.runOnIdle { notice.value = SoundEffectNotice(3, "提示", SoundEffectNoticeTier.STANDARD,
            SoundEffectNoticePhase.FAILED, "没有访问权限", kind = IslandNoticeKind.ERROR) }
        compose.onNodeWithText("没有访问权限").assertExists()
        compose.runOnIdle { notice.value = SoundEffectNotice(4, "提示", SoundEffectNoticeTier.STANDARD,
            SoundEffectNoticePhase.READY, "版本信息已复制", kind = IslandNoticeKind.MESSAGE) }
        compose.onNodeWithTag("island_message_notice").assertExists()
        compose.onNodeWithTag("island_error_notice").assertDoesNotExist()
        compose.onNodeWithText("版本信息已复制").assertExists()
        compose.onNodeWithText("未启用").assertDoesNotExist()
        assertEquals(0, installs)
    }

    @Test fun inAppNoticeOnlySurfaceNeverComposesLyricsFpsOrTrackArtworkWhileRetracting() {
        compose.mainClock.autoAdvance = false
        val lyrics = MutableStateFlow(PlaybackLyricsState())
        val playback = MutableStateFlow(PlayerState(song = Song(1, "普通歌名")))
        val preferences = MutableStateFlow(DesktopLyricsSettings())
        val notice = MutableStateFlow<SoundEffectNotice?>(SoundEffectNotice(1, "提示", SoundEffectNoticeTier.STANDARD,
            SoundEffectNoticePhase.FAILED, "网络连接失败", kind = IslandNoticeKind.ERROR))
        compose.setContent { Box(Modifier.width(340.dp)) {
            DesktopLyricsSurface(lyrics, playback, preferences, {}, {}, {}, defaultFpsMode = true,
                noticeOnly = true, effectNotices = notice)
        } }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("网络连接失败").assertExists()
        compose.onNodeWithTag("desktop_island").assertDoesNotExist()
        compose.onNodeWithText("普通歌名").assertDoesNotExist()
        compose.runOnIdle { notice.value = null }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("desktop_island").assertDoesNotExist()
        compose.onNodeWithText("普通歌名").assertDoesNotExist()
    }
}
