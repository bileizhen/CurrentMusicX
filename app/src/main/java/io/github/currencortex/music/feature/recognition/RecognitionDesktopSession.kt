package io.github.currencortex.music.feature.recognition

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-local handoff; no audio, fingerprint or pending recording survives process death. */
object RecognitionDesktopSession {
    private val mutableState = MutableStateFlow(RecognitionState())
    private val mutableAttached = MutableStateFlow(false)
    val state = mutableState.asStateFlow()
    val attached = mutableAttached.asStateFlow()
    internal fun publish(state: RecognitionState) { mutableState.value = state }
    internal fun attach() { mutableAttached.value = true }
    internal fun detach() { mutableAttached.value = false }
    fun clearResult() { if (!attached.value) mutableState.value = RecognitionState() }
}

internal fun recognitionIslandTitle(state: RecognitionState): String = when (state.phase) {
    RecognitionPhase.RECORDING -> "正在聆听 · ${state.seconds} 秒"
    RecognitionPhase.RECOGNIZING -> "正在识别音乐…"
    RecognitionPhase.MATCHED -> state.songs.firstOrNull()?.name ?: "找到这首歌了"
    RecognitionPhase.NO_MATCH -> "暂未识别到歌曲"
    RecognitionPhase.ERROR -> "识曲未完成"
    RecognitionPhase.IDLE -> "听歌识曲"
}

internal fun recognitionIslandSubtitle(state: RecognitionState): String = when (state.phase) {
    RecognitionPhase.RECORDING -> if (state.seconds < 3) "靠近音源 · 最长 10 秒" else "点击胶囊，立即识别"
    RecognitionPhase.RECOGNIZING -> "正在比对音频指纹"
    RecognitionPhase.MATCHED -> state.songs.firstOrNull()?.artists.orEmpty().ifBlank { "点击查看识别结果" }
    else -> state.message ?: "点击返回听歌识曲"
}
