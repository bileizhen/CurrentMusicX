package io.github.currencortex.music.feature.library

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.media.PlaybackMode
import io.github.currencortex.music.core.media.PlayerMode
import io.github.currencortex.music.core.network.AppResult
import io.github.currencortex.music.core.network.appResult
import io.github.currencortex.music.data.library.Playlist
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.component.LocalMusicPageActive
import io.github.currencortex.music.ui.component.LocalRoomSongRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal class PlaylistPreviewPlayback(val enabled: Boolean, val play: (Playlist, Boolean) -> Unit)

/** Both playlist entrances use the same session and lifecycle guards while loading tracks. */
@Composable internal fun rememberPlaylistPreviewPlayback(vm: LibraryViewModel,
    play: (List<Song>, Int) -> Unit): PlaylistPreviewPlayback {
    var loading by remember(vm) { mutableStateOf(false) }
    var job by remember(vm) { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val request = LocalRoomSongRequest.current
    val pageActive = LocalMusicPageActive.current
    val modes = remember(vm) { vm.container.playerController.state.map { it.mode }.distinctUntilChanged() }
    // Keep playback disabled until the lifecycle-aware collector supplies the actual mode.
    val mode by modes.collectAsStateWithLifecycle(initialValue = null)
    val allowed by rememberUpdatedState(request == null && mode == PlayerMode.LOCAL && pageActive)
    val currentPlay by rememberUpdatedState(play)
    LaunchedEffect(pageActive) { if (!pageActive) job?.cancel() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { job?.cancel() }
    return PlaylistPreviewPlayback(allowed && !loading) { playlist, shuffle ->
        if (!loading && allowed) {
            val expected = vm.container.musicSession()
            loading = true
            vm.message.value = "正在加载歌单…"
            job = scope.launch {
                try {
                    val result = appResult {
                        if (playlist.source == "netease") vm.container.neteaseLibrary.playlist(playlist.id).songs
                        else vm.container.libraryRepository.playlist(playlist.id).songs
                    }
                    if (expected != vm.container.musicSession() || !allowed ||
                        vm.container.playerController.state.value.mode != PlayerMode.LOCAL) return@launch
                    when (result) {
                        is AppResult.Failure -> vm.container.errorNotices.show(result.message)
                        is AppResult.Success -> {
                            val songs = result.value
                            if (songs.isEmpty()) vm.message.value = "歌单暂无歌曲"
                            else {
                                vm.container.playerController.setMode(if (shuffle) PlaybackMode.SHUFFLE else PlaybackMode.LIST)
                                currentPlay(songs, if (shuffle) songs.indices.random() else 0)
                            }
                        }
                    }
                } finally { loading = false }
            }
        }
    }
}
