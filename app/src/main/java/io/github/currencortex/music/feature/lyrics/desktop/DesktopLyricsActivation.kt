package io.github.currencortex.music.feature.lyrics.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DesktopActivationState(val connecting: Boolean = false, val error: String? = null)

/** A late connection must never override a newer close or revoked permission. */
internal class DesktopLyricsActivation(private val scope: CoroutineScope,
    private val permitted: () -> Boolean, private val connect: suspend () -> Unit,
    private val saveEnabled: suspend (Boolean) -> Unit) {
    private var revision = 0
    private var operation: Job? = null
    private val mutable = MutableStateFlow(DesktopActivationState())
    val state = mutable.asStateFlow()
    fun request(enabled: Boolean) {
        val request = ++revision
        operation?.cancel()
        mutable.value = DesktopActivationState(connecting = enabled)
        operation = scope.launch {
            try {
                if (enabled) {
                    if (!permitted()) throw IllegalStateException("请先授予悬浮窗权限")
                    connect()
                    ensureActive()
                    if (!permitted()) throw IllegalStateException("悬浮窗权限已关闭，请重新开启")
                }
                if (request == revision) saveEnabled(enabled)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (request == revision) {
                    saveEnabled(false)
                    mutable.value = DesktopActivationState(error = if (failure is IllegalStateException)
                        failure.message else "无法开启桌面歌词，请重试")
                }
            } finally { if (request == revision) mutable.value = mutable.value.copy(connecting = false) }
        }
    }
    fun close() { ++revision; operation?.cancel() }
}
