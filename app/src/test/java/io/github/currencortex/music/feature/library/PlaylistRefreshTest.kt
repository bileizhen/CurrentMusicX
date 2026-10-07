package io.github.currencortex.music.feature.library

import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import org.junit.Assert.*
import org.junit.Test

class PlaylistRefreshTest {
    @Test fun insertingAndSortingDoNotChangeExistingRowKeys() {
        val a = Song(1, "A")
        val b = Song(2, "B")
        val c = Song(3, "C")
        val before = playlistSongKeys(listOf(a, b))
        assertEquals(before, playlistSongKeys(listOf(c, a, b)).drop(1))
        assertEquals(before.reversed(), playlistSongKeys(listOf(b.copy(name = "Edited"), a)))
    }

    @Test fun identicalRefreshKeepsTheListAndSongInstances() {
        val songs = listOf(Song(1, "A"), Song(2, "B"))
        val before = LibraryDetailState(songs = songs, loaded = true)
        val fetched = before.copy(songs = songs.map { it.copy() })
        val merged = before.mergeFetched(fetched)
        assertSame(songs, merged.songs)
        assertSame(songs[0], merged.songs[0])
    }

    @Test fun refreshMergesAdditionsEditsRemovalsAndServerOrder() {
        val a = Song(1, "A")
        val b = Song(2, "B")
        val removed = Song(3, "Removed")
        val added = Song(4, "New")
        val edited = b.copy(album = "Updated album")
        val merged = LibraryDetailState(songs = listOf(a, b, removed)).mergeFetched(
            LibraryDetailState(songs = listOf(added, edited, a.copy())))
        assertEquals(listOf(added, edited, a), merged.songs)
        assertSame(a, merged.songs[2])
        assertSame(edited, merged.songs[1])
    }

    @Test fun duplicateTracksAndDifferentProvidersHaveUniqueStableKeys() {
        val a = Song(1, "A")
        val songs = listOf(a, a.copy(), a.copy(musicSource = MusicSource.QQ_MUSIC), a.copy(video = true))
        val keys = playlistSongKeys(songs)
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(keys, playlistSongKeys(listOf(Song(99, "New")) + songs).drop(1))
    }
}
