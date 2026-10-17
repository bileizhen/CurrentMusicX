package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.binding.*
import io.github.currencortex.music.feature.library.LibraryViewModel
import io.github.currencortex.music.ui.CurrentMusicApp
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.UUID

class AccountAssetSyncTest {
    @get:Rule val compose = createComposeRule()
    @Test fun projectLoginRestoresCloudBindingHistoryAndBothPlaylistNamespaces(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath
                    val second = request.getHeader("Authorization") == "Bearer second"
                    val body = when (path) {
                        "/cm/auth/login" -> """{"token":"first","user":{"id":7,"username":"listener","nickname":"Listener"}}"""
                        "/cm/auth/me" -> """{"id":7,"username":"listener","nickname":"Listener"}"""
                        "/cm/ncmbind" -> if (second) """{"bound":false}""" else """{"bound":true,"profile":{"uid":700,"nickname":"Cloud NetEase"}}"""
                        "/cm/playlists" -> """{"playlists":[{"id":88,"name":"Project playlist","user_id":7}]}"""
                        "/cm/ncm/user/playlist" -> """{"code":200,"playlist":[{"id":88,"name":"NetEase playlist","creator":{"userId":700}}]}"""
                        "/cm/plays/recent" -> """{"songs":[{"ncm_id":${if (second) 22 else 11},"name":"Cloud recent"}]}"""
                        "/cm/daily" -> """{"daily":[],"forYou":[]}"""
                        "/cm/likes/mine" -> """{"songs":[]}"""
                        "/cm/room/active" -> """{"room":null}"""
                        else -> return MockResponse().setResponseCode(404)
                    }
                    return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
                }
            }
            server.start()
            val endpoint = server.url("/").toString()
            val container = AppContainer(app, "cloud-sync-${UUID.randomUUID()}", useNativeNetease = true,
                neteaseEndpoints = io.github.currencortex.music.core.netease.NeteaseEndpoints(endpoint, endpoint, endpoint))
            val models = ViewModelStore()
            try {
                container.ready.await(); container.sessionRestored.await()
                container.updateSettings.setAutoCheck(false)
                container.desktopLyrics.markRecommendationSeen()
                val url = server.url("/cm/").toString()
                container.accountRepository.server = url; container.musicSettings.setServer(url)
                container.authRepository.login("listener", "isolated-password")
                assertFalse(container.neteaseSessions.state.value.loggedIn)
                lateinit var vm: LibraryViewModel
                compose.runOnIdle { vm = LibraryViewModel(container); models.put("library", vm) }
                compose.setContent { CurrentMusicApp(container) }
                compose.waitUntil(20_000) { vm.home.value.loaded && !vm.home.value.loading && vm.home.value.playlists.size == 2 }
                assertEquals(listOf(11L), vm.home.value.recent.map { it.id })
                assertEquals(setOf("", "netease"), vm.home.value.playlists.map { it.source }.toSet())
                compose.onNodeWithTag("tab_2").performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("open_binding").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("open_binding").performScrollTo().performClick()
                compose.waitUntil(15_000) { compose.onAllNodesWithText("Cloud NetEase").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("netease_direct_notice").performScrollTo()
                    .assertTextContains("已复用当前 CurrentMusic 账号的云端网易云绑定", substring = true)
                container.accountRepository.save("second", io.github.currencortex.music.data.auth.UserDto(8, "second", "Second"))
                compose.waitUntil(15_000) { vm.home.value.recent.singleOrNull()?.id == 22L && !vm.home.value.loading }
                assertFalse(container.bindingRepository.cachedStatus().bound)
                assertEquals(1, vm.home.value.playlists.size)
            } finally { compose.runOnIdle { models.clear() }; container.close() }
        }
    }

    /** Read-only real account probe. Credentials never leave the app process. */
    @Test fun liveCloudBindingAndAssetsAreCompatibleWithTheNativeApp(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("assetsLive") == "true")
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val container = app.container
        container.ready.await(); container.sessionRestored.await()
        val expected = container.audioSession()
        assertNotNull(expected.token)
        val localBefore = container.neteaseSessions.state.value
        val api = ApiClient({ container.audioSession().server }, { container.audioSession().token }, {})
        val results = linkedMapOf<String, JsonElement>()
        val failures = linkedMapOf<String, String>()
        val recovered = linkedMapOf<String, String>()
        suspend fun read(path: String, query: Map<String, String> = emptyMap()): JsonElement? {
            return when (val result = appResult { api.request("GET", path, query, authenticated = true,
                expectedSession = expected, callTimeoutMillis = 25_000) }) {
                is AppResult.Success -> result.value.also { results[path] = it }
                is AppResult.Failure -> { failures[path] = "${result.kind.name} HTTP ${result.status}"; null }
            }
        }
        read("auth/me")
        val binding = read("ncmbind")?.let { api.decode<BindingState>(it) }
        val recent = read("plays/recent", mapOf("limit" to "50"))?.let {
            api.decode<io.github.currencortex.music.data.library.SongListDto>(it) }
        val playlists = read("playlists")?.let {
            api.decode<io.github.currencortex.music.data.library.PlaylistsDto>(it) }
        var neteasePlaylists = 0
        var neteaseLikes = 0
        if (binding?.bound == true && !binding.stale) {
            read("ncm/user/playlist", mapOf("uid" to "${binding.profile!!.uid}", "limit" to "100"))?.jsonObject?.let { raw ->
                if (raw["code"]?.jsonPrimitive?.intOrNull == 200) neteasePlaylists = raw["playlist"]!!.jsonArray.size
                else failures["ncm/user/playlist"] = "upstream code ${raw["code"]}"
            }
            read("ncmbind/likelist")
            read("ncm/likelist", mapOf("uid" to "${binding.profile!!.uid}"))
            val actions = io.github.currencortex.music.data.song.NeteaseSongActionsRepository(api) { expected }
            when (val likes = appResult { actions.likedIds(fresh = true) }) {
                is AppResult.Success -> {
                    neteaseLikes = likes.value.size
                    for (path in listOf("ncmbind/likelist", "ncm/likelist")) failures.remove(path)?.let { recovered[path] = it }
                }
                is AppResult.Failure -> failures["cloudLikes"] = "${likes.kind.name} HTTP ${likes.status}"
            }
        }
        assertEquals(localBefore, container.neteaseSessions.state.value)
        if (recent != null) assertEquals(recent.songs.map { it.id }, container.libraryRepository.recent(fresh = true).map { it.id })
        val report = buildJsonObject {
            put("cloudBound", binding?.bound); put("cloudStale", binding?.stale)
            put("recentCount", recent?.songs?.size); put("cloudPlaylistCount", playlists?.playlists?.size)
            put("neteasePlaylistCount", neteasePlaylists); put("neteaseLikeCount", neteaseLikes)
            put("localSessionUnchanged", true)
            put("recovered", buildJsonObject { recovered.forEach { (path, error) -> put(path, error) } })
            put("errors", buildJsonObject { failures.forEach { (path, error) -> put(path, error) } })
        }
        File(app.getExternalFilesDir(null), "asset-sync-verification.json").writeText(report.toString())
        assertTrue("Upstream read failures: $failures", failures.isEmpty())
    }
}
