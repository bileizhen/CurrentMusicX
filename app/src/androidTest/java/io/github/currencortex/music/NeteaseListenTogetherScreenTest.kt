package io.github.currencortex.music

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.core.netease.NeteaseAccountState
import io.github.currencortex.music.core.netease.listen.NeteaseListenTogetherState
import io.github.currencortex.music.core.netease.listen.ListenTogetherOperation
import io.github.currencortex.music.data.binding.BoundProfile
import io.github.currencortex.music.data.netease.listen.ListenTogetherParticipant
import io.github.currencortex.music.data.netease.listen.ListenTogetherRoom
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.room.NeteaseListenTogetherActions
import io.github.currencortex.music.feature.room.NeteaseListenTogetherContent
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Pure UI fixture: actions are counters, so neither invitations nor room writes leave this process. */
class NeteaseListenTogetherScreenTest {
    @get:Rule val compose = createComposeRule()
    private val state = mutableStateOf(NeteaseListenTogetherState())
    private val account = mutableStateOf(NeteaseAccountState(BoundProfile(7, "本机用户"), loggedIn = true))
    private val blocked = mutableStateOf(false)
    private var creates = 0; private var joins = mutableListOf<String>(); private var resumes = 0
    private var refreshes = 0; private var leaves = 0; private var logins = 0; private var players = 0
    private var copied: String? = null
    private val invitation = "https://st.music.163.com/listen-together/share/index.html?roomId=fixture_room&inviterId=7"
    private val room = ListenTogetherRoom("fixture_room", 7, listOf(ListenTogetherParticipant(7, "本机用户")))
    private fun show(binding: Boolean = true) {
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            top.yukonga.miuix.kmp.basic.Scaffold {
                NeteaseListenTogetherContent(state.value, account.value, PlayerState(song = Song(11, "正在听的歌", "歌手")),
                    NeteaseListenTogetherActions(create = { creates++ }, join = { joins += it }, resume = { resumes++ },
                        refresh = { refreshes++ }, leave = { leaves++ }, dismissError = { state.value = state.value.copy(error = null) },
                        copyInvitation = { copied = it }, pasteInvitation = { invitation }),
                    onBack = {}, onSearch = {}, onPlayer = { players++ },
                    onBinding = if (binding) ({ logins++ }) else null, currentMusicRoomActive = blocked.value)
            }
        } }
    }
    @Test fun localNeteaseLoginIsRequiredAndMissingNavigationNeverShowsDeadLoginButton() {
        account.value = NeteaseAccountState()
        show(binding = false)
        compose.onNodeWithTag("netease_listen_create").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("netease_listen_login").assertDoesNotExist()
        compose.onNodeWithTag("netease_listen_invite").performScrollTo().performTextInput(invitation)
        compose.onNodeWithTag("netease_listen_join").performScrollTo().assertIsNotEnabled()
        assertEquals(0, creates); assertTrue(joins.isEmpty())
    }
    @Test fun initialLookupUsesSkeletonAndJoiningRetainsDraftAndDisablesRepeatedActions() {
        state.value = NeteaseListenTogetherState(busy = true, operation = ListenTogetherOperation.REFRESH)
        show()
        compose.onNodeWithTag("netease_listen_loading").assertExists()
        compose.onNodeWithTag("netease_listen_create").assertDoesNotExist()
        compose.runOnIdle { state.value = NeteaseListenTogetherState() }
        compose.onNodeWithTag("netease_listen_invite").performScrollTo().performTextInput(invitation)
        compose.runOnIdle { state.value = state.value.copy(busy = true, operation = ListenTogetherOperation.JOIN) }
        compose.onNodeWithTag("netease_listen_join").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("netease_listen_join_loading").assertExists()
        compose.onNodeWithTag("netease_listen_paste").assertIsNotEnabled()
        compose.onNodeWithTag("netease_listen_invite").assertTextContains(invitation)
        compose.runOnIdle { state.value = state.value.copy(busy = false, operation = ListenTogetherOperation.NONE, error = "连接失败") }
        compose.onNodeWithTag("netease_listen_invite").performScrollTo().assertTextContains(invitation)
        assertEquals(0, creates); assertTrue(joins.isEmpty())
    }
    @Test fun loadingIdleAndReconnectingLayoutsHaveRealDeviceScreenshotsWithoutRoomWrites() {
        fun capture(name: String) {
            compose.waitForIdle()
            val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            val directory = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "listen-ui").apply { mkdirs() }
            instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                java.io.File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        state.value = NeteaseListenTogetherState(busy = true, operation = ListenTogetherOperation.REFRESH)
        show()
        compose.onNodeWithTag("netease_listen_loading").assertExists()
        capture("loading")
        compose.runOnIdle { state.value = NeteaseListenTogetherState() }
        compose.onNodeWithTag("netease_listen_create").assertExists()
        capture("idle")
        compose.runOnIdle { state.value = NeteaseListenTogetherState(room = room.copy(participants = room.participants +
            ListenTogetherParticipant(9, "好友")), active = true, connected = false, queueCount = 20, invitationUrl = invitation) }
        compose.onNodeWithTag("netease_listen_connection").assertTextEquals("正在恢复同步…")
        capture("reconnecting")
        compose.runOnIdle { state.value = state.value.copy(connected = true) }
        compose.onNodeWithTag("netease_listen_connection").assertTextEquals("已连接 · 播放同步中")
        capture("connected")
        assertEquals(0, creates); assertTrue(joins.isEmpty()); assertEquals(0, leaves)
    }
    @Test fun expiredAccountOffersRealLoginNavigationAndBlocksRoomWrites() {
        account.value = account.value.copy(stale = true)
        show()
        compose.onNodeWithTag("netease_listen_login").performClick()
        assertEquals(1, logins)
        compose.onNodeWithTag("netease_listen_create").performScrollTo().assertIsNotEnabled()
        assertEquals(0, creates)
    }
    @Test fun rejectsCurrentMusicSixDigitCodeAndJoinsOnlyAfterExplicitPastedInvitation() {
        show()
        compose.onNodeWithTag("netease_listen_invite").performScrollTo().performTextInput("001234")
        compose.onNodeWithTag("netease_listen_join").performScrollTo().performClick()
        compose.onNodeWithTag("netease_listen_invite_error").assertExists()
        assertTrue(joins.isEmpty())
        compose.onNodeWithTag("netease_listen_paste").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue("Pasting must not auto-join", joins.isEmpty())
        compose.onNodeWithTag("netease_listen_join").performScrollTo().performClick()
        assertEquals(listOf(invitation), joins)
        compose.runOnIdle { state.value = state.value.copy(busy = true) }
        compose.onNodeWithTag("netease_listen_join").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(busy = false, error = "暂时无法连接") }
        compose.onNodeWithTag("netease_listen_invite").performScrollTo().assertTextContains(invitation)
        compose.onNodeWithTag("netease_listen_retry").performScrollTo().performClick()
        assertEquals(1, refreshes)
        assertEquals(1, joins.size)
    }
    @Test fun activeRoomShowsPairCopiesOnlyOnTapAndRequiresExplicitExitConfirmation() {
        state.value = NeteaseListenTogetherState(room = room, active = true, connected = true, queueCount = 2, invitationUrl = invitation)
        show()
        compose.onNodeWithContentDescription("一起听：本机用户、等待好友加入").assertExists()
        assertNull("Opening the page must not copy or send invitations", copied)
        compose.onNodeWithTag("netease_listen_copy").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(invitation, copied)
        compose.onNodeWithTag("netease_listen_player").performScrollTo().performClick()
        assertEquals(1, players)
        compose.onNodeWithTag("netease_listen_leave").performScrollTo().performClick()
        assertEquals(0, leaves)
        compose.onNodeWithTag("netease_listen_cancel_leave").performClick()
        assertEquals(0, leaves)
        compose.onNodeWithTag("netease_listen_leave").performScrollTo().performClick()
        compose.onNodeWithTag("netease_listen_confirm_leave").performClick()
        assertEquals(1, leaves)
        assertEquals(0, creates); assertTrue(joins.isEmpty())
    }

    @Test fun officialShortLinkInsideShareTextCanBeSubmittedWithoutAutoJoining() {
        show()
        val shared = "我的耳机分你一半，和我一起听歌吧～快点开看看 https://163cn.tv/fixtureInvite (@网易云音乐)"
        compose.onNodeWithTag("netease_listen_invite").performScrollTo().performTextInput(shared)
        assertTrue(joins.isEmpty())
        compose.onNodeWithTag("netease_listen_join").performScrollTo().performClick()
        assertEquals(listOf(shared), joins)
        compose.onNodeWithTag("netease_listen_invite_error").assertDoesNotExist()
    }
    @Test fun detectsExistingRoomWithoutAutoResumeAndKeepsCurrentMusicRoomsSeparate() {
        state.value = NeteaseListenTogetherState(availableRoom = room)
        blocked.value = true
        show()
        compose.onNodeWithTag("netease_listen_resume").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("netease_listen_create").performScrollTo().assertIsNotEnabled()
        assertEquals(0, resumes)
        compose.runOnIdle { blocked.value = false }
        compose.onNodeWithTag("netease_listen_resume").performScrollTo().performClick()
        assertEquals(1, resumes)
    }
}
