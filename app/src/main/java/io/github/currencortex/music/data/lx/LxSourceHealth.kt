package io.github.currencortex.music.data.lx

import io.github.currencortex.music.core.network.SourceAvailabilityStatus
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.QqMusicTrackRef
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.data.song.SongExternalIds

data class LxSourceHealth(val status: SourceAvailabilityStatus = SourceAvailabilityStatus.UNKNOWN,
    val checkedAtMs: Long = 0, val message: String? = null) {
    fun fresh(now: Long): Boolean = status !in setOf(SourceAvailabilityStatus.UNKNOWN, SourceAvailabilityStatus.CHECKING) &&
        now >= checkedAtMs && now - checkedAtMs < (if (status == SourceAvailabilityStatus.AVAILABLE) 600_000 else 60_000)
}

/** Fixed test tracks keep plugin checks independent of the current queue and login. */
internal fun lxProbeSong(platform: LxPlatform): Song = when (platform) {
    LxPlatform.NETEASE -> Song(2135426631, "5:20AM", "刀酱", "5:20AM", durationMs = 142000)
    LxPlatform.QQ_MUSIC -> {
        // https://y.qq.com/n/ryqq/songDetail/002TlJS53pvjNF
        val mid = "002TlJS53pvjNF"
        Song(648474549, "Dawnlight", "VZEUS", "Dawnlight", durationMs = 96000,
            musicSource = MusicSource.QQ_MUSIC, externalIds = SongExternalIds(qqMusicId = mid),
            qqMusic = QqMusicTrackRef(mid, 648474549, "003jE4bK0y7HlO", mediaMid = mid))
    }
}

/** A successful URL/HTTP response alone may be HTML or a JSON error. Check actual audio bytes. */
internal fun lxAudioPrefix(bytes: ByteArray): Boolean {
    if (bytes.size < 16) return false
    fun ascii(offset: Int, value: String) = bytes.size >= offset + value.length &&
        value.indices.all { bytes[offset + it] == value[it].code.toByte() }
    return ascii(0, "fLaC") || ascii(0, "OggS") || ascii(4, "ftyp") ||
        ascii(0, "RIFF") && ascii(8, "WAVE") ||
        ascii(0, "ID3") && bytes[3].toInt() in 2..4 ||
        (bytes[0].toInt() and 255) == 255 && (bytes[1].toInt() and 0xE0) == 0xE0
}
