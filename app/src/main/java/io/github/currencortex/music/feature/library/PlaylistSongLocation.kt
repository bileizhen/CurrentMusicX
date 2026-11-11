package io.github.currencortex.music.feature.library

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyLayoutScrollScope
import androidx.compose.foundation.lazy.LazyListState
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.SongLyricsStore
import kotlinx.coroutines.flow.SharedFlow
import kotlin.math.abs
import kotlin.math.sign

/** Only the active playlist observes requests from the mini player. */
internal val LocalCurrentSongLocateRequests = staticCompositionLocalOf<SharedFlow<Unit>?> { null }

internal val PlaylistLocateAnimation = tween<Float>(durationMillis = 450, easing = EaseOutCubic)

internal suspend fun LazyListState.locatePlaylistSong(index: Int) {
    scroll {
        val lazyScroll = LazyLayoutScrollScope(this@locatePlaylistSong, this)
        var distance = lazyScroll.calculateDistanceTo(index).toFloat()
        val maxTravel = layoutInfo.viewportSize.height * .9f
        if (maxTravel > 0f && abs(distance) > maxTravel) {
            // Start close to distant targets so a large playlist needs only one short animation.
            lazyScroll.snapToItem(index)
            distance = -scrollBy(-sign(distance) * maxTravel)
        }
        var consumed = 0f
        animate(0f, distance, animationSpec = PlaylistLocateAnimation) { value, _ ->
            consumed += scrollBy(value - consumed)
        }
        lazyScroll.snapToItem(index)
    }
}

internal fun playlistCurrentSongIndex(songs: List<Song>, current: Song?): Int {
    if (current == null) return -1
    val identity = SongLyricsStore.identity(current)
    return songs.indexOfFirst { it.video == current.video && SongLyricsStore.identity(it) == identity }
}
