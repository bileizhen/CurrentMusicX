package io.github.currencortex.music.feature.player

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.currencortex.music.core.media.PlayerMode
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.feature.lyrics.domain.LyricsSynchronizer
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import io.github.currencortex.music.feature.lyrics.timeline.LyricCaptionTimeline
import io.github.currencortex.music.feature.lyrics.timeline.LyricsPlaybackClock
import io.github.currencortex.music.feature.lyrics.timeline.PlaybackAnchor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private fun PlayerState.miniLyricAnchor(active: Boolean) =
    PlaybackAnchor(positionMs, anchorRealtimeMs, playing && !loading && active, durationMs, playbackSpeed, timelineRevision)

@Composable internal fun miniPlayerLyric(
    vm: PlayerViewModel, songKey: String?, songId: Long?, mode: PlayerMode, active: Boolean,
): String {
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    val matches = if (lyrics.songKey != null) lyrics.songKey == songKey else lyrics.songId == songId
    if (!matches) return ""
    var lastDocument by remember(vm, songKey) { mutableStateOf<LyricsDocument?>(null) }
    val document = if (lyrics.loading) lastDocument else lyrics.document
    SideEffect { if (!lyrics.loading) lastDocument = lyrics.document }
    if (document == null || document.lines.isEmpty()) return ""
    val timeline = remember(document) { LyricCaptionTimeline(document) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val offset = settings.lyricsOffsetMs - lyrics.songOffsetMs
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return key(timeline, songKey, mode) {
        val clock = remember(vm) { LyricsPlaybackClock() }
        val initialText = remember(clock) {
            val now = SystemClock.elapsedRealtime()
            clock.update(songKey, vm.state.value.miniLyricAnchor(active), now)
            timeline.textAt(LyricsSynchronizer.lyricPosition(clock.positionAt(now), offset))
        }
        val text by produceState(initialText, vm, offset, active, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.map { it.miniLyricAnchor(active) }.distinctUntilChanged().collectLatest { sample ->
                    clock.update(songKey, sample, SystemClock.elapsedRealtime())
                    while (true) {
                        val now = SystemClock.elapsedRealtime()
                        val position = LyricsSynchronizer.lyricPosition(clock.positionAt(now), offset)
                        value = timeline.textAt(position)
                        val wait = timeline.nextCheckMs(clock, now, offset) ?: break
                        delay((now + wait - SystemClock.elapsedRealtime()).coerceAtLeast(1L))
                    }
                }
            }
        }
        text
    }
}
