package io.github.currencortex.music.core.media

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi

/** Resample decoded PCM, without changing speed or pitch. Re-read after sink reconfiguration. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class OutputSampleRateProcessor(private val requestedRate: () -> Int,
    private val sonic: SonicAudioProcessor = SonicAudioProcessor()) : AudioProcessor by sonic {
    override fun flush(streamMetadata: AudioProcessor.StreamMetadata) = sonic.flush(streamMetadata)
    @Deprecated("Use stream metadata")
    override fun flush() = sonic.flush(AudioProcessor.StreamMetadata(androidx.media3.common.C.TIME_UNSET))
    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sonic.setOutputSampleRateHz(requestedRate().takeIf { it > 0 } ?: SonicAudioProcessor.SAMPLE_RATE_NO_CHANGE)
        return sonic.configure(inputAudioFormat)
    }
}
