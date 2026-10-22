package io.github.currencortex.music.feature.lyrics.desktop

import io.github.currencortex.music.core.media.IslandNoticeKind
import io.github.currencortex.music.core.media.SoundEffectNotice
import io.github.currencortex.music.core.media.SoundEffectNoticePhase
import io.github.currencortex.music.core.media.SoundEffectNoticeTier
import org.junit.Assert.*
import org.junit.Test

class DesktopIslandVisibilityPolicyTest {
    private fun notice(kind: IslandNoticeKind, phase: SoundEffectNoticePhase = SoundEffectNoticePhase.READY) =
        SoundEffectNotice(1, "提示", SoundEffectNoticeTier.STANDARD, phase = phase, kind = kind)

    @Test fun foregroundHasNoPermanentLyricsOrFpsButBackgroundKeepsDesktopBehavior() {
        assertFalse(desktopIslandContentAllowed(appForeground = true, notice = null))
        assertTrue(desktopIslandContentAllowed(appForeground = false, notice = null))
        assertFalse(fallbackIslandNoticeVisible(notice = null, desktopAttached = false))
    }

    @Test fun everyTransientNoticeAndDownloadPhaseIsAllowedInForeground() {
        for (kind in IslandNoticeKind.entries) {
            for (phase in SoundEffectNoticePhase.entries) {
                assertTrue("$kind/$phase must appear in-app", desktopIslandContentAllowed(true, notice(kind, phase)))
                assertTrue("$kind/$phase needs fallback without an overlay", fallbackIslandNoticeVisible(notice(kind, phase), false))
            }
        }
    }

    @Test fun attachedOverlayOwnsNoticesThroughoutVisibilityTransitions() {
        val downloadReady = notice(IslandNoticeKind.UPDATE_DOWNLOAD)
        assertFalse(fallbackIslandNoticeVisible(downloadReady, desktopAttached = true))
        assertTrue(fallbackIslandNoticeVisible(downloadReady, desktopAttached = false))
        assertTrue(desktopIslandContentAllowed(appForeground = false, notice = downloadReady))
        assertFalse(desktopIslandContentAllowed(appForeground = true, notice = null))
    }
}
