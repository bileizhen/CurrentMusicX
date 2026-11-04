package io.github.currencortex.music.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import io.github.currencortex.music.data.library.Playlist
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.nativeui.GlassMenuItem
import io.github.currencortex.music.ui.component.nativeui.MusicArtworkPreview

@Composable internal fun PlaylistArtworkPreview(
    playlist: Playlist, anchor: Rect, canPlay: Boolean, onPlay: (shuffle: Boolean) -> Unit,
    onOpen: () -> Unit, onDismiss: () -> Unit,
) {
    MusicArtworkPreview(
        cover = playlist.cover.ifBlank { playlist.songs.firstOrNull()?.cover.orEmpty() },
        title = playlist.name, anchor = anchor,
        actions = listOf(
            GlassMenuItem("play", "播放全部", MusicIcons.Play, enabled = canPlay, onClick = { onPlay(false) }),
            GlassMenuItem("shuffle", "随机播放", MusicIcons.Shuffle, enabled = canPlay, onClick = { onPlay(true) }),
        ),
        destinations = listOf(GlassMenuItem("open_playlist", "打开歌单", onClick = onOpen)),
        onDismiss = onDismiss, previewKind = "歌单",
    )
}
