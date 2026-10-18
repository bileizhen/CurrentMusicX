package io.github.currencortex.music.core.update

import io.github.currencortex.music.core.media.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Progress and verified downloads stay visible until cleared, installed or the island is disabled. */
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
                if (download is UpdateDownloadState.Ready) {
                    if (!active) { sequence--; active = true }
                    mutable.value = SoundEffectNotice(sequence, "下载完成", SoundEffectNoticeTier.STANDARD,
                        SoundEffectNoticePhase.READY, "CurrentMusic 更新已校验，点击查看",
                        IslandNoticeKind.UPDATE_DOWNLOAD, 100)
                    return@collectLatest
                }
                if (!active) { mutable.value = null; return@collectLatest }
                val (name, phase, message) = when (download) {
                    is UpdateDownloadState.Failed -> Triple("下载失败", SoundEffectNoticePhase.FAILED, download.message)
                    else -> Triple("已取消下载", SoundEffectNoticePhase.WAITING, "更新下载已取消")
                }
                mutable.value = SoundEffectNotice(sequence, name, SoundEffectNoticeTier.STANDARD, phase, message,
                    IslandNoticeKind.UPDATE_DOWNLOAD)
                delay(if (download is UpdateDownloadState.Failed) 5_000 else 1_800)
                mutable.value = null
                active = false
            }
    } }
}

/** A user-triggered effect acknowledgement briefly interrupts the persistent download status.
 * The latest download remains intact and becomes visible again when the effect notice expires.
 */
internal fun prioritizedIslandNotices(download: StateFlow<SoundEffectNotice?>, effects: StateFlow<SoundEffectNotice?>,
    scope: CoroutineScope, errors: StateFlow<SoundEffectNotice?> = MutableStateFlow(null)): StateFlow<SoundEffectNotice?> =
    combine(download, effects, errors) { update, effect, error -> error ?: effect ?: update }
        .stateIn(scope, SharingStarted.Eagerly, null)
