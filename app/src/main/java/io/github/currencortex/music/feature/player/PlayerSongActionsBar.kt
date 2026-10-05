package io.github.currencortex.music.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.media.PlaybackMode
import io.github.currencortex.music.core.media.PlayerMode
import io.github.currencortex.music.data.song.NeteaseSongActionsRepository

@Composable internal fun PlayerSongActionsBar(vm: PlayerViewModel, onComments: () -> Unit, onQueue: () -> Unit) {
    val queue by vm.queue.collectAsStateWithLifecycle()
    val player by vm.state.collectAsStateWithLifecycle()
    val actions by vm.actions.collectAsStateWithLifecycle()
    val heartLoading by vm.heartLoading.collectAsStateWithLifecycle()
    val id = NeteaseSongActionsRepository.songId(queue.current)
    val current = actions.takeIf { it.songId == id }
    PlayerFunctionBar(actionCount = 4) {
        PlayerIconButton(PlayerIcon.LIKE, if (current?.liked == true) "取消喜欢" else "喜欢", vm::toggleNeteaseLike,
            Modifier.testTag("player_like"), selected = current?.liked == true, count = current?.likeCount,
            showCount = true, enabled = id != null && current?.liking != true, loading = current?.liking == true)
        PlayerIconButton(PlayerIcon.COMMENT, "评论区", onComments, Modifier.testTag("player_comments"),
            count = current?.commentCount, showCount = true, enabled = id != null)
        PlayerIconButton(when (queue.mode) {
            PlaybackMode.ONE -> PlayerIcon.REPEAT_ONE
            PlaybackMode.SHUFFLE -> PlayerIcon.SHUFFLE
            PlaybackMode.HEART -> PlayerIcon.HEART_MODE
            PlaybackMode.LIST -> PlayerIcon.REPEAT
        }, queue.mode.label, vm::cyclePlaybackMode, Modifier.testTag("player_cycle_mode"),
            selected = queue.mode == PlaybackMode.HEART, enabled = player.mode == PlayerMode.LOCAL && !heartLoading, loading = heartLoading)
        PlayerIconButton(PlayerIcon.QUEUE, "播放列表，${queue.songs.size} 首", onQueue, Modifier.testTag("open_player_queue"))
    }
}
