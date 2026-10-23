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
    private val originalProcessing: (NeteaseOriginalPreset) -> Unit = {},
    private val outputSampleRate: () -> Int = { 0 },
    private val outputSettings: () -> io.github.currencortex.music.data.settings.AudioOutputSettings = {
        io.github.currencortex.music.data.settings.AudioOutputSettings(sampleRate = outputSampleRate()) },
    private val outputStatus: (String) -> Unit = {}) : DefaultRenderersFactory(context) {
    override fun buildAudioRenderers(context: Context, extensionRendererMode: Int,
        mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector, enableDecoderFallback: Boolean,
        audioSink: AudioSink, eventHandler: android.os.Handler,
        eventListener: androidx.media3.exoplayer.audio.AudioRendererEventListener,
        out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>) {
        // The app uses platform audio decoders. Preserve the source's declared integer
        // precision instead of Media3's automatic float preference in bit-perfect mode.
        out.add(object : androidx.media3.exoplayer.audio.MediaCodecAudioRenderer(context, codecAdapterFactory,
            mediaCodecSelector, enableDecoderFallback, eventHandler, eventListener, audioSink) {
            override fun getMediaFormat(format: androidx.media3.common.Format, codecMimeType: String,
                codecMaxInputSize: Int, codecOperatingRate: Float): android.media.MediaFormat {
                val result = super.getMediaFormat(format, codecMimeType, codecMaxInputSize, codecOperatingRate)
                if (outputSettings().mode == io.github.currencortex.music.data.settings.AudioOutputMode.BIT_PERFECT)
                    result.setInteger(android.media.MediaFormat.KEY_PCM_ENCODING,
                        format.pcmEncoding.takeIf { it in BitPerfectAudioSink.PCM_ENCODINGS } ?: androidx.media3.common.C.ENCODING_PCM_16BIT)
                return result
            }
            override fun onOutputFormatChanged(format: androidx.media3.common.Format, mediaFormat: android.media.MediaFormat?) {
                if (outputSettings().mode == io.github.currencortex.music.data.settings.AudioOutputMode.BIT_PERFECT &&
                    format.sampleMimeType != androidx.media3.common.MimeTypes.AUDIO_RAW && mediaFormat != null &&
                    format.pcmEncoding in BitPerfectAudioSink.PCM_ENCODINGS) {
                    val actual = if (mediaFormat.containsKey(android.media.MediaFormat.KEY_PCM_ENCODING))
                        mediaFormat.getInteger(android.media.MediaFormat.KEY_PCM_ENCODING) else androidx.media3.common.C.ENCODING_PCM_16BIT
                    if (actual != format.pcmEncoding) {
                        val reason = "Bit-perfect 已停止：系统解码器未保留音源 PCM 位深"
                        outputStatus(reason)
                        throw createRendererException(IllegalStateException(reason), format,
                            androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)
                    }
                }
                super.onOutputFormatChanged(format, mediaFormat)
            }
        })
    }
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParams: Boolean): AudioSink =
        BitPerfectAudioSink(context, DefaultAudioSink.Builder(context).setEnableFloatOutput(false)
            .setEnableAudioOutputPlaybackParameters(false)
            .setAudioOutputProvider(MusicAudioOutputProvider(context, outputSettings, outputStatus))
            // Keep the optional stage present before an engine import, so an existing
            // playback session can begin using it without recreating the player.
            .setAudioProcessors(arrayOf(NeteaseOriginalAudioProcessor({
                if (outputSettings().mode == io.github.currencortex.music.data.settings.AudioOutputMode.DIRECT) null else originalPreset()
            }, originalFailure, originalProcessing), DockAudioProcessor(meter, probe), OutputSampleRateProcessor(requestedRate = {
                if (outputSettings().mode == io.github.currencortex.music.data.settings.AudioOutputMode.DIRECT) 0 else outputSampleRate()
            }))).build(), outputSettings, outputStatus)
}
