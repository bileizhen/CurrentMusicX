package io.github.currencortex.music

import android.content.*
import android.os.Bundle
import android.os.IBinder
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.data.settings.MusicSettings
import io.github.currencortex.music.data.settings.MusicTransitionMode
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.await
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Actual service/session/AudioTrack with silent local WAVs. No production account or system volume changes. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackPauseFadeServiceTest {
    private val app get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    private val container get() = app.container
    private lateinit var settings: MusicSettings
    private lateinit var server: MockWebServer
    private lateinit var songs: List<Song>
    private lateinit var controller: MediaController
    private lateinit var raw: Player
    private lateinit var view: PlayerView
    private var binder: MusicService.VideoBinder? = null
    private var bound = false
    private val connected = CompletableDeferred<MusicService.VideoBinder>()
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) { connected.complete(service as MusicService.VideoBinder) }
        override fun onServiceDisconnected(name: ComponentName?) = Unit
    }

    @Before fun prepare() = runBlocking<Unit> {
        check(app.packageName.endsWith(".verification"))
        app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        container.ready.await(); container.navidrome.ready.await()
        check(!container.navidrome.state.value.configured && !container.playerController.state.value.playing)
        settings = container.musicSettings.snapshot()
        val bytes = 8000 * 30 * 2
        val wav = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + bytes); put("WAVEfmt ".toByteArray()); putInt(16)
            putShort(1); putShort(1); putInt(8000); putInt(16000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(bytes)
        }.array()
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath.substringAfterLast('/')
                    if (path == "stream.view") return MockResponse().setHeader("Content-Type", "audio/wav").setBody(Buffer().write(wav))
                    val data = when (path) {
                        "ping.view" -> "\"serverVersion\":\"pause-fixture\""
                        "getUser.view" -> "\"user\":{\"downloadRole\":false}"
                        "getAlbum.view" -> """"album":{"song":[{"id":"pause-1","title":"淡出一","duration":30,"suffix":"wav"},{"id":"pause-2","title":"淡出二","duration":30,"suffix":"wav"}]}"""
                        else -> ""
                    }
                    return MockResponse().setBody("""{"subsonic-response":{"status":"ok"${if (data.isBlank()) "" else ",$data"}}}""")
                }
            }
            start()
        }
        container.musicSettings.setConcurrentPlayback(false)
        container.musicSettings.setTransitionMode(MusicTransitionMode.OFF)
        container.navidrome.connect(server.url("/").toString(), "fixture", "fixture-password")
        songs = container.navidrome.album("album")
        withContext(Dispatchers.Main) { container.playerController.playList(songs, 0) }
        await("playing") { container.playerController.state.value.playing }
        controller = withContext(Dispatchers.Main) { container.playerController.connect() }
        bound = app.bindService(Intent(app, MusicService::class.java).setAction(MusicService.VIDEO_SURFACE), connection, Context.BIND_AUTO_CREATE)
        assertTrue(bound)
        binder = withTimeout(5000) { connected.await() }
        withContext(Dispatchers.Main) {
            view = PlayerView(app); binder!!.attach(view); raw = view.player!!
            controller.volume = .4f
        }
        await("user volume") { raw.volume == .4f }
    }

    @After fun finish() = runBlocking<Unit> {
        withContext(Dispatchers.Main) {
            if (::view.isInitialized) binder?.detach(view)
            if (bound) app.unbindService(connection)
            container.playerController.state.value = container.playerController.state.value.copy(mode = PlayerMode.LOCAL)
            container.playerController.clear(); container.playerController.disconnect()
        }
        app.stopService(Intent(app, MusicService::class.java)); delay(300)
        if (::settings.isInitialized) {
            container.musicSettings.setTransitionMode(settings.transitionMode)
            container.musicSettings.setConcurrentPlayback(settings.allowConcurrentPlayback)
        }
        container.navidrome.disconnect()
        if (::server.isInitialized) server.shutdown()
    }

    private suspend fun await(label: String, condition: () -> Boolean) {
        try { withTimeout(10000) { while (!withContext(Dispatchers.Main) { condition() }) delay(10) } }
        catch (e: TimeoutCancellationException) { throw AssertionError("$label: ${container.playerController.state.value}", e) }
    }

    @Test fun sessionPauseHasIntermediateNativeGainsAndRapidResumeKeepsUserVolume() = runBlocking<Unit> {
        withContext(Dispatchers.Main) { controller.pause() }
        await("audible fade") { raw.playWhenReady && raw.volume > .02f && raw.volume < .38f }
        withContext(Dispatchers.Main) {
            assertFalse("UI acknowledges pause before native audio stops", controller.playWhenReady)
            assertEquals(.4f, controller.volume, .0001f)
        }
        await("native pause") { !raw.playWhenReady && raw.volume == 0f }
        withContext(Dispatchers.Main) { controller.play() }
        await("resumed") { raw.isPlaying && raw.volume > .399f }
        withContext(Dispatchers.Main) { controller.pause() }
        await("second fade") { raw.playWhenReady && raw.volume in .05f.. .35f }
        withContext(Dispatchers.Main) { controller.play(); controller.volume = .7f }
        delay(400)
        withContext(Dispatchers.Main) {
            assertTrue("The cancelled pause cannot stop a later resume", raw.isPlaying)
            assertEquals(.7f, raw.volume, .0001f)
            assertEquals(.7f, controller.volume, .0001f)
            controller.mute()
        }
        await("mute") { raw.volume == 0f }
        withContext(Dispatchers.Main) { controller.unmute() }
        await("unmute retains user gain") { raw.volume == .7f }
    }

    @Test fun changingSongsDuringTheFadeCannotPauseOrMuteTheNewSong() = runBlocking<Unit> {
        withContext(Dispatchers.Main) { controller.pause() }
        await("fading") { raw.playWhenReady && raw.volume in .05f.. .35f }
        withContext(Dispatchers.Main) { container.playerController.playSong(songs[1]) }
        await("new song") { raw.currentMediaItem?.mediaId == songs[1].id.toString() && raw.isPlaying }
        delay(400)
        withContext(Dispatchers.Main) { assertTrue(raw.isPlaying); assertEquals(.4f, raw.volume, .0001f) }
    }

    @Test fun aTrackEndingDuringTheFadeDoesNotAdvanceTheQueue() = runBlocking<Unit> {
        withContext(Dispatchers.Main) { controller.seekTo(29_600) }
        await("near end") { raw.isPlaying && raw.currentPosition >= 29_840 }
        withContext(Dispatchers.Main) { controller.pause() }
        await("stopped") { !raw.playWhenReady }
        delay(400)
        assertEquals(songs[0].id, container.playbackQueue.state.value.current?.id)
        withContext(Dispatchers.Main) { assertFalse(raw.playWhenReady) }
    }

    @Test fun roomPauseAcknowledgesTheCommandBeforeTheLocalNativeFadeFinishes() = runBlocking<Unit> {
        var roomAnchor = 0L
        withContext(Dispatchers.Main) {
            container.playerController.state.value = container.playerController.state.value.copy(mode = PlayerMode.ROOM)
            roomAnchor = raw.currentPosition
            controller.sendCustomCommand(SessionCommand(MusicService.ROOM_SYNC, Bundle.EMPTY), Bundle().apply {
                putBoolean("play", false); putFloat("speed", 1f); putLong("position", roomAnchor)
            }).await()
            assertTrue("Room command does not wait for the local fade", raw.playWhenReady)
        }
        await("room fade") { raw.playWhenReady && raw.volume in .05f.. .35f }
        await("room pause") { !raw.playWhenReady && raw.volume == 0f }
        await("shared pause position") { kotlin.math.abs(raw.currentPosition - roomAnchor) < 30 }
        withContext(Dispatchers.Main) {
            controller.sendCustomCommand(SessionCommand(MusicService.ROOM_SYNC, Bundle.EMPTY), Bundle().apply {
                putBoolean("play", true); putFloat("speed", 1f)
            }).await()
        }
        await("room resume") { raw.isPlaying && raw.volume > .399f }
    }
}
