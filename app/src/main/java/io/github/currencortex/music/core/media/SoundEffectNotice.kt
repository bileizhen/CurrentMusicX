package io.github.currencortex.music.core.media

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SoundEffectNoticeTier { STANDARD, VIP, SVIP;
    companion object { fun fromMembership(value: Int) = when (value) { 4 -> SVIP; 3 -> VIP; else -> STANDARD } }
}
enum class SoundEffectNoticePhase { APPLYING, READY, WAITING, FAILED }
enum class IslandNoticeKind { SOUND_EFFECT, UPDATE_DOWNLOAD, ERROR, MESSAGE }
data class IslandNoticeAction(val label: String, val invoke: () -> Unit)

data class SoundEffectNotice(val id: Long, val name: String, val tier: SoundEffectNoticeTier,
    val phase: SoundEffectNoticePhase = SoundEffectNoticePhase.APPLYING, val message: String = "正在启用音效…",
    val kind: IslandNoticeKind = IslandNoticeKind.SOUND_EFFECT, val progress: Int? = null,
    val action: IslandNoticeAction? = null)
data class SoundEffectNoticeResult(val phase: SoundEffectNoticePhase, val message: String)

internal fun SoundEffectNotice?.isUpdateInstallNotice() =
    this?.kind == IslandNoticeKind.UPDATE_DOWNLOAD && phase == SoundEffectNoticePhase.READY

internal fun SoundEffectNotice?.isActionableIslandNotice() = this?.action != null || isUpdateInstallNotice()

/** One transient, latest-intent-wins notice shared by the in-app and desktop island. */
class SoundEffectNoticeCenter(private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow<SoundEffectNotice?>(null)
    val state = mutable.asStateFlow()
    private var sequence = 0L
    private var expiry: Job? = null

    @Synchronized fun begin(name: String, tier: SoundEffectNoticeTier): Long {
        val id = ++sequence
        mutable.value = SoundEffectNotice(id, name, tier)
        expire(id, 40_000)
        return id
    }
    @Synchronized fun complete(id: Long, result: SoundEffectNoticeResult) {
        val current = mutable.value?.takeIf { it.id == id } ?: return
        mutable.value = current.copy(phase = result.phase, message = result.message)
        expire(id, if (result.phase == SoundEffectNoticePhase.FAILED) 5_000 else 3_600)
    }
    @Synchronized fun clear(id: Long? = null) {
        if (id != null && mutable.value?.id != id) return
        expiry?.cancel(); expiry = null; mutable.value = null
    }
    private fun expire(id: Long, millis: Long) {
        expiry?.cancel()
        expiry = scope.launch { delay(millis); clear(id) }
    }
}

internal fun originalEffectNoticeResult(loading: Boolean, enabled: Boolean, requested: Boolean,
    processing: Boolean, message: String): SoundEffectNoticeResult? = when {
    loading -> null
    enabled -> SoundEffectNoticeResult(SoundEffectNoticePhase.READY,
        if (processing) "音效已生效" else "已开启，播放后生效")
    requested && message.startsWith("正在") -> null
    requested -> SoundEffectNoticeResult(SoundEffectNoticePhase.WAITING, message)
    else -> SoundEffectNoticeResult(SoundEffectNoticePhase.FAILED, message)
}
