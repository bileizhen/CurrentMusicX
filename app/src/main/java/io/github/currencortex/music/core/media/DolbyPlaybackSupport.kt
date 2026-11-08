package io.github.currencortex.music.core.media

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import io.github.currencortex.music.data.settings.AudioOutputMode
import io.github.currencortex.music.data.settings.AudioOutputSettings

/** The service's sink decodes to PCM. Encoded HDMI passthrough and a Dolby effect toggle
 * do not establish that this renderer and its current output can play the source. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class DolbyPlaybackSupport(private val context: Context, attributes: AudioAttributes) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val platformAttributes = attributes.platformAudioAttributes
    private val hasDecoder by lazy {
        listOf(MimeTypes.AUDIO_E_AC3_JOC, MimeTypes.AUDIO_E_AC3).any { mimeType ->
            runCatching {
                val format = Format.Builder().setSampleMimeType(mimeType)
                    .setSampleRate(SOURCE_RATE).setChannelCount(CHANNELS).build()
                MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, false, false).any { it.isFormatSupported(context, format) }
            }.getOrDefault(false)
        }
    }

    fun supports(mode: PlayerMode, output: AudioOutputSettings): Boolean = canPreferDolby(mode, output.mode,
        routeSupportsPcm = { Build.VERSION.SDK_INT >= 33 && runCatching { supportsRoutedPcm(output) }.getOrDefault(false) },
        decoderAvailable = { hasDecoder })

    @RequiresApi(33)
    private fun supportsRoutedPcm(output: AudioOutputSettings): Boolean {
        val device = manager.getAudioDevicesForAttributes(platformAttributes).singleOrNull() ?: return false
        if (device.type == AudioDeviceInfo.TYPE_REMOTE_SUBMIX || device.type == AudioDeviceInfo.TYPE_UNKNOWN) return false
        // AudioTrack's preferred device is only a hint. Require it to agree with the system route.
        if (output.deviceKey.isNotBlank() && device.outputKey() != output.deviceKey) return false
        val rate = output.sampleRate.takeIf { it > 0 } ?: SOURCE_RATE
        val pcm = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_5POINT1).setSampleRate(rate).build()
        val multichannel = device.audioProfiles.any { profile ->
            profile.format == pcm.encoding && pcm.channelMask in profile.channelMasks && rate in profile.sampleRates
        }
        // This is a check for a usable PCM path. It is not evidence of Atmos rendering.
        val spatializer = manager.spatializer
        return multichannel || spatializer.isAvailable && spatializer.isEnabled && spatializer.canBeSpatialized(platformAttributes, pcm)
    }

    private companion object {
        const val SOURCE_RATE = 48_000
        const val CHANNELS = 6
    }
}

internal fun canPreferDolby(mode: PlayerMode, output: AudioOutputMode,
    routeSupportsPcm: () -> Boolean, decoderAvailable: () -> Boolean): Boolean =
    mode == PlayerMode.LOCAL && output == AudioOutputMode.AUDIO_TRACK && routeSupportsPcm() && decoderAvailable()

internal data class DolbyResume(val positionMs: Long, val play: Boolean)

internal fun dolbyResume(autoDolby: Boolean, sameTrack: Boolean, state: PlayerState, quality: AudioQuality,
    positionMs: Long, play: Boolean): DolbyResume? {
    if (!autoDolby || !sameTrack || state.mode != PlayerMode.LOCAL || state.resolving || quality != AudioQuality.AUTO) return null
    return DolbyResume(positionMs.coerceAtLeast(0), play)
}

internal fun Player.clearForDolbyRetry() {
    // Removing the only prepared item can signal STATE_ENDED and advance the queue.
    stop()
    clearMediaItems()
}

internal fun isDolbyPlaybackFailure(errorCode: Int): Boolean =
    when (errorCode) {
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> true
        else -> false
    }
