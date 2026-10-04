package io.github.currencortex.music.feature.lyrics.domain

import io.github.currencortex.music.data.song.*

data class LyricsKey(val source: MusicSource, val songId: String) {
    init { require(songId.matches(Regex("[A-Za-z0-9_-]{1,128}"))) }
    val cacheName get() = "${source.name.lowercase(java.util.Locale.ROOT)}_$songId.ttml"
}

object LyricsMatcher {
    fun candidates(song: Song): List<LyricsKey> {
        val ids = song.externalIds
        val values = linkedMapOf(MusicSource.NETEASE to (ids.neteaseId ?: song.id.takeIf { it > 0 && song.musicSource == MusicSource.NETEASE }?.toString()),
            MusicSource.QQ_MUSIC to ids.qqMusicId, MusicSource.APPLE_MUSIC to ids.appleMusicId, MusicSource.SPOTIFY to ids.spotifyId)
        return (listOf(song.musicSource) + MusicSource.entries).distinct().mapNotNull { source ->
            values[source]?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,128}")) && it != "0" }?.let { LyricsKey(source, it) }
        }
    }
    fun matches(documentIds: Map<String, List<String>>, key: LyricsKey): Boolean {
        val field = when (key.source) { MusicSource.NETEASE -> "ncmMusicId"; MusicSource.QQ_MUSIC -> "qqMusicId"; MusicSource.APPLE_MUSIC -> "appleMusicId"; MusicSource.SPOTIFY -> "spotifyId" }
        // Older files omit metadata. When present, the id must agree with the indexed path.
        return documentIds[field].let { it.isNullOrEmpty() || key.songId in it }
    }
}
