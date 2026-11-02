package io.github.currencortex.music

import io.github.currencortex.music.core.media.NativeAudioBridge
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

/** JNI guard tests only: never opens a real stream, changes focus, or claims a USB device. */
class NativeAudioLifecycleTest {
    @Test fun releasedUnknownAndRepeatedReleaseTokensNeverDereferenceNativeMemory() {
        assertTrue(NativeAudioBridge.available)
        for (handle in listOf(0L, -1L, Long.MAX_VALUE)) {
            NativeAudioBridge.close(handle); NativeAudioBridge.close(handle)
            NativeAudioBridge.play(handle); NativeAudioBridge.pause(handle); NativeAudioBridge.volume(handle, Float.NaN)
            assertEquals(-899, NativeAudioBridge.write(handle, ByteBuffer.allocateDirect(8), 0, 8))
            assertEquals(0, NativeAudioBridge.api(handle)); assertEquals(-899, NativeAudioBridge.error(handle))
            assertEquals(0L, NativeAudioBridge.position(handle)); assertEquals(0L, NativeAudioBridge.bufferFrames(handle))
            assertNull(NativeAudioBridge.snapshot(handle))
        }
    }
    @Test fun invalidStreamParametersAreRejectedBeforeOpeningHardware() {
        assertTrue(NativeAudioBridge.available)
        assertEquals(0L, NativeAudioBridge.open(9, 48000, 2, 0, false))
        assertEquals(0L, NativeAudioBridge.open(2, -1, 2, 0, false))
        assertEquals(0L, NativeAudioBridge.open(2, 48000, 0, 0, false))
        assertEquals(0L, NativeAudioBridge.open(2, 48000, 2, -1, false))
        assertEquals(0L, NativeAudioBridge.open(2, 48000, 2, 0, false, 0))
    }
}
