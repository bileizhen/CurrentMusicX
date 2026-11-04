package io.github.currencortex.music.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.*

/** Shared by artwork cards and long-pressed search results. */
@Composable internal fun SongArtworkPreview(vm: LibraryViewModel, song: Song, anchor: Rect,
    play: () -> Unit, queue: () -> Unit, roomRequest: Boolean, playEnabled: Boolean = true,
    playVersion: (Song) -> Unit, navigate: (String) -> Unit, share: (Song) -> Unit,
    download: ((Song) -> Unit)?, onDismiss: () -> Unit) {
    val libraryActions = librarySongMenuItems(vm, song, navigate)
    val like = libraryActions.firstOrNull { it.id == "song_like_${song.id}" }
    val insights = songInsightsMenuItems(vm.container.musicRepository.insights, song, playVersion)
    val more = buildList {
        if (!song.video && song.musicSource != MusicSource.LOCAL) {
            if (download != null) add(GlassMenuItem("song_download", "下载歌曲", MusicIcons.Download, onClick = { download(song) }))
            add(GlassMenuItem("song_share", "分享歌曲", onClick = { share(song) }))
        }
        addAll(libraryActions.filterNot { !roomRequest && it.id == like?.id })
        addAll(insights)
    }
    MusicArtworkPreview(song.cover, song.name, anchor,
        actions = buildList {
            add(GlassMenuItem("play", if (roomRequest) "点歌" else "播放", MusicIcons.Play, enabled = playEnabled, onClick = play))
            if (!roomRequest) {
                like?.let { add(it.copy(id = "like", label = "喜欢", icon = MusicIcons.Heart)) }
                add(GlassMenuItem("queue", "加入队列", MusicIcons.List, onClick = queue))
            }
        }, destinations = buildList {
            if (song.artists.isNotBlank()) add(GlassMenuItem("artist", "查看歌手",
                onClick = { navigate(song.artistIds.singleOrNull()?.let { "lib/artist/$it" } ?: catalogRoute(false, song.artists)) }))
            if (song.album.isNotBlank()) add(GlassMenuItem("album", "查看专辑", onClick = { navigate(catalogRoute(true, song.album)) }))
            if (more.isNotEmpty()) add(GlassMenuItem("more", "更多操作", children = more))
        }, onDismiss = onDismiss)
}
