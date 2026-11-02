package io.github.currencortex.music.core.media.engine

import io.github.currencortex.music.data.settings.AudioOutputMode

data class AudioDeviceCapabilities(val rates: Set<Int> = emptySet(), val encodings: Set<Int> = emptySet(),
    val channels: Set<Int> = emptySet(), val usb: Boolean = false)
data class AudioFormatDecision(val accepted: Boolean, val reason: String? = null, val requiresRuntimeVerification: Boolean = true)
/** Descriptor/capability lists are hints; successful stream creation and observed format remain required. */
object AudioFormatNegotiator {
    fun negotiate(format: AudioPcmFormat, mode: AudioOutputMode, device: AudioDeviceCapabilities): AudioFormatDecision {
        if ((format.sampleRate ?: 0) <= 0 || (format.channels ?: 0) <= 0 || format.bitDepth == null)
            return AudioFormatDecision(false, "解码器未报告有效 PCM 格式")
        if (mode == AudioOutputMode.BIT_PERFECT && !device.usb) return AudioFormatDecision(false, "未找到 USB 输出设备")
        if (mode == AudioOutputMode.DIRECT && format.encoding != 2) return AudioFormatDecision(false, "当前 Direct 后端仅支持 PCM 16 bit")
        if (device.rates.isNotEmpty() && format.sampleRate !in device.rates) return AudioFormatDecision(false, "设备未声明此采样率")
        if (device.encodings.isNotEmpty() && format.encoding !in device.encodings) return AudioFormatDecision(false, "设备未声明此 PCM 位深")
        if (device.channels.isNotEmpty() && format.channels !in device.channels) return AudioFormatDecision(false, "设备未声明此声道数")
        return AudioFormatDecision(true)
    }
}
