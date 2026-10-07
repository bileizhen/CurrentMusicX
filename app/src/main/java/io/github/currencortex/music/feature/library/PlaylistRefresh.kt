package io.github.currencortex.music.feature.library

import io.github.currencortex.music.data.song.Song

/** Stable across insertion, sorting and metadata edits; repeated tracks keep distinct occurrences. */
internal fun playlistSongKeys(songs: List<Song>): List<String> {
    val occurrences = mutableMapOf<String, Int>()
    return songs.map { song ->
        val identity = "${song.musicSource}-${song.id}-${song.video}"
        val occurrence = occurrences.getOrDefault(identity, 0)
        occurrences[identity] = occurrence + 1
        "$identity-$occurrence"
    }
}

/** Keep unchanged rows (and an unchanged list) while accepting server additions, edits and removals. */
internal fun LibraryDetailState.mergeFetched(fetched: LibraryDetailState): LibraryDetailState {
    if (songs == fetched.songs) return fetched.copy(songs = songs, playlist = fetched.playlist?.copy(songs = songs))
    val old = playlistSongKeys(songs).zip(songs).toMap()
    val merged = playlistSongKeys(fetched.songs).zip(fetched.songs).map { (key, song) ->
        old[key]?.takeIf { it == song } ?: song
    }
    return fetched.copy(songs = merged, playlist = fetched.playlist?.copy(songs = merged))
}
