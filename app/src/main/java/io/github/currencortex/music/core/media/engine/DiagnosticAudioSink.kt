package io.github.currencortex.music.core.media.engine

import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class DiagnosticAudioSink(delegate: AudioSink, private val diagnostics: AudioDiagnostics,
    private val active: () -> Boolean, private val formatState: AudioFormatState = AudioFormatState()) : ForwardingAudioSink(delegate) {
    override fun configure(config: AudioSink.AudioSinkConfig) {
        runCatching {
            formatState.decoded(AudioPcmFormat(config.format.sampleRate.takeIf { it > 0 },
                config.format.pcmEncoding.takeIf { it > 0 }, config.format.channelCount.takeIf { it > 0 }))
            if (active()) formatState.publish(diagnostics)
        }
        super.configure(config)
    }
}
