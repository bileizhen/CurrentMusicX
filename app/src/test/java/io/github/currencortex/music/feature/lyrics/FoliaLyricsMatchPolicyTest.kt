package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.*
import io.github.currencortex.music.feature.lyrics.domain.FoliaLyricsMatchPolicy
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class FoliaLyricsMatchPolicyTest {
    /** Expected values were executed from Folia f96c65b's actual matchScore.ts with its dependencies. */
    @Test fun metadataScoresAndEligibilityMatchUpstreamAcrossTwentySevenCases() {
        val fixture = Json.parseToJsonElement(checkNotNull(javaClass.getResourceAsStream("/lyrics-match/folia-score-parity.json"))
            .bufferedReader().use { it.readText() }).jsonObject
        fun JsonObject.text(key: String) = get(key)?.jsonPrimitive?.contentOrNull.orEmpty()
        for (row in fixture["rows"]!!.jsonArray.map { it.jsonObject }) {
            val target = row["target"]!!.jsonObject
            val source = row["candidate"]!!.jsonObject
            val song = Song(1, target.text("title"), target.text("artist"), target.text("album"), durationMs = target["durationMs"]!!.jsonPrimitive.long)
            val candidate = LyricsCandidate(LyricsMatchSource.QQ_MUSIC, "1", source.text("name"),
                source["artists"]!!.jsonArray.joinToString(", ") { it.jsonObject.text("name") }, source["album"]!!.jsonObject.text("name"),
                source["durationMs"]!!.jsonPrimitive.long)
            val actual = FoliaLyricsMatchPolicy.details(song, candidate)
            val expected = row["expected"]!!.jsonObject
            val label = row.text("label")
            assertEquals(label, expected["score"]!!.jsonPrimitive.int, actual.score)
            assertEquals(label, expected["titleMatched"]!!.jsonPrimitive.boolean, actual.titleMatched)
            assertEquals(label, expected["artistMatched"]!!.jsonPrimitive.boolean, actual.artistMatched)
            assertEquals(label, expected["albumMatched"]!!.jsonPrimitive.booleanOrNull, actual.albumMatched)
            assertEquals(label, expected["durationMatched"]!!.jsonPrimitive.booleanOrNull, actual.durationMatched)
        }
    }

    @Test fun searchQueriesRemoveDuplicatedArtistPrefixesAndBoundLongAlbums() {
        val query = FoliaLyricsMatchPolicy.query(Song(1, "Singer - Fixture", "Singer", "A".repeat(100)))
        assertEquals("Fixture - Singer - " + "A".repeat(48), query)
    }

    @Test fun normalizationPreservesInternationalLettersAndMatchesKanaAndTraditionalChinese() {
        assertEquals("sayonarano夏 café", FoliaLyricsMatchPolicy.normalize("さよならの夏 - Café!"))
        assertEquals("안녕 мир", FoliaLyricsMatchPolicy.normalize("안녕, мир?"))
        assertEquals("无尽的爱", FoliaLyricsMatchPolicy.normalize("無盡的愛"))
    }
}
