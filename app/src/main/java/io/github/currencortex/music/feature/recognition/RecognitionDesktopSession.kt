package io.github.currencortex.music.feature.recognition

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-local handoff; no audio, fingerprint or pending recording survives process death. */
object RecognitionDesktopSession {
    private val mutableState = MutableStateFlow(RecognitionState())
    private val mutableAttached = MutableStateFlow(false)
    private val mutableRunning = MutableStateFlow(false)
    val state = mutableState.asStateFlow()
    val attached = mutableAttached.asStateFlow()
    val running = mutableRunning.asStateFlow()
    internal fun publish(state: RecognitionState) { mutableState.value = state }
    internal fun attach(desktop: Boolean = true) { mutableRunning.value = true; mutableAttached.value = desktop }
    internal fun detach() { mutableAttached.value = false; mutableRunning.value = false }
    fun clearResult() { if (!running.value) mutableState.value = RecognitionState() }
}

internal fun recognitionIslandTitle(state: RecognitionState): String = when (state.phase) {
    RecognitionPhase.RECORDING -> "${if (state.source == RecognitionAudioSource.SYSTEM) "系统音频" else "正在聆听"} · ${state.seconds} 秒"
    RecognitionPhase.RECOGNIZING -> "正在识别音乐…"
    RecognitionPhase.MATCHED -> state.songs.firstOrNull()?.name ?: "找到这首歌了"
    RecognitionPhase.NO_MATCH -> "暂未识别到歌曲"
    RecognitionPhase.ERROR -> "识曲未完成"
    RecognitionPhase.IDLE -> "听歌识曲"
}

internal fun recognitionIslandSubtitle(state: RecognitionState): String = when (state.phase) {
    RecognitionPhase.RECORDING -> if (state.seconds < 3) "${if (state.source == RecognitionAudioSource.SYSTEM) "播放音乐" else "靠近音源"} · 最长 10 秒" else "点击胶囊，立即识别"
    RecognitionPhase.RECOGNIZING -> if (state.step == RecognitionStep.MATCH) "正在匹配歌曲" else "正在分析声音"
    RecognitionPhase.MATCHED -> state.songs.firstOrNull()?.artists.orEmpty().ifBlank { "点击查看识别结果" }
    else -> state.message ?: "点击返回听歌识曲"
}
