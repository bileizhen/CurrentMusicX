package io.github.currencortex.music.data.emby

import io.github.currencortex.music.core.emby.*
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.core.security.TokenStore
import io.github.currencortex.music.data.song.*
import io.github.currencortex.music.feature.lyrics.data.SongLyricsStore
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import okhttp3.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.TimeUnit

class EmbyRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val server = MockWebServer()
    private class Vault : TokenStore {
        var value: String? = null
        override fun read() = value
        override fun write(token: String) { value = token }
        override fun clear() { value = null }
    }
    private val vault = Vault()
    private val repo = EmbyRepository(vault, scope)
    private val track = """{"Id":"track-1","Name":"Song","Type":"Audio","Album":"Album","AlbumId":"album-1","AlbumPrimaryImageTag":"tag","Artists":["Singer"],"RunTimeTicks":123450000,"Container":"flac","MediaSources":[{"Id":"source-1"}],"MediaStreams":[{"Type":"Audio","SampleRate":96000,"Channels":2}],"UserData":{"IsFavorite":true}}"""
    @Before fun start() { server.start() }
    @After fun stop() { scope.cancel(); server.shutdown() }
    private suspend fun login(user: String = "user-1", download: Boolean = false) {
        server.enqueue(MockResponse().setBody("""{"AccessToken":"fixture-token","User":{"Id":"$user","Policy":{"EnableMediaPlayback":true,"EnableContentDownloading":$download}}}"""))
        repo.connect(server.url("/music/").toString(), "fixture", "fixture-password")
    }
    private suspend fun song(): Song {
        server.enqueue(MockResponse().setBody("""{"Items":[$track],"TotalRecordCount":1}"""))
        return repo.browse("songs").songs.single()
    }
    @Test fun loginUsesBodyAndPersistsOnlyTokenWithServer() = runBlocking {
        login()
        val request = server.takeRequest()
        assertEquals("/music/emby/Users/AuthenticateByName", request.path)
        assertEquals("POST", request.method)
        assertTrue(request.body.readUtf8().contains("fixture-password"))
        assertNull(request.getHeader("X-Emby-Token"))
        assertFalse(vault.value!!.contains("fixture-password"))
        val restored = EmbyRepository(vault, scope); restored.ready.await()
        assertEquals(repo.state.value, restored.state.value)
        assertFalse(EmbyAccess("http://example.org/", "user", "id", "secret", "device").toString().contains("secret"))
    }
    @Test fun normalizePreservesReverseProxyAndRejectsCredentialsOrQueries() {
        assertEquals("https://example.org/music/emby/", normalizeEmbyServer("https://example.org/music/emby/"))
        assertEquals("https://example.org/emby/", normalizeEmbyServer("https://example.org"))
        listOf("https://user:pass@example.org", "https://example.org?api_key=secret", "ftp://example.org", "https://example.org/#login").forEach {
            assertTrue(runCatching { normalizeEmbyServer(it) }.isFailure)
        }
    }
    @Test fun failedLoginKeepsPreviousAccountAndNeverEchoesServerBody() = runBlocking {
        login(); val before = repo.state.value
        server.enqueue(MockResponse().setResponseCode(401).setBody("private server message"))
        val failure = appResult { repo.connect(server.url("/").toString(), "other", "bad") } as AppResult.Failure
        assertEquals(ErrorKind.Unauthorized, failure.kind)
        assertFalse(failure.message.contains("private server message"))
        assertEquals(before, repo.state.value)
    }
    @Test fun songsKeepOpaqueIdsAndNoCredentialsInSerializedMetadata() = runBlocking {
        login(); val first = song(); val second = song()
        assertEquals(first, second); assertTrue(first.id < 0)
        assertEquals(12345, first.durationMs)
        assertTrue(first.privateLibrary); assertEquals("source-1", first.emby!!.mediaSourceId)
        assertTrue(repo.likes.value[first.id] == true)
        val serialized = ApiJson.encodeToString(first)
        listOf("fixture-token", "fixture-password", "fixture").forEach { assertFalse(serialized.contains(it)) }
        val source = repo.source(first)
        assertEquals(96000, source.sampleRate); assertEquals(2, source.channelCount)
        assertTrue(source.url.contains("Static=true")); assertTrue(source.url.contains("MediaSourceId=source-1"))
        login("user-2"); val other = song()
        assertNotEquals(first.id, other.id)
        assertNotEquals(SongLyricsStore.identity(first), SongLyricsStore.identity(other))
        assertFalse(repo.accepts(first)); assertTrue(runCatching { repo.source(first) }.isFailure)
    }
    @Test fun allCollectionsUseOffsetsAndServerTotalsEvenForShortPages() = runBlocking {
        login(); server.takeRequest()
        for (kind in listOf("albums", "artists", "playlists", "songs", "likes", "album", "artist", "playlist", "search")) {
            server.enqueue(MockResponse().setBody("""{"Items":[$track],"TotalRecordCount":100}"""))
            val page = repo.browse(kind, "parent", "query", 40)
            assertEquals(41, page.nextOffset); assertTrue(page.more)
            val url = server.takeRequest().requestUrl!!
            assertEquals("40", url.queryParameter("StartIndex")); assertEquals("40", url.queryParameter("Limit"))
            assertEquals("query", url.queryParameter("SearchTerm"))
            if (kind == "playlist") { assertEquals("/music/emby/Playlists/parent/Items", url.encodedPath); assertNull(url.queryParameter("SortBy")) }
            if (kind == "artist") assertEquals("parent", url.queryParameter("ArtistIds"))
        }
    }
    @Test fun albumAndArtistArtworkUsesTheInheritedImageOwner() = runBlocking {
        login()
        for (type in listOf("MusicAlbum", "MusicArtist")) {
            server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"parent","Name":"Album","Type":"$type","ImageTags":{},"PrimaryImageItemId":"image-owner","PrimaryImageTag":"inherited-tag"}],"TotalRecordCount":1}"""))
            val cover = repo.browse(if (type == "MusicAlbum") "albums" else "artists").entries.single().cover
            assertTrue(cover.contains("/Items/image-owner/Images/Primary"))
            assertTrue(cover.contains("Tag=inherited-tag"))
            assertFalse(cover.contains("fixture-token"))
        }
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"own","Name":"Album","Type":"MusicAlbum","ImageTags":{"Primary":"own-tag"},"PrimaryImageItemId":"child","PrimaryImageTag":"fallback"}],"TotalRecordCount":1}"""))
        val own = repo.browse().entries.single().cover
        assertTrue(own.contains("/Items/own/Images/Primary")); assertTrue(own.contains("Tag=own-tag"))
    }
    @Test fun mediaAuthenticationPreservesRangeAndReturnsSafeRequest() = runBlocking {
        login(); val song = song(); server.takeRequest(); server.takeRequest()
        val safe = repo.source(song).url
        val http = OkHttpClient.Builder().addInterceptor(repo.client.mediaInterceptor()).build()
        server.enqueue(MockResponse().setResponseCode(206).setBody("audio"))
        http.newCall(Request.Builder().url(safe).header("Range", "bytes=12-15").build()).execute().use {
            assertEquals("audio", it.body!!.string()); assertEquals(safe, it.request.url.toString())
            assertNull(it.request.header("X-Emby-Token"))
        }
        val request = server.takeRequest()
        assertEquals("fixture-token", request.getHeader("X-Emby-Token"))
        assertEquals("bytes=12-15", request.getHeader("Range"))
        assertNull(request.requestUrl!!.queryParameter(EmbyClient.MARKER))
        val malicious = safe.replace("/Audio/track-1/stream", "/Users/user-1")
        assertTrue(runCatching { http.newCall(Request.Builder().url(malicious).build()).execute().close() }.isFailure)
        assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
    }
    @Test fun redirectsDoNotForwardAccountHeaders() = runBlocking {
        login(); val song = song(); server.takeRequest(); server.takeRequest()
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/cdn/audio")))
        server.enqueue(MockResponse().setBody("audio"))
        val http = OkHttpClient.Builder().addInterceptor(repo.client.mediaInterceptor()).build()
        http.newCall(Request.Builder().url(repo.source(song).url).header("Range", "bytes=0-4").build()).execute().use { assertEquals("audio", it.body!!.string()) }
        assertEquals("fixture-token", server.takeRequest().getHeader("X-Emby-Token"))
        val redirected = server.takeRequest()
        assertNull(redirected.getHeader("X-Emby-Token")); assertNull(redirected.getHeader("X-Emby-Authorization"))
        assertEquals("bytes=0-4", redirected.getHeader("Range"))
    }
    @Test fun favoritesAndDownloadsRespectProviderAndPermissions() = runBlocking {
        login(); val song = song(); server.takeRequest(); server.takeRequest()
        assertTrue(runCatching { repo.source(song, download = true) }.exceptionOrNull() is ApiException)
        server.enqueue(MockResponse().setBody("{}"))
        assertFalse(repo.setLiked(song, false)); assertFalse(repo.likes.value[song.id]!!)
        val mutation = server.takeRequest()
        assertEquals("DELETE", mutation.method); assertEquals("/music/emby/Users/user-1/FavoriteItems/track-1", mutation.path)
        login(download = true)
        assertTrue(repo.source(song, download = true).url.contains("/Items/track-1/Download"))
        server.enqueue(MockResponse().setResponseCode(204))
        repo.disconnect(); assertNull(vault.value); assertFalse(repo.state.value.configured)
    }
    @Test fun cancellationAlsoInterruptsSlowResponseBody() = runBlocking {
        login(); server.takeRequest()
        server.enqueue(MockResponse().setBody("{\"Items\":[]}").setBodyDelay(3, TimeUnit.SECONDS))
        val job = launch(Dispatchers.Default) { repo.browse() }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        delay(100)
        withTimeout(2000) { job.cancelAndJoin() }
    }
    @Test fun zeroTotalWithFullPageStillAllowsLoadingMore() = runBlocking {
        login()
        server.enqueue(MockResponse().setBody("""{"Items":[${List(40) { track }.joinToString(",")}],"TotalRecordCount":0}"""))
        assertTrue(repo.browse("search", query = "song").more)
    }
    @Test fun canceledPaginationCancelsWireRequest() = runBlocking {
        login(); server.takeRequest()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = launch(Dispatchers.Default) { repo.browse() }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        withTimeout(2000) { job.cancelAndJoin() }
    }
}
