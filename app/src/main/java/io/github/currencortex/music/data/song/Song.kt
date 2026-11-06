package io.github.currencortex.music.data.song

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable data class Song(val id: Long, val name: String, val artists: String = "",
                             val album: String = "", val cover: String = "", val durationMs: Long = 0, val mv: Long = 0,
                             val artistIds: List<Long> = emptyList(), val video: Boolean = false,
                             val musicSource: MusicSource = MusicSource.NETEASE,
                             val externalIds: SongExternalIds = SongExternalIds(),
                             val navidrome: NavidromeTrackRef? = null, val videoQuality: Int = 1080, val localUri: String = "", val emby: EmbyTrackRef? = null) {
    val privateLibrary get() = musicSource == MusicSource.NAVIDROME || musicSource == MusicSource.EMBY
    val privateIdentity get() = emby?.identity ?: navidrome?.identity
    val originalQualityLabel get() = if (musicSource == MusicSource.EMBY) "Emby 原音质" else "Navidrome 原音质"
}
@Serializable enum class MusicSource { NETEASE, QQ_MUSIC, APPLE_MUSIC, SPOTIFY, NAVIDROME, LOCAL, EMBY }
@Serializable data class NavidromeTrackRef(val id: String, val identity: String, val format: String = "",
    val sampleRate: Int = 0, val channels: Int = 0)
@Serializable data class SongExternalIds(val neteaseId: String? = null, val qqMusicId: String? = null,
    val appleMusicId: String? = null, val spotifyId: String? = null)
@Serializable data class LyricContextDto(val t: Long? = null, val txt: String = "", val matched: Boolean = false)
@Serializable data class SongDto(@SerialName("ncm_id") val id: Long, val name: String, val artists: String = "",
                                val album: String = "", val pic: String = "", val duration: Long = 0, val mv: Long = 0,
                                @SerialName("artist_ids") val artistIds: List<Long> = emptyList(), val lyric: String = "",
                                @SerialName("lyricMatch") val lyricMatch: String = "",
                                @SerialName("lyricContext") val lyricContext: List<LyricContextDto> = emptyList(),
                                val lyricContextStatus: String? = null, val lyricContextRetryable: Boolean = false) {
    fun toDomain() = Song(id, name, artists, album, pic.replace("http:", "https:"), duration, mv, artistIds)
}
@Serializable data class SearchDto(val songs: List<SongDto> = emptyList(), val totals: SearchTotals = SearchTotals(), val hasMore: SearchMore = SearchMore(),
    val catalogSongs: List<SongDto> = emptyList())
@Serializable data class SearchTotals(val song: Int = 0)
@Serializable data class SearchMore(val song: Boolean = false)
/** One line of a lyric-search hit with its immediate neighbours, when the source provides them. */
data class SearchLyricContext(val before: String?, val matched: String, val after: String?,
    val status: String? = null, val retryable: Boolean = false)
enum class SearchBackend { PROJECT, NATIVE }
data class SearchPage(val songs: List<Song>, val total: Int, val hasMore: Boolean,
    val matchedLyrics: Map<Long, SearchLyricContext> = emptyMap(), val upstreamCount: Int = songs.size,
    val backend: SearchBackend? = null)
@Serializable data class SongDetailDto(val songs: List<SongDto> = emptyList())
@Serializable data class SongUrlDto(val url: String? = null, val type: String = "", val level: String = "",
                                  val sr: Int = 0, val ch: Int = 0, val sampleRate: Int = 0, val channelCount: Int = 0, val md5: String = "",
                                  val expi: Long = 0, val source: String = "", val requestedLevel: String = "",
    val matchSource: String = "", val br: Long = 0, val bits: Int = 0, val size: Long = 0,
    val durationMs: Long = 0, val expectedDurationMs: Long = 0)
@Serializable data class AudioSource(val url: String, val level: String, val sampleRate: Int, val channelCount: Int, val format: String = "", val md5: String = "",
                                    val validForSeconds: Long = 30, val source: String = "", val requestedLevel: String = "",
    val matchSource: String = "", val bitrate: Long = 0, val bits: Int = 0, val size: Long = 0,
    val durationMs: Long = 0, val expectedDurationMs: Long = 0) {
    val matched get() = level == "matched" || source == "netease-match"
    val qualityLabel get() = if (matched) "匹配音源" else
        io.github.currencortex.music.core.media.AudioQuality.entries.firstOrNull { it.value == level }?.label ?: "未知音质"
    // Without a file digest, two signed URLs may point to different recordings.
    val cacheIdentity: String get() = if (!matched) "" else md5.takeIf { it.matches(Regex("[a-fA-F0-9]{32}")) }
        ?.lowercase() ?: java.security.MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    val highSpec get() = sampleRate > 48000 || channelCount > 2 ||
        // Some audio providers omit numeric specifications for these high-spec levels.
        ((sampleRate <= 0 || channelCount <= 0) && level in setOf("hires", "jymaster", "sky", "jyeffect"))
}
@Serializable data class LyricDto(val lines: List<LyricLine> = emptyList())
@Serializable data class LyricLine(val t: Long, val txt: String, val trans: String = "")

@Serializable data class EmbyTrackRef(val id: String, val identity: String, val mediaSourceId: String = "",
    val format: String = "", val sampleRate: Int = 0, val channels: Int = 0)
