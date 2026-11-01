package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.netease.NeteaseEndpoints
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.binding.BoundProfile
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.ui.CurrentMusicApp
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** Native transport and real navigation, using synthetic accounts in the isolated package. */
class NeteasePlaylistRemovalTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var server: MockWebServer
    private val writes = CopyOnWriteArrayList<String>()
    @Volatile private var owned = true
    @Volatile private var subscribed = true
    @Volatile private var renamed = false
    @Volatile private var failWrite = false
    private var detailId = 88L

    @Before fun setup(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(context.packageName.endsWith(".verification"))
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath
                    val body = when (path) {
                        "/cm/playlists" -> """{"playlists":[
                            {"id":3,"name":"Obsolete imported snapshot","source":"ncm","user_id":7},
                            {"id":88,"name":"Keep custom playlist","source":"currentmusic","user_id":7}]}"""
                        "/cm/playlists/88" -> """{"id":88,"name":"Keep custom playlist","source":"currentmusic","user_id":7,"tracks":[]}"""
                        "/weapi/user/playlist" -> """{"code":200,"playlist":[${buildList {
                            if (owned) add("""{"id":88,"name":"${if (renamed) "Renamed upstream" else "Owned native playlist"}","trackCount":1,"creator":{"userId":42}}""")
                            if (subscribed) add("""{"id":89,"name":"Subscribed native playlist","subscribed":true,"trackCount":1,"creator":{"userId":99}}""")
                        }.joinToString(",")}]}"""
                        "/eapi/playlist/detail", "/eapi/v6/playlist/detail" -> """{"code":200,"playlist":{
                            "id":$detailId,"name":"${if (detailId == 88L) "Owned native playlist" else "Subscribed native playlist"}",
                            "creator":{"userId":${if (detailId == 88L) 42 else 99}},"subscribed":${detailId == 89L},
                            "trackCount":1,"trackIds":[{"id":11}],"tracks":[{"id":11,"name":"Fixture track"}]}}"""
                        "/weapi/playlist/remove", "/eapi/playlist/unsubscribe" -> {
                            writes += path
                            if (failWrite) """{"code":500}""" else {
                                if (path.endsWith("remove")) owned = false else subscribed = false
                                """{"code":200}"""
                            }
                        }
                        "/cm/daily" -> """{"daily":[],"forYou":[]}"""
                        "/cm/plays/recent", "/cm/likes/mine" -> """{"songs":[]}"""
                        "/cm/room/active" -> """{"room":null}"""
                        "/eapi/song/like/get" -> """{"code":200,"ids":[]}"""
                        else -> return MockResponse().setResponseCode(404)
                    }
                    return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
                }
            }
            start()
        }
        val endpoint = server.url("/").toString()
        container = AppContainer(context, "playlist-removal-${UUID.randomUUID()}", useNativeNetease = true,
            neteaseEndpoints = NeteaseEndpoints(endpoint, endpoint, endpoint))
        container.ready.await(); container.sessionRestored.await()
        val base = server.url("/cm/").toString()
        container.musicSettings.setServer(base)
        container.accountRepository.server = base
        container.accountRepository.save("synthetic-project-account", UserDto(7, nickname = "Fixture listener"))
        container.updateSettings.setAutoCheck(false)
        container.settings.edit { AppearanceSettings(themeMode = ThemeMode.LIGHT, blur = false) }
        container.desktopLyrics.markRecommendationSeen()
        container.neteaseSessions.login(mapOf("MUSIC_U" to "synthetic-native-account"), BoundProfile(42, "Fixture"),
            container.neteaseSessions.snapshot())
    }

    @After fun cleanup() { container.close(); server.shutdown() }

    private fun openIndex() {
        compose.setContent { CurrentMusicApp(container) }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("open_playlists").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("open_playlists").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Subscribed native playlist").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Obsolete imported snapshot").assertDoesNotExist()
        compose.onNodeWithText("Keep custom playlist").assertExists()
    }

    private fun openDetails(id: Long) {
        detailId = id
        openIndex()
        compose.onNodeWithText(if (id == 88L) "Owned native playlist" else "Subscribed native playlist").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("playlist_song_11").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("playlist_more").performClick()
    }

    @Test fun ownedDeletionUpdatesIndexAndKeepsSameIdCustomPlaylist() {
        openDetails(88)
        compose.onNodeWithTag("delete_playlist").assertTextContains("删除歌单").performClick()
        compose.onNodeWithText("确认删除").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("playlist_index").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Owned native playlist").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Keep custom playlist").assertExists()
        compose.onNodeWithText("Obsolete imported snapshot").assertDoesNotExist()
        assertEquals(listOf("/weapi/playlist/remove"), writes.toList())
    }

    @Test fun subscriptionCanBeCancelledWithoutShowingRenameOrPermanentDelete() {
        openDetails(89)
        compose.onNodeWithTag("rename_playlist").assertDoesNotExist()
        compose.onNodeWithTag("delete_playlist").assertTextContains("取消收藏").performClick()
        compose.onNodeWithText("确认取消收藏").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("playlist_index").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Subscribed native playlist").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Owned native playlist").assertExists()
        assertEquals(listOf("/eapi/playlist/unsubscribe"), writes.toList())
    }

    @Test fun indexRefreshAcceptsUpstreamRenameAndSubscriptionRemoval() {
        openIndex()
        renamed = true; subscribed = false
        compose.onNodeWithTag("playlist_index").performTouchInput { swipeDown() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Renamed upstream").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Subscribed native playlist").assertDoesNotExist()
        compose.onNodeWithText("Obsolete imported snapshot").assertDoesNotExist()
        compose.onNodeWithText("Keep custom playlist").assertExists()
        assertTrue(writes.isEmpty())
    }

    @Test fun failedCancellationKeepsDetailsAndAllowsRetry() {
        failWrite = true
        openDetails(89)
        compose.onNodeWithTag("delete_playlist").performClick()
        compose.onNodeWithText("确认取消收藏").performClick()
        compose.waitUntil(10_000) { writes.size == 1 }
        compose.waitForIdle()
        compose.onNodeWithTag("playlist_song_11").assertExists()
        failWrite = false
        compose.onNodeWithText("确认取消收藏").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("playlist_index").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf("/eapi/playlist/unsubscribe", "/eapi/playlist/unsubscribe"), writes.toList())
    }
}
