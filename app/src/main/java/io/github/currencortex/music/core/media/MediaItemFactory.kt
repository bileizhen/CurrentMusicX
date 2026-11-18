package io.github.currencortex.music.core.media

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.currencortex.music.data.song.Song

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object MediaItemFactory {
    fun create(song: Song, url: String, cacheKey: String? = null, source: io.github.currencortex.music.data.song.AudioSource? = null,
        autoDolby: Boolean = false) = MediaItem.Builder().setMediaId(song.id.toString()).setUri(url)
        .setCustomCacheKey(cacheKey)
        .setMediaMetadata(MediaMetadata.Builder().setExtras(source?.let { android.os.Bundle().apply {
            putString("cm.audio.info", io.github.currencortex.music.core.network.ApiJson.encodeToString(
                AudioStreamInfo.serializer(), AudioStreamInfo.from(it)))
            it.source.takeIf { value -> value.startsWith("lx:") }?.removePrefix("lx:")?.let { id -> putString("cm.lx.source", id) }
            if (autoDolby) putBoolean("cm.audio.autoDolby", true)
        } }).setTitle(song.name).setArtist(song.artists).setAlbumTitle(song.album)
            .setArtworkUri(song.cover.takeIf { it.isNotBlank() }?.let(Uri::parse)).build()).build()
}

internal val MediaItem.autoDolby: Boolean get() = mediaMetadata.extras?.getBoolean("cm.audio.autoDolby") == true
internal val MediaItem.lxSourceId: String? get() = mediaMetadata.extras?.getString("cm.lx.source")

internal fun ResolvedAudio.toMediaItem(song: Song, autoDolby: Boolean) =
    MediaItemFactory.create(song, source.url, key.takeIf(String::isNotBlank), source, autoDolby)
