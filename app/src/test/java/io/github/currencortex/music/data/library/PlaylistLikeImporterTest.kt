package io.github.currencortex.music.data.library

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.song.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistLikeImporterTest {
    private val session = RequestSession("https://example.test/cm/", "test")
    private fun playlist(songs: List<Song>, owner: Long = 7, source: String = "") =
        Playlist(3, "自建歌单", "", "", source, owner, songs.size, songs)

    @Test fun skipsExistingDuplicatesAndUnsupportedTracksAndRetryIsIdempotent() = runTest {
        val songs = listOf(Song(1, "已有"), Song(2, "新增"), Song(2, "重复"), Song(4, "视频", video = true),
            Song(5, "QQ", musicSource = MusicSource.QQ_MUSIC),
            Song(6, "映射", externalIds = SongExternalIds(neteaseId = "20")))
        val liked = mutableSetOf(1L)
        val written = mutableListOf<Long>()
        val importer = PlaylistLikeImporter(backgroundScope, { session }, { 7 }, { playlist(songs) },
            { PlaylistLikeTarget(9, liked.toSet()) }, { song, expected, uid ->
                assertEquals(session, expected); assertEquals(9L, uid)
                val id = NeteaseSongActionsRepository.songId(song)!!
                written += id; liked += id
            }, pause = { yield() })
        importer.start(3, "自建歌单"); runCurrent()
        assertEquals(listOf(2L, 20L), written)
        assertEquals(2, importer.state.value.imported)
        assertEquals(4, importer.state.value.skipped)
        assertEquals(6, importer.state.value.processed)
        assertFalse(importer.state.value.running)
        importer.start(3, "自建歌单"); runCurrent()
        assertEquals(6, importer.state.value.skipped)
        assertEquals(2, written.size)
    }

    @Test fun unavailableSongIsReportedOnceAndOtherSongsContinue() = runTest {
        val importer = PlaylistLikeImporter(backgroundScope, { session }, { 7 },
            { playlist(listOf(Song(2, "失效"), Song(2, "重复"), Song(3, "正常"))) },
            { PlaylistLikeTarget(9, emptySet()) }, { song, _, _ ->
                if (song.id == 2L) throw ApiException(ErrorKind.NotFound)
            }, pause = {})
        importer.start(3, "歌单"); runCurrent()
        assertEquals(1, importer.state.value.imported)
        assertEquals(1, importer.state.value.failed)
        assertEquals(1, importer.state.value.skipped)
        assertEquals(listOf("失效：内容不存在"), importer.state.value.failures)
        assertNull(importer.state.value.error)
    }

    @Test fun rateLimitStopsRemainingWritesAndKeepsPartialCounts() = runTest {
        val writes = mutableListOf<Long>()
        val importer = PlaylistLikeImporter(backgroundScope, { session }, { 7 },
            { playlist((1L..3L).map { Song(it, "$it") }) }, { PlaylistLikeTarget(9, emptySet()) },
            { song, _, _ -> writes += song.id; if (song.id == 2L) throw ApiException(ErrorKind.RateLimited) }, pause = {})
        importer.start(3, "歌单"); runCurrent()
        assertEquals(listOf(1L, 2L), writes)
        assertEquals(2, importer.state.value.processed)
        assertEquals(1, importer.state.value.imported)
        assertEquals(1, importer.state.value.failed)
        assertEquals(ErrorKind.RateLimited.message, importer.state.value.error)
    }

    @Test fun sessionChangeDuringPacingStopsBeforeTheNextWrite() = runTest {
        var current = session
        val writes = mutableListOf<Long>()
        val importer = PlaylistLikeImporter(backgroundScope, { current }, { 7 },
            { playlist(listOf(Song(1, "一"), Song(2, "二"))) }, { PlaylistLikeTarget(9, emptySet()) },
            { song, _, _ -> writes += song.id }, pause = { current = RequestSession(session.server, "other") })
        importer.start(3, "歌单"); runCurrent()
        assertEquals(listOf(1L), writes)
        assertEquals(1, importer.state.value.processed)
        assertEquals(ErrorKind.NeteaseSessionChanged.message, importer.state.value.error)
    }

    @Test fun doubleTapSharesOneTaskAndStoppingDoesNotReportUnconfirmedSuccess() = runTest {
        val entered = CompletableDeferred<Unit>()
        val importer = PlaylistLikeImporter(backgroundScope, { session }, { 7 }, { playlist(listOf(Song(1, "一"))) },
            { PlaylistLikeTarget(9, emptySet()) }, { _, _, _ -> entered.complete(Unit); awaitCancellation() })
        importer.start(3, "歌单"); importer.start(4, "别的歌单"); runCurrent()
        assertTrue(entered.isCompleted)
        assertEquals(3L, importer.state.value.playlistId)
        importer.stop(); runCurrent()
        assertFalse(importer.state.value.running)
        assertEquals(0, importer.state.value.imported)
        assertEquals(0, importer.state.value.processed)
        assertTrue(importer.state.value.error!!.startsWith("已停止"))
    }

    @Test fun onlyOwnedCurrentMusicPlaylistsCanBeImported() = runTest {
        for (list in listOf(playlist(listOf(Song(1, "一")), owner = 8),
                playlist(listOf(Song(1, "一")), source = "ncm"))) {
            val importer = PlaylistLikeImporter(backgroundScope, { session }, { 7 }, { list },
                { error("Must reject before contacting the destination") }, { _, _, _ -> error("Must not write") })
            importer.start(3, "歌单"); runCurrent()
            assertEquals(ErrorKind.Forbidden.message, importer.state.value.error)
        }
    }
}
