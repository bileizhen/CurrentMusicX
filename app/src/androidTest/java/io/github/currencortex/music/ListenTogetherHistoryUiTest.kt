package io.github.currencortex.music

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.currencortex.music.data.netease.listen.*
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.room.ListenArchivePage
import io.github.currencortex.music.feature.room.ListenTogetherArchiveState
import io.github.currencortex.music.feature.room.ListenTogetherHistoryContent
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Pure callbacks: these fixtures cannot create rooms, send invitations or write to NetEase. */
class ListenTogetherHistoryUiTest {
    @get:Rule val compose = createComposeRule()
    private val self = ListenTogetherParticipant(7, "我")
    private val peer = ListenTogetherParticipant(9, "好友")
    private val state = mutableStateOf(ListenTogetherArchiveState(page = ListenArchivePage.HISTORY))
    private val active = mutableStateOf(false)
    private var retries = 0
    private var invites = 0
    private var shares = 0
    private var sharedReport: ListenTogetherRecordReport? = null
    private var collects = 0
    private val opened = mutableListOf<String>()
    private val filters = mutableListOf<Long?>()
    private fun show() {
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            top.yukonga.miuix.kmp.basic.Scaffold {
                ListenTogetherHistoryContent(state.value, onBack = {}, onHistory = { filters += it }, onRanking = {},
                    onReport = { opened += it }, onMore = {}, onRetry = { retries++ }, onInvite = { invites++ },
                    onShare = { shares++; sharedReport = it }, onCollect = { collects++ }, onOfficial = {}, activeRoom = active.value)
            }
        } }
    }

    @Test fun emptyHistoryAndPermissionDeniedRankingAreDistinctFromZeroStatistics() {
        state.value = state.value.copy(history = ListenTogetherHistoryPage())
        show()
        compose.onNodeWithTag("listen_archive_empty").assertExists()
        compose.runOnIdle { state.value = ListenTogetherArchiveState(page = ListenArchivePage.RANKING,
            error = "会员权限不足", permissionRequired = true) }
        compose.onNodeWithText("需要网易云会员权限").assertExists()
        compose.onNodeWithTag("listen_archive_empty").assertDoesNotExist()
        compose.onNodeWithText("0 分钟").assertDoesNotExist()
        compose.onNodeWithTag("listen_archive_retry").performScrollTo().performClick()
        assertEquals(1, retries)
    }

    @Test fun reportUsesServerValuesAndLeavesUnavailableTotalsMissing() {
        state.value = ListenTogetherArchiveState(page = ListenArchivePage.REPORT, self = self,
            report = ListenTogetherRecordReport("room", self, peer, durationMs = 5_940_000, songCount = 32))
        show()
        compose.onNodeWithText("32 首歌曲").performScrollTo().assertExists()
        compose.onNodeWithText("1 小时 39 分钟").assertExists()
        compose.onAllNodesWithText("—").assertCountEquals(2)
        compose.onNodeWithTag("listen_archive_collect").assertDoesNotExist()
        assertEquals(0, shares)
        assertEquals(0, collects)
    }

    @Test fun anonymousReportNeverRestoresARealAvatarFromAnEarlierRoom() {
        val realPeer = peer.copy(nickname = "不能显示的真实昵称", avatarUrl = "https://fixture.invalid/private-avatar.png")
        state.value = ListenTogetherArchiveState(page = ListenArchivePage.REPORT, self = self,
            sourceRoom = ListenTogetherRoom("room", self.userId, listOf(self, realPeer)),
            report = ListenTogetherRecordReport("room", self, peer.copy(nickname = "匿名听友", masked = true)))
        show()
        compose.onNodeWithText("不能显示的真实昵称").assertDoesNotExist()
        compose.onNodeWithTag("listen_archive_share").performScrollTo().performClick()
        assertTrue(sharedReport!!.peer!!.masked)
        assertEquals("匿名听友", sharedReport!!.peer!!.nickname)
        assertEquals("", sharedReport!!.peer!!.avatarUrl)
    }

    @Test fun historyReportShareAndInviteOnlyRunAfterExplicitTap() {
        val row = ListenTogetherHistoryRecord("first", "room", peer, durationMs = 600_000, songCount = 5, canInvite = true)
        state.value = ListenTogetherArchiveState(page = ListenArchivePage.HISTORY, history = ListenTogetherHistoryPage(records = listOf(row)))
        show()
        assertTrue(opened.isEmpty()); assertEquals(0, invites); assertEquals(0, shares)
        compose.onNodeWithTag("listen_archive_open_first").performScrollTo().performClick()
        assertEquals(listOf("room"), opened)
        compose.onNodeWithTag("listen_archive_invite_first").performScrollTo().performClick()
        assertEquals(1, invites)
        compose.runOnIdle { state.value = ListenTogetherArchiveState(page = ListenArchivePage.REPORT,
            report = ListenTogetherRecordReport("room", self, peer, canInvite = true, showCollectButton = true)) }
        assertEquals(0, shares); assertEquals(0, collects)
        compose.onNodeWithTag("listen_archive_share").performScrollTo().performClick()
        assertEquals(1, shares)
        compose.onNodeWithTag("listen_archive_collect").performScrollTo().performClick()
        assertEquals(1, collects)
    }

    @Test fun activeRoomBlocksInvitationsAndRankingOpensActualPeerHistory() {
        active.value = true
        state.value = ListenTogetherArchiveState(page = ListenArchivePage.REPORT,
            report = ListenTogetherRecordReport("room", self, peer, canInvite = true))
        show()
        compose.onNodeWithTag("listen_archive_report_invite").performScrollTo().assertIsNotEnabled()
        assertEquals(0, invites)
        compose.runOnIdle { state.value = ListenTogetherArchiveState(page = ListenArchivePage.HISTORY,
            history = ListenTogetherHistoryPage(records = listOf(ListenTogetherHistoryRecord("row", "room", peer, canInvite = true)))) }
        compose.onNodeWithTag("listen_archive_invite_row").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { state.value = ListenTogetherArchiveState(page = ListenArchivePage.RANKING,
            ranking = ListenTogetherRanking(listOf(ListenTogetherRankEntry(peer, 2, 38_400_000, 14)))) }
        compose.onNodeWithTag("listen_archive_rank_history_9").performScrollTo().performClick()
        assertEquals(listOf(9L), filters)
        assertEquals(0, invites)
    }
}
