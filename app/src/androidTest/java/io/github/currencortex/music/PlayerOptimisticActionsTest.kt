package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.player.*
import io.github.currencortex.music.ui.theme.LeiTheme
import io.github.currencortex.music.ui.util.viewModelFactory
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Hold actual HTTP replies so immediate feedback and stale responses are tested separately. */
class PlayerOptimisticActionsTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var server: MockWebServer
    private lateinit var vm: PlayerViewModel
    @Volatile private var likeGate: CountDownLatch? = null
    @Volatile private var heartGate: CountDownLatch? = null
    private val liked = AtomicBoolean(false)
    private val failLike = AtomicBoolean(false)
    private val failHeart = AtomicBoolean(false)
    private val likeRequests = AtomicInteger()
    private val heartRequests = AtomicInteger()

    @Before fun setup(): Unit = runBlocking {
        container = AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(), "optimistic-${UUID.randomUUID()}")
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = when (request.requestUrl!!.encodedPath) {
                        "/cm/ncmbind" -> """{"bound":true,"profile":{"uid":7}}"""
                        "/cm/ncmbind/likelist" -> """{"ids":${if (liked.get()) "[11]" else "[]"}}"""
                        "/cm/ncm/song/red/count" -> """{"code":200,"data":{"count":100}}"""
                        "/cm/ncm/comment/music" -> """{"code":200,"total":0}"""
                        "/cm/ncm/lyric" -> """{"lines":[{"t":0,"txt":"预览歌词"}]}"""
                        "/cm/ncm/user/playlist" -> """{"code":200,"playlist":[{"id":42,"specialType":5,"creator":{"userId":7}}]}"""
                        "/cm/ncmbind/like/11" -> {
                            val desired = io.github.currencortex.music.core.network.ApiJson.parseToJsonElement(request.body.readUtf8())
                                .let { (it as kotlinx.serialization.json.JsonObject)["like"].toString().toBoolean() }
                            likeRequests.incrementAndGet(); likeGate?.await(10, TimeUnit.SECONDS)
                            if (failLike.getAndSet(false)) return MockResponse().setResponseCode(500)
                            liked.set(desired); """{"code":200}"""
                        }
                        "/cm/ncm/playmode/intelligence/list" -> {
                            heartRequests.incrementAndGet(); heartGate?.await(10, TimeUnit.SECONDS)
                            if (failHeart.getAndSet(false)) return MockResponse().setResponseCode(500)
                            """{"code":200,"data":[{"songInfo":{"id":12,"name":"推荐歌曲","dt":30000}},{"songInfo":{"id":13,"name":"推荐歌曲二","dt":30000}},{"songInfo":{"id":14,"name":"推荐歌曲三","dt":30000}}]}"""
                        }
                        else -> return MockResponse().setResponseCode(404)
                    }
                    return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
                }
            }; start()
        }
        container.ready.await(); container.sessionRestored.await()
        val url = server.url("/cm/").toString()
        container.musicSettings.setServer(url); container.accountRepository.server = url
        container.accountRepository.save("isolated-optimistic-token", UserDto(7, nickname = "Fixture"))
        val song = Song(11, "即时响应", artists = "Fixture", durationMs = 30000)
        container.playbackQueue.replace(listOf(song, Song(23, "原列表")), 0)
        container.playerController.state.value = PlayerState(song = song, positionMs = 1500, durationMs = 30000)
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            vm = viewModel(factory = viewModelFactory { PlayerViewModel(container) })
            top.yukonga.miuix.kmp.basic.Scaffold { PlayerScreen(vm, {}, {}) }
        } }
        compose.waitUntil(10000) { vm.actions.value.likeCount == 100L && vm.actions.value.liked != null }
        container.bindingRepository.status()
        compose.waitUntil(5000) { vm.usesNeteaseLibrary.value }
    }

    @After fun cleanup() { likeGate?.countDown(); heartGate?.countDown(); container.close(); server.shutdown() }

    @Test fun likesSwitchBeforeReplyRollBackOnFailureAndHonorTheLatestTap() {
        likeGate = CountDownLatch(1); failLike.set(true)
        compose.onNodeWithTag("player_like").performClick()
        compose.onNodeWithTag("player_like").assertIsSelected().assertIsEnabled()
        compose.waitUntil(5000) { likeRequests.get() == 1 }
        assertFalse("The held request has not changed server state", liked.get())
        likeGate!!.countDown()
        compose.waitUntil(5000) { vm.likePreview.value == null && vm.actions.value.error != null }
        compose.onNodeWithTag("player_like").assertIsNotSelected().assertIsEnabled()

        likeGate = CountDownLatch(1)
        compose.onNodeWithTag("player_like").performClick()
        compose.waitUntil(5000) { likeRequests.get() == 2 }
        compose.onNodeWithTag("player_like").assertIsSelected().assertIsEnabled()
        compose.onNodeWithTag("player_like").performClick()
        compose.onNodeWithTag("player_like").assertIsNotSelected().assertIsEnabled()
        likeGate!!.countDown()
        compose.waitUntil(5000) { vm.likePreview.value == null && likeRequests.get() == 3 }
        compose.onNodeWithTag("player_like").assertIsNotSelected()
        assertFalse(liked.get())

        likeGate = null
        compose.onNodeWithTag("player_like").performClick()
        compose.waitUntil(5000) { vm.likePreview.value == null && liked.get() }
        likeGate = CountDownLatch(1); failLike.set(true)
        compose.onNodeWithTag("player_like").performClick()
        compose.onNodeWithTag("player_like").assertIsNotSelected().assertIsEnabled()
        compose.waitUntil(5000) { likeRequests.get() == 5 }
        likeGate!!.countDown()
        compose.waitUntil(5000) { vm.likePreview.value == null && vm.actions.value.error != null }
        compose.onNodeWithTag("player_like").assertIsSelected().assertIsEnabled()
        assertTrue(liked.get())
    }

    @Test fun heartSwitchesBeforeReplyCanBeLeftImmediatelyAndRollsBackOnFailure() {
        compose.runOnUiThread { vm.setPlaybackMode(PlaybackMode.SHUFFLE) }
        val original = container.playbackQueue.state.value.songs
        heartGate = CountDownLatch(1); failHeart.set(true)
        compose.onNodeWithTag("player_cycle_mode").performClick()
        compose.onNodeWithTag("player_cycle_mode").assertContentDescriptionEquals(PlaybackMode.HEART.label).assertIsSelected().assertIsEnabled()
        compose.waitUntil(5000) { heartRequests.get() == 1 }
        assertEquals(original, container.playbackQueue.state.value.songs)
        heartGate!!.countDown()
        compose.waitUntil(5000) { container.playbackQueue.state.value.mode == PlaybackMode.SHUFFLE && !vm.heartLoading.value }
        compose.onNodeWithTag("player_cycle_mode").assertIsNotSelected().assertIsEnabled()

        heartGate = CountDownLatch(1)
        compose.onNodeWithTag("player_cycle_mode").performClick()
        compose.waitUntil(5000) { heartRequests.get() == 2 }
        compose.onNodeWithTag("player_cycle_mode").assertIsSelected().assertIsEnabled().performClick()
        compose.onNodeWithTag("player_cycle_mode").assertContentDescriptionEquals(PlaybackMode.LIST.label).assertIsNotSelected()
        heartGate!!.countDown()
        compose.waitUntil(5000) { !vm.heartLoading.value }
        assertEquals(PlaybackMode.LIST, container.playbackQueue.state.value.mode)
        assertEquals(original, container.playbackQueue.state.value.songs)
        assertEquals(1500L, container.playerController.state.value.positionMs)
        assertFalse(container.playerController.state.value.playing)
    }
}
