package io.github.currencortex.music

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.core.room.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.data.song.Song

/** Explicitly opted in on a logged-in phone. Never starts a player or reads credentials out of the app. */
class LiveRoomContractTest {
    @Test fun ownedPrivateRoomRoundTripAndAuthenticatedSse() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("m4Live") == "1")
        val container = ApplicationProvider.getApplicationContext<CurrentMusicApplication>().container
        container.sessionRestored.await()
        assumeTrue(container.accountRepository.state.value.account != null)
        val repo = container.roomRepository; val expected = repo.session()
        val account = container.accountRepository.state.value.account!!.id
        repo.list("", 0)
        val room = repo.create("Native validation ${System.currentTimeMillis()}", "", public = false, free = false, expected = expected)
        try {
            assertTrue(room.code.matches(Regex("\\d{6}")))
            assertEquals(room.id, repo.find(room.code).id)
            repo.action(room.id, "join", expected = expected)
            val detail = repo.detail(room.id, expected)
            assertEquals(io.github.currencortex.music.data.room.RoomRole.OWNER, detail.role(account))
            assertTrue(repo.sync(room.id, expected) > 0)
            val open = withTimeout(15000) { RoomSseClient().events(room.id, detail.latestSeq, null, expected).first() }
            assertEquals(RoomSignal.Open, open)
            repo.action(room.id, "pause", expected = expected)
            val updated = repo.detail(room.id, expected)
            assertFalse(updated.timeline?.playing == true)
        } finally {
            withContext(NonCancellable) { repo.action(room.id, "close", expected = expected) }
        }
        assertEquals(account, container.accountRepository.state.value.account?.id)
    }
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun castingMediaSessionRoutesCommandsAndKeepsActualExoPlayerSilent() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("m4Live") == "1")
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val container = context.container; container.ready.await(); container.sessionRestored.await()
        val player = container.playerController
        val facade = withContext(Dispatchers.Main) { player.connect() }
        assumeTrue(withContext(Dispatchers.Main) { !facade.isPlaying && player.state.value.mode == PlayerMode.LOCAL && player.queue.state.value.current != null })
        val original = player.queue.state.value
        val commands = java.util.concurrent.atomic.AtomicInteger()
        val seek = java.util.concurrent.atomic.AtomicLong()
        val controls = object : ExternalPlayback {
            override fun play(playing: Boolean) { commands.incrementAndGet(); player.state.value = player.state.value.copy(playing = playing) }
            override fun seek(position: Long) { seek.set(position) }
            override fun next() { commands.incrementAndGet() }
            override fun previous() {}
            override fun request(song: Song) {}
            override fun stop() {}
        }
        val binding = CompletableDeferred<MusicService.VideoBinder>()
        val connection = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) { binding.complete(service as MusicService.VideoBinder) }
            override fun onServiceDisconnected(name: android.content.ComponentName?) {}
        }
        var bound = false
        var binder: MusicService.VideoBinder? = null
        var view: androidx.media3.ui.PlayerView? = null
        try {
            withContext(Dispatchers.Main) {
                assertTrue(player.beginExternal(PlayerMode.CAST, controls))
                player.state.value = PlayerState(song = original.current, playing = true, positionMs = 1000,
                    durationMs = original.current!!.durationMs.coerceAtLeast(180000), mode = PlayerMode.CAST)
                bound = context.bindService(android.content.Intent(context, MusicService::class.java).setAction(MusicService.VIDEO_SURFACE), connection, android.content.Context.BIND_AUTO_CREATE)
            }
            assertTrue(bound); binder = withTimeout(3000) { binding.await() }
            withContext(Dispatchers.Main) {
                view = androidx.media3.ui.PlayerView(context); binder!!.attach(view!!)
                assertFalse("The actual local ExoPlayer must be silent while casting", view!!.player!!.isPlaying)
            }
            withTimeout(3000) { while (!withContext(Dispatchers.Main) { facade.isPlaying }) delay(20) }
            withContext(Dispatchers.Main) { assertEquals(original.current!!.name, facade.mediaMetadata.title.toString()); facade.pause() }
            withTimeout(3000) { while (commands.get() == 0) delay(20) }
            withContext(Dispatchers.Main) { facade.seekTo(30000) }
            withTimeout(3000) { while (seek.get() != 30000L) delay(20) }
            withContext(Dispatchers.Main) { assertFalse(view!!.player!!.isPlaying) }
        } finally {
            withContext(NonCancellable + Dispatchers.Main) {
                if (view != null && binder != null) binder!!.detach(view!!)
                if (bound) context.unbindService(connection)
                player.endExternal(controls)
            }
        }
        assertEquals(original.songs, player.queue.state.value.songs); assertEquals(original.index, player.queue.state.value.index)
        assertEquals(PlayerMode.LOCAL, player.state.value.mode); assertFalse(player.state.value.playing)
    }
}
