package io.github.currencortex.music.data.song

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable data class Song(val id: Long, val name: String, val artists: String = "",
                             val album: String = "", val cover: String = "", val durationMs: Long = 0, val mv: Long = 0,
                             val artistIds: List<Long> = emptyList(), val video: Boolean = false)
@Serializable data class SongDto(@SerialName("ncm_id") val id: Long, val name: String, val artists: String = "",
                                val album: String = "", val pic: String = "", val duration: Long = 0, val mv: Long = 0,
                                @SerialName("artist_ids") val artistIds: List<Long> = emptyList()) {
    fun toDomain() = Song(id, name, artists, album, pic.replace("http:", "https:"), duration, mv, artistIds)
}
@Serializable data class SearchDto(val songs: List<SongDto> = emptyList(), val totals: SearchTotals = SearchTotals(), val hasMore: SearchMore = SearchMore())
@Serializable data class SearchTotals(val song: Int = 0)
@Serializable data class SearchMore(val song: Boolean = false)
data class SearchPage(val songs: List<Song>, val total: Int, val hasMore: Boolean)
@Serializable data class SongDetailDto(val songs: List<SongDto> = emptyList())
@Serializable data class SongUrlDto(val url: String? = null, val type: String = "", val level: String = "",
                                  val sr: Int = 0, val ch: Int = 0, val sampleRate: Int = 0, val channelCount: Int = 0, val md5: String = "")
@Serializable data class AudioSource(val url: String, val level: String, val sampleRate: Int, val channelCount: Int, val format: String = "", val md5: String = "") {
    val highSpec get() = sampleRate > 48000 || channelCount > 2 ||
        // Some audio providers omit numeric specifications for these high-spec levels.
        ((sampleRate <= 0 || channelCount <= 0) && level in setOf("hires", "jymaster", "sky", "jyeffect"))
}
@Serializable data class LyricDto(val lines: List<LyricLine> = emptyList())
@Serializable data class LyricLine(val t: Long, val txt: String, val trans: String = "")
