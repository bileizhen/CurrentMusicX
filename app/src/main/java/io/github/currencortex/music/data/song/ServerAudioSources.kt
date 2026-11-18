package io.github.currencortex.music.data.song

import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.core.network.ApiClient
import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import io.github.currencortex.music.core.network.RequestSession
import kotlinx.serialization.Serializable

internal const val SERVER_AUDIO_SOURCE_IDENTITY_PREFIX = "server-source:"
private val audioSourceId = Regex("[a-z][a-z0-9-]{0,63}")

@Serializable
data class ServerAudioSource(val id: String, val name: String, val platforms: List<String>) {
    internal fun supported() = id.matches(audioSourceId) && name.isNotBlank() &&
        name.length <= 80 && name.none(Char::isISOControl) && "netease" in platforms
}

@Serializable
internal data class ServerAudioSourceCatalog(val version: Int, val sources: List<ServerAudioSource>) {
    fun supportedSources(): List<ServerAudioSource> {
        if (version != 1) throw ApiException(ErrorKind.Parse, userMessage = "当前 App 不支持此音源协议版本")
        return sources.filter(ServerAudioSource::supported).distinctBy(ServerAudioSource::id)
    }
}

@Serializable
data class ServerAudioSourceOwner(val server: String, val accountId: Long)

@Serializable
internal data class ImportedServerAudioSource(val owner: ServerAudioSourceOwner, val source: ServerAudioSource)

class ServerAudioSources(private val api: ApiClient) {
    suspend fun catalog(session: RequestSession): List<ServerAudioSource> {
        val value = api.request("GET", "audio-sources", authenticated = true, expectedSession = session,
            projectOnly = true, callTimeoutMillis = 10_000)
        return api.decode<ServerAudioSourceCatalog>(value).supportedSources()
    }

    suspend fun resolve(source: ServerAudioSource, id: Long, quality: AudioQuality, session: RequestSession,
        refresh: Boolean, supportsDolby: Boolean): SongUrlDto {
        require(source.supported())
        val query = buildMap {
            put("id", id.toString())
            put("level", quality.value)
            if (refresh) put("refresh", "1")
            if (quality == AudioQuality.AUTO && supportsDolby) put("supportsDolby", "1")
        }
        val value = api.request("GET", "audio-sources/${source.id}/resolve", query, authenticated = true,
            expectedSession = session, projectOnly = true)
        return api.decode(value)
    }
}
