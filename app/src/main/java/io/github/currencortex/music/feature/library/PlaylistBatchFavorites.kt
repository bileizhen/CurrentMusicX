package io.github.currencortex.music.feature.library

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song

internal fun favoriteSelectionKey(song: Song) = "${song.musicSource}:${song.id}:${song.video}"

internal fun supportsBatchFavorite(song: Song) = !song.video && !song.privateLibrary && song.musicSource != MusicSource.LOCAL

internal fun playlistSupportsMultiSelect(route: String) = route.startsWith("lib/playlist/") ||
    route.startsWith("lib/ncmplaylist/") || route == "lib/likes"

data class BatchFavoriteState(val running: Boolean = false, val completed: Int = 0,
    val total: Int = 0, val error: String? = null)

/** Stop at the first failed write. Retrying repeats the same explicit desired state. */
internal suspend fun setBatchFavorites(songs: List<Song>, liked: Boolean,
    checkOwner: suspend () -> Unit, write: suspend (Song, Boolean) -> Unit,
    progress: (Int) -> Unit): AppResult<Unit> = appResult {
    val unique = songs.distinctBy(::favoriteSelectionKey)
    require(unique.all(::supportsBatchFavorite))
    unique.forEachIndexed { index, song ->
        checkOwner()
        write(song, liked)
        checkOwner()
        progress(index + 1)
    }
}
