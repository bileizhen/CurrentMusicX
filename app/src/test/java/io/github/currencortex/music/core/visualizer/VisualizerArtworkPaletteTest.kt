package io.github.currencortex.music.core.visualizer

import org.junit.Assert.*
import org.junit.Test

class VisualizerArtworkPaletteTest {
    @Test fun darkBordersDoNotDrownOutTheCoverSubject() {
        val palette = VisualizerArtworkPalette.fromPixels(IntArray(120) { 0xFF000000.toInt() } + IntArray(24) { 0xFFFF3040.toInt() })!!
        assertTrue((palette.primary ushr 16 and 255) > (palette.primary and 255) * 2)
    }
    @Test fun differentArtworkProducesDifferentStablePalettes() {
        val red = IntArray(144) { 0xFFDE2540.toInt() }
        val blue = IntArray(144) { 0xFF2559DE.toInt() }
        assertNotEquals(VisualizerArtworkPalette.fromPixels(red), VisualizerArtworkPalette.fromPixels(blue))
        assertEquals(VisualizerArtworkPalette.fromPixels(red), VisualizerArtworkPalette.fromPixels(red.reversedArray()))
        assertArrayEquals(IntArray(144) { 0xFFDE2540.toInt() }, red)
    }
    @Test fun grayscaleRemainsNeutralAndTransparentArtworkUsesFallback() {
        assertNull(VisualizerArtworkPalette.fromPixels(IntArray(144)))
        val p = VisualizerArtworkPalette.fromPixels(IntArray(144) { 0xFF888888.toInt() })!!.primary
        assertEquals(p ushr 16 and 255, p and 255)
        assertEquals(p ushr 8 and 255, p and 255)
    }
    @Test fun accentCanComeFromAnotherProminentColorInTheSameArtwork() {
        val p = VisualizerArtworkPalette.fromPixels(IntArray(80) { 0xFFEE3030.toInt() } + IntArray(64) { 0xFF3030EE.toInt() })!!
        assertTrue((p.primary ushr 16 and 255) > (p.primary and 255))
        assertTrue((p.accent and 255) > (p.accent ushr 16 and 255))
    }
}
