package io.github.currencortex.music

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.netease.NeteaseEndpoints
import io.github.currencortex.music.data.binding.BoundProfile
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.room.RoomScreen
import io.github.currencortex.music.feature.room.RoomViewModel
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.basic.Scaffold
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** Entire nested room flow, with native transport fixtures and no real room writes. */
class ListenTogetherNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun systemBackUnwindsReportRankingHistoryAndNativePageOneStepAtATime() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(context.packageName.endsWith(".verification"))
        val paths = CopyOnWriteArrayList<String>()
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath
                    paths += path
                    val data = when {
                        path.endsWith("/listen/together/status/get") -> """{"inRoom":false}"""
                        path.endsWith("/listen/together/full/records") -> """{"vip":true,"records":[{"id":"first","roomId":"fixture-room","userId":9,"nick":"好友","duration":600000,"totalSong":3}]}"""
                        path.endsWith("/listen/together/record/get") -> """{"roomId":"fixture-room","userInfo":{"userId":7,"nickname":"本机用户"},"anotherUserInfo":{"userId":9,"nickname":"好友"},"duration":600,"songCount":3}"""
                        path.endsWith("/listen/together/rank/list") -> """{"rankLists":[{"uid":9,"nickname":"好友","totalDuration":600000,"totalSongCount":3}]}"""
                        else -> return MockResponse().setResponseCode(404)
                    }
                    return MockResponse().setBody("""{"code":200,"data":$data}""")
                }
            }
            start()
        }
        val endpoint = server.url("/").toString()
        val c = AppContainer(context, "listen-nav-${UUID.randomUUID()}", useNativeNetease = true,
            neteaseEndpoints = NeteaseEndpoints(endpoint, endpoint, endpoint))
        val models = androidx.lifecycle.ViewModelStore()
        try {
            c.ready.await(); c.sessionRestored.await()
            c.neteaseSessions.login(mapOf("MUSIC_U" to "local-fixture"), BoundProfile(7, "本机用户"), c.neteaseSessions.snapshot())
            val vm = RoomViewModel(c)
            models.put("room", vm)
            lateinit var back: OnBackPressedDispatcher
            var outerBacks = 0
            compose.setContent { LeiTheme(AppearanceSettings(blur = false)) { Scaffold {
                back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
                BackHandler { outerBacks++ }
                RoomScreen(vm, onBack = { outerBacks++ }, onSearch = {}, onPlayer = {})
            } } }
            fun pressBack() { compose.runOnUiThread { back.onBackPressed() }; compose.waitForIdle() }
            compose.onNodeWithTag("open_netease_listen_together").performClick()
            compose.waitUntil(8000) { compose.onAllNodesWithTag("netease_listen_history").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("netease_listen_history").performClick()
            compose.waitUntil(8000) { compose.onAllNodesWithTag("listen_archive_open_first").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("listen_archive_open_first").performScrollTo().performClick()
            compose.waitUntil(8000) { compose.onAllNodesWithTag("listen_archive_report").fetchSemanticsNodes().isNotEmpty() }
            pressBack()
            compose.onNodeWithText("一起听记录").assertExists()
            assertEquals(1, paths.count { it.endsWith("/full/records") })
            compose.onNodeWithTag("listen_archive_ranking").performScrollTo().performClick()
            compose.waitUntil(8000) { compose.onAllNodesWithTag("listen_archive_rank_9").fetchSemanticsNodes().isNotEmpty() }
            pressBack()
            compose.onNodeWithText("一起听记录").assertExists()
            pressBack()
            compose.onNodeWithTag("netease_listen_screen").assertExists()
            pressBack()
            compose.onNodeWithTag("room_screen").assertExists()
            assertEquals(0, outerBacks)
            assertFalse(paths.any { "/room/create" in it || "/invitation/accept" in it || "/device/reconnect/notice" in it })
        } finally { compose.runOnUiThread { models.clear() }; c.close(); server.shutdown() }
    }
}
