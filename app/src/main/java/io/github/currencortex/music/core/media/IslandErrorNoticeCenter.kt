package io.github.currencortex.music.core.media

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Transient app feedback, independent of the saved desktop-lyrics preference. */
class IslandErrorNoticeCenter(private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow<SoundEffectNotice?>(null)
    val state = mutable.asStateFlow()
    private var sequence = 0L
    private var expiry: Job? = null
    private var expiryGeneration = 0L

    @Synchronized fun show(message: String): Long = publish(message, IslandNoticeKind.ERROR, 5_500)

    @Synchronized fun showMessage(message: String, longDuration: Boolean = false): Long =
        publish(message, IslandNoticeKind.MESSAGE, if (longDuration) 5_500 else 3_600)

    @Synchronized fun showUndo(message: String, onUndo: () -> Unit): Long =
        publish(message, IslandNoticeKind.MESSAGE, 5_000, onUndo)

    private fun publish(message: String, kind: IslandNoticeKind, duration: Long, onUndo: (() -> Unit)? = null): Long {
        val text = message.trim()
        if (text.isBlank()) return 0
        // Repeated feedback extends its lifetime without restarting the island's entrance.
        val id = mutable.value?.takeIf { onUndo == null && it.action == null && it.kind == kind && it.message == text }?.id ?: ++sequence
        val action = onUndo?.let { callback -> IslandNoticeAction("撤销") {
            val accepted = synchronized(this) {
                if (mutable.value?.id != id) false else { clear(id); true }
            }
            if (accepted) callback()
        } }
        mutable.value = SoundEffectNotice(id, "操作提示", SoundEffectNoticeTier.STANDARD,
            if (kind == IslandNoticeKind.ERROR) SoundEffectNoticePhase.FAILED else SoundEffectNoticePhase.READY,
            text, kind = kind, action = action)
        expiry?.cancel()
        val generation = ++expiryGeneration
        expiry = scope.launch {
            delay(duration)
            synchronized(this@IslandErrorNoticeCenter) {
                if (generation == expiryGeneration) clear(id)
            }
        }
        return id
    }

    @Synchronized fun clear(id: Long? = null) {
        if (id != null && mutable.value?.id != id) return
        expiryGeneration++
        expiry?.cancel(); expiry = null; mutable.value = null
    }
}
