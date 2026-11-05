package io.github.currencortex.music.core.media

import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.data.song.AudioSource
import kotlinx.serialization.Serializable

/** Public playback metadata only: never place signed URLs or credentials in a media session. */
@Serializable data class AudioStreamInfo(val label: String, val matched: Boolean = false,
    val format: String = "", val bitrate: Long = 0, val sampleRate: Int = 0, val channels: Int = 0) {
    val summary get() = listOfNotNull(label, format.uppercase().takeIf { it.isNotBlank() },
        bitrate.takeIf { it > 0 }?.let { "${it / 1000} kbps" },
        sampleRate.takeIf { it > 0 }?.let { "$it Hz" }, channels.takeIf { it > 0 }?.let { "$it 声道" }).joinToString(" · ")
    companion object {
        fun from(source: AudioSource) = AudioStreamInfo(source.qualityLabel, source.matched,
            source.format, source.bitrate, source.sampleRate, source.channelCount)
        fun decode(value: String?): AudioStreamInfo? = value?.let {
            runCatching { ApiJson.decodeFromString(serializer(), it) }.getOrNull()
        }
    }
}
