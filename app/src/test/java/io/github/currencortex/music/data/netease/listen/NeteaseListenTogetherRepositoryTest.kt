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
import java.util.concurrent.TimeUnit

class NeteaseListenTogetherRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private lateinit var sessions: NeteaseSessionStore
    private lateinit var repository: NeteaseListenTogetherRepository

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
        sessions.login(mapOf("MUSIC_U" to "fixture-local-only"), BoundProfile(100, "自己", "https://p1.music.126.net/self.jpg"), sessions.snapshot())
        val url = server.url("/").toString()
        repository = NeteaseListenTogetherRepository(NeteaseTransport(sessions, NeteaseEndpoints(url, url, url)))
    }

    @After fun cleanup() { scope.cancel(); server.shutdown() }

    private fun response(body: String) = server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/json"))
    private fun room() = """{"roomId":"room-100","creatorId":100,"roomType":"FRIEND","roomUsers":[{"userId":100,"nickname":"自己","avatarUrl":"http://p1.music.126.net/self.jpg"},{"userId":200,"nickname":"朋友","avatarUrl":"https://p2.music.126.net/friend.jpg"}]}"""
    private fun roomResponse() = """{"code":200,"data":{"type":"CREATE_SUCCESS","roomInfo":${room()}}}"""
    private fun payload(request: RecordedRequest): JsonObject {
        val encoded = request.body.readUtf8().split('&').associate {
            it.split('=', limit = 2).let { parts -> parts[0] to URLDecoder.decode(parts[1], "UTF-8") }
        }.getValue("params")
        val bytes = encoded.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return ApiJson.parseToJsonElement(NeteaseCrypto.decryptResponse(bytes).toString(Charsets.UTF_8)
            .split("-36cd479b6b5-")[1]).jsonObject
    }

    @Test fun createAndAcceptUseOriginalAndroidRoutesAndReturnBothAccountAvatars() = runBlocking {
        val expected = repository.captureSession()
        response(roomResponse())
        val created = repository.create(expected)
        assertEquals("room-100", created.roomId)
        assertEquals(listOf(100L, 200L), created.participants.map { it.userId })
        assertEquals("https://p1.music.126.net/self.jpg", created.participants.first().avatarUrl)
        val create = server.takeRequest()
        assertEquals("/eapi/listen/together/room/create", create.path)
        assertNull(create.getHeader("Authorization"))
        val data = payload(create)
        assertEquals("android", data["header"]!!.jsonObject["os"]!!.jsonPrimitive.content)
        assertEquals("fixture-local-only", data["header"]!!.jsonObject["MUSIC_U"]!!.jsonPrimitive.content)
        assertEquals("", data["robotUid"]!!.jsonPrimitive.content)

        response(roomResponse())
        repository.accept(expected, ListenTogetherInvitation("room-100", 100))
        val accept = server.takeRequest()
        assertEquals("/eapi/listen/together/play/invitation/accept", accept.path)
        assertEquals("100", payload(accept)["inviterId"]!!.jsonPrimitive.content)
        assertEquals(2, server.requestCount)
    }

    @Test fun alreadyInAnotherRoomOrMismatchedInvitationNeverAttachesOldRoom() = runBlocking {
        val expected = repository.captureSession()
        response("""{"code":200,"data":{"type":"ALREADY_IN_ROOM","roomInfo":${room()}}}""")
        assertTrue(runCatching { repository.create(expected) }.exceptionOrNull() is IllegalStateException)
        response("""{"code":200,"data":{"type":"ALREADY_IN_ROOM","roomInfo":${room()}}}""")
        assertTrue(runCatching { repository.accept(expected, ListenTogetherInvitation("room-200", 200)) }.exceptionOrNull() is IllegalStateException)
        response(roomResponse())
        assertTrue(runCatching { repository.accept(expected, ListenTogetherInvitation("room-200", 200)) }.exceptionOrNull() is IllegalStateException)
    }

    @Test fun heartbeatIsLivenessWhileRecoverySnapshotContainsRemoteCommandAndQueue() = runBlocking {
        val expected = repository.captureSession()
        response("""{"code":200,"data":{"result":true,"timeSpan":30,"time":1700000000000}}""")
        assertTrue(repository.heartbeat(expected, "room-100", 5, true, 12345, listOf(ListenTogetherVersion(100, 10), ListenTogetherVersion(200, 20))))
        val beat = payload(server.takeRequest())
        assertEquals("PLAY", beat["playStatus"]!!.jsonPrimitive.content)
        assertEquals("12345", beat["progress"]!!.jsonPrimitive.content)
        assertEquals("[{\"userId\":100,\"version\":10,\"outerId\":null},{\"userId\":200,\"version\":20,\"outerId\":null}]", beat["playlistVersion"]!!.jsonPrimitive.content)

        response("""{"code":200,"data":{"playlist":{"playMode":"ORDER_LOOP","version":[{"userId":100,"version":11,"outerId":null},{"userId":200,"version":21,"outerId":"peer"}],"displayList":{"changed":true,"result":["5",6]},"randomList":{"changed":false}},"playCommand":{"commandType":"PROGRESS","targetSongId":"5","formerSongId":"4","progress":15000,"playStatus":"PAUSE","clientSeq":9,"serverSeq":50,"userId":200}}}""")
        val snapshot = repository.playlist(expected, "room-100", ListenTogetherQueue(randomSongIds = listOf(6, 5)))
        assertEquals(listOf(5L, 6L), snapshot.queue.songIds)
        assertEquals(listOf(6L, 5L), snapshot.queue.randomSongIds)
        assertEquals(listOf(ListenTogetherVersion(100, 11), ListenTogetherVersion(200, 21, "peer")), snapshot.queue.version)
        assertEquals(50L, snapshot.playback!!.serverSeq)
        assertEquals(15000L, snapshot.playback.positionMs)
        assertFalse(snapshot.playback.playing)
        assertEquals("/eapi/listen/together/sync/playlist/get", server.takeRequest().path)
    }

    @Test fun explicitCommandsSerializeNestedStringsAndIndependentSequences() = runBlocking {
        val expected = repository.captureSession()
        response("""{"code":200,"data":{"result":true}}""")
        repository.reportPlayback(expected, "room-100", ListenTogetherPlayback("GOTO", 88, 77, true, 9000, 3))
        val playRequest = server.takeRequest()
        assertEquals("/eapi/listen/together/play/command/report", playRequest.path)
        val command = ApiJson.parseToJsonElement(payload(playRequest)["commandInfo"]!!.jsonPrimitive.content).jsonObject
        assertEquals("GOTO", command["commandType"]!!.jsonPrimitive.content)
        assertEquals("88", command["targetSongId"]!!.jsonPrimitive.content)
        assertEquals("MANUAL", command["triggerType"]!!.jsonPrimitive.content)
        assertEquals(3, command["clientSeq"]!!.jsonPrimitive.int)
        assertFalse(command.containsKey("serverSeq"))

        response("""{"code":200,"data":{"result":true}}""")
        repository.reportQueue(expected, "room-100", ListenTogetherQueue(listOf(88, 99), anchorSongId = 88, version = listOf(ListenTogetherVersion(100, 1), ListenTogetherVersion(200, 2)), clientSeq = 4))
        val queueRequest = server.takeRequest()
        assertEquals("/eapi/listen/together/sync/list/command/report", queueRequest.path)
        val outer = payload(queueRequest)
        val queue = ApiJson.parseToJsonElement(outer["playlistParam"]!!.jsonPrimitive.content).jsonObject
        assertEquals("4", outer["clientSeq"]!!.jsonPrimitive.content)
        assertEquals("REPLACE", queue["commandType"]!!.jsonPrimitive.content)
        assertEquals("88", queue["displayList"]!!.jsonArray.first().jsonPrimitive.content)
        assertEquals(listOf(1L, 2L), queue["version"]!!.jsonArray.map { it.jsonObject["version"]!!.jsonPrimitive.long })
    }

    @Test fun emptySharedQueueUsesReplaceWithoutEndingTheRoom() = runBlocking {
        val expected = repository.captureSession()
        response("""{"code":200,"data":{"result":true}}""")
        repository.reportQueue(expected, "room-100", ListenTogetherQueue(clientSeq = 5,
            version = listOf(ListenTogetherVersion(100, 2))))
        val request = server.takeRequest()
        assertEquals("/eapi/listen/together/sync/list/command/report", request.path)
        val queue = ApiJson.parseToJsonElement(payload(request)["playlistParam"]!!.jsonPrimitive.content).jsonObject
        assertEquals("REPLACE", queue["commandType"]!!.jsonPrimitive.content)
        assertTrue(queue["displayList"]!!.jsonArray.isEmpty())
        assertEquals("0", queue["anchorSongId"]!!.jsonPrimitive.content)
        assertEquals(1, server.requestCount)
    }

    @Test fun restoringOnAnotherDeviceRegistersItBeforeAcceptingCommands() = runBlocking {
        val expected = repository.captureSession()
        response("""{"code":200,"data":{"roomId":"room-100","mainDevice":false,"canReconnect":true}}""")
        response("""{"code":200,"data":{"result":true}}""")
        repository.restoreDevice(expected, "room-100")
        assertEquals("/eapi/listen/together/restore/reconnect/info", server.takeRequest().path)
        val notice = server.takeRequest()
        assertEquals("/eapi/listen/together/device/reconnect/notice", notice.path)
        assertEquals("room-100", payload(notice)["roomId"]!!.jsonPrimitive.content)
    }

    @Test fun restoringCurrentDeviceDoesNotStealAnotherSessionOrSendAnUnnecessaryNotice() = runBlocking {
        val expected = repository.captureSession()
        response("""{"code":200,"data":{"roomId":"room-100","mainDevice":true}}""")
        repository.restoreDevice(expected, "room-100"); server.takeRequest()
        assertEquals(1, server.requestCount)
        response("""{"code":200,"data":{"roomId":"other-room","mainDevice":false,"canReconnect":true}}""")
        assertTrue(runCatching { repository.restoreDevice(expected, "room-100") }.isFailure)
        server.takeRequest()
        assertEquals(2, server.requestCount)
    }

    @Test fun httpSuccessWithoutACommandAcknowledgementIsNotAccepted() = runBlocking {
        val expected = repository.captureSession()
        response("""{"code":200,"data":{}}""")
        assertTrue(runCatching { repository.reportPlayback(expected, "room-100",
            ListenTogetherPlayback("NEXT", 20, 10, true, clientSeq = 1)) }.isFailure)
    }

    @Test fun recommendationUsesNativeIntegerStatusAndPreservesServerPermissionMessage() = runBlocking {
        val expected = repository.captureSession()
        response("""{"code":200,"data":{"success":true,"refreshPlaylist":true}}""")
        val enabled = repository.setRecommendation(expected, "room-100", true)
        assertTrue(enabled.success); assertTrue(enabled.refreshPlaylist)
        val request = server.takeRequest()
        assertEquals("/eapi/listen/together/heart/rcmd/change", request.path)
        assertEquals(1, payload(request)["status"]!!.jsonPrimitive.int)
        response("""{"code":200,"data":{"success":false,"toast":"需要会员权限"}}""")
        val rejected = repository.setRecommendation(expected, "room-100", false)
        assertFalse(rejected.success); assertEquals("需要会员权限", rejected.message)
    }

    @Test fun statusAndLeaveDoNotInventAJoinRouteOrOpenExternalMessages() = runBlocking {
        val expected = repository.captureSession()
        response("""{"code":200}""")
        repository.requestSync(expected, "room-100")
        val notice = server.takeRequest()
        assertEquals("/eapi/listen/together/sync/notice", notice.path)
        assertEquals("room-100", payload(notice)["roomId"]!!.jsonPrimitive.content)
        response("""{"code":200,"data":{"inRoom":true,"status":"PLAY","roomInfo":${room()}}}""")
        assertEquals("room-100", repository.status(expected).room!!.roomId)
        assertEquals("/eapi/listen/together/status/get", server.takeRequest().path)
        response("""{"code":200,"data":{"success":true}}""")
        repository.leave(expected, "room-100")
        val leave = server.takeRequest()
        assertEquals("/eapi/listen/together/end/v2", leave.path)
        val leaveData = payload(leave)
        assertFalse(leaveData["needRecord"]!!.jsonPrimitive.boolean)
        assertEquals("NORMAL_END", leaveData["exitType"]!!.jsonPrimitive.content)
        response("""{"code":200,"data":{"inRoom":false}}""")
        assertFalse(repository.status(expected).inRoom)
    }

    @Test fun logoutOrSwitchNeverSendsAnOldRoomActionWithNewAccountCredentials() = runBlocking {
        val expected = repository.captureSession()
        sessions.clear()
        val error = try { repository.create(expected); null } catch (failure: ApiException) { failure }
        assertEquals(ErrorKind.NeteaseSessionChanged, error?.kind)
        val noLogin = try { repository.captureSession(); null } catch (failure: ApiException) { failure }
        assertEquals(ErrorKind.NeteaseBindingRequired, noLogin?.kind)
        assertEquals(0, server.requestCount)
    }

    @Test fun changingAccountWhileARequestIsInFlightDropsItsLateResponse() = runBlocking {
        val expected = repository.captureSession()
        server.enqueue(MockResponse().setBody(roomResponse()).setBodyDelay(500, TimeUnit.MILLISECONDS))
        val pending = async { runCatching { repository.create(expected) } }
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(3, TimeUnit.SECONDS) })
        sessions.clear()
        assertEquals(ErrorKind.NeteaseSessionChanged, (pending.await().exceptionOrNull() as ApiException).kind)
    }

    @Test fun invalidRoomAndMalformedSnapshotAreNotSilentlyTreatedAsPlaying() = runBlocking {
        val expected = repository.captureSession()
        response("""{"code":488,"message":"room invalid"}""")
        val invalid = runCatching { repository.status(expected) }.exceptionOrNull() as ApiException
        assertEquals(ErrorKind.NotFound, invalid.kind)
        response("""{"code":200,"data":{"playlist":{"displayList":{"changed":true,"result":["bad-id"]}}}}""")
        val malformed = runCatching { repository.playlist(expected, "room-100") }.exceptionOrNull() as ApiException
        assertEquals(ErrorKind.Parse, malformed.kind)
        response("""{"code":200,"data":{"result":false,"message":"rejected"}}""")
        val rejected = runCatching {
            repository.reportPlayback(expected, "room-100", ListenTogetherPlayback("PLAY", targetSongId = 88, playing = true))
        }.exceptionOrNull() as ApiException
        assertEquals(ErrorKind.Server, rejected.kind)
        response("""{"code":200,"data":{"result":false}}""")
        assertFalse(repository.heartbeat(expected, "room-100", 88, false, 0, emptyList()))
    }

    @Test fun invitationsUseOriginalShareFieldsAndRejectUntrustedOrAmbiguousLinks() {
        val invitation = ListenTogetherInvitation("room-100", 100)
        val url = invitation.shareUrl(88)
        assertEquals(invitation, ListenTogetherInvitation.parse("一起听 $url"))
        assertEquals(invitation, ListenTogetherInvitation.parse("orpheus://nm/play/listenTogether?roomId=room-100&inviterId=100"))
        assertNull(ListenTogetherInvitation.parse("https://evil.invalid/?roomId=room-100&inviterId=100"))
        assertNull(ListenTogetherInvitation.parse("https://st.music.163.com/?roomId=room-100&roomId=other&inviterId=100"))
        assertNull(ListenTogetherInvitation.parse("roomId=room-100&inviterId=0"))
        assertNull(ListenTogetherInvitation.parse("roomId=../other&inviterId=100"))
        assertEquals(0, server.requestCount)
    }
}
