package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.feature.lyrics.ui.lyricGlyphLift
import io.github.currencortex.music.feature.lyrics.ui.lyricGlowStrength
import io.github.currencortex.music.feature.lyrics.ui.lyricGlowWeight
import io.github.currencortex.music.feature.lyrics.ui.lyricGlowReveal
import io.github.currencortex.music.feature.lyrics.model.LyricWord
import org.junit.Assert.*
import org.junit.Test

class LyricsEffectsTest {
    @Test fun mediumHeldNotesGlowMoreOftenWithoutLightingOrdinaryPhrases() {
        assertTrue(lyricGlowWeight(LyricWord("光", 0, 750, 0, 1), emptyList()) > .25f)
        assertTrue(lyricGlowWeight(LyricWord("光", 0, 1100, 0, 1), emptyList()) > .7f)
        assertEquals(0f, lyricGlowWeight(LyricWord("光", 0, 500, 0, 1), emptyList()), 0f)
        assertEquals(0f, lyricGlowWeight(LyricWord("我们沿着星光走", 0, 3000, 0, 7), emptyList()), 0f)
    }
    @Test fun glyphBloomFadesInContinuouslyRatherThanCuttingTheHaloAtTheSweepEdge() {
        assertEquals(0f, lyricGlowReveal(0f), 0f)
        assertEquals(1f, lyricGlowReveal(1f), 0f)
        val samples = (-10..110).map { lyricGlowReveal(it / 100f) }
        assertTrue(samples.zipWithNext().all { (a, b) -> b >= a && b - a < .025f })
        assertTrue(lyricGlowReveal(.3f) in .1f.. .7f)
    }
    @Test fun glyphsRiseContinuouslyAndStayRaisedUntilSeekingBeforeTheirStart() {
        val samples = (950..1600 step 10).map { lyricGlyphLift(it.toLong(), 1000) }
        assertEquals(0f, samples.first(), 0f)
        assertEquals(1f, samples.last(), 0f)
        assertTrue(samples.max() > .95f)
        assertTrue(samples.all { it in 0f..1f })
        assertTrue(samples.zipWithNext().all { (a, b) -> b >= a && b - a < .12f })
        assertEquals(0f, lyricGlyphLift(900, 1000), 0f)
    }
    @Test fun earlierGlyphStaysRaisedAsTheNextGlyphRises() {
        assertEquals(1f, lyricGlyphLift(2100, 1000), 0f)
        assertTrue(lyricGlyphLift(2100, 2000) in .1f.. .9f)
        assertEquals(1f, lyricGlyphLift(100000, 1000), 0f)
    }
    @Test fun normalPhrasesDoNotGlowWhileSustainedNotesAndMarkedEmphasisCan() {
        assertEquals(0f, lyricGlowWeight(LyricWord("光", 0, 300, 0, 1), emptyList()), 0f)
        assertEquals(0f, lyricGlowWeight(LyricWord("我们沿着星光走", 0, 3000, 0, 7), emptyList()), 0f)
        assertTrue(lyricGlowWeight(LyricWord("光", 0, 2000, 0, 1), emptyList()) > .9f)
        assertEquals(1f, lyricGlowWeight(LyricWord("光", 0, 500, 0, 1), listOf("x-emphasis")), 0f)
    }
    @Test fun glowBuildsToTheVocalPeakAndIsGoneOutsideThatWord() {
        assertEquals(0f, lyricGlowStrength(900, 1000, 3000), 0f)
        assertEquals(0f, lyricGlowStrength(3200, 1000, 3000), 0f)
        assertTrue(lyricGlowStrength(2200, 1000, 3000) > .9f)
        assertTrue(lyricGlowStrength(1100, 1000, 3000) < .1f)
        val samples = (900..3100 step 16).map { lyricGlowStrength(it.toLong(), 1000, 3000) }
        assertTrue(samples.zipWithNext().all { (a, b) -> kotlin.math.abs(a - b) < .08f })
    }
}
