package io.github.currencortex.music.data.navidrome

import io.github.currencortex.music.core.navidrome.*
import io.github.currencortex.music.core.security.TokenStore
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.data.song.*
import io.github.currencortex.music.feature.lyrics.domain.LyricsMatcher
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import okhttp3.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.TimeUnit

class NavidromeRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val server = MockWebServer()
    private class Vault : TokenStore {
        var value: String? = null
        override fun read() = value
        override fun write(token: String) { value = token }
        override fun clear() { value = null }
    }
    private val vault = Vault()
    private val repo = NavidromeRepository(vault, scope)
    private fun ok(data: String = "") = MockResponse().setBody("""{"subsonic-response":{"status":"ok"${if (data.isEmpty()) "" else ",$data"}}}""")
    private val track = """{"id":"uuid-track-123","title":"Original song","artist":"Artist","album":"Album","coverArt":"cover-uuid","duration":120,"suffix":"flac","samplingRate":48000,"channelCount":2}"""
    @Before fun start() { server.start() }
    @After fun stop() { scope.cancel(); server.shutdown() }
    private suspend fun login() {
        server.enqueue(ok("\"serverVersion\":\"fixture\"")); server.enqueue(ok("\"user\":{\"downloadRole\":true}"))
        repo.connect(server.url("/music/").toString(), "fixture-user", "fixture-password")
    }
    private suspend fun song(): Song {
        server.enqueue(ok("\"album\":{\"song\":[$track]}"))
        return repo.album("album-uuid").single()
    }
    @Test fun saltedAuthenticationPreservesBasePathAndNeverSendsPassword() = runBlocking {
        login()
        val first = server.takeRequest().requestUrl!!
        assertEquals("/music/rest/ping.view", first.encodedPath)
        assertEquals("fixture-user", first.queryParameter("u"))
        assertEquals(digest("MD5", "fixture-password" + first.queryParameter("s")), first.queryParameter("t"))
        assertFalse(first.toString().contains("fixture-password")); assertNull(first.queryParameter("p"))
        assertNotEquals(first.queryParameter("s"), server.takeRequest().requestUrl!!.queryParameter("s"))
        assertEquals(server.url("/music/").toString(), normalizeNavidromeServer(server.url("/music/rest/").toString()))
        assertFalse(NavidromeAccess("https://example.org/", "user", "secret").toString().contains("secret"))
    }
    @Test fun failedLoginDoesNotReplaceExistingAccountAndRestoreRetainsIdentity() = runBlocking {
        login(); val previous = repo.state.value
        server.enqueue(MockResponse().setBody("""{"subsonic-response":{"status":"failed","error":{"code":40,"message":"private details"}}}"""))
        try { repo.connect(server.url("/").toString(), "wrong", "wrong"); fail() } catch (e: ApiException) { assertEquals(ErrorKind.Unauthorized, e.kind); assertFalse(e.message!!.contains("private details")) }
        assertEquals(previous, repo.state.value)
        repo.home(true)
        val restored = NavidromeRepository(vault, scope); restored.ready.await()
        assertEquals(previous.identity, restored.state.value.identity); assertTrue(restored.state.value.home)
        restored.disconnect(); assertFalse(restored.state.value.configured); assertNull(vault.value)
    }
    @Test fun opaqueIdsStayStableAndSerializationContainsOnlySafeMediaUrls() = runBlocking {
        login(); val one = song(); val two = song()
        assertTrue(one.id < 0); assertEquals(one.id, two.id); assertEquals("uuid-track-123", one.navidrome!!.id)
        assertEquals(MusicSource.NAVIDROME, LyricsMatcher.candidates(one).single().source)
        val serialized = ApiJson.encodeToString(one)
        assertFalse(serialized.contains("fixture-password")); assertFalse(serialized.contains("fixture-user"))
        assertFalse(one.cover.contains("&t=")); assertFalse(one.cover.contains("&u="))
        val source = repo.source(one, AudioQuality.AUTO)
        assertTrue(source.url.contains("format=raw")); assertEquals(48000, source.sampleRate)
        val otherVault = Vault(); val other = NavidromeRepository(otherVault, scope)
        server.enqueue(ok()); server.enqueue(ok()); other.connect(server.url("/music/").toString(), "other", "password")
        server.enqueue(ok("\"album\":{\"song\":[$track]}"))
        assertNotEquals(one.id, other.album("album-uuid").single().id)
        assertFalse(other.accepts(one))
    }
    @Test fun markedMediaIsSignedOnlyForPinnedRoutesPreservesRangeAndSafeResponseRequest() = runBlocking {
        login(); server.takeRequest(); server.takeRequest()
        val http = OkHttpClient.Builder().addInterceptor(repo.client.mediaInterceptor()).build()
        val safe = repo.client.media("stream", listOf("id" to "uuid-track-123", "format" to "raw"))
        server.enqueue(MockResponse().setResponseCode(206).setBody("audio"))
        http.newCall(Request.Builder().url(safe).header("Range", "bytes=100-").build()).execute().use {
            assertEquals(safe, it.request.url.toString()); assertEquals("audio", it.body!!.string())
        }
        val signed = server.takeRequest()
        assertEquals("bytes=100-", signed.getHeader("Range")); assertNull(signed.getHeader("Token"))
        assertEquals("uuid-track-123", signed.requestUrl!!.queryParameter("id")); assertNotNull(signed.requestUrl!!.queryParameter("t"))
        val forged = safe.replace("/music/rest/", "/other/rest/")
        try { http.newCall(Request.Builder().url(forged).build()).execute(); fail() } catch (_: java.io.IOException) {}
        server.enqueue(MockResponse().setHeader("Location", server.url("/untrusted")).setResponseCode(302))
        try { http.newCall(Request.Builder().url(safe).build()).execute(); fail() } catch (_: java.io.IOException) {}
        server.takeRequest(); assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
        repo.disconnect()
        try { http.newCall(Request.Builder().url(safe).build()).execute(); fail() } catch (_: ApiException) {}
    }
    @Test fun ordinaryImageRequestsRemainUnauthenticatedAndForgedAuthParametersAreRejected() = runBlocking {
        login(); server.takeRequest(); server.takeRequest()
        val http = OkHttpClient.Builder().addInterceptor(repo.client.mediaInterceptor()).build()
        server.enqueue(MockResponse().setBody("cover"))
        http.newCall(Request.Builder().url(server.url("/public/cover")).build()).execute().close()
        val request = server.takeRequest(); assertNull(request.requestUrl!!.queryParameter("t")); assertNull(request.getHeader("Token"))
        val forged = repo.client.media("stream", listOf("id" to "uuid")) + "&u=other"
        try { http.newCall(Request.Builder().url(forged).build()).execute(); fail() } catch (_: java.io.IOException) {}
        assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
    }
    @Test fun synchronizedEmbeddedLyricsAndLrcFallbackKeepRealTiming() = runBlocking {
        login(); val track = song()
        server.enqueue(ok(""""lyricsList":{"structuredLyrics":[{"synced":true,"offset":200,"line":[{"start":1000,"value":"First"},{"start":3000,"value":"Second"}]},{"kind":"translation","line":[{"start":1000,"value":"译文"}]}]}"""))
        val lyrics = repo.lyrics(track)
        assertEquals(1200L, lyrics.lines.first().startTimeMs); assertEquals(3200L, lyrics.lines.first().endTimeMs)
        assertEquals("译文", lyrics.lines.first().translation); assertEquals("Navidrome", lyrics.metadata.source)
        server.enqueue(ok()); server.enqueue(ok(""""lyrics":{"value":"[00:02.00]Embedded LRC"}"""))
        assertEquals(2000L, repo.lyrics(track).lines.single().startTimeMs)
    }
    @Test fun likesAndPlaylistWritesUseOpaqueSongIdsAndRemovalIndex() = runBlocking {
        login(); val song = song()
        server.enqueue(ok("\"song\":$track")); server.enqueue(ok())
        assertTrue(repo.toggleLike(song)); assertTrue(repo.likes.value[song.id] == true)
        server.enqueue(ok()); repo.add("playlist-uuid", song)
        server.enqueue(ok()); repo.remove("playlist-uuid", 3)
        repeat(4) { server.takeRequest() }
        assertEquals("uuid-track-123", server.takeRequest().requestUrl!!.queryParameter("id"))
        assertEquals("uuid-track-123", server.takeRequest().requestUrl!!.queryParameter("songIdToAdd"))
        assertEquals("3", server.takeRequest().requestUrl!!.queryParameter("songIndexToRemove"))
    }
    @Test fun pagingUsesOffsetsAndCorruptVaultDoesNotCrashRestoration() = runBlocking {
        login(); repeat(2) { server.takeRequest() }
        server.enqueue(ok("\"albumList2\":{\"album\":[]}")); assertTrue(repo.albums(40).isEmpty())
        assertEquals("40", server.takeRequest().requestUrl!!.queryParameter("offset"))
        server.enqueue(ok("\"searchResult3\":{\"song\":[$track]}")); assertEquals(1, repo.search("abc", 40).songs.size)
        val query = server.takeRequest().requestUrl!!
        assertEquals("40", query.queryParameter("songOffset")); assertEquals("0", query.queryParameter("albumCount"))
        val bad = NavidromeRepository(Vault().apply { value = "broken" }, scope); bad.ready.await(); assertFalse(bad.state.value.configured)
    }
}
