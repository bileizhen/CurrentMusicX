package io.github.currencortex.music

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.core.netease.*
import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.core.security.TokenStore
import io.github.currencortex.music.data.binding.BoundProfile
import io.github.currencortex.music.data.roaming.*
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.roaming.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Silent player and local HTTP fixtures: no real credentials or playback service are used. */
class PersonalRoamingControllerTest {
    private lateinit var scope: CoroutineScope
    private lateinit var server: MockWebServer
    private lateinit var sessions: NeteaseSessionStore
    private lateinit var player: PlayerController
    private lateinit var controller: PersonalRoamingController
    private val source = MutableStateFlow("fixture")
    private val requests = AtomicInteger()
    private var release = CountDownLatch(0)
    private var entered = CountDownLatch(1)
    @Volatile private var fail = false
    private val original = listOf(Song(900, "Original"), Song(901, "Original tail"))
    @Before fun setup() = runBlocking {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val vault = object : TokenStore {
            var value: String? = null
            override fun read() = value
            override fun write(token: String) { value = token }
            override fun clear() { value = null }
        }
        sessions = NeteaseSessionStore(vault, scope); sessions.ready.await()
        sessions.login(mapOf("MUSIC_U" to "fixture"), BoundProfile(7, "Test"), sessions.snapshot())
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val n = requests.incrementAndGet()
                    entered.countDown(); release.await(10, TimeUnit.SECONDS)
                    if (fail) return MockResponse().setResponseCode(503)
                    val hex = URLDecoder.decode(request.body.readUtf8().substringAfter("params="), "UTF-8")
                    val bytes = NeteaseCrypto.decryptResponse(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
                    val body = ApiJson.parseToJsonElement(bytes.toString(Charsets.UTF_8).split("-36cd479b6b5-")[1]).jsonObject
                    val base = if (body["mode"]?.jsonPrimitive?.content == "FAMILIAR") 100 else n * 10
                    return MockResponse().setBody("""{"code":200,"data":[${(0..2).joinToString(",") { """{"id":${base + it},"name":"Recommended ${base + it}"}""" }}]}""")
                }
            }
            start()
        }
        val endpoint = server.url("/").toString()
        val repository = PersonalRoamingRepository(NeteaseTransport(sessions, NeteaseEndpoints(endpoint, endpoint, endpoint)))
        val store = PreferenceDataStoreFactory.create(scope = scope,
            produceFile = { context.preferencesDataStoreFile("roaming-test-${UUID.randomUUID()}.preferences_pb") })
        onMain {
            player = PlayerController(context, PlaybackQueue(), scope, createController = { error("Silent fixture") })
            player.queue.replace(original, 0)
            player.state.value = PlayerState(song = original.first(), positionMs = 9_000)
            controller = PersonalRoamingController(repository, sessions, player, RoamingPreferences(store), scope, source)
        }
        waitFor { controller.state.value.ready }
    }
    @After fun cleanup() { release.countDown(); scope.cancel(); server.shutdown() }
    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private fun waitFor(test: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 8_000
        var ready = false
        while (!ready && System.currentTimeMillis() < deadline) {
            onMain { ready = test() }
            if (!ready) Thread.sleep(20)
        }
        assertTrue("Timed out waiting for roaming state", ready)
    }
    @Test fun keepsOriginalQueueAndPositionAndContinuouslyAppendsWithoutWrapping() {
        onMain { controller.start() }
        waitFor { controller.state.value.active && !controller.state.value.loading }
        assertEquals(listOf(10L, 11L, 12L), player.queue.state.value.songs.map { it.id })
        onMain { player.next() }
        waitFor { player.queue.state.value.songs.size == 6 && !controller.state.value.loading }
        assertEquals(11L, player.queue.state.value.current!!.id)
        onMain { controller.stop() }
        assertEquals(original, player.queue.state.value.songs)
        assertEquals(9_000L, player.queue.state.value.positionMs)
        assertNull(player.queue.state.value.radioSession)
    }
    @Test fun switchingModeReplacesOnlyStationAndFailedSwitchRetainsConfirmedMode() {
        onMain { controller.start() }
        waitFor { controller.state.value.active && !controller.state.value.loading }
        onMain { controller.start(RoamingCatalog.fallback.modes[1]) }
        waitFor { controller.state.value.choice.code == "FAMILIAR" && !controller.state.value.loading }
        assertEquals(listOf(100L, 101L, 102L), player.queue.state.value.songs.map { it.id })
        fail = true
        onMain { controller.start(RoamingCatalog.fallback.modes[2]) }
        waitFor { !controller.state.value.loading && controller.state.value.error != null }
        assertEquals("FAMILIAR", controller.state.value.choice.code)
        assertEquals(100L, player.queue.state.value.current!!.id)
        onMain { controller.stop() }; assertEquals(original, player.queue.state.value.songs)
    }
    @Test fun manualPlaybackDuringRequestCannotBeReplacedByItsLateResponse() {
        release = CountDownLatch(1)
        onMain { controller.start() }; assertTrue(entered.await(5, TimeUnit.SECONDS))
        onMain { player.playSong(Song(77, "User choice")) }
        release.countDown()
        waitFor { !controller.state.value.loading }
        assertEquals(77L, player.queue.state.value.current!!.id)
        assertFalse(controller.state.value.active)
        assertEquals(listOf(900L, 77L, 901L), player.queue.state.value.songs.map { it.id })
    }
    @Test fun playbackPositionTicksDoNotCancelARecommendationRequest() {
        release = CountDownLatch(1)
        onMain { controller.start() }; assertTrue(entered.await(5, TimeUnit.SECONDS))
        onMain {
            player.queue.state.value = player.queue.state.value.copy(positionMs = 10_500)
            player.state.value = player.state.value.copy(positionMs = 10_500)
        }
        release.countDown()
        waitFor { controller.state.value.active && !controller.state.value.loading }
        onMain { controller.stop() }
        assertEquals(original, player.queue.state.value.songs)
        assertEquals(10_500L, player.queue.state.value.positionMs)
    }
    @Test fun accountChangeCancelsPendingRequestWithoutWritingItsStationToAnotherAccount() = runBlocking {
        release = CountDownLatch(1)
        onMain { controller.start() }; assertTrue(entered.await(5, TimeUnit.SECONDS))
        sessions.clear(); release.countDown()
        waitFor { !controller.state.value.loading }
        assertEquals(original, player.queue.state.value.songs)
        assertFalse(controller.state.value.active)
    }
    @Test fun pauseAtStationBoundaryPreventsDelayedRecommendationFromAutoPlaying() {
        onMain { controller.updateSettings(controller.state.value.settings.copy(preload = false)); controller.start() }
        waitFor { controller.state.value.active && !controller.state.value.loading }
        onMain { player.select(2) }
        release = CountDownLatch(1); entered = CountDownLatch(1)
        onMain { player.next() }; assertTrue(entered.await(5, TimeUnit.SECONDS))
        assertTrue(controller.state.value.waiting)
        onMain { player.pause() }
        release.countDown()
        waitFor { !controller.state.value.loading }
        assertEquals(12L, player.queue.state.value.current!!.id)
        assertEquals(6, player.queue.state.value.songs.size)
        assertFalse(player.state.value.showPause)
    }
    @Test fun changingAudioSourceLeavesStationWithoutOverwritingCurrentQueue() {
        onMain { controller.start() }
        waitFor { controller.state.value.active && !controller.state.value.loading }
        source.value = "another source"
        waitFor { !controller.state.value.active }
        assertNull(player.queue.state.value.radioSession)
        assertEquals(10L, player.queue.state.value.current!!.id)
    }
}
