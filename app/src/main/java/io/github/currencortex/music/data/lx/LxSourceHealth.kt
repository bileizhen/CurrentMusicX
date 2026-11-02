package io.github.currencortex.music.data.lx

import io.github.currencortex.music.core.network.SourceAvailabilityStatus

data class LxSourceHealth(val status: SourceAvailabilityStatus = SourceAvailabilityStatus.UNKNOWN,
    val checkedAtMs: Long = 0, val message: String? = null) {
    fun fresh(now: Long): Boolean = status !in setOf(SourceAvailabilityStatus.UNKNOWN, SourceAvailabilityStatus.CHECKING) &&
        now >= checkedAtMs && now - checkedAtMs < (if (status == SourceAvailabilityStatus.AVAILABLE) 600_000 else 60_000)
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
