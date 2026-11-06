package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.data.song.*
import io.github.currencortex.music.feature.lyrics.data.*
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.feature.lyrics.model.LyricLine
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalLyricsTest {
    @get:Rule val temporary = TemporaryFolder()
    private val song = Song(-1, "Deserve", musicSource = MusicSource.LOCAL, localUri = "content://media/external/audio/media/7")
    private val origin = LyricsOrigin("本地内嵌歌词", "local")
    private val local = SavedLyrics(requireNotNull(decodeEmbeddedLyrics("[00:01]local lyrics", 180000)), origin)
    private val forbidden = LyricsProvider { error("Local lyrics must work offline") }

    @Test fun embeddedBilingualLrcKeepsTranslationOutOfPrimaryTimeline() {
        val document = requireNotNull(decodeEmbeddedLyrics(
            "[00:01.000]こんにちは\n[00:01.000]你好\n[00:03.000]さようなら\n[00:03.000]再见", 10000))
        assertEquals(listOf("こんにちは", "さようなら"), document.lines.map { it.text })
        assertEquals(listOf("你好", "再见"), document.lines.map { it.translation })
        assertEquals(3000L, document.lines.first().endTimeMs)
        assertFalse(document.hasWordTiming)
    }

    @Test fun downloadedEnhancedLrcRoundTripsOriginalWordsAndTranslation() {
        val original = LyricLine(1000, 3000, "Hello world",
            listOf(LyricWord("Hello", 1000, 1500, 0, 5), LyricWord("world", 2000, 3000, 6, 11)),
            translation = "你好，世界")
        val exported = io.github.currencortex.music.core.download.DownloadLyrics.lrc(song, LyricsDocument(listOf(original)))
        val decoded = requireNotNull(decodeEmbeddedLyrics(exported, 10000)).lines.single()
        assertEquals(original, decoded)
        assertEquals(decoded, LyricsFileCodec.decode(exported.toByteArray(), "song.lrc").document.lines.single())
    }

    @Test fun ambiguousSimultaneousLyricsAndDifferentTimestampsStaySeparate() {
        for (raw in listOf(
            "[00:01]第一声部\n[00:01]第二声部",
            "[00:01]First voice\n[00:01]Second voice",
            "[00:01]Hello\n[00:01.001]你好",
            "[00:01]你好\n[00:01]Hello",
            "[00:01]Hello\n[00:01]你好\n[00:01]ni hao",
            "[00:01]Hello\n[00:01]<00:01>你<00:02>好<00:03>",
            "[00:01]Hello[tr]已有翻译[/tr]\n[00:01]另一声部")) {
            val expected = io.github.currencortex.music.feature.lyrics.parser.LyricsParser.lrc(raw)
            assertEquals(raw, expected, requireNotNull(decodeEmbeddedLyrics(raw, 10000)))
        }
    }

    @Test fun timedAndUntimedTagsKeepTheirOriginalTiming() {
        assertEquals(1000L, local.document.lines.single().startTimeMs)
        val plain = requireNotNull(decodeEmbeddedLyrics("first line\nsecond line", 180000))
        assertEquals("first line\nsecond line", plain.lines.single().text)
        assertFalse(plain.hasWordTiming)
        assertEquals(180000L, plain.lines.single().endTimeMs)
        assertNull(decodeEmbeddedLyrics(" ", 0))
        assertNull(decodeEmbeddedLyrics("[999999999999:00]broken", 0))
        assertNull(decodeEmbeddedLyrics("a".repeat(LyricsFileCodec.MAX_BYTES + 1), 0))
    }

    @Test fun localTagsWinBeforeAutomaticOnlineReferenceAndAreReadAgain() = runBlocking {
        var reads = 0
        val repo = LyricsRepository(forbidden, forbidden, LyricsCache(temporary.newFolder()),
            selection = { LyricsSelection(listOf("auto"), forbidden, automatic = true) },
            autoMatch = { error("Must not require online reference") }, localLyrics = { reads++; local })
        repeat(2) { assertEquals(origin, repo.load(song).origin) }
        assertEquals(2, reads)
    }

    @Test fun unreadableTagsFallBackAndCancellationPropagates() = runBlocking {
        val repo = LyricsRepository(forbidden, forbidden, LyricsCache(temporary.newFolder()),
            selection = { LyricsSelection(listOf("auto"), forbidden, automatic = true) },
            autoMatch = { local.copy(origin = LyricsOrigin("online")) }, localLyrics = { throw java.io.IOException() })
        assertEquals("online", repo.load(song).origin?.label)
        val cancelled = LyricsRepository(forbidden, forbidden, LyricsCache(temporary.newFolder()),
            localLyrics = { throw CancellationException("cancelled") })
        try { cancelled.load(song); fail("Cancellation swallowed") } catch (_: CancellationException) { }
    }

    @Test fun cloudSongsNeverReadLocalFiles() = runBlocking {
        val provider = LyricsProvider { LyricsProviderResult.Parsed(local.document) }
        val repo = LyricsRepository(provider, provider, LyricsCache(temporary.newFolder()),
            localLyrics = { error("Not a local song") })
        assertEquals(local.document, repo.load(Song(7, "cloud")).document)
    }

    @Test fun manualImportStillWinsOverEmbeddedTags() = runBlocking {
        val store = SongLyricsStore(temporary.newFolder())
        store.saveImported(song, local.copy(origin = LyricsOrigin("imported file", "imported")))
        val repo = LyricsRepository(forbidden, forbidden, LyricsCache(temporary.newFolder()), overrides = store,
            localLyrics = { error("Manual choice must win") })
        assertEquals("imported", repo.load(song).origin?.provider)
    }
}
