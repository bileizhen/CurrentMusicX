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
import androidx.media3.common.Format
import androidx.media3.common.util.Clock
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioTrackAudioOutput
import io.github.currencortex.music.data.settings.AudioOutputSettings
import java.util.concurrent.atomic.AtomicBoolean

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal interface BitPerfectStream : AutoCloseable {
    val output: AudioOutput
    fun verify()
}

/** Uses only Android-advertised BIT_PERFECT formats; never substitutes the default mixer. */
@androidx.annotation.RequiresApi(34)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class AndroidBitPerfectStream private constructor(private val manager: AudioManager,
    private val session: BitPerfectSession, private val native: AndroidBitPerfectPlatform,
    override val output: AudioTrackAudioOutput) : BitPerfectStream {
    private val track = native.createdTrack!!.track
    private val closed = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private val routing = AudioRouting.OnRoutingChangedListener { verifyEvent() }
    private val changed = AudioManager.OnPreferredMixerAttributesChangedListener { _, device, _ ->
        if (device.id == session.device.id) verifyEvent()
    }
    private val devices = object : android.media.AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
            if (removed.any { it.id == session.device.id }) {
                session.invalidate(BitPerfectFailureCode.DISCONNECTED, "USB 输出设备已断开")
                close()
            }
        }
    }
    private fun verifyEvent() {
        if (closed.get()) return
        CurrentMusicAudioDiagnostics.record("routing/mixer callback targetId=${session.device.id} actualRoute=${track.routedDevice?.id ?: "unknown"}")
        try { session.verify(forcePolicyCheck = true) } catch (_: Exception) { close() }
    }
    override fun verify() = session.verify()
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { track.removeOnRoutingChangedListener(routing) }
        runCatching { manager.removeOnPreferredMixerAttributesChangedListener(changed) }
        runCatching { manager.unregisterAudioDeviceCallback(devices) }
        try { session.close() } finally { synchronized(ownership) { if (owner === this) owner = null } }
    }

    companion object {
        private val ownership = Any()
        private var owner: AndroidBitPerfectStream? = null
        fun open(context: Context, format: Format, settings: AudioOutputSettings, mediaAttributes: AudioAttributes,
            clock: Clock, report: (String) -> Unit): BitPerfectStream = synchronized(ownership) {
            check(owner == null) { "已有 Bit-perfect 输出占用设备，请先停止另一段播放" }
            val manager = context.getSystemService(AudioManager::class.java)
            val native = AndroidBitPerfectPlatform(manager, mediaAttributes, format, clock)
            val mask = if (format.channelMask != Format.NO_VALUE) format.channelMask else Util.getAudioTrackChannelConfig(format.channelCount)
            val decoded = BitPerfectPcm(format.sampleRate, format.pcmEncoding, format.channelCount, mask)
            val session = BitPerfectSession.open(native, decoded, settings.deviceKey,
                { android.os.SystemClock.elapsedRealtime() }, CurrentMusicAudioDiagnostics::record, report)
            val result = AndroidBitPerfectStream(manager, session, native, native.createdTrack!!.output)
            owner = result
            try {
                result.track.addOnRoutingChangedListener(result.routing, result.handler)
                manager.addOnPreferredMixerAttributesChangedListener(context.mainExecutor, result.changed)
                manager.registerAudioDeviceCallback(result.devices, result.handler)
                CurrentMusicAudioDiagnostics.record("callbacks=registered; no ordinary-mixer fallback")
                result
            } catch (e: Exception) {
                session.invalidate(BitPerfectFailureCode.PLATFORM_ERROR, "注册音频路由监听失败（${e.javaClass.simpleName}）")
                result.close()
                throw e
            }
        }
    }
}

