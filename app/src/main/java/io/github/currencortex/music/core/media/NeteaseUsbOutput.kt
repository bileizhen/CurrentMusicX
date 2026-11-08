package io.github.currencortex.music.core.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.media.AudioDeviceInfo
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import io.github.currencortex.music.BuildConfig
import io.github.currencortex.music.data.settings.AudioOutputSettings
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@androidx.annotation.Keep
internal object NeteaseUsbBridge {
    const val ADAPTER = "ncm-usb-v3-status-init"
    const val SHA256 = "150daa62df3b2f5a9b9bb5d54a3108e90495e50853c4b5ac7df3f63137f70a7a"
    private var loaded = false
    @Synchronized fun load(context: Context) {
        if (loaded) return
        check(BuildConfig.NETEASE_ORIGINAL_EFFECTS && android.os.Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a") {
            "[USB_ABI] 本构建未内置 arm64 网易云 USB 引擎"
        }
        val binary = File(context.applicationInfo.nativeLibraryDir, "libncmusbaudio.so")
        check(binary.isFile && binary.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = ByteArray(16384)
            while (true) { val count = input.read(bytes); if (count < 0) break; digest.update(bytes, 0, count) }
            digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == SHA256
        }) { "[USB_DRIVER_HASH] 网易云 USB 引擎缺失或版本不匹配" }
        try {
            System.loadLibrary("ncmusbaudio")
            System.loadLibrary("currentmusic_netease_usb")
            check(checkAbi()) { "[USB_DRIVER_ABI] USB 引擎接口校验失败" }
        } catch (e: LinkageError) { throw IllegalStateException("[USB_DRIVER_LOAD] USB 引擎或适配库加载失败", e) }
        loaded = true
    }
    external fun checkAbi(): Boolean
    external fun open(fd: Int, rate: Int, channels: Int, encoding: Int, sourceBits: Int): Long
    external fun diagnostics(): String
    external fun testFormatSelection(): Boolean
    external fun testTransferState(raw: Int, detached: Boolean): Boolean
    external fun write(handle: Long, buffer: ByteBuffer, offset: Int, bytes: Int): Int
    external fun play(handle: Long, playing: Boolean)
    external fun position(handle: Long): Long
    external fun verify(handle: Long)
    external fun close(handle: Long)
}

