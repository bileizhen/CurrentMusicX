package io.github.currencortex.music.core.media

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.media.AudioRouting
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.Clock
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioTrackAudioOutput
import io.github.currencortex.music.data.settings.AudioOutputSettings
import java.util.concurrent.atomic.AtomicReference

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal interface BitPerfectStream : AutoCloseable {
    val output: AudioOutput
    fun verify()
}

/** Only negotiates formats explicitly advertised as BIT_PERFECT by the Android USB HAL. */
@androidx.annotation.RequiresApi(34)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class AndroidBitPerfectStream private constructor(
    private val manager: AudioManager, private val device: AudioDeviceInfo,
    private val attributes: android.media.AudioAttributes, private val mixer: AudioMixerAttributes,
    private val report: (String) -> Unit, override val output: AudioTrackAudioOutput,
) : BitPerfectStream {
    private val track = output.audioTrack
    private val failed = AtomicReference<String?>(null)
    private val closed = java.util.concurrent.atomic.AtomicBoolean(false)
    private var confirmed = false
    private val handler = Handler(Looper.getMainLooper())
    private val routing = AudioRouting.OnRoutingChangedListener { checkRoute() }
    private val changed = AudioManager.OnPreferredMixerAttributesChangedListener { _, changedDevice, _ ->
        if (!closed.get() && changedDevice.id == device.id && manager.getPreferredMixerAttributes(attributes, device) != mixer)
            invalidate("系统已撤销逐位输出配置")
    }
    private val devices = object : android.media.AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
            if (removed.any { it.id == device.id }) invalidate("USB 输出设备已断开")
        }
    }
    private fun invalidate(reason: String) {
        if (closed.get()) return
        if (failed.compareAndSet(null, reason)) {
            runCatching { track.pause() }
            report("Bit-perfect 已停止：$reason")
        }
    }
    private fun checkRoute() {
        if (closed.get()) return
        val routed = track.routedDevice ?: return
        if (routed.id != device.id) invalidate("实际输出设备与选定 USB 设备不一致")
    }
    override fun verify() {
        check(!closed.get()) { "USB 输出已关闭" }
        checkRoute()
        failed.get()?.let { error(it) }
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) { confirmed = false; return }
        if (!confirmed && track.routedDevice?.id == device.id) {
            check(manager.getPreferredMixerAttributes(attributes, device) == mixer) { "系统逐位输出配置已失效" }
            confirmed = true
            val f = mixer.format
            report("Bit-perfect · 系统已确认 USB 路由与逐位配置 · ${f.sampleRate} Hz · ${pcmLabel(f.encoding)} · ${f.channelCount} 声道")
        }
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { track.removeOnRoutingChangedListener(routing) }
        runCatching { manager.removeOnPreferredMixerAttributesChangedListener(changed) }
        runCatching { manager.unregisterAudioDeviceCallback(devices) }
        // Close the track before releasing the UID-owned mixer preference; do not let
        // queued samples briefly play through a normal mixer during teardown.
        runCatching { track.pause(); track.flush() }
        output.release()
        synchronized(ownership) {
            if (owner === this) {
                runCatching {
                    if (manager.getPreferredMixerAttributes(attributes, device) == mixer)
                        manager.clearPreferredMixerAttributes(attributes, device)
                }
                owner = null
            }
        }
    }
    companion object {
        private val ownership = Any()
        private var owner: AndroidBitPerfectStream? = null
        fun open(context: Context, format: Format, settings: AudioOutputSettings,
            mediaAttributes: AudioAttributes, clock: Clock, report: (String) -> Unit): BitPerfectStream = synchronized(ownership) {
            check(owner == null) { "已有 Bit-perfect 输出占用设备，请先停止另一段播放" }
            val manager = context.getSystemService(AudioManager::class.java)
            val usb = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
            val device = if (settings.deviceKey.isEmpty()) {
                check(usb.size <= 1) { "检测到多个 USB 设备，请选择首选输出设备" }
                usb.singleOrNull()
            } else usb.firstOrNull { it.outputKey() == settings.deviceKey }
            checkNotNull(device) { "请连接并选择支持 Bit-perfect 的 USB DAC" }
            val mask = if (format.channelMask != Format.NO_VALUE) format.channelMask else Util.getAudioTrackChannelConfig(format.channelCount)
            val expected = AudioFormat.Builder().setEncoding(format.pcmEncoding)
                .setSampleRate(format.sampleRate).setChannelMask(mask).build()
            val mixer = manager.getSupportedMixerAttributes(device).firstOrNull {
                it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT && it.format == expected
            }
            checkNotNull(mixer) { "设备未提供 ${format.sampleRate} Hz / ${pcmLabel(format.pcmEncoding)} / ${format.channelCount} 声道的逐位输出" }
            val attributes = mediaAttributes.audioAttributesV21.audioAttributes
            check(attributes.usage == android.media.AudioAttributes.USAGE_MEDIA) { "逐位输出仅支持媒体播放" }
            check(manager.setPreferredMixerAttributes(attributes, device, mixer)) { "系统拒绝启用逐位输出" }
            var track: AudioTrack? = null
            var registered: AndroidBitPerfectStream? = null
            try {
                check(manager.getPreferredMixerAttributes(attributes, device) == mixer) { "系统未保留逐位输出配置" }
                val frameSize = Util.getPcmFrameSize(format.pcmEncoding, format.channelCount)
                val minimum = AudioTrack.getMinBufferSize(format.sampleRate, mask, format.pcmEncoding)
                check(minimum > 0) { "AudioTrack 不支持此 PCM 格式" }
                val bytes = ((maxOf(minimum, format.sampleRate * frameSize / 4) + frameSize - 1) / frameSize) * frameSize
                track = AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(expected)
                    .setBufferSizeInBytes(bytes).setTransferMode(AudioTrack.MODE_STREAM).build()
                check(track.state == AudioTrack.STATE_INITIALIZED && track.setPreferredDevice(device)) { "无法打开所选 USB 输出" }
                val config = AudioOutputProvider.OutputConfig.Builder().setEncoding(format.pcmEncoding)
                    .setSampleRate(format.sampleRate).setChannelMask(mask).setBufferSize(bytes)
                    .setAudioAttributes(mediaAttributes).setAudioSessionId(track.audioSessionId).build()
                val result = AndroidBitPerfectStream(manager, device, attributes, mixer, report,
                    AudioTrackAudioOutput(track, config, null, 1f, clock))
                registered = result
                owner = result
                track.addOnRoutingChangedListener(result.routing, result.handler)
                manager.addOnPreferredMixerAttributesChangedListener(context.mainExecutor, result.changed)
                manager.registerAudioDeviceCallback(result.devices, result.handler)
                report("Bit-perfect · 系统已接受逐位配置，等待 USB 播放路由")
                result
            } catch (error: Exception) {
                if (registered != null) registered.close() else track?.release()
                if (manager.getPreferredMixerAttributes(attributes, device) == mixer)
                    manager.clearPreferredMixerAttributes(attributes, device)
                throw error
            }
        }
    }
}

internal fun pcmLabel(encoding: Int) = when (encoding) {
    C.ENCODING_PCM_8BIT -> "PCM 8 bit"
    C.ENCODING_PCM_16BIT -> "PCM 16 bit"
    C.ENCODING_PCM_24BIT -> "PCM 24 bit"
    C.ENCODING_PCM_32BIT -> "PCM 32 bit"
    C.ENCODING_PCM_FLOAT -> "PCM Float 32"
    else -> "不支持的 PCM 格式"
}
