package io.github.currencortex.music.data.lx

import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class LxAutoQualityTest {
    private val song = Song(123, "Track")
    private fun requested(data: JsonObject) = data.getValue("info").jsonObject.getValue("type").jsonPrimitive.content

    @Test fun addedQualityOptionsOnlyRequestDeclaredLxFormats() {
        val caps = listOf(LxCapability("wy", lxQualities))
        assertEquals("128k", lxTrackRequest(song, AudioQuality.HIGHER, caps).quality)
        assertEquals("flac24bit", lxTrackRequest(song, AudioQuality.HIRES, caps).quality)
        listOf(AudioQuality.VIVID, AudioQuality.DOLBY, AudioQuality.JYEFFECT).forEach {
            assertEquals("flac", lxTrackRequest(song, it, listOf(LxCapability("wy", listOf("flac")))).quality)
        }
    }

    @Test fun autoUsesHighestNeteaseQualityForEveryNonemptyDeclaration() = runBlocking {
        for (mask in 1 until (1 shl lxQualities.size)) {
            val declared = lxQualities.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
            val calls = mutableListOf<String>()
            // Ignore source ordering, duplicate/unknown qualities and higher levels on other platforms.
            val caps = listOf(LxCapability("tx", lxQualities), LxCapability("wy", declared.reversed() + declared + "unknown"))
            val (request, url) = lxResolveTrack(song, AudioQuality.AUTO, caps) {
                calls += requested(it); "https://example.com/audio"
            }
            assertEquals(listOf(declared.last()), calls)
            assertEquals(declared.last(), request.quality)
            assertEquals("https://example.com/audio", url)
        }
    }

    @Test fun trackRejectionFallsBackOnlyThroughDeclaredLevelsAndStopsOnSuccess() = runBlocking {
        val calls = mutableListOf<String>()
        val caps = listOf(LxCapability("wy", listOf("128k", "flac24bit", "320k")))
        val (request, _) = lxResolveTrack(song, AudioQuality.AUTO, caps) {
            val level = requested(it); calls += level
            if (level != "320k") throw LxSourceException("No such quality", qualityUnavailable = true)
            "https://example.com/audio.mp3"
        }
        assertEquals(listOf("flac24bit", "320k"), calls)
        assertEquals("320k", request.quality)
    }

    @Test fun allRejectedQualitiesAreTriedOnceWithoutChangingProvider() = runBlocking {
        val calls = mutableListOf<String>()
        val failure = LxSourceException("Unavailable", qualityUnavailable = true)
        try {
            lxResolveTrack(song, AudioQuality.AUTO, listOf(LxCapability("wy", lxQualities))) {
                calls += requested(it); throw failure
            }
            fail("Expected rejection")
        } catch (error: LxSourceException) { assertSame(failure, error) }
        assertEquals(lxQualities.reversed(), calls)
    }

    @Test fun manualQualityDoesNotAddFallbackRequests() = runBlocking {
        var calls = 0
        try {
            lxResolveTrack(song, AudioQuality.LOSSLESS, listOf(LxCapability("wy", lxQualities))) {
                calls++; assertEquals("flac", requested(it))
                throw LxSourceException("Unavailable", qualityUnavailable = true)
            }
            fail("Expected rejection")
        } catch (_: LxSourceException) { }
        assertEquals(1, calls)
    }

    @Test fun runtimeFailureAndCancellationDoNotTriggerLowerQualityRetries() = runBlocking {
        for (failure in listOf(LxSourceException("Initialization or timeout"), CancellationException("Cancelled"))) {
            var calls = 0
            try {
                lxResolveTrack(song, AudioQuality.AUTO, listOf(LxCapability("wy", lxQualities))) {
                    calls++; throw failure
                }
                fail("Expected failure")
            } catch (error: Exception) { assertSame(failure, error) }
            assertEquals(1, calls)
        }
    }
}
