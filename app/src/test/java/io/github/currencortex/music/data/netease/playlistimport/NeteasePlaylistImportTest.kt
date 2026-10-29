package io.github.currencortex.music.data.netease.playlistimport

import io.github.currencortex.music.core.netease.*
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.core.security.TokenStore
import io.github.currencortex.music.data.binding.BoundProfile
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

class NeteasePlaylistImportTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val server = MockWebServer()
    private lateinit var sessions: NeteaseSessionStore
    private lateinit var repository: NeteasePlaylistImportRepository
    @Before fun setup() = runBlocking {
        server.start()
        sessions = NeteaseSessionStore(object : TokenStore {
            override fun read(): String? = null
            override fun write(token: String) = Unit
            override fun clear() = Unit
        }, scope)
        sessions.ready.await()
        val base = server.url("/").toString()
        repository = NeteasePlaylistImportRepository(NeteaseTransport(sessions, NeteaseEndpoints(base, base, base)), base)
    }
    @After fun close() { scope.cancel(); server.shutdown() }
    private suspend fun login(id: Long = 42) = sessions.login(mapOf("MUSIC_U" to "fixture-$id"), BoundProfile(id, "Fixture"), sessions.snapshot())
    private fun enqueue(body: String) = server.enqueue(MockResponse().setBody(body))
    private fun take(): Pair<RecordedRequest, JsonObject> {
        val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        val encoded = URLDecoder.decode(request.body.readUtf8().substringAfter("params="), "UTF-8")
        val bytes = encoded.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val payload = NeteaseCrypto.decryptResponse(bytes).toString(Charsets.UTF_8).split("-36cd479b6b5-")[1]
        return request to Json.parseToJsonElement(payload).jsonObject
    }

    @Test fun loggedOutSubmissionNeverSendsARequest() = runBlocking {
        val result = appResult { repository.submitText("歌曲 - 歌手", false) }
        assertEquals(ErrorKind.NeteaseBindingRequired, (result as AppResult.Failure).kind)
        assertEquals(0, server.requestCount)
    }

    @Test fun linksAreValidatedAndCreatedAsTheOfficialBatchEnvelope() = runBlocking {
        login()
        enqueue("""{"code":200,"data":{"link":"https://example.com/list?id=1"}}""")
        enqueue("""{"code":200,"data":{"taskId":99}}""")
        val task = repository.submitLinks(listOf("分享歌单 https://example.com/list?id=1"), true)
        assertEquals(99L, task.id)
        val (check, checked) = take()
        assertEquals("/eapi/playlist/import/url/check", check.path)
        assertEquals("https://example.com/list?id=1", checked["link"]?.jsonPrimitive?.content)
        val (create, payload) = take()
        assertEquals("/eapi/playlist/import/name/task/create", create.path)
        assertNull(create.getHeader("Authorization"))
        assertTrue(create.getHeader("Cookie")!!.contains("MUSIC_U=fixture-42"))
        assertEquals(JsonPrimitive(true), payload["importStarPlaylist"])
        val songs = Json.parseToJsonElement(payload["songs"]!!.jsonPrimitive.content).jsonArray
        assertEquals("https://example.com/list?id=1", songs.single().jsonObject["url"]!!.jsonPrimitive.content)
    }

    @Test fun textAndLocalImportsSendOnlyTheDocumentedMetadata() = runBlocking {
        login()
        enqueue("""{"code":200,"data":{"taskId":1}}""")
        repository.submitText("歌名 - 歌手\n第二首", false)
        val (_, text) = take()
        val song = Json.parseToJsonElement(text["songs"]!!.jsonPrimitive.content).jsonArray.single().jsonObject
        assertEquals("rpc://playlist/import?text=%E6%AD%8C%E5%90%8D%20-%20%E6%AD%8C%E6%89%8B%0A%E7%AC%AC%E4%BA%8C%E9%A6%96", song["url"]!!.jsonPrimitive.content)
        enqueue("""{"code":200,"data":{"taskId":2}}""")
        repository.submitLocal(listOf(Song(-1, "歌曲", "歌手", "专辑", localUri = "content://private/audio")), false)
        val (request, local) = take()
        assertEquals("/eapi/playlist/import/name/task/create", request.path)
        val metadata = Json.parseToJsonElement(local["multiSongs"]!!.jsonPrimitive.content).jsonArray.single().jsonObject
        assertEquals(setOf("songName", "artistName", "albumName"), metadata.keys)
        assertFalse(local.toString().contains("content://"))
    }

    @Test fun invalidAndUnsupportedLinksCannotCreateTasks() = runBlocking {
        login()
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.submitLinks(List(11) { "https://example.com/$it" }, false) } }
        assertEquals(0, server.requestCount)
        enqueue("""{"code":200,"data":null}""")
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.submitLinks(listOf("https://example.com/a"), false) } }
        assertEquals(1, server.requestCount)
    }

    @Test fun expiredLoginIsMarkedStaleAndNeverRetriedAutomatically() = runBlocking {
        login()
        enqueue("""{"code":301,"message":"登录过期"}""")
        val result = appResult { repository.submitText("歌曲", false) }
        assertEquals(ErrorKind.NeteaseBindingRequired, (result as AppResult.Failure).kind)
        assertTrue(sessions.state.value.stale)
        appResult { repository.submitText("歌曲", false) }
        assertEquals(1, server.requestCount)
    }

    @Test fun accountSwitchStopsOldTaskQueriesBeforeTheNetwork() = runBlocking {
        login()
        enqueue("""{"code":200,"data":{"taskId":99}}""")
        val task = repository.submitText("歌曲", false)
        login(43)
        val result = appResult { repository.status(task) }
        assertEquals(ErrorKind.NeteaseSessionChanged, (result as AppResult.Failure).kind)
        assertEquals(1, server.requestCount)
    }

    @Test fun sharedTextAndUnicodeUseEncodeUriRatherThanFormEncoding() {
        assertEquals("https://example.com/a", extractImportLink("分享 https://example.com/a。"))
        assertEquals("%E6%AD%8C%20&%20%E6%9B%B2%25", encodeImportUri("歌 & 曲%"))
    }

    @Test fun officialStringStatusesPreservePartialFailureAndEmptyPendingResults() = runBlocking {
        login()
        enqueue("""{"code":200,"data":{"taskId":99}}""")
        val task = repository.submitText("歌曲", false)
        take()
        for (state in listOf("IMPORTING", "COMPLETE", "FAILED", "PLAYLIST_DELETE", "PLAYLIST_FULL_SOME_SUCCESS",
            "PLAYLIST_FULL_NONE_SUCCESS", "RECOGNIZE_NO_SONGS", "UNKNOWN")) {
            enqueue("""{"code":200,"data":{"taskProcess":"progress","tasks":[{"id":99,"playlistId":123,"status":"$state","msg":""}]}}""")
            val status = repository.status(task)
            assertEquals(state !in setOf("IMPORTING", "UNKNOWN"), status.finished)
            assertEquals(state in setOf("COMPLETE", "PLAYLIST_FULL_SOME_SUCCESS"), status.successful)
            assertEquals(listOf(123L), status.playlistIds)
            val (request, payload) = take()
            assertEquals("/eapi/playlist/import/task/status/v2", request.path)
            assertEquals("[99]", payload["taskIds"]!!.jsonPrimitive.content)
        }
        enqueue("""{"code":200,"data":{"tasks":[]}}""")
        assertFalse(repository.status(task).finished)
    }

    @Test fun imageImportUsesScopedNosTokenAndCreatesRecognitionOnlyAfterCompleteUpload() = runBlocking {
        login()
        enqueue("""{"code":200,"playlist":[{"id":123,"name":"我的歌单","creator":{"userId":42}}]}""")
        enqueue("""{"code":200,"data":{"objectKey":"folder/image.png","token":"scoped-fixture"}}""")
        enqueue("""{"offset":4,"context":"complete"}""")
        enqueue("""{"code":200,"data":{"taskId":999}}""")
        val bytes = byteArrayOf(1, 2, 3, 4)
        val task = repository.submitImages(listOf(PlaylistImportImage(bytes, "image.png", "image/png")), 123)
        assertEquals(999L, task.id)
        take()
        val (allocation, params) = take()
        assertEquals("/eapi/nos/token/whalealloc", allocation.path)
        assertEquals("yyimgs", params["bucket"]!!.jsonPrimitive.content)
        assertEquals("64e97e4d", params["bizKey"]!!.jsonPrimitive.content)
        val upload = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("/yyimgs/folder/image.png?offset=0&complete=true&version=1.0", upload.path)
        assertNull(upload.getHeader("Cookie")); assertNull(upload.getHeader("Authorization"))
        assertEquals("scoped-fixture", upload.getHeader("x-nos-token"))
        assertArrayEquals(bytes, upload.body.readByteArray())
        val (create, payload) = take()
        assertEquals("/eapi/playlist/import/create", create.path)
        assertEquals("[\"yyimgs/folder/image.png\"]", payload["picIds"]!!.jsonPrimitive.content)
        assertEquals(123L, payload["playlistId"]!!.jsonPrimitive.long)
    }

    @Test fun unconfirmedImageUploadNeverCreatesAnImportTask() = runBlocking {
        login()
        enqueue("""{"code":200,"playlist":[{"id":123,"name":"我的歌单","creator":{"userId":42}}]}""")
        enqueue("""{"code":200,"data":{"objectKey":"image.png","token":"scoped-fixture"}}""")
        enqueue("""{"offset":0}""")
        val result = appResult { repository.submitImages(listOf(PlaylistImportImage(byteArrayOf(1), "image.png", "image/png")), 123) }
        assertTrue(result is AppResult.Failure)
        assertEquals(3, server.requestCount)
    }
}
