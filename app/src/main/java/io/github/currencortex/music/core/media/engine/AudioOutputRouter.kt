package io.github.currencortex.music.core.media.engine

import io.github.currencortex.music.data.settings.AudioOutputMode
import io.github.currencortex.music.data.settings.AudioOutputSettings

/** Requested preference stays intact; a failed physical path is downgraded once per attempt. */
class AudioOutputRouter(private val diagnostics: AudioDiagnostics) {
    private var requested = AudioOutputSettings()
    private var failure: String? = null
    @Synchronized fun request(settings: AudioOutputSettings) {
        val changed = settings.copy(bufferPolicy = requested.bufferPolicy) != requested
        requested = settings
        if (changed) failure = null
        diagnostics.update { it.copy(requestedMode = settings.mode,
            requestedRate = settings.sampleRate.takeIf { value -> value > 0 && !settings.mode.bypassProcessing },
            fallbackReason = failure, bitPerfect = when {
                failure != null -> BitPerfectEvidence.FALLBACK
                !changed && it.bitPerfect == BitPerfectEvidence.ANDROID_CONFIGURATION_VERIFIED -> it.bitPerfect
                settings.mode == AudioOutputMode.BIT_PERFECT -> BitPerfectEvidence.REQUESTED
                settings.mode == AudioOutputMode.NETEASE_USB -> BitPerfectEvidence.UNVERIFIED
                else -> BitPerfectEvidence.INACTIVE
            }) }
    }
    @Synchronized fun effective(settings: AudioOutputSettings): AudioOutputSettings {
        requestIfChanged(settings)
        return if (failure == null) settings else settings.copy(mode = AudioOutputMode.AUDIO_TRACK, sampleRate = 0)
    }
    private fun requestIfChanged(settings: AudioOutputSettings) { if (settings != requested) request(settings) }
    @Synchronized fun fallback(reason: String): Boolean {
        if (failure != null || requested.mode == AudioOutputMode.AUDIO_TRACK) return false
        failure = reason.take(500)
        diagnostics.update { it.copy(backend = null, output = AudioPcmFormat(), bitPerfect = BitPerfectEvidence.FALLBACK,
            softwareBufferFrames = null, deviceBufferFrames = null, capacityFrames = null, underruns = null,
            latencyMs = null, latencyEvidence = "系统未提供", usbCapabilities = null,
            fallbackReason = failure, formatEvidence = "原输出不可用，等待 AudioTrack 实际格式") }
        return true
    }
    @Synchronized fun retry() {
        failure = null
        diagnostics.update { it.copy(bitPerfect = BitPerfectEvidence.INACTIVE) }
        request(requested)
    }
    @Synchronized fun fallbackReason(): String? = failure
}
