package io.github.currencortex.music

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.netease.*
import io.github.currencortex.music.data.binding.BoundProfile
import io.github.currencortex.music.data.netease.tasks.*
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.ui.CurrentMusicApp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** Local task fixtures and isolated preferences; never changes the installed user's NetEase account. */
class NeteaseTasksCapabilitiesTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var server: MockWebServer
    private val paths = CopyOnWriteArrayList<String>()
    @Volatile private var cloudFails = false
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    private fun json(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "application/json")
    @Before fun prepare() = runBlocking<Unit> {
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath
                    paths += path
                    return when (path) {
                        "/weapi/w/nuser/account/get" -> json("""{"code":200,"profile":{"userId":42,"nickname":"Task fixture"}}""")
                        "/weapi/point/signed/get" -> json("""{"code":200,"data":{"days":3}}""")
                        "/weapi/usertool/task/list/all" -> if (cloudFails) MockResponse().setResponseCode(503) else json("""{"code":200,"data":[{"taskId":1,"userTaskId":0,"taskName":"听音乐30分钟","taskDescription":"每日听日推歌曲满30分钟","currentCount":12,"targetCount":30},{"taskId":2,"userTaskId":0,"taskName":"关注歌手"}]}""")
                        "/weapi/v1/user/info" -> json("""{"code":200,"userPoint":{"balance":470}}""")
                        "/weapi/vipnewcenter/app/level/task/list" -> json("""{"code":200,"data":{"taskList":[{"taskItems":[{"taskId":"99_7","name":"","action":"浏览黑胶时光机","description":"成长值+3","unGetIds":["99_7"]}]}]}}""")
                        "/weapi/vipnewcenter/app/level/growhpoint/basic" -> json("""{"code":200,"data":{"userLevel":{"levelName":"黑胶·陆","growthPoint":19018}}}""")
                        "/weapi/nmusician/workbench/mission/cycle/list" -> json("""{"code":403,"message":"你还不是音乐人，请先入驻成为音乐人"}""")
                        "/weapi/point/dailyTask", "/weapi/pointmall/user/sign" -> json("""{"code":200,"data":{"sign":true}}""")
                        "/cm/announcements" -> json("""{"items":[]}""")
                        else -> json("""{"code":200,"data":[],"items":[],"songs":[]}""")
                    }
                }
            }
            start()
        }
        val endpoint = server.url("/").toString()
        container = AppContainer(context, "netease-tasks-${UUID.randomUUID()}", useNativeNetease = true,
            neteaseEndpoints = NeteaseEndpoints(endpoint, endpoint, endpoint))
        container.ready.await(); container.sessionRestored.await()
        val base = server.url("/cm/").toString()
        container.musicSettings.setServer(base); container.accountRepository.server = base
        container.updateSettings.setAutoCheck(false)
        container.settings.edit { AppearanceSettings(blur = false) }
        val paused = io.github.currencortex.music.data.song.Song(900, "Paused fixture")
        container.playerController.queue.replace(listOf(paused), 0)
        container.playerController.state.value = io.github.currencortex.music.core.media.PlayerState(song = paused)
        container.neteaseSessions.ready.await()
        container.neteaseSessions.login(mapOf("MUSIC_U" to "local-fixture"), BoundProfile(42, "Task fixture"), container.neteaseSessions.snapshot())
    }
    @After fun finish() {
        runCatching {
            save("netease-tasks-last.png")
            File(context.externalCacheDir, "netease-tasks-semantics.txt").writeText(compose.onRoot().printToString())
        }
        if (::container.isInitialized) container.close()
        if (::server.isInitialized) server.shutdown()
    }
    private fun awaitTag(tag: String) = compose.waitUntil(20_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun show() {
        compose.setContent { CurrentMusicApp(container) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }
    private fun openBinding() {
        compose.onNodeWithTag("tab_2").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithTag("tab_2").assertIsSelected(); awaitTag("profile_screen")
        compose.onNodeWithTag("profile_screen").performScrollToNode(hasTestTag("open_binding"))
        compose.onNodeWithTag("open_binding").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; awaitTag("binding_screen")
        compose.onNodeWithTag("binding_screen").performScrollToNode(hasTestTag("netease_tasks_enabled"))
    }
    private fun save(name: String) {
        compose.onNodeWithTag("music_window").captureToImage().asAndroidBitmap().let { bitmap ->
            File(context.externalCacheDir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
    }
    private fun openTasks() {
        click("tab_2"); awaitTag("profile_screen")
        compose.onNodeWithTag("profile_screen").performScrollToNode(hasTestTag("open_netease_tasks"))
        click("open_netease_tasks"); awaitTag("netease_tasks")
    }
    @Test fun accountSwitchShowsTaskEntryBelowNeteaseAccountAndLeavesNavigationAtFourTabs() = runBlocking<Unit> {
        show(); awaitTag("tab_2")
        compose.onNodeWithTag("tab_4").assertDoesNotExist()
        openBinding()
        compose.onNodeWithTag("netease_tasks_enabled").assertIsOff()
        click("netease_tasks_enabled")
        withTimeout(5_000) { container.neteaseTasksEnabled.first { it == true } }
        click("binding_back"); awaitTag("profile_screen")
        compose.onNodeWithTag("profile_screen").performScrollToNode(hasTestTag("open_netease_tasks"))
        assertTrue(compose.onNodeWithTag("open_binding").fetchSemanticsNode().boundsInRoot.top <
            compose.onNodeWithTag("open_netease_tasks").fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithTag("tab_4").assertDoesNotExist()
        click("open_netease_tasks"); awaitTag("netease_tasks")
        click("tasks_tab_YUNBEI")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("可用云贝 470 · 2 项任务").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tasks_list").performScrollToNode(hasText("关注歌手"))
        compose.onNodeWithText("关注歌手").assertIsDisplayed()
        compose.onNodeWithTag("tasks_list").performScrollToIndex(0)
        save("netease-tasks-cloud.png")
        click("tasks_back"); awaitTag("profile_screen")
        openBinding()
        compose.onNodeWithTag("netease_tasks_enabled").assertIsOn()
        click("netease_tasks_enabled")
        withTimeout(5_000) { container.neteaseTasksEnabled.first { it == false } }
        click("binding_back"); awaitTag("profile_screen")
        compose.onNodeWithTag("open_netease_tasks").assertDoesNotExist()
        compose.onNodeWithTag("tab_4").assertDoesNotExist()
        click("tab_3"); compose.onNodeWithTag("tab_3").assertIsSelected()
        assertFalse(container.neteaseTaskPreferences.enabled.first())
    }
    @Test fun taskPageRetriesOfficialErrorsShowsVipNamesAndCheckInDoesNotChangeTheQueue() = runBlocking<Unit> {
        cloudFails = true
        container.neteaseTaskPreferences.enable(true)
        show(); awaitTag("tab_2"); openTasks()
        val queue = container.playerController.queue.state.value
        compose.onNodeWithTag("tasks_sign").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        withTimeout(5_000) { container.neteaseTaskPreferences.options(42).first { it.signedDay.isNotEmpty() } }
        compose.onNodeWithTag("tasks_sign").assertIsNotEnabled()
        compose.onNodeWithTag("tasks_list").performScrollToNode(hasTestTag("tasks_journal_toggle"))
        compose.onNodeWithText("日常 · 签到成功").assertDoesNotExist()
        click("tasks_journal_toggle")
        compose.onNodeWithText("日常 · 签到成功").assertExists()
        click("tasks_journal_toggle")
        compose.onNodeWithTag("tasks_list").performScrollToIndex(0)
        compose.onNodeWithTag("tasks_tab_YUNBEI").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; awaitTag("tasks_retry")
        cloudFails = false
        compose.onNodeWithTag("tasks_retry").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("可用云贝 470 · 2 项任务").fetchSemanticsNodes().isNotEmpty() }
        cloudFails = true
        click("tasks_refresh"); awaitTag("tasks_retry")
        compose.onNodeWithText("可用云贝 470 · 2 项任务").assertExists()
        cloudFails = false
        click("tasks_retry")
        compose.onNodeWithTag("tasks_tab_VIP").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("黑胶·陆 · 成长值 19018").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tasks_list").performScrollToNode(hasText("浏览黑胶时光机"))
        compose.onNodeWithText("浏览黑胶时光机").assertIsDisplayed()
        compose.onNodeWithTag("tasks_list").performScrollToIndex(0)
        compose.onNodeWithTag("tasks_tab_MUSICIAN").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("你还不是音乐人，请先入驻成为音乐人").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tasks_sign").assertIsNotEnabled()
        assertEquals(1, paths.count { it == "/weapi/point/dailyTask" })
        assertEquals(queue, container.playerController.queue.state.value)
    }
}
