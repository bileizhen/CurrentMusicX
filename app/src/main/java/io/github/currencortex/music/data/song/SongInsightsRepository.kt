package io.github.currencortex.music.data.song

import io.github.currencortex.music.core.network.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable data class SongQualityVariant(val immerseType: String = "", val br: Long = 0, val size: Long = 0, val sr: Long = 0)
@Serializable data class SongQualityInfo(val level: String, val label: String = "", val br: Long = 0,
    val size: Long = 0, val sr: Long = 0, val variants: List<SongQualityVariant> = emptyList())
@Serializable data class SongQualities(val id: Long, val qualities: List<SongQualityInfo>, val playability: String = "unknown")
@Serializable data class ChorusSegment(val startMs: Long, val endMs: Long)
@Serializable data class SongChorus(val id: Long, val segments: List<ChorusSegment>)
@Serializable data class SongVersions(val id: Long, val songs: List<SongDto>, val playability: String = "unknown")

/** Public, project-only supplemental data. It never grants playback rights or resolves audio URLs. */
class SongInsightsRepository(private val api: ApiClient,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val timeoutMillis: Long = 5_000) {
    private data class Key(val server: String, val kind: String, val id: Long)
    private data class Cached(val value: JsonElement, val expires: Long)
    private val monitor = Any()
    private val cache = LinkedHashMap<Key, Cached>()
    // Fixed stripes bound memory; identical requests wait for the first successful result.
    private val locks = Array(16) { Mutex() }
    fun session() = api.publicSession()

    private suspend fun <T> read(song: Song, kind: String, decode: suspend (JsonElement, Long) -> T): T {
        val id = NeteaseSongActionsRepository.songId(song) ?: throw ApiException(ErrorKind.NotFound)
        val owner = session()
        val key = Key(owner.server, kind, id)
        return locks[(key.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            if (session() != owner) throw ApiException(ErrorKind.Unauthorized)
            val cached = synchronized(monitor) {
                cache.entries.removeAll { it.key.server != owner.server || it.value.expires <= now() }
                cache[key]?.value
            }
            val raw = cached ?: (withTimeoutOrNull(timeoutMillis) {
                api.request("GET", "song/$kind", mapOf("id" to "$id"),
                    authenticated = false, expectedSession = owner, projectOnly = true)
            } ?: throw ApiException(ErrorKind.Timeout))
            val value = decode(raw, id) // Missing arrays / mismatched IDs are errors, not empty results.
            if (session() != owner) throw ApiException(ErrorKind.Unauthorized)
            if (cached == null) synchronized(monitor) {
                if (cache.size >= 96) cache.remove(cache.keys.first())
                cache[key] = Cached(raw, now() + 60_000)
            }
            value
        }
    }

    suspend fun qualities(song: Song): List<SongQualityInfo> = read(song, "qualities") { raw, id ->
        val response = api.decode<SongQualities>(raw)
        checkId(response.id, id)
        response.qualities.filter { it.level.isNotBlank() }.distinctBy { it.level }.map {
            it.copy(br = it.br.coerceAtLeast(0), size = it.size.coerceAtLeast(0), sr = it.sr.coerceAtLeast(0),
                variants = it.variants.distinct().map { v -> v.copy(br = v.br.coerceAtLeast(0), size = v.size.coerceAtLeast(0), sr = v.sr.coerceAtLeast(0)) })
        }
    }
    suspend fun chorus(song: Song): List<ChorusSegment> = read(song, "chorus") { raw, id ->
        val response = api.decode<SongChorus>(raw)
        checkId(response.id, id)
        response.segments.filter { it.startMs >= 0 && it.endMs > it.startMs }
            .distinct().sortedBy { it.startMs }
    }
    suspend fun versions(song: Song): List<Song> = read(song, "versions") { raw, id ->
        val response = api.decode<SongVersions>(raw)
        checkId(response.id, id)
        response.songs.filter { it.id > 0 && it.id != id && it.name.isNotBlank() }.distinctBy { it.id }
            .map { it.toDomain().copy(externalIds = SongExternalIds(neteaseId = it.id.toString())) }
    }
    private fun checkId(actual: Long, expected: Long) {
        if (actual != expected) throw ApiException(ErrorKind.Parse)
    }
}
