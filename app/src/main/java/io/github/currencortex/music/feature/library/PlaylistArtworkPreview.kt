package io.github.currencortex.music.feature.library

import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.network.AppResult
import io.github.currencortex.music.core.network.appResult
import androidx.compose.ui.geometry.Rect
import io.github.currencortex.music.data.library.Playlist
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.nativeui.GlassMenuItem
import io.github.currencortex.music.ui.component.nativeui.MusicArtworkPreview

@Composable internal fun PlaylistArtworkPreview(
    vm: LibraryViewModel, playlist: Playlist, anchor: Rect, canPlay: Boolean, onPlay: (shuffle: Boolean) -> Unit,
    onOpen: () -> Unit, onDismiss: () -> Unit,
) {
    val binding by vm.container.bindingRepository.state.collectAsStateWithLifecycle()
    val session by vm.container.neteaseSessions.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var info by remember(playlist.id, playlist.source, session.revision) { mutableStateOf<Playlist?>(null) }
    val native = playlist.source == "netease"
    LaunchedEffect(playlist.id, playlist.source, session.revision) {
        if (native) when (val result = appResult { vm.container.neteaseLibrary.playlistInfo(playlist.id) }) {
            is AppResult.Success -> info = result.value
            is AppResult.Failure -> vm.container.errorNotices.show(result.message)
        }
    }
    val target = info
    val canSubscribe = native && !playlist.nativeOwned && !playlist.nativeLiked &&
        target?.nativeOwned != true && target?.nativeLiked != true
    val subscribed = target?.nativeSubscribed ?: playlist.nativeSubscribed
    MusicArtworkPreview(
        cover = playlist.cover.ifBlank { playlist.songs.firstOrNull()?.cover.orEmpty() },
        title = playlist.name, anchor = anchor,
        actions = buildList {
            add(GlassMenuItem("play", "播放全部", MusicIcons.Play, enabled = canPlay, onClick = { onPlay(false) }))
            if (canSubscribe) add(GlassMenuItem("subscribe", if (subscribed) "已收藏" else "收藏",
                if (subscribed) Icons.Default.Star else MusicIcons.Star,
                enabled = target != null && !busy && binding?.bound == true && binding?.stale == false,
                keepOpen = true, onClick = {
                    vm.action(if (subscribed) "已取消收藏歌单" else "已收藏歌单") {
                        if (subscribed) vm.container.neteaseLibrary.unsubscribe(playlist.id)
                        else vm.container.neteaseLibrary.subscribe(playlist.id)
                        info = target?.copy(nativeSubscribed = !subscribed)
                    }
                }))
            add(GlassMenuItem("shuffle", "随机播放", MusicIcons.Shuffle, enabled = canPlay, onClick = { onPlay(true) }))
        },
        destinations = listOf(GlassMenuItem("open_playlist", "打开歌单", onClick = onOpen)),
        onDismiss = onDismiss, previewKind = "歌单",
    )
}
