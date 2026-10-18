package io.github.currencortex.music.core.media

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Errors are transient feedback, independent of the saved desktop-lyrics preference. */
class IslandErrorNoticeCenter(private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow<SoundEffectNotice?>(null)
    val state = mutable.asStateFlow()
    private var sequence = 0L
    private var expiry: Job? = null

    @Synchronized fun show(message: String): Long {
        val text = message.trim()
        if (text.isBlank()) return 0
        val id = ++sequence
        mutable.value = SoundEffectNotice(id, "操作提示", SoundEffectNoticeTier.STANDARD,
            SoundEffectNoticePhase.FAILED, text, kind = IslandNoticeKind.ERROR)
        expiry?.cancel()
        expiry = scope.launch { delay(5_500); clear(id) }
        return id
    }

    @Synchronized fun clear(id: Long? = null) {
        if (id != null && mutable.value?.id != id) return
        expiry?.cancel(); expiry = null; mutable.value = null
    }
}
