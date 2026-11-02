package io.github.currencortex.music.core.media

import android.content.Context
import android.media.AudioManager
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.*
import io.github.currencortex.music.data.settings.AudioOutputMode
import io.github.currencortex.music.data.settings.AudioOutputSettings
import io.github.currencortex.music.data.settings.AudioBufferPolicy
import io.github.currencortex.music.core.media.engine.AudioFormatNegotiator
import io.github.currencortex.music.core.media.engine.AudioPcmFormat
import io.github.currencortex.music.core.media.engine.AudioDeviceCapabilities

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class MusicAudioOutputProvider(context: Context, private val settings: () -> AudioOutputSettings,
    private val report: (String) -> Unit,
    private val fallback: AudioOutputProvider = AudioTrackAudioOutputProvider.Builder(context).build(),
    private val diagnostics: io.github.currencortex.music.core.media.engine.AudioDiagnostics? = null,
    private val primary: () -> Boolean = { true },
    private val router: io.github.currencortex.music.core.media.engine.AudioOutputRouter? = null) : AudioOutputProvider by fallback {
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
        if (requested.mode != AudioOutputMode.AUDIO_TRACK) {
            try {
                val api = when (requested.mode) { AudioOutputMode.OPENSL_ES -> 1; AudioOutputMode.AAUDIO, AudioOutputMode.DIRECT -> 2; else -> 0 }
                val device = manager.preferredOutput(requested.deviceKey)
                val capabilities = device?.let { AudioDeviceCapabilities(it.sampleRates.toSet(), it.encodings.toSet(), it.channelCounts.toSet()) }
                    ?: AudioDeviceCapabilities()
                val decision = AudioFormatNegotiator.negotiate(AudioPcmFormat(config.sampleRate, config.encoding,
                    Integer.bitCount(config.channelMask)), requested.mode, capabilities)
                require(decision.accepted) { decision.reason.orEmpty() }
                require(config.encoding == C.ENCODING_PCM_16BIT) { "当前原生后端仅支持 PCM 16 bit" }
                val queueMs = when (requested.bufferPolicy) {
                    AudioBufferPolicy.BALANCED -> 500
                    AudioBufferPolicy.RESPONSIVE -> 120
                    AudioBufferPolicy.RESILIENT -> 1000
                }
                val output = NativeAudioOutput(config, api, requested.mode == AudioOutputMode.DIRECT, device?.id ?: 0, queueMs)
                val actual = if (output.actualApi == 2) "AAudio" else "OpenSL ES"
                report("${requested.mode.label} · $actual · PCM 16 bit · ${config.sampleRate} Hz" +
                    if (requested.mode == AudioOutputMode.DIRECT && output.actualExclusive) " · 流已报告独占" else if (actual == "OpenSL ES" && device != null) " · 设备路由跟随系统" else "")
                return tracked(output, config, actual)
            } catch (error: Exception) {
                if (primary()) router?.fallback("${requested.mode.label} 创建失败（${error.javaClass.simpleName}）")
                report("${requested.mode.label} 不可用，已回退 AudioTrack · ${config.sampleRate} Hz：${error.message}")
                return tracked(fallback.getAudioOutput(config), config, "AudioTrack")
            }
        }
        val output = fallback.getAudioOutput(config)
        report("AudioTrack · ${config.sampleRate} Hz")
        return tracked(output, config, "AudioTrack")
    }
    private fun tracked(output: AudioOutput, config: AudioOutputProvider.OutputConfig, backend: String): AudioOutput = diagnostics?.let {
        io.github.currencortex.music.core.media.engine.DiagnosticAudioOutput(output, config, it, primary, backend) { id ->
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { device -> device.id == id }?.outputLabel()
        }
    } ?: output
}
