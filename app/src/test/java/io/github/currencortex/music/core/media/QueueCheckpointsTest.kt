package io.github.currencortex.music.core.media

import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueueCheckpointsTest {
    @Test fun savesNewestPendingPositionAfterSlowWriteAndSkipsIdenticalTicks() = runTest {
        val checkpoints = QueueCheckpoints()
        val first = QueueSnapshot(songs = listOf(Song(1, "First")), index = 0)
        val latest = first.copy(positionMs = 3000)
        val allowWrite = CompletableDeferred<Unit>()
        val saved = mutableListOf<QueueSnapshot>()
        backgroundScope.launch {
            checkpoints.writeTo(save = {
                allowWrite.await()
                saved += it
            }, onFailure = { throw it })
        }
        checkpoints.offer(first)
        runCurrent()
        checkpoints.offer(first.copy(positionMs = 1500))
        checkpoints.offer(latest)
        allowWrite.complete(Unit)
        runCurrent()
        checkpoints.offer(latest.copy())
        runCurrent()
        assertEquals(listOf(first, latest), saved)
    }

    @Test fun retriesUnchangedSnapshotAfterFailedWrite() = runTest {
        val checkpoints = QueueCheckpoints()
        val snapshot = QueueSnapshot(songs = listOf(Song(1, "First")), index = 0)
        var attempts = 0
        var failures = 0
        val saved = mutableListOf<QueueSnapshot>()
        backgroundScope.launch {
            checkpoints.writeTo(save = {
                if (++attempts == 1) error("Simulated database failure")
                saved += it
            }, onFailure = { failures++ })
        }
        checkpoints.offer(snapshot)
        runCurrent()
        checkpoints.offer(snapshot)
        runCurrent()
        assertEquals(1, failures)
        assertEquals(listOf(snapshot), saved)
    }
}
