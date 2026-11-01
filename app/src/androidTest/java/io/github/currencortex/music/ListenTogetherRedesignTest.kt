package io.github.currencortex.music

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.core.netease.NeteaseAccountState
import io.github.currencortex.music.core.netease.listen.NeteaseListenTogetherState
import io.github.currencortex.music.data.binding.BoundProfile
import io.github.currencortex.music.data.netease.listen.*
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.feature.room.*
import io.github.currencortex.music.ui.component.LocalMusicBottomInset
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Local-only screenshots and reachability checks; no account, room or sharing writes. */
class ListenTogetherRedesignTest {
    @get:Rule val compose = createComposeRule()
    @Test fun lightPagesKeepTheHeaderFixedAndActionsReachable() = verify(false)
    @Test fun darkLargeTypePagesKeepTheHeaderFixedAndActionsReachable() = verify(true)

    private fun verify(large: Boolean) {
        val page = mutableIntStateOf(0)
        val self = ListenTogetherParticipant(7, "音乐爱好者")
        val peer = ListenTogetherParticipant(9, "一起听歌的朋友")
        var checks = 0
        var shares = 0
        compose.setContent {
            LeiTheme(AppearanceSettings(themeMode = if (large) ThemeMode.DARK else ThemeMode.LIGHT,
                customTextScale = large, textScale = 1.4f)) {
                CompositionLocalProvider(LocalMusicBottomInset provides 80.dp) {
                    top.yukonga.miuix.kmp.basic.Scaffold {
                        when (page.intValue) {
                            0 -> NeteaseListenTogetherContent(NeteaseListenTogetherState(),
                                NeteaseAccountState(BoundProfile(7, "音乐爱好者"), loggedIn = true), PlayerState(),
                                NeteaseListenTogetherActions({}, {}, {}, { checks++ }, {}, {}, {}, { null }),
                                onBack = {}, onSearch = {}, onPlayer = {}, onHistory = {})
                            1 -> ListenTogetherHistoryContent(ListenTogetherArchiveState(page = ListenArchivePage.HISTORY,
                                self = self, history = ListenTogetherHistoryPage(records = List(8) { index ->
                                    ListenTogetherHistoryRecord("record$index", "room$index", peer, songCount = 12,
                                        durationMs = 3_600_000, canInvite = true)
                                })), onBack = {}, onHistory = {}, onRanking = {}, onReport = {}, onMore = {}, onRetry = {},
                                onInvite = {}, onShare = {}, onCollect = {}, onOfficial = {})
                            else -> ListenTogetherFateContent(ListenTogetherFateReport(9, self, peer,
                                totalDurationMs = 38_400_000, totalSongCount = 124, commonRedSongCount = 5,
                                daysSinceFirstListen = 13, mostPlayedSongName = "把喜欢的歌一起听"), false, false,
                                onBack = {}, onRetry = {}, onHistory = {}, onShare = { shares++ },
                                onDeleteRequest = {}, onOfficial = {})
                        }
                    }
                }
            }
        }
        val suffix = if (large) "dark-large" else "light"
        capture("session-$suffix")
        val sessionBack = compose.onNodeWithTag("netease_listen_back").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("netease_listen_check").performScrollTo().performClick()
        assertEquals(1, checks)
        assertEquals(sessionBack, compose.onNodeWithTag("netease_listen_back").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { page.intValue = 1 }
        capture("history-$suffix")
        val historyBack = compose.onNodeWithTag("listen_archive_back").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("listen_archive_list").performScrollToNode(hasTestTag("listen_archive_invite_record7"))
        compose.onNodeWithTag("listen_archive_invite_record7").performScrollTo().assertIsDisplayed()
        assertEquals(historyBack, compose.onNodeWithTag("listen_archive_back").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { page.intValue = 2 }
        capture("fate-$suffix")
        val fateBack = compose.onNodeWithTag("listen_fate_back").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("listen_fate_list").performScrollToNode(hasTestTag("listen_fate_share"))
        compose.onNodeWithTag("listen_fate_share").performScrollTo().performClick()
        assertEquals(1, shares)
        compose.onNodeWithTag("listen_fate_delete").performScrollTo().assertIsDisplayed()
        val action = compose.onNodeWithTag("listen_fate_delete").fetchSemanticsNode().boundsInRoot
        val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(action.left >= viewport.left && action.right <= viewport.right)
        assertEquals(fateBack, compose.onNodeWithTag("listen_fate_back").fetchSemanticsNode().boundsInRoot)
        capture("fate-actions-$suffix")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.externalCacheDir, "together-$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
