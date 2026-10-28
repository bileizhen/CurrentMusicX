package io.github.currencortex.music.core.media

import org.junit.Assert.*
import org.junit.Test

class BitPerfectSessionTest {
    private val pcm = BitPerfectPcm(192000, 21, 2, 12)
    private val usb = BitPerfectDevice(42, "usb", "NK1 test double", 11,
        listOf(44100, 48000, 192000), listOf(21, 22, 4), listOf(12), listOf(3))
    private inner class Track(override var format: BitPerfectPcm = pcm) : BitPerfectTrack {
        override var initialized = true
        override var playing = false
        override var routedDeviceId: Int? = null
        var preferred = true
        var releases = 0
        var pauses = 0
        var releaseThrows = false
        override fun prefer(device: BitPerfectDevice) = preferred
        override fun pauseAndFlush() { pauses++ }
        override fun release() { releases++; if (releaseThrows) error("release") }
    }
    private inner class Platform : BitPerfectPlatform {
        override var sdk = 34
        override var usage = 1
        var connected = listOf(usb)
        var supported = listOf(BitPerfectMixer(0, pcm, 1))
        var retained: BitPerfectMixer? = null
        var accept = true
        var retain = true
        var queryFails = false
        var createFails = false
        var queries = 0
        var clears = 0
        var creates = 0
        var time = 0L
        var track = Track()
        val logs = mutableListOf<String>()
        val reports = mutableListOf<String>()
        override fun devices() = connected
        override fun mixers(device: BitPerfectDevice): List<BitPerfectMixer> {
            queries++; if (queryFails) error("query"); return supported
        }
        override fun setPreferred(device: BitPerfectDevice, mixer: BitPerfectMixer): Boolean {
            if (accept && retain) retained = mixer
            return accept
        }
        override fun getPreferred(device: BitPerfectDevice) = retained
        override fun clearPreferred(device: BitPerfectDevice): Boolean { clears++; retained = null; return true }
        override fun createTrack(mixer: BitPerfectMixer): BitPerfectTrack {
            creates++; if (createFails) error("create"); return track
        }
        fun open(format: BitPerfectPcm = pcm) = BitPerfectSession.open(this, format, "usb", { time }, logs::add, reports::add)
    }
    private fun failure(code: BitPerfectFailureCode, block: () -> Unit) {
        try { block(); fail("Expected $code") } catch (e: BitPerfectFailure) { assertEquals(code, e.code) }
    }

