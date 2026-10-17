package io.github.currencortex.music.data.library

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.core.netease.*
import io.github.currencortex.music.core.security.TokenStore
import io.github.currencortex.music.data.binding.*
import io.github.currencortex.music.data.settings.MusicSettingsRepository
import io.github.currencortex.music.data.song.NeteaseSongActionsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.TimeUnit

class CloudAssetSyncTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val server = MockWebServer()
    private val nativeServer = MockWebServer()
    private lateinit var sessions: NeteaseSessionStore
    private lateinit var current: LibraryRepository
    private lateinit var primary: PrimaryMusicLibrary
    private var token = "first-account"
    private var accountId = 7L
    private var bound = true
    private var failNative = false
    private var failCloud = false
    private fun owner() = RequestSession(server.url("/cm/").toString(), token, sessions.state.value.revision)
    private fun json(body: String) = MockResponse().setBody(body)
    @Before fun setup(): Unit = runBlocking {
        server.start(); nativeServer.start()
        sessions = NeteaseSessionStore(object : TokenStore {
            override fun read(): String? = null
            override fun write(token: String) {}
            override fun clear() {}
        }, scope)
        sessions.ready.await()
        val endpoint = nativeServer.url("/").toString()
        val api = ApiClient({ owner().server }, { token }, {}, netease = NeteaseGateway(
            NeteaseTransport(sessions, NeteaseEndpoints(endpoint, endpoint, endpoint))))
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                "/cm/ncmbind" -> json("""{"bound":$bound,"profile":{"uid":700}}""")
                "/cm/ncm/user/playlist" -> if (failNative) MockResponse().setResponseCode(503) else
                    json("""{"code":200,"playlist":[{"id":88,"name":"Cloud NetEase","creator":{"userId":700}}]}""")
                "/cm/playlists" -> if (failCloud) MockResponse().setResponseCode(503) else
                    json("""{"playlists":[{"id":88,"name":"Project playlist","user_id":7}]}""")
                "/cm/plays/recent" -> json("""{"songs":[{"ncm_id":${if (request.getHeader("Authorization") == "Bearer first-account") 11 else 22},"name":"Cloud recent"}]}""")
                "/cm/daily" -> json("""{"daily":[{"ncm_id":33,"name":"Cloud daily"}]}""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        val settings = MusicSettingsRepository(object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = transform(data.value).also { data.value = it }
        }, scope)
        val binding = BindingRepository(api, ::owner) {}
        current = LibraryRepository(api, { accountId }, ::owner)
        val native = NeteaseLibraryRepository(api, binding, NeteaseSongActionsRepository(api, session = ::owner), ::owner)
        primary = PrimaryMusicLibrary(current, native, binding, settings, scope)
    }
    @After fun cleanup() { scope.cancel(); server.shutdown(); nativeServer.shutdown() }
    @Test fun projectHistoryIsUsedEvenWithALocalNeteaseLoginAndCacheDoesNotCrossAccounts(): Unit = runBlocking {
        sessions.login(mapOf("MUSIC_U" to "local-only"), BoundProfile(42), sessions.snapshot())
        assertEquals(listOf(11L), current.recent().map { it.id })
        val request = server.takeRequest()
        assertEquals("/cm/plays/recent?limit=50", request.path)
        assertNull(request.getHeader("Cookie"))
        token = "second-account"; accountId = 8
        assertEquals(listOf(22L), current.recent().map { it.id })
        assertEquals(0, nativeServer.requestCount)
    }
    @Test fun projectAndBoundNeteasePlaylistsKeepSeparateIds(): Unit = runBlocking {
        val lists = primary.playlists()
        assertEquals(2, lists.size)
        assertEquals(listOf("", "netease"), lists.map { it.source })
        assertEquals(listOf(88L, 88L), lists.map { it.id })
        assertTrue(primary.resolveNetease())
        assertEquals(0, nativeServer.requestCount)
    }
    @Test fun unboundProjectAccountFallsBackToItsOwnMusicAssets(): Unit = runBlocking {
        bound = false
        assertFalse(primary.resolveNetease())
        assertEquals("Project playlist", primary.playlists().single().name)
        assertNull(primary.likedPlaylist())
        assertEquals(33L, current.daily().songs.single().id)
        assertEquals(0, nativeServer.requestCount)
    }
    @Test fun nativeFailureDoesNotHideCloudPlaylistsAndCloudFailureDoesNotHideNative(): Unit = runBlocking {
        failNative = true
        assertEquals("Project playlist", primary.playlists(fresh = true).single().name)
        failNative = false; failCloud = true
        assertEquals("Cloud NetEase", primary.playlists(fresh = true).single().name)
    }
    @Test fun aHistoryReplyFromThePreviousProjectAccountIsDiscarded(): Unit = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = json("""{"songs":[{"ncm_id":11,"name":"Old account"}]}""")
                .setBodyDelay(400, TimeUnit.MILLISECONDS)
        }
        val pending = async { appResult { current.recent() } }
        withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(3, TimeUnit.SECONDS)) }
        token = "second-account"; accountId = 8
        assertTrue(pending.await() is AppResult.Failure)
    }
}
