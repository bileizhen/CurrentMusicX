package io.github.currencortex.music.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.media.PlaybackMode
import io.github.currencortex.music.core.media.PlayerMode
import io.github.currencortex.music.data.song.NeteaseSongActionsRepository

@Composable internal fun PlayerSongActionsBar(vm: PlayerViewModel, onComments: () -> Unit, onQueue: () -> Unit,
    onSleep: () -> Unit, onLike: (() -> Unit)?) {
    val queue by vm.queue.collectAsStateWithLifecycle()
    val player by vm.state.collectAsStateWithLifecycle()
    val actions by vm.actions.collectAsStateWithLifecycle()
    val statuses by vm.libraryStatuses.collectAsStateWithLifecycle()
    val heartLoading by vm.heartLoading.collectAsStateWithLifecycle()
    val sleep by vm.sleepTimer.state.collectAsStateWithLifecycle()
    val id = NeteaseSongActionsRepository.songId(queue.current)
    val current = actions.takeIf { it.songId == id }
    PlayerFunctionBar(actionCount = 5) {
        PlayerIconButton(PlayerIcon.LIKE, "收录到我喜欢", { onLike?.invoke() },
            Modifier.testTag("player_like"), selected = statuses[queue.current?.id]?.liked == true || current?.liked == true, count = current?.likeCount,
            showCount = true, enabled = queue.current?.video == false && onLike != null)
        PlayerIconButton(PlayerIcon.COMMENT, "评论区", onComments, Modifier.testTag("player_comments"),
            count = current?.commentCount, showCount = true, enabled = id != null)
        // Lit while a timer is running, so an armed timer is visible without opening the sheet.
        PlayerIconButton(PlayerIcon.ALARM, "定时关闭${if (sleep.enabled) "，正在计时" else ""}", onSleep,
            Modifier.testTag("player_sleep_timer"), selected = sleep.enabled)
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
