package io.github.currencortex.music.core.update

import io.github.currencortex.music.core.media.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Download progress has no timeout; terminal acknowledgements briefly retain the same capsule. */
class UpdateIslandNotice(downloads: Flow<UpdateDownloadState>, enabled: Flow<Boolean>, scope: CoroutineScope) {
    private val mutable = MutableStateFlow<SoundEffectNotice?>(null)
    val state = mutable.asStateFlow()

    init { scope.launch {
        var active = false
        var sequence = 0L
        combine(downloads.distinctUntilChanged(), enabled.distinctUntilChanged()) { download, show -> download to show }
            .collectLatest { (download, show) ->
                if (!show) { active = false; mutable.value = null; return@collectLatest }
                if (download is UpdateDownloadState.Downloading) {
                    if (!active) { sequence--; active = true }
                    val percent = if (download.total > 0) (download.received.toDouble() / download.total * 100)
                        .toInt().coerceIn(0, 100) else null
                    mutable.value = SoundEffectNotice(sequence, "更新下载", SoundEffectNoticeTier.STANDARD,
                        message = percent?.let { "更新下载 $it%" } ?: "正在连接下载源",
                        kind = IslandNoticeKind.UPDATE_DOWNLOAD, progress = percent)
                    return@collectLatest
                }
                // Do not replay an old completed task when the app/island is opened later.
                if (!active) { mutable.value = null; return@collectLatest }
                val (name, phase, message) = when (download) {
                    is UpdateDownloadState.Ready -> Triple("下载完成", SoundEffectNoticePhase.READY, "更新已校验，可从通知或设置安装")
                    is UpdateDownloadState.Failed -> Triple("下载失败", SoundEffectNoticePhase.FAILED, download.message)
                    else -> Triple("已取消下载", SoundEffectNoticePhase.WAITING, "更新下载已取消")
                }
                mutable.value = SoundEffectNotice(sequence, name, SoundEffectNoticeTier.STANDARD, phase, message,
                    IslandNoticeKind.UPDATE_DOWNLOAD, if (download is UpdateDownloadState.Ready) 100 else null)
                delay(if (download is UpdateDownloadState.Failed) 5_000 else if (download is UpdateDownloadState.Ready) 3_600 else 1_800)
                mutable.value = null
                active = false
            }
    } }
}

/** Keep effect notices independent so their callbacks cannot overwrite an active download. */
internal fun prioritizedIslandNotices(download: StateFlow<SoundEffectNotice?>, effects: StateFlow<SoundEffectNotice?>,
    scope: CoroutineScope): StateFlow<SoundEffectNotice?> =
    combine(download, effects) { update, effect -> update ?: effect }.stateIn(scope, SharingStarted.Eagerly, null)
