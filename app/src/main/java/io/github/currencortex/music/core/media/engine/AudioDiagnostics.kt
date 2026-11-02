package io.github.currencortex.music.core.media.engine

import io.github.currencortex.music.data.settings.AudioOutputMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Null means unavailable. Requested values are never promoted to measured values. */
data class AudioPcmFormat(val sampleRate: Int? = null, val encoding: Int? = null, val channels: Int? = null) {
    val bitDepth: Int? get() = when (encoding) { 3 -> 8; 2 -> 16; 21 -> 24; 22, 4 -> 32; else -> null }
    fun describe(): String = "${sampleRate?.let { "$it Hz" } ?: "采样率未知"} · ${bitDepth?.let { "$it bit" } ?: "位深未知"}" +
        (if (encoding == 4) " float" else "") + " · ${channels?.let { "$it ch" } ?: "声道未知"}"
}
enum class BitPerfectEvidence { INACTIVE, REQUESTED, ANDROID_CONFIGURATION_VERIFIED, UNVERIFIED, FALLBACK }
data class AudioDiagnosticsSnapshot(
    val requestedMode: AudioOutputMode = AudioOutputMode.AUDIO_TRACK,
    val backend: String? = null,
    val source: AudioPcmFormat = AudioPcmFormat(),
    val decoded: AudioPcmFormat = AudioPcmFormat(),
    val requestedRate: Int? = null,
    val output: AudioPcmFormat = AudioPcmFormat(),
    val formatEvidence: String = "尚未创建音频输出",
    val device: String? = null,
    val deviceId: Int? = null,
    val usbCapabilities: String? = null,
    val softwareBufferFrames: Long? = null,
    val deviceBufferFrames: Int? = null,
    val capacityFrames: Int? = null,
    val underruns: Int? = null,
    val latencyMs: Double? = null,
    val latencyEvidence: String = "系统未提供",
    val requestedEffects: List<String> = emptyList(),
    val appliedEffects: List<String> = emptyList(),
    val processingBypass: String? = null,
    val bitPerfect: BitPerfectEvidence = BitPerfectEvidence.INACTIVE,
    val fallbackReason: String? = null,
    val playing: Boolean = false,
)
class AudioDiagnostics {
    private val mutable = MutableStateFlow(AudioDiagnosticsSnapshot())
    val state = mutable.asStateFlow()
    fun update(change: (AudioDiagnosticsSnapshot) -> AudioDiagnosticsSnapshot) = mutable.update(change)
    fun source(value: AudioPcmFormat) = update { it.copy(source = value) }
    fun decoded(value: AudioPcmFormat) = update { it.copy(decoded = value) }
    fun resetTrack() = update { it.copy(source = AudioPcmFormat(), decoded = AudioPcmFormat(), output = AudioPcmFormat(),
        backend = null, formatEvidence = "等待解码与输出", appliedEffects = emptyList(), latencyMs = null,
        device = null, deviceId = null, usbCapabilities = null, softwareBufferFrames = null, deviceBufferFrames = null, capacityFrames = null,
        underruns = null, playing = false) }
    fun text(): String = state.value.let { s -> buildString {
        appendLine("请求模式: ${s.requestedMode.label}"); appendLine("实际后端: ${s.backend ?: "系统未提供"}")
        appendLine("源声明: ${s.source.describe()}"); appendLine("解码PCM: ${s.decoded.describe()}")
        appendLine("请求采样率: ${s.requestedRate ?: "跟随音源"}"); appendLine("输出: ${s.output.describe()}")
        appendLine("格式证据: ${s.formatEvidence}"); appendLine("设备: ${s.device ?: "系统未提供"}")
        appendLine("USB DAC 声明能力: ${s.usbCapabilities ?: "系统未提供"}")
        appendLine("软件缓冲帧: ${s.softwareBufferFrames ?: "系统未提供"}")
        appendLine("设备缓冲帧: ${s.deviceBufferFrames ?: "系统未提供"}")
        appendLine("缓冲容量帧: ${s.capacityFrames ?: "系统未提供"}"); appendLine("欠载: ${s.underruns ?: "系统未提供"}")
        appendLine("延迟: ${s.latencyMs?.let { "%.2f ms".format(java.util.Locale.ROOT, it) } ?: "无法验证"} · ${s.latencyEvidence}")
        appendLine("请求DSP: ${s.requestedEffects.joinToString().ifBlank { "无" }}")
        appendLine("已观察DSP: ${s.appliedEffects.joinToString().ifBlank { "未观察到处理" }}")
        appendLine("处理策略: ${s.processingBypass ?: "正常处理链"}"); appendLine("逐位状态: ${s.bitPerfect}")
        appendLine("最终DAC逐位透明度: 未验证"); appendLine("蓝牙当前编码: 系统未提供")
        s.fallbackReason?.let { appendLine("回退原因: $it") }
    } }
}