@androidx.annotation.RequiresApi(34)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class AndroidBitPerfectPlatform(private val manager: AudioManager, private val media: AudioAttributes,
    private val decoded: Format, private val clock: Clock) : BitPerfectPlatform {
    private val attributes = media.audioAttributesV21.audioAttributes
    private val nativeDevices = mutableMapOf<Int, AudioDeviceInfo>()
    private var supported = emptyList<AudioMixerAttributes>()
    var createdTrack: AndroidBitPerfectTrack? = null
        private set
    override val sdk get() = Build.VERSION.SDK_INT
    override val usage get() = attributes.usage
    override fun devices(): List<BitPerfectDevice> = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .filter { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
        .onEach { nativeDevices[it.id] = it }.map(CurrentMusicAudioDiagnostics::device)
    private fun nativeDevice(value: BitPerfectDevice) = requireNotNull(nativeDevices[value.id])
    override fun mixers(device: BitPerfectDevice): List<BitPerfectMixer> {
        supported = manager.getSupportedMixerAttributes(nativeDevice(device))
        return supported.mapIndexed { index, mixer -> mixer.snapshot(index) }
    }
    override fun setPreferred(device: BitPerfectDevice, mixer: BitPerfectMixer) =
        manager.setPreferredMixerAttributes(attributes, nativeDevice(device), supported[mixer.token])
    override fun getPreferred(device: BitPerfectDevice) = manager.getPreferredMixerAttributes(attributes, nativeDevice(device))?.snapshot(-1)
    override fun clearPreferred(device: BitPerfectDevice) = manager.clearPreferredMixerAttributes(attributes, nativeDevice(device))
    override fun createTrack(mixer: BitPerfectMixer): BitPerfectTrack {
        // Keep the exact HAL-returned AudioFormat, including its positional/index masks.
        val actualFormat = supported[mixer.token].format
        val frameSize = Util.getPcmFrameSize(actualFormat.encoding, actualFormat.channelCount)
        val trackingMask = if (decoded.channelMask != Format.NO_VALUE) decoded.channelMask else Util.getAudioTrackChannelConfig(decoded.channelCount)
        // This legacy sizing API accepts positional masks only. This does NOT select the track format.
        val minimum = AudioTrack.getMinBufferSize(actualFormat.sampleRate, trackingMask, actualFormat.encoding)
        CurrentMusicAudioDiagnostics.record("minBufferBytes=$minimum frameBytes=$frameSize trackingPositionMask=0x${trackingMask.toString(16)}")
        if (minimum <= 0) throw BitPerfectFailure(BitPerfectFailureCode.TRACK_INITIALIZATION, "AudioTrack 缓冲区查询拒绝此 PCM 格式，返回 $minimum")
        val bytes = ((maxOf(minimum, actualFormat.sampleRate * frameSize / 4) + frameSize - 1) / frameSize) * frameSize
        val track = AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(actualFormat)
            .setBufferSizeInBytes(bytes).setTransferMode(AudioTrack.MODE_STREAM).build()
        return try {
            // AudioTrackAudioOutput uses channelMask only to count interleaved channels for its clock.
            // The AudioTrack itself was already constructed above from the unchanged HAL AudioFormat.
            val config = AudioOutputProvider.OutputConfig.Builder().setEncoding(actualFormat.encoding)
                .setSampleRate(actualFormat.sampleRate).setChannelMask(trackingMask).setBufferSize(bytes)
                .setAudioAttributes(media).setAudioSessionId(track.audioSessionId).build()
            AndroidBitPerfectTrack(track, AudioTrackAudioOutput(track, config, null, 1f, clock), nativeDevices)
                .also { createdTrack = it }
        } catch (e: Exception) { track.release(); throw e }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class AndroidBitPerfectTrack(val track: AudioTrack, val output: AudioTrackAudioOutput,
    private val devices: Map<Int, AudioDeviceInfo>) : BitPerfectTrack {
    override val initialized get() = track.state == AudioTrack.STATE_INITIALIZED
    override val format get() = track.format.snapshot()
    override val playing get() = track.playState == AudioTrack.PLAYSTATE_PLAYING
    override val routedDeviceId get() = track.routedDevice?.id
    override fun prefer(device: BitPerfectDevice) = track.setPreferredDevice(requireNotNull(devices[device.id]))
    override fun pauseAndFlush() { try { track.pause() } finally { track.flush() } }
    override fun release() {
        try { output.release() }
        catch (e: Exception) {
            // If Media3 fails before scheduling its asynchronous release, still release the track.
            runCatching { track.release() }
                .onFailure { CurrentMusicAudioDiagnostics.record("cleanup.rawAudioTrack=${it.javaClass.simpleName}") }
            throw e
        }
    }
}

private fun AudioFormat.snapshot() = BitPerfectPcm(sampleRate, encoding, channelCount, channelMask, channelIndexMask)
@androidx.annotation.RequiresApi(34)
private fun AudioMixerAttributes.snapshot(token: Int) = BitPerfectMixer(token, format.snapshot(), mixerBehavior)
internal fun pcmLabel(encoding: Int) = bitPerfectEncodingLabel(encoding)
