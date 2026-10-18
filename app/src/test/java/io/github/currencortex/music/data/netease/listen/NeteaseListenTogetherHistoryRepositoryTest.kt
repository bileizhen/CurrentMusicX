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

class NeteaseListenTogetherHistoryRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private lateinit var sessions: NeteaseSessionStore
    private lateinit var repository: NeteaseListenTogetherHistoryRepository

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
        repository = NeteaseListenTogetherHistoryRepository(NeteaseTransport(sessions, NeteaseEndpoints(url, url, url)))
    }

    @After fun cleanup() { scope.cancel(); server.shutdown() }

    private fun response(data: String) = server.enqueue(MockResponse().setBody("""{"code":200,"data":$data}""")
        .setHeader("Content-Type", "application/json"))
    private fun payload(request: RecordedRequest): JsonObject {
        val encoded = request.body.readUtf8().split('&').associate {
            it.split('=', limit = 2).let { parts -> parts[0] to URLDecoder.decode(parts[1], "UTF-8") }
        }.getValue("params")
        val bytes = encoded.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return ApiJson.parseToJsonElement(NeteaseCrypto.decryptResponse(bytes).toString(Charsets.UTF_8)
            .split("-36cd479b6b5-")[1]).jsonObject
    }

    private fun row(id: String = "1") = """{"id":"$id","roomId":"room-100","userId":200,"nick":"朋友","avatar":"http://p1.music.126.net/f.jpg","date":"今天","startTime":1700000000000,"duration":1800000,"totalSong":13,"online":true,"canInvite":true,"ltType":1}"""

    @Test fun officialHistoryUsesStringifiedPaginationAndKeepsServerUnitsAndOrder() = runBlocking {
        val expected = repository.captureSession()
        response("""{"vip":true,"vipType":11,"records":[${row("2")},${row("1")}],"hasMore":true,"nextCursor":"cursor-2","mostListen":{"friendUid":200,"friendNick":"朋友","friendAvatar":"http://p1.music.126.net/f.jpg","selfAvatar":"http://p1.music.126.net/s.jpg","totalDuration":2329320000,"totalSong":1389}}""")
        val page = repository.history(expected)
        assertEquals(listOf("2", "1"), page.records.map { it.id })
        assertEquals(1800000L, page.records.first().durationMs)
        assertEquals(13L, page.records.first().songCount)
        assertEquals("https://p1.music.126.net/f.jpg", page.records.first().user.avatarUrl)
        assertEquals(2329320000L, page.mostListen!!.totalDurationMs)
        assertTrue(page.hasMore)
        val request = server.takeRequest()
        assertEquals("/eapi/listen/together/full/records", request.path)
        val body = payload(request)
        assertTrue(body["page"]!!.jsonPrimitive.isString)
        val sentPage = ApiJson.parseToJsonElement(body["page"]!!.jsonPrimitive.content).jsonObject
        assertEquals(50, sentPage["size"]!!.jsonPrimitive.int)
        assertEquals("", sentPage["cursor"]!!.jsonPrimitive.content)
        assertEquals("android", body["header"]!!.jsonObject["os"]!!.jsonPrimitive.content)
        assertNull(request.getHeader("Authorization"))
    }

    @Test fun nonMemberHistoryHidesLockedLastRowAndMostListenNumbers() = runBlocking {
        response("""{"vip":false,"records":[${row("visible")},${row("locked")}],"hasMore":true,"nextCursor":"must-not-use","mostListen":{"friendUid":200,"totalDuration":123456,"totalSong":99}}""")
        val page = repository.history(repository.captureSession())
        assertEquals(listOf("visible"), page.records.map { it.id })
        assertNull(page.mostListen!!.totalDurationMs)
        assertNull(page.mostListen!!.totalSongCount)
        assertFalse(page.hasMore)
        assertEquals("", page.nextCursor)
    }

    @Test fun emptyMostListenAndGroupHistoryDoNotBreakTwoPersonHistory() = runBlocking {
        response("""{"vip":true,"mostListen":{},"records":[{"id":"group","recordRoomType":"multi_room","multiGroupTo":{"groupMembers":[{"uid":300}]}},${row()}]}""")
        val page = repository.history(repository.captureSession())
        assertNull(page.mostListen)
        assertEquals(listOf("1"), page.records.map { it.id })
    }

    @Test fun rankingAndDeeperHistoryRecheckMembershipBeforeProtectedRoutes() = runBlocking {
        val expected = repository.captureSession()
        response("""{"vip":false,"records":[]}""")
        assertEquals(ErrorKind.Forbidden, (runCatching { repository.ranking(expected) }.exceptionOrNull() as ApiException).kind)
        response("""{"vip":false,"records":[]}""")
        assertEquals(ErrorKind.Forbidden, (runCatching { repository.history(expected, "untrusted-cursor") }.exceptionOrNull() as ApiException).kind)
        assertEquals(2, server.requestCount)
        repeat(2) { assertEquals("/eapi/listen/together/full/records", server.takeRequest().path) }
    }

    @Test fun rankingUsesNativeRankListAndNeverReordersServerRanking() = runBlocking {
        val expected = repository.captureSession()
        response("""{"vip":true,"records":[]}""")
        response("""{"vipType":11,"rankLists":[{"uid":200,"nickname":"第一位","avatar":"http://p1.music.126.net/f.jpg","totalDuration":2329320000,"totalSongCount":1389,"online":true},{"uid":300,"nickname":"第二位","totalDuration":"*","totalSongCount":null}]}""")
        val rank = repository.ranking(expected)
        assertEquals(listOf(200L, 300L), rank.rankings.map { it.user.userId })
        assertEquals(listOf(1, 2), rank.rankings.map { it.rank })
        assertNull(rank.rankings[1].totalDurationMs)
        assertNull(rank.rankings[1].totalSongCount)
        server.takeRequest()
        assertEquals("/eapi/listen/together/rank/list", server.takeRequest().path)
    }

    @Test fun reportUsesReadOnlyRecordRouteAndServerStatisticsRatherThanQueueEstimates() = runBlocking {
        val expected = repository.captureSession()
        response("""{"roomId":"room-100","userInfo":{"userId":100,"nickname":"自己"},"anotherUserInfo":{"userId":200,"nickname":"朋友"},"duration":5940,"songCount":32,"totalDuration":1027920,"totalSongCount":124,"endTime":1700000000000,"canInvite":true,"online":true,"showCollectBtn":true,"roomType":"FRIEND","scene":"MOBILE_FRIEND"}""")
        val report = repository.record(expected, "room-100")
        assertEquals(5940000L, report.durationMs)
        assertEquals(32L, report.songCount)
        assertEquals(1027920000L, report.totalDurationMs)
        assertEquals(124L, report.totalSongCount)
        assertEquals("朋友", report.peer!!.nickname)
        assertTrue(report.showCollectButton)
        assertTrue(report.officialUrl().endsWith("roomId=room-100&nm_style=sbt"))
        val request = server.takeRequest()
        assertEquals("/eapi/listen/together/record/get", request.path)
        assertEquals("room-100", payload(request)["roomId"]!!.jsonPrimitive.content)
        assertEquals(1, server.requestCount)
    }

    @Test fun maskedStatisticsStayMissingAndAnonymousIdentityStaysMasked() = runBlocking {
        val expected = repository.captureSession()
        response("""{"roomId":"room-100","duration":"*","songCount":null,"totalDuration":-1,"anotherUserInfo":{"userId":200,"nickname":"must-not-reveal","avatarUrl":"https://p1.music.126.net/real.jpg"},"newUnmaskFlag":false,"matchPlayInfo":{"maskDisplay":{"peerDisplayName":"匿名朋友","peerMaskAsset":{"assetUrl":"http://p1.music.126.net/mask.jpg"}}}}""")
        val report = repository.record(expected, "room-100")
        assertNull(report.durationMs)
        assertNull(report.songCount)
        assertNull(report.totalDurationMs)
        assertEquals("匿名朋友", report.peer!!.nickname)
        assertEquals("https://p1.music.126.net/mask.jpg", report.peer.avatarUrl)
        assertTrue(report.peer.masked)
    }

    @Test fun reportSongListUsesNativeRoomHistoryRatherThanCurrentPlayerQueue() = runBlocking {
        response("""{"songIds":["440208643",123,440208643],"followers":[]}""")
        assertEquals(listOf(440208643L, 123L), repository.roomSongs(repository.captureSession(), "room-100"))
        assertEquals("/eapi/listen/together/room/songs/list", server.takeRequest().path)
    }

    @Test fun wrongRoomAndAccountSwitchCannotPublishAnotherAccountsReport() = runBlocking {
        val expected = repository.captureSession()
        response("""{"roomId":"room-other"}""")
        assertEquals(ErrorKind.Parse, (runCatching { repository.record(expected, "room-100") }.exceptionOrNull() as ApiException).kind)
        sessions.clear()
        val failure = runCatching { repository.history(expected) }.exceptionOrNull()
        assertEquals(ErrorKind.NeteaseSessionChanged, (failure as ApiException).kind)
        assertEquals(1, server.requestCount)
    }

    @Test fun backendMembershipDenialIsPreserved() = runBlocking {
        response("""{"vip":true,"records":[]}""")
        server.enqueue(MockResponse().setBody("""{"code":403,"message":"VIP required"}"""))
        val failure = runCatching { repository.ranking(repository.captureSession()) }.exceptionOrNull()
        assertEquals(ErrorKind.Forbidden, (failure as ApiException).kind)
    }
}