    @Test fun recognizedUsbWithoutBitPerfectIsNotHardwareRejection() {
        val p = Platform(); p.supported = listOf(BitPerfectMixer(0, pcm, 0))
        failure(BitPerfectFailureCode.NO_BIT_PERFECT_MIXER) { p.open() }
        assertEquals(0, p.creates); assertTrue(p.logs.any { "hardware capability not established" in it })
    }
    @Test fun device192IsNotAMixer192Configuration() {
        val p = Platform(); p.supported = listOf(BitPerfectMixer(0, pcm.copy(rate = 48000), 1))
        failure(BitPerfectFailureCode.MIXER_RATE) { p.open() }; assertEquals(0, p.creates)
    }
    @Test fun only441And48CannotResampleAndClaim192() {
        val p = Platform(); p.supported = listOf(44100, 48000).mapIndexed { i, rate -> BitPerfectMixer(i, pcm.copy(rate = rate), 1) }
        failure(BitPerfectFailureCode.MIXER_RATE) { p.open() }
    }
    @Test fun packed24DoesNotMatchInteger32OrFloat() {
        for (encoding in listOf(22, 4)) {
            val p = Platform(); p.supported = listOf(BitPerfectMixer(0, pcm.copy(encoding = encoding), 1))
            failure(BitPerfectFailureCode.MIXER_ENCODING) { p.open() }
        }
    }
    @Test fun requestRejectedCreatesNoTrack() {
        val p = Platform(); p.accept = false
        failure(BitPerfectFailureCode.REQUEST_REJECTED) { p.open() }
        assertEquals(0, p.creates); assertEquals(0, p.clears)
    }
    @Test fun successfulRequestMustBeRetained() {
        val p = Platform(); p.retain = false
        failure(BitPerfectFailureCode.PREFERENCE_NOT_RETAINED) { p.open() }; assertEquals(1, p.clears)
    }
    @Test fun wrongActualRouteStopsAndReleasesOnce() {
        val p = Platform(); val session = p.open()
        p.track.playing = true; p.track.routedDeviceId = 7
        failure(BitPerfectFailureCode.ROUTE_MISMATCH) { session.verify() }
        session.close(); assertEquals(1, p.track.releases); assertEquals(1, p.clears)
    }
    @Test fun wrongRouteAtInitializationAlsoReleasesOnce() {
        val p = Platform(); p.track.routedDeviceId = 7
        failure(BitPerfectFailureCode.ROUTE_MISMATCH) { p.open() }
        assertEquals(1, p.track.releases); assertEquals(1, p.clears)
    }
    @Test fun disconnectDuringPlaybackReleasesAndDoesNotFallback() {
        val p = Platform(); val session = p.open()
        p.track.playing = true; p.track.routedDeviceId = usb.id; session.verify()
        p.connected = emptyList(); p.time = 501
        failure(BitPerfectFailureCode.DISCONNECTED) { session.verify() }
        assertEquals(1, p.track.releases); assertEquals(1, p.creates)
    }
    @Test fun changedSongFormatUsesNewMixerAndTrackAfterCleanup() {
        val p = Platform(); val first = p.open(); val old = p.track; first.close()
        val next = pcm.copy(rate = 44100, encoding = 2)
        p.supported = listOf(BitPerfectMixer(0, next, 1)); p.track = Track(next)
        val second = p.open(next)
        assertEquals(next, second.mixer.format); assertEquals(1, old.releases)
        second.close(); assertEquals(2, p.clears)
    }
    @Test fun api33DoesNotCallMixerApi() {
        val p = Platform(); p.sdk = 33
        failure(BitPerfectFailureCode.ANDROID_VERSION) { p.open() }; assertEquals(0, p.queries)
    }
    @Test fun releaseExceptionStillClearsPreferenceAndAllowsRecovery() {
        val p = Platform(); val first = p.open(); p.track.releaseThrows = true; first.close(); first.close()
        assertEquals(1, p.clears); assertNull(p.retained)
        p.track = Track(); p.open().close(); assertEquals(2, p.clears)
    }
    @Test fun trackConstructionFailureClearsAcceptedPreference() {
        val p = Platform(); p.createFails = true
        failure(BitPerfectFailureCode.PLATFORM_ERROR) { p.open() }; assertEquals(1, p.clears)
    }
    @Test fun actualTrackFormatMustMatchWithoutContainerSubstitution() {
        val p = Platform(); p.track.format = pcm.copy(encoding = 22)
        failure(BitPerfectFailureCode.TRACK_FORMAT) { p.open() }; assertEquals(1, p.track.releases)
    }
    @Test fun preferenceRejectionReleasesTrack() {
        val p = Platform(); p.track.preferred = false
        failure(BitPerfectFailureCode.DEVICE_PREFERENCE) { p.open() }; assertEquals(1, p.track.releases)
    }
    @Test fun noRouteNeverReportsSuccessAndTimesOut() {
        val p = Platform(); val session = p.open(); p.track.playing = true; session.verify()
        assertFalse(p.reports.any { "已确认" in it }); p.time = 2000
        failure(BitPerfectFailureCode.ROUTE_UNCONFIRMED) { session.verify() }
    }
    @Test fun confirmedMeansActualTrackRouteAndRetainedMixerAllMatch() {
        val p = Platform(); val session = p.open(); p.track.playing = true; p.track.routedDeviceId = usb.id
        session.verify(); assertTrue(p.reports.last().contains("Android 已确认")); session.close()
    }
    @Test fun revokedPreferenceFailsClosed() {
        val p = Platform(); val session = p.open(); p.retained = null
        failure(BitPerfectFailureCode.MIXER_REVOKED) { session.verify(true) }; assertEquals(1, p.track.releases)
    }
    @Test fun cleanupDoesNotClearNewerDifferentConfiguration() {
        val p = Platform(); val session = p.open(); p.retained = BitPerfectMixer(1, pcm.copy(rate = 48000), 1)
        session.close(); assertEquals(0, p.clears)
    }
    @Test fun queryFailureIsDifferentFromEmptyCapabilities() {
        val p = Platform(); p.queryFails = true
        failure(BitPerfectFailureCode.MIXER_QUERY) { p.open() }
    }
    @Test fun canonicalStereoMaskRepresentationsMatchButDifferentInterleaveDoesNot() {
        assertTrue(pcm.accepts(pcm.copy(indexMask = 3)))
        assertTrue(pcm.accepts(pcm.copy(positionMask = 0, indexMask = 3)))
        assertFalse(pcm.accepts(pcm.copy(positionMask = 0, indexMask = 5)))
        assertFalse(pcm.copy(channels = 6, positionMask = 252).accepts(pcm.copy(channels = 6, positionMask = 0, indexMask = 63)))
    }
    @Test fun allReturnedMixerFieldsAreLogged() {
        val p = Platform(); p.open().close()
        assertTrue(p.logs.any { "supportedMixer[0]=" in it && "192000" in it && "(21)" in it && "position=0xc" in it && "behavior=1" in it })
    }
}
