package io.github.currencortex.music.feature.player

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.currencortex.music.core.network.AppResult
import io.github.currencortex.music.core.network.appResult
import io.github.currencortex.music.data.song.ChorusSegment
import io.github.currencortex.music.data.song.NeteaseSongActionsRepository
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.data.song.SongInsightsRepository

/** Supplemental metadata must never block playback or survive a change of song/server. */
@Composable internal fun rememberPlayerChorus(song: Song?, repository: SongInsightsRepository): List<ChorusSegment> {
    val owner = song to repository.session()
    var segments by remember(owner) { mutableStateOf(emptyList<ChorusSegment>()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(owner, lifecycle) {
        if (song == null || NeteaseSongActionsRepository.songId(song) == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            segments = emptyList()
            val result = appResult { repository.chorus(song) }
            if (repository.session() == owner.second && result is AppResult.Success) segments = result.value
        }
    }
    return segments
}

/** Use the actual player duration; invalid/unknown intervals must not invent a chorus. */
internal fun playerChorusSegments(segments: List<ChorusSegment>, durationMs: Long): List<ChorusSegment> {
    if (durationMs <= 0) return emptyList()
    return segments.asSequence()
        .filter { it.startMs >= 0 && it.endMs > it.startMs && it.endMs <= 9_007_199_254_740_991L && it.startMs < durationMs }
        .map { it.copy(endMs = minOf(it.endMs, durationMs)) }
        .distinct().sortedBy { it.startMs }.toList()
}
