package io.github.currencortex.music.feature.library

import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.network.appResult
import io.github.currencortex.music.data.song.NeteaseSongActionsRepository
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
    val nativeLikes by vm.container.neteaseSongActions.likedState.collectAsStateWithLifecycle()
    val likesUseNetease by vm.container.primaryLibrary.likesUseNetease.collectAsStateWithLifecycle()
    val statuses by vm.statuses.collectAsStateWithLifecycle()
    val embyLikes by vm.container.emby.likes.collectAsStateWithLifecycle()
    val navidromeLikes by vm.container.navidrome.likes.collectAsStateWithLifecycle()
    val likeBusy by vm.primaryLikeBusy.collectAsStateWithLifecycle()
    LaunchedEffect(song.id, song.musicSource, likesUseNetease) {
        if (song.musicSource == MusicSource.EMBY) appResult { vm.container.emby.detail(song) }
        else vm.refreshSearchLikes(listOf(song))
    }
    val liked = when (song.musicSource) {
        MusicSource.EMBY -> embyLikes[song.id] == true
        MusicSource.NAVIDROME -> navidromeLikes[song.id] == true
        else -> if (likesUseNetease) NeteaseSongActionsRepository.songId(song) in nativeLikes
            else statuses[song.id]?.liked == true
    }
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
                like?.let { add(it.copy(id = "like", label = if (liked) "已喜欢" else "喜欢",
                    icon = if (liked) Icons.Default.Favorite else MusicIcons.Heart,
                    enabled = it.enabled && !likeBusy, keepOpen = true)) }
                add(GlassMenuItem("queue", "加入队列", MusicIcons.List, onClick = queue))
            }
        }, destinations = buildList {
            if (song.artists.isNotBlank()) add(GlassMenuItem("artist", "查看歌手",
                onClick = { navigate(song.artistIds.singleOrNull()?.let { "lib/artist/$it" } ?: catalogRoute(false, song.artists)) }))
            if (song.album.isNotBlank()) add(GlassMenuItem("album", "查看专辑", onClick = { navigate(catalogRoute(true, song.album)) }))
            if (more.isNotEmpty()) add(GlassMenuItem("more", "更多操作", children = more))
        }, onDismiss = onDismiss)
}
