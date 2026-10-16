package io.github.currencortex.music.data.effects

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater

class NcaeDecoderTest {
    private fun container(plain: ByteArray, type: Int = 1): ByteArray {
        val compressor = Deflater(6, true)
        val scratch = ByteArray(plain.size + 128)
        val compressed = try {
            compressor.setInput(plain); compressor.finish(); scratch.copyOf(compressor.deflate(scratch))
        } finally { compressor.end() }
        val key = ByteArray(12) { (it + 40).toByte() }
        val masked = ByteArray(13) { i -> if (i == 4) 75 else (key[if (i < 4) i else i - 1].toInt() xor 75).toByte() }
        val box = IntArray(256) { it }; var j = 0
        repeat(256) { i -> j = (j + box[i] + (key[i % 12].toInt() and 255)) and 255; val swap=box[i]; box[i]=box[j]; box[j]=swap }
        val encrypted = compressed.mapIndexed { i, byte -> val at=(i+1) and 255
            (byte.toInt() xor box[(box[(box[at]+at) and 255]+box[at]) and 255]).toByte() }.toByteArray()
        return ByteBuffer.allocate(17 + masked.size + encrypted.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(byteArrayOf(78,67,65,69)); putInt(encrypted.size); putInt(0); putShort(0); putShort(type.toShort()); put(masked.size.toByte()); put(masked); put(encrypted)
        }.array()
    }
    @Test fun decodesJsonAndBinaryAndMultipleKeyStreamCycles() {
        val json = """{"se":{"on":true,"presence":6}}""".toByteArray()
        assertArrayEquals(json, NcaeDecoder.decode(container(json)).bytes)
        val binary = ByteArray(10000) { (it * 37).toByte() }
        val decoded = NcaeDecoder.decode(container(binary, 2))
        assertEquals(2, decoded.type); assertArrayEquals(binary, decoded.bytes)
    }
    @Test fun rejectsTruncatedMagicLengthsTypesAndCorruptPayload() {
        val valid = container("hello".toByteArray())
        listOf(valid.copyOf(17), valid.copyOf(valid.size-1), valid.clone().also { it[0]=0 },
            valid.clone().also { it[14]=99 }, valid.clone().also { it[16]=4 },
            // XORing the two DEFLATE block-type bits reserves type 3 after decryption.
            valid.clone().also { it[30]=(it[30].toInt() xor 4).toByte() })
            .forEach { assertThrows(Exception::class.java) { NcaeDecoder.decode(it) } }
    }
    @Test fun rejectsOversizedDecodedOutput() {
        assertThrows(IllegalArgumentException::class.java) { NcaeDecoder.decode(container(ByteArray(2*1024*1024+1))) }
    }
}
