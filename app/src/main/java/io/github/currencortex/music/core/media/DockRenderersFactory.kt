package io.github.currencortex.music.core.media

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

@androidx.annotation.OptIn(UnstableApi::class)
internal class DockRenderersFactory(context: Context, private val meter: DockAudioLevel,
    private val probe: TrackEnergyProbe? = null,
    private val originalPreset: () -> NeteaseOriginalPreset? = { null },
    private val originalFailure: (NeteaseOriginalPreset?, String) -> Unit = { _, _ -> },
    private val originalProcessing: (NeteaseOriginalPreset) -> Unit = {}) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParams: Boolean): AudioSink =
        DefaultAudioSink.Builder(context).setEnableFloatOutput(enableFloatOutput && !NeteaseNativeEngine.available())
            .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
            // Keep the optional stage present before an engine import, so an existing
            // playback session can begin using it without recreating the player.
            .setAudioProcessors(arrayOf(NeteaseOriginalAudioProcessor(originalPreset, originalFailure, originalProcessing),
                DockAudioProcessor(meter, probe))).build()
}
