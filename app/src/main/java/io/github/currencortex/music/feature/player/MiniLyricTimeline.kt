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
import io.github.currencortex.music.feature.lyrics.timeline.LyricsPlaybackClock
import io.github.currencortex.music.feature.lyrics.timeline.PlaybackAnchor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** A mini caption holds through silence until the next visible line, including after a seek. */
internal class MiniLyricTimeline(document: LyricsDocument) {
    private data class Cue(val startMs: Long, val text: String)

    private val cues = buildList<Cue> {
        val synchronizer = LyricsSynchronizer(document)
        val boundaries = sortedSetOf<Long>()
        for (line in document.lines) {
            boundaries.add(line.startTimeMs)
            boundaries.add(line.endTimeMs)
            for (vocal in line.backgroundVocals) {
                boundaries.add(vocal.startTimeMs)
                boundaries.add(vocal.endTimeMs)
            }
        }
        for (time in boundaries) {
            val text = synchronizer.activeLines(time).asReversed().firstNotNullOfOrNull { index ->
                val line = document.lines[index]
                line.text.takeIf { it.isNotBlank() } ?: line.backgroundVocals.firstOrNull {
                    time >= it.startTimeMs && time < it.endTimeMs && it.text.isNotBlank()
                }?.text
            }
            if (text != null && text != lastOrNull()?.text) add(Cue(time, text))
        }
    }

    private fun cueIndex(position: Long): Int {
        val index = cues.binarySearchBy(position) { it.startMs }
        return if (index >= 0) index else -index - 2
    }

    fun textAt(position: Long): String = cues.getOrNull(cueIndex(position))?.text.orEmpty()

    fun nextCheckMs(clock: LyricsPlaybackClock, now: Long, offset: Long): Long? {
        val index = cueIndex(LyricsSynchronizer.lyricPosition(clock.positionAt(now), offset))
        val start = cues.getOrNull(index)?.startMs ?: Long.MIN_VALUE
        val end = cues.getOrNull(index + 1)?.startMs
        return clock.nextIntervalCheckMs(now) { playbackPosition ->
            val position = LyricsSynchronizer.lyricPosition(playbackPosition, offset)
            position >= start && (end == null || position < end)
        }
    }
}

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
    val timeline = remember(document) { MiniLyricTimeline(document) }
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
