package io.github.currencortex.music.core.media

/** Descriptor declarations, not a claim that the DAC accepted a stream or uses this clock. */
internal data class UsbDeclaredPcmFormat(
    val interfaceNumber: Int, val alternateSetting: Int, val uacVersion: Int,
    val channels: Int, val subslotBytes: Int, val validBits: Int,
    val discreteRates: List<Int>, val continuousRateRange: IntRange?,
    val outputEndpoint: Int, val maxPacketBytes: Int, val interval: Int,
)
internal data class UsbAudioDescriptorReport(val formats: List<UsbDeclaredPcmFormat>, val error: String? = null) {
    val valid get() = error == null
}

internal fun usableUsbIsoOutput(direction: Int, transferType: Int, maxPacketBytes: Int, attributes: Int): Boolean =
    direction == 0 && transferType == 1 && maxPacketBytes > 0 && ((attributes shr 4) and 3) in setOf(0, 2)

/** Independently implemented from USB Audio Class descriptor field layouts.
 * UAC2 rate support belongs to its clock-source controls, not the format descriptor.
 * No control transfer, interface claim, device permission request or audio I/O occurs here.
 */
internal object UsbAudioDescriptorParser {
    private data class Alternate(val number: Int, val alternate: Int, val protocol: Int,
        var pcm: Boolean = false, var channels: Int = 0, var bytes: Int = 0, var bits: Int = 0,
        var rates: List<Int> = emptyList(), var range: IntRange? = null,
        val endpoints: MutableList<Triple<Int, Int, Int>> = mutableListOf())

    fun parse(raw: ByteArray): UsbAudioDescriptorReport {
        if (raw.isEmpty() || raw.size > 1024 * 1024) return UsbAudioDescriptorReport(emptyList(), "描述符为空或超过读取上限")
        val formats = mutableListOf<UsbDeclaredPcmFormat>()
        var current: Alternate? = null
        var configurationEnd: Int? = null
        fun finish() {
            val a = current ?: return
            if (!a.pcm || a.channels !in 1..32 || a.bytes !in 1..4 || a.bits !in 1..a.bytes * 8) return
            a.endpoints.forEach { (endpoint, packet, interval) ->
                formats += UsbDeclaredPcmFormat(a.number, a.alternate, if (a.protocol == 0x20) 2 else 1,
                    a.channels, a.bytes, a.bits, a.rates, a.range, endpoint, packet, interval)
            }
        }
        fun byte(offset: Int) = raw[offset].toInt() and 255
        fun word(offset: Int) = byte(offset) or (byte(offset + 1) shl 8)
        fun rate(offset: Int) = word(offset) or (byte(offset + 2) shl 16)
        fun invalid() = UsbAudioDescriptorReport(emptyList(), "USB Audio 描述符结构不完整或字段无效")
        var offset = 0
        while (offset < raw.size) {
            if (offset == configurationEnd) { finish(); current = null; configurationEnd = null }
            if (raw.size - offset < 2) return invalid()
            val length = byte(offset)
            if (length < 2 || length > raw.size - offset) return invalid()
            if (configurationEnd?.let { offset + length > it } == true) return invalid()
            when (byte(offset + 1)) {
                2 -> { // Configuration boundary; interfaces in separate configurations never combine.
                    if (configurationEnd != null) return invalid()
                    if (length < 9 || word(offset + 2) < length || word(offset + 2) > raw.size - offset) return invalid()
                    finish(); current = null
                    configurationEnd = offset + word(offset + 2)
                }
                4 -> {
                    if (length < 9) return invalid()
                    finish()
                    current = if (byte(offset + 5) == 1 && byte(offset + 6) == 2 && byte(offset + 7) in listOf(0, 0x20))
                        Alternate(byte(offset + 2), byte(offset + 3), byte(offset + 7)) else null
                }
                0x24 -> {
                    val a = current
                    if (a != null) {
                        if (length < 3) return invalid()
                        when (byte(offset + 2)) {
                            1 -> if (a.protocol == 0) {
                                if (length < 7) return invalid()
                                a.pcm = word(offset + 5) == 1
                            } else {
                                if (length < 16) return invalid()
                                a.pcm = byte(offset + 5) == 1 && (byte(offset + 6) and 1) != 0
                                a.channels = byte(offset + 10)
                            }
                            2 -> if (a.protocol == 0) {
                                if (length < 8) return invalid()
                                if (byte(offset + 3) == 1) {
                                    a.channels = byte(offset + 4); a.bytes = byte(offset + 5); a.bits = byte(offset + 6)
                                    val count = byte(offset + 7)
                                    if (count == 0) {
                                        if (length < 14) return invalid()
                                        val low = rate(offset + 8); val high = rate(offset + 11)
                                        if (low <= 0 || high < low) return invalid()
                                        a.range = low..high
                                    } else {
                                        if (length < 8 + count * 3) return invalid()
                                        a.rates = List(count) { rate(offset + 8 + it * 3) }.distinct()
                                        if (a.rates.any { it <= 0 }) return invalid()
                                    }
                                }
                            } else {
                                if (length < 6) return invalid()
                                if (byte(offset + 3) == 1) { a.bytes = byte(offset + 4); a.bits = byte(offset + 5) }
                            }
                        }
                    }
                }
                5 -> {
                    val a = current
                    if (a != null) {
                        if (length < 7) return invalid()
                        val address = byte(offset + 2); val attributes = byte(offset + 3)
                        val packet = word(offset + 4); val extraTransactions = (packet shr 11) and 3
                        if (extraTransactions == 3) return invalid()
                        val packetBytes = (packet and 0x7ff) * (extraTransactions + 1)
                        if (usableUsbIsoOutput(address and 0x80, attributes and 3, packetBytes, attributes)) {
                            val interval = byte(offset + 6)
                            if (interval !in 1..16) return invalid()
                            a.endpoints += Triple(address, packetBytes, interval)
                        }
                    }
                }
            }
            offset += length
        }
        finish()
        return UsbAudioDescriptorReport(formats)
    }
}
