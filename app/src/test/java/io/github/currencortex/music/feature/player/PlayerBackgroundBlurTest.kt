package io.github.currencortex.music.feature.player

import org.junit.Assert.*
import org.junit.Test

class PlayerBackgroundBlurTest {
    @Test fun zeroBlurPreservesTheImageAndDoesNotMutateInput() {
        val input = intArrayOf(0xff000000.toInt(), 0xffffffff.toInt())
        val output = blurBackgroundPixels(input, 2, 1, 0)
        assertArrayEquals(input, output); assertNotSame(input, output)
    }
    @Test fun blurSoftensEdgesAndPreservesConstantColorAndOpacity() {
        val input = IntArray(25) { if (it % 5 < 2) 0xff000000.toInt() else 0xffffffff.toInt() }
        val result = blurBackgroundPixels(input, 5, 5, 1)
        assertTrue((result[2] and 255) in 1..254)
        assertTrue(result.all { it ushr 24 == 255 })
        val constant = IntArray(16) { 0xff123456.toInt() }
        assertArrayEquals(constant, blurBackgroundPixels(constant, 4, 4, 48))
        assertEquals(0xff123456.toInt(), blurBackgroundPixels(intArrayOf(0xff123456.toInt()), 1, 1, 80).single())
    }
}
