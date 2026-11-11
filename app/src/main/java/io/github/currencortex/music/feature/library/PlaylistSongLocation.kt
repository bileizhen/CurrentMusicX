package io.github.currencortex.music.feature.library

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.SongLyricsStore
import kotlinx.coroutines.flow.SharedFlow

/** Only the active playlist observes requests from the mini player. */
internal val LocalCurrentSongLocateRequests = staticCompositionLocalOf<SharedFlow<Unit>?> { null }

internal fun playlistCurrentSongIndex(songs: List<Song>, current: Song?): Int {
    if (current == null) return -1
    val identity = SongLyricsStore.identity(current)
    return songs.indexOfFirst { it.video == current.video && SongLyricsStore.identity(it) == identity }
}
