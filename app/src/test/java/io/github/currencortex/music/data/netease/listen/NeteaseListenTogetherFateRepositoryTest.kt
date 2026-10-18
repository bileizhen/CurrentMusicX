package io.github.currencortex.music.data.netease.listen

import io.github.currencortex.music.core.netease.*
import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.core.network.ErrorKind
import io.github.currencortex.music.core.security.TokenStore
import io.github.currencortex.music.data.binding.BoundProfile
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.*
import org.junit.Assert.*
import java.net.URLDecoder

class NeteaseListenTogetherFateRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private lateinit var sessions: NeteaseSessionStore
    private lateinit var repository: NeteaseListenTogetherFateRepository

    @Before fun setup() = runBlocking {
        server = MockWebServer().apply { start() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val vault = object : TokenStore {
            private var value: String? = null
            override fun read() = value
            override fun write(token: String) { value = token }
            override fun clear() { value = null }
        }
        sessions = NeteaseSessionStore(vault, scope).also { it.ready.await() }
        sessions.login(mapOf("MUSIC_U" to "fixture-local-only"), BoundProfile(100, "自己", ""), sessions.snapshot())
        val url = server.url("/").toString()
        repository = NeteaseListenTogetherFateRepository(NeteaseTransport(sessions, NeteaseEndpoints(url, url, url)))
    }

    @After fun cleanup() { scope.cancel(); server.shutdown() }
    private fun response(data: String) = server.enqueue(MockResponse().setBody("""{"code":200,"data":$data}""")
        .setHeader("Content-Type", "application/json"))
    private fun member(vip: Boolean = true) = response("""{"vip":$vip,"vipType":11,"records":[]}""")
    private fun report() = """{"selfAvatar":"http://p1.music.126.net/self.jpg","partnerAvatar":"http://p1.music.126.net/peer.jpg","selfNick":"自己","partnerNick":"朋友","totalDuration":2319772000,"totalSongNum":1389,"commonRedSongNum":20,"startListenTime":1700000000000,"startListenDayNum":300,"lastestTime":1700086400000,"mostPopSongName":"共同听过的歌","longestDuration":492000000,"vipType":11,"ltType":1,"canInvite":true,"openAct":true}"""
    private fun payload(request: RecordedRequest): JsonObject {
        val encoded = request.body.readUtf8().split('&').associate {
            it.split('=', limit = 2).let { parts -> parts[0] to URLDecoder.decode(parts[1], "UTF-8") }
        }.getValue("params")
        val bytes = encoded.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return ApiJson.parseToJsonElement(NeteaseCrypto.decryptResponse(bytes).toString(Charsets.UTF_8)
            .split("-36cd479b6b5-")[1]).jsonObject
    }

    @Test fun officialFateReadKeepsMillisecondDurationsAndTimestampsAndOnlyUsesBoundAccount() = runBlocking {
        val expected = repository.captureSession()
        member(); response(report())
        val result = repository.fate(expected, 200, 1)
        assertEquals(100L, result.self.userId)
        assertEquals(200L, result.peer.userId)
        assertEquals("https://p1.music.126.net/peer.jpg", result.peer.avatarUrl)
        assertEquals(2319772000L, result.totalDurationMs)
        assertEquals(492000000L, result.longestDurationMs)
        assertEquals(1700000000000L, result.firstListenTimeMs)
        assertEquals(1700086400000L, result.latestLateNightTimeMs)
        assertEquals(300L, result.daysSinceFirstListen)
        assertEquals(1389L, result.totalSongCount)
        assertEquals(20L, result.commonRedSongCount)
        assertTrue(result.openActivity)
        assertTrue(result.canInvite)
        assertEquals("共同听过的歌", result.mostPlayedSongName)
        val membership = server.takeRequest()
        assertEquals("/eapi/listen/together/full/records", membership.path)
        assertEquals("{\"size\":50,\"cursor\":\"\"}", payload(membership)["page"]!!.jsonPrimitive.content)
        val request = server.takeRequest()
        assertEquals("/eapi/listen/together/single/record/new", request.path)
        val body = payload(request)
        assertEquals(200L, body["partnerUid"]!!.jsonPrimitive.long)
        assertEquals(1, body["scene"]!!.jsonPrimitive.int)
        assertEquals("android", body["header"]!!.jsonObject["os"]!!.jsonPrimitive.content)
        assertNull(request.getHeader("Authorization"))
        assertTrue(result.officialUrl().contains("partnerUid=200&scene=1"))
        assertEquals(2, server.requestCount)
    }

    @Test fun missingMaskedAndNegativeStatisticsNeverBecomeFabricatedZeroes() = runBlocking {
        member(); response("""{"ltType":1,"totalDuration":"*","totalSongNum":null,"commonRedSongNum":0,"startListenDayNum":-1,"startListenTime":0,"lastestTime":null,"longestDuration":"*"}""")
        val result = repository.fate(repository.captureSession(), 200)
        assertNull(result.totalDurationMs)
        assertNull(result.totalSongCount)
        assertEquals(0L, result.commonRedSongCount)
        assertNull(result.daysSinceFirstListen)
        assertNull(result.firstListenTimeMs)
        assertNull(result.latestLateNightTimeMs)
        assertNull(result.longestDurationMs)
        assertEquals("", result.mostPlayedSongName)
    }

    @Test fun anonymousFateMasksBothAvatarsAndPeerName() = runBlocking {
        member(); response("""{"ltType":3,"selfNick":"自己","partnerNick":"不可泄露的名字","selfAvatar":"https://p1.music.126.net/realself.jpg","partnerAvatar":"https://p1.music.126.net/realpeer.jpg","newUnmaskFlag":false,"matchPlayInfo":{"maskDisplay":{"peerDisplayName":"神秘听友","selfMaskAsset":{"assetUrl":"http://p1.music.126.net/selfmask.jpg"},"peerMaskAsset":{"assetUrl":"http://p1.music.126.net/peermask.jpg"}}}}""")
        val result = repository.fate(repository.captureSession(), 200, 3)
        assertTrue(result.self.masked)
        assertTrue(result.peer.masked)
        assertEquals("神秘听友", result.peer.nickname)
        assertEquals("https://p1.music.126.net/peermask.jpg", result.peer.avatarUrl)
        assertEquals("https://p1.music.126.net/selfmask.jpg", result.self.avatarUrl)
    }

    @Test fun membershipIsFreshlyCheckedForEveryReadAndWrite() = runBlocking {
        val expected = repository.captureSession()
        member(false)
        assertEquals(ErrorKind.Forbidden, (runCatching { repository.fate(expected, 200, 1) }.exceptionOrNull() as ApiException).kind)
        member(false)
        assertEquals(ErrorKind.Forbidden, (runCatching { repository.deleteFate(expected, 200, 1) }.exceptionOrNull() as ApiException).kind)
        assertEquals(2, server.requestCount)
        repeat(2) { assertEquals("/eapi/listen/together/full/records", server.takeRequest().path) }
    }

    @Test fun explicitDeleteOnlyRemovesFateByDefaultAndUsesOfficialPayload() = runBlocking {
        member(); response("true")
        repository.deleteFate(repository.captureSession(), 200, 1)
        server.takeRequest()
        val request = server.takeRequest()
        assertEquals("/eapi/listen/together/rank/delete", request.path)
        val body = payload(request)
        assertFalse(body["recordDel"]!!.jsonPrimitive.boolean)
        assertEquals(200L, body["partnerUid"]!!.jsonPrimitive.long)
        assertEquals(1, body["ltType"]!!.jsonPrimitive.int)
    }

    @Test fun deletingHistoryNeedsAnExplicitSeparateTrueOption() = runBlocking {
        member(); response("{}")
        repository.deleteFate(repository.captureSession(), 200, 1, deleteRecords = true)
        server.takeRequest()
        assertTrue(payload(server.takeRequest())["recordDel"]!!.jsonPrimitive.boolean)
    }

    @Test fun rejectedWriteAndBackendPermissionDenialAreNotSuccessfulDeletion() = runBlocking {
        val expected = repository.captureSession()
        member(); response("false")
        assertEquals(ErrorKind.Server, (runCatching { repository.deleteFate(expected, 200, 1) }.exceptionOrNull() as ApiException).kind)
        member(); server.enqueue(MockResponse().setBody("""{"code":403,"message":"membership expired"}"""))
        assertEquals(ErrorKind.Forbidden, (runCatching { repository.deleteFate(expected, 200, 1) }.exceptionOrNull() as ApiException).kind)
    }

    @Test fun emptyReportIsDeletedWhileMissingOrMismatchedIdentityCannotBeAttached() = runBlocking {
        val expected = repository.captureSession()
        member(); response("{}")
        assertEquals(ErrorKind.NotFound, (runCatching { repository.fate(expected, 200) }.exceptionOrNull() as ApiException).kind)
        member(); response("""{"partnerUid":300,"ltType":1}""")
        assertEquals(ErrorKind.Parse, (runCatching { repository.fate(expected, 200) }.exceptionOrNull() as ApiException).kind)
        member(); response("""{"totalDuration":123}""")
        assertEquals(ErrorKind.Parse, (runCatching { repository.fate(expected, 200) }.exceptionOrNull() as ApiException).kind)
    }

    @Test fun changedOrMissingAccountNeverIssuesDeleteForNewAccount() = runBlocking {
        val expected = repository.captureSession()
        sessions.clear()
        assertEquals(ErrorKind.NeteaseSessionChanged,
            (runCatching { repository.deleteFate(expected, 200, 1) }.exceptionOrNull() as ApiException).kind)
        assertEquals(ErrorKind.NeteaseBindingRequired,
            (runCatching { repository.captureSession() }.exceptionOrNull() as ApiException).kind)
        assertEquals(0, server.requestCount)
    }

    @Test fun invalidTargetsDoNotIssueAnyRequests() = runBlocking {
        val expected = repository.captureSession()
        assertTrue(runCatching { repository.fate(expected, 0) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { repository.fate(expected, 200, -1) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { repository.deleteFate(expected, 200, 0) }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, server.requestCount)
    }
}
