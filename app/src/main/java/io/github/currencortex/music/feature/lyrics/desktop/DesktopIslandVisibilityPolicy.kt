package io.github.currencortex.music.feature.lyrics.desktop

import io.github.currencortex.music.core.media.IslandNoticeKind
import io.github.currencortex.music.core.media.SoundEffectNotice

/** Foreground pages reserve the island for transient feedback; saved desktop settings stay untouched. */
internal fun desktopIslandContentAllowed(appForeground: Boolean, notice: SoundEffectNotice?): Boolean =
    !appForeground || notice.isTemporaryIslandNotice()

/** An attached overlay owns the transition, even while its visible flag changes during an exit. */
internal fun fallbackIslandNoticeVisible(notice: SoundEffectNotice?, desktopAttached: Boolean): Boolean =
    notice.isTemporaryIslandNotice() && !desktopAttached

private fun SoundEffectNotice?.isTemporaryIslandNotice(): Boolean = when (this?.kind) {
    IslandNoticeKind.SOUND_EFFECT, IslandNoticeKind.UPDATE_DOWNLOAD, IslandNoticeKind.ERROR -> true
    null -> false
}
