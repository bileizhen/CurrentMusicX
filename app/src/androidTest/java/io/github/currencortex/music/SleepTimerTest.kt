package io.github.currencortex.music

import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Drives the real container/timer without audio or network: only PlayerState is simulated. */
class SleepTimerTest {
    private fun container() = AppContainer(ApplicationProvider.getApplicationContext(), "sleep-${UUID.randomUUID()}")

    private suspend fun ready(container: AppContainer, song: Song) {
        container.ready.await(); container.sessionRestored.await()
        container.playbackQueue.replace(listOf(song), 0)
        container.playerController.state.value = PlayerState(song = song, playing = true, durationMs = 60_000)
    }

    @Test fun expiryPausesPlaybackAndReportsIt() = runBlocking {
        val container = container()
        try {
            val song = Song(880001, "定时关闭验证")
            ready(container, song)
            container.sleepTimer.startForTesting(600)
            withTimeout(5000) { container.sleepTimer.state.filter { !it.enabled }.first() }
            assertFalse("Expiry must pause playback", container.playerController.state.value.playing)
            assertEquals("定时关闭已生效，已暂停播放", container.sleepTimer.state.value.notice)
        } finally { container.close() }
    }

    @Test fun extendKeepsPlayingUntilTheSongEnds() = runBlocking {
        val container = container()
        try {
            val song = Song(880002, "延长验证")
            ready(container, song)
            container.sleepTimer.startForTesting(600, extendToSongEnd = true)
            withTimeout(5000) { container.sleepTimer.state.filter { it.waitingForSongEnd }.first() }
            assertTrue("Extend must not pause mid-song", container.playerController.state.value.playing)
            // The queue advances to the next song, which is what "song finished" looks like here.
            val next = Song(880003, "下一首")
            container.playbackQueue.replace(listOf(next), 0)
            container.playerController.state.value = PlayerState(song = next, playing = true, durationMs = 60_000)
            withTimeout(5000) { container.sleepTimer.state.filter { !it.enabled }.first() }
            assertFalse("Playback must pause once the song is over", container.playerController.state.value.playing)
            assertEquals("已播完当前歌曲，已暂停播放", container.sleepTimer.state.value.notice)
        } finally { container.close() }
    }

    @Test fun switchingOffCancelsWithoutPausing() = runBlocking {
        val container = container()
        try {
            val song = Song(880004, "取消验证")
            ready(container, song)
            container.sleepTimer.startForTesting(60_000)
            assertTrue(container.sleepTimer.state.value.enabled)
            container.sleepTimer.stop()
            assertFalse(container.sleepTimer.state.value.enabled)
            assertTrue("Cancelling must not pause playback", container.playerController.state.value.playing)
        } finally { container.close() }
    }

    @Test fun repeatOneBoundaryEndsTheExtendedTimer() = runBlocking {
        val container = container()
        try {
            val song = Song(880005, "单曲循环结束验证")
            ready(container, song)
            container.sleepTimer.startForTesting(100, extendToSongEnd = true)
            withTimeout(5000) { container.sleepTimer.state.filter { it.waitingForSongEnd }.first() }
            container.playerController.state.value = container.playerController.state.value.copy(positionMs = 59_900)
            kotlinx.coroutines.delay(100)
            container.playerController.state.value = container.playerController.state.value.copy(positionMs = 0)
            kotlinx.coroutines.delay(100)
            assertTrue("Seeking backward must not count as song completion", container.sleepTimer.state.value.enabled)
            container.playerController.completedTracks.value += 1
            withTimeout(5000) { container.sleepTimer.state.filter { !it.enabled }.first() }
            assertFalse(container.playerController.state.value.playing)
        } finally { container.close() }
    }

    @Test fun manuallyPausedSongDoesNotKeepTheExtendedTimerWaiting(): Unit = runBlocking {
        val container = container()
        try {
            ready(container, Song(880006, "暂停验证"))
            container.sleepTimer.startForTesting(100, extendToSongEnd = true)
            withTimeout(5000) { container.sleepTimer.state.filter { it.waitingForSongEnd }.first() }
            container.playerController.state.value = container.playerController.state.value.copy(playing = false)
            withTimeout(5000) { container.sleepTimer.state.filter { !it.enabled }.first() }
        } finally { container.close() }
    }
}
