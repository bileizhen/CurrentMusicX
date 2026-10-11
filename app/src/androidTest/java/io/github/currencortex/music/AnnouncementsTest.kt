package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.announcement.Announcement
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.announcement.AnnouncementDialog
import io.github.currencortex.music.feature.announcement.AnnouncementViewModel
import io.github.currencortex.music.ui.CurrentMusicApp
import io.github.currencortex.music.ui.theme.LeiTheme
import io.github.currencortex.music.ui.util.viewModelFactory
import top.yukonga.miuix.kmp.basic.Scaffold
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AnnouncementsTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var server: MockWebServer
    private val release = CountDownLatch(1)
    private val requests = AtomicInteger()
    @Volatile private var extraNotice = false
    private val fixture = """{"items":[{"id":1,"title":"公告标题","body":"## 正文小标题\n- 功能已上线","pinned":true,"author":"站长Rcst20","createdAt":1791646979},
        {"id":2,"title":"详情公告","body":"正文部分","link":"https://bilibili.com"}]}"""

    @Before fun prepare() = runBlocking {
        container = AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(), "announcements-${UUID.randomUUID()}")
        container.ready.await(); container.sessionRestored.await()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                "/cm/announcements" -> {
                    requests.incrementAndGet()
                    assertNull(request.getHeader("Authorization"))
                    release.await(15, TimeUnit.SECONDS)
                    MockResponse().setBody(if (extraNotice) fixture.dropLast(2) + ",{" +
                        "\"id\":3,\"title\":\"新增公告\",\"body\":\"新的正文\"}]}" else fixture)
                }
                "/cm/ncmbind" -> MockResponse().setBody("""{"bound":false}""")
                "/cm/daily" -> MockResponse().setBody("""{"daily":[],"forYou":[],"artists":[]}""")
                "/cm/playlists" -> MockResponse().setBody("""{"playlists":[]}""")
                "/cm/plays/recent", "/cm/likes/mine" -> MockResponse().setBody("""{"songs":[]}""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        val base = server.url("/cm/").toString()
        container.musicSettings.setServer(base); container.accountRepository.server = base
        container.accountRepository.save("isolated-test-token", UserDto(7, nickname = "Fixture user"))
        container.updateSettings.setAutoCheck(false)
        container.settings.edit { AppearanceSettings(blur = false) }
    }

    @After fun finish() = runBlocking {
        release.countDown()
        if (::container.isInitialized) { container.accountRepository.clear(); container.close() }
        if (::server.isInitialized) server.shutdown()
    }

    private fun pumpUntil(condition: () -> Boolean) = compose.waitUntil(15_000) {
        compose.mainClock.advanceTimeBy(32)
        condition()
    }

    @Test fun fetchedAnnouncementWaitsForLaunchThenDismissesForThisSession() {
        compose.mainClock.autoAdvance = false
        compose.setContent { CurrentMusicApp(container, animateLaunch = true) }
        pumpUntil { requests.get() == 1 }
        compose.onNodeWithTag("launch_animation").assertExists()
        release.countDown()
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithTag("announcements").assertDoesNotExist()
        pumpUntil { compose.onAllNodesWithTag("launch_animation").fetchSemanticsNodes().isEmpty() }
        pumpUntil { compose.onAllNodesWithTag("announcements").fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("公告（2）").assertIsDisplayed()
        compose.onNodeWithText("置顶").assertIsDisplayed()
        compose.onNodeWithText("正文小标题").assertIsDisplayed()
        compose.onNodeWithTag("announcement_details").assertDoesNotExist()
        compose.onNodeWithTag("announcement_page").assertTextEquals("1 / 2")
        compose.onNodeWithText("详情公告").assertIsNotDisplayed()
        compose.onNodeWithTag("announcements").performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("announcement_page").assertTextEquals("2 / 2")
        compose.onNodeWithText("详情公告").assertIsDisplayed()
        compose.onNodeWithText("查看详情").assertIsDisplayed()
        val left = compose.onNodeWithTag("announcement_suppress").getUnclippedBoundsInRoot()
        val middle = compose.onNodeWithTag("announcement_dismiss").getUnclippedBoundsInRoot()
        val right = compose.onNodeWithTag("announcement_details").getUnclippedBoundsInRoot()
        assertTrue(left.right <= middle.left && middle.right <= right.left)
        compose.onNodeWithTag("announcements").performTouchInput { swipeRight() }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("announcement_page").assertTextEquals("1 / 2")
        compose.onNodeWithTag("announcement_dismiss").assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("announcements").assertDoesNotExist()
        compose.onAllNodesWithText("发现", useUnmergedTree = true).onLast().performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("announcements").assertDoesNotExist()
        assertEquals(1, requests.get())
    }

    @Test fun pendingAnnouncementDoesNotDelayTheHomeScreen() {
        compose.mainClock.autoAdvance = false
        compose.setContent { CurrentMusicApp(container, animateLaunch = true) }
        pumpUntil { compose.onAllNodesWithTag("launch_animation").fetchSemanticsNodes().isEmpty() }
        assertEquals(1L, release.count)
        compose.onNodeWithTag("announcements").assertDoesNotExist()
        release.countDown()
        pumpUntil { compose.onAllNodesWithTag("announcements").fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("announcements").performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("查看详情").assertIsDisplayed()
        compose.onNodeWithTag("announcement_dismiss").assertIsDisplayed().performClick()
    }

    @Test fun longNoticeScrollsWithinItsPageAndFooterStaysVisible() {
        var opened = ""
        var suppressed = false
        val items = listOf(Announcement(id = 1, title = "长公告",
            body = (1..40).joinToString("\n") { "正文段落 $it" } + "\n最后一段"),
            Announcement(id = 2, title = "第二页", body = "独立正文", link = "https://example.com/notice"))
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            Scaffold { AnnouncementDialog(items, onOpenLink = { opened = it }) { suppressed = it } }
        } }
        compose.onNodeWithText("最后一段").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("announcement_dismiss").assertIsDisplayed()
        compose.onNodeWithTag("announcement_page").assertTextEquals("1 / 2")
        val longHeight = compose.onNodeWithTag("announcements").getUnclippedBoundsInRoot().let { it.bottom - it.top }
        compose.onNodeWithTag("announcements").performTouchInput { swipeLeft() }
        compose.onNodeWithText("第二页").assertIsDisplayed()
        compose.onNodeWithText("独立正文").assertIsDisplayed()
        compose.onNodeWithText("最后一段").assertIsNotDisplayed()
        compose.onNodeWithTag("announcement_dismiss").assertIsDisplayed()
        compose.onNodeWithTag("announcement_page").assertTextEquals("2 / 2")
        val shortHeight = compose.onNodeWithTag("announcements").getUnclippedBoundsInRoot().let { it.bottom - it.top }
        assertTrue("Short notice must shrink the dialog", shortHeight < longHeight / 2)
        compose.onNodeWithTag("announcement_details").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("https://example.com/notice", opened) }
        compose.onNodeWithContentDescription("第1条公告").performClick()
        compose.onNodeWithTag("announcement_page").assertTextEquals("1 / 2")
        compose.onNodeWithTag("announcement_details").assertDoesNotExist()
        compose.onNodeWithTag("announcement_suppress").performClick()
        compose.runOnIdle { assertTrue(suppressed) }
    }

    @Test fun rememberedNoticesStayHiddenAcrossStartupButNewNoticeAppears() {
        release.countDown()
        var store = ViewModelStore()
        lateinit var vm: AnnouncementViewModel
        fun start() { compose.runOnIdle {
            vm = ViewModelProvider(store, viewModelFactory { AnnouncementViewModel(container) })[AnnouncementViewModel::class.java]
        } }
        try {
            start()
            compose.waitUntil(10_000) { vm.items.value.size == 2 }
            compose.runOnIdle { vm.dismiss(true) }
            compose.waitUntil(5_000) { runBlocking { container.announcementPreferences.snapshot().read.size == 2 } }
            compose.runOnIdle { store.clear() }
            store = ViewModelStore()
            extraNotice = true
            start()
            compose.waitUntil(10_000) { vm.items.value.map { it.id } == listOf(3L) }
            assertTrue(vm.suppressRead.value)
            assertEquals(2, requests.get())
            compose.runOnIdle { vm.dismiss(true) }
            compose.waitUntil(5_000) { runBlocking { container.announcementPreferences.snapshot().read.size == 3 } }
        } finally { compose.runOnIdle { store.clear() } }
    }
}
