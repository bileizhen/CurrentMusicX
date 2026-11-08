package io.github.currencortex.music.core.media

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Format limits are tied to the inspected 9.6.15 arm64 ABI, not DAC hardware limits. */
internal data class NeteaseUsbPcm(val rate: Int, val channels: Int, val encoding: Int, val precision: Int = 0) {
    val frameBytes get() = channels * when (encoding) { 2 -> 2; 21 -> 3; else -> 4 }
    val validBits get() = if (precision != 0) precision else when (encoding) { 2 -> 16; 21 -> 24; else -> 32 }
    val wireFormat get() = if (encoding == 21) copy(encoding = 22, precision = 24) else this
    fun validate() {
        check(rate in 8000..192000 && channels in 1..2) { "[USB_PCM] 当前网易云驱动适配支持 8–192 kHz 单声道或立体声" }
        check(encoding == 2 || encoding == 21 || encoding == 22) {
            "[USB_PCM] 网易云 USB 输出仅接收 PCM 16/24/32-bit 整数；浮点格式不会被当成整数"
        }
        check(precision == 0 || (encoding == 22 && precision == 24)) { "[USB_PCM] 无效的容器有效位数" }
    }
}

internal interface NeteaseUsbDriver {
    fun open(fd: Int, pcm: NeteaseUsbPcm): Long
    fun write(handle: Long, buffer: ByteBuffer): Int
    fun play(handle: Long, playing: Boolean)
    fun position(handle: Long): Long
    fun verify(handle: Long)
    fun close(handle: Long)
}

/** Injectable lifecycle; only an authorized connection's FD may enter the native engine. */
internal class NeteaseUsbSession private constructor(
    private val driver: NeteaseUsbDriver,
    private val connection: AutoCloseable,
    private val connected: () -> Boolean,
    private val handle: Long,
    val pcm: NeteaseUsbPcm,
) : AutoCloseable {
    private var closed = false
    private val scratch by lazy { ByteBuffer.allocateDirect(8192).order(ByteOrder.LITTLE_ENDIAN) }
    @Synchronized fun verify() {
        check(!closed) { "[USB_CLOSED] USB 输出已释放" }
        try {
            check(connected()) { "[USB_DISCONNECTED] USB DAC 已断开或授权已撤销" }
            driver.verify(handle)
        } catch (e: Throwable) { runCatching { close() }.exceptionOrNull()?.let(e::addSuppressed); throw e }
    }
    @Synchronized fun write(buffer: ByteBuffer, volume: Float = 1f): Int = guarded {
        check(buffer.isDirect && buffer.remaining() % pcm.frameBytes == 0) { "[USB_BUFFER] PCM 缓冲区未按帧对齐" }
        val before = buffer.position()
        val gain = usbVolumeGain(volume).toDouble()
        val wire = if (pcm.encoding != 21 && gain == 1.0) buffer else scratch.apply {
            clear()
            val count = minOf(buffer.remaining() / pcm.frameBytes, capacity() / pcm.wireFormat.frameBytes) * pcm.channels
            val sampleBytes = pcm.frameBytes / pcm.channels
            repeat(count) { sampleIndex ->
                val offset = before + sampleIndex * sampleBytes
                // Decode signed little-endian PCM independently of ByteBuffer.order().
                var sample = buffer.get(offset + sampleBytes - 1).toInt()
                for (part in sampleBytes - 2 downTo 0)
                    sample = (sample shl 8) or (buffer.get(offset + part).toInt() and 255)
                // Packed 24 stays left-aligned in the 32-bit USB container. Double
                // preserves all 32 integer bits at unity; never mutate the input,
                // since backpressure can retry the same samples at a new volume.
                if (pcm.encoding == 21) sample = sample shl 8
                val scaled = (sample.toDouble() * gain).toInt()
                if (pcm.encoding == 2) putShort(scaled.toShort()) else putInt(scaled)
            }
            flip()
        }
        val wirePosition = wire.position()
        val bytes = driver.write(handle, wire)
        check(bytes in 0..wire.remaining() && bytes % pcm.wireFormat.frameBytes == 0 && wire.position() == wirePosition && buffer.position() == before) {
            "[USB_WRITE] 驱动返回无效的已接收字节数"
        }
        val consumed = bytes / pcm.wireFormat.frameBytes * pcm.frameBytes
        buffer.position(before + consumed)
        consumed
    }
    @Synchronized fun play(playing: Boolean) = guarded { driver.play(handle, playing) }
    @Synchronized fun position() = guarded { driver.position(handle).coerceAtLeast(0) }
    private inline fun <T> guarded(action: () -> T): T {
        verify()
        try { return action() } catch (e: Throwable) { runCatching { close() }.exceptionOrNull()?.let(e::addSuppressed); throw e }
    }
    @Synchronized override fun close() {
        if (closed) return
        closed = true
        try { driver.close(handle) } finally { connection.close() }
    }
    companion object {
        fun open(pcm: NeteaseUsbPcm, permitted: Boolean, fd: Int, connection: AutoCloseable,
            connected: () -> Boolean, driver: NeteaseUsbDriver): NeteaseUsbSession {
            var handle = 0L
            try {
                pcm.validate()
                check(permitted) { "[USB_PERMISSION] 尚未获得所选 USB DAC 的访问授权" }
                check(connected()) { "[USB_DISCONNECTED] 所选 USB DAC 已断开" }
                check(fd >= 0) { "[USB_OPEN] 无法打开 USB 设备" }
                handle = driver.open(fd, pcm.wireFormat)
                check(handle != 0L) { "[USB_OPEN] 原生 USB 输出初始化失败" }
                return NeteaseUsbSession(driver, connection, connected, handle, pcm)
            } catch (e: Throwable) {
                runCatching { if (handle != 0L) driver.close(handle) }.exceptionOrNull()?.let(e::addSuppressed)
                runCatching { connection.close() }.exceptionOrNull()?.let(e::addSuppressed)
                throw e
            }
        }
    }
}
