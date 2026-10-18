package io.github.currencortex.music.feature.share

import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song

internal data class SongShareContent(val title: String, val artists: String, val publicUrl: String?) {
    val text: String get() = listOf("分享歌曲《$title》", artists, publicUrl.orEmpty())
        .filter(String::isNotBlank).joinToString("\n")
    val filename: String get() = title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
        .take(64).trim().ifBlank { "歌曲分享" } + ".png"

    companion object {
        fun from(song: Song): SongShareContent {
            fun clean(value: String) = value.replace(Regex("[\\r\\n\\t]+"), " ").trim()
            // IDs from other sources and private Navidrome credentials are never public song URLs.
            val id = if (!song.video && song.musicSource == MusicSource.NETEASE && song.navidrome == null)
                (song.externalIds.neteaseId?.toLongOrNull() ?: song.id).takeIf { it > 0 } else null
            return SongShareContent(clean(song.name).ifBlank { "未命名歌曲" },
                clean(song.artists).ifBlank { "未知歌手" },
                id?.let { "https://music.20110208.xyz/app/#/song/$it" })
        }
    }
}
