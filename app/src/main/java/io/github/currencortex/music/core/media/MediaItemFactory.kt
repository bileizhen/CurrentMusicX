package io.github.currencortex.music.core.media

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.currencortex.music.data.song.Song

object MediaItemFactory {
    fun create(song: Song, url: String) = MediaItem.Builder().setMediaId(song.id.toString()).setUri(url)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(song.name).setArtist(song.artists).setAlbumTitle(song.album)
            .setArtworkUri(song.cover.takeIf { it.isNotBlank() }?.let(Uri::parse)).build()).build()
}
