package io.github.currencortex.music.core.dlna

import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.song.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class DlnaControllerTest {
    @Test fun rejectedLosslessFallsBackToCompatibleQualityAndStopsBeforeRestoringQueue() = runBlocking {
        MockWebServer().use { server ->
            val observed = CopyOnWriteArrayList<RecordedRequest>()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    observed.add(request)
                    if (request.requestUrl!!.encodedPath == "/cm/ncm/song/url") {
                        val compatible = request.requestUrl!!.queryParameter("level") == "exhigh"
                        return MockResponse().setBody("""{"url":"https://cdn.test/${if (compatible) "song.mp3" else "song.flac"}","sr":44100,"ch":2}""")
                    }
                    if (request.getHeader("SOAPACTION")?.contains("SetAVTransportURI") == true && request.body.clone().readUtf8().contains("song.flac")) return MockResponse().setResponseCode(500)
                    return MockResponse().setBody("""<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><ok/></s:Body></s:Envelope>""")
                }
            }
            val expected = RequestSession(server.url("/cm/").toString(), "isolated-cast-token")
            val player = SilentExternalPlayer().apply { queue.replace(listOf(Song(8, "Local")), 0) }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val controller = DlnaController(player, MusicRepository(ApiClient({ expected.server }, { expected.token }, {})), { expected }, scope)
            try {
                controller.start(DlnaDevice("tv", "Emulated TV", server.url("/description").toString(), DlnaService("urn:schemas-upnp-org:service:AVTransport:1", server.url("/av").toString())))
                withTimeout(4000) { while (controller.state.value.busy) delay(20) }
                assertNull(controller.state.value.error); assertEquals("极高（兼容）", controller.state.value.quality)
                assertEquals(PlayerMode.CAST, player.state.value.mode)
                assertEquals(listOf("lossless", "exhigh"), observed.filter { it.requestUrl!!.encodedPath.contains("/song/url") }.map { it.requestUrl!!.queryParameter("level") })
                assertTrue(observed.filter { it.requestUrl!!.encodedPath == "/av" }.all { it.getHeader("Authorization") == null })
                controller.stop()
                withTimeout(4000) { while (controller.state.value.device != null) delay(20) }
                assertEquals(PlayerMode.LOCAL, player.state.value.mode); assertFalse(player.state.value.playing); assertEquals(8L, player.queue.state.value.current?.id)
                assertTrue(observed.last().getHeader("SOAPACTION")!!.contains("#Stop"))
            } finally { scope.cancel() }
        }
    }
}
