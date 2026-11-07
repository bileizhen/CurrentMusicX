package io.github.currencortex.music.core.media

import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class ListeningHistoryDay(val date: LocalDate, val durationMs: Long, val songCount: Int)

/** Display metadata only: no playback URL, account token or private-library reference. */
data class ListeningHistorySong(val key: String, val name: String, val artists: String,
    val album: String, val cover: String, val durationMs: Long)

data class ListeningHistoryReport(val start: LocalDate, val endInclusive: LocalDate,
    val days: List<ListeningHistoryDay>, val songs: List<ListeningHistorySong>, val songCount: Int,
    val hourlyMs: List<Long>, val allTimeMs: Long, val firstRecordedDay: LocalDate?,
    val detailedDurationMs: Long) {
    val durationMs: Long get() = days.sumOf { it.durationMs }
    val listeningDays: Int get() = days.count { it.durationMs > 0 }
}

internal data class ListeningHistorySlice(val day: String, val hour: Int, val durationMs: Long)

/** Real elapsed intervals, split at local hour boundaries, including repeated/missing DST hours. */
internal fun listeningHistorySlices(durationMs: Long, endedAt: Long, zone: ZoneId): List<ListeningHistorySlice> {
    if (durationMs <= 0) return emptyList()
    val result = mutableListOf<ListeningHistorySlice>()
    var start = Math.subtractExact(endedAt, durationMs)
    while (start < endedAt) {
        val local = Instant.ofEpochMilli(start).atZone(zone)
        // Resolve the next wall-clock hour, rather than adding one elapsed hour:
        // half-hour DST changes (e.g. Lord Howe) must still split at 03:00.
        val boundary = local.toLocalDateTime().truncatedTo(ChronoUnit.HOURS).plusHours(1)
            .atZone(zone).toInstant().toEpochMilli()
        val end = minOf(endedAt, boundary)
        result += ListeningHistorySlice(local.toLocalDate().toString(), local.hour, end - start)
        start = end
    }
    return result
}

internal fun listeningHistorySong(song: Song): ListeningHistorySong {
    val identity = when (song.musicSource) {
        MusicSource.NAVIDROME -> listOf(song.navidrome?.identity.orEmpty(), song.navidrome?.id.orEmpty())
        MusicSource.EMBY -> listOf(song.emby?.identity.orEmpty(), song.emby?.id.orEmpty())
        MusicSource.LOCAL -> listOf(song.localUri.ifBlank { song.id.toString() })
        MusicSource.NETEASE -> listOf(song.id.toString())
        MusicSource.QQ_MUSIC -> listOf(song.externalIds.qqMusicId?.takeIf { it.isNotBlank() } ?: song.id.toString())
        MusicSource.APPLE_MUSIC -> listOf(song.externalIds.appleMusicId?.takeIf { it.isNotBlank() } ?: song.id.toString())
        MusicSource.SPOTIFY -> listOf(song.externalIds.spotifyId?.takeIf { it.isNotBlank() } ?: song.id.toString())
    }
    // Length-prefix fields so different provider/account/track combinations cannot alias.
    val raw = (listOf(song.musicSource.name) + identity).joinToString("") { "${it.length}:$it" }
    val key = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    val cover = if (song.privateLibrary) "" else safeListeningCover(song.cover)
    return ListeningHistorySong(key, song.name, song.artists, song.album, cover, 0)
}

private fun safeListeningCover(value: String): String = runCatching {
    val uri = URI(value)
    // Never persist signed query parameters, user-info, local paths or private content URIs.
    if (uri.scheme !in setOf("https", "http") || uri.host.isNullOrBlank() || uri.userInfo != null) ""
    else URI("https", null, uri.host, uri.port, uri.path, null, null).toASCIIString()
}.getOrDefault("")