private object OriginalUsbDriver : NeteaseUsbDriver {
    private inline fun <T> native(action: () -> T): T {
        try { return action() }
        finally { runCatching { NeteaseUsbBridge.diagnostics().lineSequence().filter(String::isNotBlank).forEach(CurrentMusicAudioDiagnostics::record) } }
    }
    override fun open(fd: Int, pcm: NeteaseUsbPcm): Long {
        return native { NeteaseUsbBridge.open(fd, pcm.rate, pcm.channels, pcm.encoding, pcm.validBits) }
    }
    override fun write(handle: Long, buffer: ByteBuffer) = native { NeteaseUsbBridge.write(handle, buffer, buffer.position(), buffer.remaining()) }
    override fun play(handle: Long, playing: Boolean) = native { NeteaseUsbBridge.play(handle, playing) }
    override fun position(handle: Long) = native { NeteaseUsbBridge.position(handle) }
    override fun verify(handle: Long) = native { NeteaseUsbBridge.verify(handle) }
    override fun close(handle: Long) = native { NeteaseUsbBridge.close(handle) }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class NeteaseUsbStream private constructor(
    private val context: Context, private val session: NeteaseUsbSession,
    private val deviceId: Int, private val deviceLabel: String,
    private val declared: UsbAudioDescriptorReport, report: (String) -> Unit,
) : BitPerfectStream {
    private val closed = AtomicBoolean(false)
    private val outputImpl = OriginalUsbOutput(session, UsbMediaVolume(context), report, ::close)
    override val output: AudioOutput get() = outputImpl
    fun snapshot(): io.github.currencortex.music.core.media.engine.UsbOutputSnapshot {
        val wire = session.pcm.wireFormat
        val capability = if (!declared.valid) "系统未提供有效 USB 描述符；DAC 能力未验证" else {
            val entries = declared.formats.take(8).joinToString("；") { value ->
                val rates = when {
                    value.discreteRates.isNotEmpty() -> value.discreteRates.joinToString("/") + " Hz"
                    value.continuousRateRange != null -> "${value.continuousRateRange.first}–${value.continuousRateRange.last} Hz"
                    else -> "时钟范围未查询"
                }
                "UAC${value.uacVersion} ${value.channels}ch/${value.validBits}bit/${value.subslotBytes}B $rates"
            }
            "描述符声明 ${declared.formats.size} 个 PCM alternate：${entries.ifBlank { "无可解析声明" }}；实际接受能力未验证"
        }
        return io.github.currencortex.music.core.media.engine.UsbOutputSnapshot(deviceLabel,
            io.github.currencortex.music.core.media.engine.AudioPcmFormat(wire.rate, wire.encoding, wire.channels), capability)
    }
    private val detached = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            @Suppress("DEPRECATION") val device = intent.getParcelableExtra<android.hardware.usb.UsbDevice>(UsbManager.EXTRA_DEVICE)
            if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED && device?.deviceId == deviceId) {
                CurrentMusicAudioDiagnostics.record("usbHost.finalState=DISCONNECTED")
                report("USB 独占（网易云）已停止：[USB_DISCONNECTED] USB DAC 已断开")
                cleanup.execute { close() }
            }
        }
    }
    private var registered = false
    @Synchronized private fun register() {
        ContextCompat.registerReceiver(context, detached, IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED), ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
        session.verify() // Catch detach between open and receiver registration.
    }
    override fun verify() { check(!closed.get()) { "[USB_CLOSED] USB 输出已释放" }; outputImpl.verify() }
    @Synchronized override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try { if (registered) { registered = false; context.unregisterReceiver(detached) } }
        finally { try { session.close() } finally { CurrentMusicAudioDiagnostics.record("usbHost.resources=RELEASED") } }
    }
    companion object {
        private val cleanup = Executors.newSingleThreadExecutor { task -> Thread(task, "UsbExclusiveCleanup").apply { isDaemon = true } }
        fun open(context: Context, format: Format, settings: AudioOutputSettings, report: (String) -> Unit): NeteaseUsbStream {
            val pcm = NeteaseUsbPcm(format.sampleRate, format.channelCount, format.pcmEncoding)
            pcm.validate()
            val app = context.applicationContext
            NeteaseUsbBridge.load(app)
            val manager = app.getSystemService(UsbManager::class.java)
            val device = NeteaseUsbDevices.select(NeteaseUsbDevices.devices(app), settings.usbDeviceKey)
            val permitted = manager.hasPermission(device)
            CurrentMusicAudioDiagnostics.record("usbHost.engine=NetEase9.6.15 adapter=${NeteaseUsbBridge.ADAPTER} abi=arm64 sha256=${NeteaseUsbBridge.SHA256} " +
                "vendor=${device.vendorId} product=${device.productId} permission=$permitted decoded=$pcm requested=${pcm.wireFormat} AndroidMixer=unused")
            if (pcm.encoding == 21) CurrentMusicAudioDiagnostics.record("usbHost.pcmAdaptation=packed24_to_signed32_left_aligned resample=false bitPerfect=not_claimed")
            CurrentMusicAudioDiagnostics.record("usbHost.volume=system_media_times_player software_attenuation=true bitPerfect=not_claimed")
            check(permitted) { "[USB_PERMISSION] 请在音频输出设置中授权访问 USB DAC" }
            val connection = manager.openDevice(device) ?: error("[USB_OPEN] Android 无法打开已授权的 USB DAC")
            val declared = try { UsbAudioDescriptorParser.parse(connection.rawDescriptors) }
                catch (_: Exception) { UsbAudioDescriptorReport(emptyList(), "系统未提供描述符") }
            CurrentMusicAudioDiagnostics.record("usbHost.descriptorFormats=${declared.formats.size} " +
                "descriptorRead=${if (declared.valid) "OK" else "INVALID"} " +
                "rates=descriptor_declarations UAC2_clock_ranges=not_queried hardwareAcceptance=unverified")
            val session = NeteaseUsbSession.open(pcm, permitted, connection.fileDescriptor, AutoCloseable { connection.close() },
                { manager.deviceList.values.any { it.deviceId == device.deviceId } && manager.hasPermission(device) }, OriginalUsbDriver)
            val stream = NeteaseUsbStream(app, session, device.deviceId, NeteaseUsbDevices.label(device), declared, report)
            try { stream.register() } catch (e: Exception) { stream.close(); throw e }
            CurrentMusicAudioDiagnostics.record("usbHost.finalState=OPENED bitPerfect=not_claimed positionClock=engine_queue_estimate")
            report("USB 独占（网易云）· 已打开 ${NeteaseUsbDevices.label(device)} · ${pcm.rate} Hz / ${if (pcm.encoding == 21) "PCM 24-bit 无损扩展至 32-bit" else "PCM ${if (pcm.encoding == 2) 16 else 32}-bit"} / ${pcm.channels} 声道 · 等待传输")
            return stream
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class OriginalUsbOutput(private val session: NeteaseUsbSession, private val mediaVolume: UsbMediaVolume,
    private val report: (String) -> Unit,
    private val releaseStream: () -> Unit) : AudioOutput {
    private val listeners = mutableSetOf<AudioOutput.Listener>()
    private var playing = false
    private var draining = false
    private var bytes = 0L
    private var announced = false
    private var released = false
    private var volume = 1f
    private var lastPosition = 0L
    private var lastAdvance = SystemClock.elapsedRealtime()
    fun verify() {
        session.verify()
        val pendingUs = bytes / session.pcm.frameBytes * 1_000_000 / sampleRate - lastPosition
        if (playing && pendingUs > 20000 && SystemClock.elapsedRealtime() - lastAdvance > 5000)
            error("[USB_STALLED] USB 输出超过 5 秒未前进，请检查 DAC 和完整诊断")
    }
    override fun play() { session.play(true); playing = true; lastAdvance = SystemClock.elapsedRealtime() }
    override fun pause() { session.play(false); playing = false }
    override fun write(buffer: ByteBuffer, encodedAccessUnitCount: Int, presentationTimeUs: Long): Boolean {
        verify()
        bytes += session.write(buffer, mediaVolume.gain() * volume)
        return !buffer.hasRemaining()
    }
    override fun getPositionUs(): Long {
        val duration = bytes / session.pcm.frameBytes * 1_000_000 / sampleRate
        val position = session.position().coerceAtMost(duration)
        if (position > lastPosition) {
            lastPosition = position; lastAdvance = SystemClock.elapsedRealtime()
            if (!announced) {
                announced = true
                CurrentMusicAudioDiagnostics.record("usbHost.finalState=ADVANCING bitPerfect=not_claimed")
                report("USB 独占（网易云）· PCM ${if (session.pcm.encoding == 2) 16 else 32}-bit · $sampleRate Hz · 引擎进度已前进；非 DAC 硬件校验")
                listeners.toList().forEach { it.onPositionAdvancing(System.currentTimeMillis()) }
            }
        }
        if (draining && position >= duration && playing) pause()
        return position
    }
    // The raw sink handles flush by closing/recreating this entire stream. Never
    // reset the engine ring while Media3 still accounts those bytes as submitted.
    override fun flush() { release() }
    override fun stop() { draining = true }
    override fun release() {
        if (released) return
        released = true
        try { releaseStream() } finally { listeners.toList().forEach { it.onReleased() } }
    }
    override fun setVolume(volume: Float) {
        val gain = usbVolumeGain(volume)
        if (this.volume != gain) CurrentMusicAudioDiagnostics.record("usbHost.playerVolume=$gain")
        this.volume = gain
    }
    override fun isOffloadedPlayback() = false
    override fun getAudioSessionId() = C.AUDIO_SESSION_ID_UNSET
    override fun getSampleRate() = session.pcm.rate
    override fun getBufferSizeInFrames() = sampleRate / 10L
    override fun getPlaybackParameters() = PlaybackParameters.DEFAULT
    override fun isStalled() = false
    override fun addListener(listener: AudioOutput.Listener) { listeners += listener }
    override fun removeListener(listener: AudioOutput.Listener) { listeners -= listener }
    override fun setPlaybackParameters(playbackParams: PlaybackParameters) = Unit
    override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) = Unit
    override fun setOffloadEndOfStream() = Unit
    override fun attachAuxEffect(effectId: Int) = Unit
    override fun setAuxEffectSendLevel(level: Float) = Unit
    override fun setPreferredDevice(preferredDevice: AudioDeviceInfo?) = Unit // Actual route is the authorized USB FD.
    override fun canReuseAudioOutput(currentConfig: AudioOutputProvider.OutputConfig,
        newFormat: AudioOutputProvider.FormatConfig, newConfig: AudioOutputProvider.OutputConfig) = false
}
