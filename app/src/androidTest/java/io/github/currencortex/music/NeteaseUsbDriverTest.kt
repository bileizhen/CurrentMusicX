package io.github.currencortex.music

import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.NeteaseUsbBridge
import io.github.currencortex.music.core.media.NeteaseUsbDevices
import org.junit.Assert.*
import org.junit.Test

/** No device claim, permission prompt, playback, account, or user queue access. */
class NeteaseUsbDriverTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    @Test fun pinnedOriginalLibraryLoadsAndAllRecoveredInterfacesResolve() {
        NeteaseUsbBridge.load(context)
        assertTrue(NeteaseUsbBridge.checkAbi())
    }
    @Test fun invalidInputIsRejectedBeforeUsbDeviceClaim() {
        NeteaseUsbBridge.load(context)
        val error = assertThrows(IllegalStateException::class.java) { NeteaseUsbBridge.open(-1, 192000, 2, 21, 24) }
        assertTrue(error.message.orEmpty().contains("USB_PCM"))
    }
    @Test fun nativeSelectorSeparatesValidBitsContainersRatesChannelsAndPcmTypes() {
        NeteaseUsbBridge.load(context)
        assertTrue(NeteaseUsbBridge.testFormatSelection())
    }
    @Test fun nativeRunningIsHealthyWhileTimeoutErrorsDetachAndUnknownStatesFail() {
        NeteaseUsbBridge.load(context)
        assertTrue(NeteaseUsbBridge.testTransferState(0, false))
        assertTrue(NeteaseUsbBridge.testTransferState(1, false))
        for (status in listOf(-1, 2, 3, 4, Int.MAX_VALUE)) assertFalse("status=$status", NeteaseUsbBridge.testTransferState(status, false))
        for (status in 0..3) assertFalse("detached status=$status", NeteaseUsbBridge.testTransferState(status, true))
    }
    @Test fun nativeDiagnosticsIncludeAdapterVersionAndAreConsumedOnlyOnce() {
        NeteaseUsbBridge.load(context)
        assertThrows(IllegalStateException::class.java) { NeteaseUsbBridge.open(-1, 48000, 2, 2, 16) }
        assertTrue(NeteaseUsbBridge.diagnostics().contains("adapter=${NeteaseUsbBridge.ADAPTER}"))
        assertEquals("", NeteaseUsbBridge.diagnostics())
    }
    @Test fun noDacIsReportedWithoutOpeningNormalOutput() {
        val devices = NeteaseUsbDevices.devices(context)
        if (devices.isEmpty()) {
            val error = assertThrows(IllegalStateException::class.java) { NeteaseUsbDevices.select(devices, "") }
            assertTrue(error.message.orEmpty().contains("USB_MISSING"))
        }
    }
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun actualRawSinkStopsWithUsbMissingInsteadOfAudioTrackFallback() {
        if (NeteaseUsbDevices.devices(context).isNotEmpty()) return
        val status = mutableListOf<String>()
        val settings = io.github.currencortex.music.data.settings.AudioOutputSettings(
            mode = io.github.currencortex.music.data.settings.AudioOutputMode.NETEASE_USB)
        val sink = io.github.currencortex.music.core.media.BitPerfectAudioSink(context,
            androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context).build(), { settings }, status::add)
        val format = androidx.media3.common.Format.Builder().setSampleMimeType(androidx.media3.common.MimeTypes.AUDIO_RAW)
            .setSampleRate(48000).setChannelCount(2).setPcmEncoding(2).build()
        try {
            sink.configure(androidx.media3.exoplayer.audio.AudioSink.AudioSinkConfig.Builder(format).build())
            assertThrows(androidx.media3.exoplayer.audio.AudioSink.InitializationException::class.java) {
                sink.handleBuffer(java.nio.ByteBuffer.allocateDirect(8), 0, 1)
            }
            assertTrue(status.last().contains("USB_MISSING"))
            assertTrue(status.last().startsWith("USB 独占（网易云） 已停止"))
        } finally { sink.reset() }
    }
}
