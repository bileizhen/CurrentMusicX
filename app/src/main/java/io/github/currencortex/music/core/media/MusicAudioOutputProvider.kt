package io.github.currencortex.music.core.media

import android.content.Context
import android.media.AudioManager
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.*
import io.github.currencortex.music.data.settings.AudioOutputMode
import io.github.currencortex.music.data.settings.AudioOutputSettings

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class MusicAudioOutputProvider(context: Context, private val settings: () -> AudioOutputSettings,
    private val report: (String) -> Unit,
    private val fallback: AudioOutputProvider = AudioTrackAudioOutputProvider.Builder(context).build()) : AudioOutputProvider by fallback {
    private val manager = context.getSystemService(AudioManager::class.java)
    override fun setClock(clock: androidx.media3.common.util.Clock) = fallback.setClock(clock)
    override fun hasPendingReleases() = fallback.hasPendingReleases()
    override fun getFormatSupport(formatConfig: AudioOutputProvider.FormatConfig): AudioOutputProvider.FormatSupport {
        // Always decode to PCM so native output, DSP and output-rate selection share one path.
        if (formatConfig.format.sampleMimeType != MimeTypes.AUDIO_RAW) return AudioOutputProvider.FormatSupport.Builder()
            .setFormatSupportLevel(AudioOutputProvider.FORMAT_UNSUPPORTED).build()
        return fallback.getFormatSupport(formatConfig)
    }
    override fun getAudioOutput(config: AudioOutputProvider.OutputConfig): AudioOutput {
        val requested = settings()
        check(!requested.mode.rawPcm) { "${requested.mode.label} 必须通过独立的原始 PCM 输出链路" }
        if (requested.mode != AudioOutputMode.AUDIO_TRACK && config.encoding == C.ENCODING_PCM_16BIT) {
            try {
                val api = when (requested.mode) { AudioOutputMode.OPENSL_ES -> 1; AudioOutputMode.AAUDIO, AudioOutputMode.DIRECT -> 2; else -> 0 }
                val device = manager.preferredOutput(requested.deviceKey)
                val output = NativeAudioOutput(config, api, requested.mode == AudioOutputMode.DIRECT, device?.id ?: 0)
                val actual = if (output.actualApi == 2) "AAudio" else "OpenSL ES"
                report("${requested.mode.label} · $actual · PCM 16 bit · ${config.sampleRate} Hz" +
                    if (requested.mode == AudioOutputMode.DIRECT) " · 已获得独占输出" else if (actual == "OpenSL ES" && device != null) " · 设备路由跟随系统" else "")
                return output
            } catch (error: Exception) {
                report("${requested.mode.label} 不可用，已回退 AudioTrack · ${config.sampleRate} Hz：${error.message}")
                return fallback.getAudioOutput(config)
            }
        }
        val output = fallback.getAudioOutput(config)
        report("AudioTrack · ${config.sampleRate} Hz")
        return output
    }
}
