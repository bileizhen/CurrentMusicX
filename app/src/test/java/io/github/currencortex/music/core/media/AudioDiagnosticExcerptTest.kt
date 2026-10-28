package io.github.currencortex.music.core.media

import org.junit.Assert.*
import org.junit.Test

class AudioDiagnosticExcerptTest {
    @Test fun latestCopyExcludesOldAttemptsAndRetainsAllTwelveFormatsAndFailure() {
        val old = (1..5).flatMap { listOf("source.declared old-$it", "decodedPcm.sink old-$it", "finalState=FAILED old-$it") }
        val current = listOf("source.declared latest", "decoder.requestedPcmEncoding=2", "source.declared latest",
            "decoder.output rate=48000", "decodedPcm.sink rate=48000", "androidSdk=37", "device id=1702",
            "supportedMixerAttributes.count=12") + (0..11).map { "supportedMixer[$it]=48000 Hz / PCM 16-bit behavior=${if (it == 11) 1 else 0}" } +
            listOf("finalState=FAILED failure=[REQUEST_REJECTED]", "player.finalState=ERROR errorCode=5001")
        val settingsScan = listOf("device settings snapshot", "supportedMixer[0]=settings snapshot")
        val result = AudioDiagnosticExcerpt.latest("CurrentMusicAudio", old + current + settingsScan)
        assertFalse(result.contains("old-5")); assertFalse(result.contains("settings snapshot"))
        assertTrue(result.contains("decoder.requestedPcmEncoding=2"))
        for (i in 0..11) assertTrue("missing $i", result.contains("supportedMixer[$i]="))
        assertTrue(result.contains("player.finalState=ERROR")); assertTrue(result.toByteArray(Charsets.UTF_8).size <= 3000)
    }

    @Test fun oversizedAttemptPreservesWholeLinesAndFinalResultWithExplicitNotice() {
        val lines = listOf("source.declared latest", "decodedPcm.sink rate=48000") +
            (0..99).map { "supportedMixer[$it]=" + "x".repeat(90) } + listOf("finalState=FAILED end")
        val result = AudioDiagnosticExcerpt.latest("header", lines)
        assertTrue(result.toByteArray(Charsets.UTF_8).size <= 3000)
        assertTrue(result.contains("source.declared latest")); assertTrue(result.contains("finalState=FAILED end"))
        assertTrue(result.contains("已省略部分中间行"))
        result.lineSequence().filter { it.startsWith("supportedMixer[") }.forEach { assertTrue(it.endsWith("x".repeat(90))) }
    }

    @Test fun copiesRecentCapabilitySnapshotWithoutPlayback() {
        val report = AudioDiagnosticExcerpt.latest("header", listOf("device connected", "supportedMixerAttributes.count=0"))
        assertTrue(report.contains("device connected")); assertTrue(report.contains("supportedMixerAttributes.count=0"))
    }
}
