package io.github.currencortex.music.core.netease.listen

import io.github.currencortex.music.core.media.ExternalPlayback
import io.github.currencortex.music.core.media.ExternalPlayer
import io.github.currencortex.music.core.media.PlaybackMode
import io.github.currencortex.music.core.media.PlaybackQueue
import io.github.currencortex.music.core.media.PlayerMode
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.core.media.QueueSnapshot
import io.github.currencortex.music.core.netease.NeteaseCredential
import io.github.currencortex.music.core.netease.NeteaseSession
import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import io.github.currencortex.music.data.binding.BoundProfile
import io.github.currencortex.music.data.netease.listen.*
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NeteaseListenTogetherSessionTest {
    private class FakeProtocol(private val now: () -> Long) : ListenTogetherProtocol {
        var revision = 0L
        val credential = NeteaseCredential(mapOf("MUSIC_U" to "local-fixture"), BoundProfile(100, "自己", "https://self.invalid/avatar"))
        val room = ListenTogetherRoom("room-100", 100, listOf(
            ListenTogetherParticipant(100, "自己", "https://self.invalid/avatar"),
            ListenTogetherParticipant(200, "朋友", "https://friend.invalid/avatar")))
        var inRoom = true
        var queue = ListenTogetherQueue(listOf(10, 20), anchorSongId = 10, version = listOf(ListenTogetherVersion(100, 1), ListenTogetherVersion(200, 2)))
        var playback: ListenTogetherPlayback? = ListenTogetherPlayback("PLAY", 10, playing = true,
            positionMs = 5000, serverSeq = 1, userId = 200)
        var createAction: suspend () -> ListenTogetherRoom = { room }
        var acceptAction: suspend () -> ListenTogetherRoom = { room }
        var queueFailure: Exception? = null
        var playlistFailure: Exception? = null
        var leaveAction: suspend () -> Unit = {}
        var statusAction: suspend () -> Unit = {}
        var restores = 0
        val beats = mutableListOf<Long>()
        val reports = mutableListOf<Pair<String, Int>>()
        val reportedQueues = mutableListOf<ListenTogetherQueue>()
        val playbackCommands = mutableListOf<ListenTogetherPlayback>()
        var playlists = 0
        var syncNotices = 0
        var syncFailure: ApiException? = null
        var leaves = 0
        override suspend fun captureSession() = NeteaseSession(credential, revision)
        override fun checkSession(expected: NeteaseSession) {
            if (expected.revision != revision) throw ApiException(ErrorKind.NeteaseSessionChanged)
        }
        override suspend fun create(expected: NeteaseSession, refer: String, inviteUid: Long?) = createAction()
        override suspend fun accept(expected: NeteaseSession, invitation: ListenTogetherInvitation, refer: String) = acceptAction()
        override suspend fun status(expected: NeteaseSession): ListenTogetherStatus {
            statusAction()
            return ListenTogetherStatus(inRoom, room.takeIf { inRoom })
        }
        override suspend fun restoreDevice(expected: NeteaseSession, roomId: String) { restores++ }
        override suspend fun requestSync(expected: NeteaseSession, roomId: String) { syncNotices++; syncFailure?.let { throw it } }
        override suspend fun heartbeat(expected: NeteaseSession, roomId: String, songId: Long,
            playing: Boolean, positionMs: Long, version: List<ListenTogetherVersion>): Boolean { beats += now(); return inRoom }
        override suspend fun playlist(expected: NeteaseSession, roomId: String, local: ListenTogetherQueue): ListenTogetherSnapshot {
            playlists++
            playlistFailure?.let { throw it }
            return ListenTogetherSnapshot(queue, playback)
        }
        override suspend fun reportPlayback(expected: NeteaseSession, roomId: String, playback: ListenTogetherPlayback) {
            reports += "play" to playback.clientSeq
            playbackCommands += playback
        }
        override suspend fun reportQueue(expected: NeteaseSession, roomId: String, queue: ListenTogetherQueue) {
            queueFailure?.let { throw it }
            reports += "queue" to queue.clientSeq
            reportedQueues += queue
            this.queue = queue
        }
        override suspend fun leave(expected: NeteaseSession, roomId: String) { leaves++; leaveAction() }
    }

    /** In-memory PCM-free player: verifies ownership, restore and corrections without any Android service. */
    private class SilentPlayer : ExternalPlayer {
        override val queue = PlaybackQueue()
        override val state = MutableStateFlow(PlayerState())
        override var external: ExternalPlayback? = null
        var beginAction: suspend () -> Unit = {}
        var trackAction: suspend () -> Unit = {}
        var begins = 0
        var ends = 0
        val loaded = mutableListOf<Long>()
        val corrections = mutableListOf<Pair<Long, Boolean>>()
        private var originalQueue: QueueSnapshot? = null
        private var originalState: PlayerState? = null
        override suspend fun beginExternal(mode: PlayerMode, controls: ExternalPlayback): Boolean {
            if (external != null) return false
            begins++
            originalQueue = queue.state.value
            originalState = state.value
            external = controls
            state.value = state.value.copy(mode = mode, listenTogether = null)
            beginAction()
            return true
        }
        override fun endExternal(controls: ExternalPlayback) {
            if (external !== controls) return
            ends++
            external = null
            originalQueue?.let(queue::restore)
            state.value = (originalState ?: PlayerState()).copy(mode = PlayerMode.LOCAL, listenTogether = null)
        }
        override suspend fun roomTrack(song: Song?, url: String?, position: Long, playing: Boolean) {
            trackAction()
            song?.let { loaded += it.id }
            state.value = state.value.copy(song = song, positionMs = position, playing = playing, playRequested = playing)
        }
        override suspend fun roomCorrection(position: Long, seek: Boolean, speed: Float, playing: Boolean) {
            corrections += position to seek
            state.value = state.value.copy(positionMs = if (seek) position else state.value.positionMs,
                playing = playing, playRequested = playing)
        }
    }

    private class Fixture(test: TestScope) {
        val scope = test.backgroundScope
        val repo = FakeProtocol { test.testScheduler.currentTime }
        val player = SilentPlayer()
        val session = NeteaseListenTogetherSession(repo, player, scope, detail = { Song(it, "歌曲$it") },
            source = { "https://local-resolved.invalid/${it.id}.mp3" }, monotonic = { test.testScheduler.currentTime }, pollIntervalMs = 1000)
        fun original(songs: List<Song> = listOf(Song(1, "原队列")), index: Int = 0, mode: PlaybackMode = PlaybackMode.LIST) {
            player.queue.replace(songs, index)
            player.queue.setMode(mode)
            player.state.value = PlayerState(song = songs.getOrNull(index), playing = true, positionMs = 3210)
        }
        fun assertLocal() {
            assertFalse(session.state.value.active)
            assertFalse(session.state.value.busy)
            assertEquals(PlayerMode.LOCAL, player.state.value.mode)
            assertNull(player.state.value.listenTogether)
            assertNull(player.external)
        }
    }

    @Test fun heartbeatUsesTwentySecondsAndRepeatedSnapshotNeverRewindsProgress() = runTest {
        val f = Fixture(this)
        f.session.resume(); runCurrent()
        assertEquals(1, f.repo.syncNotices)
        assertEquals(listOf(0L), f.repo.beats)
        assertEquals(listOf(10L), f.player.loaded)
        assertEquals(2, f.player.state.value.listenTogether!!.participants.size)
        f.player.state.value = f.player.state.value.copy(positionMs = 12500)
        advanceTimeBy(19_999); runCurrent()
        assertEquals(listOf(0L), f.repo.beats)
        assertTrue(f.repo.playlists > 10)
        assertEquals(12500L, f.player.state.value.positionMs)
        assertTrue(f.player.corrections.isEmpty())
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf(0L, 20_000L), f.repo.beats)
        assertEquals(12500L, f.player.state.value.positionMs)

        f.repo.playback = f.repo.playback!!.copy(commandType = "PROGRESS", serverSeq = 2)
        advanceTimeBy(1000); runCurrent()
        assertEquals(listOf(5000L to true), f.player.corrections)
        f.player.state.value = f.player.state.value.copy(positionMs = 8000)
        advanceTimeBy(4000); runCurrent()
        assertEquals(1, f.player.corrections.size)
        assertEquals(8000L, f.player.state.value.positionMs)
    }

    @Test fun queueAndPlaybackReportsShareOneIncreasingClientSequence() = runTest {
        val f = Fixture(this)
        f.original(); f.repo.playback = null
        f.session.create(); runCurrent()
        assertEquals(listOf("queue" to 1, "play" to 2), f.repo.reports)
        assertEquals(listOf(ListenTogetherVersion(100, 0)), f.repo.reportedQueues.single().version)
        f.session.request(Song(3, "点歌")); runCurrent()
        f.session.seek(9000); runCurrent()
        assertEquals(listOf("queue" to 1, "play" to 2, "queue" to 3, "play" to 4, "play" to 5), f.repo.reports)
        assertEquals(9000L, f.player.state.value.positionMs)
        assertEquals(listOf(ListenTogetherVersion(100, 1)), f.repo.reportedQueues.last().version)
    }

    @Test fun unavailableLegacySyncNoticeDoesNotBlockWorkingRecoverySnapshots() = runTest {
        val f = Fixture(this)
        f.repo.syncFailure = ApiException(ErrorKind.NotFound)
        f.session.resume(); runCurrent()
        assertTrue(f.session.state.value.active)
        assertTrue(f.session.state.value.connected)
        assertEquals(listOf(10L), f.player.loaded)
        assertEquals(1, f.repo.syncNotices)
    }

    @Test fun queueVersionPreservesPeerAndNeverReusesAFailedReservation() = runTest {
        val f = Fixture(this)
        f.session.resume(); runCurrent()
        f.repo.queueFailure = IllegalStateException("模拟断网")
        f.session.request(Song(3, "点歌")); runCurrent()
        f.repo.queueFailure = null
        f.session.request(Song(3, "点歌")); runCurrent()
        assertEquals(listOf(ListenTogetherVersion(200, 2), ListenTogetherVersion(100, 3)), f.repo.reportedQueues.last().version)
    }

    @Test fun lateCreateAfterDisconnectCannotAttachOrOverwriteTheNewGeneration() = runTest {
        val f = Fixture(this)
        f.original()
        val release = CompletableDeferred<Unit>()
        f.repo.createAction = { release.await(); f.repo.room }
        f.session.create(); runCurrent()
        assertTrue(f.session.state.value.busy)
        f.session.disconnect("新会话")
        release.complete(Unit); runCurrent()
        f.assertLocal()
        assertEquals("新会话", f.session.state.value.error)
        assertEquals(0, f.player.begins)
        assertTrue(f.repo.reports.isEmpty())
        assertTrue(f.repo.beats.isEmpty())
    }

    @Test fun lateJoinFromAnOldAccountCannotAttachOrPublishAvatars() = runTest {
        val f = Fixture(this)
        val release = CompletableDeferred<Unit>()
        f.repo.acceptAction = { release.await(); f.repo.room }
        f.session.join(ListenTogetherInvitation("room-100", 200).shareUrl()); runCurrent()
        f.repo.revision++
        release.complete(Unit); runCurrent()
        f.assertLocal()
        assertNotNull(f.session.state.value.error)
        assertEquals(0, f.player.begins)
    }

    @Test fun accountChangesDuringBeginExternalRestoreTheOriginalQueue() = runTest {
        val f = Fixture(this)
        f.original()
        val original = f.player.queue.state.value
        f.player.beginAction = { f.repo.revision++ }
        f.session.create(); runCurrent()
        f.assertLocal()
        assertEquals(1, f.player.ends)
        assertEquals(original, f.player.queue.state.value)
        assertEquals(1L, f.player.state.value.song!!.id)
        assertTrue(f.repo.beats.isEmpty())
    }

    @Test fun createQueueFailureRollsBackInsteadOfLeavingAnUnpolledRoomPlayer() = runTest {
        val f = Fixture(this)
        f.original()
        f.repo.queueFailure = ApiException(ErrorKind.Server)
        f.session.create(); runCurrent()
        f.assertLocal()
        assertEquals(1, f.player.ends)
        assertEquals(1L, f.player.queue.state.value.current!!.id)
        assertNotNull(f.session.state.value.error)
        advanceTimeBy(30_000); runCurrent()
        assertTrue(f.repo.beats.isEmpty())
    }

    @Test fun initialJoinSnapshotFailureOrTrackFailureRestoresPlayerOwnership() = runTest {
        val f = Fixture(this)
        f.original()
        f.repo.playlistFailure = ApiException(ErrorKind.Server)
        f.session.join(ListenTogetherInvitation("room-100", 200).shareUrl()); runCurrent()
        f.assertLocal()
        assertEquals(1, f.player.ends)
        f.repo.playlistFailure = null
        f.player.trackAction = { throw IllegalStateException("player unavailable") }
        f.session.resume(); runCurrent()
        f.assertLocal()
        assertEquals(2, f.player.ends)
        assertTrue(f.repo.beats.isEmpty())
    }

    @Test fun leaveClearsAvatarsAndRestoresOriginalLocalQueueEvenWhenRemoteExitFails() = runTest {
        val f = Fixture(this)
        f.original(listOf(Song(1, "原来一"), Song(2, "原来二")), 1)
        val original = f.player.queue.state.value
        f.session.resume(); runCurrent()
        assertNotNull(f.player.state.value.listenTogether)
        f.repo.leaveAction = { throw ApiException(ErrorKind.Server) }
        f.session.leave(); runCurrent()
        f.assertLocal()
        assertEquals(original, f.player.queue.state.value)
        assertEquals(2L, f.player.state.value.song!!.id)
        assertEquals(1, f.repo.leaves)
        assertNotNull(f.session.state.value.error)
        val beatCount = f.repo.beats.size
        advanceTimeBy(30_000); runCurrent()
        assertEquals(beatCount, f.repo.beats.size)
    }

    @Test fun cancellationDuringRemoteLeaveStillRestoresLocalPlayback() = runTest {
        val f = Fixture(this)
        f.original(); f.session.resume(); runCurrent()
        val release = CompletableDeferred<Unit>()
        f.repo.leaveAction = { release.await() }
        f.session.leave(); runCurrent()
        assertTrue(f.session.state.value.busy)
        f.scope.cancel(); runCurrent()
        f.assertLocal()
        assertEquals(1L, f.player.queue.state.value.current!!.id)
    }

    @Test fun shuffledCreationPublishesTheSamePermutationAndAddsRequestedSongsToBothLists() = runTest {
        val f = Fixture(this)
        f.original((1L..8L).map { Song(it, "歌曲$it") }, 3, PlaybackMode.SHUFFLE)
        f.repo.playback = null
        f.session.create(); runCurrent()
        val queue = f.repo.reportedQueues.single()
        assertEquals("RANDOM", queue.playMode)
        assertEquals(queue.songIds.toSet(), queue.randomSongIds.toSet())
        assertEquals(queue.songIds.size, queue.randomSongIds.size)
        assertEquals(4L, queue.anchorSongId)
        f.session.request(Song(99, "新增")); runCurrent()
        val updated = f.repo.reportedQueues.last()
        assertEquals(queue.randomSongIds + 99L, updated.randomSongIds)
        assertEquals(99L, updated.songIds[updated.anchorPosition])
    }

    @Test fun fullQueueRejectsAdditionalSongsAndKeepsAnExistingAnchorWithinBounds() = runTest {
        val f = Fixture(this)
        f.original((1L..1001L).map { Song(it, "歌曲$it") }, 1000)
        f.repo.playback = null
        f.session.create(); runCurrent()
        val queue = f.repo.reportedQueues.single()
        assertEquals(1000, queue.songIds.size)
        assertEquals(1001L, queue.anchorSongId)
        assertEquals(1001L, queue.songIds[queue.anchorPosition])
        val reports = f.repo.reports.size
        f.session.request(Song(1002, "超出")); runCurrent()
        assertEquals(reports, f.repo.reports.size)
        assertTrue(f.session.state.value.error!!.contains("队列已满"))
        f.session.request(Song(1001, "已有")); runCurrent()
        assertEquals(reports + 2, f.repo.reports.size)
        assertEquals(1001L, f.repo.reportedQueues.last().let { it.songIds[it.anchorPosition] })
    }

    @Test fun recoveryPublishesTheEntireSharedListWithoutReplacingTheSavedLocalQueue() = runTest {
        val f = Fixture(this); f.original()
        val original = f.player.queue.state.value
        f.session.resume(); runCurrent()
        assertEquals(listOf(10L, 20L), f.session.queue.value.songs.map { it.id })
        assertEquals(listOf("歌曲10", "歌曲20"), f.session.queue.value.songs.map { it.name })
        assertEquals(10L, f.session.queue.value.current!!.id)
        f.repo.queue = f.repo.queue.copy(songIds = listOf(20, 10, 30), playMode = "RANDOM", randomSongIds = listOf(30, 10, 20))
        advanceTimeBy(1000); runCurrent()
        assertEquals(listOf(20L, 10L, 30L), f.session.queue.value.songs.map { it.id })
        assertEquals(1, f.session.queue.value.index)
        assertEquals(PlaybackMode.SHUFFLE, f.session.queue.value.mode)
        assertEquals(listOf(10L), f.player.loaded)
        f.session.disconnect(); runCurrent()
        assertEquals(original, f.player.queue.state.value)
        assertTrue(f.session.queue.value.songs.isEmpty())
    }

    @Test fun enqueueAndModeChangesReportSharedListsWithoutRestartingTheCurrentSong() = runTest {
        val f = Fixture(this); f.session.resume(); runCurrent()
        f.session.enqueue(Song(30, "新歌")); runCurrent()
        assertEquals(listOf(10L, 20L, 30L), f.repo.reportedQueues.last().songIds)
        assertTrue(f.repo.playbackCommands.isEmpty())
        f.session.setQueueMode(PlaybackMode.SHUFFLE); runCurrent()
        val random = f.repo.reportedQueues.last()
        assertEquals(random.songIds.toSet(), random.randomSongIds.toSet())
        assertEquals(PlaybackMode.SHUFFLE, f.session.queue.value.mode)
        assertEquals(listOf(10L), f.player.loaded)
        f.session.selectSong(20); runCurrent()
        assertEquals(20L, f.repo.playbackCommands.last().targetSongId)
        assertEquals("GOTO", f.repo.playbackCommands.last().commandType)
        assertEquals(20L, f.session.queue.value.current!!.id)
    }

    @Test fun existingAddNextActionInsertsIntoSharedQueueWithoutChangingPlayback() = runTest {
        val f = Fixture(this); f.session.resume(); runCurrent()
        f.session.enqueue(Song(30, "下一首添加"), next = true); runCurrent()
        assertEquals(listOf(10L, 30L, 20L), f.repo.reportedQueues.last().songIds)
        assertTrue(f.repo.playbackCommands.isEmpty())
        assertEquals(10L, f.player.state.value.song!!.id)
    }

    @Test fun removalRefreshesPeerChangesAndOnlyChangesPlaybackWhenRemovingCurrentSong() = runTest {
        val f = Fixture(this); f.session.resume(); runCurrent()
        f.repo.queue = f.repo.queue.copy(songIds = listOf(10, 20, 30), version = listOf(ListenTogetherVersion(100, 1), ListenTogetherVersion(200, 9)))
        f.session.removeSong(20); runCurrent()
        assertEquals(listOf(10L, 30L), f.repo.reportedQueues.last().songIds)
        assertEquals(9L, f.repo.reportedQueues.last().version.first { it.userId == 200L }.version)
        assertTrue(f.repo.playbackCommands.isEmpty())
        f.session.removeSong(10); runCurrent()
        assertEquals(listOf(30L), f.session.queue.value.songs.map { it.id })
        assertEquals(30L, f.repo.playbackCommands.last().targetSongId)
        assertEquals(0, f.repo.leaves)
    }

    @Test fun rejectedQueueChangeKeepsDisplayedSongsAndClearingKeepsTheRoomOpen() = runTest {
        val f = Fixture(this); f.session.resume(); runCurrent()
        f.repo.queueFailure = ApiException(ErrorKind.Server)
        f.session.removeSong(20); runCurrent()
        assertEquals(listOf(10L, 20L), f.session.queue.value.songs.map { it.id })
        assertNotNull(f.session.state.value.error)
        f.repo.queueFailure = null
        f.session.clearQueue(); runCurrent()
        assertTrue(f.repo.reportedQueues.last().songIds.isEmpty())
        assertEquals("PAUSE", f.repo.playbackCommands.last().commandType)
        assertTrue(f.session.queue.value.songs.isEmpty())
        assertNull(f.player.state.value.song)
        assertTrue(f.session.state.value.active)
        assertEquals(0, f.repo.leaves)
        advanceTimeBy(2000); runCurrent()
        assertNull(f.player.state.value.song) // An old recovery playCommand must not resurrect the removed song.
        f.session.enqueue(Song(99, "继续")); runCurrent()
        f.session.selectSong(99); runCurrent()
        assertEquals(99L, f.player.state.value.song!!.id)
    }

    @Test fun staleSongSelectionDoesNotAddOrPlayARemovedSong() = runTest {
        val f = Fixture(this); f.session.resume(); runCurrent()
        f.repo.queue = f.repo.queue.copy(songIds = listOf(10))
        f.session.selectSong(20); runCurrent()
        assertTrue(f.repo.reports.isEmpty())
        assertNotNull(f.session.state.value.error)
    }

    @Test fun explicitRefreshRecoversAChangedDeviceWithAResetSequence() = runTest {
        val f = Fixture(this); f.repo.playback = f.repo.playback!!.copy(serverSeq = 100)
        f.session.resume(); runCurrent()
        f.repo.playback = f.repo.playback!!.copy(targetSongId = 20, serverSeq = 1, clientSeq = 1)
        f.session.refresh(); runCurrent()
        assertEquals(20L, f.player.state.value.song!!.id)
        assertEquals(2, f.repo.syncNotices)
        assertTrue(f.repo.reports.isEmpty()) // Recovery never takes over the peer's transport.
        val loads = f.player.loaded.size
        advanceTimeBy(3000); runCurrent()
        assertEquals(loads, f.player.loaded.size)
        assertTrue(f.player.corrections.isEmpty())
    }

    @Test fun startupRestoresInterruptedRoomAndLeavesAEndedRoomAlone() = runTest {
        val f = Fixture(this); f.original()
        f.session.restoreAutomatically { true }; runCurrent()
        assertTrue(f.session.state.value.active)
        assertEquals(1, f.repo.restores)
        assertEquals(10L, f.player.state.value.song!!.id)
        f.session.disconnect()
        f.repo.inRoom = false
        f.session.restoreAutomatically { true }; runCurrent()
        f.assertLocal()
        assertNull(f.session.state.value.error)
        assertEquals(1L, f.player.queue.state.value.current!!.id)
        assertEquals(0, f.repo.leaves)
    }

    @Test fun userPlaybackWhileStartupLookupIsPendingCancelsAutomaticRecovery() = runTest {
        val f = Fixture(this); f.original()
        val release = CompletableDeferred<Unit>()
        var eligible = true
        f.repo.statusAction = { release.await() }
        f.session.restoreAutomatically { eligible }; runCurrent()
        eligible = false
        release.complete(Unit); runCurrent()
        f.assertLocal()
        assertEquals(0, f.repo.restores)
        assertEquals(0, f.player.begins)
        assertEquals(1L, f.player.queue.state.value.current!!.id)
    }

    @Test fun manualCutsPublishTheSharedListBeforeTheDesktopPlaybackCommand() = runTest {
        val f = Fixture(this); f.session.resume(); runCurrent()
        f.session.next(); runCurrent()
        assertEquals(listOf("queue" to 1, "play" to 2), f.repo.reports)
        assertEquals(listOf(10L, 20L), f.repo.reportedQueues.last().songIds)
        assertEquals(20L, f.repo.playbackCommands.last().targetSongId)
        f.session.selectSong(10); runCurrent()
        assertEquals(listOf("queue" to 3, "play" to 4), f.repo.reports.takeLast(2))
        assertEquals(10L, f.repo.playbackCommands.last().targetSongId)
    }
}
