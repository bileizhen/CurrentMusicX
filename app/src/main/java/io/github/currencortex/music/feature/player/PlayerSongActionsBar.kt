package io.github.currencortex.music.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.media.PlaybackMode
import io.github.currencortex.music.core.media.PlayerMode
import io.github.currencortex.music.data.song.NeteaseSongActionsRepository

@Composable internal fun PlayerSongActionsBar(vm: PlayerViewModel, onComments: () -> Unit, onQueue: () -> Unit,
    onSleep: () -> Unit, onLike: (() -> Unit)?, onEffects: (() -> Unit)? = null, onOptions: (() -> Unit)? = null) {
    val queue by vm.queue.collectAsStateWithLifecycle()
    val actions by vm.actions.collectAsStateWithLifecycle()
    val statuses by vm.libraryStatuses.collectAsStateWithLifecycle()
    val neteaseMain by vm.usesNeteaseLibrary.collectAsStateWithLifecycle()
    val sleep by vm.sleepTimer.state.collectAsStateWithLifecycle()
    val id = NeteaseSongActionsRepository.songId(queue.current)
    val navidrome = queue.current?.musicSource == io.github.currencortex.music.data.song.MusicSource.NAVIDROME
    val current = actions.takeIf { it.songId == if (navidrome) queue.current?.id else id }
    PlayerFunctionBar(actionCount = 5, transportAligned = onOptions != null) {
        // Match transport: fixed 48dp end buttons, three equally weighted middle slots.
        val middleSlot = if (onOptions != null) Modifier.weight(1f) else Modifier
        val liked = if (neteaseMain || navidrome) current?.liked == true else statuses[queue.current?.id]?.liked == true
        PlayerIconButton(PlayerIcon.LIKE, if (liked) "取消喜欢" else "喜欢", vm::togglePrimaryLike,
            Modifier.testTag("player_like"), selected = liked, count = current?.likeCount,
            showCount = true, enabled = queue.current?.video == false && current?.liking != true,
            loading = current?.liking == true, onLongClick = onLike)
        Box(middleSlot, contentAlignment = Alignment.Center) {
            PlayerIconButton(PlayerIcon.COMMENT, "评论区", onComments, Modifier.testTag("player_comments"),
                count = current?.commentCount, showCount = true, enabled = id != null)
        }
        // Lit while a timer is running, so an armed timer is visible without opening the sheet.
        Box(middleSlot, contentAlignment = Alignment.Center) {
            PlayerIconButton(PlayerIcon.ALARM, "定时关闭${if (sleep.enabled) "，正在计时" else ""}", onSleep,
                Modifier.testTag("player_sleep_timer"), selected = sleep.enabled)
        }
        if (onOptions != null) {
            Box(middleSlot, contentAlignment = Alignment.Center) {
                PlayerIconButton(PlayerIcon.EFFECTS, "音效", onEffects ?: {}, Modifier.testTag("player_effects"), enabled = onEffects != null)
            }
            PlayerIconButton(PlayerIcon.MORE, "播放与歌词设置", onOptions, Modifier.testTag("lyrics_options"))
        } else {
            PlayerPlaybackModeButton(vm)
            PlayerQueueButton(vm, onQueue)
        }
    }
}

@Composable internal fun PlayerPlaybackModeButton(vm: PlayerViewModel) {
    val queue by vm.queue.collectAsStateWithLifecycle()
    val player by vm.state.collectAsStateWithLifecycle()
    val heartLoading by vm.heartLoading.collectAsStateWithLifecycle()
    val together by vm.together.state.collectAsStateWithLifecycle()
    val shared by vm.together.queue.collectAsStateWithLifecycle()
    val mode = if (together.active) shared.mode else queue.mode
    PlayerIconButton(when (mode) {
        PlaybackMode.ONE -> PlayerIcon.REPEAT_ONE
        PlaybackMode.SHUFFLE -> PlayerIcon.SHUFFLE
        PlaybackMode.HEART -> PlayerIcon.HEART_MODE
        PlaybackMode.LIST -> PlayerIcon.REPEAT
    }, mode.label, vm::cyclePlaybackMode, Modifier.testTag("player_cycle_mode"),
        selected = mode == PlaybackMode.HEART, enabled = if (together.active) !together.busy else player.mode == PlayerMode.LOCAL && !heartLoading, loading = heartLoading)
}

@Composable internal fun PlayerQueueButton(vm: PlayerViewModel, onQueue: () -> Unit) {
    val queue by vm.queue.collectAsStateWithLifecycle()
    val together by vm.together.state.collectAsStateWithLifecycle()
    val shared by vm.together.queue.collectAsStateWithLifecycle()
    PlayerIconButton(PlayerIcon.QUEUE, if (together.active) "一起听歌单，${shared.songs.size} 首" else "播放列表，${queue.songs.size} 首", onQueue, Modifier.testTag("open_player_queue"))
}
