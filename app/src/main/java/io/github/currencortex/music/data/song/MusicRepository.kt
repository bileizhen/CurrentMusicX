package io.github.currencortex.music.data.song

import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.core.network.*
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class MusicRepository(private val api: ApiClient) {
    suspend fun search(keyword: String, offset: Int = 0): AppResult<SearchPage> = appResult {
        val result = api.get<SearchDto>("ncm/search", mapOf("keywords" to keyword, "offset" to "$offset", "limit" to "30", "type" to "song"))
        SearchPage(result.songs.map(SongDto::toDomain), result.totals.song, result.hasMore.song)
    }
    suspend fun detail(id: Long): Song = api.get<SongDetailDto>("ncm/song/detail", mapOf("ids" to "$id")).songs.first().toDomain()
    suspend fun lyrics(id: Long): AppResult<List<LyricLine>> = appResult {
        api.get<LyricDto>("ncm/lyric", mapOf("id" to "$id")).lines.filter { it.txt.isNotBlank() }.sortedBy { it.t }
    }
    suspend fun source(id: Long, quality: AudioQuality, session: RequestSession? = null): AudioSource {
        val query = mapOf("id" to "$id", "level" to quality.value)
        val dto = if (session == null) api.get<SongUrlDto>("ncm/song/url", query)
            else ApiJson.decodeFromJsonElement<SongUrlDto>(api.request("GET", "ncm/song/url", query,
                authenticated = true, expectedSession = session))
        val url = dto.url?.toHttpUrlOrNull() ?: throw ApiException(ErrorKind.NotFound)
        return AudioSource(url.toString(), dto.level, maxOf(dto.sr, dto.sampleRate), maxOf(dto.ch, dto.channelCount), dto.type, dto.md5)
    }
}
