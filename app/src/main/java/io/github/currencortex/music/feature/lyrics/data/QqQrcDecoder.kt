// QRC decryption informed by WXRIW/QQMusicDecoder, MIT.
// https://github.com/WXRIW/QQMusicDecoder/tree/0e1494194523dd885405812a91ee9b9702bfb30c
// Original QQ Music cipher reconstruction by WXRIW, cnbluefire and LyricDecoder contributors.
package io.github.currencortex.music.feature.lyrics.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.InflaterInputStream

/**
 * QQ Music's QRC container is a zero-padded, deflated payload encrypted by a DES-like
 * block cipher. The substitution boxes are QQ-specific, so JCE DESede cannot decode it.
 */
internal object QqQrcDecoder {
    private val QQ_KEY = "!@#)(*$%123ZXC!@!@#)(NHL".toByteArray(Charsets.US_ASCII)
    private val HEX = Regex("[0-9A-Fa-f]+")

    fun decrypt(encryptedHex: String): String {
        val compact = encryptedHex.filterNot(Char::isWhitespace)
        require(compact.length % 16 == 0 && compact.length <= LyricsFileCodec.MAX_BYTES * 2 &&
            HEX.matches(compact)) { "QQ 音乐 QRC 密文无效" }
        val encrypted = ByteArray(compact.length / 2) { index ->
            compact.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
        val schedules = Array(3) { Array(16) { IntArray(6) } }
        // Decrypt order is D(K3), E(K2), D(K1), matching QQ Music's client.
        keySchedule(QQ_KEY.copyOfRange(0, 8), schedules[2], decrypt = true)
        keySchedule(QQ_KEY.copyOfRange(8, 16), schedules[1], decrypt = false)
        keySchedule(QQ_KEY.copyOfRange(16, 24), schedules[0], decrypt = true)

        val decrypted = ByteArray(encrypted.size)
        for (offset in encrypted.indices step 8) {
            val block = encrypted.copyOfRange(offset, offset + 8)
            val first = ByteArray(8)
            val second = ByteArray(8)
            crypt(block, first, schedules[0])
            crypt(first, second, schedules[1])
            crypt(second, decrypted, offset, schedules[2])
        }
        return inflate(decrypted).toString(Charsets.UTF_8).removePrefix("\uFEFF")
    }

    private fun inflate(bytes: ByteArray): ByteArray = InflaterInputStream(ByteArrayInputStream(bytes)).use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val size = input.read(buffer)
            if (size < 0) break
            require(output.size() + size <= LyricsFileCodec.MAX_BYTES) { "QQ 音乐 QRC 解压后过大" }
            output.write(buffer, 0, size)
        }
        output.toByteArray()
    }

    private fun keySchedule(key: ByteArray, schedule: Array<IntArray>, decrypt: Boolean) {
        var c = 0
        var d = 0
        for (i in 0 until 28) {
            c = c or bitNum(key, KEY_PERM_C[i], 31 - i)
            d = d or bitNum(key, KEY_PERM_D[i], 31 - i)
        }
        for (i in 0 until 16) {
            val shift = KEY_RND_SHIFT[i]
            c = ((c shl shift) or (c ushr (28 - shift))) and 0xfffffff0.toInt()
            d = ((d shl shift) or (d ushr (28 - shift))) and 0xfffffff0.toInt()
            val target = if (decrypt) 15 - i else i
            for (j in 0 until 24) {
                schedule[target][j / 8] = schedule[target][j / 8] or
                    bitNumIntr(c, KEY_COMPRESSION[j], 7 - j % 8)
            }
            for (j in 24 until 48) {
                schedule[target][j / 8] = schedule[target][j / 8] or
                    bitNumIntr(d, KEY_COMPRESSION[j] - 27, 7 - j % 8)
            }
        }
    }

    private fun crypt(input: ByteArray, output: ByteArray, schedule: Array<IntArray>) =
        crypt(input, output, 0, schedule)

    private fun crypt(input: ByteArray, output: ByteArray, outputOffset: Int, schedule: Array<IntArray>) {
        val state = IntArray(2)
        initialPermutation(state, input)
        for (round in 0 until 15) {
            val left = state[0]
            state[0] = state[1]
            state[1] = f(state[1], schedule[round]) xor left
        }
        state[0] = f(state[1], schedule[15]) xor state[0]
        inversePermutation(state, output, outputOffset)
    }

    private fun f(state: Int, key: IntArray): Int {
        val expanded = IntArray(6)
        val high = bitNumIntl(state, 31, 0) or ((state and 0xf0000000.toInt()) ushr 1) or
            bitNumIntl(state, 4, 5) or bitNumIntl(state, 3, 6) or
            ((state and 0x0f000000) ushr 3) or bitNumIntl(state, 8, 11) or
            bitNumIntl(state, 7, 12) or ((state and 0x00f00000) ushr 5) or
            bitNumIntl(state, 12, 17) or bitNumIntl(state, 11, 18) or
            ((state and 0x000f0000) ushr 7) or bitNumIntl(state, 16, 23)
        val low = bitNumIntl(state, 15, 0) or ((state and 0x0000f000) shl 15) or
            bitNumIntl(state, 20, 5) or bitNumIntl(state, 19, 6) or
            ((state and 0x00000f00) shl 13) or bitNumIntl(state, 24, 11) or
            bitNumIntl(state, 23, 12) or ((state and 0x000000f0) shl 11) or
            bitNumIntl(state, 28, 17) or bitNumIntl(state, 27, 18) or
            ((state and 0x0000000f) shl 9) or bitNumIntl(state, 0, 23)
        expanded[0] = (high ushr 24) and 0xff
        expanded[1] = (high ushr 16) and 0xff
        expanded[2] = (high ushr 8) and 0xff
        expanded[3] = (low ushr 24) and 0xff
        expanded[4] = (low ushr 16) and 0xff
        expanded[5] = (low ushr 8) and 0xff
        for (index in 0 until 6) expanded[index] = expanded[index] xor key[index]

        var substituted = (S_BOXES[0][sBoxBit(expanded[0] ushr 2)] shl 28) or
            (S_BOXES[1][sBoxBit(((expanded[0] and 0x03) shl 4) or (expanded[1] ushr 4))] shl 24) or
            (S_BOXES[2][sBoxBit(((expanded[1] and 0x0f) shl 2) or (expanded[2] ushr 6))] shl 20) or
            (S_BOXES[3][sBoxBit(expanded[2] and 0x3f)] shl 16) or
            (S_BOXES[4][sBoxBit(expanded[3] ushr 2)] shl 12) or
            (S_BOXES[5][sBoxBit(((expanded[3] and 0x03) shl 4) or (expanded[4] ushr 4))] shl 8) or
            (S_BOXES[6][sBoxBit(((expanded[4] and 0x0f) shl 2) or (expanded[5] ushr 6))] shl 4) or
            S_BOXES[7][sBoxBit(expanded[5] and 0x3f)]

        substituted = bitNumIntl(substituted, 15, 0) or bitNumIntl(substituted, 6, 1) or
            bitNumIntl(substituted, 19, 2) or bitNumIntl(substituted, 20, 3) or
            bitNumIntl(substituted, 28, 4) or bitNumIntl(substituted, 11, 5) or
            bitNumIntl(substituted, 27, 6) or bitNumIntl(substituted, 16, 7) or
            bitNumIntl(substituted, 0, 8) or bitNumIntl(substituted, 14, 9) or
            bitNumIntl(substituted, 22, 10) or bitNumIntl(substituted, 25, 11) or
            bitNumIntl(substituted, 4, 12) or bitNumIntl(substituted, 17, 13) or
            bitNumIntl(substituted, 30, 14) or bitNumIntl(substituted, 9, 15) or
            bitNumIntl(substituted, 1, 16) or bitNumIntl(substituted, 7, 17) or
            bitNumIntl(substituted, 23, 18) or bitNumIntl(substituted, 13, 19) or
            bitNumIntl(substituted, 31, 20) or bitNumIntl(substituted, 26, 21) or
            bitNumIntl(substituted, 2, 22) or bitNumIntl(substituted, 8, 23) or
            bitNumIntl(substituted, 18, 24) or bitNumIntl(substituted, 12, 25) or
            bitNumIntl(substituted, 29, 26) or bitNumIntl(substituted, 5, 27) or
            bitNumIntl(substituted, 21, 28) or bitNumIntl(substituted, 10, 29) or
            bitNumIntl(substituted, 3, 30) or bitNumIntl(substituted, 24, 31)
        return substituted
    }

    private fun initialPermutation(state: IntArray, input: ByteArray) {
        state[0] = bitNum(input, 57, 31) or bitNum(input, 49, 30) or bitNum(input, 41, 29) or
            bitNum(input, 33, 28) or bitNum(input, 25, 27) or bitNum(input, 17, 26) or
            bitNum(input, 9, 25) or bitNum(input, 1, 24) or bitNum(input, 59, 23) or
            bitNum(input, 51, 22) or bitNum(input, 43, 21) or bitNum(input, 35, 20) or
            bitNum(input, 27, 19) or bitNum(input, 19, 18) or bitNum(input, 11, 17) or
            bitNum(input, 3, 16) or bitNum(input, 61, 15) or bitNum(input, 53, 14) or
            bitNum(input, 45, 13) or bitNum(input, 37, 12) or bitNum(input, 29, 11) or
            bitNum(input, 21, 10) or bitNum(input, 13, 9) or bitNum(input, 5, 8) or
            bitNum(input, 63, 7) or bitNum(input, 55, 6) or bitNum(input, 47, 5) or
            bitNum(input, 39, 4) or bitNum(input, 31, 3) or bitNum(input, 23, 2) or
            bitNum(input, 15, 1) or bitNum(input, 7, 0)
        state[1] = bitNum(input, 56, 31) or bitNum(input, 48, 30) or bitNum(input, 40, 29) or
            bitNum(input, 32, 28) or bitNum(input, 24, 27) or bitNum(input, 16, 26) or
            bitNum(input, 8, 25) or bitNum(input, 0, 24) or bitNum(input, 58, 23) or
            bitNum(input, 50, 22) or bitNum(input, 42, 21) or bitNum(input, 34, 20) or
            bitNum(input, 26, 19) or bitNum(input, 18, 18) or bitNum(input, 10, 17) or
            bitNum(input, 2, 16) or bitNum(input, 60, 15) or bitNum(input, 52, 14) or
            bitNum(input, 44, 13) or bitNum(input, 36, 12) or bitNum(input, 28, 11) or
            bitNum(input, 20, 10) or bitNum(input, 12, 9) or bitNum(input, 4, 8) or
            bitNum(input, 62, 7) or bitNum(input, 54, 6) or bitNum(input, 46, 5) or
            bitNum(input, 38, 4) or bitNum(input, 30, 3) or bitNum(input, 22, 2) or
            bitNum(input, 14, 1) or bitNum(input, 6, 0)
    }

    private fun inversePermutation(state: IntArray, output: ByteArray, offset: Int) {
        output[offset + 3] = (bitNumIntr(state[1], 7, 7) or bitNumIntr(state[0], 7, 6) or
            bitNumIntr(state[1], 15, 5) or bitNumIntr(state[0], 15, 4) or
            bitNumIntr(state[1], 23, 3) or bitNumIntr(state[0], 23, 2) or
            bitNumIntr(state[1], 31, 1) or bitNumIntr(state[0], 31, 0)).toByte()
        output[offset + 2] = (bitNumIntr(state[1], 6, 7) or bitNumIntr(state[0], 6, 6) or
            bitNumIntr(state[1], 14, 5) or bitNumIntr(state[0], 14, 4) or
            bitNumIntr(state[1], 22, 3) or bitNumIntr(state[0], 22, 2) or
            bitNumIntr(state[1], 30, 1) or bitNumIntr(state[0], 30, 0)).toByte()
        output[offset + 1] = (bitNumIntr(state[1], 5, 7) or bitNumIntr(state[0], 5, 6) or
            bitNumIntr(state[1], 13, 5) or bitNumIntr(state[0], 13, 4) or
            bitNumIntr(state[1], 21, 3) or bitNumIntr(state[0], 21, 2) or
            bitNumIntr(state[1], 29, 1) or bitNumIntr(state[0], 29, 0)).toByte()
        output[offset] = (bitNumIntr(state[1], 4, 7) or bitNumIntr(state[0], 4, 6) or
            bitNumIntr(state[1], 12, 5) or bitNumIntr(state[0], 12, 4) or
            bitNumIntr(state[1], 20, 3) or bitNumIntr(state[0], 20, 2) or
            bitNumIntr(state[1], 28, 1) or bitNumIntr(state[0], 28, 0)).toByte()
        output[offset + 7] = (bitNumIntr(state[1], 3, 7) or bitNumIntr(state[0], 3, 6) or
            bitNumIntr(state[1], 11, 5) or bitNumIntr(state[0], 11, 4) or
            bitNumIntr(state[1], 19, 3) or bitNumIntr(state[0], 19, 2) or
            bitNumIntr(state[1], 27, 1) or bitNumIntr(state[0], 27, 0)).toByte()
        output[offset + 6] = (bitNumIntr(state[1], 2, 7) or bitNumIntr(state[0], 2, 6) or
            bitNumIntr(state[1], 10, 5) or bitNumIntr(state[0], 10, 4) or
            bitNumIntr(state[1], 18, 3) or bitNumIntr(state[0], 18, 2) or
            bitNumIntr(state[1], 26, 1) or bitNumIntr(state[0], 26, 0)).toByte()
        output[offset + 5] = (bitNumIntr(state[1], 1, 7) or bitNumIntr(state[0], 1, 6) or
            bitNumIntr(state[1], 9, 5) or bitNumIntr(state[0], 9, 4) or
            bitNumIntr(state[1], 17, 3) or bitNumIntr(state[0], 17, 2) or
            bitNumIntr(state[1], 25, 1) or bitNumIntr(state[0], 25, 0)).toByte()
        output[offset + 4] = (bitNumIntr(state[1], 0, 7) or bitNumIntr(state[0], 0, 6) or
            bitNumIntr(state[1], 8, 5) or bitNumIntr(state[0], 8, 4) or
            bitNumIntr(state[1], 16, 3) or bitNumIntr(state[0], 16, 2) or
            bitNumIntr(state[1], 24, 1) or bitNumIntr(state[0], 24, 0)).toByte()
    }

    private fun bitNum(bytes: ByteArray, bit: Int, target: Int): Int =
        (((bytes[bit / 32 * 4 + 3 - (bit % 32) / 8].toInt() ushr (7 - bit % 8)) and 1) shl target)

    private fun bitNumIntr(value: Int, bit: Int, target: Int): Int =
        (((value ushr (31 - bit)) and 1) shl target)

    private fun bitNumIntl(value: Int, shift: Int, target: Int): Int =
        ((value shl shift) and Int.MIN_VALUE) ushr target

    private fun sBoxBit(value: Int): Int =
        (value and 0x20) or ((value and 0x1f) ushr 1) or ((value and 1) shl 4)

    private val KEY_RND_SHIFT = intArrayOf(1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1)
    private val KEY_PERM_C = intArrayOf(56, 48, 40, 32, 24, 16, 8, 0, 57, 49, 41, 33, 25, 17,
        9, 1, 58, 50, 42, 34, 26, 18, 10, 2, 59, 51, 43, 35)
    private val KEY_PERM_D = intArrayOf(62, 54, 46, 38, 30, 22, 14, 6, 61, 53, 45, 37, 29, 21,
        13, 5, 60, 52, 44, 36, 28, 20, 12, 4, 27, 19, 11, 3)
    private val KEY_COMPRESSION = intArrayOf(13, 16, 10, 23, 0, 4, 2, 27, 14, 5, 20, 9,
        22, 18, 11, 3, 25, 7, 15, 6, 26, 19, 12, 1,
        40, 51, 30, 36, 46, 54, 29, 39, 50, 44, 32, 47,
        43, 48, 38, 55, 33, 52, 45, 41, 49, 35, 28, 31)

    private val S_BOXES = arrayOf(
        intArrayOf(14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7,
            0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8,
            4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0,
            15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13),
        intArrayOf(15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10,
            3, 13, 4, 7, 15, 2, 8, 15, 12, 0, 1, 10, 6, 9, 11, 5,
            0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15,
            13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9),
        intArrayOf(10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8,
            13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1,
            13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7,
            1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12),
        intArrayOf(7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15,
            13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9,
            10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4,
            3, 15, 0, 6, 10, 10, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14),
        intArrayOf(2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9,
            14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6,
            4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14,
            11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3),
        intArrayOf(12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11,
            10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8,
            9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6,
            4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13),
        intArrayOf(4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1,
            13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6,
            1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2,
            6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12),
        intArrayOf(13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7,
            1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2,
            7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8,
            2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11)
    )
}
