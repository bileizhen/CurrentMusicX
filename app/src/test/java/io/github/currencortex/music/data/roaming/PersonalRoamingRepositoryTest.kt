package io.github.currencortex.music.data.roaming

import io.github.currencortex.music.core.netease.*
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.core.security.TokenStore
import io.github.currencortex.music.data.binding.BoundProfile
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.net.URLDecoder

class PersonalRoamingRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val vault = object : TokenStore {
        var value: String? = null
        override fun read() = value
        override fun write(token: String) { value = token }
        override fun clear() { value = null }
    }
    private lateinit var server: MockWebServer
    private lateinit var sessions: NeteaseSessionStore
    private lateinit var repository: PersonalRoamingRepository
    @Before fun setup() = runBlocking {
        server = MockWebServer().apply { start() }
        sessions = NeteaseSessionStore(vault, scope); sessions.ready.await()
        val endpoint = server.url("/").toString()
        repository = PersonalRoamingRepository(NeteaseTransport(sessions, NeteaseEndpoints(endpoint, endpoint, endpoint)))
    }
    @After fun cleanup() { scope.cancel(); server.shutdown() }
    private suspend fun login() = sessions.login(mapOf("MUSIC_U" to "fixture-only"), BoundProfile(7, "test"), sessions.snapshot())
    private fun response(json: String) = server.enqueue(MockResponse().setBody(json))
    private fun payload(request: RecordedRequest): JsonObject {
        val hex = URLDecoder.decode(request.body.readUtf8().substringAfter("params="), "UTF-8")
        val encrypted = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val raw = NeteaseCrypto.decryptResponse(encrypted).toString(Charsets.UTF_8)
        return ApiJson.parseToJsonElement(raw.split("-36cd479b6b5-")[1]).jsonObject
    }
    @Test fun catalogUsesNativeAndroidEndpointAndPreservesServerOrderAndSceneParameters() = runBlocking {
        response("""{"code":200,"data":{"crossPlatformResource":{"dslData":{
            "recommendModeList":[{"code":"DEFAULT","title":"默认模式"},{"code":"PUZZLE_MODE_RCMD","title":"拼图模式","subTitle":"拼封面"}],
            "currentSceneList":[{"code":"RAINY","title":"下雨天","fmCascadeModeStr":"rain"},{"code":"K_POP","title":"K-Pop"}]}}}}""")
        val catalog = repository.catalog()
        assertEquals(listOf("DEFAULT", "PUZZLE_MODE_RCMD"), catalog.modes.map { it.code })
        assertEquals(listOf("RAINY", "K_POP"), catalog.scenes.map { it.code })
        assertEquals("SCENE_RCMD", catalog.scenes.first().mode)
        assertEquals("RAINY", catalog.scenes.first().subMode)
        val request = server.takeRequest()
        assertEquals("/eapi/link/position/show/resource", request.path)
        assertNull(request.getHeader("Authorization"))
        val params = payload(request)
        assertEquals("FMTopModeDialog", params["positionCode"]!!.jsonPrimitive.content)
        assertEquals("android", params["header"]!!.jsonObject["os"]!!.jsonPrimitive.content)
    }
    @Test fun eachModeSendsItsActualCodeAndExcludesAlreadySeenSongs() = runBlocking {
        login()
        for (choice in RoamingCatalog.fallback.modes + RoamingChoice("RAINY", "下雨天", scene = true, cascade = "native")) {
            response("""{"code":200,"data":[{"id":11,"name":"seen"},{"id":12,"name":"new","artists":[{"id":8,"name":"artist"}],"album":{"name":"album","picUrl":"http://p1.music.126.net/cover"}},{"id":12,"name":"duplicate"}]}""")
            val songs = repository.songs(choice, listOf(11))
            assertEquals(listOf(12L), songs.map { it.id })
            assertEquals("artist", songs.single().artists)
            assertTrue(songs.single().cover.startsWith("https:"))
            val request = server.takeRequest()
            assertEquals("/eapi/v1/radio/get", request.path)
            assertNull(request.getHeader("Authorization"))
            val params = payload(request)
            assertEquals(choice.mode, params["mode"]!!.jsonPrimitive.content)
            assertEquals(choice.subMode, params["subMode"]!!.jsonPrimitive.content)
            assertEquals("11", params["unplaySongIds"]!!.jsonPrimitive.content)
            assertEquals(3, params["limit"]!!.jsonPrimitive.int)
            assertFalse(params["openAidj"]!!.jsonPrimitive.boolean)
            if (choice.scene) assertEquals("native", params["fmCascadeModeStr"]!!.jsonPrimitive.content)
        }
    }
    @Test fun missingLoginNeverRequestsRecommendationsAndExpiredLoginIsMarkedStale() = runBlocking {
        try { repository.songs(RoamingCatalog.fallback.modes.first()); fail() }
        catch (e: ApiException) { assertEquals(ErrorKind.NeteaseBindingRequired, e.kind) }
        assertEquals(0, server.requestCount)
        login(); response("""{"code":301,"message":"请登录"}""")
        try { repository.songs(RoamingCatalog.fallback.modes.first()); fail() }
        catch (e: ApiException) { assertEquals(ErrorKind.NeteaseBindingRequired, e.kind) }
        assertTrue(sessions.state.value.stale)
    }
    @Test fun nullAndInvalidSongsAreEmptyAndDoNotBecomeSyntheticTracks() {
        assertTrue(PersonalRoamingRepository.parseSongs(ApiJson.parseToJsonElement("""{"data":null}""").jsonObject).isEmpty())
        assertTrue(PersonalRoamingRepository.parseSongs(ApiJson.parseToJsonElement("""{"data":[{}, {"id":0,"name":"bad"},{"id":7,"name":""}]}""").jsonObject).isEmpty())
    }
}
