package io.github.currencortex.music.core.media

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.currencortex.music.data.song.Song

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object MediaItemFactory {
    fun create(song: Song, url: String, cacheKey: String? = null) = MediaItem.Builder().setMediaId(song.id.toString()).setUri(url)
        .setCustomCacheKey(cacheKey)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(song.name).setArtist(song.artists).setAlbumTitle(song.album)
            .setArtworkUri(song.cover.takeIf { it.isNotBlank() }?.let(Uri::parse)).build()).build()
}
