package io.github.currencortex.music.core.network

import io.github.currencortex.music.core.config.ServerDefaults
import kotlinx.coroutines.*
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import io.github.currencortex.music.data.profile.*
import io.github.currencortex.music.core.room.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class CurrentMusicServersTest {
    @Test fun allStreamsUseSelectedTransportWithoutChangingAccountIdentity() = runBlocking {
        MockWebServer().use { server ->
            val session = RequestSession(ServerDefaults.URL, "stream-token")
            val route: (String) -> String = { server.url("/cm/").toString() }
            fun stream(body: String) = MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body)
            server.enqueue(stream("event: message\ndata: {\"seq\":1,\"type\":\"message\",\"payload\":{\"id\":7}}\n\n"))
            withTimeout(3000) { ChatEventClient(routeServer = route).events(session, 0).first() }
            val chat = server.takeRequest()
            assertEquals("/cm/live/chat?sse=1&since=0", chat.path)
            assertEquals("Bearer stream-token", chat.getHeader("Authorization"))
            server.enqueue(stream("event: done\ndata: {\"text\":\"回答\"}\n\n"))
            withTimeout(3000) { PlayerAiRepository({ session }, routeServer = route)
                .ask("问题", PostAttachment("song", 7, "歌"), false).toList() }
            val ai = server.takeRequest()
            assertEquals("/cm/ai/live", ai.path); assertEquals("POST", ai.method)
            assertEquals("Bearer stream-token", ai.getHeader("Authorization"))
            server.enqueue(stream("event: snapshot\ndata: {}\n\n"))
            withTimeout(3000) { RoomSseClient(routeServer = route).events("room", 9, "8", session)
                .first { it is RoomSignal.Event } }
            val room = server.takeRequest()
            assertEquals("/cm/live/room/events?since=9&sse=1", room.path)
            assertEquals("8", room.getHeader("Last-Event-ID"))
            assertEquals("Bearer stream-token", room.getHeader("Authorization"))
        }
    }
    @Test fun automaticChoosesHealthyPresetAndKeepsLogicalSessionStable() = runBlocking {
        val router = CurrentMusicServerRouter { address ->
            ServerHealth(ServerStatus.AVAILABLE, if (address == ServerDefaults.CDN) 20 else 200)
        }
        router.configure(CurrentMusicServer.AUTO, ServerDefaults.URL)
        val session = RequestSession(ServerDefaults.URL, "test-token")
        router.refresh("https://custom.example/cm/")
        assertEquals(ServerDefaults.CDN, router.resolve(session.server))
        assertEquals(RequestSession(ServerDefaults.URL, "test-token"), session)
        assertEquals("https://custom.example/cm/", router.resolve("https://custom.example/cm/"))
        assertEquals("https://music.163.com/", router.resolve("https://music.163.com/"))
        router.configure(CurrentMusicServer.MAIN, ServerDefaults.URL)
        assertEquals(ServerDefaults.URL, router.resolve(session.server))
        router.configure(CurrentMusicServer.CDN, ServerDefaults.URL)
        assertEquals(ServerDefaults.CDN, router.resolve(session.server))
        router.configure(CurrentMusicServer.CUSTOM, ServerDefaults.URL)
        assertEquals(ServerDefaults.URL, router.resolve(session.server))
    }

    @Test fun customEvenWhenFastestNeverWinsAutomaticAndTieUsesMain() = runBlocking {
        val router = CurrentMusicServerRouter { address ->
            ServerHealth(ServerStatus.AVAILABLE, if (CurrentMusicServer.builtIn(address)) 100 else 1)
        }
        router.configure(CurrentMusicServer.AUTO, ServerDefaults.URL)
        router.refresh("https://custom.example/cm/")
        assertEquals(ServerDefaults.URL, router.automatic.value)
        assertEquals(listOf(CurrentMusicServer.AUTO, CurrentMusicServer.MAIN, CurrentMusicServer.CDN,
            CurrentMusicServer.CDN_GEEKERTAO, CurrentMusicServer.CDN_CTCEHS, CurrentMusicServer.CUSTOM),
            CurrentMusicServer.entries.toList())
    }

    @Test fun geekertaoPresetParticipatesInAutomaticAndExplicitRoutingWithoutChangingIdentity() = runBlocking {
        val router = CurrentMusicServerRouter { address ->
            ServerHealth(ServerStatus.AVAILABLE, if (address == ServerDefaults.CDN_GEEKERTAO) 10 else 100)
        }
        val session = RequestSession(ServerDefaults.URL, "test-token")
        router.configure(CurrentMusicServer.AUTO, session.server)
        router.refresh()
        assertEquals(ServerDefaults.CDN_GEEKERTAO, router.resolve(session.server))
        assertEquals(CurrentMusicServer.CDN_GEEKERTAO, CurrentMusicServer.infer("https://currentmusic.geekertao.com/cm"))
        assertTrue(CurrentMusicServer.builtIn(ServerDefaults.CDN_GEEKERTAO))
        router.configure(CurrentMusicServer.CDN_GEEKERTAO, session.server)
        assertEquals(ServerDefaults.CDN_GEEKERTAO, router.resolve(session.server))
        assertEquals(ServerDefaults.CDN, router.resolve(ServerDefaults.CDN))
        assertEquals(RequestSession(ServerDefaults.URL, "test-token"), session)
        router.configure(CurrentMusicServer.MAIN, ServerDefaults.CDN_GEEKERTAO)
        assertEquals(ServerDefaults.URL, router.resolve(ServerDefaults.CDN_GEEKERTAO))
    }

    @Test fun ctcehsPresetParticipatesInAutomaticAndExplicitRoutingWithoutChangingOtherAccounts() = runBlocking {
        val router=CurrentMusicServerRouter {address->
            ServerHealth(ServerStatus.AVAILABLE,if(address==ServerDefaults.CDN_CTCEHS)10 else 100)
        }
        router.configure(CurrentMusicServer.AUTO,ServerDefaults.URL)
        router.refresh()
        assertEquals(ServerDefaults.CDN_CTCEHS,router.resolve(ServerDefaults.URL))
        assertEquals(CurrentMusicServer.CDN_CTCEHS,CurrentMusicServer.infer("https://music.ctcehs.com/cm"))
        router.configure(CurrentMusicServer.CDN_CTCEHS,ServerDefaults.URL)
        assertEquals(ServerDefaults.CDN_CTCEHS,router.resolve(ServerDefaults.URL))
        assertEquals(ServerDefaults.CDN,router.resolve(ServerDefaults.CDN))
        assertEquals("https://private.example/cm/",router.resolve("https://private.example/cm/"))
        router.configure(CurrentMusicServer.MAIN,ServerDefaults.CDN_CTCEHS)
        assertEquals(ServerDefaults.URL,router.resolve(ServerDefaults.CDN_CTCEHS))
    }

    @Test fun failedChecksKeepLastWorkingRouteAndCustomAccountsNeverReroute() = runBlocking {
        var failed = false
        val router = CurrentMusicServerRouter { address ->
            if (failed) ServerHealth(ServerStatus.TIMEOUT)
            else ServerHealth(ServerStatus.AVAILABLE, if (address == ServerDefaults.CDN) 1 else 50)
        }
        router.configure(CurrentMusicServer.AUTO, ServerDefaults.URL)
        router.refresh(); failed = true; router.refresh()
        assertEquals(ServerDefaults.CDN, router.resolve(ServerDefaults.URL))
        router.configure(CurrentMusicServer.AUTO, "https://custom.example/cm/")
        assertEquals("https://custom.example/cm/", router.resolve("https://custom.example/cm/"))
        assertEquals(ServerDefaults.URL, router.resolve(ServerDefaults.URL))
    }

    @Test fun selectingFromCdnAnchorDoesNotRouteUnrelatedSavedMainAccount() = runBlocking {
        val router = CurrentMusicServerRouter { ServerHealth(ServerStatus.AVAILABLE, 1) }
        router.configure(CurrentMusicServer.MAIN, ServerDefaults.CDN)
        assertEquals(ServerDefaults.URL, router.resolve(ServerDefaults.CDN))
        router.configure(CurrentMusicServer.CDN, ServerDefaults.CDN)
        assertEquals(ServerDefaults.URL, router.resolve(ServerDefaults.URL))
    }

    @Test fun probeRequiresActualAnnouncementJsonAndNeverFollowsRedirects() = runBlocking {
        MockWebServer().use { server ->
            MockWebServer().use { redirect ->
                val probe = CurrentMusicServerProbe()
                server.enqueue(MockResponse().setBody("""{"items":[],"count":0}"""))
                assertEquals(ServerStatus.AVAILABLE, probe.check(server.url("/cm/").toString()).status)
                val request = server.takeRequest()
                assertEquals("/cm/announcements?limit=1", request.path)
                assertNull(request.getHeader("Authorization")); assertNull(request.getHeader("Cookie"))
                for (body in listOf("<html>challenge</html>", "{}", """{"items":{}}""")) {
                    server.enqueue(MockResponse().setBody(body))
                    assertEquals(ServerStatus.UNAVAILABLE, probe.check(server.url("/cm/").toString()).status)
                }
                server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", redirect.url("/cm/")))
                assertEquals(ServerStatus.UNAVAILABLE, probe.check(server.url("/cm/").toString()).status)
                assertNull(redirect.takeRequest(100, TimeUnit.MILLISECONDS))
                server.enqueue(MockResponse().setResponseCode(401).setBody("""{"items":[]}"""))
                assertEquals(ServerStatus.UNAVAILABLE, probe.check(server.url("/cm/").toString()).status)
            }
        }
    }

    @Test fun probeTimeoutAndCancellationAreBounded() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"items":[]}""").setHeadersDelay(2, TimeUnit.SECONDS))
            assertEquals(ServerStatus.TIMEOUT, CurrentMusicServerProbe(timeoutMs = 80).check(server.url("/cm/").toString()).status)
            server.enqueue(MockResponse().setBody("""{"items":[]}""").setHeadersDelay(2, TimeUnit.SECONDS))
            val job = launch { CurrentMusicServerProbe().check(server.url("/cm/").toString()); fail("cancelled probe completed") }
            delay(50); job.cancelAndJoin()
            assertTrue(job.isCancelled)
        }
    }

    @Test fun reroutedApiPreservesPathMethodQueryAndSessionWithoutCallingOrigin() = runBlocking {
        MockWebServer().use { cdn ->
            cdn.enqueue(MockResponse().setBody("""{"ok":true}"""))
            var expired = false
            val api = ApiClient({ ServerDefaults.URL }, { "fixture-token" }, { expired = true },
                routeServer = { if (it == ServerDefaults.URL) cdn.url("/cm/").toString() else it })
            val session = RequestSession(ServerDefaults.URL, "fixture-token")
            val result = api.request("POST", "test/route", mapOf("q" to "中文 &"), authenticated = true, expectedSession = session)
            assertTrue(result.jsonObject.containsKey("ok"))
            val request = cdn.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/cm/test/route", request.requestUrl!!.encodedPath)
            assertEquals("中文 &", request.requestUrl!!.queryParameter("q"))
            assertEquals("Bearer fixture-token", request.getHeader("Authorization"))
            assertFalse(expired)
        }
    }
}
