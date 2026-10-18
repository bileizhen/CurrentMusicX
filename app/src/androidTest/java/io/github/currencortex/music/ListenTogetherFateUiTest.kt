package io.github.currencortex.music

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.currencortex.music.data.netease.listen.*
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.room.*
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Callback fixtures never send invitations, share externally or delete native-account records. */
class ListenTogetherFateUiTest {
    @get:Rule val compose = createComposeRule()
    private val self = ListenTogetherParticipant(7, "本机用户")
    private val peer = ListenTogetherParticipant(9, "匿名听友")
    private val report = mutableStateOf<ListenTogetherFateReport?>(null)
    private val loading = mutableStateOf(false)
    private val permission = mutableStateOf(false)
    private val busy = mutableStateOf(false)
    private var retries = 0
    private var shares = 0
    private var deleteRequests = 0
    private val histories = mutableListOf<Long>()
    private fun showFate() {
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            top.yukonga.miuix.kmp.basic.Scaffold {
                ListenTogetherFateContent(report.value, loading.value, permission.value, onBack = {}, onRetry = { retries++ },
                    onHistory = { histories += it }, onShare = { shares++ }, onDeleteRequest = { deleteRequests++ },
                    onOfficial = {}, busy = busy.value)
            }
        } }
    }

    @Test fun loadingPermissionAndEmptyStatesDoNotInventZeroStatistics() {
        loading.value = true
        showFate()
        compose.onNodeWithTag("listen_fate_loading").assertExists()
        compose.onNodeWithTag("listen_fate_empty").assertDoesNotExist()
        compose.runOnIdle { loading.value = false; permission.value = true }
        compose.onNodeWithTag("listen_fate_permission").assertExists()
        compose.onNodeWithTag("listen_fate_retry").performScrollTo().performClick()
        assertEquals(1, retries)
        compose.onNodeWithText("0 分钟").assertDoesNotExist()
        compose.runOnIdle { permission.value = false }
        compose.onNodeWithTag("listen_fate_empty").assertExists()
    }

    @Test fun serverMillisecondsAndDaysAreDisplayedWithoutRecalculationOrZeroFill() {
        report.value = ListenTogetherFateReport(9, self, peer, totalDurationMs = 38_400_000, totalSongCount = 124,
            commonRedSongCount = 5, firstListenTimeMs = 1_728_662_400_000, daysSinceFirstListen = 13,
            latestLateNightTimeMs = 1_728_662_400_000, mostPlayedSongName = "一起听过的歌", longestDurationMs = null)
        showFate()
        compose.onNodeWithTag("listen_fate_total_duration").assertTextEquals("10 小时 40 分钟")
        compose.onNodeWithText("本机用户 · 匿名听友").assertExists()
        compose.onNodeWithText("124 首歌曲").assertExists()
        compose.onNodeWithText("5 首歌曲").assertExists()
        compose.onNodeWithText("13 天").performScrollTo().assertExists()
        compose.onNodeWithText("一起听过的歌").performScrollTo().assertExists()
        compose.onNodeWithText("—").performScrollTo().assertExists()
        compose.onNodeWithText("0 分钟").assertDoesNotExist()
    }

    @Test fun shareAndSensitiveDeleteOnlyRequestCallbacksAndBusyStateBlocksRepeats() {
        report.value = ListenTogetherFateReport(9, self, peer, openActivity = true)
        showFate()
        assertEquals(0, shares); assertEquals(0, deleteRequests); assertTrue(histories.isEmpty())
        compose.onNodeWithTag("listen_fate_share").performScrollTo().performClick()
        assertEquals(1, shares)
        compose.onNodeWithTag("listen_fate_delete").performScrollTo().performClick()
        assertEquals(1, deleteRequests)
        compose.onNodeWithText("删除报告会回收关联的一起听徽章与进度。").performScrollTo().assertExists()
        compose.onNodeWithTag("listen_fate_history").performScrollTo().performClick()
        assertEquals(listOf(9L), histories)
        compose.runOnIdle { busy.value = true }
        compose.onNodeWithTag("listen_fate_delete").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("listen_fate_share").performScrollTo().assertIsNotEnabled()
        assertEquals(1, deleteRequests)
    }

    @Test fun mostListenAndRankingFateEntriesPassTheActualPeerAndNativeScene() {
        val state = mutableStateOf(ListenTogetherArchiveState(page = ListenArchivePage.HISTORY, self = self,
            history = ListenTogetherHistoryPage(mostListen = ListenTogetherMostListen(peer, ltType = 5))))
        val requests = mutableListOf<Pair<Long, Int>>()
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            top.yukonga.miuix.kmp.basic.Scaffold {
                ListenTogetherHistoryContent(state.value, onBack = {}, onHistory = {}, onRanking = {}, onReport = {},
                    onMore = {}, onRetry = {}, onInvite = {}, onShare = {}, onCollect = {}, onOfficial = {},
                    onFate = { user, scene -> requests += user.userId to scene })
            }
        } }
        assertTrue(requests.isEmpty())
        compose.onNodeWithTag("listen_archive_most_fate").performScrollTo().performClick()
        assertEquals(listOf(9L to 5), requests)
        compose.runOnIdle { state.value = ListenTogetherArchiveState(page = ListenArchivePage.RANKING,
            ranking = ListenTogetherRanking(listOf(ListenTogetherRankEntry(peer, rank = 1, ltType = 6)))) }
        compose.onNodeWithTag("listen_archive_rank_fate_9").performScrollTo().performClick()
        assertEquals(listOf(9L to 5, 9L to 6), requests)
    }

    @Test fun completedRoomReportCanOpenTheSamePartnersFateWithoutReplacingItsStatistics() {
        val requests = mutableListOf<Pair<Long, Int>>()
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            top.yukonga.miuix.kmp.basic.Scaffold {
                ListenTogetherHistoryContent(ListenTogetherArchiveState(page = ListenArchivePage.REPORT,
                    report = ListenTogetherRecordReport("completed-room", self, peer, songCount = 7, ltType = 4)),
                    onBack = {}, onHistory = {}, onRanking = {}, onReport = {}, onMore = {}, onRetry = {},
                    onInvite = {}, onShare = {}, onCollect = {}, onOfficial = {},
                    onFate = { user, scene -> requests += user.userId to scene })
            }
        } }
        compose.onNodeWithTag("listen_archive_report_fate").performScrollTo().performClick()
        assertEquals(listOf(9L to 4), requests)
        compose.onNodeWithText("7 首歌曲").performScrollTo().assertExists()
    }

    @Test fun maskedReportCannotInviteEvenWhenTheServerCanInviteFlagIsTrue() {
        var invitations = 0
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) { top.yukonga.miuix.kmp.basic.Scaffold {
            ListenTogetherFateContent(ListenTogetherFateReport(9, self, peer.copy(masked = true), canInvite = true),
                false, false, onBack = {}, onRetry = {}, onHistory = {}, onShare = {}, onDeleteRequest = {},
                onOfficial = {}, onInvite = { invitations++ })
        } } }
        compose.onNodeWithTag("listen_fate_invite").performScrollTo().assertIsNotEnabled()
        assertEquals(0, invitations)
    }
}
