package io.github.currencortex.music.data.lx

import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.data.song.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class LxSourceProtocolTest {
    @Test fun scriptMetadataAndIdentityPreserveExactScript() {
        val script = "/**\n * @name My source\n * @author Tester\n * @version 1.0\n */\nlet x = 1;"
        val source = lxScriptInfo(script)
        assertEquals("My source", source.name); assertEquals("Tester", source.author)
        assertEquals(64, source.id.length); assertEquals(source.id, lxScriptInfo(script).id)
        assertNotEquals(source.id, lxScriptInfo("\uFEFF$script").id)
        assertNotEquals(AudioSourceAccess(AudioProvider.LX, lxSourceId = source.id).identity,
            AudioSourceAccess(AudioProvider.LX, lxSourceId = lxScriptInfo("$script\n").id).identity)
        assertThrows(IllegalArgumentException::class.java) { lxScriptInfo("lx.send('inited', {});") }
    }
    @Test fun initializationOnlyAcceptsMusicUrlAndKnownQualities() {
        val value = Json.parseToJsonElement("""{"sources":{
            "wy":{"type":"music","actions":["musicUrl"],"qualitys":["320k","128k","unknown"]},
            "tx":{"type":"music","actions":["musicUrl"],"qualitys":["flac"]},
            "alien":{"type":"music","actions":["musicUrl"],"qualitys":["128k"]}}}""").jsonObject
        assertEquals(listOf(LxCapability("wy", listOf("128k", "320k"))), lxCapabilities(value))
        assertThrows(LxSourceException::class.java) { lxCapabilities(buildJsonObject {}) }
    }
    @Test fun correctPlatformIdsAndQualityFallbackWithoutInventingIds() {
        val caps = listOf(LxCapability("wy", listOf("128k", "320k", "flac")), LxCapability("tx", listOf("320k")))
        val song = Song(123, "Track", "Artist", durationMs = 205000)
        val request = lxTrackRequest(song, AudioQuality.AUTO, caps)
        assertEquals("wy", request.platform); assertEquals("flac", request.quality)
        assertEquals("123", request.data["info"]!!.jsonObject["musicInfo"]!!.jsonObject["songmid"]!!.jsonPrimitive.content)
        assertEquals("128k", lxTrackRequest(song, AudioQuality.STANDARD, caps).quality)
        val qq = song.copy(musicSource = MusicSource.QQ_MUSIC, externalIds = SongExternalIds(qqMusicId = "QQ-mid"))
        assertThrows(LxSourceException::class.java) { lxTrackRequest(qq, AudioQuality.STANDARD, caps) }
        val explicitNeteaseId = lxTrackRequest(qq.copy(externalIds = SongExternalIds(qqMusicId = "QQ-mid", neteaseId = "456")), AudioQuality.STANDARD, caps)
        assertEquals("wy", explicitNeteaseId.platform)
        assertEquals("456", explicitNeteaseId.data["info"]!!.jsonObject["musicInfo"]!!.jsonObject["songmid"]!!.jsonPrimitive.content)
        assertThrows(LxSourceException::class.java) { lxTrackRequest(qq.copy(externalIds = SongExternalIds()), AudioQuality.AUTO, caps) }
        assertThrows(LxSourceException::class.java) { lxTrackRequest(song, AudioQuality.AUTO, listOf(caps[1])) }
    }

    @Test fun oldDeclarationsAreRestrictedWithoutInventingQualitiesOrNeteaseIds() {
        val mixed = listOf(LxCapability("tx", listOf("flac24bit")), LxCapability("wy", listOf("invalid", "320k", "128k")))
        assertEquals(listOf(LxCapability("wy", listOf("128k", "320k"))), lxNeteaseCapabilities(mixed))
        assertTrue(lxNeteaseCapabilities(listOf(mixed.first())).isEmpty())
        val other = Song(123, "Other", musicSource = MusicSource.SPOTIFY, externalIds = SongExternalIds(neteaseId = "not-an-id"))
        assertThrows(LxSourceException::class.java) { lxTrackRequest(other, AudioQuality.AUTO, mixed) }
    }
}
